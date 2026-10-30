package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile-namespaced shortcut storage. Three profiles (0-2); v1.4's flat
 * "shortcuts" key migrates to profile 0 on first run. Offline, no accounts.
 */
public class ShortcutStore {
    private static final String PREFS = "dock";
    private static final String KEY_SHORTCUTS = "shortcuts"; // legacy v1.4 key
    private static final String KEY_DOCK_ON = "dock_on";
    private static final String KEY_ROUND = "round_icons";
    private static final String KEY_OPACITY = "glass_opacity";
    private static final String KEY_MEMO = "dock_memo";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_PROFILE_NAMES = "profile_names";
    private static final String KEY_ISLAND_ICONS = "island_icons_count";
    private static final String KEY_ISLAND_BATTERY = "island_show_battery";
    private static final String KEY_HOME_CLOCK = "home_show_clock";
    private static final String KEY_HOME_BATTERY = "home_show_battery";
    private static final String KEY_HOME_PROFILES = "home_show_profiles";
    private static final String KEY_HOME_COLUMNS = "home_grid_columns";
    private static final String KEY_HOME_HIDE_LABELS = "home_hide_labels";
    private static final String KEY_HOME_WIDGETS = "home_widgets_p";
    private static final String KEY_DOUBLE_TAP_LOCK = "home_double_tap_lock";
    private static final String KEY_MONOCHROME_ICONS = "home_monochrome_icons";
    private static final String KEY_SEARCH_CALCULATOR = "search_calculator";
    private static final String KEY_WIDGET_HEIGHT = "widget_h_";
    private static final String KEY_ISLAND_SEPARATE_APPS = "island_separate_apps";
    private static final String KEY_ISLAND_SHORTCUTS = "island_shortcuts";
    private static final String KEY_HOME_CLEAN_MODE = "home_clean_mode";
    private static final String KEY_HOME_ALIGN_BOTTOM = "home_align_bottom";
    private static final String KEY_HOME_ICON_SIZE = "home_icon_size";
    private static final String KEY_DRAWER_AUTO_KEYBOARD = "drawer_auto_keyboard";
    private static final String KEY_LOCKED_APPS = "locked_apps";
    public static final int MAX = 32;
    public static final int PROFILES = 3;

    private static String profileKey(int profile) {
        return "shortcuts_p" + profile;
    }

    /** Clamp a profile index to the valid range; never trust stored/imported values. */
    public static int clampProfile(int profile) {
        if (profile < 0) return 0;
        if (profile >= PROFILES) return PROFILES - 1;
        return profile;
    }

    public static int activeProfile(Context ctx) {
        return clampProfile(prefs(ctx).getInt(KEY_ACTIVE_PROFILE, 0));
    }

    public static void setActiveProfile(Context ctx, int profile) {
        prefs(ctx).edit().putInt(KEY_ACTIVE_PROFILE, clampProfile(profile)).apply();
    }

    public static String profileName(Context ctx, int profile) {
        String[] defaults = {"Default", "Profile 2", "Profile 3"};
        profile = clampProfile(profile);
        try {
            JSONArray a = new JSONArray(
                    prefs(ctx).getString(KEY_PROFILE_NAMES, "[]"));
            if (profile < a.length()) {
                String n = a.getString(profile);
                if (!n.isEmpty()) return n;
            }
        } catch (JSONException ignored) {
        }
        return defaults[profile];
    }

