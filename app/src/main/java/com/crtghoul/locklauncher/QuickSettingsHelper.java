package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class QuickSettingsHelper {

    public static class SettingMatch {
        public final String title;
        public final String icon;
        public final Intent intent;

        public SettingMatch(String title, String icon, Intent intent) {
            this.title = title;
            this.icon = icon;
            this.intent = intent;
        }
    }

    public static SettingMatch matchSetting(Context ctx, String query) {
        if (query == null) return null;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return null;

        if (q.equals("wifi") || q.equals("wi-fi") || q.equals("internet") || q.equals("wlan")) {
            return new SettingMatch("Wi-Fi Settings", "📶", new Intent(Settings.ACTION_WIFI_SETTINGS));
        } else if (q.equals("bluetooth") || q.equals("bt")) {
            return new SettingMatch("Bluetooth Settings", "🔷", new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } else if (q.equals("battery") || q.equals("power") || q.equals("battery saver")) {
            return new SettingMatch("Battery Settings", "🔋", new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
        } else if (q.equals("display") || q.equals("brightness") || q.equals("screen") || q.equals("dark mode")) {
            return new SettingMatch("Display Settings", "🔆", new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } else if (q.equals("sound") || q.equals("volume") || q.equals("audio") || q.equals("vibrate") || q.equals("ringtone")) {
            return new SettingMatch("Sound & Vibration", "🔊", new Intent(Settings.ACTION_SOUND_SETTINGS));
        } else if (q.equals("hotspot") || q.equals("tether") || q.equals("tethering")) {
            return new SettingMatch("Tethering & Hotspot", "📡", new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } else if (q.equals("apps") || q.equals("applications") || q.equals("app manager")) {
            return new SettingMatch("Manage Applications", "📱", new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        } else if (q.equals("storage") || q.equals("memory") || q.equals("disk")) {
            return new SettingMatch("Storage Settings", "💾", new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        } else if (q.equals("location") || q.equals("gps")) {
            return new SettingMatch("Location & GPS", "📍", new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else if (q.equals("nfc")) {
            return new SettingMatch("NFC Settings", "💳", new Intent(Settings.ACTION_NFC_SETTINGS));
        } else if (q.equals("vpn")) {
            return new SettingMatch("VPN Settings", "🛡️", new Intent(Settings.ACTION_VPN_SETTINGS));
        } else if (q.equals("airplane") || q.equals("flight mode")) {
            return new SettingMatch("Airplane Mode", "✈️", new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        } else if (q.equals("developer") || q.equals("dev options") || q.equals("usb debugging")) {
            return new SettingMatch("Developer Options", "🛠️", new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } else if (q.equals("date") || q.equals("time") || q.equals("clock")) {
            return new SettingMatch("Date & Time Settings", "🕒", new Intent(Settings.ACTION_DATE_SETTINGS));
        } else if (q.equals("wallpaper") || q.equals("wallpapers")) {
            return new SettingMatch("Wallpaper Picker", "🖼️", new Intent(Intent.ACTION_SET_WALLPAPER));
        }

        return null;
    }
}
