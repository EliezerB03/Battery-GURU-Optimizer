# BATTERY GURU OPTIMIZER (UNISTALLER)
# Developed by @EliezerB03

#!/system/bin/sh

# ============================= GENERAL OPTIMIZATIONS =============================

#---------------< KERNEL DEBUGGING >---------------#
echo '1' > /proc/sys/kernel/panic
echo 'Y' > /sys/kernel/debug/debug_enabled
echo 'Y' > /sys/kernel/debug/seclog/seclog_enabled

#---------------< STORAGE OPTIMIZATIONS >---------------#
echo '0' > /sys/block/sda/queue/rotational
echo '0' > /sys/block/sda/queue/add_random
echo '1' > /sys/block/sda/queue/rq_affinity

#---------------< THERMAL OPTIMIZATIONS >---------------#
system_table_unset activity_manager_constants
pm enable com.google.android.gms/.chimera.GmsIntentOperationService

#---------------< SWAP OPTIMIZATIONS >---------------#
echo '0' > /proc/sys/vm/dirty_ratio
echo '200' > /proc/sys/vm/dirty_expire_centisecs
echo '500' > /proc/sys/vm/dirty_writeback_centisecs
echo '0' > /proc/sys/vm/laptop_mode
echo '100' > /proc/sys/vm/swappiness

# ================================== CPU SETTINGS =================================

#---------------< CPU FREQ SETTINGS >---------------#
echo '1794000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo '2704000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

#---------------< CPU HOTPLUG OPTIMIZATIONS >---------------#
echo '1' > /sys/power/cpuhotplug/governor/user_mode
echo '1' > /sys/power/cpuhotplug/governor/enabled

#---------------< ENABLE POWER EFFICIENT >---------------#
echo 'N' > /sys/module/workqueue/parameters/power_efficient

#---------------< UNDERVOLT SETTINGS >---------------#
echo '0' > /sys/power/percent_margin/big_margin_percent
echo '0' > /sys/power/percent_margin/lit_margin_percent
echo '0' > /sys/power/percent_margin/g3d_margin_percent
echo '0' > /sys/power/percent_margin/mif_margin_percent
echo '0' > /sys/power/percent_margin/aud_margin_percent
echo '0' > /sys/power/percent_margin/cam_margin_percent
echo '0' > /sys/power/percent_margin/disp_margin_percent
echo '0' > /sys/power/percent_margin/fsys0_margin_percent
echo '0' > /sys/power/percent_margin/int_margin_percent
echo '0' > /sys/power/percent_margin/intcam_margin_percent
echo '0' > /sys/power/percent_margin/iva_margin_percent
echo '0' > /sys/power/percent_margin/score_margin_percent

sleep 0.5
# ========================================== CLEANER ===========================================
rm -rf /data/data/me.weishu.kernelsu/app_webview
rm -rf /data/data/com.rifsxd.ksunext/app_webview
rm -rf /data/data/me.bmax.apatch/app_webview
rm -rf /data/adb/battery-guru-optimizer-9810
