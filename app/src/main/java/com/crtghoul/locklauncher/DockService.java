package com.crtghoul.locklauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Foreground service posting the persistent launcher dock as an ongoing
 * notification. It shows on the lock screen (when the user allows this app's
 * notifications there) — the Android equivalent of Lock Launcher's
 * Live Activity dock. No ads, no network, everything local.
 *
 * v1.4 hardening for lock-screen reliability:
 * - IMPORTANCE_DEFAULT (silent) so OEMs don't hide it on the lock screen
 * - collapsed 1-row view + expanded full grid (big content view)
 * - standard action buttons for the first 3 shortcuts as a fallback in case
 *   the OEM strips the custom RemoteViews layout
 */
public class DockService extends Service {
    public static final String ACTION_REFRESH = "com.crtghoul.locklauncher.REFRESH";
    private static final String CHANNEL = "dock_v2";
    private static final String OLD_CHANNEL = "dock";
    private static final int NOTIF_ID = 1;

    private static long lastRefreshCallMs = 0L;
    private android.os.Handler handler;
    private final Runnable refreshRunnable = this::postDockNotification;

    public static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, DockService.class);
            ctx.startForegroundService(i);
        } catch (Exception ignored) {}
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, DockService.class));
        } catch (Exception ignored) {}
    }

    public static synchronized void refresh(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshCallMs < 200L) {
            return;
        }
        lastRefreshCallMs = now;
        try {
            Intent i = new Intent(ctx, DockService.class);
            i.setAction(ACTION_REFRESH);
            ctx.startForegroundService(i);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new android.os.Handler(android.os.Looper.getMainLooper());
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.deleteNotificationChannel(OLD_CHANNEL);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Launcher dock", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Persistent app launcher dock");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler == null) {
            handler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 60);
        return START_STICKY;
    }

    private void postDockNotification() {
        List<Shortcut> shortcuts = ShortcutStore.load(this);
        if (shortcuts.isEmpty()) {
            stopSelf();
            return;
        }
        try {
            Notification n = buildNotification(shortcuts);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(refreshRunnable);
        }
        super.onDestroy();
    }

    private Notification buildNotification(List<Shortcut> shortcuts) {
        RemoteViews collapsed = new RemoteViews(getPackageName(),
                R.layout.notification_dock_collapsed);
        IconUtil.fillDock(this, collapsed, shortcuts, 100, 4);
        IconUtil.applyCardStyle(this, collapsed);

        RemoteViews expanded = new RemoteViews(getPackageName(),
                R.layout.notification_dock);
        IconUtil.fillDock(this, expanded, shortcuts, 100, 16);
        IconUtil.applyCardStyle(this, expanded);
        IconUtil.applyMemo(this, expanded);

        // Tapping the notification body opens the config app.
        Intent cfg = new Intent(this, MainActivity.class);
        PendingIntent cfgPi = PendingIntent.getActivity(
                this, 999, cfg, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setContentIntent(cfgPi)
                .setOngoing(true)
                .setShowWhen(false)
                // P1-2: private by default. A PUBLIC notification exposes
                // dock labels and the memo on the lock screen to anyone
                // holding the phone; PRIVATE hides custom content until unlock.
                .setVisibility(Notification.VISIBILITY_PRIVATE);

        // Fallback: standard action buttons with real icons, in case the OEM
        // strips the custom RemoteViews layout.
        for (int i = 0; i < shortcuts.size() && i < 3; i++) {
            Shortcut s = shortcuts.get(i);
            PendingIntent pi = IconUtil.pendingFor(this, s, 600 + i);
            if (pi != null) {
                b.addAction(new Notification.Action.Builder(
                        Icon.createWithBitmap(IconUtil.iconFor(this, s)),
                        s.label, pi).build());
            }
        }
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
