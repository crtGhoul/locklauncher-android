package com.crtghoul.locklauncher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One dock shortcut: app, URL, call, SMS, flashlight, settings panel,
 * app deep-shortcut, profile switcher, or interactive folder.
 * Carries a stable id and profileSerial (for multi-user/cloned apps).
 */
public class Shortcut {
    public static final String TYPE_APP = "app";
    public static final String TYPE_URL = "url";
    public static final String TYPE_CALL = "call";
    public static final String TYPE_SMS = "sms";
    public static final String TYPE_TORCH = "torch";
    public static final String TYPE_PANEL = "panel";
    public static final String TYPE_DEEP = "deep";
    public static final String TYPE_PROFILE = "profile";
    public static final String TYPE_FOLDER = "folder";
    public static final String TYPE_EMPTY = "empty";

    public static final String ICON_AUTO = "auto";
    public static final String ICON_LETTER = "letter";
    public static final String ICON_EMOJI = "emoji";
    public static final String ICON_IMAGE = "image";

    public String id;        // stable uuid, keys the custom icon file
    public String type;
    public String pkg;       // package name (apps)
    public String url;       // url (urls)
    public String data;      // phone number (call/sms), intent URI (deep), panel key (panel)
    public String label;
    public String iconMode;  // auto | letter | emoji | image
    public int iconColor;    // ARGB for letter/emoji tile, -1 = auto
    public String iconEmoji; // emoji text (emoji mode)
    public long profileSerial; // 0 = owner/primary user; >0 = cloned or work profile serial
    public List<Shortcut> folderItems = new ArrayList<>(); // items contained if TYPE_FOLDER

    public Shortcut(String type, String pkg, String url, String label) {
        this(UUID.randomUUID().toString(), type, pkg, url, "", label,
                ICON_AUTO, -1, "", 0);
    }

    public Shortcut(String type, String pkg, String url, String label, long profileSerial) {
        this(UUID.randomUUID().toString(), type, pkg, url, "", label,
                ICON_AUTO, -1, "", profileSerial);
    }

    public Shortcut(String type, String pkg, String url, String data,
                    String label, String iconMode, int iconColor) {
        this(UUID.randomUUID().toString(), type, pkg, url, data, label,
                iconMode, iconColor, "", 0);
    }

    public Shortcut(String id, String type, String pkg, String url, String data,
                    String label, String iconMode, int iconColor, String iconEmoji) {
        this(id, type, pkg, url, data, label, iconMode, iconColor, iconEmoji, 0);
    }

    public Shortcut(String id, String type, String pkg, String url, String data,
                    String label, String iconMode, int iconColor, String iconEmoji, long profileSerial) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.type = type;
        this.pkg = pkg;
        this.url = url;
        this.data = data;
        this.label = label;
        this.iconMode = iconMode;
        this.iconColor = iconColor;
        this.iconEmoji = iconEmoji;
        this.profileSerial = profileSerial;
    }

    public static Shortcut createFolder(String folderName, List<Shortcut> initialItems) {
        Shortcut f = new Shortcut(UUID.randomUUID().toString(), TYPE_FOLDER, "", "", "",
                folderName, ICON_AUTO, -1, "", 0);
        if (initialItems != null) {
            f.folderItems.addAll(initialItems);
        }
        return f;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("t", type);
        o.put("p", pkg == null ? "" : pkg);
        o.put("u", url == null ? "" : url);
        o.put("d", data == null ? "" : data);
        o.put("l", label == null ? "" : label);
        o.put("im", iconMode == null ? ICON_AUTO : iconMode);
        o.put("ic", iconColor);
        o.put("ie", iconEmoji == null ? "" : iconEmoji);
        o.put("ps", profileSerial);

        if (TYPE_FOLDER.equals(type) && folderItems != null && !folderItems.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (Shortcut item : folderItems) {
                if (item != null) arr.put(item.toJson());
            }
            o.put("fi", arr);
        }
        return o;
    }

    public static Shortcut fromJson(JSONObject o) throws JSONException {
        Shortcut s = new Shortcut(
                o.optString("id", ""),
                o.optString("t", TYPE_APP),
                o.optString("p", ""),
                o.optString("u", ""),
                o.optString("d", ""),
                o.optString("l", ""),
                o.optString("im", ICON_AUTO),
                o.optInt("ic", -1),
                o.optString("ie", ""),
                o.optLong("ps", 0));

        if (TYPE_FOLDER.equals(s.type) && o.has("fi")) {
            JSONArray arr = o.optJSONArray("fi");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject itemObj = arr.optJSONObject(i);
                    if (itemObj != null) {
                        s.folderItems.add(Shortcut.fromJson(itemObj));
                    }
                }
            }
        }
        return s;
    }

    public boolean isApp() {
        return TYPE_APP.equals(type);
    }

    public boolean isFolder() {
        return TYPE_FOLDER.equals(type);
    }

    public boolean isClone() {
        return profileSerial > 0;
    }

    public boolean isEmpty() {
        return TYPE_EMPTY.equals(type);
    }

    public static Shortcut createEmpty() {
        return new Shortcut(UUID.randomUUID().toString(), TYPE_EMPTY, "", "", "", "", ICON_AUTO, -1, "", 0);
    }

    public String typeName() {
        switch (type) {
            case TYPE_URL: return "web";
            case TYPE_CALL: return "call";
            case TYPE_SMS: return "sms";
            case TYPE_TORCH: return "flashlight";
            case TYPE_PANEL: return "panel";
            case TYPE_DEEP: return "deep link";
            case TYPE_PROFILE: return "profile";
            case TYPE_FOLDER: return "folder";
            default: return "app";
        }
    }
}
