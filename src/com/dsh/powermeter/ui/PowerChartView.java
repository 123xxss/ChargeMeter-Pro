package com.dsh.powermeter.ui;

import android.content.Context;
import android.graphics.Canvas;
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

    public PowerChartView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        colorAccent = context.getColor(R.color.accent);
        colorAccentEnd = context.getColor(R.color.teal);
        colorGrid = context.getColor(R.color.divider);
        colorMuted = context.getColor(R.color.text_muted);
        colorAppleAccent = context.getColor(R.color.apple_accent);
        colorNeonAccent = context.getColor(R.color.neon_accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.2f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(colorAccent);
        fill.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        text.setTextSize(dp(10));
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

        // 网格
        for (int i = 1; i <= 3; i++) {
            float y = padT + chartH * i / 4f;
            canvas.drawLine(padL, y, w - padR, y, grid);
        }
        // 右侧最大刻度
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(java.util.Locale.ROOT, "%.0f W", maxValue),
                w - padR, padT + dp(9), text);

        int n;
        synchronized (values) { n = count; }
        if (n < 2) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("充电中自动绘制功率曲线", w / 2f, h / 2f, text);
            return;
        }

        path.reset();
        fillPath.reset();
        boolean first = true;
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
                path.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        // 末端贴底，形成填充
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
