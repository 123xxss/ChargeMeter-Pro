package com.dsh.powermeter.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.R;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.DeviceInfo;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.core.SystemStats;
import com.dsh.powermeter.shizuku.ShizukuBatterySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 详情页：实时功率大卡 + 充电数据 / 系统状态 / 设备信息分组卡片。
 *
 * 打开期间订阅 StateStore 每秒刷新；关闭时立即反注册。
 * 全部使用程序化 View，不新增布局资源。
 */
public final class DetailDialog {

    private DetailDialog() {
    }

    public static void show(Activity activity, PowerSnapshot initial) {
        if (activity == null || activity.isFinishing()) return;
        Context c = activity;
        int accent = c.getColor(R.color.accent);
        int cardAlt = c.getColor(R.color.card_alt);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout page = UiKit.column(c);
        int margin = UiKit.dp(c, 16);
        page.setPadding(margin, UiKit.dp(c, 6), margin, UiKit.dp(c, 10));

        // 顶部：只保留实时状态和完成，不再出现“详情”大标题；整张弹窗用圆角背景包住。
        LinearLayout header = UiKit.row(c);
        TextView live = UiKit.pill(c, "● 实时", accent, cardAlt);
        live.setTextSize(11f);
        header.addView(live);
        TextView closeTop = UiKit.textColor(c, "完成", 14f, accent, true);
        int cp = UiKit.dp(c, 10);
        closeTop.setPadding(cp, UiKit.dp(c, 6), cp, UiKit.dp(c, 6));
        closeTop.setOnClickListener(v -> dialog.dismiss());
        header.addView(new android.view.View(c), UiKit.weighted(1f));
        header.addView(closeTop, UiKit.left(c, UiKit.wrapWrap(), 4));
        page.addView(header, UiKit.matchWrap());

        // 实时功率主卡
        LinearLayout hero = UiKit.card(c);
        hero.setBackground(UiKit.rounded(c, c.getColor(R.color.card), 24f));
        TextView heroLabel = UiKit.text(c, "实时入电池功率", 12f, R.color.text_secondary, true);
        hero.addView(heroLabel, UiKit.matchWrap());
        LinearLayout heroLine = UiKit.row(c);
        TextView heroPower = UiKit.textColor(c, "--", 40f, accent, true);
        heroPower.setSingleLine(true);
        heroPower.setEllipsize(TextUtils.TruncateAt.END);
        heroLine.addView(heroPower, UiKit.weighted(1f));
        TextView heroState = UiKit.pill(c, "读取中", accent, cardAlt);
        heroLine.addView(heroState, UiKit.left(c, UiKit.wrapWrap(), 8));
        hero.addView(heroLine, UiKit.top(c, UiKit.matchWrap(), 2));
        TextView heroSub = UiKit.text(c, "正在读取电池数据", 12f, R.color.text_secondary, false);
        heroSub.setSingleLine(true);
        heroSub.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroSub, UiKit.top(c, UiKit.matchWrap(), 2));
        page.addView(hero, UiKit.top(c, UiKit.matchWrap(), 12));

        // 充电数据
        page.addView(UiKit.section(c, "充电数据"), UiKit.matchWrap());
        LinearLayout chargeCard = UiKit.card(c);
        Map<String, TextView> rows = new LinkedHashMap<>();
        addRow(c, chargeCard, rows, "电压");
        addRow(c, chargeCard, rows, "电流");
        addRow(c, chargeCard, rows, "电池温度");
        addRow(c, chargeCard, rows, "平滑峰值");
        addRow(c, chargeCard, rows, "剩余时间");
        addRow(c, chargeCard, rows, "充电档位");
        addRow(c, chargeCard, rows, "电量");
        addRow(c, chargeCard, rows, "数据来源");
        addRow(c, chargeCard, rows, "电芯数");
        addRow(c, chargeCard, rows, "电芯数设置");
        page.addView(chargeCard, UiKit.matchWrap());

