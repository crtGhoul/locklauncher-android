package com.crtghoul.locklauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-posts the dock after a reboot when it was enabled. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (ShortcutStore.isDockOn(context) && !ShortcutStore.load(context).isEmpty()) {
                try {
                    DockService.start(context);
                } catch (Exception ignored) {
                    // Android 12+ may block FGS starts from boot; user re-enables from the app.
                }
            }
            if (IslandService.isIslandOn(context) && IslandService.canShow(context)) {
                IslandService.start(context);
            }
        }
    }
}
