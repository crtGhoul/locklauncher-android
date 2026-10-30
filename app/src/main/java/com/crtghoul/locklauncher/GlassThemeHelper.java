package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

/**
 * Unified Glass & Blur Theme Engine for Lock Launcher v2.20.
 *
 * Powers the Floating Island, Edge Sidebar, Notification Dock, Home Widgets,
 * and Home Search/Now-Playing surfaces together from a single set of parameters:
 * - Glass Mode: "frosted" (Frosted glass), "liquid" (Liquid glass), "classic" (Classic)
 * - Sub-theme: "light", "dark", "colored", "glow"
 * - Blur radius: 0..25 (default 6) — applies Window blurBehind on API 31+ overlays
 *   and RenderEffect on dedicated background layers
 * - Surface opacity: 0..100% (default 18% for glass, 80% for classic)
 * - Darkness: 0..100 (0.00..1.00, default 50 -> 0.5)
 * - Accent hue: 0..360° (default 200°) & Saturation: 0..100 (0.0..1.0, default 50 -> 0.5)
 * - Wallpaper Monet accent toggle
 * - Icon size inside glass: 70..130% (default 100%)
 */
public final class GlassThemeHelper {
    private static final String PREFS = "dock";

    public static final String MODE_FROSTED = "frosted";
    public static final String MODE_LIQUID = "liquid";
    public static final String MODE_CLASSIC = "classic";

    public static final String SUB_LIGHT = "light";
    public static final String SUB_DARK = "dark";
    public static final String SUB_COLORED = "colored";
    public static final String SUB_GLOW = "glow";

    private static final String KEY_GLASS_MODE = "glass_mode";
    private static final String KEY_GLASS_SUBTHEME = "glass_subtheme";
    private static final String KEY_GLASS_BLUR = "glass_blur_radius";
    private static final String KEY_GLASS_OPACITY_PCT = "glass_surface_opacity_pct";
    private static final String KEY_GLASS_DARKNESS = "glass_darkness_pct";
    private static final String KEY_GLASS_HUE = "glass_accent_hue";
    private static final String KEY_GLASS_SAT = "glass_accent_sat";
    private static final String KEY_GLASS_WALLPAPER_ACCENT = "glass_use_wallpaper_accent";
    private static final String KEY_GLASS_ICON_SCALE = "glass_icon_scale_pct";

    public static final int DEFAULT_BLUR = 6;
    public static final int DEFAULT_OPACITY_PCT = 18;
    public static final int DEFAULT_DARKNESS_PCT = 50; // 0.5
    public static final int DEFAULT_HUE = 200;
    public static final int DEFAULT_SAT_PCT = 50; // 0.5
    public static final int DEFAULT_ICON_SCALE_PCT = 100;

    private GlassThemeHelper() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- Getters & Setters ----

    public static String getMode(Context ctx) {
        String m = prefs(ctx).getString(KEY_GLASS_MODE, MODE_FROSTED);
        if (!MODE_FROSTED.equals(m) && !MODE_LIQUID.equals(m) && !MODE_CLASSIC.equals(m)) {
            return MODE_FROSTED;
        }
        return m;
    }

    public static String getGlassMode(Context ctx) {
        return getMode(ctx);
    }

    public static void setMode(Context ctx, String mode) {
        prefs(ctx).edit().putString(KEY_GLASS_MODE, mode).apply();
    }

    public static void setGlassMode(Context ctx, String mode) {
        setMode(ctx, mode);
    }

    public static String getSubTheme(Context ctx) {
        String s = prefs(ctx).getString(KEY_GLASS_SUBTHEME, SUB_DARK);
        if (!SUB_LIGHT.equals(s) && !SUB_DARK.equals(s) && !SUB_COLORED.equals(s) && !SUB_GLOW.equals(s)) {
            return SUB_DARK;
        }
        return s;
    }

    public static void setSubTheme(Context ctx, String subTheme) {
        prefs(ctx).edit().putString(KEY_GLASS_SUBTHEME, subTheme).apply();
    }

    public static int getBlurRadius(Context ctx) {
        return Math.max(0, Math.min(25, prefs(ctx).getInt(KEY_GLASS_BLUR, DEFAULT_BLUR)));
    }

    public static int getBlurLevel(Context ctx) {
        return getBlurRadius(ctx);
    }

    public static void setBlurRadius(Context ctx, int blur) {
        prefs(ctx).edit().putInt(KEY_GLASS_BLUR, Math.max(0, Math.min(25, blur))).apply();
    }

    public static void setBlurLevel(Context ctx, int blur) {
        setBlurRadius(ctx, blur);
    }

    public static void resetBlurRadius(Context ctx) {
        setBlurRadius(ctx, DEFAULT_BLUR);
    }

