package com.guru.batteryoptimizer;

import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.view.View;
import android.view.Window;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.splashscreen.SplashScreen;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private WebView webApp;
    private WebViewAssetLoader assetLoader;
    private WindowInsetsControllerCompat windowInsetsController;
    private boolean backgroundState = false;
    private boolean keepSplashScreen = true;
    private boolean isChangingConfig = false;
    private View splashScreenView = null;
    private Insets currentInsets = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            backgroundState = savedInstanceState.getBoolean("backgroundState", false);
            keepSplashScreen = false;
        }
        setupCustomSplash();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();
        webApp = findViewById(R.id.webview);
        setupAssetLoader();
        setupWebView();
        getSystemInsets();
        updateSystemBarsAppearance();
        if (savedInstanceState == null) {
            webApp.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        } else {
            webApp.restoreState(savedInstanceState);
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("backgroundState", backgroundState);
        if (webApp != null) {
            webApp.saveState(outState);
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        if (backgroundState && webApp != null) {
            webApp.onResume();
            webApp.resumeTimers();
            webApp.evaluateJavascript("typeof window.onAppForegrounded === 'function' && window.onAppForegrounded();", null);
            backgroundState = false;
        }
    }
    @Override
    public void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && webApp != null) {
            webApp.evaluateJavascript("typeof window.onAppBackgrounded === 'function' && window.onAppBackgrounded();", null);
            webApp.onPause();
            webApp.pauseTimers();
            backgroundState = true;
        }
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (webApp == null) return;
        if (hasFocus) {
            webApp.evaluateJavascript("typeof window.onAppWindowFocus === 'function' && window.onAppWindowFocus();", null);
        } else {
            webApp.evaluateJavascript("typeof window.onAppWindowBlur === 'function' && window.onAppWindowBlur();", null);
        }
    }
    @Override
    public void onDestroy() {
        if (webApp != null && !isChangingConfigurations()) {
            webApp.destroy();
        }
        super.onDestroy();
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSystemBarsAppearance();
    }
    private void setupCustomSplash() {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> keepSplashScreen);
        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(splashScreenView.getView(), View.ALPHA, 1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.setStartDelay(50);
            fadeOut.start();
            splashScreenView.getView().postDelayed(() -> {splashScreenView.remove(); }, 250);
        });
    }
    private void setupAssetLoader() {
        assetLoader = new WebViewAssetLoader.Builder().addPathHandler("/assets/", 
        new WebViewAssetLoader.AssetsPathHandler(this)).build();
    }
    private void setupWebView() {
        webApp.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webApp.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings webAppSettings = webApp.getSettings();
        webAppSettings.setDomStorageEnabled(true);
        webAppSettings.setJavaScriptEnabled(true);
        webAppSettings.setSupportZoom(false);
        webAppSettings.setDisplayZoomControls(false);
        webAppSettings.setBuiltInZoomControls(false);
        webAppSettings.setAllowFileAccess(false);
        webAppSettings.setAllowContentAccess(false);
        webAppSettings.setGeolocationEnabled(false);
        webAppSettings.setNeedInitialFocus(false);
        float systemFontScale = getResources().getConfiguration().fontScale;
        float maxAllowedScale = 1.43f;
        int zoom = (int)(Math.min(systemFontScale, maxAllowedScale) * 100);
        webAppSettings.setTextZoom(zoom);
        webApp.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (currentInsets != null) {
                    sendInsetsToWeb(currentInsets);
                }
            }
        });
        webApp.setWebChromeClient(new WebChromeClient());
        webApp.addJavascriptInterface(new GuruAPIs(this), "guru");
    }
    public void hideNativeSplash() {
        keepSplashScreen = false;
    }
    private void setupEdgeToEdge() {
        Window window = getWindow();
        View decorView = window.getDecorView();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        windowInsetsController = WindowCompat.getInsetsController(window, decorView);
        if (windowInsetsController != null) {
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        window.setNavigationBarContrastEnforced(false);
    }
    private void updateSystemBarsAppearance() {
        if (windowInsetsController == null) return;
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        windowInsetsController.setAppearanceLightStatusBars(!isDarkMode);
        windowInsetsController.setAppearanceLightNavigationBars(!isDarkMode);
    }
   private void sendInsetsToWeb(Insets insets) {
        if (webApp == null) return;
        float insetDensity = getResources().getDisplayMetrics().density;
        String js = String.format(Locale.US,
                "window.setSystemInsets(%.2f, %.2f, %.2f, %.2f);",
                insets.top / insetDensity, insets.bottom / insetDensity,
                insets.left / insetDensity, insets.right / insetDensity );
        webApp.evaluateJavascript(js, null);
    }
    private void getSystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(webApp, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            currentInsets = bars;
            sendInsetsToWeb(bars);
            return insets; 
        });
    }
}