package com.guru.batteryoptimizer.utils
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import androidx.appcompat.widget.SearchView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.startSearchMode

// ======= Search Data =======
data class SearchItem(
    val title: String,
    val tabIndex: Int,
    val viewId: Int
)
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

// ======= Search UI Builder =======
fun buildSearchResultsView(
    fragment: Fragment,
    results: List<SearchItem>,
    query: String,
    onItemClick: (SearchItem) -> Unit
): android.view.View {
    val context = fragment.requireContext()
    val dm = fragment.resources.displayMetrics
    val padding = (10 * dm.density).toInt()
    val heightDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, dm).toInt()
    val scrollView = (android.view.LayoutInflater.from(context).inflate(R.xml.search_results_view, null) as NestedScrollView).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        isNestedScrollingEnabled = true
    }
    val innerLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(padding, padding, padding, padding)
    }
    val roundedLayout = dev.oneuiproject.oneui.widget.RoundedLinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        setBackgroundColor(ta.getColor(0, 0))
        ta.recycle()
    }
    results.forEachIndexed { index, item ->
        if (index > 0) {
            android.view.View(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, heightDp)
                val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider))
                background = ta.getDrawable(0)
                ta.recycle()
            }.also { roundedLayout.addView(it) }
        }
        dev.oneuiproject.oneui.widget.CardItemView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            title = highlightQuery(item.title, query, context)
            summary = when (item.tabIndex) {
                0 -> fragment.getString(R.string.device_toolbartitle)
                1 -> fragment.getString(R.string.general_toolbartitle)
                2 -> fragment.getString(R.string.advanced_toolbartitle)
                3 -> fragment.getString(R.string.about_toolbartitle)
                else -> ""
            }
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
    innerLayout.addView(roundedLayout)
    scrollView.addView(innerLayout)
    return scrollView
}
fun buildSearchEmptyView(context: Context): android.widget.TextView {
    return android.widget.TextView(context).apply {
        text = context.getString(R.string.search_no_results)
        textSize = 16f
        gravity = android.view.Gravity.CENTER
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        setTextColor(ta.getColor(0, android.graphics.Color.GRAY))
        ta.recycle()
    }
}
fun buildSearchInitialView(context: Context): android.widget.TextView {
    return android.widget.TextView(context).apply {
        text = context.getString(R.string.search_type_to_search)
        textSize = 16f
        gravity = android.view.Gravity.CENTER
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val ta = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        setTextColor(ta.getColor(0, android.graphics.Color.GRAY))
        ta.recycle()
    }
}

