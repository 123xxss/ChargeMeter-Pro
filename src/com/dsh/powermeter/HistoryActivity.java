package com.dsh.powermeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.powermeter.core.HistoryDb;
import com.dsh.powermeter.ui.UiKit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 本地充电历史列表；只读取本机 SQLite，不联网、不导出。 */
public final class HistoryActivity extends Activity {

    private LinearLayout list;
    private TextView title;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildShell();
        applyBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBars();
        render();
    }

    private void applyBars() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        UiKit.styleSystemBars(this, getColor(R.color.bg), !night);
    }

    private void buildShell() {
        LinearLayout root = UiKit.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        LinearLayout header = UiKit.row(this);
        header.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6),
                UiKit.dp(this, 16), UiKit.dp(this, 6));
        header.addView(UiKit.backButton(this, v -> finish()));
        title = UiKit.text(this, "充电历史", 20f, R.color.text_primary, true);
        header.addView(title, UiKit.left(this, UiKit.weighted(1f), 2));
        TextView clear = UiKit.textColor(this, "清空", 13f, getColor(R.color.accent), true);
        clear.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 6),
                UiKit.dp(this, 6), UiKit.dp(this, 6));
        clear.setOnClickListener(v -> confirmClear());
        header.addView(clear);
        root.addView(header, UiKit.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.bg));
        LinearLayout content = UiKit.column(this);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 2),
                UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        UiKit.applyEdgeToEdgeInsets(this, root);

        empty = UiKit.text(this,
                "还没有充电记录。\n接入电源充电结束一次后，这里会显示本次/历史统计。",
                14f, R.color.text_secondary, false);
        empty.setLineSpacing(UiKit.dp(this, 3), 1f);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, UiKit.dp(this, 80), 0, 0);
        content.addView(empty, UiKit.matchWrap());
        list = UiKit.column(this);
        content.addView(list, UiKit.matchWrap());
    }

    private void render() {
        if (list == null || title == null) return;
        HistoryDb db = HistoryDb.get(this);
        List<HistoryDb.Record> records = db.latest(100);
        list.removeAllViews();
        int count = db.count();
        title.setText(count > 0 ? "充电历史 · " + count : "充电历史");
        empty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        SimpleDateFormat fmt = new SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault());
        for (int i = 0; i < records.size(); i++) {
            HistoryDb.Record r = records.get(i);
            LinearLayout card = UiKit.card(this);
            TextView when = UiKit.text(this, fmt.format(new Date(r.endTimeMs)),
                    12f, R.color.text_secondary, true);
            card.addView(when, UiKit.matchWrap());
            TextView power = UiKit.text(this,
                    String.format(Locale.ROOT, "平均 %.2f W · 峰值 %.2f W",
                            r.avgW, r.peakW),
                    18f, R.color.text_primary, true);
            power.setSingleLine(true);
            power.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(power, UiKit.top(this, UiKit.matchWrap(), 3));
            String sub = "时长 " + UiKit.duration(r.durationMs)
                    + (r.startLevel >= 0 && r.endLevel >= 0
                    ? " · 电量 " + r.startLevel + "% → " + r.endLevel + "%" : "");
            TextView subView = UiKit.text(this, sub, 12f, R.color.text_secondary, false);
            subView.setSingleLine(true);
            subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(subView, UiKit.top(this, UiKit.matchWrap(), 3));
            list.addView(card, UiKit.top(this, UiKit.matchWrap(), 8));
        }
        list.setAlpha(0f);
        list.setTranslationY(UiKit.dp(this, 8));
        list.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空充电历史")
                .setMessage("只删除本机的充电历史记录，不会影响设置和当前实时数据。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    HistoryDb.get(this).clear();
                    render();
                })
                .show();
    }
}
