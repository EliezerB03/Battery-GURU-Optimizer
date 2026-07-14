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
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SeslProgressBar
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

class GeneralSettings : Fragment(R.layout.general_settings_lay), SearchableFragment {
    private lateinit var scrollView:        NestedScrollView
    private lateinit var optimizeApps:      CardItemView
    private lateinit var battappsOpt:       SwitchItemView
    private lateinit var cachedappsOpt:     SwitchItemView
    private lateinit var cpuHotplug:        SwitchItemView
    private lateinit var powerEff:          SwitchItemView
    private lateinit var thermalOpt:        SwitchItemView
    private lateinit var cpuEffi:           SwitchItemView
    private lateinit var gpuEffi:           SwitchItemView
    private lateinit var swapOpt:           SwitchItemView
    private lateinit var storageOpt:        SwitchItemView
    private lateinit var batteryprotectOpt: SwitchItemView
    private lateinit var seekBarBattLevel:  SeekBarPlus
    private lateinit var tvBattLimitTitle:  TextView
    private lateinit var tvBattLevel:       TextView
    private lateinit var btnBattLevelDown:  ImageButton
    private lateinit var btnBattLevelUp:    ImageButton
    private lateinit var guruFunctions:     GuruFunctions
    private var longPressJob:        Job? = null
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var searchContainerView: android.view.View? = null
    private var searchEmptyView:     android.widget.TextView? = null
    private val hapticCursorMove by lazy { SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(41) }
    private val viewScope get() = viewLifecycleOwner.lifecycleScope
    private val guru      get() = (requireActivity() as MainActivity).guru
    private val appState  get() = (requireActivity() as MainActivity).appState

