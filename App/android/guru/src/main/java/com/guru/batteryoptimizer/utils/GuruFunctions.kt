package com.guru.batteryoptimizer.utils

import android.content.Context
import android.content.SharedPreferences

class GuruFunctions(private val guru: GuruAPIs, private val context: Context) {
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
    private data class OptionDef(
        val prefKey: String, val execKey: String,
        val marker:  String,
        val onSave:  String, val offSave: String,
        val onCmd:   String, val offCmd:  String
    )
    private suspend fun applyToggle(opt: OptionDef, enabled: Boolean) {
        saveBool(opt.prefKey, enabled)
        val block   = if (enabled) opt.onSave else opt.offSave
        val liveCmd = if (enabled) opt.onCmd  else opt.offCmd
        guru.execSafe(opt.execKey, "${awkBlock(opt.marker, block)}; $liveCmd")
    }

    // ======= General Settings =======
    private val OPT_BATT_APPS = OptionDef(
        prefKey  = "battapps_opt",
        execKey  = "battapps_opt",
        marker   = "forceappsbattopt_on",
        onSave  = "# forceappsbattopt_on=1\nsettings put global forced_app_standby_enabled 1",
        offSave = "# forceappsbattopt_on=0\nsettings put global forced_app_standby_enabled 0",
        onCmd   = "settings put global forced_app_standby_enabled 1",
        offCmd  = "settings put global forced_app_standby_enabled 0"
    )
    private val OPT_CACHED_APPS = OptionDef(
        prefKey  = "cachedapps_opt",
        execKey  = "cachedapps_opt",
        marker   = "cachedappsopt_on",
        onSave  = "# cachedappsopt_on=1\nsettings put global cached_apps_freezer enabled",
        offSave = "# cachedappsopt_on=0\nsettings put global cached_apps_freezer disabled",
        onCmd   = "settings put global cached_apps_freezer enabled",
        offCmd  = "settings put global cached_apps_freezer disabled"
    )
    private val OPT_CPU_HOTPLUG = OptionDef(
        prefKey  = "cpu_hotplug_opt",
        execKey  = "cpu_hotplug_opt",
        marker   = "hotplugset_on",
        onSave  = "# hotplugset_on=1\necho '0' > /sys/power/cpuhotplug/governor/user_mode\necho '0' > /sys/power/cpuhotplug/governor/enabled",
        offSave = "# hotplugset_on=0\necho '1' > /sys/power/cpuhotplug/governor/user_mode\necho '1' > /sys/power/cpuhotplug/governor/enabled",
        onCmd   = "echo '0' > /sys/power/cpuhotplug/governor/user_mode; echo '0' > /sys/power/cpuhotplug/governor/enabled",
        offCmd  = "echo '1' > /sys/power/cpuhotplug/governor/user_mode; echo '1' > /sys/power/cpuhotplug/governor/enabled"
    )
    private val OPT_POWER_EFF = OptionDef(
        prefKey  = "power_effi_opt",
        execKey  = "power_effi_opt",
        marker   = "powereffiset_on",
        onSave  = "# powereffiset_on=1\necho 'Y' > /sys/module/workqueue/parameters/power_efficient",
        offSave = "# powereffiset_on=0\necho 'N' > /sys/module/workqueue/parameters/power_efficient",
        onCmd   = "echo 'Y' > /sys/module/workqueue/parameters/power_efficient",
        offCmd  = "echo 'N' > /sys/module/workqueue/parameters/power_efficient"
    )
    private val OPT_THERMAL = OptionDef(
        prefKey  = "thermal_opt",
        execKey  = "thermal_opt",
        marker   = "thermalset_on",
        onSave  = "# thermalset_on=1\n" +
            "system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0\n" +
            "chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus\n" +
            "echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres\n" +
            "echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms\n" +
            "echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres\n" +
            "echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres\n" +
            "chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus\n" +
            "pm disable com.google.android.gms/.chimera.GmsIntentOperationService",
        offSave = "# thermalset_on=0\nsystem_table_unset activity_manager_constants\npm enable com.google.android.gms/.chimera.GmsIntentOperationService",
        onCmd   = "system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0; chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus; echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres; echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms; echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres; chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus; pm disable com.google.android.gms/.chimera.GmsIntentOperationService",
        offCmd  = "system_table_unset activity_manager_constants; pm enable com.google.android.gms/.chimera.GmsIntentOperationService"
    )
    private val OPT_CPU_EFFI = OptionDef(
        prefKey  = "cpu_effiboost_opt",
        execKey  = "cpu_effiboost_opt",
        marker   = "cpueffiset_on",
        onSave  = "# cpueffiset_on=1\n" +
            "echo '90' > /sys/kernel/ehmp/up_threshold\necho '80' > /sys/kernel/ehmp/down_threshold\necho '4096' > /sys/kernel/ehmp/min_residency\n" +
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
        offSave = "# cpueffiset_on=0\n" +
            "echo '79' > /sys/kernel/ehmp/up_threshold\necho '128' > /sys/kernel/ehmp/down_threshold\necho '8192' > /sys/kernel/ehmp/min_residency\n" +
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
        onCmd   = "echo '85' > /sys/kernel/ehmp/up_threshold; echo '80' > /sys/kernel/ehmp/down_threshold; echo '4096' > /sys/kernel/ehmp/min_residency; echo '0' > /sys/kernel/ehmp/global_boost; echo '0 0 0 0' > /sys/kernel/ehmp/kernel_prefer_perf; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head; echo '500 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail; echo '700 1014000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head; echo '700 0 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail; echo '1000 546000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail; echo '200 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head; echo '600 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail; echo '130 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head; echo '500 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail; echo '0 416000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head; echo '200 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail",
        offCmd  = "echo '79' > /sys/kernel/ehmp/up_threshold; echo '128' > /sys/kernel/ehmp/down_threshold; echo '8192' > /sys/kernel/ehmp/min_residency; echo '0' > /sys/kernel/ehmp/global_boost; echo '0 0 0 0' > /sys/kernel/ehmp/kernel_prefer_perf; echo '500 1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/key/head; echo '500 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/key/tail; echo '700 1469000 1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/head; echo '700 0 1053000 0 0 1 1 0' > /sys/devices/virtual/input_booster/key_two/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/head; echo '0 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/keyboard/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/head; echo '0 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/mouse_wheel/tail; echo '1000 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/multitouch/tail; echo '200 1170000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/head; echo '600 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/pen_hover/tail; echo '130 1066000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/head; echo '500 858000 832000 0 0 1 1 0' > /sys/devices/virtual/input_booster/touch/tail; echo '0 1066000 0 0 0 1 1 0' > /sys/devices/virtual/input_booster/touchkey/head; echo '200 0 0 0 0 0 0 0' > /sys/devices/virtual/input_booster/touchkey/tail"
    )
    private val OPT_GPU_EFFI = OptionDef(
        prefKey  = "gpu_effiboost_opt",
        execKey  = "gpu_effiboost_opt",
        marker   = "gpueffiset_on",
        onSave  = "# gpueffiset_on=1\necho '1' > /sys/devices/platform/17500000.mali/dvfs_governor\necho '99'  > /sys/devices/platform/17500000.mali/highspeed_load\necho '5'   > /sys/devices/platform/17500000.mali/highspeed_delay\necho '150' > /sys/devices/platform/17500000.mali/dvfs_period\necho '100'  > /sys/kernel/gpu/gpu_poweroff_delay\necho '1'   > /sys/kernel/gpu/gpu_cl_boost_disable",
        offSave = "# gpueffiset_on=0\necho '3' > /sys/devices/platform/17500000.mali/dvfs_governor\necho '94'  > /sys/devices/platform/17500000.mali/highspeed_load\necho '0'   > /sys/devices/platform/17500000.mali/highspeed_delay\necho '100' > /sys/devices/platform/17500000.mali/dvfs_period\necho '50'  > /sys/kernel/gpu/gpu_poweroff_delay\necho '0'   > /sys/kernel/gpu/gpu_cl_boost_disable",
        onCmd   = "echo '1' > /sys/devices/platform/17500000.mali/dvfs_governor; echo '99' > /sys/devices/platform/17500000.mali/highspeed_load; echo '5' > /sys/devices/platform/17500000.mali/highspeed_delay; echo '150' > /sys/devices/platform/17500000.mali/dvfs_period; echo '100' > /sys/kernel/gpu/gpu_poweroff_delay; echo '1' > /sys/kernel/gpu/gpu_cl_boost_disable",
        offCmd  = "echo '3' > /sys/devices/platform/17500000.mali/dvfs_governor; echo '94' > /sys/devices/platform/17500000.mali/highspeed_load; echo '0' > /sys/devices/platform/17500000.mali/highspeed_delay; echo '100' > /sys/devices/platform/17500000.mali/dvfs_period; echo '50' > /sys/kernel/gpu/gpu_poweroff_delay; echo '0' > /sys/kernel/gpu/gpu_cl_boost_disable"
    )
    private val OPT_SWAP = OptionDef(
        prefKey  = "swap_opt",
        execKey  = "swap_opt",
        marker   = "swapset_on",
        onSave  = "# swapset_on=1\necho '10' > /proc/sys/vm/dirty_ratio\necho '3' > /proc/sys/vm/dirty_background_ratio\necho '2500' > /proc/sys/vm/dirty_expire_centisecs\necho '1250' > /proc/sys/vm/dirty_writeback_centisecs\necho '2' > /proc/sys/vm/laptop_mode\necho '70' > /proc/sys/vm/swappiness",
        offSave = "# swapset_on=0\necho '0' > /proc/sys/vm/dirty_ratio\necho '0' > /proc/sys/vm/dirty_background_ratio\necho '200' > /proc/sys/vm/dirty_expire_centisecs\necho '500' > /proc/sys/vm/dirty_writeback_centisecs\necho '0' > /proc/sys/vm/laptop_mode\necho '100' > /proc/sys/vm/swappiness",
        onCmd   = "echo '10' > /proc/sys/vm/dirty_ratio; echo '3' > /proc/sys/vm/dirty_background_ratio; echo '2500' > /proc/sys/vm/dirty_expire_centisecs; echo '1250' > /proc/sys/vm/dirty_writeback_centisecs; echo '2' > /proc/sys/vm/laptop_mode; echo '70' > /proc/sys/vm/swappiness",
        offCmd  = "echo '0' > /proc/sys/vm/dirty_ratio; echo '0' > /proc/sys/vm/dirty_background_ratio; echo '200' > /proc/sys/vm/dirty_expire_centisecs; echo '500' > /proc/sys/vm/dirty_writeback_centisecs; echo '0' > /proc/sys/vm/laptop_mode; echo '100' > /proc/sys/vm/swappiness"
    )
    private val OPT_STORAGE = OptionDef(
        prefKey  = "storage_opt",
        execKey  = "storage_opt",
        marker   = "storageset_on",
        onSave  = "# storageset_on=1\necho '1' > /sys/block/sda/queue/rotational\necho '1' > /sys/block/sda/queue/add_random\necho '2' > /sys/block/sda/queue/rq_affinity",
        offSave = "# storageset_on=0\necho '0' > /sys/block/sda/queue/rotational\necho '0' > /sys/block/sda/queue/add_random\necho '1' > /sys/block/sda/queue/rq_affinity",
        onCmd   = "echo '1' > /sys/block/sda/queue/rotational; echo '1' > /sys/block/sda/queue/add_random; echo '2' > /sys/block/sda/queue/rq_affinity",
        offCmd  = "echo '0' > /sys/block/sda/queue/rotational; echo '0' > /sys/block/sda/queue/add_random; echo '1' > /sys/block/sda/queue/rq_affinity"
    )

