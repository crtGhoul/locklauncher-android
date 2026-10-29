# Lock Launcher v2.11 — Motion / Fluidity Primitives: Integration Guide

Base: GitHub `crtGhoul/locklauncher-android`, HEAD `c0c1ced`, tag v2.11.
Working copy: `~/workspace/locklauncher-review/code-motion` ONLY.
Status: **new helper files only, no existing file modified, nothing committed
or pushed.** Integration snippets below are copy-paste-ready but are NOT
applied yet — applying them is a separate, reviewable step, and it must
be merged with the security-fix copy (`code-v211`) by a human, not by
blindly copying files, because the two copies will have diverged.

## 0. The translation, in one paragraph

This is a native Android Java app. There is no CSS, no Tailwind, no
`navigator.vibrate`, no `will-change`, no `backdrop-filter` in it, and
adding any of those would be fake code that does not compile. Every
requested web primitive is translated below to the Android mechanism
that actually does the same job. If a requested effect has no Android
equivalent (true live backdrop blur behind an overlay window, a CSS
36px pill), this doc says so instead of faking it.

## 1. Measured facts the helpers are built on (v2.11, read from code)

- Collapsed island pill: NOT 36px. `IslandService.showDockPill`:
  WRAP_CONTENT window, minWidth 92dp, minHeight 32dp, corner radius
  17dp, padding 14dp/6dp, at `statusBarHeight() + 8dp`,
  gravity TOP|CENTER_HORIZONTAL, x/y persisted in "dock" prefs
  (`island_x` / `island_y`). It contains no app icons when collapsed.
- Expanded deck: `IslandService.showExpanded`: card width
  `min(screenWidth - 32dp, 360dp)`, WRAP_CONTENT height, topMargin
  `statusBarHeight() + 8dp`, corner radius 22dp, GridLayout
  columnCount 4, cell height 76dp, icon 44dp, scroll height
  `min(280dp, 84dp * rows)`. Current entrance: ViewPropertyAnimator
  scale 0.88 -> 1.0, alpha 0 -> 1.0, 180ms, DecelerateInterpolator —
  no spring, no velocity inheritance.
- Home grid: `HomeActivity` GridView `R.id.home_grid`, adapter holds
  exactly `ShortcutStore.MAX = 16` padded slots per profile
  (`ShortcutStore.loadPadded`, 3 profiles), columns from
  `ShortcutStore.homeGridColumns()`. Clean Mode today
  (`HomeActivity.applyCleanModeState`, ~line 972) animates the WHOLE
  GridView: reveal 220ms OvershootInterpolator(1.2), hide 160ms to
  alpha 0 / scale 0.92 then GONE. No per-item stagger exists.
- Home press feedback already exists in one place:
  `HomeActivity.launchOrHandleShortcut` does a 40ms scale-down to
  0.92 then a 120ms OvershootInterpolator(2.0) rebound. It is neither
  instant on down nor velocity-preserving. PressableHelper replaces
  it; do not keep both.
- Haptics already used: `performHapticFeedback` LONG_PRESS /
  VIRTUAL_KEY / KEYBOARD_TAP in HomeActivity, FolderActivity,
  AppPickerActivity. HapticHelper builds on that, it does not invent
  a new mechanism.
- Wallpaper tint already exists: `DynamicThemeHelper.extractTheme`
  (WallpaperManager.getWallpaperColors, API 27+, HSV clamp, indigo
  fallback 0xFF5865F2), called only from HomeActivity.
  WallpaperTintHelper extends it; it does not re-extract wallpaper.
- Island organizer today is a DIALOG, not drag-and-drop:
  `IslandService.showIslandOrganizerDialog` (Add / Sort A-Z /
  Clear All / per-item Manage -> showIslandAppMenu). Deck cells have
  click (launch) and long-press (menu) only. There is no draggable
  island item to snap. MagneticSnapHelper is ready-to-wire, not a
  behaviour change, until a drag path is added.
