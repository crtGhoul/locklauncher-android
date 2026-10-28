package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modern fast App Drawer & Picker with multi-user/cloned app discovery,
 * automatic smart categorization chips, live fallback web searches,
 * and direct Home Screen / Folder pinning.
 */
public class AppPickerActivity extends Activity {
    public static final String EXTRA_PACKAGE = "pkg";   // legacy single
    public static final String EXTRA_LABEL = "label";   // legacy single
    public static final String EXTRA_PACKAGES = "pkgs"; // multi-select
    public static final String EXTRA_LABELS = "labels"; // multi-select
    public static final String EXTRA_DRAWER_MODE = "drawer_mode";

    private final List<AppEntry> all = new ArrayList<>();
    private final Map<String, String> selected = new LinkedHashMap<>(); // pkg -> label
    private AppAdapter adapter;
    private Button addButton;
    private View scrollSearchActions;
    private Button btnGoogle, btnYoutube, btnPlay, btnWeb;
    private Button chipAll, chipClones, chipAi, chipMedia, chipTools, chipSocial, chipSystem;
    private String currentCategory = "all";

    // Powerhouse Calculator & Quick Settings
    private View cardCalculator, cardQuickSetting;
    private TextView textCalcExpr, textCalcResult, textSettingIcon, textSettingTitle;
    private Button btnCalcCopy;

    static class AppEntry {
        String pkg, label;
        Drawable icon;
        UserHandle user;
        long profileSerial;
        ComponentName component;
        boolean isClone;
        boolean isSystem;
        String category; // "ai", "media", "tools", "social", "system"
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_app_picker);

        SharedPreferences prefs = getSharedPreferences("drawer_prefs", MODE_PRIVATE);
        Set<String> hidden = prefs.getStringSet("hidden_apps", new HashSet<>());
        boolean isDrawerMode = getIntent().getBooleanExtra(EXTRA_DRAWER_MODE, false);