    // ======= About GURU (Labs) =======
    private val OPT_FORCE_DOZE = OptionDef(
        prefKey  = "forcedoze_opt",
        execKey  = "forcedoze_opt",
        marker   = "forcedoze_on",
        onSave  = "# forcedoze_on=1\ndumpsys deviceidle force-idle",
        offSave = "# forcedoze_on=0\ndumpsys deviceidle unforce",
        onCmd   = "dumpsys deviceidle force-idle",
        offCmd  = "dumpsys deviceidle unforce"
    )
    private val OPT_LIMIT_BG = OptionDef(
        prefKey  = "limitprocess_opt",
        execKey  = "limitprocess_opt",
        marker   = "limitprocess_on",
        onSave  = "# limitprocess_on=1\nSDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 4\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 4\nfi",
        offSave = "# limitprocess_on=0\nSDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 -1\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 -1\nfi",
        onCmd   = "SDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 4\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 4\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 4\nfi",
        offCmd  = "SDK=\$(getprop ro.build.version.sdk)\nif [ \"\$SDK\" -eq 36 ]; then\nservice call activity 52 i32 -1\nelif [ \"\$SDK\" -eq 35 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 34 ]; then\nservice call activity 51 i32 -1\nelif [ \"\$SDK\" -eq 33 ]; then\nservice call activity 44 i32 -1\nfi"
    )
    private val OPT_NSD = OptionDef(
        prefKey  = "nsd_opt",
        execKey  = "nsd_opt",
        marker   = "nsdopt_on",
        onSave  = "# nsdopt_on=1\nsettings put global nsd_on 1",
        offSave = "# nsdopt_on=0\nsettings put global nsd_on 0",
        onCmd   = "settings put global nsd_on 1",
        offCmd  = "settings put global nsd_on 0"
    )

