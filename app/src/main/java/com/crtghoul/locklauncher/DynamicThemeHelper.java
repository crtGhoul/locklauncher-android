package com.crtghoul.locklauncher;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

/**
 * Material You Dynamic Theming engine.
 * Extracts dominant colors from the live user wallpaper (API 27+)
 * and harmonizes accent colors for search bars, cards, and buttons.
 */
public class DynamicThemeHelper {

    public static class ThemeColors {
        public final int primaryAccent;
        public final int subtleGlassTint;
        public final int cardBackground;

        public ThemeColors(int primaryAccent, int subtleGlassTint, int cardBackground) {
            this.primaryAccent = primaryAccent;
            this.subtleGlassTint = subtleGlassTint;
            this.cardBackground = cardBackground;
        }
    }

    public static ThemeColors extractTheme(Context ctx) {
        int defaultAccent = 0xFF5865F2; // Lock Launcher classic indigo accent
        int defaultGlass = 0xCC101223;
        int defaultCard = 0xDD1B1F31;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(ctx);
                if (wm != null) {
                    WallpaperColors colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if (colors != null) {
                        int primary = colors.getPrimaryColor().toArgb();
                        // Adjust lightness to ensure high contrast against dark theme
                        float[] hsv = new float[3];
                        Color.colorToHSV(primary, hsv);
                        hsv[1] = Math.min(hsv[1], 0.75f); // Cap saturation
                        hsv[2] = Math.max(hsv[2], 0.65f); // Ensure high brightness
                        int adjustedPrimary = Color.HSVToColor(hsv);

                        int glass = Color.argb(204,
                                (int) (Color.red(primary) * 0.2f + 16),
                                (int) (Color.green(primary) * 0.2f + 18),
                                (int) (Color.blue(primary) * 0.2f + 35));

                        int card = Color.argb(220,
                                (int) (Color.red(primary) * 0.25f + 25),
                                (int) (Color.green(primary) * 0.25f + 28),
                                (int) (Color.blue(primary) * 0.25f + 45));

                        return new ThemeColors(adjustedPrimary, glass, card);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ThemeColors(defaultAccent, defaultGlass, defaultCard);
    }
}
