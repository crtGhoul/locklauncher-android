package com.crtghoul.locklauncher;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;

/**
 * Live Now-Playing state and universal media transport controller.
 * Bridges both WebAppActivity (YouTube Music / YouTube background playback)
 * and native Android media sessions / notifications.
 *
 * v2.21 Fixes:
 * - Android 14/15 (API 34+) Background Activity Launch (BAL) opt-in on PendingIntent.send()
 *   so "Tap to open" on the Music Square, Dynamic Island, and Notification always opens the player.
 * - Direct MediaSessionManager active session lookup so system players (Spotify, YT Music, Apple Music, etc.)
 *   are detected and opened reliably even if notification extras omit contentIntent.
 */
public class NowPlayingHelper {
    public static final String ACTION_MEDIA_COMMAND = "com.crtghoul.locklauncher.MEDIA_COMMAND";
    public static final String EXTRA_CMD = "cmd";
    public static final String CMD_TOGGLE = "toggle";
    public static final String CMD_NEXT = "next";
    public static final String CMD_PREV = "prev";

    private static volatile String sTitle = "";
    private static volatile String sArtist = "";
    private static volatile boolean sIsPlaying = false;
    private static volatile boolean sIsWebApp = false;
    private static volatile String sWebUrl = "";
    private static volatile String sSourcePkg = "";
    private static volatile PendingIntent sContentIntent = null;
    private static volatile long sLastUpdatedMs = 0L;

    public static class MediaState {
        public final String title;
        public final String artist;
        public final boolean isPlaying;
        public final boolean isWebApp;
        public final String webUrl;
        public final String url;
        public final String sourcePkg;
        public final PendingIntent contentIntent;

        public MediaState(String title, String artist, boolean isPlaying, boolean isWebApp,
                          String webUrl, String sourcePkg, PendingIntent contentIntent) {
            this.title = title;
            this.artist = artist;
            this.isPlaying = isPlaying;
            this.isWebApp = isWebApp;
            this.webUrl = webUrl;
            this.url = webUrl;
            this.sourcePkg = sourcePkg;
            this.contentIntent = contentIntent;
        }
    }

    public static synchronized void updateWebMedia(Context ctx, String title, String artist, boolean isPlaying, String url) {
        if (title == null || title.trim().isEmpty()) return;
        String cleanTitle = title.trim()
                .replaceAll("\\s*-\\s*YouTube Music$", "")
                .replaceAll("\\s*-\\s*YouTube$", "")
                .replaceAll("^\\(\\d+\\)\\s*", "");
        if (cleanTitle.isEmpty()) cleanTitle = title.trim();
        sTitle = cleanTitle;
        sArtist = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "YouTube Music · WebApp";
        sIsPlaying = isPlaying;
        sIsWebApp = true;
        if (url != null && !url.isEmpty()) sWebUrl = url;
        sLastUpdatedMs = System.currentTimeMillis();
        broadcastChange(ctx);
    }

    public static synchronized void clearWebMedia(Context ctx) {
        if (sIsWebApp) {
            sTitle = "";
            sArtist = "";
            sIsPlaying = false;
            sIsWebApp = false;
            sWebUrl = "";
            sLastUpdatedMs = 0L;
            broadcastChange(ctx);
        }
    }

    public static synchronized void updateSystemMedia(Context ctx, String title, String artist,
                                                      boolean isPlaying, String pkg, PendingIntent contentIntent) {
        // Don't overwrite active WebApp playback with stale system notification
        if (sIsWebApp && sIsPlaying && (System.currentTimeMillis() - sLastUpdatedMs) < 20000L) {
            return;
        }
        if (ctx != null && pkg != null && pkg.equals(ctx.getPackageName())) {
            // Our own WebAppActivity notification — keep sIsWebApp true
            return;
        }
        sTitle = title != null ? title.trim() : "";
        sArtist = artist != null ? artist.trim() : "";
        sIsPlaying = isPlaying;
        sIsWebApp = false;
        sSourcePkg = pkg != null ? pkg : "";
        sContentIntent = contentIntent;
        sLastUpdatedMs = System.currentTimeMillis();
    }

    public static synchronized void clearSystemMedia() {
        if (!sIsWebApp) {
            sTitle = "";
            sArtist = "";
            sIsPlaying = false;
            sSourcePkg = "";
            sContentIntent = null;
        }
    }

