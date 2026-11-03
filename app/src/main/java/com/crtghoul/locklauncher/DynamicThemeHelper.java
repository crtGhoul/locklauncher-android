package com.crtghoul.locklauncher;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.os.Build;

/**
 * Material You & Chameleon Wallpaper-Adaptive Theming Engine.
 * Extracts dominant primary, secondary, and tertiary colors from the live user
 * wallpaper (API 27+ WallpaperColors, zero storage permissions required) and
 * computes translucent wallpaper-blended icon plates, borders, and color matrices
 * so app icons seamlessly blend into the active wallpaper.
 */
public class DynamicThemeHelper {

    public static class ThemeColors {
        public final int primaryAccent;
        public final int secondaryAccent;
        public final int tertiaryAccent;
        public final int subtleGlassTint;
        public final int cardBackground;

        public ThemeColors(int primaryAccent, int secondaryAccent, int tertiaryAccent,
                           int subtleGlassTint, int cardBackground) {
            this.primaryAccent = primaryAccent;
            this.secondaryAccent = secondaryAccent;
            this.tertiaryAccent = tertiaryAccent;
            this.subtleGlassTint = subtleGlassTint;
            this.cardBackground = cardBackground;
        }
    }

    public static ThemeColors extractTheme(Context ctx) {
        int defaultAccent = 0xFF5865F2; // Lock Launcher classic indigo accent
        int defaultSecondary = 0xFF8B5CF6;
        int defaultTertiary = 0xFF06B6D4;
        int defaultGlass = 0xCC101223;
        int defaultCard = 0xDD1B1F31;

        if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(ctx);
                WallpaperColors colors = null;
                if (wm != null) {
                    colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if (colors == null) {
                        colors = wm.getWallpaperColors(WallpaperManager.FLAG_LOCK);
                    }
                }

                int rawPrimary = 0;
                int rawSecondary = 0;
                int rawTertiary = 0;

                if (colors != null) {
                    rawPrimary = colors.getPrimaryColor().toArgb();
                    if (colors.getSecondaryColor() != null) {
                        rawSecondary = colors.getSecondaryColor().toArgb();
                    }
                    if (colors.getTertiaryColor() != null) {
                        rawTertiary = colors.getTertiaryColor().toArgb();
                    }
                }

                // On Android 12+ (API 31+ / Monet), check system Material You accent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        int sysAccent = ctx.getColor(android.R.color.system_accent1_500);
                        if (sysAccent != 0) {
                            if (rawPrimary == 0) rawPrimary = sysAccent;
                        }
                    } catch (Throwable ignored) {}
                }

                if (rawPrimary != 0) {
                    int primary = normalizeAccent(rawPrimary);
                    int secondary = rawSecondary != 0 ? normalizeAccent(rawSecondary) : shiftHue(primary, 28f);
                    int tertiary = rawTertiary != 0 ? normalizeAccent(rawTertiary) : shiftHue(primary, -32f);

                    int glass = Color.argb(204,
                            (int) (Color.red(primary) * 0.24f + 14),
                            (int) (Color.green(primary) * 0.24f + 16),
                            (int) (Color.blue(primary) * 0.24f + 30));

                    int card = Color.argb(220,
                            (int) (Color.red(primary) * 0.28f + 22),
                            (int) (Color.green(primary) * 0.28f + 24),
                            (int) (Color.blue(primary) * 0.28f + 40));

                    return new ThemeColors(primary, secondary, tertiary, glass, card);
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to GlassThemeHelper's active custom accent hue/sat if WallpaperColors is unavailable
        if (ctx != null) {
            float hue = GlassThemeHelper.getAccentHue(ctx);
            float sat = Math.max(0.35f, GlassThemeHelper.getAccentSatPct(ctx) / 100f);
            int customPrimary = Color.HSVToColor(new float[]{hue, sat, 0.90f});
            int customSecondary = shiftHue(customPrimary, 30f);
            int customTertiary = shiftHue(customPrimary, -30f);
            int glass = Color.argb(204,
                    (int) (Color.red(customPrimary) * 0.22f + 14),
                    (int) (Color.green(customPrimary) * 0.22f + 16),
                    (int) (Color.blue(customPrimary) * 0.22f + 30));
            int card = Color.argb(220,
                    (int) (Color.red(customPrimary) * 0.26f + 22),
                    (int) (Color.green(customPrimary) * 0.26f + 24),
                    (int) (Color.blue(customPrimary) * 0.26f + 40));
            return new ThemeColors(customPrimary, customSecondary, customTertiary, glass, card);
        }

        return new ThemeColors(defaultAccent, defaultSecondary, defaultTertiary, defaultGlass, defaultCard);
    }

    private static int normalizeAccent(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.25f, Math.min(hsv[1], 0.78f));
        hsv[2] = Math.max(hsv[2], 0.68f);
        return Color.HSVToColor(hsv);
    }

    private static int shiftHue(int color, float deltaDegrees) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + deltaDegrees + 360f) % 360f;
        return Color.HSVToColor(hsv);
    }

    /**
     * Builds a ColorMatrix that harmonizes an app icon's RGB channels toward the
     * active wallpaper's primary/secondary palette while preserving contrast and
     * brand recognition.
     *
     * @param blendFraction 0.0f (original icon) .. 1.0f (deep chameleon wallpaper fusion)
     */
    public static ColorMatrix buildChameleonColorMatrix(Context ctx, float blendFraction) {
        float amt = Math.max(0f, Math.min(1f, blendFraction));
        ThemeColors tc = extractTheme(ctx);
        int wp = tc.primaryAccent;
        float wr = Color.red(wp) / 255f;
        float wg = Color.green(wp) / 255f;
        float wb = Color.blue(wp) / 255f;

        // Slightly desaturate conflicting extremes so the icon sits naturally inside the wallpaper
        ColorMatrix desat = new ColorMatrix();
        desat.setSaturation(1f - (amt * 0.38f));

        // Chromatic tint matrix biased toward wallpaper primary RGB
        float keep = 1f - (amt * 0.28f);
        float tintScale = amt * 0.28f;
        float offset = amt * 18f;
        ColorMatrix tint = new ColorMatrix(new float[]{
                keep + tintScale * wr, tintScale * wr * 0.4f, tintScale * wr * 0.2f, 0, offset * wr,
                tintScale * wg * 0.2f, keep + tintScale * wg, tintScale * wg * 0.4f, 0, offset * wg,
                tintScale * wb * 0.3f, tintScale * wb * 0.3f, keep + tintScale * wb, 0, offset * wb,
                0,                     0,                     0,                     1, 0
        });
        desat.postConcat(tint);
        return desat;
    }
}
