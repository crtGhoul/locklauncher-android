package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal translucent modal Activity for folders.
 * Opens seamlessly when a folder is tapped in:
 * - Any Lock Launcher App Widget (4x1, 16-slot, 2x1, 1x1)
 * - The Home Screen grid
 * - The Lock Screen Notification Dock
 * - The Quick Settings Tiles
 * - The Floating Island overlay
 */
public class FolderActivity extends Activity {

    public static final String EXTRA_FOLDER_ID = "folder_id";
    public static final String EXTRA_FOLDER_NAME = "folder_name";
    public static final String EXTRA_PROFILE = "folder_profile";

    private static final int REQUEST_PICK_APP = 3001;

    private String folderId;
    private String folderName;
    private int profile = 0;
    private Shortcut folderShortcut;
    private List<Shortcut> allShortcuts = new ArrayList<>();

    private TextView titleView;
    private GridView gridView;
    private View emptyHint;
    private FolderGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayHelper.enforceHighRefreshRate(this);
        setContentView(R.layout.activity_folder);

        Intent intent = getIntent();
        folderId = intent != null ? intent.getStringExtra(EXTRA_FOLDER_ID) : null;
        folderName = intent != null ? intent.getStringExtra(EXTRA_FOLDER_NAME) : null;
        profile = intent != null ? intent.getIntExtra(EXTRA_PROFILE, ShortcutStore.activeProfile(this)) : 0;
        profile = ShortcutStore.clampProfile(profile);

        titleView = findViewById(R.id.folder_title);
        gridView = findViewById(R.id.folder_grid);
        emptyHint = findViewById(R.id.folder_empty_hint);
        Button btnRename = findViewById(R.id.btn_folder_rename);
        Button btnAdd = findViewById(R.id.btn_folder_add_app);
        Button btnClose = findViewById(R.id.btn_folder_close);
        View root = findViewById(R.id.folder_root);

        // Tap outside card to close
        root.setOnClickListener(v -> finish());
        findViewById(R.id.folder_card).setOnClickListener(v -> {}); // prevent card clicks from closing
        btnClose.setOnClickListener(v -> finish());

        loadFolderData();

        if (folderShortcut == null) {
            Toast.makeText(this, "Folder not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        titleView.setText("📁 " + folderShortcut.label);

        adapter = new FolderGridAdapter();
        gridView.setAdapter(adapter);

        // Tap app icon to launch
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (folderShortcut.folderItems != null && position < folderShortcut.folderItems.size()) {
                Shortcut child = folderShortcut.folderItems.get(position);
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                boolean ok = IconUtil.fireShortcut(FolderActivity.this, child, view);
                if (!ok) {
                    Toast.makeText(FolderActivity.this, "Could not open " + child.label, Toast.LENGTH_SHORT).show();
                }
                finish();
                overridePendingTransition(R.anim.launcher_fade_in, R.anim.launcher_fade_out);
            }
        });

