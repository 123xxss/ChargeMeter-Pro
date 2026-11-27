package com.dsh.powermeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.powermeter.core.AppUsageRanker;
import com.dsh.powermeter.core.ChargeEngine;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.SessionTracker;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.service.PowerService;
import com.dsh.powermeter.shizuku.ShizukuBatterySource;
import com.dsh.powermeter.ui.DetailDialog;
import com.dsh.powermeter.ui.PowerCardView;
import com.dsh.powermeter.ui.PowerChartView;
import com.dsh.powermeter.ui.UiKit;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 主界面：实时充电功率 + 指标 + 本次/上次充电统计 + 详情入口。
 *
 * 全部界面程序化构建，不新增布局资源；颜色统一来自 R.color，
 * 深浅色切换由系统重建 Activity 自动完成。
 */
public final class MainActivity extends Activity implements StateStore.Listener {

    private static final int REQ_NOTIFICATION = 2001;

    private static final int BANNER_NONE = 0;
    private static final int BANNER_NOTIFICATION = 1;
    private static final int BANNER_SETTINGS = 2;

    private final Handler main = new Handler(Looper.getMainLooper());

    private ChargeEngine engine;
    /** UI 是否完整构建；构建/刷新异常时进入安全模式，避免直接闪退。 */
    private boolean uiReady;
    private boolean fallbackShown;

    private LinearLayout banner;
    private TextView bannerText;
    private int bannerAction = BANNER_NONE;

    private LinearLayout masterOffCard;

    private PowerCardView heroCard;
    private PowerChartView chart;
    private LinearLayout chartCard;
    private TextView chartHint;
    private long lastChartTimestamp = -1;
    private boolean lastSessionActive;
    private boolean entranceAnimated;
    private LinearLayout systemCard;
    private LinearLayout usageCard;
    private LinearLayout usageList;
    private TextView usageHint;
    private final Map<String, Drawable> usageIconCache = new HashMap<>();
    private long lastUsageLoad;
    private final Runnable usageTicker = new Runnable() {
        @Override
        public void run() {
            refreshUsagePreview();
            main.postDelayed(this, 3000L);
        }
    };
    private UiKit.Metric sysCpu;
    private UiKit.Metric sysRam;
    private UiKit.Metric sysGpu;
    private UiKit.Metric sysThermal;
    private TextView systemHint;

    private LinearLayout gridCard;
    private TextView headerStatus;
    private TextView gridTitle;
    private LinearLayout gridRow1;
    private LinearLayout gridRow2;
    private UiKit.Metric cellVoltage;
    private UiKit.Metric cellCurrent;
    private UiKit.Metric cellTemp;
    private UiKit.Metric cellPeak;

    private LinearLayout sessionCard;
    private TextView sessionHeading;
    private TextView sessionSub;
    private LinearLayout sessionMetrics;
    private UiKit.Metric sessionDuration;
    private UiKit.Metric sessionAvg;
    private UiKit.Metric sessionPeak;

