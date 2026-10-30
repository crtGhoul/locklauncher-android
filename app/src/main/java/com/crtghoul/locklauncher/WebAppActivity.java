package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Standalone Immersive 100% Ad-Free Web App Container.
 * Features:
 * - Floating Minimalist Web Navigation Dock with Back (◀), Forward (▶), Refresh/Stop (↻/✕),
 *   Domain Pill, Fullscreen Toggle (⛶/🗗), and Close (✕).
 * - Smooth Multi-Account Authentication: Seamless Google, Microsoft, and OAuth account switching
 *   with full cross-origin cookie persistence and popup window transport routing.
 * - Anti-Block User-Agent: Sanitized Chrome Mobile headers preventing Google's embedded WebView security block.
 * - Hardware Gesture Support: Android 13+ OnBackInvokedCallback and hardware back history traversal.
 * - 100% True Fullscreen: Supports HTML5 video/canvas/game onShowCustomView fullscreen.
 * - Airtight Ad & Popup Suppression (AppForge/appmaka architecture):
 *   1. Blocks rogue store redirects (market://, intent://) and malicious popup dialog traps.
 *   2. Network request interceptor across 220+ ad networks with MIME-safe null payloads.
 *   3. Continuous DOM ad & overlay cleanup via MutationObserver.
 * - 120Hz Hardware-Accelerated Rendering on LTPO Displays.
 */
public class WebAppActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private LinearLayout webNavBar;
    private TextView btnBack;
    private TextView btnForward;
    private TextView btnRefresh;
    private TextView textDomain;
    private TextView btnToggleFullscreen;
    private TextView btnClose;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean isImmersiveFullscreen = false;
    private boolean isLoading = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    // File chooser (was missing from WebAppActivity through v2.15, so
    // <input type=file> taps did nothing in WebView: Android WebView
    // shows no picker on its own; Chrome does this itself.)
    private static final int REQ_FILE_CHOOSER = 4101;
    private ValueCallback<Uri[]> fileUploadCallback;

    // Blob-download saver state. DownloadManager cannot fetch blob:/data:
    // URLs (they live inside the page), so a blob download is fetched by
    // page JS, split into base64 chunks, and written here to Downloads.
    // The interface methods are gated on pendingBlobActive: a page can
    // only write a file while a real download the user triggered is in
    // progress, and only to Downloads, under a sanitized filename.
    private boolean pendingBlobActive = false;
    private boolean pendingBlobStarted = false;
    private String pendingBlobMime;
    private String pendingBlobFilename;
    private OutputStream pendingBlobOut;
    private Uri pendingBlobUri;
    private File pendingBlobLegacyFile;
    private int pendingBlobReceived = 0;
    private Object backCallback;

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

        // Standard clean edge-to-edge view by default
        applyFullscreen(false);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null && getIntent().getData() != null) {
            url = getIntent().getDataString();
        }
        if (url == null || url.trim().isEmpty()) {
            finish();
            return;
        }
        // P0-1: only http/https may be loaded as the initial URL. Any other
        // scheme (intent:, file:, javascript:, market:, ...) is rejected
        // instead of being handed to WebView, which would otherwise follow it.
        try {
            String scheme = Uri.parse(url.trim()).getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                finish();
                return;
            }
        } catch (Exception e) {
            finish();
            return;
        }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        // P0-1: an attacker-controlled EXTRA_TITLE must not reach the window
        // title / Recents task description verbatim. Strip control characters
        // and cap length; the host fallback below covers the empty case.
        if (title != null) {
            title = title.replaceAll("[\\p{Cntrl}]", "").trim();
            if (title.length() > 80) title = title.substring(0, 80);
            if (title.isEmpty()) title = null;
        }
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
        webNavBar = findViewById(R.id.web_nav_bar);
        btnBack = findViewById(R.id.btn_web_back);
        btnForward = findViewById(R.id.btn_web_forward);
        btnRefresh = findViewById(R.id.btn_web_refresh);
        textDomain = findViewById(R.id.text_web_domain);
        btnToggleFullscreen = findViewById(R.id.btn_toggle_fullscreen);
        btnClose = findViewById(R.id.btn_web_close);

        // Setup Back Button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                }
            });
        }

        // Setup Forward Button
        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                if (webView != null && webView.canGoForward()) {
                    webView.goForward();
                }
            });
        }

        // Setup Refresh / Stop Button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                if (webView != null) {
                    if (isLoading) {
                        webView.stopLoading();
                    } else {
                        webView.reload();
                    }
                }
            });
        }

        // Setup Domain indicator pill click (shows full URL in toast)
        if (textDomain != null) {
            textDomain.setOnClickListener(v -> {
                if (webView != null && webView.getUrl() != null) {
                    String info = (webView.getTitle() != null ? webView.getTitle() + "\n" : "") + webView.getUrl();
                    Toast.makeText(this, info, Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Setup Fullscreen Toggle Button
        if (btnToggleFullscreen != null) {
            btnToggleFullscreen.setOnClickListener(v -> {
                isImmersiveFullscreen = !isImmersiveFullscreen;
                applyFullscreen(isImmersiveFullscreen);
                btnToggleFullscreen.setText(isImmersiveFullscreen ? "🗗" : "⛶");
                Toast.makeText(this, isImmersiveFullscreen ? "Immersive Fullscreen" : "Standard View", Toast.LENGTH_SHORT).show();
            });
        }

        // Setup Close Button
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // Enable Cookies and 3rd-party cookies (essential for Google Account switching & cross-origin OAuth)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // Enforce 120Hz LTPO GPU hardware layer & prevent Android/OxygenOS from waiving
        // renderer priority when the screen turns off or the user switches apps
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        requestMediaAudioFocus();

        // Register predictive back gesture on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.window.OnBackInvokedCallback callback = this::handleBackNavigation;
            this.backCallback = callback;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback);
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

        // Sanitize User-Agent to bypass Google's "This browser or app may not be secure" block
        String defaultUa = settings.getUserAgentString();
        if (defaultUa != null) {
            String cleanUa = defaultUa.replace("; wv", "")
                    .replaceAll("Version\\/\\d+\\.\\d+\\s*", "");
            settings.setUserAgentString(cleanUa);
        }

        // P0-1: never let a loaded page reach local files or content providers.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // Multi-window and JS window opening for smooth OAuth / Account Chooser popups
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        // Blob-download bridge. Registered before any page loads; every
        // method is gated on pendingBlobActive (see BlobSaver).
        webView.addJavascriptInterface(new BlobSaver(), "LLBlobSaver");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    WebChromeClient.FileChooserParams params) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
                fileUploadCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    String type = "*/*";
                    String[] accept = params != null
                            ? params.getAcceptTypes() : null;
                    if (accept != null && accept.length == 1
                            && accept[0] != null && !accept[0].isEmpty()
                            && !accept[0].contains(",")
                            && !accept[0].startsWith(".")) {
                        type = accept[0];
                    }
                    intent.setType(type);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                            params != null && params.getMode()
                                    == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(WebAppActivity.this,
                            "Couldn't open the file picker",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    isLoading = true;
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                }
                updateNavState();

                // Progressive DOM ad cleansing and background playback hook during loading
                if (newProgress == 30 || newProgress == 60 || newProgress == 85) {
                    injectClientScripts(view);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String pageTitle) {
                if (pageTitle != null && !pageTitle.isEmpty()) {
                    setTitle(pageTitle);
                }
                updateNavState();
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (resultMsg == null) return false;

                // Create a temporary hidden WebView to receive the popup's window transport
                // so Chromium's V8 JavaScript engine does not hang or reject window.open promises
                // (e.g. during Google Account switching, OAuth, or link clicks).
                final WebView tempWebView = new WebView(view.getContext());
                WebSettings tempSettings = tempWebView.getSettings();
                tempSettings.setJavaScriptEnabled(true);
                tempSettings.setDomStorageEnabled(true);
                tempSettings.setDatabaseEnabled(true);
                tempSettings.setUserAgentString(view.getSettings().getUserAgentString());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(tempWebView, true);
                }

                tempWebView.setWebViewClient(new WebViewClient() {
                    private boolean redirected = false;

                    private boolean handleRedirect(String targetUrl) {
                        if (redirected) return true;
                        if (targetUrl == null || targetUrl.isEmpty() || targetUrl.equals("about:blank")) {
                            return false;
                        }
                        redirected = true;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().flush();
                        }
                        if (!AdBlockEngine.isAdUrl(targetUrl)) {
                            // Funnel navigation safely into the main WebView with cookies synced
                            view.post(() -> view.loadUrl(targetUrl));
                        }
                        view.post(tempWebView::destroy);
                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        if (request != null && request.getUrl() != null) {
                            return handleRedirect(request.getUrl().toString());
                        }
                        return false;
                    }

                    @SuppressWarnings("deprecation")
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        return handleRedirect(url);
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        super.onPageStarted(v, url, favicon);
                        handleRedirect(url);
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                if (transport != null) {
                    transport.setWebView(tempWebView);
                    resultMsg.sendToTarget();
                    return true;
                }
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
                if (webNavBar != null) webNavBar.setVisibility(View.GONE);
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
                if (webNavBar != null) webNavBar.setVisibility(View.VISIBLE);

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

                // Block known ad/tracker domains
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
                isLoading = true;
                updateNavState();
                injectClientScripts(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                injectClientScripts(view);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                isLoading = false;
                updateNavState();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }
                injectClientScripts(view);
            }
        });

        webView.setDownloadListener((dlUrl, userAgent, contentDisposition,
                mimeType, contentLength) -> {
            if (dlUrl == null) return;
            String scheme = null;
            try {
                scheme = Uri.parse(dlUrl).getScheme();
            } catch (Exception ignored) {
            }
            if ("blob".equals(scheme)) {
                // Client-side output (ffmpeg.wasm, canvas, fflate ZIP):
                // DownloadManager cannot fetch blob: URLs.
                startBlobDownload(dlUrl, mimeType, contentDisposition);
                return;
            }
            if ("data".equals(scheme)) {
                saveDataUrlDownload(dlUrl, mimeType, contentDisposition);
                return;
            }
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                Toast.makeText(this,
                        "This download type needs a full browser",
                        Toast.LENGTH_LONG).show();
                return;
            }
            try {
                DownloadManager dm = (DownloadManager)
                        getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) throw new IllegalStateException("no DM");
                String filename = filenameForDownload(
                        dlUrl, contentDisposition, mimeType);
                DownloadManager.Request req =
                        new DownloadManager.Request(Uri.parse(dlUrl));
                req.setTitle(filename);
                req.setDescription("Downloading via Lock Launcher");
                if (mimeType != null) req.setMimeType(mimeType);
                if (userAgent != null) {
                    req.addRequestHeader("User-Agent", userAgent);
                }
                String cookies = CookieManager.getInstance().getCookie(dlUrl);
                if (cookies != null) {
                    req.addRequestHeader("Cookie", cookies);
                }
                if (webView.getUrl() != null) {
                    req.addRequestHeader("Referer", webView.getUrl());
                }
                req.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, filename);
                req.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                dm.enqueue(req);
                Toast.makeText(this, "Downloading " + filename,
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't start download",
                        Toast.LENGTH_SHORT).show();
            }
        });

        webView.loadUrl(url);
        updateNavState();
    }

    private static String jsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) return "download";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "download";
        }
        return name;
    }

    private static String filenameForDownload(String url,
            String contentDisposition, String mimeType) {
        String name = sanitizeFilename(
                URLUtil.guessFileName(url, contentDisposition, mimeType));
        if (!name.contains(".")) {
            String ext = mimeType != null
                    ? MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mimeType) : null;
            if (ext != null && !ext.isEmpty()) name += "." + ext;
        }
        return name;
    }

    private OutputStream openDownloadsOutput(String filename, String mime)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            if (mime != null) {
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
            }
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            pendingBlobUri = uri;
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) {
                throw new IOException("openOutputStream failed");
            }
            return out;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, filename);
            pendingBlobLegacyFile = file;
            return new FileOutputStream(file);
        }
    }

    private void abortPendingBlob() {
        pendingBlobActive = false;
        pendingBlobStarted = false;
        if (pendingBlobOut != null) {
            try {
                pendingBlobOut.close();
            } catch (IOException ignored) {
            }
            pendingBlobOut = null;
        }
        try {
            if (pendingBlobUri != null) {
                getContentResolver().delete(pendingBlobUri, null, null);
            } else if (pendingBlobLegacyFile != null) {
                pendingBlobLegacyFile.delete();
            }
        } catch (Exception ignored) {
        }
        pendingBlobUri = null;
        pendingBlobLegacyFile = null;
        pendingBlobReceived = 0;
    }

    private void startBlobDownload(String blobUrl, String mimeType,
            String contentDisposition) {
        abortPendingBlob();
        pendingBlobActive = true;
        pendingBlobStarted = false;
        pendingBlobMime = mimeType;
        pendingBlobFilename = filenameForDownload(
                blobUrl, contentDisposition, mimeType);
        String js = "(function(){"
                + "fetch(" + jsString(blobUrl) + ")"
                + ".then(function(r){return r.blob();})"
                + ".then(function(b){return new Promise(function(res,rej){"
                + "var fr=new FileReader();"
                + "fr.onload=function(){res(fr.result);};"
                + "fr.onerror=function(){rej(new Error('read failed'));};"
                + "fr.readAsDataURL(b);});})"
                + ".then(function(dataUrl){"
                + "var base64=(dataUrl.split(',')[1]||'');"
                + "var CH=1000000;"
                + "var total=Math.ceil(base64.length/CH);"
                + "window.LLBlobSaver.begin("
                + jsString(pendingBlobFilename) + ",total);"
                + "for(var i=0;i<total;i++){"
                + "window.LLBlobSaver.chunk(i,"
                + "base64.substring(i*CH,Math.min(base64.length,(i+1)*CH)));}"
                + "window.LLBlobSaver.end();})"
                + ".catch(function(e){"
                + "window.LLBlobSaver.error(String(e));});"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void saveDataUrlDownload(String dataUrl, String mimeType,
            String contentDisposition) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) throw new IOException("bad data URL");
            String header = dataUrl.substring(5, comma);
            String dataPart = dataUrl.substring(comma + 1);
            String mime = mimeType;
            if (mime == null && !header.isEmpty()) {
                mime = header.split(";")[0];
                if (mime.isEmpty()) mime = null;
            }
            byte[] bytes;
            if (header.contains(";base64")) {
                bytes = Base64.decode(dataPart, Base64.DEFAULT);
            } else {
                bytes = Uri.decode(dataPart).getBytes("UTF-8");
            }
            String filename = filenameForDownload(
                    dataUrl, contentDisposition, mime);
            OutputStream out = openDownloadsOutput(filename, mime);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            pendingBlobUri = null;
            pendingBlobLegacyFile = null;
            Toast.makeText(this, "Saved to Downloads: " + filename,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            pendingBlobUri = null;
            pendingBlobLegacyFile = null;
            Toast.makeText(this, "Download failed",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Page-side blob saver. Called only while a user-triggered download
     * is pending (pendingBlobActive); stray calls are ignored, and the
     * only destination is Downloads under a sanitized filename.
     */
    private final class BlobSaver {
        @JavascriptInterface
        public void begin(String filename, int totalChunks) {
            if (!pendingBlobActive) return;
            try {
                String safe = sanitizeFilename(filename);
                if (!safe.isEmpty()) pendingBlobFilename = safe;
                pendingBlobOut = openDownloadsOutput(
                        pendingBlobFilename, pendingBlobMime);
                pendingBlobStarted = true;
                pendingBlobReceived = 0;
                if (totalChunks <= 0) end();
            } catch (Exception e) {
                failBlob();
            }
        }

        @JavascriptInterface
        public void chunk(int index, String base64Chunk) {
            if (!pendingBlobActive || !pendingBlobStarted
                    || pendingBlobOut == null || base64Chunk == null) {
                return;
            }
            try {
                byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);
                pendingBlobOut.write(bytes);
                pendingBlobReceived++;
            } catch (Exception e) {
                failBlob();
            }
        }

        @JavascriptInterface
        public void end() {
            if (!pendingBlobActive) return;
            try {
                if (pendingBlobOut != null) pendingBlobOut.close();
            } catch (IOException ignored) {
            }
            pendingBlobOut = null;
            // Finished file: clear the URI so a later abort won't delete it.
            pendingBlobUri = null;
            pendingBlobLegacyFile = null;
            pendingBlobActive = false;
            pendingBlobStarted = false;
            final String name = pendingBlobFilename;
            handler.post(() -> Toast.makeText(WebAppActivity.this,
                    "Saved to Downloads: " + name,
                    Toast.LENGTH_LONG).show());
        }

        @JavascriptInterface
        public void error(String message) {
            failBlob();
        }

        private void failBlob() {
            try {
                if (pendingBlobOut != null) pendingBlobOut.close();
            } catch (IOException ignored) {
            }
            pendingBlobOut = null;
            try {
                if (pendingBlobUri != null) {
                    getContentResolver().delete(pendingBlobUri, null, null);
                } else if (pendingBlobLegacyFile != null) {
                    pendingBlobLegacyFile.delete();
                }
            } catch (Exception ignored) {
            }
            pendingBlobUri = null;
            pendingBlobLegacyFile = null;
            pendingBlobActive = false;
            pendingBlobStarted = false;
            handler.post(() -> Toast.makeText(WebAppActivity.this,
                    "Download failed", Toast.LENGTH_SHORT).show());
        }
    }

    private void updateNavState() {
        if (webView == null) return;

        boolean canBack = webView.canGoBack();
        if (btnBack != null) {
            btnBack.setEnabled(canBack);
            btnBack.setAlpha(canBack ? 1.0f : 0.35f);
        }

        boolean canFwd = webView.canGoForward();
        if (btnForward != null) {
            btnForward.setEnabled(canFwd);
            btnForward.setAlpha(canFwd ? 1.0f : 0.35f);
        }

        if (btnRefresh != null) {
            btnRefresh.setText(isLoading ? "✕" : "↻");
        }

        if (textDomain != null) {
            String curUrl = webView.getUrl();
            if (curUrl != null && !curUrl.isEmpty()) {
                try {
                    Uri u = Uri.parse(curUrl);
                    String host = u.getHost();
                    if (host != null) {
                        if (host.startsWith("www.")) host = host.substring(4);
                        boolean isHttps = "https".equalsIgnoreCase(u.getScheme());
                        textDomain.setText((isHttps ? "🔒 " : "🌐 ") + host);
                    }
                } catch (Exception ignored) {
                }
            }
        }
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

    private void handleBackNavigation() {
        if (customView != null) {
            // Exit fullscreen video/game first
            if (webView != null && webView.getWebChromeClient() != null) {
                webView.getWebChromeClient().onHideCustomView();
            }
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        finish();
    }

    private void injectClientScripts(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(AdBlockEngine.BACKGROUND_PLAYBACK_JS, null);
        view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
    }

    private void requestMediaAudioFocus() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attrs)
                            .setWillPauseWhenDucked(false)
                            .setOnAudioFocusChangeListener(focusChange -> {
                                // Retain WebView playback state across transient system notifications
                            })
                            .build();
                }
                audioManager.requestAudioFocus(audioFocusRequest);
            }
        } catch (Exception ignored) {
        }
    }

    private void abandonMediaAudioFocus() {
        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }
        if (webView != null) {
            // Do NOT call webView.onPause() here — calling onPause() suspends Chromium's
            // media thread and kills background / screen-off YouTube & YouTube Music audio.
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || fileUploadCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            android.content.ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                results = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    results[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileUploadCallback.onReceiveValue(results);
        fileUploadCallback = null;
    }

    @Override
    protected void onDestroy() {
        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
        }
        abortPendingBlob();
        abandonMediaAudioFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
            } catch (Exception ignored) {
            }
            backCallback = null;
        }
        if (webView != null) {
            webView.onPause();
            webView.destroy();
        }
        super.onDestroy();
    }
}