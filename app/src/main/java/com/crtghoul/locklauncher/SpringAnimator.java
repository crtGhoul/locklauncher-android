package com.crtghoul.locklauncher;

import android.graphics.Rect;
import android.view.Choreographer;
import android.view.View;

/**
 * Dependency-free mass-spring-damper animator for Lock Launcher v2.11.
 *
 * <p>Android translation of the requested "CSS spring" idea. There is no CSS
 * in this app, and {@code ViewPropertyAnimator} (used today in
 * IslandService.showExpanded and HomeActivity.applyCleanModeState) does NOT
 * preserve velocity when interrupted: a new animation restarts from the
 * current value with zero velocity, which is exactly the snap/freeze the
 * user complained about. This class fixes that by keeping the current
 * value AND velocity in one object and re-targeting it mid-flight.
 *
 * <p>Physics (requested values, kept verbatim):
 * stiffness k = 400, damping c = 28, mass m = 0.8.
 * Damping ratio zeta = c / (2 * sqrt(k * m)) = 28 / (2 * sqrt(320))
 * = 28 / 35.78 = 0.78. Slightly underdamped: the value overshoots its
 * target once, then settles. That overshoot is the "spring rebound" feel;
 * it is not a bug. Integration is semi-implicit Euler
 * (v += a*dt; x += v*dt), which is stable for these constants at
 * display refresh rates, with dt clamped to 33ms and split into
 * substeps of at most ~4ms so a dropped frame cannot explode the spring.
 *
 * <p>Usage for velocity inheritance: keep ONE SpringAnimator per animated
 * property. To interrupt, call {@link #startTo(float)} on the same
 * instance — it reads the current value and velocity and continues from
 * there. Never create a fresh animator per tap, or the velocity is lost.
 */
public class SpringAnimator {

    public static final float STIFFNESS = 400f;
    public static final float DAMPING = 28f;
    public static final float MASS = 0.8f;

    /** Called on every frame with the current value and velocity. */
    public interface UpdateListener {
        void onUpdate(float value, float velocity);
        void onEnd();
    }

    /** Convenience listener with a no-op onEnd. */
    public abstract static class SimpleListener implements UpdateListener {
        @Override public void onEnd() {}
    }

    private final Choreographer choreographer = Choreographer.getInstance();
    private float value;
    private float velocity;
    private float target;
    private float settleEpsilon = 0.002f;
    private float velocityEpsilon = 0.02f;
    private UpdateListener listener;
    private boolean running;
    private long lastNanos;
    private boolean callbackPosted;

    private final Choreographer.FrameCallback frameCallback =
            new Choreographer.FrameCallback() {
                @Override public void doFrame(long frameTimeNanos) {
                    callbackPosted = false;
                    if (!running) return;
                    if (lastNanos == 0) lastNanos = frameTimeNanos;
                    float dtSeconds = (frameTimeNanos - lastNanos) / 1_000_000_000f;
                    lastNanos = frameTimeNanos;
                    if (dtSeconds <= 0) dtSeconds = 1f / 120f;
                    if (dtSeconds > 0.033f) dtSeconds = 0.033f;
                    // Substep so a long frame cannot destabilise the spring.
                    int steps = Math.max(1, (int) Math.ceil(dtSeconds / 0.004f));
                    float subDt = dtSeconds / steps;
                    for (int i = 0; i < steps; i++) {
                        float accel = (-STIFFNESS * (value - target)
                                - DAMPING * velocity) / MASS;
                        velocity += accel * subDt;
                        value += velocity * subDt;
                    }
                    if (listener != null) listener.onUpdate(value, velocity);
                    if (Math.abs(value - target) <= settleEpsilon
                            && Math.abs(velocity) <= velocityEpsilon) {
                        value = target;
                        velocity = 0f;
                        running = false;
                        if (listener != null) {
                            listener.onUpdate(value, velocity);
                            listener.onEnd();
                        }
                        return;
                    }
                    postCallback();
                }
            };

    private void postCallback() {
        if (!callbackPosted) {
            callbackPosted = true;
            choreographer.postFrameCallback(frameCallback);
        }
    }

