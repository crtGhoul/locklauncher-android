package com.crtghoul.locklauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-performance Web App Logo & Brand Engine.
 * Provides authentic, crisp vector/canvas brand logos for 20+ top web services
 * and automatically fetches high-res 128px favicons over the web for any custom domain.
 */
public class WebLogoHelper {

    private static final int SIZE = 144;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface LogoCallback {
        void onLogoReady(Bitmap logo);
    }

    public static class PresetLogo {
        public final String name;
        public final String domain;
        public final String defaultUrl;

        public PresetLogo(String name, String domain, String defaultUrl) {
            this.name = name;
            this.domain = domain;
            this.defaultUrl = defaultUrl;
        }
    }

    public static List<PresetLogo> getPresetList() {
        List<PresetLogo> list = new ArrayList<>();
        list.add(new PresetLogo("YouTube", "youtube.com", "https://youtube.com"));
        list.add(new PresetLogo("Google", "google.com", "https://google.com"));
        list.add(new PresetLogo("X / Twitter", "x.com", "https://x.com"));
        list.add(new PresetLogo("Reddit", "reddit.com", "https://reddit.com"));
        list.add(new PresetLogo("Instagram", "instagram.com", "https://instagram.com"));
        list.add(new PresetLogo("ChatGPT", "chatgpt.com", "https://chatgpt.com"));
        list.add(new PresetLogo("Spotify", "spotify.com", "https://open.spotify.com"));
        list.add(new PresetLogo("GitHub", "github.com", "https://github.com"));
        list.add(new PresetLogo("Netflix", "netflix.com", "https://netflix.com"));
        list.add(new PresetLogo("Discord", "discord.com", "https://discord.com"));
        list.add(new PresetLogo("Twitch", "twitch.tv", "https://twitch.tv"));
        list.add(new PresetLogo("Amazon", "amazon.com", "https://amazon.com"));
        list.add(new PresetLogo("TikTok", "tiktok.com", "https://tiktok.com"));
        list.add(new PresetLogo("Facebook", "facebook.com", "https://facebook.com"));
        list.add(new PresetLogo("Wikipedia", "wikipedia.org", "https://wikipedia.org"));
        list.add(new PresetLogo("Claude", "claude.ai", "https://claude.ai"));
        list.add(new PresetLogo("LinkedIn", "linkedin.com", "https://linkedin.com"));
        list.add(new PresetLogo("Pinterest", "pinterest.com", "https://pinterest.com"));
        return list;
    }

