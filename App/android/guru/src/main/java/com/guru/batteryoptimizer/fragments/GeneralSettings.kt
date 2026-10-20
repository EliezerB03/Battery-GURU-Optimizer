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
import com.guru.batteryoptimizer.widgets.CardItemView
import com.guru.batteryoptimizer.widgets.SeekBarPlus
import com.guru.batteryoptimizer.widgets.SwitchItemView

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SeslProgressBar
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector
import kotlinx.coroutines.*

class GeneralSettings : Fragment(R.layout.general_settings_lay), SearchableFragment {
    private val hapticCursorMove by lazy { SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(41) }
    private val viewScope get() = viewLifecycleOwner.lifecycleScope
    private val main      get() = requireActivity() as MainActivity
    private val guru      get() = main.guru
    private val appState  get() = main.appState
    private var longPressJob:        Job? = null
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var searchContainerView: View? = null
    private var searchEmptyView:     AppCompatTextView? = null
    private lateinit var scrollView:        NestedScrollView
    private lateinit var optimizeApps:      CardItemView
    private lateinit var battappsOpt:       SwitchItemView
    private lateinit var cachedappsOpt:     SwitchItemView
    private lateinit var wakelockblockOpt:  SwitchItemView
    private lateinit var cpuHotplug:        SwitchItemView
    private lateinit var powerEff:          SwitchItemView
    private lateinit var thermalOpt:        SwitchItemView
    private lateinit var cpuEffi:           SwitchItemView
    private lateinit var gpuEffi:           SwitchItemView
    private lateinit var swapOpt:           SwitchItemView
    private lateinit var storageOpt:        SwitchItemView
    private lateinit var batteryprotectOpt: SwitchItemView
    private lateinit var seekBarBattLevel:  SeekBarPlus
    private lateinit var tvBattLimitTitle:  AppCompatTextView
    private lateinit var tvBattLevel:       AppCompatTextView
    private lateinit var btnBattLevelDown:  ImageButton
    private lateinit var btnBattLevelUp:    ImageButton
    private lateinit var guruFunctions:     GuruFunctions
    private companion object {
        val GENERAL_DIALOGS = setOf(
            AppState.FragmentDialog.GENERAL_INFO, AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING, AppState.FragmentDialog.OPTIMIZE_APPS,
            AppState.FragmentDialog.OPTIMIZING_APPS, AppState.FragmentDialog.OPTIMIZING_CANCELED, AppState.FragmentDialog.APPS_OPTIMIZED
        )
    }

