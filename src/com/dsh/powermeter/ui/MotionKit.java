package com.dsh.powermeter.ui;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import com.dsh.powermeter.Prefs;

import java.util.WeakHashMap;

/**
 * 统一动效：Q 弹入场、错峰、数值轻微回弹和按压反馈。
 * 所有动画只改变 alpha/translation/scale，不参与数据计算。
 */
public final class MotionKit {

    private static final WeakHashMap<View, Boolean> PRESS_ATTACHED = new WeakHashMap<>();

    private MotionKit() {
    }

    public static boolean reduced(int style) {
        return style == 2;
    }

    public static Interpolator enter(int motionStyle) {
        if (motionStyle == 1) return new OvershootInterpolator(1.05f);
        if (motionStyle == 3) return new OvershootInterpolator(1.25f);
        if (motionStyle == 4) return new OvershootInterpolator(0.75f);
        return new OvershootInterpolator(0.9f);
    }

    public static Interpolator value(int motionStyle) {
        if (motionStyle == 4) return new PathInterpolator(0.22f, 1f, 0.36f, 1f);
        if (motionStyle == 1 || motionStyle == 3) return new OvershootInterpolator(1.6f);
        return new DecelerateInterpolator(1.2f);
    }

    /** 卡片入场：透明 + 下移 + 轻微缩放，落地时 Q 弹回正。 */
    public static void enterCard(View v, int delayMs, int distancePx, long duration, int style) {
        if (v == null || reduced(style)) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration)
                .start();
    }

    /** 设置/详情/列表里的轻量 Q 弹出现。 */
    public static void fadeUp(View v, int delayMs, int distancePx, long duration, int style) {
        if (v == null || reduced(style)) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        v.setScaleX(0.97f);
        v.setScaleY(0.97f);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration)
                .start();
    }

    /** 可点击控件统一按压回弹；不拦截点击事件。 */
    public static void attachPress(final View view) {
        if (view == null || PRESS_ATTACHED.containsKey(view)) return;
        PRESS_ATTACHED.put(view, Boolean.TRUE);
        view.setOnTouchListener((v, event) -> {
            int motion = Prefs.get(v.getContext()).motionStyle();
            if (reduced(motion)) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(0.965f).scaleY(0.965f)
                            .setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(260)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** 通知铃铛等图标切换时的短促回弹。 */
    public static void bounceIcon(View v) {
        if (v == null) return;
        int motion = Prefs.get(v.getContext()).motionStyle();
        if (reduced(motion)) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate().scaleX(1.16f).scaleY(1.16f)
                .setDuration(110)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(280)
                        .setInterpolator(new OvershootInterpolator(2.4f)).start())
                .start();
    }
}
