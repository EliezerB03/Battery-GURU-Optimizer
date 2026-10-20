package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.fragments.AboutGuru
import com.guru.batteryoptimizer.fragments.AdvancedSettings
import com.guru.batteryoptimizer.fragments.DeviceStatus
import com.guru.batteryoptimizer.fragments.GeneralSettings
import com.guru.batteryoptimizer.widgets.CardItemView
import com.guru.batteryoptimizer.widgets.ListItemView
import com.guru.batteryoptimizer.widgets.ToolbarController

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SeslLinearLayoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout

// ======= Search Data =======
data class SearchItem(val title: String, val tabIndex: Int, val viewId: Int)
object SearchManager {
    private val items = mutableListOf<SearchItem>()
    fun register(newItems: List<SearchItem>) {
        items.clear()
        items.addAll(newItems)
    }
    fun isEmpty(): Boolean = items.isEmpty()
    fun search(query: String): List<SearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val q = trimmed.lowercase()
        return items.filter { it.title.lowercase().contains(q) }
    }
}

// ======= Search Fragment Interface =======
interface SearchableFragment {
    fun showSearchResults(results: List<SearchItem>, isInitialState: Boolean = false)
    fun hideSearchResults()
}

// ======= Tabs =======
private val TAB_TITLES    = listOf(R.string.device_toolbartitle, R.string.general_toolbartitle, R.string.advanced_toolbartitle, R.string.about_toolbartitle)
private val TAB_FRAGMENTS = listOf(DeviceStatus::class.java, GeneralSettings::class.java, AdvancedSettings::class.java, AboutGuru::class.java)
private fun tabTag(tab: Int): String? = TAB_FRAGMENTS.getOrNull(tab)?.simpleName

// ======= Theme Helper =======
private inline fun <T> Context.themeAttr(attr: Int, read: (TypedArray) -> T): T {
    val ta = theme.obtainStyledAttributes(intArrayOf(attr))
    return try { read(ta) } finally { ta.recycle() }
}

// ======= Highlight Text =======
fun Context.getColorPrimaryCompat(): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
    return typedValue.data
}
fun highlightQuery(text: String, query: String, context: Context): CharSequence {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return text
    val spannable = SpannableString(text)
    val lowerText = text.lowercase()
    val lowerQuery = trimmedQuery.lowercase()
    val color = context.getColorPrimaryCompat()
    var startIndex = lowerText.indexOf(lowerQuery)
    while (startIndex >= 0) {
        val endIndex = startIndex + lowerQuery.length
        spannable.setSpan(ForegroundColorSpan(color), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        startIndex = lowerText.indexOf(lowerQuery, endIndex)
    }
    return spannable
}

// ======= Center On Screen =======
private fun View.keepCenteredOnScreen() {
    val loc = IntArray(2)
    val guard = ViewTreeObserver.OnPreDrawListener {
        val window = rootView
        if (width > 0 && height > 0 && window.width > 0) {
            getLocationInWindow(loc)
            val barHeight = window.findViewById<View>(R.id.search_view)?.height?.takeIf { it > 0 }?: (52f * resources.displayMetrics.density).toInt()
            val bottomInset = ViewCompat.getRootWindowInsets(this)
                ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())?.bottom ?: 0
            val untranslatedLeft = loc[0] - translationX
            val untranslatedTop = loc[1] - translationY
            val targetX = window.width / 2f - (untranslatedLeft + width / 2f)
            val targetY = -(untranslatedTop + barHeight + bottomInset) / 2f
            if (Math.abs(targetX - translationX) > 1f) translationX = targetX
            if (Math.abs(targetY - translationY) > 1f) translationY = targetY
        }
        true
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { v.viewTreeObserver.addOnPreDrawListener(guard) }
        override fun onViewDetachedFromWindow(v: View) { v.viewTreeObserver.removeOnPreDrawListener(guard) }
    })
}

// ======= Separator Icon Helper =======
fun AppCompatTextView.fitDrawableToText(extraDp: Float = 0f) {
    val extraPx = (extraDp * resources.displayMetrics.density).toInt()
    val sizePx  = textSize.toInt() + extraPx
    compoundDrawablesRelative[0]?.let { drawable ->
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.setTint(currentTextColor)
        setCompoundDrawablesRelative(drawable, null, null, null)
    }
}

