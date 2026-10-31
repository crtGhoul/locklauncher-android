import React, { useState, useEffect } from 'react';
import {
  Shield,
  Play,
  Pause,
  Lock,
  Unlock,
  Check,
  Copy,
  Search,
  ArrowUpDown,
  ArrowLeft,
  ArrowRight,
  RotateCw,
  Maximize2,
  Minimize2,
  X,
  UserCheck,
  FastForward,
  SlidersHorizontal,
  Clock,
  PictureInPicture2,
  EyeOff,
  Trash2,
  Plus,
  Download,
  ChevronDown,
  ChevronUp,
  SkipBack,
  SkipForward,
  ExternalLink,
  Music2,
} from 'lucide-react';
import { analyzeUrlInterception, JAVA_SNIPPETS } from './adBlockLogic';
import { usePWAInstall } from './usePWAInstall';

interface AppShortcut {
  id: string;
  name: string;
  url?: string;
  category: 'web' | 'system';
  code: string;
  subtitle: string;
  installedStandalone?: boolean;
}

interface HomeWidgetItem {
  id: number;
  label: string;
  size: string;
}

const INITIAL_SHORTCUTS: AppShortcut[] = [
  {
    id: 'ytm',
    name: 'YT Music',
    url: 'https://music.youtube.com/watch?v=dQw4w9WgXcQ',
    category: 'web',
    code: 'YM',
    subtitle: 'Standalone App · MediaSession · Ad-Strip',
    installedStandalone: true,
  },
  {
    id: 'yt',
    name: 'YouTube',
    url: 'https://m.youtube.com/watch?v=locklauncher_120hz',
    category: 'web',
    code: 'YT',
    subtitle: 'Standalone App · SponsorBlock · 120Hz',
    installedStandalone: true,
  },
  {
    id: 'chatgpt',
    name: 'ChatGPT',
    url: 'https://chatgpt.com/',
    category: 'web',
    code: 'AI',
    subtitle: 'Standalone App · OAuth Transport',
    installedStandalone: true,
  },
  {
    id: 'reddit',
    name: 'Reddit',
    url: 'https://www.reddit.com/r/androiddev',
    category: 'web',
    code: 'RD',
    subtitle: 'Standalone App · Promo Stripping',
    installedStandalone: true,
  },
  {
    id: 'github',
    name: 'GitHub',
    url: 'https://github.com/crtGhoul/locklauncher-android',
    category: 'web',
    code: 'GH',
    subtitle: 'Auto-Release v2.21 · Blob Saver',
    installedStandalone: true,
  },
  {
    id: 'dialer',
    name: 'Phone',
    category: 'system',
    code: 'PH',
    subtitle: '0ms Native LauncherApps Intent',
  },
  {
    id: 'camera',
    name: 'Camera',
    category: 'system',
    code: 'CM',
    subtitle: 'Keyguard Dismiss Hardware Shortcut',
  },
  {
    id: 'settings',
    name: 'Settings',
    category: 'system',
    code: 'OS',
    subtitle: '120Hz LTPO & Profile Serial Isolation',
  },
];

const PRESET_TEST_URLS = [
  {
    label: 'YT Music InnerTube Player (Ad Stripped)',
    url: 'https://music.youtube.com/youtubei/v1/player?key=AIzaSyA',
  },
  {
    label: 'SponsorBlock Skip Segments API',
    url: 'https://sponsor.ajay.app/api/skipSegments?videoID=dQw4w9WgXcQ',
  },
  {
    label: 'YouTube Pre-Roll Ad Break (Blocked)',
    url: 'https://m.youtube.com/youtubei/v1/player/ad_break?key=AIzaSyA',
  },
  {
    label: 'YouTube Audio/Video Stream (Protected)',
    url: 'https://rr2---sn-ab5l6nzy.googlevideo.com/videoplayback?expire=173999&mime=audio/mp4',
  },
  {
    label: 'Google OAuth Account Chooser (Desktop UA)',
    url: 'https://accounts.google.com/o/oauth2/v2/auth?client_id=youtube_web',
  },
  {
    label: 'DoubleClick Ad SDK (Blocked)',
    url: 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js',
  },
];

const TRACK_PLAYLIST = [
  { title: 'Midnight City (120Hz Remaster)', artist: 'M83 · YT Music Standalone' },
  { title: 'Starboy (Lossless Audio Stream)', artist: 'The Weeknd · YT Music Standalone' },
  { title: 'Veridis Quo (Zero-Ad Mix)', artist: 'Daft Punk · YT Music Standalone' },
];

interface WallpaperPreset {
  id: 'aurora' | 'emerald' | 'ember' | 'crimson';
  name: string;
  screenBg: string;
  orbPrimary: string;
  orbSecondary: string;
  tilePlateBg: string;
  tileBorder: string;
  glyphPlateBg: string;
  glyphBorder: string;
  glyphText: string;
  accentText: string;
  musicSquareBg: string;
}

const WALLPAPER_PRESETS: WallpaperPreset[] = [
  {
    id: 'aurora',
    name: 'Aurora Indigo',
    screenBg: 'bg-gradient-to-br from-[#0B1026] via-[#131836] to-[#090B14]',
    orbPrimary: 'bg-indigo-500/30',
    orbSecondary: 'bg-sky-500/25',
    tilePlateBg: 'bg-indigo-950/35 backdrop-blur-md',
    tileBorder: 'border-indigo-400/35 hover:border-indigo-300/60',
    glyphPlateBg: 'bg-gradient-to-br from-indigo-500/30 to-sky-500/20 backdrop-blur-md',
    glyphBorder: 'border-indigo-300/45',
    glyphText: 'text-indigo-100',
    accentText: 'text-indigo-300',
    musicSquareBg: 'from-indigo-950/65 via-slate-900/75 to-sky-950/50 border-indigo-400/35',
  },
  {
    id: 'emerald',
    name: 'Cyber Mint',
    screenBg: 'bg-gradient-to-br from-[#061F1C] via-[#0B2926] to-[#070E10]',
    orbPrimary: 'bg-emerald-500/30',
    orbSecondary: 'bg-teal-400/25',
    tilePlateBg: 'bg-emerald-950/35 backdrop-blur-md',
    tileBorder: 'border-emerald-400/35 hover:border-emerald-300/60',
    glyphPlateBg: 'bg-gradient-to-br from-emerald-500/30 to-teal-500/20 backdrop-blur-md',
    glyphBorder: 'border-emerald-300/45',
    glyphText: 'text-emerald-100',
    accentText: 'text-emerald-300',
    musicSquareBg: 'from-emerald-950/65 via-zinc-900/75 to-teal-950/50 border-emerald-400/35',
  },
  {
    id: 'ember',
    name: 'Obsidian Sunset',
    screenBg: 'bg-gradient-to-br from-[#26100B] via-[#2E1610] to-[#0D0809]',
    orbPrimary: 'bg-amber-500/30',
    orbSecondary: 'bg-rose-500/25',
    tilePlateBg: 'bg-amber-950/35 backdrop-blur-md',
    tileBorder: 'border-amber-400/35 hover:border-amber-300/60',
    glyphPlateBg: 'bg-gradient-to-br from-amber-500/30 to-orange-500/20 backdrop-blur-md',
    glyphBorder: 'border-amber-300/45',
    glyphText: 'text-amber-100',
    accentText: 'text-amber-300',
    musicSquareBg: 'from-amber-950/65 via-zinc-900/75 to-rose-950/50 border-amber-400/35',
  },
  {
    id: 'crimson',
    name: 'Midnight Rose',
    screenBg: 'bg-gradient-to-br from-[#250A1C] via-[#2B1126] to-[#0A070E]',
    orbPrimary: 'bg-rose-500/30',
    orbSecondary: 'bg-fuchsia-500/25',
    tilePlateBg: 'bg-rose-950/35 backdrop-blur-md',
    tileBorder: 'border-rose-400/35 hover:border-rose-300/60',
    glyphPlateBg: 'bg-gradient-to-br from-rose-500/30 to-fuchsia-500/20 backdrop-blur-md',
    glyphBorder: 'border-rose-300/45',
    glyphText: 'text-rose-100',
    accentText: 'text-rose-300',
    musicSquareBg: 'from-rose-950/65 via-zinc-900/75 to-fuchsia-950/50 border-rose-400/35',
  },
];

function evaluateQuickMath(expr: string): string | null {
  const cleaned = expr.replace(/\s+/g, '');
  if (!/^[0-9+\-*/().^%]+$/.test(cleaned) || !/[+\-*/^%]/.test(cleaned)) {
    return null;
  }
  try {
    const normalized = cleaned.replace(/\^/g, '**');
    const fn = new Function(`"use strict"; return (${normalized});`);
    const result = fn();
    if (typeof result === 'number' && Number.isFinite(result)) {
      return Number.isInteger(result) ? String(result) : result.toFixed(4).replace(/\.?0+$/, '');
    }
    return null;
  } catch {
    return null;
  }
}

