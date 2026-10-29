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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.preferredRefreshRate = 120.0f;
                try {
                    lp.getClass().getField("preferredMinDisplayRefreshRate").setFloat(lp, 120.0f);
                    lp.getClass().getField("preferredMaxDisplayRefreshRate").setFloat(lp, 120.0f);
                } catch (Throwable ignored) {}
                window.setAttributes(lp);
            }
        } catch (Exception ignored) {
        }
    }
}
