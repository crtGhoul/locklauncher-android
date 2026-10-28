package com.crtghoul.locklauncher;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/** Base for the 4 widget sizes (1x1, 2x1, 4x1, 4x2) — the iOS-style widget pack. */
public abstract class BaseDockWidget extends AppWidgetProvider {

    protected abstract int layoutRes();

    protected abstract int slotCount();

    protected abstract int pendingBase();

    public static void refreshAll(Context ctx) {
        Class<?>[] providers = {
                DockWidgetProvider.class, DockWidget1x1.class,
                DockWidget2x1.class, DockWidget4x1.class
        };
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        for (Class<?> c : providers) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, c));
            if (ids.length > 0) {
                Intent i = new Intent(ctx, c);
                i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                ctx.sendBroadcast(i);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        List<Shortcut> shortcuts = ShortcutStore.load(context);
        for (int id : ids) {
            int start = ShortcutStore.widgetStartSlot(context, id);
            int count = slotCount();
            List<Shortcut> slice = start < shortcuts.size()
                    ? shortcuts.subList(start, Math.min(start + count, shortcuts.size()))
                    : java.util.Collections.<Shortcut>emptyList();
            RemoteViews rv = new RemoteViews(context.getPackageName(), layoutRes());
            // Unique request codes per widget instance so overlapping slices
            // don't collide on PendingIntents.
            IconUtil.fillDock(context, rv, slice, pendingBase() + (id % 500) * 64, count);
            IconUtil.applyCardStyle(context, rv);
            try {
                Intent cfg = new Intent(context, MainActivity.class);
                PendingIntent cfgPi = PendingIntent.getActivity(
                        context, 998, cfg,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                rv.setOnClickPendingIntent(R.id.widget_title, cfgPi);
            } catch (Exception ignored) {
                // Layouts without a title view (1x1) just skip this.
            }
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            ShortcutStore.clearWidgetStartSlot(context, id);
        }
    }
}
