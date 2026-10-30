export interface InterceptAnalysis {
  url: string;
  blocked: boolean;
  reason: string;
  mimeType: string;
  payloadPreview: string;
  corsHeader: string;
}

const AD_DOMAINS = new Set([
  'doubleclick.net',
  'googlesyndication.com',
  'googleadservices.com',
  'adservice.google.com',
  'pagead2.googlesyndication.com',
  'tpc.googlesyndication.com',
  'pagead2.googleadservices.com',
  'ads.google.com',
  'google-analytics.com',
  'googletagmanager.com',
  'googletagservices.com',
  'partner.googleadservices.com',
  'imasdk.googleapis.com',
  'innovid.com',
  'spotxchange.com',
  'spotx.tv',
  'tremorhub.com',
  'springserve.com',
  'aniview.com',
  'teads.tv',
  'unruly.co',
  'vidoomy.com',
  'kargo.com',
  'connatix.com',
  'streamrail.com',
  'vidazoo.com',
  'adnxs.com',
  'appnexus.com',
  'criteo.com',
  'criteo.net',
  'taboola.com',
  'outbrain.com',
  'pubmatic.com',
  'rubiconproject.com',
  'magnite.com',
  'openx.net',
  'openx.com',
  'indexexchange.com',
  'revcontent.com',
  'zergnet.com',
  'mgid.com',
  'nativo.com',
  'sharethrough.com',
  'triplelift.com',
  'sovrn.com',
  'smartadserver.com',
  'yieldmo.com',
  'adform.net',
  'bidswitch.net',
  'media.net',
  'buysellads.com',
  'carbonads.net',
  'amazon-adsystem.com',
  'aax.amazon-adsystem.com',
  'applovin.com',
  'vungle.com',
  'ironsrc.com',
  'unityads.unity3d.com',
  'mopub.com',
  'inmobi.com',
  'chartboost.com',
  'popads.net',
  'popcash.net',
  'propellerads.com',
  'adcash.com',
  'clickadu.com',
  'adsterra.com',
  'exoclick.com',
  'trafficjunky.com',
  'monetag.com',
  'scorecardresearch.com',
  'quantserve.com',
  'moatads.com',
  'hotjar.com',
  'clarity.ms',
]);