- Media: IslandService already gets a real MediaController and checks
  `PlaybackState.STATE_PLAYING` in `getActiveMediaController`. The
  music indicator must take that boolean from the caller; it must
  never claim bar heights are audio amplitudes.
- Search: `FuzzyMatcher.matchScore` lowercases without a locale,
  scores substring/acronym/camel/subsequence per target.
  `MathEvaluator.evaluate` catches only `Exception`; deep paren
  nesting recurses in `Parser.parseFactor` and can StackOverflow.
  NOTE (2026-10-29, parent memory): the security-fix copy
  (`code-v211`) already added depth/length caps to MathEvaluator.
  This motion copy is a fresh HEAD clone, so its MathEvaluator is
  still uncapped — MemoizedSearch carries the same guard as a
  wrapper. When merging, keep ONE guard (prefer the one inside
  MathEvaluator) and treat MemoizedSearch's as redundant-but-harmless.

## 2. New files (all under app/src/main/java/com/crtghoul/locklauncher/)

- `SpringAnimator.java` — mass-spring-damper integrator
  (k=400, c=28, m=0.8, zeta = 28 / (2*sqrt(320)) = 0.78, slightly
  underdamped with one overshoot), Choreographer-driven, semi-implicit
  Euler with dt clamped to 33ms and ~4ms substeps. Velocity inheritance:
  keep ONE instance per property and call `startTo(newTarget)` to
  re-target mid-flight. Includes `SpringAnimator.Flip` (FLIP: measure
  first/last window rects, invert with scale+translation only, spring
  to identity) and `SpringAnimator.HardwareLayer` (see below).
  No separate HardwareLayerHelper file: the layer helpers live here
  because every animator needs them and a 10-file split for a
  6-line helper would be clutter, not clarity.
- `HapticHelper.java` — performHapticFeedback first (KEYBOARD_TAP /
  VIRTUAL_KEY / LONG_PRESS), 8ms VibrationEffect fallback (API 26+,
  deprecated vibrate() below that) only when no View exists, user
  toggle `motion_haptics_enabled` in "dock" prefs, default ON.
- `PressableHelper.java` — ACTION_DOWN sets scale 0.92 synchronously
  (0ms, no animator), ACTION_UP/CANCEL springs to 1.0 with overshoot.
  `attach(view, onClick)` replaces the ad-hoc Home press animation.
- `StaggerHelper.java` — 12ms/item cascade (constant
  DELAY_PER_ITEM_MS = 12), spring scale/alpha per item. GridView
  version cascades VISIBLE children only (GridView recycles; forcing
  off-screen children into existence would defeat recycling).
- `IslandMorphHelper.java` — FLIP morph pill -> deck, plus spring
  entrance/collapse fallbacks (honestly labelled as entrances, not
  morphs, when no pill exists).
- `MagneticSnapHelper.java` — drag session: nearest-slot search,
  spring to slot centre only on snap change, haptic tick on change,
  snap-or-return on release, velocity inherited via held X/Y springs.
- `IslandGlowHelper.java` — layered deck background (obsidian +
  specular highlight + stroke, matching current 0xF40D1117 / 22dp /
  stroke), frosted background (API 31+ RenderEffect on a dedicated
  background layer only, translucent fallback below that — see §5),
  radial ambient glow, dominant-colour estimate from icon bitmaps,
  charging ripple, music indicator bars, silent-mode dot.
- `WallpaperTintHelper.java` — monochromatic wallpaper tint +
  WCAG contrast (relative luminance, ratio >= 4.5 for text, white/
  black auto-selection). Extends DynamicThemeHelper, does not
  re-extract wallpaper.
- `MemoizedSearch.java` — LRU query cache (256 entries), 1-2 char
  prefix index built once per target list, full FuzzyMatcher scoring
  otherwise, math cache (128 entries) + length/depth guard
  (200 chars / depth 32, StackOverflowError caught as invalid).

No existing file was modified in this copy. No CSS, Tailwind,
backdrop-blur, translateZ, will-change, or navigator.vibrate was added.

