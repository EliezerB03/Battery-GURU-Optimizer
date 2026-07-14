package com.guru.batteryoptimizer.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class GuruAPIs(private val context: Context) {
    // ======= Objet Variables =======
    companion object {
        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10)
            )
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentActivity: java.lang.ref.WeakReference<android.app.Activity>? = null
    private fun runOnUiThread(block: () -> Unit) = mainHandler.post(block)

    // ======= Download State =======
    var isDownloading: Boolean = false
        private set
    var downloadProgress: Int = 0
        private set
    var downloadCompletedWhileDetached: Boolean = false
    private val downloadCancelFlag = AtomicBoolean(false)
    private var onProgressCallback: ((Int) -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null
    fun reattachDownloadCallbacks(
        onProgress: ((Int) -> Unit)?,
        onComplete: () -> Unit,
        onCancel: () -> Unit,
        onError: () -> Unit
    ) {
        onProgressCallback = onProgress
        onCompleteCallback = onComplete
        onCancelCallback   = onCancel
        onErrorCallback    = onError
    }
    fun detachDownloadCallbacks() {
        onProgressCallback = null
        onCompleteCallback = null
        onCancelCallback   = null
        onErrorCallback    = null
    }
    fun cancelActiveDownload() {
        downloadCancelFlag.set(true)
    }

    // ======= Reset State =======
    var isResetting: Boolean = false
        private set
    
    // ======= Optimizing Apps State =======
    var isAppOptimizing: Boolean = false
        private set
    var onOptimizeProgress: ((Int) -> Unit)? = null
    var onOptimizeComplete: (() -> Unit)? = null
    var onOptimizeCancel: (() -> Unit)? = null
    private val optimizeCancelFlag = AtomicBoolean(false)
    fun cancelActiveOptimize() {
        optimizeCancelFlag.set(true)
    }

    // ======= Helpers =======
    private fun errorJson(e: Exception) = runCatching {
        JSONObject().apply {
            put("success", false)
            put("error", e.message)
        }.toString()
    }.getOrElse { """{"success":false,"error":"Unknown error"}""" }

    // ======= Download File Helper =======
    private fun downloadFile(
        urlStr: String,
        outFile: File,
        onProgress: ((Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout    = 10_000
            connect()
        }
        val totalBytes = conn.contentLengthLong
        var downloaded = 0L
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    if (isCancelled()) {
                        conn.disconnect()
                        throw CancellationException("Download cancelled")
                    }
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (totalBytes > 0) {
                        val percent = ((downloaded * 100) / totalBytes).toInt()
                        onProgress?.invoke(percent)
                    }
                }
            }
        }
        conn.disconnect()
    }
    private fun downloadFileWithRetry(
        urlStr: String,
        outFile: File,
        onProgress: ((Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ) {
        val retryDeadline   = System.currentTimeMillis() + 10_000L
        val retryIntervalMs = 500L
        var lastException: Exception? = null
        var attempted = false
        while (!attempted || System.currentTimeMillis() < retryDeadline) {
            if (isCancelled()) throw CancellationException("Download cancelled")
            try {
                downloadFile(urlStr, outFile, onProgress, isCancelled)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                attempted = true
                if (System.currentTimeMillis() < retryDeadline) {
                    Thread.sleep(retryIntervalMs)
                }
            }
        }
        throw lastException ?: Exception("Download failed")
    }

    // ============ APIs (ROOT) ============
    @Volatile var cancelRunningOperations: Boolean = false
    fun isRoot(): String = runCatching {
        val cached = Shell.getCachedShell()
        if (cached != null && cached.isAlive) {
            val hasRoot = runCatching {
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(5)
                    .build("su")
                    .use { it.isRoot }
            }.getOrElse { false }
            if (!hasRoot) closeShell()
            JSONObject().apply {
                put("success", true)
                put("hasRoot", hasRoot)
            }.toString()
        } else {
            closeShell()
            val hasRoot = Shell.getShell().isRoot
            JSONObject().apply {
                put("success", true)
                put("hasRoot", hasRoot)
            }.toString()
        }
    }.getOrElse { e ->
        runCatching {
            JSONObject().apply {
                put("success", false)
                put("hasRoot", false)
                put("error", (e as Exception).message)
            }.toString()
        }.getOrElse { """{"success":false,"hasRoot":false,"error":"Unknown error"}""" }
    }
    // getDeviceInfo
    fun getDeviceInfo(): String = runCatching {
        val model = Shell.cmd("getprop ro.boot.em.model").exec().out
            .firstOrNull()?.trim().orEmpty()
        JSONObject().apply {
            put("success", true)
            put("model",   model)
        }.toString()
    }.getOrElse { e -> errorJson(e as Exception) }
    // downloadUpdate
    fun downloadUpdate(
        url: String,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: () -> Unit,
        onCancel: () -> Unit,
        onError: () -> Unit
    ) {
        downloadCancelFlag.set(false)
        isDownloading = true
        downloadProgress = 0
        onProgressCallback = onProgress
        onCompleteCallback = onComplete
        onCancelCallback   = onCancel
        onErrorCallback    = onError
        val apkFile = File(context.cacheDir, "update.apk")
        Thread {
            runCatching {
                downloadFileWithRetry(url, apkFile, onProgress = { percent ->
                    downloadProgress = percent
                    runOnUiThread { onProgressCallback?.invoke(percent) }
                }, isCancelled = { downloadCancelFlag.get() || cancelRunningOperations })
                if (cancelRunningOperations) {
                    isDownloading = false
                    runCatching { apkFile.delete() }
                    return@runCatching
                }
                isDownloading = false
                if (onCompleteCallback != null) {
                    runOnUiThread { onCompleteCallback?.invoke() }
                } else {
                    downloadCompletedWhileDetached = true
                }
            }.onFailure { e ->
                isDownloading = false
                runCatching { apkFile.delete() }
                if (!cancelRunningOperations) runOnUiThread {
                    if (e is CancellationException) onCancelCallback?.invoke()
                    else onErrorCallback?.invoke()
                }
            }
        }.start()
    }
    // installCachedUpdate
    fun installCachedUpdate(onError: () -> Unit) {
        Thread {
            if (cancelRunningOperations) return@Thread
            runCatching {
                Thread.sleep(100)
                if (cancelRunningOperations) return@Thread
                Thread.sleep(1500)
                val apkFile = File(context.cacheDir, "update.apk")
                if (cancelRunningOperations) return@Thread
                if (!apkFile.exists()) throw Exception("APK not found in cache")
                Shell.cmd("pm install -r ${apkFile.absolutePath}").exec()
                runCatching { apkFile.delete() }
                runOnUiThread {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
            }.onFailure {
                runOnUiThread { onError() }
            }
        }.start()
    }
    // resetToDefault
    fun resetToDefault() {
        if (isResetting) return
        isResetting = true
        Thread {
            if (cancelRunningOperations) { isResetting = false; return@Thread }
            runCatching {
                if (cancelRunningOperations) { isResetting = false; return@Thread }
                val pkg  = "com.guru.batteryoptimizer"
                val base = "/data/data/$pkg"
                val destPath = "$base/guru.sh"
                val escaped = GuruFunctions.Scripts.GURU_SCRIPT.replace("'", "'\\''")
                Thread.sleep(1600)
                if (cancelRunningOperations) { isResetting = false; return@Thread }
                exec("printf '%s' '$escaped' > $destPath")
                exec("chmod 755 $destPath")
                exec("sh $destPath")
                exec("find $base -mindepth 1 -maxdepth 1 -exec rm -rf {} +")
                Thread.sleep(100)
                runOnUiThread { closeApp() }
            }
        }.start()
    }
    // optimizeAllApps
    fun optimizeAllApps() {
        if (isAppOptimizing) return
        isAppOptimizing = true
        optimizeCancelFlag.set(false)
        Thread {
            if (cancelRunningOperations || optimizeCancelFlag.get()) { isAppOptimizing = false; return@Thread }
            runCatching {
                if (cancelRunningOperations || optimizeCancelFlag.get()) { isAppOptimizing = false; return@Thread }
                Thread.sleep(500)
                if (cancelRunningOperations || optimizeCancelFlag.get()) {
                    isAppOptimizing = false
                    runOnUiThread { onOptimizeCancel?.invoke() }
                    return@Thread
                }
                val packages = exec("pm list packages | cut -d: -f2").lines().map {it.trim()}.filter {it.isNotEmpty()}
                val total = packages.size
                var done = 0
                for (pkg in packages) {
                    if (cancelRunningOperations || optimizeCancelFlag.get()) {
                        isAppOptimizing = false
                        runOnUiThread { onOptimizeCancel?.invoke() }
                        return@Thread
                    }
                    exec("cmd package compile -m speed-profile \"$pkg\" > /dev/null 2>&1")
                    done++
                    val percent = if (total > 0) ((done * 100) / total) else 0
                    runOnUiThread { onOptimizeProgress?.invoke(percent) }
                }
                Thread.sleep(100)
                isAppOptimizing = false
                runOnUiThread { onOptimizeComplete?.invoke() }
            }
        }.start()
    }
    // execSafe (exec function)
    private val execScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingJobs = mutableMapOf<String, Deferred<String>>()
    private val execMutex = Mutex()
    suspend fun execSafe(key: String, command: String): String {
        val deferred = execMutex.withLock {
            pendingJobs[key]?.cancel()
            val d = execScope.async { runCatching { exec(command) }.getOrElse { "" } }
            pendingJobs[key] = d; d
        }
        return try {
            deferred.await()
        } catch (_: CancellationException) {
            ""
        } finally {
            execMutex.withLock { if (pendingJobs[key] === deferred) pendingJobs.remove(key) }
        }
    }
    // exec (core function)
    fun exec(command: String): String = runCatching {
        val result = Shell.cmd(command).exec()
        if (!result.isSuccess) return ""
        result.out.joinToString("\n")
    }.getOrElse { "" }
    
    // ============ APIs (NON ROOT) ============
    // closeApp
    fun closeApp() {
        runCatching {
            execScope.cancel()
            closeShell()
        }
        Handler(Looper.getMainLooper()).post {
            currentActivity?.get()?.finish()
            Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 250)
        }
    }
    fun attachActivity(activity: android.app.Activity) {currentActivity = java.lang.ref.WeakReference(activity)}
    // restartApp
    fun restartApp() {
        Handler(Looper.getMainLooper()).post {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return@post
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
    // closeShell
    fun closeShell() { runCatching { Shell.getCachedShell()?.takeIf { it.isAlive }?.close() } }
    // getAppInfo
    fun getAppInfo(): String = runCatching {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        JSONObject().apply {
            put("success",     true)
            put("versionCode", pInfo.longVersionCode)
            put("versionName", pInfo.versionName)
            put("packageName", context.packageName)
            put("updateUrl",   "https://raw.githubusercontent.com/EliezerB03/Battery-GURU-Optimizer/master/App/updater.json")
        }.toString()
    }.getOrElse { e -> errorJson(e as Exception) }
}