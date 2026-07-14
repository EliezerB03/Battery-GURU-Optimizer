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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.widget.CardItemView
import dev.oneuiproject.oneui.widget.SwitchItemView
import kotlinx.coroutines.*
import org.json.JSONObject

class AboutGuru : Fragment(R.layout.about_guru_lay), SearchableFragment {
    private lateinit var scrollView:           NestedScrollView
    private lateinit var appVersion:           CardItemView
    private lateinit var github_developerpage: CardItemView
    private lateinit var github_sourcepage:    CardItemView
    private lateinit var changelog:            CardItemView
    private lateinit var checkUpdate:          CardItemView
    private lateinit var importSettings:       CardItemView
    private lateinit var exportSettings:       CardItemView
    private lateinit var importLauncher:       ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher:       ActivityResultLauncher<String>
    private lateinit var limitBackground:      SwitchItemView
    private lateinit var forceDoze:            SwitchItemView
    private lateinit var nsdOpt:               SwitchItemView
    private lateinit var resetSettings:        Button
    private lateinit var guruFunctions:        GuruFunctions
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var searchContainerView: android.view.View? = null
    private var searchEmptyView:     android.widget.TextView? = null
    private val viewScope get() = viewLifecycleOwner.lifecycleScope
    private val appState get()  = (requireActivity() as MainActivity).appState

