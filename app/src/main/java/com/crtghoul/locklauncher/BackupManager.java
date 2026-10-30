package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline JSON backup/restore v2 via the Storage Access Framework.
 * One file holds all profiles, settings, web apps, icon pack selections,
 * custom labels, hidden apps, and base64 custom icons.
 * Zero permissions, zero network. Fully backward compatible with v1 backups.
 */
public class BackupManager {
    private static final int SCHEMA_V1 = 1;
    private static final int SCHEMA_V2 = 2;

    public static class ImportResult {
        public int slotsRestored;
        public int missingApps;
        /** Slots dropped because they were invalid or unresolvable (not just uninstalled). */
        public int invalidDropped;
        /** Labels/packages of dropped slots, for the restore toast. */
        public java.util.List<String> droppedLabels = new java.util.ArrayList<>();
    }

    /** Validate one imported shortcut. Returns null if it must be dropped. */
    private static String validateShortcut(Context ctx, Shortcut s, ImportResult res) {
        if (s == null) return "empty";
        if (s.isEmpty()) return null; // empty slots are kept as-is
        if (Shortcut.TYPE_APP.equals(s.type)) {
            if (s.pkg == null || s.pkg.isEmpty()) return "app with no package";
            if (!IconUtil.isAppLaunchable(ctx, s)) {
                res.missingApps++;
                return "app not installed/disabled: " + s.pkg;
            }
            return null;
        }
        if (Shortcut.TYPE_URL.equals(s.type)) {
            String u = s.url == null ? "" : s.url.trim();
            if (u.isEmpty()) return "url with no address";
            try {
                String scheme = Uri.parse(u).getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    return "url with non-http(s) scheme";
                }
            } catch (Exception e) {
                return "unparseable url";
            }
            return null;
        }
        if (Shortcut.TYPE_DEEP.equals(s.type)) {
            String d = s.data == null ? "" : s.data.trim();
            if (d.isEmpty()) return "deep link with no data";
            String low = d.toLowerCase();
            if (low.contains("component=") || low.contains(";sel")) {
                return "deep link with explicit component/selector";
            }
            if (low.startsWith("http://") || low.startsWith("https://")) {
                return null;
            }
            if (!low.startsWith("intent:")) {
                return "deep link with unsupported scheme";
            }
            try {
                Intent intent = Intent.parseUri(d, Intent.URI_INTENT_SCHEME);
                if (intent.getComponent() != null) return "deep link with explicit component";
                if (intent.getSelector() != null) return "deep link with selector";
                java.util.List<ResolveInfo> handlers = ctx.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                if (handlers == null || handlers.isEmpty()) return "deep link with no handler";
            } catch (Exception e) {
                return "unparseable deep link";
            }
            return null;
        }
        if (Shortcut.TYPE_CALL.equals(s.type) || Shortcut.TYPE_SMS.equals(s.type)) {
            // Sanitise at import so a crafted backup cannot smuggle codes/URIs into dial data.
            s.data = IconUtil.sanitizeCallData(s.data);
            return null;
        }
        if (Shortcut.TYPE_FOLDER.equals(s.type)) {
            if (s.folderItems != null) {
                java.util.List<Shortcut> kept = new java.util.ArrayList<>();
                for (Shortcut child : s.folderItems) {
                    String why = validateShortcut(ctx, child, res);
                    if (why == null) kept.add(child);
                    else { res.invalidDropped++; res.droppedLabels.add(child.label == null ? "?" : child.label); }
                }
                s.folderItems = kept;
            }
            return null;
        }
        // torch / panel / profile / other known types: kept (panel keys are
        // resolved to system settings intents at launch time, not executed).
        return null;
    }

    public static boolean exportTo(Context ctx, OutputStream out) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA_V2);
            root.put("app", "com.crtghoul.locklauncher");

            JSONObject settings = new JSONObject();
            settings.put("round_icons", ShortcutStore.isRoundIcons(ctx));
            settings.put("glass_opacity", ShortcutStore.glassOpacity(ctx));
            settings.put("dock_memo", ShortcutStore.dockMemo(ctx));
            settings.put("active_profile", ShortcutStore.activeProfile(ctx));
            settings.put("island_icons_count", ShortcutStore.islandIconsCount(ctx));
            settings.put("island_show_battery", ShortcutStore.isIslandBattery(ctx));
            settings.put("home_show_clock", ShortcutStore.isHomeClock(ctx));
            settings.put("home_show_battery", ShortcutStore.isHomeBattery(ctx));
            settings.put("home_show_profiles", ShortcutStore.isHomeProfiles(ctx));
            settings.put("home_grid_columns", ShortcutStore.homeGridColumns(ctx));
            settings.put("home_hide_labels", ShortcutStore.isHomeHideLabels(ctx));
            settings.put("home_double_tap_lock", ShortcutStore.isDoubleTapLock(ctx));
            settings.put("home_monochrome_icons", ShortcutStore.isMonochromeIcons(ctx));
            settings.put("search_calculator", ShortcutStore.isSearchCalculator(ctx));

            // Backup v2 extensions
            settings.put("active_icon_pack", IconPackManager.getActiveIconPack(ctx));
            settings.put("notification_badges_enabled", LockNotificationListenerService.isBadgesEnabled(ctx));
            settings.put("badge_style", LockNotificationListenerService.getBadgeStyle(ctx));
            settings.put("badge_size_pct", LockNotificationListenerService.getBadgeSizePct(ctx));
            settings.put("badge_color", LockNotificationListenerService.getBadgeColor(ctx));
            JSONArray disabledBadges = new JSONArray();
            for (String pkg : LockNotificationListenerService.getDisabledBadgeApps(ctx)) {
                disabledBadges.put(pkg);
            }
            settings.put("disabled_badge_apps", disabledBadges);

            // v2.20 Glass & Blur + Icon appearance + Edge sidebar
            settings.put("glass_mode", GlassThemeHelper.getGlassMode(ctx));
            settings.put("glass_sub_theme", GlassThemeHelper.getSubTheme(ctx));
            settings.put("glass_blur_level", GlassThemeHelper.getBlurLevel(ctx));
            settings.put("glass_surface_opacity_pct", GlassThemeHelper.getSurfaceOpacityPct(ctx));
            settings.put("glass_darkness_pct", GlassThemeHelper.getDarknessPct(ctx));
            settings.put("glass_theme_hue", GlassThemeHelper.getThemeHue(ctx));
            settings.put("glass_saturation_pct", GlassThemeHelper.getSaturationPct(ctx));
            settings.put("glass_wallpaper_accent", GlassThemeHelper.isWallpaperAccent(ctx));
            settings.put("glass_icon_scale_pct", GlassThemeHelper.getIconScaleInsideGlass(ctx));
            settings.put("minimal_icons", ShortcutStore.isMinimalIcons(ctx));
            settings.put("glass_icon_backgrounds", ShortcutStore.isGlassIconBackgrounds(ctx));
            settings.put("icon_shape", ShortcutStore.getIconShape(ctx));
            settings.put("edge_sidebar_on", ShortcutStore.isEdgeSidebarOn(ctx));

            SharedPreferences drawerPrefs = ctx.getSharedPreferences("drawer_prefs", Context.MODE_PRIVATE);
            Set<String> hidden = drawerPrefs.getStringSet("hidden_apps", new HashSet<>());
            JSONArray hiddenArr = new JSONArray();
            for (String h : hidden) hiddenArr.put(h);
            settings.put("hidden_apps", hiddenArr);

            JSONObject customLabels = new JSONObject();
            for (Map.Entry<String, ?> entry : drawerPrefs.getAll().entrySet()) {
                if (entry.getKey().startsWith("custom_label_") && entry.getValue() instanceof String) {
                    customLabels.put(entry.getKey(), (String) entry.getValue());
                }
            }
            settings.put("custom_labels", customLabels);

            root.put("settings", settings);

            JSONArray profiles = new JSONArray();
            for (int p = 0; p < ShortcutStore.PROFILES; p++) {
                JSONObject prof = new JSONObject();
                prof.put("name", ShortcutStore.profileName(ctx, p));
                JSONArray slots = new JSONArray();
                for (Shortcut s : ShortcutStore.load(ctx, p)) {
                    slots.put(s.toJson());
                }
                prof.put("slots", slots);
                profiles.put(prof);
            }
            root.put("profiles", profiles);

            // Custom icon PNGs, keyed by shortcut id.
            JSONObject icons = new JSONObject();
            File dir = ctx.getFilesDir();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (n.startsWith("icon_") && n.endsWith(".png")) {
                        String id = n.substring(5, n.length() - 4);
                        icons.put(id, base64Of(f));
                    }
                }
            }
            root.put("icons", icons);

            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ImportResult importFrom(Context ctx, InputStream in) {
        ImportResult res = new ImportResult();
        try {
            String json = readAll(in);
            JSONObject root = new JSONObject(json);
            int schema = root.optInt("schema", 0);
            if (schema < SCHEMA_V1 || schema > SCHEMA_V2) return null;

            JSONObject settings = root.optJSONObject("settings");
            if (settings != null) {
                ShortcutStore.setRoundIcons(ctx, settings.optBoolean("round_icons", false));
                ShortcutStore.setGlassOpacity(ctx, settings.optInt("glass_opacity", 204));
                ShortcutStore.setDockMemo(ctx, settings.optString("dock_memo", ""));
                ShortcutStore.setActiveProfile(ctx, settings.optInt("active_profile", 0));
                if (settings.has("island_icons_count")) {
                    ShortcutStore.setIslandIconsCount(ctx, settings.optInt("island_icons_count", 4));
                }
                if (settings.has("island_show_battery")) {
                    ShortcutStore.setIslandBattery(ctx, settings.optBoolean("island_show_battery", true));
                }
                if (settings.has("home_show_clock")) {
                    ShortcutStore.setHomeClock(ctx, settings.optBoolean("home_show_clock", true));
                }
                if (settings.has("home_show_battery")) {
                    ShortcutStore.setHomeBattery(ctx, settings.optBoolean("home_show_battery", true));
                }
                if (settings.has("home_show_profiles")) {
                    ShortcutStore.setHomeProfiles(ctx, settings.optBoolean("home_show_profiles", true));
                }
                if (settings.has("home_grid_columns")) {
                    ShortcutStore.setHomeGridColumns(ctx, settings.optInt("home_grid_columns", 4));
                }
                if (settings.has("home_hide_labels")) {
                    ShortcutStore.setHomeHideLabels(ctx, settings.optBoolean("home_hide_labels", false));
                }
                if (settings.has("home_double_tap_lock")) {
                    ShortcutStore.setDoubleTapLock(ctx, settings.optBoolean("home_double_tap_lock", false));
                }
                if (settings.has("home_monochrome_icons")) {
                    ShortcutStore.setMonochromeIcons(ctx, settings.optBoolean("home_monochrome_icons", false));
                }
                if (settings.has("search_calculator")) {
                    ShortcutStore.setSearchCalculator(ctx, settings.optBoolean("search_calculator", true));
                }

                // V2 settings restore
                if (settings.has("active_icon_pack")) {
                    IconPackManager.setActiveIconPack(ctx, settings.optString("active_icon_pack", ""));
                }
                if (settings.has("notification_badges_enabled")) {
                    LockNotificationListenerService.setBadgesEnabled(ctx, settings.optBoolean("notification_badges_enabled", true));
                }
                if (settings.has("badge_style")) {
                    LockNotificationListenerService.setBadgeStyle(ctx, settings.optString("badge_style", LockNotificationListenerService.STYLE_NUMBER));
                }
                if (settings.has("badge_size_pct")) {
                    LockNotificationListenerService.setBadgeSizePct(ctx, settings.optInt("badge_size_pct", 100));
                }
                if (settings.has("badge_color")) {
                    LockNotificationListenerService.setBadgeColor(ctx, settings.optInt("badge_color", 0xFF10B981));
                }
                if (settings.has("disabled_badge_apps")) {
                    JSONArray disArr = settings.optJSONArray("disabled_badge_apps");
                    if (disArr != null) {
                        for (int i = 0; i < disArr.length(); i++) {
                            String pkg = disArr.optString(i, "");
                            if (!pkg.isEmpty()) {
                                LockNotificationListenerService.setAppBadgeAllowed(ctx, pkg, false);
                            }
                        }
                    }
                }
                if (settings.has("glass_mode")) {
                    GlassThemeHelper.setGlassMode(ctx, settings.optString("glass_mode", GlassThemeHelper.MODE_FROSTED));
                }
                if (settings.has("glass_sub_theme")) {
                    GlassThemeHelper.setSubTheme(ctx, settings.optString("glass_sub_theme", GlassThemeHelper.SUB_DARK));
                }
                if (settings.has("glass_blur_level")) {
                    GlassThemeHelper.setBlurLevel(ctx, settings.optInt("glass_blur_level", 6));
                }
                if (settings.has("glass_surface_opacity_pct")) {
                    GlassThemeHelper.setSurfaceOpacityPct(ctx, settings.optInt("glass_surface_opacity_pct", 18));
                }
                if (settings.has("glass_darkness_pct")) {
                    GlassThemeHelper.setDarknessPct(ctx, settings.optInt("glass_darkness_pct", 50));
                }
                if (settings.has("glass_theme_hue")) {
                    GlassThemeHelper.setThemeHue(ctx, settings.optInt("glass_theme_hue", 200));
                }
                if (settings.has("glass_saturation_pct")) {
                    GlassThemeHelper.setSaturationPct(ctx, settings.optInt("glass_saturation_pct", 50));
                }
                if (settings.has("glass_wallpaper_accent")) {
                    GlassThemeHelper.setWallpaperAccent(ctx, settings.optBoolean("glass_wallpaper_accent", true));
                }
                if (settings.has("glass_icon_scale_pct")) {
                    GlassThemeHelper.setIconScaleInsideGlass(ctx, settings.optInt("glass_icon_scale_pct", 100));
                }
                if (settings.has("minimal_icons")) {
                    ShortcutStore.setMinimalIcons(ctx, settings.optBoolean("minimal_icons", false));
                }
                if (settings.has("glass_icon_backgrounds")) {
                    ShortcutStore.setGlassIconBackgrounds(ctx, settings.optBoolean("glass_icon_backgrounds", false));
                }
                if (settings.has("icon_shape")) {
                    ShortcutStore.setIconShape(ctx, settings.optString("icon_shape", ShortcutStore.SHAPE_SYSTEM));
                }
                if (settings.has("edge_sidebar_on")) {
                    ShortcutStore.setEdgeSidebarOn(ctx, settings.optBoolean("edge_sidebar_on", false));
                }

                SharedPreferences drawerPrefs = ctx.getSharedPreferences("drawer_prefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor dEdit = drawerPrefs.edit();
                if (settings.has("hidden_apps")) {
                    JSONArray hiddenArr = settings.optJSONArray("hidden_apps");
                    if (hiddenArr != null) {
                        Set<String> hiddenSet = new HashSet<>();
                        for (int i = 0; i < hiddenArr.length(); i++) {
                            hiddenSet.add(hiddenArr.getString(i));
                        }
                        dEdit.putStringSet("hidden_apps", hiddenSet);
                    }
                }
                if (settings.has("custom_labels")) {
                    JSONObject clObj = settings.optJSONObject("custom_labels");
                    if (clObj != null) {
                        Iterator<String> it = clObj.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            dEdit.putString(k, clObj.optString(k));
                        }
                    }
                }
                dEdit.apply();
            }

            // P0-2: validate every profile first, then save. Nothing is
            // kept that is missing, unresolvable, or carries a disallowed
            // scheme/component, and dropped slots are reported, not silently kept.
            JSONArray profiles = root.optJSONArray("profiles");
            java.util.List<java.util.List<Shortcut>> validated = new java.util.ArrayList<>();
            java.util.List<String> validatedNames = new java.util.ArrayList<>();
            if (profiles != null) {
                for (int p = 0; p < profiles.length() && p < ShortcutStore.PROFILES; p++) {
                    JSONObject prof = profiles.getJSONObject(p);
                    validatedNames.add(prof.optString("name", "Profile " + (p + 1)));
                    List<Shortcut> slots = new ArrayList<>();
                    JSONArray arr = prof.optJSONArray("slots");
                    if (arr != null) {
                        for (int i = 0; i < arr.length() && i < ShortcutStore.MAX; i++) {
                            Shortcut s = Shortcut.fromJson(arr.getJSONObject(i));
                            String why = validateShortcut(ctx, s, res);
                            if (why != null) {
                                res.invalidDropped++;
                                res.droppedLabels.add(s.label == null || s.label.isEmpty() ? (s.pkg == null ? "?" : s.pkg) : s.label);
                                continue;
                            }
                            slots.add(s);
                            res.slotsRestored++;
                        }
                    }
                    validated.add(slots);
                }
            }
            for (int p = 0; p < validated.size(); p++) {
                ShortcutStore.setProfileName(ctx, p, validatedNames.get(p));
                ShortcutStore.save(ctx, p, validated.get(p));
            }

            JSONObject icons = root.optJSONObject("icons");
            if (icons != null) {
                Iterator<String> keys = icons.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    // P0-2: icon ids become file-name fragments. Anything
                    // outside [A-Za-z0-9_-] (including ../ and backslashes)
                    // is dropped rather than written.
                    if (!IconUtil.isValidIconId(id)) continue;
                    String b64 = icons.getString(id);
                    if (b64 == null || b64.length() > 2000000) continue;
                    byte[] png = android.util.Base64.decode(
                            b64, android.util.Base64.NO_WRAP);
                    try (FileOutputStream fos = new FileOutputStream(
                            IconUtil.customIconFile(ctx, id))) {
                        fos.write(png);
                    } catch (Exception ignored) {
                    }
                }
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    private static String base64Of(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            return android.util.Base64.encodeToString(bos.toByteArray(),
                    android.util.Base64.NO_WRAP);
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
