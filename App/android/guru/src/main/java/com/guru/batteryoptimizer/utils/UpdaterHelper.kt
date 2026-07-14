package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslProgressBar
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdaterHelper(private val activity: AppCompatActivity, private val guru: GuruAPIs) {
    var downloadCompletedWhileDetached: Boolean = false
    private val context: Context get() = activity
    private val appState get() = (activity as MainActivity).appState
    private val prefs: SharedPreferences = activity.getSharedPreferences("guru_updater", Context.MODE_PRIVATE)
    private var currentDialog: AlertDialog? = null
    private var currentDialogScrollView: NestedScrollView? = null
    private val scope get() = activity.lifecycleScope

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

    // ======= Detach / Destroy =======
    fun detach() {
        saveDialogScroll()
        guru.detachDownloadCallbacks()
        appState.onUpdateCheckResult = null
        if (currentDialog?.isShowing != true) {
            when (appState.updaterPhase) {
                AppState.UpdaterPhase.UPDATE_AVAILABLE,
                AppState.UpdaterPhase.DOWNLOAD_CANCELED,
                AppState.UpdaterPhase.INSTALL_UPDATE,
                AppState.UpdaterPhase.PENDING_INSTALL_UPDATE,
                AppState.UpdaterPhase.UP_TO_DATE,
                AppState.UpdaterPhase.CONNECTION_ERROR,
                AppState.UpdaterPhase.DOWNLOAD_ERROR,
                AppState.UpdaterPhase.INSTALL_ERROR,
                AppState.UpdaterPhase.WHATSNEW -> {
                    appState.updaterPhase = AppState.UpdaterPhase.NONE
                    appState.updaterDialogScrollY = 0
                }
                else -> {}
            }
        }
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
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                appState.updaterDialogScrollY = 0
                onDismissed()
            }
            currentDialog?.dismiss()
        } else {
            currentDialog?.dismiss()
            appState.updaterPhase = AppState.UpdaterPhase.NONE
            appState.updaterDialogScrollY = 0
        }
        currentDialog = null
        currentDialogScrollView = null
        return wasShowing
    }

    // ======= Recreation Re-attach =======
    fun reattach() {
        val savedScrollY = appState.updaterDialogScrollY
        when (appState.updaterPhase) {
            AppState.UpdaterPhase.CHECKING_UPDATE -> {
                val pending = appState.pendingUpdateCheckResult
                when {
                    pending != null -> {
                        appState.pendingUpdateCheckResult = null
                        handleCheckResult(pending)
                    }
                    appState.isCheckingUpdate -> {
                        showCheckingUpdateDialog(restoreScrollY = savedScrollY, isRestore = true)
                        appState.onUpdateCheckResult = { result -> handleCheckResult(result) }
                    }
                    else -> runCheckFlow()
                }
            }
            AppState.UpdaterPhase.UPDATE_AVAILABLE -> {
                showUpdateAvailableDialog(
                    remoteVersionName  = appState.updaterRemoteVersionName,
                    currentVersionName = appState.updaterCurrentVersionName,
                    downloadUrl        = appState.updaterDownloadUrl,
                    changelog          = appState.updaterChangelog,
                    restoreScrollY     = savedScrollY,
                    isRestore = true
                )
            }
            AppState.UpdaterPhase.DOWNLOADING -> {
                if (guru.isDownloading) {
                    reattachDownloadingDialog()
                } else if (guru.downloadCompletedWhileDetached) {
                    guru.downloadCompletedWhileDetached = false
                    setCachedApkVersionCode(appState.updaterRemoteVersionCode)
                    showInstallDialog()
                } else {
                    if (hasCachedApk()) {
                        showInstallDialog()
                    } else {
                        val downloadUrl = appState.updaterDownloadUrl
                        val remoteCode = appState.updaterRemoteVersionCode
                        if (!downloadUrl.isNullOrBlank()) {
                            showDownloadingDialog(downloadUrl, remoteCode, restoreScrollY = savedScrollY, isRestore = true)
                        } else {
                            showDownloadErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
                        }
                    }
                }
            }
            AppState.UpdaterPhase.DOWNLOAD_CANCELED -> {
                showDownloadCanceledDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.INSTALL_UPDATE -> {
                showInstallDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.PENDING_INSTALL_UPDATE -> {
                showPendingInstallDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.INSTALLING -> {
                showInstallingDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.UP_TO_DATE -> {
                showUpToDateDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.CONNECTION_ERROR -> {
                showConnectionErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.DOWNLOAD_ERROR -> {
                showDownloadErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.INSTALL_ERROR -> {
                showInstallErrorDialog(restoreScrollY = savedScrollY, isRestore = true)
            }
            AppState.UpdaterPhase.WHATSNEW -> {
                showWhatsNewDialog(restoreScrollY = savedScrollY, isRestore = true, onDismiss = {
                    (activity as? MainActivity)?.isBlocked = false
                })
            }
            AppState.UpdaterPhase.NONE -> {
                val pending = appState.pendingUpdateCheckResult
                if (pending != null) {
                    appState.pendingUpdateCheckResult = null
                    handleCheckResult(pending)
                }
            }
        }
    }

    // ======= Main Checks =======
    fun checkForUpdates() {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        if (appState.isCheckingUpdate) return
        if (hasCachedApk()) {
            (activity as? MainActivity)?.isBlocked = true
            showInstallDialog()
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
                (activity as? MainActivity)?.isBlocked = false
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
                (activity as? MainActivity)?.isBlocked = true
                showPendingInstallDialog()
            }
            return
        }
        withContext(Dispatchers.Main) { runCheckFlow(silent = true) }
    }

    // ======= Main Update Flow =======
    private fun runCheckFlow(silent: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        if (appState.isCheckingUpdate) return
        appState.isCheckingUpdate = true
        if (!silent) (activity as? MainActivity)?.isBlocked = true
        val appInfo = parseAppInfo()
        if (appInfo == null) {
            appState.isCheckingUpdate = false
            if (!silent) showConnectionErrorDialog()
            return
        }
        if (!silent) {
            if (currentDialog?.isShowing != true) showCheckingUpdateDialog()
        }
        appState.onUpdateCheckResult = { result -> handleCheckResult(result) }
        val startTime = System.currentTimeMillis()
        appState.persistentScope.launch {
            val fetchResult = withContext(Dispatchers.IO) {
                runCatching<JSONObject> {
                    val urlStr = "${appInfo.updateUrl}?_=${System.currentTimeMillis()}"
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout    = 10_000
                        useCaches      = false
                        setRequestProperty("Accept", "application/json")
                        connect()
                    }
                    if (conn.responseCode != HttpURLConnection.HTTP_OK)
                        throw Exception("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val json = JSONObject(body)
                    if (!json.has("versionCode") || !json.has("versionName"))
                        throw Exception("Invalid JSON format")
                    json
                }
            }
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
                } else {
                    appState.pendingUpdateCheckResult = pendingResult
                }
            }
        }
    }

    // ======= Check Result Handler =======
    private fun handleCheckResult(result: AppState.PendingUpdateCheckResult) {
        val json           = result.json
        val appVersionCode = result.appVersionCode
        val appVersionName = result.appVersionName
        val silent         = result.silent
        if (!silent) {
            (activity as? MainActivity)?.isBlocked = true
            currentDialog?.dismiss()
        }
        if (json != null) {
            val remoteVersion     = json.getLong("versionCode")
            val remoteVersionName = json.getString("versionName")
            val downloadUrl       = json.optString("downloadUrl", "")
            val changelog = when {
                json.has("changelog") -> {
                    val changelogObj = json.get("changelog")
                    when (changelogObj) {
                        is org.json.JSONArray -> (0 until changelogObj.length()).map { changelogObj.getString(it) }.joinToString("\n")
                        else -> changelogObj.toString()
                    }
                }
                else -> ""
            }
            if (remoteVersion > appVersionCode) {
                appState.updaterRemoteVersionCode = remoteVersion
                if (hasCachedApkForVersion(remoteVersion)) {
                    showInstallDialog()
                } else {
                    if (getCachedApkVersionCode() != -1L) clearCachedApk()
                    showUpdateAvailableDialog(remoteVersionName, appVersionName, downloadUrl, changelog)
                }
            } else {
                if (!silent) {
                    if (getCachedApkVersionCode() != -1L) clearCachedApk()
                    showUpToDateDialog()
                } else {
                    appState.updaterPhase = AppState.UpdaterPhase.NONE
                }
            }
        } else {
            if (!silent) showConnectionErrorDialog()
            else appState.updaterPhase = AppState.UpdaterPhase.NONE
        }
    }

    // ======= Install Flow =======
    private fun startDownload(downloadUrl: String, remoteVersionCode: Long, availableDialog: AlertDialog) {
        (activity as? MainActivity)?.isBlocked = true
        availableDialog.dismiss()
        showDownloadingDialog(downloadUrl, remoteVersionCode)
    }
    private fun startInstall(installDialog: AlertDialog) {
        (activity as? MainActivity)?.isBlocked = true
        installDialog.dismiss()
        showInstallingDialog()
        prefs.edit().remove("cached_apk_version_code").apply()
        guru.installCachedUpdate {
            clearCachedApk()
            currentDialog?.dismiss()
            showInstallErrorDialog()
        }
    }

    // ======= Dialog Builders =======
    // Checking Update Dialog
    private fun showCheckingUpdateDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.CHECKING_UPDATE
        appState.updaterDialogScrollY = 0
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_loading_title, null)
        titleView.findViewById<TextView>(R.id.dialog_title).text = context.getString(R.string.updater_checkingupdatetitle)
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_checkingupdatemsg)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Update Available Dialog
    private fun showUpdateAvailableDialog(remoteVersionName: String, currentVersionName: String, downloadUrl: String, changelog: String, restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase              = AppState.UpdaterPhase.UPDATE_AVAILABLE
        appState.updaterRemoteVersionName  = remoteVersionName
        appState.updaterCurrentVersionName = currentVersionName
        appState.updaterDownloadUrl        = downloadUrl
        appState.updaterChangelog          = changelog
        val changelogLine = if (changelog.isNotBlank()) context.getString(R.string.updater_updateavailablechangelog, changelog) else ""
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_updateavailabletitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_updateavailablemsg, remoteVersionName, currentVersionName, changelogLine)
        applyDialogIcon(titleText, R.drawable.ic_dialog_update)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton(context.getString(R.string.updater_btn_notnow)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .setPositiveButton(context.getString(R.string.updater_btn_updatenow), null)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                startDownload(downloadUrl, appState.updaterRemoteVersionCode, dialog)
            }
        }
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Downloading Dialog
    private fun buildDownloadingDialog(initialProgress: Int, restoreScrollY: Int = 0): Triple<AlertDialog, TextView, SeslProgressBar> {
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_downloadingtitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_progress_message, null)
        val messageText = messageView.findViewById<TextView>(R.id.dialog_message)
        val progressBar = messageView.findViewById<SeslProgressBar>(R.id.progressbar)
        messageText.text = context.getString(R.string.dialog_progress)
        progressBar.progress = initialProgress
        applyDialogIcon(titleText, R.drawable.ic_dialog_download)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .setNegativeButton(context.getString(R.string.dialog_btn_cancel), null)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
        return Triple(dialog, messageText, progressBar)
    }
    private fun showDownloadingDialog(downloadUrl: String, remoteVersionCode: Long, restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOADING
        val (dialog, messageText, progressBar) = buildDownloadingDialog(initialProgress = 0, restoreScrollY = restoreScrollY)
        dialog.setOnShowListener {
            progressBar.isIndeterminate = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                isEnabled = false
                setOnClickListener { guru.cancelActiveDownload() }
            }
        }
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        scope.launch {
            delay(500)
            progressBar.isIndeterminate = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            guru.downloadUpdate(
                url = downloadUrl,
                onProgress = { percent ->
                    messageText.text = context.getString(R.string.dialog_progress) + " ${percent.fmt()}%"
                    progressBar.progress = percent
                },
                onComplete = {
                    setCachedApkVersionCode(remoteVersionCode)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
                    scope.launch {
                        delay(500)
                        dialog.dismiss()
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
        }
    }
    private fun reattachDownloadingDialog(isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOADING
        val currentProgress = guru.downloadProgress
        val (dialog, messageText, progressBar) = buildDownloadingDialog(initialProgress = currentProgress)
        if (currentProgress > 0) {
            messageText.text = context.getString(R.string.dialog_progress) + " ${currentProgress.fmt()}%"
        }
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {guru.cancelActiveDownload()}}
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        guru.reattachDownloadCallbacks(
            onProgress = { percent ->
                messageText.text = context.getString(R.string.dialog_progress) + " ${percent.fmt()}%"
                progressBar.progress = percent
            },
            onComplete = {
                setCachedApkVersionCode(appState.updaterRemoteVersionCode)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
                scope.launch {
                    delay(500)
                    dialog.dismiss()
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
    }
    // Download Canceled Dialog
    private fun showDownloadCanceledDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOAD_CANCELED
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_downloadcanceledtitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_downloadcanceledmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Install Update Dialog
    private fun showInstallDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.INSTALL_UPDATE
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_installupdatetitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_installupdatemsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_update)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton(context.getString(R.string.updater_btn_later)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .setPositiveButton(context.getString(R.string.updater_btn_installnow), null)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                startInstall(dialog)
            }
        }
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Pending Install Dialog
    private fun showPendingInstallDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.PENDING_INSTALL_UPDATE
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_installupdatetitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_pendinginstallupdatemsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_update)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton(context.getString(R.string.updater_btn_later)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .setPositiveButton(context.getString(R.string.updater_btn_installnow), null)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                startInstall(dialog)
            }
        }
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Installing Dialog
    private fun showInstallingDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.INSTALLING
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_loading_title, null)
        titleView.findViewById<TextView>(R.id.dialog_title).text = context.getString(R.string.updater_installingtitle)
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_installingmsg)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Up To Date Dialog
    private fun showUpToDateDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.UP_TO_DATE
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_uptodatetitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_uptodatemsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_info)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_ok)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Connection Error Dialog
    private fun showConnectionErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.CONNECTION_ERROR
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_connectionerrortitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_connectionerrormsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Download Error Dialog
    private fun showDownloadErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.DOWNLOAD_ERROR
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_downloaderrortitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_downloaderrormsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Install Error Dialog
    private fun showInstallErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.INSTALL_ERROR
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_installerrortitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = context.getString(R.string.updater_installerrormsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }
    // Whats New Dialog
    private fun showWhatsNewDialog(restoreScrollY: Int = 0, onDismiss: (() -> Unit)? = null, isRestore: Boolean = false) {
        if ((activity as? MainActivity)?.checks?.lostRootDetected == true) return
        appState.updaterPhase = AppState.UpdaterPhase.WHATSNEW
        val builder = AlertDialog.Builder(context)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = context.getString(R.string.updater_whatsnewtitle) }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text =
            context.getString(R.string.updater_whatsnewhighlighted) + "\n" + """
                ⦁ App rebuilt from scratch (fully native)
                ⦁ Added color palette support
                ⦁ Added support for multiple languages
                    - Spanish
                    - Portuguese
                    - French
                    - Japanese
                    - Arabic
                    - Indonesian
                    - Turkish
                    - Romanian
                ⦁ Added new 'Device status' section to monitor your device usage
                ⦁ Added 'Search' function to all sections
                ⦁ Merged 'Freqs/UV settings' into to new 'Advanced settings' section
                ⦁ And more to discover!
            """.trimIndent() + "\n\n" + context.getString(R.string.updater_whatsnewfooter)
        applyDialogIcon(titleText, R.drawable.ic_dialog_changelog)
        val scrollView = setupScrollIndicators(messageView)
        val dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(context.getString(R.string.dialog_btn_close)) { d, _ ->
                appState.updaterPhase = AppState.UpdaterPhase.NONE
                d.dismiss()
                onDismiss?.invoke()
            }
            .create()
        dialog.seslSetBackgroundBlurEnabled(true)
        dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog.show()
        if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (activity as? MainActivity)?.checks?.attachFocusCheck(dialog)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        currentDialog = dialog
        currentDialogScrollView = scrollView
    }

    // ======= Helpers =======
    private fun parseAppInfo(): AppInfo? = runCatching {
        val json = JSONObject(guru.getAppInfo())
        if (!json.optBoolean("success")) return null
        AppInfo(
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            updateUrl   = json.getString("updateUrl")
        )
    }.getOrNull()
    private fun applyDialogIcon(titleText: TextView, drawableRes: Int) {
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