    public static void resetBlurLevel(Context ctx) {
        resetBlurRadius(ctx);
    }

    public static int getSurfaceOpacityPct(Context ctx) {
        if (!prefs(ctx).contains(KEY_GLASS_OPACITY_PCT)) {
            int legacyAlpha = ShortcutStore.glassOpacity(ctx);
            int pct = Math.round((legacyAlpha / 255f) * 100f);
            return Math.max(0, Math.min(100, pct));
        }
        return Math.max(0, Math.min(100, prefs(ctx).getInt(KEY_GLASS_OPACITY_PCT, DEFAULT_OPACITY_PCT)));
    }

    public static void setSurfaceOpacityPct(Context ctx, int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        prefs(ctx).edit().putInt(KEY_GLASS_OPACITY_PCT, clamped).apply();
        // Keep legacy 0..255 glass_opacity in sync so existing backup/widget code works seamlessly
        int alpha255 = Math.round((clamped / 100f) * 255f);
        ShortcutStore.setGlassOpacity(ctx, alpha255);
    }

    public static void resetSurfaceOpacityPct(Context ctx) {
        setSurfaceOpacityPct(ctx, DEFAULT_OPACITY_PCT);
    }

    /** Darkness 0..100 representing 0.00..1.00 (default 50 -> 0.5). */
    public static int getDarknessPct(Context ctx) {
        return Math.max(0, Math.min(100, prefs(ctx).getInt(KEY_GLASS_DARKNESS, DEFAULT_DARKNESS_PCT)));
    }

    public static void setDarknessPct(Context ctx, int pct) {
        prefs(ctx).edit().putInt(KEY_GLASS_DARKNESS, Math.max(0, Math.min(100, pct))).apply();
    }

    public static void resetDarknessPct(Context ctx) {
        setDarknessPct(ctx, DEFAULT_DARKNESS_PCT);
    }

    /** Hue 0..360 degrees (default 200). */
    public static int getAccentHue(Context ctx) {
        return Math.max(0, Math.min(360, prefs(ctx).getInt(KEY_GLASS_HUE, DEFAULT_HUE)));
    }

    public static int getThemeHue(Context ctx) {
        return getAccentHue(ctx);
    }

    public static void setAccentHue(Context ctx, int hue) {
        prefs(ctx).edit().putInt(KEY_GLASS_HUE, Math.max(0, Math.min(360, hue))).apply();
    }

    public static void setThemeHue(Context ctx, int hue) {
        setAccentHue(ctx, hue);
    }

    public static void resetAccentHue(Context ctx) {
        setAccentHue(ctx, DEFAULT_HUE);
    }

    public static void resetThemeHue(Context ctx) {
        resetAccentHue(ctx);
    }

    /** Saturation 0..100 representing 0.00..1.00 (default 50 -> 0.5). */
    public static int getAccentSatPct(Context ctx) {
        return Math.max(0, Math.min(100, prefs(ctx).getInt(KEY_GLASS_SAT, DEFAULT_SAT_PCT)));
    }

    public static int getSaturationPct(Context ctx) {
        return getAccentSatPct(ctx);
    }

    public static void setAccentSatPct(Context ctx, int satPct) {
        prefs(ctx).edit().putInt(KEY_GLASS_SAT, Math.max(0, Math.min(100, satPct))).apply();
    }

    public static void setSaturationPct(Context ctx, int satPct) {
        setAccentSatPct(ctx, satPct);
    }

    public static void resetAccentSatPct(Context ctx) {
        setAccentSatPct(ctx, DEFAULT_SAT_PCT);
    }

    public static void resetSaturationPct(Context ctx) {
        resetAccentSatPct(ctx);
    }

    public static boolean isUseWallpaperAccent(Context ctx) {
        return prefs(ctx).getBoolean(KEY_GLASS_WALLPAPER_ACCENT, false);
    }

    public static boolean isWallpaperAccent(Context ctx) {
        return isUseWallpaperAccent(ctx);
    }

    public static void setUseWallpaperAccent(Context ctx, boolean use) {
        prefs(ctx).edit().putBoolean(KEY_GLASS_WALLPAPER_ACCENT, use).apply();
    }

    public static void setWallpaperAccent(Context ctx, boolean use) {
        setUseWallpaperAccent(ctx, use);
    }

    /** Icon size inside glass surfaces: 70..130% (default 100%). */
    public static int getIconScalePct(Context ctx) {
        return Math.max(70, Math.min(130, prefs(ctx).getInt(KEY_GLASS_ICON_SCALE, DEFAULT_ICON_SCALE_PCT)));
    }

    public static int getIconScaleInsideGlass(Context ctx) {
        return getIconScalePct(ctx);
    }

