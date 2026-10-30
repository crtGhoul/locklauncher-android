package com.crtghoul.locklauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Guided 4-step First-Run Onboarding Flow (re-runnable anytime from Settings):
 * 1) 01 / APPEARANCE: Glass style (Frosted, Liquid, Classic) + Original vs Minimal icons + Glass icon plates.
 * 2) 02 / WALLPAPER: Pick wallpaper from photos/system chooser + Wallpaper Accent toggle.
 * 3) 03 / PERMISSIONS: Optional permissions & special access cards with live checkmarks.
 * 4) 04 / LAUNCHER MODE: Keep current default launcher (overlay mode) or open system Home selector.
 */
public class OnboardingActivity extends Activity {
    private static final int REQ_NOTIF = 101;
    private static final int REQ_CALENDAR = 102;

    private int currentStep = 0; // 0..3

    private TextView stepDots;
    private TextView stepTag;
    private TextView stepTitle;
    private TextView stepSubtitle;
    private LinearLayout previewGlassCard;
    private TextView previewModeTag;
    private LinearLayout previewIconsRow;

    private View panelAppearance;
    private View panelWallpaper;
    private View panelPermissions;
    private View panelLauncher;

    private Button btnBack;
    private Button btnNext;

    private Button btnModeFrosted, btnModeLiquid, btnModeClassic;
    private Button btnIconsOriginal, btnIconsMinimal;
    private Switch switchGlassIcons;
    private Switch switchWallpaperAccent;

    private Button btnPermNotif, btnPermOverlay, btnPermListener, btnPermAccessibility, btnPermCalendar, btnPermBattery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayHelper.enforceHighRefreshRate(this);
        setContentView(R.layout.activity_onboarding);

        stepDots = findViewById(R.id.onb_step_dots);
        stepTag = findViewById(R.id.onb_step_tag);
        stepTitle = findViewById(R.id.onb_step_title);
        stepSubtitle = findViewById(R.id.onb_step_subtitle);
        previewGlassCard = findViewById(R.id.onb_preview_glass_card);
        previewModeTag = findViewById(R.id.onb_preview_mode_tag);
        previewIconsRow = findViewById(R.id.onb_preview_icons_row);

        panelAppearance = findViewById(R.id.onb_panel_appearance);
        panelWallpaper = findViewById(R.id.onb_panel_wallpaper);
        panelPermissions = findViewById(R.id.onb_panel_permissions);
        panelLauncher = findViewById(R.id.onb_panel_launcher);

        btnBack = findViewById(R.id.btn_onb_back);
        btnNext = findViewById(R.id.btn_onb_next);

        findViewById(R.id.btn_onb_skip).setOnClickListener(v -> finishOnboarding());

        btnModeFrosted = findViewById(R.id.btn_onb_mode_frosted);
        btnModeLiquid = findViewById(R.id.btn_onb_mode_liquid);
        btnModeClassic = findViewById(R.id.btn_onb_mode_classic);

        btnIconsOriginal = findViewById(R.id.btn_onb_icons_original);
        btnIconsMinimal = findViewById(R.id.btn_onb_icons_minimal);
        switchGlassIcons = findViewById(R.id.switch_onb_glass_icons);
        switchWallpaperAccent = findViewById(R.id.switch_onb_wallpaper_accent);

        btnModeFrosted.setOnClickListener(v -> selectGlassMode(GlassThemeHelper.MODE_FROSTED));
        btnModeLiquid.setOnClickListener(v -> selectGlassMode(GlassThemeHelper.MODE_LIQUID));
        btnModeClassic.setOnClickListener(v -> selectGlassMode(GlassThemeHelper.MODE_CLASSIC));

        btnIconsOriginal.setOnClickListener(v -> {
            ShortcutStore.setMinimalIcons(this, false);
            IconUtil.clearCache();
            refreshAppearanceControls();
            refreshLivePreview();
        });
        btnIconsMinimal.setOnClickListener(v -> {
            ShortcutStore.setMinimalIcons(this, true);
            IconUtil.clearCache();
            refreshAppearanceControls();
            refreshLivePreview();
        });