// ======= Search UI Builder =======
fun buildSearchResultsView(fragment: Fragment, results: List<SearchItem>, query: String, onItemClick: (SearchItem) -> Unit): View {
    val context = fragment.requireContext()
    val dm = fragment.resources.displayMetrics
    val heightDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, dm).toInt()
    val scrollView = (LayoutInflater.from(context).inflate(R.xml.search_results_view, null) as NestedScrollView).apply {
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        isNestedScrollingEnabled = true
        overScrollMode = View.OVER_SCROLL_ALWAYS
        isFillViewport = true
    }
    val innerLayout = scrollView.findViewById<LinearLayout>(R.id.search_results_container)
    val groupRoot = (LayoutInflater.from(context).inflate(R.layout.search_rounded_group, null) as LinearLayout).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    groupRoot.findViewById<AppCompatTextView>(R.id.sep_search_results)?.fitDrawableToText(extraDp = 8f)
    val roundedLayout = groupRoot.findViewById<SeslLinearLayoutCompat>(R.id.search_rounded_group).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setBackgroundColor(context.themeAttr(android.R.attr.colorBackground) { it.getColor(0, 0) })
    }
    results.forEachIndexed { index, item ->
        if (index > 0) {
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightDp)
                background = context.themeAttr(android.R.attr.listDivider) { it.getDrawable(0) }
            }.also { roundedLayout.addView(it) }
        }
        CardItemView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            title = highlightQuery(item.title, query, context)
            summary = TAB_TITLES.getOrNull(item.tabIndex)?.let { fragment.getString(it) } ?: ""
            isClickable = true
            isFocusable = true
            isEnabled = true
            setOnTouchListener { _, event ->
                if (android.os.Build.VERSION.SDK_INT >= 29) {dispatchTouchEvent(event)}
                false
            }
            setOnClickListener { onItemClick(item) }
        }.also { roundedLayout.addView(it) }
    }
    innerLayout.addView(groupRoot, 0)
    return scrollView
}
private fun buildSearchMessageView(context: Context, @StringRes textRes: Int): AppCompatTextView {
    return AppCompatTextView(context).apply {
        setText(textRes)
        textSize = 16f
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setTextColor(context.themeAttr(android.R.attr.textColorSecondary) { it.getColor(0, Color.GRAY) })
        keepCenteredOnScreen()
    }
}
fun buildSearchEmptyView(context: Context): AppCompatTextView = buildSearchMessageView(context, R.string.search_no_results)
fun buildSearchInitialView(context: Context): AppCompatTextView = buildSearchMessageView(context, R.string.search_type_to_search)

