# BATTERY GURU OPTIMIZER (SETTINGS)
# Developed by @EliezerB03

#!/sbin/sh

function OPTIMIZER_WAIT() {
 while [[ -z $(getprop sys.boot_completed) ]]; do sleep 5; done
}
OPTIMIZER_WAIT()

# ============================= GENERAL OPTIMIZATIONS =============================

# DISABLE KERNEL DEBUGGING
echo '0' > /proc/sys/kernel/panic
echo 'N' > /sys/kernel/debug/debug_enabled
echo 'N' > /sys/kernel/debug/seclog/seclog_enabled

# STORAGE OPTIMIZATIONS
echo '1' > /sys/block/sda/queue/rotational
echo '1' > /sys/block/sda/queue/add_random
echo '256' > /sys/block/sda/queue/nr_requests
echo '2' > /sys/block/sda/queue/rq_affinity
echo 'cfq' > /sys/block/sda/queue/scheduler
echo '0' > /sys/block/sda/queue/nomerges
echo '0' > /sys/block/sda/queue/iostats
echo 'cfq' > /sys/block/mmcblk0/queue/scheduler
echo '0' > /sys/block/mmcblk0/queue/iostats

# THERMAL OPTIMIZATIONS
system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0
chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms
echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres
echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres
chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus
pm disable com.google.android.gms/.chimera.GmsIntentOperationService

# GMS OPTIMIZATIONS 
conflict=$(xml=$(find /data/adb -iname "*.xml")
echo "conflict")
sed -i '/allow-in-power-save package="com.google.android.gms"/d;/allow-in-data-usage-save package="com.google.android.gms"/d' $xml

# ================================== CPU SETTINGS =================================

# CPU FREQ SETTINGS
echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo '1794000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

# CPU HOTPLUG OPTIMIZATIONS
echo '0' > /sys/power/cpuhotplug/governor/user_mode
echo '0' > /sys/power/cpuhotplug/governor/enabled

# ENABLE POWER EFFICIENT
echo 'Y' > /sys/module/workqueue/parameters/power_efficient

# UNDERVOLT SETTINGS
echo '-5' > /sys/power/percent_margin/big_margin_percent
echo '-5' > /sys/power/percent_margin/lit_margin_percent
echo '-5' > /sys/power/percent_margin/g3d_margin_percent
echo '-5' > /sys/power/percent_margin/mif_margin_percent
echo '-5' > /sys/power/percent_margin/aud_margin_percent
echo '-5' > /sys/power/percent_margin/cam_margin_percent
echo '-5' > /sys/power/percent_margin/disp_margin_percent
echo '-5' > /sys/power/percent_margin/fsys0_margin_percent
echo '-5' > /sys/power/percent_margin/int_margin_percent
echo '-5' > /sys/power/percent_margin/intcam_margin_percent
echo '-5' > /sys/power/percent_margin/iva_margin_percent
echo '-5' > /sys/power/percent_margin/score_margin_percent
