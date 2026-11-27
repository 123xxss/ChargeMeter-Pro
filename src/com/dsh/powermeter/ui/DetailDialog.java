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

import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.R;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.core.SystemStats;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「详情」弹窗：实时展示电池读数、系统状态、数据来源与口径说明。
 *
 * 弹窗打开期间订阅 StateStore，每秒跟随主界面刷新；关闭时立即反注册。
 * 固定口径：这里始终是进入电池的功率，不宣称、不标注“充电器输入功率”。
 */
public final class DetailDialog {

    private DetailDialog() {
    }

    public static void show(Activity activity, PowerSnapshot initial) {
        if (activity == null || activity.isFinishing()) return;

        Context c = activity;
        int accent = c.getColor(R.color.accent);
        int textPrimary = c.getColor(R.color.text_primary);
        int textSecondary = c.getColor(R.color.text_secondary);
        int cardAlt = c.getColor(R.color.card_alt);

        LinearLayout card = UiKit.column(c);
        card.setBackground(UiKit.rounded(c, c.getColor(R.color.card), 24f));
        int pad = UiKit.dp(c, 20);
        card.setPadding(pad, pad, pad, pad);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout titleRow = UiKit.row(c);
        TextView title = UiKit.text(c, "详情 · 实时刷新中", 17f, R.color.text_primary, true);
        titleRow.addView(title, UiKit.weighted(1f));
        TextView closeTop = UiKit.textColor(c, "关闭", 14f, accent, true);
        int cp = UiKit.dp(c, 8);
        closeTop.setPadding(cp, UiKit.dp(c, 4), cp, UiKit.dp(c, 4));
        closeTop.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(closeTop);
        card.addView(titleRow, UiKit.matchWrap());

        Map<String, TextView> rows = new LinkedHashMap<>();
        addRow(c, card, rows, "实时功率");
        addRow(c, card, rows, "电压");
        addRow(c, card, rows, "电流");
        addRow(c, card, rows, "电池温度");
        addRow(c, card, rows, "平滑峰值");
        addRow(c, card, rows, "剩余时间");
        addRow(c, card, rows, "当前充电模式");
        addRow(c, card, rows, "充电状态");
        addRow(c, card, rows, "充电档位");
        addRow(c, card, rows, "电量");
        addRow(c, card, rows, "数据来源");
        addRow(c, card, rows, "电芯数");
        addRow(c, card, rows, "电芯数设置");
        addRow(c, card, rows, "CPU");
        addRow(c, card, rows, "内存");
        addRow(c, card, rows, "GPU");
        addRow(c, card, rows, "系统温度");
        addRow(c, card, rows, "存储");

        // 口径说明
        LinearLayout notes = UiKit.column(c);
        notes.setBackground(UiKit.rounded(c, cardAlt, 14f));
        int np = UiKit.dp(c, 12);
        notes.setPadding(np, np, np, np);
        addNote(c, notes, "此处为进入电池的功率；边充边用时可能低于充电器实际输出功率。");
        addNote(c, notes, "CPU/内存/存储来自系统公开接口；GPU/系统温度读取标准 sysfs，部分机型不开放，显示 -- 属正常。");
        addNote(c, notes, "所有数据仅在本机处理，不上传、不联网。");
        card.addView(notes, UiKit.top(c, UiKit.matchWrap(), 16));

        TextView close = UiKit.primaryButton(c, "关闭", v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = UiKit.matchWrap();
        closeLp.height = UiKit.dp(c, 46);
        closeLp.topMargin = UiKit.dp(c, 16);
        card.addView(close, closeLp);

        ScrollView scroll = new ScrollView(c);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        int margin = UiKit.dp(c, 20);
        scroll.setPadding(margin, margin, margin, margin);
        scroll.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);

        // 实时刷新：每帧直接更新行文本，不重建弹窗。
        updateRows(c, rows, initial != null ? initial : StateStore.get().latest());
        StateStore.Listener listener = snapshot -> {
            try {
                updateRows(c, rows, snapshot);
            } catch (Throwable ignored) {
            }
        };
        StateStore.get().addListener(listener);
        dialog.setOnDismissListener(d -> StateStore.get().removeListener(listener));

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            int height = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.86f);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height);
        }
    }

    private static void addRow(Context c, LinearLayout parent, Map<String, TextView> rows,
                               String label) {
        LinearLayout row = UiKit.row(c);
        TextView l = UiKit.text(c, label, 13f, R.color.text_secondary, false);
        row.addView(l, UiKit.weighted(1f));
        TextView v = UiKit.text(c, "--", 13.5f, R.color.text_primary, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (c.getResources().getDisplayMetrics().widthPixels * 0.54f));
        row.addView(v, UiKit.left(c, UiKit.wrapWrap(), 10));
        parent.addView(row, UiKit.top(c, UiKit.matchWrap(), 9));
        rows.put(label, v);
    }

    private static void updateRows(Context c, Map<String, TextView> rows, PowerSnapshot s) {
        Prefs prefs = Prefs.get(c);
        boolean has = s != null;
        boolean showTemp = has ? s.showTemperature : prefs.showTemperature();
        boolean showEta = has ? s.showEstimate : prefs.showEstimate();
        boolean showTier = has ? s.showTier : prefs.showTier();

        if (!has) {
            for (TextView v : rows.values()) v.setText("--");
            return;
        }

        set(rows, "实时功率", powerText(s));
        set(rows, "电压", UiKit.volts(s.voltageV) + " V"
                + (s.voltageCorrected ? " · 2 芯合并" : ""));
        set(rows, "电流", UiKit.amps(s.currentA) + " A");
        set(rows, "电池温度", showTemp ? UiKit.temp(s.temperatureC) + " °C" : "已隐藏");
        set(rows, "平滑峰值", UiKit.watts(s.peakW) + " W");
        set(rows, "剩余时间", showEta ? formatRemaining(s) : "已隐藏");
        set(rows, "当前充电模式", safe(s.modeLabel));
        set(rows, "充电状态", s.state == null ? "--" : s.state.label);
        set(rows, "充电档位", showTier
                ? (s.tier == null ? "--" : s.tier.label) : "已隐藏");
        set(rows, "电量", s.level >= 0 ? s.level + "%" : "--");
        set(rows, "数据来源", safe(s.sourceLabel));
        set(rows, "电芯数", s.cells + " 芯"
                + (s.voltageCorrected ? "（按 2 芯合并）" : ""));
        set(rows, "电芯数设置", cellCountLabel(prefs));

        SystemStats st = s.systemStats;
        if (st == null) {
            set(rows, "CPU", "--");
            set(rows, "内存", "--");
            set(rows, "GPU", "--");
            set(rows, "系统温度", "--");
            set(rows, "存储", "--");
        } else {
            set(rows, "CPU", st.cpuSummary() + (st.cpuCores > 0 ? " · " + st.cpuCores + " 核" : ""));
            set(rows, "内存", st.ramSummary()
                    + (st.ramTotalMb > 0
                    ? String.format(java.util.Locale.ROOT, " · 已用 %.0f%%", st.ramUsedPercent) : ""));
            set(rows, "GPU", Double.isNaN(st.gpuPercent) ? "系统未开放" : st.gpuSummary());
            set(rows, "系统温度", Double.isNaN(st.thermalMaxC) ? "系统未开放" : st.thermalSummary());
            set(rows, "存储", st.storageSummary());
        }
    }

    private static String powerText(PowerSnapshot s) {
        if (s.realtime && s.nearZero) return "接近零（" + safe(s.modeLabel) + "）";
        if (!s.valid) return "暂不可用";
        return UiKit.watts(s.powerW) + " W";
    }

    private static void set(Map<String, TextView> rows, String key, String value) {
        TextView v = rows.get(key);
        if (v != null) v.setText(value == null ? "--" : value);
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
