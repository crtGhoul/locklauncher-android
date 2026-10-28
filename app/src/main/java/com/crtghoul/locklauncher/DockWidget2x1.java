package com.crtghoul.locklauncher;

/** 2x1 widget: two actions side by side. */
public class DockWidget2x1 extends BaseDockWidget {
    @Override protected int layoutRes() { return R.layout.widget_2x1; }
    @Override protected int slotCount() { return 2; }
    @Override protected int pendingBase() { return 400; }
}
