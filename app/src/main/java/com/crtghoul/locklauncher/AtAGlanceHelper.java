package com.crtghoul.locklauncher;

import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.format.DateFormat;

import java.util.Date;

/**
 * 100% on-device, privacy-first "At a Glance" smart provider.
 * Gathers next alarm, upcoming calendar events, and live battery status
 * without any cloud connections, telemetry, or account syncing.
 */
public class AtAGlanceHelper {

    public static class GlanceItem {
        public final String icon;
        public final String title;
        public final String subtitle;
        public final Intent actionIntent;

        public GlanceItem(String icon, String title, String subtitle, Intent actionIntent) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.actionIntent = actionIntent;
        }
    }

    /**
     * Gets the next active alarm clock.
     */
    public static GlanceItem getNextAlarm(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
                if (info != null) {
                    long triggerTime = info.getTriggerTime();
                    String timeStr = DateFormat.getTimeFormat(ctx).format(new Date(triggerTime));
                    long diff = triggerTime - System.currentTimeMillis();
                    String dayStr;
                    if (diff < 12 * 3600 * 1000L) {
                        dayStr = "Today";
                    } else if (diff < 36 * 3600 * 1000L) {
                        dayStr = "Tomorrow";
                    } else {
                        dayStr = (String) DateFormat.format("EEE", triggerTime);
                    }
                    Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                    return new GlanceItem("⏰", timeStr, dayStr, intent);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Gets the next upcoming calendar event in the next 24 hours (if calendar permission granted).
     */
    public static GlanceItem getNextCalendarEvent(Context ctx) {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Cursor cursor = null;
        try {
            long now = System.currentTimeMillis();
            long end = now + (24 * 60 * 60 * 1000L); // 24 hours ahead

            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, now);
            ContentUris.appendId(builder, end);

            String[] projection = new String[]{
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.EVENT_ID
            };

            ContentResolver cr = ctx.getContentResolver();
            cursor = cr.query(builder.build(), projection, null, null, CalendarContract.Instances.BEGIN + " ASC LIMIT 1");
            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                long begin = cursor.getLong(1);
                long eventId = cursor.getLong(2);

                String timeStr = DateFormat.getTimeFormat(ctx).format(new Date(begin));
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
                return new GlanceItem("📅", title != null ? title : "Calendar Event", timeStr, intent);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Live battery and charging state.
     */
    public static GlanceItem getBatteryStatus(Context ctx) {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = ctx.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                int pct = (int) ((level / (float) scale) * 100);
                String icon = isCharging ? "⚡" : (pct > 20 ? "🔋" : "🪫");
                String title = pct + "%";
                String sub = isCharging ? "Charging" : (pct <= 20 ? "Low" : "Normal");
                Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                return new GlanceItem(icon, title, sub, intent);
            }
        } catch (Exception ignored) {
        }
        return new GlanceItem("🔋", "100%", "Battery", null);
    }
}
