package com.crtghoul.locklauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance, offline Icon Pack engine compatible with all standard
 * Nova / Apex / ADW / Lawnchair icon packs from Google Play and F-Droid.
 *
 * Reads appfilter.xml, maps ComponentNames to drawables, caches drawables,
 * and seamlessly replaces standard launcher icons.
 */
public class IconPackManager {
    private static final String TAG = "IconPackManager";
    public static final String PREF_ICON_PACK = "active_icon_pack";
    public static final String PACK_SYSTEM_DEFAULT = "";
    public static final String PACK_MATERIAL_YOU = "monochrome_material_you";

    private static final String[] ICON_PACK_ACTIONS = new String[]{
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "com.gau.go.launcherex.theme"
    };

    public static class IconPackInfo {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        public IconPackInfo(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private static String sCurrentPack = null;
    private static Resources sPackResources = null;
    private static final Map<String, String> sComponentMap = new HashMap<>();
    private static final Map<String, Bitmap> sBitmapCache = new HashMap<>();

    public static synchronized String getActiveIconPack(Context ctx) {
        return ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .getString(PREF_ICON_PACK, PACK_SYSTEM_DEFAULT);
    }

    public static synchronized void setActiveIconPack(Context ctx, String packPackage) {
        // P1-11: a restored pack name is untrusted. Only accept it if it
        // is the built-in default/material-you value or an actually
        // installed icon pack; anything else falls back to system default.
        if (packPackage != null && !packPackage.isEmpty() && !packPackage.equals(PACK_MATERIAL_YOU)) {
            boolean installed = false;
            try {
                for (IconPackInfo info : getInstalledIconPacks(ctx)) {
                    if (packPackage.equals(info.packageName)) { installed = true; break; }
                }
            } catch (Exception ignored) {
            }
            if (!installed) packPackage = PACK_SYSTEM_DEFAULT;
        }
        ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_ICON_PACK, packPackage == null ? PACK_SYSTEM_DEFAULT : packPackage)
                .apply();
        clearCache();
        loadPack(ctx, packPackage);
    }

    public static synchronized void clearCache() {
        sCurrentPack = null;
        sPackResources = null;
        sComponentMap.clear();
        sBitmapCache.clear();
    }

    /**
     * Finds all installed icon packs on the device.
     */
    public static List<IconPackInfo> getInstalledIconPacks(Context ctx) {
        List<IconPackInfo> list = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        Map<String, IconPackInfo> found = new HashMap<>();

        for (String action : ICON_PACK_ACTIONS) {
            Intent intent = new Intent(action);
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo ri : activities) {
                String pkg = ri.activityInfo.packageName;
                if (!found.containsKey(pkg)) {
                    String label = ri.loadLabel(pm).toString();
                    Drawable icon = ri.loadIcon(pm);
                    IconPackInfo info = new IconPackInfo(pkg, label, icon);
                    found.put(pkg, info);
                    list.add(info);
                }
            }
        }
        return list;
    }

    /**
     * Lazily loads and parses appfilter.xml of the given icon pack.
     */
    private static synchronized void loadPack(Context ctx, String packPackage) {
        if (packPackage == null || packPackage.isEmpty() || packPackage.equals(PACK_MATERIAL_YOU)) {
            clearCache();
            sCurrentPack = packPackage;
            return;
        }
        if (packPackage.equals(sCurrentPack) && sPackResources != null) {
            return;
        }
        clearCache();
        sCurrentPack = packPackage;

        try {
            PackageManager pm = ctx.getPackageManager();
            sPackResources = pm.getResourcesForApplication(packPackage);

            // Attempt 1: Read appfilter.xml from assets
            InputStream is = null;
            try {
                Context packContext = ctx.createPackageContext(packPackage, Context.CONTEXT_IGNORE_SECURITY);
                is = packContext.getAssets().open("appfilter.xml");
            } catch (Exception ignored) {
            }

            if (is != null) {
                parseAppFilterStream(is);
                try { is.close(); } catch (Exception ignored) {}
            } else {
                // Attempt 2: Read from res/xml/appfilter
                int resId = sPackResources.getIdentifier("appfilter", "xml", packPackage);
                if (resId != 0) {
                    XmlResourceParser parser = sPackResources.getXml(resId);
                    parseAppFilterXml(parser);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading icon pack: " + packPackage, e);
        }
    }

    private static void parseAppFilterStream(InputStream is) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");
            parseXml(parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing appfilter stream", e);
        }
    }

    private static void parseAppFilterXml(XmlPullParser parser) {
        try {
            parseXml(parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing appfilter XML", e);
        }
    }

    private static void parseXml(XmlPullParser parser) throws Exception {
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "item".equalsIgnoreCase(parser.getName())) {
                String comp = parser.getAttributeValue(null, "component");
                String drawableName = parser.getAttributeValue(null, "drawable");
                if (comp != null && drawableName != null) {
                    // Normalize ComponentInfo{com.example.app/com.example.app.MainActivity} -> com.example.app
                    String cleanKey = sanitizeComponent(comp);
                    if (!cleanKey.isEmpty()) {
                        sComponentMap.put(cleanKey, drawableName);
                    }
                }
            }
            eventType = parser.next();
        }
    }

    private static String sanitizeComponent(String comp) {
        if (comp.startsWith("ComponentInfo{")) {
            comp = comp.substring("ComponentInfo{".length());
            if (comp.endsWith("}")) comp = comp.substring(0, comp.length() - 1);
        }
        return comp.trim();
    }

    /**
     * Resolves an icon for the given package and activity from the active icon pack.
     * Returns null if no icon pack is active or no match was found.
     */
    public static synchronized Bitmap getIcon(Context ctx, String pkg, String cls) {
        String activePack = getActiveIconPack(ctx);
        if (activePack.isEmpty() || activePack.equals(PACK_MATERIAL_YOU)) {
            return null;
        }

        if (!activePack.equals(sCurrentPack) || sPackResources == null) {
            loadPack(ctx, activePack);
        }
        if (sPackResources == null) return null;

        String cacheKey = (pkg != null ? pkg : "") + "/" + (cls != null ? cls : "");
        if (sBitmapCache.containsKey(cacheKey)) {
            return sBitmapCache.get(cacheKey);
        }

        String drawableName = null;
        // Priority 1: Full component match pkg/cls
        if (cls != null && !cls.isEmpty()) {
            drawableName = sComponentMap.get(pkg + "/" + cls);
        }
        // Priority 2: Package-only search
        if (drawableName == null && pkg != null) {
            for (Map.Entry<String, String> entry : sComponentMap.entrySet()) {
                if (entry.getKey().startsWith(pkg + "/")) {
                    drawableName = entry.getValue();
                    break;
                }
            }
        }

        if (drawableName != null) {
            try {
                int resId = sPackResources.getIdentifier(drawableName, "drawable", sCurrentPack);
                if (resId != 0) {
                    Drawable d = sPackResources.getDrawable(resId, null);
                    if (d != null) {
                        Bitmap bmp = drawableToBitmap(d);
                        sBitmapCache.put(cacheKey, bmp);
                        return bmp;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap src = ((BitmapDrawable) drawable).getBitmap();
            // P1-11: cap a pack-supplied bitmap instead of trusting its size.
            if (src != null && src.getWidth() > 0 && src.getHeight() > 0
                    && src.getWidth() <= 1024 && src.getHeight() <= 1024) {
                return src;
            }
            if (src == null) return null;
            return Bitmap.createScaledBitmap(src, 144, 144, true);
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0 || w > 1024 || h > 1024) { w = 144; h = 144; }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
