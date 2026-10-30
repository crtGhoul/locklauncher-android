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
} from 'lucide-react';
import { analyzeUrlInterception, JAVA_SNIPPETS } from './adBlockLogic';

interface AppShortcut {
  id: string;
  name: string;
  url?: string;
  category: 'web' | 'system';
  code: string;
  subtitle: string;
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
    subtitle: 'InnerTube Ad-Strip · Desktop UA',
  },
  {
    id: 'yt',
    name: 'YouTube',
    url: 'https://m.youtube.com/watch?v=locklauncher_120hz',
    category: 'web',
    code: 'YT',
    subtitle: 'SponsorBlock · PiP · 120Hz',
  },
  {
    id: 'chatgpt',
    name: 'ChatGPT',
    url: 'https://chatgpt.com/',
    category: 'web',
    code: 'AI',
    subtitle: 'OAuth Transport · Zero Trackers',
  },
  {
    id: 'reddit',
    name: 'Reddit',
    url: 'https://www.reddit.com/r/androiddev',
    category: 'web',
    code: 'RD',
    subtitle: 'Cosmetic CSS · Promo Stripping',
  },
  {
    id: 'github',
    name: 'GitHub',
    url: 'https://github.com/crtGhoul/locklauncher-android',
    category: 'web',
    code: 'GH',
    subtitle: 'Auto-Release v2.20 · Blob Saver',
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
  // Simulator states
  const [islandExpanded, setIslandExpanded] = useState(false);
  const [islandOrganizeMode, setIslandOrganizeMode] = useState(false);
  const [shortcuts, setShortcuts] = useState<AppShortcut[]>(INITIAL_SHORTCUTS);
  const [selectedSwapIndex, setSelectedSwapIndex] = useState<number | null>(null);
  const [cleanModeHidden, setCleanModeHidden] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Home Widgets state (mirrors HomeActivity.java widget manager)
  const [homeWidgets, setHomeWidgets] = useState<HomeWidgetItem[]>([
    { id: 101, label: 'Lock Launcher 4x1 Dock', size: '4x1' },
  ]);
  const [widgetMenuOpen, setWidgetMenuOpen] = useState(false);

  // WebAppActivity container state
  const [activeWebApp, setActiveWebApp] = useState<AppShortcut | null>(INITIAL_SHORTCUTS[0]);
  const [isScreenLocked, setIsScreenLocked] = useState(false);
  const [isPlayingMedia, setIsPlayingMedia] = useState(true);
  const [playbackSeconds, setPlaybackSeconds] = useState(142);
  const [isImmersiveFullscreen, setIsImmersiveFullscreen] = useState(false);
  const [adBreakSimulated, setAdBreakSimulated] = useState(false);
  const [powerMenuOpen, setPowerMenuOpen] = useState(false);
  const [audioOnlyMode, setAudioOnlyMode] = useState(false);
  const [sleepTimerMin, setSleepTimerMin] = useState<number>(0);
  const [pipActive, setPipActive] = useState(false);
  const [adSkipLog, setAdSkipLog] = useState<string>(
    'InnerTube fetch/XHR hook active: adPlacements & adSlots stripped; SponsorBlock segments loaded.'
  );
  const [activeGoogleAccount, setActiveGoogleAccount] = useState('crtghoul@gmail.com');
  const [oauthModalOpen, setOauthModalOpen] = useState(false);

  // Workbench states
  const [activeWorkbenchTab, setActiveWorkbenchTab] = useState<'inspector' | 'code'>('inspector');
  const [probeUrl, setProbeUrl] = useState(PRESET_TEST_URLS[0].url);
  const [selectedCodeTab, setSelectedCodeTab] = useState<keyof typeof JAVA_SNIPPETS>('ytMusicAdStrip');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Advance simulated media timer even when screen is locked or on Home screen (proving background playback)
  useEffect(() => {
    if (!isPlayingMedia) return;
    const timer = window.setInterval(() => {
      setPlaybackSeconds((prev) => prev + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [isPlayingMedia]);

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
      setIsPlayingMedia(app.id === 'yt' || app.id === 'ytm');
      setIslandExpanded(false);
      setPipActive(false);
    } else {
      setActiveWebApp(null);
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
    : 'youtube.com';

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
          <span className="text-xs font-mono text-zinc-500 tabular-nums">v2.20 · BUILD 58</span>
        </div>

        <nav className="hidden md:flex items-center gap-6 text-xs font-medium text-zinc-400">
          <button
            onClick={() => setActiveWorkbenchTab('inspector')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWorkbenchTab === 'inspector' ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            Interceptor & SponsorBlock
          </button>
          <button
            onClick={() => setActiveWorkbenchTab('code')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWorkbenchTab === 'code' ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            Java & Workflow Source
          </button>
          <button
            onClick={() => {
              setActiveWebApp(shortcuts[0]);
              setIsPlayingMedia(true);
            }}
            className="hover:text-white transition-colors whitespace-nowrap shrink-0"
          >
            WebApp Container
          </button>
          <button
            onClick={() => {
              setActiveWebApp(null);
              setIslandExpanded(false);
            }}
            className="hover:text-white transition-colors whitespace-nowrap shrink-0"
          >
            Home Screen
          </button>
        </nav>

        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => {
              setActiveWorkbenchTab('code');
              handleCopyCode(selectedCodeTab);
            }}
            className="px-3.5 py-1.5 text-xs font-medium text-zinc-950 bg-zinc-100 rounded-md hover:bg-white transition-colors whitespace-nowrap shrink-0 flex items-center gap-1.5"
          >
            {copiedKey ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedKey ? 'Copied Source' : 'Copy Active Source'}</span>
          </button>
        </div>
      </header>

      {/* Main 1440px Multi-Column Workbench */}
      <main className="max-w-7xl w-full mx-auto px-6 py-7 grid grid-cols-1 lg:grid-cols-12 gap-8 flex-1 items-start">
        {/* Left 5 Columns: Hardware Device Frame (OnePlus 13 · OxygenOS 15) */}
        <section className="lg:col-span-5 flex flex-col items-center">
          <div className="w-full max-w-[400px] rounded-[32px] border border-zinc-800 bg-[#11131A] p-4 shadow-2xl">
            {/* Hardware Frame Top Control Bar */}
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-zinc-800/80 text-xs text-zinc-400">
              <span className="font-mono text-[11px] text-zinc-300">ONEPLUS 13 · 120HZ LTPO</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setIsScreenLocked((prev) => !prev)}
                  className={`px-2.5 py-1 rounded font-medium transition-colors flex items-center gap-1.5 whitespace-nowrap shrink-0 ${
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
                    className="px-2.5 py-1 rounded bg-zinc-800/90 text-zinc-300 hover:bg-zinc-700 font-medium whitespace-nowrap shrink-0"
                  >
                    Go Home
                  </button>
                )}
              </div>
            </div>

            {/* Viewport Screen Area */}
            <div className="relative h-[650px] rounded-[24px] bg-[#090A0F] border border-zinc-800/90 overflow-hidden flex flex-col justify-between">
              {/* Dynamic Island Overlay Capsule */}
              <div className="pt-3 px-3 z-20">
                {!islandExpanded ? (
                  <button
                    onClick={() => setIslandExpanded(true)}
                    className="mx-auto flex items-center justify-between gap-5 px-4 py-1.5 rounded-full bg-zinc-950 border border-zinc-800 text-[11px] text-zinc-200 hover:border-zinc-600 transition-colors"
                  >
                    <span className="font-mono tabular-nums text-zinc-400">94% · 120Hz</span>
                    <span className="font-medium text-zinc-200">
                      {isPlayingMedia ? `Playing · ${formatTime(playbackSeconds)}` : 'Dynamic Island'}
                    </span>
                  </button>
                ) : (
                  <div className="rounded-xl bg-zinc-900/95 backdrop-blur-md border border-zinc-700/80 p-3 shadow-xl">
                    <div className="flex items-center justify-between pb-2 mb-2 border-b border-zinc-800">
                      <span className="text-xs font-semibold text-white">
                        Dynamic Island · 16-Slot Deck
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
                            className={`w-full aspect-square rounded-lg flex flex-col items-center justify-center p-1.5 transition-transform active:scale-95 border ${
                              selectedSwapIndex === idx
                                ? 'border-zinc-200 bg-zinc-800'
                                : 'border-zinc-800 bg-zinc-950/80 hover:border-zinc-700'
                            }`}
                          >
                            <span className="w-7 h-7 rounded bg-zinc-800 text-zinc-100 font-mono text-[11px] font-semibold flex items-center justify-center">
                              {item.code}
                            </span>
                            <span className="text-[10px] text-zinc-300 mt-1 truncate max-w-full">
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
                        AUDIOFOCUS_GAIN · KEEP_ALIVE
                      </span>
                      <span className="font-mono tabular-nums text-zinc-200">
                        {formatTime(playbackSeconds)}
                      </span>
                    </div>
                    <p className="text-sm font-semibold text-white truncate">
                      {activeWebApp ? activeWebApp.name : 'YT Music'} — Background Stream
                    </p>
                    <p className="text-xs text-zinc-400 mt-1">
                      document.hidden = false · Renderer priority IMPORTANT
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

              {/* Active View: Either Standalone WebAppActivity or De-AI'd Home Launcher */}
              {activeWebApp ? (
                <div className="flex-1 flex flex-col justify-between px-3 pb-3 pt-2 overflow-y-auto">
                  <div className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-3.5 flex-1 flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between gap-2 pb-2.5 border-b border-zinc-800">
                        <div>
                          <h2 className="text-sm font-bold text-white font-display">
                            {activeWebApp.name}
                          </h2>
                          <p className="text-[11px] text-zinc-400">{activeWebApp.subtitle}</p>
                        </div>
                        <button
                          onClick={() => setOauthModalOpen((prev) => !prev)}
                          className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-[11px] font-mono text-zinc-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
                        >
                          <UserCheck className="w-3.5 h-3.5 text-emerald-400" />
                          <span className="truncate max-w-[115px]">{activeGoogleAccount}</span>
                        </button>
                      </div>

                      {/* OAuth Account Switcher Popup Simulation */}
                      {oauthModalOpen && (
                        <div className="mt-2.5 p-3 rounded-lg bg-zinc-950 border border-zinc-700">
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

                      {/* WebApp Power Controls Sheet (Triggered by tapping Domain Bar or Controls button) */}
                      {powerMenuOpen && (
                        <div className="mt-2.5 p-3 rounded-lg bg-zinc-950 border border-zinc-700 space-y-2">
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
                                const next = sleepTimerMin === 0 ? 15 : sleepTimerMin === 15 ? 30 : sleepTimerMin === 30 ? 60 : 0;
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
                              <span>{sleepTimerMin > 0 ? `Sleep: ${sleepTimerMin}m` : 'Sleep Timer'}</span>
                            </button>
                            <button
                              onClick={() => {
                                setPipActive(true);
                                setPowerMenuOpen(false);
                                setActiveWebApp(null);
                                setAdSkipLog('Entered Android Picture-in-Picture (16:9 Rational) floating window.');
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
                      <div className="mt-2.5 rounded-lg bg-zinc-950 border border-zinc-800 p-3">
                        <div className="flex items-center justify-between text-[11px] text-zinc-400 mb-2">
                          <span className="font-mono">
                            {audioOnlyMode ? 'AUDIO_ONLY_OLED_MODE' : 'HTML5_SURFACE · 120HZ'}
                          </span>
                          <span className="font-mono tabular-nums text-zinc-300">
                            {adBreakSimulated ? 'SPONSOR SKIP -> 42.8s' : `${formatTime(playbackSeconds)} / 18:40`}
                          </span>
                        </div>

                        <div
                          className={`h-24 rounded border flex flex-col items-center justify-center px-3 text-center transition-colors ${
                            audioOnlyMode
                              ? 'bg-black border-zinc-900 text-zinc-500'
                              : 'bg-zinc-900/70 border-zinc-800 text-zinc-100'
                          }`}
                        >
                          <p className="text-xs font-semibold">
                            {adBreakSimulated
                              ? 'SponsorBlock + Ad Guard: Seeking Past Segment...'
                              : audioOnlyMode
                              ? 'OLED Black Screen Active · Audio Streaming'
                              : `${activeWebApp.name} · Zero Ad Placements`}
                          </p>
                          <p className="text-[11px] text-zinc-400 mt-1 font-mono">
                            adPlacements: [] · SponsorBlock: Active
                          </p>
                        </div>

                        <div className="flex items-center justify-between gap-2 mt-2.5">
                          <button
                            onClick={() => setIsPlayingMedia((p) => !p)}
                            className="px-3 py-1.5 rounded bg-zinc-100 hover:bg-white text-xs font-semibold text-zinc-950 flex items-center gap-1.5 whitespace-nowrap shrink-0"
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
                            onClick={triggerSimulatedPreRoll}
                            className="px-2.5 py-1.5 rounded bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
                          >
                            <FastForward className="w-3.5 h-3.5 text-zinc-300" />
                            <span>Test Sponsor/Ad Skip</span>
                          </button>
                        </div>
                      </div>

                      {/* Live Telemetry Readout inside WebApp */}
                      <div className="mt-2.5 p-2.5 rounded-lg bg-zinc-950 border border-zinc-800/80 text-xs space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="text-zinc-400">Visibility & Focus</span>
                          <span className="font-mono text-[11px] text-emerald-400">
                            hidden=false · hasFocus=true
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-zinc-400">InnerTube JSON Filter</span>
                          <span className="font-mono text-[11px] text-zinc-200">
                            /youtubei/v1/player stripped
                          </span>
                        </div>
                        <p className="text-[11px] text-zinc-400 pt-1 border-t border-zinc-800/80 leading-snug">
                          {adSkipLog}
                        </p>
                      </div>
                    </div>

                    {/* Floating Minimalist Web Navigation Dock (De-AI'd) */}
                    {!isImmersiveFullscreen ? (
                      <div className="mt-3 px-2.5 py-1.5 rounded-full bg-zinc-950 border border-zinc-800 flex items-center justify-between gap-1.5">
                        <button
                          onClick={() => setAdSkipLog('Navigated back in WebView history.')}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-300 hover:bg-zinc-800"
                          title="Back"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setAdSkipLog('Navigated forward in WebView history.')}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-400 hover:bg-zinc-800"
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
                          className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-300 hover:bg-zinc-800"
                          title="Refresh"
                        >
                          <RotateCw className="w-3.5 h-3.5" />
                        </button>

                        <button
                          onClick={() => setPowerMenuOpen((prev) => !prev)}
                          className="px-2.5 py-1 rounded-full bg-zinc-900 border border-zinc-800 hover:border-zinc-600 text-[11px] font-mono text-zinc-200 truncate max-w-[140px] flex items-center gap-1.5"
                          title="Tap for PiP, Sleep Timer & OLED Controls"
                        >
                          <SlidersHorizontal className="w-3 h-3 text-zinc-400 shrink-0" />
                          <span className="truncate">
                            {sleepTimerMin > 0 ? `${activeHost} · ${sleepTimerMin}m` : activeHost}
                          </span>
                        </button>

                        <button
                          onClick={() => setIsImmersiveFullscreen(true)}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-300 hover:bg-zinc-800"
                          title="Immersive Fullscreen"
                        >
                          <Maximize2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setActiveWebApp(null)}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-400 hover:text-white hover:bg-zinc-800"
                          title="Close WebApp"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setIsImmersiveFullscreen(false)}
                        className="mt-3 mx-auto px-3 py-1 rounded-full bg-zinc-900 border border-zinc-700 text-xs text-zinc-300 flex items-center gap-1.5"
                      >
                        <Minimize2 className="w-3 h-3" />
                        <span>Exit Fullscreen</span>
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                /* De-AI'd Flagship Lock Launcher Home Screen */
                <div className="flex-1 flex flex-col justify-between p-4 overflow-y-auto">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <p className="text-xl font-bold font-display text-white tracking-tight">
                          Thursday, Oct 30
                        </p>
                        <p className="text-[11px] text-zinc-400 font-mono">
                          0% ADS · OFFLINE LAUNCHER
                        </p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => setWidgetMenuOpen((prev) => !prev)}
                          className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-[11px] font-medium text-zinc-300 hover:border-zinc-700"
                        >
                          Widgets ({homeWidgets.length})
                        </button>
                        <button
                          onClick={() => setCleanModeHidden((prev) => !prev)}
                          className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-[11px] font-medium text-zinc-300 hover:border-zinc-700"
                        >
                          {cleanModeHidden ? 'Show Grid' : 'Clean'}
                        </button>
                      </div>
                    </div>

                    {/* Widget Manager Modal (Matches user's showWidgetMenu / showManageWidgetsDialog in HomeActivity.java) */}
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
                          <p className="text-[11px] text-zinc-400">No active widgets on this profile.</p>
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

                    {/* Picture-in-Picture (PiP) or Ongoing Background Audio Card when on Home Screen */}
                    {isPlayingMedia && (
                      <div className="mb-3 p-3 rounded-lg bg-zinc-900 border border-zinc-700 text-xs">
                        <div className="flex items-center justify-between text-zinc-400 mb-1">
                          <span className="font-mono text-[11px] text-emerald-400">
                            {pipActive ? 'PIP_WINDOW · 16:9 FLOATING' : 'BACKGROUND_AUDIO · ONGOING'}
                          </span>
                          <span className="font-mono tabular-nums text-zinc-200">
                            {formatTime(playbackSeconds)}
                          </span>
                        </div>
                        <p className="text-white font-semibold text-xs truncate">
                          YT Music — Ad-Free Stream Active
                        </p>
                        <p className="text-[11px] text-zinc-400 mt-0.5">
                          Playing uninterrupted while on Home screen or in other apps.
                        </p>
                        <div className="flex items-center justify-between gap-2 mt-2.5 pt-2 border-t border-zinc-800">
                          <button
                            onClick={() => setIsPlayingMedia((p) => !p)}
                            className="px-2.5 py-1 rounded bg-zinc-100 hover:bg-white text-zinc-950 font-semibold text-[11px] flex items-center gap-1"
                          >
                            <Pause className="w-3 h-3" />
                            <span>Pause</span>
                          </button>
                          <button
                            onClick={() => {
                              const ytm = shortcuts.find((s) => s.id === 'ytm') || shortcuts[0];
                              setActiveWebApp(ytm);
                              setPipActive(false);
                            }}
                            className="px-2.5 py-1 rounded bg-zinc-800 hover:bg-zinc-700 text-zinc-200 font-medium text-[11px]"
                          >
                            Open Player
                          </button>
                        </div>
                      </div>
                    )}

                    {!cleanModeHidden && (
                      <div className="grid grid-cols-4 gap-2">
                        {filteredShortcuts.map((app, index) => (
                          <button
                            key={app.id}
                            onClick={() => handleSlotTap(app, index)}
                            className="p-2.5 rounded-xl bg-zinc-900/70 border border-zinc-800/90 hover:border-zinc-600 flex flex-col items-center justify-center transition-transform active:scale-95"
                          >
                            <div className="w-9 h-9 rounded-lg bg-zinc-800 border border-zinc-700/80 flex items-center justify-center text-xs font-mono font-semibold text-zinc-100">
                              {app.code}
                            </div>
                            <span className="text-[11px] font-medium text-zinc-200 mt-1.5 truncate max-w-full">
                              {app.name}
                            </span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="pt-3 border-t border-zinc-900 flex items-center justify-between text-[11px] text-zinc-400">
                    <span>Tap YT Music or YouTube to open WebApp</span>
                    <span className="font-mono tabular-nums">v2.20</span>
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
                RELEASE READY · GITHUB ACTIONS AUTO-APK SYNC
              </span>
              <span className="text-xs font-mono text-zinc-400 tabular-nums">
                versionCode=58 · versionName=2.20
              </span>
            </div>
            <h1 className="text-2xl font-bold text-white font-display tracking-tight">
              Lock Launcher v2.20 Architecture & Everything-Inspired Workbench
            </h1>
            <p className="text-sm text-zinc-300 mt-2 leading-relaxed">
              Updated across <code className="text-zinc-100">AdBlockEngine.java</code>,{' '}
              <code className="text-zinc-100">WebAppActivity.java</code>,{' '}
              <code className="text-zinc-100">HomeActivity.java</code>, and{' '}
              <code className="text-zinc-100">.github/workflows/release.yml</code>. Pushing to{' '}
              <code className="text-zinc-100">main</code> automatically compiles{' '}
              <code className="text-zinc-100">locklauncher.apk</code>, commits the updated APK binary back to the repo, and publishes the GitHub Release.
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
                URL Interceptor & New Features
              </button>
              <button
                onClick={() => setActiveWorkbenchTab('code')}
                className={`px-3.5 py-1.5 text-xs font-semibold rounded transition-colors whitespace-nowrap shrink-0 ${
                  activeWorkbenchTab === 'code'
                    ? 'bg-zinc-100 text-zinc-950'
                    : 'text-zinc-400 hover:text-white'
                }`}
              >
                Java & GitHub Release Source
              </button>
            </div>
          </div>

          {activeWorkbenchTab === 'inspector' ? (
            <div className="rounded-xl border border-zinc-800 bg-[#11131A] p-6 space-y-6">
              <div>
                <h2 className="text-base font-semibold text-white font-display">
                  01. Request Interceptor & SponsorBlock Tester
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
                      <span className="text-zinc-500 block">Rule & Pipeline Stage</span>
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

              {/* New Features & De-AI Summary Matrix */}
              <div className="pt-4 border-t border-zinc-800">
                <h3 className="text-base font-semibold text-white font-display mb-3">
                  02. What Was Added in v2.20 (Build 58)
                </h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-zinc-800 text-zinc-400">
                        <th className="py-2.5 pr-4 font-semibold">Feature / Upgrade</th>
                        <th className="py-2.5 px-4 font-semibold">What It Solves</th>
                        <th className="py-2.5 pl-4 font-semibold">Implementation</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/70 text-zinc-300">
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Guided 4-Step First-Run Onboarding
                        </td>
                        <td className="py-3 px-4">
                          Appearance & Icon Style, Wallpaper Picker, Optional Permissions, and Overlay vs Home role setup
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          OnboardingActivity.java + activity_onboarding.xml
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Unified Glass Theme System + Icon Packs
                        </td>
                        <td className="py-3 px-4">
                          Synchronized Blur (0-25px), Opacity, Darkness & Accent across Island, Sidebar & Dock + ADW/Nova Icon Packs & Minimal Mode
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          GlassThemeHelper.java + IconPackManager.java
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Notification Badges (Off / Dot / Number) + Grouped Settings
                        </td>
                        <td className="py-3 px-4">
                          Per-app badge toggles and Everything-style 5-tab Settings with per-setting ↺ reset buttons
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          MainActivity, HomeActivity, EdgeSidebarService
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Auto APK Commit + GitHub Release
                        </td>
                        <td className="py-3 px-4">
                          Syncing to GitHub automatically compiles v2.20 APK, updates locklauncher.apk in repo, and publishes release
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          .github/workflows/release.yml (v2.20 / code 58)
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-xl border border-zinc-800 bg-[#11131A] p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-1 p-1 bg-zinc-950 border border-zinc-800 rounded-lg">
                  <button
                    onClick={() => setSelectedCodeTab('ytMusicAdStrip')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'ytMusicAdStrip'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    1. InnerTube + SponsorBlock
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('backgroundPlayback')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'backgroundPlayback'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    2. Background Audio JS
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('desktopUaAndKeepalive')}
                    className={`px-3 py-1.5 text-xs font-medium rounded transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'desktopUaAndKeepalive'
                        ? 'bg-zinc-800 text-white'
                        : 'text-zinc-400 hover:text-white'
                    }`}
                  >
                    3. PiP, Sleep Timer & OLED Menu
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
