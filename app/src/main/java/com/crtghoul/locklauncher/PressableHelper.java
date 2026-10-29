package com.crtghoul.locklauncher;

import android.view.MotionEvent;
import android.view.View;

/**
 * Touch-down compression for icon views, Android-native.
 *
 * <p>Requested behaviour, translated: every icon scales to 0.92 on
 * pointer-down with "instant 0ms response", rebounding with a spring
 * overshoot to 1.0 on release. There is no pointer-down CSS here; the
 * equivalent is a touch listener that sets scale synchronously in
 * ACTION_DOWN (no animator, no post delay — that is what "0ms" means
 * in this codebase) and runs {@link SpringAnimator} (k=400, c=28,
 * m=0.8, zeta ~0.78, so it overshoots 1.0 once) on ACTION_UP/CANCEL.
 *
 * <p>This unifies the ad-hoc version already in
 * HomeActivity.launchOrHandleShortcut, which does a 40ms scale-down
 * followed by a 120ms OvershootInterpolator(2.0) rebound: that version
 * is neither instant on down nor velocity-preserving. Replace those
 * call sites with {@link #attach(View, View.OnClickListener)}; do not
 * keep both, or the two animations will fight over scaleX/scaleY.
 *
 * <p>Velocity inheritance: the release spring is held per view. If the
 * user taps again mid-rebound, ACTION_DOWN cancels the spring (the
 * view keeps its current scale) and sets 0.92 directly, and the next
 * release springs from whatever scale the view is actually at.
 */
public final class PressableHelper {

    private static final float PRESSED_SCALE = 0.92f;
    private static final float REST_SCALE = 1.0f;

    private PressableHelper() {}

    /**
     * Attach press behaviour + click handling to an icon view.
     * The supplied click listener fires on ACTION_UP only if the finger
     * is still inside the view and the gesture was not cancelled.
     * Returns the touch listener, for callers that need to remove it.
     */
    public static View.OnTouchListener attach(final View view,
            final View.OnClickListener onClick) {
        final SpringAnimator[] releaseSpring = new SpringAnimator[1];
        View.OnTouchListener listener = new View.OnTouchListener() {
            private boolean pressed = false;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pressed = true;
                        if (releaseSpring[0] != null) {
                            releaseSpring[0].cancel();
                            SpringAnimator.HardwareLayer.end(v);
                            releaseSpring[0] = null;
                        }
                        // Instant: no animator, no delay.
                        v.setScaleX(PRESSED_SCALE);
                        v.setScaleY(PRESSED_SCALE);
                        return true;
                    case MotionEvent.ACTION_UP: {
                        if (!pressed) return false;
                        pressed = false;
                        final float startScale = v.getScaleX();
                        releaseSpring[0] = new SpringAnimator();
                        SpringAnimator.HardwareLayer.begin(v);
                        final SpringAnimator spring = releaseSpring[0];
                        spring.start(startScale, 0f, REST_SCALE, 0.002f,
                                new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float val, float vel) {
                                        v.setScaleX(val);
                                        v.setScaleY(val);
                                    }
                                    @Override public void onEnd() {
                                        v.setScaleX(REST_SCALE);
                                        v.setScaleY(REST_SCALE);
                                        SpringAnimator.HardwareLayer.end(v);
                                    }
                                });
                        // Click only if the finger lifted inside the view.
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && x <= v.getWidth()
                                && y >= 0 && y <= v.getHeight()
                                && onClick != null) {
                            HapticHelper.tick(v);
                            onClick.onClick(v);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        pressed = false;
                        final float cancelScale = v.getScaleX();
                        releaseSpring[0] = new SpringAnimator();
                        SpringAnimator.HardwareLayer.begin(v);
                        releaseSpring[0].start(cancelScale, 0f, REST_SCALE,
                                0.002f, new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float val, float vel) {
                                        v.setScaleX(val);
                                        v.setScaleY(val);
                                    }
                                    @Override public void onEnd() {
                                        v.setScaleX(REST_SCALE);
                                        v.setScaleY(REST_SCALE);
                                        SpringAnimator.HardwareLayer.end(v);
                                    }
                                });
                        return true;
                    default:
                        return false;
                }
            }
        };
        view.setOnTouchListener(listener);
        return listener;
    }

    /** Instant press-down, for callers that manage their own listener. */
    public static void pressDown(View view) {
        if (view == null) return;
        view.setScaleX(PRESSED_SCALE);
        view.setScaleY(PRESSED_SCALE);
    }

    /** Spring rebound to 1.0, for callers that manage their own listener. */
    public static SpringAnimator release(View view) {
        if (view == null) return null;
        final float startScale = view.getScaleX();
        SpringAnimator spring = new SpringAnimator();
        SpringAnimator.HardwareLayer.begin(view);
        spring.start(startScale, 0f, REST_SCALE, 0.002f,
                new SpringAnimator.SimpleListener() {
                    @Override public void onUpdate(float val, float vel) {
                        view.setScaleX(val);
                        view.setScaleY(val);
                    }
                    @Override public void onEnd() {
                        view.setScaleX(REST_SCALE);
                        view.setScaleY(REST_SCALE);
                        SpringAnimator.HardwareLayer.end(view);
                    }
                });
        return spring;
    }
}
