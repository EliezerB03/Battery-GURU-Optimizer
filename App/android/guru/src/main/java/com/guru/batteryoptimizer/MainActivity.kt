package com.guru.batteryoptimizer
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.fragments.DeviceStatus
import com.guru.batteryoptimizer.fragments.GeneralSettings
import com.guru.batteryoptimizer.fragments.AdvancedSettings
import com.guru.batteryoptimizer.fragments.AboutGuru
import com.guru.batteryoptimizer.utils.Checks
import com.guru.batteryoptimizer.utils.navigateToSearchResult
import com.guru.batteryoptimizer.utils.restoreSearchModeIfNeeded
import com.guru.batteryoptimizer.utils.SearchItem
import com.guru.batteryoptimizer.utils.startSearchModeUI
import com.guru.batteryoptimizer.utils.SearchManager
import com.guru.batteryoptimizer.utils.UpdaterHelper
import com.guru.batteryoptimizer.widgets.ToolbarController

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnCancel
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.oneui.floatingactioncontainer.FloatingBottomLayout

class MainActivity : AppCompatActivity() {
    // ======= States =======
    private var scrollView: NestedScrollView? = null
    private var fadeScrollView: NestedScrollView? = null
    private var pinnedResults: View? = null
    private var pinnedStartBottom = 0
    private var keepSplashScreen = true
    private var toolbarListenerEnabled = true
    private var ignoreGesture = false
    private var fadePreTop = 0f
    private var fadePreBottom = 0f
    private lateinit var toolbar: ToolbarController
    private lateinit var floatingBottom: FloatingBottomLayout
    private lateinit var fragmentDevice: DeviceStatus
    private lateinit var fragmentGeneral: GeneralSettings
    private lateinit var fragmentAdvanced: AdvancedSettings
    private lateinit var fragmentAbout: AboutGuru
    private lateinit var fadeTop: View
    private lateinit var fadeBottom: View
    var switchBarShouldBeVisible = false
    var holdSearchFade = false
    var searchRestoreHidden = false
    var isBlocked: Boolean
        get() = appState.isBlocked
        set(value) { appState.isBlocked = value }
    lateinit var appState: AppState; private set
    lateinit var checks: Checks
    lateinit var updater: UpdaterHelper
    private val pendingScrollResets = HashMap<NestedScrollView, ViewTreeObserver.OnPreDrawListener>()
    private val fadeUpdater = ViewTreeObserver.OnPreDrawListener { updateFades(); true }
    private val pinListener = AppBarLayout.OnOffsetChangedListener { appBar, _ -> pinnedResults?.translationY = (pinnedStartBottom - appBar.bottom).toFloat() }
    private val fragments: List<Fragment> get() = listOf(fragmentDevice, fragmentGeneral, fragmentAdvanced, fragmentAbout)
    val toolbarLayout: ToolbarController get() = toolbar
    val themeOverlay: View get() = findViewById(R.id.theme_overlay)
    val guru get() = appState.guru
    private inline fun <reified T : Fragment> findOrCreate(create: () -> T): T =
        supportFragmentManager.findFragmentByTag(T::class.java.simpleName) as? T ?: create()
    private fun View.onNextPreDraw(block: () -> Boolean) = viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean { if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this); return block() }
    })

    // ======= Lifecycle =======
    override fun onCreate(savedInstanceState: Bundle?) {
        setupSplash()
        super.onCreate(savedInstanceState)
        appState = ViewModelProvider(this)[AppState::class.java]
        if (savedInstanceState != null) {
            val inDeadZone = appState.loadingStarted && !appState.checksPassed && appState.dialogPhase == AppState.DialogPhase.NONE
            if (inDeadZone) appState.loadingStarted = false
            if (!appState.loadingStarted) appState.splashAnimationStarted = false
            appState.splashExited = true
        }
        val isRecreation = savedInstanceState != null && appState.loadingStarted
        appState.themeOverlayFade?.cancel()
        appState.themeOverlayFade = null
        appState.themeOverlayAnimating = false
        if (isRecreation && (appState.checksPassed || appState.currentCheckStep == AppState.CheckStep.DONE)) {installSplashScreen().setKeepOnScreenCondition {false}}
        setContentView(R.layout.activity_main)
        floatingBottom = findViewById(R.id.sesl_floating_bottom_layout)
        fadeTop    = findViewById(R.id.fade_top)
        fadeBottom = findViewById(R.id.fade_bottom)
        setupFades()
        findViewById<View>(R.id.root_layout).viewTreeObserver.addOnPreDrawListener(fadeUpdater)
        toolbar = ToolbarController(
            this, findViewById(R.id.app_bar), findViewById(R.id.collapsing_toolbar), findViewById(R.id.sesl_floating_toolbar_layout),
            findViewById(R.id.toolbar), findViewById(R.id.search_view)
        )
        setSupportActionBar(toolbar.toolbar)
        toolbar.onImeClosing = { ime -> collapseIfNoRangeAfterIme(ime) }
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
        setupInsets()
        updateStatusBarForOrientation()
        updater = UpdaterHelper(this, appState.guru)
        updater.onWidgetStateChangedGlobal = { state -> updateAboutBadge(state) }
        updateAboutBadge(appState.updaterWidgetState)
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
        val overlayVisible = if (isRecreation) !(appState.checksPassed || appState.currentCheckStep == AppState.CheckStep.DONE)
                            else (!appState.loadingStarted || !appState.checksPassed)
        appState.themeOverlayVisible = overlayVisible
        appState.themeOverlayAlpha = 1f
        themeOverlay.visibility = if (overlayVisible) View.VISIBLE else View.GONE
        themeOverlay.alpha = 1f
        checks = Checks(this, appState.guru) {if (!updater.checkWhatsNew(onDismiss = null)) updater.checkForUpdatesPostLoading()}
        registerSearchItems()
        toolbar.appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBar, offset ->
            if (toolbarListenerEnabled) {
                appState.toolbarExpanded = (offset == 0)
                val visibleHeight = appBar.height + offset
                appState.toolbarHideDepth = (appBar.seslGetCollapsedHeight().toInt() - visibleHeight).coerceAtLeast(0)
            }
        })
        val bottomTab = findViewById<TabLayout>(R.id.bottom_tab)
        applyTabTint(bottomTab)
        bottomTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { onBottomTabChosen(tab.position) }
            override fun onTabReselected(tab: TabLayout.Tab) { onBottomTabChosen(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
        })
        if (isRecreation) {
            appState.toolbarAnimatedTab = appState.selectedTab
            restoreSearchModeIfNeeded()
            val savedExpanded = appState.toolbarExpanded
            val savedHideDepth = appState.toolbarHideDepth
            toolbarListenerEnabled = false
            if (savedHideDepth > 0) { setToolbarMasked(false) }
            toolbar.post {
                if (savedHideDepth > 0) {
                    restoreToolbarHide(savedHideDepth) {
                        appState.toolbarExpanded = false
                        appState.toolbarHideDepth = savedHideDepth
                        toolbarListenerEnabled = true
                    }
                } else {
                    toolbar.setExpanded(savedExpanded, false)
                    appState.toolbarExpanded = savedExpanded
                    toolbarListenerEnabled = true
                }
            }
            bottomTab.post { selectTab(appState.selectedTab) }
            checks.reattach()
            updater.reattach()
        }
        fragmentDevice   = findOrCreate { DeviceStatus() }
        fragmentGeneral  = findOrCreate { GeneralSettings() }
        fragmentAdvanced = findOrCreate { AdvancedSettings() }
        fragmentAbout    = findOrCreate { AboutGuru() }
        val transaction  = supportFragmentManager.beginTransaction().setReorderingAllowed(true).setCustomAnimations(R.animator.no_anim, R.animator.no_anim)
        fragments.forEach { if (!it.isAdded) transaction.add(R.id.nav_host_fragment, it, it.javaClass.simpleName) }
        val activeFragment = fragments[appState.selectedTab.coerceIn(0, fragments.lastIndex)]
        fragments.forEach { if (it === activeFragment) transaction.show(it) else transaction.hide(it) }
        transaction.commitAllowingStateLoss()
        window.decorView.post { attachScrollable(activeFragment) }
        if (!isRecreation) bottomTab.post { selectTab(appState.selectedTab) }
        keepSplashScreen = false
    }
    override fun onResume() {
        super.onResume()
        if (appState.checksPassed) {
            checks.reCheckRoot()
            checks.checkAppInstalled()
        }
        if (appState.loadingStarted) return
        if (appState.splashExited) {
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
        } else { isBlocked = true }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateStatusBarForOrientation()
    }
    override fun onDestroy() {
        if (isChangingConfigurations && appState.isSearchModeActive) appState.saveScroll("search")
        appState.detachScroll("search", isChangingConfigurations)
        super.onDestroy()
        val rootVto = findViewById<View>(R.id.root_layout).viewTreeObserver
        if (rootVto.isAlive) { rootVto.removeOnPreDrawListener(fadeUpdater) }
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

    // ======= Splash / Loading =======
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
            anim.doOnEnd { onEndOrCancel() }
            anim.doOnCancel { onEndOrCancel() }
            anim.start()
        }
    }
    private fun startLoading() {
        if (appState.loadingStarted || !appState.splashExited) return
        appState.loadingStarted = true
        appState.checksStarted  = true
        checks.start()
    }

    // ======= Insets / Touch =======
    private fun setupInsets() {
        val root = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val i          = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val shadow     = resources.getDimensionPixelSize(com.google.android.material.R.dimen.sesl_floating_toolbar_projection_background_elevation)
            val stable     = stableSystemInsets(i.top, i.bottom)
            val fadeScale  = 1f
            val topBase    = fadeDimenPx("sesl_fading_edge_top_height", 48f)
            val topStatus  = fadeDimenPx("sesl_fading_edge_on_status_bar_top_height", 80f)
            val botBase    = fadeDimenPx("sesl_fading_edge_bottom_height", 40f)
            val botNavi    = fadeDimenPx("sesl_fading_edge_on_navi_bar_bottom_height", 92f)
            val fullTop    = ((if (stable.first > 0)  maxOf(stable.first+topBase, topStatus) else topBase)*fadeScale).toInt()
            val fullBottom = ((if (stable.second > 0) maxOf(stable.second+botBase, botNavi)  else botBase)*fadeScale).toInt()
            setFadeHeights(top = (fullTop-(stable.first -i.top)).coerceAtLeast(0), bottom = (fullBottom -(stable.second-i.bottom)).coerceAtLeast(0))
            view.updatePadding(left = i.left, right = i.right)
            toolbar.applyInsets(i.top)
            floatingBottom.customPadding = Rect(0, shadow, 0, i.bottom + shadow * 2)
            floatingBottom.setWindowBottomInset(i.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun stableSystemInsets(curTop: Int, curBottom: Int): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT < 33) return curTop to curBottom
        val types = android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
        val max = windowManager.maximumWindowMetrics.windowInsets.getInsets(types)
        return maxOf(curTop, max.top) to maxOf(curBottom, max.bottom)
    }
    private fun updateStatusBarForOrientation() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isBlocked || ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) return true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val navInsets = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.systemGestures())
            val bottom = navInsets?.bottom ?: 0
            ignoreGesture = bottom > 0 && ev.y >= window.decorView.height - bottom
        }
        return ignoreGesture || super.dispatchTouchEvent(ev)
    }

    // ======= Top Toolbar =======
    fun updateTitle(title: String) { toolbar.setTitle(title, title) }
    private fun setToolbarMasked(masked: Boolean) {
        toolbar.appBarLayout.alpha = if (masked) 0f else 1f
        toolbar.floatingToolbar.visibility = if (masked) View.INVISIBLE else View.VISIBLE
    }
    private fun restoreToolbarHide(depth: Int, onDone: () -> Unit) {
        val abl = toolbar.appBarLayout
        val parent = abl.parent as? CoordinatorLayout
        val behavior = (abl.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? AppBarLayout.Behavior
        val target = findViewById<View>(R.id.nav_host_fragment)
        toolbar.setExpanded(false, false)
        if (parent == null || behavior == null || target == null) { setToolbarMasked(false); onDone(); return }
        abl.onNextPreDraw {
            val consumed = IntArray(2)
            behavior.onStartNestedScroll(parent, abl, target, target, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            behavior.onNestedPreScroll(parent, abl, target, 0, depth, consumed, ViewCompat.TYPE_TOUCH)
            behavior.onStopNestedScroll(parent, abl, target, ViewCompat.TYPE_TOUCH)
            onDone()
            abl.onNextPreDraw { setToolbarMasked(false); true }
            false
        }
    }

    // ======= Bottom Navbar =======
    private fun onBottomTabChosen(position: Int) {
        appState.selectedTab = position
        fragments.getOrNull(position)?.let { navigateTo(it) }
    }
    private fun updateAboutBadge(state: AppState.UpdaterWidgetState) {
        val showDot = state == AppState.UpdaterWidgetState.UPDATE_AVAILABLE || state == AppState.UpdaterWidgetState.INSTALL_UPDATE
        val tabs = findViewById<TabLayout>(R.id.bottom_tab)
        if (tabs.tabCount > 3) tabs.seslShowDotBadge(3, showDot)
    }
    private fun applyTabTint(bottomTab: TabLayout) {
        val selectedColor = ContextCompat.getColor(this, R.color.app_label_text_color)
        val normalId = resources.getIdentifier("sesl_list_subheader_text_color", "color", packageName)
        val normalColor = ContextCompat.getColor(this, normalId)
        val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(selectedColor, normalColor))
        bottomTab.tabIconTint = tint
        bottomTab.setTabTextColors(tint)
    }
    fun selectTab(index: Int) {
        val bottomTab = findViewById<TabLayout>(R.id.bottom_tab)
        applyTabTint(bottomTab)
        if (index in 0 until bottomTab.tabCount) bottomTab.getTabAt(index)?.select()
    }

    // ======= Fragments =======
    private fun navigateTo(fragment: Fragment) {
        supportFragmentManager.executePendingTransactions()
        val isRealSwitch = fragments.any { it !== fragment && it.isAdded && !it.isHidden }
        val toolbarWasHidden = isRealSwitch && toolbar.isHidden
        val previousScroll = scrollView
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true).setCustomAnimations(R.animator.no_anim, R.animator.no_anim)
        fragments.forEach { if (it !== fragment) transaction.hide(it) }
        transaction.show(fragment).commitNow()
        attachScrollable(fragment)
        if (toolbarWasHidden) {runWhenHidden(previousScroll) { toolbar.collapseFromHide(animate = false) }}
        updateTitle(intArrayOf(R.string.device_toolbartitle, R.string.general_toolbartitle, R.string.advanced_toolbartitle, R.string.about_toolbartitle)
            .getOrNull(fragments.indexOf(fragment))?.let { getString(it) } ?: "")
    }
    private fun runWhenHidden(view: View?, action: () -> Unit) {
        if (view == null || !view.isShown) { action(); return }
        val vto = window.decorView.viewTreeObserver
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            private var done = false
            override fun onPreDraw(): Boolean {
                if (done || view.isShown) return true
                done = true
                if (vto.isAlive) vto.removeOnPreDrawListener(this)
                action()
                return true
            }
        })
    }
    fun animateToolbar(showSwitchBar: Boolean, title: String, tabIndex: Int) {
        switchBarShouldBeVisible = showSwitchBar
        if (appState.toolbarAnimatedTab == tabIndex) return
        appState.toolbarAnimatedTab = tabIndex
        val translationPx = 30f * resources.displayMetrics.density
        fragments.getOrNull(tabIndex)?.view?.let { v ->
            v.alpha = 0f
            v.translationY = translationPx
            v.animate().alpha(1f).translationY(0f).setDuration(250).setStartDelay(0).setInterpolator(FastOutSlowInInterpolator()).withStartAction{updateTitle(title)}.start()
        }
    }
    fun dismissActiveFragmentDialog(onDismissed: (() -> Unit)? = null): Boolean = when (val f = fragments.getOrNull(appState.selectedTab)) {
        is DeviceStatus     -> f.dismissActiveDialog(onDismissed)
        is GeneralSettings  -> f.dismissActiveDialog(onDismissed)
        is AdvancedSettings -> f.dismissActiveDialog(onDismissed)
        is AboutGuru        -> f.dismissActiveDialog(onDismissed)
        else -> false
    }

    // ======= Scroll Fragment Behavior =======
    private fun attachScrollable(fragment: Fragment) {
        val scroll = fragment.view?.findViewById<NestedScrollView>(R.id.scroll_view) ?: return
        floatingBottom.setNestedScrollView(scroll)
        toolbar.floatingToolbar.setNestedScrollView(scroll)
        scroll.seslSetFadingEdgeEnabled(false)
        scrollView = scroll
    }
    fun reattachScrollables() {
        val s = scrollView ?: return
        val ft = toolbar.floatingToolbar
        ft.setNestedScrollView(s)
        if (ft.alpha < 1f) ft.setLayoutVisibility(true, animation = true, force = true)
    }
    fun resetScroll(scrollView: NestedScrollView) {
        pendingScrollResets.remove(scrollView)?.let { old -> val vto = scrollView.viewTreeObserver
            if (vto.isAlive) vto.removeOnPreDrawListener(old)
        }
        fun ownerIsHidden() = supportFragmentManager.fragments.any { f -> f.isHidden && f.view?.findViewById<View>(R.id.scroll_view) === scrollView}
        if (!ownerIsHidden() || !scrollView.isShown) {
            applyScrollReset(scrollView)
            return
        }
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (ownerIsHidden() && scrollView.isShown) return true
                val vto = scrollView.viewTreeObserver
                if (vto.isAlive) vto.removeOnPreDrawListener(this)
                pendingScrollResets.remove(scrollView)
                if (ownerIsHidden()) applyScrollReset(scrollView)
                return true
            }
        }
        pendingScrollResets[scrollView] = listener
        scrollView.viewTreeObserver.addOnPreDrawListener(listener)
    }
    private fun applyScrollReset(scrollView: NestedScrollView) {
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

    // ======= Fade Top / Bottom / Tail =======
    private fun fadeDimenPx(name: String, fallbackDp: Float): Int {
        val id = resources.getIdentifier(name, "dimen", packageName)
        return if (id != 0) resources.getDimensionPixelSize(id)
        else (fallbackDp * resources.displayMetrics.density).toInt()
    }
    private fun setupFades() {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, tv, true)
        val solid = tv.data
        val clear = solid and 0x00FFFFFF
        fadeTop.background    = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(solid, clear))
        fadeBottom.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(solid, clear))
    }
    private fun View.setHeight(h: Int) { if (layoutParams.height != h) { layoutParams.height = h; requestLayout() } }
    private fun setFadeHeights(top: Int, bottom: Int) { fadeTop.setHeight(top); fadeBottom.setHeight(bottom) }
    private fun updateFades() {
        updateSearchHideLock()
        updateTail()
        val target = if (appState.isSearchModeActive) findSearchResultsScroll() else scrollView
        val current = fadeScrollView
        if (current !== target && (current == null || !current.isShown)) fadeScrollView = target
        val scroll = fadeScrollView
        val child  = scroll?.getChildAt(0)
        if (scroll == null || child == null || !scroll.isAttachedToWindow) {
            fadeTop.alpha = 0f
            fadeBottom.alpha = 0f
            return
        }
        val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
        val range    = child.height - viewport
        val y        = scroll.scrollY
        val ramp     = 15f * resources.displayMetrics.density
        val abl      = toolbar.appBarLayout
        val hideDepth = (abl.seslGetCollapsedHeight() - abl.bottom).coerceAtLeast(0f)
        val hideTop  = if (range > 0) (hideDepth / ramp).coerceIn(0f, 1f) else 0f
        val top      = maxOf(if (range > 0) (y / ramp).coerceIn(0f, 1f) else 0f, hideTop, fadePreTop)
        val bottom   = maxOf(if (range > 0) ((range - y) / ramp).coerceIn(0f, 1f) else 0f, fadePreBottom)
        if (fadeTop.alpha != top) fadeTop.alpha = top
        if (fadeBottom.alpha != bottom) fadeBottom.alpha = bottom
    }
    fun preShowFades(top: Float, bottom: Float) {
        fadePreTop = top
        fadePreBottom = bottom
        updateFades()
    }
    fun clearPreFades() {
        fadePreTop = 0f
        fadePreBottom = 0f
        updateFades()
    }
    private fun updateTail() {
        applyTail(if (appState.isSearchModeActive) findSearchResultsScroll() else null, R.id.search_tail_space, findViewById(R.id.search_floating_bottom_layout))
        applyTail(scrollView, R.id.fragment_tail_space, floatingBottom)
    }
    private fun applyTail(s: NestedScrollView?, tailId: Int, bar: View) {
        if (s == null || !s.isShown || s.height <= 0 || bar.visibility == View.GONE || bar.height <= 0) return
        val tail = s.findViewById<View>(tailId) ?: return
        val loc = IntArray(2)
        s.getLocationInWindow(loc)
        val parentShift = (s.parent as? View)?.translationY ?: 0f
        val scrollBottom = loc[1] - s.translationY.toInt() - parentShift.toInt() + s.height
        bar.getLocationInWindow(loc)
        val pillTop = loc[1] + bar.paddingTop
        val gap = (23f * resources.displayMetrics.density).toInt()
        val target = (scrollBottom - pillTop + gap).coerceAtLeast(0)
        val lp = tail.layoutParams
        if (lp.height != target) {
            lp.height = target
            tail.layoutParams = lp
        }
    }

    // ======= Search Mode =======
    private fun registerSearchItems() = SearchManager.register(listOf(
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
        SearchItem(getString(R.string.about_githubsummary),             3, R.id.github),
        SearchItem(getString(R.string.about_developersummary),          3, R.id.developer),
        SearchItem(getString(R.string.about_option_changelog),          3, R.id.changelog),
        SearchItem(getString(R.string.about_option_check_update),       3, R.id.check_update),
        SearchItem(getString(R.string.about_option_import),             3, R.id.import_settings),
        SearchItem(getString(R.string.about_option_export),             3, R.id.export_settings),
        SearchItem(getString(R.string.about_option_limitbackground),    3, R.id.limitbackground_opt),
        SearchItem(getString(R.string.about_option_forcedoze),          3, R.id.forcedoze_opt),
        SearchItem(getString(R.string.about_option_nsd),                3, R.id.nsd_opt),
        SearchItem(getString(R.string.about_button_reset_settings),     3, R.id.reset_settings),
    ))
    fun showSearchDialog() {startSearchModeUI()}
    fun pinSearchResults() {
        unpinSearchResults()
        val v = findSearchResultsScroll() ?: return
        pinnedResults = v
        pinnedStartBottom = toolbar.appBarLayout.bottom
        toolbar.appBarLayout.addOnOffsetChangedListener(pinListener)
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                if (pinnedResults === view) unpinSearchResults()
            }
        })
        v.postDelayed({ if (pinnedResults === v) unpinSearchResults() }, 1500)
    }
    private fun unpinSearchResults() {
        toolbar.appBarLayout.removeOnOffsetChangedListener(pinListener)
        pinnedResults?.translationY = 0f
        pinnedResults = null
    }
    private fun updateSearchHideLock() {
        if (!appState.isSearchModeActive) {
            toolbar.setNoScrollRangeLock(false)
            return
        }
        if (toolbar.appBarLayout.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE)) return
        val s = findSearchResultsScroll()
        val c = s?.getChildAt(0)
        if (s == null || c == null || !s.isAttachedToWindow || s.height <= 0 || c.height <= 0) return
        val range = c.height - (s.height - s.paddingTop - s.paddingBottom)
        toolbar.setNoScrollRangeLock(range <= 0)
    }
    private fun collapseIfNoRangeAfterIme(ime: Int) {
        if (isDestroyed || !appState.isSearchModeActive) return
        if (!toolbar.appBarLayout.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE)) return
        val s = findSearchResultsScroll() ?: return
        val c = s.getChildAt(0) ?: return
        if (!s.isAttachedToWindow || s.height <= 0 || c.height <= 0) return
        val range = c.height - (s.height + ime - s.paddingTop - s.paddingBottom)
        if (range <= 0) toolbar.setNoScrollRangeLock(true, animate = true)
    }
    private fun findSearchResultsScroll(): NestedScrollView? =
        (fragments.getOrNull(appState.selectedTab)?.view as? ViewGroup)?.children?.firstOrNull { it is NestedScrollView && it.id != R.id.scroll_view } as? NestedScrollView
}