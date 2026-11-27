package com.dsh.powermeter.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.dsh.powermeter.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 程序化界面小工具：尺寸、圆角、卡片、开关行、胶囊标签与数值格式化。
 *
 * 全部界面都在 Java 里搭建，不新增 layout / drawable 资源；
 * 颜色统一从 R.color 取，自动跟随系统深浅色。
 */
public final class UiKit {

    private static final int RIPPLE_COLOR = 0x222F6BFF;

    private UiKit() {
    }

    // ---------------------------------------------------------------- 基础尺寸

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context c, int fillColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable stroked(Context c, int fillColor, int strokeColor,
                                           float strokeDp, float radiusDp) {
        GradientDrawable d = rounded(c, fillColor, radiusDp);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(Context c, int startColor, int endColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 在水波纹之下保留原有背景，用于可点击的卡片 / 行。 */
    public static void setRipple(View view) {
        Drawable content = view.getBackground();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE_COLOR), content, null));
        view.setClickable(true);
    }

    // ---------------------------------------------------------------- 文本

    public static TextView textColor(Context c, CharSequence value, float sizeSp, int color,
                                     boolean medium) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        return t;
    }

    public static TextView text(Context c, CharSequence value, float sizeSp, int colorRes,
                                boolean medium) {
        return textColor(c, value, sizeSp, c.getColor(colorRes), medium);
    }

    public static TextView label(Context c, CharSequence value) {
        return text(c, value, 12f, R.color.text_muted, false);
    }

    /** 设置页分组标题。 */
    public static TextView section(Context c, CharSequence value) {
        TextView t = text(c, value, 13f, R.color.text_secondary, true);
        t.setPadding(dp(c, 4), dp(c, 18), dp(c, 4), dp(c, 8));
        return t;
    }

    // ---------------------------------------------------------------- 容器

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weighted(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams top(Context c, LinearLayout.LayoutParams lp,
                                                int topDp) {
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    public static LinearLayout.LayoutParams left(Context c, LinearLayout.LayoutParams lp,
                                                 int leftDp) {
        lp.leftMargin = dp(c, leftDp);
        return lp;
    }

    public static void space(LinearLayout parent, int heightDp) {
        View v = new View(parent.getContext());
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp)));
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(rounded(c, c.getColor(R.color.card), 20f));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    // ---------------------------------------------------------------- 胶囊 / 按钮

    public static TextView pill(Context c, CharSequence value, int textColor, int bgColor) {
        TextView t = textColor(c, value, 12f, textColor, true);
        t.setSingleLine(true);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(rounded(c, bgColor, 100f));
        return t;
    }

    public static void stylePill(Context c, TextView t, CharSequence value, int textColor,
                                 int bgColor) {
        t.setText(value);
        t.setTextColor(textColor);
        t.setBackground(rounded(c, bgColor, 100f));
    }

    public static TextView primaryButton(Context c, CharSequence value,
                                         View.OnClickListener listener) {
        TextView t = text(c, value, 15f, R.color.white, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        t.setBackground(rounded(c, c.getColor(R.color.accent), 14f));
        if (listener != null) t.setOnClickListener(listener);
        setRipple(t);
        return t;
    }

    public static TextView ghostButton(Context c, CharSequence value,
                                       View.OnClickListener listener) {
        TextView t = text(c, value, 14f, R.color.text_secondary, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setBackground(rounded(c, c.getColor(R.color.card_alt), 14f));
        if (listener != null) t.setOnClickListener(listener);
        setRipple(t);
        return t;
    }

    public static ImageView iconButton(Context c, int drawableRes, int tintColorRes,
                                       View.OnClickListener listener) {
        ImageView iv = new ImageView(c);
        int pad = dp(c, 10);
        iv.setPadding(pad, pad, pad, pad);
        iv.setImageResource(drawableRes);
        iv.setColorFilter(c.getColor(tintColorRes));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        iv.setBackground(rounded(c, 0x00000000, 22f));
        if (listener != null) iv.setOnClickListener(listener);
        setRipple(iv);
        return iv;
    }

    public static ImageView backButton(Context c, View.OnClickListener listener) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(chevron(c, c.getColor(R.color.text_primary), true));
        int pad = dp(c, 10);
        iv.setPadding(pad, pad, pad, pad);
        iv.setContentDescription("返回");
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        iv.setBackground(rounded(c, 0x00000000, 22f));
        if (listener != null) iv.setOnClickListener(listener);
        setRipple(iv);
        return iv;
    }

    public static Drawable chevron(Context c, int color, boolean back) {
        return new ChevronDrawable(dp(c, 22), color, back);
    }

    // ---------------------------------------------------------------- 行

    /** 可点击的设置导航行：标题 + 说明 + 右侧状态值 + 箭头。 */
    public static LinearLayout actionRow(Context c, CharSequence title, CharSequence subtitle,
                                         CharSequence value, View.OnClickListener listener) {
        LinearLayout container = row(c);
        container.setBackground(rounded(c, c.getColor(R.color.card), 16f));
        int h = dp(c, 16);
        container.setPadding(h, dp(c, 13), h, dp(c, 13));
        LinearLayout texts = column(c);
        texts.addView(text(c, title, 15f, R.color.text_primary, true));
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = text(c, subtitle, 12f, R.color.text_secondary, false);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(sub, top(c, matchWrap(), 2));
        }
        container.addView(texts, weighted(1f));
        if (value != null && value.length() > 0) {
            TextView v = text(c, value, 13f, R.color.text_secondary, false);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setMaxWidth(dp(c, 132));
            container.addView(v, left(c, wrapWrap(), 8));
        }
        ImageView chev = new ImageView(c);
        chev.setImageDrawable(chevron(c, c.getColor(R.color.text_muted), false));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18));
        clp.leftMargin = dp(c, 4);
        container.addView(chev, clp);
        if (listener != null) container.setOnClickListener(listener);
        setRipple(container);
        return container;
    }

    /** 开关行：标题 + 说明 + Switch。整行点击也可以切换。 */
    public static final class SwitchRow extends LinearLayout {

        public final Switch toggle;

        public SwitchRow(Context c, CharSequence title, CharSequence subtitle, boolean checked,
                         boolean enabled, CompoundButton.OnCheckedChangeListener listener) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(rounded(c, c.getColor(R.color.card), 16f));
            int h = dp(c, 16);
            setPadding(h, dp(c, 12), h, dp(c, 12));
            LinearLayout texts = column(c);
            texts.addView(text(c, title, 15f, R.color.text_primary, true));
            if (subtitle != null && subtitle.length() > 0) {
                TextView sub = text(c, subtitle, 12f, R.color.text_secondary, false);
                sub.setMaxLines(3);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(sub, top(c, matchWrap(), 2));
            }
            addView(texts, weighted(1f));
            toggle = new Switch(c);
            toggle.setChecked(checked);
            toggle.setEnabled(enabled);
            toggle.setShowText(false);
            toggle.setOnCheckedChangeListener(listener);
            addView(toggle, left(c, wrapWrap(), 12));
            setOnClickListener(v -> {
                if (toggle.isEnabled()) toggle.toggle();
            });
            setRipple(this);
        }
    }

    /** 一组数值 + 说明文字（会话统计 / 四宫格复用）。 */
    public static final class Metric {
        public final LinearLayout root;
        public final TextView value;
        public final TextView label;

        private Metric(LinearLayout root, TextView value, TextView label) {
            this.root = root;
            this.value = value;
            this.label = label;
        }
    }

    public static Metric metric(Context c, CharSequence labelText, CharSequence initialValue) {
        LinearLayout root = column(c);
        TextView value = text(c, initialValue, 17f, R.color.text_primary, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(value, matchWrap());
        TextView label = text(c, labelText, 11.5f, R.color.text_muted, false);
        root.addView(label, top(c, matchWrap(), 2));
        return new Metric(root, value, label);
    }

    // ---------------------------------------------------------------- 系统栏 / 边距

    /** 状态栏 / 导航栏配色与图标明暗；barColor 用 R.color（随深浅色自动切换）。 */
    public static void styleSystemBars(Activity activity, int barColor, boolean darkIcons) {
        Window window = activity.getWindow();
        window.setStatusBarColor(barColor);
        window.setNavigationBarColor(barColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkIcons ? mask : 0, mask);
            }
        } else {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (darkIcons) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** targetSdk 35+ 强制 edge-to-edge，这里按系统栏高度给根视图补内边距。 */
    public static void applyEdgeToEdgeInsets(Activity activity, View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Window window = activity.getWindow();
        window.setDecorFitsSystemWindows(false);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return WindowInsets.CONSUMED;
        });
        view.requestApplyInsets();
    }

    // ---------------------------------------------------------------- 数值格式化

    public static String watts(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String volts(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String amps(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String temp(double value) {
        if (Double.isNaN(value) || value <= 0) return "--";
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 把毫秒格式化成「1 小时 05 分」。 */
    public static String duration(long millis) {
        if (millis <= 0) return "--";
        long totalMinutes = Math.max(1L, Math.round(millis / 60000.0));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours <= 0) return minutes + " 分钟";
        if (minutes == 0) return hours + " 小时";
        return hours + " 小时 " + minutes + " 分";
    }

    public static String clock(long millis) {
        if (millis <= 0) return "--";
        return new SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    // ---------------------------------------------------------------- 自绘箭头

    private static final class ChevronDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size;
        private final boolean back;

        ChevronDrawable(int size, int color, boolean back) {
            this.size = size;
            this.back = back;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, size * 0.09f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            float w = getBounds().width();
            float h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            float cx = getBounds().left + w / 2f;
            float cy = getBounds().top + h / 2f;
            float s = Math.min(w, h) * 0.18f;
            path.reset();
            if (back) {
                path.moveTo(cx + s, cy - s);
                path.lineTo(cx - s, cy);
                path.lineTo(cx + s, cy + s);
            } else {
                path.moveTo(cx - s, cy - s);
                path.lineTo(cx + s, cy);
                path.lineTo(cx - s, cy + s);
            }
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }
}
