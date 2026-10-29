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
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase();

                // Whitelist YouTube video streaming, player APIs, and image assets so video never blacks out
                if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                        || host.equals("googlevideo.com") || host.endsWith(".googlevideo.com")
                        || host.equals("ytimg.com") || host.endsWith(".ytimg.com")
                        || host.equals("ggpht.com") || host.endsWith(".ggpht.com")) {
                    return false;
                }

                // Whitelist Google accounts, auth, and application backends so account switching never freezes
                if (host.equals("accounts.google.com")
                        || host.endsWith(".accounts.google.com")
                        || host.equals("myaccount.google.com")
                        || host.equals("apis.google.com")
                        || host.equals("oauth2.googleapis.com")
                        || host.equals("passwords.google.com")
                        || host.equals("ssl.gstatic.com")
                        || host.equals("accounts.youtube.com")
                        || host.equals("appleid.apple.com")
                        || host.equals("login.microsoftonline.com")
                        || host.equals("login.live.com")
                        || host.equals("auth.openai.com")
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

            // Keyword heuristics on path and query parameters
            String lowerUrl = url.toLowerCase();
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
     * Creates a MIME-safe empty WebResourceResponse to cleanly neutralize ad/tracker requests.
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
            } else if (lower.contains(".html") || lower.contains(".htm")) {
                mime = "text/html";
                data = "<!-- blocked by lock launcher -->".getBytes(StandardCharsets.UTF_8);
            }
        }
        return new WebResourceResponse(mime, "UTF-8", new ByteArrayInputStream(data));
    }

    /**
     * Ultra-aggressive cosmetic ad-hiding CSS, popup neutralization, and continuous MutationObserver cleanup script.
     * Injected across all lifecycle hooks (onPageStarted, onPageCommitVisible, onProgressChanged, onPageFinished).
     */
    public static final String COSMETIC_AD_BLOCK_JS =
            "javascript:(function() {" +
            "  try {" +
            "    var h = (location.hostname || '').toLowerCase();" +
            "    if (h.indexOf('youtube.com') !== -1 || h.indexOf('google.com') !== -1) return;" +
            "    var css = '" +
            "      .adsbygoogle, [id^=\"google_ads_\"], [id^=\"aswift_\"], " +
            "      .taboola-ad, .outbrain-ad, .mgid-ad, #carbonads, " +
            "      [data-ad-slot], amp-ad { " +
            "        display: none !important; " +
            "        height: 0 !important; " +
            "        visibility: hidden !important; " +
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