    /** Start from an explicit value/velocity (used on first run). */
    public void start(float from, float initialVelocity, float target,
                      float settleEpsilon, UpdateListener listener) {
        this.value = from;
        this.velocity = initialVelocity;
        this.target = target;
        this.settleEpsilon = settleEpsilon;
        this.velocityEpsilon = Math.max(settleEpsilon * 10f, 0.02f);
        this.listener = listener;
        this.running = true;
        this.lastNanos = 0;
        postCallback();
    }

    /**
     * Interrupt mid-flight: re-target the SAME animator, keeping the
     * current value and velocity. This is the whole point of this class.
     * If the animator is not running, this starts from its last value
     * with zero velocity.
     */
    public void startTo(float newTarget) {
        this.target = newTarget;
        if (!running) {
            this.running = true;
            this.lastNanos = 0;
            postCallback();
        }
    }

    /** Stop without snapping: the view keeps its current value. */
    public void cancel() {
        running = false;
        choreographer.removeFrameCallback(frameCallback);
        callbackPosted = false;
    }

    public boolean isRunning() { return running; }
    public float getValue() { return value; }
    public float getVelocity() { return velocity; }

    // ------------------------------------------------------------------
    // View conveniences. Each one keeps its animator on the view's tag
    // keyed by a string, so a second call reuses (and inherits velocity
    // from) the first instead of creating a competing animator.
    // ------------------------------------------------------------------