    // ======= Lifecycle =======
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            handleImport(uri)
        }
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri ?: return@registerForActivityResult
            handleExport(uri)
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView           = view.findViewById(R.id.scroll_view)
        appVersion           = view.findViewById(R.id.app_version)
        github_developerpage = view.findViewById(R.id.developer)
        github_sourcepage    = view.findViewById(R.id.github)
        changelog            = view.findViewById(R.id.changelog)
        checkUpdate          = view.findViewById(R.id.check_update)
        importSettings       = view.findViewById(R.id.import_settings)
        exportSettings       = view.findViewById(R.id.export_settings)
        limitBackground      = view.findViewById(R.id.limitbackground_opt)
        forceDoze            = view.findViewById(R.id.forcedoze_opt)
        resetSettings        = view.findViewById(R.id.reset_settings)
        nsdOpt               = view.findViewById(R.id.nsd_opt)
        guruFunctions        = GuruFunctions((requireActivity() as MainActivity).guru, requireContext())
        appState.attachScroll("about", scrollView)
        if (!isHidden) {
            requireActivity().invalidateOptionsMenu()
            (activity as? MainActivity)?.updateTitle(getString(R.string.about_toolbartitle))
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
        }
        setupViews()
        fixSeparatorIcons(view)
        restoreDialogIfNeeded()
    }
    override fun onStart() {
        super.onStart()
        appState.restoreScroll("about")
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            (activity as? MainActivity)?.resetScroll(scrollView)
            requireActivity().removeMenuProvider(menuProvider)
        } else {
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
            (activity as? MainActivity)?.animateToolbar(false, getString(R.string.about_toolbartitle), 3)
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
        appState.saveScroll("about")
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
        appState.detachScroll("about", activity?.isChangingConfigurations == true)
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true) {
            when (appState.fragmentDialog) {
                AppState.FragmentDialog.ABOUT_INFO,
                AppState.FragmentDialog.CHANGELOG,
                AppState.FragmentDialog.LABS_WARNING,
                AppState.FragmentDialog.RESET,
                AppState.FragmentDialog.RESETTING,
                AppState.FragmentDialog.IMPORT_ERROR,
                AppState.FragmentDialog.IMPORT_SUCESS,
                AppState.FragmentDialog.EXPORT_SUCESS -> {
                    appState.fragmentDialog        = AppState.FragmentDialog.NONE
                    appState.fragmentDialogScrollY = 0
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
            AppState.FragmentDialog.ABOUT_INFO    -> showInfoDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.CHANGELOG     -> showChangelogDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.LABS_WARNING  -> showWarningDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.RESET         -> showResetDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.RESETTING     -> showResettingDialog(restoreScrollY = scrollY, startReset = false, isRestore = true)
            AppState.FragmentDialog.IMPORT_ERROR  -> showImportErrorDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.IMPORT_SUCESS -> showImportSuccessDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.EXPORT_SUCESS -> showExportSuccessDialog(restoreScrollY = scrollY, isRestore = true)
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

    // ======= Header Info Dialog =======
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
        appState.fragmentDialog = AppState.FragmentDialog.ABOUT_INFO
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_infotitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_infomsg)
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
            if (appState.fragmentDialog        == AppState.FragmentDialog.ABOUT_INFO) {
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

    // ======= Changelog Dialog =======
    private fun showChangelogDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.CHANGELOG
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_changelogtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = """
            ⦁ App rebuilt from scratch (fully native)
            ⦁ Added color palette support
            ⦁ Added support for multiple languages
                - Spanish
                - Portuguese
                - French
                - Japanese
                - Arabic
                - Indonesian
                - Turkish
                - Romanian
            ⦁ Added new 'Device status' section to monitor your device usage
            ⦁ Added 'Search' function to all sections
            ⦁ Merged 'Freqs/UV settings' into to new 'Advanced settings' section
            ⦁ Added function to import/export settings
            ⦁ Added 'Optimize all apps' option
            ⦁ Added 'Freeze cached apps' option
            ⦁ Added 'CPU efficiency boost' option
            ⦁ Added 'GPU efficiency boost' option
            ⦁ Added 'Disable network service discovery' option (Experimental)
            ⦁ Removed 'GPU boost performance' option
            ⦁ App performance improvements
            ⦁ App update improvements
            ⦁ Other many improvements
        """.trimIndent()
        applyDialogIcon(titleText, R.drawable.ic_dialog_changelog)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_close)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.CHANGELOG) {
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

    // ======= Warning Dialog =======
    private fun showWarningDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.LABS_WARNING
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_labs_warningtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_labs_warningmsg)
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
            if (appState.fragmentDialog        == AppState.FragmentDialog.LABS_WARNING) {
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

    // ======= Reset Dialog =======
    private fun showResetDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.RESET
        val builder      = AlertDialog.Builder(requireContext())
        val titleView    = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText    = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_resettitle) }
        val messageView  = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_resetmsg)
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_dialog_reset)?.let { drawable ->
            val fm       = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size     = (baseSize * 1.2f).toInt()
            val offsetV  = ((size - baseSize) / 2)
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
        }
        val scrollView   = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton(getString(R.string.dialog_btn_close)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
            }
            .setPositiveButton(getString(R.string.about_reset_btn_confirm), null)
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnShowListener {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                setOnClickListener {
                    val current = dialog
                    appState.fragmentDialog        = AppState.FragmentDialog.NONE
                    appState.fragmentDialogScrollY = 0
                    dialog           = null
                    dialogScrollView = null
                    (requireActivity() as MainActivity).isBlocked = true
                    current?.dismiss()
                    showResettingDialog()
                }
            }
        }
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.RESET) {
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

    // ======= Resetting Dialog =======
    private fun showResettingDialog(restoreScrollY: Int = 0, startReset: Boolean = true, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        if (dialog?.isShowing == true) return
        appState.fragmentDialog = AppState.FragmentDialog.RESETTING
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_loading_title, null)
        titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_resettingtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_resettingmsg)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.show()
        if (isRestore) dialog?.window?.setWindowAnimations(R.style.DialogAnimationNoEnter)
        (requireActivity() as MainActivity).checks.attachFocusCheck(dialog!!)
        if (restoreScrollY > 0) scrollView?.post { scrollView.scrollTo(0, restoreScrollY) }
        dialogScrollView = scrollView
        (requireActivity() as MainActivity).isBlocked = true
        if (startReset) (requireActivity() as MainActivity).guru.resetToDefault()
    }

    // ======= Import Error Dialog =======
    private fun showImportErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.IMPORT_ERROR
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_import_errortitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_import_errormsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_error, tint = false)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.dialog_btn_close)) { d, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                d.dismiss()
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.IMPORT_ERROR) {
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

    // ======= Import Success Dialog =======
    private fun showImportSuccessDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.IMPORT_SUCESS
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_import_successtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_import_successmsg)
        applyDialogIcon(titleText, R.drawable.ic_dialog_info)
        val scrollView  = setupScrollIndicators(messageView)
        dialog = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton(getString(R.string.about_btn_restart)) { _, _ ->
                appState.fragmentDialog        = AppState.FragmentDialog.NONE
                appState.fragmentDialogScrollY = 0
                (requireActivity() as MainActivity).guru.restartApp()
            }
            .create()
        dialog?.seslSetBackgroundBlurEnabled(true)
        dialog?.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
        dialog?.setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog        == AppState.FragmentDialog.IMPORT_SUCESS) {
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

    // ======= Export Success Dialog =======
    private fun showExportSuccessDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if ((requireActivity() as MainActivity).checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.EXPORT_SUCESS
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_icon_title, null)
        val titleText   = titleView.findViewById<TextView>(R.id.dialog_title).apply { text = getString(R.string.about_export_successtitle) }
        val messageView = layoutInflater.cloneInContext(builder.context).inflate(R.xml.dialog_message, null)
        messageView.findViewById<TextView>(R.id.dialog_message).text = getString(R.string.about_export_successmsg)
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
            if (appState.fragmentDialog        == AppState.FragmentDialog.EXPORT_SUCESS) {
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
        github_developerpage.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EliezerB03")))
        }
        github_sourcepage.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EliezerB03/Battery-GURU-Optimizer?tab=readme-ov-file#%E3%85%A4")))
        }
        changelog.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            showChangelogDialog()
        }
        checkUpdate.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            (requireActivity() as MainActivity).updater.checkForUpdates()
        }
        importSettings.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
        exportSettings.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            exportLauncher.launch("bg-settings.bgs")
        }
        limitBackground.onCheckedChangedListener = null
        limitBackground.isChecked = guruFunctions.getBool("limitprocess_opt", false)
        limitBackground.onCheckedChangedListener = { _, checked ->
            if (checked && dialog?.isShowing != true) {
                (requireActivity() as MainActivity).isBlocked = true
                showWarningDialog()
            }
            applyLimitBackground(checked)
        }
        forceDoze.onCheckedChangedListener = null
        forceDoze.isChecked = guruFunctions.getBool("forcedoze_opt", false)
        forceDoze.onCheckedChangedListener = { _, checked ->
            if (checked && dialog?.isShowing != true) {
                (requireActivity() as MainActivity).isBlocked = true
                showWarningDialog()
            }
            applyForceDoze(checked)
        }
        nsdOpt.onCheckedChangedListener = null
        nsdOpt.isChecked = guruFunctions.getBool("nsd_opt", false)
        nsdOpt.onCheckedChangedListener = { _, checked -> applyNSDOpt(checked) }
        resetSettings.setOnClickListener {
            if (dialog?.isShowing == true) return@setOnClickListener
            (requireActivity() as MainActivity).isBlocked = true
            showResetDialog()
        }
    }

    // ======= Helpers =======
    private fun applyDialogIcon(titleText: TextView, drawableRes: Int, tint: Boolean = true, paddingDp: Float = 8f) {
        ContextCompat.getDrawable(requireContext(), drawableRes)?.let { drawable ->
            val fm       = titleText.paint.fontMetricsInt
            val baseSize = (-fm.ascent + fm.descent) + fm.leading
            val size     = (baseSize * 1.2f).toInt()
            val offsetV  = ((size - baseSize) / 2)
            drawable.setBounds(0, -offsetV, size, size - offsetV)
            if (tint) drawable.setTint(titleText.currentTextColor)
            titleText.setCompoundDrawablesRelative(drawable, null, null, null)
            titleText.compoundDrawablePadding = (paddingDp * resources.displayMetrics.density).toInt()
        }
    }

    // ======= Scroll Indicators =======
    private fun setupScrollIndicators(messageView: View): NestedScrollView? {
        val scrollView   = messageView.findViewById<NestedScrollView>(R.id.dialog_scroll) ?: return null
        val indicatorTop = messageView.findViewById<View>(R.id.scrollIndicatorUp)
        val indicatorBot = messageView.findViewById<View>(R.id.scrollIndicatorDown)
        fun update() {
            indicatorTop.visibility = if (scrollView.canScrollVertically(-1)) View.VISIBLE else View.GONE
            indicatorBot.visibility = if (scrollView.canScrollVertically(1))  View.VISIBLE else View.GONE
        }
        scrollView.post { update() }
        scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> update() })
        return scrollView
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
        listOf(R.id.sep_about_battery_guru, R.id.sep_devinfo, R.id.sep_updates, R.id.sep_importexport, R.id.sep_labs)
            .forEach { id -> view.findViewById<TextView>(id)?.fitDrawableToText(extraDp = 8f) }
    }

    // ======= Apply Functions =======
    private fun applyLimitBackground(enabled: Boolean) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyLimitBackground(enabled) } }
    private fun applyForceDoze(enabled: Boolean) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyForceDoze(enabled) } }
    private fun applyNSDOpt(enabled: Boolean) { viewScope.launch(Dispatchers.IO) { guruFunctions.applyNSDOpt(enabled) } }

    // ======= Import/Export Function =======
    private fun handleImport(uri: Uri) {
        try {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("Cannot read file")
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val requiredKeys = listOf(
                "show_memory", "temp_fahrenheit_unit",
                "battapps_opt", "cachedapps_opt", "cpu_hotplug_opt", "power_effi_opt", "thermal_opt", "cpu_effiboost_opt", "gpu_effiboost_opt", "swap_opt", "storage_opt", "batteryprotect_opt", "batt_level",
                "frq_on", "frq_little", "frq_big", "volt_on",
                "limitprocess_opt", "forcedoze_opt", "nsd_opt"
            )
            if (!requiredKeys.all { json.has(it) }) throw Exception("Missing keys")
            val prefs = requireContext().getSharedPreferences("guru_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().apply {
                listOf(
                    "show_memory", "temp_fahrenheit_unit",
                    "battapps_opt", "cachedapps_opt", "cpu_hotplug_opt", "power_effi_opt", "thermal_opt", "cpu_effiboost_opt", "gpu_effiboost_opt", "swap_opt", "storage_opt", "batteryprotect_opt",
                    "frq_on", "volt_on",
                    "limitprocess_opt", "forcedoze_opt", "nsd_opt"
                ).forEach { key -> putBoolean(key, json.getBoolean(key)) }
                putInt("batt_level", json.getInt("batt_level"))
                putInt("frq_little", json.getInt("frq_little"))
                putInt("frq_big",    json.getInt("frq_big"))
                listOf(
                    "big_volt", "lit_volt", "g3d_volt", "mif_volt", "aud_volt",
                    "cam_volt", "cp_volt", "disp_volt", "fsys0_volt", "int_volt",
                    "intcam_volt", "iva_volt", "score_volt"
                ).forEach { key -> if (json.has(key)) putInt(key, json.getInt(key)) }
            }.apply()
            showImportSuccessDialog()
        } catch (_: Exception) {
            showImportErrorDialog()
        }
    }
    private fun handleExport(uri: Uri) {
        try {
            val prefs = requireContext().getSharedPreferences("guru_settings", android.content.Context.MODE_PRIVATE)
            val boolDefaults = mapOf(
                "show_memory"           to true,
                "temp_fahrenheit_unit"  to false,
                "battapps_opt"          to true,
                "cachedapps_opt"        to true,
                "cpu_hotplug_opt"       to true,
                "power_effi_opt"        to true,
                "thermal_opt"           to true,
                "cpu_effiboost_opt"     to true,
                "gpu_effiboost_opt"     to true,
                "swap_opt"              to true,
                "storage_opt"           to true,
                "batteryprotect_opt"    to false,
                "frq_on"                to true,
                "volt_on"               to true,
                "limitprocess_opt"      to false,
                "forcedoze_opt"         to false,
                "nsd_opt"               to false
            )
            val intDefaults = mapOf(
                "batt_level"  to 90,
                "frq_little"  to 1690000,
                "frq_big"     to 1690000,
                "big_volt"    to -4,
                "lit_volt"    to -4,
                "g3d_volt"    to -4,
                "mif_volt"    to -4,
                "aud_volt"    to -4,
                "cam_volt"    to -4,
                "cp_volt"     to 0,
                "disp_volt"   to -4,
                "fsys0_volt"  to -4,
                "int_volt"    to -4,
                "intcam_volt" to -4,
                "iva_volt"    to -4,
                "score_volt"  to -4
            )
            val json = JSONObject()
            boolDefaults.forEach { (key, default) -> json.put(key, prefs.getBoolean(key, default)) }
            intDefaults.forEach  { (key, default) -> json.put(key, prefs.getInt(key, default))     }
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            showExportSuccessDialog()
        } catch (_: Exception) {}
    }

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