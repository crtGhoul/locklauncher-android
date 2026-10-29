package com.crtghoul.locklauncher;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Android translation of the requested {@code navigator.vibrate([8])}.
 *
 * <p>navigator.vibrate does not exist on Android. The native equivalents,
 * in order of preference, are:
 * 1. {@link View#performHapticFeedback(int)} — system-managed, respects
 *    the user's haptics settings, and is what HomeActivity, FolderActivity
 *    and AppPickerActivity already use (LONG_PRESS, VIRTUAL_KEY,
 *    KEYBOARD_TAP). Prefer this whenever a View is available.
 * 2. {@link Vibrator} with a one-shot {@link VibrationEffect} (API 26+)
 *    — only as a fallback when no View is available (e.g. a Service
 *    animating an overlay window). The 8ms duration from the request is
 *    kept verbatim here, at DEFAULT_AMPLITUDE, not maximum.
 *
 * <p>All helpers are gated by a user toggle, default ON, stored in the
 * existing "dock" SharedPreferences (the same prefs file IslandService
 * and ShortcutStore already use) under "motion_haptics_enabled". Turning
 * it off must silence every helper in this file, including the fallback.
 */
public final class HapticHelper {

    private static final String PREFS = "dock";
    private static final String KEY_ENABLED = "motion_haptics_enabled";

    private HapticHelper() {}

    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return true;
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, true);
        } catch (Exception ignored) {
            return true;
        }
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        if (ctx == null) return;
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Exception ignored) {
        }
    }

    /** Light tick for slot taps, island expansion, folder launches. */
    public static void tick(View view) {
        if (view == null) return;
        if (!isEnabled(view.getContext())) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {
        }
    }

    /** Confirmation tick for a completed action (e.g. a snap landing). */
    public static void confirm(View view) {
        if (view == null) return;
        if (!isEnabled(view.getContext())) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception ignored) {
        }
    }

    /** Long-press tick, matching existing HomeActivity/FolderActivity use. */
    public static void longPress(View view) {
        if (view == null) return;
        if (!isEnabled(view.getContext())) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignored) {
        }
    }

    /**
     * Fallback only: 8ms one-shot vibration when no View is available
     * (for example, IslandService animating an overlay). Requires the
     * VIBRATE permission, which is normal-level and auto-granted; if the
     * Vibrator service is absent this is a silent no-op.
     */
    public static void vibrateOneShot(Context ctx, long millis) {
        if (ctx == null || !isEnabled(ctx)) return;
        try {
            Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        millis, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(millis);
            }
        } catch (Exception ignored) {
        }
    }

    /** The requested 8ms pulse, via the fallback path. */
    public static void pulse8ms(Context ctx) {
        vibrateOneShot(ctx, 8L);
    }
}