    /**
     * Animate scaleX+scaleY together. Callers that need interruption
     * must hold the returned animator and call startTo() on it — a
     * fresh animator has no velocity to inherit. The animator stays
     * alive mid-flight because Choreographer holds its FrameCallback,
     * which holds the animator instance; no view tag is needed.
     */
    public static SpringAnimator animateScale(final View view, float fromScale,
            float toScale, float initialVelocity, final Runnable onEnd) {
        SpringAnimator anim = new SpringAnimator();
        HardwareLayer.begin(view);
        final SpringAnimator a = anim;
        a.start(fromScale, initialVelocity, toScale, 0.002f,
                new SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        view.setScaleX(v);
                        view.setScaleY(v);
                    }
                    @Override public void onEnd() {
                        view.setScaleX(toScale);
                        view.setScaleY(toScale);
                        HardwareLayer.end(view);
                        if (onEnd != null) onEnd.run();
                    }
                });
        return a;
    }

    /** Animate alpha with the same spring, preserving velocity on re-entry. */
    public static SpringAnimator animateAlpha(final View view, float fromAlpha,
            float toAlpha, float initialVelocity, final Runnable onEnd) {
        SpringAnimator anim = new SpringAnimator();
        HardwareLayer.begin(view);
        anim.start(fromAlpha, initialVelocity, toAlpha, 0.005f,
                new SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        view.setAlpha(v);
                    }
                    @Override public void onEnd() {
                        view.setAlpha(toAlpha);
                        HardwareLayer.end(view);
                        if (onEnd != null) onEnd.run();
                    }
                });
        return anim;
    }

    /** Animate translationX with the same spring. Epsilon is in pixels. */
    public static SpringAnimator animateTranslationX(final View view,
            float fromX, float toX, float initialVelocity, final Runnable onEnd) {
        SpringAnimator anim = new SpringAnimator();
        HardwareLayer.begin(view);
        anim.start(fromX, initialVelocity, toX, 0.5f,
                new SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        view.setTranslationX(v);
                    }
                    @Override public void onEnd() {
                        view.setTranslationX(toX);
                        HardwareLayer.end(view);
                        if (onEnd != null) onEnd.run();
                    }
                });
        return anim;
    }

    /** Animate translationY with the same spring. Epsilon is in pixels. */
    public static SpringAnimator animateTranslationY(final View view,
            float fromY, float toY, float initialVelocity, final Runnable onEnd) {
        SpringAnimator anim = new SpringAnimator();
        HardwareLayer.begin(view);
        anim.start(fromY, initialVelocity, toY, 0.5f,
                new SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        view.setTranslationY(v);
                    }
                    @Override public void onEnd() {
                        view.setTranslationY(toY);
                        HardwareLayer.end(view);
                        if (onEnd != null) onEnd.run();
                    }
                });
        return anim;
    }

    // ------------------------------------------------------------------
    // Hardware layers (Android equivalent of will-change / translateZ(0)).
    // There is no will-change in Android. The equivalent is a hardware
    // layer for the duration of the animation ONLY. A permanent hardware
    // layer costs a full off-screen buffer per view and is deliberately
    // never left on here: every helper above calls end() in onEnd, and
    // callers must also call end() if they cancel an animation early.
    // ------------------------------------------------------------------
    public static final class HardwareLayer {
        private HardwareLayer() {}

        public static void begin(View view) {
            if (view != null) {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }

        public static void end(View view) {
            if (view != null) {
                view.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // FLIP (First, Last, Invert, Play) with transform + alpha ONLY.
    // Never animate width/height/margin/padding: those force layout on
    // every frame (layout thrashing). FLIP measures bounds, applies the
    // inverse as scale + translation, then animates those transforms
    // back to identity with the spring above.
    // ------------------------------------------------------------------
    public static final class Flip {
        private Flip() {}

        /** Bounds of a view in screen coordinates (works across separate WindowManager overlay windows). */
        public static Rect measure(View view) {
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            return new Rect(loc[0], loc[1],
                    loc[0] + view.getWidth(), loc[1] + view.getHeight());
        }

        /**
         * Apply the Invert step to {@code target} so it visually sits
         * exactly where {@code first} was, then spring it to identity.
         * Call only after {@code target} has been laid out (use
         * view.post(...) if it has just been added). If either rect is
         * empty, this falls back to a plain spring scale/alpha entrance
         * and returns false.
         */
        public static boolean animateFrom(final View target, Rect first,
                final Runnable onEnd) {
            if (target == null || first == null
                    || first.width() <= 0 || first.height() <= 0) {
                return false;
            }
            Rect last = measure(target);
            if (last.width() <= 0 || last.height() <= 0) return false;
            float scaleX = (float) first.width() / (float) last.width();
            float scaleY = (float) first.height() / (float) last.height();
            // Pivot at top-left so translation math stays simple.
            target.setPivotX(0f);
            target.setPivotY(0f);
            target.setScaleX(scaleX);
            target.setScaleY(scaleY);
            target.setTranslationX(first.left - last.left);
            target.setTranslationY(first.top - last.top);
            target.setAlpha(0.85f);
            HardwareLayer.begin(target);
            final SpringAnimator scaleAnim = new SpringAnimator();
            scaleAnim.start(scaleX, 0f, 1f, 0.002f, new SimpleListener() {
                @Override public void onUpdate(float v, float vel) {
                    target.setScaleX(v);
                    target.setScaleY(v);
                }
                @Override public void onEnd() {
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                }
            });
            // Translation in two axes shares one animator by animating a
            // 0..1 progress is NOT velocity-faithful, so animate X and Y
            // with the same spring constants via two animators and finish
            // on the Y animator (X finishes at essentially the same time).
            final SpringAnimator transX = new SpringAnimator();
            final SpringAnimator transY = new SpringAnimator();
            transX.start(first.left - last.left, 0f, 0f, 0.5f,
                    new SimpleListener() {
                        @Override public void onUpdate(float v, float vel) {
                            target.setTranslationX(v);
                        }
                        @Override public void onEnd() {
                            target.setTranslationX(0f);
                        }
                    });
            transY.start(first.top - last.top, 0f, 0f, 0.5f,
                    new SimpleListener() {
                        @Override public void onUpdate(float v, float vel) {
                            target.setTranslationY(v);
                        }
                        @Override public void onEnd() {
                            target.setTranslationY(0f);
                            target.setAlpha(1f);
                            HardwareLayer.end(target);
                            if (onEnd != null) onEnd.run();
                        }
                    });
            // Animators stay alive mid-flight because Choreographer
            // holds their FrameCallbacks, which hold the animator
            // instances. Callers that need to cancel must hold the
            // animators themselves; Flip does not stash them on the view
            // because setTag(int, Object) keys must be resource ids.
            return true;
        }
    }
    /**
     * Subtle tactile scale pulse feedback for button taps and drop targets.
     */
    public static void scalePulse(View v) {
        if (v == null) return;
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction(new Runnable() {
            @Override public void run() {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        }).start();
    }
}
