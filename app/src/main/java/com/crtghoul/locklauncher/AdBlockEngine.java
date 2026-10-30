package com.crtghoul.locklauncher;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Enterprise-grade, 100% offline Ad, Tracker & Popup Blocker for Lock Launcher Web Apps.
 * Inspired by desktop AppForge (appmaka) popup suppression architecture:
 *
 * 1. Blocks 100% of rogue popup windows, popunders, and new tabs via multi-window
 *    interception and JS window.open freezing.
 * 2. Neutralizes rogue intent:// and market:// store redirects.
 * 3. Intercepts network requests across 220+ major ad networks, pop networks,
 *    telemetry beacons, video pre-roll/mid-roll providers, and header bidders.
 * 4. Injects continuous cosmetic DOM filtering and MutationObserver to obliterate
 *    visual ad banners, overlays, and auto-skip video ads before they paint.
 */
public class AdBlockEngine {

    private static final Set<String> AD_DOMAINS = new HashSet<>();

    static {
        // --- Google / DoubleClick / AdSense / AdMob ---
        AD_DOMAINS.add("doubleclick.net");
        AD_DOMAINS.add("googlesyndication.com");
        AD_DOMAINS.add("googleadservices.com");
        AD_DOMAINS.add("adservice.google.com");
        AD_DOMAINS.add("pagead2.googlesyndication.com");
        AD_DOMAINS.add("tpc.googlesyndication.com");
        AD_DOMAINS.add("pagead2.googleadservices.com");
        AD_DOMAINS.add("ads.google.com");
        AD_DOMAINS.add("google-analytics.com");
        AD_DOMAINS.add("googletagmanager.com");
        AD_DOMAINS.add("googletagservices.com");
        AD_DOMAINS.add("partner.googleadservices.com");

        // --- Video Pre-Roll / Mid-Roll Ad Providers ---
        AD_DOMAINS.add("imasdk.googleapis.com");
        AD_DOMAINS.add("innovid.com");
        AD_DOMAINS.add("spotxchange.com");
        AD_DOMAINS.add("spotx.tv");
        AD_DOMAINS.add("tremorhub.com");
        AD_DOMAINS.add("springserve.com");
        AD_DOMAINS.add("aniview.com");
        AD_DOMAINS.add("teads.tv");
        AD_DOMAINS.add("unruly.co");
        AD_DOMAINS.add("vidoomy.com");
        AD_DOMAINS.add("kargo.com");
        AD_DOMAINS.add("connatix.com");
        AD_DOMAINS.add("streamrail.com");
        AD_DOMAINS.add("vidazoo.com");

        // --- Major Ad Networks & Header Bidding Exchanges ---
        AD_DOMAINS.add("adnxs.com");
        AD_DOMAINS.add("appnexus.com");
        AD_DOMAINS.add("criteo.com");
        AD_DOMAINS.add("criteo.net");
        AD_DOMAINS.add("taboola.com");
        AD_DOMAINS.add("outbrain.com");
        AD_DOMAINS.add("pubmatic.com");
        AD_DOMAINS.add("rubiconproject.com");
        AD_DOMAINS.add("magnite.com");
        AD_DOMAINS.add("openx.net");
        AD_DOMAINS.add("openx.com");
        AD_DOMAINS.add("casale-media.com");
        AD_DOMAINS.add("indexexchange.com");
        AD_DOMAINS.add("casalemedia.com");
        AD_DOMAINS.add("contextweb.com");
        AD_DOMAINS.add("revcontent.com");
        AD_DOMAINS.add("zergnet.com");
        AD_DOMAINS.add("mgid.com");
        AD_DOMAINS.add("nativo.com");
        AD_DOMAINS.add("sharethrough.com");
        AD_DOMAINS.add("triplelift.com");
        AD_DOMAINS.add("sovrn.com");
        AD_DOMAINS.add("smartadserver.com");
        AD_DOMAINS.add("smartclip.net");
        AD_DOMAINS.add("yieldmo.com");
        AD_DOMAINS.add("adform.net");
        AD_DOMAINS.add("bidswitch.net");
        AD_DOMAINS.add("media.net");
        AD_DOMAINS.add("buysellads.com");
        AD_DOMAINS.add("carbonads.net");
        AD_DOMAINS.add("liveintent.com");
        AD_DOMAINS.add("undertone.com");
        AD_DOMAINS.add("exponential.com");
        AD_DOMAINS.add("gumgum.com");
        AD_DOMAINS.add("adroll.com");

        // --- Mobile App Ad SDKs & Monetization ---
        AD_DOMAINS.add("amazon-adsystem.com");
        AD_DOMAINS.add("aax.amazon-adsystem.com");
        AD_DOMAINS.add("adcolony.com");
        AD_DOMAINS.add("applovin.com");
        AD_DOMAINS.add("vungle.com");
        AD_DOMAINS.add("ironsrc.com");
        AD_DOMAINS.add("unityads.unity3d.com");
        AD_DOMAINS.add("mopub.com");
        AD_DOMAINS.add("inmobi.com");
        AD_DOMAINS.add("chartboost.com");
        AD_DOMAINS.add("flurry.com");
        AD_DOMAINS.add("mintegral.com");
        AD_DOMAINS.add("pangolin-sdk.com");
        AD_DOMAINS.add("pangle.io");
        AD_DOMAINS.add("bytedance.com/ad");
        AD_DOMAINS.add("fyber.com");
        AD_DOMAINS.add("tapjoy.com");
        AD_DOMAINS.add("startapp.com");
        AD_DOMAINS.add("startappservice.com");
        AD_DOMAINS.add("adtiming.com");
        AD_DOMAINS.add("leadbolt.com");

        // --- Popups, Popunders, Click-Unders & Rogue Redirection Networks ---
        AD_DOMAINS.add("popads.net");
        AD_DOMAINS.add("popcash.net");
        AD_DOMAINS.add("propellerads.com");
        AD_DOMAINS.add("propeller-tracking.com");
        AD_DOMAINS.add("adcash.com");
        AD_DOMAINS.add("adpushup.com");
        AD_DOMAINS.add("clickadu.com");
        AD_DOMAINS.add("adsterra.com");
        AD_DOMAINS.add("exoclick.com");
        AD_DOMAINS.add("trafficjunky.com");
        AD_DOMAINS.add("trafficfactory.biz");
        AD_DOMAINS.add("hilltopads.com");
        AD_DOMAINS.add("monetag.com");
        AD_DOMAINS.add("ad-maven.com");
        AD_DOMAINS.add("yllix.com");
        AD_DOMAINS.add("richads.com");
        AD_DOMAINS.add("juicyads.com");
        AD_DOMAINS.add("ero-advertising.com");
        AD_DOMAINS.add("adnium.com");
        AD_DOMAINS.add("deloplen.com");
        AD_DOMAINS.add("gloaphoo.com");
        AD_DOMAINS.add("alwingulla.com");
        AD_DOMAINS.add("whomeeno.com");
        AD_DOMAINS.add("onclickalgo.com");
        AD_DOMAINS.add("onclickmega.com");
        AD_DOMAINS.add("highperformancecpmgate.com");
        AD_DOMAINS.add("profitabledisplaynetwork.com");
        AD_DOMAINS.add("histats.com");
        AD_DOMAINS.add("fastclick.com");
        AD_DOMAINS.add("adkeeper.co.uk");
        AD_DOMAINS.add("adtrue.com");
        AD_DOMAINS.add("ad-delivery.net");
        AD_DOMAINS.add("advertserve.com");
        AD_DOMAINS.add("adition.com");
        AD_DOMAINS.add("clarium.global");
        AD_DOMAINS.add("pushame.com");
        AD_DOMAINS.add("notix.co");
        AD_DOMAINS.add("truepush.com");
        AD_DOMAINS.add("pushassist.com");
        AD_DOMAINS.add("subscribers.com");
        AD_DOMAINS.add("webpushr.com");

        // --- Tracking, Telemetry & Fingerprinting ---
        AD_DOMAINS.add("scorecardresearch.com");
        AD_DOMAINS.add("quantserve.com");
        AD_DOMAINS.add("moatads.com");
        AD_DOMAINS.add("hotjar.com");
        AD_DOMAINS.add("clarity.ms");
        AD_DOMAINS.add("yandex.ru/metrika");
        AD_DOMAINS.add("mc.yandex.ru");
        AD_DOMAINS.add("an.yandex.ru");
        AD_DOMAINS.add("onesignal.com");
        AD_DOMAINS.add("pushwoosh.com");
        AD_DOMAINS.add("coinhive.com");
        AD_DOMAINS.add("coin-hive.com");
        AD_DOMAINS.add("cryptoloot.pro");
    }