    public static void setIconScalePct(Context ctx, int pct) {
        prefs(ctx).edit().putInt(KEY_GLASS_ICON_SCALE, Math.max(70, Math.min(130, pct))).apply();
    }

    public static void setIconScaleInsideGlass(Context ctx, int pct) {
        setIconScalePct(ctx, pct);
    }

    public static void resetIconScalePct(Context ctx) {
        setIconScalePct(ctx, DEFAULT_ICON_SCALE_PCT);
    }

    public static void resetIconScaleInsideGlass(Context ctx) {
        resetIconScalePct(ctx);
    }

    public static void resetAll(Context ctx) {
        setMode(ctx, MODE_FROSTED);
        setSubTheme(ctx, SUB_DARK);
        resetBlurRadius(ctx);
        resetSurfaceOpacityPct(ctx);
        resetDarknessPct(ctx);
        resetAccentHue(ctx);
        resetAccentSatPct(ctx);
        setUseWallpaperAccent(ctx, true);
        resetIconScalePct(ctx);
        refreshAllSurfaces(ctx);
    }

    // ---- Color & Surface Computation ----

    /** Resolves the active accent color (either Wallpaper Monet accent or custom HSV slider). */
    public static int resolveAccentColor(Context ctx) {
        if (isUseWallpaperAccent(ctx)) {
            DynamicThemeHelper.ThemeColors tc = DynamicThemeHelper.extractTheme(ctx);
            if (tc != null) return tc.primaryAccent;
        }
        float hue = getAccentHue(ctx);
        float sat = getAccentSatPct(ctx) / 100f;
        return Color.HSVToColor(new float[]{hue, sat, 0.92f});
    }

