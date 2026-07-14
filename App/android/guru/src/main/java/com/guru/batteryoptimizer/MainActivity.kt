package com.guru.batteryoptimizer
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.fragments.DeviceStatus
import com.guru.batteryoptimizer.fragments.GeneralSettings
import com.guru.batteryoptimizer.fragments.AdvancedSettings
import com.guru.batteryoptimizer.fragments.AboutGuru
import com.guru.batteryoptimizer.utils.Checks
import com.guru.batteryoptimizer.utils.navigateToSearchResult
import com.guru.batteryoptimizer.utils.restoreSearchModeIfNeeded
import com.guru.batteryoptimizer.utils.SearchableFragment
import com.guru.batteryoptimizer.utils.SearchItem
import com.guru.batteryoptimizer.utils.startSearchModeUI
import com.guru.batteryoptimizer.utils.SearchManager
import com.guru.batteryoptimizer.utils.UpdaterHelper

import android.animation.Animator
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.BottomTabLayout
import dev.oneuiproject.oneui.layout.startSearchMode
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var fragmentDevice:   DeviceStatus
    private lateinit var fragmentGeneral:  GeneralSettings
    private lateinit var fragmentAdvanced: AdvancedSettings
    private lateinit var fragmentAbout:    AboutGuru
    private lateinit var toolbar: ToolbarLayout
    private var keepSplashScreen = true
    private var isTouching = false
    private var toolbarListenerEnabled = true
    lateinit var appState: AppState; private set
    lateinit var checks: Checks
    lateinit var updater: UpdaterHelper
    val toolbarLayout: ToolbarLayout get() = toolbar
    val themeOverlay: View get() = findViewById(R.id.theme_overlay)
    val guru get() = appState.guru
    var isBlocked: Boolean
        get() = appState.isBlocked
        set(value) { appState.isBlocked = value }
    var switchBarShouldBeVisible = false
        set(value) {
            field = value
            toolbarLayout.switchBar.visibility = if (value) View.VISIBLE else View.GONE
        }

    // ======= Lifecycle =======
    override fun onCreate(savedInstanceState: Bundle?) {
        setupSplash()
        super.onCreate(savedInstanceState)
        appState = ViewModelProvider(this)[AppState::class.java]
        if (savedInstanceState != null) { 
            val inDeadZone = appState.loadingStarted && !appState.checksPassed && appState.dialogPhase == AppState.DialogPhase.NONE
            if (inDeadZone) { appState.loadingStarted = false }
            if (!appState.loadingStarted) {
                appState.splashAnimationStarted = false
                appState.splashExited           = true
            } else { appState.splashExited = true }
        }
        val isRecreation = savedInstanceState != null && appState.loadingStarted
        appState.themeOverlayFade?.cancel()
        appState.themeOverlayFade = null
        appState.themeOverlayAnimating = false
        if (appState.checksPassed || appState.currentCheckStep == AppState.CheckStep.DONE) {
            appState.themeOverlayVisible = false
            appState.themeOverlayAlpha = 1f
        } else {
            appState.themeOverlayVisible = true
            appState.themeOverlayAlpha = 1f
        }
        if (isRecreation && (appState.checksPassed || appState.currentCheckStep == AppState.CheckStep.DONE)) {
            installSplashScreen().setKeepOnScreenCondition { false }
        }
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbarLayout)
        updater = UpdaterHelper(this, appState.guru)
        appState.guru.attachActivity(this)
        appState.onOverlayDismiss = {
            themeOverlay.animate().cancel()
            themeOverlay.visibility = View.VISIBLE
            themeOverlay.alpha = 1f
            themeOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    themeOverlay.visibility = View.GONE
                    themeOverlay.alpha = 1f
                    appState.themeOverlayVisible = false
                    appState.themeOverlayAlpha = 1f
                    isBlocked = false
                }
                .start()
        }
        appState.themeOverlayFade?.cancel()
        appState.themeOverlayFade = null
        appState.themeOverlayAnimating = false
        if (appState.checksPassed || appState.currentCheckStep == AppState.CheckStep.DONE) {
            themeOverlay.visibility = View.GONE
            themeOverlay.alpha = 1f
            appState.themeOverlayVisible = false
            appState.themeOverlayAlpha = 1f
        } else {
            themeOverlay.visibility = if (appState.themeOverlayVisible) View.VISIBLE else View.GONE
            themeOverlay.alpha = appState.themeOverlayAlpha
            if (appState.themeOverlayVisible && themeOverlay.alpha == 0f) {
                themeOverlay.alpha = 1f
                appState.themeOverlayAlpha = 1f
            }
        }
        if (!isRecreation) {
            when {
                !appState.loadingStarted -> {
                    appState.themeOverlayVisible = true
                    appState.themeOverlayAlpha = 1f
                    themeOverlay.visibility = View.VISIBLE
                    themeOverlay.alpha = 1f
                }
                !appState.checksPassed -> {
                    appState.themeOverlayVisible = true
                    appState.themeOverlayAlpha = 1f
                    themeOverlay.visibility = View.VISIBLE
                    themeOverlay.alpha = 1f
                }
                else -> {
                    appState.themeOverlayVisible = false
                    appState.themeOverlayAlpha = 1f
                    themeOverlay.visibility = View.GONE
                    themeOverlay.alpha = 1f
                }
            }
        }
        checks = Checks(this, appState.guru) {
            val hadWhatsNew = updater.checkWhatsNew(onDismiss = null)
            if (!hadWhatsNew) {
                updater.checkForUpdatesPostLoading()
            }
        }
        if (SearchManager.isEmpty()) SearchManager.register(listOf(
            // Device Status
            SearchItem(getString(R.string.device_option_show_memory),       0, R.id.show_memory),
            SearchItem(getString(R.string.device_option_usefahrenheit),     0, R.id.switch_fahrenheit_unit),
            SearchItem(getString(R.string.device_batterycap),               0, R.id.battery_cap),
            SearchItem(getString(R.string.device_batteryhealth),            0, R.id.battery_health),
            // General Settings
            SearchItem(getString(R.string.general_option_optimizeapps),     1, R.id.optimizeapps_opt),
            SearchItem(getString(R.string.general_option_battapps),         1, R.id.battapps_opt),
            SearchItem(getString(R.string.general_option_cachedapps),       1, R.id.cachedapps_opt),
            SearchItem(getString(R.string.general_option_cpuhotplug),       1, R.id.cpu_hotplug_opt),
            SearchItem(getString(R.string.general_option_powereffi),        1, R.id.power_effi_opt),
            SearchItem(getString(R.string.general_option_thermal),          1, R.id.thermal_opt),
            SearchItem(getString(R.string.general_option_cpueffiboost),     1, R.id.cpu_effiboost_opt),
            SearchItem(getString(R.string.general_option_gpueffiboost),     1, R.id.gpu_effiboost_opt),
            SearchItem(getString(R.string.general_option_swap),             1, R.id.swap_opt),
            SearchItem(getString(R.string.general_option_storage),          1, R.id.storage_opt),
            SearchItem(getString(R.string.general_option_batteryprotect),   1, R.id.batteryprotect_opt),
            // Advanced Settings
            SearchItem(getString(R.string.advanced_option_use_frq),         2, R.id.switch_freqs),
            SearchItem(getString(R.string.advanced_option_maxlittle),       2, R.id.lit_freq),
            SearchItem(getString(R.string.advanced_option_maxbig),          2, R.id.big_freq),
            SearchItem(getString(R.string.advanced_option_use_uv),          2, R.id.switch_uv),
            SearchItem(getString(R.string.advanced_uv_seekbar_cpulittle),   2, R.id.container_LitVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_cpubig),      2, R.id.container_BigVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_gpu),         2, R.id.container_GpuVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_mif),         2, R.id.container_MifVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_aud),         2, R.id.container_AudVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_cam),         2, R.id.container_CamVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_cp),          2, R.id.container_CpVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_disp),        2, R.id.container_DispVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_fsys),        2, R.id.container_FsysVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_int),         2, R.id.container_IntVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_intcam),      2, R.id.container_IntCamVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_iva),         2, R.id.container_IvaVolt),
            SearchItem(getString(R.string.advanced_uv_seekbar_score),       2, R.id.container_ScoreVolt),
            // About GURU
            SearchItem(getString(R.string.about_github),                    3, R.id.github),
            SearchItem(getString(R.string.about_option_changelog),          3, R.id.changelog),
            SearchItem(getString(R.string.about_option_check_update),       3, R.id.check_update),
            SearchItem(getString(R.string.about_option_import),             3, R.id.import_settings),
            SearchItem(getString(R.string.about_option_export),             3, R.id.export_settings),
            SearchItem(getString(R.string.about_option_limitbackground),    3, R.id.limitbackground_opt),
            SearchItem(getString(R.string.about_option_forcedoze),          3, R.id.forcedoze_opt),
            SearchItem(getString(R.string.about_option_nsd),                3, R.id.nsd_opt),
            SearchItem(getString(R.string.about_button_reset_settings),     3, R.id.reset_settings),
        ))
        toolbar.appBarLayout.addOnOffsetChangedListener { _, offset ->
            if (toolbarListenerEnabled) { appState.toolbarExpanded = (offset == 0) }
        }
        val bottomTab = findViewById<BottomTabLayout>(R.id.bottom_tab)
        bottomTab.show(true)
        bottomTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                appState.selectedTab = tab.position
                when (tab.position) {
                    0 -> navigateTo(fragmentDevice)
                    1 -> navigateTo(fragmentGeneral)
                    2 -> navigateTo(fragmentAdvanced)
                    3 -> navigateTo(fragmentAbout)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        if (isRecreation) {
            appState.toolbarAnimatedTab = appState.selectedTab
            restoreSearchModeIfNeeded()
            val savedExpanded = appState.toolbarExpanded
            toolbarListenerEnabled = false
            toolbar.post {
                toolbar.setExpanded(savedExpanded, false)
                appState.toolbarExpanded = savedExpanded
                toolbarListenerEnabled = true
            }
            bottomTab.post { bottomTab.getTabAt(appState.selectedTab)?.select() }
            checks.reattach()
            updater.reattach()
        }
        val fm = supportFragmentManager
        fragmentDevice   = (fm.findFragmentByTag(DeviceStatus::class.java.simpleName)     as? DeviceStatus)     ?: DeviceStatus()
        fragmentGeneral  = (fm.findFragmentByTag(GeneralSettings::class.java.simpleName)  as? GeneralSettings)  ?: GeneralSettings()
        fragmentAdvanced = (fm.findFragmentByTag(AdvancedSettings::class.java.simpleName) as? AdvancedSettings) ?: AdvancedSettings()
        fragmentAbout    = (fm.findFragmentByTag(AboutGuru::class.java.simpleName)        as? AboutGuru)        ?: AboutGuru()
        val transaction  = fm.beginTransaction().setReorderingAllowed(true)
        if (!fragmentDevice.isAdded)   transaction.add(R.id.nav_host_fragment, fragmentDevice,   DeviceStatus::class.java.simpleName)
        if (!fragmentGeneral.isAdded)  transaction.add(R.id.nav_host_fragment, fragmentGeneral,  GeneralSettings::class.java.simpleName)
        if (!fragmentAdvanced.isAdded) transaction.add(R.id.nav_host_fragment, fragmentAdvanced, AdvancedSettings::class.java.simpleName)
        if (!fragmentAbout.isAdded)    transaction.add(R.id.nav_host_fragment, fragmentAbout,    AboutGuru::class.java.simpleName)
        val all = listOf(fragmentDevice, fragmentGeneral, fragmentAdvanced, fragmentAbout)
        val safeIndex = appState.selectedTab.coerceIn(0, all.lastIndex)
        val activeFragment = all[safeIndex]
        all.forEach {if (it === activeFragment) transaction.show(it) else transaction.hide(it)}
        transaction.commitAllowingStateLoss()
        keepSplashScreen = false
    }
    override fun onResume() {
        super.onResume()
        if (appState.checksPassed) {
            checks.reCheckRoot()
            checks.checkAppInstalled()
        }
        if (appState.loadingStarted) return
        if (appState.splashAnimationStarted && appState.splashExited) {
            startLoading()
            return
        }
        if (appState.splashExited && !appState.splashAnimationStarted) {
            startLoading()
            return
        }
        window.decorView.postDelayed({
            if (!appState.loadingStarted) {
                appState.splashAnimationStarted = true 
                appState.splashExited = true
                startLoading()
            }
        }, 1000)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (appState.checksPassed) {
                checks.reCheckRoot()
                checks.checkAppInstalled()
            }
            else if (appState.checksStarted) isBlocked = false
        } else {
            isBlocked = true
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        appState.onOverlayDismiss = null
        if (isChangingConfigurations) {
            checks.detach()
            updater.detach()
        } else {
            checks.destroy()
            updater.destroy()
            guru.onOptimizeComplete = null
        }
    }

    // ======= Splash =======
    private fun setupSplash() {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        splashScreen.setOnExitAnimationListener { splashView ->
            appState.splashAnimationStarted = true
            val anim = ObjectAnimator.ofFloat(splashView.view, View.ALPHA, 1f, 0f)
            anim.duration = 200
            anim.startDelay = 100
            val onEndOrCancel = {
                runCatching { splashView.remove() }
                if (!isDestroyed && !isFinishing) {
                    window.decorView.post {
                        window.decorView.post {
                            appState.splashExited = true
                            startLoading()
                        }
                    }
                }
            }
            anim.addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) { onEndOrCancel() }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) { onEndOrCancel() }
                override fun onAnimationRepeat(animation: Animator) {}
            })
            anim.start()
        }
    }

    // ======= Loading Trigger =======
    private fun startLoading() {
        if (appState.loadingStarted || !appState.splashExited) return
        appState.loadingStarted = true
        appState.checksStarted  = true
        checks.start()
    }

    // ======= Fragment Animation =======
    fun updateTitle(title: String) { toolbar.setTitle(title, title) }
    fun animateToolbar(showSwitchBar: Boolean, title: String, tabIndex: Int) {
        switchBarShouldBeVisible = showSwitchBar
        if (appState.toolbarAnimatedTab == tabIndex) return
        appState.toolbarAnimatedTab = tabIndex
        val translationPx = 30f * resources.displayMetrics.density
        val activeFragment = when (tabIndex) {
            0 -> fragmentDevice
            1 -> fragmentGeneral
            2 -> fragmentAdvanced
            3 -> fragmentAbout
            else -> null
        }
        val fragmentView = activeFragment?.view
        listOf(fragmentView, toolbarLayout.switchBar).filterNotNull().forEach { v ->
            v.alpha = 0f
            v.translationY = translationPx
            v.animate().alpha(1f).translationY(0f).setDuration(250).setStartDelay(0).setInterpolator(FastOutSlowInInterpolator()).withStartAction{updateTitle(title)}.start()
        }
    }

    // ======= Search Function =======
    fun showSearchDialog() {startSearchModeUI()}

    // ======= Dismiss Dialog (for Root Checker) =======
    fun dismissActiveFragmentDialog(onDismissed: (() -> Unit)? = null): Boolean {
        val tag = when (appState.selectedTab) {
            0 -> DeviceStatus::class.java.simpleName
            1 -> GeneralSettings::class.java.simpleName
            2 -> AdvancedSettings::class.java.simpleName
            3 -> AboutGuru::class.java.simpleName
            else -> return false
        }
        return when (val f = supportFragmentManager.findFragmentByTag(tag)) {
            is DeviceStatus      -> f.dismissActiveDialog(onDismissed)
            is GeneralSettings   -> f.dismissActiveDialog(onDismissed)
            is AdvancedSettings  -> f.dismissActiveDialog(onDismissed)
            is AboutGuru         -> f.dismissActiveDialog(onDismissed)
            else -> false
        }
    }

    // ======= Navbar Behavior =======
    private fun navigateTo(fragment: Fragment) {
        val all = listOf(fragmentDevice, fragmentGeneral, fragmentAdvanced, fragmentAbout)
        supportFragmentManager.executePendingTransactions()
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        all.forEach { if (it !== fragment) transaction.hide(it) }
        transaction.show(fragment).commitNow()
        val title = when (fragment) {
            fragmentDevice   -> getString(R.string.device_toolbartitle)
            fragmentGeneral  -> getString(R.string.general_toolbartitle)
            fragmentAdvanced -> getString(R.string.advanced_toolbartitle)
            fragmentAbout    -> getString(R.string.about_toolbartitle)
            else -> ""
        }
        updateTitle(title)
    }

    // ======= Touch Behavior =======
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (isBlocked) return true
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { isTouching = true }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> { return true }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { isTouching = false }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ======= Scroll Fragment Behavior =======
    fun resetScroll(scrollView: NestedScrollView) {
        try {
            val field = NestedScrollView::class.java.getDeclaredField("mScroller")
            field.isAccessible = true
            val scroller = field.get(scrollView) as android.widget.OverScroller
            scroller.abortAnimation()
        } catch (_: Exception) {}
        val oldDelay = scrollView.scrollBarDefaultDelayBeforeFade
        val oldDuration = scrollView.scrollBarFadeDuration
        scrollView.scrollBarDefaultDelayBeforeFade = 0
        scrollView.scrollBarFadeDuration = 0
        scrollView.scrollTo(0, 0)
        scrollView.post {
            scrollView.scrollBarDefaultDelayBeforeFade = oldDelay
            scrollView.scrollBarFadeDuration = oldDuration
        }
    }
}