export function analyzeUrlInterception(rawUrl: string): InterceptAnalysis {
  const trimmed = rawUrl.trim();
  if (!trimmed) {
    return {
      url: '',
      blocked: false,
      reason: 'Empty URL',
      mimeType: 'text/plain',
      payloadPreview: '',
      corsHeader: 'Access-Control-Allow-Origin: *',
    };
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed.startsWith('http') ? trimmed : `https://${trimmed}`);
  } catch {
    return {
      url: trimmed,
      blocked: true,
      reason: 'Rejected non-HTTP/HTTPS scheme or malformed URI (P0-1 Intent/Market Guard)',
      mimeType: 'text/plain',
      payloadPreview: 'Blocked navigation',
      corsHeader: 'Access-Control-Allow-Origin: *',
    };
  }

  const host = parsed.hostname.toLowerCase();
  const path = parsed.pathname.toLowerCase();
  const lowerUrl = parsed.href.toLowerCase();

  // SponsorBlock API endpoint check
  if (host === 'sponsor.ajay.app' && path.includes('/api/skipsegments')) {
    return {
      url: parsed.href,
      blocked: false,
      reason: 'SponsorBlock Segment API — returns [start, end] timestamps for automatic sponsor/intro/outro skipping',
      mimeType: 'application/json',
      payloadPreview: '[{ "segment": [14.2, 42.8], "category": "sponsor" }]',
      corsHeader: 'Native Chromium Fetch',
    };
  }

  // 1. Surgical YouTube & YouTube Music Ad & Telemetry Interception
  if (host === 'youtube.com' || host.endsWith('.youtube.com')) {
    if (
      path.startsWith('/pagead/') ||
      path.startsWith('/api/stats/ads') ||
      path.startsWith('/api/stats/atr') ||
      path.startsWith('/ptracking') ||
      path.startsWith('/get_midroll_') ||
      path.includes('/youtubei/v1/player/ad_break') ||
      path.includes('/youtubei/v1/att/get') ||
      lowerUrl.includes('&adformat=') ||
      lowerUrl.includes('?adformat=') ||
      lowerUrl.includes('&ad_type=') ||
      lowerUrl.includes('googleads.g.doubleclick.net')
    ) {
      return buildBlockedResponse(
        parsed.href,
        'Surgical YouTube / YT Music Ad Endpoint Interception (Pre-roll, mid-roll, audio tracking blocked without breaking player stream)'
      );
    }

    if (path.includes('/youtubei/v1/player') || path.includes('/youtubei/v1/next')) {
      return {
        url: parsed.href,
        blocked: false,
        reason: 'YouTube Music InnerTube Track API — adPlacements & adSlots stripped via client-side fetch/XHR hook (100% ad-free audio stream)',
        mimeType: 'application/json',
        payloadPreview: '{ ...trackData, adPlacements: [], adSlots: [], playerAds: [] }',
        corsHeader: 'Native Chromium Fetch (Sanitized In-Flight)',
      };
    }

    return {
      url: parsed.href,
      blocked: false,
      reason: 'Whitelisted YouTube core player / UI / API request (Preserves HTML5 player integrity)',
      mimeType: 'passthrough',
      payloadPreview: 'Network Passthrough (200 OK)',
      corsHeader: 'Native Chromium Fetch',
    };
  }

  // 2. Whitelist YouTube media delivery & static image CDNs
  if (
    host === 'googlevideo.com' ||
    host.endsWith('.googlevideo.com') ||
    host === 'ytimg.com' ||
    host.endsWith('.ytimg.com') ||
    host === 'ggpht.com' ||
    host.endsWith('.ggpht.com') ||
    host === 'lh3.googleusercontent.com'
  ) {
    return {
      url: parsed.href,
      blocked: false,
      reason: 'Whitelisted YouTube Video/Audio Stream & Thumbnail CDN (Zero black-screen guarantee)',
      mimeType: 'passthrough',
      payloadPreview: 'Media Stream Passthrough',
      corsHeader: 'Native Chromium Fetch',
    };
  }

  // 3. Whitelist Google OAuth & Multi-Account Switching
  const oauthHosts = [
    'accounts.google.com',
    'myaccount.google.com',
    'apis.google.com',
    'oauth2.googleapis.com',
    'passwords.google.com',
    'ssl.gstatic.com',
    'www.gstatic.com',
    'accounts.youtube.com',
    'appleid.apple.com',
    'login.microsoftonline.com',
    'login.live.com',
    'auth.openai.com',
    'auth0.openai.com',
    'id.atlassian.com',
    'github.com',
  ];
  if (oauthHosts.some((oh) => host === oh || host.endsWith(`.${oh}`))) {
    return {
      url: parsed.href,
      blocked: false,
      reason: 'Whitelisted OAuth / Account Chooser Host (Desktop UA prevents "disallowed_useragent" block; 3rd-party cookies synced)',
      mimeType: 'passthrough',
      payloadPreview: 'OAuth Session Passthrough',
      corsHeader: 'CookieManager.flush() synced',
    };
  }

  for (const adDomain of AD_DOMAINS) {
    if (host === adDomain || host.endsWith(`.${adDomain}`)) {
      return buildBlockedResponse(parsed.href, `Matched AD_DOMAINS blocklist (${adDomain})`);
    }
  }

  if (
    lowerUrl.includes('/ads.js') ||
    lowerUrl.includes('/pagead/') ||
    lowerUrl.includes('adsbygoogle') ||
    lowerUrl.includes('/ad-banner') ||
    lowerUrl.includes('/adserver') ||
    lowerUrl.includes('/adservice') ||
    lowerUrl.includes('/ad-delivery') ||
    lowerUrl.includes('/prebid') ||
    lowerUrl.includes('doubleclick.net') ||
    lowerUrl.includes('/popunder') ||
    lowerUrl.includes('/popads') ||
    lowerUrl.includes('popup.js') ||
    lowerUrl.includes('/sponsored/')
  ) {
    return buildBlockedResponse(parsed.href, 'Matched path/query ad heuristic filter');
  }

  return {
    url: parsed.href,
    blocked: false,
    reason: 'Clean first-party / content request — allowed',
    mimeType: 'passthrough',
    payloadPreview: 'Standard WebView Network Request',
    corsHeader: 'Standard',
  };
}

