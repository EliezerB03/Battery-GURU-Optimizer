package com.guru.batteryoptimizer.fragments
import com.guru.batteryoptimizer.BuildConfig
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
import com.guru.batteryoptimizer.widgets.semSetToolTipText
import com.guru.batteryoptimizer.widgets.SwitchItemView
import com.guru.batteryoptimizer.widgets.UpdatableWidget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SeslProgressBar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject

class AboutGuru : Fragment(R.layout.about_guru_lay), SearchableFragment {
    private val viewScope get() = viewLifecycleOwner.lifecycleScope
    private val main     get() = requireActivity() as MainActivity
    private val appState get()  = main.appState
    private var iconPaletteApplied = false
    private var dialog:              AlertDialog? = null
    private var dialogScrollView:    NestedScrollView? = null
    private var searchContainerView: View? = null
    private var searchEmptyView:     AppCompatTextView? = null
    private lateinit var scrollView:           NestedScrollView
    private lateinit var appIconHeader:        AppCompatImageView
    private lateinit var appVersion:           AppCompatTextView
    private lateinit var appDeveloper:         AppCompatTextView
    private lateinit var github_sourcepage:    AppCompatImageButton
    private lateinit var github_developer:     AppCompatImageButton
    private lateinit var checkUpdate:          CardItemView
    private lateinit var checkUpdateWidget:    UpdatableWidget
    private lateinit var changelog:            CardItemView
    private lateinit var importSettings:       CardItemView
    private lateinit var exportSettings:       CardItemView
    private lateinit var importLauncher:       ActivityResultLauncher<Array<String>>
    private lateinit var exportLauncher:       ActivityResultLauncher<String>
    private lateinit var limitBackground:      SwitchItemView
    private lateinit var forceDoze:            SwitchItemView
    private lateinit var nsdOpt:               SwitchItemView
    private lateinit var resetSettings:        AppCompatButton
    private lateinit var guruFunctions:        GuruFunctions
    private companion object {
        val ABOUT_DIALOGS = setOf(
            AppState.FragmentDialog.ABOUT_INFO, AppState.FragmentDialog.CHANGELOG, AppState.FragmentDialog.LABS_WARNING,
            AppState.FragmentDialog.RESET, AppState.FragmentDialog.RESETTING, AppState.FragmentDialog.IMPORT_WARNING,
            AppState.FragmentDialog.IMPORT_ERROR, AppState.FragmentDialog.IMPORT_SUCESS, AppState.FragmentDialog.EXPORT_SUCESS
        )
        val BOOL_DEFAULTS = mapOf(
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
        val VOLT_KEYS = listOf(
            "big_volt", "lit_volt", "g3d_volt", "mif_volt", "aud_volt", "cam_volt", "cp_volt",
            "disp_volt", "fsys0_volt", "int_volt", "intcam_volt", "iva_volt", "score_volt"
        )
        val INT_DEFAULTS = mapOf("batt_level" to 90, "frq_little" to 1690000, "frq_big" to 1690000) +
            VOLT_KEYS.associateWith { if (it == "cp_volt") 0 else -4 }
    }

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
        appIconHeader        = view.findViewById(R.id.about_app_icon_header)
        appVersion           = view.findViewById(R.id.about_app_version_header)
        appVersion.text      = getString(R.string.about_appversion, BuildConfig.VERSION_NAME)
        appDeveloper         = view.findViewById(R.id.about_app_developer_header)
        appDeveloper.text    = getString(R.string.about_developer, "EliezerB03")
        github_sourcepage    = view.findViewById(R.id.github)
        github_developer     = view.findViewById(R.id.developer)
        checkUpdate          = view.findViewById(R.id.check_update)
        checkUpdateWidget    = view.findViewById(R.id.check_update_widget)
        checkUpdateWidget.onWidgetInflated = {widgetView -> applyWidgetDots(widgetView)}
        changelog            = view.findViewById(R.id.changelog)
        importSettings       = view.findViewById(R.id.import_settings)
        exportSettings       = view.findViewById(R.id.export_settings)
        limitBackground      = view.findViewById(R.id.limitbackground_opt)
        forceDoze            = view.findViewById(R.id.forcedoze_opt)
        resetSettings        = view.findViewById(R.id.reset_settings)
        nsdOpt               = view.findViewById(R.id.nsd_opt)
        guruFunctions        = GuruFunctions(main.guru, requireContext())
        applyAppIcon()
        appState.attachScroll("about", scrollView)
        if (!isHidden) {
            requireActivity().invalidateOptionsMenu()
            (activity as? MainActivity)?.updateTitle(getString(R.string.about_toolbartitle))
            requireActivity().removeMenuProvider(menuProvider)
            requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
        }
        main.updater.onWidgetStateChanged = { state -> applyWidgetState(state) }
        main.updater.onCheckingStateChanged = { checking ->
            checkUpdate.isClickable  = !checking
            checkUpdate.isFocusable  = !checking
        }
        applyWidgetState(appState.updaterWidgetState)
        val isChecking = appState.updaterWidgetState == AppState.UpdaterWidgetState.CHECKING_UPDATE
        checkUpdate.isClickable = !isChecking
        checkUpdate.isFocusable = !isChecking
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
                if (query.isBlank()) { showSearchResults(emptyList(), isInitialState = true) }
                else { showSearchResults(SearchManager.search(query)) }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (isIconPaletteActive() != iconPaletteApplied) applyAppIcon()
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
        (activity as? MainActivity)?.updater?.onWidgetStateChanged = null
        (activity as? MainActivity)?.updater?.onCheckingStateChanged = null
        if (dialog?.isShowing != true && activity?.isChangingConfigurations != true && appState.fragmentDialog in ABOUT_DIALOGS) clearDialogState()
        dialog           = null
        dialogScrollView = null
    }

