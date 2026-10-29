package com.crtghoul.locklauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Config screen: 3 dock profiles x 16 shortcuts (apps, web, call, SMS,
 * flashlight, settings panels, app deep-shortcuts, profile switcher),
 * per-shortcut icons (app/letter/emoji/gallery), glass opacity, memo,
 * one-tap QS tile + widget pinning, and setup steps.
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK_APP = 1;
    private static final int REQ_NOTIF = 2;
    private static final int REQ_PICK_CONTACT = 3;
    private static final int REQ_PERM = 4;
    private static final int REQ_DEEP_CREATE = 7;
    private static final int REQ_ICON_IMAGE = 8;
    private static final int REQ_EXPORT = 9;
    private static final int REQ_IMPORT = 10;

    private List<Shortcut> shortcuts;
    private ShortcutAdapter adapter;
    private Switch dockSwitch;
    private LinearLayout preview;
    private Button[] profileTabs = new Button[3];
    private String pendingContactKind; // "call"/"sms"/"torch" while permission/pick in flight
    private int pendingIconPos = -1;
    private boolean swapMode = false;
    private int swapFirst = -1;
    private Switch islandSwitch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayHelper.enforceHighRefreshRate(this);
        setContentView(R.layout.activity_main);

        shortcuts = ShortcutStore.load(this);
        adapter = new ShortcutAdapter();

        preview = findViewById(R.id.dock_preview);
        refreshPreview();

        profileTabs[0] = findViewById(R.id.tab_p0);
        profileTabs[1] = findViewById(R.id.tab_p1);
        profileTabs[2] = findViewById(R.id.tab_p2);
        for (int i = 0; i < 3; i++) {
            final int p = i;
            profileTabs[i].setOnClickListener(v -> switchProfile(p));
            profileTabs[i].setOnLongClickListener(v -> {
                renameProfile(p);
                return true;
            });
        }
        refreshProfileTabs();

        View openHome = findViewById(R.id.btn_open_home);
        if (openHome != null) {
            openHome.setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        }

        ListView list = findViewById(R.id.shortcut_list);
        list.setAdapter(adapter);

        Button add = findViewById(R.id.btn_add);
        add.setOnClickListener(v -> {
            if (shortcuts.size() >= ShortcutStore.MAX) {
                toast("This profile is full (16 max) — try another profile");
                return;
            }
            showAddChooser();
        });

        View addWebApp = findViewById(R.id.btn_add_webapp);
        if (addWebApp != null) {
            addWebApp.setOnClickListener(v -> {
                if (shortcuts.size() >= ShortcutStore.MAX) {
                    toast("This profile is full (16 max) — try another profile");
                    return;
                }
                showCustomUrlDialog("", "");
            });
        }

        View searchApps = findViewById(R.id.btn_search_apps);
        if (searchApps != null) {
            searchApps.setOnClickListener(v -> {
                Intent i = new Intent(this, AppPickerActivity.class);
                i.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, true);
                startActivity(i);
            });
        }

        dockSwitch = findViewById(R.id.switch_dock);
        dockSwitch.setChecked(ShortcutStore.isDockOn(this));
        dockSwitch.setOnCheckedChangeListener((v, on) -> setDockEnabled(on));

        Switch roundSwitch = findViewById(R.id.switch_round);
        roundSwitch.setChecked(ShortcutStore.isRoundIcons(this));
        roundSwitch.setOnCheckedChangeListener((v, on) -> {
            ShortcutStore.setRoundIcons(this, on);
            persist();
        });

        Switch homeClockSwitch = findViewById(R.id.switch_home_clock);
        if (homeClockSwitch != null) {
            homeClockSwitch.setChecked(ShortcutStore.isHomeClock(this));
            homeClockSwitch.setOnCheckedChangeListener((v, on) -> ShortcutStore.setHomeClock(this, on));
        }

        Switch homeBatterySwitch = findViewById(R.id.switch_home_battery);
        if (homeBatterySwitch != null) {
            homeBatterySwitch.setChecked(ShortcutStore.isHomeBattery(this));
            homeBatterySwitch.setOnCheckedChangeListener((v, on) -> ShortcutStore.setHomeBattery(this, on));
        }

        Switch homeProfilesSwitch = findViewById(R.id.switch_home_profiles);
        if (homeProfilesSwitch != null) {
            homeProfilesSwitch.setChecked(ShortcutStore.isHomeProfiles(this));
            homeProfilesSwitch.setOnCheckedChangeListener((v, on) -> ShortcutStore.setHomeProfiles(this, on));
        }

        Switch homeHideLabelsSwitch = findViewById(R.id.switch_home_hide_labels);
        if (homeHideLabelsSwitch != null) {
            homeHideLabelsSwitch.setChecked(ShortcutStore.isHomeHideLabels(this));
            homeHideLabelsSwitch.setOnCheckedChangeListener((v, on) -> ShortcutStore.setHomeHideLabels(this, on));
        }

        Switch homeCleanModeSwitch = findViewById(R.id.switch_home_clean_mode);
        if (homeCleanModeSwitch != null) {
            homeCleanModeSwitch.setChecked(ShortcutStore.isHomeCleanMode(this));
            homeCleanModeSwitch.setOnCheckedChangeListener((v, on) -> {
                ShortcutStore.setHomeCleanMode(this, on);
                Toast.makeText(this, on ? "Clean Mode: Apps tucked into 'Apps' pill" : "Clean Mode disabled", Toast.LENGTH_SHORT).show();
            });
        }

        Button btnGridCols = findViewById(R.id.btn_home_grid_density);
        if (btnGridCols != null) {
            btnGridCols.setText("Home Grid Density: " + ShortcutStore.homeGridColumns(this) + " Columns (tap to cycle 3, 4, 5)");
            btnGridCols.setOnClickListener(v -> {
                int next = ShortcutStore.homeGridColumns(this) + 1;
                if (next > 5) next = 3;
                ShortcutStore.setHomeGridColumns(this, next);
                btnGridCols.setText("Home Grid Density: " + next + " Columns (tap to cycle 3, 4, 5)");
            });
        }

        Switch doubleTapLockSwitch = findViewById(R.id.switch_double_tap_lock);
        if (doubleTapLockSwitch != null) {
            doubleTapLockSwitch.setChecked(ShortcutStore.isDoubleTapLock(this));
            doubleTapLockSwitch.setOnCheckedChangeListener((v, on) -> {
                ShortcutStore.setDoubleTapLock(this, on);
                if (on && !LockScreenService.isServiceRunning()) {
                    LockScreenService.requestAccessibilityPermission(this);
                }
            });
        }

        Switch monochromeIconsSwitch = findViewById(R.id.switch_monochrome_icons);
        if (monochromeIconsSwitch != null) {
            monochromeIconsSwitch.setChecked(ShortcutStore.isMonochromeIcons(this));
            monochromeIconsSwitch.setOnCheckedChangeListener((v, on) -> {
                ShortcutStore.setMonochromeIcons(this, on);
                Toast.makeText(this, on ? "Monochrome mode enabled" : "Full color mode enabled", Toast.LENGTH_SHORT).show();
            });
        }

        Switch searchCalculatorSwitch = findViewById(R.id.switch_search_calculator);
        if (searchCalculatorSwitch != null) {
            searchCalculatorSwitch.setChecked(ShortcutStore.isSearchCalculator(this));
            searchCalculatorSwitch.setOnCheckedChangeListener((v, on) -> {
                ShortcutStore.setSearchCalculator(this, on);
            });
        }

        Switch faviconSwitch = findViewById(R.id.switch_favicon_fetch);
        if (faviconSwitch != null) {
            faviconSwitch.setChecked(WebLogoHelper.isFaviconFetchEnabled(this));
            faviconSwitch.setOnCheckedChangeListener((v, on) -> {
                WebLogoHelper.setFaviconFetchEnabled(this, on);
            });
        }

        Switch notifBadgesSwitch = findViewById(R.id.switch_notification_badges);
        if (notifBadgesSwitch != null) {
            notifBadgesSwitch.setChecked(LockNotificationListenerService.isBadgesEnabled(this));
            notifBadgesSwitch.setOnCheckedChangeListener((v, on) -> {
                LockNotificationListenerService.setBadgesEnabled(this, on);
                if (on && !LockNotificationListenerService.isPermissionGranted(this)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Notification Badges")
                            .setMessage("To calculate unread dots & badges offline, allow notification access in the next screen.")
                            .setPositiveButton("Enable", (d, w) -> {
                                try {
                                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                                } catch (Exception ignored) {}
                            })
                            .setNegativeButton("Later", null)
                            .show();
                }
            });
        }

        Button btnIconPack = findViewById(R.id.btn_icon_pack);
        if (btnIconPack != null) {
            btnIconPack.setOnClickListener(v -> showIconPackDialog());
        }

        Button btnHiddenApps = findViewById(R.id.btn_hidden_apps);
        if (btnHiddenApps != null) {
            btnHiddenApps.setOnClickListener(v -> showHiddenAppsDialog());
        }

        SeekBar opacity = findViewById(R.id.seek_opacity);
        opacity.setMax(255);
        opacity.setProgress(ShortcutStore.glassOpacity(this));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                ShortcutStore.setGlassOpacity(MainActivity.this, s.getProgress());
                persist();
            }
        });

        EditText memo = findViewById(R.id.edit_memo);
        memo.setText(ShortcutStore.dockMemo(this));
        memo.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                ShortcutStore.setDockMemo(this, memo.getText().toString());
                persist();
            }
        });

        View.OnClickListener tokenListener = v -> {
            String token = "";
            int id = v.getId();
            if (id == R.id.btn_token_battery) token = "{battery}";
            else if (id == R.id.btn_token_date) token = "{date}";
            else if (id == R.id.btn_token_time) token = "{time}";
            else if (id == R.id.btn_token_profile) token = "{profile}";
            else if (id == R.id.btn_token_alarms) token = "{alarms}";
            else if (id == R.id.btn_token_next) token = "{next}";
            int start = Math.max(memo.getSelectionStart(), 0);
            int end = Math.max(memo.getSelectionEnd(), 0);
            memo.getText().replace(Math.min(start, end), Math.max(start, end), token, 0, token.length());
            ShortcutStore.setDockMemo(this, memo.getText().toString());
            persist();
        };
        findViewById(R.id.btn_token_battery).setOnClickListener(tokenListener);
        findViewById(R.id.btn_token_date).setOnClickListener(tokenListener);
        findViewById(R.id.btn_token_time).setOnClickListener(tokenListener);
        findViewById(R.id.btn_token_profile).setOnClickListener(tokenListener);
        View btnAlarms = findViewById(R.id.btn_token_alarms);
        if (btnAlarms != null) btnAlarms.setOnClickListener(tokenListener);
        View btnNext = findViewById(R.id.btn_token_next);
        if (btnNext != null) btnNext.setOnClickListener(tokenListener);

        View setHome = findViewById(R.id.btn_set_home);
        if (setHome != null) {
            setHome.setOnClickListener(v -> {
                Intent homeIntent = new Intent(Settings.ACTION_HOME_SETTINGS);
                try {
                    startActivity(homeIntent);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                    } catch (Exception ex) {
                        toast("Go to Settings → Apps → Default apps → Home app");
                    }
                }
            });
        }

        findViewById(R.id.btn_pin_tiles).setOnClickListener(v -> pinQsTiles());
        findViewById(R.id.btn_pin_w1).setOnClickListener(v ->
                pinWidget(DockWidget1x1.class));
        findViewById(R.id.btn_pin_w2).setOnClickListener(v ->
                pinWidget(DockWidget2x1.class));
        findViewById(R.id.btn_pin_w4).setOnClickListener(v ->
                pinWidget(DockWidget4x1.class));
        findViewById(R.id.btn_pin_w44).setOnClickListener(v ->
                pinWidget(DockWidgetProvider.class));

        findViewById(R.id.btn_island_perm).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        islandSwitch = findViewById(R.id.switch_island);
        islandSwitch.setChecked(IslandService.isIslandOn(this));
        islandSwitch.setOnCheckedChangeListener((v, on) -> setIslandEnabled(on));

        Switch islandBatterySwitch = findViewById(R.id.switch_island_battery);
        islandBatterySwitch.setChecked(ShortcutStore.isIslandBattery(this));
        islandBatterySwitch.setOnCheckedChangeListener((v, on) -> {
            ShortcutStore.setIslandBattery(this, on);
            IslandService.refresh(this);
        });

        Button islandCountBtn = findViewById(R.id.btn_island_count);
        islandCountBtn.setText("Island icon count: " + ShortcutStore.islandIconsCount(this) + " (tap to cycle 2–6)");
        islandCountBtn.setOnClickListener(v -> {
            int c = (ShortcutStore.islandIconsCount(this) % 6) + 1;
            if (c < 2) c = 2;
            ShortcutStore.setIslandIconsCount(this, c);
            islandCountBtn.setText("Island icon count: " + c + " (tap to cycle 2–6)");
            IslandService.refresh(this);
        });

        Switch islandSeparateSwitch = findViewById(R.id.switch_island_separate);
        if (islandSeparateSwitch != null) {
            islandSeparateSwitch.setChecked(ShortcutStore.isIslandSeparate(this));
            islandSeparateSwitch.setOnCheckedChangeListener((v, on) -> {
                ShortcutStore.setIslandSeparate(this, on);
                IslandService.refresh(this);
                Toast.makeText(this, on ? "Dynamic Island has separate apps now" : "Dynamic Island linked to home profile", Toast.LENGTH_SHORT).show();
            });
        }

        Button btnManageIsland = findViewById(R.id.btn_manage_island_apps);
        if (btnManageIsland != null) {
            btnManageIsland.setOnClickListener(v -> showManageIslandAppsDialog());
        }

        findViewById(R.id.btn_sort).setOnClickListener(v -> {
            Collections.sort(shortcuts, (x, y) -> x.label.toLowerCase(Locale.ROOT)
                    .compareTo(y.label.toLowerCase(Locale.ROOT)));
            persist();
            toast("Sorted A–Z");
        });
        findViewById(R.id.btn_swap).setOnClickListener(v -> {
            swapMode = !swapMode;
            swapFirst = -1;
            ((Button) v).setText(swapMode ? "Cancel swap" : "Swap");
            adapter.notifyDataSetChanged();
            if (swapMode) toast("Tap two shortcuts to swap them");
        });
        findViewById(R.id.btn_prune).setOnClickListener(v -> {
            PackageManager pm = getPackageManager();
            int removed = 0;
            Iterator<Shortcut> it = shortcuts.iterator();
            while (it.hasNext()) {
                Shortcut s = it.next();
                if (s.isApp()) {
                    try {
                        pm.getApplicationInfo(s.pkg, 0);
                    } catch (Exception e) {
                        IconUtil.deleteCustomIcon(this, s.id);
                        it.remove();
                        removed++;
                    }
                }
            }
            persist();
            toast(removed == 0 ? "Nothing to clean up"
                    : "Removed " + removed + " uninstalled app" + (removed == 1 ? "" : "s"));
        });
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            int p = ShortcutStore.activeProfile(this);
            String pName = ShortcutStore.profileName(this, p);
            new AlertDialog.Builder(this)
                    .setTitle("Clear " + pName + "?")
                    .setMessage("Remove all shortcuts in this profile?")
                    .setPositiveButton("Clear", (d, w) -> {
                        for (Shortcut s : shortcuts) {
                            IconUtil.deleteCustomIcon(this, s.id);
                        }
                        shortcuts.clear();
                        if (ShortcutStore.isDockOn(this)) {
                            ShortcutStore.setDockOn(this, false);
                            DockService.stop(this);
                            dockSwitch.setChecked(false);
                        }
                        persist();
                        toast("Cleared " + pName);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        findViewById(R.id.btn_backup_export).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "locklauncher_backup.json");
            try {
                startActivityForResult(i, REQ_EXPORT);
            } catch (Exception e) {
                toast("No file app found");
            }
        });
        findViewById(R.id.btn_backup_import).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            try {
                startActivityForResult(i, REQ_IMPORT);
            } catch (Exception e) {
                toast("No file app found");
            }
        });

        findViewById(R.id.btn_setup_notif).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });
        findViewById(R.id.btn_setup_lockscreen).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
            toast("Set Lock screen notifications to 'Show'");
        });
        findViewById(R.id.btn_setup_battery).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                toast("Allow 'Unrestricted' battery use for Lock Launcher in Settings");
            }
        });

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            String sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (sharedText != null && !sharedText.trim().isEmpty()) {
                String url = extractUrl(sharedText.trim());
                showCustomUrlDialog(url, sharedTitle != null ? sharedTitle : "");
            }
        }
    }

    private String extractUrl(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s]+").matcher(text);
        if (m.find()) {
            return m.group();
        }
        return text.trim();
    }

    // ---------- profiles ----------

    @Override
    protected void onResume() {
        super.onResume();
        // The island can be hidden by long-pressing it, which changes the
        // pref behind the toggle's back — re-sync the switch here.
        if (islandSwitch != null) {
            boolean on = IslandService.isIslandOn(this);
            if (islandSwitch.isChecked() != on) {
                islandSwitch.setChecked(on);
            }
        }
    }

    private void switchProfile(int p) {
        ShortcutStore.setActiveProfile(this, p);
        shortcuts = ShortcutStore.load(this);
        adapter.notifyDataSetChanged();
        refreshPreview();
        refreshProfileTabs();
        persist();
        toast("Editing: " + ShortcutStore.profileName(this, p));
    }

    private void refreshProfileTabs() {
        int active = ShortcutStore.activeProfile(this);
        for (int i = 0; i < 3; i++) {
            profileTabs[i].setText(ShortcutStore.profileName(this, i));
            profileTabs[i].setBackgroundResource(
                    i == active ? R.drawable.btn_accent : R.drawable.btn_ghost);
        }
    }

    private void renameProfile(int p) {
        EditText input = new EditText(this);
        input.setText(ShortcutStore.profileName(this, p));
        input.setTextColor(0xFFFFFFFF);
        new AlertDialog.Builder(this)
                .setTitle("Rename profile")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) {
                        ShortcutStore.setProfileName(this, p, n);
                        refreshProfileTabs();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- add chooser ----------

    private void showAddChooser() {
        String[] items = {"App", "Web link", "Call contact", "Text contact (SMS)",
                "Flashlight toggle", "System settings panel", "App deep shortcut",
                "Switch dock profile"};
        new AlertDialog.Builder(this)
                .setTitle("Add shortcut")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            startActivityForResult(
                                    new Intent(this, AppPickerActivity.class), REQ_PICK_APP);
                            break;
                        case 1: showUrlDialog(); break;
                        case 2: addContactShortcut("call"); break;
                        case 3: addContactShortcut("sms"); break;
                        case 4: addTorchShortcut(); break;
                        case 5: showPanelPicker(); break;
                        case 6: showDeepShortcutPicker(); break;
                        case 7:
                            shortcuts.add(new Shortcut(Shortcut.TYPE_PROFILE, "", "", "",
                                    "Switch profile", Shortcut.ICON_LETTER, 0xFF7C5CFF));
                            persist();
                            break;
                    }
                })
                .show();
    }

    private void showPanelPicker() {
        new AlertDialog.Builder(this)
                .setTitle("System panel")
                .setItems(IconUtil.PANEL_NAMES, (d, which) -> {
                    String key = IconUtil.PANEL_KEYS[which];
                    shortcuts.add(new Shortcut(Shortcut.TYPE_PANEL, "", "",
                            key, IconUtil.PANEL_NAMES[which],
                            Shortcut.ICON_LETTER, 0xFF3B82F6));
                    persist();
                })
                .show();
    }

    private void showDeepShortcutPicker() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentActivities(
                new Intent(Intent.ACTION_CREATE_SHORTCUT), 0);
        if (ris.isEmpty()) {
            toast("No apps offer deep shortcuts");
            return;
        }
        List<ResolveInfo> sorted = new ArrayList<>(ris);
        java.util.Collections.sort(sorted, (a, b) -> String.valueOf(
                a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        String[] names = new String[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            names[i] = String.valueOf(sorted.get(i).loadLabel(pm));
        }
        new AlertDialog.Builder(this)
                .setTitle("Pick app")
                .setItems(names, (d, which) -> {
                    ResolveInfo ri = sorted.get(which);
                    Intent i = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                    i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    try {
                        startActivityForResult(i, REQ_DEEP_CREATE);
                    } catch (Exception e) {
                        toast("Couldn't open shortcut creator");
                    }
                })
                .show();
    }

    // ---------- icon editor ----------

    /** Icon picker: app icon / letter / emoji / gallery image. */
    private void showIconDialog(int pos) {
        Shortcut s = shortcuts.get(pos);
        List<String> items = new ArrayList<>();
        if (s.isApp()) items.add("App icon (default)");
        items.add("Letter tile (8 colors)");
        items.add("Emoji tile");
        items.add("Custom image (gallery)");
        new AlertDialog.Builder(this)
                .setTitle("Icon for " + s.label)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String choice = items.get(which);
                    if (choice.startsWith("App icon")) {
                        s.iconMode = Shortcut.ICON_AUTO;
                        s.iconColor = -1;
                        s.iconEmoji = "";
                        IconUtil.deleteCustomIcon(this, s.id);
                        persist();
                    } else if (choice.startsWith("Letter")) {
                        showColorPicker(pos, false);
                    } else if (choice.startsWith("Emoji")) {
                        showEmojiDialog(pos);
                    } else {
                        pendingIconPos = pos;
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("image/*");
                        try {
                            startActivityForResult(i, REQ_ICON_IMAGE);
                        } catch (Exception e) {
                            toast("No gallery app found");
                        }
                    }
                })
                .show();
    }

    private void showColorPicker(int pos, boolean forEmoji) {
        Shortcut s = shortcuts.get(pos);
        new AlertDialog.Builder(this)
                .setTitle(forEmoji ? "Emoji background" : "Tile color")
                .setItems(IconUtil.LETTER_COLOR_NAMES, (d, which) -> {
                    if (forEmoji) {
                        s.iconMode = Shortcut.ICON_EMOJI;
                        s.iconColor = IconUtil.LETTER_COLORS[which];
                        IconUtil.deleteCustomIcon(this, s.id);
                        persist();
                    } else {
                        s.iconMode = Shortcut.ICON_LETTER;
                        s.iconColor = IconUtil.LETTER_COLORS[which];
                        IconUtil.deleteCustomIcon(this, s.id);
                        persist();
                    }
                })
                .show();
    }

    private void showEmojiDialog(int pos) {
        Shortcut s = shortcuts.get(pos);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_emoji, null);
        EditText emoji = v.findViewById(R.id.emoji_text);
        if (Shortcut.ICON_EMOJI.equals(s.iconMode) && s.iconEmoji != null) {
            emoji.setText(s.iconEmoji);
        }
        new AlertDialog.Builder(this)
                .setTitle("Emoji tile")
                .setView(v)
                .setPositiveButton("Use emoji", (d, w) -> {
                    String e = emoji.getText().toString().trim();
                    if (e.isEmpty()) {
                        toast("Type an emoji first");
                        return;
                    }
                    s.iconMode = Shortcut.ICON_EMOJI;
                    s.iconEmoji = e.length() > 4 ? e.substring(0, 4) : e;
                    s.iconColor = -1; // transparent; recolor via color picker if wanted
                    IconUtil.deleteCustomIcon(this, s.id);
                    persist();
                    showColorPicker(pos, true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- contact / torch ----------

    private void addContactShortcut(String kind) {
        pendingContactKind = kind;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERM);
            return;
        }
        if ("call".equals(kind) && checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_PERM + 1);
            return;
        }
        pickContact();
    }

    private void pickContact() {
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI),
                    REQ_PICK_CONTACT);
        } catch (Exception e) {
            toast("No contacts app found");
        }
    }

    private void addTorchShortcut() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingContactKind = "torch";
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM);
            return;
        }
        shortcuts.add(new Shortcut(Shortcut.TYPE_TORCH, "", "", "", "Flashlight",
                Shortcut.ICON_LETTER, 0xFFF59E0B));
        persist();
        toast("Flashlight added");
    }

    // ---------- one-tap pinning ----------

    private void pinQsTiles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast("On this Android version, add tiles manually: swipe down, tap edit");
            return;
        }
        try {
            android.app.StatusBarManager sbm = getSystemService(android.app.StatusBarManager.class);
            Class<?>[] tiles = {DockTile1.class, DockTile2.class, DockTile3.class};
            String[] labels = {"Lock Launcher 1", "Lock Launcher 2", "Lock Launcher 3"};
            for (int i = 0; i < 3; i++) {
                Shortcut s = i < shortcuts.size() ? shortcuts.get(i) : null;
                Icon icon = Icon.createWithBitmap(s != null
                        ? IconUtil.iconFor(this, s)
                        : IconUtil.letterIcon(String.valueOf(i + 1), 0xFF7C5CFF));
                final int idx = i;
                sbm.requestAddTileService(
                        new ComponentName(this, tiles[i]),
                        labels[i], icon, getMainExecutor(),
                        result -> {
                            if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                toast(labels[idx] + " added");
                            }
                        });
            }
        } catch (Exception e) {
            toast("Couldn't pin tiles: " + e.getMessage());
        }
    }

    private void pinWidget(Class<?> provider) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            if (!mgr.isRequestPinAppWidgetSupported()) {
                toast("Your launcher doesn't support widget pinning");
                return;
            }
            mgr.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } catch (Exception e) {
            toast("Couldn't pin widget");
        }
    }

    // ---------- island ----------

    private void setIslandEnabled(boolean on) {
        if (on && !IslandService.canShow(this)) {
            toast("Allow 'Display over other apps' first");
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Switch sw = findViewById(R.id.switch_island);
            if (sw != null) sw.setChecked(false);
            return;
        }
        IslandService.setIslandOn(this, on);
        if (on) {
            IslandService.start(this);
            toast("Island on — tap the pill to expand your dock");
        } else {
            IslandService.stop(this);
        }
    }

    // ---------- dock toggle / permissions ----------

    private void setDockEnabled(boolean on) {
        if (on) {
            if (shortcuts.isEmpty()) {
                toast("Add at least one shortcut first");
                dockSwitch.setChecked(false);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                dockSwitch.setChecked(false);
                return;
            }
            ShortcutStore.setDockOn(this, true);
            DockService.start(this);
            toast("Dock on — it now shows in notifications and on the lock screen");
        } else {
            ShortcutStore.setDockOn(this, false);
            DockService.stop(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_NOTIF) {
            if (granted) {
                dockSwitch.setChecked(true);
                setDockEnabled(true);
            } else {
                toast("Notifications are required for the lock-screen dock");
            }
        } else if (code == REQ_PERM) {
            String kind = pendingContactKind;
            if ("torch".equals(kind)) {
                if (granted) addTorchShortcut();
                else toast("Camera permission needed for the flashlight");
                pendingContactKind = null;
            } else if (kind != null) {
                if (granted) addContactShortcut(kind);
                else {
                    toast("Contacts permission needed");
                    pendingContactKind = null;
                }
            }
        } else if (code == REQ_PERM + 1) {
            if (pendingContactKind != null) pickContact();
            pendingContactKind = null;
        }
    }

    // ---------- dialogs ----------

    private void showIconPackDialog() {
        List<IconPackManager.IconPackInfo> packs = IconPackManager.getInstalledIconPacks(this);
        List<String> names = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();

        names.add("📱 Default System Icons");
        pkgs.add(IconPackManager.PACK_SYSTEM_DEFAULT);

        names.add("🎨 Material You / Monochrome");
        pkgs.add(IconPackManager.PACK_MATERIAL_YOU);

        for (IconPackManager.IconPackInfo p : packs) {
            names.add("📦 " + p.label);
            pkgs.add(p.packageName);
        }

        String current = IconPackManager.getActiveIconPack(this);
        int selectedIdx = 0;
        for (int i = 0; i < pkgs.size(); i++) {
            if (pkgs.get(i).equals(current)) {
                selectedIdx = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Icon Pack")
                .setSingleChoiceItems(names.toArray(new String[0]), selectedIdx, (dialog, which) -> {
                    String chosen = pkgs.get(which);
                    IconPackManager.setActiveIconPack(this, chosen);
                    IconUtil.clearCache();
                    toast("Applied " + names.get(which));
                    dialog.dismiss();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    DockService.refresh(this);
                    IslandService.refresh(this);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHiddenAppsDialog() {
        SharedPreferences prefs = getSharedPreferences("drawer_prefs", MODE_PRIVATE);
        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
        if (hidden.isEmpty()) {
            toast("No apps are currently hidden");
            return;
        }

        PackageManager pm = getPackageManager();
        List<String> hiddenPkgs = new ArrayList<>(hidden);
        List<String> labels = new ArrayList<>();
        for (String pkg : hiddenPkgs) {
            try {
                labels.add(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
            } catch (Exception e) {
                labels.add(pkg);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Hidden Apps (Tap to unhide)")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String pkg = hiddenPkgs.get(which);
                    hidden.remove(pkg);
                    prefs.edit().putStringSet("hidden_apps", hidden).apply();
                    toast("Unhid " + labels.get(which));
                })
                .setPositiveButton("Close", null)
                .setNeutralButton("Unhide All", (dialog, which) -> {
                    prefs.edit().putStringSet("hidden_apps", new HashSet<>()).apply();
                    toast("Unhid all apps");
                })
                .show();
    }

    private void showUrlDialog() {
        String[] presetLabels = {
                "Google (Search)", "YouTube", "WhatsApp Direct", "Google Maps",
                "ChatGPT", "GitHub", "Reddit", "Custom URL..."
        };
        String[] presetUrls = {
                "https://google.com", "https://youtube.com", "https://api.whatsapp.com",
                "https://maps.google.com", "https://chatgpt.com", "https://github.com",
                "https://reddit.com", ""
        };
        String[] presetNames = {
                "Google", "YouTube", "WhatsApp", "Maps",
                "ChatGPT", "GitHub", "Reddit", ""
        };
        String[] presetEmojis = {
                "\uD83D\uDD0D", "\u25B6\uFE0F", "\uD83D\uDCAC", "\uD83D\uDDFA\uFE0F",
                "\uD83D\uDCA1", "\uD83D\uDC19", "\uD83E\uDD16", ""
        };
        int[] presetColors = {
                0xFF3B82F6, 0xFFEF4444, 0xFF10B981, 0xFF10B981,
                0xFF10B981, 0xFF374151, 0xFFF59E0B, -1
        };

        new AlertDialog.Builder(this)
                .setTitle("Add web app shortcut")
                .setItems(presetLabels, (d, which) -> {
                    if (which == presetLabels.length - 1) {
                        showCustomUrlDialog("", "");
                    } else {
                        showCustomUrlDialog(presetUrls[which], presetNames[which]);
                    }
                })
                .show();
    }

    private void showCustomUrlDialog() {
        showCustomUrlDialog("", "");
    }

    private void showCustomUrlDialog(String initialUrl, String initialName) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_url, null);
        EditText name = v.findViewById(R.id.url_name);
        EditText url = v.findViewById(R.id.url_value);
        CheckBox chkPin = v.findViewById(R.id.chk_pin_home);

        if (initialName != null && !initialName.isEmpty()) {
            name.setText(initialName);
        }
        if (initialUrl != null && !initialUrl.isEmpty()) {
            url.setText(initialUrl);
        } else {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
                    if (clip != null) {
                        String c = clip.toString().trim();
                        if (c.startsWith("http://") || c.startsWith("https://")) {
                            url.setText(c);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        new AlertDialog.Builder(this)
                .setTitle("Add Web App")
                .setView(v)
                .setPositiveButton("Add", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) {
                        toast("Enter a URL");
                        return;
                    }
                    if (!u.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                        u = "https://" + u;
                    }
                    if (n.isEmpty()) {
                        try {
                            android.net.Uri parsed = android.net.Uri.parse(u);
                            String host = parsed.getHost();
                            if (host != null && !host.isEmpty()) {
                                n = host.replaceFirst("^www\\.", "");
                            }
                        } catch (Exception ignored) {}
                        if (n == null || n.isEmpty()) {
                            n = u.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "")
                                    .replaceFirst("^www\\.", "").split("/")[0];
                        }
                        if (n == null || n.isEmpty()) n = "Web";
                    }
                    Shortcut s = new Shortcut(Shortcut.TYPE_URL, "", u, n);
                    shortcuts.add(s);
                    persist();
                    if (chkPin != null && chkPin.isChecked()) {
                        boolean pinned = IconUtil.pinShortcutToHomeScreen(this, s);
                        toast(pinned ? n + " added & pinned to Home screen" : n + " added");
                    } else {
                        toast(n + " added as web app");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditUrlDialog(Shortcut s, int pos) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_url, null);
        EditText name = v.findViewById(R.id.url_name);
        EditText url = v.findViewById(R.id.url_value);
        CheckBox chkPin = v.findViewById(R.id.chk_pin_home);
        if (chkPin != null) chkPin.setChecked(false);
        name.setText(s.label);
        url.setText(s.url);
        new AlertDialog.Builder(this)
                .setTitle("Edit Web App")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) {
                        toast("Enter a URL");
                        return;
                    }
                    if (!u.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                        u = "https://" + u;
                    }
                    if (n.isEmpty()) {
                        try {
                            android.net.Uri parsed = android.net.Uri.parse(u);
                            String host = parsed.getHost();
                            if (host != null && !host.isEmpty()) {
                                n = host.replaceFirst("^www\\.", "");
                            }
                        } catch (Exception ignored) {}
                        if (n == null || n.isEmpty()) n = "Web";
                    }
                    s.label = n;
                    s.url = u;
                    persist();
                    if (chkPin != null && chkPin.isChecked()) {
                        IconUtil.pinShortcutToHomeScreen(this, s);
                    }
                    toast(n + " updated");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- activity results ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_APP && res == RESULT_OK && data != null) {
            ArrayList<String> pkgs = data.getStringArrayListExtra(
                    AppPickerActivity.EXTRA_PACKAGES);
            ArrayList<String> labels = data.getStringArrayListExtra(
                    AppPickerActivity.EXTRA_LABELS);
            long[] serials = data.getLongArrayExtra(
                    AppPickerActivity.EXTRA_PROFILE_SERIALS);
            if (pkgs != null && !pkgs.isEmpty()) {
                int added = 0;
                for (int i = 0; i < pkgs.size(); i++) {
                    if (shortcuts.size() >= ShortcutStore.MAX) break;
                    String label = (labels != null && i < labels.size())
                            ? labels.get(i) : pkgs.get(i);
                    long sSerial = (serials != null && i < serials.length) ? serials[i] : 0;
                    shortcuts.add(new Shortcut(Shortcut.TYPE_APP, pkgs.get(i), "", label, sSerial));
                    added++;
                }
                persist();
                if (added < pkgs.size()) {
                    toast("Added " + added + " — profile is full at 16");
                } else {
                    toast("Added " + added + " app" + (added == 1 ? "" : "s"));
                }
            } else {
                // Legacy single-app result.
                String pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE);
                String label = data.getStringExtra(AppPickerActivity.EXTRA_LABEL);
                if (pkg != null && shortcuts.size() < ShortcutStore.MAX) {
                    shortcuts.add(new Shortcut(Shortcut.TYPE_APP, pkg, "", label));
                    persist();
                }
            }
        } else if (req == REQ_PICK_CONTACT && res == RESULT_OK && data != null
                && pendingContactKind != null) {
            addContactFromUri(data.getData(), pendingContactKind);
            pendingContactKind = null;
        } else if (req == REQ_DEEP_CREATE && res == RESULT_OK && data != null) {
            addDeepShortcut(data);
        } else if (req == REQ_ICON_IMAGE && res == RESULT_OK && data != null
                && pendingIconPos >= 0 && pendingIconPos < shortcuts.size()) {
            importGalleryIcon(data.getData(), shortcuts.get(pendingIconPos));
            pendingIconPos = -1;
        } else if (req == REQ_EXPORT && res == RESULT_OK && data != null) {
            exportBackup(data.getData());
        } else if (req == REQ_IMPORT && res == RESULT_OK && data != null) {
            importBackup(data.getData());
        }
    }

    private void exportBackup(Uri uri) {
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null && BackupManager.exportTo(this, out)) {
                toast("Backup saved");
            } else {
                toast("Backup failed");
            }
        } catch (Exception e) {
            toast("Backup failed");
        }
    }

    private void importBackup(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Restore backup?")
                .setMessage("This replaces all 3 profiles and settings with the backup file.")
                .setPositiveButton("Restore", (d, w) -> {
                    try (java.io.InputStream in =
                                 getContentResolver().openInputStream(uri)) {
                        BackupManager.ImportResult r = in != null
                                ? BackupManager.importFrom(this, in) : null;
                        if (r == null) {
                            toast("Couldn't read backup file");
                            return;
                        }
                        shortcuts = ShortcutStore.load(this);
                        adapter.notifyDataSetChanged();
                        refreshPreview();
                        refreshProfileTabs();
                        persist();
                        String msg = "Restored " + r.slotsRestored + " shortcuts";
                        if (r.missingApps > 0) {
                            msg += " (" + r.missingApps + " apps not installed)";
                        }
                        if (r.invalidDropped > 0) {
                            msg += " (" + r.invalidDropped + " invalid/unresolvable slots dropped)";
                        }
                        toast(msg);
                    } catch (Exception e) {
                        toast("Restore failed");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addDeepShortcut(Intent data) {
        try {
            Intent shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            if (shortcutIntent == null) {
                toast("App didn't return a shortcut");
                return;
            }
            if (name == null || name.isEmpty()) name = "Deep link";
            Shortcut s = new Shortcut(Shortcut.TYPE_DEEP, "", "",
                    shortcutIntent.toUri(Intent.URI_INTENT_SCHEME),
                    name, Shortcut.ICON_LETTER, -1);
            Bitmap iconBmp = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
            if (iconBmp != null) {
                IconUtil.saveCustomIcon(this, s.id,
                        IconUtil.squareCrop(iconBmp, 192));
                s.iconMode = Shortcut.ICON_IMAGE;
            } else {
                Intent.ShortcutIconResource res = data.getParcelableExtra(
                        Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
                Bitmap resBmp = loadShortcutIconResource(res);
                if (resBmp != null) {
                    IconUtil.saveCustomIcon(this, s.id,
                            IconUtil.squareCrop(resBmp, 192));
                    s.iconMode = Shortcut.ICON_IMAGE;
                }
            }
            shortcuts.add(s);
            persist();
            toast(name + " added");
        } catch (Exception e) {
            toast("Couldn't save deep shortcut");
        }
    }

    private Bitmap loadShortcutIconResource(Intent.ShortcutIconResource res) {
        if (res == null) return null;
        try {
            android.content.res.Resources r = getPackageManager()
                    .getResourcesForApplication(res.packageName);
            int id = r.getIdentifier(res.resourceName, null, null);
            if (id == 0) return null;
            android.graphics.drawable.Drawable d = r.getDrawable(id, null);
            Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            d.setBounds(0, 0, 192, 192);
            d.draw(c);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void importGalleryIcon(Uri uri, Shortcut s) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp == null) {
                toast("Couldn't read image");
                return;
            }
            if (IconUtil.saveCustomIcon(this, s.id, IconUtil.squareCrop(bmp, 192))) {
                s.iconMode = Shortcut.ICON_IMAGE;
                persist();
                toast("Custom icon set");
            } else {
                toast("Couldn't save icon");
            }
        } catch (Exception e) {
            toast("Couldn't read image");
        }
    }

    private void addContactFromUri(Uri contactUri, String kind) {
        Cursor c = null;
        try {
            c = getContentResolver().query(contactUri, null, null, null, null);
            if (c == null || !c.moveToFirst()) {
                toast("Couldn't read contact");
                return;
            }
            String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
            String name = c.getString(c.getColumnIndexOrThrow(
                    ContactsContract.Contacts.DISPLAY_NAME));
            int hasPhone = c.getInt(c.getColumnIndexOrThrow(
                    ContactsContract.Contacts.HAS_PHONE_NUMBER));
            if (hasPhone <= 0) {
                toast("Contact has no phone number");
                return;
            }
            Cursor p = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{id}, null);
            String number = null;
            if (p != null) {
                if (p.moveToFirst()) number = p.getString(0);
                p.close();
            }
            if (number == null || number.isEmpty()) {
                toast("Contact has no phone number");
                return;
            }
            String type = "call".equals(kind) ? Shortcut.TYPE_CALL : Shortcut.TYPE_SMS;
            shortcuts.add(new Shortcut(type, "", "", number, name,
                    Shortcut.ICON_LETTER, -1));
            persist();
            toast(name + " added");
        } catch (Exception e) {
            toast("Couldn't read contact");
        } finally {
            if (c != null) c.close();
        }
    }

    // ---------- refresh ----------

    private void persist() {
        ShortcutStore.save(this, shortcuts);
        adapter.notifyDataSetChanged();
        refreshPreview();
        if (ShortcutStore.isDockOn(this) && !shortcuts.isEmpty()) {
            DockService.refresh(this);
        }
        BaseDockWidget.refreshAll(this);
        BaseDockTile.requestTileUpdate(this);
        IslandService.refresh(this);
    }

    private void refreshPreview() {
        preview.removeAllViews();
        if (shortcuts.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("Empty — add shortcuts below");
            hint.setTextColor(0xFF9AA0B4);
            hint.setTextSize(13);
            hint.setPadding(8, 8, 8, 8);
            preview.addView(hint);
            return;
        }
        for (Shortcut s : shortcuts) {
            ImageView iv = new ImageView(this);
            int px = (int) (48 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
            int m = (int) (6 * getResources().getDisplayMetrics().density);
            lp.setMargins(m, m, m, m);
            iv.setLayoutParams(lp);
            iv.setImageBitmap(IconUtil.iconFor(this, s));
            iv.setContentDescription(s.label);
            preview.addView(iv);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    class ShortcutAdapter extends BaseAdapter {
        @Override public int getCount() { return shortcuts.size(); }
        @Override public Shortcut getItem(int p) { return shortcuts.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.row_shortcut, parent, false);
            }
            Shortcut s = getItem(pos);
            ImageView icon = v.findViewById(R.id.sc_icon);
            icon.setImageBitmap(IconUtil.iconFor(MainActivity.this, s));
            icon.setOnClickListener(b -> showIconDialog(pos));
            ((TextView) v.findViewById(R.id.sc_name)).setText(s.label);
            ((TextView) v.findViewById(R.id.sc_type)).setText(s.typeName());

            // Swap-mode highlight + tap-to-swap.
            View card = ((ViewGroup) v).getChildAt(0);
            if (card != null) {
                card.setBackgroundResource(
                        swapMode && pos == swapFirst ? R.drawable.btn_accent
                                : R.drawable.row_card);
            }
            v.setOnClickListener(swapMode ? lv -> onSwapTap(pos) : lv -> {
                boolean ok = IconUtil.fireShortcut(MainActivity.this, s);
                if (!ok) toast(IconUtil.launchFailureReason(MainActivity.this, s));
            });

            v.findViewById(R.id.btn_up).setOnClickListener(b -> {
                if (pos > 0) {
                    Shortcut t = shortcuts.remove(pos);
                    shortcuts.add(pos - 1, t);
                    persist();
                }
            });
            v.findViewById(R.id.btn_down).setOnClickListener(b -> {
                if (pos < shortcuts.size() - 1) {
                    Shortcut t = shortcuts.remove(pos);
                    shortcuts.add(pos + 1, t);
                    persist();
                }
            });
            v.findViewById(R.id.btn_del).setOnClickListener(b -> deleteShortcut(pos));
            v.setOnLongClickListener(lv -> {
                showShortcutOptions(pos);
                return true;
            });
            return v;
        }
    }

    private void showShortcutOptions(int pos) {
        if (pos < 0 || pos >= shortcuts.size()) return;
        Shortcut s = shortcuts.get(pos);
        int currentProfile = ShortcutStore.activeProfile(this);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        options.add("🚀 Launch now");
        actions.add(() -> {
            boolean ok = IconUtil.fireShortcut(this, s);
            if (!ok) toast(IconUtil.launchFailureReason(this, s));
        });

        options.add("✏️ Rename Shortcut");
        actions.add(() -> {
            EditText input = new EditText(this);
            input.setText(s.label);
            if (s.label != null) input.setSelection(s.label.length());
            new AlertDialog.Builder(this)
                    .setTitle("Rename Shortcut")
                    .setMessage("Enter new display name:")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        String newName = input.getText().toString().trim();
                        if (!newName.isEmpty()) {
                            s.label = newName;
                            if (s.isApp() && s.pkg != null) {
                                String key = AppPickerActivity.getCustomLabelKey(s.pkg, s.profileSerial);
                                getSharedPreferences("dock", MODE_PRIVATE)
                                        .edit().putString(key, newName).apply();
                            }
                            persist();
                            toast("Renamed to " + newName);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        options.add("📌 Pin to Home screen (Main Page)");
        actions.add(() -> {
            boolean pinned = IconUtil.pinShortcutToHomeScreen(this, s);
            toast(pinned ? "Pinned " + s.label + " to Home screen" : "Home screen pinning not supported");
        });

        if (Shortcut.TYPE_URL.equals(s.type)) {
            options.add("✏️ Edit Web App");
            actions.add(() -> showEditUrlDialog(s, pos));
        }

        options.add("🎨 Change icon / style");
        actions.add(() -> showIconDialog(pos));

        for (int p = 0; p < ShortcutStore.PROFILES; p++) {
            if (p != currentProfile) {
                final int targetP = p;
                options.add("📋 Copy to " + ShortcutStore.profileName(this, p));
                actions.add(() -> copyShortcutToProfile(s, targetP));
            }
        }

        options.add("✕ Delete");
        actions.add(() -> deleteShortcut(pos));

        new AlertDialog.Builder(this)
                .setTitle(s.label)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void copyShortcutToProfile(Shortcut src, int targetProfile) {
        List<Shortcut> targetList = ShortcutStore.load(this, targetProfile);
        if (targetList.size() >= ShortcutStore.MAX) {
            toast(ShortcutStore.profileName(this, targetProfile) + " is full (16 max)");
            return;
        }
        String newId = java.util.UUID.randomUUID().toString();
        Shortcut clone = new Shortcut(newId, src.type, src.pkg, src.url, src.data,
                src.label, src.iconMode, src.iconColor, src.iconEmoji);
        java.io.File srcIcon = IconUtil.customIconFile(this, src.id);
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
        ShortcutStore.save(this, targetProfile, targetList);
        toast("Copied to " + ShortcutStore.profileName(this, targetProfile));
    }

    /** Swap mode: first tap marks, second tap exchanges the two slots. */
    private void onSwapTap(int pos) {
        if (swapFirst < 0) {
            swapFirst = pos;
            adapter.notifyDataSetChanged();
        } else if (swapFirst == pos) {
            swapFirst = -1;
            adapter.notifyDataSetChanged();
        } else {
            Shortcut a = shortcuts.get(swapFirst);
            shortcuts.set(swapFirst, shortcuts.get(pos));
            shortcuts.set(pos, a);
            swapFirst = -1;
            swapMode = false;
            Button swapBtn = findViewById(R.id.btn_swap);
            if (swapBtn != null) swapBtn.setText("Swap");
            persist();
            toast("Swapped");
        }
    }

    /** Removes a shortcut and its custom icon; shared by the row button and long-press. */    private void deleteShortcut(int pos) {
        if (pos < 0 || pos >= shortcuts.size()) return;
        Shortcut removed = shortcuts.remove(pos);
        IconUtil.deleteCustomIcon(this, removed.id);
        if (shortcuts.isEmpty() && ShortcutStore.isDockOn(this)) {
            ShortcutStore.setDockOn(this, false);
            DockService.stop(this);
            dockSwitch.setChecked(false);
        }
        persist();
        toast("Deleted " + removed.label);
    }

    private void showManageIslandAppsDialog() {
        List<Shortcut> island = ShortcutStore.loadIsland(this);
        List<String> items = new ArrayList<>();
        items.add("➕ Add App to Dynamic Island");
        items.add("🔄 Copy All Home Apps to Island");
        if (!island.isEmpty()) {
            items.add("🗑️ Clear All Island Apps");
        }
        for (int i = 0; i < island.size(); i++) {
            Shortcut s = island.get(i);
            items.add("Slot " + (i + 1) + ": " + (s.isEmpty() ? "[ ✨ Empty ]" : (s.isFolder() ? "📁 " : "") + s.label));
        }

        new AlertDialog.Builder(this)
                .setTitle("🏝️ Dynamic Island Apps (" + island.size() + ")")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(this, AppPickerActivity.class);
                        pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
                        startActivity(pick);
                    } else if (which == 1) {
                        List<Shortcut> home = ShortcutStore.load(this);
                        List<Shortcut> clean = new ArrayList<>();
                        for (Shortcut h : home) {
                            if (!h.isEmpty()) clean.add(h);
                        }
                        ShortcutStore.saveIsland(this, clean);
                        IslandService.refresh(this);
                        Toast.makeText(this, "Copied home apps to Dynamic Island (" + clean.size() + " apps)", Toast.LENGTH_SHORT).show();
                        showManageIslandAppsDialog();
                    } else if (!island.isEmpty() && which == 2) {
                        ShortcutStore.saveIsland(this, new ArrayList<>());
                        IslandService.refresh(this);
                        Toast.makeText(this, "Cleared Dynamic Island apps", Toast.LENGTH_SHORT).show();
                    } else {
                        int offset = (!island.isEmpty() ? 3 : 2);
                        int slotIdx = which - offset;
                        if (slotIdx >= 0 && slotIdx < island.size()) {
                            Shortcut s = island.get(slotIdx);
                            String[] actions = {"🔄 Replace App", "🗑️ Remove from Island", "⬅ Move Earlier", "➡ Move Later"};
                            new AlertDialog.Builder(this)
                                    .setTitle(s.label)
                                    .setItems(actions, (ad, aw) -> {
                                        if (aw == 0) {
                                            Intent pick = new Intent(this, AppPickerActivity.class);
                                            pick.putExtra(AppPickerActivity.EXTRA_PICK_FOR_ISLAND, true);
                                            pick.putExtra(AppPickerActivity.EXTRA_ISLAND_SLOT_INDEX, slotIdx);
                                            startActivity(pick);
                                        } else if (aw == 1) {
                                            island.remove(slotIdx);
                                            ShortcutStore.saveIsland(this, island);
                                            IslandService.refresh(this);
                                            Toast.makeText(this, "Removed " + s.label, Toast.LENGTH_SHORT).show();
                                            showManageIslandAppsDialog();
                                        } else if (aw == 2 && slotIdx > 0) {
                                            Shortcut prev = island.get(slotIdx - 1);
                                            island.set(slotIdx - 1, s);
                                            island.set(slotIdx, prev);
                                            ShortcutStore.saveIsland(this, island);
                                            IslandService.refresh(this);
                                            showManageIslandAppsDialog();
                                        } else if (aw == 3 && slotIdx < island.size() - 1) {
                                            Shortcut next = island.get(slotIdx + 1);
                                            island.set(slotIdx + 1, s);
                                            island.set(slotIdx, next);
                                            ShortcutStore.saveIsland(this, island);
                                            IslandService.refresh(this);
                                            showManageIslandAppsDialog();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    }
                })
                .setPositiveButton("Done", null)
                .show();
    }
}