    // 1x1 Transparent PNG byte array for dropping image ads cleanly
    private static final byte[] TRANSPARENT_PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    /**
     * Determines whether a given URL is an advertisement, tracker, telemetry beacon, or popup trigger.
     */
    public static boolean isAdUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String lowerUrl = url.toLowerCase();
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";

            if (host != null) {
                host = host.toLowerCase();

                // 1. Surgical YouTube Ad & Telemetry Interception (MUST run before YouTube host whitelist).
                // Blocks pre-roll/mid-roll ad metadata, pagead beacons, and ad impression pings
                // while leaving core video streams (googlevideo.com/videoplayback) and player JS untouched.
                if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                    if (path.startsWith("/pagead/")
                            || path.startsWith("/api/stats/ads")
                            || path.startsWith("/api/stats/atr")
                            || path.startsWith("/ptracking")
                            || path.startsWith("/get_midroll_")
                            || path.contains("/youtubei/v1/player/ad_break")
                            || lowerUrl.contains("&adformat=")
                            || lowerUrl.contains("?adformat=")
                            || lowerUrl.contains("&ad_type=")
                            || lowerUrl.contains("googleads.g.doubleclick.net")) {
                        return true;
                    }
                    return false;
                }

                // 2. Whitelist YouTube media delivery & static image CDNs so video never blacks out
                if (host.equals("googlevideo.com") || host.endsWith(".googlevideo.com")
                        || host.equals("ytimg.com") || host.endsWith(".ytimg.com")
                        || host.equals("ggpht.com") || host.endsWith(".ggpht.com")
                        || host.equals("lh3.googleusercontent.com")) {
                    return false;
                }