    // ======= Apply Functions (General Settings) =======
    suspend fun applyBattAppsOpt(enabled: Boolean)   = applyToggle(OPT_BATT_APPS,   enabled)
    suspend fun applyCachedAppsOpt(enabled: Boolean) = applyToggle(OPT_CACHED_APPS, enabled)
    suspend fun applyCpuHotplug(enabled: Boolean)    = applyToggle(OPT_CPU_HOTPLUG, enabled)
    suspend fun applyPowerEff(enabled: Boolean)      = applyToggle(OPT_POWER_EFF,   enabled)
    suspend fun applyThermalOpt(enabled: Boolean)    = applyToggle(OPT_THERMAL,     enabled)
    suspend fun applyCpuEffiBoost(enabled: Boolean)  = applyToggle(OPT_CPU_EFFI,    enabled)
    suspend fun applyGpuEffiBoost(enabled: Boolean)  = applyToggle(OPT_GPU_EFFI,    enabled)
    suspend fun applySwapOpt(enabled: Boolean)       = applyToggle(OPT_SWAP,        enabled)
    suspend fun applyStorageOpt(enabled: Boolean)    = applyToggle(OPT_STORAGE,     enabled)
    // Battery Protection (Unique Logic)
    suspend fun applyBattLevel(level: Int, protectEnabled: Boolean) {
        saveAll {putBoolean("batteryprotect_opt", protectEnabled); putInt("batt_level", level)}
        val battCapacity = if (protectEnabled) level else 100
        val scriptCmd = buildString {
            append(if (protectEnabled) "sed -i \"s|^# batteryprotect_on=.*|# batteryprotect_on=1|\" $scriptPath"
                   else                "sed -i \"s|^# batteryprotect_on=.*|# batteryprotect_on=0|\" $scriptPath")
            append("; sed -i \"s|^# battery_litmit=.*|# battery_litmit=${level}%|\" $scriptPath")
            append("; ${sedValue("/sys/class/power_supply/battery/batt_full_capacity", "$battCapacity")}")
        }
        guru.execSafe("batt_level", "$scriptCmd; echo '$battCapacity' > /sys/class/power_supply/battery/batt_full_capacity")
    }

