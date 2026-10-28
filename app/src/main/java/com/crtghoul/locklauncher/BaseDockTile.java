package com.crtghoul.locklauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.List;

/** Base for the 3 Quick Settings launcher tiles (slots 0-2 of the active profile). */
public abstract class BaseDockTile extends TileService {
    protected abstract int slot();

    public static void requestTileUpdate(Context ctx) {
        try {
            TileService.requestListeningState(ctx,
                    new android.content.ComponentName(ctx, DockTile1.class));
            TileService.requestListeningState(ctx,
                    new android.content.ComponentName(ctx, DockTile2.class));
            TileService.requestListeningState(ctx,
                    new android.content.ComponentName(ctx, DockTile3.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        List<Shortcut> shortcuts = ShortcutStore.load(this);
        if (slot() < shortcuts.size()) {
            Shortcut s = shortcuts.get(slot());
            tile.setLabel(s.label);
            tile.setState(Tile.STATE_ACTIVE);
            try {
                tile.setIcon(Icon.createWithBitmap(IconUtil.iconFor(this, s)));
            } catch (Exception ignored) {
            }
        } else {
            tile.setLabel("Launcher " + (slot() + 1));
            tile.setSubtitle("empty — open Lock Launcher");
            tile.setState(Tile.STATE_UNAVAILABLE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        List<Shortcut> shortcuts = ShortcutStore.load(this);
        if (slot() >= shortcuts.size()) {
            Toast.makeText(this, "Open Lock Launcher to set this tile", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent launch = IconUtil.launchIntent(this, shortcuts.get(slot()));
        if (launch != null) {
            startActivityAndCollapse(launch);
        } else {
            Toast.makeText(this, "Can't launch this shortcut", Toast.LENGTH_SHORT).show();
        }
    }
}
