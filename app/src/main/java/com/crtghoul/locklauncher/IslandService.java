package com.crtghoul.locklauncher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
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

import java.util.List;

/**
 * Dynamic-Island-style floating bar: a collapsed pill at the top of the
 * screen that expands into the active profile's shortcut grid on tap.
 * Needs "Display over other apps" permission. Best-effort on the lock
 * screen — some OEMs (incl. OxygenOS) restrict overlays there.
 */
public class IslandService extends Service {
    public static final String ACTION_REFRESH =
            "com.crtghoul.locklauncher.ISLAND_REFRESH";

    private static boolean running = false;

    private WindowManager wm;
    private View islandView;
    private WindowManager.LayoutParams params;
    private boolean expanded = false;

    public static boolean isRunning() {
        return running;
    }

    public static boolean canShow(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    public static void start(Context ctx) {
        if (!canShow(ctx)) return;
        try {
            ctx.startService(new Intent(ctx, IslandService.class));
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
            ctx.startService(i);
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
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
        removeView();
        super.onDestroy();
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
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
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

    private void showCollapsed() {
        removeView();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        List<Shortcut> shortcuts = ShortcutStore.load(this);

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackgroundResource(R.drawable.dock_card);
        int pad = dp(8);
        pill.setPadding(pad * 2, pad, pad * 2, pad);

        int targetCount = ShortcutStore.islandIconsCount(this);
        int shown = Math.min(targetCount, shortcuts.size());
        final ImageView[] iconViews = new ImageView[shown];
        final Shortcut[] shownShortcuts = new Shortcut[shown];

        if (ShortcutStore.isIslandBattery(this)) {
            TextView bat = new TextView(this);
            bat.setText(IconUtil.getLiveBatteryInfo(this));
            bat.setTextColor(0xFF00E676);
            bat.setTextSize(11);
            bat.setPadding(0, 0, dp(6), 0);
            pill.addView(bat);
        }

        for (int i = 0; i < shown; i++) {
            ImageView iv = new ImageView(this);
            int s = dp(34);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(dp(3), 0, dp(3), 0);
            iv.setLayoutParams(lp);
            iv.setImageBitmap(IconUtil.iconFor(this, shortcuts.get(i)));
            pill.addView(iv);
            iconViews[i] = iv;
            shownShortcuts[i] = shortcuts.get(i);
        }
        if (shown == 0) {
            TextView t = new TextView(this);
            t.setText("＋ Lock Launcher");
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(13);
            pill.addView(t);
        }

        final View pillView = pill;
        // Direct tap on icon = launches shortcut immediately.
        // Tap on padding/background = expands grid.
        // Drag = moves island. Long-press = hides island.

        params = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        // Restore the dragged position (default: top-center under the status bar).
        android.content.SharedPreferences iprefs =
                getSharedPreferences("dock", MODE_PRIVATE);
        params.x = iprefs.getInt("island_x", 0);
        params.y = iprefs.getInt("island_y", statusBarHeight() + dp(8));

        final android.os.Handler handler = new android.os.Handler(getMainLooper());
        final Runnable longPress = this::hideIsland;
        final int[] drag = new int[4]; // startX, startY, downRawX, downRawY
        final boolean[] moved = new boolean[1];
        pill.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    drag[0] = params.x;
                    drag[1] = params.y;
                    drag[2] = (int) e.getRawX();
                    drag[3] = (int) e.getRawY();
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
                    if (!moved[0]) {
                        int tapX = (int) e.getX();
                        int tapY = (int) e.getY();
                        boolean launched = false;
                        android.graphics.Rect rect = new android.graphics.Rect();
                        for (int i = 0; i < iconViews.length; i++) {
                            if (iconViews[i] != null) {
                                iconViews[i].getHitRect(rect);
                                if (rect.contains(tapX, tapY)) {
                                    v.performClick();
                                    if (!IconUtil.fireShortcut(IslandService.this, shownShortcuts[i])) {
                                        Toast.makeText(IslandService.this,
                                                "Couldn't open " + shownShortcuts[i].label,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    launched = true;
                                    break;
                                }
                            }
                        }
                        if (!launched) {
                            v.performClick();
                            showExpanded();
                        }
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
        return Math.max(0, Math.min(h - dp(160), y));
    }

    private void showExpanded() {
        removeView();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        List<Shortcut> shortcuts = ShortcutStore.load(this);

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> showCollapsed());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.dock_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        clp.topMargin = statusBarHeight() + dp(8);
        card.setLayoutParams(clp);
        // Don't let taps on the card collapse it.
        card.setOnClickListener(v -> { });
        card.setOnLongClickListener(v -> {
            hideIsland();
            return true;
        });

        TextView title = new TextView(this);
        title.setText(ShortcutStore.profileName(this,
                ShortcutStore.activeProfile(this)) + "  ·  tap outside to close");
        title.setTextColor(0x8AFFFFFF);
        title.setTextSize(11);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        card.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int cell = dp(72);
        for (int i = 0; i < shortcuts.size(); i++) {
            final Shortcut s = shortcuts.get(i);
            LinearLayout cell2 = new LinearLayout(this);
            cell2.setOrientation(LinearLayout.VERTICAL);
            cell2.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cell;
            glp.height = cell + dp(18);
            cell2.setLayoutParams(glp);

            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp =
                    new LinearLayout.LayoutParams(dp(48), dp(48));
            iv.setLayoutParams(ilp);
            iv.setImageBitmap(IconUtil.iconFor(this, s));
            cell2.addView(iv);

            TextView label = new TextView(this);
            label.setText(s.label);
            label.setTextColor(0xD8FFFFFF);
            label.setTextSize(10);
            label.setMaxLines(1);
            label.setGravity(Gravity.CENTER);
            cell2.addView(label);

            cell2.setOnClickListener(v -> {
                if (IconUtil.fireShortcut(IslandService.this, s)) {
                    showCollapsed();
                } else {
                    Toast.makeText(IslandService.this,
                            "Couldn't open " + s.label, Toast.LENGTH_SHORT).show();
                }
            });
            grid.addView(cell2);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(320));
        scroll.setLayoutParams(slp);
        card.addView(scroll);

        root.addView(card);
        params = baseParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        // Expanded state needs touch: clear NOT_FOCUSABLE so the scrim eats taps.
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        islandView = root;
        expanded = true;
        try {
            wm.addView(islandView, params);
        } catch (Exception ignored) {
        }
    }

    private void rebuild() {
        if (expanded) showExpanded();
        else showCollapsed();
    }

    /** Long-press hides the island; the in-app toggle brings it back. */
    private void hideIsland() {
        setIslandOn(this, false);
        Toast.makeText(this, "Island hidden — turn it back on in Lock Launcher",
                Toast.LENGTH_LONG).show();
        stopSelf();
    }
}