// ======= Search Mode Controller =======
fun MainActivity.startSearchModeUI(animate: Boolean = true, showKeyboard: Boolean = false) {
    val toolbar: ToolbarController = toolbarLayout
    val restoring = appState.isSearchModeActive
    searchResultNavigating = false
    holdSearchFade = false
    searchResultsAnimationsEnabled = animate
    try {
        toolbar.startSearchMode(
            onBackBehavior = ToolbarController.SearchModeOnBackBehavior.DISMISS,
            onQuery = { query: String, _: Boolean ->
                appState.searchQuery = query
                handleSearchQuery(query)
                true
            },
            onStart = { searchView: SearchView ->
                searchView.queryHint = getString(R.string.search_hint)
                if (appState.searchQuery.isNotEmpty()) {
                    searchView.setQuery(appState.searchQuery, false)
                    handleSearchQuery(appState.searchQuery)
                } else {handleSearchQuery("", collapse = false)}
            },
            onEnd = { _: SearchView ->
                appState.isSearchModeActive = false
                appState.searchQuery = ""
                reattachScrollables()
                val navigating = searchResultNavigating
                searchResultNavigating = false
                if (!navigating) getActiveSearchFragment()?.hideSearchResults()
            },
            animate = animate, showKeyboard = showKeyboard,
            savedExpanded = if (restoring) appState.searchWasExpanded else null, savedHidden = if (restoring) appState.searchWasHidden else null,
            onStateCaptured = { expanded, hidden ->
                appState.searchWasExpanded = expanded
                appState.searchWasHidden = hidden
            }
        )
    } finally { searchResultsAnimationsEnabled = true }
    appState.isSearchModeActive = true
}
fun MainActivity.restoreSearchModeIfNeeded() {
    if (!appState.isSearchModeActive) return
    val root = findViewById<View>(R.id.root_layout)
    root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            root.viewTreeObserver.removeOnPreDrawListener(this)
            startSearchModeUI(animate = false, showKeyboard = false)
            return false
        }
    })
}
private fun MainActivity.handleSearchQuery(query: String, collapse: Boolean = true) {
    if (query.isEmpty()) {
        toolbarLayout.setCollapsedLock(true, collapse)
        getActiveSearchFragment()?.showSearchResults(emptyList(), isInitialState = true)
    } else {
        val results = SearchManager.search(query)
        toolbarLayout.setCollapsedLock(results.isEmpty(), collapse)
        getActiveSearchFragment()?.showSearchResults(results, isInitialState = false)
    }
}
private fun MainActivity.getActiveSearchFragment(): SearchableFragment? {
    val tag = tabTag(appState.selectedTab) ?: return null
    return supportFragmentManager.findFragmentByTag(tag) as? SearchableFragment
}
private class SearchScrollPlan(
    val destY: Int, val needsScroll: Boolean,
    val goUp: Boolean, val goDown: Boolean,
    val keepHide: Boolean, val hidden: Boolean, val expanded: Boolean,
    val preTop: Float, val preBottom: Float
)
private fun MainActivity.planSearchScroll(target: View, scroll: NestedScrollView, fragView: View, assumeHidden: Boolean?): SearchScrollPlan {
    val abl = toolbarLayout.appBarLayout
    val loc = IntArray(2)
    val rect = Rect()
    target.getDrawingRect(rect)
    scroll.offsetDescendantRectToMyCoords(target, rect)
    scroll.getLocationInWindow(loc)
    var scrollTopScreen = loc[1] - fragView.translationY.toInt()
    (abl.parent as View).getLocationInWindow(loc)
    val coordTop = loc[1]
    val topLimit = coordTop + abl.seslGetCollapsedHeight().toInt()
    val floatingBottom = findViewById<View>(R.id.sesl_floating_bottom_layout)
    floatingBottom.getLocationInWindow(loc)
    val bottomGap = (3f * resources.displayMetrics.density).toInt()
    val bottomLimit = loc[1] - floatingBottom.translationY.toInt() - bottomGap
    var expanded = toolbarLayout.isExpanded
    var hidden = !expanded && (abl.seslHasAppBarState(AppBarLayout.SESL_STATE_HIDE) || scrollTopScreen < topLimit - 2)
    if (assumeHidden != null) {
        expanded = false
        hidden = assumeHidden
        scrollTopScreen = if (hidden) coordTop else topLimit
    }
    val contentTop = rect.top - scroll.scrollY
    val targetTop = scrollTopScreen + contentTop
    val targetBottom = targetTop + target.height
    val visibleTop = if (expanded) maxOf(topLimit, scrollTopScreen) else topLimit
    val isFullyVisible = targetTop >= visibleTop && targetBottom <= bottomLimit
    val goUp = !isFullyVisible && targetTop < visibleTop
    val goDown = !isFullyVisible && !goUp
    val hideRawY = scroll.scrollY + (coordTop + contentTop - topLimit)
    val keepHide = goUp && !expanded && hideRawY > 0
    val finalScrollTop = if (goDown || keepHide) coordTop else topLimit
    val finalTop = finalScrollTop + contentTop
    val finalBottom = finalTop + target.height
    val delta = when {
        isFullyVisible -> 0
        goUp -> minOf(finalTop - topLimit, 0)
        else -> maxOf(minOf(finalBottom - bottomLimit, maxOf(finalTop - topLimit, 0)), 0)
    }
    val contentHeight = scroll.getChildAt(0)?.height ?: 0
    val maxY = (contentHeight + scroll.paddingTop + scroll.paddingBottom - scroll.height).coerceAtLeast(0)
    val destY = (scroll.scrollY + delta).coerceIn(0, maxY)
    val rampPx = 10f * resources.displayMetrics.density
    val fadeRange = contentHeight - (scroll.height - scroll.paddingTop - scroll.paddingBottom)
    val endsHidden = if (goDown || keepHide) true else if (goUp) false else hidden
    val preTop = if (fadeRange > 0) maxOf((destY / rampPx).coerceIn(0f, 1f), if (endsHidden) 1f else 0f) else 0f
    val preBottom = if (fadeRange > 0) ((fadeRange - destY) / rampPx).coerceIn(0f, 1f) else 0f
    return SearchScrollPlan(destY, destY != scroll.scrollY, goUp, goDown, keepHide, hidden, expanded, preTop, preBottom)
}
fun MainActivity.preShowSearchResultFades(item: SearchItem, fragView: View?) {
    val fv = fragView ?: return
    val target = fv.findViewById<View>(item.viewId) ?: return
    val scroll = fv.findViewById<NestedScrollView>(R.id.scroll_view) ?: return
    val plan = planSearchScroll(target, scroll, fv, toolbarLayout.restoresHidden)
    preShowFades(plan.preTop, plan.preBottom)
    fv.postDelayed({ clearPreFades() }, 4000)
}
fun MainActivity.navigateToSearchResult(item: SearchItem) {
    TAB_TITLES.getOrNull(item.tabIndex)?.let { updateTitle(getString(it)) }
    val bottomTab = findViewById<View>(R.id.bottom_tab)
    bottomTab.postDelayed({
        val wasSameTab = item.tabIndex == appState.selectedTab
        val previousFragment = getActiveSearchFragment()
        selectTab(item.tabIndex)
        previousFragment?.hideSearchResults()
        holdSearchFade = false
        toolbarLayout.releaseTitleRestore()
        appState.selectedTab = item.tabIndex
        val fragmentTag = tabTag(item.tabIndex) ?: return@postDelayed
        if (!wasSameTab) {
            val destView = supportFragmentManager.findFragmentByTag(fragmentTag)?.view
            destView?.post {
                val earlyTarget = destView.findViewById<View>(item.viewId)
                val earlyScroll = destView.findViewById<NestedScrollView>(R.id.scroll_view)
                if (earlyTarget != null && earlyScroll != null) {
                    val earlyPlan = planSearchScroll(earlyTarget, earlyScroll, destView, searchRestoreHidden)
                    preShowFades(earlyPlan.preTop, earlyPlan.preBottom)
                    destView.postDelayed({ clearPreFades() }, 4000)
                }
            }
        }
        findViewById<View>(R.id.nav_host_fragment)?.postDelayed({
            supportFragmentManager.findFragmentByTag(fragmentTag)?.view?.let { fragView ->
                val target = fragView.findViewById<View>(item.viewId) ?: return@postDelayed
                val scroll = fragView.findViewById<NestedScrollView>(R.id.scroll_view) ?: return@postDelayed
                target.post {
                    val abl = toolbarLayout.appBarLayout
                    val plan = planSearchScroll(target, scroll, fragView, null)
                    val innerView = when (target) {
                        is ListItemView -> target.container
                        else -> {
                            val ta = target.context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                            if (target.background == null) target.background = ta.getDrawable(0)
                            ta.recycle()
                            target
                        }
                    }
                    var lastY = scroll.scrollY
                    var stableCount = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            val currentY = scroll.scrollY
                            if (currentY == lastY) stableCount++ else stableCount = 0
                            lastY = currentY
                            val done = if (plan.needsScroll) (Math.abs(currentY - plan.destY) <= 1 || stableCount >= 10) else stableCount >= 2
                            if (done) {
                                abl.seslAllowStartNestedScroll(true)
                                clearPreFades()
                                scroll.postDelayed({
                                    when (target) {
                                        is ListItemView -> {
                                            val bg = innerView.background
                                            bg?.state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
                                            innerView.postDelayed({bg?.state = intArrayOf(android.R.attr.state_enabled)}, 500)
                                        }
                                        is Button -> {
                                            innerView.isPressed = true
                                            innerView.postDelayed({ innerView.isPressed = false }, 500)
                                        }
                                        else -> {
                                            val holder = SeslLinearLayoutCompat.ItemBackgroundHolder()
                                            holder.setPress(innerView)
                                            innerView.postDelayed({ holder.setRelease() }, 500)
                                        }
                                    }
                                }, 350)
                                return
                            } else { scroll.postDelayed(this, 16) }
                        }
                    }
                    preShowFades(plan.preTop, plan.preBottom)
                    scroll.postDelayed({ clearPreFades() }, 2500)
                    val startAnim = Runnable {
                        if ((plan.goDown || plan.keepHide) && !plan.hidden) abl.seslSetHide(true, true)
                        else if (plan.goUp && !plan.keepHide && (plan.hidden || plan.expanded)) abl.setExpanded(false, true)
                        if (plan.needsScroll) {
                            abl.seslAllowStartNestedScroll(false)
                            scroll.postDelayed({ abl.seslAllowStartNestedScroll(true) }, 2000)
                            scroll.smoothScrollTo(0, plan.destY)
                        }
                        scroll.post(runnable)
                    }
                    scroll.postDelayed(startAnim, 50)
                }
            }
        }, 400)
    }, 200)
}

