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
            String id = cm.getCameraIdList()[0];
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