    public static synchronized MediaState getActiveState(Context ctx) {
        if (sTitle != null && !sTitle.isEmpty()) {
            // Expire paused state after 45 minutes
            if (!sIsPlaying && (System.currentTimeMillis() - sLastUpdatedMs) > 45 * 60 * 1000L) {
                return null;
            }
            return new MediaState(sTitle, sArtist, sIsPlaying, sIsWebApp, sWebUrl, sSourcePkg, sContentIntent);
        }

        // Check active MediaSessionManager controllers if notification permission is granted
        if (ctx != null && LockNotificationListenerService.isPermissionGranted(ctx)) {
            try {
                MediaSessionManager mm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (mm != null) {
                    ComponentName cn = new ComponentName(ctx, LockNotificationListenerService.class);
                    List<MediaController> list = mm.getActiveSessions(cn);
                    if (list != null) {
                        for (MediaController mc : list) {
                            if (mc == null) continue;
                            PlaybackState ps = mc.getPlaybackState();
                            boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                            MediaMetadata meta = mc.getMetadata();
                            if (meta != null) {
                                String t = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                                String a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                                if (t != null && !t.trim().isEmpty()) {
                                    boolean ownPkg = ctx.getPackageName().equals(mc.getPackageName());
                                    return new MediaState(
                                            t.trim(),
                                            a != null ? a.trim() : "Media Player",
                                            playing,
                                            ownPkg,
                                            sWebUrl,
                                            mc.getPackageName(),
                                            mc.getSessionActivity());
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback: if AudioManager detects active music stream on the device
        if (ctx != null) {
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && am.isMusicActive()) {
                    return new MediaState(
                            "Active Audio Stream",
                            sWebUrl != null && !sWebUrl.isEmpty() ? "Web Player · Tap to open" : "System Audio · Tap to open",
                            true,
                            sWebUrl != null && !sWebUrl.isEmpty(),
                            sWebUrl,
                            sSourcePkg,
                            sContentIntent);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void sendCommand(Context ctx, String cmd) {
        if (ctx == null || cmd == null) return;
        if (sIsWebApp || WebAppActivity.isAlive()) {
            if (CMD_TOGGLE.equals(cmd)) {
                sIsPlaying = !sIsPlaying;
            }
            Intent i = new Intent(ACTION_MEDIA_COMMAND);
            i.setPackage(ctx.getPackageName());
            i.putExtra(EXTRA_CMD, cmd);
            ctx.sendBroadcast(i);
            return;
        }
        int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (CMD_NEXT.equals(cmd)) {
            keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
        } else if (CMD_PREV.equals(cmd)) {
            keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else if (CMD_TOGGLE.equals(cmd)) {
            sIsPlaying = !sIsPlaying;
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long eventTime = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
                KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
                am.dispatchMediaKeyEvent(down);
                am.dispatchMediaKeyEvent(up);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Builds Android 14/15+ Background Activity Launch (BAL) allowed bundle for PendingIntent.send().
     */
    public static Bundle buildBalAllowedBundle() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                return opts.toBundle();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean sendPendingIntentCompat(Context ctx, PendingIntent pi) {
        if (pi == null) return false;
        try {
            Bundle balBundle = buildBalAllowedBundle();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && balBundle != null) {
                pi.send(ctx, 0, null, null, null, null, balBundle);
            } else {
                pi.send();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens the active media player (WebAppActivity or native music app).
     * Guaranteed to work from HomeActivity, IslandService overlay, or Notification shade.
     */
    public static boolean openActivePlayer(Context ctx) {
        if (ctx == null) return false;
        MediaState st = getActiveState(ctx);

        // 1. If WebAppActivity is active or we have a WebApp media state, bring WebAppActivity to front
        if ((st != null && st.isWebApp) || WebAppActivity.isAlive()) {
            try {
                String targetUrl = (st != null && st.webUrl != null && !st.webUrl.isEmpty())
                        ? st.webUrl
                        : WebAppActivity.getActiveUrl();
                if (targetUrl == null || targetUrl.isEmpty()) {
                    targetUrl = "https://music.youtube.com";
                }
                Intent resume = new Intent(ctx, WebAppActivity.class);
                resume.setAction("com.crtghoul.locklauncher.OPEN_WEB_APP");
                resume.putExtra(WebAppActivity.EXTRA_URL, targetUrl);
                if (st != null && st.title != null && !st.title.isEmpty()) {
                    resume.putExtra(WebAppActivity.EXTRA_TITLE, st.title);
                }
                resume.setData(Uri.parse(targetUrl));
                resume.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                ctx.startActivity(resume);
                return true;
            } catch (Exception ignored) {
            }
        }

        // 2. If system MediaState has a contentIntent, send it with Android 14/15 BAL opt-in
        if (st != null && st.contentIntent != null) {
            if (sendPendingIntentCompat(ctx, st.contentIntent)) {
                return true;
            }
        }

        // 3. If system MediaState has a package name, launch that package directly
        if (st != null && st.sourcePkg != null && !st.sourcePkg.isEmpty()) {
            try {
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(st.sourcePkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    ctx.startActivity(launch);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        // 4. Fallback: open YouTube Music WebApp container
        try {
            Intent ytm = IconUtil.buildWebIntent(ctx, "https://music.youtube.com", "YT Music");
            if (ytm != null) {
                ctx.startActivity(ytm);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void broadcastChange(Context ctx) {
        if (ctx == null) return;
        try {
            Intent intent = new Intent(LockNotificationListenerService.ACTION_NOTIFICATIONS_CHANGED);
            intent.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}
