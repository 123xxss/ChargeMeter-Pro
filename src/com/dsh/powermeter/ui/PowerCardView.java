package com.dsh.powermeter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import com.dsh.powermeter.R;
import com.dsh.powermeter.core.ChargeState;
import com.dsh.powermeter.core.PowerSnapshot;

import java.util.Locale;

/**
 * 自绘的“充电功率卡”，提供三种风格：
 * - Apple 极简：浅色/深色卡片、iOS 蓝强调色、居中大数字；
 * - 经典渐变：蓝绿渐变；
 * - 深色霓虹：深色底 + 青色描边/发光。
 *
 * 高度固定 236dp；内容全部 Canvas 绘制，不依赖嵌套 LinearLayout，避免 OEM 测量差异。
 */
public final class PowerCardView extends View {

    public static final int STYLE_APPLE = 0;
    public static final int STYLE_CLASSIC = 1;
    public static final int STYLE_NEON = 2;
    public static final int STYLE_MINT = 3;
    public static final int STYLE_VIOLET = 4;

    private static final int HEIGHT_DP = 236;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path bolt = new Path();

    private final float density;
    private final float scaledDensity;

    private PowerSnapshot snapshot;
    private int widthPx;
    private int heightPx;
    private boolean animating;
    private int style = STYLE_APPLE;

    private final int cCard;
    private final int cCardAlt;
    private final int cTextPrimary;
    private final int cTextSecondary;
    private final int cTextMuted;
    private final int cDivider;
    private final int cAppleAccent;
    private final int cAppleSoft;
    private final int cHeroStart;
    private final int cHeroEnd;
    private final int cWarnStart;
    private final int cWarnEnd;
    private final int cNeonStart;
    private final int cNeonEnd;
    private final int cNeonAccent;
    private final int cMintStart;
    private final int cMintEnd;
    private final int cMintText;
    private final int cMintAccent;
    private final int cVioletStart;
    private final int cVioletEnd;
    private final int cVioletAccent;

    private final Runnable pulseTick = new Runnable() {
        @Override
        public void run() {
            if (!animating) return;
            invalidate();
            postOnAnimationDelayed(this, 33);
        }
    };