    public static void setProfileName(Context ctx, int profile, String name) {
        profile = clampProfile(profile);
        try {
            JSONArray a;
            try {
                a = new JSONArray(prefs(ctx).getString(KEY_PROFILE_NAMES, "[]"));
            } catch (JSONException e) {
                a = new JSONArray();
            }
            while (a.length() <= profile) a.put("");
            a.put(profile, name);
            prefs(ctx).edit().putString(KEY_PROFILE_NAMES, a.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static List<Shortcut> load(Context ctx) {
        return load(ctx, activeProfile(ctx));
    }

    public static List<Shortcut> load(Context ctx, int profile) {
        List<Shortcut> out = new ArrayList<>();
        SharedPreferences p = prefs(ctx);
        String raw = p.getString(profileKey(profile), null);
        if (raw == null && profile == 0) {
            // Migrate v1.4 flat storage.
            raw = p.getString(KEY_SHORTCUTS, "[]");
            p.edit().putString(profileKey(0), raw).remove(KEY_SHORTCUTS).apply();
        }
        if (raw == null) raw = "[]";
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length() && i < MAX; i++) {
                out.add(Shortcut.fromJson(a.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static List<Shortcut> loadPadded(Context ctx, int profile) {
        List<Shortcut> list = load(ctx, profile);
        while (list.size() < MAX) {
            list.add(Shortcut.createEmpty());
        }
        return list;
    }

    public static void save(Context ctx, List<Shortcut> list) {
        save(ctx, activeProfile(ctx), list);
    }

    public static void save(Context ctx, int profile, List<Shortcut> list) {
        JSONArray a = new JSONArray();
        for (Shortcut s : list) {
            try {
                a.put(s.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(ctx).edit().putString(profileKey(profile), a.toString()).apply();
    }

    public static boolean isDockOn(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DOCK_ON, false);
    }

    public static void setDockOn(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_DOCK_ON, on).apply();
    }

    public static boolean isRoundIcons(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ROUND, false);
    }

    public static void setRoundIcons(Context ctx, boolean round) {
        prefs(ctx).edit().putBoolean(KEY_ROUND, round).apply();
    }

    /** Glass card opacity 0-255, default 204 (80%). */
    public static int glassOpacity(Context ctx) {
        return prefs(ctx).getInt(KEY_OPACITY, 204);
    }

    public static void setGlassOpacity(Context ctx, int opacity) {
        prefs(ctx).edit().putInt(KEY_OPACITY, opacity).apply();
    }

    public static String dockMemo(Context ctx) {
        return prefs(ctx).getString(KEY_MEMO, "");
    }

    public static void setDockMemo(Context ctx, String memo) {
        prefs(ctx).edit().putString(KEY_MEMO, memo == null ? "" : memo).apply();
    }

    /** Per-widget start-slot offset for small widgets (0-based). */
    public static int widgetStartSlot(Context ctx, int appWidgetId) {
        return prefs(ctx).getInt("widget_start_" + appWidgetId, 0);
    }

    public static void setWidgetStartSlot(Context ctx, int appWidgetId, int start) {
        prefs(ctx).edit().putInt("widget_start_" + appWidgetId, start).apply();
    }

    public static void clearWidgetStartSlot(Context ctx, int appWidgetId) {
        prefs(ctx).edit().remove("widget_start_" + appWidgetId).apply();
    }

    public static int islandIconsCount(Context ctx) {
        return prefs(ctx).getInt(KEY_ISLAND_ICONS, 4);
    }

    public static void setIslandIconsCount(Context ctx, int count) {
        prefs(ctx).edit().putInt(KEY_ISLAND_ICONS, Math.max(2, Math.min(6, count))).apply();
    }

    public static boolean isIslandBattery(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ISLAND_BATTERY, true);
    }

    public static void setIslandBattery(Context ctx, boolean show) {
        prefs(ctx).edit().putBoolean(KEY_ISLAND_BATTERY, show).apply();
    }

    public static boolean isHomeClock(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_CLOCK, true);
    }

    public static void setHomeClock(Context ctx, boolean show) {
        prefs(ctx).edit().putBoolean(KEY_HOME_CLOCK, show).apply();
    }

    public static boolean isHomeBattery(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_BATTERY, true);
    }

    public static void setHomeBattery(Context ctx, boolean show) {
        prefs(ctx).edit().putBoolean(KEY_HOME_BATTERY, show).apply();
    }

    public static boolean isHomeProfiles(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_PROFILES, true);
    }

    public static void setHomeProfiles(Context ctx, boolean show) {
        prefs(ctx).edit().putBoolean(KEY_HOME_PROFILES, show).apply();
    }

    public static int homeGridColumns(Context ctx) {
        return prefs(ctx).getInt(KEY_HOME_COLUMNS, 4);
    }

    public static void setHomeGridColumns(Context ctx, int cols) {
        prefs(ctx).edit().putInt(KEY_HOME_COLUMNS, Math.max(3, Math.min(5, cols))).apply();
    }

    public static boolean isHomeHideLabels(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_HIDE_LABELS, false);
    }

    public static void setHomeHideLabels(Context ctx, boolean hide) {
        prefs(ctx).edit().putBoolean(KEY_HOME_HIDE_LABELS, hide).apply();
    }

    public static List<Integer> homeWidgets(Context ctx, int profile) {
        List<Integer> list = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_HOME_WIDGETS + profile, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                list.add(a.getInt(i));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void setHomeWidgets(Context ctx, int profile, List<Integer> widgetIds) {
        JSONArray a = new JSONArray();
        if (widgetIds != null) {
            for (int id : widgetIds) a.put(id);
        }
        prefs(ctx).edit().putString(KEY_HOME_WIDGETS + profile, a.toString()).apply();
    }

    public static void addHomeWidget(Context ctx, int profile, int widgetId) {
        List<Integer> current = homeWidgets(ctx, profile);
        if (!current.contains(widgetId)) {
            current.add(widgetId);
            setHomeWidgets(ctx, profile, current);
        }
    }

    public static void removeHomeWidget(Context ctx, int profile, int widgetId) {
        List<Integer> current = homeWidgets(ctx, profile);
        if (current.remove(Integer.valueOf(widgetId))) {
            setHomeWidgets(ctx, profile, current);
        }
    }

    public static int widgetHeight(Context ctx, int widgetId) {
        return prefs(ctx).getInt(KEY_WIDGET_HEIGHT + widgetId, -2); // -2 is ViewGroup.LayoutParams.WRAP_CONTENT
    }

    public static void setWidgetHeight(Context ctx, int widgetId, int heightDp) {
        prefs(ctx).edit().putInt(KEY_WIDGET_HEIGHT + widgetId, heightDp).apply();
    }

    public static boolean isDoubleTapLock(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DOUBLE_TAP_LOCK, false);
    }

    public static void setDoubleTapLock(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_DOUBLE_TAP_LOCK, enabled).apply();
    }

