# BATTERY OPTIMIZER (CHECKS/ALL SETTINGS)
# Developed by @EliezerB03

#!/data/adb/magisk/busybox sh
set -o standalone

# ============================= GENERAL OPTIMIZATIONS =============================

echo '0' > /proc/sys/kernel/panic
echo 'N' > /sys/kernel/debug/debug_enabled
echo 'N' > /sys/kernel/debug/seclog/seclog_enabled
echo 'Y' > /sys/module/workqueue/parameters/power_efficient

# ============================= CPU FREQ SETTINGS =============================

echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
echo '1794000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

# ============================= CPU HOTPLUG OPTIMIZATIONS =============================

echo '10' > /sys/power/cpuhotplug/governor/single_change_ms
echo '10' > /sys/power/cpuhotplug/governor/dual_change_ms
echo '1' > /sys/power/cpuhotplug/governor/skip_lit_enabled
echo '1' > /sys/power/cpuhotplug/governor/ldsum_enabled
echo '175' > /sys/power/cpuhotplug/governor/big_idle_thr

# ============================= THERMAL OPTIMIZATIONS =============================



# ============================= STORAGE OPTIMIZATIONS =============================

echo '1' > /sys/block/sda/queue/rotational
echo '1' > /sys/block/sda/queue/add_random
echo '256' > /sys/block/sda/queue/nr_requests
echo '2' > /sys/block/sda/queue/rq_affinity
echo 'cfq' > /sys/block/sda/queue/scheduler
echo '0' > /sys/block/sda/queue/nomerges
echo '0' > /sys/block/sda/queue/iostats
echo 'cfq' > /sys/block/mmcblk0/queue/scheduler
echo '0' > /sys/block/mmcblk0/queue/iostats

# ============================= UNDERVOLT SETTINGS =============================

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

# ===============================================================================

# ============================= CHECKING AND PATCHING ANY CONFLICTING MODULES (if present) =============================
{
GMS0="\"com.google.android.gms"\"
STR1="allow-unthrottled-location package=$GMS0"
STR2="allow-ignore-location-settings package=$GMS0"
STR3="allow-in-power-save package=$GMS0"
STR4="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
}

{
find /data/adb/* -type f -iname "*.xml" -print |
while IFS= read -r XML; do
for X in $XML; do
if grep -qE "$STR1|$STR2|$STR3|$STR4" $X 2> $NULL; then
sed -i "/$STR1/d;/$STR2/d;/$STR3/d;/$STR4/d" $X
fi
done
done
}
