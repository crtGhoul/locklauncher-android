package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
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
        });

        findViewById(R.id.btn_quick_add_web).setOnClickListener(v -> {
            showAddWebAppDialog("", "");
        });

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
                            // Swipe up -> Open searchable app drawer
                            Intent drawer = new Intent(HomeActivity.this, AppPickerActivity.class);
                            drawer.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
                            startActivity(drawer);
                            return true;
                        } else {
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
            if (position >= 0 && position < shortcuts.size()) {
                Shortcut s = shortcuts.get(position);
                if (s.isFolder()) {
                    showFolderModal(s);
                    return;
                }
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate()
                        .scaleX(0.90f).scaleY(0.90f)
                        .setDuration(60)
                        .withEndAction(() -> {
                            view.animate()
                                    .scaleX(1.0f).scaleY(1.0f)
                                    .setDuration(130)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                                    .start();
                        }).start();

                boolean ok = IconUtil.fireShortcut(this, s, view);
                if (!ok) {
                    Toast.makeText(this, "Could not open " + s.label, Toast.LENGTH_SHORT).show();
                }
            }
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shortcuts.size()) {
                showShortcutMenu(position);
                return true;
            }
            return false;
        });

        loadShortcuts();
        loadWidgets();
        applyDynamicTheming();
    }

    private void applyDynamicTheming() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                DynamicThemeHelper.ThemeColors theme = DynamicThemeHelper.extractTheme(this);
                View searchBar = findViewById(R.id.home_search_bar);
                if (searchBar != null && searchBar.getBackground() != null) {
                    searchBar.getBackground().setTint(theme.subtleGlassTint);
                }
                Button btnAddWeb = findViewById(R.id.btn_quick_add_web);
                if (btnAddWeb != null && btnAddWeb.getBackground() != null) {
                    btnAddWeb.getBackground().setTint(theme.primaryAccent);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        timeHandler.post(timeRunnable);
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
    }

    private void showWallpaperMenu() {
        List<String> opts = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();

        if (lastRemovedShortcut != null) {
            final Shortcut toRestore = lastRemovedShortcut;
            final int restorePos = lastRemovedIndex;
            opts.add("↩️ Undo: Restore " + toRestore.label);
            acts.add(() -> {
                int p = ShortcutStore.activeProfile(this);
                int pos = Math.min(Math.max(0, restorePos), shortcuts.size());
                shortcuts.add(pos, toRestore);
                ShortcutStore.save(this, p, shortcuts);
                loadShortcuts();
                Toast.makeText(this, "Restored " + toRestore.label, Toast.LENGTH_SHORT).show();
                lastRemovedShortcut = null;
                lastRemovedIndex = -1;
            });
        }

        opts.add("🧩 Add Android Widget");
        acts.add(this::selectWidget);

        opts.add("📁 Create New Folder");
        acts.add(this::createNewFolderDialog);

        opts.add("🖼️ Change Wallpaper");
        acts.add(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
            } catch (Exception e) {
                Toast.makeText(this, "Wallpaper picker not found", Toast.LENGTH_SHORT).show();
            }
        });

        opts.add("🌐 Add Web App");
        acts.add(() -> showAddWebAppDialog("", ""));

        opts.add("➕ Add App / Shortcut");
        acts.add(() -> {
            Intent addAppIntent = new Intent(this, AppPickerActivity.class);
            addAppIntent.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            startActivity(addAppIntent);
        });

        opts.add("📱 Search & All Apps");
        acts.add(() -> {
            Intent drawer = new Intent(this, AppPickerActivity.class);
            drawer.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
            startActivity(drawer);
        });

        opts.add("⚙️ Launcher Settings");
        acts.add(() -> startActivity(new Intent(this, MainActivity.class)));

        new AlertDialog.Builder(this)
                .setTitle("Home Screen")
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < acts.size()) {
                        acts.get(which).run();
                    }
                })
                .show();
    }

    // ---- Widget Management ----

    private void selectWidget() {
        if (appWidgetHost == null) return;
        String[] choices = {
                "✨ Add Lock Launcher App Dock Widget (4x1)",
                "✨ Add Lock Launcher Full Dock Widget (16-Slot)",
                "🧩 Browse All Installed Widgets..."
        };
        new AlertDialog.Builder(this)
                .setTitle("Add Widget")
                .setItems(choices, (d, which) -> {
                    if (which == 0) {
                        bindInternalWidget(DockWidget4x1.class);
                    } else if (which == 1) {
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
        if (resultCode == RESULT_OK) {
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

        for (int wId : widgetIds) {
            try {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(wId);
                if (info != null) {
                    AppWidgetHostView hostView = appWidgetHost.createView(this, wId, info);
                    hostView.setAppWidget(wId, info);
                    final int currentWidgetId = wId;
                    hostView.setOnLongClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Widget")
                                .setMessage("Remove this widget from the home screen?")
                                .setPositiveButton("Remove", (d, w) -> {
                                    ShortcutStore.removeHomeWidget(this, profile, currentWidgetId);
                                    try {
                                        appWidgetHost.deleteAppWidgetId(currentWidgetId);
                                    } catch (Exception ignored) {
                                    }
                                    loadWidgets();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        return true;
                    });

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = (int) (getResources().getDisplayMetrics().density * 10);
                    widgetsContainer.addView(hostView, lp);
                }
            } catch (Exception ignored) {
            }
        }
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
                    if (shortcuts.size() >= ShortcutStore.MAX) {
                        Toast.makeText(this, "Home screen is full (16 max)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Shortcut folder = Shortcut.createFolder(name, new ArrayList<>());
                    shortcuts.add(folder);
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
                icon.setImageBitmap(IconUtil.iconFor(HomeActivity.this, child));
                label.setText(child.label);
                label.setVisibility(ShortcutStore.isHomeHideLabels(HomeActivity.this) ? View.GONE : View.VISIBLE);
                return v;
            }
        };
        grid.setAdapter(folderAdapter);

        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Shortcut child = items.get(pos);
            dialog.dismiss();
            IconUtil.fireShortcut(HomeActivity.this, child);
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
            profileTabs[i].setBackgroundResource(i == currentProfile ? R.drawable.btn_accent : R.drawable.btn_ghost);
        }
        shortcuts = ShortcutStore.load(this, currentProfile);
        adapter.notifyDataSetChanged();
        if (emptyHint != null) {
            emptyHint.setVisibility(shortcuts.isEmpty() ? View.VISIBLE : View.GONE);
        }
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

    private void showShortcutMenu(int position) {
        Shortcut s = shortcuts.get(position);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (s.isFolder()) {
            options.add("📂 Open folder");
            actions.add(() -> showFolderModal(s));

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
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        } else {
            options.add("🚀 Launch app");
            actions.add(() -> IconUtil.fireShortcut(this, s));

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
                                loadShortcuts();
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

            options.add("🔀 Reorder / Move Slot");
            actions.add(() -> {
                String[] slotNames = new String[shortcuts.size()];
                for (int i = 0; i < shortcuts.size(); i++) {
                    slotNames[i] = "Slot " + (i + 1) + ": " + shortcuts.get(i).label;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Swap with which slot?")
                        .setItems(slotNames, (d, targetIdx) -> {
                            if (targetIdx != position) {
                                Shortcut moved = shortcuts.remove(position);
                                shortcuts.add(targetIdx, moved);
                                ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
                                loadShortcuts();
                                Toast.makeText(this, "Moved to slot " + (targetIdx + 1), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            });

            options.add("📌 Pin to Home screen (Main Page)");
            actions.add(() -> {
                boolean pinned = IconUtil.pinShortcutToHomeScreen(this, s);
                Toast.makeText(this, pinned ? "Pinned " + s.label + " to Home screen" : "Pinning not supported", Toast.LENGTH_SHORT).show();
            });

            if (Shortcut.TYPE_URL.equals(s.type)) {
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
                        targetList.add(s);
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
            shortcuts.remove(position);
            ShortcutStore.save(this, ShortcutStore.activeProfile(this), shortcuts);
            loadShortcuts();
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
                    if (shortcuts.size() >= ShortcutStore.MAX) {
                        Toast.makeText(this, "Home screen is full (16 max)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Shortcut sc = new Shortcut(Shortcut.TYPE_URL, "", u, n);
                    shortcuts.add(sc);
                    ShortcutStore.save(this, p, shortcuts);
                    loadShortcuts();
                    if (chkPin != null && chkPin.isChecked()) {
                        IconUtil.pinShortcutToHomeScreen(this, sc);
                    }
                    Toast.makeText(this, n + " added as Web App", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
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
                    s.url = u;
                    s.label = n;
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

            icon.setImageBitmap(IconUtil.iconFor(HomeActivity.this, s));
            label.setText(s.label);
            label.setVisibility(ShortcutStore.isHomeHideLabels(HomeActivity.this) ? View.GONE : View.VISIBLE);
            return v;
        }
    }
}
