import React, { useState, useEffect } from 'react';
import {
  Shield,
  Play,
  Pause,
  Lock,
  Unlock,
  Volume2,
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
  SlidersHorizontal,
  UserCheck,
  FastForward,
  Sparkles,
  Globe,
  Terminal,
} from 'lucide-react';
import { analyzeUrlInterception, JAVA_SNIPPETS } from './adBlockLogic';

interface AppShortcut {
  id: string;
  name: string;
  url?: string;
  category: 'web' | 'system';
  accent: string;
  symbol: string;
  subtitle: string;
}

const INITIAL_SHORTCUTS: AppShortcut[] = [
  {
    id: 'yt',
    name: 'YouTube',
    url: 'https://m.youtube.com/watch?v=locklauncher_120hz',
    category: 'web',
    accent: 'bg-rose-600 text-white',
    symbol: '▶',
    subtitle: 'Ad-Free WebApp · Background Audio',
  },
  {
    id: 'ytm',
    name: 'YT Music',
    url: 'https://music.youtube.com/playlist?list=LL_OFFLINE_MIX',
    category: 'web',
    accent: 'bg-red-700 text-white',
    symbol: '♫',
    subtitle: 'Screen-Off Audio · _lact Keepalive',
  },
  {
    id: 'chatgpt',
    name: 'ChatGPT',
    url: 'https://chatgpt.com/',
    category: 'web',
    accent: 'bg-emerald-700 text-white',
    symbol: '✦',
    subtitle: 'OAuth Popup Transport · Zero Trackers',
  },
  {
    id: 'reddit',
    name: 'Reddit',
    url: 'https://www.reddit.com/r/androiddev',
    category: 'web',
    accent: 'bg-orange-600 text-white',
    symbol: 'R',
    subtitle: 'Cosmetic CSS · Promo Slot Stripping',
  },
  {
    id: 'github',
    name: 'GitHub',
    url: 'https://github.com/crtGhoul/locklauncher-android',
    category: 'web',
    accent: 'bg-slate-800 text-slate-100',
    symbol: 'GH',
    subtitle: 'Blob & Release APK Downloader',
  },
  {
    id: 'dialer',
    name: 'Phone',
    category: 'system',
    accent: 'bg-indigo-600 text-white',
    symbol: '✆',
    subtitle: '0ms Native LauncherApps Intent',
  },
  {
    id: 'camera',
    name: 'Camera',
    category: 'system',
    accent: 'bg-sky-700 text-white',
    symbol: '◉',
    subtitle: 'Keyguard Dismiss Hardware Shortcut',
  },
  {
    id: 'settings',
    name: 'Settings',
    category: 'system',
    accent: 'bg-slate-700 text-slate-200',
    symbol: '⚙',
    subtitle: '120Hz LTPO & Profile Serial Isolation',
  },
];