    private LinearLayout footerCard;

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        maybeShowLastCrash();
        try {
            // 必须先 setContentView 再取 WindowInsetsController；
            // Android 16 上 DecorView 未创建时 getInsetsController() 会 NPE。
            buildUi();
            applySystemBars();
            uiReady = true;
        } catch (Throwable t) {
            showSafeFallback(t);
            return;
        }
        main.postDelayed(this::showGuideIfNeeded, 700);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!uiReady) return;
        try {
            Prefs prefs = Prefs.get(this);
            if (prefs.masterEnabled()) {
                try {
                    // 从可见界面用 startService，避免 FGS 启动失败时触发系统
                    // “did not call startForeground” 的进程级崩溃。
                    PowerService.startFromActivity(this);
                } catch (Throwable ignored) {
                }
            }
            engine = ChargeEngine.get(this);
            engine.retain();
            StateStore.get().addListener(this);
            engine.sampleNow();
            render(StateStore.get().latest());
            requestNotificationPermissionIfNeeded();
            main.removeCallbacks(usageTicker);
            main.post(usageTicker);
        } catch (Throwable t) {
            showSafeFallback(t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!uiReady) return;
        try {
            applySystemBars();
            if (heroCard != null) heroCard.setStyle(Prefs.get(this).uiStyle());
            if (chart != null) chart.setStyle(Prefs.get(this).uiStyle());
            render(StateStore.get().latest());
        } catch (Throwable t) {
            showSafeFallback(t);
        }
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(usageTicker);
        StateStore.get().removeListener(this);
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 状态刷新

    @Override
    public void onSnapshot(PowerSnapshot snapshot) {
        if (!uiReady) return;
        try {
            render(snapshot);
        } catch (Throwable t) {
            showSafeFallback(t);
        }
    }

    private void animateEntrance() {
        if (entranceAnimated) return;
        entranceAnimated = true;
        int motion = Prefs.get(this).motionStyle();
        if (motion == 2) return;
        boolean rich = motion == 1;
        boolean vivo = motion == 3;
        boolean ios = motion == 4;
        View[] views = {heroCard, gridCard, chartCard,
                systemCard, usageCard, sessionCard, footerCard};
        int step = UiKit.dp(this, vivo ? 18 : (rich ? 14 : 10));
        int delay = vivo ? 44 : (rich ? 42 : 28);
        int duration = vivo ? 340 : (rich ? 320 : (ios ? 260 : 220));
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(step);
            android.view.ViewPropertyAnimator anim = v.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(i * (long) delay);
            if (vivo) anim.setInterpolator(new android.view.animation.OvershootInterpolator(0.7f));
            if (ios) anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.setDuration(duration).start();
        }
    }

    private void render(PowerSnapshot snapshot) {
        Prefs prefs = Prefs.get(this);
        boolean master = prefs.masterEnabled();

        masterOffCard.setVisibility(master ? View.GONE : View.VISIBLE);
        heroCard.setVisibility(master ? View.VISIBLE : View.GONE);
        gridCard.setVisibility(master ? View.VISIBLE : View.GONE);
        sessionCard.setVisibility(master ? View.VISIBLE : View.GONE);
        chartCard.setVisibility(master ? View.VISIBLE : View.GONE);
        systemCard.setVisibility(master ? View.VISIBLE : View.GONE);
        usageCard.setVisibility(master ? View.VISIBLE : View.GONE);
        footerCard.setVisibility(master ? View.VISIBLE : View.GONE);
        updateBanner();
        updateHeaderStatus(master, snapshot);
        if (!master) {
            if (chart != null) chart.clear();
            if (chartHint != null) chartHint.setText("最近 2 分钟");
            lastChartTimestamp = -1;
            lastSessionActive = false;
            return;
        }

        renderHero(snapshot);
        renderGrid(snapshot);
        renderChart(snapshot);
        renderSystem(snapshot);
        refreshUsagePreview();
        renderSession(snapshot);
    }

    private void renderHero(PowerSnapshot s) {
        // 所有绘制在 PowerCardView 内部完成，避免子视图测量/权重导致的缺失与拉伸。
        heroCard.setData(s);
    }

    private void renderGrid(PowerSnapshot s) {
        Prefs prefs = Prefs.get(this);
        boolean showTemp = s != null ? s.showTemperature : prefs.showTemperature();
        cellTemp.root.setVisibility(showTemp ? View.VISIBLE : View.INVISIBLE);
        boolean hideForIdle = s != null && s.state == ChargeState.UNPLUGGED
                && !s.realtime;
        // 未充电且未开启实时功耗时，PRD 要求隐藏全部充电相关数值。
        setMetricValue(cellVoltage, s == null || hideForIdle ? "--" : UiKit.volts(s.voltageV));
        setMetricValue(cellCurrent, s == null || hideForIdle ? "--" : UiKit.amps(s.currentA));
        setMetricValue(cellTemp, s == null || hideForIdle ? "--" : UiKit.temp(s.temperatureC));
        boolean noPeak = s == null || !s.sessionActive || s.peakW <= 0.01;
        setMetricValue(cellPeak, hideForIdle || noPeak ? "--" : UiKit.watts(s.peakW));
    }

    private void renderChart(PowerSnapshot s) {
        if (chart == null) return;
        if (s == null) return;
        if (s.sessionActive && !lastSessionActive) {
            // 新的一次充电从空曲线开始，避免上一次的旧数据干扰判断。
            chart.clear();
            lastChartTimestamp = -1;
        }
        lastSessionActive = s.sessionActive;
        if (s.timestampMs == lastChartTimestamp) return;
        if (s.charging && s.valid && s.chargeW > 0) {
            lastChartTimestamp = s.timestampMs;
            chart.add(s.chargeW);
            if (chartHint != null) {
                chartHint.setText(String.format(java.util.Locale.ROOT,
                        "当前 %.2f W", s.chargeW));
            }
        } else if (chartHint != null) {
            chartHint.setText("最近 2 分钟");
        }
    }

    private void renderSystem(PowerSnapshot s) {
        if (sysCpu == null) return;
        com.dsh.powermeter.core.SystemStats st = s == null ? null : s.systemStats;
        if (st == null) {
            setMetricValue(sysCpu, "--");
            setMetricValue(sysRam, "--");
            setMetricValue(sysGpu, "--");
            setMetricValue(sysThermal, "--");
            sysCpu.label.setText("CPU");
            sysRam.label.setText("可用内存");
            sysGpu.label.setText("GPU");
            sysThermal.label.setText("系统温度");
            systemHint.setVisibility(View.VISIBLE);
            systemHint.setText("系统状态读取中 · 点此用 Shizuku 尝试读取");
            return;
        }
        boolean unavailable = Double.isNaN(st.cpuPercent)
                || Double.isNaN(st.gpuPercent) || Double.isNaN(st.thermalMaxC);
        systemHint.setVisibility(unavailable ? View.VISIBLE : View.GONE);
        if (unavailable) {
            systemHint.setText("CPU/GPU/温度读不到？点此用 Shizuku 尝试读取");
        }
        // 数值只放短指标，频率/已用比例放次要标签，避免 “19% · 0.30G…” 被截断。
        if (Double.isNaN(st.cpuPercent)) {
            setMetricValue(sysCpu, "--");
            sysCpu.label.setText("CPU");
        } else {
            setMetricValue(sysCpu, String.format(java.util.Locale.ROOT, "%.0f%%", st.cpuPercent));
            String detail = "CPU";
            if (!Double.isNaN(st.cpuFreqGhz)) {
                detail += String.format(java.util.Locale.ROOT, " · %.2fGHz", st.cpuFreqGhz);
            }
            if (st.cpuCores > 0) detail += " · " + st.cpuCores + " 核";
            sysCpu.label.setText(detail);
        }
        setMetricValue(sysRam, st.ramSummary());
        sysRam.label.setText(st.ramTotalMb > 0
                ? String.format(java.util.Locale.ROOT, "可用内存 · 已用 %.0f%%", st.ramUsedPercent)
                : "可用内存");
        setMetricValue(sysGpu, Double.isNaN(st.gpuPercent) ? "--" : st.gpuSummary());
        sysGpu.label.setText(Double.isNaN(st.gpuPercent) ? "GPU · 系统未开放" : "GPU 使用率");
        setMetricValue(sysThermal, Double.isNaN(st.thermalMaxC) ? "--" : st.thermalSummary());
        sysThermal.label.setText(Double.isNaN(st.thermalMaxC) ? "温度 · 未开放" : "系统最高温度");
    }

    private void openShizukuForStats() {
        try {
            if (!ShizukuBatterySource.isAvailable()) {
                Toast.makeText(this, "未检测到 Shizuku，请先安装并启动 Shizuku", Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuBatterySource.hasPermission()) {
                ShizukuBatterySource.requestPermission(this);
                Toast.makeText(this, "请在 Shizuku 弹窗中允许本应用", Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.get(this).setShizukuReadEnabled(true);
            ShizukuBatterySource.bindIfPossible();
            Toast.makeText(this, "正在用 Shizuku 读取系统状态", Toast.LENGTH_SHORT).show();
            ChargeEngine.get(this).sampleNow();
        } catch (Throwable t) {
            Toast.makeText(this, "Shizuku 操作失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshUsagePreview() {
        if (usageList == null || usageHint == null) return;
        long now = System.currentTimeMillis();
        if (now - lastUsageLoad < 2500L) return;
        lastUsageLoad = now;
        try {
            PowerSnapshot snapshot = StateStore.get().latest();
            double watts = snapshot != null ? snapshot.powerW : Double.NaN;
            AppUsageRanker.Result result = AppUsageRanker.load(this, watts);
            usageList.removeAllViews();
            if (!result.hasAccess) {
                usageHint.setText("未授权 · 点击进入授权后显示最近 5 分钟前台排行");
                return;
            }
            if (result.entries.isEmpty()) {
                usageHint.setText("最近 5 分钟暂无前台记录 · 3 秒刷新");
                return;
            }
            usageHint.setText("最近 5 分钟前台 · 含系统应用 · 3 秒刷新");
            List<AppUsageRanker.Entry> entries = result.entries;
            int count = Math.min(3, entries.size());
            for (int i = 0; i < count; i++) {
                AppUsageRanker.Entry e = entries.get(i);
                LinearLayout row = UiKit.row(this);
                row.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 4));
                TextView rank = UiKit.textColor(this, String.valueOf(i + 1), 13f,
                        getColor(i == 0 ? R.color.accent : R.color.text_muted), true);
                row.addView(rank, new LinearLayout.LayoutParams(
                        UiKit.dp(this, 20), UiKit.dp(this, 28)));
                ImageView icon = new ImageView(this);
                Drawable d = usageIconCache.get(e.packageName);
                if (d == null) {
                    try {
                        d = getPackageManager().getApplicationIcon(e.packageName);
                    } catch (Throwable ignored) {
                    }
                    if (d != null) usageIconCache.put(e.packageName, d);
                }
                if (d != null) icon.setImageDrawable(d);
                row.addView(icon, new LinearLayout.LayoutParams(
                        UiKit.dp(this, 24), UiKit.dp(this, 24)));
                TextView name = UiKit.text(this, e.label + (e.system ? " · 系统" : ""),
                        12.5f, R.color.text_primary, true);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameLp = UiKit.weighted(1f);
                nameLp.leftMargin = UiKit.dp(this, 8);
                row.addView(name, nameLp);
                String val = Double.isFinite(e.estimatedW) && e.estimatedW > 0
                        ? String.format(Locale.ROOT, "≈%.2f W", e.estimatedW)
                        : String.format(Locale.ROOT, "%.0f%%", e.percent);
                TextView w = UiKit.textColor(this, val, 11.5f,
                        getColor(i == 0 ? R.color.accent : R.color.text_secondary), true);
                w.setSingleLine(true);
                w.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(w, UiKit.left(this, UiKit.wrapWrap(), 8));
                usageList.addView(row, UiKit.matchWrap());
            }
        } catch (Throwable t) {
            if (usageHint != null) usageHint.setText("排行读取失败 · 点击查看详情");
        }
    }

    private void renderSession(PowerSnapshot s) {
        Prefs prefs = Prefs.get(this);
        if (s != null && s.sessionActive) {
            sessionHeading.setText("本次充电");
            sessionSub.setText("进行中 · 已充 " + UiKit.duration(s.sessionDurationMs));
            sessionMetrics.setVisibility(View.VISIBLE);
            setMetricValue(sessionDuration, compactDuration(s.sessionDurationMs));
            setMetricValue(sessionAvg, UiKit.watts(s.sessionAvgW));
            setMetricValue(sessionPeak, UiKit.watts(s.sessionPeakW));
            return;
        }

        SessionTracker.Summary last = s != null ? s.lastSession : null;
        boolean has = false;
        long durationMs = 0;
        double avgW = 0;
        double peakW = 0;
        long endMs = 0;
        int startLevel = -1;
        int endLevel = -1;
        if (last != null) {
            has = true;
            durationMs = last.durationMs;
            avgW = last.avgW;
            peakW = last.peakW;
            endMs = last.endTimeMs;
            startLevel = last.startLevel;
            endLevel = last.endLevel;
        } else if (prefs.hasLastSession()) {
            has = true;
            durationMs = prefs.lastSessionDuration();
            avgW = prefs.lastSessionAvg();
            peakW = prefs.lastSessionPeak();
            endMs = prefs.lastSessionEnd();
            startLevel = prefs.lastSessionStartLevel();
            endLevel = prefs.lastSessionEndLevel();
        }

        if (has) {
            sessionHeading.setText("上次充电");
            String sub = endMs > 0 ? UiKit.clock(endMs) : "之前";
            if (startLevel >= 0 && endLevel >= 0) {
                sub = sub + " · " + startLevel + "% → " + endLevel + "%";
            }
            sessionSub.setText(sub);
            sessionMetrics.setVisibility(View.VISIBLE);
            setMetricValue(sessionDuration, compactDuration(durationMs));
            setMetricValue(sessionAvg, avgW > 0 ? UiKit.watts(avgW) : "--");
            setMetricValue(sessionPeak, peakW > 0 ? UiKit.watts(peakW) : "--");
        } else {
            sessionHeading.setText("暂无充电记录");
            sessionSub.setText("接入电源充一次电后，这里会显示本次与上次的统计。");
            sessionMetrics.setVisibility(View.GONE);
        }
    }

    private String compactDuration(long millis) {
        if (millis <= 0) return "--";
        long minutes = Math.max(1L, Math.round(millis / 60000.0));
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours <= 0) return minutes + " 分";
        return hours + " 时 " + (rest < 10 ? "0" + rest : String.valueOf(rest)) + " 分";
    }

    private void updateBanner() {
        boolean allowed = notificationsAllowed();
        boolean prefsOn = Prefs.get(this).notificationEnabled();
        if (!allowed) {
            bannerAction = BANNER_NOTIFICATION;
            bannerText.setText("通知权限未开启，状态栏卡片无法显示。点此进入系统通知设置。");
            banner.setVisibility(View.VISIBLE);
        } else if (!prefsOn) {
            bannerAction = BANNER_SETTINGS;
            bannerText.setText("状态栏通知已在设置中关闭。点此重新开启。");
            banner.setVisibility(View.VISIBLE);
        } else {
            bannerAction = BANNER_NONE;
            banner.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- 操作

    private void enableMaster() {
        Prefs.get(this).setMasterEnabled(true);
        try {
            PowerService.startFromActivity(this);
        } catch (Throwable ignored) {
        }
        ChargeEngine.get(this).sampleNow();
        render(StateStore.get().latest());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        Prefs prefs = Prefs.get(this);
        if (prefs.notificationAsked()) return;
        prefs.markNotificationAsked();
        try {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQ_NOTIFICATION);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showGuideIfNeeded() {
        if (isFinishing() || isDestroyed()) return;
        try {
            Prefs prefs = Prefs.get(this);
            if (!BrandCompat.isTargetBrand() || prefs.guideShown()) return;
            AlertDialog.Builder builder = dialogBuilder("后台运行设置");
            builder.setMessage(BrandCompat.guideSteps()
                    + "\n\n机型：" + BrandCompat.brandDisplayName()
                    + "\n也可以稍后在设置页重新查看。");
            builder.setPositiveButton("去设置", (dialog, which) -> {
                Prefs.get(this).markGuideShown();
                BrandCompat.openBackgroundSettings(this);
            });
            builder.setNegativeButton("稍后", (dialog, which) -> Prefs.get(this).markGuideShown());
            builder.setCancelable(false);
            builder.show();
        } catch (Throwable t) {
            // OEM 弹窗主题差异等异常不应影响主界面
            CrashReporter.record(this, t);
        }
    }

    private AlertDialog.Builder dialogBuilder(CharSequence title) {
        int theme = isNight()
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, theme);
        builder.setTitle(title);
        return builder;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION) {
            updateBanner();
        }
    }

    // ---------------------------------------------------------------- 崩溃与安全模式

    /** 上一次启动/运行异常时，把堆栈显示出来，方便一键复制反馈。 */
    private void maybeShowLastCrash() {
        String report = CrashReporter.readLast(this);
        if (report == null) return;
        try {
            TextView detail = new TextView(this);
            detail.setText(report);
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            detail.setTypeface(Typeface.MONOSPACE);
            detail.setTextIsSelectable(true);
            detail.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 12),
                    UiKit.dp(this, 16), UiKit.dp(this, 12));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(detail);
            new AlertDialog.Builder(this)
                    .setTitle("上次启动异常（可复制反馈）")
                    .setView(scroll)
                    .setPositiveButton("复制", (dialog, which) -> copyText(report))
                    .setNeutralButton("清除", (dialog, which) -> CrashReporter.clear(this))
                    .setNegativeButton("关闭", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    /** 极简安全模式：UI 构建/刷新失败时仍能打开并复制错误，而不是闪退。 */
    private void showSafeFallback(Throwable error) {
        if (fallbackShown || isFinishing()) return;
        fallbackShown = true;
        uiReady = false;
        CrashReporter.record(this, error);
        try {
            String report = CrashReporter.stack(error);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = UiKit.dp(this, 24);
            root.setPadding(pad, pad, pad, pad);
            root.setBackgroundColor(0xFFF4F6FA);

            TextView title = new TextView(this);
            title.setText(getString(R.string.app_name) + " 启动异常");
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            title.setTextColor(0xFF10151F);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(title);

            TextView tip = new TextView(this);
            tip.setText("已进入安全模式（不会闪退）。请点下面按钮复制错误信息发给开发者。");
            tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tip.setTextColor(0xFF5B6675);
            tip.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 12));
            root.addView(tip);

            TextView copy = new TextView(this);
            copy.setText("复制错误信息");
            copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            copy.setTextColor(0xFFFFFFFF);
            copy.setTypeface(Typeface.DEFAULT_BOLD);
            copy.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 10),
                    UiKit.dp(this, 18), UiKit.dp(this, 10));
            copy.setBackground(UiKit.rounded(this, 0xFF2F6BFF, 100f));
            copy.setOnClickListener(v -> copyText(report));
            root.addView(copy);

            TextView detail = new TextView(this);
            detail.setText(report);
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            detail.setTypeface(Typeface.MONOSPACE);
            detail.setTextColor(0xFF5B6675);
            detail.setTextIsSelectable(true);
            detail.setPadding(0, UiKit.dp(this, 14), 0, 0);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(detail);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            setContentView(root);
        } catch (Throwable ignored) {
            // 极端的资源/系统问题：到此为止，至少不要让安全模式自己再崩一次
        }
    }

    private void copyText(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("PowerMeter Pro crash", text));
                Toast.makeText(this, "已复制，可直接粘贴发送", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- 系统栏

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars() {
        UiKit.styleSystemBars(this, getColor(R.color.bg), !isNight());
    }

    private boolean notificationsAllowed() {
        try {
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 界面搭建

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.bg));

        LinearLayout root = UiKit.column(this);
        root.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 6),
                UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(root, new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        UiKit.applyEdgeToEdgeInsets(this, scroll);

        // 顶栏
        LinearLayout header = UiKit.row(this);
        TextView title = UiKit.text(this, getString(R.string.app_name), 23f, R.color.text_primary, true);
        header.addView(title, UiKit.weighted(1f));
        headerStatus = UiKit.pill(this, "待机", getColor(R.color.text_secondary),
                getColor(R.color.card_alt));
        headerStatus.setTextSize(11f);
        headerStatus.setMaxWidth(UiKit.dp(this, 108));
        headerStatus.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(headerStatus, UiKit.left(this, UiKit.wrapWrap(), 6));
        ImageView settings = UiKit.iconButton(this, R.drawable.ic_settings,
                R.color.text_secondary,
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        settings.setContentDescription("设置");
        header.addView(settings);
        root.addView(header, UiKit.matchWrap());

        // 通知权限横幅
        banner = UiKit.row(this);
        banner.setBackground(UiKit.rounded(this, getColor(R.color.warning_bg), 14f));
        banner.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 11),
                UiKit.dp(this, 12), UiKit.dp(this, 11));
        bannerText = UiKit.textColor(this, "", 13f, getColor(R.color.warning_text), true);
        bannerText.setMaxLines(2);
        bannerText.setEllipsize(TextUtils.TruncateAt.END);
        banner.addView(bannerText, UiKit.weighted(1f));
        ImageView bannerChevron = new ImageView(this);
        bannerChevron.setImageDrawable(UiKit.chevron(this, getColor(R.color.warning_text), false));
        banner.addView(bannerChevron, UiKit.left(this,
                new LinearLayout.LayoutParams(UiKit.dp(this, 18), UiKit.dp(this, 18)), 6));
        banner.setOnClickListener(v -> {
            if (bannerAction == BANNER_NOTIFICATION) {
                BrandCompat.openNotificationSettings(this);
            } else if (bannerAction == BANNER_SETTINGS) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        });
        UiKit.setRipple(banner);
        root.addView(banner, UiKit.top(this, UiKit.matchWrap(), 14));

        // 总开关关闭提示
        buildMasterOffCard();
        root.addView(masterOffCard, UiKit.top(this, UiKit.matchWrap(), 14));

        // 主视觉卡片：自绘紧凑卡，固定高度，内容不会因布局测量被挤掉
        buildHeroCard();
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 236));
        heroLp.topMargin = UiKit.dp(this, 14);
        root.addView(heroCard, heroLp);

        // 实时数据：单卡片 2×2 数据块
        gridCard = UiKit.card(this);
        LinearLayout gridHead = UiKit.row(this);
        gridTitle = UiKit.text(this, "实时数据", 14f, R.color.text_primary, true);
        gridHead.addView(gridTitle, UiKit.weighted(1f));
        TextView gridHint = UiKit.text(this, "1 秒刷新", 11f, R.color.text_muted, false);
        gridHead.addView(gridHint);
        gridCard.addView(gridHead, UiKit.matchWrap());
        gridRow1 = UiKit.row(this);
        gridRow2 = UiKit.row(this);
        cellVoltage = UiKit.metric(this, "电压 (V)", "--");
        cellCurrent = UiKit.metric(this, "电流 (A)", "--");
        cellTemp = UiKit.metric(this, "温度 (°C)", "--");
        cellPeak = UiKit.metric(this, "峰值 (W)", "--");
        cellVoltage.value.setTextSize(20f);
        cellCurrent.value.setTextSize(20f);
        cellTemp.value.setTextSize(20f);
        cellPeak.value.setTextSize(20f);
        addFlatCell(gridRow1, cellVoltage);
        addFlatCell(gridRow1, cellCurrent);
        addFlatCell(gridRow2, cellTemp);
        addFlatCell(gridRow2, cellPeak);
        gridCard.addView(gridRow1, UiKit.top(this, UiKit.matchWrap(), 8));
        gridCard.addView(gridRow2, UiKit.matchWrap());
        root.addView(gridCard, UiKit.top(this, UiKit.matchWrap(), 12));

        // 实时功率曲线（最近 2 分钟）
        chartCard = UiKit.card(this);
        LinearLayout chartHead = UiKit.row(this);
        TextView chartTitle = UiKit.text(this, "功率曲线", 14f, R.color.text_primary, true);
        chartHead.addView(chartTitle, UiKit.weighted(1f));
        chartHint = UiKit.text(this, "最近 2 分钟", 11f, R.color.text_muted, false);
        chartHead.addView(chartHint);
        chartCard.addView(chartHead, UiKit.matchWrap());
        chart = new PowerChartView(this);
        chart.setStyle(Prefs.get(this).uiStyle());
        chartCard.addView(chart, UiKit.top(this, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 132)), 6));
        root.addView(chartCard, UiKit.top(this, UiKit.matchWrap(), 12));

        // 系统状态：CPU / 内存 / GPU / 温度（只读，系统不开放时显示 --）
        systemCard = UiKit.card(this);
        systemCard.addView(UiKit.text(this, "系统状态", 14f, R.color.text_primary, true),
                UiKit.matchWrap());
        LinearLayout sysRow1 = UiKit.row(this);
        LinearLayout sysRow2 = UiKit.row(this);
        sysCpu = UiKit.metric(this, "CPU", "--");
        sysRam = UiKit.metric(this, "可用内存", "--");
        sysGpu = UiKit.metric(this, "GPU", "--");
        sysThermal = UiKit.metric(this, "系统温度", "--");
        addCell(sysRow1, sysCpu);
        addCell(sysRow1, sysRam);
        addCell(sysRow2, sysGpu);
        addCell(sysRow2, sysThermal);
        systemCard.addView(sysRow1, UiKit.top(this, UiKit.matchWrap(), 8));
        systemCard.addView(sysRow2, UiKit.matchWrap());
        systemHint = UiKit.textColor(this, "", 11f, getColor(R.color.accent), true);
        systemHint.setMaxLines(2);
        systemHint.setEllipsize(TextUtils.TruncateAt.END);
        systemHint.setPadding(0, UiKit.dp(this, 8), 0, 0);
        systemHint.setOnClickListener(v -> openShizukuForStats());
        systemCard.addView(systemHint, UiKit.matchWrap());
        root.addView(systemCard, UiKit.top(this, UiKit.matchWrap(), 12));

        // 应用耗电排行：主界面直接展示 Top 3，完整列表在独立页
        usageCard = UiKit.card(this);
        LinearLayout usageHead = UiKit.row(this);
        TextView usageTitle = UiKit.text(this, "应用耗电排行", 14f,
                R.color.text_primary, true);
        usageHead.addView(usageTitle, UiKit.weighted(1f));
        TextView usageMore = UiKit.textColor(this, "查看全部", 11.5f,
                getColor(R.color.accent), true);
        usageMore.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 4),
                UiKit.dp(this, 2), UiKit.dp(this, 4));
        usageHead.addView(usageMore);
        usageCard.addView(usageHead, UiKit.matchWrap());
        usageHint = UiKit.text(this, "正在读取应用排行…", 11f,
                R.color.text_muted, false);
        usageHint.setMaxLines(2);
        usageHint.setEllipsize(TextUtils.TruncateAt.END);
        usageCard.addView(usageHint, UiKit.top(this, UiKit.matchWrap(), 2));
        usageList = UiKit.column(this);
        usageCard.addView(usageList, UiKit.top(this, UiKit.matchWrap(), 6));
        usageCard.setOnClickListener(v -> startActivity(
                new Intent(this, AppUsageActivity.class)));
        usageMore.setOnClickListener(v -> startActivity(
                new Intent(this, AppUsageActivity.class)));
        UiKit.setRipple(usageCard);
        root.addView(usageCard, UiKit.top(this, UiKit.matchWrap(), 12));

        // 本次 / 上次充电
        buildSessionCard();
        root.addView(sessionCard, UiKit.top(this, UiKit.matchWrap(), 12));

        // 数据口径
        buildFooterCard();
        root.addView(footerCard, UiKit.top(this, UiKit.matchWrap(), 12));
    }

    private void buildMasterOffCard() {
        masterOffCard = UiKit.column(this);
        masterOffCard.setBackground(UiKit.stroked(this, getColor(R.color.card),
                getColor(R.color.divider), 1f, 20f));
        int p = UiKit.dp(this, 18);
        masterOffCard.setPadding(p, p, p, p);

        TextView title = UiKit.text(this, "实时充电功率显示已关闭", 17f,
                R.color.text_primary, true);
        masterOffCard.addView(title, UiKit.matchWrap());
        TextView sub = UiKit.text(this, "开启后才会采样，并在状态栏常驻显示实时充电功率。", 13f,
                R.color.text_secondary, false);
        sub.setLineSpacing(UiKit.dp(this, 2), 1f);
        masterOffCard.addView(sub, UiKit.top(this, UiKit.matchWrap(), 5));
        TextView button = UiKit.primaryButton(this, "去开启", v -> enableMaster());
        LinearLayout.LayoutParams lp = UiKit.wrapWrap();
        lp.topMargin = UiKit.dp(this, 14);
        lp.height = UiKit.dp(this, 44);
        masterOffCard.addView(button, lp);
    }

    private void buildHeroCard() {
        heroCard = new PowerCardView(this);
        heroCard.setStyle(Prefs.get(this).uiStyle());
        heroCard.setOnClickListener(v -> DetailDialog.show(MainActivity.this,
                StateStore.get().latest()));
        heroCard.setOnLongClickListener(v -> {
            PowerSnapshot latest = StateStore.get().latest();
            if (latest != null) {
                copyText(String.format(java.util.Locale.ROOT, "%.2f W", latest.powerW));
            }
            return true;
        });
    }

    private void buildSessionCard() {
        sessionCard = UiKit.card(this);
        sessionHeading = UiKit.text(this, "暂无充电记录", 15f, R.color.text_primary, true);
        sessionHeading.setSingleLine(true);
        sessionHeading.setEllipsize(TextUtils.TruncateAt.END);
        sessionCard.addView(sessionHeading, UiKit.matchWrap());
        sessionSub = UiKit.text(this, "", 12f, R.color.text_secondary, false);
        sessionSub.setMaxLines(2);
        sessionSub.setEllipsize(TextUtils.TruncateAt.END);
        sessionCard.addView(sessionSub, UiKit.top(this, UiKit.matchWrap(), 3));

        sessionMetrics = UiKit.row(this);
        sessionDuration = UiKit.metric(this, "时长", "--");
        sessionAvg = UiKit.metric(this, "平均功率 (W)", "--");
        sessionPeak = UiKit.metric(this, "平滑峰值 (W)", "--");
        sessionDuration.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sessionAvg.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sessionPeak.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sessionMetrics.addView(sessionDuration.root, UiKit.weighted(1f));
        sessionMetrics.addView(sessionAvg.root, UiKit.weighted(1f));
        sessionMetrics.addView(sessionPeak.root, UiKit.weighted(1f));
        sessionCard.addView(sessionMetrics, UiKit.top(this, UiKit.matchWrap(), 12));
    }

    private void buildFooterCard() {
        footerCard = UiKit.column(this);
        int p = UiKit.dp(this, 14);
        footerCard.setPadding(p, p, p, p);
        footerCard.setBackground(UiKit.rounded(this, getColor(R.color.card_alt), 16f));
        TextView note = UiKit.text(this,
                "功率为进入电池的估算值；边充边用时可能低于充电器实际输出功率。", 11.5f,
                R.color.text_secondary, false);
        note.setLineSpacing(UiKit.dp(this, 1), 1f);
        footerCard.addView(note, UiKit.matchWrap());
        TextView note2 = UiKit.text(this, "数据只在本机处理，无网络权限，不上传任何信息。",
                11.5f, R.color.text_muted, false);
        note2.setLineSpacing(UiKit.dp(this, 1), 1f);
        footerCard.addView(note2, UiKit.top(this, UiKit.matchWrap(), 4));
    }

    private void addFlatCell(LinearLayout row, UiKit.Metric cell) {
        int p = UiKit.dp(this, 6);
        cell.root.setPadding(0, p, 0, p);
        LinearLayout.LayoutParams lp = UiKit.weighted(1f);
        row.addView(cell.root, lp);
    }

    private void setMetricValue(UiKit.Metric metric, String value) {
        if (metric == null) return;
        String next = value == null ? "--" : value;
        if (next.contentEquals(metric.value.getText())) return;
        metric.value.animate().cancel();
        int motion = Prefs.get(this).motionStyle();
        if (motion == 2) {
            metric.value.setAlpha(1f);
            metric.value.setText(next);
            return;
        }
        metric.value.setAlpha(0.5f);
        metric.value.setText(next);
        metric.value.animate().alpha(1f)
                .setDuration(motion == 3 || motion == 1 ? 240 : (motion == 4 ? 200 : 160)).start();
    }

    private void updateHeaderStatus(boolean master, PowerSnapshot s) {
        if (headerStatus == null) return;
        if (!master) {
            headerStatus.setText("已关闭");
        } else if (s == null) {
            headerStatus.setText("读取中");
        } else if (s.state == ChargeState.UNPLUGGED) {
            headerStatus.setText(s.realtime ? "实时" : "待机");
        } else if (s.state == ChargeState.CHARGING) {
            headerStatus.setText("充电中");
        } else if (s.state == ChargeState.FULL) {
            headerStatus.setText("已充满");
        } else if (s.state == ChargeState.PAUSED) {
            headerStatus.setText("已暂停");
        } else {
            headerStatus.setText("读取中");
        }
    }

    private void addCell(LinearLayout row, UiKit.Metric cell) {
        int p = UiKit.dp(this, 14);
        cell.root.setBackground(UiKit.rounded(this, getColor(R.color.card), 16f));
        cell.root.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 12));
        LinearLayout.LayoutParams lp = UiKit.weighted(1f);
        int m = UiKit.dp(this, 5);
        lp.setMargins(m, m, m, m);
        row.addView(cell.root, lp);
    }
}