        // Long press app inside folder for options
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (folderShortcut.folderItems != null && position < folderShortcut.folderItems.size()) {
                Shortcut child = folderShortcut.folderItems.get(position);
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                String[] opts = {
                        "🚀 Launch",
                        "✏️ Rename App",
                        "📌 Pin to Home screen",
                        "ℹ️ App Info",
                        "🗑️ Remove from folder"
                };
                new AlertDialog.Builder(FolderActivity.this)
                        .setTitle(child.label)
                        .setItems(opts, (d, which) -> {
                            switch (which) {
                                case 0:
                                    if (!IconUtil.fireShortcut(FolderActivity.this, child)) {
                                        Toast.makeText(FolderActivity.this, IconUtil.launchFailureReason(FolderActivity.this, child), Toast.LENGTH_SHORT).show();
                                    }
                                    finish();
                                    break;
                                case 1:
                                    EditText input = new EditText(FolderActivity.this);
                                    input.setText(child.label);
                                    if (child.label != null) input.setSelection(child.label.length());
                                    new AlertDialog.Builder(FolderActivity.this)
                                            .setTitle("Rename App")
                                            .setView(input)
                                            .setPositiveButton("Save", (rd, rw) -> {
                                                String newName = input.getText().toString().trim();
                                                if (!newName.isEmpty()) {
                                                    child.label = newName;
                                                    if (child.isApp() && child.pkg != null) {
                                                        String key = AppPickerActivity.getCustomLabelKey(child.pkg, child.profileSerial);
                                                        getSharedPreferences("dock", Context.MODE_PRIVATE)
                                                                .edit().putString(key, newName).apply();
                                                    }
                                                    saveAndSync();
                                                    adapter.notifyDataSetChanged();
                                                    Toast.makeText(FolderActivity.this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                                                }
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .show();
                                    break;
                                case 2:
                                    boolean pinned = IconUtil.pinShortcutToHomeScreen(FolderActivity.this, child);
                                    Toast.makeText(FolderActivity.this, pinned ? "Pinned " + child.label : "Pinning not supported", Toast.LENGTH_SHORT).show();
                                    break;
                                case 3:
                                    try {
                                        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        info.setData(Uri.parse("package:" + child.pkg));
                                        startActivity(info);
                                    } catch (Exception ignored) {}
                                    break;
                                case 4:
                                    folderShortcut.folderItems.remove(position);
                                    saveAndSync();
                                    adapter.notifyDataSetChanged();
                                    updateEmptyState();
                                    Toast.makeText(FolderActivity.this, "Removed " + child.label, Toast.LENGTH_SHORT).show();
                                    break;
                            }
                        })
                        .show();
                return true;
            }
            return false;
        });

        // Rename folder
        btnRename.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setText(folderShortcut.label);
            if (folderShortcut.label != null) input.setSelection(folderShortcut.label.length());
            new AlertDialog.Builder(this)
                    .setTitle("Rename Folder")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        String newName = input.getText().toString().trim();
                        if (!newName.isEmpty()) {
                            folderShortcut.label = newName;
                            titleView.setText("📁 " + newName);
                            saveAndSync();
                            Toast.makeText(this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Add app to folder
        btnAdd.setOnClickListener(v -> {
            Intent picker = new Intent(this, AppPickerActivity.class);
            picker.putExtra(AppPickerActivity.EXTRA_DRAWER_MODE, false);
            startActivityForResult(picker, REQUEST_PICK_APP);
        });

        updateEmptyState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // P1-5: singleTop redelivery. Re-read the folder from the new
        // intent instead of keeping the stale folder from onCreate.
        super.onNewIntent(intent);
        setIntent(intent);
        folderId = intent != null ? intent.getStringExtra(EXTRA_FOLDER_ID) : null;
        folderName = intent != null ? intent.getStringExtra(EXTRA_FOLDER_NAME) : null;
        profile = intent != null ? intent.getIntExtra(EXTRA_PROFILE, ShortcutStore.activeProfile(this)) : 0;
        profile = ShortcutStore.clampProfile(profile);
        loadFolderData();
        if (folderShortcut == null) {
            Toast.makeText(this, "Folder not found", Toast.LENGTH_SHORT).show();
            finish();
        } else if (titleView != null) {
            titleView.setText("📁 " + folderShortcut.label);
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    }

    private void loadFolderData() {
        // P1-5: only the profile named in the intent is searched, and
        // only by UUID. Matching by label alone let any caller open a
        // same-named folder in another profile.
        profile = ShortcutStore.clampProfile(profile);
        allShortcuts = ShortcutStore.load(this, profile);
        folderShortcut = findFolder(allShortcuts);
    }

    private Shortcut findFolder(List<Shortcut> list) {
        if (list == null || folderId == null || folderId.isEmpty()) return null;
        for (Shortcut s : list) {
            if (s.isFolder() && folderId.equals(s.id)) return s;
        }
        return null;
    }

    private void updateEmptyState() {
        boolean empty = folderShortcut == null || folderShortcut.folderItems == null || folderShortcut.folderItems.isEmpty();
        if (emptyHint != null) emptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (gridView != null) gridView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void saveAndSync() {
        ShortcutStore.save(this, profile, allShortcuts);
        BaseDockWidget.refreshAll(this);
        DockService.refresh(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_APP && resultCode == RESULT_OK && data != null) {
            ArrayList<String> pkgs = data.getStringArrayListExtra(AppPickerActivity.EXTRA_PACKAGES);
            ArrayList<String> labels = data.getStringArrayListExtra(AppPickerActivity.EXTRA_LABELS);
            long[] serials = data.getLongArrayExtra(AppPickerActivity.EXTRA_PROFILE_SERIALS);
            if (pkgs != null && labels != null && folderShortcut != null) {
                if (folderShortcut.folderItems == null) folderShortcut.folderItems = new ArrayList<>();
                for (int i = 0; i < pkgs.size(); i++) {
                    String pkg = pkgs.get(i);
                    String lbl = i < labels.size() ? labels.get(i) : pkg;
                    long sSerial = (serials != null && i < serials.length) ? serials[i] : 0;
                    folderShortcut.folderItems.add(new Shortcut(Shortcut.TYPE_APP, pkg, "", lbl, sSerial));
                }
                saveAndSync();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(this, "Added " + pkgs.size() + " apps to " + folderShortcut.label, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FolderGridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return (folderShortcut != null && folderShortcut.folderItems != null) ? folderShortcut.folderItems.size() : 0;
        }

        @Override
        public Shortcut getItem(int position) {
            return folderShortcut.folderItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FolderActivity.this).inflate(R.layout.item_home_shortcut, parent, false);
            }
            Shortcut item = getItem(position);
            ImageView icon = convertView.findViewById(R.id.sc_icon);
            TextView label = convertView.findViewById(R.id.sc_label);
            icon.setImageBitmap(IconUtil.iconFor(FolderActivity.this, item));
            label.setText(item.label);
            label.setVisibility(View.VISIBLE);
            return convertView;
        }
    }
}
