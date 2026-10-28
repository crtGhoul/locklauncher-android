package com.crtghoul.locklauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class LockScreenService extends AccessibilityService {
    private static LockScreenService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No event inspection needed
    }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static boolean lockScreen(Context ctx) {
        if (instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
        return false;
    }

    public static void requestAccessibilityPermission(Context ctx) {
        Toast.makeText(ctx, "Enable 'Lock Launcher' under Downloaded Apps / Accessibility to allow Double-Tap to Lock", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