    // ======= Lifecycle =======
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView          = view.findViewById(R.id.scroll_view)
        optimizeApps        = view.findViewById(R.id.optimizeapps_opt)
        battappsOpt         = view.findViewById(R.id.battapps_opt)
        cachedappsOpt       = view.findViewById(R.id.cachedapps_opt)
        wakelockblockOpt    = view.findViewById(R.id.wakelock_opt)
        cpuHotplug          = view.findViewById(R.id.cpu_hotplug_opt)
        powerEff            = view.findViewById(R.id.power_effi_opt)
        thermalOpt          = view.findViewById(R.id.thermal_opt)
        cpuEffi             = view.findViewById(R.id.cpu_effiboost_opt)
        gpuEffi             = view.findViewById(R.id.gpu_effiboost_opt)
        swapOpt             = view.findViewById(R.id.swap_opt)
        storageOpt          = view.findViewById(R.id.storage_opt)
        batteryprotectOpt   = view.findViewById(R.id.batteryprotect_opt)
        seekBarBattLevel    = view.findViewById(R.id.seekBarBattLevel)
        tvBattLimitTitle    = view.findViewById(R.id.tvBattLimitTitle)
        tvBattLevel         = view.findViewById(R.id.tvBattLevel)
        btnBattLevelDown    = view.findViewById(R.id.btnBattLevelDown)
        btnBattLevelUp      = view.findViewById(R.id.btnBattLevelUp)
        guruFunctions       = GuruFunctions(guru, requireContext())
        appState.attachScroll("general", scrollView)
        if (!isHidden) {
            requireActivity().invalidateOptionsMenu()
            (activity as? MainActivity)?.updateTitle(getString(R.string.general_toolbartitle))
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
        }
        setupViews()
        fixSeparatorIcons(view)
        restoreDialogIfNeeded()
        appState.onOptimizeCompleteReady = {if (isAdded) showAppsOptimizedDialog()}
        appState.onOptimizeCancelReady   = {if (isAdded) showOptimizingCanceledDialog()}
    }
    override fun onStart() {
        super.onStart()
        appState.restoreScroll("general")
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            (activity as? MainActivity)?.resetScroll(scrollView)
            requireActivity().removeMenuProvider(menuProvider)
        } else {
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
            (activity as? MainActivity)?.animateToolbar(false, getString(R.string.general_toolbartitle), 1)
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
        appState.saveScroll("general")
    }
    override fun onDestroyView() {
        if (activity?.isChangingConfigurations == true) {
            dialog?.window?.setWindowAnimations(0)
            dialog?.dismiss()
        }
        super.onDestroyView()
        searchContainerView = null
        searchEmptyView     = null
        appState.onOptimizeCompleteReady = null
        appState.onOptimizeCancelReady   = null
        guru.onOptimizeProgress = null
        guru.onOptimizeComplete = null
        guru.onOptimizeCancel   = null
        appState.fragmentDialogScrollY = dialogScrollView?.scrollY ?: appState.fragmentDialogScrollY
        appState.detachScroll("general", activity?.isChangingConfigurations == true)
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true && appState.fragmentDialog in GENERAL_DIALOGS) {
            clearDialogState()
            appState.optimizeProgress = 0
        }
        dialog           = null
        dialogScrollView = null
    }

    // ======= Restore Dialog =======
    private fun restoreDialogIfNeeded() {
        if (dialog?.isShowing == true) return
        val scrollY = appState.fragmentDialogScrollY
        when (appState.fragmentDialog) {
            AppState.FragmentDialog.GENERAL_INFO                 -> showInfoDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING -> showBattProtectWarningDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.OPTIMIZE_APPS                -> showOptimizeAppsDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.OPTIMIZING_APPS              -> {
                if (!guru.isAppOptimizing) {
                    clearDialogState()
                    appState.optimizeCancelBtnEnabled = false
                    appState.optimizeProgress = 0
                    val result = appState.optimizeCompletedSuccessfully
                    appState.optimizeCompletedSuccessfully = null
                    when (result) {
                        true  -> showAppsOptimizedDialog()
                        false -> showOptimizingCanceledDialog()
                        null  -> {}
                    }
                    return
                }
                guru.onOptimizeProgress = { percent ->
                    if (isAdded) {
                        dialog?.findViewById<AppCompatTextView>(R.id.dialog_message)?.text = getString(R.string.dialog_progress) + " ${percent.fmt()}%"
                        dialog?.findViewById<SeslProgressBar>(R.id.progressbar)?.progress = percent
                    }
                }
                attachOptimizingCallbacks()
                showOptimizingAppsDialog(restoreScrollY = scrollY, isRestore = true)
            }
            AppState.FragmentDialog.OPTIMIZING_CANCELED          -> showOptimizingCanceledDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.APPS_OPTIMIZED               -> showAppsOptimizedDialog(restoreScrollY = scrollY, isRestore = true)
            else -> {}
        }
    }

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

    // ======= Dialog Builder =======
    private fun clearDialogState() {
        appState.fragmentDialog        = AppState.FragmentDialog.NONE
        appState.fragmentDialogScrollY = 0
    }
    private class DialogParts(val builder: AlertDialog.Builder, val titleView: View, val messageView: View, val scrollView: NestedScrollView?)
    private fun prepareDialog(
        @StringRes title: Int, @DrawableRes icon: Int, @StringRes message: Int?,
        paddingDp: Float = 8f, messageLayout: Int = R.xml.dialog_message
    ): DialogParts {
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<AppCompatTextView>(R.id.dialog_title).apply { text = getString(title) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(messageLayout, null)
        if (message != null) messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = getString(message)
        applyDialogIcon(titleText, icon, paddingDp)
        return DialogParts(builder, titleView, messageView, setupScrollIndicators(messageView))
    }
    private fun styleDialog(d: AlertDialog) {
        d.seslSetBackgroundBlurEnabled(true)
        d.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
    }
    private fun AlertDialog.clearStateOnDismiss(state: AppState.FragmentDialog, extra: (() -> Unit)? = null) {
        setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog == state) {
                clearDialogState()
                extra?.invoke()
            }
        }
    }
    private fun presentDialog(d: AlertDialog, scrollView: NestedScrollView?, restoreScrollY: Int, isRestore: Boolean) {
        d.show()
        if (isRestore) d.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        main.checks.attachFocusCheck(d)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
    }
    private fun showNotice(
        state: AppState.FragmentDialog, @StringRes title: Int, @StringRes message: Int, @StringRes button: Int, @DrawableRes icon: Int,
        restoreScrollY: Int, isRestore: Boolean, iconPaddingDp: Float = 8f, blockUi: Boolean = false, requireAdded: Boolean = false,
        onPositive: (() -> Unit)? = null, onDismissed: (() -> Unit)? = null
    ) {
        if (main.checks.lostRootDetected) return
        if (requireAdded && !isAdded) return
        appState.fragmentDialog = state
        if (blockUi) main.isBlocked = true
        val p = prepareDialog(title, icon, message, iconPaddingDp)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setPositiveButton(getString(button)) { dlg, _ ->
                clearDialogState()
                dlg.dismiss()
                onPositive?.invoke()
            }
            .create()
        dialog = d
        styleDialog(d)
        d.clearStateOnDismiss(state, onDismissed)
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }

    // ======= Menu Helper =======
    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) { menuInflater.inflate(R.menu.header_buttons, menu) }
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.search_fragment -> { main.showSearchDialog(); true }
                R.id.info_fragment -> {
                    if (dialog?.isShowing == true) return true
                    main.isBlocked = true
                    showInfoDialog()
                    true
                }
                else -> false
            }
        }
    }
    // ======= Info Dialog =======
    private fun showInfoDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.GENERAL_INFO, R.string.general_infotitle, R.string.general_infomsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_info, restoreScrollY, isRestore
    )

    // ======= Battery Protect Warning Dialog =======
    private fun showBattProtectWarningDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING, R.string.general_battprotectwarningtitle, R.string.general_battprotectwarningmsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_warning, restoreScrollY, isRestore, iconPaddingDp = 10f, blockUi = true
    )

    // ======= Optimize All Apps Notice =======
    private fun showOptimizeAppsDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main.checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZE_APPS
        val p = prepareDialog(R.string.general_optimizeappstitle, R.drawable.ic_dialog_update, R.string.general_optimizeappsmsg)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setNegativeButton(getString(R.string.dialog_btn_cancel)) { dlg, _ ->
                clearDialogState()
                dlg.dismiss()
            }
            .setPositiveButton(getString(R.string.general_btn_optimizeapps), null)
            .create()
        dialog = d
        styleDialog(d)
        d.setOnShowListener {
            if (!isAdded) return@setOnShowListener
            d.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                val current = dialog
                clearDialogState()
                dialog           = null
                dialogScrollView = null
                main.isBlocked = true
                current?.dismiss()
                showOptimizingAppsDialog()
            }
        }
        d.clearStateOnDismiss(AppState.FragmentDialog.OPTIMIZE_APPS)
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }

    // ======= Optimizing Apps Dialog =======
    private fun showOptimizingAppsDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main.checks.lostRootDetected) return
        if (dialog?.isShowing == true) return
        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZING_APPS
        val p = prepareDialog(R.string.general_optimizingtitle, R.drawable.ic_dialog_optimize, null, messageLayout = R.xml.dialog_progress_message)
        val messageText = p.messageView.findViewById<AppCompatTextView>(R.id.dialog_message)
        val progressBar = p.messageView.findViewById<SeslProgressBar>(R.id.progressbar)
        applyProgressMode(progressBar)
        val savedProgress = if (isRestore) appState.optimizeProgress else 0
        messageText.text = getString(R.string.dialog_progress) + if (savedProgress > 0) " ${savedProgress.fmt()}%" else ""
        progressBar.progress = savedProgress
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
            .create()
        dialog = d
        styleDialog(d)
        d.setCanceledOnTouchOutside(false)
        d.setOnShowListener {
            if (!isAdded) return@setOnShowListener
            d.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val cancelBtn = d.getButton(AlertDialog.BUTTON_NEGATIVE)
            progressBar.isIndeterminate = !appState.optimizeCancelBtnEnabled && appState.optimizeProgress == 0
            cancelBtn?.isEnabled = appState.optimizeCancelBtnEnabled
            if (!appState.optimizeCancelBtnEnabled) {
                cancelBtn?.postDelayed({
                    if (!isAdded) return@postDelayed
                    progressBar.isIndeterminate = false
                    appState.optimizeCancelBtnEnabled = true
                    cancelBtn.isEnabled = true
                }, 400)
            }
            cancelBtn?.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                cancelBtn.isEnabled = false
                appState.optimizeCancelBtnEnabled = false
                guru.cancelActiveOptimize()
            }
        }
        d.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            d.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            appState.optimizeCancelBtnEnabled      = false
            appState.optimizeCompletedSuccessfully = null
            if (appState.fragmentDialog == AppState.FragmentDialog.OPTIMIZING_APPS) clearDialogState()
        }
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
        main.isBlocked = true
        guru.onOptimizeProgress = { percent ->
            if (isAdded) {
                appState.optimizeProgress = percent
                messageText.text = getString(R.string.dialog_progress) + " ${percent.fmt()}%"
                progressBar.progress = percent
            }
        }
        attachOptimizingCallbacks()
        if (!isRestore) guru.optimizeAllApps()
    }
    // ======= Attach Optimizing =======
    private fun attachOptimizingCallbacks() {
        guru.onOptimizeComplete = { finishOptimizing(success = true) }
        guru.onOptimizeCancel   = { finishOptimizing(success = false) }
    }
    private fun finishOptimizing(success: Boolean) {
        if (!isAdded) return
        appState.optimizeProgress = 0
        guru.onOptimizeProgress   = null
        guru.onOptimizeCancel     = null
        appState.optimizeCompletedSuccessfully = success
        if (success) appState.optimizeCancelBtnEnabled = false
        val capturedDialog = dialog
        appState.persistentScope.launch {
            delay(400)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (dialog === capturedDialog) {
                    dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    dialog?.dismiss()
                    dialog           = null
                    dialogScrollView = null
                }
                if (success) {
                    appState.fragmentDialog = AppState.FragmentDialog.APPS_OPTIMIZED
                    appState.onOptimizeCompleteReady?.invoke()
                } else {
                    appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZING_CANCELED
                    appState.onOptimizeCancelReady?.invoke()
                }
            }
        }
    }

    // ======= Optimizing Canceled Dialog =======
    private fun clearCompleteCallback() { guru.onOptimizeComplete = null }
    private fun showOptimizingCanceledDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.OPTIMIZING_CANCELED, R.string.general_optimizingcanceledtitle, R.string.general_optimizingcanceledmsg,
        R.string.dialog_btn_close, R.drawable.ic_dialog_warning, restoreScrollY, isRestore,
        requireAdded = true, onPositive = { clearCompleteCallback() }, onDismissed = { clearCompleteCallback() }
    )
    // ======= Apps Optimized Dialog =======
    private fun showAppsOptimizedDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.APPS_OPTIMIZED, R.string.general_optimizedtitle, R.string.general_optimizedmsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_info, restoreScrollY, isRestore,
        requireAdded = true, onPositive = { clearCompleteCallback() }, onDismissed = { clearCompleteCallback() }
    )

    // ======= Setup =======
    private fun bindSwitch(item: SwitchItemView, key: String, default: Boolean = true, onChange: (Boolean) -> Unit) {
        item.onCheckedChangedListener = null
        item.isChecked = guruFunctions.getBool(key, default)
        item.onCheckedChangedListener = { _, checked -> onChange(checked) }
    }
    private fun setupViews() {
        optimizeApps.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            main.isBlocked = true
            showOptimizeAppsDialog()
        }
        bindSwitch(battappsOpt,       "battapps_opt")      { io { applyBattAppsOpt(it) } }
        bindSwitch(cachedappsOpt,     "cachedapps_opt")    { io { applyCachedAppsOpt(it) } }
        bindSwitch(wakelockblockOpt,  "wakelock_opt")      { io { applyWakelockBlocker(it) } }
        bindSwitch(cpuHotplug,        "cpu_hotplug_opt")   { io { applyCpuHotplug(it) } }
        bindSwitch(powerEff,          "power_effi_opt")    { io { applyPowerEff(it) } }
        bindSwitch(thermalOpt,        "thermal_opt")       { io { applyThermalOpt(it) } }
        bindSwitch(cpuEffi,           "cpu_effiboost_opt") { io { applyCpuEffiBoost(it) } }
        bindSwitch(gpuEffi,           "gpu_effiboost_opt") { io { applyGpuEffiBoost(it) } }
        bindSwitch(swapOpt,           "swap_opt")          { io { applySwapOpt(it) } }
        bindSwitch(storageOpt,        "storage_opt")       { io { applyStorageOpt(it) } }
        bindSwitch(batteryprotectOpt, "batteryprotect_opt", default = false) { checked ->
            if (checked && dialog?.isShowing != true) {
                main.isBlocked = true
                showBattProtectWarningDialog()
            }
            launchBattLevel(seekBarBattLevel.progress, checked)
            updateBattLevelState(checked)
        }
        val level = guruFunctions.getInt("batt_level", 90)
        seekBarBattLevel.progress = level
        tvBattLevel.text = "${level.fmt()}%"
        updateBattLevelState(batteryprotectOpt.isChecked)
        seekBarBattLevel.setMode(SeslSeekBar.MODE_LEVEL_BAR)
        seekBarBattLevel.setTickMark(AppCompatResources.getDrawable(requireContext(), androidx.appcompat.R.drawable.sesl_level_seekbar_tick_mark))
        seekBarBattLevel.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) { tvBattLevel.text = "${progress.fmt()}%" }
            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) { launchBattLevel(seekBar.progress) }
        })
        setupAdjustButton(btnBattLevelDown, isIncrement = false)
        setupAdjustButton(btnBattLevelUp,   isIncrement = true)
    }
    private fun isMonetActive(): Boolean = !android.provider.Settings.Secure.getString(requireContext().contentResolver, "theme_customization_overlay_packages").isNullOrEmpty()
    private fun applyProgressMode(bar: SeslProgressBar) {bar.setMode(if (isMonetActive()) 0 else SeslProgressBar.MODE_GRADIENT_HORIZONTAL)}

    // ======= SeekBar Buttons =======
    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdjustButton(button: ImageButton, isIncrement: Boolean) {
        button.setOnClickListener { adjustSeekBar(isIncrement) }
        button.setOnLongClickListener {
            longPressJob?.cancel()
            longPressJob = viewScope.launch { while (isActive) { adjustSeekBar(isIncrement); delay(300) } }
            false
        }
        button.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) longPressJob?.cancel()
            false
        }
    }
    private fun adjustSeekBar(isIncrement: Boolean) {
        val current  = seekBarBattLevel.progress
        val newValue = if (isIncrement) (current + 1).coerceAtMost(seekBarBattLevel.max)
                       else             (current - 1).coerceAtLeast(seekBarBattLevel.min)
        if (newValue == current) return
        seekBarBattLevel.progress = newValue
        tvBattLevel.text = "${newValue.fmt()}%"
        seekBarBattLevel.performHapticFeedback(hapticCursorMove)
        launchBattLevel(newValue)
    }

    // ======= Seekbar State =======
    private fun updateBattLevelState(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.4f
        seekBarBattLevel.isEnabled = enabled
        btnBattLevelDown.isEnabled = enabled
        btnBattLevelUp.isEnabled   = enabled
        btnBattLevelDown.alpha     = alpha
        btnBattLevelUp.alpha       = alpha
        tvBattLimitTitle.alpha     = alpha
        tvBattLevel.alpha          = alpha
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
        listOf(R.id.sep_system, R.id.sep_kernel, R.id.sep_cpu, R.id.sep_gpu, R.id.sep_memory, R.id.sep_storage, R.id.sep_battery)
            .forEach { id -> view.findViewById<AppCompatTextView>(id)?.fitDrawableToText(extraDp = 8f) }
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

    // ======= Apply Functions =======
    private fun io(block: suspend GuruFunctions.() -> Unit) { viewScope.launch(Dispatchers.IO) { guruFunctions.block() } }
    private fun launchBattLevel(level: Int, enabled: Boolean = batteryprotectOpt.isChecked) { io { applyBattLevel(level, enabled) } }

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