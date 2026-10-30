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
    private static final String PREF_BADGE_STYLE = "notification_badge_style"; // "off", "dot", "number"
    private static final String PREF_BADGE_SIZE_PCT = "notification_badge_size_pct"; // 50..150, default 100
    private static final String PREF_BADGE_COLOR = "notification_badge_color";
    private static final String PREF_DISABLED_BADGE_APPS = "notification_disabled_badge_apps";

    public static final String STYLE_OFF = "off";
    public static final String STYLE_DOT = "dot";
    public static final String STYLE_NUMBER = "number";

    public static final int[] BADGE_COLOR_PRESETS = {
            0xFFFF3B30, // Red / Coral
            0xFFEC4899, // Pink / Rose
            0xFF3B82F6, // Blue
            0xFF10B981, // Emerald Green
            0xFFFFFFFF  // Pure White
    };

    private static final Map<String, Integer> sNotificationCounts = new ConcurrentHashMap<>();
    private static boolean sIsConnected = false;

    public static String getBadgeStyle(Context ctx) {
        android.content.SharedPreferences p = ctx.getSharedPreferences("dock", Context.MODE_PRIVATE);
        if (!p.contains(PREF_BADGE_STYLE)) {
            boolean legacyOn = p.getBoolean(PREF_BADGES_ENABLED, true);
            return legacyOn ? STYLE_NUMBER : STYLE_OFF;
        }
        String s = p.getString(PREF_BADGE_STYLE, STYLE_NUMBER);
        if (!STYLE_OFF.equals(s) && !STYLE_DOT.equals(s) && !STYLE_NUMBER.equals(s)) {
            return STYLE_NUMBER;
        }
        return s;
    }

    public static void setBadgeStyle(Context ctx, String style) {
        if (!STYLE_OFF.equals(style) && !STYLE_DOT.equals(style) && !STYLE_NUMBER.equals(style)) {
            style = STYLE_NUMBER;
        }
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_BADGE_STYLE, style)
                .putBoolean(PREF_BADGES_ENABLED, !STYLE_OFF.equals(style))
                .apply();
        broadcastUpdate(ctx);
    }

    public static boolean isBadgesEnabled(Context ctx) {
        return !STYLE_OFF.equals(getBadgeStyle(ctx));
    }

    public static void setBadgesEnabled(Context ctx, boolean enabled) {
        setBadgeStyle(ctx, enabled ? STYLE_NUMBER : STYLE_OFF);
    }

    public static int getBadgeSizePct(Context ctx) {
        return Math.max(50, Math.min(150,
                ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                        .getInt(PREF_BADGE_SIZE_PCT, 100)));
    }

    public static void setBadgeSizePct(Context ctx, int pct) {
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_BADGE_SIZE_PCT, Math.max(50, Math.min(150, pct)))
                .apply();
        broadcastUpdate(ctx);
    }

    public static void resetBadgeSizePct(Context ctx) {
        setBadgeSizePct(ctx, 100);
    }

    public static int getBadgeColor(Context ctx) {
        return ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .getInt(PREF_BADGE_COLOR, BADGE_COLOR_PRESETS[0]);
    }

    public static void setBadgeColor(Context ctx, int color) {
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_BADGE_COLOR, color)
                .apply();
        broadcastUpdate(ctx);
    }

    public static java.util.Set<String> getDisabledBadgeApps(Context ctx) {
        return new java.util.HashSet<>(
                ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                        .getStringSet(PREF_DISABLED_BADGE_APPS, new java.util.HashSet<>()));
    }

    public static boolean isAppBadgeAllowed(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return !getDisabledBadgeApps(ctx).contains(pkg);
    }

    public static void setAppBadgeAllowed(Context ctx, String pkg, boolean allowed) {
        if (pkg == null || pkg.isEmpty()) return;
        java.util.Set<String> disabled = getDisabledBadgeApps(ctx);
        if (allowed) {
            disabled.remove(pkg);
        } else {
            disabled.add(pkg);
        }
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_DISABLED_BADGE_APPS, disabled)
                .apply();
        broadcastUpdate(ctx);
    }

    public static void setDisabledBadgeApps(Context ctx, java.util.Set<String> disabled) {
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_DISABLED_BADGE_APPS, disabled != null ? disabled : new java.util.HashSet<>())
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

    public static int getVisibleCount(Context ctx, String pkg) {
        if (ctx == null || pkg == null || !isBadgesEnabled(ctx) || !isAppBadgeAllowed(ctx, pkg)) {
            return 0;
        }
        return getCount(pkg);
    }

    /**
     * Binds a notification badge TextView according to the active style (Off, Dot, or Number),
     * per-app badge filter, badge size slider (50..150%), and badge color.
     */
    public static void bindBadgeView(Context ctx, android.widget.TextView badgeView, String pkg) {
        if (badgeView == null) return;
        String style = getBadgeStyle(ctx);
        if (STYLE_OFF.equals(style) || pkg == null || !isAppBadgeAllowed(ctx, pkg)) {
            badgeView.setVisibility(android.view.View.GONE);
            return;
        }
        int count = getCount(pkg);
        if (count <= 0) {
            badgeView.setVisibility(android.view.View.GONE);
            return;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        float scale = getBadgeSizePct(ctx) / 100f;
        int color = getBadgeColor(ctx);
        boolean isWhite = (color & 0x00FFFFFF) == 0x00FFFFFF;

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setStroke(Math.max(1, (int) (1.2f * density)), isWhite ? 0xFF111827 : 0xFFFFFFFF);

        android.view.ViewGroup.LayoutParams lp = badgeView.getLayoutParams();
        if (STYLE_DOT.equals(style)) {
            int dotPx = Math.max((int) (8 * density), (int) (11 * density * scale));
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            badgeView.setText("");
            badgeView.setMinWidth(dotPx);
            badgeView.setPadding(0, 0, 0, 0);
            if (lp != null) {
                lp.width = dotPx;
                lp.height = dotPx;
                badgeView.setLayoutParams(lp);
            }
        } else {
            int hPx = Math.max((int) (14 * density), (int) (18 * density * scale));
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(hPx / 2f);
            badgeView.setText(count > 99 ? "99+" : String.valueOf(count));
            badgeView.setTextColor(isWhite ? 0xFF000000 : 0xFFFFFFFF);
            badgeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(8f, 10f * scale));
            int padH = Math.max((int) (3 * density), (int) (4.5f * density * scale));
            badgeView.setPadding(padH, 0, padH, 0);
            badgeView.setMinWidth(hPx);
            if (lp != null) {
                lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = hPx;
                badgeView.setLayoutParams(lp);
            }
        }
        badgeView.setBackground(bg);
        badgeView.setVisibility(android.view.View.VISIBLE);
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
