package com.crtghoul.locklauncher;

import android.graphics.PointF;
import android.view.View;

import java.util.List;

/**
 * Magnetic drag-and-drop snapping for the Dynamic Island Organize mode.
 *
 * <p>Measured integration constraint: IslandService v2.11 has NO
 * drag-and-drop organizer. Organizing is a dialog
 * (showIslandOrganizerDialog: Add / Sort A-Z / Clear All / per-item
 * Manage -> showIslandAppMenu) and the deck cells only have click
 * (launch) and long-press (menu) handlers (showExpanded grid cells).
 * There is no draggable island item to snap today. This helper is
 * therefore a ready-to-wire primitive, not a behaviour change: the
 * integration doc shows exactly where a drag path would have to be
 * added (a long-press drag on deck cells, or the HomeActivity drag
 * pattern already used for home slots) before snapping can run.
 * Do not claim the island "has magnetic snapping" until that drag
 * path exists and is tested on device.
 *
 * <p>Behaviour: while dragging, the helper tracks the dragged view's
 * centre in its parent's coordinates, finds the nearest slot centre,
 * and — only when the nearest slot changes — springs the view's
 * translation toward that slot centre and fires one haptic tick.
 * On release it springs to the nearest centre (or back to the origin
 * if no slot is within snapRadius) and reports the snapped index via
 * the callback. Translation only; never layout position, width, or
 * height. Velocity is inherited because the X and Y spring animators
 * are held for the whole drag and re-targeted with startTo().
 */
public final class MagneticSnapHelper {

    /** Called when a drag settles on a slot, or -1 if it returns home. */
    public interface SnapListener {
        void onSnap(int slotIndex);
    }

    private MagneticSnapHelper() {}

    /** Nearest slot centre to (x, y), or -1 if none is within radius. */
    public static int nearestSlot(float x, float y,
            List<PointF> slotCentres, float snapRadiusPx) {
        if (slotCentres == null) return -1;
        int best = -1;
        float bestDistSq = snapRadiusPx * snapRadiusPx;
        for (int i = 0; i < slotCentres.size(); i++) {
            PointF c = slotCentres.get(i);
            if (c == null) continue;
            float dx = x - c.x;
            float dy = y - c.y;
            float dSq = dx * dx + dy * dy;
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                best = i;
            }
        }
        return best;
    }

    /**
     * A drag session for one dragged view. Hold one session per drag;
     * do not create a new session per move event, or velocity and
     * snap-change state are lost.
     */
    public static final class Session {
        private final View dragged;
        private final float originX;
        private final float originY;
        private SpringAnimator animX;
        private SpringAnimator animY;
        private int lastSnapped = -1;

        public Session(View dragged) {
            this.dragged = dragged;
            this.originX = dragged.getTranslationX();
            this.originY = dragged.getTranslationY();
        }

        /**
         * Call on every drag-move with the dragged centre in parent
         * coordinates and the slot centres in the same coordinates.
         * Springs toward the nearest slot only when it changes, so
         * the view does not jitter between two equidistant slots.
         */
        public void onDragMove(float centreX, float centreY,
                List<PointF> slotCentres, float snapRadiusPx) {
            if (dragged == null) return;
            int nearest = nearestSlot(centreX, centreY, slotCentres, snapRadiusPx);
            if (nearest != lastSnapped) {
                lastSnapped = nearest;
                if (nearest >= 0) {
                    PointF target = slotCentres.get(nearest);
                    // Target translation = slot centre - view centre at rest.
                    // The caller passes centres in parent coordinates and
                    // the view's rest centre is (left + width/2,
                    // top + height/2) in the same coordinates; the
                    // helper approximates with the view's current
                    // translation plus the delta from its current centre.
                    // For exactness, callers should compute the target
                    // translation themselves and use snapTo() directly.
                    float targetTx = dragged.getTranslationX()
                            + (target.x - centreX);
                    float targetTy = dragged.getTranslationY()
                            + (target.y - centreY);
                    springTo(targetTx, targetTy);
                    HapticHelper.tick(dragged);
                }
            }
        }

        /** Spring to an absolute translation, inheriting velocity. */
        public void springTo(float targetTx, float targetTy) {
            if (animX == null) {
                animX = new SpringAnimator();
                animX.start(dragged.getTranslationX(), 0f, targetTx, 0.5f,
                        translationListener(true));
            } else {
                animX.startTo(targetTx);
            }
            if (animY == null) {
                animY = new SpringAnimator();
                animY.start(dragged.getTranslationY(), 0f, targetTy, 0.5f,
                        translationListener(false));
            } else {
                animY.startTo(targetTy);
            }
        }

        private SpringAnimator.UpdateListener translationListener(final boolean isX) {
            return new SpringAnimator.SimpleListener() {
                @Override public void onUpdate(float v, float vel) {
                    if (isX) dragged.setTranslationX(v);
                    else dragged.setTranslationY(v);
                }
            };
        }

        /**
         * Release: snap to the nearest slot centre's translation, or
         * return to the drag origin if none is within radius.
         * The listener receives the snapped slot index, or -1.
         */
        public void onRelease(float centreX, float centreY,
                List<PointF> slotCentres, float snapRadiusPx,
                SnapListener listener) {
            int nearest = nearestSlot(centreX, centreY, slotCentres, snapRadiusPx);
            if (nearest >= 0) {
                PointF target = slotCentres.get(nearest);
                float targetTx = dragged.getTranslationX() + (target.x - centreX);
                float targetTy = dragged.getTranslationY() + (target.y - centreY);
                springTo(targetTx, targetTy);
                HapticHelper.confirm(dragged);
                if (listener != null) listener.onSnap(nearest);
            } else {
                springTo(originX, originY);
                if (listener != null) listener.onSnap(-1);
            }
            lastSnapped = -1;
        }

        public void cancel() {
            if (animX != null) animX.cancel();
            if (animY != null) animY.cancel();
        }
    }
}
