package com.guru.batteryoptimizer.fragments
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.utils.hideSearchResultsFromView
import com.guru.batteryoptimizer.utils.GuruFunctions
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt
import com.guru.batteryoptimizer.utils.showSearchResultsInView
import com.guru.batteryoptimizer.utils.SearchableFragment
import com.guru.batteryoptimizer.utils.SearchItem
import com.guru.batteryoptimizer.utils.SearchManager
import com.guru.batteryoptimizer.utils.navigateToSearchResult

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector
import dev.oneuiproject.oneui.widget.CardItemView
import dev.oneuiproject.oneui.widget.SeekBarPlus
import dev.oneuiproject.oneui.widget.SwitchItemView
import kotlinx.coroutines.*

class AdvancedSettings : Fragment(R.layout.advanced_settings_lay), SearchableFragment {
    private lateinit var scrollView:        NestedScrollView
    private lateinit var groups:            List<SeekBarGroup>
    private lateinit var litFreqItem:       CardItemView
    private lateinit var bigFreqItem:       CardItemView
    private lateinit var spinnerLitFreq:    AppCompatSpinner
    private lateinit var spinnerBigFreq:    AppCompatSpinner
    private lateinit var guruFunctions:     GuruFunctions
    private lateinit var switchFreqs:       SwitchItemView
    private lateinit var switchUv:          SwitchItemView
    private lateinit var litEntries:        Array<String>
    private lateinit var bigEntries:        Array<String>
    private var longPressJob:        Job? = null
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var searchContainerView: android.view.View? = null
    private var searchEmptyView:     android.widget.TextView? = null
    private val hapticCursorMove by lazy {
        @SuppressLint("RestrictedApi")
        SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(41)
    }
    private val seekBarTickMark by lazy { AppCompatResources.getDrawable(requireContext(), androidx.appcompat.R.drawable.sesl_level_seekbar_tick_mark) }
    private data class SeekBarGroup(
        val seekBar:         SeekBarPlus,
        val tvTitle:         TextView,
        val tvPercent:       TextView,
        val btnDown:         ImageButton,
        val btnUp:           ImageButton,
        val voltKey:         String,
        val defaultProgress: Int = 4
    )
    private val litValues = arrayOf(455000,598000,715000,832000,949000,1053000,1248000,1456000,1690000,1794000)
    private val bigValues = arrayOf(650000,741000,858000,962000,1066000,1170000,1261000,1469000,1586000,1690000,1794000,1924000,2002000,2106000,2314000,2652000,2704000)
    private val viewScope get() = viewLifecycleOwner.lifecycleScope
    private val appState  get() = (requireActivity() as MainActivity).appState

