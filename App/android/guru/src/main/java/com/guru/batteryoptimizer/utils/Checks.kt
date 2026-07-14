package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt

import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject

class Checks(
    private val activity: MainActivity,
    val guru: GuruAPIs,
    private val onLoadingDone: (suspend () -> Unit)? = null
) {
    private var loadingDialog: AlertDialog? = null
    private var loadingDialogMessage: TextView? = null
    private var loadingStarted = false
    private var checksPassed
        get() = activity.appState.checksPassed
        set(value) { activity.appState.checksPassed = value }
    private var rootCheckRunning = false
    private var checkAppInstalledRunning = false
    private val scope get() = activity.lifecycleScope
    private var fatalDialog: AlertDialog? = null
    private var checksJob: Job? = null
    private var rootCheckJob: Job? = null
    private var appInstalledJob: Job? = null
    private var rootLostJob: Job? = null
    var lostRootDetected: Boolean
        get() = activity.appState.lostRootDetected
        set(value) { activity.appState.lostRootDetected = value }
    val batteryHealthText: String
        get() {
            val health = activity.appState.batteryHealthRaw
            val cycles = activity.appState.batteryCyclesRaw
            return if (health != null && cycles != null)
                "${health.toIntOrNull()?.fmt() ?: health}% / ${cycles.toFloatOrNull()?.fmt() ?: cycles} ${activity.getString(R.string.device_batterycycles)}"
            else
                activity.getString(R.string.device_batteryunknown)
        }
    companion object {
        private val supportedModels = listOf("SM-G960F", "SM-G960N", "SM-G965F", "SM-G965N", "SM-N960F", "SM-N960N", "SM-N770F")
        private val DIGITS_REGEX    = Regex("(\\d+)")
    }

    // ======= Entry Point =======
    fun start() {
        if (loadingStarted) return
        loadingStarted = true
        activity.appState.currentCheckStep = AppState.CheckStep.NOT_STARTED
        activity.appState.restoreLoadingDialog = false
        activity.appState.monitoringReadyFlag = false
        checksJob = scope.launch {runCheckSequence()}
    }
    fun destroy() {
        loadingDialog?.window?.setWindowAnimations(0)
        loadingDialog?.dismiss()
        loadingDialog = null
        fatalDialog?.window?.setWindowAnimations(0)
        fatalDialog?.dismiss()
        fatalDialog = null
    }
    fun detach() {
        loadingDialog = null
        loadingDialogMessage = null
        fatalDialog = null
    }

    // ======= Recreation Re-attach =======
    fun reattach() {
        val phase = activity.appState.dialogPhase
        val msg   = activity.appState.loadingMessage
        when (phase) {
            AppState.DialogPhase.LOADING -> {
                activity.appState.themeOverlayFade?.cancel()
                activity.appState.themeOverlayFade = null
                activity.appState.themeOverlayVisible = true
                activity.appState.themeOverlayAlpha = 1f
                activity.themeOverlay.visibility = View.VISIBLE
                activity.themeOverlay.alpha = 1f
                if (activity.appState.restoreLoadingDialog) {
                    showLoadingDialog(activity.getString(R.string.checksloading_title), msg, isRestore = true)
                }
                if (!checksPassed && activity.appState.currentCheckStep != AppState.CheckStep.DONE) {
                    checksJob = scope.launch { runCheckSequence(resume = true) }
                }
            }
            AppState.DialogPhase.FATAL_ROOT -> {
                showFatalDialog(activity.getString(R.string.checks_fatalroottitle), activity.getString(R.string.checks_fataltootmsg), noDim = true, isRestore = true)
            }
            AppState.DialogPhase.FATAL_DEVICE -> {
                showFatalDialog(activity.getString(R.string.checks_fataldevicetitle), activity.getString(R.string.checks_fataldevicemsg), noDim = true, isRestore = true)
            }
            AppState.DialogPhase.APP_DISABLED -> {
                showFatalDialog(activity.getString(R.string.checks_appdisabledtitle), activity.getString(R.string.checks_appdisabledmsg), noDim = !checksPassed, isRestore = true)
            }
            AppState.DialogPhase.FATAL_ROOT_LOST -> {
                lostRootDetected = true
                showFatalDialog(activity.getString(R.string.checks_fatalrootlosttitle), activity.getString(R.string.checks_fatalrootlostmsg), noDim = !checksPassed, isRestore = true)
            }
            AppState.DialogPhase.WHATSNEW,
            AppState.DialogPhase.NONE -> {
                if (activity.appState.checksPassed || activity.appState.currentCheckStep == AppState.CheckStep.DONE) {
                    activity.appState.themeOverlayVisible = false
                    activity.appState.themeOverlayAlpha = 1f
                    activity.themeOverlay.visibility = View.GONE
                    activity.themeOverlay.alpha = 1f
                } else if (!activity.appState.loadingStarted) {
                    loadingStarted = false
                    start()
                }
            }
        }
    }

    // ======= Root Checker =======
    fun reCheckRoot() {
        if (!checksPassed || lostRootDetected || rootCheckRunning) return
        rootCheckJob = scope.launch {
            rootCheckRunning = true
            try {
                val result = withContext(Dispatchers.IO) { JSONObject(guru.isRoot()) }
                if (result.optBoolean("success", false) && !result.optBoolean("hasRoot", false)) {showRootLostDialog()}
            } catch (_: Exception) {
            } finally {
                rootCheckRunning = false
                activity.isBlocked = false
            }
        }
    }
    fun attachFocusCheck(dialog: AlertDialog) {
        val original = dialog.window?.callback ?: return
        dialog.window?.callback = object : Window.Callback by original {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                original.onWindowFocusChanged(hasFocus)
                if (hasFocus && checksPassed && !lostRootDetected) reCheckRoot()
            }
        }
    }
    private fun showRootLostDialog() {
        if (lostRootDetected) return
        lostRootDetected = true
        activity.appState.onMonitoringError?.invoke()
        guru.cancelRunningOperations = true
        guru.cancelActiveDownload()
        rootLostJob = scope.launch {
            activity.runOnUiThread {
                loadingDialog?.dismiss()
                loadingDialog = null
                loadingDialogMessage = null
                activity.appState.dialogPhase = AppState.DialogPhase.FATAL_ROOT_LOST
                val showFatal = {
                    showFatalDialog(activity.getString(R.string.checks_fatalrootlosttitle), activity.getString(R.string.checks_fatalrootlostmsg), noDim = !checksPassed)
                }
                val hadFragment = activity.dismissActiveFragmentDialog(onDismissed = showFatal)
                val hadUpdater  = if (!hadFragment) activity.updater.dismissActiveDialog(onDismissed = showFatal) else false
                if (!hadFragment && !hadUpdater) {
                    showFatal()
                }
            }
        }
    }

    // ======= Check Sequence =======
    private suspend fun runCheckSequence(resume: Boolean = false) {
        try {
            if (!resume) {
                showLoadingDialog(activity.getString(R.string.checksloading_title), activity.getString(R.string.checks_checkingrequirements))
                delay(100)
            } else {
                if (!activity.appState.restoreLoadingDialog) {
                    showLoadingDialog(activity.getString(R.string.checksloading_title), activity.appState.loadingMessage)
                }
                activity.appState.restoreLoadingDialog = false
            }
            // Checking Root
            if (activity.appState.currentCheckStep.ordinal < AppState.CheckStep.CHECKING_ROOT.ordinal) {
                activity.appState.currentCheckStep = AppState.CheckStep.CHECKING_ROOT
                updatePhase(AppState.DialogPhase.LOADING, activity.getString(R.string.checks_checkingrequirements))
                val rootResult = withContext(Dispatchers.IO) { JSONObject(guru.isRoot()) }
                if (!rootResult.optBoolean("hasRoot", false)) {
                    dismissLoadingDialog()
                    activity.appState.dialogPhase = AppState.DialogPhase.FATAL_ROOT
                    activity.runOnUiThread {
                        showFatalDialog(activity.getString(R.string.checks_fatalroottitle), activity.getString(R.string.checks_fataltootmsg), noDim = true)
                    }
                    return
                }
            }
            delay(10)
            // Checking Device Support
            if (activity.appState.currentCheckStep.ordinal < AppState.CheckStep.CHECKING_DEVICE.ordinal) {
                activity.appState.currentCheckStep = AppState.CheckStep.CHECKING_DEVICE
                val deviceResult = withContext(Dispatchers.IO) { JSONObject(guru.getDeviceInfo()) }
                val model = deviceResult.optString("model", "")
                if (model !in supportedModels) {
                    dismissLoadingDialog()
                    activity.appState.dialogPhase = AppState.DialogPhase.FATAL_DEVICE
                    activity.runOnUiThread {
                        showFatalDialog(activity.getString(R.string.checks_fataldevicetitle), activity.getString(R.string.checks_fataldevicemsg), noDim = true)
                    }
                    return
                }
            }
            delay(10)
            // Checking App Installed
            if (activity.appState.currentCheckStep.ordinal < AppState.CheckStep.CHECKING_APPINSTALLED.ordinal) {
                activity.appState.currentCheckStep = AppState.CheckStep.CHECKING_APPINSTALLED
                if (checkGuruAppInstalled()) return
            }
            // Checking Settings
            if (activity.appState.currentCheckStep.ordinal < AppState.CheckStep.CHECKING_SETTINGS.ordinal) {
                activity.appState.currentCheckStep = AppState.CheckStep.CHECKING_SETTINGS
                updatePhase(AppState.DialogPhase.LOADING, activity.getString(R.string.checks_checkingsettings))
                withContext(Dispatchers.Main) { activity.appState.monitoringReadyFlag = true; activity.appState.onMonitoringReady?.invoke() }
                delay(10)
                scope.launch { fetchBatteryHealth(); fetchBatteryCapacity() }
                delay(10)
                if (!resume) {
                    activity.appState.guruScriptJob?.cancel()
                    activity.appState.guruScriptJob = activity.appState.persistentScope.launch(Dispatchers.IO) {
                        removeModuleScript()
                        deployAndRunGuruScript()
                    }
                    activity.appState.guruScriptJob?.join()
                }
            }
            if (resume) {
                activity.appState.persistentScope.launch {
                    activity.appState.guruScriptJob?.join()
                    withContext(Dispatchers.Main) { dismissWithFade() }
                }
                return
            }
            delay(10)
            dismissWithFade()
        } catch (e: Exception) {
            activity.appState.restoreLoadingDialog = false
            dismissWithFade()
        }
    }
    private fun updatePhase(phase: AppState.DialogPhase, message: String) {
        activity.appState.dialogPhase    = phase
        activity.appState.loadingMessage = message
        activity.appState.restoreLoadingDialog = (phase == AppState.DialogPhase.LOADING)
        activity.runOnUiThread {loadingDialogMessage?.text = message}
    }
    fun checkAppInstalled() {
        if (!checksPassed || lostRootDetected || checkAppInstalledRunning) return
        checkAppInstalledRunning = true
        appInstalledJob = scope.launch {
            try {
                if (checkGuruAppInstalled()) return@launch
            } catch (_: Exception) {
            } finally {
                checkAppInstalledRunning = false
                activity.isBlocked = false
            }
        }
    }
    private suspend fun checkGuruAppInstalled(): Boolean {
        val result = withContext(Dispatchers.IO) { guru.exec("pm list packages") }
        val guruAppInstalled = result.lines().any { it.trim() == "package:com.guru.batteryoptimizer.beta" }
        if (guruAppInstalled) {
            guru.cancelRunningOperations = true
            guru.cancelActiveDownload()
            loadingDialog?.dismiss()
            loadingDialog = null
            loadingDialogMessage = null
            withContext(Dispatchers.IO) { guru.exec("rm -f /data/data/com.guru.batteryoptimizer/guru.sh") }
            activity.appState.dialogPhase = AppState.DialogPhase.APP_DISABLED
            activity.runOnUiThread {
                val showAppInstalled = {
                    showFatalDialog(activity.getString(R.string.checks_appdisabledtitle), activity.getString(R.string.checks_appdisabledmsg), noDim = !checksPassed)
                }
                val hadFragment = activity.dismissActiveFragmentDialog(onDismissed = showAppInstalled)
                val hadUpdater  = if (!hadFragment) activity.updater.dismissActiveDialog(onDismissed = showAppInstalled) else false
                if (!hadFragment && !hadUpdater) {
                    showAppInstalled()
                }
            }
            return true
        }
        return false
    }

    // ======= Battery Capacity =======
    private suspend fun fetchBatteryCapacity() {
        try {
            val raw = withContext(Dispatchers.IO) {
                guru.exec("read -r v < /sys/class/power_supply/battery/charge_full 2>/dev/null && echo \"\$v\" || echo ''")
            }.trim()
            val uah = raw.toLongOrNull()
            activity.appState.batteryCapacityMah = if (uah != null) (uah / 1000).toInt() else null
        } catch (_: Exception) {
            activity.appState.batteryCapacityMah = null
        } finally {
            withContext(Dispatchers.Main) {
                activity.appState.onBatteryCapacityReady?.invoke()
            }
        }
    }

    // ======= Battery Health =======
    private suspend fun fetchBatteryHealth() {
        try {
            val raw = withContext(Dispatchers.IO) {guru.exec("dumpsys battery 2>/dev/null | grep -E 'mSavedBatteryAsoc|mSavedBatteryUsage'")}
            val lines = raw.lines()
            val healthLine = lines.firstOrNull { it.contains("mSavedBatteryAsoc") }.orEmpty()
            val cyclesLine = lines.firstOrNull { it.contains("mSavedBatteryUsage") }.orEmpty()
            var health: String? = null
            var cycles: String? = null
            if (healthLine.isNotBlank()) { DIGITS_REGEX.find(healthLine)?.groupValues?.get(1)?.let { health = it }}
            if (cyclesLine.isNotBlank()) {
                DIGITS_REGEX.find(cyclesLine)?.groupValues?.get(1)?.let { raw ->
                    cycles = if (raw.length >= 3) {
                        val withoutLast = raw.dropLast(1)
                        withoutLast.dropLast(1) + "." + withoutLast.takeLast(1)
                    } else raw
                }
            }
            activity.appState.batteryHealthRaw = health
            activity.appState.batteryCyclesRaw = cycles
        } catch (_: Exception) {
            activity.appState.batteryHealthRaw = null
            activity.appState.batteryCyclesRaw = null
        } finally {
            withContext(Dispatchers.Main) { activity.appState.onBatteryHealthReady?.invoke() }
        }
    }

    // ======= Dialog (Loading) =======
    private fun showLoadingDialog(title: String, message: String, isRestore: Boolean = false) {
        activity.runOnUiThread {
            if (loadingDialog?.isShowing == true) {
                loadingDialogMessage?.text = message
                return@runOnUiThread
            }
            try {
                val builder = AlertDialog.Builder(activity)
                val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_loading_title, null)
                titleView.findViewById<TextView>(R.id.dialog_title).text = title
                val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
                val msgTextView = messageView.findViewById<TextView>(R.id.dialog_message)
                msgTextView.text = message
                loadingDialogMessage = msgTextView
                setupScrollIndicators(messageView)
                loadingDialog = builder
                    .setCustomTitle(titleView)
                    .setView(messageView)
                    .setCancelable(false)
                    .create()
                loadingDialog?.seslSetBackgroundBlurEnabled(true)
                loadingDialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
                loadingDialog?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                loadingDialog?.setOnDismissListener {
                    loadingDialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                loadingDialog?.show()
                if (isRestore) loadingDialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
                loadingDialog?.window?.setDimAmount(0f)
                activity.appState.restoreLoadingDialog = true
                activity.isBlocked = true
            } catch (e: Exception) {
                loadingDialog = null
                loadingDialogMessage = null
            }
        }
    }

    // ======= Dialog (Dismiss) =======
    private fun dismissLoadingDialog() {
        activity.runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
            loadingDialogMessage = null
            activity.appState.restoreLoadingDialog = false
        }
    }
    private fun dismissWithFade() {
        activity.runOnUiThread {
            try {
                activity.appState.restoreLoadingDialog = false
                activity.appState.themeOverlayAnimating = false
                checksPassed = true
                activity.appState.currentCheckStep = AppState.CheckStep.DONE
                activity.appState.dialogPhase = AppState.DialogPhase.NONE
                loadingDialog?.dismiss()
                loadingDialog = null
                loadingDialogMessage = null
                if (activity.isDestroyed) {
                    activity.appState.themeOverlayVisible = false
                    activity.appState.themeOverlayAlpha = 1f
                    if (activity.appState.dialogPhase != AppState.DialogPhase.LOADING) {
                        activity.appState.onOverlayDismiss?.invoke()
                    }
                    return@runOnUiThread
                }
                activity.themeOverlay.animate().cancel()
                activity.themeOverlay.visibility = View.VISIBLE
                activity.themeOverlay.alpha = 1f
                activity.themeOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        activity.themeOverlay.visibility = View.GONE
                        activity.themeOverlay.alpha = 1f
                        activity.appState.themeOverlayVisible = false
                        activity.appState.themeOverlayAlpha = 1f
                        activity.isBlocked = false
                        scope.launch { onLoadingDone?.invoke() }
                        reCheckRoot()
                    }
                    .start()
            } catch (e: Exception) {
                activity.appState.themeOverlayVisible = false
                activity.appState.themeOverlayAlpha = 1f
                if (!activity.isDestroyed) {
                    activity.themeOverlay.visibility = View.GONE
                    activity.themeOverlay.alpha = 1f
                }
                activity.isBlocked = false
                reCheckRoot()
            }
        }
    }

    // ======= Dialog (Fatal Error) =======
    private fun showFatalDialog(title: String, message: String, noDim: Boolean = false, isRestore: Boolean = false) {
        activity.runOnUiThread {
            if (fatalDialog?.isShowing == true) return@runOnUiThread
            if (activity.isDestroyed) return@runOnUiThread
            activity.appState.restoreLoadingDialog = false
            activity.appState.themeOverlayAnimating = false
            try {
                activity.themeOverlay.animate().cancel()
                fatalDialog = buildIconDialog(title = title, message = message, icon = R.drawable.ic_dialog_error)
                    .setPositiveButton(activity.getString(R.string.checks_closeapp)) { _, _ -> guru.closeApp() }
                    .create()
                fatalDialog?.seslSetBackgroundBlurEnabled(true)
                fatalDialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
                fatalDialog?.show()
                if (isRestore) fatalDialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
                if (noDim) fatalDialog?.window?.setDimAmount(0f)
                activity.isBlocked = false
            } catch (e: Exception) {
                guru.closeApp()
            }
        }
    }

    // ======= Dialog Builder =======
    fun buildIconDialog(title: String, message: String, icon: Int): AlertDialog.Builder {
        val builder = AlertDialog.Builder(activity)
        val titleView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = title }
        val messageView = activity.layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = message
        ContextCompat.getDrawable(activity, icon)?.let { drawable ->
            val fm = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size = (baseSize * 1.2f).toInt()
            val offsetV = ((size - baseSize) / 2)
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (8 * activity.resources.displayMetrics.density).toInt()
        }
        setupScrollIndicators(messageView)
        return builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
    }

    // ======= Deploy Script =======
    private fun removeModuleScript() {
        runCatching {
            val bootScriptPath = "/data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh"
            guru.exec("rm -f $bootScriptPath")
        }
    }
    private suspend fun deployAndRunGuruScript() {
        runCatching {
            val destPath = "/data/data/com.guru.batteryoptimizer/guru.sh"
            val script = GuruFunctions(guru, activity).buildScript()
            val escaped = script.replace("'", "'\\''")
            guru.exec("mkdir -p \$(dirname $destPath) && printf '%s' '$escaped' > $destPath && chmod 755 $destPath && sh $destPath")
        }
    }

    // ======= Scroll Indicators =======
    private fun setupScrollIndicators(scrollIndicator: View) {
        val scrollView      = scrollIndicator.findViewById<NestedScrollView>(R.id.dialog_scroll)
        val indicatorTop    = scrollIndicator.findViewById<View>(R.id.scrollIndicatorUp)
        val indicatorBottom    = scrollIndicator.findViewById<View>(R.id.scrollIndicatorDown)
        fun update() {
            indicatorTop.visibility    = if (scrollView.canScrollVertically(-1)) View.VISIBLE else View.GONE
            indicatorBottom.visibility    = if (scrollView.canScrollVertically(1))  View.VISIBLE else View.GONE
        }
        scrollView.post { update() }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> update() })
    }
}