package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.lang.ref.WeakReference;

/**
 * Standalone Immersive 100% Ad-Free Installed Web App Container (v2.21).
 * Features:
 * - Installed App Standalone Mode: Launches full-bleed with zero bottom browser chrome covering
 *   YouTube Music or YouTube controls; includes a sleek collapsible TOP bar (Return, Forward,
 *   Restart, Domain/Power Menu, Install as App, Fullscreen, Hide Bar, Close).
 * - Native Android MediaSession integration: Populates the system Top-Left Swipe-Down Music Square
 *   and Lock Screen media player with live artwork, track metadata, and hardware transport controls.
 * - Instant Tap-to-Open Resume (singleTask + onNewIntent): Tapping the Music Square, Dynamic Island,
 *   or notification resumes the active WebApp session immediately without reloading the page.
 * - 120Hz Hardware-Accelerated Rendering on LTPO Displays.
 */
public class WebAppActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";

    private static volatile WeakReference<WebAppActivity> sActiveInstance = new WeakReference<>(null);
    private static volatile String sLastLoadedUrl = "";

    public static boolean isAlive() {
        WebAppActivity act = sActiveInstance.get();
        return act != null && !act.isFinishing() && !act.isDestroyed();
    }

    public static String getActiveUrl() {
        WebAppActivity act = sActiveInstance.get();
        if (act != null && act.webView != null && act.webView.getUrl() != null) {
            return act.webView.getUrl();
        }
        return sLastLoadedUrl;
    }

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private LinearLayout webNavBar;
    private View webTopHandle;
    private TextView btnBack;
    private TextView btnForward;
    private TextView btnRefresh;
    private TextView textDomain;
    private TextView btnInstallWebApp;
    private TextView btnToggleFullscreen;
    private TextView btnHideTopBar;
    private TextView btnClose;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean isImmersiveFullscreen = false;
    private boolean isTopBarVisible = false;
    private boolean isLoading = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaSession mediaSession;
    private static final String PLAYBACK_CHANNEL = "webapp_bg_audio";
    private static final int PLAYBACK_NOTIF_ID = 4040;
    private static final String DESKTOP_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36";
    private boolean forceDesktopUa = false;
    private boolean audioOnlyOledMode = false;
    private int sleepTimerMinutes = 0;
    private String currentMediaTitle = "";
    private String currentMediaArtist = "";
    private boolean currentMediaPlaying = false;
    private final BroadcastReceiver mediaCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || webView == null) return;
            String cmd = intent.getStringExtra(NowPlayingHelper.EXTRA_CMD);
            if (cmd == null) return;
            executeWebMediaCommand(cmd);
        }
    };
    private final Runnable sleepTimerRunnable = () -> {
        sleepTimerMinutes = 0;
        if (webView != null) {
            webView.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(e){try{e.pause();}catch(err){}});", null);
        }
        currentMediaPlaying = false;
        NowPlayingHelper.updateWebMedia(WebAppActivity.this, currentMediaTitle, currentMediaArtist, false, webView != null ? webView.getUrl() : "");
        abandonMediaAudioFocus();
        updatePlaybackNotification(false);
        Toast.makeText(WebAppActivity.this, "Sleep timer finished — playback paused", Toast.LENGTH_SHORT).show();
    };

    private static final int REQ_FILE_CHOOSER = 4101;
    private ValueCallback<Uri[]> fileUploadCallback;

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
        sActiveInstance = new WeakReference<>(this);
        DisplayHelper.enforceHighRefreshRate(this);

        // Window edge-to-edge hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(0xFF090A0F);
            getWindow().setNavigationBarColor(0xFF090A0F);
        }

        setContentView(R.layout.activity_web_app);

        applyFullscreen(false);

        String url = extractSafeHttpUrl(getIntent());
        if (url == null) {
            if (sLastLoadedUrl != null && !sLastLoadedUrl.isEmpty()) {
                url = sLastLoadedUrl;
            } else {
                finish();
                return;
            }
        }
        sLastLoadedUrl = url;

        String title = sanitizeTitle(getIntent().getStringExtra(EXTRA_TITLE), url);
        setTitle(title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(title, null, 0xFF090A0F));
        }

        progressBar = findViewById(R.id.web_progress);
        webView = findViewById(R.id.web_view);
        fullscreenContainer = findViewById(R.id.fullscreen_custom_view_container);
        webNavBar = findViewById(R.id.web_nav_bar);
        webTopHandle = findViewById(R.id.web_top_handle);
        btnBack = findViewById(R.id.btn_web_back);
        btnForward = findViewById(R.id.btn_web_forward);
        btnRefresh = findViewById(R.id.btn_web_refresh);
        textDomain = findViewById(R.id.text_web_domain);
        btnInstallWebApp = findViewById(R.id.btn_install_webapp);
        btnToggleFullscreen = findViewById(R.id.btn_toggle_fullscreen);
        btnHideTopBar = findViewById(R.id.btn_hide_top_bar);
        btnClose = findViewById(R.id.btn_web_close);

        // Installed Standalone App Mode: top bar is hidden by default unless user enabled it
        isTopBarVisible = ShortcutStore.isWebAppTopBarVisible(this);
        applyTopBarVisibility(isTopBarVisible, false);

        if (webTopHandle != null) {
            GlassThemeHelper.applyToView(this, webTopHandle, 12);
            webTopHandle.setOnClickListener(v -> {
                HapticHelper.tick(v);
                applyTopBarVisibility(true, true);
            });
            webTopHandle.setOnLongClickListener(v -> {
                HapticHelper.tick(v);
                showWebAppControlMenu();
                return true;
            });
        }

        if (btnHideTopBar != null) {
            btnHideTopBar.setOnClickListener(v -> {
                HapticHelper.tick(v);
                applyTopBarVisibility(false, true);
            });
        }

        if (btnInstallWebApp != null) {
            btnInstallWebApp.setOnClickListener(v -> {
                HapticHelper.tick(v);
                installCurrentWebAppAsStandalone();
            });
        }

        // Setup Back Button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                HapticHelper.tick(v);
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                }
            });
        }

        // Setup Forward Button
        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                HapticHelper.tick(v);
                if (webView != null && webView.canGoForward()) {
                    webView.goForward();
                }
            });
        }

        // Setup Refresh / Stop Button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                HapticHelper.tick(v);
                if (webView != null) {
                    if (isLoading) {
                        webView.stopLoading();
                    } else {
                        webView.reload();
                    }
                }
            });
        }

        // Setup Domain indicator pill click / long-press (opens WebApp Power Controls Menu)
        if (textDomain != null) {
            textDomain.setOnClickListener(v -> showWebAppControlMenu());
            textDomain.setOnLongClickListener(v -> {
                showWebAppControlMenu();
                return true;
            });
        }

        // Setup Fullscreen Toggle Button
        if (btnToggleFullscreen != null) {
            btnToggleFullscreen.setOnClickListener(v -> {
                HapticHelper.tick(v);
                isImmersiveFullscreen = !isImmersiveFullscreen;
                applyFullscreen(isImmersiveFullscreen);
                btnToggleFullscreen.setText(isImmersiveFullscreen ? "🗗" : "⛶");
                if (isImmersiveFullscreen) {
                    applyTopBarVisibility(false, true);
                }
                Toast.makeText(this, isImmersiveFullscreen ? "Immersive Fullscreen" : "Standard View", Toast.LENGTH_SHORT).show();
            });
        }

        // Setup Close Button
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        initMediaSession();

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

        initPlaybackNotificationChannel();

        // Sanitize User-Agent & provide desktop Chrome UA for YouTube Music to enable full player & sign-in
        applyUserAgentForUrl(url);

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
        webView.addJavascriptInterface(new MediaBridge(), "LLMediaBridge");

        try {
            IntentFilter mf = new IntentFilter(NowPlayingHelper.ACTION_MEDIA_COMMAND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mediaCommandReceiver, mf, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mediaCommandReceiver, mf);
            }
        } catch (Exception ignored) {
        }

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
                applyTopBarVisibility(isTopBarVisible, false);

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
                applyUserAgentForUrl(url);
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        setIntent(intent);
        String newUrl = extractSafeHttpUrl(intent);
        if (newUrl == null || newUrl.isEmpty() || webView == null) {
            // Resumed via "Tap to open" — keep current page & audio playing uninterrupted
            return;
        }
        String currentUrl = webView.getUrl();
        if (currentUrl != null && !currentUrl.isEmpty()) {
            try {
                String curHost = Uri.parse(currentUrl).getHost();
                String newHost = Uri.parse(newUrl).getHost();
                if (curHost != null && newHost != null && curHost.equalsIgnoreCase(newHost)) {
                    // Already running this web app (e.g., music.youtube.com) — do not reload and kill playback!
                    return;
                }
            } catch (Exception ignored) {
            }
            if (currentUrl.equalsIgnoreCase(newUrl)) {
                return;
            }
        }
        sLastLoadedUrl = newUrl;
        String newTitle = sanitizeTitle(intent.getStringExtra(EXTRA_TITLE), newUrl);
        setTitle(newTitle);
        applyUserAgentForUrl(newUrl);
        webView.loadUrl(newUrl);
        updateNavState();
    }

    private String extractSafeHttpUrl(Intent intent) {
        if (intent == null) return null;
        String url = intent.getStringExtra(EXTRA_URL);
        if ((url == null || url.trim().isEmpty()) && intent.getData() != null) {
            url = intent.getDataString();
        }
        if (url == null || url.trim().isEmpty()) return null;
        try {
            String trimmed = url.trim();
            String scheme = Uri.parse(trimmed).getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return trimmed;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String sanitizeTitle(String rawTitle, String fallbackUrl) {
        String title = rawTitle;
        if (title != null) {
            title = title.replaceAll("[\\p{Cntrl}]", "").trim();
            if (title.length() > 80) title = title.substring(0, 80);
            if (title.isEmpty()) title = null;
        }
        if (title == null || title.isEmpty()) {
            try {
                title = Uri.parse(fallbackUrl).getHost();
                if (title != null && title.startsWith("www.")) title = title.substring(4);
            } catch (Exception e) {
                title = "Web App";
            }
        }
        return (title == null || title.isEmpty()) ? "Web App" : title;
    }

    private void applyTopBarVisibility(boolean showBar, boolean animate) {
        isTopBarVisible = showBar;
        ShortcutStore.setWebAppTopBarVisible(this, showBar);
        if (webNavBar != null) {
            GlassThemeHelper.applyToView(this, webNavBar, 0);
            if (showBar) {
                webNavBar.setVisibility(View.VISIBLE);
                if (animate) {
                    SpringAnimator.animateTranslationY(webNavBar, -dp(48), 0f, 0f, null);
                } else {
                    webNavBar.setTranslationY(0f);
                }
            } else {
                webNavBar.setVisibility(View.GONE);
            }
        }
        if (webTopHandle != null) {
            webTopHandle.setVisibility((showBar || isImmersiveFullscreen) ? View.GONE : View.VISIBLE);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void installCurrentWebAppAsStandalone() {
        if (webView == null) return;
        String curUrl = webView.getUrl();
        if (curUrl == null || curUrl.isEmpty()) curUrl = sLastLoadedUrl;
        if (curUrl == null || curUrl.isEmpty()) return;

        String label = getTitle() != null ? getTitle().toString().trim() : "";
        label = label.replaceAll("\\s*-\\s*YouTube Music$", "")
                .replaceAll("\\s*-\\s*YouTube$", "").trim();
        if (label.isEmpty() || label.length() > 28) {
            label = sanitizeTitle(null, curUrl);
        }

        Shortcut sc = new Shortcut(Shortcut.TYPE_URL, "", curUrl, label);
        Bitmap brand = WebLogoHelper.getBuiltinBrandLogo(this, curUrl);
        if (brand != null) {
            IconUtil.saveCustomIcon(this, sc.id, brand);
        } else {
            WebLogoHelper.fetchFaviconAsync(this, curUrl, sc.id, null);
        }

        // 1. Add to Lock Launcher active profile if not already present
        int prof = ShortcutStore.activeProfile(this);
        java.util.List<Shortcut> homeList = ShortcutStore.loadPadded(this, prof);
        boolean existsOnHome = false;
        for (Shortcut existing : homeList) {
            if (existing != null && Shortcut.TYPE_URL.equals(existing.type)
                    && curUrl.equalsIgnoreCase(existing.url)) {
                existsOnHome = true;
                break;
            }
        }
        if (!existsOnHome) {
            ShortcutStore.addOrPlaceShortcut(homeList, sc);
            ShortcutStore.save(this, prof, homeList);
        }

        // 2. Also add to Dynamic Island if room is available
        java.util.List<Shortcut> islandList = ShortcutStore.loadIsland(this);
        boolean existsInIsland = false;
        for (Shortcut existing : islandList) {
            if (existing != null && Shortcut.TYPE_URL.equals(existing.type)
                    && curUrl.equalsIgnoreCase(existing.url)) {
                existsInIsland = true;
                break;
            }
        }
        if (!existsInIsland && islandList.size() < ShortcutStore.MAX) {
            islandList.add(sc);
            ShortcutStore.saveIsland(this, islandList);
            IslandService.refresh(this);
        }

        // 3. Pin as a standalone Installed App icon on the system Home screen & switch to Installed Mode (hide bar)
        applyTopBarVisibility(false, true);
        boolean pinned = IconUtil.pinShortcutToHomeScreen(this, sc);
        Toast.makeText(this,
                pinned
                        ? "Installed " + label + " as standalone app (Top bar tucked)"
                        : "Installed " + label + " to Lock Launcher Home & Island",
                Toast.LENGTH_LONG).show();
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
                sLastLoadedUrl = curUrl;
                try {
                    Uri u = Uri.parse(curUrl);
                    String host = u.getHost();
                    if (host != null) {
                        if (host.startsWith("www.")) host = host.substring(4);
                        textDomain.setText(sleepTimerMinutes > 0 ? host + " · " + sleepTimerMinutes + "m" : host);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void showWebAppControlMenu() {
        if (webView == null) return;
        String curUrl = webView.getUrl() != null ? webView.getUrl() : sLastLoadedUrl;
        String[] options = new String[] {
                "📲 Install as Standalone App (Home & Island)",
                isTopBarVisible ? "▲ Hide Top Bar (Installed App Full View)" : "▾ Show Top Navigation Bar (Return / Forward / Reload)",
                "📺 Picture-in-Picture (PiP) Floating Window",
                audioOnlyOledMode ? "☀️ Disable Audio-Only OLED Saver" : "🌑 Enable Audio-Only OLED Saver",
                sleepTimerMinutes > 0 ? "⏰ Cancel Sleep Timer (" + sleepTimerMinutes + "m active)" : "⏰ Set Sleep Timer (15m / 30m / 60m)",
                forceDesktopUa ? "📱 Switch to Mobile User-Agent" : "💻 Switch to Desktop User-Agent",
                "🔗 Copy Current URL"
        };
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(getTitle() != null ? getTitle() : "Installed Web App")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        installCurrentWebAppAsStandalone();
                    } else if (which == 1) {
                        applyTopBarVisibility(!isTopBarVisible, true);
                    } else if (which == 2) {
                        enterWebAppPipMode();
                    } else if (which == 3) {
                        audioOnlyOledMode = !audioOnlyOledMode;
                        String js = "document.documentElement.classList.toggle('__ll-audio-only', " + audioOnlyOledMode + ");";
                        webView.evaluateJavascript(js, null);
                        Toast.makeText(this, audioOnlyOledMode ? "Audio-Only OLED mode enabled" : "Video display restored", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        if (sleepTimerMinutes > 0) {
                            handler.removeCallbacks(sleepTimerRunnable);
                            sleepTimerMinutes = 0;
                            updateNavState();
                            Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show();
                        } else {
                            showSleepTimerPicker();
                        }
                    } else if (which == 5) {
                        forceDesktopUa = !forceDesktopUa;
                        applyUserAgentForUrl(curUrl);
                        webView.reload();
                        Toast.makeText(this, forceDesktopUa ? "Desktop mode enabled" : "Standard mode enabled", Toast.LENGTH_SHORT).show();
                    } else if (which == 6) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", curUrl));
                            Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSleepTimerPicker() {
        final int[] mins = new int[] { 15, 30, 60 };
        String[] labels = new String[] { "15 minutes", "30 minutes", "60 minutes" };
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Sleep Timer")
                .setItems(labels, (d, which) -> {
                    sleepTimerMinutes = mins[which];
                    handler.removeCallbacks(sleepTimerRunnable);
                    handler.postDelayed(sleepTimerRunnable, sleepTimerMinutes * 60_000L);
                    updateNavState();
                    Toast.makeText(this, "Sleep timer set for " + sleepTimerMinutes + " minutes", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void enterWebAppPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(new android.util.Rational(16, 9))
                        .build();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                Toast.makeText(this, "PiP unavailable on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (webNavBar != null) {
            webNavBar.setVisibility(isInPictureInPictureMode ? View.GONE : (isTopBarVisible ? View.VISIBLE : View.GONE));
        }
        if (webTopHandle != null) {
            webTopHandle.setVisibility(isInPictureInPictureMode ? View.GONE : (isTopBarVisible ? View.GONE : View.VISIBLE));
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
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
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
        // If playing audio, move task to back instead of destroying so music continues playing!
        if (currentMediaPlaying) {
            moveTaskToBack(true);
            return;
        }
        finish();
    }

    private void applyUserAgentForUrl(String targetUrl) {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        if (forceDesktopUa || (targetUrl != null && targetUrl.toLowerCase().contains("music.youtube.com"))) {
            settings.setUserAgentString(DESKTOP_CHROME_UA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        } else {
            String defaultUa = settings.getUserAgentString();
            if (defaultUa != null && (defaultUa.contains("; wv") || defaultUa.contains("Version/"))) {
                String cleanUa = defaultUa.replace("; wv", "")
                        .replaceAll("Version\\/\\d+\\.\\d+\\s*", "");
                settings.setUserAgentString(cleanUa);
            }
        }
    }

    private void initPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        PLAYBACK_CHANNEL,
                        "Web App Playback",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Background audio playback notification");
                ch.setShowBadge(false);
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    /**
     * Initializes a real Android MediaSession so Android 12/13/14/15 & OxygenOS 15
     * display the native "Music Square" media player card when swiping down from the top-left!
     */
    private void initMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                mediaSession = new MediaSession(this, "LockLauncherWebMedia");
                mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                mediaSession.setCallback(new MediaSession.Callback() {
                    @Override
                    public void onPlay() {
                        if (!currentMediaPlaying) {
                            executeWebMediaCommand(NowPlayingHelper.CMD_TOGGLE);
                        }
                    }

                    @Override
                    public void onPause() {
                        if (currentMediaPlaying) {
                            executeWebMediaCommand(NowPlayingHelper.CMD_TOGGLE);
                        }
                    }

                    @Override
                    public void onSkipToNext() {
                        executeWebMediaCommand(NowPlayingHelper.CMD_NEXT);
                    }

                    @Override
                    public void onSkipToPrevious() {
                        executeWebMediaCommand(NowPlayingHelper.CMD_PREV);
                    }

                    @Override
                    public void onStop() {
                        if (currentMediaPlaying) {
                            executeWebMediaCommand(NowPlayingHelper.CMD_TOGGLE);
                        }
                    }
                });
                mediaSession.setActive(true);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncMediaSessionState(String displayTitle, String displayArtist, Bitmap artBmp, PendingIntent sessionPi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            try {
                if (sessionPi != null) {
                    mediaSession.setSessionActivity(sessionPi);
                }
                MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, displayTitle)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, displayArtist)
                        .putString(MediaMetadata.METADATA_KEY_ALBUM, "Lock Launcher · Ad-Free Stream");
                if (artBmp != null) {
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBmp);
                    metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artBmp);
                }
                mediaSession.setMetadata(metaBuilder.build());

                long actions = PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
                PlaybackState state = new PlaybackState.Builder()
                        .setActions(actions)
                        .setState(currentMediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                                PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                        .build();
                mediaSession.setPlaybackState(state);
                mediaSession.setActive(true);
            } catch (Exception ignored) {
            }
        }
    }

    private final class MediaBridge {
        @JavascriptInterface
        public void onMediaState(String title, String artist, boolean isPlaying) {
            String safeTitle = title != null ? title.trim() : "";
            String safeArtist = artist != null ? artist.trim() : "";
            if (safeTitle.isEmpty()) return;
            boolean changed = !safeTitle.equals(currentMediaTitle)
                    || !safeArtist.equals(currentMediaArtist)
                    || (isPlaying != currentMediaPlaying);
            currentMediaTitle = safeTitle;
            currentMediaArtist = safeArtist;
            currentMediaPlaying = isPlaying;
            if (changed) {
                handler.post(() -> {
                    String curUrl = webView != null ? webView.getUrl() : sLastLoadedUrl;
                    NowPlayingHelper.updateWebMedia(WebAppActivity.this, currentMediaTitle, currentMediaArtist, currentMediaPlaying, curUrl);
                    updatePlaybackNotification(true);
                    IslandService.refresh(WebAppActivity.this);
                });
            }
        }
    }

    private void executeWebMediaCommand(String cmd) {
        if (webView == null) return;
        String js;
        if (NowPlayingHelper.CMD_NEXT.equals(cmd)) {
            js = "(function(){"
               + "var btn = document.querySelector('.next-button, .ytp-next-button, [aria-label=\"Next\"], [aria-label=\"Next song\"], [title=\"Next\"]');"
               + "if(btn){btn.click();return;}"
               + "var v = document.querySelector('video,audio'); if(v && v.duration){v.currentTime = Math.min(v.duration, v.currentTime + 15);}"
               + "})();";
        } else if (NowPlayingHelper.CMD_PREV.equals(cmd)) {
            js = "(function(){"
               + "var btn = document.querySelector('.previous-button, .ytp-prev-button, [aria-label=\"Previous\"], [aria-label=\"Previous song\"], [title=\"Previous\"]');"
               + "if(btn){btn.click();return;}"
               + "var v = document.querySelector('video,audio'); if(v){v.currentTime = Math.max(0, v.currentTime - 15);}"
               + "})();";
        } else {
            js = "(function(){"
               + "var v = document.querySelector('video,audio');"
               + "if(v){if(v.paused){v.play();}else{v.pause();}return;}"
               + "var btn = document.querySelector('#play-pause-button, .play-pause-button, .ytp-play-button');"
               + "if(btn){btn.click();}"
               + "})();";
            currentMediaPlaying = !currentMediaPlaying;
            NowPlayingHelper.updateWebMedia(this, currentMediaTitle, currentMediaArtist, currentMediaPlaying, webView.getUrl());
            updatePlaybackNotification(true);
            IslandService.refresh(this);
        }
        webView.evaluateJavascript(js, null);
    }

    private void updatePlaybackNotification(boolean show) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (!show) {
                nm.cancel(PLAYBACK_NOTIF_ID);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                    try { mediaSession.setActive(false); } catch (Exception ignored) {}
                }
                return;
            }
            String activeUrl = (webView != null && webView.getUrl() != null) ? webView.getUrl() : sLastLoadedUrl;
            String displayTitle = (currentMediaTitle != null && !currentMediaTitle.isEmpty())
                    ? currentMediaTitle
                    : (getTitle() != null ? getTitle().toString() : "YT Music");
            String displayArtist = (currentMediaArtist != null && !currentMediaArtist.isEmpty())
                    ? currentMediaArtist + (currentMediaPlaying ? " · Playing" : " · Paused")
                    : "Playing in background · Tap to open";

            Intent resumeIntent = new Intent(this, WebAppActivity.class);
            resumeIntent.setAction("com.crtghoul.locklauncher.OPEN_WEB_APP");
            if (activeUrl != null && !activeUrl.isEmpty()) {
                resumeIntent.putExtra(EXTRA_URL, activeUrl);
                resumeIntent.setData(Uri.parse(activeUrl));
            }
            resumeIntent.putExtra(EXTRA_TITLE, displayTitle);
            resumeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Bitmap artBmp = WebLogoHelper.getBuiltinBrandLogo(this, activeUrl != null ? activeUrl : "https://music.youtube.com");
            if (artBmp == null) {
                artBmp = IconUtil.letterIcon(displayTitle, 0xFFFF0033);
            }
            syncMediaSessionState(displayTitle, displayArtist, artBmp, pi);

            Intent prevIntent = new Intent(NowPlayingHelper.ACTION_MEDIA_COMMAND).setPackage(getPackageName())
                    .putExtra(NowPlayingHelper.EXTRA_CMD, NowPlayingHelper.CMD_PREV);
            PendingIntent prevPi = PendingIntent.getBroadcast(this, 401, prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent toggleIntent = new Intent(NowPlayingHelper.ACTION_MEDIA_COMMAND).setPackage(getPackageName())
                    .putExtra(NowPlayingHelper.EXTRA_CMD, NowPlayingHelper.CMD_TOGGLE);
            PendingIntent togglePi = PendingIntent.getBroadcast(this, 402, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent nextIntent = new Intent(NowPlayingHelper.ACTION_MEDIA_COMMAND).setPackage(getPackageName())
                    .putExtra(NowPlayingHelper.EXTRA_CMD, NowPlayingHelper.CMD_NEXT);
            PendingIntent nextPi = PendingIntent.getBroadcast(this, 403, nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(this, PLAYBACK_CHANNEL);
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(currentMediaPlaying ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
             .setLargeIcon(artBmp)
             .setContentTitle(displayTitle)
             .setContentText(displayArtist)
             .setContentIntent(pi)
             .setCategory(Notification.CATEGORY_TRANSPORT)
             .setVisibility(Notification.VISIBILITY_PUBLIC)
             .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Prev", prevPi).build())
             .addAction(new Notification.Action.Builder(
                     currentMediaPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                     currentMediaPlaying ? "Pause" : "Play", togglePi).build())
             .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", nextPi).build())
             .setOngoing(currentMediaPlaying);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Notification.MediaStyle style = new Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2);
                if (mediaSession != null) {
                    style.setMediaSession(mediaSession.getSessionToken());
                }
                b.setStyle(style);
            }
            nm.notify(PLAYBACK_NOTIF_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    private static final String MEDIA_METADATA_BRIDGE_JS =
            "(function(){"
            + "if(window.__llMediaBridgeInstalled)return;"
            + "window.__llMediaBridgeInstalled=true;"
            + "function syncMedia(){"
            + "  try{"
            + "    if(!window.LLMediaBridge)return;"
            + "    var v=document.querySelector('video,audio');"
            + "    var playing=false;"
            + "    if(v && !v.paused && v.readyState>=2){playing=true;}"
            + "    var title='',artist='';"
            + "    if(navigator.mediaSession && navigator.mediaSession.metadata){"
            + "      title=navigator.mediaSession.metadata.title||'';"
            + "      artist=navigator.mediaSession.metadata.artist||'';"
            + "    }"
            + "    if(!title){"
            + "      var ytmT=document.querySelector('ytmusic-player-bar .title');"
            + "      var ytmA=document.querySelector('ytmusic-player-bar .byline');"
            + "      if(ytmT)title=(ytmT.textContent||'').trim();"
            + "      if(ytmA)artist=(ytmA.textContent||'').trim().split('•')[0].trim();"
            + "    }"
            + "    if(!title){"
            + "      var ytT=document.querySelector('h1.ytd-watch-metadata, .slim-video-information-title');"
            + "      var ytA=document.querySelector('#owner #channel-name a, .slim-owner-icon-and-title');"
            + "      if(ytT)title=(ytT.textContent||'').trim();"
            + "      if(ytA)artist=(ytA.textContent||'').trim();"
            + "    }"
            + "    if(!title && document.title){"
            + "      title=document.title.replace(/\\s*-\\s*YouTube( Music)?$/i,'').trim();"
            + "    }"
            + "    if(!artist && location.hostname){"
            + "      artist=location.hostname.replace(/^www\\./,'');"
            + "    }"
            + "    if(title && (playing || v)){"
            + "      window.LLMediaBridge.onMediaState(title,artist,playing);"
            + "    }"
            + "  }catch(e){}"
            + "}"
            + "setInterval(syncMedia,1500);"
            + "document.addEventListener('play',syncMedia,true);"
            + "document.addEventListener('pause',syncMedia,true);"
            + "document.addEventListener('loadedmetadata',syncMedia,true);"
            + "syncMedia();"
            + "})();";

    private void injectClientScripts(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(AdBlockEngine.BACKGROUND_PLAYBACK_JS, null);
        view.evaluateJavascript(AdBlockEngine.COSMETIC_AD_BLOCK_JS, null);
        view.evaluateJavascript(MEDIA_METADATA_BRIDGE_JS, null);
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
        updatePlaybackNotification(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Keep audio notification active and process alive when user switches to another app
        updatePlaybackNotification(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sActiveInstance = new WeakReference<>(this);
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
        handler.removeCallbacks(sleepTimerRunnable);
        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
        }
        abortPendingBlob();
        abandonMediaAudioFocus();
        NowPlayingHelper.clearWebMedia(this);
        updatePlaybackNotification(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {
            }
            mediaSession = null;
        }
        try {
            unregisterReceiver(mediaCommandReceiver);
        } catch (Exception ignored) {
        }
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