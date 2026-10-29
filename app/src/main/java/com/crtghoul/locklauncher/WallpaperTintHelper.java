package com.crtghoul.locklauncher;

import android.content.Context;
import android.graphics.Color;

/**
 * Monochromatic Material You tinting, extending (not duplicating)
 * DynamicThemeHelper.
 *
 * <p>DynamicThemeHelper.extractTheme already does the wallpaper
 * extraction (WallpaperManager.getWallpaperColors, API 27+, HSV clamp
 * with saturation capped at 0.75 and value floored at 0.65, classic
 * indigo fallback 0xFF5865F2). This helper does NOT re-extract the
 * wallpaper. It adds the two things that helper lacks:
 * 1. A monochromatic variant: the wallpaper accent with saturation
 *    removed (grey), value adjusted until text on it meets contrast.
 * 2. A WCAG contrast check (relative luminance, ratio
 *    (L1 + 0.05) / (L2 + 0.05)), requiring >= 4.5 for normal text,
 *    with automatic white/black text selection.
 *
 * <p>"Perfect contrast ratios" in the request is therefore translated
 * to a checked guarantee, not a slogan: {@link #chooseTextColor(int)}
 * returns the colour (white or black) with the higher ratio, and
 * {@link #meetsContrast(int, int)} lets callers verify 4.5 before
 * shipping a tint. If neither white nor black reaches 4.5 on a
 * mid-grey background, the helper darkens/lightens the background
 * itself in {@link #monochromeBackground(int)} until one of them does.
 */
public final class WallpaperTintHelper {

    /** WCAG AA threshold for normal text. */
    public static final double MIN_CONTRAST = 4.5;

    private WallpaperTintHelper() {}

    private static double relativeLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static double contrastRatio(int foreground, int background) {
        double l1 = relativeLuminance(foreground);
        double l2 = relativeLuminance(background);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean meetsContrast(int foreground, int background) {
        return contrastRatio(foreground, background) >= MIN_CONTRAST;
    }

    /** White if it contrasts better, black otherwise. Never returns a third colour. */
    public static int chooseTextColor(int background) {
        double whiteRatio = contrastRatio(0xFFFFFFFF, background);
        double blackRatio = contrastRatio(0xFF000000, background);
        return (whiteRatio >= blackRatio) ? 0xFFFFFFFF : 0xFF000000;
    }

    /** Wallpaper accent from the existing helper, never re-extracted here. */
    public static int wallpaperAccent(Context ctx) {
        try {
            DynamicThemeHelper.ThemeColors theme =
                    DynamicThemeHelper.extractTheme(ctx);
            if (theme != null) return theme.primaryAccent;
        } catch (Exception ignored) {
        }
        return 0xFF5865F2;
    }

    /**
     * Monochromatic background derived from the wallpaper accent:
     * saturation removed, value nudged until white or black text
     * reaches 4.5. Returns an opaque ARGB colour. If no value in
     * [0.15, 0.95] satisfies 4.5 (it always does at the extremes),
     * returns the darkest tried value.
     */
    public static int monochromeBackground(Context ctx) {
        int accent = wallpaperAccent(ctx);
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = 0f; // monochromatic: no hue/saturation
        // Try dark first (Lock Launcher is a dark UI), then light.
        for (float v = 0.15f; v <= 0.95f; v += 0.05f) {
            hsv[2] = v;
            int bg = Color.HSVToColor(hsv);
            if (meetsContrast(0xFFFFFFFF, bg) || meetsContrast(0xFF000000, bg)) {
                return bg;
            }
        }
        hsv[2] = 0.15f;
        return Color.HSVToColor(hsv);
    }

    /**
     * Full monochromatic pair: background + the text colour that
     * meets 4.5 on it. Returns int[2] = {background, textColor}.
     */
    public static int[] monochromePair(Context ctx) {
        int bg = monochromeBackground(ctx);
        int text = chooseTextColor(bg);
        return new int[]{bg, text};
    }
}
