package com.crtghoul.locklauncher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Icon bitmaps, launch intents, and card styling for shortcuts. */
public class IconUtil {
    private static final int SIZE = 144;

    /** Letter-tile colors offered in the icon picker. */
    public static final int[] LETTER_COLORS = {
            0xFF7C5CFF, 0xFF3B82F6, 0xFF06B6D4, 0xFF10B981,
            0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF6B7280
    };
    public static final String[] LETTER_COLOR_NAMES = {
            "Purple", "Blue", "Cyan", "Green", "Amber", "Red", "Pink", "Gray"
    };

    // ---- custom icon files (UUID-keyed, survive reorder + profiles) ----

    public static File customIconFile(Context ctx, String shortcutId) {
        return new File(ctx.getFilesDir(), "icon_" + shortcutId + ".png");
    }

    public static boolean saveCustomIcon(Context ctx, String shortcutId, Bitmap bmp) {
        try (FileOutputStream out = new FileOutputStream(customIconFile(ctx, shortcutId))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void deleteCustomIcon(Context ctx, String shortcutId) {
        try {
            customIconFile(ctx, shortcutId).delete();
        } catch (Exception ignored) {
        }
    }

    // ---- icon rendering ----

    public static Bitmap iconFor(Context ctx, Shortcut s) {
        if (s.isFolder()) {
            return finishIcon(ctx, folderBitmap(ctx, s));
        }
        Bitmap custom = loadCustomIcon(ctx, s.id);
        if (custom != null) {
            return finishIcon(ctx, custom);
        }
        Bitmap b;
        if (Shortcut.ICON_EMOJI.equals(s.iconMode)) {
            b = emojiBitmap(
                    s.iconEmoji == null || s.iconEmoji.isEmpty() ? "\uD83D\uDE80" : s.iconEmoji,
                    s.iconColor);
        } else {
            boolean wantLetter = Shortcut.ICON_LETTER.equals(s.iconMode);
            if (s.isApp() && !wantLetter) {
                if (s.profileSerial > 0) {
                    try {
                        LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                        UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                        if (la != null && um != null) {
                            UserHandle uh = um.getUserForSerialNumber(s.profileSerial);
                            if (uh != null) {
                                List<LauncherActivityInfo> acts = la.getActivityList(s.pkg, uh);
                                if (!acts.isEmpty()) {
                                    b = drawableToBitmap(acts.get(0).getBadgedIcon(0));
                                    return finishIcon(ctx, b);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                try {
                    PackageManager pm = ctx.getPackageManager();
                    Drawable d = pm.getApplicationIcon(s.pkg);
                    b = drawableToBitmap(d);
                } catch (Exception e) {
                    b = letterIcon("?", s.iconColor);
                }
            } else {
                String label = (s.label == null || s.label.isEmpty()) ? "?" : s.label;
                b = letterIcon(label, s.iconColor);
            }
        }
        return finishIcon(ctx, b);
    }

    public static Bitmap folderBitmap(Context ctx, Shortcut folder) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xEE1B1F31);
        float rad = SIZE * 0.28f;
        canvas.drawRoundRect(new RectF(0, 0, SIZE, SIZE), rad, rad, bg);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(SIZE * 0.04f);
        stroke.setColor(0x44FFFFFF);
        canvas.drawRoundRect(new RectF(2, 2, SIZE - 2, SIZE - 2), rad, rad, stroke);

        List<Shortcut> items = folder.folderItems;
        if (items != null && !items.isEmpty()) {
            int miniSize = (int) (SIZE * 0.38f);
            int pad = (int) (SIZE * 0.09f);
            int[][] pos = {
                    {pad, pad},
                    {SIZE - pad - miniSize, pad},
                    {pad, SIZE - pad - miniSize},
                    {SIZE - pad - miniSize, SIZE - pad - miniSize}
            };
            int n = Math.min(items.size(), 4);
            for (int i = 0; i < n; i++) {
                Shortcut child = items.get(i);
                Bitmap childBmp = iconForRaw(ctx, child);
                if (childBmp != null) {
                    canvas.drawBitmap(childBmp, null, new RectF(pos[i][0], pos[i][1], pos[i][0] + miniSize, pos[i][1] + miniSize), new Paint(Paint.FILTER_BITMAP_FLAG));
                }
            }
        }
        return out;
    }

    private static Bitmap iconForRaw(Context ctx, Shortcut s) {
        try {
            if (s.profileSerial > 0) {
                LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                if (la != null && um != null) {
                    UserHandle uh = um.getUserForSerialNumber(s.profileSerial);
                    if (uh != null) {
                        List<LauncherActivityInfo> acts = la.getActivityList(s.pkg, uh);
                        if (!acts.isEmpty()) return drawableToBitmap(acts.get(0).getBadgedIcon(0));
                    }
                }
            }
            if (s.isApp()) {
                return drawableToBitmap(ctx.getPackageManager().getApplicationIcon(s.pkg));
            }
        } catch (Exception ignored) {
        }
        return letterIcon(s.label == null || s.label.isEmpty() ? "?" : s.label, s.iconColor);
    }

    private static Bitmap loadCustomIcon(Context ctx, String shortcutId) {
        try {
            File f = customIconFile(ctx, shortcutId);
            if (!f.exists()) return null;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return null;
            return Bitmap.createScaledBitmap(b, SIZE, SIZE, true);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap finishIcon(Context ctx, Bitmap b) {
        if (ShortcutStore.isRoundIcons(ctx)) {
            b = circleCrop(b);
        }
        if (ShortcutStore.isMonochromeIcons(ctx)) {
            b = toMonochrome(b);
        }
        return b;
    }

    public static Bitmap toMonochrome(Bitmap src) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(c);
        return b;
    }

    /** Circle-crops a bitmap (the iOS "round background" look). */
    private static Bitmap circleCrop(Bitmap src) {
        Bitmap scaled = Bitmap.createScaledBitmap(src, SIZE, SIZE, true);
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SRC_IN));
        c.drawBitmap(scaled, 0, 0, p);
        return out;
    }

    /** Generated tile with the first letter of the label. */
    public static Bitmap letterIcon(String label, int color) {
        String str = (label == null || label.trim().isEmpty()) ? "W" : label.trim();
        String letter = str.substring(0, 1).toUpperCase();
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (color == -1) {
            int hue = Math.abs(str.hashCode()) % 360;
            bg.setColor(Color.HSVToColor(new float[]{hue, 0.55f, 0.45f}));
        } else {
            bg.setColor(color);
        }
        c.drawRoundRect(new RectF(0, 0, SIZE, SIZE), SIZE * 0.28f, SIZE * 0.28f, bg);
        drawCentered(c, letter, Color.WHITE, SIZE * 0.52f);
        return b;
    }

    public static Bitmap letterIcon(String label) {
        return letterIcon(label, -1);
    }

    /** Emoji tile; color -1 = transparent background. */
    public static Bitmap emojiBitmap(String emoji, int color) {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (color != -1) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(color);
            c.drawRoundRect(new RectF(0, 0, SIZE, SIZE), SIZE * 0.28f, SIZE * 0.28f, bg);
        }
        drawCentered(c, emoji, Color.WHITE, SIZE * 0.62f);
        return b;
    }

    private static void drawCentered(Canvas c, String text, int color, float textSize) {
        Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        fg.setColor(color);
        fg.setTextSize(textSize);
        fg.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = fg.getFontMetrics();
        float y = SIZE / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(text, SIZE / 2f, y, fg);
    }

    /** Center-crop square + scale, for gallery-picked custom icons. */
    public static Bitmap squareCrop(Bitmap src, int size) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2, y = (h - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);
        return Bitmap.createScaledBitmap(cropped, size, size, true);
    }

    // ---- intents ----

    /** Settings panel keys. */
    public static final String PANEL_WIFI = "wifi";
    public static final String PANEL_INTERNET = "internet";
    public static final String PANEL_VOLUME = "volume";
    public static final String PANEL_NFC = "nfc";
    public static final String PANEL_BLUETOOTH = "bluetooth";
    public static final String PANEL_AIRPLANE = "airplane";
    public static final String PANEL_BATTERY = "battery_saver";
    public static final String PANEL_DISPLAY = "display";
    public static final String PANEL_HOTSPOT = "hotspot";
    public static final String PANEL_ALARM = "alarm";
    public static final String PANEL_SOUND = "sound";

    /** Launch intent for a shortcut, or null if it can't be built. */
    public static Intent launchIntent(Context ctx, Shortcut s) {
        try {
            if (s.isApp()) {
                Intent i = ctx.getPackageManager().getLaunchIntentForPackage(s.pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    return i;
                }
                return null;
            }
            if (Shortcut.TYPE_TORCH.equals(s.type)
                    || Shortcut.TYPE_PROFILE.equals(s.type)) {
                return null; // handled via broadcast in pendingFor()
            }
            if (Shortcut.TYPE_CALL.equals(s.type)) {
                Intent i;
                if (ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                        == PackageManager.PERMISSION_GRANTED) {
                    i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + s.data));
                } else {
                    i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.data));
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            }
            if (Shortcut.TYPE_SMS.equals(s.type)) {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + s.data));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            }
            if (Shortcut.TYPE_PANEL.equals(s.type)) {
                Intent i = panelIntent(s.data);
                if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            }
            if (Shortcut.TYPE_DEEP.equals(s.type)) {
                Intent i = Intent.parseUri(s.data, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            }
            if (Shortcut.TYPE_URL.equals(s.type)) {
                return buildWebIntent(ctx, s.url);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds an app-style web intent:
     * 1. If an installed native app handles this link (YouTube, Spotify, WhatsApp, Reddit, Twitter, etc.),
     *    targets that native app directly so it opens inside the app.
     * 2. Otherwise opens in standalone app window (Custom Tab mode with dark toolbar, hidden URL bar,
     *    and separate task card in Recents).
     */
    public static Intent buildWebIntent(Context ctx, String rawUrl) {
        try {
            String url = rawUrl == null ? "" : rawUrl.trim();
            if (url.isEmpty()) return null;
            if (!url.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                url = "https://" + url;
            }
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();

            // Custom schemes (e.g. whatsapp://, spotify://) open directly
            if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return intent;
            }

            // Check if a dedicated non-browser app is installed that handles this URL
            try {
                PackageManager pm = ctx.getPackageManager();
                Intent queryIntent = new Intent(Intent.ACTION_VIEW, uri);
                queryIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                List<android.content.pm.ResolveInfo> handlers =
                        pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY);
                if (handlers != null) {
                    for (android.content.pm.ResolveInfo ri : handlers) {
                        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            String pkg = ri.activityInfo.packageName;
                            if (!isGenericBrowser(pkg)) {
                                Intent nativeApp = new Intent(Intent.ACTION_VIEW, uri);
                                nativeApp.setPackage(pkg);
                                nativeApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                return nativeApp;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            // Standalone app window / Custom Tab mode (independent task card in Recents)
            Intent appWindow = new Intent(Intent.ACTION_VIEW, uri);
            appWindow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            Bundle extras = new Bundle();
            extras.putBinder("android.support.customtabs.extra.SESSION", null);
            appWindow.putExtras(extras);
            appWindow.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF0D0F1D);
            appWindow.putExtra("android.support.customtabs.extra.ENABLE_URLBAR_HIDING", true);
            appWindow.putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1);
            appWindow.putExtra("android.support.customtabs.extra.SHARE_STATE", 2);
            appWindow.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);
            appWindow.putExtra("androidx.browser.customtabs.extra.COLOR_SCHEME", 2);
            return appWindow;
        } catch (Exception e) {
            return null;
        }
    }

    /** Pins any shortcut directly to the Android Home screen (main page) via ShortcutManager. */
    public static boolean pinShortcutToHomeScreen(Context ctx, Shortcut s) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.content.pm.ShortcutManager sm = ctx.getSystemService(android.content.pm.ShortcutManager.class);
                if (sm != null && sm.isRequestPinShortcutSupported()) {
                    Intent launch = launchIntent(ctx, s);
                    if (launch == null) return false;
                    if (launch.getAction() == null) {
                        launch.setAction(Intent.ACTION_VIEW);
                    }
                    Bitmap bmp = iconFor(ctx, s);
                    android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithBitmap(bmp);
                    String lbl = (s.label == null || s.label.trim().isEmpty()) ? "Shortcut" : s.label.trim();
                    android.content.pm.ShortcutInfo pinInfo = new android.content.pm.ShortcutInfo.Builder(ctx, "ll_pin_" + s.id)
                            .setShortLabel(lbl)
                            .setLongLabel(lbl)
                            .setIcon(icon)
                            .setIntent(launch)
                            .build();
                    return sm.requestPinShortcut(pinInfo, null);
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isGenericBrowser(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("browser") || p.contains("chrome") || p.contains("firefox")
                || p.contains("opera") || p.contains("edge") || p.contains("duckduckgo")
                || p.contains("webview") || p.contains("navigator");
    }

    /** Floating Settings.Panel on API 29+, plain settings pages below/fallback. */
    public static Intent panelIntent(String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            switch (key) {
                case PANEL_WIFI: return new Intent(Settings.Panel.ACTION_WIFI);
                case PANEL_INTERNET:
                    return new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
                case PANEL_VOLUME: return new Intent(Settings.Panel.ACTION_VOLUME);
                case PANEL_NFC: return new Intent(Settings.Panel.ACTION_NFC);
            }
        }
        switch (key) {
            case PANEL_WIFI: return new Intent(Settings.ACTION_WIFI_SETTINGS);
            case PANEL_INTERNET: return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            case PANEL_VOLUME: return new Intent(Settings.ACTION_SOUND_SETTINGS);
            case PANEL_NFC: return new Intent(Settings.ACTION_NFC_SETTINGS);
            case PANEL_BLUETOOTH: return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            case PANEL_AIRPLANE: return new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            case PANEL_BATTERY: return new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            case PANEL_DISPLAY: return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            case PANEL_HOTSPOT:
                try {
                    return new Intent("android.settings.TETHER_SETTINGS");
                } catch (Exception e) {
                    return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                }
            case PANEL_ALARM:
                try {
                    return new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
                } catch (Exception e) {
                    return new Intent(Settings.ACTION_DATE_SETTINGS);
                }
            case PANEL_SOUND: return new Intent(Settings.ACTION_SOUND_SETTINGS);
            default: return new Intent(Settings.ACTION_SETTINGS);
        }
    }

    public static final String[] PANEL_KEYS = {
            PANEL_WIFI, PANEL_INTERNET, PANEL_BLUETOOTH, PANEL_AIRPLANE,
            PANEL_VOLUME, PANEL_SOUND, PANEL_DISPLAY, PANEL_BATTERY,
            PANEL_HOTSPOT, PANEL_NFC, PANEL_ALARM
    };
    public static final String[] PANEL_NAMES = {
            "Wi-Fi", "Internet", "Bluetooth", "Airplane mode",
            "Volume panel", "Sound & DND", "Display & Brightness", "Battery saver",
            "Hotspot & Tethering", "NFC", "Clock & Alarms"
    };

    /** Fires a shortcut from a live View (the island overlay or home screen). True if launched. */
    public static boolean fireShortcut(Context ctx, Shortcut s) {
        try {
            if (s.isFolder()) {
                return false; // Folders are handled by opening dialog in HomeActivity
            }
            if (s.isApp() && s.profileSerial > 0) {
                try {
                    LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                    UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                    if (la != null && um != null) {
                        UserHandle uh = um.getUserForSerialNumber(s.profileSerial);
                        if (uh != null) {
                            List<LauncherActivityInfo> acts = la.getActivityList(s.pkg, uh);
                            if (!acts.isEmpty()) {
                                la.startMainActivity(acts.get(0).getComponentName(), uh, null, null);
                                return true;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (Shortcut.TYPE_TORCH.equals(s.type)) {
                Intent i = new Intent(ctx, TorchReceiver.class);
                i.setAction(TorchReceiver.ACTION_TOGGLE);
                ctx.sendBroadcast(i);
                return true;
            }
            if (Shortcut.TYPE_PROFILE.equals(s.type)) {
                Intent i = new Intent(ctx, ProfileSwitchReceiver.class);
                i.setAction(ProfileSwitchReceiver.ACTION_CYCLE);
                ctx.sendBroadcast(i);
                return true;
            }
            Intent launch = launchIntent(ctx, s);
            if (launch == null) return false;
            try {
                ctx.startActivity(launch);
                return true;
            } catch (Exception e) {
                // If opening with a specific package failed, fall back to generic browser view
                if (Shortcut.TYPE_URL.equals(s.type)) {
                    try {
                        Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(s.url));
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(fallback);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** PendingIntent for a dock slot: broadcast for torch/profile, activity otherwise. */
    public static PendingIntent pendingFor(Context ctx, Shortcut s, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Shortcut.TYPE_TORCH.equals(s.type)) {
            Intent i = new Intent(ctx, TorchReceiver.class);
            i.setAction(TorchReceiver.ACTION_TOGGLE);
            return PendingIntent.getBroadcast(ctx, requestCode, i, flags);
        }
        if (Shortcut.TYPE_PROFILE.equals(s.type)) {
            Intent i = new Intent(ctx, ProfileSwitchReceiver.class);
            i.setAction(ProfileSwitchReceiver.ACTION_CYCLE);
            return PendingIntent.getBroadcast(ctx, requestCode, i, flags);
        }
        Intent launch = launchIntent(ctx, s);
        if (launch == null) return null;
        return PendingIntent.getActivity(ctx, requestCode, launch, flags);
    }

    // ---- dock filling + card styling ----

    private static final int[] SLOTS = {
            R.id.slot0, R.id.slot1, R.id.slot2, R.id.slot3,
            R.id.slot4, R.id.slot5, R.id.slot6, R.id.slot7,
            R.id.slot8, R.id.slot9, R.id.slot10, R.id.slot11,
            R.id.slot12, R.id.slot13, R.id.slot14, R.id.slot15
    };
    private static final int[] ICONS = {
            R.id.slot0_icon, R.id.slot1_icon, R.id.slot2_icon, R.id.slot3_icon,
            R.id.slot4_icon, R.id.slot5_icon, R.id.slot6_icon, R.id.slot7_icon,
            R.id.slot8_icon, R.id.slot9_icon, R.id.slot10_icon, R.id.slot11_icon,
            R.id.slot12_icon, R.id.slot13_icon, R.id.slot14_icon, R.id.slot15_icon
    };
    private static final int[] LABELS = {
            R.id.slot0_label, R.id.slot1_label, R.id.slot2_label, R.id.slot3_label,
            R.id.slot4_label, R.id.slot5_label, R.id.slot6_label, R.id.slot7_label,
            R.id.slot8_label, R.id.slot9_label, R.id.slot10_label, R.id.slot11_label,
            R.id.slot12_label, R.id.slot13_label, R.id.slot14_label, R.id.slot15_label
    };

    /**
     * Fills the glass-dock slots of a RemoteViews (notification or widget).
     * pendingBase distinguishes the PendingIntent request codes per surface.
     * count = how many slots this layout has.
     */
    public static void fillDock(Context ctx, RemoteViews rv, List<Shortcut> shortcuts,
                               int pendingBase, int count) {
        for (int i = 0; i < count && i < SLOTS.length; i++) {
            if (i < shortcuts.size()) {
                Shortcut s = shortcuts.get(i);
                rv.setImageViewBitmap(ICONS[i], iconFor(ctx, s));
                rv.setTextViewText(LABELS[i], s.label);
                PendingIntent pi = pendingFor(ctx, s, pendingBase + i);
                if (pi != null) {
                    rv.setOnClickPendingIntent(SLOTS[i], pi);
                }
                rv.setViewVisibility(SLOTS[i], View.VISIBLE);
                rv.setContentDescription(SLOTS[i], s.label);
            } else {
                rv.setViewVisibility(SLOTS[i], View.INVISIBLE);
            }
        }
    }

    /** Applies the glass opacity slider to the card background ImageView. */
    public static void applyCardStyle(Context ctx, RemoteViews rv) {
        try {
            rv.setInt(R.id.card_bg, "setImageAlpha", ShortcutStore.glassOpacity(ctx));
        } catch (Exception ignored) {
        }
    }

    /** Live battery percentage and charging state (no permissions needed). */
    public static String getLiveBatteryInfo(Context ctx) {
        try {
            Intent batteryStatus = ctx.registerReceiver(null,
                    new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
                if (level >= 0 && scale > 0) {
                    int pct = (int) ((level / (float) scale) * 100);
                    return (isCharging ? "⚡ " : "🔋 ") + pct + "%";
                }
            }
        } catch (Exception ignored) {
        }
        return "🔋 100%";
    }

    /** Resolves dynamic live tokens like {battery}, {date}, {time}, {profile}. */
    public static String resolveMemoTokens(Context ctx, String memo) {
        if (memo == null || memo.isEmpty()) return "";
        String res = memo;
        if (res.contains("{battery}")) {
            res = res.replace("{battery}", getLiveBatteryInfo(ctx));
        }
        if (res.contains("{date}")) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "EEE, MMM d", java.util.Locale.getDefault());
            res = res.replace("{date}", sdf.format(new java.util.Date()));
        }
        if (res.contains("{time}")) {
            java.text.DateFormat df = android.text.format.DateFormat.getTimeFormat(ctx);
            res = res.replace("{time}", df.format(new java.util.Date()));
        }
        if (res.contains("{profile}")) {
            res = res.replace("{profile}",
                    ShortcutStore.profileName(ctx, ShortcutStore.activeProfile(ctx)));
        }
        return res;
    }

    /** Shows/hides the lock-screen memo header (expanded notification only). */
    public static void applyMemo(Context ctx, RemoteViews rv) {
        try {
            String rawMemo = ShortcutStore.dockMemo(ctx);
            if (rawMemo == null || rawMemo.trim().isEmpty()) {
                rv.setViewVisibility(R.id.dock_memo_text, View.GONE);
            } else {
                String formatted = resolveMemoTokens(ctx, rawMemo.trim());
                rv.setTextViewText(R.id.dock_memo_text, formatted);
                rv.setViewVisibility(R.id.dock_memo_text, View.VISIBLE);
            }
        } catch (Exception ignored) {
        }
    }
}
