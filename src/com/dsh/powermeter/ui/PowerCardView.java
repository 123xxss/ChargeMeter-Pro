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
 * 自绘的“充电功率卡”。
 *
 * 不再使用嵌套 LinearLayout + weight 排布：所有文字、状态胶囊、进度条、动画都在
 * 一个 View 里绘制，避免 OEM/Android 16 上布局测量差异导致卡片被拉满或子视图不显示。
 *
 * 高度固定为 236dp，内容紧凑；充电中会在右上角绘制一个呼吸脉冲点作为轻量动画。
 */
public final class PowerCardView extends View {

    private static final int HEIGHT_DP = 236;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float scaledDensity;
    private final RectF rect = new RectF();
    private final RectF oval = new RectF();
    private final Path bolt = new Path();

    private PowerSnapshot snapshot;
    private int widthPx;
    private int heightPx;
    private boolean animating;

    private final int colorHeroStart;
    private final int colorHeroEnd;
    private final int colorWarnStart;
    private final int colorWarnEnd;

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
        colorHeroStart = context.getColor(R.color.hero_start);
        colorHeroEnd = context.getColor(R.color.hero_end);
        colorWarnStart = context.getColor(R.color.hero_warn_start);
        colorWarnEnd = context.getColor(R.color.hero_warn_end);
        setClickable(true);
        setFocusable(true);
        setContentDescription("充电功率卡片，点击查看详情");
    }

    public void setData(PowerSnapshot s) {
        boolean wasCharging = isCharging(snapshot);
        snapshot = s;
        boolean charging = isCharging(s);
        if (charging && !animating) {
            animating = true;
            postOnAnimation(pulseTick);
        } else if (!charging && wasCharging) {
            animating = false;
            removeCallbacks(pulseTick);
        }
        updateShader();
        invalidate();
    }

    private static boolean isCharging(PowerSnapshot s) {
        return s != null && s.charging && s.state != ChargeState.FULL
                && s.state != ChargeState.PAUSED;
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
        boolean warn = snapshot != null && snapshot.state == ChargeState.ABNORMAL;
        bgPaint.setShader(new LinearGradient(0, 0, widthPx, heightPx,
                warn ? colorWarnStart : colorHeroStart,
                warn ? colorWarnEnd : colorHeroEnd, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isCharging(snapshot)) {
            animating = true;
            postOnAnimation(pulseTick);
        }
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
        float radius = dp(24);

        rect.set(0, 0, widthPx, heightPx);
        canvas.drawRoundRect(rect, radius, radius, bgPaint);

        if (snapshot == null) {
            drawPill(canvas, pad, dp(16), "读取中", 12f, 0x33FFFFFF);
            drawMain(canvas, "功率读取中", null, dp(70), 26f, "正在读取电池数据");
            return;
        }

        PowerSnapshot s = snapshot;
        boolean warn = s.state == ChargeState.ABNORMAL;
        int white = 0xFFFFFFFF;
        int soft = warn ? 0xE6FFFFFF : 0xD9FFFFFF;

        // 1. 状态胶囊
        float top = dp(16);
        float pillH = dp(24);
        float stateLeft = pad;
        drawPill(canvas, stateLeft, top, s.state == null ? "读取中" : s.state.label,
                12f, 0x33FFFFFF);
        float stateWidth = pillWidth(s.state == null ? "读取中" : s.state.label, 12f) + dp(20);

        // 2. 档位胶囊（右侧）
        if (s.showTier && s.tier != null && !s.realtime
                && (s.state == ChargeState.CHARGING || s.state == ChargeState.PAUSED
                || s.state == ChargeState.FULL)) {
            drawPillRight(canvas, right, top, s.tier.label, 11.5f, 0x2EFFFFFF);
        }

        // 3. 主数值 / 占位
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
                sub = s.modeLabel + " · 0.5 秒实时刷新";
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
            sub = s.modeLabel + " · 0.5 秒实时刷新";
        }

        if (value != null) {
            text.setTextSize(scaledDensity * 52f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            text.setColor(white);
            float baseline = top + pillH + dp(44);
            canvas.drawText(value, pad, baseline, text);
            float valueWidth = text.measureText(value);
            text.setTextSize(scaledDensity * 20f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(0xCCFFFFFF);
            canvas.drawText(unit, pad + valueWidth + dp(6), baseline, text);
        } else {
            text.setTextSize(scaledDensity * 24f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            text.setColor(white);
            canvas.drawText(placeholder == null ? "功率读取中" : placeholder,
                    pad, top + pillH + dp(42), text);
        }

        if (sub != null && !sub.isEmpty()) {
            text.setTextSize(scaledDensity * 12.5f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            text.setColor(soft);
            canvas.drawText(ellipsize(sub, right - pad), pad,
                    top + pillH + dp(72), text);
        }

        // 4. 电量进度条
        float barTop = top + pillH + dp(91);
        float barH = dp(7);
        rect.set(pad, barTop, right, barTop + barH);
        fill.setAlpha(255);
        fill.setShader(null);
        fill.setColor(0x33FFFFFF);
        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
        if (s.level >= 0) {
            float fillW = (right - pad) * Math.max(0, Math.min(100, s.level)) / 100f;
            if (fillW > 0) {
                rect.set(pad, barTop, pad + fillW, barTop + barH);
                fill.setColor(white);
                canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
            }
        }

        // 5. 电量 / 温度 / 预估
        StringBuilder info = new StringBuilder();
        if (s.level >= 0) info.append("电量 ").append(s.level).append("%");
        if (s.showTemperature && !Double.isNaN(s.temperatureC) && s.temperatureC > 0) {
            if (info.length() > 0) info.append(" · ");
            info.append("温度 ").append(fmt1(s.temperatureC)).append("°C");
        }
        if (s.showEstimate) {
            if (info.length() > 0) info.append(" · ");
            if (s.state == ChargeState.FULL) {
                info.append("已充满");
            } else if (s.estimateValid && s.remainingSeconds > 0) {
                info.append("充满约 ").append(duration(s.remainingSeconds));
            } else {
                info.append("预估中");
            }
        }
        text.setTextSize(scaledDensity * 11.5f);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setColor(soft);
        canvas.drawText(ellipsize(info.toString(), right - pad), pad,
                barTop + barH + dp(22), text);

        // 6. 底部来源 + 详情
        String source = s.sourceLabel == null ? "标准读取" : s.sourceLabel;
        String note = s.abnormalText != null && !s.abnormalText.isEmpty()
                ? s.abnormalText
                : (s.accessoryText != null && !s.accessoryText.isEmpty() ? s.accessoryText : null);
        text.setTextSize(scaledDensity * 10.5f);
        text.setColor(0xB3FFFFFF);
        String bottom = note != null ? note : ("数据来源：" + source);
        canvas.drawText(ellipsize(bottom, right - pad - dp(54)), pad,
                heightPx - dp(15), text);
        text.setTextSize(scaledDensity * 11f);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(0xE6FFFFFF);
        canvas.drawText("详情 ›", right - dp(44), heightPx - dp(15), text);

        // 7. 充电动画：进度条流光 + 状态旁呼吸闪电（纯绘制，不额外占布局）
        if (isCharging(s)) {
            long now = SystemClock.uptimeMillis();
            float phase = (now % 1600) / 1600f;
            // 进度条上移动的高光
            if (s.level >= 0) {
                float fillW = (right - pad) * Math.max(0, Math.min(100, s.level)) / 100f;
                if (fillW > dp(20)) {
                    float shimmerW = Math.max(dp(22), fillW * 0.22f);
                    float x = pad + (fillW + shimmerW) * phase - shimmerW;
                    float left = Math.max(pad, x);
                    float rr = Math.min(right, x + shimmerW);
                    if (rr > left) {
                        rect.set(left, barTop, rr, barTop + barH);
                        fill.setShader(null);
                        fill.setColor(0xFFFFFFFF);
                        fill.setAlpha(110);
                        canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
                    }
                }
            }
            // 呼吸闪电图标
            float glow = (float) (0.55 + 0.45 * Math.sin(phase * Math.PI * 2));
            float cx = stateLeft + stateWidth + dp(15);
            float cy = top + dp(12);
            fill.setShader(null);
            fill.setColor(0x2EFFFFFF);
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
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha((int) (150 + 105 * glow));
            canvas.drawPath(bolt, fill);
        }
    }

    private void drawMain(Canvas canvas, String value, String ignored, float baseline,
                          float sizeSp, String sub) {
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        text.setColor(0xFFFFFFFF);
        canvas.drawText(value, dp(20), baseline, text);
        if (sub != null) {
            text.setTextSize(scaledDensity * 12f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            text.setColor(0xD9FFFFFF);
            canvas.drawText(sub, dp(20), baseline + dp(26), text);
        }
    }

    private void drawPill(Canvas canvas, float left, float top, String label,
                          float sizeSp, int bg) {
        float w = pillWidth(label, sizeSp);
        float h = dp(24);
        rect.set(left, top, left + w, top + h);
        fill.setShader(null);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(0xFFFFFFFF);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = top + h / 2 - (fm.ascent + fm.descent) / 2;
        canvas.drawText(label, left + dp(10), baseline, text);
    }

    private void drawPillRight(Canvas canvas, float right, float top, String label,
                               float sizeSp, int bg) {
        float w = pillWidth(label, sizeSp);
        float h = dp(24);
        rect.set(right - w, top, right, top + h);
        fill.setShader(null);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        text.setTextSize(scaledDensity * sizeSp);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(0xFFFFFFFF);
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
        if (text.measureText(textValue) <= maxWidth) return textValue;
        String ellipsis = "…";
        int end = textValue.length();
        while (end > 0 && text.measureText(textValue.substring(0, end)) + text.measureText(ellipsis) > maxWidth) {
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