    public PowerCardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        cCard = context.getColor(R.color.card);
        cCardAlt = context.getColor(R.color.card_alt);
        cTextPrimary = context.getColor(R.color.text_primary);
        cTextSecondary = context.getColor(R.color.text_secondary);
        cTextMuted = context.getColor(R.color.text_muted);
        cDivider = context.getColor(R.color.divider);
        cAppleAccent = context.getColor(R.color.apple_accent);
        cAppleSoft = context.getColor(R.color.apple_soft);
        cHeroStart = context.getColor(R.color.hero_start);
        cHeroEnd = context.getColor(R.color.hero_end);
        cWarnStart = context.getColor(R.color.hero_warn_start);
        cWarnEnd = context.getColor(R.color.hero_warn_end);
        cNeonStart = context.getColor(R.color.neon_bg_start);
        cNeonEnd = context.getColor(R.color.neon_bg_end);
        cNeonAccent = context.getColor(R.color.neon_accent);
        cMintStart = context.getColor(R.color.mint_bg_start);
        cMintEnd = context.getColor(R.color.mint_bg_end);
        cMintText = context.getColor(R.color.mint_text);
        cMintAccent = context.getColor(R.color.mint_accent);
        cVioletStart = context.getColor(R.color.violet_bg_start);
        cVioletEnd = context.getColor(R.color.violet_bg_end);
        cVioletAccent = context.getColor(R.color.violet_accent);
        setClickable(true);
        setFocusable(true);
        setContentDescription("充电功率卡片，点击查看详情");
    }

    public void setStyle(int style) {
        this.style = Math.max(0, Math.min(4, style));
        updateShader();
        invalidate();
    }

    public int getStyle() {
        return style;
    }

    public void setData(PowerSnapshot s) {
        boolean wasCharging = isCharging(snapshot);
        snapshot = s;
        boolean charging = isCharging(s);
        updateAnimationState(charging, wasCharging);
        updateShader();
        String desc = s == null ? "功率读取中" : (s.modeLabel + " " + fmt(s.powerW) + " 瓦");
        setContentDescription("充电功率卡片，" + desc + "，点击查看详情");
        invalidate();
    }

    private static boolean isCharging(PowerSnapshot s) {
        return s != null && s.charging && s.state != ChargeState.FULL
                && s.state != ChargeState.PAUSED;
    }

    private void updateAnimationState(boolean charging, boolean wasCharging) {
        boolean shouldAnimate = charging && isShown();
        if (shouldAnimate && !animating) {
            animating = true;
            postOnAnimation(pulseTick);
        } else if (!shouldAnimate && (animating || wasCharging)) {
            animating = false;
            removeCallbacks(pulseTick);
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) {
            animating = false;
            removeCallbacks(pulseTick);
        } else if (isCharging(snapshot) && !animating) {
            animating = true;
            postOnAnimation(pulseTick);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            animating = false;
            removeCallbacks(pulseTick);
        } else if (isCharging(snapshot) && !animating) {
            animating = true;
            postOnAnimation(pulseTick);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (HEIGHT_DP * density + 0.5f);
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec));
        widthPx = w;
        heightPx = h;
        updateShader();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        widthPx = w;
        heightPx = h;
        updateShader();
    }

    private void updateShader() {
        if (widthPx <= 0 || heightPx <= 0) return;
        if (style == STYLE_APPLE) {
            bgPaint.setShader(null);
            bgPaint.setColor(cCard);
            return;
        }
        boolean warn = snapshot != null && snapshot.state == ChargeState.ABNORMAL;
        if (warn) {
            bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                    cWarnStart, cWarnEnd, Shader.TileMode.CLAMP));
        } else if (style == STYLE_NEON) {
            bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                    cNeonStart, cNeonEnd, Shader.TileMode.CLAMP));
        } else if (style == STYLE_MINT) {
            bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                    cMintStart, cMintEnd, Shader.TileMode.CLAMP));
        } else if (style == STYLE_VIOLET) {
            bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                    cVioletStart, cVioletEnd, Shader.TileMode.CLAMP));
        } else {
            bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                    cHeroStart, cHeroEnd, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimationState(isCharging(snapshot), false);
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(pulseTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (widthPx == 0 || heightPx == 0) return;
        float pad = dp(20);
        float right = widthPx - pad;
        float radius = dp((style == STYLE_APPLE || style == STYLE_MINT) ? 26 : 24);

        rect.set(0, 0, widthPx, heightPx);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);

        boolean apple = style == STYLE_APPLE;
        boolean mint = style == STYLE_MINT;
        boolean violet = style == STYLE_VIOLET;
        boolean light = apple || mint;
        int fg = apple ? cTextPrimary : (mint ? cMintText : 0xFFFFFFFF);
        int soft = apple ? cTextSecondary : (mint ? cMintText : 0xD9FFFFFF);
        int muted = apple ? cTextMuted : (mint ? cMintText : 0xB3FFFFFF);
        int pillBg = apple ? cAppleSoft : (mint ? 0x2200A88F : 0x33FFFFFF);
        int pillFg = apple ? cAppleAccent : (mint ? cMintAccent
                : (violet ? cVioletAccent : 0xFFFFFFFF));
        int tierBg = light ? cCardAlt : 0x26FFFFFF;
        int tierFg = light ? cTextSecondary : 0xFFFFFFFF;
        int track = light ? cDivider : 0x3DFFFFFF;
        int accent = apple ? cAppleAccent
                : (mint ? cMintAccent
                : (violet ? cVioletAccent
                : (style == STYLE_NEON ? cNeonAccent : 0xFFFFFFFF)));

        PowerSnapshot s = snapshot;
        if (s == null) {
            drawPill(canvas, pad, dp(16), "读取中", 12f, pillBg, pillFg);
            drawText(canvas, "功率读取中", dp(20), dp(88), 26f, fg, true);
            drawText(canvas, "正在读取电池数据", dp(20), dp(114), 12f, soft, false);
            return;
        }

        float top = dp(16);
        float pillH = dp(24);
        String pillText = s.discharging ? "放电中"
                : (s.state == null ? "读取中" : s.state.label);
        drawPill(canvas, pad, top, pillText, 12f, pillBg, pillFg);
        float stateWidth = pillWidth(pillText, 12f) + dp(20);

        if (s.showTier && s.tier != null && !s.realtime
                && (s.state == ChargeState.CHARGING || s.state == ChargeState.PAUSED
                || s.state == ChargeState.FULL)) {
            drawPillRight(canvas, right, top, s.tier.label, 11.5f, tierBg, tierFg);
        }

        String value = null;
        String unit = "W";
        String placeholder = null;
        String sub = null;
        if (s.realtime) {
            if (s.nearZero) {
                placeholder = "接近零";
                sub = "电池放电中，净功耗接近零";
            } else if (!s.currentSupported) {
                placeholder = "功率读取中/暂不可用";
                sub = "当前机型不支持读取电流";
            } else if (!s.valid) {
                placeholder = "功率读取中/暂不可用";
                sub = s.invalidReason;
            } else {
                value = fmt(s.powerW);
                sub = s.modeLabel + " · 1 秒刷新";
            }
        } else if (s.discharging) {
            if (s.nearZero) {
                placeholder = "接近零";
                sub = "电池放电功率接近 0";
            } else {
                value = fmt(s.powerW);
                sub = "电池放电功耗 · 1 秒刷新";
            }
        } else if (s.state == ChargeState.UNPLUGGED) {
            placeholder = "接入电源后自动显示";
            sub = "插上充电器即可读取实时功率";
        } else if (s.state == ChargeState.FULL) {
            placeholder = "已充满";
            sub = "已连接电源，停止为电池充电";
        } else if (s.state == ChargeState.PAUSED) {
            placeholder = "充电已暂停";
            sub = "当前未向电池充电";
        } else if (s.state == ChargeState.UNAVAILABLE || !s.valid) {
            placeholder = "功率读取中/暂不可用";
            sub = s.invalidReason;
        } else if (s.nearZero) {
            placeholder = "接近零";
            sub = "电池放电中，净功耗接近零";
        } else {
            value = fmt(s.powerW);
            sub = s.modeLabel + " · 1 秒刷新";
        }

        int powerColor = (s.state == ChargeState.ABNORMAL) ? cWarnStart
                : (apple || mint || violet || style == STYLE_NEON ? accent : fg);
        float baseline = top + pillH + dp(44);
        if (value != null) {
            text.setTextSize(scaledDensity * 52f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setColor(powerColor);
            float vw = text.measureText(value);
            text.setTextSize(scaledDensity * 20f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float uw = text.measureText(unit);
            float totalW = vw + dp(6) + uw;
            float x = light ? (widthPx - totalW) / 2f : pad;
            text.setTextSize(scaledDensity * 52f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText(value, x, baseline, text);
            text.setTextSize(scaledDensity * 20f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(light ? cTextSecondary : 0xCCFFFFFF);
            canvas.drawText(unit, x + vw + dp(6), baseline, text);
            if (light) {
                text.setTextSize(scaledDensity * 11f);
                text.setColor(muted);
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(s.modeLabel, widthPx / 2f, baseline + dp(20), text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        } else {
            text.setTextSize(scaledDensity * 24f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            text.setColor(fg);
            String ph = placeholder == null ? "功率读取中" : placeholder;
            if (light) {
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(ph, widthPx / 2f, baseline, text);
                text.setTextAlign(Paint.Align.LEFT);
            } else {
                canvas.drawText(ph, pad, baseline, text);
            }
        }

        if (sub != null && !sub.isEmpty()) {
            text.setTextSize(scaledDensity * 12.5f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            text.setColor(soft);
            if (light) {
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(ellipsize(sub, right - pad), widthPx / 2f,
                        top + pillH + dp(76), text);
                text.setTextAlign(Paint.Align.LEFT);
            } else {
                canvas.drawText(ellipsize(sub, right - pad), pad,
                        top + pillH + dp(72), text);
            }
        }

        float barTop = top + pillH + dp(94);
        float barH = dp(7);
        rect.set(pad, barTop, right, barTop + barH);
        fill.setShader(null);
        fill.setColor(track);
        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
        if (s.level >= 0) {
            float fillW = (right - pad) * Math.max(0, Math.min(100, s.level)) / 100f;
            if (fillW > 0) {
                rect.set(pad, barTop, pad + fillW, barTop + barH);
                fill.setColor(accent);
                canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
            }
        }

        StringBuilder info = new StringBuilder();
        if (s.level >= 0) info.append("电量 ").append(s.level).append("%");
        if (s.showTemperature && !Double.isNaN(s.temperatureC) && s.temperatureC > 0) {
            if (info.length() > 0) info.append(" · ");
            info.append("电池 ").append(fmt1(s.temperatureC)).append("°C");
        }
        if (s.showEstimate) {
            if (info.length() > 0) info.append(" · ");
            if (s.state == ChargeState.FULL) info.append("已充满");
            else if (s.estimateValid && s.remainingSeconds > 0) {
                info.append("充满约 ").append(duration(s.remainingSeconds));
            } else info.append("预估中");
        }
        text.setTextSize(scaledDensity * 11.5f);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setColor(soft);
        if (light) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(ellipsize(info.toString(), right - pad), widthPx / 2f,
                    barTop + barH + dp(22), text);
            text.setTextAlign(Paint.Align.LEFT);
        } else {
            canvas.drawText(ellipsize(info.toString(), right - pad), pad,
                    barTop + barH + dp(22), text);
        }

        String source = s.sourceLabel == null ? "标准读取" : s.sourceLabel;
        String note = s.abnormalText != null && !s.abnormalText.isEmpty()
                ? s.abnormalText
                : (s.accessoryText != null && !s.accessoryText.isEmpty() ? s.accessoryText : null);
        text.setTextSize(scaledDensity * 10.5f);
        text.setColor(muted);
        String bottom = note != null ? note : ("数据来源：" + source);
        canvas.drawText(ellipsize(bottom, right - pad - dp(54)), pad,
                heightPx - dp(15), text);
        text.setTextSize(scaledDensity * 11f);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(light ? accent : (violet || style == STYLE_NEON ? accent : 0xE6FFFFFF));
        canvas.drawText("详情 ›", right - dp(44), heightPx - dp(15), text);

        if (isCharging(s)) {
            long now = SystemClock.uptimeMillis();
            float phase = (now % 1600) / 1600f;
            if (s.level >= 0) {
                float fillW = (right - pad) * Math.max(0, Math.min(100, s.level)) / 100f;
                if (fillW > dp(20)) {
                    float shimmerW = Math.max(dp(22), fillW * 0.22f);
                    float x = pad + (fillW + shimmerW) * phase - shimmerW;
                    float left = Math.max(pad, x);
                    float rr = Math.min(right, x + shimmerW);
                    if (rr > left) {
                        rect.set(left, barTop, rr, barTop + barH);
                        fill.setColor(0xFFFFFFFF);
                        fill.setAlpha(110);
                        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
                        fill.setAlpha(255);
                    }
                }
            }
            float glow = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
            float cx = stateWidth + pad + dp(15);
            float cy = top + dp(12);
            fill.setColor(apple ? cAppleSoft : (mint ? 0x2200A88F : 0x2EFFFFFF));
            fill.setAlpha((int) (90 + 90 * glow));
            canvas.drawCircle(cx, cy, dp(9), fill);
            bolt.reset();
            bolt.moveTo(cx + dp(1.2f), cy - dp(6));
            bolt.lineTo(cx - dp(4.8f), cy + dp(1));
            bolt.lineTo(cx - dp(0.6f), cy + dp(1));
            bolt.lineTo(cx - dp(1.6f), cy + dp(6));
            bolt.lineTo(cx + dp(5.2f), cy - dp(1.2f));
            bolt.lineTo(cx + dp(0.8f), cy - dp(1.2f));
            bolt.close();
            fill.setColor(apple || mint || violet || style == STYLE_NEON ? accent : 0xFFFFFFFF);
            fill.setAlpha((int) (150 + 105 * glow));
            canvas.drawPath(bolt, fill);
            fill.setAlpha(255);
        }
    }

    private void drawText(Canvas canvas, String value, float x, float baseline,
                          float sizeSp, int color, boolean bold) {
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        text.setColor(color);
        canvas.drawText(value, x, baseline, text);
    }

    private void drawPill(Canvas canvas, float left, float top, String label,
                          float sizeSp, int bg, int fg) {
        float w = pillWidth(label, sizeSp);
        float h = dp(24);
        rect.set(left, top, left + w, top + h);
        fill.setShader(null);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(fg);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = top + h / 2 - (fm.ascent + fm.descent) / 2;
        canvas.drawText(label, left + dp(10), baseline, text);
    }

    private void drawPillRight(Canvas canvas, float right, float top, String label,
                               float sizeSp, int bg, int fg) {
        float w = pillWidth(label, sizeSp);
        float h = dp(24);
        rect.set(right - w, top, right, top + h);
        fill.setShader(null);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(fg);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = top + h / 2 - (fm.ascent + fm.descent) / 2;
        canvas.drawText(label, right - w + dp(9), baseline, text);
    }

    private float pillWidth(String label, float sizeSp) {
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text.measureText(label) + dp(20);
    }

    private String ellipsize(String textValue, float maxWidth) {
        if (textValue == null) return "";
        text.setTextSize(text.getTextSize());
        if (text.measureText(textValue) <= maxWidth) return textValue;
        String ellipsis = "…";
        int end = textValue.length();
        while (end > 0 && text.measureText(textValue.substring(0, end))
                + text.measureText(ellipsis) > maxWidth) {
            end--;
        }
        return textValue.substring(0, Math.max(0, end)) + ellipsis;
    }

    private float dp(float value) {
        return value * density;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String duration(long seconds) {
        long minutes = Math.max(1, Math.round(seconds / 60.0));
        if (minutes < 60) return minutes + " 分钟";
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时" + m + " 分";
    }
}
