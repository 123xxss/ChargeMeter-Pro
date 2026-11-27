package com.dsh.powermeter.ui;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/** 统一主界面、详情、历史的过渡节奏；只做轻量属性动画。 */
public final class MotionKit {

    private MotionKit() {
    }

    public static Interpolator enter(int motionStyle) {
        if (motionStyle == 3) return new OvershootInterpolator(0.8f);
        if (motionStyle == 1) return new OvershootInterpolator(0.55f);
        if (motionStyle == 4) return new PathInterpolator(0.22f, 1f, 0.36f, 1f);
        return new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    }

    public static Interpolator value(int motionStyle) {
        if (motionStyle == 4) return new PathInterpolator(0.22f, 1f, 0.36f, 1f);
        return new DecelerateInterpolator(1.2f);
    }

    public static void enterCard(View v, int delayMs, int distancePx, long duration, int style) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        v.setScaleX(0.985f);
        v.setScaleY(0.985f);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration)
                .start();
    }

    public static void fadeUp(View v, int delayMs, int distancePx, long duration, int style) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration)
                .start();
    }
}
