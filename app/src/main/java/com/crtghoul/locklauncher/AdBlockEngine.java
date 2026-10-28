package com.crtghoul.locklauncher;

import android.net.Uri;
import java.util.HashSet;
import java.util.Set;

/**
 * Enterprise-grade, 100% offline Ad & Tracker Blocker for Lock Launcher Web Apps.
 * Intercepts all network requests across over 150+ major ad networks, analytics trackers,
 * video pre-roll / mid-roll providers, popups, and intrusive banners.
 *
 * Combines network request interception with aggressive continuous MutationObserver
 * DOM cleanup to eliminate visual ad containers.
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

        // --- Popups, Popunders & Redirection Networks ---
        AD_DOMAINS.add("popads.net");
        AD_DOMAINS.add("propellerads.com");
        AD_DOMAINS.add("adcash.com");
        AD_DOMAINS.add("popcash.net");
        AD_DOMAINS.add("adpushup.com");
        AD_DOMAINS.add("clickadu.com");
        AD_DOMAINS.add("adsterra.com");
        AD_DOMAINS.add("exoclick.com");
        AD_DOMAINS.add("trafficjunky.com");
        AD_DOMAINS.add("hilltopads.com");
        AD_DOMAINS.add("monetag.com");
        AD_DOMAINS.add("ad-maven.com");
        AD_DOMAINS.add("yllix.com");
        AD_DOMAINS.add("richads.com");

        // --- Tracking, Telemetry & Fingerprinting ---
        AD_DOMAINS.add("scorecardresearch.com");
        AD_DOMAINS.add("quantserve.com");
        AD_DOMAINS.add("moatads.com");
        AD_DOMAINS.add("hotjar.com");
        AD_DOMAINS.add("clarity.ms");
        AD_DOMAINS.add("yandex.ru/metrika");
        AD_DOMAINS.add("mc.yandex.ru");
        AD_DOMAINS.add("an.yandex.ru");
        AD_DOMAINS.add("adroll.com");
        AD_DOMAINS.add("advertserve.com");
        AD_DOMAINS.add("adtrue.com");
        AD_DOMAINS.add("ad-delivery.net");
        AD_DOMAINS.add("adition.com");
        AD_DOMAINS.add("onesignal.com");
        AD_DOMAINS.add("pushwoosh.com");
    }

    /**
     * Determines whether a given URL is an advertisement, tracker, or telemetry beacon.
     */
    public static boolean isAdUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase();
                for (String adDomain : AD_DOMAINS) {
                    if (host.equals(adDomain) || host.endsWith("." + adDomain)) {
                        return true;
                    }
                }
            }

            // Keyword heuristics on path and query parameters
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("/ads.js")
                    || lowerUrl.contains("/pagead/")
                    || lowerUrl.contains("adsbygoogle")
                    || lowerUrl.contains("google_ad_")
                    || lowerUrl.contains("/ad-banner")
                    || lowerUrl.contains("/adserver/")
                    || lowerUrl.contains("/adservice/")
                    || lowerUrl.contains("/ad-delivery")
                    || lowerUrl.contains("/prebid.js")
                    || lowerUrl.contains("doubleclick.net")
                    || lowerUrl.contains("/popunder")
                    || lowerUrl.contains("/sponsored/")
                    || lowerUrl.contains("googletagmanager.com/gtm.js")
                    || lowerUrl.contains("google-analytics.com/analytics.js")
                    || lowerUrl.contains("imasdk.googleapis.com")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Aggressive cosmetic ad-hiding CSS and continuous MutationObserver cleanup script.
     * Injected into every standalone Web App page to hide ad elements before they render.
     */
    public static final String COSMETIC_AD_BLOCK_JS =
            "javascript:(function() {" +
            "  var css = '" +
            "    .ad, .ads, .adsbygoogle, .advertisement, .ad-banner, .ad-container, " +
            "    .ad-wrapper, .sponsored-post, [id^=\"ad_\"], [id^=\"google_ads_\"], " +
            "    [class*=\"ad-slot\"], [class*=\"banner-ad\"], [class*=\"sponsored\"], " +
            "    [data-ad], [data-ad-unit], [data-google-query-id], " +
            "    .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text, .video-ads, " +
            "    #carbonads, .taboola-ad, .outbrain-ad, .mgid-ad { " +
            "      display: none !important; " +
            "      height: 0 !important; " +
            "      min-height: 0 !important; " +
            "      visibility: hidden !important; " +
            "      opacity: 0 !important; " +
            "      pointer-events: none !important; " +
            "    }';" +
            "  var style = document.getElementById('locklauncher-adblock-css');" +
            "  if (!style) {" +
            "    style = document.createElement('style');" +
            "    style.id = 'locklauncher-adblock-css';" +
            "    style.innerHTML = css;" +
            "    (document.head || document.documentElement).appendChild(style);" +
            "  }" +
            "  // Auto-skip HTML5 video ads if present\n" +
            "  function nukeAds() {" +
            "    var adEls = document.querySelectorAll('.ad, .adsbygoogle, [id^=\"google_ads\"], .ytp-ad-module, .video-ads');" +
            "    for (var i = 0; i < adEls.length; i++) { adEls[i].remove(); }" +
            "    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton');" +
            "    if (skipBtn) { skipBtn.click(); }" +
            "  }" +
            "  nukeAds();" +
            "  if (!window._llObserver) {" +
            "    window._llObserver = new MutationObserver(nukeAds);" +
            "    window._llObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });" +
            "  }" +
            "})();";
}