// ======= Search Mode Controller =======
fun MainActivity.startSearchModeUI() {
    val toolbar: ToolbarLayout = toolbarLayout
    toolbar.startSearchMode(
        onBackBehavior = ToolbarLayout.SearchModeOnBackBehavior.DISMISS,
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
            } else {
                getActiveSearchFragment()?.showSearchResults(emptyList(), isInitialState = true)
            }
        },
        onEnd = { _: SearchView ->
            appState.isSearchModeActive = false
            appState.searchQuery = ""
            getActiveSearchFragment()?.hideSearchResults()
        }
    )
    appState.isSearchModeActive = true
}
fun MainActivity.restoreSearchModeIfNeeded() {
    if (appState.isSearchModeActive) {
        toolbarLayout.post { startSearchModeUI() }
    }
}
private fun MainActivity.handleSearchQuery(query: String) {
    if (query.isEmpty()) {
        getActiveSearchFragment()?.showSearchResults(emptyList(), isInitialState = true)
    } else {
        val results = SearchManager.search(query)
        getActiveSearchFragment()?.showSearchResults(results, isInitialState = false)
    }
}
private fun MainActivity.getActiveSearchFragment(): SearchableFragment? {
    val tag = when (appState.selectedTab) {
        0 -> com.guru.batteryoptimizer.fragments.DeviceStatus::class.java.simpleName
        1 -> com.guru.batteryoptimizer.fragments.GeneralSettings::class.java.simpleName
        2 -> com.guru.batteryoptimizer.fragments.AdvancedSettings::class.java.simpleName
        3 -> com.guru.batteryoptimizer.fragments.AboutGuru::class.java.simpleName
        else -> return null
    }
    return supportFragmentManager.findFragmentByTag(tag) as? SearchableFragment
}
fun MainActivity.navigateToSearchResult(item: SearchItem) {
    val bottomTab = findViewById<dev.oneuiproject.oneui.widget.BottomTabLayout>(R.id.bottom_tab)
    bottomTab.postDelayed({
        bottomTab.getTabAt(item.tabIndex)?.select()
        appState.selectedTab = item.tabIndex
        val fragmentTag = when (item.tabIndex) {
            0 -> com.guru.batteryoptimizer.fragments.DeviceStatus::class.java.simpleName
            1 -> com.guru.batteryoptimizer.fragments.GeneralSettings::class.java.simpleName
            2 -> com.guru.batteryoptimizer.fragments.AdvancedSettings::class.java.simpleName
            3 -> com.guru.batteryoptimizer.fragments.AboutGuru::class.java.simpleName
            else -> return@postDelayed
        }
        findViewById<android.view.View>(R.id.nav_host_fragment)?.postDelayed({
            supportFragmentManager.findFragmentByTag(fragmentTag)?.view?.let { fragView ->
                val target = fragView.findViewById<android.view.View>(item.viewId) ?: return@postDelayed
                val scroll = fragView.findViewById<NestedScrollView>(R.id.scroll_view) ?: return@postDelayed
                target.post {
                    val rect = android.graphics.Rect()
                    target.getDrawingRect(rect)
                    scroll.offsetDescendantRectToMyCoords(target, rect)
                    val expandedExtra = if (toolbarLayout.isExpanded) toolbarLayout.appBarLayout.totalScrollRange else 0
                    val isFullyVisible = rect.top >= scroll.scrollY && rect.bottom <= (scroll.scrollY + scroll.height - expandedExtra)
                    val visibleHeight = scroll.height - scroll.paddingBottom
                    val offsetY = rect.top - (visibleHeight - target.height)
                    val innerView = when (target) {
                        is dev.oneuiproject.oneui.widget.CardItemView ->
                            target.findViewById<android.view.View>(dev.oneuiproject.oneui.design.R.id.cardview_container)
                        is dev.oneuiproject.oneui.widget.SwitchItemView ->
                            target.findViewById<android.view.View>(dev.oneuiproject.oneui.design.R.id.content_frame)
                        else -> {
                            val ta = target.context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                            if (target.background == null) target.background = ta.getDrawable(0)
                            ta.recycle()
                            target
                        }
                    } ?: target
                    var lastY = scroll.scrollY
                    var stableCount = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            val currentY = scroll.scrollY
                            if (currentY == lastY) stableCount++ else stableCount = 0
                            lastY = currentY
                            if (stableCount >= 2) {
                                scroll.postDelayed({
                                    when (target) {
                                        is dev.oneuiproject.oneui.widget.CardItemView,
                                        is dev.oneuiproject.oneui.widget.SwitchItemView -> {
                                            val bg = innerView.background
                                            bg?.state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
                                            innerView.postDelayed({bg?.state = intArrayOf(android.R.attr.state_enabled)}, 500)
                                        }
                                        is android.widget.Button -> {
                                            innerView.isPressed = true
                                            innerView.postDelayed({ innerView.isPressed = false }, 500)
                                        }
                                        else -> {
                                            val holder = androidx.appcompat.widget.SeslLinearLayoutCompat.ItemBackgroundHolder()
                                            holder.setPress(innerView)
                                            innerView.postDelayed({ holder.setRelease() }, 500)
                                        }
                                    }
                                }, 200)
                                return
                            } else { scroll.postDelayed(this, 16) }
                        }
                    }
                    scroll.post(runnable)
                    if (toolbarLayout.isExpanded && !isFullyVisible) {toolbarLayout.setExpanded(false, true)}
                    if (!isFullyVisible) {scroll.smoothScrollTo(0, offsetY)}
                }
            }
        }, 300)
    }, 200)
}

// ======= Fragment Search Results Helper =======
fun Fragment.showSearchResultsInView(
    results: List<SearchItem>,
    query: String,
    root: android.view.ViewGroup,
    currentContainer: android.view.View?,
    currentEmpty: android.widget.TextView?,
    isInitialState: Boolean = false,
    onItemClick: (SearchItem) -> Unit
): Pair<android.view.View?, android.widget.TextView?> {
    val scroll = root.findViewById<NestedScrollView>(R.id.scroll_view)
    scroll.visibility = android.view.View.GONE
    currentContainer?.let { root.removeView(it) }
    currentEmpty?.let { root.removeView(it) }
    if (results.isEmpty()) {
        val emptyView = if (isInitialState) buildSearchInitialView(requireContext()) else buildSearchEmptyView(requireContext())
        root.addView(emptyView)
        return Pair(null, emptyView)
    }
    val containerView = buildSearchResultsView(this, results, query, onItemClick)
    root.addView(containerView)
    return Pair(containerView, null)
}
fun Fragment.hideSearchResultsFromView(
    root: android.view.ViewGroup,
    currentContainer: android.view.View?,
    currentEmpty: android.widget.TextView?
) {
    val scroll = root.findViewById<NestedScrollView>(R.id.scroll_view)
    scroll.visibility = android.view.View.VISIBLE
    currentContainer?.let { root.removeView(it) }
    currentEmpty?.let { root.removeView(it) }
}