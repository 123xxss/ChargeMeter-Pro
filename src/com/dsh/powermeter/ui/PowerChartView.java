package com.dsh.powermeter.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import com.dsh.powermeter.R;

/**
 * 最近 2 分钟的实时功率曲线（1 秒采样，最多 120 点）。
 * 纯 Canvas 绘制，轻量、无第三方依赖；用于快速判断功率是否在实时变化。
 */
public final class PowerChartView extends View {

    private static final int MAX_POINTS = 120;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();

    private final float[] values = new float[MAX_POINTS];
    private int count;
    private int head;
    private float maxValue = 1f;
    private final float density;
    private int colorAccent;
    private int colorAccentEnd;
    private final int colorGrid;
    private final int colorMuted;
    private final int colorAppleAccent;
    private final int colorNeonAccent;
    private final int colorMintAccent;
    private final int colorVioletAccent;

    public PowerChartView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        colorAccent = context.getColor(R.color.accent);
        colorAccentEnd = context.getColor(R.color.teal);
        colorGrid = context.getColor(R.color.divider);
        colorMuted = context.getColor(R.color.text_muted);
        colorAppleAccent = context.getColor(R.color.apple_accent);
        colorNeonAccent = context.getColor(R.color.neon_accent);
        colorMintAccent = context.getColor(R.color.mint_accent);
        colorVioletAccent = context.getColor(R.color.violet_accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.2f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(colorAccent);
        fill.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        text.setTextSize(dp(10.5f));
        text.setColor(colorMuted);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(colorAccent);
    }

    public void setStyle(int style) {
        if (style == 0) {
            colorAccent = colorAppleAccent;
            colorAccentEnd = colorAppleAccent;
        } else if (style == 2) {
            colorAccent = colorNeonAccent;
            colorAccentEnd = contextColorTeal();
        } else if (style == 3) {
            colorAccent = colorMintAccent;
            colorAccentEnd = colorMintAccent;
        } else if (style == 4) {
            colorAccent = colorVioletAccent;
            colorAccentEnd = colorVioletAccent;
        } else {
            colorAccent = getContext().getColor(R.color.accent);
            colorAccentEnd = contextColorTeal();
        }
        line.setColor(colorAccent);
        dot.setColor(colorAccentEnd);
        invalidate();
    }

    private int contextColorTeal() {
        return getContext().getColor(R.color.teal);
    }

    public void add(double watts) {
        if (!Double.isFinite(watts) || watts < 0) return;
        synchronized (values) {
            if (count < MAX_POINTS) {
                values[(head + count) % MAX_POINTS] = (float) watts;
                count++;
            } else {
                values[head] = (float) watts;
                head = (head + 1) % MAX_POINTS;
            }
            float max = 5f;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, values[(head + i) % MAX_POINTS]);
            }
            maxValue = Math.max(max * 1.15f, max + 1f);
        }
        invalidate();
    }

    public void clear() {
        synchronized (values) {
            count = 0;
            head = 0;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float padL = dp(8);
        float padR = dp(8);
        float padT = dp(10);
        float padB = dp(18);
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;

        int n;
        synchronized (values) { n = count; }

        // 空态：不画黑色网格和刻度，避免出现“几条黑线+文字叠在一起”的观感。
        if (n < 2) {
            float boxTop = padT + dp(2);
            float boxBottom = h - padB - dp(2);
            fill.setShader(null);
            fill.setColor(colorGrid);
            fill.setAlpha(55);
            android.graphics.RectF box = new android.graphics.RectF(
                    padL, boxTop, w - padR, boxBottom);
            canvas.drawRoundRect(box, dp(14), dp(14), fill);
            fill.setAlpha(255);

            // 迷你曲线图标
            float ix = w / 2f;
            float iy = boxTop + (boxBottom - boxTop) * 0.34f;
            Path icon = new Path();
            icon.moveTo(ix - dp(15), iy + dp(5));
            icon.lineTo(ix - dp(7), iy - dp(2));
            icon.lineTo(ix - dp(1), iy + dp(4));
            icon.lineTo(ix + dp(7), iy - dp(5));
            icon.lineTo(ix + dp(15), iy + dp(2));
            line.setColor(colorAccent);
            line.setAlpha(110);
            canvas.drawPath(icon, line);
            line.setAlpha(255);

            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(colorMuted);
            text.setTextSize(dp(11.5f));
            canvas.drawText("开始充电后自动记录功率曲线",
                    w / 2f, iy + dp(30), text);
            text.setTextSize(dp(9.5f));
            text.setAlpha(160);
            canvas.drawText("每秒一个点 · 最近 2 分钟",
                    w / 2f, iy + dp(44), text);
            text.setAlpha(255);
            return;
        }

        // 网格：虚线、低对比，不抢曲线
        grid.setColor(colorMuted);
        grid.setAlpha(48);
        grid.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(6)}, 0));
        for (int i = 1; i <= 3; i++) {
            float y = padT + chartH * i / 4f;
            canvas.drawLine(padL, y, w - padR, y, grid);
        }
        grid.setPathEffect(null);
        grid.setAlpha(255);
        // 右侧最大刻度
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(colorMuted);
        text.setTextSize(dp(10));
        canvas.drawText(String.format(java.util.Locale.ROOT, "%.0f W", maxValue),
                w - padR, padT + dp(9), text);

        // 平滑路径：用中点二次贝塞尔柔化折线
        path.reset();
        fillPath.reset();
        boolean first = true;
        float prevX = 0, prevY = 0;
        float lastX = padL;
        float lastY = padT + chartH;
        for (int i = 0; i < n; i++) {
            float v = values[(head + i) % MAX_POINTS];
            float x = padL + chartW * i / Math.max(1, n - 1);
            float y = padT + chartH * (1f - Math.min(1f, v / maxValue));
            if (first) {
                path.moveTo(x, y);
                fillPath.moveTo(x, padT + chartH);
                fillPath.lineTo(x, y);
                first = false;
            } else {
                float midX = (prevX + x) / 2f;
                float midY = (prevY + y) / 2f;
                path.quadTo(prevX, prevY, midX, midY);
                fillPath.quadTo(prevX, prevY, midX, midY);
            }
            prevX = x;
            prevY = y;
            lastX = x;
            lastY = y;
        }
        path.lineTo(lastX, lastY);
        fillPath.lineTo(lastX, lastY);
        fillPath.lineTo(lastX, padT + chartH);
        fillPath.close();
        fill.setShader(new LinearGradient(0, padT, 0, padT + chartH,
                (colorAccent & 0x00FFFFFF) | 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fill);
        fill.setShader(null);
        line.setColor(colorAccent);
        canvas.drawPath(path, line);
        dot.setColor(colorAccentEnd);
        canvas.drawCircle(lastX, lastY, dp(3f), dot);

        // 时间范围 + 当前值
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("-2 分钟", padL, h - dp(3), text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("现在", w - padR, h - dp(3), text);
        if (n > 0) {
            float lastValue = values[(head + n - 1) % MAX_POINTS];
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format(java.util.Locale.ROOT, "当前 %.2f W", lastValue),
                    padL, padT + dp(10), text);
        }
    }

    private float dp(float v) {
        return v * density;
    }
}
