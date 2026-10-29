package com.crtghoul.locklauncher;

import android.graphics.Rect;
import android.view.View;

/**
 * Dynamic Island morph: collapsed pill -> 4-column deck, Android-native.
 *
 * <p>Measured from IslandService.java (v2.11) — do NOT use the requested
 * "36px pill" figure, it is wrong for this app:
 * - Collapsed pill: WRAP_CONTENT window, minWidth 92dp, minHeight 32dp,
 *   corner radius 17dp, padding 14dp/6dp, positioned at
 *   statusBarHeight + 8dp, gravity TOP|CENTER_HORIZONTAL, x/y persisted
 *   in the "dock" prefs (island_x / island_y). The pill contains NO app
 *   icons in the collapsed state (showDockPill comment says so).
 * - Expanded deck: card width = min(screenWidth - 32dp, 360dp),
 *   WRAP_CONTENT height, topMargin statusBarHeight + 8dp, corner radius
 *   22dp, GridLayout columnCount = 4, cell height 76dp, icon 44dp,
 *   scroll height = min(280dp, 84dp * rows).
 * - Current entrance (showExpanded): ViewPropertyAnimator scale
 *   0.88 -> 1.0, alpha 0 -> 1.0, 180ms, DecelerateInterpolator. No
 *   spring, no velocity inheritance, and no morph: showExpanded()
 *   calls removeView() first, destroying the pill before the card
 *   exists, so there is nothing to FLIP from.
 *
 * <p>That last point is a real integration constraint, not a detail:
 * a true FLIP morph requires BOTH views alive at the same time.
 * {@link #morphFromPill(View, View, Runnable)} therefore takes a pill
 * view and a card view that are both attached, measures both in window
 * coordinates, applies the FLIP invert to the card (scale + translation
 * only, never width/height), and springs it to identity. The caller
 * must restructure IslandService.showExpanded() to NOT call
 * removeView() before creating the card, and to remove the pill only
 * in the morph's onEnd (see MOTION-INTEGRATION.md for the exact
 * restructure). If the pill is already gone, use
 * {@link #entrance(View, Runnable)} instead — a spring scale/alpha
 * entrance, honestly labelled as an entrance, not a morph.
 *
 * <p>Window note: the pill and the deck live in different
 * WindowManager windows (WRAP_CONTENT vs MATCH_PARENT), but both are
 * gravity TOP|CENTER_HORIZONTAL at the same top offset, so their
 * window rects are comparable for FLIP purposes. The morph animates
 * the card's own scale/translation/alpha only; it never animates the
 * WindowManager params or the card's layout width/height.
 */
public final class IslandMorphHelper {

    private IslandMorphHelper() {}

    /**
     * FLIP morph from an attached pill to an attached, laid-out card.
     * Returns true if a FLIP morph ran, false if the caller should use
     * {@link #entrance(View, Runnable)} (pill missing, or card not yet
     * laid out). Call with the card already added to its window; if
     * the card has zero size, post this call to the card (card.post).
     */
    public static boolean morphFromPill(final View pill, final View card,
            final Runnable onEnd) {
        if (pill == null || card == null) return false;
        if (pill.getWidth() <= 0 || card.getWidth() <= 0) return false;
        Rect first = SpringAnimator.Flip.measure(pill);
        boolean flipped = SpringAnimator.Flip.animateFrom(card, first,
                new Runnable() {
                    @Override public void run() {
                        if (onEnd != null) onEnd.run();
                    }
                });
        if (flipped) {
            HapticHelper.tick(card);
        }
        return flipped;
    }

    /**
     * Spring entrance for the deck when no pill exists to morph from
     * (e.g. the current removeView-first flow). Scale 0.88 -> 1.0 and
     * alpha 0 -> 1.0, matching the current visual start state, but
     * with the spring (k=400, c=28, m=0.8) instead of the current
     * 180ms DecelerateInterpolator. This is an entrance, not a morph,
     * and must not be described as one.
     */
    public static void entrance(final View card, final Runnable onEnd) {
        if (card == null) return;
        card.setScaleX(0.88f);
        card.setScaleY(0.88f);
        card.setAlpha(0f);
        SpringAnimator.HardwareLayer.begin(card);
        SpringAnimator scale = new SpringAnimator();
        scale.start(0.88f, 0f, 1f, 0.002f,
                new SpringAnimator.SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        card.setScaleX(v);
                        card.setScaleY(v);
                    }
                    @Override public void onEnd() {
                        card.setScaleX(1f);
                        card.setScaleY(1f);
                        SpringAnimator.HardwareLayer.end(card);
                        if (onEnd != null) onEnd.run();
                    }
                });
        SpringAnimator alpha = new SpringAnimator();
        alpha.start(0f, 0f, 1f, 0.005f,
                new SpringAnimator.SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        card.setAlpha(v);
                    }
                    @Override public void onEnd() {
                        card.setAlpha(1f);
                    }
                });
    }

    /**
     * Collapse: spring the deck back toward the pill's scale before
     * the caller removes it. This is a scale/alpha exit, not a
     * reverse FLIP (the pill does not exist yet at collapse time in
     * the current flow), and is labelled as such in the integration doc.
     */
    public static void collapse(final View card, final Runnable onEnd) {
        if (card == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        SpringAnimator.HardwareLayer.begin(card);
        SpringAnimator scale = new SpringAnimator();
        scale.start(card.getScaleX(), 0f, 0.88f, 0.002f,
                new SpringAnimator.SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        card.setScaleX(v);
                        card.setScaleY(v);
                    }
                    @Override public void onEnd() {
                        SpringAnimator.HardwareLayer.end(card);
                        if (onEnd != null) onEnd.run();
                    }
                });
        SpringAnimator alpha = new SpringAnimator();
        alpha.start(card.getAlpha(), 0f, 0f, 0.005f,
                new SpringAnimator.SimpleListener() {
                    @Override public void onUpdate(float v, float vel) {
                        card.setAlpha(v);
                    }
                    @Override public void onEnd() {
                        card.setAlpha(0f);
                    }
                });
    }
}