    public static boolean isMonochromeIcons(Context ctx) {
        return prefs(ctx).getBoolean(KEY_MONOCHROME_ICONS, false);
    }

    public static void setMonochromeIcons(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_MONOCHROME_ICONS, enabled).apply();
    }

    public static boolean isSearchCalculator(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SEARCH_CALCULATOR, true);
    }

    public static void setSearchCalculator(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SEARCH_CALCULATOR, enabled).apply();
    }

    public static boolean isIslandSeparate(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ISLAND_SEPARATE_APPS, true);
    }

    public static void setIslandSeparate(Context ctx, boolean separate) {
        prefs(ctx).edit().putBoolean(KEY_ISLAND_SEPARATE_APPS, separate).apply();
    }

    public static List<Shortcut> loadIsland(Context ctx) {
        if (!isIslandSeparate(ctx)) {
            return load(ctx);
        }
        List<Shortcut> out = new ArrayList<>();
        SharedPreferences p = prefs(ctx);
        String raw = p.getString(KEY_ISLAND_SHORTCUTS, null);
        if (raw == null) {
            List<Shortcut> prof0 = load(ctx, 0);
            for (int i = 0; i < Math.min(6, prof0.size()); i++) {
                if (!prof0.get(i).isEmpty()) out.add(prof0.get(i));
            }
            saveIsland(ctx, out);
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length() && i < MAX; i++) {
                out.add(Shortcut.fromJson(a.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static List<Shortcut> loadIslandPadded(Context ctx) {
        List<Shortcut> list = loadIsland(ctx);
        while (list.size() < MAX) {
            list.add(Shortcut.createEmpty());
        }
        return list;
    }

    public static void saveIsland(Context ctx, List<Shortcut> list) {
        JSONArray a = new JSONArray();
        for (Shortcut s : list) {
            try {
                a.put(s.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs(ctx).edit().putString(KEY_ISLAND_SHORTCUTS, a.toString()).apply();
    }

    public static boolean isHomeCleanMode(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_CLEAN_MODE, false);
    }

    public static void setHomeCleanMode(Context ctx, boolean clean) {
        prefs(ctx).edit().putBoolean(KEY_HOME_CLEAN_MODE, clean).apply();
    }

    public static boolean isHomeAlignBottom(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HOME_ALIGN_BOTTOM, false);
    }

    public static void setHomeAlignBottom(Context ctx, boolean bottom) {
        prefs(ctx).edit().putBoolean(KEY_HOME_ALIGN_BOTTOM, bottom).apply();
    }

    public static int homeIconSize(Context ctx) {
        return prefs(ctx).getInt(KEY_HOME_ICON_SIZE, 56);
    }

    public static void setHomeIconSize(Context ctx, int sizeDp) {
        prefs(ctx).edit().putInt(KEY_HOME_ICON_SIZE, sizeDp).apply();
    }

    public static boolean isDrawerAutoKeyboard(Context ctx) {
        return prefs(ctx).getBoolean(KEY_DRAWER_AUTO_KEYBOARD, true);
    }

    public static void setDrawerAutoKeyboard(Context ctx, boolean auto) {
        prefs(ctx).edit().putBoolean(KEY_DRAWER_AUTO_KEYBOARD, auto).apply();
    }

    public static java.util.Set<String> getLockedApps(Context ctx) {
        return new java.util.HashSet<>(prefs(ctx).getStringSet(KEY_LOCKED_APPS, new java.util.HashSet<>()));
    }

    public static boolean isAppLocked(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return getLockedApps(ctx).contains(pkg);
    }

    public static void setAppLocked(Context ctx, String pkg, boolean locked) {
        if (pkg == null || pkg.isEmpty()) return;
        java.util.Set<String> set = getLockedApps(ctx);
        if (locked) {
            set.add(pkg);
        } else {
            set.remove(pkg);
        }
        prefs(ctx).edit().putStringSet(KEY_LOCKED_APPS, set).apply();
    }

    public static boolean isShowEmptyGridSlots(Context ctx) {
        return prefs(ctx).getBoolean("home_show_empty_slots", false);
    }

    public static void setShowEmptyGridSlots(Context ctx, boolean show) {
        prefs(ctx).edit().putBoolean("home_show_empty_slots", show).apply();
    }

    /** Places a shortcut into the first empty slot if available, or appends up to MAX. */
    public static boolean addOrPlaceShortcut(List<Shortcut> list, Shortcut item) {
        if (list == null || item == null) return false;
        for (int i = 0; i < list.size(); i++) {
            Shortcut existing = list.get(i);
            if (existing != null && existing.isEmpty()) {
                list.set(i, item);
                return true;
            }
        }
        if (list.size() < MAX) {
            list.add(item);
            return true;
        }
        return false;
    }

    /**
     * Unified custom app label lookup across both "drawer_prefs" and "dock" preferences,
     * keyed by package name and clone/work profileSerial.
     */
    public static String getCustomAppLabel(Context ctx, String pkg, long profileSerial) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        String key = AppPickerActivity.getCustomLabelKey(pkg, profileSerial);
        SharedPreferences drawerPrefs = ctx.getSharedPreferences("drawer_prefs", Context.MODE_PRIVATE);
        String v = drawerPrefs.getString(key, null);
        if (v != null && !v.trim().isEmpty()) return v.trim();
        String vDock = prefs(ctx).getString(key, null);
        if (vDock != null && !vDock.trim().isEmpty()) return vDock.trim();
        return null;
    }

    /**
     * Saves custom app label to both "drawer_prefs" and "dock" and updates all matching
     * shortcuts across all profiles, folders, and Dynamic Island.
     */
    public static void setCustomAppLabel(Context ctx, String pkg, long profileSerial, String newLabel) {
        if (ctx == null || pkg == null || pkg.isEmpty() || newLabel == null) return;
        String trimmed = newLabel.trim();
        if (trimmed.isEmpty()) return;
        String key = AppPickerActivity.getCustomLabelKey(pkg, profileSerial);
        ctx.getSharedPreferences("drawer_prefs", Context.MODE_PRIVATE)
                .edit().putString(key, trimmed).apply();
        prefs(ctx).edit().putString(key, trimmed).apply();

        for (int profIdx = 0; profIdx < PROFILES; profIdx++) {
            List<Shortcut> pShortcuts = load(ctx, profIdx);
            boolean changed = false;
            for (Shortcut s : pShortcuts) {
                if (s.isApp() && pkg.equals(s.pkg) && s.profileSerial == profileSerial) {
                    s.label = trimmed;
                    changed = true;
                }
                if (s.isFolder() && s.folderItems != null) {
                    for (Shortcut fi : s.folderItems) {
                        if (fi.isApp() && pkg.equals(fi.pkg) && fi.profileSerial == profileSerial) {
                            fi.label = trimmed;
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                save(ctx, profIdx, pShortcuts);
            }
        }

        if (isIslandSeparate(ctx)) {
            List<Shortcut> island = loadIsland(ctx);
            boolean islChanged = false;
            for (Shortcut s : island) {
                if (s.isApp() && pkg.equals(s.pkg) && s.profileSerial == profileSerial) {
                    s.label = trimmed;
                    islChanged = true;
                }
            }
            if (islChanged) {
                saveIsland(ctx, island);
            }
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