    // ======= Apply Functions (Advanced Settings - Unique Logics) =======
    // Freqs Settings
    suspend fun applyFreqsMaster(enabled: Boolean) {
        saveBool("frq_on", enabled)
        val litHz  = prefs.getInt("frq_little", 1690000)
        val bigHz  = prefs.getInt("frq_big",    1690000)
        val litMhz = litHz / 1000
        val bigMhz = bigHz / 1000
        val block = if (enabled) """
# frqset_on=1
# lit=$litMhz
echo '$litHz' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=$bigMhz
echo '$bigHz' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq""".trimIndent()
        else """
# frqset_on=0
# lit=$litMhz
echo '1794000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=$bigMhz
echo '2704000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq""".trimIndent()
        val liveCmd = if (enabled) "echo '$litHz' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq; echo '$bigHz' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"
                      else         "echo '1794000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq; echo '2704000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"
        guru.execSafe("frq_master", "${awkBlock("frqset_on", block)}; $liveCmd")
    }
    suspend fun applyLitFreq(valueHz: Int) {
        saveInt("frq_little", valueHz)
        val enabled = prefs.getBoolean("frq_on", true)
        val bigHz   = prefs.getInt("frq_big", 1690000)
        val litMhz  = valueHz / 1000
        val bigMhz  = bigHz / 1000
        val echoLit = if (enabled) valueHz else 1794000
        val echoBig = if (enabled) bigHz   else 2704000
        val block = """
# frqset_on=${if (enabled) 1 else 0}
# lit=$litMhz
echo '$echoLit' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=$bigMhz
echo '$echoBig' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq""".trimIndent()
        val liveCmd = if (enabled) "echo '$valueHz' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq" else ""
        guru.execSafe("frq_master", "${awkBlock("frqset_on", block)}${if (liveCmd.isNotEmpty()) "; $liveCmd" else ""}")
    }
    suspend fun applyBigFreq(valueHz: Int) {
        saveInt("frq_big", valueHz)
        val enabled = prefs.getBoolean("frq_on", true)
        val litHz   = prefs.getInt("frq_little", 1690000)
        val litMhz  = litHz / 1000
        val bigMhz  = valueHz / 1000
        val echoLit = if (enabled) litHz   else 1794000
        val echoBig = if (enabled) valueHz else 2704000
        val block = """
# frqset_on=${if (enabled) 1 else 0}
# lit=$litMhz
echo '$echoLit' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=$bigMhz
echo '$echoBig' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq""".trimIndent()
        val liveCmd = if (enabled) "echo '$valueHz' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq" else ""
        guru.execSafe("frq_master", "${awkBlock("frqset_on", block)}${if (liveCmd.isNotEmpty()) "; $liveCmd" else ""}")
    }
    // UV Settings
    private val voltNames    = listOf("big","lit","g3d","mif","aud","cam","cp","disp","fsys0","int","intcam","iva","score")
    private val voltDefaults = mapOf("cp" to 0).withDefault { -4 }
    suspend fun applyVoltMaster(enabled: Boolean) {
        saveBool("volt_on", enabled)
        val lines = buildString {
            appendLine(if (enabled) "# voltset_on=1" else "# voltset_on=0")
            voltNames.forEachIndexed { i, name ->
                val saved = prefs.getInt("${name}_volt", voltDefaults.getValue(name))
                val echo  = if (enabled) saved else 0
                appendLine("# ${name}_volt=${saved}")
                if (i < voltNames.lastIndex) appendLine("echo '$echo' > /sys/power/percent_margin/${name}_margin_percent")
                else append("echo '$echo' > /sys/power/percent_margin/${name}_margin_percent")
            }
        }
        val liveCmd = voltNames.joinToString("; ") { name ->
            val saved = prefs.getInt("${name}_volt", voltDefaults.getValue(name))
            val echo  = if (enabled) saved else 0
            "echo '$echo' > /sys/power/percent_margin/${name}_margin_percent"
        }
        guru.execSafe("volt_master", "${awkBlock("voltset_on", lines)}; $liveCmd")
    }
    suspend fun applyVolt(key: String, value: Int) {
        val name      = key.removeSuffix("_volt")
        saveInt(key, value)
        val enabled   = prefs.getBoolean("volt_on", true)
        val echoValue = if (enabled) value else 0
        val scriptCmd = buildString {
            append("sed -i \"s|^# ${key}=.*|# ${key}=${value}|\" $scriptPath")
            append("; ${sedValue("/sys/power/percent_margin/${name}_margin_percent", "$echoValue")}")
        }
        guru.execSafe("volt_$name", "$scriptCmd; echo '$echoValue' > /sys/power/percent_margin/${name}_margin_percent")
    }