export default function App() {
  const { isInstallable, isInstalled, promptInstall } = usePWAInstall();
  const [pwaToast, setPwaToast] = useState<string | null>(null);

  // Simulator states
  const [islandExpanded, setIslandExpanded] = useState(false);
  const [islandOrganizeMode, setIslandOrganizeMode] = useState(false);
  const [topLeftShadeOpen, setTopLeftShadeOpen] = useState(false);
  const [shortcuts, setShortcuts] = useState<AppShortcut[]>(INITIAL_SHORTCUTS);
  const [selectedSwapIndex, setSelectedSwapIndex] = useState<number | null>(null);
  const [cleanModeHidden, setCleanModeHidden] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Home Widgets state (mirrors HomeActivity.java widget manager)
  const [homeWidgets, setHomeWidgets] = useState<HomeWidgetItem[]>([
    { id: 101, label: 'Lock Launcher 4x1 Dock', size: '4x1' },
  ]);
  const [widgetMenuOpen, setWidgetMenuOpen] = useState(false);

  // WebAppActivity container state (defaults to Installed Standalone Mode with Top Bar hidden!)
  const [activeWebApp, setActiveWebApp] = useState<AppShortcut | null>(null);
  const [lastMediaWebApp, setLastMediaWebApp] = useState<AppShortcut>(INITIAL_SHORTCUTS[0]);
  const [topBarVisible, setTopBarVisible] = useState(false);
  const [isScreenLocked, setIsScreenLocked] = useState(false);
  const [isPlayingMedia, setIsPlayingMedia] = useState(true);
  const [trackIndex, setTrackIndex] = useState(0);
  const [playbackSeconds, setPlaybackSeconds] = useState(142);
  const [isImmersiveFullscreen, setIsImmersiveFullscreen] = useState(false);
  const [adBreakSimulated, setAdBreakSimulated] = useState(false);
  const [powerMenuOpen, setPowerMenuOpen] = useState(false);
  const [audioOnlyMode, setAudioOnlyMode] = useState(false);
  const [sleepTimerMin, setSleepTimerMin] = useState<number>(0);
  const [pipActive, setPipActive] = useState(false);
  const [adSkipLog, setAdSkipLog] = useState<string>(
    'Standalone Installed Mode active: Top bar tucked away; MediaSession attached to Notification.MediaStyle for Top-Left Music Square.'
  );
  const [activeGoogleAccount, setActiveGoogleAccount] = useState('crtghoul@gmail.com');
  const [oauthModalOpen, setOauthModalOpen] = useState(false);

  // Wallpaper-Adaptive Chameleon Icon Blending state (v2.22)
  const [wallpaperIndex, setWallpaperIndex] = useState(0);
  const [wallpaperBlendIcons, setWallpaperBlendIcons] = useState(true);
  const [wallpaperBlendIntensity, setWallpaperBlendIntensity] = useState(65);
  const activeWallpaper = WALLPAPER_PRESETS[wallpaperIndex % WALLPAPER_PRESETS.length];

  // Workbench states
  const [activeWorkbenchTab, setActiveWorkbenchTab] = useState<'inspector' | 'code'>('inspector');
  const [probeUrl, setProbeUrl] = useState(PRESET_TEST_URLS[0].url);
  const [selectedCodeTab, setSelectedCodeTab] = useState<keyof typeof JAVA_SNIPPETS>('wallpaperChameleonBlend');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const currentTrack = TRACK_PLAYLIST[trackIndex % TRACK_PLAYLIST.length];

  // Advance simulated media timer even when screen is locked or on Home screen (proving background playback)
  useEffect(() => {
    if (!isPlayingMedia) return;
    const timer = window.setInterval(() => {
      setPlaybackSeconds((prev) => prev + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [isPlayingMedia]);

  const showToastMessage = (msg: string) => {
    setPwaToast(msg);
    setTimeout(() => setPwaToast(null), 3200);
  };

  // Unified "Tap to Open" handler (mirrors NowPlayingHelper.openActivePlayer)
  const handleTapToOpenActivePlayer = () => {
    const targetApp = activeWebApp || lastMediaWebApp || shortcuts[0];
    setActiveWebApp(targetApp);
    setIslandExpanded(false);
    setTopLeftShadeOpen(false);
    setPipActive(false);
    setAdSkipLog(
      `NowPlayingHelper.openActivePlayer(): Resumed ${targetApp.name} via singleTask onNewIntent() + Android 14/15 BAL-allowed PendingIntent without reloading stream.`
    );
  };

  const handleNextTrack = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setTrackIndex((prev) => (prev + 1) % TRACK_PLAYLIST.length);
    setPlaybackSeconds(0);
  };

  const handlePrevTrack = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setTrackIndex((prev) => (prev - 1 + TRACK_PLAYLIST.length) % TRACK_PLAYLIST.length);
    setPlaybackSeconds(0);
  };

  const handleTogglePlay = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setIsPlayingMedia((prev) => !prev);
  };

  const handleInstallCurrentWebApp = () => {
    if (!activeWebApp) return;
    setTopBarVisible(false);
    setShortcuts((prev) =>
      prev.map((s) => (s.id === activeWebApp.id ? { ...s, installedStandalone: true } : s))
    );
    showToastMessage(`Installed ${activeWebApp.name} as Standalone App (Top Bar hidden by default)`);
    setAdSkipLog(
      `installCurrentWebAppAsStandalone(): Pinned ${activeWebApp.name} to Home Grid & Launcher with ShortcutStore.setWebAppTopBarVisible(false).`
    );
  };

  const handlePWAHeaderInstall = async () => {
    if (isInstallable) {
      const ok = await promptInstall();
      if (ok) {
        showToastMessage('Lock Launcher Workbench installed as Standalone App!');
      }
    } else {
      showToastMessage(
        'PWA Manifest & Service Worker active — open in Chrome/Edge mobile menu -> "Add to Home screen / Install App".'
      );
    }
  };

  const handleCopyCode = (key: keyof typeof JAVA_SNIPPETS) => {
    navigator.clipboard.writeText(JAVA_SNIPPETS[key]);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1800);
  };

  const handleSortIslandApps = () => {
    setShortcuts((prev) => [...prev].sort((a, b) => a.name.localeCompare(b.name)));
    setSelectedSwapIndex(null);
  };

  const handleShiftApp = (index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= shortcuts.length) return;
    const next = [...shortcuts];
    const temp = next[index];
    next[index] = next[target];
    next[target] = temp;
    setShortcuts(next);
  };

  const handleSlotTap = (app: AppShortcut, idx: number) => {
    if (islandOrganizeMode) {
      if (selectedSwapIndex === null) {
        setSelectedSwapIndex(idx);
      } else {
        const next = [...shortcuts];
        const temp = next[selectedSwapIndex];
        next[selectedSwapIndex] = next[idx];
        next[idx] = temp;
        setShortcuts(next);
        setSelectedSwapIndex(null);
      }
      return;
    }

    if (app.url) {
      setActiveWebApp(app);
      if (app.id === 'yt' || app.id === 'ytm') {
        setLastMediaWebApp(app);
        setIsPlayingMedia(true);
      }
      setIslandExpanded(false);
      setTopLeftShadeOpen(false);
      setPipActive(false);
    } else {
      setActiveWebApp(null);
      showToastMessage(`Launched native ${app.name} via IconUtil.fireShortcut() direct startActivity`);
    }
  };

  const triggerSimulatedPreRoll = () => {
    setAdBreakSimulated(true);
    setAdSkipLog(
      'SponsorBlock + InnerTube Guard: Skipped [14.2s -> 42.8s] sponsor segment & stripped adPlacements (0ms interruption).'
    );
    setPlaybackSeconds((prev) => prev + 28);
    setTimeout(() => {
      setAdBreakSimulated(false);
    }, 800);
  };

  const handleSwitchOAuthAccount = (newEmail: string) => {
    setActiveGoogleAccount(newEmail);
    setOauthModalOpen(false);
    setAdSkipLog(
      `Desktop Chrome 132 UA + onCreateWindow WebViewTransport synced cookies for ${newEmail} via CookieManager.flush().`
    );
  };

  const analysis = analyzeUrlInterception(probeUrl);
  const mathOutput = evaluateQuickMath(searchQuery);
  const filteredShortcuts = shortcuts.filter(
    (s) =>
      s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.subtitle.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const activeHost = activeWebApp?.url
    ? new URL(activeWebApp.url).hostname.replace(/^www\./, '')
    : 'music.youtube.com';

  return (
    <div className="min-h-screen bg-[#090A0F] text-zinc-100 flex flex-col">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="flex items-center justify-between gap-8 px-6 py-3.5 border-b border-zinc-800/80 bg-[#090A0F]/95 backdrop-blur-md sticky top-0 z-30">
        <div className="flex items-center gap-3 shrink-0">
          <a
            href="#top"
            className="text-base font-bold tracking-tight text-white font-display whitespace-nowrap"
          >
            LOCK LAUNCHER
          </a>
          <span className="text-xs font-mono text-emerald-400 tabular-nums">v2.22 · BUILD 60</span>
        </div>

        <nav className="hidden md:flex items-center gap-6 text-xs font-medium text-zinc-400">
          <button
            onClick={() => {
              setActiveWebApp(null);
              setTopLeftShadeOpen(false);
              setIslandExpanded(false);
            }}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              !activeWebApp && !topLeftShadeOpen ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            Wallpaper-Blended Home
          </button>
          <button
            onClick={() => {
              setTopLeftShadeOpen((prev) => !prev);
              setIslandExpanded(false);
            }}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              topLeftShadeOpen ? 'text-sky-400 underline underline-offset-8' : ''
            }`}
          >
            Top-Left Music Shade
          </button>
          <button
            onClick={() => {
              setActiveWebApp(shortcuts[0]);
              setIsPlayingMedia(true);
              setTopLeftShadeOpen(false);
            }}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWebApp ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            Installed WebApp (Top Bar)
          </button>
          <button
            onClick={() => setActiveWorkbenchTab('code')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWorkbenchTab === 'code' ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            v2.22 Java Source
          </button>
        </nav>

        <div className="flex items-center gap-2.5 shrink-0">
          {!isInstalled && (
            <button
              onClick={handlePWAHeaderInstall}
              className="px-3 py-1.5 text-xs font-semibold text-sky-300 bg-sky-500/15 border border-sky-500/30 rounded-md hover:bg-sky-500/25 transition-all active:scale-95 whitespace-nowrap shrink-0 flex items-center gap-1.5"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Install PWA</span>
            </button>
          )}
          <button
            onClick={() => {
              setActiveWorkbenchTab('code');
              handleCopyCode(selectedCodeTab);
            }}
            className="px-3.5 py-1.5 text-xs font-medium text-zinc-950 bg-zinc-100 rounded-md hover:bg-white transition-all active:scale-95 whitespace-nowrap shrink-0 flex items-center gap-1.5"
          >
            {copiedKey ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedKey ? 'Copied Source' : 'Copy Source'}</span>
          </button>
        </div>
      </header>

      {/* Floating Toast Feedback */}
      {pwaToast && (
        <div className="fixed bottom-5 right-5 z-50 max-w-sm px-4 py-2.5 rounded-xl bg-zinc-900/95 border border-sky-500/40 text-xs text-zinc-100 shadow-2xl backdrop-blur-md flex items-center gap-2.5 animate-in fade-in slide-in-from-bottom-2">
          <Check className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{pwaToast}</span>
        </div>
      )}

      {/* Main 1440px Multi-Column Workbench */}
      <main className="max-w-7xl w-full mx-auto px-6 py-7 grid grid-cols-1 lg:grid-cols-12 gap-8 flex-1 items-start">
        {/* Left 5 Columns: Hardware Device Frame (OnePlus 13 · OxygenOS 15) */}
        <section className="lg:col-span-5 flex flex-col items-center">
          <div className="w-full max-w-[400px] rounded-[32px] border border-zinc-800 bg-[#11131A] p-4 shadow-2xl">
            {/* Hardware Frame Top Control Bar */}
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-zinc-800/80 text-xs text-zinc-400">
              <div className="flex items-center gap-2">
                <span className="font-mono text-[11px] text-zinc-300">ONEPLUS 13 · OXYGENOS 15</span>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={() => {
                    setTopLeftShadeOpen((prev) => !prev);
                    setIslandExpanded(false);
                  }}
                  className={`px-2 py-1 rounded text-[11px] font-medium transition-all active:scale-95 flex items-center gap-1 whitespace-nowrap shrink-0 ${
                    topLeftShadeOpen
                      ? 'bg-sky-500/20 text-sky-300 border border-sky-500/40'
                      : 'bg-zinc-800/90 text-zinc-300 hover:bg-zinc-700'
                  }`}
                  title="Simulate swiping down from Top-Left to open the System Music Square"
                >
                  <Music2 className="w-3 h-3" />
                  <span>Top-Left Swipe ▾</span>
                </button>
                <button
                  onClick={() => setIsScreenLocked((prev) => !prev)}
                  className={`px-2 py-1 rounded text-[11px] font-medium transition-all active:scale-95 flex items-center gap-1 whitespace-nowrap shrink-0 ${
                    isScreenLocked
                      ? 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                      : 'bg-zinc-800/90 text-zinc-300 hover:bg-zinc-700'
                  }`}
                >
                  {isScreenLocked ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
                  <span>{isScreenLocked ? 'Locked' : 'Lock'}</span>
                </button>
                {activeWebApp && (
                  <button
                    onClick={() => setActiveWebApp(null)}
                    className="px-2 py-1 rounded bg-zinc-800/90 text-zinc-300 hover:bg-zinc-700 text-[11px] font-medium transition-all active:scale-95 whitespace-nowrap shrink-0"
                  >
                    Home
                  </button>
                )}
              </div>
            </div>

            {/* Viewport Screen Area with Live Wallpaper Backdrop & Chameleon Ambient Orbs */}
            <div
              className={`relative h-[660px] rounded-[24px] ${activeWallpaper.screenBg} border border-zinc-800/90 overflow-hidden flex flex-col justify-between transition-colors duration-500`}
            >
              {/* Ambient Wallpaper Glow Layers */}
              <div
                className={`pointer-events-none absolute -top-16 -left-16 w-56 h-56 rounded-full ${activeWallpaper.orbPrimary} blur-3xl transition-all duration-500`}
              />
              <div
                className={`pointer-events-none absolute bottom-10 -right-16 w-60 h-60 rounded-full ${activeWallpaper.orbSecondary} blur-3xl transition-all duration-500`}
              />
              {/* Top-Left Swipe-Down System Notification Shade & Music Square Overlay */}
              {topLeftShadeOpen && (
                <div className="absolute inset-0 z-30 bg-[#07080D]/95 backdrop-blur-xl p-4 flex flex-col justify-between animate-in fade-in slide-in-from-top-3 duration-200">
                  <div>
                    <div className="flex items-center justify-between pb-3 mb-3 border-b border-zinc-800">
                      <div>
                        <span className="text-[11px] font-mono text-sky-400 uppercase tracking-wider">
                          Top-Left Swipe · Quick Settings Shade
                        </span>
                        <h3 className="text-sm font-bold text-white">
                          Android 15 MediaSession Music Square
                        </h3>
                      </div>
                      <button
                        onClick={() => setTopLeftShadeOpen(false)}
                        className="p-1.5 rounded-lg bg-zinc-800/80 text-zinc-300 hover:text-white transition-all active:scale-95"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>

                    {/* OxygenOS 15 / Nothing OS System Music Square Card */}
                    <div
                      onClick={handleTapToOpenActivePlayer}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === 'Enter' && handleTapToOpenActivePlayer()}
                      className="cursor-pointer rounded-2xl bg-gradient-to-br from-zinc-900 via-zinc-900/95 to-rose-950/30 border border-zinc-700/80 hover:border-rose-500/50 p-4 shadow-2xl transition-all active:scale-[0.99]"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex items-center gap-3">
                          <div className="w-14 h-14 rounded-2xl bg-rose-600 flex flex-col items-center justify-center text-white font-bold shadow-lg shrink-0">
                            <span className="text-xs font-mono">YTM</span>
                            <span className="text-[9px] font-mono opacity-80">LIVE</span>
                          </div>
                          <div>
                            <div className="flex items-center gap-1.5">
                              <span className="inline-block w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                              <span className="text-[10px] font-mono text-emerald-400 uppercase">
                                MediaSession.Token Active
                              </span>
                            </div>
                            <h4 className="text-sm font-bold text-white mt-0.5 line-clamp-1">
                              {currentTrack.title}
                            </h4>
                            <p className="text-xs text-zinc-300 line-clamp-1">
                              {currentTrack.artist}
                            </p>
                          </div>
                        </div>
                        <span className="px-2 py-1 rounded-full bg-white/10 text-[10px] font-semibold text-white flex items-center gap-1 shrink-0">
                          <span>Tap to Open</span>
                          <ExternalLink className="w-3 h-3" />
                        </span>
                      </div>

                      {/* Progress + Transport Bar inside Music Square */}
                      <div className="mt-4">
                        <div className="h-1.5 w-full rounded-full bg-zinc-800 overflow-hidden">
                          <div className="h-full w-2/3 bg-rose-500 rounded-full" />
                        </div>
                        <div className="flex items-center justify-between text-[10px] font-mono text-zinc-400 mt-1">
                          <span>{formatTime(playbackSeconds)}</span>
                          <span>04:12</span>
                        </div>
                      </div>

                      <div className="mt-3 pt-3 border-t border-zinc-800/80 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={handlePrevTrack}
                            className="w-9 h-9 rounded-full bg-zinc-800 hover:bg-zinc-700 text-white flex items-center justify-center transition-transform active:scale-90"
                            title="Previous Track"
                          >
                            <SkipBack className="w-4 h-4" />
                          </button>
                          <button
                            onClick={handleTogglePlay}
                            className="w-10 h-10 rounded-full bg-rose-500 hover:bg-rose-400 text-white flex items-center justify-center transition-transform active:scale-90 shadow-md"
                            title="Play / Pause"
                          >
                            {isPlayingMedia ? (
                              <Pause className="w-4 h-4" />
                            ) : (
                              <Play className="w-4 h-4" />
                            )}
                          </button>
                          <button
                            onClick={handleNextTrack}
                            className="w-9 h-9 rounded-full bg-zinc-800 hover:bg-zinc-700 text-white flex items-center justify-center transition-transform active:scale-90"
                            title="Next Track"
                          >
                            <SkipForward className="w-4 h-4" />
                          </button>
                        </div>

                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleTapToOpenActivePlayer();
                          }}
                          className="px-3 py-1.5 rounded-xl bg-zinc-100 hover:bg-white text-zinc-950 font-semibold text-xs flex items-center gap-1.5 transition-transform active:scale-95"
                        >
                          <span>Open Player</span>
                          <ExternalLink className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    <div className="mt-3 p-3 rounded-xl bg-zinc-900/70 border border-zinc-800 text-[11px] text-zinc-300 space-y-1">
                      <p className="font-semibold text-white">
                        Why the Top-Left Music Square now works on Android 14/15:
                      </p>
                      <p className="text-zinc-400 leading-relaxed">
                        <code className="text-zinc-200">WebAppActivity</code> now initializes a real{' '}
                        <code className="text-zinc-200">android.media.session.MediaSession</code> with{' '}
                        <code className="text-zinc-200">PlaybackState</code> &amp;{' '}
                        <code className="text-zinc-200">MediaMetadata</code> and attaches{' '}
                        <code className="text-zinc-200">mediaSession.getSessionToken()</code> to{' '}
                        <code className="text-zinc-200">Notification.MediaStyle</code>.
                      </p>
                    </div>
                  </div>

                  <button
                    onClick={() => setTopLeftShadeOpen(false)}
                    className="w-full py-2 rounded-xl bg-zinc-900 border border-zinc-800 text-xs font-medium text-zinc-300 hover:text-white"
                  >
                    Swipe Up to Close Shade ▲
                  </button>
                </div>
              )}

              {/* Dynamic Island Overlay Capsule */}
              <div className="pt-2.5 px-3 z-20">
                {!islandExpanded ? (
                  <div className="mx-auto w-fit flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-zinc-950/95 border border-zinc-800 text-[11px] text-zinc-200 hover:border-zinc-600 transition-all shadow-lg">
                    <button
                      onClick={() => setIslandExpanded(true)}
                      className="flex items-center gap-2.5 text-left"
                    >
                      <span className="w-2 h-2 rounded-full bg-sky-400 animate-pulse" />
                      <span className="font-medium text-zinc-100 truncate max-w-[165px]">
                        {isPlayingMedia
                          ? `🎵 ${currentTrack.title}`
                          : 'Dynamic Island · 94%'}
                      </span>
                    </button>
                    {isPlayingMedia && (
                      <div className="flex items-center gap-1 pl-1.5 border-l border-zinc-800">
                        <button
                          onClick={handleTogglePlay}
                          className="p-1 rounded-full hover:bg-zinc-800 text-rose-400 transition-transform active:scale-90"
                          title="Play/Pause"
                        >
                          {isPlayingMedia ? (
                            <Pause className="w-3 h-3" />
                          ) : (
                            <Play className="w-3 h-3" />
                          )}
                        </button>
                        <button
                          onClick={handleNextTrack}
                          className="p-1 rounded-full hover:bg-zinc-800 text-zinc-300 transition-transform active:scale-90"
                          title="Next Track"
                        >
                          <SkipForward className="w-3 h-3" />
                        </button>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="rounded-2xl bg-zinc-900/95 backdrop-blur-xl border border-zinc-700/80 p-3 shadow-2xl animate-in zoom-in-95 duration-150">
                    <div className="flex items-center justify-between pb-2 mb-2 border-b border-zinc-800">
                      <span className="text-xs font-semibold text-white">
                        🏝️ Dynamic Island · Music Square &amp; Deck
                      </span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => {
                            setIslandOrganizeMode((prev) => !prev);
                            setSelectedSwapIndex(null);
                          }}
                          className={`px-2 py-1 rounded text-[11px] font-medium transition-colors whitespace-nowrap shrink-0 ${
                            islandOrganizeMode
                              ? 'bg-zinc-100 text-zinc-950'
                              : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                          }`}
                        >
                          {islandOrganizeMode ? 'Done' : 'Organize'}
                        </button>
                        <button
                          onClick={() => setIslandExpanded(false)}
                          className="p-1 rounded bg-zinc-800 text-zinc-400 hover:text-white"
                          aria-label="Collapse Dynamic Island"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {/* Live Music Square inside Expanded Dynamic Island (mirrors IslandService.showExpanded) */}
                    <div
                      onClick={handleTapToOpenActivePlayer}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === 'Enter' && handleTapToOpenActivePlayer()}
                      className="mb-2.5 p-2.5 rounded-xl bg-zinc-950/90 border border-zinc-700/80 hover:border-sky-500/50 flex items-center justify-between gap-2 cursor-pointer transition-all active:scale-[0.99]"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div className="w-9 h-9 rounded-lg bg-rose-600 flex items-center justify-center text-[10px] font-mono font-bold text-white shrink-0">
                          YTM
                        </div>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-white truncate">
                            {isPlayingMedia ? '▮▯▮ ' : '▯▯▯ '}
                            {currentTrack.title}
                          </p>
                          <p className="text-[10px] text-sky-300 truncate">
                            {currentTrack.artist} · Tap to open ↗
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          onClick={handlePrevTrack}
                          className="p-1.5 rounded hover:bg-zinc-800 text-zinc-300"
                        >
                          <SkipBack className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={handleTogglePlay}
                          className="p-1.5 rounded hover:bg-zinc-800 text-rose-400"
                        >
                          {isPlayingMedia ? (
                            <Pause className="w-3.5 h-3.5" />
                          ) : (
                            <Play className="w-3.5 h-3.5" />
                          )}
                        </button>
                        <button
                          onClick={handleNextTrack}
                          className="p-1.5 rounded hover:bg-zinc-800 text-zinc-300"
                        >
                          <SkipForward className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {islandOrganizeMode && (
                      <div className="flex items-center justify-between gap-2 mb-2.5 text-[11px] text-zinc-300 bg-zinc-950 px-2.5 py-1.5 rounded">
                        <span>
                          {selectedSwapIndex !== null
                            ? `Select target slot to swap with #${selectedSwapIndex + 1}`
                            : 'Tap any two slots to swap'}
                        </span>
                        <button
                          onClick={handleSortIslandApps}
                          className="px-2 py-0.5 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 font-medium flex items-center gap-1 whitespace-nowrap shrink-0"
                        >
                          <ArrowUpDown className="w-3 h-3" />
                          <span>Sort A–Z</span>
                        </button>
                      </div>
                    )}

                    <div className="grid grid-cols-4 gap-2">
                      {shortcuts.map((item, idx) => (
                        <div key={item.id} className="flex flex-col items-center">
                          <button
                            onClick={() => handleSlotTap(item, idx)}
                            className={`w-full aspect-square rounded-lg flex flex-col items-center justify-center p-1.5 transition-all active:scale-95 border ${
                              selectedSwapIndex === idx
                                ? 'border-zinc-200 bg-zinc-800'
                                : wallpaperBlendIcons
                                ? `${activeWallpaper.tilePlateBg} ${activeWallpaper.tileBorder}`
                                : 'border-zinc-800 bg-zinc-950/80 hover:border-zinc-700'
                            }`}
                          >
                            <span
                              className={`w-7 h-7 rounded font-mono text-[11px] font-semibold flex items-center justify-center border ${
                                wallpaperBlendIcons
                                  ? `${activeWallpaper.glyphPlateBg} ${activeWallpaper.glyphBorder} ${activeWallpaper.glyphText}`
                                  : 'bg-zinc-800 border-zinc-700 text-zinc-100'
                              }`}
                            >
                              {item.code}
                            </span>
                            <span className="text-[10px] text-zinc-200 mt-1 truncate max-w-full">
                              {item.name}
                            </span>
                          </button>
                          {islandOrganizeMode && (
                            <div className="flex items-center gap-1 mt-1">
                              <button
                                onClick={() => handleShiftApp(idx, -1)}
                                className="p-0.5 rounded bg-zinc-800 text-zinc-400 hover:text-white"
                                title="Shift Left"
                              >
                                <ArrowLeft className="w-2.5 h-2.5" />
                              </button>
                              <button
                                onClick={() => handleShiftApp(idx, 1)}
                                className="p-0.5 rounded bg-zinc-800 text-zinc-400 hover:text-white"
                                title="Shift Right"
                              >
                                <ArrowRight className="w-2.5 h-2.5" />
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Screen Lock Overlay (Tests Background Audio Survival) */}
              {isScreenLocked && (
                <div className="absolute inset-0 z-10 bg-[#090A0F]/95 backdrop-blur-md flex flex-col items-center justify-between p-6 text-center">
                  <div className="mt-12">
                    <p className="text-4xl font-bold font-mono tabular-nums text-white tracking-tight">
                      22:48
                    </p>
                    <p className="text-xs text-zinc-400 mt-1">
                      Keyguard Active · Screen Off
                    </p>
                  </div>

                  <div className="w-full rounded-xl border border-zinc-800 bg-zinc-900/90 p-4 text-left">
                    <div className="flex items-center justify-between text-xs text-zinc-400 mb-1.5">
                      <span className="font-mono text-[11px] text-emerald-400">
                        MEDIASESSION · KEEP_ALIVE
                      </span>
                      <span className="font-mono tabular-nums text-zinc-200">
                        {formatTime(playbackSeconds)}
                      </span>
                    </div>
                    <p className="text-sm font-semibold text-white truncate">
                      {currentTrack.title}
                    </p>
                    <p className="text-xs text-zinc-400 mt-0.5">
                      {currentTrack.artist}
                    </p>
                    <div className="flex items-center justify-between mt-3 pt-3 border-t border-zinc-800">
                      <button
                        onClick={() => setIsPlayingMedia((p) => !p)}
                        className="px-3 py-1.5 rounded bg-zinc-100 hover:bg-white text-xs font-semibold text-zinc-950 flex items-center gap-1.5"
                      >
                        {isPlayingMedia ? (
                          <>
                            <Pause className="w-3.5 h-3.5" />
                            <span>Pause</span>
                          </>
                        ) : (
                          <>
                            <Play className="w-3.5 h-3.5" />
                            <span>Resume</span>
                          </>
                        )}
                      </button>
                      <button
                        onClick={() => setIsScreenLocked(false)}
                        className="px-3 py-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200"
                      >
                        Unlock Device
                      </button>
                    </div>
                  </div>

                  <p className="text-xs text-zinc-500">
                    Click Unlock to return to active surface
                  </p>
                </div>
              )}

              {/* Active View: Either Standalone Installed WebAppActivity or Lock Launcher Home Screen */}
              {activeWebApp ? (
                <div className="flex-1 flex flex-col justify-between px-3 pb-3 pt-2 overflow-y-auto">
                  <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-3 flex-1 flex flex-col justify-between relative">
                    {/* TOP UTILITY BAR (Moved from bottom to TOP; hidden by default in Installed Standalone Mode!) */}
                    <div>
                      {!isImmersiveFullscreen && topBarVisible ? (
                        <div className="mb-2.5 px-2 py-1.5 rounded-xl bg-zinc-950/95 border border-zinc-700/80 flex items-center justify-between gap-1 shadow-md animate-in slide-in-from-top-2 duration-150">
                          <div className="flex items-center gap-0.5">
                            <button
                              onClick={() => setAdSkipLog('Navigated back in WebView history.')}
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-300 hover:bg-zinc-800 active:scale-90"
                              title="Back"
                            >
                              <ArrowLeft className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => setAdSkipLog('Navigated forward in WebView history.')}
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-400 hover:bg-zinc-800 active:scale-90"
                              title="Forward"
                            >
                              <ArrowRight className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() =>
                                setAdSkipLog(
                                  'Reloaded page & re-injected BACKGROUND_PLAYBACK_JS + COSMETIC_AD_BLOCK_JS.'
                                )
                              }
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-300 hover:bg-zinc-800 active:scale-90"
                              title="Refresh"
                            >
                              <RotateCw className="w-3 h-3" />
                            </button>
                          </div>

                          <button
                            onClick={() => setPowerMenuOpen((prev) => !prev)}
                            className="px-2 py-1 rounded-lg bg-zinc-900 border border-zinc-800 hover:border-zinc-600 text-[10px] font-mono text-zinc-200 truncate max-w-[105px] flex items-center gap-1"
                            title="Tap for PiP, Sleep Timer & OLED Controls"
                          >
                            <SlidersHorizontal className="w-2.5 h-2.5 text-zinc-400 shrink-0" />
                            <span className="truncate">{activeHost}</span>
                          </button>

                          <div className="flex items-center gap-0.5">
                            <button
                              onClick={handleInstallCurrentWebApp}
                              className="px-2 py-1 rounded-lg bg-sky-500/20 border border-sky-500/40 text-sky-300 hover:bg-sky-500/30 text-[10px] font-semibold whitespace-nowrap"
                              title="Install as Standalone App (Hide Bar & Pin)"
                            >
                              ＋ Install
                            </button>
                            <button
                              onClick={() => setIsImmersiveFullscreen(true)}
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-300 hover:bg-zinc-800"
                              title="Immersive Fullscreen"
                            >
                              <Maximize2 className="w-3 h-3" />
                            </button>
                            <button
                              onClick={() => setTopBarVisible(false)}
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-300 hover:bg-zinc-800"
                              title="Hide Top Bar (Standalone App Mode)"
                            >
                              <ChevronUp className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => setActiveWebApp(null)}
                              className="w-6 h-6 rounded-lg flex items-center justify-center text-zinc-400 hover:text-white hover:bg-zinc-800"
                              title="Minimize to Background"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      ) : (
                        /* Subtle Top-Right Pull Pill in Installed Standalone Mode (Never blocks bottom UI!) */
                        <div className="flex items-center justify-between pb-2 mb-2 border-b border-zinc-800/80">
                          <div className="flex items-center gap-2">
                            <span className="px-2 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-[10px] font-mono text-emerald-300">
                              INSTALLED APP MODE
                            </span>
                            <h2 className="text-sm font-bold text-white font-display truncate max-w-[120px]">
                              {activeWebApp.name}
                            </h2>
                          </div>
                          <div className="flex items-center gap-1.5">
                            <button
                              onClick={() => setOauthModalOpen((prev) => !prev)}
                              className="px-2 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-[10px] font-mono text-zinc-200 flex items-center gap-1 whitespace-nowrap shrink-0"
                            >
                              <UserCheck className="w-3 h-3 text-emerald-400" />
                              <span className="truncate max-w-[85px]">{activeGoogleAccount}</span>
                            </button>
                            <button
                              onClick={() => {
                                setIsImmersiveFullscreen(false);
                                setTopBarVisible(true);
                              }}
                              className="px-2 py-1 rounded-full bg-zinc-950 border border-zinc-700 hover:border-sky-400 text-[10px] font-medium text-zinc-300 flex items-center gap-1 transition-all active:scale-95"
                              title="Reveal Top Controls Bar"
                            >
                              <span>▾ Bar</span>
                            </button>
                          </div>
                        </div>
                      )}

                      {/* OAuth Account Switcher Popup Simulation */}
                      {oauthModalOpen && (
                        <div className="mb-2.5 p-3 rounded-lg bg-zinc-950 border border-zinc-700">
                          <p className="text-xs font-semibold text-white mb-1">
                            Google OAuth · Desktop Chrome 132 UA
                          </p>
                          <p className="text-[11px] text-zinc-400 mb-2">
                            Bypasses WebView 403 disallowed_useragent; syncs cookies via WebViewTransport:
                          </p>
                          <div className="flex flex-col gap-1.5">
                            {['crtghoul@gmail.com', 'velazquez.lab@gmail.com', 'work.profile@oneplus.dev'].map(
                              (acct) => (
                                <button
                                  key={acct}
                                  onClick={() => handleSwitchOAuthAccount(acct)}
                                  className={`px-2.5 py-1.5 rounded text-xs text-left font-mono flex items-center justify-between ${
                                    activeGoogleAccount === acct
                                      ? 'bg-zinc-800 text-white border border-zinc-600'
                                      : 'bg-zinc-900 text-zinc-400 hover:bg-zinc-800'
                                  }`}
                                >
                                  <span>{acct}</span>
                                  {activeGoogleAccount === acct && <Check className="w-3.5 h-3.5" />}
                                </button>
                              )
                            )}
                          </div>
                        </div>
                      )}

                      {/* WebApp Power Controls Sheet */}
                      {powerMenuOpen && (
                        <div className="mb-2.5 p-3 rounded-lg bg-zinc-950 border border-zinc-700 space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-semibold text-white">
                              WebApp Power Controls
                            </span>
                            <button
                              onClick={() => setPowerMenuOpen(false)}
                              className="text-zinc-400 hover:text-white"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          </div>
                          <div className="grid grid-cols-2 gap-1.5 text-[11px]">
                            <button
                              onClick={() => {
                                setAudioOnlyMode((p) => !p);
                                setAdSkipLog(
                                  !audioOnlyMode
                                    ? 'Audio-Only OLED Saver enabled: <video> opacity 0.01, audio stream active.'
                                    : 'Video display layer restored.'
                                );
                              }}
                              className={`p-2 rounded border text-left flex items-center gap-1.5 ${
                                audioOnlyMode
                                  ? 'bg-zinc-800 border-zinc-500 text-white'
                                  : 'bg-zinc-900 border-zinc-800 text-zinc-300'
                              }`}
                            >
                              <EyeOff className="w-3.5 h-3.5 shrink-0" />
                              <span>{audioOnlyMode ? 'OLED Saver: ON' : 'OLED Audio-Only'}</span>
                            </button>
                            <button
                              onClick={() => {
                                const next =
                                  sleepTimerMin === 0
                                    ? 15
                                    : sleepTimerMin === 15
                                    ? 30
                                    : sleepTimerMin === 30
                                    ? 60
                                    : 0;
                                setSleepTimerMin(next);
                                setAdSkipLog(
                                  next > 0
                                    ? `Sleep Timer scheduled for ${next}m: will pause media & abandon audio focus.`
                                    : 'Sleep Timer cancelled.'
                                );
                              }}
                              className={`p-2 rounded border text-left flex items-center gap-1.5 ${
                                sleepTimerMin > 0
                                  ? 'bg-zinc-800 border-zinc-500 text-white'
                                  : 'bg-zinc-900 border-zinc-800 text-zinc-300'
                              }`}
                            >
                              <Clock className="w-3.5 h-3.5 shrink-0" />
                              <span>
                                {sleepTimerMin > 0 ? `Sleep: ${sleepTimerMin}m` : 'Sleep Timer'}
                              </span>
                            </button>
                            <button
                              onClick={() => {
                                setPipActive(true);
                                setPowerMenuOpen(false);
                                setActiveWebApp(null);
                                setAdSkipLog(
                                  'Entered Android Picture-in-Picture (16:9 Rational) floating window.'
                                );
                              }}
                              className="p-2 rounded border bg-zinc-900 border-zinc-800 text-zinc-300 hover:border-zinc-700 text-left flex items-center gap-1.5 col-span-2"
                            >
                              <PictureInPicture2 className="w-3.5 h-3.5 shrink-0" />
                              <span>Enter Picture-in-Picture (PiP) Mode on Home Screen</span>
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Simulated HTML5 Media Player with SponsorBlock + InnerTube Ad Guard */}
                      <div className="rounded-xl bg-zinc-950 border border-zinc-800 p-3">
                        <div className="flex items-center justify-between text-[11px] text-zinc-400 mb-2">
                          <span className="font-mono text-emerald-400">
                            {audioOnlyMode ? 'AUDIO_ONLY_OLED_MODE' : 'STANDALONE_APP · NO BOTTOM BAR'}
                          </span>
                          <span className="font-mono tabular-nums text-zinc-300">
                            {adBreakSimulated
                              ? 'SPONSOR SKIP -> 42.8s'
                              : `${formatTime(playbackSeconds)} / 04:12`}
                          </span>
                        </div>

                        <div
                          className={`h-28 rounded-lg border flex flex-col items-center justify-center px-3 text-center transition-colors ${
                            audioOnlyMode
                              ? 'bg-black border-zinc-900 text-zinc-500'
                              : 'bg-gradient-to-br from-zinc-900 via-zinc-900/90 to-rose-950/30 border-zinc-800 text-zinc-100'
                          }`}
                        >
                          <p className="text-xs font-bold">
                            {adBreakSimulated
                              ? 'SponsorBlock + Ad Guard: Seeking Past Segment...'
                              : audioOnlyMode
                              ? 'OLED Black Screen Active · Audio Streaming'
                              : currentTrack.title}
                          </p>
                          <p className="text-[11px] text-zinc-300 mt-0.5">{currentTrack.artist}</p>
                          <p className="text-[10px] text-emerald-400 mt-1 font-mono">
                            adPlacements: [] · MediaSession: Active
                          </p>
                        </div>

                        <div className="flex items-center justify-between gap-2 mt-2.5">
                          <div className="flex items-center gap-1.5">
                            <button
                              onClick={handlePrevTrack}
                              className="p-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 transition-transform active:scale-90"
                            >
                              <SkipBack className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={handleTogglePlay}
                              className="px-3 py-1.5 rounded bg-zinc-100 hover:bg-white text-xs font-semibold text-zinc-950 flex items-center gap-1.5 transition-transform active:scale-95 whitespace-nowrap shrink-0"
                            >
                              {isPlayingMedia ? (
                                <>
                                  <Pause className="w-3.5 h-3.5" />
                                  <span>Pause</span>
                                </>
                              ) : (
                                <>
                                  <Play className="w-3.5 h-3.5" />
                                  <span>Play</span>
                                </>
                              )}
                            </button>
                            <button
                              onClick={handleNextTrack}
                              className="p-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 transition-transform active:scale-90"
                            >
                              <SkipForward className="w-3.5 h-3.5" />
                            </button>
                          </div>

                          <button
                            onClick={triggerSimulatedPreRoll}
                            className="px-2.5 py-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 flex items-center gap-1.5 transition-transform active:scale-95 whitespace-nowrap shrink-0"
                          >
                            <FastForward className="w-3.5 h-3.5 text-zinc-300" />
                            <span>Skip Sponsor</span>
                          </button>
                        </div>
                      </div>

                      {/* Live Telemetry Readout inside WebApp */}
                      <div className="mt-2.5 p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80 text-xs space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="text-zinc-400">Navigation Bar Position</span>
                          <span className="font-mono text-[11px] text-sky-400">
                            {topBarVisible ? 'TOP BAR (EXPANDED)' : 'HIDDEN (STANDALONE APP)'}
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-zinc-400">Top-Left Shade MediaSession</span>
                          <span className="font-mono text-[11px] text-emerald-400">
                            Token Attached · Music Square Ready
                          </span>
                        </div>
                        <p className="text-[11px] text-zinc-400 pt-1 border-t border-zinc-800/80 leading-snug">
                          {adSkipLog}
                        </p>
                      </div>
                    </div>

                    {/* Unobstructed Bottom Area of Standalone WebApp (No cluttering Return/Forward/Restart/Fullscreen bar at bottom!) */}
                    <div className="mt-2 pt-2 border-t border-zinc-800/60 flex items-center justify-between text-[11px] text-zinc-400">
                      <span>Bottom edge 100% unobstructed for app tab bars</span>
                      <button
                        onClick={() => setTopBarVisible((prev) => !prev)}
                        className="text-sky-400 hover:underline font-medium flex items-center gap-1"
                      >
                        {topBarVisible ? (
                          <>
                            <ChevronUp className="w-3 h-3" />
                            <span>Hide Top Bar</span>
                          </>
                        ) : (
                          <>
                            <ChevronDown className="w-3 h-3" />
                            <span>Show Top Bar</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                /* Flagship Lock Launcher Home Screen with Chameleon Wallpaper-Blended Icons & Music Square */
                <div className="flex-1 flex flex-col justify-between p-4 overflow-y-auto z-10">
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <div>
                        <p className="text-xl font-bold font-display text-white tracking-tight">
                          Friday, Oct 31
                        </p>
                        <p className={`text-[11px] font-mono ${activeWallpaper.accentText}`}>
                          v2.22 · {activeWallpaper.name.toUpperCase()} ·{' '}
                          {wallpaperBlendIcons ? `BLEND ${wallpaperBlendIntensity}%` : 'OPAQUE ICONS'}
                        </p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => setWidgetMenuOpen((prev) => !prev)}
                          className="px-2 py-1 rounded bg-black/35 backdrop-blur-md border border-white/15 text-[11px] font-medium text-zinc-200 hover:border-white/30"
                        >
                          Widgets ({homeWidgets.length})
                        </button>
                        <button
                          onClick={() => setCleanModeHidden((prev) => !prev)}
                          className="px-2 py-1 rounded bg-black/35 backdrop-blur-md border border-white/15 text-[11px] font-medium text-zinc-200 hover:border-white/30"
                        >
                          {cleanModeHidden ? 'Show Grid' : 'Clean'}
                        </button>
                      </div>
                    </div>

                    {/* Interactive Wallpaper & Chameleon Icon Blend Bar */}
                    <div className="mb-3 p-2.5 rounded-xl bg-black/40 backdrop-blur-md border border-white/15 space-y-2">
                      <div className="flex items-center justify-between gap-2">
                        <button
                          onClick={() => {
                            setWallpaperBlendIcons((prev) => !prev);
                            showToastMessage(
                              !wallpaperBlendIcons
                                ? `Chameleon Wallpaper Blend ON (${wallpaperBlendIntensity}%)`
                                : 'Standard opaque app icons restored'
                            );
                          }}
                          className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition-all active:scale-95 ${
                            wallpaperBlendIcons
                              ? `${activeWallpaper.glyphPlateBg} ${activeWallpaper.glyphBorder} text-white`
                              : 'bg-zinc-900 border-zinc-700 text-zinc-400'
                          }`}
                        >
                          {wallpaperBlendIcons ? '✦ Blend Apps: ON' : '○ Blend Apps: OFF'}
                        </button>

                        <div className="flex items-center gap-1">
                          {WALLPAPER_PRESETS.map((wp, idx) => (
                            <button
                              key={wp.id}
                              onClick={() => {
                                setWallpaperIndex(idx);
                                showToastMessage(`Wallpaper: ${wp.name} — Icons re-blended via WallpaperColors`);
                              }}
                              className={`px-2 py-1 rounded text-[10px] font-medium transition-all ${
                                wallpaperIndex === idx
                                  ? 'bg-white text-zinc-950 font-semibold'
                                  : 'bg-black/40 text-zinc-300 hover:text-white border border-white/10'
                              }`}
                            >
                              {wp.name.split(' ')[0]}
                            </button>
                          ))}
                        </div>
                      </div>

                      {wallpaperBlendIcons && (
                        <div className="flex items-center gap-2 text-[10px] text-zinc-300">
                          <span className="shrink-0 font-mono">Blend {wallpaperBlendIntensity}%</span>
                          <input
                            type="range"
                            min={15}
                            max={100}
                            value={wallpaperBlendIntensity}
                            onChange={(e) => setWallpaperBlendIntensity(Number(e.target.value))}
                            className="w-full accent-sky-400 h-1 bg-white/20 rounded-lg cursor-pointer"
                            aria-label="Wallpaper blend intensity"
                          />
                        </div>
                      )}
                    </div>

                    {/* Interactive Home Music Square Widget (mirrors home_now_playing_bar in activity_home.xml) */}
                    <div
                      onClick={handleTapToOpenActivePlayer}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === 'Enter' && handleTapToOpenActivePlayer()}
                      className={`mb-3 p-3 rounded-2xl bg-gradient-to-r ${
                        wallpaperBlendIcons
                          ? activeWallpaper.musicSquareBg
                          : 'from-zinc-900 via-zinc-900/95 to-rose-950/25 border-zinc-700/80'
                      } backdrop-blur-md border hover:border-white/40 cursor-pointer shadow-lg transition-all active:scale-[0.99]`}
                    >
                      <div className="flex items-center justify-between gap-2.5">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div className="w-11 h-11 rounded-xl bg-rose-600 flex flex-col items-center justify-center text-white font-mono font-bold shrink-0 shadow">
                            <span className="text-[11px]">YTM</span>
                            <span className="text-[8px] opacity-80">
                              {formatTime(playbackSeconds)}
                            </span>
                          </div>
                          <div className="min-w-0">
                            <p className="text-xs font-bold text-white truncate">
                              {isPlayingMedia ? '▮▯▮ ' : '▯▯▯ '}
                              {currentTrack.title}
                            </p>
                            <p className="text-[11px] text-sky-300 truncate mt-0.5">
                              {currentTrack.artist} · Tap to open ↗
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            onClick={handlePrevTrack}
                            className="w-7 h-7 rounded-lg bg-zinc-800/90 hover:bg-zinc-700 text-white flex items-center justify-center transition-transform active:scale-90"
                            title="Previous"
                          >
                            <SkipBack className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={handleTogglePlay}
                            className="w-8 h-8 rounded-lg bg-rose-500 hover:bg-rose-400 text-white flex items-center justify-center transition-transform active:scale-90"
                            title="Play / Pause"
                          >
                            {isPlayingMedia ? (
                              <Pause className="w-3.5 h-3.5" />
                            ) : (
                              <Play className="w-3.5 h-3.5" />
                            )}
                          </button>
                          <button
                            onClick={handleNextTrack}
                            className="w-7 h-7 rounded-lg bg-zinc-800/90 hover:bg-zinc-700 text-white flex items-center justify-center transition-transform active:scale-90"
                            title="Next"
                          >
                            <SkipForward className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleTapToOpenActivePlayer();
                            }}
                            className="ml-1 px-2 py-1 rounded-lg bg-zinc-100 hover:bg-white text-zinc-950 font-bold text-[10px] transition-transform active:scale-95"
                          >
                            OPEN ↗
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Widget Manager Modal */}
                    {widgetMenuOpen && (
                      <div className="mb-3 p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-xs space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-white">Manage Home Widgets</span>
                          <button
                            onClick={() => setWidgetMenuOpen(false)}
                            className="text-zinc-400 hover:text-white"
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>
                        {homeWidgets.length === 0 ? (
                          <p className="text-[11px] text-zinc-400">
                            No active widgets on this profile.
                          </p>
                        ) : (
                          <div className="space-y-1.5">
                            {homeWidgets.map((w) => (
                              <div
                                key={w.id}
                                className="flex items-center justify-between px-2.5 py-1.5 rounded bg-zinc-950 border border-zinc-800"
                              >
                                <span className="text-[11px] text-zinc-200">
                                  {w.label} ({w.size})
                                </span>
                                <button
                                  onClick={() =>
                                    setHomeWidgets((prev) => prev.filter((item) => item.id !== w.id))
                                  }
                                  className="text-zinc-400 hover:text-rose-400 flex items-center gap-1 text-[11px]"
                                >
                                  <Trash2 className="w-3 h-3" />
                                  <span>Delete</span>
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                        <div className="flex items-center justify-between gap-2 pt-1">
                          <button
                            onClick={() =>
                              setHomeWidgets((prev) => [
                                ...prev,
                                {
                                  id: Date.now(),
                                  label: 'Lock Launcher 2x1 Pill',
                                  size: '2x1',
                                },
                              ])
                            }
                            className="px-2.5 py-1 rounded bg-zinc-100 text-zinc-950 font-medium text-[11px] flex items-center gap-1"
                          >
                            <Plus className="w-3 h-3" />
                            <span>Add Widget</span>
                          </button>
                          {homeWidgets.length > 0 && (
                            <button
                              onClick={() => setHomeWidgets([])}
                              className="px-2.5 py-1 rounded bg-zinc-800 text-zinc-300 hover:text-white text-[11px]"
                            >
                              Remove All
                            </button>
                          )}
                        </div>
                      </div>
                    )}

                    {/* Search Bar + Instant MathEvaluator */}
                    <div className="relative mb-3">
                      <Search className="w-3.5 h-3.5 text-zinc-500 absolute left-3 top-1/2 -translate-y-1/2" />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Search apps, web, or math (e.g. 120 * 8)..."
                        className="w-full pl-8 pr-3 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-white placeholder:text-zinc-500 focus:outline-none focus:border-zinc-600"
                      />
                    </div>

                    {mathOutput && (
                      <div className="mb-3 px-3 py-2 rounded-lg bg-zinc-900 border border-zinc-700 flex items-center justify-between text-xs">
                        <span className="text-zinc-400">MathEvaluator</span>
                        <span className="font-mono font-bold text-emerald-400 tabular-nums">
                          = {mathOutput}
                        </span>
                      </div>
                    )}

                    {/* Active Home Widgets Preview */}
                    {homeWidgets.length > 0 && (
                      <div className="mb-3 space-y-1.5">
                        {homeWidgets.map((w) => (
                          <div
                            key={w.id}
                            className="px-3 py-2 rounded-lg bg-zinc-900/60 border border-zinc-800/90 flex items-center justify-between text-[11px]"
                          >
                            <span className="text-zinc-300 font-medium">{w.label}</span>
                            <button
                              onClick={() => setWidgetMenuOpen(true)}
                              className="text-zinc-500 hover:text-zinc-300 font-mono"
                            >
                              {w.size} · Edit
                            </button>
                          </div>
                        ))}
                      </div>
                    )}

                    {!cleanModeHidden && (
                      <div className="grid grid-cols-4 gap-2">
                        {filteredShortcuts.map((app, index) => (
                          <button
                            key={app.id}
                            onClick={() => handleSlotTap(app, index)}
                            style={
                              wallpaperBlendIcons
                                ? {
                                    opacity: 0.78 + (wallpaperBlendIntensity / 100) * 0.22,
                                  }
                                : undefined
                            }
                            className={`p-2.5 rounded-2xl border flex flex-col items-center justify-center transition-all duration-300 active:scale-95 relative ${
                              wallpaperBlendIcons
                                ? `${activeWallpaper.tilePlateBg} ${activeWallpaper.tileBorder} shadow-lg`
                                : 'bg-zinc-900 border-zinc-700 hover:border-zinc-500'
                            }`}
                          >
                            <div
                              className={`w-9 h-9 rounded-xl border flex items-center justify-center text-xs font-mono font-semibold transition-all duration-300 ${
                                wallpaperBlendIcons
                                  ? `${activeWallpaper.glyphPlateBg} ${activeWallpaper.glyphBorder} ${activeWallpaper.glyphText}`
                                  : 'bg-zinc-800 border-zinc-600 text-white'
                              }`}
                            >
                              {app.code}
                            </div>
                            <span className="text-[11px] font-medium text-zinc-100 mt-1.5 truncate max-w-full">
                              {app.name}
                            </span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="pt-3 border-t border-white/10 flex items-center justify-between text-[11px] text-zinc-300">
                    <span>Switch wallpapers above to test Chameleon Icon Blend</span>
                    <span className="font-mono tabular-nums text-emerald-400">v2.22</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>

        {/* Right 7 Columns: Engineering Workbench */}
        <section className="lg:col-span-7 flex flex-col gap-6">
          <div className="rounded-xl border border-zinc-800 bg-[#11131A] p-6">
            <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
              <span className="text-xs font-mono text-emerald-400">
                CHAMELEON WALLPAPER ICON BLEND · UTF-8 VERIFIED · ANDROID 15 READY
              </span>
              <span className="text-xs font-mono text-zinc-400 tabular-nums">
                versionCode=60 · versionName=2.22
              </span>
            </div>
            <h1 className="text-2xl font-bold text-white font-display tracking-tight">
              Lock Launcher v2.22 — Wallpaper-Blended Apps, Music Square &amp; Standalone WebApps
            </h1>
            <p className="text-sm text-zinc-300 mt-2 leading-relaxed">
              Added <strong className="text-white">Chameleon Wallpaper-Adaptive App Icon Blending</strong> so apps across the Home Grid, Dynamic Island, and Edge Sidebar seamlessly blend into your active wallpaper via live <code className="text-zinc-100">WallpaperColors</code> extraction + translucent squircle glass plates + foreground glyph color harmonization, alongside Android 14/15 <strong className="text-white">Tap to Open</strong>, the Top-Left Swipe-Down <strong className="text-white">Music Square</strong>, and <strong className="text-white">Installable Standalone WebApps</strong>.
            </p>

            {/* Segmented Mode Control */}
            <div className="flex items-center gap-1 p-1 bg-zinc-950 border border-zinc-800 rounded-lg mt-5 w-fit">
              <button
                onClick={() => setActiveWorkbenchTab('inspector')}
                className={`px-3.5 py-1.5 text-xs font-semibold rounded transition-colors whitespace-nowrap shrink-0 ${
                  activeWorkbenchTab === 'inspector'
                    ? 'bg-zinc-100 text-zinc-950'
                    : 'text-zinc-400 hover:text-white'
                }`}
              >
                v2.22 Upgrades &amp; URL Interceptor
              </button>
              <button
                onClick={() => setActiveWorkbenchTab('code')}
                className={`px-3.5 py-1.5 text-xs font-semibold rounded transition-colors whitespace-nowrap shrink-0 ${
                  activeWorkbenchTab === 'code'
                    ? 'bg-zinc-100 text-zinc-950'
                    : 'text-zinc-400 hover:text-white'
                }`}
              >
                Java &amp; GitHub Release Source
              </button>
            </div>
          </div>

          {activeWorkbenchTab === 'inspector' ? (
            <div className="rounded-xl border border-zinc-800 bg-[#11131A] p-6 space-y-6">
              {/* v2.22 Upgrade Summary Matrix */}
              <div>
                <h2 className="text-base font-semibold text-white font-display mb-3">
                  01. What Was Built &amp; Upgraded in v2.22 (Build 60)
                </h2>
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-zinc-800 text-zinc-400">
                        <th className="py-2.5 pr-4 font-semibold">Feature / Fix</th>
                        <th className="py-2.5 px-4 font-semibold">Implementation Details</th>
                        <th className="py-2.5 pl-4 font-semibold">Modified Files</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/70 text-zinc-300">
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Blend Apps into Wallpaper ("Chameleon Glass")
                        </td>
                        <td className="py-3 px-4">
                          Extracts primary, secondary &amp; tertiary <code className="text-zinc-200">WallpaperColors</code> (API 27+) + registers a live <code className="text-zinc-200">OnColorsChangedListener</code>, strips harsh opaque adaptive icon plates, renders a translucent wallpaper-tinted glass squircle + specular rim, and harmonizes foreground glyphs via <code className="text-zinc-200">buildChameleonColorMatrix()</code>.
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          DynamicThemeHelper.java, IconUtil.java, HomeActivity.java, MainActivity.java
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          "Tap to Open" Fixed on Android 14/15
                        </td>
                        <td className="py-3 px-4">
                          Android 14/15 silently blocked <code className="text-zinc-200">PendingIntent.send()</code> without <code className="text-zinc-200">MODE_BACKGROUND_ACTIVITY_START_ALLOWED</code>, and <code className="text-zinc-200">WebAppActivity</code> lacked <code className="text-zinc-200">onNewIntent()</code>. Now launches/resumes 100% reliably without reloading.
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          NowPlayingHelper.java, IconUtil.java, WebAppActivity.java
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Top-Left Swipe-Down "Music Square" Widget
                        </td>
                        <td className="py-3 px-4">
                          Attached a live <code className="text-zinc-200">android.media.session.MediaSession</code> token, <code className="text-zinc-200">PlaybackState</code>, and album art to <code className="text-zinc-200">Notification.MediaStyle</code> + added Music Square to Home &amp; Dynamic Island.
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          WebAppActivity.java, IslandService.java, HomeActivity.java
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Installable Standalone WebApps + Top Bar
                        </td>
                        <td className="py-3 px-4">
                          Moved the Return/Forward/Restart/Fullscreen bar to the <strong>top</strong> of the screen and hid it by default in Standalone Installed Mode (with a <code className="text-zinc-200">▾ Bar</code> pull pill &amp; <code className="text-zinc-200">＋ Install</code> button).
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          activity_web_app.xml, WebAppActivity.java, ShortcutStore.java
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="pt-4 border-t border-zinc-800">
                <h2 className="text-base font-semibold text-white font-display">
                  02. Request Interceptor &amp; SponsorBlock Tester
                </h2>
                <p className="text-xs text-zinc-400 mt-1">
                  Inspect how <code className="text-zinc-200">AdBlockEngine</code> handles YouTube Music InnerTube JSON payloads, SponsorBlock skip segments, Google OAuth, and ad networks.
                </p>

                <div className="mt-4">
                  <label htmlFor="url-probe" className="block text-xs font-medium text-zinc-300 mb-1.5">
                    Request URL
                  </label>
                  <input
                    id="url-probe"
                    type="text"
                    value={probeUrl}
                    onChange={(e) => setProbeUrl(e.target.value)}
                    className="w-full px-3.5 py-2 rounded-lg bg-zinc-950 border border-zinc-800 text-xs font-mono text-zinc-100 focus:outline-none focus:border-zinc-600"
                  />
                </div>

                {/* Preset Quick-Test Buttons */}
                <div className="flex flex-wrap gap-1.5 mt-3">
                  {PRESET_TEST_URLS.map((preset) => (
                    <button
                      key={preset.label}
                      onClick={() => setProbeUrl(preset.url)}
                      className={`px-2.5 py-1.5 rounded text-xs font-medium border transition-colors whitespace-nowrap shrink-0 ${
                        probeUrl === preset.url
                          ? 'bg-zinc-800 border-zinc-500 text-white'
                          : 'bg-zinc-950 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
                      }`}
                    >
                      {preset.label}
                    </button>
                  ))}
                </div>

                {/* Interception Result Table */}
                <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-950 p-4 space-y-3 text-xs">
                  <div className="flex items-center justify-between pb-2.5 border-b border-zinc-800">
                    <span className="text-zinc-400">Engine Decision</span>
                    <span
                      className={`font-mono font-semibold flex items-center gap-1.5 ${
                        analysis.blocked ? 'text-amber-300' : 'text-emerald-400'
                      }`}
                    >
                      <Shield className="w-3.5 h-3.5" />
                      <span>
                        {analysis.blocked
                          ? 'BLOCKED (SYNTHETIC EMPTY PAYLOAD)'
                          : 'ALLOWED / SANITIZED IN-FLIGHT'}
                      </span>
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                    <div>
                      <span className="text-zinc-500 block">Rule &amp; Pipeline Stage</span>
                      <span className="text-zinc-200 font-medium mt-0.5 block leading-snug">
                        {analysis.reason}
                      </span>
                    </div>
                    <div>
                      <span className="text-zinc-500 block">Response Payload</span>
                      <span className="text-zinc-200 font-mono mt-0.5 block">
                        {analysis.mimeType} → {analysis.payloadPreview}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-xl border border-zinc-800 bg-[#11131A] p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-1 p-1 bg-zinc-950 border border-zinc-800 rounded-lg">
                  <button
                    onClick={() => setSelectedCodeTab('wallpaperChameleonBlend')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'wallpaperChameleonBlend'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    1. Chameleon Wallpaper Blend
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('desktopUaAndKeepalive')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'desktopUaAndKeepalive'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    2. Music Square &amp; Tap-to-Open
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('ytMusicAdStrip')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'ytMusicAdStrip'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    3. InnerTube + SponsorBlock
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('githubAutoRelease')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'githubAutoRelease'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    4. GitHub Auto-Release YAML
                  </button>
                </div>

                <button
                  onClick={() => handleCopyCode(selectedCodeTab)}
                  className="px-3 py-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
                >
                  {copiedKey === selectedCodeTab ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                      <span>Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" />
                      <span>Copy Code</span>
                    </>
                  )}
                </button>
              </div>

              <pre className="p-4 rounded-lg bg-zinc-950 border border-zinc-800 text-xs font-mono text-zinc-200 overflow-x-auto leading-relaxed max-h-[460px]">
                <code>{JAVA_SNIPPETS[selectedCodeTab]}</code>
              </pre>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
