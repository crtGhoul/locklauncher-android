package com.crtghoul.locklauncher;

/** 4x4 widget: the full 16-slot dock. */
public class DockWidgetProvider extends BaseDockWidget {
    @Override protected int layoutRes() { return R.layout.widget_dock; }
    @Override protected int slotCount() { return 16; }
    @Override protected int pendingBase() { return 200; }
}