    // ======= Apply Functions (About GURU) =======
    suspend fun applyLimitBackground(enabled: Boolean) = applyToggle(OPT_LIMIT_BG,   enabled)
    suspend fun applyForceDoze(enabled: Boolean)       = applyToggle(OPT_FORCE_DOZE, enabled)
    suspend fun applyNSDOpt(enabled: Boolean)          = applyToggle(OPT_NSD,        enabled)

    // ======= Build Script For Apply Preferences =======
    fun buildScript(): String {
        // General
        val battApps    = getBool("battapps_opt",       true)
        val cachedApps  = getBool("cachedapps_opt",     true)
        val hotplug     = getBool("cpu_hotplug_opt",    true)
        val powerEffi   = getBool("power_effi_opt",     true)
        val thermal     = getBool("thermal_opt",        true)
        val cpuEffi     = getBool("cpu_effiboost_opt",  true)
        val gpuEffi     = getBool("gpu_effiboost_opt",  true)
        val swap        = getBool("swap_opt",           true)
        val storage     = getBool("storage_opt",        true)
        val battProtect = getBool("batteryprotect_opt", false)
        val battLevel   = getInt("batt_level",          90)
        val battCapacity = if (battProtect) battLevel else 100
        // Freqs
        val frqOn       = getBool("frq_on",             true)
        val litHz       = getInt("frq_little",          1690000)
        val bigHz       = getInt("frq_big",             1690000)
        val litMhz      = litHz / 1000
        val bigMhz      = bigHz / 1000
        val echoLit     = if (frqOn) litHz else 1794000
        val echoBig     = if (frqOn) bigHz else 2704000
        // UV
        val voltOn      = getBool("volt_on",            true)
        val voltValues  = voltNames.associateWith { name -> getInt("${name}_volt", voltDefaults.getValue(name)) }
        val voltageLines = buildString {
            appendLine("# voltset_on=${if (voltOn) 1 else 0}")
            voltNames.forEach { name ->
                val saved = voltValues[name]!!
                val echo  = if (voltOn) saved else 0
                appendLine("# ${name}_volt=$saved")
                appendLine("echo '$echo' > /sys/power/percent_margin/${name}_margin_percent")
            }
        }
        // About (Labs)
        val limitProc   = getBool("limitprocess_opt",   false)
        val forceDoze   = getBool("forcedoze_opt",      false)
        val nsd         = getBool("nsd_opt",            false)
        // Build String logic
        fun block(opt: OptionDef, enabled: Boolean) = if (enabled) opt.onSave else opt.offSave
        return buildString {
            appendLine("# BATTERY GURU OPTIMIZER (ALL SETTINGS)")
            appendLine("# Developed by @EliezerB03")
            appendLine()
            appendLine("#!/system/bin/sh")
            appendLine()
            appendLine("# ============== ALL OPTIMIZATIONS  ============== #")
            appendLine()
            appendLine("#---------------< BATTERY OPTIMIZATIONS ON APPS >---------------#")
            appendLine(block(OPT_BATT_APPS,   battApps))
            appendLine()
            appendLine("#---------------< FREEZE CACHED APPS >---------------#")
            appendLine(block(OPT_CACHED_APPS, cachedApps))
            appendLine()
            appendLine("#---------------< CPU HOTPLUG OPTIMIZATIONS >---------------#")
            appendLine(block(OPT_CPU_HOTPLUG, hotplug))
            appendLine()
            appendLine("#---------------< CPU POWER EFFICIENT >---------------#")
            appendLine(block(OPT_POWER_EFF,   powerEffi))
            appendLine()
            appendLine("#---------------< THERMAL OPTIMIZATIONS >---------------#")
            appendLine(block(OPT_THERMAL,     thermal))
            appendLine()
            appendLine("#---------------< CPU EFFICIENCY BOOST >---------------#")
            appendLine(block(OPT_CPU_EFFI,    cpuEffi))
            appendLine()
            appendLine("#---------------< GPU EFFICIENCY BOOST >---------------#")
            appendLine(block(OPT_GPU_EFFI,    gpuEffi))
            appendLine()
            appendLine("#---------------< SWAP OPTIMIZATIONS >---------------#")
            appendLine(block(OPT_SWAP,        swap))
            appendLine()
            appendLine("#---------------< STORAGE OPTIMIZATIONS >---------------#")
            appendLine(block(OPT_STORAGE,     storage))
            appendLine()
            appendLine("#---------------< BATTERY PROTECTION >---------------#")
            appendLine("# batteryprotect_on=${if (battProtect) 1 else 0}")
            appendLine("# battery_litmit=${battLevel}%")
            appendLine("echo '$battCapacity' > /sys/class/power_supply/battery/batt_full_capacity")
            appendLine()
            appendLine("#---------------< FREQS SETTINGS >---------------#")
            appendLine("# frqset_on=${if (frqOn) 1 else 0}")
            appendLine("# lit=$litMhz")
            appendLine("echo '$echoLit' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")
            appendLine("# big=$bigMhz")
            appendLine("echo '$echoBig' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq")
            appendLine()
            appendLine("#---------------< VOLTAGE SETTINGS >---------------#")
            append(voltageLines)
            appendLine()
            appendLine("#---------------< LIMIT BACKGROUND APP PROCESS (LABS) >---------------#")
            appendLine(block(OPT_LIMIT_BG,    limitProc))
            appendLine()
            appendLine("#---------------< FORCE DOZE (LABS) >---------------#")
            appendLine(block(OPT_FORCE_DOZE,  forceDoze))
            appendLine()
            appendLine("#---------------< NETWORK SERVICE DISCOVERY (LABS) >---------------#")
            appendLine(block(OPT_NSD,         nsd))
            appendLine()
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

#---------------< CPU HOTPLUG OPTIMIZATIONS >---------------#
# hotplugset_on=1
echo '0' > /sys/power/cpuhotplug/governor/user_mode
echo '0' > /sys/power/cpuhotplug/governor/enabled

#---------------< CPU POWER EFFICIENT >---------------#
# powereffiset_on=1
echo 'Y' > /sys/module/workqueue/parameters/power_efficient

#---------------< THERMAL OPTIMIZATIONS >---------------#
# thermalset_on=1
system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0
chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms
echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres
echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres
chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus
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
echo '10' > /proc/sys/vm/dirty_ratio
echo '3' > /proc/sys/vm/dirty_background_ratio
echo '2500' > /proc/sys/vm/dirty_expire_centisecs
echo '1250' > /proc/sys/vm/dirty_writeback_centisecs
echo '2' > /proc/sys/vm/laptop_mode
echo '70' > /proc/sys/vm/swappiness

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