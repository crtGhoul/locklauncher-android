package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Standalone Immersive 100% Ad-Free Web App Container.
 * Features:
 * - 100% True Fullscreen: Supports HTML5 video/canvas/game onShowCustomView fullscreen.
 * - Dedicated Floating ⛶ Fullscreen Toggle for instant distraction-free immersive mode.
 * - Zero Browser Chrome: No address bar, no search bar, no URL input.
 * - Airtight Ad & Popup Suppression (AppForge/appmaka architecture):
 *   1. Intercepts all window.open / target="_blank" popups and drops them (0% popup leaks).
 *   2. Blocks rogue store redirects (market://, intent://) and popup dialog traps.
 *   3. Network request interceptor across 220+ ad networks with MIME-safe null payloads.
 *   4. Continuous DOM ad & overlay cleanup via MutationObserver.
 * - 120Hz Hardware-Accelerated Rendering on LTPO Displays.
 */
public class WebAppActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private TextView btnToggleFullscreen;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean isImmersiveFullscreen = true;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayHelper.enforceHighRefreshRate(this);

        // Window edge-to-edge hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
        }

        setContentView(R.layout.activity_web_app);

        // Apply true immersive fullscreen immediately
        applyFullscreen(true);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null && getIntent().getData() != null) {
            url = getIntent().getDataString();
        }
        if (url == null || url.trim().isEmpty()) {
            finish();
            return;
        }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) {
            try {
                title = Uri.parse(url).getHost();
                if (title != null && title.startsWith("www.")) title = title.substring(4);
            } catch (Exception e) {
                title = "Web App";
            }
        }
        if (title == null) title = "Web App";

        setTitle(title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(title, null, 0xFF101223));
        }

        progressBar = findViewById(R.id.web_progress);
        webView = findViewById(R.id.web_view);
        fullscreenContainer = findViewById(R.id.fullscreen_custom_view_container);
        btnToggleFullscreen = findViewById(R.id.btn_toggle_fullscreen);

        if (btnToggleFullscreen != null) {
            btnToggleFullscreen.setOnClickListener(v -> {
                isImmersiveFullscreen = !isImmersiveFullscreen;
                applyFullscreen(isImmersiveFullscreen);
                btnToggleFullscreen.setText(isImmersiveFullscreen ? "⛶" : "🗗");
                Toast.makeText(this, isImmersiveFullscreen ? "Immersive Fullscreen" : "Standard View", Toast.LENGTH_SHORT).show();
            });

            // Fade floating fullscreen button slightly after 4 seconds
            handler.postDelayed(() -> {
                if (btnToggleFullscreen != null) {
                    btnToggleFullscreen.animate().alpha(0.35f).setDuration(500).start();
                }
            }, 4000);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Multi-window enabled so WebChromeClient.onCreateWindow strictly intercepts all popup triggers
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }

                // Progressive DOM ad cleansing during loading
                if (newProgress == 30 || newProgress == 60 || newProgress == 85) {
                    view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String pageTitle) {
                if (pageTitle != null && !pageTitle.isEmpty()) {
                    setTitle(pageTitle);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // 100% block popup windows!
                // If this is a real user click on a legitimate link (not an ad), navigate in the same view.
                WebView.HitTestResult hitResult = view.getHitTestResult();
                if (hitResult != null) {
                    String extra = hitResult.getExtra();
                    int type = hitResult.getType();
                    if (extra != null && !AdBlockEngine.isAdUrl(extra)
                            && (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                        view.loadUrl(extra);
                    }
                }
                // Returning false tells WebView to drop the popup window completely.
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (AdBlockEngine.isAdUrl(url)) {
                    result.cancel();
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }

            @Override
            public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                // Auto-confirm unload to defeat scam redirect traps
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                if (AdBlockEngine.isAdUrl(url)) {
                    result.cancel();
                    return true;
                }
                return super.onJsConfirm(view, url, message, result);
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                if (AdBlockEngine.isAdUrl(url)) {
                    result.cancel();
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                if (btnToggleFullscreen != null) btnToggleFullscreen.setVisibility(View.GONE);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);

                applyFullscreen(true);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                if (btnToggleFullscreen != null) btnToggleFullscreen.setVisibility(View.VISIBLE);

                applyFullscreen(isImmersiveFullscreen);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    return handleUrlNavigation(view, request.getUrl().toString());
                }
                return false;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null) {
                    return handleUrlNavigation(view, url);
                }
                return false;
            }

            private boolean handleUrlNavigation(WebView view, String destUrl) {
                if (destUrl == null || destUrl.isEmpty()) return true;

                Uri uri = Uri.parse(destUrl);
                String scheme = uri.getScheme();
                if (scheme == null) return false;
                scheme = scheme.toLowerCase();

                // Block rogue store redirects and ad intents (market://, intent://, etc.)
                if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("about") && !scheme.equals("data")) {
                    return true;
                }

                // Block known ad/tracker/popup domains
                if (AdBlockEngine.isAdUrl(destUrl)) {
                    return true;
                }

                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String reqUrl = request.getUrl().toString();
                    if (AdBlockEngine.isAdUrl(reqUrl)) {
                        return AdBlockEngine.createEmptyResourceResponse(reqUrl);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null && AdBlockEngine.isAdUrl(url)) {
                    return AdBlockEngine.createEmptyResourceResponse(url);
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
            }
        });

        webView.loadUrl(url);
    }

    private void applyFullscreen(boolean hideBars) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (hideBars) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            if (hideBars) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            // Exit fullscreen video/game first
            if (webView != null && webView.getWebChromeClient() != null) {
                webView.getWebChromeClient().onHideCustomView();
            }
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DisplayHelper.enforceHighRefreshRate(this);
        applyFullscreen(isImmersiveFullscreen);
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