        // 系统状态
        page.addView(UiKit.section(c, "系统状态"), UiKit.matchWrap());
        LinearLayout sysCard = UiKit.card(c);
        addRow(c, sysCard, rows, "CPU");
        addRow(c, sysCard, rows, "内存");
        addRow(c, sysCard, rows, "GPU");
        addRow(c, sysCard, rows, "系统温度");
        addRow(c, sysCard, rows, "存储");
        TextView shizukuHint = UiKit.textColor(c,
                "CPU/GPU/系统温度未开放？点此用 Shizuku 尝试读取", 11.5f, accent, true);
        shizukuHint.setMaxLines(2);
        shizukuHint.setEllipsize(TextUtils.TruncateAt.END);
        shizukuHint.setPadding(0, UiKit.dp(c, 10), 0, 0);
        shizukuHint.setOnClickListener(v -> requestShizuku(activity, c));
        sysCard.addView(shizukuHint, UiKit.matchWrap());
        page.addView(sysCard, UiKit.matchWrap());

        // 设备信息
        page.addView(UiKit.section(c, "设备信息"), UiKit.matchWrap());
        LinearLayout deviceCard = UiKit.card(c);
        addRow(c, deviceCard, rows, "厂商");
        addRow(c, deviceCard, rows, "品牌");
        addRow(c, deviceCard, rows, "型号");
        addRow(c, deviceCard, rows, "设备代号");
        addRow(c, deviceCard, rows, "Android 版本");
        addRow(c, deviceCard, rows, "ROM 版本");
        addRow(c, deviceCard, rows, "ABI");
        page.addView(deviceCard, UiKit.matchWrap());

        // 说明
        LinearLayout notes = UiKit.column(c);
        notes.setBackground(UiKit.rounded(c, cardAlt, 16f));
        int np = UiKit.dp(c, 14);
        notes.setPadding(np, np, np, np);
        addNote(c, notes, "功率为进入电池的估算值，边充边用可能低于充电器实际输出功率。");
        addNote(c, notes, "CPU/内存/存储来自系统公开接口；GPU/系统温度部分机型只允许 Shizuku 读取。");
        addNote(c, notes, DeviceInfo.compatibilityNote());
        page.addView(notes, UiKit.top(c, UiKit.matchWrap(), 16));

        TextView close = UiKit.primaryButton(c, "关闭", v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = UiKit.matchWrap();
        closeLp.height = UiKit.dp(c, 46);
        closeLp.topMargin = UiKit.dp(c, 14);
        page.addView(close, closeLp);

        ScrollView scroll = new ScrollView(c);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        // 关键：整张弹窗有一块实心圆角底板，阻断下层主页内容透出，避免截图里
        // “详情”与底层充电中标签/曲线叠在一起。
        scroll.setBackground(UiKit.rounded(c, c.getColor(R.color.bg), 32f));
        scroll.setClipToOutline(true);
        scroll.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);

