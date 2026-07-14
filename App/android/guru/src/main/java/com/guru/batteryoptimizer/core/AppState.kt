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
    var loadingStarted  = false
    var loadingDialogVisible: Boolean = false
    var restoreLoadingDialog: Boolean = false
    var loadingDialogTitle: String = ""
    var loadingDialogMessage: String = ""
    var splashExited = false
    var splashAnimationStarted = false
    var checksStarted = false
    var checksPassed = false
    var guruScriptJob: Job? = null
    var themeOverlayFade: Job? = null
    var onOverlayDismiss: (() -> Unit)? = null
    @Volatile var themeOverlayVisible: Boolean = true
    @Volatile var themeOverlayAlpha: Float = 1f
    @Volatile var themeOverlayAnimating: Boolean = false

    // ======= UI State =======
    var selectedTab = 0
    var toolbarExpanded = true
    var toolbarAnimatedTab = -1
    var isBlocked: Boolean = true
    var batteryCapacityMah: Int? = null
    var onBatteryCapacityReady: (() -> Unit)? = null
    var batteryHealthRaw: String? = null
    var batteryCyclesRaw: String? = null
    var onBatteryHealthReady: (() -> Unit)? = null
    var onMonitoringReady: (() -> Unit)? = null
    var onMonitoringError: (() -> Unit)? = null
    var monitoringReadyFlag: Boolean = false
    var isSearchModeActive: Boolean = false
    var searchQuery: String = ""
    var zramAlgorithm: String = "N/A"

    // ======= Checks Dialog States =======
    enum class DialogPhase {
        NONE,
        LOADING, FATAL_ROOT, FATAL_DEVICE, APP_DISABLED, 
        FATAL_ROOT_LOST,
        WHATSNEW
    }
    var dialogPhase: DialogPhase = DialogPhase.NONE
    var loadingMessage: String = ""

    // ======= Check Steps =======
    enum class CheckStep {
        NOT_STARTED,
        CHECKING_ROOT, CHECKING_DEVICE, CHECKING_APPINSTALLED, CHECKING_SETTINGS,
        DONE
    }
    var currentCheckStep = CheckStep.NOT_STARTED
    var lostRootDetected = false

    // ======= Fragment Dialog States =======
    enum class FragmentDialog {
        NONE,
        DEVICE_INFO,
        GENERAL_INFO, GENERAL_BATT_PROTECT_WARNING, OPTIMIZE_APPS, OPTIMIZING_APPS, OPTIMIZING_CANCELED, APPS_OPTIMIZED,
        ADVANCED_INFO,
        ABOUT_INFO, CHANGELOG, LABS_WARNING, RESET, RESETTING, IMPORT_ERROR, IMPORT_SUCESS, EXPORT_SUCESS
    }
    var fragmentDialog: FragmentDialog = FragmentDialog.NONE
    var fragmentDialogScrollY: Int = 0
    var optimizeCancelBtnEnabled: Boolean = false
    var optimizeCompletedSuccessfully: Boolean? = null
    var optimizeProgress: Int = 0
    var onOptimizeCompleteReady: (() -> Unit)? = null
    var onOptimizeCancelReady: (() -> Unit)? = null

    // ======= Updater Dialog States =======
    enum class UpdaterPhase {
        NONE,
        CHECKING_UPDATE, UPDATE_AVAILABLE, UP_TO_DATE,
        DOWNLOADING, DOWNLOAD_CANCELED, DOWNLOAD_ERROR,
        INSTALL_UPDATE, PENDING_INSTALL_UPDATE, INSTALLING, INSTALL_ERROR,
        CONNECTION_ERROR,
        WHATSNEW
    }
    var updaterPhase: UpdaterPhase = UpdaterPhase.NONE
    var updaterRemoteVersionName: String = ""
    var updaterCurrentVersionName: String = ""
    var updaterChangelog: String = ""
    var updaterDownloadUrl: String = ""
    var updaterDialogScrollY: Int = 0
    var updaterRemoteVersionCode: Long = -1L
    var isCheckingUpdate: Boolean = false
    var downloadCompletedWhileDetached: Boolean = false
    var downloadProgress: Int = 0
    var onUpdateCheckResult: ((PendingUpdateCheckResult) -> Unit)? = null
    var pendingUpdateCheckResult: PendingUpdateCheckResult? = null
    data class PendingUpdateCheckResult(
        val json: JSONObject?,
        val appVersionCode: Long,
        val appVersionName: String,
        val appUpdateUrl: String,
        val silent: Boolean
    )

    // ======= Scroll State =======
    private val scrollPositions = mutableMapOf<String, Int>()
    private val attachedScrollViews = mutableMapOf<String, NestedScrollView>()
    fun attachScroll(key: String, scrollView: NestedScrollView) {
        attachedScrollViews[key] = scrollView
    }
    fun restoreScroll(key: String) {
        val scrollView = attachedScrollViews[key] ?: return
        val savedY = scrollPositions.remove(key) ?: return
        if (savedY == 0) return
        scrollView.postDelayed({ scrollView.scrollTo(0, savedY) }, 300)
    }
    fun saveScroll(key: String) {
        val y = attachedScrollViews[key]?.scrollY ?: return
        scrollPositions[key] = y
    }
    fun detachScroll(key: String, savePosition: Boolean) {
        if (!savePosition) scrollPositions.remove(key)
        attachedScrollViews.remove(key)
    }
}