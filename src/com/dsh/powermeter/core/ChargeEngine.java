package com.dsh.powermeter.core;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dsh.powermeter.BrandCompat;
import com.dsh.powermeter.Prefs;

/**
 * 采样与展示状态引擎（单例）。
 *
 * 生命周期用引用计数控制：前台服务常驻持有一份，界面打开时再持有一份；
 * 无人持有时停止监听与定时器，避免后台空转耗电。
 *
 * 所有计算在主线程完成（单次采样为几次轻量读取 + 纯算术），不启动额外线程。
 */
public final class ChargeEngine {

    private static final String TAG = "ChargeEngine";
    private static final long INTERVAL_CHARGING_MS = 500;
    private static final long INTERVAL_TURBO_MS = 250;
    private static final long INTERVAL_FULL_MS = 1500;
    private static final long INTERVAL_DISCHARGE_MS = 1000;
    private static final long INTERVAL_IDLE_MS = 5000;

    private static volatile ChargeEngine instance;

    private final Context app;
    private final Prefs prefs;
    private final BatteryReader reader;
    private final ChargeNormalizer normalizer;
    private final PowerSmoother smoother = new PowerSmoother();
    private final PeakTracker peakTracker = new PeakTracker();
    private final SessionTracker sessionTracker = new SessionTracker();
    private final AbnormalDetector abnormalDetector = new AbnormalDetector();
    private final StateStore store = StateStore.get();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable tickRunnable = this::tick;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private int refs;
    private boolean started;
    private boolean timerScheduled;

    private long lastValidAt;
    private long lowPowerSince;
    private long turboUntil;
    private double lastPublishedW = -1;
    private SessionTracker.Summary lastSession;

    private ChargeEngine(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = Prefs.get(this.app);
        this.reader = new BatteryReader(this.app);
        this.normalizer = new ChargeNormalizer(prefs.currentUnitHint());
        this.lastSession = loadLastSession();
        this.prefListener = (sharedPreferences, key) -> {
            if (key == null) return;
            if ("master_enabled".equals(key)) {
                if (!prefs.masterEnabled()) {
                    publishDisabled();
                }
                main.post(this::sampleNow);
            } else {
                main.post(this::sampleNow);
            }
        };
    }

    public static ChargeEngine get(Context context) {
        if (instance == null) {
            synchronized (ChargeEngine.class) {
                if (instance == null) instance = new ChargeEngine(context);
            }
        }
        return instance;
    }

    public void retain() {
        refs++;
        if (refs == 1) start();
    }

    public void release() {
        refs--;
        if (refs <= 0) {
            refs = 0;
            stop();
        }
    }

    private void start() {
        if (started) return;
        started = true;
        try {
            prefs.raw().registerOnSharedPreferenceChangeListener(prefListener);
        } catch (Throwable ignored) {
        }
        try {
            reader.start(this::sampleNow);
        } catch (Throwable t) {
            Log.w(TAG, "启动电池读取失败", t);
        }
        sampleNow();
    }