## 3. Integration points (exact file:function + snippet)

### 3.1 Island morph — IslandService.showExpanded / showCollapsed
Constraint: showExpanded() currently calls removeView() first,
destroying the pill. A FLIP morph needs both views alive. Restructure:
```
// In showExpanded(), BEFORE removeView():
View pillView = islandView; // may be null on first show
Rect pillRect = (pillView != null)
        ? SpringAnimator.Flip.measure(pillView) : null;
// ... build card as today, add it to the window ...
card.post(() -> {
    boolean morphed = false;
    if (pillRect != null) {
        morphed = IslandMorphHelper.morphFromPill(pillView, card, () -> {
            // Remove the OLD pill window only after the morph lands.
            // (Current code removes it via removeView(); restructure
            // that call so it removes the pill view, not the card.)
        });
    }
    if (!morphed) IslandMorphHelper.entrance(card, null);
});
```
Do NOT call the old `card.animate().scaleX(0.88f)...180ms Decelerate`
entrance alongside this — the two animations fight over scale/alpha.
Collapse: replace the immediate showCollapsed() on card close with
`IslandMorphHelper.collapse(card, () -> showCollapsed())` if a
scale/alpha exit is wanted; it is an exit, not a reverse FLIP.

### 3.2 Clean Mode cascade — HomeActivity.applyCleanModeState
Current code animates the whole GridView. Replace the animate branch
with a visible-children cascade, keeping the pill/grid visibility
logic exactly as today:
```
// Reveal branch (appsRevealedInCleanMode == true):
gridView.setVisibility(View.VISIBLE);
StaggerHelper.cascadeGridView(gridView, true, null);
// Hide branch:
StaggerHelper.cascadeGridView(gridView, false,
        () -> gridView.setVisibility(View.GONE));
// Note: the hide cascade sets each CHILD gone, not the grid; the
// onAllDone above hides the grid itself, matching today's behaviour.
// If a second toggle arrives mid-cascade, the new cascade's items
// start from their current alpha/scale — do not reset them to 0/0.92
// first, or the interruption snap returns.
```
GridView note: only visible children cascade (see §1). On a phone
home grid all 16 padded slots are normally visible, so the cascade
reads as 16 items at 12ms = 192ms total stagger span. If the grid
scrolls, off-screen slots appear at rest state — acceptable, and
documented here so it is not filed as a bug later.

### 3.3 Press-down + haptics — HomeActivity.launchOrHandleShortcut
Replace the 40ms + OvershootInterpolator(2.0) block with:
```
// At adapter view creation (HomeShortcutAdapter.getView), once:
PressableHelper.attach(childView, v ->
        launchOrHandleShortcut(itemPos, v));
// And REMOVE the scale animation block inside
// launchOrHandleShortcut itself, keeping only the fireShortcut call
// and the "Could not open" toast. Otherwise two animations own scale.
```
Slot/island/folder haptics elsewhere: replace direct
`performHapticFeedback(KEYBOARD_TAP)` calls with
`HapticHelper.tick(view)` only where the new user toggle should
apply; existing LONG_PRESS calls in Home/Folder/AppPicker can stay
as-is (they are already system-managed).

### 3.4 Magnetic snap — IslandService deck cells (NEW drag path required)
There is no drag path today. To wire snapping, first add one, e.g.
long-press on a deck cell starts a drag (mirroring HomeActivity's
long-press drag initiation in HomeShortcutAdapter.getView), then:
```
// On drag start (deck cell view, parent = deck GridLayout):
MagneticSnapHelper.Session session =
        new MagneticSnapHelper.Session(draggedCell);
// On drag move (centre in parent coords, slotCentres = centres of
// the 4-column deck cells in the same coords, radius e.g. 48dp in px):
session.onDragMove(centreX, centreY, slotCentres, snapRadiusPx);
// On drop:
session.onRelease(centreX, centreY, slotCentres, snapRadiusPx,
        slotIndex -> {
            if (slotIndex >= 0) {
                // Reorder ShortcutStore.loadIsland() list, save, rebuild.
            }
        });
```
Until that drag path exists and is tested, do not describe the
island as having magnetic snapping. The HomeActivity drag path
(DRAG_FLAG_OPAQUE, scale 0.85/alpha 0.45 on long-press, target
highlight at 1.15) is the pattern to copy, with its scale animations
replaced by PressableHelper/SpringAnimator if consistency is wanted.

