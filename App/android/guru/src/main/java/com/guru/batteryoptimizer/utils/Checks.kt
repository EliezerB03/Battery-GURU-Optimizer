package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.core.AppState.CheckStep
import com.guru.batteryoptimizer.core.AppState.DialogPhase
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SeslProgressBar
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject

class Checks(private val activity: MainActivity, val guru: GuruAPIs, private val onLoadingDone: (suspend () -> Unit)? = null) {
    private val state get() = activity.appState
    private val scope get() = activity.lifecycleScope
    private var loadingDialog: AlertDialog? = null
    private var loadingDialogMessage: AppCompatTextView? = null
    private var fatalDialog: AlertDialog? = null
    private var loadingStarted = false
    private var rootCheckRunning = false
    private var checkAppInstalledRunning = false
    private var checksPassed: Boolean
        get() = state.checksPassed
        set(value) { state.checksPassed = value }
    val batteryHealthText: String
        get() {
            val health = state.batteryHealthRaw
            val cycles = state.batteryCyclesRaw
            return if (health != null && cycles != null)
                "${health.toIntOrNull()?.fmt() ?: health}% / ${cycles.toFloatOrNull()?.fmt() ?: cycles} ${activity.getString(R.string.device_batterycycles)}"
            else
                activity.getString(R.string.device_batteryunknown)
        }
    var lostRootDetected: Boolean
        get() = state.lostRootDetected
        set(value) { state.lostRootDetected = value }
    private fun isMonetActive(): Boolean = !android.provider.Settings.Secure.getString(activity.contentResolver, "theme_customization_overlay_packages").isNullOrEmpty()
    private fun applyDotsProgress(bar: SeslProgressBar?) { if (isMonetActive()) bar?.indeterminateDrawable = ContextCompat.getDrawable(activity, R.drawable.progress_dots_monet) }
    companion object {
        private val supportedModels = listOf("SM-G960F", "SM-G960N", "SM-G965F", "SM-G965N", "SM-N960F", "SM-N960N", "SM-N770F")
        private val DIGITS_REGEX    = Regex("(\\d+)")
        private const val SCRIPT_PATH = "/data/data/com.guru.batteryoptimizer/guru.sh"
        private const val MODULE_SCRIPT = "/data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh"
        private const val BETA_PKG = "package:com.guru.batteryoptimizer.beta"
    }

    // ======= Entry Point =======
    fun start() {
        if (loadingStarted) return
        loadingStarted = true
        state.currentCheckStep = CheckStep.NOT_STARTED
        state.restoreLoadingDialog = false
        state.monitoringReadyFlag = false
        scope.launch { runCheckSequence() }
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
        when (state.dialogPhase) {
            DialogPhase.LOADING -> {
                state.themeOverlayFade?.cancel()
                state.themeOverlayFade = null
                state.themeOverlayVisible = true
                state.themeOverlayAlpha = 1f
                activity.themeOverlay.visibility = View.VISIBLE
                activity.themeOverlay.alpha = 1f
                if (state.restoreLoadingDialog) { showLoadingDialog(activity.getString(R.string.checksloading_title), state.loadingMessage, isRestore = true) }
                if (!checksPassed && state.currentCheckStep != CheckStep.DONE) { scope.launch { runCheckSequence(resume = true) }}
            }
            DialogPhase.FATAL_ROOT -> showFatalDialog(R.string.checks_fatalroottitle, R.string.checks_fataltootmsg, noDim = true, isRestore = true)
            DialogPhase.FATAL_DEVICE -> showFatalDialog(R.string.checks_fataldevicetitle, R.string.checks_fataldevicemsg, noDim = true, isRestore = true)
            DialogPhase.APP_DISABLED -> showFatalDialog(R.string.checks_appdisabledtitle, R.string.checks_appdisabledmsg, noDim = !checksPassed, isRestore = true)
            DialogPhase.FATAL_ROOT_LOST -> {
                lostRootDetected = true
                showFatalDialog(R.string.checks_fatalrootlosttitle, R.string.checks_fatalrootlostmsg, noDim = !checksPassed, isRestore = true)
            }
            DialogPhase.WHATSNEW,
            DialogPhase.NONE -> {
                if (state.checksPassed || state.currentCheckStep == CheckStep.DONE) {
                    state.themeOverlayVisible = false
                    state.themeOverlayAlpha = 1f
                    activity.themeOverlay.visibility = View.GONE
                    activity.themeOverlay.alpha = 1f
                } else if (!state.loadingStarted) {
                    loadingStarted = false
                    start()
                }
            }
        }
    }

