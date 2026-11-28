package com.dsh.powermeter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.powermeter.core.AppUsageRanker;
import com.dsh.powermeter.core.PowerSnapshot;
import com.dsh.powermeter.core.PrecisePowerRanker;
import com.dsh.powermeter.core.StateStore;
import com.dsh.powermeter.shizuku.ShizukuBatterySource;
import com.dsh.powermeter.ui.MotionKit;
import com.dsh.powermeter.ui.UiKit;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应用耗电排行：前台使用时长占比 + 当前电池功率的估算耗电。
 *
 * 重要边界：Android 不向普通第三方开放精确的每应用 BatteryStats；
 * 本页不伪造“精确耗电”，只展示使用情况访问能拿到的前台时长，并按占比估算。
 */
public final class AppUsageActivity extends Activity {

    private LinearLayout content;
    private LinearLayout listContainer;
    private TextView summary;
    private TextView stateText;
    private TextView livePill;
    private boolean preciseLoading;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private boolean firstRows = true;
    private boolean refreshing;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshData();
            handler.postDelayed(this, Prefs.get(AppUsageActivity.this).shizukuPreciseEnabled()
                    ? 30000L : 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildShell();
        applyBars();
        refreshData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBars();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyBars() {
        UiKit.styleSystemBars(this, getColor(R.color.bg), !isNight());
    }

    private void buildShell() {
        LinearLayout root = UiKit.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        LinearLayout header = UiKit.row(this);
        header.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6),
                UiKit.dp(this, 16), UiKit.dp(this, 6));
        header.addView(UiKit.backButton(this, v -> finish()));
        TextView title = UiKit.text(this, "应用耗电排行", 20f, R.color.text_primary, true);
        header.addView(title, UiKit.left(this, UiKit.weighted(1f), 2));
        livePill = UiKit.pill(this,
                Prefs.get(this).shizukuPreciseEnabled() ? "精准·30秒" : "每 2 秒",
                getColor(R.color.accent), getColor(R.color.card_alt));
        livePill.setTextSize(10.5f);
        livePill.setMaxWidth(UiKit.dp(this, 118));
        header.addView(livePill);
        root.addView(header, UiKit.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.bg));
        content = UiKit.column(this);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 2),
                UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        UiKit.applyEdgeToEdgeInsets(this, root);

        LinearLayout summaryCard = UiKit.card(this);
        summary = UiKit.text(this, "正在读取使用情况…", 17.5f, R.color.text_primary, true);
        // 汇总可能包含时长 + 当前功率 + 当前前台应用，允许换行，不能单行截断。
        summary.setSingleLine(false);
        summary.setMaxLines(3);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setLineSpacing(UiKit.dp(this, 2), 1f);
        summaryCard.addView(summary, UiKit.matchWrap());
        stateText = UiKit.text(this,
                "仅按前台使用时长估算；不是系统精确的每应用耗电值。",
                12f, R.color.text_secondary, false);
        stateText.setLineSpacing(UiKit.dp(this, 2), 1f);
        summaryCard.addView(stateText, UiKit.top(this, UiKit.matchWrap(), 4));
        content.addView(summaryCard, UiKit.top(this, UiKit.matchWrap(), 14));

        listContainer = UiKit.column(this);
        content.addView(listContainer, UiKit.matchWrap());
    }

    private void refreshData() {
        if (refreshing) return;
        PowerSnapshot snapshot = StateStore.get().latest();
        final double batteryW = snapshot != null ? snapshot.powerW : Double.NaN;
        Prefs prefs = Prefs.get(this);
        if (prefs.shizukuPreciseEnabled()
                && ShizukuBatterySource.isAvailable() && ShizukuBatterySource.hasPermission()) {
            refreshPrecise(batteryW);
            return;
        }
        refreshing = true;
        try {
            AppUsageRanker.Result result = AppUsageRanker.load(this, batteryW);
            render(result, batteryW);
            if (prefs.shizukuPreciseEnabled()) {
                stateText.setText("精准模式需要 Shizuku 授权，当前已回退前台排行；" + stateText.getText());
            }
        } catch (Throwable t) {
            renderError();
        } finally {
            refreshing = false;
        }
    }

    private void refreshPrecise(final double batteryW) {
        if (preciseLoading) return;
        preciseLoading = true;
        if (livePill != null) livePill.setText("精准·30秒");
        summary.setText("正在通过 Shizuku 读取系统 BatteryStats…");
        stateText.setText("只读执行固定命令 dumpsys batterystats；约 30 秒刷新，不是实时。");
        new Thread(() -> {
            String raw = null;
            try {
                raw = ShizukuBatterySource.readBatteryStats();
            } catch (Throwable ignored) {
            }
            final PrecisePowerRanker.Result result =
                    PrecisePowerRanker.parse(this, raw, batteryW);
            runOnUiThread(() -> {
                preciseLoading = false;
                if (isFinishing()) return;
                if (result != null && result.available) {
                    renderPrecise(result);
                } else {
                    // 精准模式失败/ROM 不兼容：自动回退 UsageEvents 前台排行。
                    refreshing = true;
                    try {
                        AppUsageRanker.Result normal = AppUsageRanker.load(
                                AppUsageActivity.this, batteryW);
                        render(normal, batteryW);
                        stateText.setText((result != null && result.message != null
                                ? result.message + "；" : "") + "已回退最近 15 分钟前台排行。");
                    } catch (Throwable ignored) {
                    } finally {
                        refreshing = false;
                    }
                }
            });
        }, "wattlens-batterystats").start();
    }

    private void renderPrecise(PrecisePowerRanker.Result result) {
        listContainer.removeAllViews();
        summary.setText(String.format(Locale.ROOT,
                "系统估算 · 总 %.1f mAh · Shizuku 精准模式", result.totalMah));
        stateText.setText("来自 dumpsys batterystats 的系统估算；约 30 秒刷新，非实时，"
                + "不同 ROM 可能不完整或重置。");
        listContainer.addView(noteCard(), UiKit.top(this, UiKit.matchWrap(), 12));
        int count = Math.min(result.entries.size(), 30);
        for (int i = 0; i < count; i++) {
            listContainer.addView(preciseRow(result.entries.get(i), i + 1),
                    UiKit.top(this, UiKit.matchWrap(), 7));
        }
        if (firstRows) {
            firstRows = false;
            animateIn(listContainer);
        }
    }

    private LinearLayout preciseRow(PrecisePowerRanker.Entry e, int rank) {
        LinearLayout card = UiKit.row(this);
        card.setBackground(UiKit.rounded(this, getColor(R.color.card), 18f));
        int p = UiKit.dp(this, 12);
        card.setPadding(p, UiKit.dp(this, 10), p, UiKit.dp(this, 10));
        TextView rankView = UiKit.textColor(this, String.valueOf(rank), 17f,
                rank <= 3 ? getColor(R.color.accent) : getColor(R.color.text_muted), true);
        rankView.setGravity(Gravity.CENTER);
        card.addView(rankView, new LinearLayout.LayoutParams(
                UiKit.dp(this, 28), UiKit.dp(this, 36)));
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(
                    getPackageManager().getPackagesForUid(e.uid)[0]));
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 34), UiKit.dp(this, 34));
        iconLp.leftMargin = UiKit.dp(this, 4);
        iconLp.rightMargin = UiKit.dp(this, 10);
        card.addView(icon, iconLp);
        LinearLayout texts = UiKit.column(this);
        TextView name = UiKit.text(this, e.label, 14f, R.color.text_primary, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name, UiKit.matchWrap());
        String sub = (e.system ? "系统 · " : "")
                + String.format(Locale.ROOT, "%.2f mAh · %.0f%%", e.mah, e.percent);
        TextView subView = UiKit.text(this, sub, 11f, R.color.text_secondary, false);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subView, UiKit.top(this, UiKit.matchWrap(), 1));
        card.addView(texts, UiKit.weighted(1f));
        TextView right = UiKit.textColor(this,
                PrecisePowerRanker.formatMah(e.mah), 12f,
                rank <= 3 ? getColor(R.color.accent) : getColor(R.color.text_primary), true);
        right.setSingleLine(true);
        right.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(right, UiKit.left(this, UiKit.wrapWrap(), 8));
        return card;
    }

    private void render(AppUsageRanker.Result result, double batteryW) {
        listContainer.removeAllViews();
        if (!result.hasAccess) {
            summary.setText("需要「使用情况访问」权限");
            stateText.setText("Android 不允许普通 App 直接读取精确每应用耗电；"
                    + "授权后才能读取最近 15 分钟前台时长，并按占比估算。");
            listContainer.addView(permissionCard(), UiKit.matchWrap());
            return;
        }
        if (result.entries.isEmpty()) {
            summary.setText("过去 24 小时暂无应用使用记录");
            stateText.setText("最近 15 分钟没有前台应用记录；先正常使用手机再回来看，每 2 秒自动刷新。");
            return;
        }
        String batt = Double.isFinite(batteryW) && batteryW > 0
                ? String.format(Locale.ROOT, " · 当前 %.2f W", batteryW) : "";
        String currentName = (result.currentLabel != null && !result.currentLabel.isEmpty())
                ? result.currentLabel : result.lastLabel;
        String current = currentName == null || currentName.isEmpty()
                ? "" : "\n最近前台：" + currentName;
        summary.setText(String.format(Locale.ROOT, "最近 15 分钟 · 总前台 %s%s%s",
                UiKit.duration(result.totalForegroundMs), batt, current));
        stateText.setText("仅统计最近 15 分钟进入前台的应用；估算 = 当前电池功率 × 前台时长占比，"
                + "不是系统精确每应用耗电。");
        listContainer.addView(noteCard(), UiKit.top(this, UiKit.matchWrap(), 12));

        List<AppUsageRanker.Entry> entries = result.entries;
        int count = Math.min(entries.size(), 30);
        for (int i = 0; i < count; i++) {
            AppUsageRanker.Entry e = entries.get(i);
            listContainer.addView(row(e, i + 1), UiKit.top(this, UiKit.matchWrap(), 7));
        }
        if (entries.size() > 30) {
            TextView more = UiKit.text(this, "仅显示前 30 个应用", 11f,
                    R.color.text_muted, false);
            more.setPadding(0, UiKit.dp(this, 8), 0, 0);
            listContainer.addView(more, UiKit.matchWrap());
        }
        if (firstRows) {
            firstRows = false;
            animateIn(listContainer);
        }
    }

    private LinearLayout noteCard() {
        LinearLayout card = UiKit.column(this);
        card.setBackground(UiKit.rounded(this, getColor(R.color.card_alt), 16f));
        int p = UiKit.dp(this, 14);
        card.setPadding(p, p, p, p);
        TextView t = UiKit.text(this,
                "说明：Android 未向第三方应用开放 BatteryStats 精确每应用耗电。"
                        + "本排行只统计前台使用时长，结合当前电池功率估算；"
                        + "只显示最近 15 分钟前台出现过的应用；系统应用一并列出，不代表后台真实耗电。",
                12f, R.color.text_secondary, false);
        t.setLineSpacing(UiKit.dp(this, 2), 1f);
        card.addView(t, UiKit.matchWrap());
        return card;
    }

    private LinearLayout permissionCard() {
        LinearLayout card = UiKit.column(this);
        card.setBackground(UiKit.rounded(this, getColor(R.color.card), 20f));
        int p = UiKit.dp(this, 18);
        card.setPadding(p, p, p, p);
        TextView title = UiKit.text(this, "开启使用情况访问", 16f,
                R.color.text_primary, true);
        card.addView(title, UiKit.matchWrap());
        TextView sub = UiKit.text(this,
                "页面只读取应用前台时长，不读取聊天内容、不联网。授权后返回本页即可。",
                12.5f, R.color.text_secondary, false);
        sub.setLineSpacing(UiKit.dp(this, 2), 1f);
        card.addView(sub, UiKit.top(this, UiKit.matchWrap(), 4));
        TextView btn = UiKit.primaryButton(this, "去授权", v ->
                AppUsageRanker.openAccessSettings(this));
        LinearLayout.LayoutParams lp = UiKit.matchWrap();
        lp.height = UiKit.dp(this, 44);
        lp.topMargin = UiKit.dp(this, 12);
        card.addView(btn, lp);
        return card;
    }

    private LinearLayout row(AppUsageRanker.Entry e, int rank) {
        LinearLayout card = UiKit.row(this);
        card.setBackground(UiKit.rounded(this, getColor(R.color.card), 18f));
        int p = UiKit.dp(this, 12);
        card.setPadding(p, UiKit.dp(this, 10), p, UiKit.dp(this, 10));

        TextView rankView = UiKit.textColor(this, String.valueOf(rank), 17f,
                rank <= 3 ? getColor(R.color.accent) : getColor(R.color.text_muted), true);
        rankView.setGravity(Gravity.CENTER);
        card.addView(rankView, new LinearLayout.LayoutParams(
                UiKit.dp(this, 28), UiKit.dp(this, 36)));

        ImageView icon = new ImageView(this);
        Drawable d = iconCache.get(e.packageName);
        if (d == null) {
            try {
                d = getPackageManager().getApplicationIcon(e.packageName);
            } catch (Throwable ignored) {
            }
            if (d != null) iconCache.put(e.packageName, d);
        }
        if (d != null) icon.setImageDrawable(d);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 34), UiKit.dp(this, 34));
        iconLp.leftMargin = UiKit.dp(this, 4);
        iconLp.rightMargin = UiKit.dp(this, 10);
        card.addView(icon, iconLp);

        LinearLayout texts = UiKit.column(this);
        TextView name = UiKit.text(this, e.label, 14f, R.color.text_primary, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name, UiKit.matchWrap());
        String sub = (e.system ? "系统 · " : "") + UiKit.duration(e.foregroundMs)
                + String.format(Locale.ROOT, " · 前台 %.0f%%", e.percent);
        TextView subView = UiKit.text(this, sub, 11f, R.color.text_secondary, false);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subView, UiKit.top(this, UiKit.matchWrap(), 1));
        card.addView(texts, UiKit.weighted(1f));

        TextView watts = UiKit.text(this,
                Double.isFinite(e.estimatedW) && e.estimatedW > 0
                        ? String.format(Locale.ROOT, "≈%.2f W", e.estimatedW) : "--",
                13f, rank <= 3 ? R.color.accent : R.color.text_primary, true);
        watts.setSingleLine(true);
        watts.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(watts, UiKit.left(this, UiKit.wrapWrap(), 8));
        return card;
    }

    private void animateIn(LinearLayout parent) {
        int motion = Prefs.get(this).motionStyle();
        if (motion == 2) return;
        int step = UiKit.dp(this, 8);
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            MotionKit.enterCard(parent.getChildAt(i), i * 30, step, 300, motion);
        }
    }

    private void renderError() {
        listContainer.removeAllViews();
        summary.setText("读取使用情况失败");
        stateText.setText("请确认已授予「使用情况访问」权限；不同 ROM 设置路径可能不同。");
        listContainer.addView(permissionCard(), UiKit.matchWrap());
    }
}
