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

import com.dsh.powermeter.core.ChargeEngine;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.SessionTracker;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.service.PowerService;
import com.dsh.powermeter.ui.DetailDialog;
import com.dsh.powermeter.ui.PowerCardView;
import com.dsh.powermeter.ui.UiKit;

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
    private TextView pillState;
    private TextView pillTier;
    private TextView pillMode;
    private TextView heroNumber;
    private TextView heroPlaceholder;
    private TextView heroPlaceholderSub;
    private TextView heroNote;
    private ProgressBar levelBar;
    private TextView levelText;
    private TextView heroTemp;
    private TextView heroEta;
    private TextView heroSource;

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
            render(StateStore.get().latest());
        } catch (Throwable t) {
            showSafeFallback(t);
        }
    }

    @Override
    protected void onStop() {
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

    private void render(PowerSnapshot snapshot) {
        Prefs prefs = Prefs.get(this);
        boolean master = prefs.masterEnabled();

        masterOffCard.setVisibility(master ? View.GONE : View.VISIBLE);
        heroCard.setVisibility(master ? View.VISIBLE : View.GONE);
        gridTitle.setVisibility(master ? View.VISIBLE : View.GONE);
        gridRow1.setVisibility(master ? View.VISIBLE : View.GONE);
        gridRow2.setVisibility(master ? View.VISIBLE : View.GONE);
        sessionCard.setVisibility(master ? View.VISIBLE : View.GONE);
        footerCard.setVisibility(master ? View.VISIBLE : View.GONE);
        updateBanner();
        if (!master) return;

        renderHero(snapshot);
        renderGrid(snapshot);
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
        cellVoltage.value.setText(s == null || hideForIdle ? "--" : UiKit.volts(s.voltageV));
        cellCurrent.value.setText(s == null || hideForIdle ? "--" : UiKit.amps(s.currentA));
        cellTemp.value.setText(s == null || hideForIdle ? "--" : UiKit.temp(s.temperatureC));
        cellPeak.value.setText(s == null || hideForIdle ? "--" : UiKit.watts(s.peakW));
    }

    private void renderSession(PowerSnapshot s) {
        Prefs prefs = Prefs.get(this);
        if (s != null && s.sessionActive) {
            sessionHeading.setText("本次充电");
            sessionSub.setText("进行中 · 已充 " + UiKit.duration(s.sessionDurationMs));
            sessionMetrics.setVisibility(View.VISIBLE);
            sessionDuration.value.setText(compactDuration(s.sessionDurationMs));
            sessionAvg.value.setText(UiKit.watts(s.sessionAvgW));
            sessionPeak.value.setText(UiKit.watts(s.sessionPeakW));
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
            sessionDuration.value.setText(compactDuration(durationMs));
            sessionAvg.value.setText(avgW > 0 ? UiKit.watts(avgW) : "--");
            sessionPeak.value.setText(peakW > 0 ? UiKit.watts(peakW) : "--");
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
            title.setText("充电功率 Pro 启动异常");
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
        TextView title = UiKit.text(this, "充电功率 Pro", 24f, R.color.text_primary, true);
        header.addView(title, UiKit.weighted(1f));
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

        // 四宫格指标
        gridTitle = UiKit.section(this, "实时指标");
        gridRow1 = UiKit.row(this);
        gridRow2 = UiKit.row(this);
        cellVoltage = UiKit.metric(this, "电压 (V)", "--");
        cellCurrent = UiKit.metric(this, "电流 (A)", "--");
        cellTemp = UiKit.metric(this, "温度 (°C)", "--");
        cellPeak = UiKit.metric(this, "平滑峰值 (W)", "--");
        addCell(gridRow1, cellVoltage);
        addCell(gridRow1, cellCurrent);
        addCell(gridRow2, cellTemp);
        addCell(gridRow2, cellPeak);
        root.addView(gridTitle, UiKit.matchWrap());
        root.addView(gridRow1, UiKit.matchWrap());
        root.addView(gridRow2, UiKit.matchWrap());

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
        heroCard.setOnClickListener(v -> DetailDialog.show(MainActivity.this,
                StateStore.get().latest()));
    }

    private void buildSessionCard() {
        sessionCard = UiKit.card(this);
        sessionHeading = UiKit.text(this, "暂无充电记录", 15f, R.color.text_primary, true);
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

    private void addCell(LinearLayout row, UiKit.Metric cell) {
        int p = UiKit.dp(this, 14);
        cell.root.setBackground(UiKit.rounded(this, getColor(R.color.card), 16f));
        cell.root.setPadding(p, UiKit.dp(this, 12), p, UiKit.dp(this, 12));
        LinearLayout.LayoutParams lp = UiKit.weighted(1f);
        int m = UiKit.dp(this, 5);
        lp.setMargins(m, m, m, m);
        row.addView(cell.root, lp);
    }

    private void setHeroPower(String value) {
        SpannableStringBuilder builder = new SpannableStringBuilder(value + " W");
        int start = value.length();
        int end = builder.length();
        builder.setSpan(new RelativeSizeSpan(0.36f), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(0xCCFFFFFF), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroNumber.setText(builder);
    }
}