// ======= Fragment Search Results Helper =======
private const val SEARCH_FADE_DURATION = 250L
private val searchFadeInterpolator = PathInterpolator(0.33f, 0f, 0.67f, 1f)
private var searchResultsAnimationsEnabled = true
private var searchResultNavigating = false
fun Fragment.showSearchResultsInView(
    results: List<SearchItem>, currentContainer: View?, currentEmpty: AppCompatTextView?, isInitialState: Boolean = false
): Pair<View?, AppCompatTextView?> {
    val root = requireView() as ViewGroup
    val main = activity as? MainActivity
    val query = main?.appState?.searchQuery.orEmpty()
    val scroll = root.findViewById<NestedScrollView>(R.id.scroll_view)
    val entering = searchResultsAnimationsEnabled && scroll.visibility == View.VISIBLE && currentContainer == null && currentEmpty == null
    currentContainer?.let { it.animate().cancel(); root.removeView(it) }
    currentEmpty?.let { it.animate().cancel(); root.removeView(it) }
    val newView: View
    val result: Pair<View?, AppCompatTextView?>
    if (results.isEmpty()) {
        val emptyView = if (isInitialState) buildSearchInitialView(requireContext()) else buildSearchEmptyView(requireContext())
        newView = emptyView
        result = Pair(null, emptyView)
    } else {
        val containerView = buildSearchResultsView(this, results, query) { item ->
            searchResultNavigating = true
            (activity as? MainActivity)?.let { act ->
                act.toolbarLayout.holdTitleRestore = item.tabIndex != act.appState.selectedTab
                act.searchRestoreHidden = act.toolbarLayout.restoresHidden && item.tabIndex == act.appState.selectedTab
                if (item.tabIndex == act.appState.selectedTab) act.preShowSearchResultFades(item, view)
                act.pinSearchResults()
                act.toolbarLayout.endSearchMode()
                act.navigateToSearchResult(item)
            }
        }
        newView = containerView
        result = Pair(containerView, null)
    }
    scroll.animate().cancel()
    if (entering) {
        newView.alpha = 0f
        root.addView(newView)
        newView.animate().alpha(1f).setDuration(SEARCH_FADE_DURATION).setInterpolator(searchFadeInterpolator).start()
        scroll.animate().alpha(0f).setDuration(SEARCH_FADE_DURATION).setInterpolator(searchFadeInterpolator).withEndAction {
            scroll.visibility = View.GONE
            scroll.alpha = 1f
        }.start()
    } else {
        scroll.visibility = View.GONE
        scroll.alpha = 1f
        root.addView(newView)
    }
    main?.let { act ->
        val resultsScroll = newView as? NestedScrollView
        if (resultsScroll != null) {
            act.appState.attachScroll("search", resultsScroll)
            resultsScroll.post { act.appState.restoreScroll("search") }
            act.toolbarLayout.attachSearchScroll(resultsScroll)
        } else {
            act.appState.detachScroll("search", true)
            act.toolbarLayout.detachSearchScroll()
        }
    }
    return result
}
fun Fragment.hideSearchResultsFromView(currentContainer: View?, currentEmpty: AppCompatTextView?) {
    val root = requireView() as ViewGroup
    val scroll = root.findViewById<NestedScrollView>(R.id.scroll_view)
    scroll.animate().cancel()
    if (scroll.visibility != View.VISIBLE) {
        scroll.alpha = 0f
        scroll.visibility = View.VISIBLE
    }
    scroll.animate().alpha(1f).setDuration(SEARCH_FADE_DURATION).setInterpolator(searchFadeInterpolator).start()
    listOfNotNull<View>(currentContainer, currentEmpty).forEach { view ->
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(SEARCH_FADE_DURATION).setInterpolator(searchFadeInterpolator)
            .withEndAction { root.removeView(view) }
            .start()
    }
}