        // 1. Query all apps across all user profiles (including OxygenOS cloned apps)
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);

        if (launcherApps != null && userManager != null) {
            try {
                List<UserHandle> userProfiles = userManager.getUserProfiles();
                UserHandle myUser = Process.myUserHandle();
                for (UserHandle profile : userProfiles) {
                    boolean isCloneOrWork = !myUser.equals(profile);
                    long pSerial = userManager.getSerialNumberForUser(profile);
                    List<LauncherActivityInfo> activityList = launcherApps.getActivityList(null, profile);
                    for (LauncherActivityInfo info : activityList) {
                        String pkg = info.getApplicationInfo().packageName;
                        if (pkg.equals(getPackageName())) continue;
                        if (isDrawerMode && hidden.contains(pkg)) continue;

                        AppEntry e = new AppEntry();
                        e.pkg = pkg;
                        e.user = profile;
                        e.profileSerial = pSerial;
                        e.component = info.getComponentName();
                        e.isClone = isCloneOrWork;
                        e.isSystem = (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        String rawLabel = info.getLabel().toString();
                        e.label = isCloneOrWork ? (rawLabel + " (Clone)") : rawLabel;
                        e.icon = info.getBadgedIcon(getResources().getDisplayMetrics().densityDpi);

                        // Classify category
                        e.category = classifyApp(e.pkg, rawLabel, e.isSystem, e.isClone);
                        all.add(e);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to standard PackageManager if LauncherApps didn't populate
        if (all.isEmpty()) {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                if (isDrawerMode && hidden.contains(pkg)) continue;
                AppEntry e = new AppEntry();
                e.pkg = pkg;
                e.label = ri.loadLabel(pm).toString();
                e.icon = ri.loadIcon(pm);
                e.isSystem = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                e.category = classifyApp(e.pkg, e.label, e.isSystem, false);
                all.add(e);
            }
        }

        Collections.sort(all, (a, c) -> a.label.compareToIgnoreCase(c.label));

        adapter = new AppAdapter();
        ListView list = findViewById(R.id.app_list);
        list.setAdapter(adapter);
        list.setFastScrollEnabled(true);

        addButton = findViewById(R.id.btn_add_selected);
        scrollSearchActions = findViewById(R.id.scroll_search_actions);
        btnGoogle = findViewById(R.id.btn_search_google);
        btnYoutube = findViewById(R.id.btn_search_youtube);
        btnPlay = findViewById(R.id.btn_search_play);
        btnWeb = findViewById(R.id.btn_search_web);

        chipAll = findViewById(R.id.chip_all);
        chipClones = findViewById(R.id.chip_clones);
        chipAi = findViewById(R.id.chip_ai);
        chipMedia = findViewById(R.id.chip_media);
        chipTools = findViewById(R.id.chip_tools);
        chipSocial = findViewById(R.id.chip_social);
        chipSystem = findViewById(R.id.chip_system);

        cardCalculator = findViewById(R.id.card_calculator);
        textCalcExpr = findViewById(R.id.text_calc_expr);
        textCalcResult = findViewById(R.id.text_calc_result);
        btnCalcCopy = findViewById(R.id.btn_calc_copy);

        cardQuickSetting = findViewById(R.id.card_quick_setting);
        textSettingIcon = findViewById(R.id.text_setting_icon);
        textSettingTitle = findViewById(R.id.text_setting_title);

        setupAlphabetScrubber();

        // Count how many clones we found
        int cloneCount = 0;
        for (AppEntry ae : all) if (ae.isClone) cloneCount++;
        if (chipClones != null) {
            chipClones.setText("Clones (" + cloneCount + ")");
        }

        View.OnClickListener chipListener = v -> {
            if (v == chipAll) currentCategory = "all";
            else if (v == chipClones) currentCategory = "clones";
            else if (v == chipAi) currentCategory = "ai";
            else if (v == chipMedia) currentCategory = "media";
            else if (v == chipTools) currentCategory = "tools";
            else if (v == chipSocial) currentCategory = "social";
            else if (v == chipSystem) currentCategory = "system";

            updateCategoryChipStyles();
            adapter.applyFilter();
        };

        if (chipAll != null) chipAll.setOnClickListener(chipListener);
        if (chipClones != null) chipClones.setOnClickListener(chipListener);
        if (chipAi != null) chipAi.setOnClickListener(chipListener);
        if (chipMedia != null) chipMedia.setOnClickListener(chipListener);
        if (chipTools != null) chipTools.setOnClickListener(chipListener);
        if (chipSocial != null) chipSocial.setOnClickListener(chipListener);
        if (chipSystem != null) chipSystem.setOnClickListener(chipListener);

        if (isDrawerMode) {
            addButton.setVisibility(View.GONE);
            list.setOnItemClickListener((parent, view, pos, id) -> {
                AppEntry e = adapter.getItem(pos);
                launchApp(e, launcherApps);
            });
            list.setOnItemLongClickListener((parent, view, pos, id) -> {
                AppEntry e = adapter.getItem(pos);
                String[] opts = {
                        "🚀 Launch app",
                        "📌 Pin to Home screen (Main Page)",
                        "📁 Add to Folder...",
                        "➕ Add to Lock Launcher Dock",
                        "ℹ️ App Info",
                        "🚫 Hide from drawer",
                        "🗑️ Uninstall"
                };
                new AlertDialog.Builder(this)
                        .setTitle(e.label)
                        .setItems(opts, (d, which) -> {
                            switch (which) {
                                case 0:
                                    launchApp(e, launcherApps);
                                    break;
                                case 1:
                                    // Pin directly to active profile with full clone support
                                    int p = ShortcutStore.activeProfile(this);
                                    List<Shortcut> current = ShortcutStore.load(this, p);
                                    if (current.size() >= ShortcutStore.MAX) {
                                        Toast.makeText(this, "Home screen is full (16 max)", Toast.LENGTH_SHORT).show();
                                    } else {
                                        current.add(new Shortcut(Shortcut.TYPE_APP, e.pkg, "", e.label, e.profileSerial));
                                        ShortcutStore.save(this, p, current);
                                        Toast.makeText(this, "Pinned " + e.label + " to Home screen", Toast.LENGTH_SHORT).show();
                                    }
                                    break;
                                case 2:
                                    showAddToFolderDialog(e);
                                    break;
                                case 3:
                                    int curP = ShortcutStore.activeProfile(this);
                                    List<Shortcut> dockList = ShortcutStore.load(this, curP);
                                    if (dockList.size() >= ShortcutStore.MAX) {
                                        Toast.makeText(this, "Dock is full (16 max)", Toast.LENGTH_SHORT).show();
                                    } else {
                                        dockList.add(new Shortcut(Shortcut.TYPE_APP, e.pkg, "", e.label, e.profileSerial));
                                        ShortcutStore.save(this, curP, dockList);
                                        Toast.makeText(this, "Added to Lock Launcher", Toast.LENGTH_SHORT).show();
                                    }
                                    break;
                                case 4:
                                    Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    info.setData(Uri.parse("package:" + e.pkg));
                                    startActivity(info);
                                    break;
                                case 5:
                                    Set<String> curHidden = new HashSet<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
                                    curHidden.add(e.pkg);
                                    prefs.edit().putStringSet("hidden_apps", curHidden).apply();
                                    all.remove(e);
                                    adapter.applyFilter();
                                    Toast.makeText(this, e.label + " hidden from drawer", Toast.LENGTH_SHORT).show();
                                    break;
                                case 6:
                                    Intent uninst = new Intent(Intent.ACTION_DELETE);
                                    uninst.setData(Uri.parse("package:" + e.pkg));
                                    startActivity(uninst);
                                    break;
                            }
                        })
                        .show();
                return true;
            });
        } else {
            list.setOnItemClickListener((parent, view, pos, id) -> {
                AppEntry e = adapter.getItem(pos);
                if (selected.containsKey(e.pkg)) {
                    selected.remove(e.pkg);
                } else {
                    selected.put(e.pkg, e.label);
                }
                adapter.notifyDataSetChanged();
                updateAddButton();
            });
            updateAddButton();
            addButton.setOnClickListener(v -> {
                Intent r = new Intent();
                r.putStringArrayListExtra(EXTRA_PACKAGES, new ArrayList<>(selected.keySet()));
                r.putStringArrayListExtra(EXTRA_LABELS, new ArrayList<>(selected.values()));
                setResult(RESULT_OK, r);
                finish();
            });
        }

        EditText search = findViewById(R.id.search);
        if (isDrawerMode) {
            search.setHint("Search " + all.size() + " apps, Google, YouTube...");
            search.requestFocus();
            search.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120);
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int b, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int b, int c, int a) {
                String query = s == null ? "" : s.toString().trim();
                adapter.filterQuery = query;
                adapter.applyFilter();

                // 1. Live Math Calculation
                if (ShortcutStore.isSearchCalculator(AppPickerActivity.this)) {
                    MathEvaluator.Result calc = MathEvaluator.evaluate(query);
                    if (calc.isValid) {
                        cardCalculator.setVisibility(View.VISIBLE);
                        textCalcExpr.setText(query);
                        textCalcResult.setText("= " + calc.formatted);
                        btnCalcCopy.setOnClickListener(v -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Result", calc.formatted));
                                Toast.makeText(AppPickerActivity.this, "Copied " + calc.formatted, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        cardCalculator.setVisibility(View.GONE);
                    }
                } else {
                    cardCalculator.setVisibility(View.GONE);
                }

                // 2. Quick Device Settings Match
                QuickSettingsHelper.SettingMatch settingMatch = QuickSettingsHelper.matchSetting(AppPickerActivity.this, query);
                if (settingMatch != null) {
                    cardQuickSetting.setVisibility(View.VISIBLE);
                    textSettingIcon.setText(settingMatch.icon);
                    textSettingTitle.setText(settingMatch.title);
                    cardQuickSetting.setOnClickListener(v -> {
                        try {
                            startActivity(settingMatch.intent);
                        } catch (Exception e) {
                            Toast.makeText(AppPickerActivity.this, "Setting not supported on this device", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    cardQuickSetting.setVisibility(View.GONE);
                }

                if (query.isEmpty()) {
                    if (scrollSearchActions != null) scrollSearchActions.setVisibility(View.GONE);
                } else {
                    if (scrollSearchActions != null) scrollSearchActions.setVisibility(View.VISIBLE);
                    btnGoogle.setText("🔍 Google \"" + query + "\"");
                    btnYoutube.setText("▶️ YouTube \"" + query + "\"");
                    btnPlay.setText("🛍️ Play Store \"" + query + "\"");

                    // Direct domain detection
                    if (query.contains(".") && !query.contains(" ") && (query.endsWith(".com") || query.endsWith(".org") || query.endsWith(".net") || query.endsWith(".io") || query.endsWith(".ai") || query.endsWith(".app") || query.endsWith(".dev") || query.startsWith("http://") || query.startsWith("https://"))) {
                        btnWeb.setText("🌐 Open " + query);
                    } else {
                        btnWeb.setText("🌐 Open Web App");
                    }

                    btnGoogle.setOnClickListener(v -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                        startActivity(i);
                    });
                    btnYoutube.setOnClickListener(v -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                        startActivity(i);
                    });
                    btnPlay.setOnClickListener(v -> {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)));
                            startActivity(i);
                        } catch (Exception ex) {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query)));
                            startActivity(i);
                        }
                    });
                    btnWeb.setOnClickListener(v -> {
                        String url = query;
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        Intent i = new Intent(AppPickerActivity.this, WebAppActivity.class);
                        i.putExtra(WebAppActivity.EXTRA_URL, url);
                        i.putExtra(WebAppActivity.EXTRA_TITLE, query);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        startActivity(i);
                    });
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private String classifyApp(String pkg, String label, boolean isSystem, boolean isClone) {
        if (isClone) return "clones";
        String p = pkg.toLowerCase();
        String l = label.toLowerCase();

        if (p.contains("muse") || p.contains("gemini") || p.contains("chatgpt") || p.contains("openai")
                || p.contains("claude") || p.contains("copilot") || p.contains("perplexity") || p.contains("poe")
                || l.contains("muse") || l.contains("gemini") || l.contains("chatgpt") || l.contains("ai")) {
            return "ai";
        }
        if (p.contains("youtube") || p.contains("music") || p.contains("video") || p.contains("camera")
                || p.contains("gallery") || p.contains("photo") || p.contains("stremio") || p.contains("sound")
                || p.contains("song") || p.contains("media") || p.contains("radio") || p.contains("tv")
                || l.contains("youtube") || l.contains("music") || l.contains("camera") || l.contains("gallery")) {
            return "media";
        }
        if (p.contains("mail") || p.contains("message") || p.contains("sms") || p.contains("discord")
                || p.contains("reddit") || p.contains("telegram") || p.contains("whatsapp") || p.contains("twitter")
                || p.contains("signal") || p.contains("social") || l.contains("mail") || l.contains("messages")) {
            return "social";
        }
        if (isSystem) {
            return "system";
        }
        return "tools";
    }

    private void setupAlphabetScrubber() {
        LinearLayout scrubber = findViewById(R.id.alphabet_scrubber);
        TextView bubble = findViewById(R.id.scrubber_bubble);
        ListView list = findViewById(R.id.app_list);
        if (scrubber == null || bubble == null || list == null) return;
        scrubber.removeAllViews();

        String[] alphabet = {"#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        for (String letter : alphabet) {
            TextView tv = new TextView(this);
            tv.setText(letter);
            tv.setTextSize(9f);
            tv.setTextColor(0xAAFFFFFF);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            tv.setLayoutParams(lp);
            scrubber.addView(tv);
        }

        scrubber.setOnTouchListener((v, event) -> {
            float y = event.getY();
            float height = v.getHeight();
            if (height <= 0) return true;
            int index = (int) ((y / height) * alphabet.length);
            index = Math.max(0, Math.min(alphabet.length - 1, index));
            String targetLetter = alphabet[index];

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    bubble.setVisibility(View.VISIBLE);
                    bubble.setText(targetLetter);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (adapter != null) {
                        int targetPos = adapter.findPositionForLetter(targetLetter);
                        if (targetPos >= 0) {
                            list.setSelection(targetPos);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    bubble.setVisibility(View.GONE);
                    return true;
            }
            return true;
        });
    }

    private void updateCategoryChipStyles() {
        if (chipAll != null) chipAll.setBackgroundResource("all".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipClones != null) chipClones.setBackgroundResource("clones".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipAi != null) chipAi.setBackgroundResource("ai".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipMedia != null) chipMedia.setBackgroundResource("media".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipTools != null) chipTools.setBackgroundResource("tools".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipSocial != null) chipSocial.setBackgroundResource("social".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
        if (chipSystem != null) chipSystem.setBackgroundResource("system".equals(currentCategory) ? R.drawable.btn_accent : R.drawable.btn_ghost);
    }

    private void showAddToFolderDialog(AppEntry e) {
        int p = ShortcutStore.activeProfile(this);
        List<Shortcut> list = ShortcutStore.load(this, p);
        List<Shortcut> folders = new ArrayList<>();
        for (Shortcut s : list) {
            if (s.isFolder()) folders.add(s);
        }

        List<String> options = new ArrayList<>();
        options.add("➕ Create New Folder");
        for (Shortcut f : folders) {
            options.add("📁 " + f.label + " (" + (f.folderItems != null ? f.folderItems.size() : 0) + ")");
        }

        new AlertDialog.Builder(this)
                .setTitle("Add " + e.label + " to folder")
                .setItems(options.toArray(new String[0]), (dialog, idx) -> {
                    if (idx == 0) {
                        EditText input = new EditText(this);
                        input.setHint("Folder Name (e.g. Work, Media)");
                        new AlertDialog.Builder(this)
                                .setTitle("New Folder")
                                .setView(input)
                                .setPositiveButton("Create", (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (name.isEmpty()) name = "Folder";
                                    if (list.size() >= ShortcutStore.MAX) {
                                        Toast.makeText(this, "Home screen is full", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    Shortcut newFolder = Shortcut.createFolder(name, Collections.singletonList(
                                            new Shortcut(Shortcut.TYPE_APP, e.pkg, "", e.label, e.profileSerial)
                                    ));
                                    list.add(newFolder);
                                    ShortcutStore.save(this, p, list);
                                    Toast.makeText(this, "Created folder " + name + " with " + e.label, Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        Shortcut target = folders.get(idx - 1);
                        if (target.folderItems == null) target.folderItems = new ArrayList<>();
                        target.folderItems.add(new Shortcut(Shortcut.TYPE_APP, e.pkg, "", e.label, e.profileSerial));
                        ShortcutStore.save(this, p, list);
                        Toast.makeText(this, "Added to " + target.label, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchApp(AppEntry e, LauncherApps launcherApps) {
        try {
            if (e.component != null && e.user != null && launcherApps != null) {
                launcherApps.startMainActivity(e.component, e.user, null, null);
                finish();
                return;
            }
        } catch (Exception ignored) {
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(e.pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            finish();
        } else {
            Toast.makeText(this, "Cannot launch " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAddButton() {
        if (addButton != null) {
            addButton.setText("Add selected (" + selected.size() + ")");
            addButton.setEnabled(!selected.isEmpty());
            addButton.setAlpha(selected.isEmpty() ? 0.5f : 1f);
        }
    }

    class AppAdapter extends BaseAdapter {
        private List<AppEntry> shown = new ArrayList<>(all);
        String filterQuery = "";

        void applyFilter() {
            List<AppEntry> filtered = new ArrayList<>();
            final String q = filterQuery.trim().toLowerCase();
            final Map<AppEntry, Integer> scores = new HashMap<>();

            for (AppEntry e : all) {
                if ("clones".equals(currentCategory) && !e.isClone) continue;
                if ("ai".equals(currentCategory) && !"ai".equals(e.category)) continue;
                if ("media".equals(currentCategory) && !"media".equals(e.category)) continue;
                if ("tools".equals(currentCategory) && !"tools".equals(e.category)) continue;
                if ("social".equals(currentCategory) && !"social".equals(e.category)) continue;
                if ("system".equals(currentCategory) && !e.isSystem) continue;

                if (!q.isEmpty()) {
                    int score = FuzzyMatcher.matchScore(e.label, q);
                    if (score < 0) {
                        score = FuzzyMatcher.matchScore(e.pkg, q);
                    }
                    if (score < 0) continue;
                    scores.put(e, score);
                }
                filtered.add(e);
            }

            if (!q.isEmpty()) {
                Collections.sort(filtered, (a, b) -> {
                    int sa = scores.containsKey(a) ? scores.get(a) : 0;
                    int sb = scores.containsKey(b) ? scores.get(b) : 0;
                    if (sa != sb) return Integer.compare(sb, sa); // higher score first
                    return a.label.compareToIgnoreCase(b.label);
                });
            } else {
                Collections.sort(filtered, (a, b) -> a.label.compareToIgnoreCase(b.label));
            }

            shown = filtered;
            notifyDataSetChanged();
        }

        public int findPositionForLetter(String letter) {
            if (shown.isEmpty()) return -1;
            if (letter.equals("#")) {
                for (int i = 0; i < shown.size(); i++) {
                    String lbl = shown.get(i).label;
                    if (lbl != null && !lbl.isEmpty()) {
                        char first = lbl.toUpperCase().charAt(0);
                        if (!Character.isLetter(first)) return i;
                    }
                }
                return 0;
            }
            char target = letter.charAt(0);
            for (int i = 0; i < shown.size(); i++) {
                String lbl = shown.get(i).label;
                if (lbl != null && !lbl.isEmpty()) {
                    char first = lbl.toUpperCase().charAt(0);
                    if (first >= target) return i;
                }
            }
            return shown.size() - 1;
        }

        @Override public int getCount() { return shown.size(); }
        @Override public AppEntry getItem(int pos) { return shown.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(AppPickerActivity.this).inflate(R.layout.row_app, parent, false);
            }
            AppEntry e = getItem(pos);
            ImageView icon = v.findViewById(R.id.app_icon);
            TextView name = v.findViewById(R.id.app_name);
            TextView check = v.findViewById(R.id.app_check);

            icon.setImageDrawable(e.icon);
            name.setText(e.label);

            if (check != null) {
                boolean sel = selected.containsKey(e.pkg);
                check.setVisibility(sel ? View.VISIBLE : View.GONE);
            }
            return v;
        }
    }
}