        updateAll(c, rows, heroPower, heroSub, heroState, shizukuHint,
                initial != null ? initial : StateStore.get().latest());
        StateStore.Listener listener = snapshot -> {
            try {
                updateAll(c, rows, heroPower, heroSub, heroState, shizukuHint, snapshot);
            } catch (Throwable ignored) {
            }
        };
        StateStore.get().addListener(listener);
        dialog.setOnDismissListener(d -> StateStore.get().removeListener(listener));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.5f);
            window.setWindowAnimations(Prefs.get(c).motionStyle() == 2
                    ? R.style.DialogAnimationNone : R.style.DialogAnimation);
            int height = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.80f);
            int width = c.getResources().getDisplayMetrics().widthPixels - UiKit.dp(c, 24);
            window.setGravity(android.view.Gravity.CENTER);
            window.setLayout(width, height);
            window.getDecorView().setPadding(0, UiKit.dp(c, 8), 0, UiKit.dp(c, 8));
        }
        dialog.show();
        if (window != null) {
            int height = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.80f);
            int width = c.getResources().getDisplayMetrics().widthPixels - UiKit.dp(c, 24);
            window.setLayout(width, height);
        }
        animateSections(page, 1); // 跳过顶部状态栏，从功率大卡开始
    }

    private static void addRow(Context c, LinearLayout parent, Map<String, TextView> rows,
                               String label) {
        LinearLayout row = UiKit.row(c);
        TextView l = UiKit.text(c, label, 13f, R.color.text_secondary, false);
        row.addView(l, UiKit.weighted(1f));
        TextView v = UiKit.text(c, "--", 13.5f, R.color.text_primary, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (c.getResources().getDisplayMetrics().widthPixels * 0.56f));
        row.addView(v, UiKit.left(c, UiKit.wrapWrap(), 10));
        parent.addView(row, UiKit.top(c, UiKit.matchWrap(), 10));
        rows.put(label, v);
    }

    private static void updateAll(Context c, Map<String, TextView> rows,
                                  TextView heroPower, TextView heroSub, TextView heroState,
                                  TextView shizukuHint, PowerSnapshot s) {
        Prefs prefs = Prefs.get(c);
        setDeviceRows(rows);

        if (s == null) {
            heroPower.setText("--");
            heroSub.setText("正在读取电池数据");
            heroState.setText("读取中");
            for (Map.Entry<String, TextView> e : rows.entrySet()) {
                if (!isDeviceRow(e.getKey())) setText(e.getValue(), "--");
            }
            shizukuHint.setVisibility(View.GONE);
            return;
        }

        String main = powerText(s);
        setText(heroPower, main);
        heroState.setText(s.state == null ? "读取中" : s.state.label);
        heroSub.setText((s.modeLabel == null ? "" : s.modeLabel)
                + " · " + (s.sourceLabel == null ? "标准读取" : s.sourceLabel));

        boolean showTemp = s.showTemperature;
        boolean showEta = s.showEstimate;
        boolean showTier = s.showTier;
        set(rows, "电压", UiKit.volts(s.voltageV) + " V"
                + (s.voltageCorrected ? " · 2 芯合并" : ""));
        set(rows, "电流", UiKit.amps(s.currentA) + " A");
        set(rows, "电池温度", showTemp ? UiKit.temp(s.temperatureC) + " °C" : "已隐藏");
        set(rows, "平滑峰值", UiKit.watts(s.peakW) + " W");
        set(rows, "剩余时间", showEta ? formatRemaining(s) : "已隐藏");
        set(rows, "充电档位", showTier ? (s.tier == null ? "--" : s.tier.label) : "已隐藏");
        set(rows, "电量", s.level >= 0 ? s.level + "%" : "--");
        set(rows, "数据来源", safe(s.sourceLabel));
        set(rows, "电芯数", s.cells + " 芯" + (s.voltageCorrected ? "（按 2 芯合并）" : ""));
        set(rows, "电芯数设置", cellCountLabel(prefs));

        SystemStats st = s.systemStats;
        if (st == null) {
            set(rows, "CPU", "--");
            set(rows, "内存", "--");
            set(rows, "GPU", "--");
            set(rows, "系统温度", "--");
            set(rows, "存储", "--");
        } else {
            set(rows, "CPU", st.cpuSummary()
                    + (st.cpuCores > 0 ? " · " + st.cpuCores + " 核" : ""));
            set(rows, "内存", st.ramSummary() + (st.ramTotalMb > 0
                    ? String.format(java.util.Locale.ROOT, " · 已用 %.0f%%", st.ramUsedPercent)
                    : ""));
            set(rows, "GPU", Double.isNaN(st.gpuPercent) ? "系统未开放" : st.gpuSummary());
            set(rows, "系统温度", Double.isNaN(st.thermalMaxC) ? "系统未开放" : st.thermalSummary());
            set(rows, "存储", st.storageSummary());
        }

        boolean unavailable = st == null || Double.isNaN(st.cpuPercent)
                || Double.isNaN(st.gpuPercent) || Double.isNaN(st.thermalMaxC);
        shizukuHint.setVisibility(unavailable ? View.VISIBLE : View.GONE);
    }

    private static void setDeviceRows(Map<String, TextView> rows) {
        set(rows, "厂商", DeviceInfo.manufacturer());
        set(rows, "品牌", DeviceInfo.brand());
        set(rows, "型号", DeviceInfo.model());
        set(rows, "设备代号", DeviceInfo.device());
        set(rows, "Android 版本", DeviceInfo.androidVersion());
        set(rows, "ROM 版本", DeviceInfo.romVersion());
        set(rows, "ABI", DeviceInfo.abi());
    }

    private static boolean isDeviceRow(String key) {
        return "厂商".equals(key) || "品牌".equals(key) || "型号".equals(key)
                || "设备代号".equals(key) || "Android 版本".equals(key)
                || "ROM 版本".equals(key) || "ABI".equals(key);
    }

    private static void requestShizuku(Activity activity, Context c) {
        try {
            if (!ShizukuBatterySource.isAvailable()) {
                Toast.makeText(c, "未检测到 Shizuku，请先安装并启动 Shizuku",
                        Toast.LENGTH_LONG).show();
            } else if (!ShizukuBatterySource.hasPermission()) {
                ShizukuBatterySource.requestPermission(activity);
                Toast.makeText(c, "请在 Shizuku 弹窗中允许本应用",
                        Toast.LENGTH_LONG).show();
            } else {
                Prefs.get(c).setShizukuReadEnabled(true);
                ShizukuBatterySource.bindIfPossible();
                Toast.makeText(c, "正在用 Shizuku 读取系统状态",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void animateSections(LinearLayout page, int from) {
        Context c = page.getContext();
        int motion = Prefs.get(c).motionStyle();
        if (motion == 2) return;
        boolean rich = motion == 1;
        boolean vivo = motion == 3;
        boolean ios = motion == 4;
        int step = UiKit.dp(c, vivo ? 18 : (rich ? 16 : 12));
        int delay = vivo ? 40 : (rich ? 34 : 22);
        int duration = vivo ? 320 : (rich ? 300 : (ios ? 250 : 200));
        for (int i = from; i < page.getChildCount(); i++) {
            View v = page.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(step);
            android.view.ViewPropertyAnimator anim = v.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay((i - from) * (long) delay);
            if (vivo) anim.setInterpolator(new android.view.animation.OvershootInterpolator(0.7f));
            if (ios) anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.setDuration(duration).start();
        }
    }

    private static String powerText(PowerSnapshot s) {
        if (s.realtime && s.nearZero) return "接近零";
        if (!s.valid) return "暂不可用";
        return UiKit.watts(s.powerW) + " W";
    }

    private static void set(Map<String, TextView> rows, String key, String value) {
        TextView v = rows.get(key);
        if (v != null) setText(v, value);
    }

    /** 数值变化时轻微淡入，避免每秒刷新造成生硬跳变。 */
    private static void setText(TextView v, String value) {
        if (v == null) return;
        String next = value == null ? "--" : value;
        if (next.contentEquals(v.getText())) return;
        v.animate().cancel();
        int motion = Prefs.get(v.getContext()).motionStyle();
        if (motion == 2) {
            v.setAlpha(1f);
            v.setText(next);
            return;
        }
        v.setAlpha(0.55f);
        v.setText(next);
        v.animate().alpha(1f).setDuration(motion == 1 ? 240 : 160).start();
    }

    private static void addNote(Context c, LinearLayout parent, String text) {
        TextView t = UiKit.textColor(c, text, 12f, c.getColor(R.color.text_secondary), false);
        t.setLineSpacing(UiKit.dp(c, 2), 1f);
        parent.addView(t, UiKit.top(c, UiKit.matchWrap(), 6));
    }

    private static String formatRemaining(PowerSnapshot s) {
        if (s == null) return "--";
        if (s.state == ChargeState.FULL) return "已充满";
        if (s.estimateValid && s.remainingSeconds > 0) {
            return "约 " + UiKit.duration(s.remainingSeconds * 1000L);
        }
        if (s.state == ChargeState.UNPLUGGED) return "--";
        return "估算中";
    }

    private static String cellCountLabel(Prefs prefs) {
        int override = prefs.cellCountOverride();
        if (override > 0) return override + " 芯（手动）";
        return "自动（推荐 " + com.dsh.powermeter.BrandCompat.recommendedCellCount() + " 芯）";
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "--" : value;
    }
}
