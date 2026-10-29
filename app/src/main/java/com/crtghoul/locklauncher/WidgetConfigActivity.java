package com.crtghoul.locklauncher;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Picks which slot range a placed small widget displays (e.g. slots 5-8). */
public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int slotCount = 4;
    private int start = 0;
    private Button[] slotBtns = new Button[16];
    private TextView summary;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(Activity.RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        slotCount = detectSlotCount();
        start = ShortcutStore.widgetStartSlot(this, appWidgetId);

        List<Shortcut> shortcuts = ShortcutStore.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(0xFF101223);

        TextView title = new TextView(this);
        title.setText("Which slots should this widget show?");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        summary = new TextView(this);
        summary.setTextColor(0xFF9AA0B4);
        summary.setTextSize(14);
        summary.setPadding(0, 0, 0, dp(12));
        root.addView(summary);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int i = 0; i < 16; i++) {
            final int s = i;
            Button btn = new Button(this);
            String label = (i + 1) + "\n" + (i < shortcuts.size()
                    ? shortcuts.get(i).label : "—");
            btn.setText(label);
            btn.setTextSize(11);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(i % 4, 1f);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                start = s;
                refreshButtons();
            });
            slotBtns[i] = btn;
            grid.addView(btn);
        }
        root.addView(grid);

        Button save = new Button(this);
        save.setText("Save widget");
        save.setBackgroundResource(R.drawable.btn_accent);
        save.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(16);
        save.setLayoutParams(slp);
        save.setOnClickListener(v -> {
            ShortcutStore.setWidgetStartSlot(this, appWidgetId, start);
            BaseDockWidget.refreshAll(this);
            Intent r = new Intent();
            r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(Activity.RESULT_OK, r);
            finish();
        });
        root.addView(save);

        setContentView(root);
        refreshButtons();
    }

    private int detectSlotCount() {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(this);
            // P1-10: an unknown/forged widget id must not crash config.
            android.appwidget.AppWidgetProviderInfo info = mgr.getAppWidgetInfo(appWidgetId);
            if (info == null || info.provider == null) return 4;
            String cls = info.provider.getClassName();
            if (cls.contains("1x1")) return 1;
            if (cls.contains("2x1")) return 2;
            return 4;
        } catch (Exception e) {
            return 4;
        }
    }

    private void refreshButtons() {
        int maxStart = 16 - slotCount;
        if (start > maxStart) start = maxStart;
        for (int i = 0; i < 16; i++) {
            boolean inRange = i >= start && i < start + slotCount;
            slotBtns[i].setBackgroundResource(
                    inRange ? R.drawable.btn_accent : R.drawable.btn_ghost);
        }
        summary.setText("Showing slots " + (start + 1) + "–" + (start + slotCount));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