    // ======= Restore Dialog =======
    private fun restoreDialogIfNeeded() {
        if (dialog?.isShowing == true) return
        val scrollY = appState.fragmentDialogScrollY
        when (appState.fragmentDialog) {
            AppState.FragmentDialog.ABOUT_INFO      -> showInfoDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.CHANGELOG       -> showChangelogDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.LABS_WARNING    -> showWarningDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.RESET           -> showResetDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.RESETTING       -> showResettingDialog(restoreScrollY = scrollY, startReset = false, isRestore = true)
            AppState.FragmentDialog.IMPORT_WARNING  -> showImportWarningDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.IMPORT_ERROR    -> showImportErrorDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.IMPORT_SUCESS   -> showImportSuccessDialog(restoreScrollY = scrollY, isRestore = true)
            AppState.FragmentDialog.EXPORT_SUCESS   -> showExportSuccessDialog(restoreScrollY = scrollY, isRestore = true)
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
    private fun inflateDialogView(builder: AlertDialog.Builder, layout: Int): View =
        layoutInflater.cloneInContext(builder.context).inflate(layout, null)
    private class DialogParts(val builder: AlertDialog.Builder, val titleView: View, val messageView: View, val scrollView: NestedScrollView?)
    private fun prepareDialog(
        @StringRes title: Int, @DrawableRes icon: Int, @StringRes message: Int?, messageText: String? = null,
        paddingDp: Float = 8f, tintIcon: Boolean = true
    ): DialogParts {
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = inflateDialogView(builder, R.xml.dialog_icon_title)
        val titleText   = titleView.findViewById<AppCompatTextView>(R.id.dialog_title).apply { text = getString(title) }
        val messageView = inflateDialogView(builder, R.xml.dialog_message)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = messageText ?: getString(message!!)
        applyDialogIcon(titleText, icon, tintIcon, paddingDp)
        return DialogParts(builder, titleView, messageView, setupScrollIndicators(messageView))
    }
    private fun styleDialog(d: AlertDialog) {
        d.seslSetBackgroundBlurEnabled(true)
        d.window?.setBackgroundDrawableResource(androidx.appcompat.R.drawable.sesl_dialog_inset_background)
    }
    private fun AlertDialog.clearStateOnDismiss(state: AppState.FragmentDialog) {
        setOnDismissListener {
            if (!isAdded) return@setOnDismissListener
            if (appState.fragmentDialog == state) clearDialogState()
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
        state: AppState.FragmentDialog, @StringRes title: Int, @StringRes message: Int?, @StringRes button: Int, @DrawableRes icon: Int,
        restoreScrollY: Int, isRestore: Boolean, messageText: String? = null, iconPaddingDp: Float = 8f, tintIcon: Boolean = true,
        cancelable: Boolean = true, onPositive: (() -> Unit)? = null
    ) {
        if (main.checks.lostRootDetected) return
        appState.fragmentDialog = state
        val p = prepareDialog(title, icon, message, messageText, iconPaddingDp, tintIcon)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setCancelable(cancelable)
            .setPositiveButton(getString(button)) { dlg, _ ->
                clearDialogState()
                dlg.dismiss()
                onPositive?.invoke()
            }
            .create()
        dialog = d
        styleDialog(d)
        if (!cancelable) d.setCanceledOnTouchOutside(false)
        d.clearStateOnDismiss(state)
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }
    private fun View.onTapUnlessDialog(action: () -> Unit) = setOnClickListener {
        if (dialog?.isShowing == true) return@setOnClickListener
        main.isBlocked = true
        action()
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
        AppState.FragmentDialog.ABOUT_INFO, R.string.about_infotitle, R.string.about_infomsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_info, restoreScrollY, isRestore
    )

    // ======= Changelog Dialog =======
    private fun showChangelogDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.CHANGELOG, R.string.about_changelogtitle, null,
        R.string.dialog_btn_close, R.drawable.ic_dialog_changelog, restoreScrollY, isRestore,
        messageText = """
            ⦁ UI theme updated to One UI 9 design
            ⦁ Added Bulgarian language
            ⦁ Added 'Wakelock blocker optimizations' option
            ⦁ Revamped 'About GURU' section
            ⦁ Refactored 'Check for app update' option
            ⦁ Re-adjusted 'ZRAM optimizations' option
            ⦁ Minor tweak on 'Thermal optimizations' option
            ⦁ Improved Import/Export option logic
            ⦁ Improved several descriptions across all app pages for all languages
            ⦁ Improved app performance
            ⦁ Various code optimizations
            ⦁ Improved logic and various bug fixes
        """.trimIndent()
    )

    // ======= Warning Dialog =======
    private fun showWarningDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.LABS_WARNING, R.string.about_labs_warningtitle, R.string.about_labs_warningmsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_warning, restoreScrollY, isRestore, iconPaddingDp = 10f
    )

    // ======= Reset Dialog =======
    private fun showResetDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main.checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.RESET
        val p = prepareDialog(R.string.about_resettitle, R.drawable.ic_dialog_reset, R.string.about_resetmsg, paddingDp = 10f, tintIcon = false)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setNegativeButton(getString(R.string.dialog_btn_close)) { dlg, _ ->
                clearDialogState()
                dlg.dismiss()
            }
            .setPositiveButton(getString(R.string.about_reset_btn_confirm), null)
            .create()
        dialog = d
        styleDialog(d)
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                setOnClickListener {
                    val current = dialog
                    clearDialogState()
                    dialog           = null
                    dialogScrollView = null
                    main.isBlocked = true
                    current?.dismiss()
                    showResettingDialog()
                }
            }
        }
        d.clearStateOnDismiss(AppState.FragmentDialog.RESET)
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }

    // ======= Resetting Dialog =======
    private fun showResettingDialog(restoreScrollY: Int = 0, startReset: Boolean = true, isRestore: Boolean = false) {
        if (main.checks.lostRootDetected) return
        if (dialog?.isShowing == true) return
        appState.fragmentDialog = AppState.FragmentDialog.RESETTING
        val builder     = AlertDialog.Builder(requireContext())
        val titleView   = inflateDialogView(builder, R.xml.dialog_loading_title)
        titleView.findViewById<AppCompatTextView>(R.id.dialog_title).text = getString(R.string.about_resettingtitle)
        applyDotsProgress(titleView.findViewById(R.id.dialog_loading_progress))
        val messageView = inflateDialogView(builder, R.xml.dialog_message)
        messageView.findViewById<AppCompatTextView>(R.id.dialog_message).text = getString(R.string.about_resettingmsg)
        val scrollView  = setupScrollIndicators(messageView)
        val d = builder
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .create()
        dialog = d
        styleDialog(d)
        d.setCanceledOnTouchOutside(false)
        presentDialog(d, scrollView, restoreScrollY, isRestore)
        main.isBlocked = true
        if (startReset) main.guru.resetToDefault()
    }

    // ======= Import Warning Dialog =======
    private fun showImportWarningDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) {
        if (main.checks.lostRootDetected) return
        appState.fragmentDialog = AppState.FragmentDialog.IMPORT_WARNING
        val p = prepareDialog(R.string.about_import_warningtitle, R.drawable.ic_dialog_update, R.string.about_import_warningmsg)
        val d = p.builder
            .setCustomTitle(p.titleView)
            .setView(p.messageView)
            .setNegativeButton(getString(R.string.dialog_btn_cancel)) { dlg, _ ->
                clearDialogState()
                dlg.dismiss()
            }
            .setPositiveButton(getString(R.string.about_import_warning_btn_import), null)
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
                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                current?.dismiss()
            }
        }
        d.clearStateOnDismiss(AppState.FragmentDialog.IMPORT_WARNING)
        presentDialog(d, p.scrollView, restoreScrollY, isRestore)
    }

    // ======= Import Error =======
    private fun showImportErrorDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.IMPORT_ERROR, R.string.about_import_errortitle, R.string.about_import_errormsg,
        R.string.dialog_btn_close, R.drawable.ic_dialog_error, restoreScrollY, isRestore, tintIcon = false
    )
    // ======= Import Success =======
    private fun showImportSuccessDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.IMPORT_SUCESS, R.string.about_import_successtitle, R.string.about_import_successmsg,
        R.string.about_btn_restart, R.drawable.ic_dialog_info, restoreScrollY, isRestore,
        cancelable = false, onPositive = { main.guru.restartApp() }
    )
    // ======= Export Success Dialogs =======
    private fun showExportSuccessDialog(restoreScrollY: Int = 0, isRestore: Boolean = false) = showNotice(
        AppState.FragmentDialog.EXPORT_SUCESS, R.string.about_export_successtitle, R.string.about_export_successmsg,
        R.string.dialog_btn_ok, R.drawable.ic_dialog_info, restoreScrollY, isRestore
    )

    // ======= Setup =======
    private fun io(block: suspend GuruFunctions.() -> Unit) { viewScope.launch(Dispatchers.IO) { guruFunctions.block() } }
    private fun bindSwitch(item: SwitchItemView, key: String, default: Boolean = false, onChange: (Boolean) -> Unit) {
        item.onCheckedChangedListener = null
        item.isChecked = guruFunctions.getBool(key, default)
        item.onCheckedChangedListener = { _, checked -> onChange(checked) }
    }
    private fun warnIfEnabled(checked: Boolean) {
        if (checked && dialog?.isShowing != true) {
            main.isBlocked = true
            showWarningDialog()
        }
    }
    private fun setupViews() {
        github_sourcepage.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EliezerB03/Battery-GURU-Optimizer?tab=readme-ov-file#%E3%85%A4")))
        }
        github_developer.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/EliezerB03")))
        }
        github_sourcepage.semSetToolTipText(getString(R.string.about_githubsummary))
        github_developer.semSetToolTipText(getString(R.string.about_developersummary))
        checkUpdate.setOnClickListener { main.updater.checkForUpdates() }
        changelog.onTapUnlessDialog { showChangelogDialog() }
        importSettings.onTapUnlessDialog { showImportWarningDialog() }
        exportSettings.onTapUnlessDialog { exportLauncher.launch("bg-settings.bgs") }
        bindSwitch(limitBackground, "limitprocess_opt") { checked -> warnIfEnabled(checked); io { applyLimitBackground(checked) } }
        bindSwitch(forceDoze,       "forcedoze_opt")    { checked -> warnIfEnabled(checked); io { applyForceDoze(checked) } }
        bindSwitch(nsdOpt,          "nsd_opt")          { checked -> io { applyNSDOpt(checked) } }
        resetSettings.onTapUnlessDialog { showResetDialog() }
    }

    // ======= Helpers =======
    private fun isMonetActive(): Boolean = !android.provider.Settings.Secure.getString(requireContext().contentResolver, "theme_customization_overlay_packages").isNullOrEmpty()
    private fun isIconPaletteActive(): Boolean = isMonetActive() && android.provider.Settings.System.getInt(requireContext().contentResolver, "colortheme_app_icon", 0) == 1
    private fun applyDotsProgress(bar: SeslProgressBar?) {if (isMonetActive()) bar?.indeterminateDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.progress_dots_monet)}
    private fun applyWidgetDots(widgetView: View) {
        if (!isMonetActive()) return
        (widgetView as? SeslProgressBar)?.indeterminateDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.progress_dots_monet)
    }
    private fun applyAppIcon() {
        iconPaletteApplied = isIconPaletteActive()
        val ctx  = requireContext()
        val original = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher) ?: return
        val mono = if (isIconPaletteActive()) (original as? AdaptiveIconDrawable)?.monochrome?.mutate() else null
        val base = if (mono != null) {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bg = ContextCompat.getColor(ctx, if (night) android.R.color.system_accent1_500 else android.R.color.system_accent1_400)
            val fg = ContextCompat.getColor(ctx, if (night) android.R.color.system_accent1_1000 else android.R.color.system_accent1_0)
            mono.setTint(fg)
            AdaptiveIconDrawable(ColorDrawable(bg), mono)
        } else original
        val layers = listOfNotNull(base, ContextCompat.getDrawable(ctx, R.drawable.embo_shadow_app), ContextCompat.getDrawable(ctx, R.drawable.embo_highlight_app))
        appIconHeader.setImageDrawable(LayerDrawable(layers.toTypedArray()))
    }
    private fun applyDialogIcon(titleText: AppCompatTextView, drawableRes: Int, tint: Boolean = true, paddingDp: Float = 8f) {
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
    private fun applyWidgetState(state: AppState.UpdaterWidgetState) {
        val (layout, summary) = when (state) {
            AppState.UpdaterWidgetState.CHECKING_UPDATE  -> R.drawable.widget_check_progress  to R.string.updater_widget_checkingupdate
            AppState.UpdaterWidgetState.UP_TO_DATE       -> R.drawable.widget_check_mark      to R.string.updater_widget_uptodate
            AppState.UpdaterWidgetState.UPDATE_AVAILABLE -> R.drawable.widget_check_available to R.string.updater_widget_updateavailable
            AppState.UpdaterWidgetState.INSTALL_UPDATE   -> R.drawable.widget_check_available to R.string.updater_widget_installupdate
            AppState.UpdaterWidgetState.CONNECTION_ERROR -> R.drawable.widget_check_error     to R.string.updater_widget_connectionerror
            AppState.UpdaterWidgetState.NONE -> {
                checkUpdateWidget.showWidget = false
                return
            }
        }
        checkUpdateWidget.showWidget = true
        checkUpdateWidget.setWidgetLayoutResource(layout)
        checkUpdate.summary = getString(summary)
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
        listOf(R.id.sep_updates, R.id.sep_importexport, R.id.sep_labs)
            .forEach { id -> view.findViewById<AppCompatTextView>(id)?.fitDrawableToText(extraDp = 8f) }
    }

    // ======= Import/Export Function =======
    private fun getFileInfo(uri: Uri): Pair<String?, Long> =
        requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                name to size
            } else null to -1L
        } ?: (null to -1L)
    private fun handleImport(uri: Uri) {
        try {
            val (fileName, fileSize) = getFileInfo(uri)
            if (fileName?.endsWith(".bgs", ignoreCase = true) != true) throw Exception("Invalid extension")
            val maxSize = 2L * 1024 * 1024
            if (fileSize < 0 || fileSize > maxSize) throw Exception("Invalid file size")
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Exception("Cannot read file")
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val requiredInts = listOf("batt_level", "frq_little", "frq_big")
            if (!(BOOL_DEFAULTS.keys + requiredInts).all { json.has(it) }) throw Exception("Missing keys")
            requireContext().getSharedPreferences("guru_settings", Context.MODE_PRIVATE).edit().apply {
                BOOL_DEFAULTS.keys.forEach { key -> putBoolean(key, json.getBoolean(key)) }
                requiredInts.forEach { key -> putInt(key, json.getInt(key)) }
                VOLT_KEYS.forEach { key -> if (json.has(key)) putInt(key, json.getInt(key)) }
            }.apply()
            showImportSuccessDialog()
        } catch (_: Exception) { showImportErrorDialog() }
    }
    private fun handleExport(uri: Uri) {
        try {
            val prefs = requireContext().getSharedPreferences("guru_settings", Context.MODE_PRIVATE)
            val json = JSONObject()
            BOOL_DEFAULTS.forEach { (key, default) -> json.put(key, prefs.getBoolean(key, default)) }
            INT_DEFAULTS.forEach  { (key, default) -> json.put(key, prefs.getInt(key, default))     }
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            showExportSuccessDialog()
        } catch (_: Exception) {}
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