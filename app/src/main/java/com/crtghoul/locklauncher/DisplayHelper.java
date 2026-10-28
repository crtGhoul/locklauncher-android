package com.crtghoul.locklauncher;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/**
 * Enforces maximum native refresh rate (120Hz) and maximum hardware-accelerated
 * rendering on modern high-refresh-rate displays (e.g. OnePlus 13 LTPO AMOLED).
 * Eliminates the default 60Hz frame throttling imposed by OxygenOS/ColorOS.
 */
public class DisplayHelper {

    public static void enforceHighRefreshRate(Activity activity) {
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            if (window == null) return;

            // Ensure window hardware acceleration is fully active
            window.setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

            // Android 11+ (API 30+) display mode selection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Display display = activity.getDisplay();
                if (display != null) {
                    Display.Mode[] modes = display.getSupportedModes();
                    Display.Mode highestFpsMode = null;
                    for (Display.Mode mode : modes) {
                        if (highestFpsMode == null || mode.getRefreshRate() > highestFpsMode.getRefreshRate()) {
                            highestFpsMode = mode;
                        }
                    }
                    if (highestFpsMode != null && highestFpsMode.getRefreshRate() >= 89.0f) {
                        WindowManager.LayoutParams lp = window.getAttributes();
                        lp.preferredDisplayModeId = highestFpsMode.getModeId();
                        lp.preferredRefreshRate = highestFpsMode.getRefreshRate();
                        window.setAttributes(lp);
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0 - 10.0 preferredRefreshRate
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.preferredRefreshRate = 120.0f;
                window.setAttributes(lp);
            }
        } catch (Exception ignored) {
        }
    }
}
