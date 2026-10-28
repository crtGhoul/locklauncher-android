package com.crtghoul.locklauncher;

import android.net.Uri;
import java.util.HashSet;
import java.util.Set;

/**
 * High-performance, offline zero-dependency Ad & Tracker Blocker for Web Apps.
 * Intercepts network requests and suppresses ad domains, trackers, and popup banners.
 */
public class AdBlockEngine {

    private static final Set<String> AD_DOMAINS = new HashSet<>();

    static {
        // Major Ad Networks & Exchange Brokers
        AD_DOMAINS.add("doubleclick.net");
        AD_DOMAINS.add("googlesyndication.com");
        AD_DOMAINS.add("googleadservices.com");
        AD_DOMAINS.add("adservice.google.com");
        AD_DOMAINS.add("adnxs.com");
        AD_DOMAINS.add("criteo.com");
        AD_DOMAINS.add("criteo.net");
        AD_DOMAINS.add("taboola.com");
        AD_DOMAINS.add("outbrain.com");
        AD_DOMAINS.add("scorecardresearch.com");
        AD_DOMAINS.add("amazon-adsystem.com");
        AD_DOMAINS.add("adcolony.com");
        AD_DOMAINS.add("applovin.com");
        AD_DOMAINS.add("vungle.com");
        AD_DOMAINS.add("mopub.com");
        AD_DOMAINS.add("popads.net");
        AD_DOMAINS.add("propellerads.com");
        AD_DOMAINS.add("adpushup.com");
        AD_DOMAINS.add("inmobi.com");
        AD_DOMAINS.add("flurry.com");
        AD_DOMAINS.add("chartboost.com");
        AD_DOMAINS.add("adform.net");
        AD_DOMAINS.add("bidswitch.net");
        AD_DOMAINS.add("pubmatic.com");
        AD_DOMAINS.add("rubiconproject.com");
        AD_DOMAINS.add("openx.net");
        AD_DOMAINS.add("casale-media.com");
        AD_DOMAINS.add("contextweb.com");
        AD_DOMAINS.add("revcontent.com");
        AD_DOMAINS.add("zergnet.com");
        AD_DOMAINS.add("mgid.com");
        AD_DOMAINS.add("exoclick.com");
        AD_DOMAINS.add("trafficjunky.com");
        AD_DOMAINS.add("clickadu.com");
        AD_DOMAINS.add("ad-delivery.net");
        AD_DOMAINS.add("adition.com");
        AD_DOMAINS.add("smartadserver.com");
        AD_DOMAINS.add("yieldmo.com");
        AD_DOMAINS.add("quantserve.com");
        AD_DOMAINS.add("moatads.com");
        AD_DOMAINS.add("adroll.com");
        AD_DOMAINS.add("advertserve.com");
        AD_DOMAINS.add("adtrue.com");
    }

    public static boolean isAdUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase();

            for (String adDomain : AD_DOMAINS) {
                if (host.equals(adDomain) || host.endsWith("." + adDomain)) {
                    return true;
                }
            }

            // Keyword heuristics on path/query
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("/ads.js") || lowerUrl.contains("/pagead/")
                    || lowerUrl.contains("adsbygoogle") || lowerUrl.contains("google_ad_")
                    || lowerUrl.contains("/ad-banner") || lowerUrl.contains("/adserver/")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Cosmetic ad-hiding CSS injected into standalone Web Apps.
     */
    public static final String COSMETIC_AD_BLOCK_CSS =
            "javascript:(function() {" +
            "var style = document.getElementById('locklauncher-adblock-css');" +
            "if (!style) {" +
            "  style = document.createElement('style');" +
            "  style.id = 'locklauncher-adblock-css';" +
            "  style.innerHTML = '" +
            "    .ad, .ads, .adsbygoogle, .advertisement, .ad-banner, .ad-container, " +
            "    .ad-wrapper, .sponsored-post, [id^=\"ad_\"], [id^=\"google_ads_\"], " +
            "    [class*=\"ad-slot\"], [class*=\"banner-ad\"] { display: none !important; height: 0 !important; visibility: hidden !important; }';" +
            "  document.head.appendChild(style);" +
            "}})();";
}
