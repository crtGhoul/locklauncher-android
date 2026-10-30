package com.crtghoul.locklauncher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On-device notification listener for accurate app notification badges (dots and numeric counts).
 * Runs 100% offline. Never logs or reads private notification text.
 */
public class LockNotificationListenerService extends NotificationListenerService {
    public static final String ACTION_NOTIFICATIONS_CHANGED =
            "com.crtghoul.locklauncher.NOTIFICATIONS_CHANGED";
    private static final String PREF_BADGES_ENABLED = "notification_badges_enabled";

    private static final Map<String, Integer> sNotificationCounts = new ConcurrentHashMap<>();
    private static boolean sIsConnected = false;

    public static boolean isBadgesEnabled(Context ctx) {
        return ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .getBoolean(PREF_BADGES_ENABLED, true);
    }

    public static void setBadgesEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_BADGES_ENABLED, enabled)
                .apply();
        broadcastUpdate(ctx);
    }

    public static boolean isPermissionGranted(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            ComponentName cn = new ComponentName(ctx, LockNotificationListenerService.class);
            return flat.contains(cn.flattenToString()) || flat.contains(ctx.getPackageName());
        }
        return false;
    }

    public static int getCount(String pkg) {
        if (pkg == null) return 0;
        Integer c = sNotificationCounts.get(pkg);
        return c != null ? c : 0;
    }

    @Override
    public void onListenerConnected() {
        sIsConnected = true;
        recalculateCounts();
    }

    @Override
    public void onListenerDisconnected() {
        sIsConnected = false;
        sNotificationCounts.clear();
        broadcastUpdate(this);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        recalculateCounts();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        recalculateCounts();
    }

    private void recalculateCounts() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            Map<String, Integer> counts = new ConcurrentHashMap<>();
            boolean foundMedia = false;
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn == null) continue;
                    Notification n = sbn.getNotification();
                    if (n == null) continue;
                    String pkg = sbn.getPackageName();

                    // Check if this is an active media playback notification
                    if (!foundMedia && pkg != null && !pkg.equals(getPackageName())) {
                        boolean isMedia = Notification.CATEGORY_TRANSPORT.equals(n.category)
                                || (n.extras != null && n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION));
                        if (isMedia && n.extras != null) {
                            CharSequence titleCs = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                            CharSequence textCs = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                            String title = titleCs != null ? titleCs.toString() : "";
                            String artist = textCs != null ? textCs.toString() : "";
                            if (!title.isEmpty()) {
                                boolean isOngoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
                                NowPlayingHelper.updateSystemMedia(this, title, artist, isOngoing, pkg, n.contentIntent);
                                foundMedia = true;
                            }
                        }
                    }

                    // Skip ongoing / sticky notifications for unread badge counts
                    if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;
                    if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) continue;

                    if (pkg != null && !pkg.equals(getPackageName())) {
                        int current = counts.containsKey(pkg) ? counts.get(pkg) : 0;
                        counts.put(pkg, current + 1);
                    }
                }
            }
            if (!foundMedia) {
                NowPlayingHelper.clearSystemMedia();
            }
            sNotificationCounts.clear();
            sNotificationCounts.putAll(counts);
            broadcastUpdate(this);
        } catch (Exception ignored) {
        }
    }

    private static void broadcastUpdate(Context ctx) {
        try {
            Intent intent = new Intent(ACTION_NOTIFICATIONS_CHANGED);
            intent.setPackage(ctx.getPackageName());
            ctx.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}
