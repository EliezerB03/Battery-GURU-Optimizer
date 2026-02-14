package com.guru.batteryoptimizer;

import android.app.Activity;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.JavascriptInterface;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.List;
import com.topjohnwu.superuser.Shell;

public class GuruAPIs {
    private Activity activity;
    static {
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10));
    }
    public GuruAPIs(Activity activity) {
        this.activity = activity;
    }
    // Haptic Vibrator Helper
    private Vibrator getHapticVibrator(Context context) {
        VibratorManager vm = context.getSystemService(VibratorManager.class);
        return vm != null ? vm.getDefaultVibrator() : null;
    }
    // App Updater Helper
    private void downloadFile(String urlStr, File outFile) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();
        try (InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }
    // ============ BASIC APIs ============
    // = guru.openURL() =
    @JavascriptInterface
    public void openURL(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            activity.startActivity(intent);
        } catch (Exception ignored) {}
    }
    // = guru.closeApp() =
    @JavascriptInterface
    public void closeApp() {
        try {
            closeShell();
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Exception ignored) {}
    }
     // = guru.hideSplash() =
    @JavascriptInterface
    public void hideSplash() {
        activity.runOnUiThread(() -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).hideNativeSplash();
            }
        });
    }
    // = guru.getAppInfo()
    @JavascriptInterface
    public String getAppInfo() {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            JSONObject result = new JSONObject();
            result.put("success", true);
            long versionCode = pInfo.getLongVersionCode();
            result.put("versionCode", versionCode);
            result.put("versionName", pInfo.versionName);
            result.put("packageName", activity.getPackageName());
            result.put("updateUrl", "https://raw.githubusercontent.com/EliezerB03/Battery-GURU-Optimizer/master/App/updater.json");
            return result.toString();
        } catch (Exception e) {
            try {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", e.getMessage());
                return error.toString();
            } catch (Exception je) {
                return "{\"success\":false,\"error\":\"Unknown error\"}";
            }
        }
    }
    // ============== HAPTIC VIBRATIONS APIs ==============
    // = guru.hapticToggle()
    @JavascriptInterface
    public void hapticToggle() {
        activity.runOnUiThread(() -> {
            try {
                View rootView = activity.getWindow().getDecorView().getRootView();
                if (rootView != null) {
                    int hapticType;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        hapticType = HapticFeedbackConstants.TOGGLE_ON;
                    } else {
                        hapticType = HapticFeedbackConstants.CONFIRM;
                    }
                    rootView.performHapticFeedback(
                        hapticType, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    );
                }
            } catch (Exception ignored) {}
        });
    }
    // = guru.hapticSlider()
    @JavascriptInterface
    public void hapticSlider() {
        activity.runOnUiThread(() -> {
            try {
                View rootView = activity.getWindow().getDecorView().getRootView();
                if (rootView != null) {
                    rootView.performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    );
                }
            } catch (Exception ignored) {}
        });
    }
    // ============== ROOT APIs ==============
    // = guru.isRoot() =
    @JavascriptInterface
    public String isRoot() {
        try {
            closeShell();
            Shell shell = Shell.getShell();
            boolean hasRoot = shell.isRoot();
            JSONObject result = new JSONObject();
            result.put("hasRoot", hasRoot);
            result.put("success", true);
            return result.toString();
        } catch (Exception e) {
            try {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("hasRoot", false);
                error.put("error", e.getMessage());
                return error.toString();
            } catch (Exception je) {
                return "{\"success\":false,\"hasRoot\":false,\"error\":\"Unknown error\"}";
            }
        }
    }
    // = guru.getDeviceInfo() =
    @JavascriptInterface
    public String getDeviceInfo() {
        try {
            List<String> out = Shell.cmd("getprop ro.boot.em.model").exec().getOut();
            String model = (out != null && !out.isEmpty()) ? out.get(0).trim() : "";
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("model", model);
            result.put("raw", out); 
            return result.toString();
        } catch (Exception e) {
            try {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", e.getMessage());
                return error.toString();
            } catch (Exception je) {
                return "{\"success\":false,\"error\":\"Unknown error\"}";
            }
        }
    }
    // = guru.closeShell() =
    public void closeShell() {
        try {
            Shell shell = Shell.getCachedShell();
            if (shell != null && shell.isAlive()) {
                shell.close();
            }
        } catch (Exception e) {}
    }
    // = guru.installUpdate() =
    @JavascriptInterface
    public void installUpdate(String url) {
        new Thread(() -> {
            try {
                File apkFile = new File(activity.getCacheDir(), "update.apk");
                downloadFile(url, apkFile);
                Shell.Result result = Shell.cmd("pm install -r " + apkFile.getAbsolutePath()).exec();
                activity.runOnUiThread(() -> {
                    activity.finishAffinity();
                    System.exit(0);
                });
            } catch (Exception e) {}
        }).start();
    }
    // = guru.exec() =
    @JavascriptInterface
    public String exec(String command) {
        try {
            Shell.Result result = Shell.cmd(command).exec();
            if (!result.isSuccess()) {
                return "";
            }
            StringBuilder output = new StringBuilder();
            for (String line : result.getOut()) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(line);
            }
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }
}