    // ======= Lifecycle =======
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView      = view.findViewById(R.id.scroll_view)
        switchFreqs     = view.findViewById(R.id.switch_freqs)
        switchUv        = view.findViewById(R.id.switch_uv)
        litFreqItem     = view.findViewById(R.id.lit_freq)
        bigFreqItem     = view.findViewById(R.id.big_freq)
        spinnerLitFreq  = view.findViewById(R.id.spinner_lit_freq)
        spinnerBigFreq  = view.findViewById(R.id.spinner_big_freq)
        litEntries      = litValues.map { "${(it / 1000).fmt()} MHz" }.toTypedArray()
        bigEntries      = bigValues.map { "${(it / 1000).fmt()} MHz" }.toTypedArray()
        guruFunctions   = GuruFunctions((requireActivity() as MainActivity).guru, requireContext())
        litFreqItem.isSummaryUserUpdatable = true
        bigFreqItem.isSummaryUserUpdatable = true
        spinnerLitFreq.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, litEntries).apply {
            setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        }
        spinnerBigFreq.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, bigEntries).apply {
            setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        }
        spinnerLitFreq.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val saved = guruFunctions.getInt("frq_little", 1690000)
                if (litValues[position] == saved) return
                litFreqItem.summary = litEntries[position]
                applyLitFreq(litValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        spinnerBigFreq.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val saved = guruFunctions.getInt("frq_big", 1690000)
                if (bigValues[position] == saved) return
                bigFreqItem.summary = bigEntries[position]
                applyBigFreq(bigValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        litFreqItem.setOnClickListener { spinnerLitFreq.performClick() }
        bigFreqItem.setOnClickListener { spinnerBigFreq.performClick() }
        fun sb(id: Int)  = view.findViewById<SeekBarPlus>(id)
        fun tv(id: Int)  = view.findViewById<TextView>(id)
        fun btn(id: Int) = view.findViewById<ImageButton>(id)
        groups = listOf(
            SeekBarGroup(sb(R.id.seekBarBigVolt),    tv(R.id.tvBigVoltTitle),    tv(R.id.tvBigVoltPercent),    btn(R.id.tvBigVoltDown),    btn(R.id.tvBigVoltUp),    "big_volt",    4),
            SeekBarGroup(sb(R.id.seekBarLitVolt),    tv(R.id.tvLitVoltTitle),    tv(R.id.tvLitVoltPercent),    btn(R.id.tvLitVoltDown),    btn(R.id.tvLitVoltUp),    "lit_volt",    4),
            SeekBarGroup(sb(R.id.seekBarGpuVolt),    tv(R.id.tvGpuVoltTitle),    tv(R.id.tvGpuVoltPercent),    btn(R.id.tvGpuVoltDown),    btn(R.id.tvGpuVoltUp),    "g3d_volt",    4),
            SeekBarGroup(sb(R.id.seekBarMifVolt),    tv(R.id.tvMifVoltTitle),    tv(R.id.tvMifVoltPercent),    btn(R.id.tvMifVoltDown),    btn(R.id.tvMifVoltUp),    "mif_volt",    4),
            SeekBarGroup(sb(R.id.seekBarAudVolt),    tv(R.id.tvAudVoltTitle),    tv(R.id.tvAudVoltPercent),    btn(R.id.tvAudVoltDown),    btn(R.id.tvAudVoltUp),    "aud_volt",    4),
            SeekBarGroup(sb(R.id.seekBarCamVolt),    tv(R.id.tvCamVoltTitle),    tv(R.id.tvCamVoltPercent),    btn(R.id.tvCamVoltDown),    btn(R.id.tvCamVoltUp),    "cam_volt",    4),
            SeekBarGroup(sb(R.id.seekBarCPVolt),     tv(R.id.tvCPVoltTitle),     tv(R.id.tvCPVoltPercent),     btn(R.id.tvCPVoltDown),     btn(R.id.tvCPVoltUp),     "cp_volt",     0),
            SeekBarGroup(sb(R.id.seekBarDispVolt),   tv(R.id.tvDispVoltTitle),   tv(R.id.tvDispVoltPercent),   btn(R.id.tvDispVoltDown),   btn(R.id.tvDispVoltUp),   "disp_volt",   4),
            SeekBarGroup(sb(R.id.seekBarFsysVolt),   tv(R.id.tvFsysVoltTitle),   tv(R.id.tvFsysVoltPercent),   btn(R.id.tvFsysVoltDown),   btn(R.id.tvFsysVoltUp),   "fsys0_volt",  4),
            SeekBarGroup(sb(R.id.seekBarIntVolt),    tv(R.id.tvIntVoltTitle),    tv(R.id.tvIntVoltPercent),    btn(R.id.tvIntVoltDown),    btn(R.id.tvIntVoltUp),    "int_volt",    4),
            SeekBarGroup(sb(R.id.seekBarIntCamVolt), tv(R.id.tvIntCamVoltTitle), tv(R.id.tvIntCamVoltPercent), btn(R.id.tvIntCamVoltDown), btn(R.id.tvIntCamVoltUp), "intcam_volt", 4),
            SeekBarGroup(sb(R.id.seekBarIvaVolt),    tv(R.id.tvIvaVoltTitle),    tv(R.id.tvIvaVoltPercent),    btn(R.id.tvIvaVoltDown),    btn(R.id.tvIvaVoltUp),    "iva_volt",    4),
            SeekBarGroup(sb(R.id.seekBarScoreVolt),  tv(R.id.tvScoreVoltTitle),  tv(R.id.tvScoreVoltPercent),  btn(R.id.tvScoreVoltDown),  btn(R.id.tvScoreVoltUp),  "score_volt",  4)
        )
        appState.attachScroll("advanced", scrollView)
        if (!isHidden) {
            requireActivity().invalidateOptionsMenu()
            (activity as? MainActivity)?.updateTitle(getString(R.string.advanced_toolbartitle))
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
        }
        setupViews()
        fixSeparatorIcons(view)
        restoreDialogIfNeeded()
    }
    override fun onStart() {
        super.onStart()
        appState.restoreScroll("advanced")
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            (activity as? MainActivity)?.resetScroll(scrollView)
            requireActivity().removeMenuProvider(menuProvider)
        } else {
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
            (activity as? MainActivity)?.animateToolbar(false, getString(R.string.advanced_toolbartitle), 2)
            val mainActivity = activity as? MainActivity ?: return
            if (mainActivity.appState.isSearchModeActive) {
                val query = mainActivity.appState.searchQuery
                if (query.isBlank()) {
                    showSearchResults(emptyList(), isInitialState = true)
                } else {
                    showSearchResults(SearchManager.search(query))
                }
            }
        }
    }
    override fun onStop() {
        super.onStop()
        appState.saveScroll("advanced")
    }
    override fun onDestroyView() {
        if (activity?.isChangingConfigurations == true) {
            dialog?.window?.setWindowAnimations(0)
            dialog?.dismiss()
        }
        super.onDestroyView()
        searchContainerView = null
        searchEmptyView     = null
        appState.fragmentDialogScrollY = dialogScrollView?.scrollY ?: appState.fragmentDialogScrollY
        appState.detachScroll("advanced", activity?.isChangingConfigurations == true)
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true) {
            if (appState.fragmentDialog == AppState.FragmentDialog.ADVANCED_INFO) {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
            }
        }
        dialog           = null
        dialogScrollView = null
    }

    // ======= Restore Dialog =======
    private fun restoreDialogIfNeeded() {
        if (dialog?.isShowing == true) return
        val scrollY = appState.fragmentDialogScrollY
        when (appState.fragmentDialog) {
            AppState.FragmentDialog.ADVANCED_INFO -> showInfoDialog(restoreScrollY = scrollY, isRestore = true)
            else -> {}
        }
    }

    // ======= Dismiss Dialog (for Root Checker) =======
    fun dismissActiveDialog(onDismissed: (() -> Unit)? = null): Boolean {
        val wasShowing = dialog?.isShowing == true
        if (wasShowing && onDismissed != null) {
            dialog?.setOnDismissListener {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                onDismissed()
            }
            dialog?.dismiss()
        } else {
            dialog?.dismiss()
        }
        dialog           = null
        dialogScrollView = null
        return wasShowing
    }

    // ======= Info Dialog =======
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) { menuInflater.inflate(R.menu.header_buttons, menu) }
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.search_fragment -> { (requireActivity() as MainActivity).showSearchDialog(); true }
                R.id.info_fragment -> {
                    if (dialog?.isShowing == true) return true
                    (requireActivity() as MainActivity).isBlocked = true
                    showInfoDialog()
                    true
                }
                else -> false
            }
        }
    }
    private fun showInfoDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.ADVANCED_INFO
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.advanced_infotitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.advanced_infomsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_info)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_ok)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.ADVANCED_INFO) {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
            }
        }
        dialog?.show()
        if (isRestore) dialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (requireActivity() as MainActivity).checks.attachFocusCheck(dialog!!)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
    }

    // ======= Setup =======
    private fun setupViews() {
        val freqsOn = guruFunctions.getBool("frq_on",  true)
        val uvOn    = guruFunctions.getBool("volt_on", true)
        switchFreqs.onCheckedChangedListener = null
        switchFreqs.isChecked = freqsOn
        switchFreqs.onCheckedChangedListener = { _, isChecked ->
            setFreqsItemsEnabled(isChecked)
            applyFreqsMaster(isChecked)
        }
        val savedLit = guruFunctions.getInt("frq_little", 1690000)
        val savedBig = guruFunctions.getInt("frq_big",    1690000)
        val litIdx   = litValues.indexOf(savedLit).takeIf { it >= 0 } ?: litValues.indexOf(1690000)
        val bigIdx   = bigValues.indexOf(savedBig).takeIf { it >= 0 } ?: bigValues.indexOf(1690000)
        spinnerLitFreq.setSelection(litIdx, false)
        spinnerBigFreq.setSelection(bigIdx, false)
        litFreqItem.summary = litEntries[litIdx]
        bigFreqItem.summary = bigEntries[bigIdx]
        switchUv.onCheckedChangedListener = null
        switchUv.isChecked = uvOn
        switchUv.onCheckedChangedListener = { _, isChecked ->
            setUvItemsEnabled(isChecked)
            applyVoltMaster(isChecked)
        }
        groups.forEach { group ->
            val saved    = guruFunctions.getInt(group.voltKey, -group.defaultProgress)
            val progress = -saved
            group.seekBar.progress = progress.coerceIn(group.seekBar.min, group.seekBar.max)
            group.tvPercent.text   = toDisplayValue(group.seekBar.progress)
        }
        val tickMark = seekBarTickMark
        groups.forEach { group ->
            group.seekBar.setMode(SeslSeekBar.MODE_LEVEL_BAR)
            group.seekBar.setTickMark(tickMark)
            group.seekBar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                    group.tvPercent.text = toDisplayValue(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar) { applyVolt(group.voltKey, -seekBar.progress) }
            })
            setupAdjustButton(group, isIncrement = false)
            setupAdjustButton(group, isIncrement = true)
        }
        setFreqsItemsEnabled(freqsOn)
        setUvItemsEnabled(uvOn)
    }

    // ======= SeekBar Buttons =======
    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdjustButton(group: SeekBarGroup, isIncrement: Boolean) {
        val button = if (isIncrement) group.btnUp else group.btnDown
        button.setOnClickListener { adjustSeekBar(group, isIncrement) }
        button.setOnLongClickListener {
            longPressJob?.cancel()
            longPressJob = viewScope.launch { while (isActive) { adjustSeekBar(group, isIncrement); delay(300) } }
            false
        }
        button.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) longPressJob?.cancel()
            false
        }
    }
    private fun adjustSeekBar(group: SeekBarGroup, isIncrement: Boolean) {
        val current  = group.seekBar.progress
        val newValue = if (isIncrement) (current + 1).coerceAtMost(group.seekBar.max)
                       else             (current - 1).coerceAtLeast(group.seekBar.min)
        if (newValue == current) return
        group.seekBar.progress = newValue
        group.tvPercent.text   = toDisplayValue(newValue)
        group.seekBar.performHapticFeedback(hapticCursorMove)
        applyVolt(group.voltKey, -newValue)
    }

    // ======= Options State =======
    private fun setFreqsItemsEnabled(enabled: Boolean) {
        litFreqItem.isEnabled    = enabled
        bigFreqItem.isEnabled    = enabled
        spinnerLitFreq.isEnabled = enabled
        spinnerBigFreq.isEnabled = enabled
    }
    private fun setUvItemsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        groups.forEach { group ->
            group.seekBar.isEnabled = enabled
            group.btnDown.isEnabled = enabled
            group.btnUp.isEnabled   = enabled
            group.btnDown.alpha     = alpha
            group.btnUp.alpha       = alpha
            group.tvTitle.alpha     = alpha
            group.tvPercent.alpha   = alpha
        }
    }

    // ======= Separator Icons =======
    private fun TextView.fitDrawableToText(extraDp: Float = 0f) {
        val extraPx = (extraDp * resources.displayMetrics.density).toInt()
        val sizePx  = textSize.toInt() + extraPx
        compoundDrawablesRelative[0]?.let { drawable ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.setTint(currentTextColor)
            setCompoundDrawablesRelative(drawable, null, null, null)
        }
    }
    private fun fixSeparatorIcons(view: View) {
        listOf(R.id.sep_freqs, R.id.sep_uv)
            .forEach { id -> view.findViewById<TextView>(id)?.fitDrawableToText(extraDp = 8f) }
        view.findViewById<ImageView>(R.id.ic_warning_freq)?.let { icWarning ->
            view.findViewById<TextView>(R.id.text_warning_advanced)?.let { tvWarning ->
                val extraPx = (15f * resources.displayMetrics.density).toInt()
                val sizePx  = tvWarning.textSize.toInt() + extraPx
                icWarning.layoutParams = icWarning.layoutParams.apply {
                    width  = sizePx
                    height = sizePx
                }
            }
        }
    }

    // ======= Dialog Helpers =======
    private fun applyDialogIcon(titleText: TextView, drawableRes: Int, paddingDp: Float = 8f) {
        ContextCompat.getDrawable(requireContext(), drawableRes)?.let { drawable ->
            val fm       = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size     = (baseSize * 1.2f).toInt()
            val offsetV  = ((size - baseSize) / 2)
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            drawable.setTint(titleText.currentTextColor)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (paddingDp * resources.displayMetrics.density).toInt()
        }
    }

    // ======= Scroll Indicators =======
    private fun setupScrollIndicators(messageView: View): NestedScrollView? {
        val scrollView      = messageView.findViewById<NestedScrollView>(R.id.dialog_scroll) ?: return null
        val indicatorTop    = messageView.findViewById<View>(R.id.scrollIndicatorUp)
        val indicatorBottom = messageView.findViewById<View>(R.id.scrollIndicatorDown)
        fun update() {
            indicatorTop.visibility    = if (scrollView.canScrollVertically(-1)) View.VISIBLE else View.GONE
            indicatorBottom.visibility = if (scrollView.canScrollVertically(1))  View.VISIBLE else View.GONE
        }
        scrollView.post { update() }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> update() })
        return scrollView
    }

    // ======= Apply Functions =======
    private fun applyFreqsMaster(enabled: Boolean) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyFreqsMaster(enabled) } }
    private fun applyLitFreq(value: Int)           { viewScope.launch(Dispatchers.IO) { guruFunctions.applyLitFreq(value) } }
    private fun applyBigFreq(value: Int)           { viewScope.launch(Dispatchers.IO) { guruFunctions.applyBigFreq(value) } }
    private fun applyVoltMaster(enabled: Boolean)  { viewScope.launch(Dispatchers.IO) { guruFunctions.applyVoltMaster(enabled) } }
    private fun applyVolt(key: String, value: Int) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyVolt(key, value) } }

    // ======= Search Results Function =======
    override fun showSearchResults(results: List<SearchItem>, isInitialState: Boolean) {
        if (!isAdded) return
        val root = requireView() as android.view.ViewGroup
        val result = showSearchResultsInView(
            query = (requireActivity() as MainActivity).appState.searchQuery,
            root = root, results = results, currentContainer = searchContainerView, currentEmpty = searchEmptyView, isInitialState = isInitialState,
            onItemClick = { item ->
                (requireActivity() as MainActivity).run {
                    toolbarLayout.endSearchMode()
                    navigateToSearchResult(item)
                }
            }
        )
        searchContainerView = result.first
        searchEmptyView     = result.second
    }
    override fun hideSearchResults() {
        if (!isAdded) return
        val root = requireView() as android.view.ViewGroup
        hideSearchResultsFromView(root, searchContainerView, searchEmptyView)
        searchContainerView = null
        searchEmptyView     = null
    }

    // ======= Helpers =======
    private fun toDisplayValue(progress: Int): String = if (progress == 0) "${0.fmt()}%" else "-${progress.fmt()}%"
}