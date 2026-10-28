package com.crtghoul.locklauncher;

/** 4x1 widget: four actions in a row — like the iOS "4 widget actions" widget. */
public class DockWidget4x1 extends BaseDockWidget {
    @Override protected int layoutRes() { return R.layout.widget_4x1; }
    @Override protected int slotCount() { return 4; }
    @Override protected int pendingBase() { return 300; }
}
