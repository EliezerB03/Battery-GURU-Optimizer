package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SeslProgressBar
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdaterHelper(private val activity: AppCompatActivity, private val guru: GuruAPIs) {
    private companion object {
        val DETACH_RESET_PHASES = setOf(
            AppState.UpdaterPhase.UPDATE_AVAILABLE, AppState.UpdaterPhase.DOWNLOAD_CANCELED, AppState.UpdaterPhase.INSTALL_UPDATE,
            AppState.UpdaterPhase.PENDING_INSTALL_UPDATE, AppState.UpdaterPhase.DOWNLOAD_ERROR, AppState.UpdaterPhase.INSTALL_ERROR,
            AppState.UpdaterPhase.WHATSNEW
        )
    }
    private val context: Context get() = activity
    private val appState get() = (activity as MainActivity).appState
    private val main: MainActivity? get() = activity as? MainActivity
    private val scope get() = activity.lifecycleScope
    private val prefs: SharedPreferences = activity.getSharedPreferences("guru_updater", Context.MODE_PRIVATE)
    private var currentDialog: AlertDialog? = null
    private var currentDialogScrollView: NestedScrollView? = null
    var downloadCompletedWhileDetached: Boolean = false
    var onWidgetStateChanged: ((AppState.UpdaterWidgetState) -> Unit)? = null
    var onWidgetStateChangedGlobal: ((AppState.UpdaterWidgetState) -> Unit)? = null
    var onCheckingStateChanged: ((Boolean) -> Unit)? = null

    // ======= APK Cache Helpers =======
    private fun getCachedApkVersionCode(): Long = prefs.getLong("cached_apk_version_code", -1L)
    private fun setCachedApkVersionCode(versionCode: Long) = prefs.edit().putLong("cached_apk_version_code", versionCode).apply()
    private fun clearCachedApk() {
        prefs.edit().remove("cached_apk_version_code").apply()
        runCatching { File(activity.cacheDir, "update.apk").delete() }
    }
    private fun hasCachedApk(): Boolean {
        if (getCachedApkVersionCode() == -1L) return false
        return File(activity.cacheDir, "update.apk").exists()
    }
    private fun hasCachedApkForVersion(versionCode: Long): Boolean {
        if (getCachedApkVersionCode() != versionCode) return false
        return File(activity.cacheDir, "update.apk").exists()
    }

    // ======= Widget State Helper =======
    private fun setWidgetState(state: AppState.UpdaterWidgetState) {
        appState.updaterWidgetState = state
        onWidgetStateChanged?.invoke(state)
        onWidgetStateChangedGlobal?.invoke(state)
    }

    // ======= Detach / Destroy =======
    fun detach() {
        saveDialogScroll()
        guru.detachDownloadCallbacks()
        appState.onUpdateCheckResult = null
        if (currentDialog?.isShowing != true && appState.updaterPhase in DETACH_RESET_PHASES) clearDialogState()
        guru.downloadCompletedWhileDetached = false
        currentDialog = null
        currentDialogScrollView = null
    }
    fun destroy() {
        currentDialog?.window?.setWindowAnimations(0)
        currentDialog?.dismiss()
        currentDialog = null
        currentDialogScrollView = null
        appState.onUpdateCheckResult = null
        appState.isCheckingUpdate = false
        appState.pendingUpdateCheckResult = null
        guru.detachDownloadCallbacks()
    }
    private fun saveDialogScroll() { appState.updaterDialogScrollY = currentDialogScrollView?.scrollY ?: 0 }
    fun dismissActiveDialog(onDismissed: (() -> Unit)? = null): Boolean {
        val wasShowing = currentDialog?.isShowing == true
        if (wasShowing && onDismissed != null) {
            currentDialog?.setOnDismissListener {
                clearDialogState()
                onDismissed()
            }
            currentDialog?.dismiss()
        } else {
            currentDialog?.dismiss()
            clearDialogState()
        }
        currentDialog = null
        currentDialogScrollView = null
        return wasShowing
    }
    private fun clearDialogState() {
        appState.updaterPhase = AppState.UpdaterPhase.NONE
        appState.updaterDialogScrollY = 0
    }

    // ======= Checking State Helper =======
    private fun finishChecking() {
        scope.launch {
            delay(200)
            onCheckingStateChanged?.invoke(false)
        }
    }

    // ======= Recreation Re-attach =======
    fun reattach() {
        val savedScrollY = appState.updaterDialogScrollY
        when (appState.updaterPhase) {
            AppState.UpdaterPhase.UPDATE_AVAILABLE -> showUpdateAvailableDialog(
                remoteVersionName  = appState.updaterRemoteVersionName,
                currentVersionName = appState.updaterCurrentVersionName,
                downloadUrl        = appState.updaterDownloadUrl,
                changelog          = appState.updaterChangelog,
                restoreScrollY     = savedScrollY,
                isRestore = true
            )
            AppState.UpdaterPhase.DOWNLOADING -> {
                if (guru.isDownloading) {
                    reattachDownloadingDialog()
                } else if (guru.downloadCompletedWhileDetached) {
                    guru.downloadCompletedWhileDetached = false
                    setCachedApkVersionCode(appState.updaterRemoteVersionCode)
                    showInstallDialog()
                } else if (hasCachedApk()) { showInstallDialog()}
                else {
                    val downloadUrl = appState.updaterDownloadUrl
                    if (!downloadUrl.isNullOrBlank()) {showDownloadingDialog(downloadUrl, appState.updaterRemoteVersionCode, restoreScrollY = savedScrollY, isRestore = true)}
                    else { showDownloadErrorDialog(restoreScrollY = savedScrollY, isRestore = true) }
                }
            }
            AppState.UpdaterPhase.DOWNLOAD_CANCELED        -> showDownloadCanceledDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.INSTALL_UPDATE           -> showInstallDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.PENDING_INSTALL_UPDATE   -> showPendingInstallDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.INSTALLING               -> showInstallingDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.DOWNLOAD_ERROR           -> showDownloadErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.INSTALL_ERROR            -> showInstallErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
            AppState.UpdaterPhase.WHATSNEW                 -> showWhatsNewDialog(restoreScrollY = savedScrollY, isRestore = true, onDismiss = { main?.isBlocked = false })
            AppState.UpdaterPhase.NONE -> {
                val pending = appState.pendingUpdateCheckResult
                if (pending != null) {
                    appState.pendingUpdateCheckResult = null
                    handleCheckResult(pending)
                } else if (appState.isCheckingUpdate) { appState.onUpdateCheckResult = { result -> handleCheckResult(result) }}
            }
            else -> {}
        }
    }

    // ======= Main Checks =======
    fun checkForUpdates() {
        if (main?.checks?.lostRootDetected == true) return
        if (appState.isCheckingUpdate) return
        if (hasCachedApk()) {
            setWidgetState(AppState.UpdaterWidgetState.INSTALL_UPDATE)
            main?.isBlocked = true
            showInstallDialog()
            return
        }
        if (appState.updaterWidgetState == AppState.UpdaterWidgetState.UPDATE_AVAILABLE) {
            main?.isBlocked = true
            showUpdateAvailableDialog(
                remoteVersionName  = appState.updaterRemoteVersionName,
                currentVersionName = appState.updaterCurrentVersionName,
                downloadUrl        = appState.updaterDownloadUrl,
                changelog          = appState.updaterChangelog
            )
            return
        }
        runCheckFlow()
    }
    suspend fun checkWhatsNew(onDismiss: (() -> Unit)? = null): Boolean {
        val currentVersion = withContext(Dispatchers.IO) {
            runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode }.getOrElse { -1L }
        }
        val lastShownVersion = prefs.getLong("whatsnew_last_version", -1L)
        if (currentVersion <= 0 || currentVersion == lastShownVersion) return false
        prefs.edit().putLong("whatsnew_last_version", currentVersion).apply()
        withContext(Dispatchers.Main) {
            showWhatsNewDialog(onDismiss = {
                main?.isBlocked = false
                onDismiss?.invoke()
                appState.persistentScope.launch { checkForUpdatesPostLoading() }
            })
        }
        return true
    }
    suspend fun checkForUpdatesPostLoading() {
        if (appState.isCheckingUpdate) return
        if (hasCachedApk()) {
            withContext(Dispatchers.Main) {
                setWidgetState(AppState.UpdaterWidgetState.INSTALL_UPDATE)
                main?.isBlocked = true
                showPendingInstallDialog()
            }
            return
        }
        withContext(Dispatchers.Main) {
            main?.isBlocked = true
            runCheckFlow(silent = true)
        }
    }

    // ======= Main Update Flow =======
    private fun runCheckFlow(silent: Boolean = false) {
        if (main?.checks?.lostRootDetected == true) return
        if (appState.isCheckingUpdate) return
        appState.isCheckingUpdate = true
        val appInfo = parseAppInfo()
        if (appInfo == null) {
            appState.isCheckingUpdate = false
            setWidgetState(AppState.UpdaterWidgetState.CONNECTION_ERROR)
            if (silent) endSilentCheck()
            return
        }
        if (!silent) {
            setWidgetState(AppState.UpdaterWidgetState.CHECKING_UPDATE)
            onCheckingStateChanged?.invoke(true)
        }
        appState.onUpdateCheckResult = { result -> handleCheckResult(result) }
        val startTime = System.currentTimeMillis()
        appState.persistentScope.launch {
            val fetchResult = withContext(Dispatchers.IO) {fetchUpdateJsonWithRetry(appInfo.updateUrl, retry = !silent, startTime = startTime)}
            if (!silent) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = 1100L - elapsed
                if (remaining > 0) delay(remaining)
            }
            appState.isCheckingUpdate = false
            withContext(Dispatchers.Main) {
                val pendingResult = AppState.PendingUpdateCheckResult(
                    json           = fetchResult.getOrNull(),
                    appVersionCode = appInfo.versionCode,
                    appVersionName = appInfo.versionName,
                    appUpdateUrl   = appInfo.updateUrl,
                    silent         = silent
                )
                val handler = appState.onUpdateCheckResult
                if (handler != null) {
                    appState.onUpdateCheckResult = null
                    handler(pendingResult)
                } else { appState.pendingUpdateCheckResult = pendingResult }
            }
        }
    }
    private suspend fun fetchUpdateJsonWithRetry(updateUrl: String, retry: Boolean, startTime: Long): Result<JSONObject> {
        val retryDelayMs = 1_250L
        val deadline = startTime + 10_000L
        while (true) {
            val left = deadline - System.currentTimeMillis()
            val timeoutMs = if (retry) left.coerceIn(300L, 10_000L).toInt() else 10_000
            val result = runCatching { fetchUpdateJson(updateUrl, timeoutMs) }
            if (result.isSuccess || !retry) return result
            if (result.exceptionOrNull() !is IOException) return result
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return result
            if (remaining < retryDelayMs) {
                delay(remaining)
                return result
            }
            delay(retryDelayMs)
        }
    }

    private fun fetchUpdateJson(updateUrl: String, timeoutMs: Int): JSONObject {
        val urlStr = "$updateUrl?_=${System.currentTimeMillis()}"
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = timeoutMs
            readTimeout    = timeoutMs
            useCaches      = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw Exception("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            if (!json.has("versionCode") || !json.has("versionName"))
                throw Exception("Invalid JSON format")
            return json
        } finally { conn.disconnect() }
    }

    // ======= Check Result Handler =======
    private fun endSilentCheck() {
        appState.updaterPhase = AppState.UpdaterPhase.NONE
        main?.isBlocked = false
    }
    private fun handleCheckResult(result: AppState.PendingUpdateCheckResult) {
        val json           = result.json
        val appVersionCode = result.appVersionCode
        val appVersionName = result.appVersionName
        val silent         = result.silent
        if (json == null) {
            setWidgetState(AppState.UpdaterWidgetState.CONNECTION_ERROR)
            if (!silent) finishChecking() else endSilentCheck()
            return
        }
        val remoteVersion     = json.getLong("versionCode")
        val remoteVersionName = json.getString("versionName")
        val downloadUrl       = json.optString("downloadUrl", "")
        val changelog = json.opt("changelog")?.let { obj ->
            if (obj is JSONArray) (0 until obj.length()).joinToString("\n") { obj.getString(it) } else obj.toString()
        } ?: ""
        if (remoteVersion <= appVersionCode) {
            if (getCachedApkVersionCode() != -1L) clearCachedApk()
            setWidgetState(AppState.UpdaterWidgetState.UP_TO_DATE)
            if (!silent) finishChecking() else endSilentCheck()
            return
        }
        appState.updaterRemoteVersionCode = remoteVersion
        if (hasCachedApkForVersion(remoteVersion)) {
            setWidgetState(AppState.UpdaterWidgetState.INSTALL_UPDATE)
            if (!silent) finishChecking()
            showInstallDialog()
        } else {
            if (getCachedApkVersionCode() != -1L) clearCachedApk()
            appState.updaterRemoteVersionName  = remoteVersionName
            appState.updaterCurrentVersionName = appVersionName
            appState.updaterDownloadUrl        = downloadUrl
            appState.updaterChangelog          = changelog
            setWidgetState(AppState.UpdaterWidgetState.UPDATE_AVAILABLE)
            if (!silent) finishChecking() else showUpdateAvailableDialog(remoteVersionName, appVersionName, downloadUrl, changelog)
        }
    }

    // ======= Install Flow =======
    private fun startDownload(downloadUrl: String, remoteVersionCode: Long, availableDialog: AlertDialog) {
        main?.isBlocked = true
        availableDialog.dismiss()
        showDownloadingDialog(downloadUrl, remoteVersionCode)
    }
    private fun startInstall(installDialog: AlertDialog) {
        main?.isBlocked = true
        installDialog.dismiss()
        showInstallingDialog()
        prefs.edit().remove("cached_apk_version_code").apply()
        guru.installCachedUpdate {
            clearCachedApk()
            currentDialog?.dismiss()
            showInstallErrorDialog()
        }
    }

    // ======= Dialog Builder =======
    private class DialogParts(val builder: AlertDialog.Builder, val titleView: View, val messageView: View, val scrollView: NestedScrollView?)
    private fun inflateDialogView(builder: AlertDialog.Builder, layout: Int): View =
        activity.layoutInflater.cloneInContext(builder.context).inflate(layout, null)
    private fun prepareDialog(@StringRes title: Int, @DrawableRes icon: Int, message: String, messageLayout: Int = R.xml.dialog_message): DialogParts {
        val builder     = AlertDialog.Builder(context)
        val titleView   = inflateDialogView(builder, R.xml.dialog_icon_title)
        val titleText   = titleView.findViewById<AppCompatTextView>(R.id.dialog_title).apply { text = context.getString(title) }
        val messageView = inflateDialogView(builder, messageLayout)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = message
        applyDialogIcon(titleText, icon)
        return DialogParts(builder, titleView, messageView, setupScrollIndicators(messageView))
    }
    private fun styleDialog(d: AlertDialog) {
        d.seslSetBackgroundBlurEnabled(true)
        d.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
    }
    private fun showAndAttach(d: AlertDialog, isRestore: Boolean) {
        d.show()
        if (isRestore) d.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        main?.checks?.attachFocusCheck(d)
    }
    private fun presentDialog(d: AlertDialog, scrollView: NestedScrollView?, restoreScrollY: Int, isRestore: Boolean) {
        styleDialog(d)
        showAndAttach(d, isRestore)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = d
        currentDialogScrollView = scrollView
    }
    private fun showNotice(
        phase: AppState.UpdaterPhase, @StringRes title: Int, message: String, @DrawableRes icon: Int,
        restoreScrollY: Int, isRestore: Boolean, onClose: (() -> Unit)? = null
    ) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterPhase = phase
        val p = prepareDialog(title, icon, message)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { dlg, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                dlg.dismiss()
                onClose?.invoke()
            }
            .create()
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }
    private fun showActionDialog(
        phase: AppState.UpdaterPhase, @StringRes title: Int, message: String, @StringRes negative: Int, @StringRes positive: Int,
        restoreScrollY: Int, isRestore: Boolean, onPositive: (AlertDialog) -> Unit
    ) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterPhase = phase
        val p = prepareDialog(title, R.drawable.ic_dialog_update, message)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setNegativeButton(context.getString(negative)) { dlg, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                dlg.dismiss()
            }
            .setPositiveButton(context.getString(positive), null)
            .create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { onPositive(d) } }
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }

    // Update Available Dialog
    private fun showUpdateAvailableDialog(remoteVersionName: String, currentVersionName: String, downloadUrl: String, changelog: String, restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterRemoteVersionName  = remoteVersionName
        appState.updaterCurrentVersionName = currentVersionName
        appState.updaterDownloadUrl        = downloadUrl
        appState.updaterChangelog          = changelog
        val changelogLine = if (changelog.isNotBlank()) context.getString(R.string.updater_updateavailablechangelog, changelog) else ""
        showActionDialog(
            AppState.UpdaterPhase.UPDATE_AVAILABLE, R.string.updater_updateavailabletitle,
            context.getString(R.string.updater_updateavailablemsg, remoteVersionName, currentVersionName, changelogLine),
            R.string.updater_btn_notnow, R.string.updater_btn_updatenow, restoreScrollY, isRestore
        ) { dlg -> startDownload(downloadUrl, appState.updaterRemoteVersionCode, dlg) }
    }
    // Install Update Dialog
    private fun showInstallDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showActionDialog(
        AppState.UpdaterPhase.INSTALL_UPDATE, R.string.updater_installupdatetitle, context.getString(R.string.updater_installupdatemsg),
        R.string.updater_btn_later, R.string.updater_btn_installnow, restoreScrollY, isRestore
    ) { dlg -> startInstall(dlg) }
    // Pending Install Dialog
    private fun showPendingInstallDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showActionDialog(
        AppState.UpdaterPhase.PENDING_INSTALL_UPDATE, R.string.updater_installupdatetitle, context.getString(R.string.updater_pendinginstallupdatemsg),
        R.string.updater_btn_later, R.string.updater_btn_installnow, restoreScrollY, isRestore
    ) { dlg -> startInstall(dlg) }
    // Downloading Dialog
    private class DownloadHandlers(val onProgress: (Int) -> Unit, val onComplete: () -> Unit, val onCancel: () -> Unit, val onError: () -> Unit)
    private fun downloadHandlers(dialog: AlertDialog, messageText: AppCompatTextView, progressBar: SeslProgressBar, remoteVersionCode: () -> Long) = DownloadHandlers(
        onProgress = { percent ->
            messageText.text = context.getString(R.string.dialog_progress) + " ${percent.fmt()}%"
            progressBar.progress = percent
        },
        onComplete = {
            setCachedApkVersionCode(remoteVersionCode())
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
            scope.launch {
                delay(500)
                dialog.dismiss()
                setWidgetState(AppState.UpdaterWidgetState.INSTALL_UPDATE)
                showInstallDialog()
            }
        },
        onCancel = {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
            scope.launch {
                delay(500)
                dialog.dismiss()
                showDownloadCanceledDialog()
            }
        },
        onError = {
            dialog.dismiss()
            showDownloadErrorDialog()
        }
    )
    private fun buildDownloadingDialog(initialProgress: Int, restoreScrollY: Int = 0): Triple<AlertDialog, AppCompatTextView, SeslProgressBar> {
        val p = prepareDialog(
            R.string.updater_downloadingtitle, R.drawable.ic_dialog_download,
            context.getString(R.string.dialog_progress), R.xml.dialog_progress_message
        )
        val messageText = p.messageView.findViewById<AppCompatTextView>(R.id.dialog_message)
        val progressBar = p.messageView.findViewById<SeslProgressBar>(R.id.progressbar)
        applyProgressMode(progressBar)
        progressBar.progress = initialProgress
        val scrollView = p.scrollView
        val dialog = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setCancelable(false)
            .setNegativeButton(context.getString(R.string.dialog_btn_cancel), null)
            .create()
        styleDialog(dialog)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
        return Triple(dialog, messageText, progressBar)
    }
    private fun showDownloadingDialog(downloadUrl: String, remoteVersionCode: Long, restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOADING
        val (dialog, messageText, progressBar) = buildDownloadingDialog(initialProgress = 0, restoreScrollY = restoreScrollY)
        dialog.setOnShowListener {
            progressBar.isIndeterminate = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                isEnabled = false
                setOnClickListener { guru.cancelActiveDownload() }
            }
        }
        showAndAttach(dialog, isRestore)
        scope.launch {
            delay(500)
            progressBar.isIndeterminate = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            val handlers = downloadHandlers(dialog, messageText, progressBar) { remoteVersionCode }
            guru.downloadUpdate(
                url = downloadUrl,
                onProgress = handlers.onProgress, onComplete = handlers.onComplete,
                onCancel = handlers.onCancel, onError = handlers.onError
            )
        }
    }
    private fun reattachDownloadingDialog(isRestore: Boolean = false) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOADING
        val currentProgress = guru.downloadProgress
        val (dialog, messageText, progressBar) = buildDownloadingDialog(initialProgress = currentProgress)
        if (currentProgress > 0) {
            messageText.text = context.getString(R.string.dialog_progress) + " ${currentProgress.fmt()}%"
        }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { guru.cancelActiveDownload() } }
        showAndAttach(dialog, isRestore)
        val handlers = downloadHandlers(dialog, messageText, progressBar) { appState.updaterRemoteVersionCode }
        guru.reattachDownloadCallbacks(handlers.onProgress, handlers.onComplete, handlers.onCancel, handlers.onError)
    }

    // Download Canceled Dialog
    private fun showDownloadCanceledDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.UpdaterPhase.DOWNLOAD_CANCELED, R.string.updater_downloadcanceledtitle, context.getString(R.string.updater_downloadcanceledmsg),
        R.drawable.ic_dialog_warning, restoreScrollY, isRestore
    )
    // Download Error Dialog
    private fun showDownloadErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.UpdaterPhase.DOWNLOAD_ERROR, R.string.updater_downloaderrortitle, context.getString(R.string.updater_downloaderrormsg),
        R.drawable.ic_dialog_warning, restoreScrollY, isRestore
    )
    // Install Error Dialogs
    private fun showInstallErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.UpdaterPhase.INSTALL_ERROR, R.string.updater_installerrortitle, context.getString(R.string.updater_installerrormsg),
        R.drawable.ic_dialog_warning, restoreScrollY, isRestore
    )
    // Whats New Dialog
    private fun showWhatsNewDialog(restoreScrollY: Int = 0, onDismiss: (() -> Unit)? = null, isRestore: Boolean = false) = showNotice(
        AppState.UpdaterPhase.WHATSNEW, R.string.updater_whatsnewtitle,
        context.getString(R.string.updater_whatsnewhighlighted) + "\n" + """
                ⦁ UI theme updated to One UI 9 design
                ⦁ Added Bulgarian language
                ⦁ Added 'Wakelock blocker optimizations' option
                ⦁ Revamped 'About GURU' section
                ⦁ Refactored 'Check for app update' option
                ⦁ And more to discover!
            """.trimIndent() + "\n\n" + context.getString(R.string.updater_whatsnewfooter), R.drawable.ic_dialog_changelog, restoreScrollY, isRestore, onClose = onDismiss
    )
    // Installing Dialog
    private fun showInstallingDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.INSTALLING
        val builder     = AlertDialog.Builder(context)
        val titleView   = inflateDialogView(builder, R.xml.dialog_loading_title)
        titleView.findViewById<AppCompatTextView>(R.id.dialog_title).text = context.getString(R.string.updater_installingtitle)
        applyDotsProgress(titleView.findViewById(R.id.dialog_loading_progress))
        val messageView = inflateDialogView(builder, R.xml.dialog_message)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = context.getString(R.string.updater_installingmsg)
        val scrollView  = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        presentDialog(dialog, scrollView, restoreScrollY, isRestore)
    }

    // ======= Helpers =======
    private fun isMonetActive(): Boolean = !android.provider.Settings.Secure.getString(context.contentResolver, "theme_customization_overlay_packages").isNullOrEmpty()
    private fun applyProgressMode(bar: SeslProgressBar) {bar.setMode(if (isMonetActive()) 0 else SeslProgressBar.MODE_GRADIENT_HORIZONTAL)}
    private fun applyDotsProgress(bar: SeslProgressBar?) {if (isMonetActive()) bar?.indeterminateDrawable = ContextCompat.getDrawable(context, R.drawable.progress_dots_monet)}
    private fun parseAppInfo(): AppInfo? = runCatching {
        val json = JSONObject(guru.getAppInfo())
        if (!json.optBoolean("success")) return null
        AppInfo(
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            updateUrl   = json.getString("updateUrl")
        )
    }.getOrNull()
    private fun applyDialogIcon(titleText: AppCompatTextView, drawableRes: Int) {
        ContextCompat.getDrawable(context, drawableRes)?.let { drawable ->
            val fm = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size = (baseSize * 1.2f).toInt()
            val offsetV = ((size - baseSize) / 2)
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            drawable.setTint(titleText.currentTextColor)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        }
    }

    // ======= Scroll Indicators =======
    private fun setupScrollIndicators(messageView: View): NestedScrollView? {
        val scrollView   = messageView.findViewById<NestedScrollView>(R.id.dialog_scroll) ?: return null
        val indicatorTop = messageView.findViewById<View>(R.id.scrollIndicatorUp)
        val indicatorBot = messageView.findViewById<View>(R.id.scrollIndicatorDown)
        fun update() {
            indicatorTop.visibility = if (scrollView.canScrollVertically(-1)) View.VISIBLE else View.GONE
            indicatorBot.visibility = if (scrollView.canScrollVertically(1))  View.VISIBLE else View.GONE
        }
        scrollView.post { update() }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> update() })
        return scrollView
    }

    // ======= Data Variables =======
    private data class AppInfo(
        val versionCode: Long,
        val versionName: String,
        val updateUrl: String
    )
}