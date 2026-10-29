package com.crtghoul.locklauncher;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic Floating Island overlay with multiple states:
 * 1. Minimalist Collapsed Capsule: Sleek black pill with battery / island glyph (no cluttering app icons)
 * 2. Media Playback: Track title, artist, live play/pause & skip controls
 * 3. Charging Pulse: Connected wattage/battery indicator
 * 4. Expanded Island Deck: High-res symmetrical grid of chosen Island apps with add, remove, and reorder controls
 */
public class IslandService extends Service {
    public static final String ACTION_REFRESH =
            "com.crtghoul.locklauncher.ISLAND_REFRESH";

    private static boolean running = false;

    private WindowManager wm;
    private View islandView;
    private WindowManager.LayoutParams params;
    private boolean expanded = false;
    private boolean mediaDismissed = false;
    private String lastChargingMsg = null;

    private Handler mainHandler;
    private BroadcastReceiver batteryReceiver;

    public static boolean isRunning() {
        return running;
    }

    public static boolean canShow(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    public static void start(Context ctx) {
        if (!canShow(ctx)) return;
        try {
            Intent intent = new Intent(ctx, IslandService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, IslandService.class));
        } catch (Exception ignored) {
        }
    }

    public static void refresh(Context ctx) {
        if (!running) return;
        try {
            Intent i = new Intent(ctx, IslandService.class).setAction(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isIslandOn(Context ctx) {
        return ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .getBoolean("island_on", false);
    }

    public static void setIslandOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit().putBoolean("island_on", on).apply();
    }

    private static final int NOTIF_ID = 4041;
    private static final String CHANNEL_ID = "island_service_channel";

    private void ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHANNEL_ID,
                    "Dynamic Island",
                    NotificationManager.IMPORTANCE_MIN
            );
            chan.setDescription("Dynamic Island overlay service");
            chan.setShowBadge(false);
            chan.enableLights(false);
            chan.enableVibration(false);
            chan.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(chan);
            }
            Notification.Builder nb = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Dynamic Island Active")
                    .setContentText("Tap to configure Island")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE);
            Intent appIntent = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, appIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            nb.setContentIntent(pi);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, nb.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, nb.build());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureForeground();
        mainHandler = new Handler(Looper.getMainLooper());
        registerIslandReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        ensureForeground();
        if (ACTION_REFRESH.equals(intent != null ? intent.getAction() : "")) {
            rebuild();
        } else if (islandView == null) {
            showCollapsed();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        unregisterIslandReceivers();
        removeView();
        super.onDestroy();
    }

    private void registerIslandReceivers() {
        try {
            batteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent != null ? intent.getAction() : null;
                    if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                        triggerChargingPulse();
                    } else if (LockNotificationListenerService.ACTION_NOTIFICATIONS_CHANGED.equals(action)) {
                        if (!expanded) {
                            showCollapsed();
                        }
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(LockNotificationListenerService.ACTION_NOTIFICATIONS_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(batteryReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, f);
            }
        } catch (Exception ignored) {
        }
    }

    private void unregisterIslandReceivers() {
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {
            }
            batteryReceiver = null;
        }
    }

    private void triggerChargingPulse() {
        lastChargingMsg = "âš¡ " + IconUtil.getLiveBatteryInfo(this) + " (Charging)";
        rebuild();
        mainHandler.postDelayed(() -> {
            lastChargingMsg = null;
            rebuild();
        }, 4000);
    }

    private void removeView() {
        if (wm != null && islandView != null) {
            try {
                wm.removeView(islandView);
            } catch (Exception ignored) {
            }
        }
        islandView = null;
        expanded = false;
    }

    private WindowManager.LayoutParams baseParams(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = statusBarHeight() + dp(8);
        return p;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private MediaController getActiveMediaController() {
        if (!LockNotificationListenerService.isPermissionGranted(this)) return null;
        try {
            MediaSessionManager mm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mm != null) {
                ComponentName cn = new ComponentName(this, LockNotificationListenerService.class);
                List<MediaController> list = mm.getActiveSessions(cn);
                if (list != null) {
                    for (MediaController mc : list) {
                        PlaybackState ps = mc.getPlaybackState();
                        if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                            return mc;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showCollapsed() {
        removeView();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Check 1: Charging Pulse State
        if (lastChargingMsg != null) {
            showChargingPill(lastChargingMsg);
            return;
        }

        // Check 2: Media Session State
        MediaController media = !mediaDismissed ? getActiveMediaController() : null;
        if (media != null) {
            showMediaPill(media);
            return;
        }

        // Default State: Clean Minimalist Island Capsule
        showDockPill();
    }

    private void showChargingPill(String msg) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.dock_card);
        pill.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(0xFF00E676); // glowing battery green
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        pill.addView(tv);

        attachPillView(pill);
    }

    private void showMediaPill(MediaController mc) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.dock_card);
        pill.setPadding(dp(14), dp(6), dp(14), dp(6));

        TextView musicIcon = new TextView(this);
        musicIcon.setText("ðŸŽµ ");
        musicIcon.setTextSize(14);
        pill.addView(musicIcon);

        MediaMetadata meta = mc.getMetadata();
        String title = "Now Playing";
        String artist = "";
        if (meta != null) {
            String t = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (t != null && !t.isEmpty()) title = t;
            String a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (a != null && !a.isEmpty()) artist = " Â· " + a;
        }

        TextView textInfo = new TextView(this);
        textInfo.setText(title + artist);
        textInfo.setTextColor(0xFFFFFFFF);
        textInfo.setTextSize(12);
        textInfo.setMaxLines(1);
        textInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textInfo.setMaxWidth(dp(150));
        textInfo.setPadding(0, 0, dp(10), 0);
        pill.addView(textInfo);

        // Play/Pause control
        TextView btnPlayPause = new TextView(this);
        btnPlayPause.setText("â¸ï¸");
        btnPlayPause.setTextSize(15);
        btnPlayPause.setPadding(dp(4), 0, dp(6), 0);
        btnPlayPause.setOnClickListener(v -> {
            try {
                mc.getTransportControls().pause();
                rebuild();
            } catch (Exception ignored) {}
        });
        pill.addView(btnPlayPause);

        // Next Track control
        TextView btnNext = new TextView(this);
        btnNext.setText("â­ï¸");
        btnNext.setTextSize(15);
        btnNext.setPadding(dp(4), 0, dp(2), 0);
        btnNext.setOnClickListener(v -> {
            try {
                mc.getTransportControls().skipToNext();
                rebuild();
            } catch (Exception ignored) {}
        });
        pill.addView(btnNext);

        textInfo.setOnClickListener(v -> {
            try {
                if (mc.getSessionActivity() != null) {
                    mc.getSessionActivity().send();
                }
            } catch (Exception ignored) {}
        });

        attachPillView(pill);
    }

    /**
     * Minimalist Collapsed Capsule Pill:
     * Does NOT display app icons inside the collapsed pill!
     * Tap anywhere on the pill smoothly expands the Dynamic Island.
     */
    private void showDockPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xF2000000); // Pure deep obsidian black
        pillBg.setCornerRadius(dp(17));
        pillBg.setStroke(dp(1), 0x33FFFFFF); // Sleek subtle frosted border
        pill.setBackground(pillBg);

        int padH = dp(14);
        int padV = dp(6);
        pill.setPadding(padH, padV, padH, padV);
        pill.setMinimumWidth(dp(92));
        pill.setMinimumHeight(dp(32));

        TextView glyph = new TextView(this);
        glyph.setText("â—");
        glyph.setTextColor(0x7AFFFFFF);
        glyph.setTextSize(9);
        glyph.setPadding(0, 0, dp(5), 0);
        pill.addView(glyph);

        TextView label = new TextView(this);
        if (ShortcutStore.isIslandBattery(this)) {
            label.setText(IconUtil.getLiveBatteryInfo(this));
            label.setTextColor(0xFF00E676);
        } else {
            label.setText("Island");
            label.setTextColor(0xDDFFFFFF);
        }
        label.setTextSize(12);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        pill.addView(label);

        attachPillView(pill);
    }

    private void attachPillView(LinearLayout pill) {
        final View pillView = pill;
        params = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);

        android.content.SharedPreferences iprefs =
                getSharedPreferences("dock", MODE_PRIVATE);
        params.x = iprefs.getInt("island_x", 0);
        params.y = iprefs.getInt("island_y", statusBarHeight() + dp(8));

        final Handler handler = new Handler(getMainLooper());
        final Runnable longPress = this::hideIsland;
        final int[] drag = new int[4];
        final boolean[] moved = new boolean[1];
        final float[] startTouchX = new float[1];

        pill.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    drag[0] = params.x;
                    drag[1] = params.y;
                    drag[2] = (int) e.getRawX();
                    drag[3] = (int) e.getRawY();
                    startTouchX[0] = e.getRawX();
                    moved[0] = false;
                    handler.postDelayed(longPress, 500);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = (int) e.getRawX() - drag[2];
                    int dy = (int) e.getRawY() - drag[3];
                    if (!moved[0] && Math.hypot(dx, dy) > dp(10)) {
                        moved[0] = true;
                        handler.removeCallbacks(longPress);
                    }
                    if (moved[0]) {
                        params.x = clampX(drag[0] + dx);
                        params.y = clampY(drag[1] + dy);
                        try {
                            wm.updateViewLayout(islandView, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    float swipeDeltaX = e.getRawX() - startTouchX[0];
                    if (Math.abs(swipeDeltaX) > dp(60)) {
                        // Swipe dismisses media mode or switches to dock
                        mediaDismissed = !mediaDismissed;
                        rebuild();
                        return true;
                    }

                    if (!moved[0]) {
                        v.performClick();
                        showExpanded();
                    } else {
                        iprefs.edit()
                                .putInt("island_x", params.x)
                                .putInt("island_y", params.y)
                                .apply();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    return true;
            }
            return false;
        });

        islandView = pillView;
        expanded = false;
        try {
            wm.addView(islandView, params);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't show island: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private int clampX(int x) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int half = dp(110);
        return Math.max(-w / 2 + half, Math.min(w / 2 - half, x));
    }

    private int clampY(int y) {
        int h = getResources().getDisplayMetrics().heightPixels;
        return Math.max(statusBarHeight() + dp(4), Math.min(h - dp(160), y));
    }

    /**
     * Expanded Dynamic Island Deck:
     * Fullscreen translucent backdrop dismisses on outside tap.
     * High-res symmetrical 4-column deck of user's chosen Island apps with quick actions.
     */
    private void showExpanded() {
        removeView();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        List<Shortcut> allShortcuts = ShortcutStore.loadIsland(this);
        final List<Shortcut> shortcuts = new ArrayList<>();
        for (Shortcut s : allShortcuts) {
            if (s != null && !s.isEmpty()) shortcuts.add(s);
        }

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> showCollapsed());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF40D1117); // Obsidian Glass
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), 0x3DFFFFFF);
        card.setBackground(cardBg);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));

        int cardW = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(360));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        clp.topMargin = statusBarHeight() + dp(8);
        card.setLayoutParams(clp);
        card.setOnClickListener(v -> { }); // prevent dismiss on card tap

        // 120Hz GPU Entrance Animation
        card.setScaleX(0.88f);
        card.setScaleY(0.88f);
        card.setAlpha(0.0f);
        card.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        // Header Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(10));

        TextView title = new TextView(this);
        title.setText("ðŸï¸ Dynamic Island");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title);

        TextView badge = new TextView(this);
        badge.setText("  " + shortcuts.size() + " apps");
        badge.setTextColor(0x8AFFFFFF);
        badge.setTextSize(11);
        header.addView(badge);

        View spacer = new View(this);
        LinearLayout.LayoutParams splp = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(splp);
        header.addView(spacer);

        // [ ⚙️ Organize ] button
        TextView btnOrg = new TextView(this);
        btnOrg.setText("⚙️ Organize");
        btnOrg.setTextColor(0xDDFFFFFF);
        btnOrg.setTextSize(12);
        btnOrg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable orgBg = new GradientDrawable();
        orgBg.setColor(0x20FFFFFF);
        orgBg.setCornerRadius(dp(12));
        orgBg.setStroke(dp(1), 0x33FFFFFF);
        btnOrg.setBackground(orgBg);
        btnOrg.setPadding(dp(8), dp(5), dp(8), dp(5));
        btnOrg.setOnClickListener(v -> showIslandOrganizerDialog());
        header.addView(btnOrg);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(6), 1));
        header.addView(gap);

        // [ ï¼‹ Add App ] button
        TextView btnAdd = new TextView(this);
        btnAdd.setText("ï¼‹ Add App");
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setTextSize(12);
        btnAdd.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x28FFFFFF);
        btnBg.setCornerRadius(dp(12));
        btnBg.setStroke(dp(1), 0x4DFFFFFF);
        btnAdd.setBackground(btnBg);
        btnAdd.setPadding(dp(10), dp(5), dp(10), dp(5));
        btnAdd.setOnClickListener(v -> {
            showCollapsed();
            Intent pick = new Intent(this, AppPickerActivity.class);
            pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
            pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(pick);
        });
        header.addView(btnAdd);

        // [ âœ• ] close button
        TextView btnClose = new TextView(this);
        btnClose.setText(" âœ• ");
        btnClose.setTextColor(0x8AFFFFFF);
        btnClose.setTextSize(16);
        btnClose.setPadding(dp(8), dp(4), dp(2), dp(4));
        btnClose.setOnClickListener(v -> showCollapsed());
        header.addView(btnClose);

        card.addView(header);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dlp.bottomMargin = dp(10);
        div.setLayoutParams(dlp);
        card.addView(div);

        if (shortcuts.isEmpty()) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(12), dp(24), dp(12), dp(24));

            TextView emptyGlyph = new TextView(this);
            emptyGlyph.setText("ðŸï¸");
            emptyGlyph.setTextSize(32);
            emptyGlyph.setGravity(Gravity.CENTER);
            emptyBox.addView(emptyGlyph);

            TextView emptyText = new TextView(this);
            emptyText.setText("Dynamic Island is Empty");
            emptyText.setTextColor(0xFFFFFFFF);
            emptyText.setTextSize(14);
            emptyText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dp(6), 0, dp(4));
            emptyBox.addView(emptyText);

            TextView emptySub = new TextView(this);
            emptySub.setText("Add your favorite apps to launch them directly from the island.");
            emptySub.setTextColor(0x8AFFFFFF);
            emptySub.setTextSize(12);
            emptySub.setGravity(Gravity.CENTER);
            emptySub.setPadding(0, 0, 0, dp(14));
            emptyBox.addView(emptySub);

            TextView btnAddBig = new TextView(this);
            btnAddBig.setText("  ï¼‹ Add Apps to Island  ");
            btnAddBig.setTextColor(0xFF000000);
            btnAddBig.setTextSize(13);
            btnAddBig.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            GradientDrawable bBigBg = new GradientDrawable();
            bBigBg.setColor(0xFFFFFFFF);
            bBigBg.setCornerRadius(dp(14));
            btnAddBig.setBackground(bBigBg);
            btnAddBig.setPadding(dp(16), dp(8), dp(16), dp(8));
            btnAddBig.setOnClickListener(v -> {
                showCollapsed();
                Intent pick = new Intent(this, AppPickerActivity.class);
                pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
                pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(pick);
            });
            emptyBox.addView(btnAddBig);
            card.addView(emptyBox);
        } else {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(4);
            int colW = (cardW - dp(32)) / 4;

            for (int i = 0; i < shortcuts.size(); i++) {
                final int index = i;
                final Shortcut s = shortcuts.get(i);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = colW;
                glp.height = dp(76);
                cell.setLayoutParams(glp);
                cell.setPadding(dp(2), dp(4), dp(2), dp(4));

                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
                iv.setLayoutParams(ilp);
                iv.setImageBitmap(IconUtil.iconFor(this, s));
                cell.addView(iv);

                TextView label = new TextView(this);
                label.setText(s.label);
                label.setTextColor(0xD8FFFFFF);
                label.setTextSize(10);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, dp(3), 0, 0);
                cell.addView(label);

                cell.setOnClickListener(v -> {
                    if (IconUtil.fireShortcut(IslandService.this, s)) {
                        showCollapsed();
                    } else {
                        Toast.makeText(IslandService.this, "Couldn't open " + s.label, Toast.LENGTH_SHORT).show();
                    }
                });

                cell.setOnLongClickListener(v -> {
                    showIslandAppMenu(s, index);
                    return true;
                });

                grid.addView(cell);
            }

            ScrollView scroll = new ScrollView(this);
            scroll.addView(grid);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.min(dp(280), dp(84 * ((shortcuts.size() + 3) / 4))));
            scroll.setLayoutParams(slp);
            card.addView(scroll);
        }

        root.addView(card);
        params = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        islandView = root;
        expanded = true;
        try {
            wm.addView(islandView, params);
        } catch (Exception ignored) {
        }
    }

    private void showIslandAppMenu(Shortcut s, int index) {
        if (islandView == null) return;
        final List<Shortcut> shortcuts = ShortcutStore.loadIsland(this);
        String[] opts = {
                "ðŸš€ Launch App",
                "ðŸ—‘ï¸ Remove from Island",
                (index > 0 ? "â¬…ï¸ Move Left" : "â¬…ï¸ (Already First)"),
                (index < shortcuts.size() - 1 ? "âž¡ï¸ Move Right" : "âž¡ï¸ (Already Last)")
        };

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("ðŸï¸ " + s.label);
        b.setItems(opts, (d, which) -> {
            switch (which) {
                case 0:
                    IconUtil.fireShortcut(IslandService.this, s);
                    showCollapsed();
                    break;
                case 1:
                    shortcuts.remove(index);
                    ShortcutStore.saveIsland(IslandService.this, shortcuts);
                    showExpanded();
                    Toast.makeText(IslandService.this, "Removed " + s.label, Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    if (index > 0) {
                        Shortcut prev = shortcuts.get(index - 1);
                        shortcuts.set(index - 1, s);
                        shortcuts.set(index, prev);
                        ShortcutStore.saveIsland(IslandService.this, shortcuts);
                        showExpanded();
                    }
                    break;
                case 3:
                    if (index < shortcuts.size() - 1) {
                        Shortcut next = shortcuts.get(index + 1);
                        shortcuts.set(index + 1, s);
                        shortcuts.set(index, next);
                        ShortcutStore.saveIsland(IslandService.this, shortcuts);
                        showExpanded();
                    }
                    break;
            }
        });
        b.setNegativeButton("Close", null);
        AlertDialog dialog = b.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        try {
            dialog.show();
        } catch (Exception e) {
            IconUtil.fireShortcut(IslandService.this, s);
            showCollapsed();
        }
    }

    private void showIslandSwapDialog(Shortcut s, int index) {
        final List<Shortcut> shortcuts = ShortcutStore.loadIsland(this);
        String[] targets = new String[shortcuts.size()];
        for (int i = 0; i < shortcuts.size(); i++) {
            targets[i] = "Slot " + (i + 1) + ": " + shortcuts.get(i).label;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Swap " + s.label + " with:");
        b.setItems(targets, (d, targetIdx) -> {
            if (targetIdx != index && targetIdx >= 0 && targetIdx < shortcuts.size()) {
                Shortcut target = shortcuts.get(targetIdx);
                shortcuts.set(targetIdx, s);
                shortcuts.set(index, target);
                ShortcutStore.saveIsland(this, shortcuts);
                showExpanded();
                Toast.makeText(this, "Swapped " + s.label + " with " + target.label, Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancel", null);
        AlertDialog dialog = b.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        try {
            dialog.show();
        } catch (Exception ignored) {}
    }

    private void showIslandOrganizerDialog() {
        final List<Shortcut> shortcuts = ShortcutStore.loadIsland(this);
        List<String> opts = new ArrayList<>();
        opts.add("➕ Add Apps to Island");
        opts.add("🔤 Sort Apps A–Z");
        opts.add("🗑️ Clear All Island Apps");
        for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut sc = shortcuts.get(i);
            opts.add((i + 1) + ". " + sc.label + " (Manage)");
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("🏝️ Organize Dynamic Island");
        b.setItems(opts.toArray(new String[0]), (d, which) -> {
            if (which == 0) {
                showCollapsed();
                Intent pick = new Intent(this, AppPickerActivity.class);
                pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
                pick.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(pick);
            } else if (which == 1) {
                shortcuts.sort((s1, s2) -> (s1.label != null ? s1.label : "").compareToIgnoreCase(s2.label != null ? s2.label : ""));
                ShortcutStore.saveIsland(this, shortcuts);
                showExpanded();
                Toast.makeText(this, "Island apps sorted A–Z", Toast.LENGTH_SHORT).show();
            } else if (which == 2) {
                shortcuts.clear();
                ShortcutStore.saveIsland(this, shortcuts);
                showExpanded();
                Toast.makeText(this, "Cleared Dynamic Island", Toast.LENGTH_SHORT).show();
            } else {
                int itemIdx = which - 3;
                if (itemIdx >= 0 && itemIdx < shortcuts.size()) {
                    showIslandAppMenu(shortcuts.get(itemIdx), itemIdx);
                }
            }
        });
        b.setPositiveButton("Done", null);
        AlertDialog dialog = b.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_PHONE);
        }
        try {
            dialog.show();
        } catch (Exception ignored) {}
    }

    private void rebuild() {
        if (expanded) showExpanded();
        else showCollapsed();
    }

    /** Long-press hides the island; the in-app toggle brings it back. */
    private void hideIsland() {
        setIslandOn(this, false);
        Toast.makeText(this, "Island hidden â€” turn it back on in Lock Launcher",
                Toast.LENGTH_LONG).show();
        stopSelf();
    }
}