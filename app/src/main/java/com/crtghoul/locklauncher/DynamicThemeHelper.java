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
 * wallpaper (API 27+ WallpaperColors & API 31+ Monet dynamic accents) and
 * computes translucent wallpaper-blended icon plates, luminous glyph tints,
 * borders, and color matrices so app icons seamlessly blend into the active wallpaper.
 */
public class DynamicThemeHelper {

    public static class ThemeColors {
        public final int primaryAccent;
        public final int secondaryAccent;
        public final int tertiaryAccent;
        public final int subtleGlassTint;
        public final int cardBackground;
        public final int glyphTint;
        public final int plateFillColor;
        public final int plateBorderColor;

        public ThemeColors(int primaryAccent, int secondaryAccent, int tertiaryAccent,
                           int subtleGlassTint, int cardBackground,
                           int glyphTint, int plateFillColor, int plateBorderColor) {
            this.primaryAccent = primaryAccent;
            this.secondaryAccent = secondaryAccent;
            this.tertiaryAccent = tertiaryAccent;
            this.subtleGlassTint = subtleGlassTint;
            this.cardBackground = cardBackground;
            this.glyphTint = glyphTint;
            this.plateFillColor = plateFillColor;
            this.plateBorderColor = plateBorderColor;
        }

        public ThemeColors(int primaryAccent, int secondaryAccent, int tertiaryAccent,
                           int subtleGlassTint, int cardBackground) {
            this(primaryAccent, secondaryAccent, tertiaryAccent, subtleGlassTint, cardBackground,
                    computeLuminousTint(primaryAccent, true),
                    Color.argb(165, (int)(Color.red(primaryAccent)*0.32f+16), (int)(Color.green(primaryAccent)*0.32f+18), (int)(Color.blue(primaryAccent)*0.32f+28)),
                    Color.argb(110, (Color.red(primaryAccent)+255)/2, (Color.green(primaryAccent)+255)/2, (Color.blue(primaryAccent)+255)/2));
        }
    }

    public static ThemeColors extractTheme(Context ctx) {
        int defaultAccent = 0xFF5865F2; // Lock Launcher classic indigo accent
        int defaultSecondary = 0xFF8B5CF6;
        int defaultTertiary = 0xFF06B6D4;
        int defaultGlass = 0xCC101223;
        int defaultCard = 0xDD1B1F31;

        if (ctx != null) {
            int rawPrimary = 0;
            int rawSecondary = 0;
            int rawTertiary = 0;
            int rawGlyph = 0;

            // 1. On Android 12+ (API 31+ / Monet), query the live wallpaper system accents
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    int sysAccent = ctx.getColor(android.R.color.system_accent1_500);
                    if (sysAccent != 0) rawPrimary = sysAccent;
                    int sysSec = ctx.getColor(android.R.color.system_accent2_500);
                    if (sysSec != 0) rawSecondary = sysSec;
                    int sysTert = ctx.getColor(android.R.color.system_accent3_500);
                    if (sysTert != 0) rawTertiary = sysTert;
                    int sysGlyph = ctx.getColor(android.R.color.system_accent1_200);
                    if (sysGlyph != 0) rawGlyph = sysGlyph;
                } catch (Throwable ignored) {}
            }

            // 2. Query WallpaperColors directly (API 27+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    WallpaperManager wm = WallpaperManager.getInstance(ctx);
                    if (wm != null) {
                        WallpaperColors colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                        if (colors == null) {
                            colors = wm.getWallpaperColors(WallpaperManager.FLAG_LOCK);
                        }
                        if (colors != null) {
                            if (rawPrimary == 0 && colors.getPrimaryColor() != null) {
                                rawPrimary = colors.getPrimaryColor().toArgb();
                            }
                            if (rawSecondary == 0 && colors.getSecondaryColor() != null) {
                                rawSecondary = colors.getSecondaryColor().toArgb();
                            }
                            if (rawTertiary == 0 && colors.getTertiaryColor() != null) {
                                rawTertiary = colors.getTertiaryColor().toArgb();
                            }
                        }
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

                int glyph = rawGlyph != 0 ? rawGlyph : computeLuminousTint(primary, true);

                int plateFill = Color.argb(165,
                        (int) (Color.red(primary) * 0.32f + 16),
                        (int) (Color.green(primary) * 0.32f + 18),
                        (int) (Color.blue(primary) * 0.32f + 28));

                int plateBorder = Color.argb(110,
                        Math.min(255, (Color.red(glyph) + Color.red(primary)) / 2),
                        Math.min(255, (Color.green(glyph) + Color.green(primary)) / 2),
                        Math.min(255, (Color.blue(glyph) + Color.blue(primary)) / 2));

                return new ThemeColors(primary, secondary, tertiary, glass, card, glyph, plateFill, plateBorder);
            }
        }

        // Fallback to GlassThemeHelper custom primary
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
        int glyph = computeLuminousTint(customPrimary, true);
        int plateFill = Color.argb(165,
                (int) (Color.red(customPrimary) * 0.32f + 16),
                (int) (Color.green(customPrimary) * 0.32f + 18),
                (int) (Color.blue(customPrimary) * 0.32f + 28));
        int plateBorder = Color.argb(110,
                Math.min(255, (Color.red(glyph) + Color.red(customPrimary)) / 2),
                Math.min(255, (Color.green(glyph) + Color.green(customPrimary)) / 2),
                Math.min(255, (Color.blue(glyph) + Color.blue(customPrimary)) / 2));

        return new ThemeColors(customPrimary, customSecondary, customTertiary, glass, card, glyph, plateFill, plateBorder);
    }

    public static int computeLuminousTint(int color, boolean isDark) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (isDark) {
            hsv[1] = Math.max(0.18f, Math.min(hsv[1] * 0.70f, 0.45f));
            hsv[2] = 0.96f;
        } else {
            hsv[1] = Math.max(0.40f, Math.min(hsv[1], 0.85f));
            hsv[2] = 0.32f;
        }
        return Color.HSVToColor(hsv);
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
     * active wallpaper's primary/secondary palette while preserving brand glyph clarity.
     *
     * @param blendFraction 0.0f (original icon) .. 1.0f (deep chameleon wallpaper fusion)
     */
    public static ColorMatrix buildChameleonColorMatrix(Context ctx, float blendFraction) {
        float amt = Math.max(0f, Math.min(1f, blendFraction));
        ThemeColors tc = extractTheme(ctx);
        int wp = tc.glyphTint;
        float wr = Color.red(wp) / 255f;
        float wg = Color.green(wp) / 255f;
        float wb = Color.blue(wp) / 255f;

        // Desaturate conflicting raw brand colors proportional to blendFraction
        ColorMatrix desat = new ColorMatrix();
        desat.setSaturation(1f - (amt * 0.60f));

        // Chromatic tint matrix deeply biased toward wallpaper primary & secondary RGB
        float keep = 1f - (amt * 0.38f);
        float tintScale = amt * 0.62f;
        float offset = amt * 68f;
        ColorMatrix tint = new ColorMatrix(new float[]{
                keep + tintScale * wr, tintScale * wr * 0.3f, tintScale * wr * 0.2f, 0, offset * wr,
                tintScale * wg * 0.2f, keep + tintScale * wg, tintScale * wg * 0.3f, 0, offset * wg,
                tintScale * wb * 0.2f, tintScale * wb * 0.2f, keep + tintScale * wb, 0, offset * wb,
                0,                     0,                     0,                     1, 0
        });
        desat.postConcat(tint);
        return desat;
    }
}