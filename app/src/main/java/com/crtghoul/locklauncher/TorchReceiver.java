package com.crtghoul.locklauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.widget.Toast;

/** Toggles the flashlight when a torch dock shortcut is tapped. */
public class TorchReceiver extends BroadcastReceiver {
    public static final String ACTION_TOGGLE = "com.crtghoul.locklauncher.TORCH_TOGGLE";
    private static final String PREFS = "dock";
    private static final String KEY_TORCH = "torch_on";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_TOGGLE.equals(intent.getAction())) return;
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Camera permission needed for flashlight",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            CameraManager cm = context.getSystemService(CameraManager.class);
            // P1-10: pick the first camera that actually has a flash,
            // not blindly index 0 (which may be a flash-less camera).
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                try {
                    Boolean hasFlash = cm.getCameraCharacteristics(cid)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (Boolean.TRUE.equals(hasFlash)) { id = cid; break; }
                } catch (Exception ignored) {
                }
            }
            if (id == null) {
                Toast.makeText(context, "Flashlight not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            boolean on = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_TORCH, false);
            cm.setTorchMode(id, on);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_TORCH, on).apply();
            Toast.makeText(context, on ? "Flashlight on" : "Flashlight off",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Flashlight not available",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
