package com.dsh.powermeter.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import com.dsh.powermeter.Prefs;

import java.util.WeakHashMap;

/**
 * 五套动画风格的独立参数：
 * 0 标准=轻弹；1 增强=明显回弹；2 关闭；3 vivo=大幅吸附；4 Apple=无回弹的流体。
 */
public final class MotionKit {

    private static final WeakHashMap<View, Boolean> PRESS_ATTACHED = new WeakHashMap<>();

    public static final class Profile {
        public final int distanceDp;
        public final int staggerMs;
        public final long durationMs;
        public final float enterScale;
        public final float pressScale;
        public final long pressDownMs;
        public final long pressUpMs;
        public final float valueScale;
        public final int valueTravelDp;
        public final long valueDurationMs;
        public final boolean horizontal;

        private Profile(int distanceDp, int staggerMs, long durationMs, float enterScale,
                        float pressScale, long pressDownMs, long pressUpMs,
                        float valueScale, int valueTravelDp, long valueDurationMs,
                        boolean horizontal) {
            this.distanceDp = distanceDp;
            this.staggerMs = staggerMs;
            this.durationMs = durationMs;
            this.enterScale = enterScale;
            this.pressScale = pressScale;
            this.pressDownMs = pressDownMs;
            this.pressUpMs = pressUpMs;
            this.valueScale = valueScale;
            this.valueTravelDp = valueTravelDp;
            this.valueDurationMs = valueDurationMs;
            this.horizontal = horizontal;
        }
    }

    private MotionKit() {
    }

    public static boolean reduced(int style) {
        return style == 2;
    }

    public static Profile profile(int style) {
        switch (style) {
            case 1:
                // 增强：回弹更重、行程更长、错峰更明显
                return new Profile(20, 62, 430, 0.88f, 0.94f, 70, 320,
                        0.93f, 5, 260, false);
            case 3:
                // vivo 原子：从侧向吸附进来，缩放和回弹最大
                return new Profile(26, 72, 470, 0.84f, 0.92f, 65, 350,
                        0.92f, 6, 280, true);
            case 4:
                // Apple：无回弹，距离短、时间长、节奏软
                return new Profile(6, 28, 520, 0.975f, 0.985f, 80, 240,
                        0.99f, 1, 260, false);
            case 0:
            default:
                // 标准：轻微 Q 弹
                return new Profile(9, 32, 290, 0.955f, 0.97f, 90, 230,
                        0.98f, 2, 190, false);
        }
    }

    public static Interpolator enter(int style) {
        switch (style) {
            case 1: return new OvershootInterpolator(2.1f);
            case 3: return new OvershootInterpolator(1.6f);
            case 4: return new PathInterpolator(0.22f, 1f, 0.36f, 1f);
            default: return new OvershootInterpolator(0.8f);
        }
    }

    public static Interpolator release(int style) {
        switch (style) {
            case 1: return new OvershootInterpolator(2.8f);
            case 3: return new OvershootInterpolator(2.2f);
            case 4: return new DecelerateInterpolator();
            default: return new OvershootInterpolator(1.7f);
        }
    }

    public static void enterCards(View[] views, int style) {
        if (views == null) return;
        Profile p = profile(style);
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            if (v != null) enterCard(v, i * p.staggerMs, p.distanceDp, p.durationMs, style);
        }
    }

    public static void enterCard(View v, int delayMs, int distancePx, long duration, int style) {
        enterCard(v, delayMs, distancePx, duration, style, 0);
    }

    public static void enterCard(View v, int index, int style) {
        Profile p = profile(style);
        enterCard(v, index * p.staggerMs, p.distanceDp, p.durationMs, style, index);
    }

    private static void enterCard(View v, int delayMs, int distancePx, long duration,
                                  int style, int index) {
        if (v == null || reduced(style)) return;
        Profile p = profile(style);
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        if (p.horizontal) v.setTranslationX((index % 2 == 0 ? 1 : -1) * dp(v.getContext(), 12));
        else v.setTranslationX(0f);
        v.setScaleX(p.enterScale);
        v.setScaleY(p.enterScale);
        android.view.ViewPropertyAnimator anim = v.animate()
                .alpha(1f).translationY(0f).translationX(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration);
        anim.start();
    }

    /** 设置/详情里的轻量错峰。 */
    public static void fadeUp(View v, int index, int style) {
        if (v == null || reduced(style)) return;
        Profile p = profile(style);
        fadeUp(v, index * p.staggerMs, p.distanceDp, p.durationMs, style, index);
    }

    @Deprecated
    public static void fadeUp(View v, int delayMs, int distancePx, long duration, int style) {
        fadeUp(v, delayMs, distancePx, duration, style, 0);
    }

    private static void fadeUp(View v, int delayMs, int distancePx, long duration,
                               int style, int index) {
        if (v == null || reduced(style)) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(distancePx);
        if (style == 3) v.setTranslationX((index % 2 == 0 ? 1 : -1) * dp(v.getContext(), 8));
        else v.setTranslationX(0f);
        v.setScaleX(profile(style).enterScale + (1f - profile(style).enterScale) * 0.45f);
        v.setScaleY(v.getScaleX());
        v.animate().alpha(1f).translationY(0f).translationX(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delayMs)
                .setInterpolator(enter(style))
                .setDuration(duration)
                .start();
    }

    /** 数值变化：四种风格使用不同回弹幅度；关闭时直接更新。 */
    public static void valueChange(TextView v, String next, int style) {
        if (v == null) return;
        if (next == null) next = "--";
        if (next.contentEquals(v.getText())) return;
        v.animate().cancel();
        if (reduced(style)) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setText(next);
            return;
        }
        Profile p = profile(style);
        v.setAlpha(style == 4 ? 0.72f : 0.55f);
        v.setTranslationY(dp(v.getContext(), p.valueTravelDp));
        v.setScaleX(p.valueScale);
        v.setScaleY(p.valueScale);
        v.setText(next);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setInterpolator(style == 4 ? new PathInterpolator(0.22f, 1f, 0.36f, 1f)
                        : style == 3 ? new OvershootInterpolator(2.0f)
                        : style == 1 ? new OvershootInterpolator(1.7f)
                        : new DecelerateInterpolator(1.2f))
                .setDuration(p.valueDurationMs)
                .start();
    }

    /** 可点击控件统一按压回弹；不拦截点击事件。 */
    public static void attachPress(final View view) {
        if (view == null || PRESS_ATTACHED.containsKey(view)) return;
        PRESS_ATTACHED.put(view, Boolean.TRUE);
        view.setOnTouchListener((v, event) -> {
            int motion = Prefs.get(v.getContext()).motionStyle();
            if (reduced(motion)) return false;
            Profile p = profile(motion);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(p.pressScale).scaleY(p.pressScale)
                            .setDuration(p.pressDownMs)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(p.pressUpMs)
                            .setInterpolator(release(motion)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** 通知铃铛等图标切换时的风格化回弹。 */
    public static void bounceIcon(View v) {
        if (v == null) return;
        int motion = Prefs.get(v.getContext()).motionStyle();
        if (reduced(motion)) return;
        float peak = motion == 3 ? 1.28f : motion == 1 ? 1.22f : motion == 4 ? 1.06f : 1.14f;
        long up = motion == 4 ? 260 : 300;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate().scaleX(peak).scaleY(peak)
                .setDuration(motion == 4 ? 160 : 110)
                .setInterpolator(motion == 4 ? new DecelerateInterpolator()
                        : new DecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(up)
                        .setInterpolator(release(motion)).start())
                .start();
    }

    private static int dp(Context c, int value) {
        return UiKit.dp(c, value);
    }
}
