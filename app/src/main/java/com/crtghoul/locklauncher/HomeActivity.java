package com.crtghoul.locklauncher;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
/**
 * Flagship, fully customizable, offline Android home launcher.
 * Supports AppWidgetHost live widgets, interactive 2x2 app folders,
 * multi-user clone container execution, wallpaper gesture suite (swipe up/down),
 * dynamic grid density, and distraction-free header controls.
 */
public class HomeActivity extends Activity {
    private static final int APPWIDGET_HOST_ID = 1024;
    private static final int REQUEST_PICK_APPWIDGET = 2001;
    private static final int REQUEST_CREATE_APPWIDGET = 2002;
    private static final int REQUEST_PICK_APP_FOR_SLOT = 2005;
    private static final int REQ_PICK_CUSTOM_LOGO = 2006;
    private static final int REQUEST_PICK_APP_FOR_ISLAND = 2007;
    private int pendingSlotIndex = -1;
    private Shortcut pendingLogoShortcut = null;
    private int activeDraggedPosition = -1;
    private boolean dragWasDropped = false;
    private View dragActionBar;
    private View dragActionRemove;
    private View dragActionOptions;
    private View toggleAppsPill;
    private TextView toggleAppsPillText;
    private boolean appsRevealedInCleanMode = false;
    private TextView clockView;
    private TextView dateBatteryView;
    private GridView gridView;
    private View emptyHint;
    private LinearLayout widgetsContainer;
    private Button[] profileTabs = new Button[ShortcutStore.PROFILES];
    private HomeShortcutAdapter adapter;
    private List<Shortcut> shortcuts = new ArrayList<>();
    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private GestureDetector gestureDetector;
    private Shortcut lastRemovedShortcut = null;
    private int lastRemovedIndex = -1;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            updateLiveHeader();
            timeHandler.postDelayed(this, 1000);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayHelper.enforceHighRefreshRate(this);
        setContentView(R.layout.activity_home);
        // AppWidgetHost Initialization
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        try {
            appWidgetHost.startListening();
        } catch (Exception ignored) {
        }
        widgetsContainer = findViewById(R.id.home_widgets_container);
        clockView = findViewById(R.id.home_clock);
        dateBatteryView = findViewById(R.id.home_date_battery);
        gridView = findViewById(R.id.home_grid);
        emptyHint = findViewById(R.id.home_empty_hint);
        dragActionBar = findViewById(R.id.drag_action_bar);
        dragActionRemove = findViewById(R.id.drag_action_remove);
        dragActionOptions = findViewById(R.id.drag_action_options);
        if (dragActionRemove != null) {
            dragActionRemove.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        int src = -1;
                        try {
                            ClipData cd = event.getClipData();
                            if (cd != null && cd.getItemCount() > 0) {
                                src = Integer.parseInt(cd.getItemAt(0).getText().toString());
                            }
                        } catch (Exception ignored) {}
                        if (src >= 0 && src < shortcuts.size()) {
                            dragWasDropped = true;
                            Shortcut s = shortcuts.get(src);
                            lastRemovedShortcut = s;
                            lastRemovedIndex = src;
                            shortcuts.set(src, Shortcut.createEmpty());
                            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Removed " + s.label + " from Home (Tap wallpaper -> Undo)", Toast.LENGTH_LONG).show();
                        }
                        return true;
                }
                return true;
            });
        }
        if (dragActionOptions != null) {
            dragActionOptions.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        int src = -1;
                        try {
                            ClipData cd = event.getClipData();
                            if (cd != null && cd.getItemCount() > 0) {
                                src = Integer.parseInt(cd.getItemAt(0).getText().toString());
                            }
                        } catch (Exception ignored) {}
                        if (src >= 0 && src < shortcuts.size()) {
                            dragWasDropped = true;
                            final int p = src;
                            v.post(() -> showShortcutMenu(p));
                        }
                        return true;
                }
                return true;
            });
        }
        profileTabs[0] = findViewById(R.id.home_tab_p0);
        profileTabs[1] = findViewById(R.id.home_tab_p1);
        profileTabs[2] = findViewById(R.id.home_tab_p2);
        for (int i = 0; i < ShortcutStore.PROFILES; i++) {
            final int p = i;
            profileTabs[i].setOnClickListener(v -> switchProfile(p));
            profileTabs[i].setOnLongClickListener(v -> {
                renameProfile(p);
                return true;
            });
        }
        findViewById(R.id.btn_home_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
        });
        findViewById(R.id.home_search_bar).setOnClickListener(v -> {
            Intent intent = new Intent(this, AppPickerActivity.class);
            intent.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            startActivity(intent);
            overridePendingTransition(R.anim.drawer_slide_up, R.anim.launcher_fade_out);
        });
        View quickWidget = findViewById(R.id.btn_quick_add_widget);
        if (quickWidget != null) {
            quickWidget.setOnClickListener(v -> showWidgetMenu());
        }
        findViewById(R.id.btn_quick_add_web).setOnClickListener(v -> {
            showAddWebAppDialog("", "");
        });
        toggleAppsPill = findViewById(R.id.btn_toggle_apps_pill);
        toggleAppsPillText = findViewById(R.id.text_toggle_apps_pill);
        if (toggleAppsPill != null) {
            toggleAppsPill.setOnClickListener(v -> toggleCleanModeApps());
            toggleAppsPill.setOnLongClickListener(v -> {
                showCleanModeOptionsDialog();
                return true;
            });
        }
        View.OnLongClickListener bgLongClickListener = v -> {
            showWallpaperMenu();
            return true;
        };
        View root = findViewById(R.id.home_root);
        if (root != null) root.setOnLongClickListener(bgLongClickListener);
        View header = findViewById(R.id.header_container);
        if (header != null) header.setOnLongClickListener(bgLongClickListener);
        // Swipe Gestures: Swipe Up for Drawer, Swipe Down for Notifications, Double-Tap to Lock
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY = 120;
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (ShortcutStore.isDoubleTapLock(HomeActivity.this)) {
                    if (LockScreenService.isServiceRunning()) {
                        LockScreenService.lockScreen(HomeActivity.this);
                        return true;
                    } else {
                        LockScreenService.requestAccessibilityPermission(HomeActivity.this);
                        return true;
                    }
                }
                return false;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY) {
                        if (diffY < 0) {
                            if (ShortcutStore.isHomeCleanMode(HomeActivity.this) && !appsRevealedInCleanMode) {
                                toggleCleanModeApps();
                                return true;
                            }
                            // Swipe up -> Open searchable app drawer
                            Intent drawer = new Intent(HomeActivity.this, AppPickerActivity.class);
                            drawer.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
                            startActivity(drawer);
                            overridePendingTransition(R.anim.drawer_slide_up, R.anim.launcher_fade_out);
                            return true;
                        } else {
                            if (ShortcutStore.isHomeCleanMode(HomeActivity.this) && appsRevealedInCleanMode) {
                                toggleCleanModeApps();
                                return true;
                            }
                            // Swipe down -> Open notification shade
                            expandNotificationShade();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        adapter = new HomeShortcutAdapter();
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            launchOrHandleShortcut(position, view);
        });
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shortcuts.size()) {
                Shortcut s = shortcuts.get(position);
                if (s.isEmpty()) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAddShortcutDialogForSlot(position);
                    return true;
                }
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                activeDraggedPosition = position;
                dragWasDropped = false;
                if (dragActionBar != null) dragActionBar.setVisibility(View.VISIBLE);
                ClipData data = ClipData.newPlainText("pos", String.valueOf(position));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(data, shadow, view, View.DRAG_FLAG_OPAQUE);
                } else {
                    view.startDrag(data, shadow, view, 0);
                }
                view.animate().scaleX(0.85f).scaleY(0.85f).alpha(0.45f).setDuration(120).start();
                return true;
            }
            return false;
        });
        loadShortcuts();
        loadWidgets();
        applyDynamicTheming();
        handleCloneLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCloneLaunchIntent(intent);
    }

    private void handleCloneLaunchIntent(Intent intent) {
        if (intent == null || !"com.crtghoul.locklauncher.LAUNCH_CLONE".equals(intent.getAction())) return;
        String pkg = intent.getStringExtra("clone_pkg");
        long serial = intent.getLongExtra("clone_serial", 0L);
        intent.setAction(null);
        if (pkg != null && !pkg.isEmpty() && serial > 0) {
            Shortcut sc = new Shortcut(Shortcut.TYPE_APP, pkg, "", pkg, serial);
            IconUtil.fireShortcut(this, sc);
        }
    }
    private void launchOrHandleShortcut(int position, View view) {
        if (position < 0 || position >= shortcuts.size()) return;
        Shortcut s = shortcuts.get(position);
        if (s.isEmpty()) {
            if (view != null) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAddShortcutDialogForSlot(position);
            return;
        }
        if (s.isFolder()) {
            showFolderModal(s);
            return;
        }
        if (s.isApp() && ShortcutStore.isAppLocked(this, s.pkg)) {
            BiometricHelper.authenticate(this, "Unlock " + s.label, "Authenticate to open app", new BiometricHelper.AuthCallback() {
                @Override
                public void onSuccess() {
                    performFireShortcut(s, view);
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(HomeActivity.this, "Authentication required to open " + s.label, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        performFireShortcut(s, view);
    }
    private void performFireShortcut(Shortcut s, View view) {
        boolean ok = IconUtil.fireShortcut(this, s, view);
        if (!ok) {
            Toast.makeText(this, IconUtil.launchFailureReason(this, s), Toast.LENGTH_SHORT).show();
        }
    }
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
    private void alignShortcutsBottom() {
        List<Shortcut> nonEmpties = new ArrayList<>();
        for (Shortcut sc : shortcuts) {
            if (!sc.isEmpty()) nonEmpties.add(sc);
        }
        if (nonEmpties.isEmpty()) return;
        shortcuts.clear();
        int emptyCount = Math.max(0, ShortcutStore.MAX - nonEmpties.size());
        for (int i = 0; i < emptyCount; i++) {
            shortcuts.add(Shortcut.createEmpty());
        }
        shortcuts.addAll(nonEmpties);
        while (shortcuts.size() < ShortcutStore.MAX) {
            shortcuts.add(Shortcut.createEmpty());
        }
    }
    private void pushShortcutsToBottom() {
        alignShortcutsBottom();
        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
        loadShortcuts();
        BaseDockWidget.refreshAll(this);
        DockService.refresh(this);
        Toast.makeText(this, "Pushed icons to bottom for one-hand reach", Toast.LENGTH_SHORT).show();
    }
    private void applyDynamicTheming() {
        try {
            View searchBar = findViewById(R.id.home_search_bar);
            if (searchBar != null) {
                GlassThemeHelper.applyToView(this, searchBar, 24f);
            }
            View nowPlaying = findViewById(R.id.home_now_playing_bar);
            if (nowPlaying != null) {
                GlassThemeHelper.applyToView(this, nowPlaying, 20f);
            }
            View pill = findViewById(R.id.btn_toggle_apps_pill);
            if (pill != null) {
                GlassThemeHelper.applyToView(this, pill, 20f);
            }
            View tabs = findViewById(R.id.home_tabs_container);
            if (tabs != null) {
                GlassThemeHelper.applyToView(this, tabs, 20f);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                DynamicThemeHelper.ThemeColors theme = DynamicThemeHelper.extractTheme(this);
                Button btnAddWeb = findViewById(R.id.btn_quick_add_web);
                if (btnAddWeb != null && btnAddWeb.getBackground() != null) {
                    btnAddWeb.getBackground().setTint(theme.primaryAccent);
                }
            }
        } catch (Exception ignored) {
        }
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }
    private final BroadcastReceiver notificationBadgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateLiveHeader();
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        DisplayHelper.enforceHighRefreshRate(this);
        timeHandler.post(timeRunnable);
        try {
            IntentFilter filter = new IntentFilter(LockNotificationListenerService.ACTION_NOTIFICATIONS_CHANGED);
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationBadgeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notificationBadgeReceiver, filter);
            }
        } catch (Exception ignored) {}
        // Apply dynamic grid columns
        if (gridView != null) {
            gridView.setNumColumns(ShortcutStore.homeGridColumns(this));
        }
        loadShortcuts();
        loadWidgets();
        applyDynamicTheming();
    }
    @Override
    protected void onPause() {
        super.onPause();
        timeHandler.removeCallbacks(timeRunnable);
        try {
            unregisterReceiver(notificationBadgeReceiver);
        } catch (Exception ignored) {}
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appWidgetHost != null) {
            try {
                appWidgetHost.stopListening();
            } catch (Exception ignored) {
            }
        }
    }
    @Override
    public void onBackPressed() {
        // Native launcher invariant: back does not exit home
    }
    private void expandNotificationShade() {
        try {
            Object service = getSystemService("statusbar");
            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
            java.lang.reflect.Method expand = statusbarManager.getMethod("expandNotificationsPanel");
            expand.invoke(service);
        } catch (Exception e) {
            try {
                Object service = getSystemService("statusbar");
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                java.lang.reflect.Method expand = statusbarManager.getMethod("expand");
                expand.invoke(service);
            } catch (Exception ignored) {
            }
        }
    }
    private void updateLiveHeader() {
        boolean showClock = ShortcutStore.isHomeClock(this);
        boolean showBattery = ShortcutStore.isHomeBattery(this);
        boolean showTabs = ShortcutStore.isHomeProfiles(this);
        View tabsContainer = findViewById(R.id.home_tabs_container);
        if (tabsContainer != null) {
            tabsContainer.setVisibility(showTabs ? View.VISIBLE : View.GONE);
        }
        if (clockView != null) {
            clockView.setVisibility(showClock ? View.VISIBLE : View.GONE);
            if (showClock) {
                clockView.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()));
            }
        }
        if (dateBatteryView != null) {
            dateBatteryView.setVisibility((showClock || showBattery) ? View.VISIBLE : View.GONE);
            String date = showClock ? new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date()) : "";
            String battery = showBattery ? IconUtil.getLiveBatteryInfo(this) : "";
            if (!date.isEmpty() && !battery.isEmpty()) {
                dateBatteryView.setText(date + "  ·  " + battery);
            } else if (!date.isEmpty()) {
                dateBatteryView.setText(date);
            } else if (!battery.isEmpty()) {
                dateBatteryView.setText(battery);
            }
        }
        // 🌟 At a Glance Smart Bar (100% on-device next alarm, calendar, battery)
        TextView glanceAlarm = findViewById(R.id.glance_alarm);
        TextView glanceEvent = findViewById(R.id.glance_event);
        TextView glanceBattery = findViewById(R.id.glance_battery);
        if (glanceAlarm != null) {
            AtAGlanceHelper.GlanceItem alarm = AtAGlanceHelper.getNextAlarm(this);
            if (alarm != null) {
                glanceAlarm.setText(alarm.icon + " " + alarm.title + " (" + alarm.subtitle + ")");
                glanceAlarm.setVisibility(View.VISIBLE);
                glanceAlarm.setOnClickListener(v -> {
                    try { startActivity(alarm.actionIntent); } catch (Exception ignored) {}
                });
            } else {
                glanceAlarm.setVisibility(View.GONE);
            }
        }
        if (glanceEvent != null) {
            AtAGlanceHelper.GlanceItem event = AtAGlanceHelper.getNextCalendarEvent(this);
            if (event != null) {
                glanceEvent.setText(event.icon + " " + event.title + " • " + event.subtitle);
                glanceEvent.setVisibility(View.VISIBLE);
                glanceEvent.setOnClickListener(v -> {
                    try { startActivity(event.actionIntent); } catch (Exception ignored) {}
                });
            } else {
                glanceEvent.setVisibility(View.GONE);
            }
        }
        if (glanceBattery != null) {
            AtAGlanceHelper.GlanceItem bat = AtAGlanceHelper.getBatteryStatus(this);
            if (bat != null && showBattery) {
                glanceBattery.setText(bat.icon + " " + bat.title + ("Charging".equals(bat.subtitle) ? " ⚡" : ""));
                glanceBattery.setVisibility(View.VISIBLE);
                glanceBattery.setOnClickListener(v -> {
                    if (bat.actionIntent != null) {
                        try { startActivity(bat.actionIntent); } catch (Exception ignored) {}
                    }
                });
            } else {
                glanceBattery.setVisibility(View.GONE);
            }
        }
        // 🎵 Live Now Playing Media Pill (WebAppActivity YouTube Music / YouTube & System Media)
        View nowPlayingBar = findViewById(R.id.home_now_playing_bar);
        if (nowPlayingBar != null) {
            NowPlayingHelper.MediaState media = NowPlayingHelper.getActiveState(this);
            if (media != null && media.title != null && !media.title.isEmpty()) {
                nowPlayingBar.setVisibility(View.VISIBLE);
                TextView npTitle = findViewById(R.id.now_playing_title);
                TextView npArtist = findViewById(R.id.now_playing_artist);
                TextView btnPrev = findViewById(R.id.btn_media_prev);
                TextView btnToggle = findViewById(R.id.btn_media_toggle);
                TextView btnNext = findViewById(R.id.btn_media_next);
                View infoBox = findViewById(R.id.now_playing_info_container);

                String prefix = media.isPlaying ? "▮▯▮  " : "▯▯▯  ";
                if (npTitle != null) npTitle.setText(prefix + media.title);
                if (npArtist != null) {
                    String sub = (media.artist != null && !media.artist.isEmpty()) ? media.artist : "Playing in background";
                    npArtist.setText(sub + "  ·  Tap to open");
                }
                if (btnToggle != null) {
                    btnToggle.setText(media.isPlaying ? "⏸" : "▶");
                    btnToggle.setOnClickListener(v -> {
                        HapticHelper.tick(v);
                        SpringAnimator.scalePulse(v);
                        NowPlayingHelper.sendCommand(this, NowPlayingHelper.CMD_TOGGLE);
                        updateLiveHeader();
                    });
                }
                if (btnPrev != null) {
                    btnPrev.setOnClickListener(v -> {
                        HapticHelper.tick(v);
                        SpringAnimator.scalePulse(v);
                        NowPlayingHelper.sendCommand(this, NowPlayingHelper.CMD_PREV);
                    });
                }
                if (btnNext != null) {
                    btnNext.setOnClickListener(v -> {
                        HapticHelper.tick(v);
                        SpringAnimator.scalePulse(v);
                        NowPlayingHelper.sendCommand(this, NowPlayingHelper.CMD_NEXT);
                    });
                }
                if (infoBox != null) {
                    infoBox.setOnClickListener(v -> {
                        HapticHelper.tick(v);
                        NowPlayingHelper.openActivePlayer(this);
                    });
                }
            } else {
                nowPlayingBar.setVisibility(View.GONE);
            }
        }
    }
    private void showWallpaperMenu() {
        List<String> opts = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (lastRemovedShortcut != null) {
            final Shortcut toRestore = lastRemovedShortcut;
            final int restorePos = lastRemovedIndex;
            opts.add("Undo: Restore " + toRestore.label);
            acts.add(() -> {
                int p = ShortcutStore.activeProfile(this);
                int pos = Math.min(Math.max(0, restorePos), shortcuts.size());
                shortcuts.set(pos, toRestore);
                ShortcutStore.save(this, p, shortcuts);
                loadShortcuts();
                Toast.makeText(this, "Restored " + toRestore.label, Toast.LENGTH_SHORT).show();
                lastRemovedShortcut = null;
                lastRemovedIndex = -1;
            });
        }
        int currentProfile = ShortcutStore.activeProfile(this);
        List<Integer> currentWidgets = ShortcutStore.homeWidgets(this, currentProfile);
        if (!currentWidgets.isEmpty()) {
            opts.add("Delete / Manage Widgets (" + currentWidgets.size() + " active)");
            acts.add(this::showManageWidgetsDialog);
        }
        opts.add("Add Android Widget");
        acts.add(this::showWidgetMenu);
        opts.add("Add Apps to Home Screen");
        acts.add(() -> {
            Intent addAppIntent = new Intent(this, AppPickerActivity.class);
            addAppIntent.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            startActivity(addAppIntent);
        });
        opts.add("Move & Organize Apps (Push to Bottom, Sort, Clear All)");
        acts.add(this::showGridOrganizerDialog);
        boolean showEmptyOutlines = ShortcutStore.isShowEmptyGridSlots(this);
        opts.add(showEmptyOutlines ? "Hide Empty Grid Outlines (Clean Wallpaper)" : "Show Empty Grid Outlines (Edit Slots)");
        acts.add(() -> {
            ShortcutStore.setShowEmptyGridSlots(this, !showEmptyOutlines);
            loadShortcuts();
            Toast.makeText(this, !showEmptyOutlines ? "Empty slot targets visible — tap any slot to place app/widget" : "Empty slot outlines hidden for clean wallpaper", Toast.LENGTH_SHORT).show();
        });
        opts.add("Grid Columns & Layout (4x4, 4x8, Bottom Thumb-Reach)");
        acts.add(this::showColumnsDialog);
        opts.add("Dynamic Island Settings (16 Slots & Toggle)");
        acts.add(this::showIslandAppManagerDialog);
        boolean clean = ShortcutStore.isHomeCleanMode(this);
        opts.add(clean ? "Clean Mode: Enabled (Tap to Show Apps)" : "Clean Mode: Hide Apps behind Pill");
        acts.add(() -> {
            boolean next = !clean;
            ShortcutStore.setHomeCleanMode(this, next);
            appsRevealedInCleanMode = false;
            applyCleanModeState(true);
            Toast.makeText(this, next ? "Clean Mode active — tap Apps pill to reveal." : "Clean Mode disabled", Toast.LENGTH_SHORT).show();
        });
        opts.add("Create New Folder");
        acts.add(this::createNewFolderDialog);
        opts.add("Change Wallpaper");
        acts.add(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
            } catch (Exception e) {
                Toast.makeText(this, "Wallpaper picker not found", Toast.LENGTH_SHORT).show();
            }
        });
        opts.add("Add Web App");
        acts.add(() -> showAddWebAppDialog("", ""));
        opts.add("Search & All Apps");
        acts.add(() -> {
            Intent drawer = new Intent(this, AppPickerActivity.class);
            drawer.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            startActivity(drawer);
        });
        String curGlass = GlassThemeHelper.getGlassMode(this);
        opts.add("Glass & Blur Style (" + curGlass.toUpperCase(Locale.ROOT) + ") — Cycle Frosted / Liquid / Classic");
        acts.add(() -> {
            String nextMode = GlassThemeHelper.MODE_FROSTED.equals(curGlass)
                    ? GlassThemeHelper.MODE_LIQUID
                    : (GlassThemeHelper.MODE_LIQUID.equals(curGlass) ? GlassThemeHelper.MODE_CLASSIC : GlassThemeHelper.MODE_FROSTED);
            GlassThemeHelper.setGlassMode(this, nextMode);
            GlassThemeHelper.refreshAllSurfaces(this);
            applyDynamicTheming();
            loadShortcuts();
            Toast.makeText(this, "Glass theme: " + nextMode, Toast.LENGTH_SHORT).show();
        });
        boolean minIcons = ShortcutStore.isMinimalIcons(this);
        opts.add(minIcons ? "Icons: Minimal (Switch to Original)" : "Icons: Original (Switch to Minimal)");
        acts.add(() -> {
            ShortcutStore.setMinimalIcons(this, !minIcons);
            IconUtil.clearCache();
            loadShortcuts();
            GlassThemeHelper.refreshAllSurfaces(this);
            Toast.makeText(this, !minIcons ? "Minimal icons enabled" : "Original icons restored", Toast.LENGTH_SHORT).show();
        });
        opts.add("Launcher Settings");
        acts.add(() -> startActivity(new Intent(this, MainActivity.class)));
        new AlertDialog.Builder(this)
                .setTitle("Home Screen Customization")
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < acts.size()) {
                        acts.get(which).run();
                    }
                })
                .show();
    }
    // ---- Widget Management ----
    private void showWidgetMenu() {
        int profile = ShortcutStore.activeProfile(this);
        List<Integer> widgetIds = ShortcutStore.homeWidgets(this, profile);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        options.add("+ Add New Widget...");
        actions.add(this::selectWidget);

        if (!widgetIds.isEmpty()) {
            options.add("Delete / Manage Widgets (" + widgetIds.size() + " active)...");
            actions.add(this::showManageWidgetsDialog);

            options.add("Remove All Widgets from this Screen");
            actions.add(this::removeAllWidgetsConfirm);
        }

        new AlertDialog.Builder(this)
                .setTitle("Home Screen Widgets")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void showManageWidgetsDialog() {
        int profile = ShortcutStore.activeProfile(this);
        List<Integer> widgetIds = ShortcutStore.homeWidgets(this, profile);
        if (widgetIds.isEmpty()) {
            Toast.makeText(this, "No active widgets to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[widgetIds.size() + 1];
        for (int i = 0; i < widgetIds.size(); i++) {
            int wId = widgetIds.get(i);
            String name = "Widget #" + wId;
            try {
                if (appWidgetManager != null) {
                    AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(wId);
                    if (info != null && info.label != null && !info.label.isEmpty()) {
                        name = info.label;
                    }
                }
            } catch (Exception ignored) {}
            items[i] = "Delete: " + name;
        }
        items[widgetIds.size()] = "Delete All Widgets";

        new AlertDialog.Builder(this)
                .setTitle("Manage & Delete Widgets")
                .setItems(items, (d, which) -> {
                    if (which == widgetIds.size()) {
                        removeAllWidgetsConfirm();
                    } else {
                        int wId = widgetIds.get(which);
                        ShortcutStore.removeHomeWidget(this, profile, wId);
                        try {
                            if (appWidgetHost != null) appWidgetHost.deleteAppWidgetId(wId);
                        } catch (Exception ignored) {}
                        loadWidgets();
                        Toast.makeText(this, "Widget deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Done", null)
                .show();
    }

    private void removeAllWidgetsConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Remove All Widgets?")
                .setMessage("This will remove all widgets from the current home screen profile.")
                .setPositiveButton("Remove All", (d, w) -> {
                    int profile = ShortcutStore.activeProfile(this);
                    List<Integer> widgetIds = ShortcutStore.homeWidgets(this, profile);
                    for (int wId : widgetIds) {
                        try {
                            if (appWidgetHost != null) appWidgetHost.deleteAppWidgetId(wId);
                        } catch (Exception ignored) {}
                    }
                    ShortcutStore.setHomeWidgets(this, profile, new ArrayList<>());
                    loadWidgets();
                    Toast.makeText(this, "All widgets removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void selectWidget() {
        if (appWidgetHost == null) return;
        String[] choices = {
                "Add Lock Launcher 2x1 Quick Dock Widget",
                "Add Lock Launcher 4x1 App Dock Widget",
                "Add Lock Launcher 1x1 Mini Pill Widget",
                "Add Lock Launcher Full Dock Widget (16-Slot)",
                "Browse All Installed Android Widgets..."
        };
        new AlertDialog.Builder(this)
                .setTitle("Add Widget")
                .setItems(choices, (d, which) -> {
                    if (which == 0) {
                        bindInternalWidget(DockWidget2x1.class);
                    } else if (which == 1) {
                        bindInternalWidget(DockWidget4x1.class);
                    } else if (which == 2) {
                        bindInternalWidget(DockWidget1x1.class);
                    } else if (which == 3) {
                        bindInternalWidget(DockWidgetProvider.class);
                    } else {
                        launchSystemWidgetPicker();
                    }
                })
                .show();
    }
    private void bindInternalWidget(Class<?> providerClass) {
        if (appWidgetHost == null || appWidgetManager == null) return;
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        android.content.ComponentName cn = new android.content.ComponentName(this, providerClass);
        boolean bound = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn);
        }
        if (bound) {
            int profile = ShortcutStore.activeProfile(this);
            ShortcutStore.addHomeWidget(this, profile, appWidgetId);
            loadWidgets();
            BaseDockWidget.refreshAll(this);
            Toast.makeText(this, "App Widget added to Home screen", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, cn);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        }
    }
    private void launchSystemWidgetPicker() {
        if (appWidgetHost == null) return;
        int appWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_APP_FOR_SLOT && resultCode == RESULT_OK && data != null && pendingSlotIndex >= 0) {
            ArrayList<String> pkgs = data.getStringArrayListExtra(AppPickerActivity.EXTRA_PACKAGES);
            ArrayList<String> labels = data.getStringArrayListExtra(AppPickerActivity.EXTRA_LABELS);
            long[] serials = data.getLongArrayExtra(AppPickerActivity.EXTRA_PROFILE_SERIALS);
            if (pkgs != null && !pkgs.isEmpty()) {
                String pkg = pkgs.get(0);
                String label = labels != null && !labels.isEmpty() ? labels.get(0) : pkg;
                long serial = serials != null && serials.length > 0 ? serials[0] : 0;
                Shortcut sc = new Shortcut(Shortcut.TYPE_APP, pkg, "", label, serial);
                shortcuts.set(pendingSlotIndex, sc);
                int nextSlot = pendingSlotIndex + 1;
                for (int i = 1; i < pkgs.size(); i++) {
                    while (nextSlot < shortcuts.size() && !shortcuts.get(nextSlot).isEmpty()) {
                        nextSlot++;
                    }
                    if (nextSlot < shortcuts.size()) {
                        String p = pkgs.get(i);
                        String l = i < labels.size() ? labels.get(i) : p;
                        long s = (serials != null && i < serials.length) ? serials[i] : 0;
                        shortcuts.set(nextSlot, new Shortcut(Shortcut.TYPE_APP, p, "", l, s));
                        nextSlot++;
                    }
                }
                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                loadShortcuts();
                BaseDockWidget.refreshAll(this);
                DockService.refresh(this);
                Toast.makeText(this, "Placed in slot " + (pendingSlotIndex + 1), Toast.LENGTH_SHORT).show();
            }
            pendingSlotIndex = -1;
        } else if (requestCode == REQ_PICK_CUSTOM_LOGO && resultCode == RESULT_OK && data != null && pendingLogoShortcut != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    InputStream is = getContentResolver().openInputStream(imageUri);
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                    if (bmp != null) {
                        Bitmap cropped = IconUtil.squareCrop(bmp, 144);
                        IconUtil.saveCustomIcon(this, pendingLogoShortcut.id, cropped);
                        IconUtil.clearLruCache();
                        loadShortcuts();
                        BaseDockWidget.refreshAll(this);
                        DockService.refresh(this);
                        Toast.makeText(this, "Logo updated from image", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
            }
            pendingLogoShortcut = null;
        } else if (requestCode == REQUEST_PICK_APP_FOR_ISLAND && resultCode == RESULT_OK && data != null) {
            ArrayList<String> pkgs = data.getStringArrayListExtra(AppPickerActivity.EXTRA_PACKAGES);
            ArrayList<String> labels = data.getStringArrayListExtra(AppPickerActivity.EXTRA_LABELS);
            long[] serials = data.getLongArrayExtra(AppPickerActivity.EXTRA_PROFILE_SERIALS);
            if (pkgs != null && !pkgs.isEmpty()) {
                List<Shortcut> island = ShortcutStore.loadIsland(this);
                for (int i = 0; i < pkgs.size() && island.size() < ShortcutStore.MAX; i++) {
                    String p = pkgs.get(i);
                    String l = (labels != null && i < labels.size()) ? labels.get(i) : p;
                    long s = (serials != null && i < serials.length) ? serials[i] : 0;
                    island.add(new Shortcut(Shortcut.TYPE_APP, p, "", l, s));
                }
                ShortcutStore.saveIsland(this, island);
                IslandService.refresh(this);
                Toast.makeText(this, "Added to Dynamic Island!", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1 && appWidgetHost != null) {
                appWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }
    private void configureWidget(Intent data) {
        if (data == null) return;
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo != null && appWidgetInfo.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }
    private void createWidget(Intent data) {
        if (data == null) return;
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (appWidgetId == -1) return;
        int profile = ShortcutStore.activeProfile(this);
        ShortcutStore.addHomeWidget(this, profile, appWidgetId);
        loadWidgets();
        Toast.makeText(this, "Widget added to home screen", Toast.LENGTH_SHORT).show();
    }
    private void loadWidgets() {
        if (widgetsContainer == null || appWidgetManager == null || appWidgetHost == null) return;
        widgetsContainer.removeAllViews();
        int profile = ShortcutStore.activeProfile(this);
        List<Integer> widgetIds = ShortcutStore.homeWidgets(this, profile);
        for (int i = 0; i < widgetIds.size(); i++) {
            int wId = widgetIds.get(i);
            final int widgetIndex = i;
            try {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(wId);
                if (info != null) {
                    AppWidgetHostView hostView = appWidgetHost.createView(this, wId, info);
                    hostView.setAppWidget(wId, info);
                    final int currentWidgetId = wId;
                    hostView.setOnLongClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        List<String> wOpts = new ArrayList<>();
                        List<Runnable> wActs = new ArrayList<>();
                        if (widgetIndex > 0) {
                            wOpts.add("⬆️ Move Widget Up");
                            wActs.add(() -> {
                                List<Integer> cur = ShortcutStore.homeWidgets(this, profile);
                                if (widgetIndex < cur.size()) {
                                    int item = cur.remove(widgetIndex);
                                    cur.add(widgetIndex - 1, item);
                                    ShortcutStore.setHomeWidgets(this, profile, cur);
                                    loadWidgets();
                                    Toast.makeText(this, "Moved widget up", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        if (widgetIndex < widgetIds.size() - 1) {
                            wOpts.add("⬇️ Move Widget Down");
                            wActs.add(() -> {
                                List<Integer> cur = ShortcutStore.homeWidgets(this, profile);
                                if (widgetIndex < cur.size()) {
                                    int item = cur.remove(widgetIndex);
                                    cur.add(widgetIndex + 1, item);
                                    ShortcutStore.setHomeWidgets(this, profile, cur);
                                    loadWidgets();
                                    Toast.makeText(this, "Moved widget down", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        wOpts.add("📐 Resize Widget Height");
                        wActs.add(() -> showResizeWidgetDialog(currentWidgetId));
                        wOpts.add("➕ Add Another Widget");
                        wActs.add(this::selectWidget);
                        wOpts.add("🗑️ Remove Widget");
                        wActs.add(() -> {
                            ShortcutStore.removeHomeWidget(this, profile, currentWidgetId);
                            try {
                                appWidgetHost.deleteAppWidgetId(currentWidgetId);
                            } catch (Exception ignored) {
                            }
                            loadWidgets();
                            Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show();
                        });
                        new AlertDialog.Builder(this)
                                .setTitle(info.label != null ? info.label : "Widget Controls")
                                .setItems(wOpts.toArray(new String[0]), (d, which) -> wActs.get(which).run())
                                .show();
                        return true;
                    });
                    int customHeight = ShortcutStore.widgetHeight(this, wId);
                    int heightPx = customHeight == -2
                            ? ViewGroup.LayoutParams.WRAP_CONTENT
                            : (int) (getResources().getDisplayMetrics().density * customHeight);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
                    lp.bottomMargin = (int) (getResources().getDisplayMetrics().density * 10);
                    widgetsContainer.addView(hostView, lp);
                }
            } catch (Exception ignored) {
            }
        }
    }
    private void showResizeWidgetDialog(int widgetId) {
        String[] heights = {
                "Wrap Content (Auto)",
                "Compact (85 dp)",
                "Standard (140 dp)",
                "Medium (200 dp)",
                "Expanded (280 dp)",
                "Full Height (360 dp)"
        };
        int[] values = {-2, 85, 140, 200, 280, 360};
        new AlertDialog.Builder(this)
                .setTitle("Resize Widget Height")
                .setItems(heights, (d, which) -> {
                    ShortcutStore.setWidgetHeight(this, widgetId, values[which]);
                    loadWidgets();
                    Toast.makeText(this, "Widget resized to " + heights[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }
    // ---- Folders ----
    private void createNewFolderDialog() {
        EditText input = new EditText(this);
        input.setHint("Folder name (e.g. Agents, Media)");
        new AlertDialog.Builder(this)
                .setTitle("New Folder")
                .setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "Folder";
                    int p = ShortcutStore.activeProfile(this);
                    Shortcut folder = Shortcut.createFolder(name, new ArrayList<>());
                    if (!ShortcutStore.addOrPlaceShortcut(shortcuts, folder)) {
                        Toast.makeText(this, "Home screen is full (" + ShortcutStore.MAX + " max)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ShortcutStore.save(this, p, shortcuts);
                    loadShortcuts();
                    Toast.makeText(this, "Created folder: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void showFolderModal(Shortcut folder) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder, null);
        TextView titleView = dialogView.findViewById(R.id.folder_title);
        titleView.setText(folder.label);
        GridView grid = dialogView.findViewById(R.id.folder_grid);
        View emptyHintView = dialogView.findViewById(R.id.folder_empty_hint);
        Button btnRename = dialogView.findViewById(R.id.btn_folder_rename);
        Button btnAdd = dialogView.findViewById(R.id.btn_folder_add_app);
        Button btnClose = dialogView.findViewById(R.id.btn_folder_close);
        List<Shortcut> items = folder.folderItems != null ? folder.folderItems : new ArrayList<>();
        emptyHintView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        BaseAdapter folderAdapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Shortcut getItem(int p) { return items.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(HomeActivity.this).inflate(R.layout.item_home_shortcut, parent, false);
                }
                Shortcut child = getItem(pos);
                ImageView icon = v.findViewById(R.id.sc_icon);
                TextView label = v.findViewById(R.id.sc_label);
                TextView badge = v.findViewById(R.id.sc_badge);
                icon.setImageBitmap(IconUtil.iconFor(HomeActivity.this, child));
                label.setText(child.label);
                label.setVisibility(ShortcutStore.isHomeHideLabels(HomeActivity.this) ? View.GONE : View.VISIBLE);
                if (badge != null) {
                    if (child.isApp()) {
                        LockNotificationListenerService.bindBadgeView(HomeActivity.this, badge, child.pkg);
                    } else {
                        badge.setVisibility(View.GONE);
                    }
                }
                v.setOnClickListener(childView -> {
                    dialog.dismiss();
                    if (!IconUtil.fireShortcut(HomeActivity.this, child, childView)) {
                        Toast.makeText(HomeActivity.this, IconUtil.launchFailureReason(HomeActivity.this, child), Toast.LENGTH_SHORT).show();
                    }
                });
                return v;
            }
        };
        grid.setAdapter(folderAdapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Shortcut child = items.get(pos);
            dialog.dismiss();
            if (!IconUtil.fireShortcut(HomeActivity.this, child)) {
                Toast.makeText(HomeActivity.this, IconUtil.launchFailureReason(HomeActivity.this, child), Toast.LENGTH_SHORT).show();
            }
        });
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            Shortcut child = items.get(pos);
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle(child.label)
                    .setMessage("Remove from " + folder.label + "?")
                    .setPositiveButton("Remove", (d, w) -> {
                        items.remove(pos);
                        folderAdapter.notifyDataSetChanged();
                        emptyHintView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        ShortcutStore.save(HomeActivity.this, ShortcutStore.activeProfile(HomeActivity.this), shortcuts);
                        loadShortcuts();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        btnRename.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setText(folder.label);
            new AlertDialog.Builder(this)
                    .setTitle("Rename Folder")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) {
                            folder.label = n;
                            titleView.setText(n);
                            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                            loadShortcuts();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        btnAdd.setOnClickListener(v -> {
            dialog.dismiss();
            Intent i = new Intent(this, AppPickerActivity.class);
            i.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            Toast.makeText(this, "Long-press any app in drawer and choose 'Add to Folder'", Toast.LENGTH_LONG).show();
            startActivity(i);
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    // ---- Profile & Shortcut Management ----
    private void loadShortcuts() {
        int currentProfile = ShortcutStore.activeProfile(this);
        for (int i = 0; i < ShortcutStore.PROFILES; i++) {
            profileTabs[i].setText(ShortcutStore.profileName(this, i));
            if (i == currentProfile) {
                profileTabs[i].setBackgroundResource(R.drawable.tab_active_pill);
                profileTabs[i].setTextColor(0xFFFFFFFF);
            } else {
                profileTabs[i].setBackgroundColor(Color.TRANSPARENT);
                profileTabs[i].setTextColor(0xBBFFFFFF);
            }
        }
        shortcuts = ShortcutStore.loadPadded(this, currentProfile);
        for (Shortcut s : shortcuts) {
            if (s != null && s.isApp() && s.pkg != null) {
                String custom = ShortcutStore.getCustomAppLabel(this, s.pkg, s.profileSerial);
                if (custom != null && !custom.isEmpty()) {
                    s.label = custom;
                }
            }
        }
        if (ShortcutStore.isHomeAlignBottom(this)) {
            alignShortcutsBottom();
        }
        final List<Shortcut> warmList = new ArrayList<>(shortcuts);
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            for (Shortcut s : warmList) {
                if (s != null && !s.isEmpty()) {
                    IconUtil.iconFor(HomeActivity.this, s);
                }
            }
        });
        adapter.notifyDataSetChanged();
        if (emptyHint != null) {
            boolean allEmpty = true;
            for (Shortcut sc : shortcuts) {
                if (!sc.isEmpty()) { allEmpty = false; break; }
            }
            emptyHint.setVisibility(allEmpty ? View.VISIBLE : View.GONE);
        }
        applyCleanModeState(false);
    }
    private void switchProfile(int profileIndex) {
        ShortcutStore.setActiveProfile(this, profileIndex);
        loadShortcuts();
        loadWidgets();
        applyDynamicTheming();
    }
    private void renameProfile(int profileIndex) {
        EditText input = new EditText(this);
        input.setText(ShortcutStore.profileName(this, profileIndex));
        new AlertDialog.Builder(this)
                .setTitle("Rename profile")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        ShortcutStore.setProfileName(this, profileIndex, name);
                        loadShortcuts();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void applyCleanModeState(boolean animate) {
        if (toggleAppsPill == null || gridView == null) return;
        boolean cleanMode = ShortcutStore.isHomeCleanMode(this);
        if (cleanMode) {
            toggleAppsPill.setVisibility(View.VISIBLE);
            if (appsRevealedInCleanMode) {
                gridView.setVisibility(View.VISIBLE);
                if (toggleAppsPillText != null) toggleAppsPillText.setText("▲ Hide Apps");
                if (animate) {
                    // Spring cascade, 12ms/item, visible children only
                    // (GridView recycles; off-screen slots appear at
                    // rest state). Replaces the whole-grid animation.
                    gridView.setAlpha(1f);
                    gridView.setScaleX(1f);
                    gridView.setScaleY(1f);
                    StaggerHelper.cascadeGridView(gridView, true, null);
                } else {
                    gridView.setAlpha(1f);
                    gridView.setScaleX(1f);
                    gridView.setScaleY(1f);
                }
            } else {
                if (toggleAppsPillText != null) toggleAppsPillText.setText("Apps");
                if (animate) {
                    StaggerHelper.cascadeGridView(gridView, false,
                            () -> gridView.setVisibility(View.GONE));
                } else {
                    gridView.setVisibility(View.GONE);
                }
            }
        } else {
            toggleAppsPill.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
            gridView.setAlpha(1f);
            gridView.setScaleX(1f);
            gridView.setScaleY(1f);
        }
    }
    private void toggleCleanModeApps() {
        appsRevealedInCleanMode = !appsRevealedInCleanMode;
        applyCleanModeState(true);
    }
    private void showCleanModeOptionsDialog() {
        String[] opts = {
                ShortcutStore.isHomeCleanMode(this) ? "Disable Clean Mode (Keep apps always visible)" : "Enable Clean Mode (Tuck apps into pill)",
                "Manage Dynamic Island Apps",
                "Organize Home Grid (16 Slots)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Home Screen Mode")
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        boolean cur = ShortcutStore.isHomeCleanMode(this);
                        ShortcutStore.setHomeCleanMode(this, !cur);
                        appsRevealedInCleanMode = false;
                        applyCleanModeState(true);
                        Toast.makeText(this, !cur ? "Clean Mode active — tap Apps to reveal." : "Clean Mode disabled", Toast.LENGTH_SHORT).show();
                    } else if (w == 1) {
                        showIslandAppManagerDialog();
                    } else if (w == 2) {
                        showGridOrganizerDialog();
                    }
                })
                .show();
    }
    private void showIslandAppManagerDialog() {
        List<Shortcut> island = ShortcutStore.loadIslandPadded(this);
        String[] items = new String[island.size() + 2];
        items[0] = "+ Add App from Drawer to Island";
        items[1] = "Copy All Current Home Apps to Island";
        for (int i = 0; i < island.size(); i++) {
            Shortcut s = island.get(i);
            items[i + 2] = "Island Slot " + (i + 1) + ": " + (s.isEmpty() ? "[ Empty ]" : s.label);
        }
        new AlertDialog.Builder(this)
                .setTitle("Dynamic Island Apps (Independent)")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(this, AppPickerActivity.class);
                        pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
                        startActivity(pick);
                    } else if (which == 1) {
                        List<Shortcut> home = ShortcutStore.load(this);
                        ShortcutStore.saveIsland(this, home);
                        IslandService.refresh(this);
                        Toast.makeText(this, "Copied home apps to Dynamic Island", Toast.LENGTH_SHORT).show();
                    } else {
                        int slotIdx = which - 2;
                        showIslandSlotOptions(slotIdx);
                    }
                })
                .setPositiveButton("Done", null)
                .show();
    }
    private void showIslandSlotOptions(int slotIdx) {
        List<Shortcut> island = ShortcutStore.loadIslandPadded(this);
        Shortcut s = island.get(slotIdx);
        if (s.isEmpty()) {
            Intent pick = new Intent(this, AppPickerActivity.class);
            pick.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, false);
            startActivityForResult(pick, REQUEST_PICK_APP_FOR_ISLAND);
            return;
        }
        String[] opts = {
                "Remove from Island",
                "Custom Display Name"
        };
        new AlertDialog.Builder(this)
                .setTitle("Island Slot " + (slotIdx + 1) + ": " + s.label)
                .setItems(opts, (d, w) -> {
                    if (w == 0) {
                        island.set(slotIdx, Shortcut.createEmpty());
                        ShortcutStore.saveIsland(this, island);
                        IslandService.refresh(this);
                        Toast.makeText(this, "Removed from Island", Toast.LENGTH_SHORT).show();
                    } else if (w == 1) {
                        EditText in = new EditText(this);
                        in.setText(s.label);
                        new AlertDialog.Builder(this)
                                .setTitle("Rename Island Shortcut")
                                .setView(in)
                                .setPositiveButton("Save", (cd, cw) -> {
                                    String n = in.getText().toString().trim();
                                    if (!n.isEmpty()) {
                                        s.label = n;
                                        ShortcutStore.saveIsland(this, island);
                                        IslandService.refresh(this);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }
    private void showShortcutMenu(int position) {
        Shortcut s = shortcuts.get(position);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        // Moving & slot reordering (Always at top for instant access)
        if (position > 0) {
            options.add("⬅️ Move Left (to slot " + position + ")");
            actions.add(() -> {
                Shortcut moved = shortcuts.remove(position);
                shortcuts.add(position - 1, moved);
                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                loadShortcuts();
                BaseDockWidget.refreshAll(this);
                DockService.refresh(this);
                Toast.makeText(this, "Moved to slot " + position, Toast.LENGTH_SHORT).show();
            });
        }
        if (position < shortcuts.size() - 1) {
            options.add("➡️ Move Right (to slot " + (position + 2) + ")");
            actions.add(() -> {
                Shortcut moved = shortcuts.remove(position);
                shortcuts.add(position + 1, moved);
                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                loadShortcuts();
                BaseDockWidget.refreshAll(this);
                DockService.refresh(this);
                Toast.makeText(this, "Moved to slot " + (position + 2), Toast.LENGTH_SHORT).show();
            });
        }
        options.add("🔀 Pick Slot to Move (1 - " + shortcuts.size() + ")");
        actions.add(() -> {
            String[] slotNames = new String[shortcuts.size()];
            for (int i = 0; i < shortcuts.size(); i++) {
                slotNames[i] = "Slot " + (i + 1) + ": " + shortcuts.get(i).label;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Move " + s.label + " to which slot?")
                    .setItems(slotNames, (d, targetIdx) -> {
                        if (targetIdx != position) {
                            Shortcut moved = shortcuts.remove(position);
                            shortcuts.add(targetIdx, moved);
                            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Moved to slot " + (targetIdx + 1), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        });
        options.add("✥ Drag & Drop to any Slot / Folder");
        actions.add(() -> {
            View itemView = gridView.getChildAt(position - gridView.getFirstVisiblePosition());
            if (itemView != null) {
                ClipData data = ClipData.newPlainText("pos", String.valueOf(position));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(itemView);
                itemView.startDragAndDrop(data, shadow, itemView, View.DRAG_FLAG_OPAQUE);
                Toast.makeText(this, "Drag and drop onto any slot or folder", Toast.LENGTH_SHORT).show();
            }
        });
        if (s.isFolder()) {
            options.add("📂 Open folder");
            actions.add(() -> {
                Intent fi = IconUtil.launchIntent(this, s);
                if (fi != null) startActivity(fi); else showFolderModal(s);
            });
            options.add("✏️ Rename folder");
            actions.add(() -> {
                EditText input = new EditText(this);
                input.setText(s.label);
                new AlertDialog.Builder(this)
                        .setTitle("Rename folder")
                        .setView(input)
                        .setPositiveButton("Save", (d, w) -> {
                            String n = input.getText().toString().trim();
                            if (!n.isEmpty()) {
                                s.label = n;
                                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                                loadShortcuts();
                                BaseDockWidget.refreshAll(this);
                                DockService.refresh(this);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        } else {
            options.add("🚀 Launch app");
            actions.add(() -> {
                if (!IconUtil.fireShortcut(this, s)) {
                    Toast.makeText(this, IconUtil.launchFailureReason(this, s), Toast.LENGTH_SHORT).show();
                }
            });
            options.add("✏️ Rename App / Custom Label");
            actions.add(() -> {
                EditText input = new EditText(this);
                input.setText(s.label);
                if (s.label != null) input.setSelection(s.label.length());
                new AlertDialog.Builder(this)
                        .setTitle("Rename App")
                        .setMessage("Enter custom display name:")
                        .setView(input)
                        .setPositiveButton("Save", (d, w) -> {
                            String newName = input.getText().toString().trim();
                            if (!newName.isEmpty()) {
                                s.label = newName;
                                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                                if (s.isApp() && s.pkg != null) {
                                    ShortcutStore.setCustomAppLabel(this, s.pkg, s.profileSerial, newName);
                                }
                                loadShortcuts();
                                BaseDockWidget.refreshAll(this);
                                DockService.refresh(this);
                                Toast.makeText(this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            // Query dynamic app shortcuts published by the target app (API 25+)
            if (s.isApp() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                try {
                    LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                    UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
                    if (la != null && la.hasShortcutHostPermission()) {
                        UserHandle uh = Process.myUserHandle();
                        if (s.profileSerial > 0 && um != null) {
                            UserHandle u = um.getUserForSerialNumber(s.profileSerial);
                            if (u != null) uh = u;
                        }
                        LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
                        query.setPackage(s.pkg);
                        query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST |
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                        List<ShortcutInfo> pinList = la.getShortcuts(query, uh);
                        if (pinList != null && !pinList.isEmpty()) {
                            int count = Math.min(pinList.size(), 4);
                            for (int i = 0; i < count; i++) {
                                ShortcutInfo si = pinList.get(i);
                                CharSequence shortLabel = si.getShortLabel();
                                if (shortLabel == null) shortLabel = si.getLongLabel();
                                if (shortLabel == null) continue;
                                final ShortcutInfo targetSi = si;
                                options.add("⚡ " + shortLabel);
                                actions.add(() -> {
                                    try {
                                        la.startShortcut(targetSi, null, null);
                                    } catch (Exception e) {
                                        Toast.makeText(this, "Could not open shortcut", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            options.add("📌 Pin to Home screen (Main Page)");
            actions.add(() -> {
                boolean pinned = IconUtil.pinShortcutToHomeScreen(this, s);
                Toast.makeText(this, pinned ? "Pinned " + s.label + " to Home screen" : "Pinning not supported", Toast.LENGTH_SHORT).show();
            });
            options.add("🏝️ Add to Dynamic Island");
            actions.add(() -> {
                List<Shortcut> isl = ShortcutStore.loadIsland(this);
                if (isl.size() >= ShortcutStore.MAX) {
                    Toast.makeText(this, "Dynamic Island is full (" + ShortcutStore.MAX + " max)", Toast.LENGTH_SHORT).show();
                } else {
                    isl.add(s);
                    ShortcutStore.saveIsland(this, isl);
                    IslandService.refresh(this);
                    Toast.makeText(this, "Added " + s.label + " to Dynamic Island", Toast.LENGTH_SHORT).show();
                }
            });
            if (s.isApp()) {
                boolean isLocked = ShortcutStore.isAppLocked(this, s.pkg);
                options.add(isLocked ? "🔓 Remove Fingerprint Lock" : "🔒 Lock with Fingerprint");
                actions.add(() -> {
                    ShortcutStore.setAppLocked(this, s.pkg, !isLocked);
                    loadShortcuts();
                    Toast.makeText(this, (!isLocked ? "Locked " : "Unlocked ") + s.label, Toast.LENGTH_SHORT).show();
                });
            }
            if (Shortcut.TYPE_URL.equals(s.type)) {
                options.add("🎨 Change Web App Logo");
                actions.add(() -> showWebLogoPickerDialog(s));
                options.add("✏️ Edit Web App");
                actions.add(() -> showEditWebAppDialog(s));
            }
            options.add("📁 Move to Folder...");
            actions.add(() -> {
                List<Shortcut> folders = new ArrayList<>();
                for (Shortcut sh : shortcuts) {
                    if (sh.isFolder()) folders.add(sh);
                }
                List<String> folderChoices = new ArrayList<>();
                for (Shortcut f : folders) {
                    folderChoices.add("📁 " + f.label);
                }
                folderChoices.add("➕ Create New Folder...");
                new AlertDialog.Builder(this)
                        .setTitle("Add to Folder")
                        .setItems(folderChoices.toArray(new String[0]), (fd, whichF) -> {
                            if (whichF < folders.size()) {
                                Shortcut targetFolder = folders.get(whichF);
                                if (targetFolder.folderItems == null) {
                                    targetFolder.folderItems = new ArrayList<>();
                                }
                                targetFolder.folderItems.add(s);
                                shortcuts.remove(position);
                                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                                loadShortcuts();
                                Toast.makeText(this, "Moved to " + targetFolder.label, Toast.LENGTH_SHORT).show();
                            } else {
                                // Create new folder with this item
                                EditText input = new EditText(this);
                                input.setHint("Folder name");
                                new AlertDialog.Builder(this)
                                        .setTitle("New Folder")
                                        .setView(input)
                                        .setPositiveButton("Create", (cd, cw) -> {
                                            String fn = input.getText().toString().trim();
                                            if (fn.isEmpty()) fn = "Folder";
                                            List<Shortcut> items = new ArrayList<>();
                                            items.add(s);
                                            Shortcut nf = Shortcut.createFolder(fn, items);
                                            shortcuts.remove(position);
                                            shortcuts.add(position, nf);
                                            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                                            loadShortcuts();
                                            Toast.makeText(this, "Created folder " + fn, Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            }
                        })
                        .show();
            });
        }
        int currentProfile = ShortcutStore.activeProfile(this);
        for (int p = 0; p < ShortcutStore.PROFILES; p++) {
            if (p != currentProfile) {
                final int targetP = p;
                options.add("📋 Copy to " + ShortcutStore.profileName(this, p));
                actions.add(() -> {
                    List<Shortcut> targetList = ShortcutStore.load(this, targetP);
                    if (targetList.size() >= ShortcutStore.MAX) {
                        Toast.makeText(this, ShortcutStore.profileName(this, targetP) + " is full", Toast.LENGTH_SHORT).show();
                    } else {
                        // P1-9: copy by value with a fresh UUID + icon copy,
                        // like MainActivity. Adding the same object meant
                        // editing the copy edited the original (same id,
                        // same custom-icon file).
                        String newId = java.util.UUID.randomUUID().toString();
                        Shortcut clone = new Shortcut(newId, s.type, s.pkg, s.url, s.data,
                                s.label, s.iconMode, s.iconColor, s.iconEmoji, s.profileSerial);
                        if (s.folderItems != null && !s.folderItems.isEmpty()) {
                            clone.folderItems = new java.util.ArrayList<>(s.folderItems);
                        }
                        java.io.File srcIcon = IconUtil.customIconFile(this, s.id);
                        if (srcIcon.exists()) {
                            java.io.File destIcon = IconUtil.customIconFile(this, newId);
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(srcIcon);
                                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destIcon)) {
                                byte[] buf = new byte[4096];
                                int n;
                                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
                            } catch (Exception ignored) {
                            }
                        }
                        targetList.add(clone);
                        ShortcutStore.save(this, targetP, targetList);
                        Toast.makeText(this, "Copied to " + ShortcutStore.profileName(this, targetP), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        options.add("🗑️ Remove from home");
        actions.add(() -> {
            lastRemovedShortcut = s;
            lastRemovedIndex = position;
            shortcuts.set(position, Shortcut.createEmpty());
            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
            loadShortcuts();
            BaseDockWidget.refreshAll(this);
            DockService.refresh(this);
            Toast.makeText(this, "Removed " + s.label + " (Tap wallpaper -> Undo)", Toast.LENGTH_LONG).show();
        });
        new AlertDialog.Builder(this)
                .setTitle(s.label)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }
    private void showAddWebAppDialog(String defaultUrl, String defaultTitle) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_url, null);
        EditText editName = dialogView.findViewById(R.id.url_name);
        EditText editUrl = dialogView.findViewById(R.id.url_value);
        CheckBox chkPin = dialogView.findViewById(R.id.chk_pin_home);
        if (defaultUrl != null && !defaultUrl.isEmpty()) editUrl.setText(defaultUrl);
        if (defaultTitle != null && !defaultTitle.isEmpty()) editName.setText(defaultTitle);
        new AlertDialog.Builder(this)
                .setTitle("Add Web App")
                .setView(dialogView)
                .setPositiveButton("Add", (d, which) -> {
                    String u = editUrl.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "https://" + u;
                    }
                    String n = editName.getText().toString().trim();
                    if (n.isEmpty()) {
                        try {
                            n = Uri.parse(u).getHost();
                            if (n != null && n.startsWith("www.")) n = n.substring(4);
                        } catch (Exception e) {
                            n = "Web App";
                        }
                    }
                    if (n == null || n.isEmpty()) n = "Web App";
                    int p = ShortcutStore.activeProfile(this);
                    Shortcut sc = new Shortcut(Shortcut.TYPE_URL, "", u, n);
                    if (pendingSlotIndex >= 0 && pendingSlotIndex < shortcuts.size()) {
                        shortcuts.set(pendingSlotIndex, sc);
                        pendingSlotIndex = -1;
                    } else {
                        int emptyIdx = -1;
                        for (int i = 0; i < shortcuts.size(); i++) {
                            if (shortcuts.get(i).isEmpty()) { emptyIdx = i; break; }
                        }
                        if (emptyIdx >= 0) {
                            shortcuts.set(emptyIdx, sc);
                        } else if (shortcuts.size() < ShortcutStore.MAX) {
                            shortcuts.add(sc);
                        } else {
                            Toast.makeText(this, "Home screen is full (" + ShortcutStore.MAX + " max)", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    Bitmap brand = WebLogoHelper.getBuiltinBrandLogo(this, u);
                    if (brand != null) {
                        IconUtil.saveCustomIcon(this, sc.id, brand);
                    } else {
                        WebLogoHelper.fetchFaviconAsync(this, u, sc.id, (logo) -> loadShortcuts());
                    }
                    ShortcutStore.save(this, p, shortcuts);
                    loadShortcuts();
                    BaseDockWidget.refreshAll(this);
                    DockService.refresh(this);
                    if (chkPin != null && chkPin.isChecked()) {
                        IconUtil.pinShortcutToHomeScreen(this, sc);
                    }
                    Toast.makeText(this, n + " added as Web App", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void showAddShortcutDialogForSlot(int slotIndex) {
        pendingSlotIndex = slotIndex;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add("📱 Add App to Slot " + (slotIndex + 1));
        actions.add(() -> {
            Intent pick = new Intent(this, AppPickerActivity.class);
            pick.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, false);
            startActivityForResult(pick, REQUEST_PICK_APP_FOR_SLOT);
        });
        options.add("🌐 Add Web App to Slot " + (slotIndex + 1));
        actions.add(() -> showAddWebAppDialog("", ""));
        options.add("📁 Create Folder in Slot " + (slotIndex + 1));
        actions.add(() -> {
            EditText input = new EditText(this);
            input.setHint("Folder name (e.g. Social, Games)");
            new AlertDialog.Builder(this)
                    .setTitle("New Folder in Slot " + (slotIndex + 1))
                    .setView(input)
                    .setPositiveButton("Create", (d, w) -> {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) name = "Folder";
                        Shortcut folder = Shortcut.createFolder(name, new ArrayList<>());
                        shortcuts.set(slotIndex, folder);
                        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                        loadShortcuts();
                        Toast.makeText(this, "Created folder: " + name, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        // 📲 Move an Existing App Here
        List<Integer> existingIndices = new ArrayList<>();
        for (int i = 0; i < shortcuts.size(); i++) {
            if (!shortcuts.get(i).isEmpty()) existingIndices.add(i);
        }
        if (!existingIndices.isEmpty()) {
            options.add("📲 Move an Existing App Here (to Slot " + (slotIndex + 1) + ")");
            actions.add(() -> {
                String[] appNames = new String[existingIndices.size()];
                for (int j = 0; j < existingIndices.size(); j++) {
                    int idx = existingIndices.get(j);
                    Shortcut sc = shortcuts.get(idx);
                    appNames[j] = "Slot " + (idx + 1) + ": " + (sc.isFolder() ? "📁 " : "") + sc.label;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Select App to Move to Slot " + (slotIndex + 1))
                        .setItems(appNames, (d, sel) -> {
                            int fromIdx = existingIndices.get(sel);
                            Shortcut moved = shortcuts.get(fromIdx);
                            shortcuts.set(slotIndex, moved);
                            shortcuts.set(fromIdx, Shortcut.createEmpty());
                            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Moved " + moved.label + " to slot " + (slotIndex + 1), Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }
        options.add("🧩 Add Android Widget to Home Screen");
        actions.add(this::showWidgetMenu);
        options.add("🗂️ Open Visual Grid Organizer");
        actions.add(this::showGridOrganizerDialog);
        options.add("🧹 Pack All Apps to Top (Fill Gaps)");
        actions.add(this::packShortcutsToTop);
        options.add("⬇️ Push All Apps to Bottom (Thumb Reach)");
        actions.add(this::pushShortcutsToBottom);
        new AlertDialog.Builder(this)
                .setTitle("Slot " + (slotIndex + 1) + " (Free Space)")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }
    private void showColumnsDialog() {
        String[] options = {
                "3 Columns (Spacious, Large Icons)",
                "4 Columns (Standard Android / OnePlus)",
                "5 Columns (Dense, More Apps)",
                "Toggle Thumb-Reach (Align Apps to Bottom)",
                ShortcutStore.isShowEmptyGridSlots(this) ? "Hide Empty Grid Outlines (Clean Wallpaper)" : "Show Empty Grid Outlines (Edit Mode)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Grid Layout & Density")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        ShortcutStore.setHomeGridColumns(this, 3);
                        if (gridView != null) gridView.setNumColumns(3);
                        loadShortcuts();
                        Toast.makeText(this, "Grid set to 3 Columns", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        ShortcutStore.setHomeGridColumns(this, 4);
                        if (gridView != null) gridView.setNumColumns(4);
                        loadShortcuts();
                        Toast.makeText(this, "Grid set to 4 Columns", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        ShortcutStore.setHomeGridColumns(this, 5);
                        if (gridView != null) gridView.setNumColumns(5);
                        loadShortcuts();
                        Toast.makeText(this, "Grid set to 5 Columns", Toast.LENGTH_SHORT).show();
                    } else if (which == 3) {
                        boolean bottom = !ShortcutStore.isHomeAlignBottom(this);
                        ShortcutStore.setHomeAlignBottom(this, bottom);
                        loadShortcuts();
                        Toast.makeText(this, bottom ? "Thumb-Reach: Apps aligned to bottom" : "Standard: Apps aligned to top", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        boolean next = !ShortcutStore.isShowEmptyGridSlots(this);
                        ShortcutStore.setShowEmptyGridSlots(this, next);
                        loadShortcuts();
                        Toast.makeText(this, next ? "Showing empty grid slots" : "Empty grid slots hidden", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
    private void showGridOrganizerDialog() {
        int currentProfile = ShortcutStore.activeProfile(this);
        String profName = ShortcutStore.profileName(this, currentProfile);
        String[] items = new String[shortcuts.size() + 6];
        items[0] = "🔤 Sort Non-Empty Apps A–Z";
        items[1] = "🧹 Compact All Apps to Top (Fill Gaps)";
        items[2] = "⬇️ Push All Apps to Bottom (One-Hand Reach)";
        items[3] = "➕ Insert Blank Space at Beginning";
        items[4] = ShortcutStore.isShowEmptyGridSlots(this) ? "🔲 Hide Empty Slot Outlines (Clean Wallpaper)" : "🔲 Show Empty Slot Outlines (Visual Placement)";
        items[5] = "🗑️ Clear All App Tiles (Widgets-Only / Free Canvas)";
        for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut sc = shortcuts.get(i);
            items[i + 6] = "Slot " + (i + 1) + ": " + (sc.isEmpty() ? "[ ✨ Free Slot — Tap to Add ]" : (sc.isFolder() ? "📁 " : "") + sc.label);
        }
        new AlertDialog.Builder(this)
                .setTitle("🗂️ Organize Grid: " + profName)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        sortShortcutsAZ();
                    } else if (which == 1) {
                        packShortcutsToTop();
                    } else if (which == 2) {
                        pushShortcutsToBottom();
                    } else if (which == 3) {
                        insertBlankSpaceAt(0);
                    } else if (which == 4) {
                        boolean next = !ShortcutStore.isShowEmptyGridSlots(this);
                        ShortcutStore.setShowEmptyGridSlots(this, next);
                        loadShortcuts();
                        Toast.makeText(this, next ? "Empty slot targets visible" : "Empty slot outlines hidden", Toast.LENGTH_SHORT).show();
                    } else if (which == 5) {
                        new AlertDialog.Builder(this)
                                .setTitle("Clear All App Tiles?")
                                .setMessage("Removes all app tiles from this profile so you have a pure widget/wallpaper canvas. You can still tap anywhere to add apps later.")
                                .setPositiveButton("Clear All Tiles", (cd, cw) -> {
                                    shortcuts.clear();
                                    while (shortcuts.size() < ShortcutStore.MAX) {
                                        shortcuts.add(Shortcut.createEmpty());
                                    }
                                    ShortcutStore.save(this, currentProfile, shortcuts);
                                    loadShortcuts();
                                    BaseDockWidget.refreshAll(this);
                                    DockService.refresh(this);
                                    Toast.makeText(this, "Cleared all app tiles", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        int slotIdx = which - 6;
                        showSlotControlDialog(slotIdx);
                    }
                })
                .setPositiveButton("Done", null)
                .show();
    }
    private void showSlotControlDialog(int slotIdx) {
        Shortcut s = shortcuts.get(slotIdx);
        if (s.isEmpty()) {
            showAddShortcutDialogForSlot(slotIdx);
            return;
        }
        String[] slotOpts = {
                "🔀 Move to another Slot...",
                "🔄 Swap with another Slot...",
                "➕ Insert Blank Space Before this",
                "🗑️ Clear Slot (Leave Empty)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Slot " + (slotIdx + 1) + ": " + s.label)
                .setItems(slotOpts, (d, opt) -> {
                    if (opt == 0) {
                        pickSlotTarget(slotIdx, false);
                    } else if (opt == 1) {
                        pickSlotTarget(slotIdx, true);
                    } else if (opt == 2) {
                        insertBlankSpaceAt(slotIdx);
                    } else if (opt == 3) {
                        lastRemovedShortcut = s;
                        lastRemovedIndex = slotIdx;
                        shortcuts.set(slotIdx, Shortcut.createEmpty());
                        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                        loadShortcuts();
                        BaseDockWidget.refreshAll(this);
                        DockService.refresh(this);
                        Toast.makeText(this, "Cleared slot " + (slotIdx + 1) + " (Tap wallpaper -> Undo)", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
    private void pickSlotTarget(int fromSlot, boolean swap) {
        String[] targets = new String[shortcuts.size()];
        for (int i = 0; i < shortcuts.size(); i++) {
            Shortcut sc = shortcuts.get(i);
            targets[i] = "Slot " + (i + 1) + ": " + (sc.isEmpty() ? "[ ✨ Empty ]" : sc.label);
        }
        new AlertDialog.Builder(this)
                .setTitle((swap ? "Swap" : "Move") + " " + shortcuts.get(fromSlot).label + " with:")
                .setItems(targets, (d, toSlot) -> {
                    if (fromSlot != toSlot) {
                        Shortcut src = shortcuts.get(fromSlot);
                        Shortcut dst = shortcuts.get(toSlot);
                        if (swap || !dst.isEmpty()) {
                            shortcuts.set(toSlot, src);
                            shortcuts.set(fromSlot, dst);
                            Toast.makeText(this, "Swapped slot " + (fromSlot + 1) + " and " + (toSlot + 1), Toast.LENGTH_SHORT).show();
                        } else {
                            shortcuts.set(toSlot, src);
                            shortcuts.set(fromSlot, Shortcut.createEmpty());
                            Toast.makeText(this, "Moved to slot " + (toSlot + 1), Toast.LENGTH_SHORT).show();
                        }
                        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                        loadShortcuts();
                        BaseDockWidget.refreshAll(this);
                        DockService.refresh(this);
                    }
                })
                .show();
    }
    private void sortShortcutsAZ() {
        List<Shortcut> nonEmpties = new ArrayList<>();
        for (Shortcut s : shortcuts) {
            if (!s.isEmpty()) nonEmpties.add(s);
        }
        nonEmpties.sort((a, b) -> {
            String la = a.label != null ? a.label : "";
            String lb = b.label != null ? b.label : "";
            return la.compareToIgnoreCase(lb);
        });
        shortcuts.clear();
        shortcuts.addAll(nonEmpties);
        while (shortcuts.size() < ShortcutStore.MAX) {
            shortcuts.add(Shortcut.createEmpty());
        }
        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
        loadShortcuts();
        BaseDockWidget.refreshAll(this);
        DockService.refresh(this);
        Toast.makeText(this, "Sorted A–Z", Toast.LENGTH_SHORT).show();
    }
    private void insertBlankSpaceAt(int index) {
        if (index < 0 || index >= shortcuts.size()) return;
        shortcuts.add(index, Shortcut.createEmpty());
        while (shortcuts.size() > ShortcutStore.MAX) {
            shortcuts.remove(shortcuts.size() - 1);
        }
        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
        loadShortcuts();
        BaseDockWidget.refreshAll(this);
        DockService.refresh(this);
        Toast.makeText(this, "Inserted space at slot " + (index + 1), Toast.LENGTH_SHORT).show();
    }
    private void packShortcutsToTop() {
        List<Shortcut> nonEmpties = new ArrayList<>();
        for (Shortcut s : shortcuts) {
            if (!s.isEmpty()) {
                nonEmpties.add(s);
            }
        }
        shortcuts.clear();
        shortcuts.addAll(nonEmpties);
        while (shortcuts.size() < ShortcutStore.MAX) {
            shortcuts.add(Shortcut.createEmpty());
        }
        ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
        loadShortcuts();
        BaseDockWidget.refreshAll(this);
        DockService.refresh(this);
        Toast.makeText(this, "Packed icons to top", Toast.LENGTH_SHORT).show();
    }
    private void showWebLogoPickerDialog(Shortcut s) {
        pendingLogoShortcut = s;
        String[] opts = {
                "✨ Auto-Fetch High-Res Favicon from Web",
                "🎨 Choose from Popular Brand Logos (YouTube, X, Reddit, etc.)",
                "🖼️ Choose Image from Device Gallery / Photos",
                "📦 Choose Icon from Installed Icon Pack",
                "🔤 Reset to Default Letter / Brand Logo"
        };
        new AlertDialog.Builder(this)
                .setTitle("Web App Logo: " + s.label)
                .setItems(opts, (dialog, which) -> {
                    if (which == 0) {
                        Toast.makeText(this, "Fetching high-res favicon...", Toast.LENGTH_SHORT).show();
                        WebLogoHelper.fetchFaviconExplicit(this, s.url, s.id, (bmp) -> {
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Favicon loaded for " + s.label, Toast.LENGTH_SHORT).show();
                        });
                    } else if (which == 1) {
                        showPresetBrandLogoDialog(s);
                    } else if (which == 2) {
                        try {
                            Intent pick = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(pick, REQ_PICK_CUSTOM_LOGO);
                        } catch (Exception e) {
                            Toast.makeText(this, "Cannot open gallery", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 3) {
                        showIconPackLogoPicker(s);
                    } else if (which == 4) {
                        IconUtil.deleteCustomIcon(this, s.id);
                        IconUtil.clearLruCache();
                        loadShortcuts();
                        BaseDockWidget.refreshAll(this);
                        DockService.refresh(this);
                        Toast.makeText(this, "Reset to default logo", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
    private void showPresetBrandLogoDialog(Shortcut s) {
        List<WebLogoHelper.PresetLogo> presets = WebLogoHelper.getPresetList();
        String[] names = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            names[i] = presets.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Brand Logo")
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < presets.size()) {
                        WebLogoHelper.PresetLogo preset = presets.get(which);
                        Bitmap bmp = WebLogoHelper.getBuiltinBrandLogo(this, preset.defaultUrl);
                        if (bmp != null) {
                            IconUtil.saveCustomIcon(this, s.id, bmp);
                            IconUtil.clearLruCache();
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Logo set to " + preset.name, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }
    private void showIconPackLogoPicker(Shortcut s) {
        String activePack = IconPackManager.getActiveIconPack(this);
        if (activePack == null || activePack.isEmpty()) {
            Toast.makeText(this, "No icon pack active. Select an icon pack in Settings first.", Toast.LENGTH_LONG).show();
            return;
        }
        // Let the user pick from existing apps to copy its themed icon pack design
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        List<String> appLabels = new ArrayList<>();
        List<String> appPkgs = new ArrayList<>();
        List<String> appCls = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            Bitmap icon = IconPackManager.getIcon(this, pkg, cls);
            if (icon != null) {
                appLabels.add(ri.loadLabel(pm).toString());
                appPkgs.add(pkg);
                appCls.add(cls);
            }
        }
        if (appLabels.isEmpty()) {
            Toast.makeText(this, "No icons matched in current pack", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Match Icon from " + activePack)
                .setItems(appLabels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < appPkgs.size()) {
                        Bitmap bmp = IconPackManager.getIcon(this, appPkgs.get(which), appCls.get(which));
                        if (bmp != null) {
                            IconUtil.saveCustomIcon(this, s.id, bmp);
                            IconUtil.clearLruCache();
                            loadShortcuts();
                            BaseDockWidget.refreshAll(this);
                            DockService.refresh(this);
                            Toast.makeText(this, "Icon updated from " + appLabels.get(which), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }
    private void showEditWebAppDialog(Shortcut s) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_url, null);
        EditText editName = dialogView.findViewById(R.id.url_name);
        EditText editUrl = dialogView.findViewById(R.id.url_value);
        CheckBox chkPin = dialogView.findViewById(R.id.chk_pin_home);
        if (s.url != null) editUrl.setText(s.url);
        if (s.label != null) editName.setText(s.label);
        new AlertDialog.Builder(this)
                .setTitle("Edit Web App")
                .setView(dialogView)
                .setPositiveButton("Save", (d, which) -> {
                    String u = editUrl.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.startsWith("http://") && !u.startsWith("https://")) {
                        u = "https://" + u;
                    }
                    String n = editName.getText().toString().trim();
                    if (n.isEmpty()) n = "Web App";
                    boolean urlChanged = !u.equalsIgnoreCase(s.url);
                    s.url = u;
                    s.label = n;
                    if (urlChanged) {
                        Bitmap brand = WebLogoHelper.getBuiltinBrandLogo(this, u);
                        if (brand != null) {
                            IconUtil.saveCustomIcon(this, s.id, brand);
                        } else {
                            WebLogoHelper.fetchFaviconAsync(this, u, s.id, (logo) -> loadShortcuts());
                        }
                    }
                    int p = ShortcutStore.activeProfile(this);
                    ShortcutStore.save(this, p, shortcuts);
                    loadShortcuts();
                    if (chkPin != null && chkPin.isChecked()) {
                        IconUtil.pinShortcutToHomeScreen(this, s);
                    }
                    Toast.makeText(this, s.label + " updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    class HomeShortcutAdapter extends BaseAdapter {
        @Override public int getCount() { return shortcuts.size(); }
        @Override public Shortcut getItem(int p) { return shortcuts.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(HomeActivity.this).inflate(R.layout.item_home_shortcut, parent, false);
            }
            Shortcut s = getItem(pos);
            ImageView icon = v.findViewById(R.id.sc_icon);
            TextView label = v.findViewById(R.id.sc_label);
            TextView badge = v.findViewById(R.id.sc_badge);
            int iconDp = ShortcutStore.homeIconSize(HomeActivity.this);
            int iconPx = dp(iconDp);
            if (icon.getLayoutParams() != null && icon.getLayoutParams().width != iconPx) {
                icon.getLayoutParams().width = iconPx;
                icon.getLayoutParams().height = iconPx;
                View parentFrame = (View) icon.getParent();
                if (parentFrame != null && parentFrame.getLayoutParams() != null) {
                    parentFrame.getLayoutParams().width = iconPx;
                    parentFrame.getLayoutParams().height = iconPx;
                }
            }
            if (s.isEmpty()) {
                icon.setImageResource(R.drawable.slot_empty_target);
                boolean showOutlines = ShortcutStore.isShowEmptyGridSlots(HomeActivity.this) || activeDraggedPosition >= 0;
                icon.setAlpha(showOutlines ? 0.38f : 0.0f);
                label.setText("");
                label.setVisibility(View.GONE);
                if (badge != null) badge.setVisibility(View.GONE);
            } else {
                icon.setAlpha(1.0f);
                icon.setImageBitmap(IconUtil.iconFor(HomeActivity.this, s));
                boolean isLocked = s.isApp() && ShortcutStore.isAppLocked(HomeActivity.this, s.pkg);
                label.setText((isLocked ? "🔒 " : "") + s.label);
                label.setVisibility(ShortcutStore.isHomeHideLabels(HomeActivity.this) ? View.GONE : View.VISIBLE);
                if (badge != null) {
                    if (s.isApp()) {
                        LockNotificationListenerService.bindBadgeView(HomeActivity.this, badge, s.pkg);
                    } else {
                        badge.setVisibility(View.GONE);
                    }
                }
            }
            final int itemPos = pos;
            v.setTag(itemPos);
            // Pressable WITHOUT consuming the touch: PressableHelper
            // .attach() returns true on DOWN, which would kill this
            // view's OnLongClickListener (drag/menu) below. Use
            // pressDown/release with a non-consuming listener instead,
            // so click, long-press drag, and menu all still fire.
            // (Attach-as-documented is therefore NOT used here.)
            v.setOnTouchListener((touchView, event) -> {
                if (s.isEmpty() && !ShortcutStore.isShowEmptyGridSlots(HomeActivity.this)) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        PressableHelper.pressDown(touchView);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        PressableHelper.release(touchView);
                        HapticHelper.tick(touchView);
                        break;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        PressableHelper.release(touchView);
                        break;
                }
                return false;
            });
            v.setOnClickListener(childView -> {
                launchOrHandleShortcut(itemPos, childView);
            });
            // Fluid native drag initiation directly on long-press
            v.setOnLongClickListener(childView -> {
                if (s.isEmpty()) {
                    childView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showAddShortcutDialogForSlot(itemPos);
                    return true;
                }
                childView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                activeDraggedPosition = itemPos;
                dragWasDropped = false;
                if (dragActionBar != null) dragActionBar.setVisibility(View.VISIBLE);
                ClipData data = ClipData.newPlainText("pos", String.valueOf(itemPos));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(childView);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    childView.startDragAndDrop(data, shadow, childView, View.DRAG_FLAG_OPAQUE);
                } else {
                    childView.startDrag(data, shadow, childView, 0);
                }
                childView.animate().scaleX(0.85f).scaleY(0.85f).alpha(0.45f).setDuration(120).start();
                return true;
            });
            v.setOnDragListener((targetView, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        if (dragActionBar != null) dragActionBar.setVisibility(View.VISIBLE);
                        if (s.isEmpty()) {
                            icon.setImageResource(R.drawable.slot_empty_target);
                            icon.animate().alpha(0.55f).setDuration(150).start();
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        targetView.animate().scaleX(1.16f).scaleY(1.16f).setDuration(110).start();
                        if (s.isEmpty()) {
                            icon.setImageResource(R.drawable.slot_empty_target_hover);
                            icon.animate().alpha(1.0f).setDuration(100).start();
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        targetView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(110).start();
                        if (s.isEmpty()) {
                            icon.setImageResource(R.drawable.slot_empty_target);
                            icon.animate().alpha(0.45f).setDuration(100).start();
                        }
                        return true;
                    case DragEvent.ACTION_DROP:
                        targetView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start();
                        SpringAnimator.scalePulse(targetView);
                        int sourcePos = -1;
                        try {
                            ClipData cd = event.getClipData();
                            if (cd != null && cd.getItemCount() > 0) {
                                sourcePos = Integer.parseInt(cd.getItemAt(0).getText().toString());
                            }
                        } catch (Exception ignored) {}
                        if (sourcePos >= 0 && sourcePos < shortcuts.size() && itemPos >= 0 && itemPos < shortcuts.size()) {
                            dragWasDropped = true;
                            activeDraggedPosition = -1;
                            if (sourcePos != itemPos) {
                                targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                Shortcut source = shortcuts.get(sourcePos);
                                Shortcut target = shortcuts.get(itemPos);
                                if (target.isFolder() && !source.isFolder()) {
                                    if (target.folderItems == null) target.folderItems = new ArrayList<>();
                                    target.folderItems.add(source);
                                    shortcuts.set(sourcePos, Shortcut.createEmpty());
                                    Toast.makeText(HomeActivity.this, "Added " + source.label + " to " + target.label, Toast.LENGTH_SHORT).show();
                                } else if (target.isEmpty()) {
                                    shortcuts.set(itemPos, source);
                                    shortcuts.set(sourcePos, Shortcut.createEmpty());
                                    Toast.makeText(HomeActivity.this, "Moved " + source.label + " to slot " + (itemPos + 1), Toast.LENGTH_SHORT).show();
                                } else {
                                    shortcuts.set(itemPos, source);
                                    shortcuts.set(sourcePos, target);
                                    Toast.makeText(HomeActivity.this, "Swapped " + source.label + " with " + target.label, Toast.LENGTH_SHORT).show();
                                }
                                ShortcutStore.save(HomeActivity.this, ShortcutStore.activeProfile(HomeActivity.this), shortcuts);
                                loadShortcuts();
                                BaseDockWidget.refreshAll(HomeActivity.this);
                                DockService.refresh(HomeActivity.this);
                            } else {
                                // Dropped back on itself -> open menu
                                showShortcutMenu(sourcePos);
                            }
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        targetView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start();
                        if (dragActionBar != null) dragActionBar.setVisibility(View.GONE);
                        if (s.isEmpty()) {
                            icon.setImageResource(R.drawable.slot_empty_target);
                            float restAlpha = ShortcutStore.isShowEmptyGridSlots(HomeActivity.this) ? 0.38f : 0.0f;
                            icon.animate().alpha(restAlpha).setDuration(150).start();
                        }
                        if (!dragWasDropped && activeDraggedPosition >= 0) {
                            final int openedPos = activeDraggedPosition;
                            activeDraggedPosition = -1;
                            targetView.post(() -> showShortcutMenu(openedPos));
                        } else {
                            activeDraggedPosition = -1;
                        }
                        return true;
                }
                return true;
            });
            return v;
        }
    }
}
