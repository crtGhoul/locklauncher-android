package com.crtghoul.locklauncher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Live Now-Playing state and universal media transport controller.
 * Bridges both WebAppActivity (YouTube Music / YouTube background playback)
 * and native Android media sessions / notifications.
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
        public final String sourcePkg;
        public final PendingIntent contentIntent;

        public MediaState(String title, String artist, boolean isPlaying, boolean isWebApp,
                          String webUrl, String sourcePkg, PendingIntent contentIntent) {
            this.title = title;
            this.artist = artist;
            this.isPlaying = isPlaying;
            this.isWebApp = isWebApp;
            this.webUrl = webUrl;
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
        sArtist = (artist != null && !artist.trim().isEmpty()) ? artist.trim() : "Web Player";
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
        if (sIsWebApp && sIsPlaying && (System.currentTimeMillis() - sLastUpdatedMs) < 15000L) {
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
            // Expire paused state after 30 minutes
            if (!sIsPlaying && (System.currentTimeMillis() - sLastUpdatedMs) > 30 * 60 * 1000L) {
                return null;
            }
            return new MediaState(sTitle, sArtist, sIsPlaying, sIsWebApp, sWebUrl, sSourcePkg, sContentIntent);
        }
        // Fallback: if AudioManager detects active music stream on the device
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                return new MediaState("Audio Playing", "System Media", true, false, "", "", null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static void sendCommand(Context ctx, String cmd) {
        if (ctx == null || cmd == null) return;
        if (sIsWebApp) {
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

    public static void openActivePlayer(Context ctx) {
        if (ctx == null) return;
        MediaState st = getActiveState(ctx);
        if (st == null) return;
        try {
            if (st.isWebApp) {
                Intent resume = new Intent(ctx, WebAppActivity.class);
                if (st.webUrl != null && !st.webUrl.isEmpty()) {
                    resume.putExtra(WebAppActivity.EXTRA_URL, st.webUrl);
                }
                resume.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                ctx.startActivity(resume);
                return;
            }
            if (st.contentIntent != null) {
                st.contentIntent.send();
                return;
            }
            if (st.sourcePkg != null && !st.sourcePkg.isEmpty()) {
                Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(st.sourcePkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(launch);
                }
            }
        } catch (Exception ignored) {
        }
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