    // ======= Root Checker =======
    fun reCheckRoot() {
        if (!checksPassed || lostRootDetected || rootCheckRunning) return
        scope.launch {
            rootCheckRunning = true
            try {
                val result = withContext(Dispatchers.IO) { JSONObject(guru.isRoot()) }
                if (result.optBoolean("success", false) && !result.optBoolean("hasRoot", false)) showRootLostDialog()
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
        state.onMonitoringError?.invoke()
        guru.cancelRunningOperations = true
        guru.cancelActiveDownload()
        scope.launch {
            activity.runOnUiThread {
                clearLoadingDialog()
                state.dialogPhase = DialogPhase.FATAL_ROOT_LOST
                showAfterDismissingDialogs {
                    showFatalDialog(R.string.checks_fatalrootlosttitle, R.string.checks_fatalrootlostmsg, noDim = !checksPassed)
                }
            }
        }
    }
    private fun showAfterDismissingDialogs(show: () -> Unit) {
        val hadFragment = activity.dismissActiveFragmentDialog(onDismissed = show)
        val hadUpdater  = if (!hadFragment) activity.updater.dismissActiveDialog(onDismissed = show) else false
        if (!hadFragment && !hadUpdater) show()
    }

    // ======= Check Sequence =======
    private fun pending(step: CheckStep) = state.currentCheckStep.ordinal < step.ordinal
    private fun failCheck(phase: DialogPhase, @StringRes title: Int, @StringRes message: Int) {
        dismissLoadingDialog()
        state.dialogPhase = phase
        showFatalDialog(title, message, noDim = true)
    }
    private suspend fun runCheckSequence(resume: Boolean = false) {
        try {
            if (!resume) {
                showLoadingDialog(activity.getString(R.string.checksloading_title), activity.getString(R.string.checks_checkingrequirements))
                delay(100)
            } else {
                if (!state.restoreLoadingDialog) { showLoadingDialog(activity.getString(R.string.checksloading_title), state.loadingMessage) }
                state.restoreLoadingDialog = false
            }
            // Checking Root
            if (pending(CheckStep.CHECKING_ROOT)) {
                state.currentCheckStep = CheckStep.CHECKING_ROOT
                updatePhase(DialogPhase.LOADING, activity.getString(R.string.checks_checkingrequirements))
                val rootResult = withContext(Dispatchers.IO) { JSONObject(guru.isRoot()) }
                if (!rootResult.optBoolean("hasRoot", false)) {
                    failCheck(DialogPhase.FATAL_ROOT, R.string.checks_fatalroottitle, R.string.checks_fataltootmsg)
                    return
                }
            }
            delay(10)
            // Checking Device Support
            if (pending(CheckStep.CHECKING_DEVICE)) {
                state.currentCheckStep = CheckStep.CHECKING_DEVICE
                val deviceResult = withContext(Dispatchers.IO) { JSONObject(guru.getDeviceInfo()) }
                if (deviceResult.optString("model", "") !in supportedModels) {
                    failCheck(DialogPhase.FATAL_DEVICE, R.string.checks_fataldevicetitle, R.string.checks_fataldevicemsg)
                    return
                }
            }
            delay(10)
            // Checking App Installed
            if (pending(CheckStep.CHECKING_APPINSTALLED)) {
                state.currentCheckStep = CheckStep.CHECKING_APPINSTALLED
                if (checkGuruAppInstalled()) return
            }
            // Checking Settings
            if (pending(CheckStep.CHECKING_SETTINGS)) {
                state.currentCheckStep = CheckStep.CHECKING_SETTINGS
                updatePhase(DialogPhase.LOADING, activity.getString(R.string.checks_checkingsettings))
                withContext(Dispatchers.Main) { state.monitoringReadyFlag = true; state.onMonitoringReady?.invoke() }
                delay(10)
                scope.launch { fetchBatteryHealth(); fetchBatteryCapacity() }
                delay(10)
                if (!resume) {
                    state.guruScriptJob?.cancel()
                    state.guruScriptJob = state.persistentScope.launch(Dispatchers.IO) { deployGuruScript() }
                    state.guruScriptJob?.join()
                }
            }
            if (resume) {
                state.persistentScope.launch {
                    state.guruScriptJob?.join()
                    withContext(Dispatchers.Main) { dismissWithFade() }
                }
                return
            }
            delay(10)
            dismissWithFade()
        } catch (e: Exception) {
            state.restoreLoadingDialog = false
            dismissWithFade()
        }
    }
    private fun updatePhase(phase: DialogPhase, message: String) {
        state.dialogPhase    = phase
        state.loadingMessage = message
        state.restoreLoadingDialog = (phase == DialogPhase.LOADING)
        activity.runOnUiThread { loadingDialogMessage?.text = message }
    }
    fun checkAppInstalled() {
        if (!checksPassed || lostRootDetected || checkAppInstalledRunning) return
        checkAppInstalledRunning = true
        scope.launch {
            try { checkGuruAppInstalled()}
            catch (_: Exception) {}
            finally {
                checkAppInstalledRunning = false
                activity.isBlocked = false
            }
        }
    }
    private suspend fun checkGuruAppInstalled(): Boolean {
        val result = withContext(Dispatchers.IO) { guru.exec("pm list packages") }
        if (result.lines().none { it.trim() == BETA_PKG }) return false
        guru.cancelRunningOperations = true
        guru.cancelActiveDownload()
        clearLoadingDialog()
        withContext(Dispatchers.IO) { guru.exec("rm -f $SCRIPT_PATH") }
        state.dialogPhase = DialogPhase.APP_DISABLED
        activity.runOnUiThread {showAfterDismissingDialogs {showFatalDialog(R.string.checks_appdisabledtitle, R.string.checks_appdisabledmsg, noDim = !checksPassed)}}
        return true
    }

    // ======= Battery Capacity =======
    private suspend fun fetchBatteryCapacity() {
        try {
            val raw = withContext(Dispatchers.IO) {guru.exec("read -r v < /sys/class/power_supply/battery/charge_full 2>/dev/null && echo \"\$v\" || echo ''")}.trim()
            state.batteryCapacityMah = raw.toLongOrNull()?.let { (it / 1000).toInt() }
        } catch (_: Exception) { state.batteryCapacityMah = null }
        finally { withContext(Dispatchers.Main) { state.onBatteryCapacityReady?.invoke() }}
    }

    // ======= Battery Health =======
    private suspend fun fetchBatteryHealth() {
        try {
            val raw = withContext(Dispatchers.IO) { guru.exec("dumpsys battery 2>/dev/null | grep -E 'mSavedBatteryAsoc|mSavedBatteryUsage'") }
            val lines = raw.lines()
            fun digitsOf(marker: String) = lines.firstOrNull { it.contains(marker) }?.let { DIGITS_REGEX.find(it)?.groupValues?.get(1) }
            state.batteryHealthRaw = digitsOf("mSavedBatteryAsoc")
            state.batteryCyclesRaw = digitsOf("mSavedBatteryUsage")?.let { digits ->
                if (digits.length >= 3) {
                    val withoutLast = digits.dropLast(1)
                    withoutLast.dropLast(1) + "." + withoutLast.takeLast(1)
                } else digits
            }
        } catch (_: Exception) {
            state.batteryHealthRaw = null
            state.batteryCyclesRaw = null
        } finally {withContext(Dispatchers.Main) {state.onBatteryHealthReady?.invoke() }}
    }

    // ======= Dialog (Loading) =======
    private fun inflateDialogView(builder: AlertDialog.Builder, layout: Int): View = activity.layoutInflater.cloneInContext(builder.context).inflate(layout, null)
    private fun showLoadingDialog(title: String, message: String, isRestore: Boolean = false) {
        activity.runOnUiThread {
            if (loadingDialog?.isShowing == true) {
                loadingDialogMessage?.text = message
                return@runOnUiThread
            }
            try {
                val builder = AlertDialog.Builder(activity)
                val titleView = inflateDialogView(builder, R.xml.dialog_loading_title)
                titleView.findViewById<AppCompatTextView>(R.id.dialog_title).text = title
                val messageView = inflateDialogView(builder, R.xml.dialog_message)
                val msgTextView = messageView.findViewById<AppCompatTextView>(R.id.dialog_message)
                applyDotsProgress(titleView.findViewById(R.id.dialog_loading_progress))
                msgTextView.text = message
                loadingDialogMessage = msgTextView
                setupScrollIndicators(messageView)
                val dialog = builder.setCustomTitle(titleView).setView(messageView).setCancelable(false).create()
                loadingDialog = dialog
                dialog.seslSetBackgroundBlurEnabled(true)
                dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                dialog.setOnDismissListener { dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                dialog.show()
                if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
                dialog.window?.setDimAmount(0f)
                state.restoreLoadingDialog = true
                activity.isBlocked = true
            } catch (e: Exception) {
                loadingDialog = null
                loadingDialogMessage = null
            }
        }
    }

    // ======= Dialog (Dismiss) =======
    private fun clearLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
        loadingDialogMessage = null
    }
    private fun dismissLoadingDialog() {
        activity.runOnUiThread {
            clearLoadingDialog()
            state.restoreLoadingDialog = false
        }
    }
    private fun dismissWithFade() {
        activity.runOnUiThread {
            try {
                state.restoreLoadingDialog = false
                state.themeOverlayAnimating = false
                checksPassed = true
                state.currentCheckStep = CheckStep.DONE
                state.dialogPhase = DialogPhase.NONE
                clearLoadingDialog()
                if (activity.isDestroyed) {
                    state.themeOverlayVisible = false
                    state.themeOverlayAlpha = 1f
                    state.onOverlayDismiss?.invoke()
                    return@runOnUiThread
                }
                val overlay = activity.themeOverlay
                overlay.animate().cancel()
                overlay.visibility = View.VISIBLE
                overlay.alpha = 1f
                overlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        overlay.visibility = View.GONE
                        overlay.alpha = 1f
                        state.themeOverlayVisible = false
                        state.themeOverlayAlpha = 1f
                        activity.isBlocked = false
                        scope.launch { onLoadingDone?.invoke() }
                        reCheckRoot()
                    }
                    .start()
            } catch (e: Exception) {
                state.themeOverlayVisible = false
                state.themeOverlayAlpha = 1f
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
    private fun showFatalDialog(@StringRes title: Int, @StringRes message: Int, noDim: Boolean = false, isRestore: Boolean = false) {
        activity.runOnUiThread {
            if (fatalDialog?.isShowing == true || activity.isDestroyed) return@runOnUiThread
            state.restoreLoadingDialog = false
            state.themeOverlayAnimating = false
            try {
                activity.themeOverlay.animate().cancel()
                val dialog = buildIconDialog(activity.getString(title), activity.getString(message), R.drawable.ic_dialog_error)
                    .setPositiveButton(activity.getString(R.string.checks_closeapp)) { _, _ -> guru.closeApp() }
                    .create()
                fatalDialog = dialog
                dialog.seslSetBackgroundBlurEnabled(true)
                dialog.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
                dialog.show()
                if (isRestore) dialog.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
                if (noDim) dialog.window?.setDimAmount(0f)
                activity.isBlocked = false
            } catch (e: Exception) { guru.closeApp() }
        }
    }

    // ======= Dialog Builder =======
    fun buildIconDialog(title: String, message: String, icon: Int): AlertDialog.Builder {
        val builder = AlertDialog.Builder(activity)
        val titleView = inflateDialogView(builder, R.xml.dialog_icon_title)
        val titleText = titleView.findViewById<AppCompatTextView>(R.id.dialog_title).apply { text = title }
        val messageView = inflateDialogView(builder, R.xml.dialog_message)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = message
        ContextCompat.getDrawable(activity, icon)?.let { drawable ->
            val fm = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size = (baseSize * 1.2f).toInt()
            val offsetV = (size - baseSize) / 2
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (8 * activity.resources.displayMetrics.density).toInt()
        }
        setupScrollIndicators(messageView)
        return builder.setCustomTitle(titleView).setView(messageView).setCancelable(false)
    }

    // ======= Deploy Script =======
    private fun deployGuruScript() {
        runCatching { guru.exec("rm -f $MODULE_SCRIPT") }
        runCatching {
            val script = GuruFunctions(guru, activity).buildScript()
            val escaped = script.replace("'", "'\\''")
            guru.exec("mkdir -p \$(dirname $SCRIPT_PATH) && printf '%s' '$escaped' > $SCRIPT_PATH && chmod 755 $SCRIPT_PATH && sh $SCRIPT_PATH")
        }
    }

    // ======= Scroll Indicators =======
    private fun setupScrollIndicators(root: View) {
        val scrollView      = root.findViewById<NestedScrollView>(R.id.dialog_scroll)
        val indicatorTop    = root.findViewById<View>(R.id.scrollIndicatorUp)
        val indicatorBottom = root.findViewById<View>(R.id.scrollIndicatorDown)
        fun update() {
            indicatorTop.visibility    = if (scrollView.canScrollVertically(-1)) View.VISIBLE else View.GONE
            indicatorBottom.visibility = if (scrollView.canScrollVertically(1))  View.VISIBLE else View.GONE
        }
        scrollView.post { update() }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> update() })
    }
}