### 3.5 Island glow / capsule states — IslandService
```
// Deck background (replaces the plain GradientDrawable in showExpanded):
card.setBackground(IslandGlowHelper.deckBackground(
        IslandGlowHelper.dominantColor(IconUtil.iconFor(this, firstShortcut)),
        dp(22)));
// Frosted background layer (dedicated background view only, NOT the card):
IslandGlowHelper.applyFrostedBackground(bgView, accent, dp(22));
// Charging ripple: start when triggerChargingPulse sets lastChargingMsg,
// cancel when it clears after 4s:
ValueAnimator ripple = IslandGlowHelper.startChargingRipple(pillView);
// Music indicator: pass the REAL playback boolean, never fake amplitude:
boolean playing = (getActiveMediaController() != null);
ValueAnimator wave = IslandGlowHelper.startMusicWave(barContainer, playing);
// Silent dot:
IslandGlowHelper.applySilentDot(silentDotView, this);
```
The existing charging pill (4s, then rebuild) and media pill
(MediaController title/artist + pause/next) stay as the source of
truth for state; the helpers only add the visual layer on top.

### 3.6 Wallpaper / monochrome tint — HomeActivity.applyDynamicTheming
```
// After the existing DynamicThemeHelper.extractTheme call:
int[] pair = WallpaperTintHelper.monochromePair(this);
// pair[0] = background (monochrome, contrast-checked),
// pair[1] = text colour (white or black, ratio >= 4.5).
// Apply pair[0] to the tinted surface and pair[1] to its text.
// Do NOT apply the monochrome pair to the island deck as well
// unless the deck is deliberately restyled — the deck has its own
// obsidian background in IslandGlowHelper.
```
Contrast is checked, not asserted: if a future tint change breaks
4.5, `WallpaperTintHelper.meetsContrast(fg, bg)` will report it.

### 3.7 Memoized search — AppPickerActivity search path
```
// Once, when the app list is built (not per keystroke):
MemoizedSearch search = new MemoizedSearch();
search.setTargets(appLabels); // labels in display order
// Per keystroke (query < 3 chars -> prefix index, else cached/full):
List<MemoizedSearch.Result> hits = search.search(query);
// Math (calculator card in AppPickerActivity):
MathEvaluator.Result math = search.evaluateMath(query);
// Call search.setTargets(...) again whenever the app list changes
// (install/uninstall/clone change); otherwise stale results persist.
```
Cache-hit speed is the only sub-millisecond claim this supports.
First-time scoring over ~200 apps is a full FuzzyMatcher pass and
must be measured, not claimed.

## 4. What was deliberately NOT done, and why

- No CSS / Tailwind / backdrop-blur-2xl / translateZ(0) / will-change /
  navigator.vibrate: none of those exist in a native Android Java app.
  The Android equivalents are in §2 (hardware layers during animation
  only, performHapticFeedback + VibrationEffect fallback, layered
  drawables + RenderEffect on API 31+).
- No permanent hardware layers: `HardwareLayer.begin` is always paired
  with `end` on animation end. A permanent layer per icon would cost
  a full off-screen buffer per view.
- No width/height/margin/padding animation anywhere: every helper
  animates scale, translation, and alpha only. The one exception to
  watch is the music wave bars, which change layout height per frame
  by design (4 tiny views inside a fixed container) — that is a
  deliberate, bounded exception, not a grid animation, and it should
  be replaced with scaleY if frame-time measurement shows a cost.
