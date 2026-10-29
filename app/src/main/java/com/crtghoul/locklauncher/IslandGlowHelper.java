package com.crtghoul.locklauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * AMOLED visual polish for the Dynamic Island, Android-native.
 *
 * <p>What was NOT done, deliberately:
 * - No CSS {@code backdrop-blur-2xl}. Android has no backdrop-filter.
 *   A View's RenderEffect (API 31+) blurs that view's OWN rendering,
 *   not the content behind an overlay window. {@link #applyFrostedCard}
 *   therefore builds a layered translucent + specular drawable and,
 *   on API 31+, applies a blur RenderEffect to a dedicated background
 *   layer only. It does not claim to sample the live backdrop. On
 *   older APIs the same layered drawable is used without blur — the
 *   translucent fallback. Any claim of "live frosted glass over the
 *   home screen" for an overlay window would need a screenshot-based
 *   blur pipeline and is out of scope here.
 * - No fake live audio data. The music wave in
 *   {@link #startMusicWave} is a synthetic phase animation driven by
 *   a boolean "is playing" supplied by the caller (IslandService
 *   already obtains a real MediaController and checks
 *   PlaybackState.STATE_PLAYING in getActiveMediaController). Bar
 *   heights are NOT audio amplitudes and must not be labelled as a
 *   waveform.
 * - No "dominant accent of active apps" magic: the glow tint comes
 *   from {@link #dominantColor(Bitmap)} over the app's icon bitmap
 *   (IconUtil.iconFor), with the classic indigo fallback when the
 *   bitmap is null. Sampling every Nth opaque pixel is a dominant-
 *   colour estimate, not a Palette analysis, and is labelled as such.
 */
public final class IslandGlowHelper {

    private static final int FALLBACK_ACCENT = 0xFF5865F2;

    private IslandGlowHelper() {}

    /**
     * Deck background: obsidian base + top specular highlight +
     * 1dp white stroke, as a LayerDrawable. Matches the current deck
     * (0xF40D1117, corner 22dp, stroke 0x3DFFFFFF) with the highlight
     * added on top, so existing corners/padding are unchanged.
     */
    public static Drawable deckBackground(int accent, int cornerRadiusPx) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(0xF40D1117);
        base.setCornerRadius(cornerRadiusPx);
        base.setStroke(1, 0x3DFFFFFF);

        GradientDrawable specular = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x00FFFFFF, 0x00000000});
        specular.setCornerRadius(cornerRadiusPx);

        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        // RadialGradient via shader is not supported on GradientDrawable
        // directly; use a solid low-alpha accent wash instead, which is
        // what the ambient glow reduces to on an opaque deck.
        glow.setColor(applyAlpha(accent, 0x14));
        glow.setCornerRadius(cornerRadiusPx);

        LayerDrawable layered = new LayerDrawable(
                new Drawable[]{glow, base, specular});
        // Specular inset: keep the highlight at the top of the card.
        layered.setLayerInset(2, 0, 0, 0, 0);
        return layered;
    }

    private static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Frosted-card treatment. On API 31+ the background view gets a
     * blur RenderEffect (blurring its own layer); on older APIs the
     * layered translucent drawable above is used unchanged. The
     * caller must pass a dedicated background view, not the card
     * itself, or the blur would also blur the card's icons and text.
     */
    public static void applyFrostedBackground(View backgroundView, int accent,
            int cornerRadiusPx) {
        if (backgroundView == null) return;
        backgroundView.setBackground(deckBackground(accent, cornerRadiusPx));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                android.graphics.RenderEffect effect =
                        android.graphics.RenderEffect.createBlurEffect(
                                20f, 20f, Shader.TileMode.CLAMP);
                backgroundView.setRenderEffect(effect);
            } catch (Exception ignored) {
                // Fallback is the layered drawable already set above.
            }
        }
    }

    /** Ambient glow drawable: radial accent wash, for use behind a pill. */
    public static Drawable glowDrawable(int accent) {
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(applyAlpha(accent, 0x2E));
        return glow;
    }

    /** Dominant-colour estimate from an icon bitmap (sampled, opaque only). */
    public static int dominantColor(Bitmap bitmap) {
        if (bitmap == null) return FALLBACK_ACCENT;
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w <= 0 || h <= 0) return FALLBACK_ACCENT;
            long rSum = 0, gSum = 0, bSum = 0;
            int count = 0;
            int step = Math.max(1, Math.min(w, h) / 16);
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.alpha(pixel) < 128) continue;
                    rSum += Color.red(pixel);
                    gSum += Color.green(pixel);
                    bSum += Color.blue(pixel);
                    count++;
                }
            }
            if (count == 0) return FALLBACK_ACCENT;
            return Color.rgb((int) (rSum / count), (int) (gSum / count),
                    (int) (bSum / count));
        } catch (Exception ignored) {
            return FALLBACK_ACCENT;
        }
    }

    /**
     * Charging ripple: a repeating scale + alpha pulse on the pill.
     * Returns the ValueAnimator so the caller can cancel it when the
     * charging state ends (IslandService already clears
     * lastChargingMsg after 4s in triggerChargingPulse; cancel the
     * ripple at the same point, or it will pulse forever).
     */
    public static ValueAnimator startChargingRipple(final View pill) {
        if (pill == null) return null;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                float scale = 1f + 0.04f * (float) Math.sin(t * Math.PI);
                pill.setScaleX(scale);
                pill.setScaleY(scale);
                pill.setAlpha(1f - 0.15f * t);
            }
        });
        animator.start();
        return animator;
    }

    /**
     * Music indicator bars. Creates 4 bars in {@code container} and
     * animates their heights with phase offsets while {@code playing}
     * is true. Heights are synthetic (a phase animation), NOT audio
     * amplitudes. When playing is false, bars sit at a neutral height
     * and no animator runs. The caller must pass the real playback
     * state (MediaController PlaybackState.STATE_PLAYING) — this
     * helper does not query media sessions itself.
     */
    public static ValueAnimator startMusicWave(LinearLayout container,
            boolean playing) {
        if (container == null) return null;
        container.removeAllViews();
        final View[] bars = new View[4];
        for (int i = 0; i < bars.length; i++) {
            View bar = new View(container.getContext());
            bar.setBackgroundColor(0xFFFFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(container.getContext(), 3),
                    dp(container.getContext(), 12));
            lp.setMargins(dp(container.getContext(), 1), 0,
                    dp(container.getContext(), 1), 0);
            bar.setLayoutParams(lp);
            container.addView(bar);
            bars[i] = bar;
        }
        if (!playing) return null;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(900);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                for (int i = 0; i < bars.length; i++) {
                    float phase = t * (float) (Math.PI * 2)
                            + i * (float) (Math.PI / 2);
                    float heightFrac = 0.45f + 0.55f
                            * (0.5f + 0.5f * (float) Math.sin(phase));
                    ViewGroup.LayoutParams lp = bars[i].getLayoutParams();
                    lp.height = (int) (dp(container.getContext(), 4)
                            + heightFrac * dp(container.getContext(), 10));
                    bars[i].setLayoutParams(lp);
                }
            }
        });
        animator.start();
        return animator;
    }

    /** True if the device is in silent or vibrate ringer mode. */
    public static boolean isSilentMode(Context ctx) {
        if (ctx == null) return false;
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(
                    Context.AUDIO_SERVICE);
            if (am == null) return false;
            int mode = am.getRingerMode();
            return mode == AudioManager.RINGER_MODE_SILENT
                    || mode == AudioManager.RINGER_MODE_VIBRATE;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Show/hide a silent-mode dot view based on the real ringer mode. */
    public static void applySilentDot(View dot, Context ctx) {
        if (dot == null) return;
        dot.setVisibility(isSilentMode(ctx) ? View.VISIBLE : View.GONE);
    }

    private static int dp(Context ctx, int v) {
        if (ctx == null) return v;
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    /** Remove a RenderEffect blur from a background view (API 31+). */
    public static void clearFrostedBackground(View backgroundView) {
        if (backgroundView == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                backgroundView.setRenderEffect(null);
            } catch (Exception ignored) {
            }
        }
    }
}
