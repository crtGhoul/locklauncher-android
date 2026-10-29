package com.crtghoul.locklauncher;

import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.List;

/**
 * Staggered spring cascade for grids, Android-native.
 *
 * <p>Requested: a 12ms offset per item when hiding/revealing the
 * Clean Mode slots. The stagger constant is kept verbatim (12ms), but
 * the mechanism is not CSS: each item gets a posted start delay of
 * index * 12ms, then a {@link SpringAnimator} scale/alpha spring.
 * Transform + alpha only — never width/height/margin/padding.
 *
 * <p>Measured integration constraint (HomeActivity, v2.11):
 * the home grid is a {@link GridView} (R.id.home_grid) whose adapter
 * holds exactly ShortcutStore.MAX = 16 padded slots per profile
 * (ShortcutStore.loadPadded, 3 profiles), with columns from
 * ShortcutStore.homeGridColumns(). A GridView only creates views for
 * VISIBLE rows, so a cascade over "all 16" is only real if all 16
 * are on screen (the normal case on a phone home grid, but not
 * guaranteed). {@link #cascadeGridView(GridView, boolean, Runnable)}
 * therefore cascades over visible children in display order and
 * documents that off-screen children simply appear at rest state.
 * Do not try to force-create off-screen GridView children; that
 * defeats view recycling for no visual benefit.
 */
public final class StaggerHelper {

    /** Requested stagger, kept verbatim. */
    public static final long DELAY_PER_ITEM_MS = 12L;

    private StaggerHelper() {}

    /**
     * Cascade a list of views in order. Reveal: from alpha 0 / scale
     * 0.92 to alpha 1 / scale 1. Hide: to alpha 0 / scale 0.92, then
     * GONE on the last item's end (only if hideSetsGone is true).
     * Each item's spring is independent; interrupting it (a second
     * cascade starting while the first is running) cancels that
     * item's spring via the returned handles only if the caller holds
     * them — see the note in MOTION-INTEGRATION.md.
     */
    public static List<SpringAnimator> cascade(List<View> views,
            boolean reveal, final Runnable onAllDone) {
        List<SpringAnimator> handles = new ArrayList<>();
        if (views == null) return handles;
        for (int i = 0; i < views.size(); i++) {
            final View child = views.get(i);
            if (child == null) continue;
            final int index = i;
            final boolean isLast = (index == views.size() - 1);
            child.postDelayed(new Runnable() {
                @Override public void run() {
                    if (reveal) {
                        child.setVisibility(View.VISIBLE);
                        child.setAlpha(0f);
                        child.setScaleX(0.92f);
                        child.setScaleY(0.92f);
                        SpringAnimator.HardwareLayer.begin(child);
                        SpringAnimator scale = new SpringAnimator();
                        scale.start(0.92f, 0f, 1f, 0.002f,
                                new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float v, float vel) {
                                        child.setScaleX(v);
                                        child.setScaleY(v);
                                    }
                                    @Override public void onEnd() {
                                        child.setScaleX(1f);
                                        child.setScaleY(1f);
                                        SpringAnimator.HardwareLayer.end(child);
                                        if (isLast && onAllDone != null) onAllDone.run();
                                    }
                                });
                        SpringAnimator alpha = new SpringAnimator();
                        alpha.start(0f, 0f, 1f, 0.005f,
                                new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float v, float vel) {
                                        child.setAlpha(v);
                                    }
                                    @Override public void onEnd() {
                                        child.setAlpha(1f);
                                    }
                                });
                        handles.add(scale);
                        handles.add(alpha);
                    } else {
                        SpringAnimator.HardwareLayer.begin(child);
                        SpringAnimator scale = new SpringAnimator();
                        scale.start(child.getScaleX(), 0f, 0.92f, 0.002f,
                                new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float v, float vel) {
                                        child.setScaleX(v);
                                        child.setScaleY(v);
                                    }
                                    @Override public void onEnd() {
                                        child.setScaleX(0.92f);
                                        child.setScaleY(0.92f);
                                        SpringAnimator.HardwareLayer.end(child);
                                        if (isLast) {
                                            child.setVisibility(View.GONE);
                                            if (onAllDone != null) onAllDone.run();
                                        }
                                    }
                                });
                        SpringAnimator alpha = new SpringAnimator();
                        alpha.start(child.getAlpha(), 0f, 0f, 0.005f,
                                new SpringAnimator.SimpleListener() {
                                    @Override public void onUpdate(float v, float vel) {
                                        child.setAlpha(v);
                                    }
                                    @Override public void onEnd() {
                                        child.setAlpha(0f);
                                    }
                                });
                        handles.add(scale);
                        handles.add(alpha);
                    }
                }
            }, index * DELAY_PER_ITEM_MS);
        }
        return handles;
    }

    /** Cascade the visible children of a GridView in display order. */
    public static List<SpringAnimator> cascadeGridView(GridView grid,
            boolean reveal, Runnable onAllDone) {
        List<View> visible = new ArrayList<>();
        if (grid != null) {
            for (int i = 0; i < grid.getChildCount(); i++) {
                View child = grid.getChildAt(i);
                if (child != null) visible.add(child);
            }
        }
        return cascade(visible, reveal, onAllDone);
    }

    /** Cascade all children of any ViewGroup in child order. */
    public static List<SpringAnimator> cascadeViewGroup(ViewGroup group,
            boolean reveal, Runnable onAllDone) {
        List<View> children = new ArrayList<>();
        if (group != null) {
            for (int i = 0; i < group.getChildCount(); i++) {
                children.add(group.getChildAt(i));
            }
        }
        return cascade(children, reveal, onAllDone);
    }
}
