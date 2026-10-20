package com.guru.batteryoptimizer.widgets
import com.guru.batteryoptimizer.R

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.oneui.floatingactioncontainer.FloatingBottomLayout
import com.google.android.material.oneui.floatingactioncontainer.FloatingToolbarLayout

class ToolbarController(
    private val activity: AppCompatActivity, val appBarLayout: AppBarLayout,
    private val collapsingToolbar: CollapsingToolbarLayout, val floatingToolbar: FloatingToolbarLayout,
    val toolbar: Toolbar, private val searchView: SearchView
) { enum class SearchModeOnBackBehavior { DISMISS, CLEAR_DISMISS }

    // ======= States =======
    private var backBehavior = SearchModeOnBackBehavior.DISMISS
    private var onEndCallback: ((SearchView) -> Unit)? = null
    private var wasExpanded = false
    private var wasHidden = false
    private var exiting = false
    private var backButtonWaitsCollapsed = false
    private var backButtonRemoved = false
    private var titleRestorePending = false
    private var cachedExpandedTitle: CharSequence? = null
    private var cachedCollapsedTitle: CharSequence? = null
    private var collapseLocked = false
    private var noRangeLocked = false
    private var hideLockMode = 0
    private var hideMenuInSearch = true
    private var searchNavInset = -1
    private var lastImeInset = -1
    private var imeAnimating = false
    private var imeClosingFired = false
    private var prevIme = 0
    var holdTitleRestore = false
    var enterSwapDelayMs = 125L
    var exitSwapDelayMs = 125L
    var onImeClosing: ((Int) -> Unit)? = null
    var isSearchMode: Boolean = false
        private set
    private val tabContainer: View? by lazy { activity.findViewById(R.id.sesl_floating_bottom_layout) }
    private val searchContainer: FloatingBottomLayout by lazy { activity.findViewById(R.id.search_floating_bottom_layout) }
    private val navHost: View by lazy { activity.findViewById(R.id.nav_host_fragment) }
    private val searchTextSizeSp = 15f
    private val searchFadeDuration = 250L
    private val searchFadeInterpolator = PathInterpolator(0.33f, 0f, 0.67f, 1f)

    // ======= Callbacks / Guards =======
    private val dragCallback = object : AppBarLayout.Behavior.DragCallback() {override fun canDrag(appBarLayout: AppBarLayout): Boolean = !collapseLocked}
    private val applySearchTitleRunnable = Runnable { runAtomic { applySearchTitleNow() } }
    private val restoreTitleRunnable = Runnable { runAtomic { restoreTitleNow() } }
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (backBehavior == SearchModeOnBackBehavior.CLEAR_DISMISS && searchView.query.isNotEmpty()) {searchView.setQuery("", false)} else {endSearchMode()}
        }
    }
    private val titleHideGuard = ViewTreeObserver.OnPreDrawListener {
        val tolerance = 2f * activity.resources.displayMetrics.density
        var redraw = false
        if (appBarLayout.seslGetCollapsedHeight() - appBarLayout.bottom > tolerance) {
            toolbar.seslSetTitleAlpha(0f)
            if (titleRestorePending && !backButtonRemoved) {
                backButtonRemoved = true
                activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
                redraw = true
            }
        }
        if (backButtonWaitsCollapsed && isSearchMode && appBarLayout.seslGetCurrentAppBarState() == AppBarLayout.SESL_STATE_COLLAPSED) {
            backButtonWaitsCollapsed = false
            setSearchBackButtonVisible(true)
            redraw = true
        }
        !redraw
    }
    private val searchMenuHideGuard = ViewTreeObserver.OnPreDrawListener {
        if (isSearchMode && hideMenuInSearch) setToolbarMenuVisible(false)
        true
    }
    private val searchTextSizeGuard = ViewTreeObserver.OnPreDrawListener {
        val searchText = searchView.seslGetAutoCompleteView()
        val target = searchTextSizePx()
        if (Math.abs(searchText.textSize - target) > 0.5f) {
            searchText.setTextSize(TypedValue.COMPLEX_UNIT_PX, target)
            return@OnPreDrawListener false
        }
        true
    }
    private val imeBackGuard = ViewTreeObserver.OnPreDrawListener {
        updateBackCallbackState()
        syncSearchInsets()
        true
    }
    private val imeAnimationCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        override fun onPrepare(animation: WindowInsetsAnimationCompat) {
            if (isSearchMode && (animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                imeAnimating = true
                imeClosingFired = false
                prevIme = ViewCompat.getRootWindowInsets(searchView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            }
        }
        override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
            if (isSearchMode && imeAnimating) {
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                lastImeInset = ime
                applyImeInset(ime)
                if (ime < prevIme && !imeClosingFired) {
                    imeClosingFired = true
                    onImeClosing?.invoke(ime)
                }
                prevIme = ime
            }
            return insets
        }
        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                imeAnimating = false
                lastImeInset = -1
                syncSearchInsets()
            }
        }
    }

    // ======= Init Toolbar =======
    init {
        appBarLayout.setBackgroundColor(Color.TRANSPARENT)
        collapsingToolbar.setExpandedTitleColor(ContextCompat.getColor(activity, R.color.app_label_text_color))
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        toolbar.viewTreeObserver.addOnPreDrawListener(titleHideGuard)
        toolbar.viewTreeObserver.addOnPreDrawListener(searchMenuHideGuard)
        toolbar.keepGuardsAttached(titleHideGuard, searchMenuHideGuard)
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        activity.findViewById<View>(R.id.root_layout)?.let { ViewCompat.setWindowInsetsAnimationCallback(it, imeAnimationCallback) }
        searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)?.let { plate ->
            plate.setBackgroundResource(searchContainer.projectionView.prjBgStartFirstView.defaultBgId)
            val platePaddingStart = plate.paddingStart
            val platePaddingEnd = plate.paddingEnd
            searchView.seslSetSearchMagIconVisibility(View.GONE)
            searchView.findViewById<View>(androidx.appcompat.R.id.search_mag_icon)?.let { mag ->
                mag.visibility = View.VISIBLE
                mag.setOnClickListener(null)
                mag.isClickable = false
                mag.isFocusable = false
                mag.isFocusableInTouchMode = false
                mag.background = null
                ViewCompat.setTooltipText(mag, null)
                mag.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                (mag.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.marginEnd += (2f * activity.resources.displayMetrics.density).toInt()
                    mag.layoutParams = it
                }
            }
            plate.setPaddingRelative(platePaddingStart, plate.paddingTop, platePaddingEnd, plate.paddingBottom)
        }
        searchView.elevation = activity.resources.getDimension(com.google.android.material.R.dimen.sesl_floating_toolbar_projection_background_elevation)
        searchView.clipChildren = false
        searchView.clipToPadding = false
        searchView.findViewById<ViewGroup>(androidx.appcompat.R.id.search_bar)?.let {
            it.clipChildren = false
            it.clipToPadding = false
        }
        searchView.seslSetNotCallShowSoftInput(true)
        searchContainer.enableScrollTransition = true
        val searchText = searchView.seslGetAutoCompleteView()
        searchText.setTextSize(TypedValue.COMPLEX_UNIT_PX, searchTextSizePx())
        searchText.viewTreeObserver.addOnPreDrawListener(searchTextSizeGuard)
        searchText.keepGuardsAttached(searchTextSizeGuard)
        floatingToolbar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) floatingToolbar.post { updateToolbarFloor() }
        }
    }

    // ======= AppBar State / Lock Behavior =======
    val isExpanded: Boolean get() = !appBarLayout.seslIsCollapsed()
    val restoresHidden: Boolean get() = wasHidden && !wasExpanded
    val isHidden: Boolean
        get() = appBarLayout.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE) || appBarLayout.seslGetCollapsedHeight() - appBarLayout.bottom > 2f * activity.resources.displayMetrics.density
    fun setExpanded(expanded: Boolean, animate: Boolean) {appBarLayout.setExpanded(expanded, animate)}
    fun collapseFromHide(animate: Boolean) { setExpanded(false, animate) }
    fun post(action: () -> Unit) { appBarLayout.post { action() } }
    fun restoreHide(scrollView: NestedScrollView, depth: Int) {
        if (depth <= 0) return
        val consumed = IntArray(2)
        if (scrollView.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)) {
            scrollView.dispatchNestedPreScroll(0, depth, consumed, null, ViewCompat.TYPE_TOUCH)
            scrollView.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        }
    }
    fun setNoScrollRangeLock(lock: Boolean, animate: Boolean = false) {
        if (noRangeLocked == lock) return
        noRangeLocked = lock
        refreshHideLock()
        if (lock && isSearchMode && appBarLayout.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE)) {appBarLayout.setExpanded(false, animate)}
    }
    fun setCollapsedLock(locked: Boolean, collapse: Boolean = true) {
        if (locked && !isSearchMode) return
        collapseLocked = locked
        refreshHideLock()
        if (locked) {
            installDragCallback()
            if (collapse && appBarLayout.seslGetCurrentAppBarState() != AppBarLayout.SESL_STATE_COLLAPSED) {appBarLayout.setExpanded(false, false)}
        }
    }
    private fun refreshHideLock() {
        val mode = when {
            !isSearchMode -> 0
            collapseLocked || noRangeLocked -> 1
            else -> 2
        }
        if (mode == hideLockMode) return
        hideLockMode = mode
        when (mode) {
            1 -> appBarLayout.seslSetAllowStateToHide(false)
            2 -> appBarLayout.seslSetAllowStateToHide(true, true)
            else -> appBarLayout.seslSetAllowStateToHide(true)
        }
    }
    private fun installDragCallback() {
        val lp = appBarLayout.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        (lp.behavior as? AppBarLayout.Behavior)?.setDragCallback(dragCallback)
    }

    // ======= Title / Back Button / Menu Behavior =======
    fun setTitle(expandedTitle: CharSequence?, collapsedTitle: CharSequence?) {
        cachedExpandedTitle = expandedTitle
        cachedCollapsedTitle = collapsedTitle
        if (isSearchMode || titleRestorePending) return
        collapsingToolbar.title = expandedTitle
        toolbar.title = collapsedTitle
    }
    fun releaseTitleRestore() {
        if (!titleRestorePending) return
        appBarLayout.removeCallbacks(restoreTitleRunnable)
        restoreTitleRunnable.run()
    }
    private fun applySearchTitleNow() {
        if (!isSearchMode) return
        val searchTitle = activity.getString(R.string.search_btn)
        collapsingToolbar.title = searchTitle
        toolbar.title = searchTitle
        if (!backButtonWaitsCollapsed) setSearchBackButtonVisible(true)
    }
    private fun restoreTitleNow() {
        if (isSearchMode || !titleRestorePending) return
        titleRestorePending = false
        holdTitleRestore = false
        collapsingToolbar.title = cachedExpandedTitle
        toolbar.title = cachedCollapsedTitle
        setSearchBackButtonVisible(false)
        setToolbarMenuVisible(true)
    }
    private fun setBackButtonInteractive(interactive: Boolean) {
        val nav = toolbar.children.filterIsInstance<ImageButton>().firstOrNull() ?: return
        if (interactive) nav.apply { setOnHoverListener(null); isFocusable = true; isClickable = true }
        else {
            toolbar.setNavigationOnClickListener(null)
            nav.apply {
                isClickable = false; isFocusable = false; isFocusableInTouchMode = false; isHovered = false
                clearFocus(); setOnHoverListener { _, _ -> true }
            }
        }
    }
    private fun setSearchBackButtonVisible(visible: Boolean) {
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(visible)
        if (visible) {
            hideMenuInSearch = true
            setToolbarMenuVisible(false)
            toolbar.setNavigationOnClickListener { endSearchMode() }
            setBackButtonInteractive(true)
        }
    }
    private fun setToolbarMenuVisible(visible: Boolean) {
        val target = if (visible) View.VISIBLE else View.INVISIBLE
        toolbar.children.filterIsInstance<ActionMenuView>().forEach { if (it.visibility != target) it.visibility = target }
    }
    private fun updateBackCallbackState() {
        val imeVisible = ViewCompat.getRootWindowInsets(searchView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val enabled = isSearchMode && !imeVisible
        if (backCallback.isEnabled != enabled) backCallback.isEnabled = enabled
    }

    // ======= Insets =======
    fun applyInsets(top: Int) {
        val collapsedHeight = appBarLayout.seslGetDefaultCollapsedHeight() + top
        if (appBarLayout.seslGetCollapsedHeight() != collapsedHeight) {
            appBarLayout.seslSetCollapsedHeight(collapsedHeight)
            appBarLayout.requestLayout()
        }
        appBarLayout.seslSetProportionExtraHeight(top)
        floatingToolbar.updatePadding(top = top)
        floatingToolbar.post { updateToolbarFloor() }
    }
    private fun updateToolbarFloor() {
        if (floatingToolbar.height == 0) return
        val lp = floatingToolbar.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val collapsed = appBarLayout.seslGetCollapsedHeight().toInt()
        val floor = (collapsed - floatingToolbar.height).coerceAtLeast(0)
        if (lp.topMargin != floor) {
            lp.topMargin = floor
            floatingToolbar.requestLayout()
        }
    }
    private fun applyImeInset(ime: Int) {
        val nav = searchNavInset.coerceAtLeast(0)
        searchContainer.translationY = -(ime - nav).coerceAtLeast(0).toFloat()
        navHost.updatePadding(bottom = ime)
    }
    private fun syncSearchInsets() {
        if (!isSearchMode) return
        val insets = ViewCompat.getRootWindowInsets(searchContainer) ?: return
        val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).bottom
        val navChanged = nav != searchNavInset
        if (navChanged) {
            searchNavInset = nav
            val shadow = activity.resources.getDimensionPixelSize(com.google.android.material.R.dimen.sesl_floating_toolbar_projection_background_elevation)
            searchContainer.customPadding = Rect(0, shadow, 0, nav + shadow * 2)
            searchContainer.setPadding(0, shadow, 0, nav + shadow * 2)
            searchContainer.setWindowBottomInset(nav)
        }
        if (imeAnimating) return
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val imeChanged = ime != lastImeInset
        if (imeChanged) lastImeInset = ime
        if (navChanged || imeChanged) applyImeInset(lastImeInset.coerceAtLeast(0))
    }

    // ======= Search Scroll / Floating Bars =======
    fun attachSearchScroll(scroll: NestedScrollView) {
        scroll.seslSetFadingEdgeEnabled(false)
        searchContainer.setNestedScrollView(scroll)
        floatingToolbar.setNestedScrollView(scroll)
        showSearchBarsIfHidden()
    }
    fun detachSearchScroll() {
        searchContainer.clearFloatingScrollableView()
        floatingToolbar.clearFloatingScrollableView()
        showSearchBarsIfHidden()
    }
    private fun showSearchBarsIfHidden() {
        if (searchContainer.visibility == View.VISIBLE && searchContainer.alpha == 0f) { searchContainer.setLayoutVisibility(true, animation = false, force = true) }
        if (floatingToolbar.alpha == 0f) { floatingToolbar.setLayoutVisibility(true, animation = false, force = true) }
    }
    private fun activeScrollY(): Int = (navHost as? ViewGroup)?.children?.firstOrNull { it.visibility == View.VISIBLE }
        ?.findViewById<NestedScrollView>(R.id.scroll_view)?.scrollY ?: 0

    // ======= Helpers (Animations / Layout) =======
    private fun View.keepGuardsAttached(vararg guards: ViewTreeObserver.OnPreDrawListener) {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                guards.forEach {
                    v.viewTreeObserver.removeOnPreDrawListener(it)
                    v.viewTreeObserver.addOnPreDrawListener(it)
                }
            }
            override fun onViewDetachedFromWindow(v: View) { guards.forEach { v.viewTreeObserver.removeOnPreDrawListener(it) } }
        })
    }
    private fun runAtomic(action: () -> Unit) {
        val vto = toolbar.viewTreeObserver
        if (!toolbar.isAttachedToWindow || !vto.isAlive) {
            action()
            return
        }
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val current = toolbar.viewTreeObserver
                if (current.isAlive) current.removeOnPreDrawListener(this)
                action()
                return false
            }
        })
        toolbar.invalidate()
    }
    private fun fadeInAnim(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(searchFadeDuration).setInterpolator(searchFadeInterpolator).start()
    }
    private fun fadeOutAnim(view: View, endVisibility: Int, onEnd: () -> Unit = {}) {
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) {
            view.visibility = endVisibility
            onEnd()
            return
        }
        view.animate().alpha(0f).setDuration(searchFadeDuration).setInterpolator(searchFadeInterpolator).withEndAction {
            view.visibility = endVisibility
            view.alpha = 1f
            onEnd()
        }.start()
    }
    private fun setNow(view: View, visibility: Int) {
        view.animate().cancel()
        view.alpha = 1f
        view.visibility = visibility
    }
    private fun searchTextSizePx(): Float {
        val res = activity.resources
        var px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, searchTextSizeSp, res.displayMetrics)
        val fontScale = res.configuration.fontScale
        if (fontScale > 1.3f) px = (px / fontScale) * 1.3f
        return px
    }

    // ======= Search Mode =======
    @SuppressLint("PrivateResource")
    fun startSearchMode(
        onBackBehavior: SearchModeOnBackBehavior = SearchModeOnBackBehavior.DISMISS,
        onQuery: (query: String, isSubmit: Boolean) -> Boolean,
        onStart: (SearchView) -> Unit = {}, onEnd: (SearchView) -> Unit = {},
        animate: Boolean = true, showKeyboard: Boolean = false,
        savedExpanded: Boolean? = null, savedHidden: Boolean? = null,
        onStateCaptured: (expanded: Boolean, hidden: Boolean) -> Unit = { _, _ -> }
    ) {
        if (isSearchMode) return
        isSearchMode = true
        backBehavior = onBackBehavior
        onEndCallback = onEnd
        if (!exiting) {
            wasExpanded = false
            wasHidden = savedHidden ?: (appBarLayout.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE) && activeScrollY() > 0)
            onStateCaptured(wasExpanded, wasHidden)
        }
        exiting = false
        holdTitleRestore = false
        noRangeLocked = false
        backButtonRemoved = false
        val enterFromHide = animate && isHidden
        setExpanded(false, animate)
        appBarLayout.removeCallbacks(restoreTitleRunnable)
        if (!titleRestorePending) {
            cachedExpandedTitle = collapsingToolbar.title
            cachedCollapsedTitle = toolbar.title
        }
        titleRestorePending = false
        hideMenuInSearch = !enterFromHide
        if (hideMenuInSearch) setToolbarMenuVisible(false)
        backButtonWaitsCollapsed = enterFromHide
        appBarLayout.removeCallbacks(applySearchTitleRunnable)
        if (!animate) applySearchTitleNow()
        else appBarLayout.postDelayed(applySearchTitleRunnable, enterSwapDelayMs)
        searchView.setOnQueryTextListener(null)
        searchView.setQuery("", false)
        searchNavInset = -1
        lastImeInset = -1
        imeAnimating = false
        syncSearchInsets()
        searchContainer.translationY = 0f
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val handled = onQuery(query.orEmpty(), true)
                if (!query.isNullOrEmpty()) searchView.clearFocus()
                return handled
            }
            override fun onQueryTextChange(newText: String?): Boolean = onQuery(newText.orEmpty(), false)
        })
        onStart(searchView)
        if (animate) {
            fadeInAnim(searchContainer)
            tabContainer?.let { fadeOutAnim(it, View.INVISIBLE) }
        } else {
            setNow(searchContainer, View.VISIBLE)
            tabContainer?.let { setNow(it, View.INVISIBLE) }
        }
        searchView.viewTreeObserver.addOnPreDrawListener(imeBackGuard)
        updateBackCallbackState()
        if (showKeyboard) {
            searchView.isIconified = false
            searchView.requestFocus()
            WindowCompat.getInsetsController(activity.window, searchView).show(WindowInsetsCompat.Type.ime())
        }
    }
    fun endSearchMode(animate: Boolean = true) {
        if (!isSearchMode) return
        isSearchMode = false
        backCallback.isEnabled = false
        val vto = searchView.viewTreeObserver
        if (vto.isAlive) vto.removeOnPreDrawListener(imeBackGuard)
        imeAnimating = false
        navHost.updatePadding(bottom = 0)
        searchNavInset = -1
        lastImeInset = -1
        val toOtherTab = holdTitleRestore
        val exitToHide = !wasExpanded && wasHidden && !toOtherTab
        noRangeLocked = false
        backButtonWaitsCollapsed = false
        searchContainer.stopPostShowRunnable()
        searchContainer.clearFloatingScrollableView()
        appBarLayout.removeCallbacks(applySearchTitleRunnable)
        exiting = true
        titleRestorePending = true
        backButtonRemoved = false
        setBackButtonInteractive(false)
        appBarLayout.removeCallbacks(restoreTitleRunnable)
        if (!animate) restoreTitleNow()
        else if (holdTitleRestore) appBarLayout.postDelayed(restoreTitleRunnable, 1000L)
        else appBarLayout.postDelayed(restoreTitleRunnable, exitSwapDelayMs)
        setCollapsedLock(false)
        if (!exitToHide) setToolbarMenuVisible(true)
        searchView.setOnQueryTextListener(null)
        WindowCompat.getInsetsController(activity.window, searchView).hide(WindowInsetsCompat.Type.ime())
        val finishSearchBar = {
            if (!isSearchMode) {
                searchView.setQuery("", false)
                searchView.clearFocus()
                searchContainer.translationY = 0f
                exiting = false
            }
        }
        if (animate) {
            fadeOutAnim(searchContainer, View.GONE) { finishSearchBar() }
            tabContainer?.let { fadeInAnim(it) }
        } else {
            setNow(searchContainer, View.GONE)
            finishSearchBar()
            tabContainer?.let { setNow(it, View.VISIBLE) }
        }
        if (wasExpanded) setExpanded(true, animate)
        else if (wasHidden) { if (!toOtherTab) appBarLayout.seslSetHide(animate, true) }
        else setExpanded(false, animate)
        wasExpanded = false
        wasHidden = false
        onEndCallback?.invoke(searchView)
        onEndCallback = null
    }
}