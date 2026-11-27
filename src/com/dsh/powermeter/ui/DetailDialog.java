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

import com.dsh.powermeter.BrandCompat;
import com.dsh.powermeter.Prefs;
import com.dsh.powermeter.R;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;

/**
 * 「详情」弹窗：展示实时读数、数据来源与口径说明。
 *
 * 固定口径：这里始终是进入电池的功率估算值，不宣称、不标注任何“充电器输入功率”。
 * 电芯数与 ×2 修正说明按本次快照来源动态给出。
 */
public final class DetailDialog {

    private DetailDialog() {
    }

    public static void show(Activity activity, PowerSnapshot snapshot) {
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

        // 标题行
        LinearLayout titleRow = UiKit.row(c);
        TextView title = UiKit.text(c, "详情", 18f, R.color.text_primary, true);
        titleRow.addView(title, UiKit.weighted(1f));
        TextView closeTop = UiKit.textColor(c, "关闭", 14f, accent, true);
        int cp = UiKit.dp(c, 8);
        closeTop.setPadding(cp, UiKit.dp(c, 4), cp, UiKit.dp(c, 4));
        closeTop.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(closeTop);
        card.addView(titleRow, UiKit.matchWrap());

        // 数据行
        boolean hasSnapshot = snapshot != null;
        boolean showTemp = hasSnapshot ? snapshot.showTemperature : Prefs.get(c).showTemperature();
        boolean showEta = hasSnapshot ? snapshot.showEstimate : Prefs.get(c).showEstimate();
        boolean showTier = hasSnapshot ? snapshot.showTier : Prefs.get(c).showTier();

        String powerValue;
        if (!hasSnapshot) {
            powerValue = "--";
        } else if (snapshot.realtime && snapshot.nearZero) {
            powerValue = "接近零（" + safe(snapshot.modeLabel) + "）";
        } else if (!snapshot.valid) {
            powerValue = "暂不可用";
        } else {
            powerValue = UiKit.watts(snapshot.powerW) + " W";
        }

        addRow(c, card, "实时功率", powerValue);
        addRow(c, card, "电压", hasSnapshot
                ? UiKit.volts(snapshot.voltageV) + " V"
                + (snapshot.voltageCorrected ? "（已 ×2 修正）" : "")
                : "--");
        addRow(c, card, "电流", hasSnapshot ? UiKit.amps(snapshot.currentA) + " A" : "--");
        addRow(c, card, "温度", showTemp
                ? (hasSnapshot ? UiKit.temp(snapshot.temperatureC) + " °C" : "--")
                : "已隐藏");
        addRow(c, card, "平滑峰值", hasSnapshot ? UiKit.watts(snapshot.peakW) + " W" : "--");
        addRow(c, card, "剩余时间", showEta ? formatRemaining(snapshot) : "已隐藏");
        addRow(c, card, "当前充电模式", hasSnapshot ? safe(snapshot.modeLabel) : "--");
        addRow(c, card, "充电状态", hasSnapshot && snapshot.state != null
                ? snapshot.state.label : "--");
        addRow(c, card, "充电档位", showTier
                ? (hasSnapshot && snapshot.tier != null ? snapshot.tier.label : "--")
                : "已隐藏");
        addRow(c, card, "电量", hasSnapshot && snapshot.level >= 0
                ? snapshot.level + "%" : "--");
        addRow(c, card, "数据来源", hasSnapshot ? safe(snapshot.sourceLabel) : "--");
        addRow(c, card, "电芯数", hasSnapshot
                ? snapshot.cells + " 芯" + (snapshot.voltageCorrected ? "（电压按 ×2 修正）" : "")
                : "--");
        addRow(c, card, "电芯数设置", cellCountLabel(Prefs.get(c)));

        // 口径说明
        LinearLayout notes = UiKit.column(c);
        notes.setBackground(UiKit.rounded(c, cardAlt, 14f));
        int np = UiKit.dp(c, 12);
        notes.setPadding(np, np, np, np);
        addNote(c, notes, "此处为进入电池的功率；边充边用时可能低于充电器实际输出功率。");
        if (hasSnapshot && (snapshot.cells > 1 || snapshot.voltageCorrected)) {
            addNote(c, notes, "本机为双电芯机型：电压按 2 芯合并估算总功率。");
        }
        if (hasSnapshot && snapshot.enhanced) {
            addNote(c, notes, "当前使用增强读取（实验性）：仅通过系统标准 sysfs 只读读取，部分机型可能不可用。");
        }
        if (!showTemp || !showEta || !showTier) {
            addNote(c, notes, "部分项目已在设置中隐藏，可到设置中重新开启。");
        }
        addNote(c, notes, "功率为估算值，会随温度、电量与厂商充电策略波动。所有数据仅在本机处理，不上传。");
        card.addView(notes, UiKit.top(c, UiKit.matchWrap(), 16));

        // 底部关闭按钮
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

    private static void addRow(Context c, LinearLayout parent, String label, String value) {
        LinearLayout row = UiKit.row(c);
        TextView l = UiKit.text(c, label, 13f, R.color.text_secondary, false);
        row.addView(l, UiKit.weighted(1f));
        TextView v = UiKit.text(c, value, 13.5f, R.color.text_primary, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth((int) (c.getResources().getDisplayMetrics().widthPixels * 0.52f));
        row.addView(v, UiKit.left(c, UiKit.wrapWrap(), 10));
        parent.addView(row, UiKit.top(c, UiKit.matchWrap(), 9));
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
