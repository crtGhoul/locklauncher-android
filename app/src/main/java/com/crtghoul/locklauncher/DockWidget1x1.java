package com.crtghoul.locklauncher;

/** 1x1 widget: one big tappable shortcut — like the iOS circular widget. */
public class DockWidget1x1 extends BaseDockWidget {
    @Override protected int layoutRes() { return R.layout.widget_1x1; }
    @Override protected int slotCount() { return 1; }
    @Override protected int pendingBase() { return 500; }
}