    public static String extractDomain(String rawUrl) {
        if (rawUrl == null) return "";
        String s = rawUrl.trim().toLowerCase(Locale.US);
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("www.")) s = s.substring(4);
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon > 0) s = s.substring(0, colon);
        return s;
    }

    /**
     * Checks if this URL matches any popular web app and generates its authentic brand logo.
     * Returns null if no built-in preset matches.
     */
    public static Bitmap getBuiltinBrandLogo(Context ctx, String rawUrl) {
        String domain = extractDomain(rawUrl);
        if (domain.isEmpty()) return null;

        if (domain.contains("youtube.com") || domain.contains("youtu.be")) {
            return drawYouTubeLogo();
        }
        if (domain.contains("google.com") || domain.contains("google.")) {
            return drawGoogleLogo();
        }
        if (domain.contains("x.com") || domain.contains("twitter.com")) {
            return drawXLogo();
        }
        if (domain.contains("reddit.com")) {
            return drawRedditLogo();
        }
        if (domain.contains("instagram.com")) {
            return drawInstagramLogo();
        }
        if (domain.contains("chatgpt.com") || domain.contains("openai.com")) {
            return drawChatGPTLogo();
        }
        if (domain.contains("spotify.com")) {
            return drawSpotifyLogo();
        }
        if (domain.contains("github.com")) {
            return drawGitHubLogo();
        }
        if (domain.contains("netflix.com")) {
            return drawNetflixLogo();
        }
        if (domain.contains("discord.com") || domain.contains("discord.gg")) {
            return drawDiscordLogo();
        }
        if (domain.contains("twitch.tv")) {
            return drawTwitchLogo();
        }
        if (domain.contains("amazon.com") || domain.contains("amazon.")) {
            return drawAmazonLogo();
        }
        if (domain.contains("tiktok.com")) {
            return drawTikTokLogo();
        }
        if (domain.contains("facebook.com")) {
            return drawFacebookLogo();
        }
        if (domain.contains("wikipedia.org")) {
            return drawWikipediaLogo();
        }
        if (domain.contains("claude.ai")) {
            return drawClaudeLogo();
        }
        if (domain.contains("linkedin.com")) {
            return drawLinkedInLogo();
        }
        if (domain.contains("pinterest.com")) {
            return drawPinterestLogo();
        }
        return null;
    }

    // ---- Authentic Brand Renderers ----

    private static Bitmap createBaseSquircle(int bgColor) {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bgColor);
        float rad = SIZE * 0.28f;
        c.drawRoundRect(new RectF(0, 0, SIZE, SIZE), rad, rad, p);
        return b;
    }

    public static Bitmap drawYouTubeLogo() {
        Bitmap b = createBaseSquircle(0xFFFF0000); // YouTube Crimson Red
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // White play button pill
        p.setColor(Color.WHITE);
        float pillW = SIZE * 0.62f;
        float pillH = SIZE * 0.44f;
        float l = (SIZE - pillW) / 2f;
        float t = (SIZE - pillH) / 2f;
        c.drawRoundRect(new RectF(l, t, l + pillW, t + pillH), SIZE * 0.12f, SIZE * 0.12f, p);

        // Red play triangle inside
        p.setColor(0xFFFF0000);
        Path tri = new Path();
        float cx = SIZE / 2f;
        float cy = SIZE / 2f;
        float triW = SIZE * 0.14f;
        float triH = SIZE * 0.12f;
        tri.moveTo(cx - triW * 0.7f, cy - triH);
        tri.lineTo(cx + triW * 1.1f, cy);
        tri.lineTo(cx - triW * 0.7f, cy + triH);
        tri.close();
        c.drawPath(tri, p);
        return b;
    }

    public static Bitmap drawGoogleLogo() {
        Bitmap b = createBaseSquircle(0xFFFFFFFF); // Clean white card
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(SIZE * 0.13f);
        p.setStrokeCap(Paint.Cap.ROUND);

        float cx = SIZE / 2f;
        float cy = SIZE / 2f;
        float r = SIZE * 0.24f;
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);

        // Google 4-color arcs
        p.setColor(0xFFEA4335); // Red (top arc)
        c.drawArc(oval, 200, 100, false, p);

        p.setColor(0xFFFBBC05); // Yellow (left arc)
        c.drawArc(oval, 120, 95, false, p);

        p.setColor(0xFF34A853); // Green (bottom arc)
        c.drawArc(oval, 35, 95, false, p);

        p.setColor(0xFF4285F4); // Blue (right arc + crossbar)
        c.drawArc(oval, 300, 50, false, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(cx - SIZE * 0.02f, cy - SIZE * 0.065f, cx + r + SIZE * 0.065f, cy + SIZE * 0.065f, p);

        return b;
    }

    public static Bitmap drawXLogo() {
        Bitmap b = createBaseSquircle(0xFF000000); // Pure pitch black
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.60f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("𝕏", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawRedditLogo() {
        Bitmap b = createBaseSquircle(0xFFFF4500); // Reddit Orangey-Red
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // White Snoo face
        p.setColor(Color.WHITE);
        float cx = SIZE / 2f;
        float cy = SIZE / 2f + SIZE * 0.04f;
        c.drawCircle(cx, cy, SIZE * 0.26f, p);

        // Left ear
        c.drawCircle(cx - SIZE * 0.28f, cy, SIZE * 0.09f, p);
        // Right ear
        c.drawCircle(cx + SIZE * 0.28f, cy, SIZE * 0.09f, p);

        // Eyes (Reddit Orange)
        p.setColor(0xFFFF4500);
        c.drawCircle(cx - SIZE * 0.10f, cy - SIZE * 0.02f, SIZE * 0.05f, p);
        c.drawCircle(cx + SIZE * 0.10f, cy - SIZE * 0.02f, SIZE * 0.05f, p);

        // Smile
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(SIZE * 0.035f);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF smile = new RectF(cx - SIZE * 0.12f, cy, cx + SIZE * 0.12f, cy + SIZE * 0.14f);
        c.drawArc(smile, 20, 140, false, p);

        // Antenna
        p.setColor(Color.WHITE);
        p.setStrokeWidth(SIZE * 0.035f);
        c.drawLine(cx, cy - SIZE * 0.26f, cx + SIZE * 0.14f, cy - SIZE * 0.36f, p);
        c.drawLine(cx + SIZE * 0.14f, cy - SIZE * 0.36f, cx + SIZE * 0.24f, cy - SIZE * 0.36f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx + SIZE * 0.26f, cy - SIZE * 0.36f, SIZE * 0.045f, p);

        return b;
    }

    public static Bitmap drawInstagramLogo() {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Instagram signature sunset gradient
        Shader grad = new LinearGradient(
                0, SIZE, SIZE, 0,
                new int[]{0xFFFFDC80, 0xFFFCAF45, 0xFFF77737, 0xFFF56040, 0xFFFD1D1D, 0xFFE1306C, 0xFFC13584, 0xFF833AB4, 0xFF5851DB, 0xFF405DE6},
                null, Shader.TileMode.CLAMP);
        p.setShader(grad);
        float rad = SIZE * 0.28f;
        c.drawRoundRect(new RectF(0, 0, SIZE, SIZE), rad, rad, p);

        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(SIZE * 0.075f);

        // Rounded camera box
        float camW = SIZE * 0.52f;
        float l = (SIZE - camW) / 2f;
        c.drawRoundRect(new RectF(l, l, l + camW, l + camW), SIZE * 0.16f, SIZE * 0.16f, p);

        // Center lens circle
        float cx = SIZE / 2f;
        c.drawCircle(cx, cx, SIZE * 0.13f, p);

        // Flash dot
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx + SIZE * 0.16f, cx - SIZE * 0.16f, SIZE * 0.035f, p);

        return b;
    }

    public static Bitmap drawChatGPTLogo() {
        Bitmap b = createBaseSquircle(0xFF10A37F); // OpenAI Teal Green
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(SIZE * 0.042f);
        p.setStrokeCap(Paint.Cap.ROUND);

        float cx = SIZE / 2f;
        float cy = SIZE / 2f;
        float r = SIZE * 0.22f;

        // Draw 6-loop rosette petal geometry
        for (int i = 0; i < 6; i++) {
            c.save();
            c.rotate(i * 60f, cx, cy);
            RectF loop = new RectF(cx - r * 0.6f, cy - r, cx + r * 0.6f, cy);
            c.drawRoundRect(loop, r * 0.3f, r * 0.3f, p);
            c.restore();
        }
        return b;
    }

    public static Bitmap drawSpotifyLogo() {
        Bitmap b = createBaseSquircle(0xFF121212); // Spotify Dark
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Green circle
        p.setColor(0xFF1ED760);
        float cx = SIZE / 2f;
        c.drawCircle(cx, cx, SIZE * 0.35f, p);

        // Black soundwaves
        p.setColor(0xFF121212);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        p.setStrokeWidth(SIZE * 0.06f);
        RectF a1 = new RectF(cx - SIZE * 0.24f, cx - SIZE * 0.15f, cx + SIZE * 0.24f, cx + SIZE * 0.28f);
        c.drawArc(a1, 205, 130, false, p);

        p.setStrokeWidth(SIZE * 0.052f);
        RectF a2 = new RectF(cx - SIZE * 0.20f, cx - SIZE * 0.06f, cx + SIZE * 0.20f, cx + SIZE * 0.24f);
        c.drawArc(a2, 208, 124, false, p);

        p.setStrokeWidth(SIZE * 0.044f);
        RectF a3 = new RectF(cx - SIZE * 0.16f, cx + SIZE * 0.02f, cx + SIZE * 0.16f, cx + SIZE * 0.22f);
        c.drawArc(a3, 210, 120, false, p);

        return b;
    }

    public static Bitmap drawGitHubLogo() {
        Bitmap b = createBaseSquircle(0xFF181717); // GitHub Dark
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.64f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("🐙", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawNetflixLogo() {
        Bitmap b = createBaseSquircle(0xFF141414); // Netflix Dark
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFE50914); // Netflix Red
        p.setTextSize(SIZE * 0.72f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("N", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawDiscordLogo() {
        Bitmap b = createBaseSquircle(0xFF5865F2); // Discord Blurple
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.58f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("🎮", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawTwitchLogo() {
        Bitmap b = createBaseSquircle(0xFF9146FF); // Twitch Purple
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);

        float cx = SIZE / 2f;
        float cy = SIZE / 2f;
        float w = SIZE * 0.50f;
        float h = SIZE * 0.50f;
        RectF r = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        c.drawRoundRect(r, SIZE * 0.10f, SIZE * 0.10f, p);

        p.setColor(0xFF9146FF);
        // Twitch eyes
        c.drawRect(cx - SIZE * 0.12f, cy - SIZE * 0.10f, cx - SIZE * 0.05f, cy + SIZE * 0.08f, p);
        c.drawRect(cx + SIZE * 0.05f, cy - SIZE * 0.10f, cx + SIZE * 0.12f, cy + SIZE * 0.08f, p);
        return b;
    }

    public static Bitmap drawAmazonLogo() {
        Bitmap b = createBaseSquircle(0xFF131921); // Amazon Dark
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.44f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        c.drawText("a", SIZE / 2f, SIZE * 0.52f, p);

        // Amazon smile
        p.setColor(0xFFFF9900); // Amazon Amber
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(SIZE * 0.05f);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF smile = new RectF(SIZE * 0.32f, SIZE * 0.48f, SIZE * 0.68f, SIZE * 0.76f);
        c.drawArc(smile, 25, 130, false, p);
        return b;
    }

    public static Bitmap drawTikTokLogo() {
        Bitmap b = createBaseSquircle(0xFF010101);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(SIZE * 0.60f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        // Cyan shadow
        p.setColor(0xFF00F2FE);
        c.drawText("🎵", SIZE / 2f - 4, y - 3, p);
        // Magenta overlay
        p.setColor(0xFFFE0979);
        c.drawText("🎵", SIZE / 2f + 4, y + 3, p);
        // Crisp white
        p.setColor(Color.WHITE);
        c.drawText("🎵", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawFacebookLogo() {
        Bitmap b = createBaseSquircle(0xFF1877F2); // Facebook Blue
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.76f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        c.drawText("f", SIZE * 0.54f, SIZE * 0.82f, p);
        return b;
    }

    public static Bitmap drawWikipediaLogo() {
        Bitmap b = createBaseSquircle(0xFFFFFFFF);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF202122);
        p.setTextSize(SIZE * 0.68f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.SERIF);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("W", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawClaudeLogo() {
        Bitmap b = createBaseSquircle(0xFFD97706); // Warm Terracotta Amber
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.60f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("✦", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawLinkedInLogo() {
        Bitmap b = createBaseSquircle(0xFF0A66C2);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.48f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("in", SIZE / 2f, y, p);
        return b;
    }

    public static Bitmap drawPinterestLogo() {
        Bitmap b = createBaseSquircle(0xFFE60023); // Pinterest Crimson
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(SIZE * 0.70f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText("P", SIZE / 2f, y, p);
        return b;
    }

    // ---- Async Web Favicon Fetcher (Google Favicon API + IconHorse) ----

    /** Explicit opt-in for network favicon fetching. Default OFF so the
     *  render path (IconUtil.iconFor) never phones home on its own and the
     *  app's offline claim is true by default. Stored in the "dock" prefs. */
    public static boolean isFaviconFetchEnabled(Context ctx) {
        try {
            return ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                    .getBoolean("favicon_fetch_enabled", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setFaviconFetchEnabled(Context ctx, boolean enabled) {
        try {
            ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                    .edit().putBoolean("favicon_fetch_enabled", enabled).apply();
        } catch (Exception ignored) {
        }
    }

    public static void fetchFaviconAsync(Context ctx, String rawUrl, String shortcutId, LogoCallback callback) {
        // P1-3: gated. The icon render path (IconUtil.iconFor) calls this
        // and must not trigger a network fetch by itself; it only fetches
        // when the user has opted in via Settings.
        if (!isFaviconFetchEnabled(ctx)) return;
        fetchFaviconExplicit(ctx, rawUrl, shortcutId, callback);
    }

    /** Explicit user-tap fetch (logo picker). This is a deliberate user
     *  action, so it does not need the global auto-fetch toggle. */
    public static void fetchFaviconExplicit(Context ctx, String rawUrl, String shortcutId, LogoCallback callback) {
        String domain = extractDomain(rawUrl);
        if (domain.isEmpty() || shortcutId == null || shortcutId.isEmpty()) return;

        new Thread(() -> {
            Bitmap downloaded = null;

            // 1. Try Google High-Res 128px Favicon Service
            downloaded = downloadImage("https://www.google.com/s2/favicons?domain=" + domain + "&sz=128");

            // 2. Try IconHorse fallback
            if (downloaded == null) {
                downloaded = downloadImage("https://icon.horse/icon/" + domain);
            }

            // 3. Try direct apple-touch-icon
            if (downloaded == null) {
                downloaded = downloadImage("https://" + domain + "/apple-touch-icon.png");
            }

            if (downloaded != null) {
                // Center-crop on clean glass card squircle
                Bitmap formatted = formatDownloadedLogo(downloaded);
                IconUtil.saveCustomIcon(ctx, shortcutId, formatted);
                IconUtil.clearLruCache();
                if (callback != null) {
                    MAIN_HANDLER.post(() -> callback.onLogoReady(formatted));
                }
            }
        }).start();
    }

    private static Bitmap downloadImage(String urlString) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            conn.connect();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // P1-11: only image payloads, capped at 2 MB, decoded
                // bounds-first so a huge/corrupt image cannot OOM the render.
                String ct = conn.getContentType();
                if (ct != null && !ct.toLowerCase(java.util.Locale.US).startsWith("image/")) return null;
                in = conn.getInputStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int total = 0, n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 2 * 1024 * 1024) return null;
                    bos.write(buf, 0, n);
                }
                byte[] bytes = bos.toByteArray();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0 || opts.outWidth > 1024 || opts.outHeight > 1024) return null;
                opts.inJustDecodeBounds = false;
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (b != null && b.getWidth() > 16 && b.getHeight() > 16) {
                    return b;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static Bitmap formatDownloadedLogo(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF1E2235); // Sleek modern dark container
        float rad = SIZE * 0.28f;
        c.drawRoundRect(new RectF(0, 0, SIZE, SIZE), rad, rad, bg);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int targetW = (int) (SIZE * 0.72f);
        int targetH = (int) (SIZE * 0.72f);
        int x = (SIZE - targetW) / 2;
        int y = (SIZE - targetH) / 2;
        c.drawBitmap(src, null, new RectF(x, y, x + targetW, y + targetH), p);
        return out;
    }
}