- No fake live data: no fake weather, no fake audio waveform, no
  "120Hz" claim for the island (IslandService never calls
  DisplayHelper and never sets a refresh rate on its overlay params;
  the "120Hz GPU Entrance Animation" comment in showExpanded is just
  a 180ms scale/alpha animator). The helpers do not change refresh
  rates; DisplayHelper's hardcoded 120.0f min=max lock (which defeats
  LTPO variable refresh) is left untouched here on purpose.
- No "100% improvement" claim: there is no baseline frame-time
  measurement in this repo to improve 100% from. See §6.
- No modification of existing files in this copy, no commit, no push.
  The security-fix copy (code-v211) is being edited concurrently;
  merging these helpers into it must be done by diff, not by copying
  the whole tree, and the MathEvaluator guard duplication noted in
  §1 must be resolved at merge time.
- No 120fps / sub-millisecond guarantees: see §6.

## 5. API limits, stated plainly

- RenderEffect blur: API 31+ only. Below that, the frosted helper
  degrades to the layered translucent drawable. True live backdrop
  blur behind a WindowManager overlay is not available via the View
  API at all; a screenshot-blur pipeline would be a separate feature.
- VibrationEffect: API 26+ (minSdk here is 28, so the fallback is
  always the modern path in practice; the deprecated vibrate() branch
  is dead code on this app's minSdk and is kept only for clarity).
- WallpaperColors: API 27+ (DynamicThemeHelper already guards this;
  below that, the indigo fallback applies).
- Choreographer spring: frame-rate dependent by design (it runs on
  the display vsync). On a 120Hz LTPO panel it steps at 120Hz; on a
  60Hz panel it steps at 60Hz. The spring constants do not change
  with refresh rate, but the visual smoothness does — which is why
  §6 requires on-device measurement.

## 6. What still needs a real OnePlus 13 + adb check (NOT verified here)

- Frame time / jank: `adb shell dumpsys gfxinfo <package> framestats`
  (or a FrameMetrics listener) during island expand/collapse, Clean
  Mode reveal/hide, and home drag, on the OnePlus 13 at 120Hz.
  Until that is measured, do NOT claim 120fps, 100% improvement, or
  "zero layout thrashing" as a measured result — the helpers are
  designed to avoid layout work, but design is not measurement.
- Spring feel: stiffness 400 / damping 28 / mass 0.8 (zeta 0.78)
  overshoots once by design. Whether that overshoot feels "flagship"
  or "bouncy" on a real phone is a judgement call that needs a
  human thumb, not a formula. If it feels too bouncy, raise damping
  toward 32-35 (zeta ~0.9-0.98) in SpringAnimator constants — one
  place, all helpers follow.
- FLIP morph end-to-end: the restructure in §3.1 (keep pill alive
  until morph lands) has not been applied or tested. The helper is
  correct in isolation; the window-swap timing in IslandService is
  the untested part.
- Magnetic snap: no drag path exists yet (§3.4). Snap radius,
  haptic tick rate, and reorder persistence all need device testing.
- Music wave / charging ripple / silent dot: visual only, need a
  phone in each state (charging, playing, silent) to confirm the
  indicators appear and cancel correctly (especially the ripple,
  which repeats infinitely until cancelled).
- Memoized search: measure first-query vs repeat-query time on a
  real app list (hundreds of apps, including clones) before quoting
  any millisecond figure. Cache hits will be fast; first-time
  scoring will not be sub-millisecond, and this doc does not claim
  otherwise.
- Haptics toggle: confirm the new "dock" prefs key does not collide
  with an existing key (searched: no existing "motion_haptics_enabled"
  key in ShortcutStore/IslandService) and that the toggle is surfaced
  in Settings — the helper reads it, but no Settings UI was added
  in this pass.
- Build: helpers were syntax-checked against android.jar (see parent
  report). A full aapt2/javac/d8 build and install on the OnePlus 13
  is still required before any release, and the security-fix merge
  (§4) must happen first or the two copies will diverge further.
