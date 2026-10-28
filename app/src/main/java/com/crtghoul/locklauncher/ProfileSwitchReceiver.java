package com.crtghoul.locklauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Cycles the active dock profile when a profile-switch shortcut is tapped. */
public class ProfileSwitchReceiver extends BroadcastReceiver {
    public static final String ACTION_CYCLE = "com.crtghoul.locklauncher.PROFILE_CYCLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_CYCLE.equals(intent.getAction())) return;
        int next = (ShortcutStore.activeProfile(context) + 1) % ShortcutStore.PROFILES;
        ShortcutStore.setActiveProfile(context, next);
        Toast.makeText(context, "Dock: " + ShortcutStore.profileName(context, next),
                Toast.LENGTH_SHORT).show();
        if (ShortcutStore.isDockOn(context)) {
            DockService.refresh(context);
        }
        BaseDockWidget.refreshAll(context);
        BaseDockTile.requestTileUpdate(context);
        IslandService.refresh(context);
    }
}
