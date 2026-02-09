# BATTERY GURU OPTIMIZER (ALL SETTINGS)
# Developed by @EliezerB03

#!/sbin/sh

# ============== ALL OPTIMIZATIONS ============== #

#---------------< BATTERY OPTIMIZATIONS ON APPS >---------------#
# forceappsbattopt_on=1
settings put global forced_app_standby_enabled 1

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

#---------------< GPU PERFORMANCE BOOST >---------------#
# gpuboost_on=0
echo '1' > /sys/devices/platform/17500000.mali/dvfs_governor

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
# lit=1690 Mhz
echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=1690 Mhz
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
if [ $(getprop ro.build.version.sdk) -eq 35 ]; then service call activity 51 i32 -1; elif [ $(getprop ro.build.version.sdk) -eq 34 ]; then service call activity 51 i32 -1; elif [ $(getprop ro.build.version.sdk) -eq 33 ]; then service call activity 44 i32 -1; fi

#---------------< FORCE DOZE (LABS) >---------------#
# forcedoze_on=0
dumpsys deviceidle unforce

# ======================================