function buildBlockedResponse(url: string, reason: string): InterceptAnalysis {
  const lower = url.toLowerCase();
  let mimeType = 'text/plain';
  let payloadPreview = 'Empty 0-byte stream';

  if (lower.includes('.js') || lower.includes('javascript')) {
    mimeType = 'application/javascript';
    payloadPreview = '/* blocked by lock launcher */';
  } else if (lower.includes('.css')) {
    mimeType = 'text/css';
    payloadPreview = '/* blocked by lock launcher */';
  } else if (
    lower.includes('.png') ||
    lower.includes('.jpg') ||
    lower.includes('.jpeg') ||
    lower.includes('.gif') ||
    lower.includes('.webp') ||
    lower.includes('.svg')
  ) {
    mimeType = 'image/png';
    payloadPreview = '1x1 Transparent PNG (68 bytes)';
  } else if (lower.includes('.json') || lower.includes('/youtubei/') || lower.includes('/api/stats/')) {
    mimeType = 'application/json';
    payloadPreview = '{}';
  } else if (lower.includes('.html') || lower.includes('.htm')) {
    mimeType = 'text/html';
    payloadPreview = '<!-- blocked by lock launcher -->';
  }

  return {
    url,
    blocked: true,
    reason,
    mimeType,
    payloadPreview,
    corsHeader: 'Access-Control-Allow-Origin: * | Cache-Control: no-store',
  };
}