const PRESET_TEST_URLS = [
  {
    label: 'YouTube Music InnerTube Track API (Ad Stripped)',
    url: 'https://music.youtube.com/youtubei/v1/player?key=AIzaSyA',
  },
  {
    label: 'YouTube Music Audio Telemetry (Blocked)',
    url: 'https://music.youtube.com/youtubei/v1/att/get?key=AIzaSyA',
  },
  {
    label: 'YouTube Pre-Roll Ad Break API (Blocked)',
    url: 'https://m.youtube.com/youtubei/v1/player/ad_break?key=AIzaSyA',
  },
  {
    label: 'YouTube Video & Audio Media Stream (Protected)',
    url: 'https://rr2---sn-ab5l6nzy.googlevideo.com/videoplayback?expire=173999&mime=audio/mp4',
  },
  {
    label: 'Google OAuth Account Chooser (Desktop UA)',
    url: 'https://accounts.google.com/o/oauth2/v2/auth?client_id=youtube_web',
  },
  {
    label: 'DoubleClick Ad SDK Script (Blocked)',
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

  // WebAppActivity container state
  const [activeWebApp, setActiveWebApp] = useState<AppShortcut | null>(INITIAL_SHORTCUTS[0]);
  const [isScreenLocked, setIsScreenLocked] = useState(false);
  const [isPlayingMedia, setIsPlayingMedia] = useState(true);
  const [playbackSeconds, setPlaybackSeconds] = useState(142);
  const [isImmersiveFullscreen, setIsImmersiveFullscreen] = useState(false);
  const [adBreakSimulated, setAdBreakSimulated] = useState(false);
  const [adSkipLog, setAdSkipLog] = useState<string>(
    'Non-destructive observer active: .video-ads preserved in DOM, 3 pre-roll requests intercepted.'
  );
  const [activeGoogleAccount, setActiveGoogleAccount] = useState('crtghoul@gmail.com');
  const [oauthModalOpen, setOauthModalOpen] = useState(false);

  // Workbench states
  const [activeWorkbenchTab, setActiveWorkbenchTab] = useState<'inspector' | 'code'>('inspector');
  const [probeUrl, setProbeUrl] = useState(PRESET_TEST_URLS[0].url);
  const [selectedCodeTab, setSelectedCodeTab] = useState<keyof typeof JAVA_SNIPPETS>('ytMusicAdStrip');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Advance simulated media timer even when screen is locked (proving background playback)
  useEffect(() => {
    if (!activeWebApp || !isPlayingMedia) return;
    const timer = window.setInterval(() => {
      setPlaybackSeconds((prev) => prev + 1);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [activeWebApp, isPlayingMedia]);

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
    } else {
      setActiveWebApp(null);
    }
  };

  const triggerSimulatedPreRoll = () => {
    setAdBreakSimulated(true);
    setAdSkipLog('Detected #movie_player.ad-showing → muted video, seeked currentTime to duration, clicked .ytp-ad-skip-button-modern (0ms black screen).');
    setTimeout(() => {
      setAdBreakSimulated(false);
    }, 900);
  };

  const handleSwitchOAuthAccount = (newEmail: string) => {
    setActiveGoogleAccount(newEmail);
    setOauthModalOpen(false);
    setAdSkipLog(
      `onCreateWindow WebViewTransport synced cookies for ${newEmail} via CookieManager.getInstance().flush().`
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

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="flex items-center justify-between gap-8 px-6 py-4 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur-md sticky top-0 z-30">
        <a
          href="#top"
          className="text-lg font-bold tracking-tight text-white font-display whitespace-nowrap shrink-0"
        >
          Lock Launcher
        </a>

        <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400">
          <button
            onClick={() => setActiveWorkbenchTab('inspector')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWorkbenchTab === 'inspector' ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            AdBlock & Audio Inspector
          </button>
          <button
            onClick={() => setActiveWorkbenchTab('code')}
            className={`hover:text-white transition-colors whitespace-nowrap shrink-0 ${
              activeWorkbenchTab === 'code' ? 'text-white underline underline-offset-8' : ''
            }`}
          >
            Java & JS Patch Source
          </button>
          <button
            onClick={() => {
              setActiveWebApp(shortcuts[0]);
              setIsPlayingMedia(true);
            }}
            className="hover:text-white transition-colors whitespace-nowrap shrink-0"
          >
            WebAppActivity Simulator
          </button>
          <button
            onClick={() => {
              setActiveWebApp(null);
              setIslandExpanded(true);
            }}
            className="hover:text-white transition-colors whitespace-nowrap shrink-0"
          >
            Dynamic Island Deck
          </button>
        </nav>

        <div className="flex items-center gap-3 shrink-0">
          <button
            onClick={() => {
              setActiveWorkbenchTab('code');
              handleCopyCode(selectedCodeTab);
            }}
            className="px-4 py-2 text-xs font-semibold text-white bg-indigo-600 rounded-lg hover:bg-indigo-500 transition-colors whitespace-nowrap shrink-0 flex items-center gap-2"
          >
            {copiedKey ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedKey ? 'Copied Snippet' : 'Copy Active Patch'}</span>
          </button>
        </div>
      </header>

      {/* Main 1440px Multi-Column Workbench */}
      <main className="max-w-7xl w-full mx-auto px-6 py-8 grid grid-cols-1 lg:grid-cols-12 gap-8 flex-1 items-start">
        {/* Left 5 Columns: Interactive OnePlus 13 (OxygenOS 15) Hardware Frame Simulator */}
        <section className="lg:col-span-5 flex flex-col items-center">
          <div className="w-full max-w-[400px] rounded-[36px] border border-slate-800 bg-slate-900/90 p-4 shadow-2xl">
            {/* Simulated Device Hardware Control Strip */}
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-800/80 text-xs text-slate-400">
              <span>OnePlus 13 · 120Hz LTPO</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setIsScreenLocked((prev) => !prev)}
                  className={`px-2.5 py-1 rounded-md font-medium transition-colors flex items-center gap-1.5 whitespace-nowrap shrink-0 ${
                    isScreenLocked
                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                      : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                  }`}
                >
                  {isScreenLocked ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
                  <span>{isScreenLocked ? 'Screen Locked' : 'Lock Screen'}</span>
                </button>
                {activeWebApp && (
                  <button
                    onClick={() => setActiveWebApp(null)}
                    className="px-2.5 py-1 rounded-md bg-slate-800 text-slate-300 hover:bg-slate-700 font-medium whitespace-nowrap shrink-0"
                  >
                    Home
                  </button>
                )}
              </div>
            </div>

            {/* Viewport Screen Area */}
            <div className="relative h-[640px] rounded-[26px] bg-slate-950 border border-slate-800/90 overflow-hidden flex flex-col justify-between">
              {/* Dynamic Island v2.12 Overlay Capsule */}
              <div className="pt-3 px-3 z-20">
                {!islandExpanded ? (
                  <button
                    onClick={() => setIslandExpanded(true)}
                    className="mx-auto flex items-center justify-between gap-4 px-4 py-1.5 rounded-full bg-black border border-slate-800 text-xs text-slate-200 hover:border-indigo-500/60 transition-colors"
                  >
                    <span className="font-mono tabular-nums">94% · 120Hz</span>
                    <span className="text-slate-400">
                      {isPlayingMedia && activeWebApp ? `Playing ${activeWebApp.name}` : 'Dynamic Island'}
                    </span>
                  </button>
                ) : (
                  <div className="rounded-2xl bg-slate-900/95 backdrop-blur-md border border-slate-700/80 p-3 shadow-xl">
                    <div className="flex items-center justify-between pb-2 mb-2 border-b border-slate-800">
                      <span className="text-xs font-semibold text-white">
                        Island Deck · 4-Column Glass
                      </span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => {
                            setIslandOrganizeMode((prev) => !prev);
                            setSelectedSwapIndex(null);
                          }}
                          className={`px-2 py-1 rounded text-[11px] font-medium transition-colors whitespace-nowrap shrink-0 ${
                            islandOrganizeMode
                              ? 'bg-indigo-600 text-white'
                              : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                          }`}
                        >
                          {islandOrganizeMode ? 'Done' : 'Organize'}
                        </button>
                        <button
                          onClick={() => setIslandExpanded(false)}
                          className="p-1 rounded bg-slate-800 text-slate-400 hover:text-white"
                          aria-label="Collapse Dynamic Island"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {islandOrganizeMode && (
                      <div className="flex items-center justify-between gap-2 mb-2.5 text-[11px] text-slate-300 bg-slate-950/80 px-2.5 py-1.5 rounded-lg">
                        <span>
                          {selectedSwapIndex !== null
                            ? `Tap target slot to swap with #${selectedSwapIndex + 1}`
                            : 'Tap any two apps to swap'}
                        </span>
                        <button
                          onClick={handleSortIslandApps}
                          className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium flex items-center gap-1 whitespace-nowrap shrink-0"
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
                            className={`w-full aspect-square rounded-xl flex flex-col items-center justify-center p-1.5 transition-transform active:scale-95 border ${
                              selectedSwapIndex === idx
                                ? 'border-indigo-400 bg-indigo-950/60'
                                : 'border-slate-800 bg-slate-950/70 hover:border-slate-700'
                            }`}
                          >
                            <span
                              className={`w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold ${item.accent}`}
                            >
                              {item.symbol}
                            </span>
                            <span className="text-[10px] text-slate-300 mt-1 truncate max-w-full">
                              {item.name}
                            </span>
                          </button>
                          {islandOrganizeMode && (
                            <div className="flex items-center gap-1 mt-1">
                              <button
                                onClick={() => handleShiftApp(idx, -1)}
                                className="p-0.5 rounded bg-slate-800 text-slate-400 hover:text-white"
                                title="Shift Left"
                              >
                                <ArrowLeft className="w-2.5 h-2.5" />
                              </button>
                              <button
                                onClick={() => handleShiftApp(idx, 1)}
                                className="p-0.5 rounded bg-slate-800 text-slate-400 hover:text-white"
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
                <div className="absolute inset-0 z-10 bg-slate-950/95 backdrop-blur-md flex flex-col items-center justify-between p-6 text-center">
                  <div className="mt-12">
                    <p className="text-4xl font-bold font-mono tabular-nums text-white tracking-tight">
                      22:48
                    </p>
                    <p className="text-xs text-slate-400 mt-1">
                      Screen Off · Lock Screen Active
                    </p>
                  </div>

                  {activeWebApp && (
                    <div className="w-full rounded-2xl border border-slate-800 bg-slate-900/90 p-4 text-left">
                      <div className="flex items-center justify-between text-xs text-indigo-300 mb-1.5">
                        <span>WebAppActivity Background Audio</span>
                        <span className="font-mono tabular-nums">{formatTime(playbackSeconds)}</span>
                      </div>
                      <p className="text-sm font-semibold text-white truncate">
                        {activeWebApp.name} — Playing with Screen Off
                      </p>
                      <p className="text-xs text-slate-400 mt-1">
                        document.hidden = false · AudioFocus GAIN held · Renderer priority IMPORTANT
                      </p>
                      <div className="flex items-center justify-between mt-3 pt-3 border-t border-slate-800">
                        <button
                          onClick={() => setIsPlayingMedia((p) => !p)}
                          className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white flex items-center gap-1.5"
                        >
                          {isPlayingMedia ? (
                            <>
                              <Pause className="w-3.5 h-3.5" />
                              <span>Pause Audio</span>
                            </>
                          ) : (
                            <>
                              <Play className="w-3.5 h-3.5" />
                              <span>Resume Audio</span>
                            </>
                          )}
                        </button>
                        <button
                          onClick={() => setIsScreenLocked(false)}
                          className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200"
                        >
                          Unlock Device
                        </button>
                      </div>
                    </div>
                  )}

                  <p className="text-xs text-slate-500">
                    Double-tap or click Unlock to return to WebAppActivity
                  </p>
                </div>
              )}

              {/* Active View: Either Standalone WebAppActivity or Flagship Home Launcher */}
              {activeWebApp ? (
                <div className="flex-1 flex flex-col justify-between px-3 pb-3 pt-2 overflow-y-auto">
                  {/* Standalone WebApp Container Body (Zero Address Bar) */}
                  <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-4 flex-1 flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between gap-2 pb-3 border-b border-slate-800">
                        <div>
                          <h2 className="text-base font-bold text-white font-display">
                            {activeWebApp.name}
                          </h2>
                          <p className="text-xs text-slate-400">{activeWebApp.subtitle}</p>
                        </div>
                        <button
                          onClick={() => setOauthModalOpen((prev) => !prev)}
                          className="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
                        >
                          <UserCheck className="w-3.5 h-3.5 text-indigo-400" />
                          <span className="truncate max-w-[115px]">{activeGoogleAccount}</span>
                        </button>
                      </div>

                      {/* OAuth Account Switcher Popup Simulation */}
                      {oauthModalOpen && (
                        <div className="mt-3 p-3 rounded-xl bg-slate-950 border border-indigo-500/40">
                          <p className="text-xs font-semibold text-white mb-1">
                            onCreateWindow · Multi-Account OAuth Transport
                          </p>
                          <p className="text-[11px] text-slate-400 mb-2">
                            3rd-party cookies + DOMStorage synced & flushed before redirect:
                          </p>
                          <div className="flex flex-col gap-1.5">
                            {['crtghoul@gmail.com', 'velazquez.lab@gmail.com', 'work.profile@oneplus.dev'].map(
                              (acct) => (
                                <button
                                  key={acct}
                                  onClick={() => handleSwitchOAuthAccount(acct)}
                                  className={`px-2.5 py-1.5 rounded-lg text-xs text-left font-mono flex items-center justify-between ${
                                    activeGoogleAccount === acct
                                      ? 'bg-indigo-600/20 text-indigo-200 border border-indigo-500/40'
                                      : 'bg-slate-900 text-slate-300 hover:bg-slate-800'
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

                      {/* Simulated HTML5 Media Player with Non-Destructive Ad Guard */}
                      <div className="mt-3 rounded-xl bg-slate-950 border border-slate-800 p-3.5">
                        <div className="flex items-center justify-between text-xs text-slate-400 mb-2">
                          <span>#movie_player · HTML5 Video</span>
                          <span className="font-mono tabular-nums text-slate-300">
                            {adBreakSimulated ? 'AD BREAK → SKIPPING (16x)' : `${formatTime(playbackSeconds)} / 18:40`}
                          </span>
                        </div>

                        <div className="h-24 rounded-lg bg-gradient-to-br from-slate-900 via-indigo-950/40 to-slate-900 border border-slate-800/80 flex flex-col items-center justify-center px-3 text-center">
                          <p className="text-xs font-semibold text-white">
                            {adBreakSimulated
                              ? 'Pre-Roll Detected — Muting & Seeking to End...'
                              : `${activeWebApp.name} 120Hz Hardware Layer Stream`}
                          </p>
                          <p className="text-[11px] text-slate-400 mt-1">
                            .video-ads & .ytp-ad-module intact · Zero black screen
                          </p>
                        </div>

                        <div className="flex items-center justify-between gap-2 mt-3">
                          <button
                            onClick={() => setIsPlayingMedia((p) => !p)}
                            className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white flex items-center gap-1.5 whitespace-nowrap shrink-0"
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
                            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
                          >
                            <FastForward className="w-3.5 h-3.5 text-indigo-400" />
                            <span>Simulate Pre-Roll Ad</span>
                          </button>
                        </div>
                      </div>

                      {/* Live Telemetry Readout inside WebApp */}
                      <div className="mt-3 p-3 rounded-xl bg-slate-950/70 border border-slate-800/80 text-xs space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="text-slate-400">Page Visibility Spoof</span>
                          <span className="font-mono text-emerald-400">hidden = false</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-slate-400">Renderer Priority</span>
                          <span className="font-mono text-slate-200">IMPORTANT (keepAlive)</span>
                        </div>
                        <p className="text-[11px] text-slate-400 pt-1 border-t border-slate-800/80">
                          {adSkipLog}
                        </p>
                      </div>
                    </div>

                    {/* Floating Frosted-Glass Web Navigation Bar (Matches WebAppActivity.java) */}
                    {!isImmersiveFullscreen ? (
                      <div className="mt-3 px-3 py-2 rounded-full bg-slate-950/90 backdrop-blur-md border border-slate-700/80 flex items-center justify-between gap-2">
                        <button
                          onClick={() => setAdSkipLog('Navigated back in WebView history.')}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-slate-300 hover:bg-slate-800 text-xs"
                          title="Back"
                        >
                          ◀
                        </button>
                        <button
                          onClick={() => setAdSkipLog('Navigated forward in WebView history.')}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-slate-400 hover:bg-slate-800 text-xs"
                          title="Forward"
                        >
                          ▶
                        </button>
                        <button
                          onClick={() =>
                            setAdSkipLog(
                              'Reloaded page & re-injected BACKGROUND_PLAYBACK_JS + COSMETIC_AD_BLOCK_JS.'
                            )
                          }
                          className="w-7 h-7 rounded-full flex items-center justify-center text-slate-300 hover:bg-slate-800 text-xs"
                          title="Refresh"
                        >
                          <RotateCw className="w-3.5 h-3.5" />
                        </button>

                        <button
                          onClick={() =>
                            setAdSkipLog(`Active URL: ${activeWebApp.url} (HTTPS Verified)`)
                          }
                          className="px-2.5 py-1 rounded-full bg-slate-900 border border-slate-800 text-[11px] font-mono text-slate-200 truncate max-w-[130px]"
                        >
                          🔒 {new URL(activeWebApp.url || 'https://youtube.com').hostname.replace(/^www\./, '')}
                        </button>

                        <button
                          onClick={() => setIsImmersiveFullscreen(true)}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-slate-300 hover:bg-slate-800 text-xs"
                          title="Immersive Fullscreen"
                        >
                          <Maximize2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setActiveWebApp(null)}
                          className="w-7 h-7 rounded-full flex items-center justify-center text-rose-400 hover:bg-slate-800 text-xs"
                          title="Close WebApp"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setIsImmersiveFullscreen(false)}
                        className="mt-3 mx-auto px-3 py-1 rounded-full bg-slate-900 border border-slate-700 text-xs text-slate-300 flex items-center gap-1.5"
                      >
                        <Minimize2 className="w-3 h-3" />
                        <span>Exit Immersive Fullscreen</span>
                      </button>
                    )}
                  </div>
                </div>
              ) : (
                /* Flagship Lock Launcher Home Screen (16-Slot Grid + Clean Mode + Search/Math Drawer) */
                <div className="flex-1 flex flex-col justify-between p-4 overflow-y-auto">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <p className="text-2xl font-bold font-display text-white">Thursday, Oct 29</p>
                        <p className="text-xs text-slate-400">
                          At A Glance · 0% Ads · Offline-First Home
                        </p>
                      </div>
                      <button
                        onClick={() => setCleanModeHidden((prev) => !prev)}
                        className="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-medium text-slate-200 hover:border-slate-700 whitespace-nowrap shrink-0"
                      >
                        {cleanModeHidden ? 'Show Apps' : 'Clean Mode'}
                      </button>
                    </div>

                    {/* Search Bar + Instant MathEvaluator */}
                    <div className="relative mb-4">
                      <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Fuzzy app search or math (e.g. 120 * 8)..."
                        className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-900 border border-slate-800 text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-indigo-500"
                      />
                    </div>

                    {mathOutput && (
                      <div className="mb-3 px-3 py-2 rounded-xl bg-indigo-950/50 border border-indigo-500/40 flex items-center justify-between text-xs">
                        <span className="text-indigo-200">MathEvaluator Result</span>
                        <span className="font-mono font-bold text-white tabular-nums">
                          = {mathOutput}
                        </span>
                      </div>
                    )}

                    {/* Ongoing Background Media Notification (Applies when returning Home while playing) */}
                    {isPlayingMedia && (
                      <div className="mb-3 p-3 rounded-2xl bg-indigo-950/60 border border-indigo-500/40 text-xs">
                        <div className="flex items-center justify-between text-indigo-300 mb-1">
                          <span className="font-semibold flex items-center gap-1.5">
                            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                            Background Audio Active
                          </span>
                          <span className="font-mono tabular-nums text-indigo-200">
                            {formatTime(playbackSeconds)}
                          </span>
                        </div>
                        <p className="text-white font-medium text-[11px] truncate">
                          YT Music · Ad-Free Background Stream
                        </p>
                        <p className="text-[10px] text-slate-400 mt-0.5">
                          Ongoing notification prevents OxygenOS 15 process kill. Switched to other apps uninterrupted.
                        </p>
                        <div className="flex items-center justify-between gap-2 mt-2 pt-2 border-t border-indigo-500/30">
                          <button
                            onClick={() => setIsPlayingMedia((p) => !p)}
                            className="px-2.5 py-1 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-[11px] flex items-center gap-1"
                          >
                            <Pause className="w-3 h-3" />
                            <span>Pause</span>
                          </button>
                          <button
                            onClick={() => {
                              const ytm = shortcuts.find((s) => s.id === 'ytm') || shortcuts[0];
                              setActiveWebApp(ytm);
                            }}
                            className="px-2.5 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-200 font-medium text-[11px]"
                          >
                            Return to WebApp
                          </button>
                        </div>
                      </div>
                    )}

                    {!cleanModeHidden && (
                      <div className="grid grid-cols-4 gap-2.5">
                        {filteredShortcuts.map((app, index) => (
                          <button
                            key={app.id}
                            onClick={() => handleSlotTap(app, index)}
                            className="p-2.5 rounded-2xl bg-slate-900/80 border border-slate-800/90 hover:border-indigo-500/50 flex flex-col items-center justify-center transition-transform active:scale-95"
                          >
                            <div
                              className={`w-10 h-10 rounded-xl flex items-center justify-center text-sm font-bold ${app.accent}`}
                            >
                              {app.symbol}
                            </div>
                            <span className="text-[11px] font-medium text-slate-200 mt-1.5 truncate max-w-full">
                              {app.name}
                            </span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="pt-3 border-t border-slate-900 flex items-center justify-between text-[11px] text-slate-400">
                    <span>Tap YouTube, YT Music, or ChatGPT to test WebAppActivity</span>
                    <span className="font-mono">v2.17</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </section>

        {/* Right 7 Columns: Engineering Workbench (Live URL Interceptor + Drop-In Java/JS Source) */}
        <section className="lg:col-span-7 flex flex-col gap-6">
          {/* Architecture Summary Header */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6">
            <h1 className="text-2xl sm:text-3xl font-bold text-white font-display tracking-tight">
              WebAppActivity & AdBlockEngine Security Workbench
            </h1>
            <p className="text-sm text-slate-300 mt-2 leading-relaxed">
              Updated directly in <code className="text-indigo-300">AdBlockEngine.java</code>,{' '}
              <code className="text-indigo-300">WebAppActivity.java</code>, and{' '}
              <code className="text-indigo-300">DisplayHelper.java</code> for OnePlus 13 (OxygenOS 15, API 28–34).
            </p>
            <div className="flex flex-wrap items-center gap-2 text-xs text-slate-400 mt-4 pt-4 border-t border-slate-800/80">
              <span>Screen-Off Playback (Visibility Spoof + AudioFocus)</span>
              <span aria-hidden="true">·</span>
              <span>Non-Destructive YouTube Ad Skipper</span>
              <span aria-hidden="true">·</span>
              <span>120Hz LTPO Mode Lock & OAuth Cookie Flush</span>
            </div>

            {/* Segmented Mode Control */}
            <div className="flex items-center gap-1 p-1 bg-slate-950 border border-slate-800 rounded-xl mt-5 w-fit">
              <button
                onClick={() => setActiveWorkbenchTab('inspector')}
                className={`px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                  activeWorkbenchTab === 'inspector'
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Live URL & Player Guard Tester
              </button>
              <button
                onClick={() => setActiveWorkbenchTab('code')}
                className={`px-4 py-2 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                  activeWorkbenchTab === 'code'
                    ? 'bg-indigo-600 text-white'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Production Java & JS Source
              </button>
            </div>
          </div>

          {activeWorkbenchTab === 'inspector' ? (
            <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6 space-y-6">
              <div>
                <h2 className="text-lg font-semibold text-white font-display">
                  01. Surgical Request Interceptor (AdBlockEngine.isAdUrl)
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  Test any URL against the updated interceptor. YouTube ad breaks and telemetry endpoints are blocked before the media host whitelist, while video streams and Google OAuth flows pass untouched.
                </p>

                <div className="mt-4">
                  <label htmlFor="url-probe" className="block text-xs font-medium text-slate-300 mb-1.5">
                    Inspect Request URL
                  </label>
                  <input
                    id="url-probe"
                    type="text"
                    value={probeUrl}
                    onChange={(e) => setProbeUrl(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-xs font-mono text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                </div>

                {/* Preset Quick-Test Buttons */}
                <div className="flex flex-wrap gap-2 mt-3">
                  {PRESET_TEST_URLS.map((preset) => (
                    <button
                      key={preset.label}
                      onClick={() => setProbeUrl(preset.url)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors whitespace-nowrap shrink-0 ${
                        probeUrl === preset.url
                          ? 'bg-indigo-600/20 border-indigo-500 text-indigo-200'
                          : 'bg-slate-950 border-slate-800 text-slate-300 hover:border-slate-700'
                      }`}
                    >
                      {preset.label}
                    </button>
                  ))}
                </div>

                {/* Interception Result Table */}
                <div className="mt-5 rounded-xl border border-slate-800 bg-slate-950/90 p-4 space-y-3 text-xs">
                  <div className="flex items-center justify-between pb-2.5 border-b border-slate-800">
                    <span className="text-slate-400">Interceptor Decision</span>
                    <span
                      className={`font-semibold flex items-center gap-1.5 ${
                        analysis.blocked ? 'text-amber-300' : 'text-emerald-400'
                      }`}
                    >
                      <Shield className="w-4 h-4" />
                      <span>
                        {analysis.blocked
                          ? 'INTERCEPTED & NEUTRALIZED (Empty MIME Response)'
                          : 'ALLOWED (Passthrough to Chromium Network Stack)'}
                      </span>
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                    <div>
                      <span className="text-slate-500 block">Matched Rule</span>
                      <span className="text-slate-200 font-medium mt-0.5 block">
                        {analysis.reason}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-500 block">Synthetic Response MIME & Payload</span>
                      <span className="text-slate-200 font-mono mt-0.5 block">
                        {analysis.mimeType} → {analysis.payloadPreview}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Architectural Verification Matrix */}
              <div className="pt-4 border-t border-slate-800">
                <h3 className="text-base font-semibold text-white font-display mb-3">
                  02. Three-Layer Hardening Matrix
                </h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-slate-800 text-slate-400">
                        <th className="py-2.5 pr-4 font-semibold">Subsystem</th>
                        <th className="py-2.5 px-4 font-semibold">Failure Mode Prevented</th>
                        <th className="py-2.5 pl-4 font-semibold">Implementation in Repo</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/70 text-slate-300">
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Screen-Off Audio Survival
                        </td>
                        <td className="py-3 px-4">
                          YouTube <code className="text-indigo-300">visibilitychange</code> & Chromium surface pause when locking phone
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          BACKGROUND_PLAYBACK_JS + setRendererPriorityPolicy(IMPORTANT, false)
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          Zero Black-Screen Ad Skip
                        </td>
                        <td className="py-3 px-4">
                          Removing <code className="text-indigo-300">.video-ads</code> crashes YouTube Polymer player state machine
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          Keep .video-ads node; seek currentTime=duration & click .ytp-ad-skip-button
                        </td>
                      </tr>
                      <tr>
                        <td className="py-3 pr-4 font-medium text-white">
                          120Hz LTPO & OAuth Sync
                        </td>
                        <td className="py-3 px-4">
                          OxygenOS 60Hz WebView throttle & dropped Google account session cookies
                        </td>
                        <td className="py-3 pl-4 font-mono text-[11px]">
                          preferredDisplayModeId (120Hz) + CookieManager.flush() in onCreateWindow
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-1 p-1 bg-slate-950 border border-slate-800 rounded-xl">
                  <button
                    onClick={() => setSelectedCodeTab('ytMusicAdStrip')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'ytMusicAdStrip'
                        ? 'bg-slate-800 text-white'
                        : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    1. YT Music API Ad Strip & CSS
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('backgroundPlayback')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'backgroundPlayback'
                        ? 'bg-slate-800 text-white'
                        : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    2. Background Audio JS Injection
                  </button>
                  <button
                    onClick={() => setSelectedCodeTab('desktopUaAndKeepalive')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap shrink-0 ${
                      selectedCodeTab === 'desktopUaAndKeepalive'
                        ? 'bg-slate-800 text-white'
                        : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    3. Desktop UA & Keepalive Service
                  </button>
                </div>

                <button
                  onClick={() => handleCopyCode(selectedCodeTab)}
                  className="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-slate-200 flex items-center gap-1.5 whitespace-nowrap shrink-0"
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

              <pre className="p-4 rounded-xl bg-slate-950 border border-slate-800 text-xs font-mono text-slate-200 overflow-x-auto leading-relaxed max-h-[460px]">
                <code>{JAVA_SNIPPETS[selectedCodeTab]}</code>
              </pre>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