    // ======= Lifecycle =======
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView          = view.findViewById(R.id.scroll_view)
        optimizeApps        = view.findViewById(R.id.optimizeapps_opt)
        battappsOpt         = view.findViewById(R.id.battapps_opt)
        cachedappsOpt       = view.findViewById(R.id.cachedapps_opt)
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
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true) {
            when (appState.fragmentDialog) {
                AppState.FragmentDialog.GENERAL_INFO,
                AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING,
                AppState.FragmentDialog.OPTIMIZE_APPS,
                AppState.FragmentDialog.OPTIMIZING_APPS,
                AppState.FragmentDialog.OPTIMIZING_CANCELED,
                AppState.FragmentDialog.APPS_OPTIMIZED -> {
                    appState.fragmentDialog        = AppState.FragmentDialog.NONE
                    appState.fragmentDialogScrollY = 0
                    appState.optimizeProgress = 0
                }
                else -> {}
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
            AppState.FragmentDialog.GENERAL_INFO                 -> showInfoDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING -> showBattProtectWarningDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.OPTIMIZE_APPS                -> showOptimizeAppsDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.OPTIMIZING_APPS              -> {
                if (!guru.isAppOptimizing) {
                    appState.fragmentDialog        = AppState.FragmentDialog.NONE
                    appState.fragmentDialogScrollY = 0
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
                        dialog?.findViewById<TextView>(R.id.dialog_message)?.text = getString(R.string.dialog_progress) + " ${percent.fmt()}%"
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
        appState.fragmentDialog = AppState.FragmentDialog.GENERAL_INFO
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.general_infotitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.general_infomsg)
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
            if (appState.fragmentDialog        == AppState.FragmentDialog.GENERAL_INFO) {
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

    // ======= Battery Protect Warning Dialog =======
    private fun showBattProtectWarningDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING
        (requireActivity() as MainActivity).isBlocked = true
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.general_battprotectwarningtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.general_battprotectwarningmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning, paddingDp = 10f)
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
            if (appState.fragmentDialog        == AppState.FragmentDialog.GENERAL_BATT_PROTECT_WARNING) {
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

    // ======= Optimize All Apps Notice =======
    private fun showOptimizeAppsDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZE_APPS
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.general_optimizeappstitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.general_optimizeappsmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_update)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton(getString(R.string.dialog_btn_cancel)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
            }
            .setPositiveButton(getString(R.string.general_btn_optimizeapps), null)
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnShowListener {
            if (!isAdded) return@setOnShowListener
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (!isAdded) return@setOnClickListener
                val current = dialog
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                dialog           = null
                dialogScrollView = null
                (requireActivity() as MainActivity).isBlocked = true
                current?.dismiss()
                showOptimizingAppsDialog()
            }
        }
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog == AppState.FragmentDialog.OPTIMIZE_APPS) {
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

    // ======= Optimizing Apps Dialog =======
    private fun showOptimizingAppsDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        if (dialog?.isShowing == true) return
        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZING_APPS
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply {text = getString(R.string.general_optimizingtitle)}
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_progress_message, null)
        val messageText = messageView.findViewById<TextView>(R.id.dialog_message)
        val progressBar = messageView.findViewById<SeslProgressBar>(R.id.progressbar)
        val savedProgress = if (isRestore) appState.optimizeProgress else 0
        messageText.text = getString(R.string.dialog_progress) + if (savedProgress > 0) " ${savedProgress.fmt()}%" else ""
        progressBar.progress = savedProgress
        applyDialogIcon(titleText, R.drawable.ic_dialog_optimize)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.setOnShowListener {
            if (!isAdded) return@setOnShowListener
            dialog?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val cancelBtn = dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
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
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            appState.optimizeCancelBtnEnabled      = false
            appState.optimizeCompletedSuccessfully = null
            if (appState.fragmentDialog == AppState.FragmentDialog.OPTIMIZING_APPS) {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
            }
        }
        dialog?.show()
        if (isRestore) dialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (requireActivity() as MainActivity).checks.attachFocusCheck(dialog!!)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
        (requireActivity() as MainActivity).isBlocked = true
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
        guru.onOptimizeComplete = {
            if (isAdded) {
                appState.optimizeProgress = 0
                guru.onOptimizeProgress   = null
                guru.onOptimizeCancel     = null
                appState.optimizeCompletedSuccessfully = true
                appState.optimizeCancelBtnEnabled      = false
                val capturedDialog = dialog
                appState.persistentScope.launch {
                    delay(400)
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        if (dialog === capturedDialog) {
                            dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            dialog?.dismiss()
                            dialog           = null
                            dialogScrollView = null
                        }
                        appState.fragmentDialog = AppState.FragmentDialog.APPS_OPTIMIZED
                        appState.onOptimizeCompleteReady?.invoke()
                    }
                }
            }
        }
        guru.onOptimizeCancel = {
            if (isAdded) {
                appState.optimizeProgress = 0
                guru.onOptimizeProgress   = null
                guru.onOptimizeCancel     = null
                appState.optimizeCompletedSuccessfully = false
                val capturedDialog = dialog
                appState.persistentScope.launch {
                    delay(400)
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        if (dialog === capturedDialog) {
                            dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            dialog?.dismiss()
                            dialog           = null
                            dialogScrollView = null
                        }
                        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZING_CANCELED
                        appState.onOptimizeCancelReady?.invoke()
                    }
                }
            }
        }
    }

    // ======= Optimizing Canceled Dialog =======
    private fun showOptimizingCanceledDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        if (!isAdded) return
        appState.fragmentDialog = AppState.FragmentDialog.OPTIMIZING_CANCELED
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.general_optimizingcanceledtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.general_optimizingcanceledmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_warning)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_close)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
                guru.onOptimizeComplete = null
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.OPTIMIZING_CANCELED) {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                guru.onOptimizeComplete        = null
            }
        }
        dialog?.show()
        if (isRestore) dialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (requireActivity() as MainActivity).checks.attachFocusCheck(dialog!!)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
    }

    // ======= Apps Optimized Dialog =======
    private fun showAppsOptimizedDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        if (!isAdded) return
        appState.fragmentDialog = AppState.FragmentDialog.APPS_OPTIMIZED
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.general_optimizedtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.general_optimizedmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_info)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_ok)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
                guru.onOptimizeComplete = null
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.APPS_OPTIMIZED) {
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                guru.onOptimizeComplete        = null
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
        optimizeApps.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            showOptimizeAppsDialog()
        }
        battappsOpt.onCheckedChangedListener = null
        battappsOpt.isChecked = guruFunctions.getBool("battapps_opt", true)
        battappsOpt.onCheckedChangedListener = { _, checked -> applyBattAppsOpt(checked) }
        cachedappsOpt.onCheckedChangedListener = null
        cachedappsOpt.isChecked = guruFunctions.getBool("cachedapps_opt", true)
        cachedappsOpt.onCheckedChangedListener = { _, checked -> applyCachedAppsOpt(checked) }
        cpuHotplug.onCheckedChangedListener = null
        cpuHotplug.isChecked = guruFunctions.getBool("cpu_hotplug_opt", true)
        cpuHotplug.onCheckedChangedListener = { _, checked -> applyCpuHotplug(checked) }
        powerEff.onCheckedChangedListener = null
        powerEff.isChecked = guruFunctions.getBool("power_effi_opt", true)
        powerEff.onCheckedChangedListener = { _, checked -> applyPowerEff(checked) }
        thermalOpt.onCheckedChangedListener = null
        thermalOpt.isChecked = guruFunctions.getBool("thermal_opt", true)
        thermalOpt.onCheckedChangedListener = { _, checked -> applyThermalOpt(checked) }
        cpuEffi.onCheckedChangedListener = null
        cpuEffi.isChecked = guruFunctions.getBool("cpu_effiboost_opt", true)
        cpuEffi.onCheckedChangedListener = { _, checked -> applyCpuEffiBoost(checked) }
        gpuEffi.onCheckedChangedListener = null
        gpuEffi.isChecked = guruFunctions.getBool("gpu_effiboost_opt", true)
        gpuEffi.onCheckedChangedListener = { _, checked -> applyGpuEffiBoost(checked) }
        swapOpt.onCheckedChangedListener = null
        swapOpt.isChecked = guruFunctions.getBool("swap_opt", true)
        swapOpt.onCheckedChangedListener = { _, checked -> applySwapOpt(checked) }
        storageOpt.onCheckedChangedListener = null
        storageOpt.isChecked = guruFunctions.getBool("storage_opt", true)
        storageOpt.onCheckedChangedListener = { _, checked -> applyStorageOpt(checked) }
        batteryprotectOpt.onCheckedChangedListener = null
        batteryprotectOpt.isChecked = guruFunctions.getBool("batteryprotect_opt", false)
        batteryprotectOpt.onCheckedChangedListener = { _, checked ->
            if (checked && dialog?.isShowing != true) {
                (requireActivity() as MainActivity).isBlocked = true
                showBattProtectWarningDialog()
            }
            applyBattProtect(checked)
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
            override fun onStopTrackingTouch(seekBar: SeslSeekBar) { applyBattLevel(seekBar.progress) }
        })
        setupAdjustButton(btnBattLevelDown, isIncrement = false)
        setupAdjustButton(btnBattLevelUp,   isIncrement = true)
    }

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
        applyBattLevel(newValue)
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
        listOf(R.id.sep_system, R.id.sep_cpu, R.id.sep_gpu, R.id.sep_memory, R.id.sep_storage, R.id.sep_battery)
            .forEach { id -> view.findViewById<TextView>(id)?.fitDrawableToText(extraDp = 8f) }
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
    private fun applyBattAppsOpt(enabled: Boolean)   { viewScope.launch(Dispatchers.IO) { guruFunctions.applyBattAppsOpt(enabled) } }
    private fun applyCachedAppsOpt(enabled: Boolean) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyCachedAppsOpt(enabled) } }
    private fun applyCpuHotplug(enabled: Boolean)    { viewScope.launch(Dispatchers.IO) { guruFunctions.applyCpuHotplug(enabled) } }
    private fun applyPowerEff(enabled: Boolean)      { viewScope.launch(Dispatchers.IO) { guruFunctions.applyPowerEff(enabled) } }
    private fun applyThermalOpt(enabled: Boolean)    { viewScope.launch(Dispatchers.IO) { guruFunctions.applyThermalOpt(enabled) } }
    private fun applyCpuEffiBoost(enabled: Boolean)  { viewScope.launch(Dispatchers.IO) { guruFunctions.applyCpuEffiBoost(enabled) } }
    private fun applyGpuEffiBoost(enabled: Boolean)  { viewScope.launch(Dispatchers.IO) { guruFunctions.applyGpuEffiBoost(enabled) } }
    private fun applySwapOpt(enabled: Boolean)       { viewScope.launch(Dispatchers.IO) { guruFunctions.applySwapOpt(enabled) } }
    private fun applyStorageOpt(enabled: Boolean)    { viewScope.launch(Dispatchers.IO) { guruFunctions.applyStorageOpt(enabled) } }
    private fun applyBattProtect(enabled: Boolean)    { viewScope.launch(Dispatchers.IO) { guruFunctions.applyBattLevel(seekBarBattLevel.progress, enabled) } }
    private fun applyBattLevel(level: Int, forceEnabled: Boolean = batteryprotectOpt.isChecked) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyBattLevel(level, forceEnabled) } }

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
}