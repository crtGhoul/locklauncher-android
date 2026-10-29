# Lock Launcher for Android

[![Release](https://img.shields.io/badge/Release-v2.11-7C5CFF.svg)](https://github.com/crtGhoul/locklauncher-android/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Zero Ads](https://img.shields.io/badge/Ads-0%25%20Offline-00E676.svg)](README.md)
[![Refresh Rate](https://img.shields.io/badge/Display-120Hz%20LTPO-FF6F00.svg)](README.md)

**Lock Launcher** is a flagship, privacy-first, 100% offline Android launcher and overlay system designed for speed, minimalism, and full control over your apps.

No ads. No telemetry. No background phoning home. Zero network trackers.

---

## ✨ Key Features

### 🏝️ Dynamic Island Overlay (v2.11)
- **Minimalist Collapsed Capsule**: A sleek, unobtrusive obsidian black pill displaying live battery status or system state without cluttering app icons.
- **Click-to-Expand Deck**: Tap the capsule to smoothly open a symmetrical 4-column glass deck of your chosen Island apps.
- **Built-in `⚙️ Organize` Suite**:
  - **🔀 Swap Positions**: Tap any app and choose another slot to instantly swap positions.
  - **🔤 Sort A–Z**: Sort your Island deck alphabetically with one tap.
  - **⬅️ / ➡️ Reorder**: Shift apps left or right one slot at a time.
  - **➕ Add / 🗑️ Remove**: Add new apps or prune existing ones without leaving the overlay.
- **Silent Foreground Service**: Backed by an `IMPORTANCE_MIN` foreground service (`specialUse`), ensuring Android 14 & 15 / OxygenOS 15 never kill or freeze the island in the background.

### 🏠 Flagship Home Launcher
- **Home Screen Slots**: 16 customizable slots per profile, supporting apps, web shortcuts, direct phone/SMS dialers, and settings panels.
- **Clean Mode (`🚀 Apps` Pill)**: Hide the app grid behind an elegant floating pill on your home screen; tap to smoothly slide apps into view, tap again to tuck them away.
- **Interactive 2×2 Folders**: Group related tools into named folders with pop-up modal access.
- **Live Android App Widgets**: Host any system or third-party widget with custom height resizing and reordering.
- **Swipe Gestures**: Swipe up to open the searchable app drawer, swipe down for the notification shade, double-tap to lock (via Accessibility Service).

### ⚡ 0ms Instant App Launching
- Powered by native `LauncherApps.startMainActivity()` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`.
- Automatic keyguard dismiss integration (`KeyguardManager.requestDismissKeyguard`) to smoothly prompt biometric unlock when launching from lock screens or overlay mode.
- Bypasses OEM transition stalls and clip-reveal animation freezes.

### 🚀 Buttery Smooth 120Hz LTPO GPU Acceleration
- **GPU Hardware Accelerated**: Overlay windows rendered with `WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED` for true 120 FPS frame delivery on LTPO AMOLED displays (e.g. OnePlus 13).
- **Background Pre-Warming**: Pre-caches shortcut bitmaps in memory via `AsyncTask.THREAD_POOL_EXECUTOR` so grid binding never drops a single frame.
- **High-Refresh Display Lock**: Automatically locks display refresh rate to 120.0f without mode switching jitter.

### 🌐 Standalone Ad-Free Web Apps
- Turn any website into a standalone native app window with dark glass styling and independent Recents task card.
- Built-in `AdBlockEngine` stripping banner frames, intrusive popups, and ad domains locally.
- Auto-fetches high-res brand icons (YouTube, X, Reddit, Spotify, etc.) or custom logos from gallery.

### 👥 Cloned Apps & Multi-User Profile Isolation
- Full support for dual/cloned apps (e.g. OxygenOS Parallel Apps, Work Profiles) with user profile serial isolation (`profileSerial`), allowing identical apps to exist simultaneously with independent custom labels.

---

## 🛠️ Building from Source

Lock Launcher builds cleanly without heavy build systems or Gradle daemon overhead.

### Prerequisites
- Android SDK Build-Tools (e.g. `34.0.0`)
- Android Platform (`android-34`)
- JDK 17

### Build with PowerShell (Windows)
```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

### Build with Bash (Linux / macOS)
```bash
./build-apk.sh
```

The script compiles via `aapt2`, compiles Java with `javac`, converts bytecode with `d8`, aligns with `zipalign`, and signs with `apksigner` to produce `locklauncher.apk`.

---

## 📲 Installation

Grab the latest compiled APK from the [Releases](https://github.com/crtGhoul/locklauncher-android/releases) section or the [Velazquez Lab Showcase](https://velazquez-lab.pages.dev/).

```bash
adb install -r locklauncher.apk
```

### Android 13+ Permissions Note:
To enable the Dynamic Island overlay:
1. Go to **Settings → Apps → Lock Launcher**.
2. Tap the top-right three dots **(⋮)** → **Allow restricted settings**.
3. Toggle on **Display over other apps**.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