    /** Resolves the base ARGB fill color for glass surfaces based on mode, subtheme, darkness, and opacity. */
    public static int resolveSurfaceFillColor(Context ctx) {
        String mode = getMode(ctx);
        String sub = getSubTheme(ctx);
        int opacityPct = getSurfaceOpacityPct(ctx);
        float darkness = getDarknessPct(ctx) / 100f; // 0.0 .. 1.0
        int accent = resolveAccentColor(ctx);

        // Compute alpha (ensure a usable floor so touches and text remain legible)
        int minAlpha = MODE_CLASSIC.equals(mode) ? 160 : 28;
        int alpha = Math.max(minAlpha, Math.min(250, Math.round((opacityPct / 100f) * 235f + (darkness * 80f))));
        if (MODE_CLASSIC.equals(mode)) {
            alpha = Math.max(180, Math.min(255, Math.round((opacityPct / 100f) * 255f)));
        }

        int r, g, b;
        if (SUB_LIGHT.equals(sub)) {
            int l = (int) (235 - darkness * 120);
            r = l;
            g = l + 3;
            b = l + 8;
        } else if (SUB_COLORED.equals(sub) || SUB_GLOW.equals(sub)) {
            float mix = 0.38f * (1f - darkness * 0.5f);
            int baseDark = (int) ((1f - darkness) * 48f + 10f);
            r = Math.min(255, (int) (Color.red(accent) * mix + baseDark));
            g = Math.min(255, (int) (Color.green(accent) * mix + baseDark));
            b = Math.min(255, (int) (Color.blue(accent) * mix + baseDark));
        } else {
            // SUB_DARK default
            int base = (int) ((1f - darkness) * 52f + 8f);
            r = base;
            g = base + 2;
            b = base + 8;
        }
        return Color.argb(alpha, Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b)));
    }

    /** Builds a layered Drawable representing the active Glass Theme (Liquid, Frosted, or Classic). */
    public static Drawable buildGlassDrawable(Context ctx, int cornerRadiusPx) {
        String mode = getMode(ctx);
        String sub = getSubTheme(ctx);
        int fill = resolveSurfaceFillColor(ctx);
        int accent = resolveAccentColor(ctx);

        GradientDrawable base = new GradientDrawable();
        base.setColor(fill);
        base.setCornerRadius(cornerRadiusPx);

        if (MODE_CLASSIC.equals(mode)) {
            int strokeCol = SUB_COLORED.equals(sub)
                    ? applyAlpha(accent, 0x88)
                    : 0x33FFFFFF;
            base.setStroke(dp(ctx, 1), strokeCol);
            return base;
        }

        if (MODE_LIQUID.equals(mode)) {
            // Liquid Glass: refractive multi-stop specular sheen + crisp bright border
            int rimColor = (SUB_COLORED.equals(sub) || SUB_GLOW.equals(sub))
                    ? blendColors(0xFFFFFFFF, accent, 0.45f)
                    : 0xFFFFFFFF;
            base.setStroke(dp(ctx, 1), applyAlpha(rimColor, 0x78));

            GradientDrawable liquidSheen = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{
                            applyAlpha(rimColor, 0x52),
                            applyAlpha(rimColor, 0x12),
                            0x00000000,
                            applyAlpha(accent, SUB_GLOW.equals(sub) ? 0x34 : 0x18)
                    });
            liquidSheen.setCornerRadius(cornerRadiusPx);

            if (SUB_GLOW.equals(sub)) {
                GradientDrawable glow = new GradientDrawable();
                glow.setColor(applyAlpha(accent, 0x26));
                glow.setCornerRadius(cornerRadiusPx);
                return new LayerDrawable(new Drawable[]{glow, base, liquidSheen});
            }
            return new LayerDrawable(new Drawable[]{base, liquidSheen});
        }

        // MODE_FROSTED (Default Frosted Glass)
        int strokeAlpha = SUB_LIGHT.equals(sub) ? 0x66 : 0x42;
        int strokeTint = SUB_COLORED.equals(sub) ? blendColors(0xFFFFFFFF, accent, 0.35f) : 0xFFFFFFFF;
        base.setStroke(dp(ctx, 1), applyAlpha(strokeTint, strokeAlpha));

        GradientDrawable frostTop = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        applyAlpha(strokeTint, SUB_LIGHT.equals(sub) ? 0x38 : 0x22),
                        0x00FFFFFF
                });
        frostTop.setCornerRadius(cornerRadiusPx);

        return new LayerDrawable(new Drawable[]{base, frostTop});
    }

    /** Applies the unified glass drawable to a View with int corner radius in dp. */
    public static void applyToView(Context ctx, View view, int cornerRadiusDp) {
        if (ctx == null || view == null) return;
        view.setBackground(buildGlassDrawable(ctx, dp(ctx, cornerRadiusDp)));
    }

    /** Applies the unified glass drawable to a View with float corner radius in dp. */
    public static void applyToView(Context ctx, View view, float cornerRadiusDp) {
        applyToView(ctx, view, Math.round(cornerRadiusDp));
    }

    /**
     * Applies OS-level Window blurBehind on Android 12+ (API 31+) when Blur > 0
     * and mode is Frosted or Liquid glass. Safe no-op on older APIs.
     */
    public static void applyWindowBlurIfSupported(Context ctx, WindowManager.LayoutParams lp) {
        if (ctx == null || lp == null) return;
        String mode = getMode(ctx);
        int blurDp = getBlurRadius(ctx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MODE_CLASSIC.equals(mode) && blurDp > 0) {
            try {
                lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                lp.setBlurBehindRadius(dp(ctx, blurDp * 2));
            } catch (Exception ignored) {
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                lp.setBlurBehindRadius(0);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Applies RenderEffect blur to a dedicated background layer View on API 31+
     * (never call on a container with text/icons or children will also blur).
     */
    public static void applyLayerBlurIfSupported(Context ctx, View dedicatedBgView) {
        if (ctx == null || dedicatedBgView == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String mode = getMode(ctx);
            int blurDp = getBlurRadius(ctx);
            try {
                if (!MODE_CLASSIC.equals(mode) && blurDp > 0) {
                    float px = Math.max(1f, dp(ctx, blurDp));
                    dedicatedBgView.setRenderEffect(
                            android.graphics.RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
                } else {
                    dedicatedBgView.setRenderEffect(null);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Renders the current Glass Theme into a Bitmap for RemoteViews
     * (`DockService` lock-screen notification dock and `BaseDockWidget` home widgets).
     */
    public static Bitmap buildRemoteViewsCardBitmap(Context ctx, int widthDp, int heightDp, int cornerRadiusDp) {
        int w = Math.max(64, dp(ctx, widthDp));
        int h = Math.max(36, dp(ctx, heightDp));
        int r = dp(ctx, cornerRadiusDp);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Drawable d = buildGlassDrawable(ctx, r);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);

        // If Frosted mode with blur > 0, add a subtle frosted noise/grain highlight line along top edge
        if (!MODE_CLASSIC.equals(getMode(ctx)) && getBlurRadius(ctx) > 0) {
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, dp(ctx, 1)));
            rim.setColor(0x40FFFFFF);
            canvas.drawRoundRect(new RectF(1, 1, w - 1, h - 1), r, r, rim);
        }
        return bmp;
    }

    /** Refreshes Island, Edge Sidebar, Notification Dock, and Home Widgets when glass settings change. */
    public static void refreshAllSurfaces(Context ctx) {
        if (ctx == null) return;
        try {
            if (ShortcutStore.isDockOn(ctx)) {
                DockService.refresh(ctx);
            }
        } catch (Exception ignored) {}
        try {
            BaseDockWidget.refreshAll(ctx);
        } catch (Exception ignored) {}
        try {
            IslandService.refresh(ctx);
        } catch (Exception ignored) {}
    }

    private static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.rgb(r, g, b);
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
