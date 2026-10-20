package com.guru.batteryoptimizer.utils

import android.content.Context
import android.content.SharedPreferences

// Global Values
private const val FREQ_LIT = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
private const val FREQ_BIG = "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"
private const val FREQ_LIT_OFF = 1794000
private const val FREQ_BIG_OFF = 2704000
private const val FREQ_DEFAULT = 1690000

class GuruFunctions(private val guru: GuruAPIs, context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("guru_settings", Context.MODE_PRIVATE)
    private val scriptPath = "/data/data/com.guru.batteryoptimizer/guru.sh"

    // ======= SharedPreferences =======
    fun getBool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    private fun saveBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun saveInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    private fun saveAll(block: SharedPreferences.Editor.() -> Unit) = prefs.edit().apply(block).apply()

    // ======= Script Helpers =======
    private fun sedValue(path: String, value: String) = "sed -i \"s|echo '[^']*' > ${path}|echo '${value}' > ${path}|g\" $scriptPath"
    private fun awkBlock(marker: String, block: String): String {
        val tmpBlock  = "${scriptPath}.${marker}.tmp"
        val tmpScript = "${scriptPath}.tmp"
        val escapedBlock = block.replace(Regex("""\$(?!\()"""), "\\$")
        return buildString {
            append("cat > $tmpBlock << 'AWKEOF'\n")
            append("$escapedBlock\n")
            append("AWKEOF\n")
            append("awk -v blk=\"$tmpBlock\" -v marker=\"^# ${marker}=\" '")
            append("BEGIN{skip=0} ")
            append("{ if(\$0 ~ marker){ skip=1; while((getline line < blk) > 0) print line; next } ")
            append("if(skip){ if(\$0 ~ /^$/ || \$0 ~ /^#-+/){ skip=0; print \$0; next } else next } ")
            append("print \$0 }' ")
            append("$scriptPath > $tmpScript && mv $tmpScript $scriptPath; rm -f $tmpBlock")
        }
    }

    // ======= Option Base Structure =======
    private class OptionDef(
        val key: String, val marker: String, val title: String, val default: Boolean,
        val onSave: String, val offSave: String, val onCmd: String, val offCmd: String
    )
    private fun opt(
        key: String, marker: String, title: String, default: Boolean, on: String, off: String, join: String = "; ",
        onCmd: String = on.replace("\n", join), offCmd: String = off.replace("\n", join)
    ) = OptionDef(key, marker, title, default, "# $marker=1\n$on", "# $marker=0\n$off", onCmd, offCmd)
    private suspend fun applyToggle(opt: OptionDef, enabled: Boolean) {
        saveBool(opt.key, enabled)
        val block   = if (enabled) opt.onSave else opt.offSave
        val liveCmd = if (enabled) opt.onCmd  else opt.offCmd
        guru.execSafe(opt.key, "${awkBlock(opt.marker, block)}; $liveCmd")
    }

    // ======= General Settings =======
    private val OPT_BATT_APPS = opt("battapps_opt", "forceappsbattopt_on", "BATTERY OPTIMIZATIONS ON APPS", true,
        on  = "settings put global forced_app_standby_enabled 1",
        off = "settings put global forced_app_standby_enabled 0")
    private val OPT_CACHED_APPS = opt("cachedapps_opt", "cachedappsopt_on", "FREEZE CACHED APPS", true,
        on  = "settings put global cached_apps_freezer enabled",
        off = "settings put global cached_apps_freezer disabled")
    private val OPT_WAKELOCK_BLOCK = opt("wakelock_opt", "wakelock_block_on", "WAKELOCK BLOCKER OPTIMIZATIONS", true,
        on  = """P=/sys/devices/virtual/misc/boeffla_wakelock_blocker/wakelock_blocker; cur=${'$'}(cat ${'$'}P); [ "${'$'}(settings get global bgo_wl_prev)" = "null" ] && settings put global bgo_wl_prev "${'$'}{cur:-none}"; for w in wlan_ctrl_wake wlan_scan_wake wlan_evt_wake; do cur=${'$'}(cat ${'$'}P); case ";${'$'}cur;" in *";${'$'}w;"*) ;; *) if [ -z "${'$'}cur" ]; then echo "${'$'}w" > ${'$'}P; else echo "${'$'}cur;${'$'}w" > ${'$'}P; fi;; esac; done""",
        off = """P=/sys/devices/virtual/misc/boeffla_wakelock_blocker/wakelock_blocker; prev=${'$'}(settings get global bgo_wl_prev); if [ "${'$'}prev" != "null" ]; then if [ "${'$'}prev" = "none" ]; then echo '' > ${'$'}P; else echo "${'$'}prev" > ${'$'}P; fi; settings delete global bgo_wl_prev; fi""")        
    private val OPT_CPU_HOTPLUG = opt("cpu_hotplug_opt", "hotplugset_on", "CPU HOTPLUG OPTIMIZATIONS", true,
        on  = "echo '0' > /sys/power/cpuhotplug/governor/user_mode\necho '0' > /sys/power/cpuhotplug/governor/enabled",
        off = "echo '1' > /sys/power/cpuhotplug/governor/user_mode\necho '1' > /sys/power/cpuhotplug/governor/enabled")
    private val OPT_POWER_EFF = opt("power_effi_opt", "powereffiset_on", "CPU POWER EFFICIENT", true,
        on  = "echo 'Y' > /sys/module/workqueue/parameters/power_efficient",
        off = "echo 'N' > /sys/module/workqueue/parameters/power_efficient")
    private val OPT_THERMAL = opt("thermal_opt", "thermalset_on", "THERMAL OPTIMIZATIONS", true,
        on  = "echo 85 > /sys/devices/platform/battery/power_supply/battery/siop_level\npm disable com.google.android.gms/.chimera.GmsIntentOperationService",
        off = "echo 100 > /sys/devices/platform/battery/power_supply/battery/siop_level\npm enable com.google.android.gms/.chimera.GmsIntentOperationService")
    private val OPT_CPU_EFFI = opt("cpu_effiboost_opt", "cpueffiset_on", "CPU EFFICIENCY BOOST", true,
        on  = "echo '90' > /sys/kernel/ehmp/up_threshold\necho '80' > /sys/kernel/ehmp/down_threshold\necho '4096' > /sys/kernel/ehmp/min_residency\n" +
            "echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head\necho '500 0      0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail\n" +
            "echo '700 1014000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head\necho '700 0       0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail\n" +
            "echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head\necho '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail\n" +
            "echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head\necho '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail\n" +
            "echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head\necho '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail\n" +
            "echo '1000 546000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head\necho '500  416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail\n" +
            "echo '200 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head\necho '600 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail\n" +
            "echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head\necho '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail\n" +
            "echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head\necho '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail\n" +
            "echo '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head\necho '200 0      0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail",
        off = "echo '79' > /sys/kernel/ehmp/up_threshold\necho '128' > /sys/kernel/ehmp/down_threshold\necho '8192' > /sys/kernel/ehmp/min_residency\n" +
            "echo '500 1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head\necho '500 0       0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail\n" +
            "echo '700 1469000 1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head\necho '700 0       1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail\n" +
            "echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head\necho '0   858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail\n" +
            "echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head\necho '500 858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail\n" +
            "echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head\necho '0   858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail\n" +
            "echo '1000 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head\necho '500  858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail\n" +
            "echo '200 1170000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head\necho '600 858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail\n" +
            "echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head\necho '500 858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail\n" +
            "echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head\necho '500 858000  832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail\n" +
            "echo '0   1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head\necho '200 0       0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail",
        onCmd  = "echo '85' > /sys/kernel/ehmp/up_threshold; echo '80' > /sys/kernel/ehmp/down_threshold; echo '4096' > /sys/kernel/ehmp/min_residency; echo '0' > /sys/kernel/ehmp/global_boost; echo '0 0 0 0' > /sys/kernel/ehmp/kernel_prefer_perf; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head; echo '500 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail; echo '700 1014000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head; echo '700 0 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail; echo '1000 546000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail; echo '200 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head; echo '600 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head; echo '200 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail",
        offCmd = "echo '79' > /sys/kernel/ehmp/up_threshold; echo '128' > /sys/kernel/ehmp/down_threshold; echo '8192' > /sys/kernel/ehmp/min_residency; echo '0' > /sys/kernel/ehmp/global_boost; echo '0 0 0 0' > /sys/kernel/ehmp/kernel_prefer_perf; echo '500 1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head; echo '500 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail; echo '700 1469000 1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head; echo '700 0 1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head; echo '0 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head; echo '0 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail; echo '1000 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail; echo '200 1170000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head; echo '600 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail; echo '0 1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head; echo '200 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail")
    private val OPT_GPU_EFFI = opt("gpu_effiboost_opt", "gpueffiset_on", "GPU EFFICIENCY BOOST", true,
        on  = "echo '1' > /sys/devices/platform/17500000.mali/dvfs_governor\necho '99'  > /sys/devices/platform/17500000.mali/highspeed_load\necho '5'   > /sys/devices/platform/17500000.mali/highspeed_delay\necho '150' > /sys/devices/platform/17500000.mali/dvfs_period\necho '100'  > /sys/kernel/gpu/gpu_poweroff_delay\necho '1'   > /sys/kernel/gpu/gpu_cl_boost_disable",
        off = "echo '3' > /sys/devices/platform/17500000.mali/dvfs_governor\necho '94'  > /sys/devices/platform/17500000.mali/highspeed_load\necho '0'   > /sys/devices/platform/17500000.mali/highspeed_delay\necho '100' > /sys/devices/platform/17500000.mali/dvfs_period\necho '50'  > /sys/kernel/gpu/gpu_poweroff_delay\necho '0'   > /sys/kernel/gpu/gpu_cl_boost_disable",
        onCmd  = "echo '1' > /sys/devices/platform/17500000.mali/dvfs_governor; echo '99' > /sys/devices/platform/17500000.mali/highspeed_load; echo '5' > /sys/devices/platform/17500000.mali/highspeed_delay; echo '150' > /sys/devices/platform/17500000.mali/dvfs_period; echo '100' > /sys/kernel/gpu/gpu_poweroff_delay; echo '1' > /sys/kernel/gpu/gpu_cl_boost_disable",
        offCmd = "echo '3' > /sys/devices/platform/17500000.mali/dvfs_governor; echo '94' > /sys/devices/platform/17500000.mali/highspeed_load; echo '0' > /sys/devices/platform/17500000.mali/highspeed_delay; echo '100' > /sys/devices/platform/17500000.mali/dvfs_period; echo '50' > /sys/kernel/gpu/gpu_poweroff_delay; echo '0' > /sys/kernel/gpu/gpu_cl_boost_disable")
    private val OPT_SWAP = opt("swap_opt", "swapset_on", "SWAP OPTIMIZATIONS", true,
        on  = "echo '20' > /proc/sys/vm/dirty_ratio\necho '5' > /proc/sys/vm/dirty_background_ratio\necho '2500' > /proc/sys/vm/dirty_expire_centisecs\necho '2500' > /proc/sys/vm/dirty_writeback_centisecs\necho '60' > /proc/sys/vm/swappiness",
        off = "echo '0' > /proc/sys/vm/dirty_ratio\necho '0' > /proc/sys/vm/dirty_background_ratio\necho '200' > /proc/sys/vm/dirty_expire_centisecs\necho '500' > /proc/sys/vm/dirty_writeback_centisecs\necho '100' > /proc/sys/vm/swappiness")
    private val OPT_STORAGE = opt("storage_opt", "storageset_on", "STORAGE OPTIMIZATIONS", true,
        on  = "echo '1' > /sys/block/sda/queue/rotational\necho '1' > /sys/block/sda/queue/add_random\necho '2' > /sys/block/sda/queue/rq_affinity",
        off = "echo '0' > /sys/block/sda/queue/rotational\necho '0' > /sys/block/sda/queue/add_random\necho '1' > /sys/block/sda/queue/rq_affinity")

    // ======= About GURU (Labs) =======
    private val OPT_LIMIT_BG = opt("limitprocess_opt", "limitprocess_on", "LIMIT BACKGROUND APP PROCESS (LABS)", false,
        on  = "SDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 4\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 4\nfi",
        off = "SDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 -1\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 -1\nfi",
        join = "\n")
    private val OPT_FORCE_DOZE = opt("forcedoze_opt", "forcedoze_on", "FORCE DOZE (LABS)", false,
        on  = "dumpsys deviceidle force-idle",
        off = "dumpsys deviceidle unforce")
    private val OPT_NSD = opt("nsd_opt", "nsdopt_on", "NETWORK SERVICE DISCOVERY (LABS)", false,
        on  = "settings put global nsd_on 1",
        off = "settings put global nsd_on 0")
    private val GENERAL_OPTS = listOf(OPT_BATT_APPS, OPT_CACHED_APPS, OPT_WAKELOCK_BLOCK, OPT_CPU_HOTPLUG, OPT_POWER_EFF, OPT_THERMAL, OPT_CPU_EFFI, OPT_GPU_EFFI, OPT_SWAP, OPT_STORAGE)
    private val LABS_OPTS = listOf(OPT_LIMIT_BG, OPT_FORCE_DOZE, OPT_NSD)

    // ======= Apply Functions (General Settings) =======
    suspend fun applyBattAppsOpt(enabled: Boolean)      = applyToggle(OPT_BATT_APPS,      enabled)
    suspend fun applyCachedAppsOpt(enabled: Boolean)    = applyToggle(OPT_CACHED_APPS,    enabled)
    suspend fun applyWakelockBlocker(enabled: Boolean)  = applyToggle(OPT_WAKELOCK_BLOCK, enabled)
    suspend fun applyCpuHotplug(enabled: Boolean)       = applyToggle(OPT_CPU_HOTPLUG,    enabled)
    suspend fun applyPowerEff(enabled: Boolean)         = applyToggle(OPT_POWER_EFF,      enabled)
    suspend fun applyThermalOpt(enabled: Boolean)       = applyToggle(OPT_THERMAL,        enabled)
    suspend fun applyCpuEffiBoost(enabled: Boolean)     = applyToggle(OPT_CPU_EFFI,       enabled)
    suspend fun applyGpuEffiBoost(enabled: Boolean)     = applyToggle(OPT_GPU_EFFI,       enabled)
    suspend fun applySwapOpt(enabled: Boolean)          = applyToggle(OPT_SWAP,           enabled)
    suspend fun applyStorageOpt(enabled: Boolean)       = applyToggle(OPT_STORAGE,        enabled)
    // Battery Protection (Unique Logic)
    private fun battProtectBlock(protectEnabled: Boolean, level: Int) =
        "# batteryprotect_on=${if (protectEnabled) 1 else 0}\n# battery_litmit=${level}%\n" +
        "echo '${if (protectEnabled) level else 100}' > /sys/class/power_supply/battery/batt_full_capacity"
    suspend fun applyBattLevel(level: Int, protectEnabled: Boolean) {
        saveAll { putBoolean("batteryprotect_opt", protectEnabled); putInt("batt_level", level) }
        val battCapacity = if (protectEnabled) level else 100
        val scriptCmd = "sed -i \"s|^# batteryprotect_on=.*|# batteryprotect_on=${if (protectEnabled) 1 else 0}|\" $scriptPath" +
            "; sed -i \"s|^# battery_litmit=.*|# battery_litmit=${level}%|\" $scriptPath" +
            "; ${sedValue("/sys/class/power_supply/battery/batt_full_capacity", "$battCapacity")}"
        guru.execSafe("batt_level", "$scriptCmd; echo '$battCapacity' > /sys/class/power_supply/battery/batt_full_capacity")
    }

    // ======= Apply Functions (Advanced Settings - Unique Logics) =======
    // Freqs Settings
    private fun freqBlock(enabled: Boolean, litHz: Int, bigHz: Int) =
        "# frqset_on=${if (enabled) 1 else 0}\n# lit=${litHz / 1000}\n" +
        "echo '${if (enabled) litHz else FREQ_LIT_OFF}' > $FREQ_LIT\n# big=${bigHz / 1000}\n" +
        "echo '${if (enabled) bigHz else FREQ_BIG_OFF}' > $FREQ_BIG"
    private suspend fun applyFreqs(enabled: Boolean, litHz: Int, bigHz: Int, liveLit: Boolean, liveBig: Boolean) {
        val live = listOfNotNull(
            if (liveLit) "echo '${if (enabled) litHz else FREQ_LIT_OFF}' > $FREQ_LIT" else null,
            if (liveBig) "echo '${if (enabled) bigHz else FREQ_BIG_OFF}' > $FREQ_BIG" else null
        ).joinToString("; ")
        guru.execSafe("frq_master", awkBlock("frqset_on", freqBlock(enabled, litHz, bigHz)) + (if (live.isNotEmpty()) "; $live" else ""))
    }
    suspend fun applyFreqsMaster(enabled: Boolean) {
        saveBool("frq_on", enabled)
        applyFreqs(enabled, getInt("frq_little", FREQ_DEFAULT), getInt("frq_big", FREQ_DEFAULT), liveLit = true, liveBig = true)
    }
    suspend fun applyLitFreq(valueHz: Int) {
        saveInt("frq_little", valueHz)
        val enabled = getBool("frq_on", true)
        applyFreqs(enabled, valueHz, getInt("frq_big", FREQ_DEFAULT), liveLit = enabled, liveBig = false)
    }
    suspend fun applyBigFreq(valueHz: Int) {
        saveInt("frq_big", valueHz)
        val enabled = getBool("frq_on", true)
        applyFreqs(enabled, getInt("frq_little", FREQ_DEFAULT), valueHz, liveLit = false, liveBig = enabled)
    }
    // UV Settings
    private val voltNames    = listOf("big","lit","g3d","mif","aud","cam","cp","disp","fsys0","int","intcam","iva","score")
    private val voltDefaults = mapOf("cp" to 0).withDefault { -4 }
    private fun voltSaved(name: String) = getInt("${name}_volt", voltDefaults.getValue(name))
    private fun voltEcho(name: String, value: Int) = "echo '$value' > /sys/power/percent_margin/${name}_margin_percent"
    private fun voltBlock(enabled: Boolean) =
        (listOf("# voltset_on=${if (enabled) 1 else 0}") + voltNames.flatMap { name ->
            listOf("# ${name}_volt=${voltSaved(name)}", voltEcho(name, if (enabled) voltSaved(name) else 0))
        }).joinToString("\n")
    suspend fun applyVoltMaster(enabled: Boolean) {
        saveBool("volt_on", enabled)
        val liveCmd = voltNames.joinToString("; ") { name -> voltEcho(name, if (enabled) voltSaved(name) else 0) }
        guru.execSafe("volt_master", "${awkBlock("voltset_on", voltBlock(enabled))}; $liveCmd")
    }
    suspend fun applyVolt(key: String, value: Int) {
        val name      = key.removeSuffix("_volt")
        saveInt(key, value)
        val echoValue = if (getBool("volt_on", true)) value else 0
        val scriptCmd = "sed -i \"s|^# ${key}=.*|# ${key}=${value}|\" $scriptPath" + "; ${sedValue("/sys/power/percent_margin/${name}_margin_percent", "$echoValue")}"
        guru.execSafe("volt_$name", "$scriptCmd; ${voltEcho(name, echoValue)}")
    }

    // ======= Apply Functions (About GURU) =======
    suspend fun applyLimitBackground(enabled: Boolean) = applyToggle(OPT_LIMIT_BG,   enabled)
    suspend fun applyForceDoze(enabled: Boolean)       = applyToggle(OPT_FORCE_DOZE, enabled)
    suspend fun applyNSDOpt(enabled: Boolean)          = applyToggle(OPT_NSD,        enabled)

    // ======= Build Script For Apply Preferences =======
    fun buildScript(): String {
        fun optSections(list: List<OptionDef>) = list.map { it.title to (if (getBool(it.key, it.default)) it.onSave else it.offSave) }
        val battProtect = getBool("batteryprotect_opt", false)
        val sections =
            optSections(GENERAL_OPTS) + listOf(
                "BATTERY PROTECTION" to battProtectBlock(battProtect, getInt("batt_level", 90)),
                "FREQS SETTINGS"     to freqBlock(getBool("frq_on", true), getInt("frq_little", FREQ_DEFAULT), getInt("frq_big", FREQ_DEFAULT)),
                "VOLTAGE SETTINGS"   to voltBlock(getBool("volt_on", true))
            ) + optSections(LABS_OPTS)
        return buildString {
            appendLine("# BATTERY GURU OPTIMIZER (ALL SETTINGS)")
            appendLine("# Developed by @EliezerB03")
            appendLine()
            appendLine("#!/system/bin/sh")
            appendLine()
            appendLine("# ============== ALL OPTIMIZATIONS  ============== #")
            appendLine()
            sections.forEach { (title, body) ->
                appendLine("#---------------< $title >---------------#")
                appendLine(body)
                appendLine()
            }
            appendLine("# ======================================")
        }
    }

    // =======================================
    // ======= Objet Variables =======
    companion object Fmt {
        fun Int.fmt(): String = String.format(java.util.Locale.getDefault(), "%d", this)
        fun Float.fmt(decimals: Int = 1): String = String.format(java.util.Locale.getDefault(), "%.${decimals}f", this)
    }
    object Scripts {
        val GURU_SCRIPT = """
# BATTERY GURU OPTIMIZER (ALL SETTINGS)
# Developed by @EliezerB03

#!/system/bin/sh

# ============== ALL OPTIMIZATIONS  ============== #

#---------------< BATTERY OPTIMIZATIONS ON APPS >---------------#
# forceappsbattopt_on=1
settings put global forced_app_standby_enabled 1

#---------------< FREEZE CACHED APPS >---------------#
# cachedappsopt_on=1
settings put global cached_apps_freezer enabled

#---------------< WAKELOCK BLOCKER OPTIMIZATIONS >---------------#
# wakelock_block_on=1
P=/sys/devices/virtual/misc/boeffla_wakelock_blocker/wakelock_blocker
cur=${'$'}(cat ${'$'}P)
[ "${'$'}(settings get global bgo_wl_prev)" = "null" ] && settings put global bgo_wl_prev "${'$'}{cur:-none}"
for w in wlan_ctrl_wake wlan_scan_wake wlan_evt_wake; do
cur=${'$'}(cat ${'$'}P)
case ";${'$'}cur;" in
*";${'$'}w;"*) ;;
*) if [ -z "${'$'}cur" ]; then echo "${'$'}w" > ${'$'}P; else echo "${'$'}cur;${'$'}w" > ${'$'}P; fi;;
esac
done

#---------------< CPU HOTPLUG OPTIMIZATIONS >---------------#
# hotplugset_on=1
echo '0' > /sys/power/cpuhotplug/governor/user_mode
echo '0' > /sys/power/cpuhotplug/governor/enabled

#---------------< CPU POWER EFFICIENT >---------------#
# powereffiset_on=1
echo 'Y' > /sys/module/workqueue/parameters/power_efficient

#---------------< THERMAL OPTIMIZATIONS >---------------#
# thermalset_on=1
echo 85 > /sys/devices/platform/battery/power_supply/battery/siop_level
pm disable com.google.android.gms/.chimera.GmsIntentOperationService

#---------------< CPU EFFICIENCY BOOST >---------------#
# cpueffiset_on=1
echo '90' > /sys/kernel/ehmp/up_threshold
echo '80' > /sys/kernel/ehmp/down_threshold
echo '4096' > /sys/kernel/ehmp/min_residency
echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head
echo '500 0      0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail
echo '700 1014000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head
echo '700 0       0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail
echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head
echo '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail
echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head
echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail
echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head
echo '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail
echo '1000 546000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head
echo '500  416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail
echo '200 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head
echo '600 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail
echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head
echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail
echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head
echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail
echo '0   416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head
echo '200 0      0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail

#---------------< GPU EFFICIENCY BOOST >---------------#
# gpueffiset_on=1
echo '1' > /sys/devices/platform/17500000.mali/dvfs_governor
echo '99' > /sys/devices/platform/17500000.mali/highspeed_load
echo '5' > /sys/devices/platform/17500000.mali/highspeed_delay
echo '150' > /sys/devices/platform/17500000.mali/dvfs_period
echo '100' > /sys/kernel/gpu/gpu_poweroff_delay
echo '1' > /sys/kernel/gpu/gpu_cl_boost_disable

#---------------< SWAP OPTIMIZATIONS >---------------#
# swapset_on=1
echo '20' > /proc/sys/vm/dirty_ratio
echo '5' > /proc/sys/vm/dirty_background_ratio
echo '2500' > /proc/sys/vm/dirty_expire_centisecs
echo '2500' > /proc/sys/vm/dirty_writeback_centisecs
echo '60' > /proc/sys/vm/swappiness
echo '50' > /proc/sys/vm/vfs_cache_pressure

#---------------< STORAGE OPTIMIZATIONS >---------------#
# storageset_on=1
echo '1' > /sys/block/sda/queue/rotational
echo '1' > /sys/block/sda/queue/add_random
echo '2' > /sys/block/sda/queue/rq_affinity

#---------------< BATTERY PROTECTION >---------------#
# batteryprotect_on=0
# battery_litmit=90%
echo '100' > /sys/class/power_supply/battery/batt_full_capacity

#---------------< FREQS SETTINGS >---------------#
# frqset_on=1
# lit=1690
echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=1690
echo '1690000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

#---------------< VOLTAGE SETTINGS >---------------#
# voltset_on=1
# big_volt=-4
echo '-4' > /sys/power/percent_margin/big_margin_percent
# lit_volt=-4
echo '-4' > /sys/power/percent_margin/lit_margin_percent
# g3d_volt=-4
echo '-4' > /sys/power/percent_margin/g3d_margin_percent
# mif_volt=-4
echo '-4' > /sys/power/percent_margin/mif_margin_percent
# aud_volt=-4
echo '-4' > /sys/power/percent_margin/aud_margin_percent
# cam_volt=-4
echo '-4' > /sys/power/percent_margin/cam_margin_percent
# cp_volt=0
echo '0' > /sys/power/percent_margin/cp_margin_percent
# disp_volt=-4
echo '-4' > /sys/power/percent_margin/disp_margin_percent
# fsys0_volt=-4
echo '-4' > /sys/power/percent_margin/fsys0_margin_percent
# int_volt=-4
echo '-4' > /sys/power/percent_margin/int_margin_percent
# intcam_volt=-4
echo '-4' > /sys/power/percent_margin/intcam_margin_percent
# iva_volt=-4
echo '-4' > /sys/power/percent_margin/iva_margin_percent
# score_volt=-4
echo '-4' > /sys/power/percent_margin/score_margin_percent

#---------------< LIMIT BACKGROUND APP PROCESS (LABS) >---------------#
# limitprocess_on=0
SDK=${'$'}(getprop ro.build.version.sdk)
if [ "${'$'}SDK" -eq 36 ]; then
service call activity 52 i32 -1
elif [ "${'$'}SDK" -eq 35 ]; then
service call activity 51 i32 -1
elif [ "${'$'}SDK" -eq 34 ]; then
service call activity 51 i32 -1
elif [ "${'$'}SDK" -eq 33 ]; then
service call activity 44 i32 -1
fi

#---------------< FORCE DOZE (LABS) >---------------#
# forcedoze_on=0
dumpsys deviceidle unforce

#---------------< NETWORK SERVICE DISCOVERY (LABS) >---------------#
# nsdopt_on=0
settings put global nsd_on 0

# ======================================
        """.trimIndent()
    }
}