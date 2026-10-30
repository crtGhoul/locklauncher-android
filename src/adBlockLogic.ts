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

  // 1. Surgical YouTube Ad & Telemetry Interception
  if (host === 'youtube.com' || host.endsWith('.youtube.com')) {
    if (
      path.startsWith('/pagead/') ||
      path.startsWith('/api/stats/ads') ||
      path.startsWith('/api/stats/atr') ||
      path.startsWith('/ptracking') ||
      path.startsWith('/get_midroll_') ||
      path.includes('/youtubei/v1/player/ad_break') ||
      lowerUrl.includes('&adformat=') ||
      lowerUrl.includes('?adformat=') ||
      lowerUrl.includes('&ad_type=') ||
      lowerUrl.includes('googleads.g.doubleclick.net')
    ) {
      return buildBlockedResponse(
        parsed.href,
        'Surgical YouTube Ad Endpoint Interception (Pre-roll / Mid-roll / Ad Stats blocked without breaking #movie_player)'
      );
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
      reason: 'Whitelisted YouTube Video Stream / Thumbnail CDN (Zero black-screen guarantee)',
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
      reason: 'Whitelisted OAuth / Account Chooser Host (3rd-party cookies + DOMStorage preserved)',
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
  backgroundPlayback: `// From AdBlockEngine.java — Injected in onPageStarted, onPageCommitVisible, onProgressChanged, onPageFinished
public static final String BACKGROUND_PLAYBACK_JS =
        "javascript:(function() {" +
        "  if (window.__llBgPlayInstalled) return;" +
        "  window.__llBgPlayInstalled = true;" +
        "  try {" +
        "    Object.defineProperty(Document.prototype, 'hidden', { get: function() { return false; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'webkitHidden', { get: function() { return false; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
        "    Object.defineProperty(Document.prototype, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });" +
        "  } catch (e) {}" +
        "  var blockEvents = { 'visibilitychange': 1, 'webkitvisibilitychange': 1, 'mozvisibilitychange': 1, 'pagehide': 1, 'freeze': 1 };" +
        "  window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
        "  document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
        "  document.addEventListener('webkitvisibilitychange', function(e) { e.stopImmediatePropagation(); }, true);" +
        "  window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);" +
        "  var origDocAdd = Document.prototype.addEventListener;" +
        "  Document.prototype.addEventListener = function(type, listener, options) {" +
        "    if (blockEvents[type]) return;" +
        "    return origDocAdd.call(this, type, listener, options);" +
        "  };" +
        "  var h = (location.hostname || '').toLowerCase();" +
        "  if (h.indexOf('youtube.com') !== -1) {" +
        "    window.addEventListener('blur', function(e) {" +
        "      if (e.target === window || e.target === document) e.stopImmediatePropagation();" +
        "    }, true);" +
        "    setInterval(function() { window._lact = Date.now(); }, 25000);" +
        "    document.addEventListener('pause', function(e) {" +
        "      var el = e.target;" +
        "      if (el && el.tagName === 'VIDEO' && window.__llAppBackgrounded && !el.ended) {" +
        "        setTimeout(function() { try { el.play(); } catch(err) {} }, 60);" +
        "      }" +
        "    }, true);" +
        "  }" +
        "})();";`,

  cosmeticAdGuard: `// From AdBlockEngine.java — Scoped Cosmetic CSS & Non-Destructive YouTube Ad Skipper
public static final String COSMETIC_AD_BLOCK_JS =
        "javascript:(function() {" +
        "  try {" +
        "    var h = (location.hostname || '').toLowerCase();" +
        "    if (h.indexOf('accounts.google.com') !== -1 || h.indexOf('myaccount.google.com') !== -1) return;" +
        "    if (h.indexOf('youtube.com') !== -1) {" +
        "      var ytStyle = document.getElementById('ll-yt-adblock-css');" +
        "      if (!ytStyle) {" +
        "        ytStyle = document.createElement('style');" +
        "        ytStyle.id = 'll-yt-adblock-css';" +
        "        ytStyle.textContent = '" +
        "          ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, " +
        "          ytm-companion-slot, ytm-statement-banner-renderer, ytm-brand-promo-shelf-renderer, " +
        "          ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ytd-banner-promo-renderer, " +
        "          ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer, " +
        "          .ytp-ad-overlay-container, .ytp-ad-message-container, " +
        "          #masthead-ad, #player-ads { display: none !important; }';" +
        "        (document.head || document.documentElement).appendChild(ytStyle);" +
        "      }" +
        "      if (!window.__llYtAdObserver) {" +
        "        window.__llYtAdObserver = true;" +
        "        var skipYtAd = function() {" +
        "          var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');" +
        "          var isAd = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));" +
        "          var video = document.querySelector('video.html5-main-video') || document.querySelector('video');" +
        "          if (isAd && video) {" +
        "            window.__llWasAdPlaying = true;" +
        "            video.muted = true;" +
        "            if (isFinite(video.duration) && video.duration > 0.5) {" +
        "              video.currentTime = video.duration;" +
        "            } else {" +
        "              video.playbackRate = 16.0;" +
        "            }" +
        "          } else if (window.__llWasAdPlaying && video) {" +
        "            window.__llWasAdPlaying = false;" +
        "            video.muted = false;" +
        "            if (video.playbackRate > 2.0) video.playbackRate = 1.0;" +
        "          }" +
        "          var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, button[class*=\"ad-skip\"]');" +
        "          for (var i = 0; i < skipBtns.length; i++) {" +
        "            try { skipBtns[i].click(); } catch(e) {}" +
        "          }" +
        "        };" +
        "        setInterval(skipYtAd, 250);" +
        "      }" +
        "      return;" +
        "    }" +
        "    var css = '" +
        "      .adsbygoogle, [id^=\"google_ads_\"], [id^=\"div-gpt-ad\"], [id^=\"aswift_\"], " +
        "      .taboola-ad, .outbrain-ad, .mgid-ad, #carbonads, " +
        "      [data-ad-slot], [data-google-query-id], amp-ad, amp-embed[type=\"taboola\"] { " +
        "        display: none !important; height: 0 !important; min-height: 0 !important; " +
        "        visibility: hidden !important; pointer-events: none !important; " +
        "      }';" +
        "    var style = document.getElementById('locklauncher-adblock-css');" +
        "    if (!style) {" +
        "      style = document.createElement('style');" +
        "      style.id = 'locklauncher-adblock-css';" +
        "      style.textContent = css;" +
        "      (document.head || document.documentElement).appendChild(style);" +
        "    }" +
        "  } catch(e) {}" +
        "})();";`,

  webAppActivityLifecycle: `// From WebAppActivity.java — 120Hz Hardware Layer, Renderer Priority, AudioFocus & OAuth Transport
webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // Pass false for waivedWhenNotVisible so OxygenOS 15 keeps renderer active with screen off
    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
}
requestMediaAudioFocus();

@Override
protected void onPause() {
    super.onPause();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().flush();
    }
    if (webView != null) {
        // Never call webView.onPause() here — keeps YouTube & YT Music audio playing when screen locks
        webView.evaluateJavascript("window.__llAppBackgrounded = true;", null);
    }
}

@Override
protected void onResume() {
    super.onResume();
    DisplayHelper.enforceHighRefreshRate(this);
    applyFullscreen(isImmersiveFullscreen);
    requestMediaAudioFocus();
    if (webView != null) {
        webView.onResume();
        webView.evaluateJavascript("window.__llAppBackgrounded = false;", null);
    }
}`,
};