        switchGlassIcons.setChecked(ShortcutStore.isGlassIconBackgrounds(this));
        switchGlassIcons.setOnCheckedChangeListener((v, on) -> {
            ShortcutStore.setGlassIconBackgrounds(this, on);
            IconUtil.clearCache();
            refreshLivePreview();
        });

        switchWallpaperAccent.setChecked(GlassThemeHelper.isWallpaperAccent(this));
        switchWallpaperAccent.setOnCheckedChangeListener((v, on) -> {
            GlassThemeHelper.setWallpaperAccent(this, on);
            refreshLivePreview();
        });

        findViewById(R.id.btn_onb_pick_wallpaper).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open system wallpaper picker", Toast.LENGTH_SHORT).show();
            }
        });

        btnPermNotif = findViewById(R.id.btn_onb_perm_notif);
        btnPermOverlay = findViewById(R.id.btn_onb_perm_overlay);
        btnPermListener = findViewById(R.id.btn_onb_perm_listener);
        btnPermAccessibility = findViewById(R.id.btn_onb_perm_accessibility);
        btnPermCalendar = findViewById(R.id.btn_onb_perm_calendar);
        btnPermBattery = findViewById(R.id.btn_onb_perm_battery);

        btnPermNotif.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            } else {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
        });

        btnPermOverlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        btnPermListener.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {}
        });

        btnPermAccessibility.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Accessibility for Screen Gestures")
                    .setMessage("Lock Launcher uses Android's Accessibility Service strictly when you double-tap the wallpaper to lock the screen. It never reads screen content or collects data.")
                    .setPositiveButton("Agree and continue", (d, w) -> LockScreenService.requestAccessibilityPermission(this))
                    .setNegativeButton("Not now", null)
                    .show();
        });

        btnPermCalendar.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR);
            } else {
                Toast.makeText(this, "Calendar permission already granted", Toast.LENGTH_SHORT).show();
            }
        });

        btnPermBattery.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        });

        findViewById(R.id.btn_onb_keep_overlay).setOnClickListener(v -> finishOnboarding());
        findViewById(R.id.btn_onb_choose_home).setOnClickListener(v -> {
            ShortcutStore.setOnboardingDone(this, true);
            GlassThemeHelper.refreshAllSurfaces(this);
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                } catch (Exception ignored) {}
            }
            finish();
        });

        btnBack.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                updateStepUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentStep < 3) {
                currentStep++;
                updateStepUI();
            } else {
                finishOnboarding();
            }
        });

        refreshAppearanceControls();
        updateStepUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionButtons();
        refreshLivePreview();
    }

    private void selectGlassMode(String mode) {
        GlassThemeHelper.setGlassMode(this, mode);
        refreshAppearanceControls();
        refreshLivePreview();
    }

    private void refreshAppearanceControls() {
        String mode = GlassThemeHelper.getGlassMode(this);
        boolean isFrosted = GlassThemeHelper.MODE_FROSTED.equals(mode);
        boolean isLiquid = GlassThemeHelper.MODE_LIQUID.equals(mode);
        boolean isClassic = GlassThemeHelper.MODE_CLASSIC.equals(mode);

        btnModeFrosted.setBackgroundResource(isFrosted ? R.drawable.btn_accent : R.drawable.btn_ghost);
        btnModeFrosted.setText((isFrosted ? "◉" : "○") + "  Frosted Glass  (Blurred surface + soft specular rim)");

        btnModeLiquid.setBackgroundResource(isLiquid ? R.drawable.btn_accent : R.drawable.btn_ghost);
        btnModeLiquid.setText((isLiquid ? "◉" : "○") + "  Liquid Glass  (Refractive crystal sheen + highlight)");

        btnModeClassic.setBackgroundResource(isClassic ? R.drawable.btn_accent : R.drawable.btn_ghost);
        btnModeClassic.setText((isClassic ? "◉" : "○") + "  Classic  (Solid clean surface with wallpaper accent)");

        boolean min = ShortcutStore.isMinimalIcons(this);
        btnIconsOriginal.setBackgroundResource(!min ? R.drawable.btn_accent : R.drawable.btn_ghost);
        btnIconsMinimal.setBackgroundResource(min ? R.drawable.btn_accent : R.drawable.btn_ghost);
    }

    private void refreshLivePreview() {
        if (previewGlassCard == null || previewIconsRow == null) return;
        GlassThemeHelper.applyToView(this, previewGlassCard, 20f);
        String mode = GlassThemeHelper.getGlassMode(this);
        if (previewModeTag != null) {
            previewModeTag.setText(mode.toUpperCase(Locale.ROOT) + " GLASS");
        }
        previewIconsRow.removeAllViews();
        List<Shortcut> list = ShortcutStore.load(this);
        int shown = 0;
        int px = (int) (46 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        for (Shortcut s : list) {
            if (s == null || s.isEmpty()) continue;
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
            lp.setMargins(margin, margin / 2, margin, margin / 2);
            lp.gravity = Gravity.CENTER;
            iv.setLayoutParams(lp);
            iv.setImageBitmap(IconUtil.iconFor(this, s));
            previewIconsRow.addView(iv);
            shown++;
            if (shown >= 4) break;
        }
    }

    private void refreshPermissionButtons() {
        boolean notifOk = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean listenerOk = LockNotificationListenerService.isPermissionGranted(this);
        boolean accessOk = LockScreenService.isServiceRunning();
        boolean calOk = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        boolean batOk = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batOk = pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        } catch (Exception ignored) {}

        btnPermNotif.setText((notifOk ? "✓  " : "○  ") + "Notifications (Lock-Screen Dock)");
        btnPermOverlay.setText((overlayOk ? "✓  " : "○  ") + "Display Over Other Apps (Island & Edge Sidebar)");
        btnPermListener.setText((listenerOk ? "✓  " : "○  ") + "Notifications & Media (Badges & Now Playing)");
        btnPermAccessibility.setText((accessOk ? "✓  " : "○  ") + "Screen Gestures (Double-Tap to Lock)");
        btnPermCalendar.setText((calOk ? "✓  " : "○  ") + "Calendar Events (At-a-Glance Smart Bar)");
        btnPermBattery.setText((batOk ? "✓  " : "○  ") + "Unrestricted Battery (Keep Dock & Island Alive)");
    }

    private void updateStepUI() {
        panelAppearance.setVisibility(currentStep == 0 ? View.VISIBLE : View.GONE);
        panelWallpaper.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        panelPermissions.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        panelLauncher.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);

        btnBack.setVisibility(currentStep > 0 ? View.VISIBLE : View.GONE);

        switch (currentStep) {
            case 0:
                stepDots.setText("━  •  •  •");
                stepTag.setText("01 / APPEARANCE");
                stepTitle.setText("How should it feel?");
                stepSubtitle.setText("Choose an appearance for your Island, Edge Sidebar, and Dock. You can change every detail later.");
                btnNext.setText("Continue with this look →");
                break;
            case 1:
                stepDots.setText("•  ━  •  •");
                stepTag.setText("02 / WALLPAPER");
                stepTitle.setText("Choose your wallpaper.");
                stepSubtitle.setText("Show your chosen image behind the glass and optionally tint surfaces with its accent color.");
                btnNext.setText("Continue to permissions →");
                break;
            case 2:
                stepDots.setText("•  •  ━  •");
                stepTag.setText("03 / PERMISSIONS");
                stepTitle.setText("What you enable is up to you.");
                stepSubtitle.setText("Enable only the features you want. All permissions are optional and can be toggled anytime.");
                btnNext.setText("Continue to final step →");
                refreshPermissionButtons();
                break;
            case 3:
                stepDots.setText("•  •  •  ━");
                stepTag.setText("04 / FINAL STEP");
                stepTitle.setText("How do you want to use Lock Launcher?");
                stepSubtitle.setText("Keep your current default home launcher and use Lock Launcher's overlays, or make it your home screen.");
                btnNext.setText("Finish setup →");
                break;
        }
        refreshLivePreview();
    }

    private void finishOnboarding() {
        ShortcutStore.setOnboardingDone(this, true);
        GlassThemeHelper.refreshAllSurfaces(this);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPermissionButtons();
    }
}
