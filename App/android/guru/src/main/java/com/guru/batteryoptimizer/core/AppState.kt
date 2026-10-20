package com.guru.batteryoptimizer.core
import com.guru.batteryoptimizer.utils.GuruAPIs

import android.app.Application
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import org.json.JSONObject

class AppState(application: Application) : AndroidViewModel(application) {
    // ======= Saved Recreation =======
    val guru: GuruAPIs by lazy { GuruAPIs(application) }
    val persistentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override fun onCleared() {
        super.onCleared()
        persistentScope.cancel()
    }

    // ======= Loading / Splash Flags =======
    var loadingStarted = false
    var restoreLoadingDialog = false
    var splashExited = false
    var splashAnimationStarted = false
    var checksStarted = false
    var checksPassed = false
    var guruScriptJob: Job? = null
    var themeOverlayFade: Job? = null
    var onOverlayDismiss: (() -> Unit)? = null
    @Volatile var themeOverlayVisible = true
    @Volatile var themeOverlayAlpha = 1f
    @Volatile var themeOverlayAnimating = false

    // ======= UI State =======
    var selectedTab = 0
    var toolbarExpanded = true
    var toolbarHideDepth = 0
    var toolbarAnimatedTab = -1
    var isBlocked = true
    var batteryCapacityMah: Int? = null
    var onBatteryCapacityReady: (() -> Unit)? = null
    var batteryHealthRaw: String? = null
    var batteryCyclesRaw: String? = null
    var onBatteryHealthReady: (() -> Unit)? = null
    var onMonitoringReady: (() -> Unit)? = null
    var onMonitoringError: (() -> Unit)? = null
    var monitoringReadyFlag = false
    var isSearchModeActive = false
    var searchWasExpanded = false
    var searchWasHidden = false
    var searchQuery = ""
    var zramAlgorithm = "N/A"

    // ======= Checks Dialog States =======
    enum class DialogPhase { NONE, LOADING, FATAL_ROOT, FATAL_DEVICE, APP_DISABLED, FATAL_ROOT_LOST, WHATSNEW }
    enum class CheckStep { NOT_STARTED, CHECKING_ROOT, CHECKING_DEVICE, CHECKING_APPINSTALLED, CHECKING_SETTINGS, DONE }
    var dialogPhase = DialogPhase.NONE
    var loadingMessage = ""
    var currentCheckStep = CheckStep.NOT_STARTED
    var lostRootDetected = false

    // ======= Fragment States =======
    enum class FragmentDialog {
        NONE,
        DEVICE_INFO,
        GENERAL_INFO, GENERAL_BATT_PROTECT_WARNING, OPTIMIZE_APPS, OPTIMIZING_APPS, OPTIMIZING_CANCELED, APPS_OPTIMIZED,
        ADVANCED_INFO,
        ABOUT_INFO, CHANGELOG, LABS_WARNING, RESET, RESETTING, IMPORT_WARNING, IMPORT_ERROR, IMPORT_SUCESS, EXPORT_SUCESS
    }
    var fragmentDialog = FragmentDialog.NONE
    var fragmentDialogScrollY = 0
    var optimizeCancelBtnEnabled = false
    var optimizeCompletedSuccessfully: Boolean? = null
    var optimizeProgress = 0
    var onOptimizeCompleteReady: (() -> Unit)? = null
    var onOptimizeCancelReady: (() -> Unit)? = null

    // ======= Updater States =======
    enum class UpdaterWidgetState { NONE, CHECKING_UPDATE, UPDATE_AVAILABLE, INSTALL_UPDATE, UP_TO_DATE, CONNECTION_ERROR }
    enum class UpdaterPhase {
        NONE,
        UPDATE_AVAILABLE,
        DOWNLOADING, DOWNLOAD_CANCELED, DOWNLOAD_ERROR,
        INSTALL_UPDATE, PENDING_INSTALL_UPDATE, INSTALLING, INSTALL_ERROR,
        CONNECTION_ERROR,
        WHATSNEW
    }
    data class PendingUpdateCheckResult(
        val json: JSONObject?, val appVersionCode: Long, val appVersionName: String,
        val appUpdateUrl: String, val silent: Boolean
    )
    var updaterWidgetState = UpdaterWidgetState.NONE
    var updaterPhase = UpdaterPhase.NONE
    var updaterRemoteVersionName = ""
    var updaterCurrentVersionName = ""
    var updaterChangelog = ""
    var updaterDownloadUrl = ""
    var updaterDialogScrollY = 0
    var updaterRemoteVersionCode = -1L
    var isCheckingUpdate = false
    var onUpdateCheckResult: ((PendingUpdateCheckResult) -> Unit)? = null
    var pendingUpdateCheckResult: PendingUpdateCheckResult? = null

    // ======= Scroll State =======
    private val scrollPositions = mutableMapOf<String, Int>()
    private val attachedScrollViews = mutableMapOf<String, NestedScrollView>()
    fun attachScroll(key: String, scrollView: NestedScrollView) { attachedScrollViews[key] = scrollView }
    fun restoreScroll(key: String) {
        val scrollView = attachedScrollViews[key] ?: return
        val savedY = scrollPositions.remove(key) ?: return
        scrollView.postDelayed({ if (savedY > 0) scrollView.scrollTo(0, savedY) }, 10)
    }
    fun saveScroll(key: String) {
        attachedScrollViews[key]?.let { scrollPositions[key] = it.scrollY }
    }
    fun detachScroll(key: String, savePosition: Boolean) {
        if (!savePosition) scrollPositions.remove(key)
        attachedScrollViews.remove(key)
    }
}