    private void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(tickRunnable);
        timerScheduled = false;
        try {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(prefListener);
        } catch (Throwable ignored) {
        }
        try {
            reader.stop();
        } catch (Throwable ignored) {
        }
        smoother.reset();
        peakTracker.reset();
    }

    public PowerSnapshot latest() {
        PowerSnapshot s = store.latest();
        return s;
    }

    public BatteryReader reader() {
        return reader;
    }

    /** 由广播/界面触发一次立即采样。 */
    public void sampleNow() {
        if (!started) return;
        main.removeCallbacks(tickRunnable);
        timerScheduled = false;
        tick();
    }

    private void tick() {
        timerScheduled = false;
        if (!started) return;
        try {
            sampleOnce();
        } catch (Throwable t) {
            Log.w(TAG, "采样失败", t);
            publishUnavailable("读取失败");
        }
        scheduleNext();
    }

    private void sampleOnce() {
        long now = System.currentTimeMillis();
        boolean master = prefs.masterEnabled();
        if (!master) {
            publishDisabled();
            return;
        }

        RawBattery raw = reader.read(prefs.enhancedReadEnabled(), prefs.shizukuReadEnabled());
        int dualMode = prefs.dualCellMode();
        NormalizedReading n = normalizer.normalize(raw, dualMode, BrandCompat.dualCellFamily());

        int unit = normalizer.preferredUnit();
        if (unit != prefs.currentUnitHint() && unit != ChargeNormalizer.UNIT_UNKNOWN) {
            prefs.setCurrentUnitHint(unit);
        }

        boolean plugged = n.plugged;
        boolean chargingStatus = raw.isChargingStatus();
        boolean charging = chargingStatus && n.valid;
        boolean full = n.state == ChargeState.FULL;
        boolean realtime = prefs.realtimePowerEnabled();
        double level = n.level;
        boolean screenOn = reader.screenOn();

        // 平滑：充电功率走功率平滑；净功耗用电压/电流平滑后相乘，保证两种口径一致。
        boolean sessionStarting = charging && !sessionTracker.active();
        if (sessionStarting) {
            peakTracker.reset();
            lowPowerSince = 0;
        }
        double smoothedW = 0;
        double smoothedV = n.voltageV;
        double smoothedA = n.currentA;
        double powerRatio = prefs.powerRatio();
        if (n.valid && charging) {
            smoothedW = smoother.addPower(n.chargeW) * powerRatio;
            smoothedV = smoother.addVoltage(n.voltageV);
            smoothedA = smoother.addCurrent(n.currentA);
            lastValidAt = now;
            peakTracker.add(smoothedW);
        } else if (!plugged && n.currentSupported && n.voltageV > 0) {
            smoothedV = smoother.addVoltage(n.voltageV);
            smoothedA = smoother.addCurrent(n.currentA);
        }

        // 会话统计与本次充电摘要
        boolean ended = sessionTracker.update(charging, smoothedW, n.level, now);
        if (ended) {
            SessionTracker.Summary s = sessionTracker.end(now, n.level);
            // 摘要里的峰值使用 3–5 秒平滑峰值，而不是单点瞬时最大值
            s.peakW = Math.max(s.peakW, peakTracker.peak());
            saveSession(s);
        }

        // 异常判定 / 配件提示
        double currentPeak = Math.max(peakTracker.peak(), sessionTracker.peakW());
        int abnormal = abnormalDetector.update(now, charging, smoothedW,
                n.temperatureC, currentPeak);
        String abnormalText = abnormalDetector.text(abnormal);

        String accessoryText = null;
        if (charging && smoothedW < 5.0 && level >= 0 && level < 95) {
            if (lowPowerSince == 0) lowPowerSince = now;
            if (now - lowPowerSince >= 60_000L) {
                accessoryText = "充电功率持续偏低，建议检查充电器、线材或接口接触";
            }
        } else {
            lowPowerSince = 0;
        }

        boolean nearZero = chargingStatus
                && Math.abs(smoothedA) <= 0.03
                && n.currentSupported;
        double netDisplayW = Math.abs(smoothedV * smoothedA);
        double chargeDisplayW = smoothedW;

        double mainW;
        String modeLabel;
        if (realtime) {
            modeLabel = chargingStatus ? "净功耗" : "放电功耗";
            mainW = nearZero ? 0 : netDisplayW;
        } else {
            modeLabel = "入电池功率";
            mainW = charging ? chargeDisplayW : (n.state == ChargeState.PAUSED ? lastPositive(chargeDisplayW) : 0);
        }

        ChargeTier tier = ChargeTier.of(chargeDisplayW,
                prefs.lowTierWatts(), prefs.highTierWatts());
        long remain = -1;
        if (charging) {
            remain = ChargeTimeEstimator.estimateSeconds(n.level, chargeDisplayW,
                    smoothedV, raw.chargeCounterUah, n.cells);
        }

        ChargeState state = n.state;
        if (!plugged) {
            state = ChargeState.UNPLUGGED;
        } else if (full) {
            state = ChargeState.FULL;
        } else if (abnormal != AbnormalDetector.NONE) {
            state = ChargeState.ABNORMAL;
        } else if (chargingStatus && !n.valid) {
            state = realtime && nearZero ? ChargeState.CHARGING : ChargeState.UNAVAILABLE;
        }

        boolean validForDisplay = realtime ? n.currentSupported : (charging && n.valid);
        String sourceLabel = n.enhanced ? "增强读取（实验性）"
                : (n.currentSupported ? "标准读取" : "读数不可用");

        PowerSnapshot snapshot = PowerSnapshot.builder()
                .timestampMs(now)
                .masterEnabled(true)
                .notificationsAllowed(notificationsAllowed())
                .realtime(realtime)
                .plugged(plugged)
                .charging(chargingStatus)
                .state(state)
                .tier(tier)
                .valid(validForDisplay)
                .nearZero(nearZero)
                .powerW(mainW)
                .modeLabel(modeLabel)
                .chargeW(chargeDisplayW)
                .netW(smoothedV * smoothedA)
                .voltageV(smoothedV)
                .currentA(smoothedA)
                .temperatureC(n.temperatureC)
                .level(n.level)
                .cells(n.cells)
                .voltageCorrected(n.voltageCorrected)
                .peakW(currentPeak)
                .remainingSeconds(remain)
                .estimateValid(remain > 0)
                .sourceLabel(sourceLabel)
                .enhanced(n.enhanced)
                .currentSupported(n.currentSupported)
                .invalidReason(n.invalidReason)
                .abnormalText(abnormalText)
                .accessoryText(accessoryText)
                .sessionActive(sessionTracker.active())
                .sessionDurationMs(sessionTracker.durationMs(now))
                .sessionAvgW(sessionTracker.averageW())
                .sessionPeakW(currentPeak)
                .lastSession(sessionTracker.active() ? null : lastSession)
                .showTemperature(prefs.showTemperature())
                .showEstimate(prefs.showEstimate())
                .showTier(prefs.showTier())
                .powerMaxW(n.powerMaxW)
                .chargeCounterUahRaw((int) Math.min(Integer.MAX_VALUE, Math.max(-1, raw.chargeCounterUah)))
                .build();

        // 自适应刷新：功率剧烈波动时临时提频
        if (lastPublishedW >= 0 && Math.abs(mainW - lastPublishedW) > 3.0) {
            turboUntil = now + 5000;
        }
        lastPublishedW = mainW;
        store.publish(snapshot);
    }

    private double lastPositive(double value) {
        return value > 0 ? value : 0;
    }

    private void publishDisabled() {
        PowerSnapshot s = PowerSnapshot.builder()
                .masterEnabled(false)
                .notificationsAllowed(notificationsAllowed())
                .state(ChargeState.UNPLUGGED)
                .sourceLabel("已关闭")
                .build();
        store.publish(s);
    }

    private void publishUnavailable(String reason) {
        PowerSnapshot s = PowerSnapshot.builder()
                .masterEnabled(prefs.masterEnabled())
                .notificationsAllowed(notificationsAllowed())
                .state(ChargeState.UNAVAILABLE)
                .invalidReason(reason)
                .sourceLabel("读数不可用")
                .build();
        store.publish(s);
    }

    private boolean notificationsAllowed() {
        try {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    private void scheduleNext() {
        main.removeCallbacks(tickRunnable);
        timerScheduled = false;
        if (!started) return;
        long now = System.currentTimeMillis();
        long delay;
        PowerSnapshot s = store.latest();
        boolean master = prefs.masterEnabled();
        if (!master) {
            delay = INTERVAL_IDLE_MS;
        } else if (s == null) {
            delay = INTERVAL_CHARGING_MS;
        } else if (s.state == ChargeState.FULL) {
            delay = INTERVAL_FULL_MS;
        } else if (s.plugged) {
            delay = now < turboUntil ? INTERVAL_TURBO_MS : INTERVAL_CHARGING_MS;
        } else if (prefs.realtimePowerEnabled()) {
            delay = reader.screenOn() ? INTERVAL_DISCHARGE_MS : 0;
        } else {
            delay = 0; // 未充电：完全休眠，等插电/亮屏广播唤醒
        }
        if (delay > 0) {
            main.postDelayed(tickRunnable, delay);
            timerScheduled = true;
        }
    }

    private SessionTracker.Summary loadLastSession() {
        if (!prefs.hasLastSession()) return null;
        SessionTracker.Summary s = new SessionTracker.Summary();
        s.endTimeMs = prefs.lastSessionEnd();
        s.durationMs = prefs.lastSessionDuration();
        s.peakW = prefs.lastSessionPeak();
        s.avgW = prefs.lastSessionAvg();
        s.startLevel = prefs.lastSessionStartLevel();
        s.endLevel = prefs.lastSessionEndLevel();
        return s;
    }

    private void saveSession(SessionTracker.Summary s) {
        if (s == null || s.durationMs < 30_000L) return;
        lastSession = s;
        prefs.saveLastSession(s.endTimeMs, s.durationMs, s.peakW, s.avgW, s.startLevel, s.endLevel);
    }
}