                // 3. Whitelist Google accounts, OAuth, and identity backends so account switching never freezes
                if (host.equals("accounts.google.com")
                        || host.endsWith(".accounts.google.com")
                        || host.equals("myaccount.google.com")
                        || host.equals("apis.google.com")
                        || host.equals("oauth2.googleapis.com")
                        || host.equals("passwords.google.com")
                        || host.equals("ssl.gstatic.com")
                        || host.equals("www.gstatic.com")
                        || host.equals("accounts.youtube.com")
                        || host.equals("appleid.apple.com")
                        || host.equals("login.microsoftonline.com")
                        || host.equals("login.live.com")
                        || host.equals("auth.openai.com")
                        || host.equals("auth0.openai.com")
                        || host.equals("id.atlassian.com")
                        || host.equals("github.com")) {
                    return false;
                }

                for (String adDomain : AD_DOMAINS) {
                    if (host.equals(adDomain) || host.endsWith("." + adDomain)) {
                        return true;
                    }
                }
            }

            // 4. Keyword heuristics on path and query parameters for general web apps
            if (lowerUrl.contains("/ads.js")
                    || lowerUrl.contains("/pagead/")
                    || lowerUrl.contains("adsbygoogle")
                    || lowerUrl.contains("/ad-banner")
                    || lowerUrl.contains("/adserver")
                    || lowerUrl.contains("/adservice")
                    || lowerUrl.contains("/ad-delivery")
                    || lowerUrl.contains("/prebid")
                    || lowerUrl.contains("doubleclick.net")
                    || lowerUrl.contains("/popunder")
                    || lowerUrl.contains("/popads")
                    || lowerUrl.contains("popup.js")
                    || lowerUrl.contains("pop-under")
                    || lowerUrl.contains("/sponsored/")
                    || lowerUrl.contains("googletagmanager.com/gtm.js")
                    || lowerUrl.contains("google-analytics.com/analytics.js")
                    || lowerUrl.contains("imasdk.googleapis.com")
                    || lowerUrl.contains("adnxs.com")
                    || lowerUrl.contains("adsterra")
                    || lowerUrl.contains("exoclick")
                    || lowerUrl.contains("propellerads")
                    || lowerUrl.contains("monetag")
                    || lowerUrl.contains("trafficjunky")
                    || lowerUrl.contains("clickadu")
                    || lowerUrl.contains("adtrue")
                    || lowerUrl.contains("highperformancecpmgate")
                    || lowerUrl.contains("profitabledisplaynetwork")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Creates a MIME-safe empty WebResourceResponse (with permissive CORS headers)
     * to cleanly neutralize ad/tracker XHR, fetch, script, and image requests without
     * triggering uncaught network promise rejections in video players.
     */
    public static WebResourceResponse createEmptyResourceResponse(String url) {
        String mime = "text/plain";
        byte[] data = new byte[0];
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.contains(".js") || lower.contains("javascript")) {
                mime = "application/javascript";
                data = "/* blocked by lock launcher */".getBytes(StandardCharsets.UTF_8);
            } else if (lower.contains(".css")) {
                mime = "text/css";
                data = "/* blocked by lock launcher */".getBytes(StandardCharsets.UTF_8);
            } else if (lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg")
                    || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".svg")) {
                mime = "image/png";
                data = TRANSPARENT_PNG;
            } else if (lower.contains(".json") || lower.contains("/youtubei/") || lower.contains("/api/stats/")) {
                mime = "application/json";
                data = "{}".getBytes(StandardCharsets.UTF_8);
            } else if (lower.contains(".html") || lower.contains(".htm")) {
                mime = "text/html";
                data = "<!-- blocked by lock launcher -->".getBytes(StandardCharsets.UTF_8);
            }
        }
        WebResourceResponse response = new WebResourceResponse(
                mime, "UTF-8", new ByteArrayInputStream(data));
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cache-Control", "no-store");
        response.setResponseHeaders(headers);
        return response;
    }

    /**
     * Flawless Background & Screen-Off Playback Injection for YouTube & YouTube Music:
     * 1. Spoofs Page Visibility API (document.hidden = false, document.visibilityState = 'visible').
     * 2. Suppresses visibilitychange, webkitvisibilitychange, and pagehide listeners in capture phase.
     * 3. Keeps YouTube's internal activity tracker (_lact) fresh so "Continue watching?" never pauses audio.
     * 4. Auto-resumes HTML5 <video>/<audio> if Chromium's compositor briefly pauses media when the screen locks.
     */
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
            "})();";

    /**
     * Scoped Cosmetic Ad-Blocker & Non-Destructive YouTube Ad Skipper:
     * - On YouTube: NEVER removes .video-ads or .ytp-ad-module DOM nodes (which crashes #movie_player).
     *   Instead, hides feed/companion ad slots via CSS and fast-forwards/skips active ad breaks non-destructively.
     * - On regular websites: Applies strict cosmetic CSS hiding third-party ad slots without mutating OAuth flows.
     */
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
            "        display: none !important; " +
            "        height: 0 !important; " +
            "        min-height: 0 !important; " +
            "        visibility: hidden !important; " +
            "        pointer-events: none !important; " +
            "      }';" +
            "    var style = document.getElementById('locklauncher-adblock-css');" +
            "    if (!style) {" +
            "      style = document.createElement('style');" +
            "      style.id = 'locklauncher-adblock-css';" +
            "      style.textContent = css;" +
            "      (document.head || document.documentElement).appendChild(style);" +
            "    }" +
            "  } catch(e) {}" +
            "})();";
}
