package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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

import java.io.ByteArrayInputStream;

/**
 * Standalone Immersive Ad-Free Web App Container.
 * Features:
 * - 100% True Fullscreen: Supports HTML5 video/canvas/game onShowCustomView fullscreen.
 * - Dedicated Floating ⛶ Fullscreen Toggle for instant distraction-free immersive mode.
 * - Zero Browser Chrome: No address bar, no search bar, no URL input.
 * - Airtight Ad & Tracker Blocking: Network request interceptor + continuous DOM ad-removal.
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

        // Block popups and multiple windows to keep the user in the ad-free container
        settings.setSupportMultipleWindows(false);
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
            }

            @Override
            public void onReceivedTitle(WebView view, String pageTitle) {
                if (pageTitle != null && !pageTitle.isEmpty()) {
                    setTitle(pageTitle);
                }
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
                    String reqUrl = request.getUrl().toString();
                    if (AdBlockEngine.isAdUrl(reqUrl)) {
                        // Block ad redirect / popup url completely
                        return true;
                    }
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String reqUrl = request.getUrl().toString();
                    if (AdBlockEngine.isAdUrl(reqUrl)) {
                        // Return completely empty stream to drop the ad request instantly
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                // Continuous DOM ad-cleaner and cosmetic CSS injection
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