export const JAVA_SNIPPETS = {
  ytMusicAdStrip: `// From AdBlockEngine.java — InnerTube JSON Ad Stripping + SponsorBlock + Audio-Only Mode
public static final String COSMETIC_AD_BLOCK_JS =
        "javascript:(function() {" +
        "  try {" +
        "    var h = (location.hostname || '').toLowerCase();" +
        "    if (h.indexOf('youtube.com') !== -1) {" +
        "      // 1. Hook window.fetch & XMLHttpRequest to purge adPlacements from InnerTube API" +
        "      if (!window.__llYtApiHooked) {" +
        "        window.__llYtApiHooked = true;" +
        "        var stripAdsFromJson = function(json) {" +
        "          if (!json || typeof json !== 'object') return json;" +
        "          ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams'].forEach(function(k) { delete json[k]; });" +
        "          return json;" +
        "        };" +
        "        var rawFetch = window.fetch;" +
        "        window.fetch = async function() {" +
        "          var res = await rawFetch.apply(this, arguments);" +
        "          var u = typeof arguments[0] === 'string' ? arguments[0] : (arguments[0] && arguments[0].url) || '';" +
        "          if (u.indexOf('/youtubei/v1/player') !== -1 || u.indexOf('/youtubei/v1/next') !== -1) {" +
        "            var json = await res.clone().json();" +
        "            stripAdsFromJson(json);" +
        "            return new Response(JSON.stringify(json), { status: res.status, statusText: res.statusText, headers: res.headers });" +
        "          }" +
        "          return res;" +
        "        };" +
        "      }" +
        "      // 2. SponsorBlock Automatic Segment Skipper (sponsor, selfpromo, music_offtopic, intro, outro)" +
        "      var m = (location.search || '').match(/[?&]v=([a-zA-Z0-9_-]{11})/);" +
        "      var vid = m ? m[1] : '';" +
        "      if (vid && vid !== window.__llSbVideoId) {" +
        "        window.__llSbVideoId = vid;" +
        "        fetch('https://sponsor.ajay.app/api/skipSegments?videoID=' + encodeURIComponent(vid) + '&categories=[\"sponsor\",\"selfpromo\",\"music_offtopic\",\"intro\",\"outro\"]')" +
        "          .then(function(r) { return r.ok ? r.json() : []; })" +
        "          .then(function(d) { window.__llSbSegments = Array.isArray(d) ? d.map(function(x){return x.segment;}) : []; });" +
        "      }" +
        "    }" +
        "  } catch(e) {}" +
        "})();";`,

  backgroundPlayback: `// From AdBlockEngine.java — Background & Screen-Off Playback JS Injection
public static final String BACKGROUND_PLAYBACK_JS =
        "javascript:(function() {" +
        "  if (window.__llBgPlayInstalled) return;" +
        "  window.__llBgPlayInstalled = true;" +
        "  try {" +
        "    Object.defineProperty(Document.prototype, 'hidden', { get: function() { return false; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'webkitHidden', { get: function() { return false; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });" +
        "    document.hasFocus = function() { return true; };" +
        "    window.hasFocus = function() { return true; };" +
        "  } catch (e) {}" +
        "  var blockEvents = { 'visibilitychange': 1, 'webkitvisibilitychange': 1, 'pagehide': 1, 'freeze': 1, 'blur': 1 };" +
        "  var origDocAdd = EventTarget.prototype.addEventListener;" +
        "  EventTarget.prototype.addEventListener = function(type, listener, options) {" +
        "    if (blockEvents[type] && (this === window || this === document)) return;" +
        "    return origDocAdd.call(this, type, listener, options);" +
        "  };" +
        "  setInterval(function() { window._lact = Date.now(); }, 20000);" +
        "  document.addEventListener('pause', function(e) {" +
        "    var el = e.target;" +
        "    if (el && (el.tagName === 'VIDEO' || el.tagName === 'AUDIO') && window.__llAppBackgrounded && !el.ended) {" +
        "      setTimeout(function() { try { el.play(); } catch(err) {} }, 50);" +
        "    }" +
        "  }, true);" +
        "})();";`,

  desktopUaAndKeepalive: `// From WebAppActivity.java — Power Controls (PiP, Sleep Timer, Audio-Only OLED, Desktop UA)
private void showWebAppControlMenu() {
    String[] options = new String[] {
            "Picture-in-Picture (PiP) Window",
            audioOnlyOledMode ? "Disable Audio-Only OLED Saver" : "Enable Audio-Only OLED Saver",
            sleepTimerMinutes > 0 ? "Cancel Sleep Timer (" + sleepTimerMinutes + "m active)" : "Set Sleep Timer (15m / 30m / 60m)",
            forceDesktopUa ? "Switch to Mobile User-Agent" : "Switch to Desktop User-Agent",
            "Copy Current URL"
    };
    new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getTitle() != null ? getTitle() : "Web App Controls")
            .setItems(options, (d, which) -> {
                if (which == 0) enterWebAppPipMode();
                else if (which == 1) {
                    audioOnlyOledMode = !audioOnlyOledMode;
                    webView.evaluateJavascript("document.documentElement.classList.toggle('__ll-audio-only', " + audioOnlyOledMode + ");", null);
                } else if (which == 2) showSleepTimerPicker();
            })
            .show();
}`,

  githubAutoRelease: `# From .github/workflows/release.yml — Automatic APK Build, Repo Sync & GitHub Release
on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  workflow_dispatch:

# Builds locklauncher.apk via aapt2 + javac + d8 + zipalign + apksigner,
# commits the compiled locklauncher.apk back to the branch ([skip ci]),
# and publishes LockLauncher-v2.20.apk to GitHub Releases automatically.`,
};
