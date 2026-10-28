package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.pm.PackageManager;

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
import java.util.List;

/**
 * Offline JSON backup/restore via the Storage Access Framework.
 * One file holds all profiles, settings, and base64 custom icons.
 * Zero permissions, zero network.
 */
public class BackupManager {
    private static final int SCHEMA = 1;

    public static class ImportResult {
        public int slotsRestored;
        public int missingApps;
    }

    public static boolean exportTo(Context ctx, OutputStream out) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
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
            if (root.optInt("schema", 0) != SCHEMA) return null;

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
            }

            PackageManager pm = ctx.getPackageManager();
            JSONArray profiles = root.optJSONArray("profiles");
            if (profiles != null) {
                for (int p = 0; p < profiles.length() && p < ShortcutStore.PROFILES; p++) {
                    JSONObject prof = profiles.getJSONObject(p);
                    ShortcutStore.setProfileName(ctx, p,
                            prof.optString("name", "Profile " + (p + 1)));
                    List<Shortcut> slots = new ArrayList<>();
                    JSONArray arr = prof.optJSONArray("slots");
                    if (arr != null) {
                        for (int i = 0; i < arr.length() && i < ShortcutStore.MAX; i++) {
                            Shortcut s = Shortcut.fromJson(arr.getJSONObject(i));
                            if (s.isApp()) {
                                try {
                                    pm.getApplicationInfo(s.pkg, 0);
                                } catch (Exception e) {
                                    res.missingApps++;
                                }
                            }
                            slots.add(s);
                            res.slotsRestored++;
                        }
                    }
                    ShortcutStore.save(ctx, p, slots);
                }
            }

            JSONObject icons = root.optJSONObject("icons");
            if (icons != null) {
                java.util.Iterator<String> keys = icons.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    byte[] png = android.util.Base64.decode(
                            icons.getString(id), android.util.Base64.NO_WRAP);
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
