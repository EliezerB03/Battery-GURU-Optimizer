package com.guru.batteryoptimizer.fragments
import com.guru.batteryoptimizer.core.AppState
import com.guru.batteryoptimizer.MainActivity
import com.guru.batteryoptimizer.R
import com.guru.batteryoptimizer.utils.hideSearchResultsFromView
import com.guru.batteryoptimizer.utils.GuruFunctions.Fmt.fmt
import com.guru.batteryoptimizer.utils.showSearchResultsInView
import com.guru.batteryoptimizer.utils.SearchableFragment
import com.guru.batteryoptimizer.utils.SearchItem
import com.guru.batteryoptimizer.utils.SearchManager
import com.guru.batteryoptimizer.utils.navigateToSearchResult
import com.guru.batteryoptimizer.widgets.CardItemView
import com.guru.batteryoptimizer.widgets.SwitchItemView

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SeslProgressBar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DeviceStatus : Fragment(R.layout.device_status_lay), SearchableFragment {
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var showMemoryUsed:      Boolean = true
    private var lastMemLines:        List<String> = emptyList()
    private var lastRamFreqMHz:      Int = 0
    private var useFahrenheit:       Boolean = false
    private var currentCpuTempC:     Float = 0f
    private var currentGpuTempC:     Float = 0f
    private var currentBatteryTempC: Float = 0f
    private var monitoringJob:       Job? = null
    private var searchContainerView: View? = null
    private var searchEmptyView:     AppCompatTextView? = null
    private var prevGpuFreq       = -1
    private var prevGpuUsage      = -1
    private var prevCpuTempC      = Float.MIN_VALUE
    private var prevGpuTempC      = Float.MIN_VALUE
    private var prevBattTempC     = Float.MIN_VALUE
    private var prevMemUsed       = -1L
    private var prevMemTotal      = -1L
    private var prevSwapUsed      = -1L
    private var prevSwapTotal     = -1L
    private var prevRamFreqMHz    = -1
    private val corePairs         = mutableListOf<Pair<CoreViewHolder, CoreViewHolder>>()
    private val memoryPairs       = mutableListOf<Pair<MemoryViewHolder, MemoryViewHolder>>()
    private val viewScope get()   = viewLifecycleOwner.lifecycleScope
    private val appState  get()   = (requireActivity() as MainActivity).appState
    private val guru      get()   = (requireActivity() as MainActivity).guru
    private val prevCpuTimes      = Array(8) { LongArray(2) }
    private val prevCoreFreq      = IntArray(8) { -1 }
    private val prevCoreUsage     = IntArray(8) { -1 }
    private lateinit var scrollView:        NestedScrollView
    private lateinit var eCoreContainer:    LinearLayout
    private lateinit var pCoreContainer:    LinearLayout
    private lateinit var gpuFreq:           AppCompatTextView
    private lateinit var gpuPercent:        AppCompatTextView
    private lateinit var gpuProgress:       SeslProgressBar
    private lateinit var showMemory:        CardItemView
    private lateinit var spinnerMemory:     AppCompatSpinner
    private lateinit var memoryContainer:   LinearLayout
    private lateinit var tvCpuTemp:         AppCompatTextView
    private lateinit var tvGpuTemp:         AppCompatTextView
    private lateinit var tvBatteryTemp:     AppCompatTextView
    private lateinit var switchFahrenheit:  SwitchItemView
    private lateinit var batteryCapacity:   CardItemView
    private lateinit var batteryHealth:     CardItemView
    private lateinit var prefs:             SharedPreferences
    private lateinit var memoryEntries:     Array<String>
    data class CoreViewHolder(
        val coreProgressBar: SeslProgressBar,
        val coreNameView: AppCompatTextView, val freqCoreView: AppCompatTextView, val percentCoreView: AppCompatTextView
    )
    data class MemoryViewHolder(
        val memoryProgressBar: SeslProgressBar,
        val memoryNameView: AppCompatTextView, val memoryView: AppCompatTextView, val memoryFreqView: AppCompatTextView, val percentMemoryView: AppCompatTextView
    )
    companion object {
        private val ZRAM_ALG_REGEX  = "\\[(.*?)]".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()
        private const val PROGRESS_MODE_STANDARD = 0
    }

    // ======= Lifecycle =======
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView          = view.findViewById(R.id.scroll_view)
        eCoreContainer      = view.findViewById(R.id.e_cores_container)
        pCoreContainer      = view.findViewById(R.id.p_cores_container)
        gpuFreq             = view.findViewById(R.id.gpu_freq)
        gpuPercent          = view.findViewById(R.id.gpu_percent)
        gpuProgress         = view.findViewById(R.id.gpu_progress)
        showMemory          = view.findViewById(R.id.show_memory)
        spinnerMemory       = view.findViewById(R.id.spinner_memory)
        memoryContainer     = view.findViewById(R.id.memory_usage_container)
        tvCpuTemp           = view.findViewById(R.id.tv_cpu_temp)
        tvGpuTemp           = view.findViewById(R.id.tv_gpu_temp)
        tvBatteryTemp       = view.findViewById(R.id.tv_battery_temp)
        switchFahrenheit    = view.findViewById(R.id.switch_fahrenheit_unit)
        batteryCapacity     = view.findViewById(R.id.battery_cap)
        batteryHealth       = view.findViewById(R.id.battery_health)
        prefs               = requireContext().getSharedPreferences("guru_settings", Context.MODE_PRIVATE)
        memoryEntries       = arrayOf(getString(R.string.device_spinner_show_memory_1), getString(R.string.device_spinner_show_memory_2))
        showMemoryUsed      = prefs.getBoolean("show_memory", true)
        showMemory.isSummaryUserUpdatable = true
        spinnerMemory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, memoryEntries).apply {
            setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        }
        spinnerMemory.setSelection(if (showMemoryUsed) 0 else 1, false)
        showMemory.summary = memoryEntries[if (showMemoryUsed) 0 else 1]
        spinnerMemory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                showMemoryUsed = (position == 0)
                showMemory.summary = memoryEntries[position]
                prefs.edit().putBoolean("show_memory", showMemoryUsed).apply()
                resetMemoryCache()
                updateMemoryData(lastMemLines, lastRamFreqMHz)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        showMemory.setOnClickListener { spinnerMemory.performClick() }
        appState.attachScroll("device_info", scrollView)
        if (!isHidden) {
            requireActivity().invalidateOptionsMenu()
            (activity as? MainActivity)?.updateTitle(getString(R.string.device_toolbartitle))
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
        }
        buildCorePairs()
        buildMemoryPairs()
        applyProgressMode(gpuProgress, circle = false)
        setupViews()
        fixSeparatorIcons(view)
        restoreDialogIfNeeded()
        appState.onMonitoringReady    = { startMonitoring() }
        appState.onMonitoringError    = { showMonitorLostValues() }
        fun showCapacity() { batteryCapacity.summary = appState.batteryCapacityMah?.let { "${it.fmt()} mAh" } ?: "Unknown" }
        fun showHealth()   { batteryHealth.summary = (requireActivity() as MainActivity).checks.batteryHealthText }
        appState.onBatteryCapacityReady = { showCapacity() }
        appState.onBatteryHealthReady   = { showHealth() }
        showCapacity()
        showHealth()
    }
    override fun onStart() {
        super.onStart()
        appState.restoreScroll("device_info")
        val step = appState.currentCheckStep
        if (appState.checksPassed || step.ordinal >= AppState.CheckStep.CHECKING_SETTINGS.ordinal || appState.monitoringReadyFlag) {startMonitoring()}
        if (appState.lostRootDetected) { appState.onMonitoringError?.invoke() }
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            (activity as? MainActivity)?.resetScroll(scrollView)
            requireActivity().removeMenuProvider(menuProvider)
        } else {
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
            (activity as? MainActivity)?.animateToolbar(false, getString(R.string.device_toolbartitle), 0)
            val mainActivity = activity as? MainActivity ?: return
            if (mainActivity.appState.isSearchModeActive) {
                val query = mainActivity.appState.searchQuery
                if (query.isBlank()) { showSearchResults(emptyList(), isInitialState = true) }
                else { showSearchResults(SearchManager.search(query)) }
            }
        }
    }
    override fun onStop() {
        super.onStop()
        appState.saveScroll("device_info")
        stopMonitoring()
    }
    override fun onDestroyView() {
        if (activity?.isChangingConfigurations == true) {
            dialog?.window?.setWindowAnimations(0)
            dialog?.dismiss()
        }
        super.onDestroyView()
        monitoringJob?.cancel()
        monitoringJob = null
        searchContainerView             = null
        searchEmptyView                 = null
        appState.onMonitoringReady      = null
        appState.onMonitoringError      = null
        appState.onBatteryCapacityReady = null
        appState.onBatteryHealthReady   = null
        appState.fragmentDialogScrollY  = dialogScrollView?.scrollY ?: appState.fragmentDialogScrollY
        appState.detachScroll("device_info", activity?.isChangingConfigurations == true)
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true) clearInfoDialogState()
        dialog            = null
        dialogScrollView  = null
    }
    // ======= Progress Mode =======
    private fun isMonetActive(): Boolean = !android.provider.Settings.Secure.getString(requireContext().contentResolver, "theme_customization_overlay_packages").isNullOrEmpty()
    private fun applyProgressMode(bar: SeslProgressBar, circle: Boolean) {
        val monet = isMonetActive()
        bar.setMode(when {
            circle && monet -> SeslProgressBar.MODE_CIRCLE
            circle          -> SeslProgressBar.MODE_GRADIENT_CIRCLE
            monet           -> PROGRESS_MODE_STANDARD
            else            -> SeslProgressBar.MODE_GRADIENT_HORIZONTAL
        })
    }

    // ======= Monitoring =======
    private fun startMonitoring() {
        refreshZramAlgorithm()
        if (monitoringJob?.isActive == true) return
        monitoringJob = viewScope.launch {
            readProcStat()
            delay(30)
            while (isActive) {
                fetchAndUpdateAll()
                delay(750)
            }
        }
    }
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    private fun resetMemoryCache() {
        prevMemUsed    = -1L
        prevMemTotal   = -1L
        prevSwapUsed   = -1L
        prevSwapTotal  = -1L
        prevRamFreqMHz = -1
    }
    private fun CoreViewHolder.showLost() {
        freqCoreView.text        = "N/A"
        percentCoreView.text     = "---%"
        coreProgressBar.progress = 0
    }
    private fun MemoryViewHolder.showLost(name: String) {
        memoryNameView.text        = name
        memoryView.text            = "N/A"
        memoryFreqView.text        = "N/A"
        percentMemoryView.text     = "---%"
        memoryProgressBar.progress = 0
    }
    private fun showMonitorLostValues() {
        stopMonitoring()
        prevCoreFreq.fill(-1)
        prevCoreUsage.fill(-1)
        prevGpuFreq    = -1
        prevGpuUsage   = -1
        prevCpuTempC   = Float.MIN_VALUE
        prevGpuTempC   = Float.MIN_VALUE
        prevBattTempC  = Float.MIN_VALUE
        resetMemoryCache()
        for ((left, right) in corePairs) { left.showLost(); right.showLost() }
        for ((ram, zram) in memoryPairs) { ram.showLost("RAM"); zram.showLost("ZRAM") }
        gpuFreq.text          = "N/A"
        gpuPercent.text       = "---%"
        gpuProgress.progress  = 0
        tvCpuTemp.text        = "N/A"
        tvGpuTemp.text        = "N/A"
        tvBatteryTemp.text    = "N/A"
    }
    private fun refreshZramAlgorithm() {
        viewScope.launch {
            val raw = withContext(Dispatchers.IO) {guru.exec("cat /sys/block/zram0/comp_algorithm 2>/dev/null || echo N/A")}
            appState.zramAlgorithm = ZRAM_ALG_REGEX.find(raw.trim())?.groupValues?.getOrNull(1)?.uppercase() ?: "N/A"
            if (lastMemLines.isNotEmpty() && !appState.lostRootDetected) {updateMemoryData(lastMemLines, lastRamFreqMHz)}
        }
    }

    // ======= Values (CPU/GPU/Memory/Battery) =======
    private data class ParsedData(
        val cpuFreqs: List<Int>, val statLines: List<String>,
        val gpuUtil: Int, val gpuClockMhz: Int,
        val cpuTempC: Float, val gpuTempC: Float, val batteryTempC: Float,
        val memLines: List<String>, val ramFreqMHz: Int
    )
    private suspend fun fetchAndUpdateAll() {
        val parsed  = withContext(Dispatchers.IO) {
            val raw = guru.exec("""
                echo __FREQ__
                for f in /sys/devices/system/cpu/cpu[0-7]/cpufreq/scaling_cur_freq; do read -r v < "${'$'}f" 2>/dev/null && echo "${'$'}v" || echo 0; done
                echo __GPU__
                read -r v < /sys/class/misc/mali0/device/utilization 2>/dev/null && echo "${'$'}v" || echo 0
                read -r v < /sys/class/misc/mali0/device/clock       2>/dev/null && echo "${'$'}v" || echo 0
                echo __TEMP__
                read -r v < /sys/class/thermal/thermal_zone0/temp    2>/dev/null && echo "${'$'}v" || echo 0
                read -r v < /sys/class/thermal/thermal_zone2/temp    2>/dev/null && echo "${'$'}v" || echo 0
                read -r v < /sys/class/thermal/thermal_zone5/temp    2>/dev/null && echo "${'$'}v" || echo 0
                echo __STAT__
                grep "^cpu[0-7] " /proc/stat
                echo __MEM__
                grep -E "^(MemTotal|MemAvailable|SwapTotal|SwapFree):" /proc/meminfo
                echo __MEMFREQ__
                for f in /sys/class/devfreq/*mif*/cur_freq; do read -r v < "${'$'}f" 2>/dev/null && echo "${'$'}v" && break; done || echo 0
            """.trimIndent())
            val lines        = raw.lines()
            val idx          = listOf("__FREQ__", "__GPU__", "__TEMP__", "__STAT__", "__MEM__", "__MEMFREQ__").map {marker -> lines.indexOfFirst {it.trim() == marker}}
            if (idx.any { it < 0 }) return@withContext null
            val memFreqIdx   = idx[5]
            val freqLines    = lines.subList(idx[0] + 1, idx[1])
            val gpuLines     = lines.subList(idx[1] + 1, idx[2])
            val tempLines    = lines.subList(idx[2] + 1, idx[3])
            val statLines    = lines.subList(idx[3] + 1, idx[4])
            val memLines     = lines.subList(idx[4] + 1, memFreqIdx)
            val cpuFreqs     = (0..7).map { freqLines.getOrNull(it)?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0 }
            val gpuUtil      = gpuLines.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val gpuClockMhz  = gpuLines.getOrNull(1)?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
            val cpuTempC     = (tempLines.getOrNull(0)?.trim()?.toFloatOrNull() ?: 0f) / 1000f
            val gpuTempC     = (tempLines.getOrNull(1)?.trim()?.toFloatOrNull() ?: 0f) / 1000f
            val batteryTempC = (tempLines.getOrNull(2)?.trim()?.toFloatOrNull() ?: 0f) / 1000f
            val ramFreqKHz   = lines.getOrNull(memFreqIdx + 1)?.trim()?.toLongOrNull() ?: 0L
            val ramFreqMHz   = (ramFreqKHz / 1000L).toInt()
            ParsedData(cpuFreqs, statLines, gpuUtil, gpuClockMhz, cpuTempC, gpuTempC, batteryTempC, memLines, ramFreqMHz)
        } ?: return
        yield()
        val cpuUsages = parseProcStat(parsed.statLines)
        for (i in 0..7) updateCoreData(i, parsed.cpuFreqs[i], cpuUsages[i])
        updateGpuData(parsed.gpuClockMhz, parsed.gpuUtil)
        updateMemoryData(parsed.memLines, parsed.ramFreqMHz)
        updateTemperatures(parsed.cpuTempC, parsed.gpuTempC, parsed.batteryTempC)
    }
    private suspend fun readProcStat() {
        val raw = withContext(Dispatchers.IO) { guru.exec("grep \"^cpu[0-7] \" /proc/stat") }
        parseProcStat(raw.lines())
    }
    private fun parseProcStat(lines: List<String>): IntArray {
        val cpuUsages = IntArray(8)
        for (i in 0..7) {
            val line  = lines.firstOrNull { it.startsWith("cpu$i ") } ?: continue
            val parts = line.trim().split(WHITESPACE_REGEX)
            if (parts.size < 5) continue
            fun part(n: Int) = parts.getOrNull(n)?.toLongOrNull() ?: 0L
            val idle      = part(4) + part(5)
            val busy      = part(1) + part(2) + part(3) + part(6) + part(7)
            val total     = idle + busy
            val prevTotal = prevCpuTimes[i][0]
            val prevIdle  = prevCpuTimes[i][1]
            val dTotal    = total - prevTotal
            val dIdle     = idle  - prevIdle
            cpuUsages[i]  = if (dTotal > 0) ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100) else 0
            prevCpuTimes[i][0] = total
            prevCpuTimes[i][1] = idle
        }
        return cpuUsages
    }

    // ======= Restore Dialog =======
    private fun restoreDialogIfNeeded() {
        if (dialog?.isShowing == true) return
        if (appState.fragmentDialog == AppState.FragmentDialog.DEVICE_INFO) showInfoDialog(restoreScrollY = appState.fragmentDialogScrollY, isRestore = true)
    }
    private fun clearDialogState() {
        appState.fragmentDialog        = AppState.FragmentDialog.NONE
        appState.fragmentDialogScrollY = 0
    }
    private fun clearInfoDialogState() {if (appState.fragmentDialog == AppState.FragmentDialog.DEVICE_INFO) clearDialogState()}

    // ======= Dismiss Dialog (for Root Checker) =======
    fun dismissActiveDialog(onDismissed: (() -> Unit)? = null): Boolean {
        val wasShowing = dialog?.isShowing == true
        if (wasShowing && onDismissed != null) {
            dialog?.setOnDismissListener {
                clearDialogState()
                onDismissed()
            }
        }
        dialog?.dismiss()
        dialog           = null
        dialogScrollView = null
        return wasShowing
    }

    // ======= Menu Helper =======
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) { menuInflater.inflate(R.menu.header_buttons, menu) }
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.search_fragment -> {
                    (requireActivity() as MainActivity).showSearchDialog()
                    true
                }
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
    // ======= Info Dialog =======
    private fun showInfoDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.DEVICE_INFO
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<AppCompatTextView>(R.id.dialog_title).apply { text = getString(R.string.device_infotitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = getString(R.string.device_infomsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_info)
        val scrollView  = setupScrollIndicators(messageView)
        val info = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_ok)) { d, _ ->
                clearDialogState()
                d.dismiss()
            }
            .create()
        dialog = info
        info.seslSetBackgroundBlurEnabled(true)
        info.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        info.setOnDismissListener { if (isAdded) clearInfoDialogState() }
        info.show()
        if (isRestore) info.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (requireActivity() as MainActivity).checks.attachFocusCheck(info)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
    }

    // ======= Setup =======
    private fun setupViews() {
        switchFahrenheit.onCheckedChangedListener = null
        useFahrenheit = prefs.getBoolean("temp_fahrenheit_unit", false)
        switchFahrenheit.isChecked = useFahrenheit
        switchFahrenheit.onCheckedChangedListener = { _, isChecked ->
            useFahrenheit = isChecked
            prefs.edit().putBoolean("temp_fahrenheit_unit", useFahrenheit).apply()
            updateTempDisplay()
        }
    }

    // ======= Temperature Conversion =======
    private fun celsiusToFahrenheit(celsius: Float): Float = (celsius * 9 / 5) + 32
    private fun updateTempDisplay() {
        fun temp(celsius: Float) = if (useFahrenheit) "${celsiusToFahrenheit(celsius).fmt()} °F" else "${celsius.fmt()} °C"
        tvCpuTemp.text     = temp(currentCpuTempC)
        tvGpuTemp.text     = temp(currentGpuTempC)
        tvBatteryTemp.text = temp(currentBatteryTempC)
    }

    // ======= CPU Usage UI Building =======
    private fun buildCorePairs() {
        val coreLabel = getString(R.string.device_cpu_cores)
        for (pairCpuIndex in 0 until 4) {
            val cpuView = LayoutInflater.from(context).inflate(R.xml.cpu_cores_info, eCoreContainer, false)
            fun holder(progressId: Int, nameId: Int, freqId: Int, percentId: Int, coreIndex: Int) = CoreViewHolder(
                cpuView.findViewById<SeslProgressBar>(progressId).also { applyProgressMode(it, circle = true) },
                cpuView.findViewById<AppCompatTextView>(nameId).also { it.text = "$coreLabel ${(coreIndex + 1).fmt()}" },
                cpuView.findViewById(freqId),
                cpuView.findViewById(percentId)
            )
            val leftCoreIndex = pairCpuIndex * 2
            val left  = holder(R.id.core_progress_left,  R.id.core_name_left,  R.id.core_freq_left,  R.id.core_percent_left,  leftCoreIndex)
            val right = holder(R.id.core_progress_right, R.id.core_name_right, R.id.core_freq_right, R.id.core_percent_right, leftCoreIndex + 1)
            if (leftCoreIndex < 4) eCoreContainer.addView(cpuView) else pCoreContainer.addView(cpuView)
            corePairs.add(Pair(left, right))
        }
    }

    // ======= Memory Usage UI Building =======
    private fun buildMemoryPairs() {
        val memoryView = LayoutInflater.from(context).inflate(R.xml.memory_info, memoryContainer, false)
        fun holder(progressId: Int, nameId: Int, usedFreeId: Int, freqId: Int, percentId: Int) = MemoryViewHolder(
            memoryView.findViewById<SeslProgressBar>(progressId).also { applyProgressMode(it, circle = true) },
            memoryView.findViewById(nameId),
            memoryView.findViewById(usedFreeId),
            memoryView.findViewById(freqId),
            memoryView.findViewById(percentId)
        )
        val left  = holder(R.id.memory_progress_left,  R.id.memory_name_left,  R.id.memory_usedfree_left,  R.id.memory_freq_left,       R.id.memory_percent_left)
        val right = holder(R.id.memory_progress_right, R.id.memory_name_right, R.id.memory_usedfree_right, R.id.memory_algorithm_right, R.id.memory_percent_right)
        memoryContainer.addView(memoryView)
        memoryPairs.add(Pair(left, right))
    }

    // ======= Separator Icons =======
    private fun AppCompatTextView.fitDrawableToText(extraDp: Float = 0f) {
        val extraPx = (extraDp * resources.displayMetrics.density).toInt()
        val sizePx  = textSize.toInt() + extraPx
        compoundDrawablesRelative[0]?.let { drawable ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.setTint(currentTextColor)
            setCompoundDrawablesRelative(drawable, null, null, null)
        }
    }
    private fun fixSeparatorIcons(view: View) {
        listOf(R.id.sep_cpu_usage, R.id.sep_gpu_usage, R.id.sep_memory_usage, R.id.sep_thermal, R.id.sep_battery_info)
            .forEach { id -> view.findViewById<AppCompatTextView>(id)?.fitDrawableToText(extraDp = 8f) }
    }

    // ======= Update Data Methods =======
    private fun mhzText(mhz: Int): String = if (mhz == 0) getString(R.string.device_soc_idle_mhz) else "${mhz.fmt()} MHz"
    private fun MemoryViewHolder.bind(name: String, text: String, freq: String, percent: Int) {
        memoryNameView.text        = name
        memoryView.text            = text
        memoryFreqView.text        = freq
        percentMemoryView.text     = "${percent.fmt()}%"
        memoryProgressBar.progress = percent
    }
    private fun kbVal(key: String, lines: List<String>): Long = lines.firstOrNull { it.startsWith(key) }
        ?.trim()?.split(WHITESPACE_REGEX)?.getOrNull(1)?.toLongOrNull() ?: 0L
    fun updateCoreData(coreIndex: Int, coreFrequencyMHz: Int, cpuUsagePercent: Int) {
        val freqChanged  = prevCoreFreq[coreIndex]  != coreFrequencyMHz
        val usageChanged = prevCoreUsage[coreIndex] != cpuUsagePercent
        if (!freqChanged && !usageChanged) return
        prevCoreFreq[coreIndex]  = coreFrequencyMHz
        prevCoreUsage[coreIndex] = cpuUsagePercent
        val pairCpuIndex = coreIndex / 2
        val isCoreLeft   = (coreIndex % 2 == 0)
        if (pairCpuIndex in corePairs.indices) {
            val cpuHolder = if (isCoreLeft) corePairs[pairCpuIndex].first else corePairs[pairCpuIndex].second
            if (freqChanged)  cpuHolder.freqCoreView.text = mhzText(coreFrequencyMHz)
            if (usageChanged) {
                cpuHolder.percentCoreView.text     = "${cpuUsagePercent.fmt()}%"
                cpuHolder.coreProgressBar.progress = cpuUsagePercent
            }
        }
    }
    fun updateGpuData(gpuFrequencyMHz: Int, gpuUsagePercent: Int) {
        val freqChanged  = prevGpuFreq  != gpuFrequencyMHz
        val usageChanged = prevGpuUsage != gpuUsagePercent
        if (!freqChanged && !usageChanged) return
        prevGpuFreq  = gpuFrequencyMHz
        prevGpuUsage = gpuUsagePercent
        if (freqChanged)  gpuFreq.text = mhzText(gpuFrequencyMHz)
        if (usageChanged) {
            gpuPercent.text      = "${gpuUsagePercent.fmt()}%"
            gpuProgress.progress = gpuUsagePercent
        }
    }
    private fun updateMemoryData(memLines: List<String>, ramFreqMHz: Int) {
        lastMemLines     = memLines
        lastRamFreqMHz   = ramFreqMHz
        val memTotal     = kbVal("MemTotal:",     memLines)
        val memAvailable = kbVal("MemAvailable:", memLines)
        val swapTotal    = kbVal("SwapTotal:",    memLines)
        val swapFree     = kbVal("SwapFree:",     memLines)
        val memUsed      = memTotal  - memAvailable
        val swapUsed     = swapTotal - swapFree
        if (memUsed      == prevMemUsed   && memTotal  == prevMemTotal  &&
            swapUsed     == prevSwapUsed  && swapTotal == prevSwapTotal &&
            ramFreqMHz   == prevRamFreqMHz) return
        prevMemUsed      = memUsed
        prevMemTotal     = memTotal
        prevSwapUsed     = swapUsed
        prevSwapTotal    = swapTotal
        prevRamFreqMHz   = ramFreqMHz
        val memPct       = if (memTotal  > 0) ((memUsed  * 100) / memTotal ).toInt().coerceIn(0, 100) else 0
        val swapPct      = if (swapTotal > 0) ((swapUsed * 100) / swapTotal).toInt().coerceIn(0, 100) else 0
        val freqText     = mhzText(ramFreqMHz)
        fun gbText(kb: Long) = (kb / 1048576f).fmt(decimals = 2)
        if (memoryPairs.isEmpty()) return
        val (ram, swap) = memoryPairs[0]
        val swapAlgorithm = getString(R.string.device_swapalgorithm_name, appState.zramAlgorithm)
        if (showMemoryUsed) {
            ram.bind(getString(R.string.device_ramused_name), "${gbText(memUsed)} / ${gbText(memTotal)} GB", freqText, memPct)
            swap.bind(getString(R.string.device_swapused_name), "${gbText(swapUsed)} / ${gbText(swapTotal)} GB", swapAlgorithm, swapPct)
        } else {
            ram.bind(getString(R.string.device_ramfree_name), "${gbText(memAvailable)} / ${gbText(memTotal)} GB", freqText, 100 - memPct)
            swap.bind(getString(R.string.device_swapfree_name), "${gbText(swapFree)} / ${gbText(swapTotal)} GB", swapAlgorithm, 100 - swapPct)
        }
    }
    fun updateTemperatures(cpuTempC: Float, gpuTempC: Float, batteryTempC: Float) {
        if (cpuTempC == prevCpuTempC && gpuTempC == prevGpuTempC && batteryTempC == prevBattTempC) return
        prevCpuTempC        = cpuTempC
        prevGpuTempC        = gpuTempC
        prevBattTempC       = batteryTempC
        currentCpuTempC     = cpuTempC
        currentGpuTempC     = gpuTempC
        currentBatteryTempC = batteryTempC
        updateTempDisplay()
    }

    // ======= Dialog Helpers =======
    private fun applyDialogIcon(titleText: AppCompatTextView, drawableRes: Int, paddingDp: Float = 8f) {
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

    // ======= Search Results Function =======
    override fun showSearchResults(results: List<SearchItem>, isInitialState: Boolean) {
        if (!isAdded) return
        val result = showSearchResultsInView(results, searchContainerView, searchEmptyView, isInitialState)
        searchContainerView = result.first
        searchEmptyView     = result.second
    }
    override fun hideSearchResults() {
        if (!isAdded) return
        hideSearchResultsFromView(searchContainerView, searchEmptyView)
        searchContainerView = null
        searchEmptyView     = null
    }
}