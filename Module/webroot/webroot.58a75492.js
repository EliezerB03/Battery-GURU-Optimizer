document.addEventListener("DOMContentLoaded",()=>{let e=document.querySelector(".close-dialog-text-reset");if(e){let s=e.closest(".reset-dialog-btn");s&&(s.removeEventListener("click",()=>{let e=document.querySelector(".oui-dialog.show"),s=document.querySelector(".oui-dialog-mask");return!!e&&!!s&&(e.classList.remove("show"),e.classList.add("hide"),s.classList.remove("show"),document.body.classList.remove("no-scroll"),setTimeout(()=>{e.classList.remove("hide")},5),!0)}),s.addEventListener("click",e=>{e.preventDefault(),e.stopPropagation();let s=document.createElement("div");s.id="temp-blocking-layer",s.style.cssText=`
          position: fixed; top: 0; left: 0;
          width: 100%; height: 100%;
          z-index: 3000; pointer-events: all;
        `,document.body.appendChild(s),document.body.classList.add("no-scroll"),(()=>{let e=document.querySelector(".oui-dialog.show"),s=document.querySelector(".oui-dialog-mask");return!!e&&(e.classList.remove("show"),e.classList.add("hide"),s&&s.classList.add("show"),document.body.classList.add("no-scroll"),setTimeout(()=>{e.classList.remove("hide")},5),!0)})()&&setTimeout(()=>{let e=document.getElementById("temp-blocking-layer");e&&e.remove(),(e=>{let s=document.getElementById(e),c=document.querySelector(".oui-dialog-mask");s&&c&&(s.classList.add("show"),c.classList.add("show"),document.body.classList.add("no-scroll"))})("closing_app_dialog"),setTimeout(()=>{(()=>{let e=["echo '0' > /proc/sys/kernel/panic","echo 'N' > /sys/kernel/debug/debug_enabled","echo 'N' > /sys/kernel/debug/seclog/seclog_enabled","echo '1' > /sys/block/sda/queue/rotational","echo '1' > /sys/block/sda/queue/add_random","echo '2' > /sys/block/sda/queue/rq_affinity","system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0","chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus","echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres","echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms","echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres","echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres","chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus","pm disable com.google.android.gms/.chimera.GmsIntentOperationService","echo '35' > /proc/sys/vm/dirty_ratio","echo '10' > /proc/sys/vm/dirty_background_ratio","echo '50000' > /proc/sys/vm/dirty_expire_centisecs","echo '20000' > /proc/sys/vm/dirty_writeback_centisecs","echo '5' > /proc/sys/vm/laptop_mode","echo '0' > /proc/sys/vm/swappiness","echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq","echo '1794000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq","echo '0' > /sys/power/cpuhotplug/governor/user_mode","echo '0' > /sys/power/cpuhotplug/governor/enabled","echo 'Y' > /sys/module/workqueue/parameters/power_efficient","echo '-4' > /sys/power/percent_margin/big_margin_percent","echo '-4' > /sys/power/percent_margin/lit_margin_percent","echo '-4' > /sys/power/percent_margin/g3d_margin_percent","echo '-4' > /sys/power/percent_margin/mif_margin_percent","echo '-4' > /sys/power/percent_margin/aud_margin_percent","echo '-4' > /sys/power/percent_margin/cam_margin_percent","echo '0' > /sys/power/percent_margin/cp_margin_percent","echo '-4' > /sys/power/percent_margin/disp_margin_percent","echo '-4' > /sys/power/percent_margin/fsys0_margin_percent","echo '-4' > /sys/power/percent_margin/int_margin_percent","echo '-4' > /sys/power/percent_margin/intcam_margin_percent","echo '-4' > /sys/power/percent_margin/iva_margin_percent","echo '-4' > /sys/power/percent_margin/score_margin_percent"],s=`# BATTERY GURU OPTIMIZER (ALL SETTINGS)
# Developed by @EliezerB03

#!/sbin/sh

# ============== GENERAL OPTIMIZATIONS  ============== #

#---------------< KERNEL DEBUGGING >---------------#
# kerneldebug_on=0
echo '0' > /proc/sys/kernel/panic
echo 'N' > /sys/kernel/debug/debug_enabled
echo 'N' > /sys/kernel/debug/seclog/seclog_enabled

#---------------< STORAGE OPTIMIZATIONS >---------------#
# storageset_on=1
echo '1' > /sys/block/sda/queue/rotational
echo '1' > /sys/block/sda/queue/add_random
echo '2' > /sys/block/sda/queue/rq_affinity

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

#---------------< SWAP OPTIMIZATIONS >---------------#
# swapset_on=1
echo '25' > /proc/sys/vm/dirty_ratio
echo '10' > /proc/sys/vm/dirty_background_ratio
echo '50000' > /proc/sys/vm/dirty_expire_centisecs
echo '20000' > /proc/sys/vm/dirty_writeback_centisecs
echo '5' > /proc/sys/vm/laptop_mode
echo '0' > /proc/sys/vm/swappiness

# ============== CPU SETTINGS ============== #

#---------------< CPU FREQ SETTINGS >---------------#
# frqset_on=1
# lit=1690 Mhz
echo '1690000' > /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# big=1794 Mhz
echo '1794000' > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

#---------------< CPU HOTPLUG OPTIMIZATIONS >---------------#
# hotplugset_on=1
echo '0' > /sys/power/cpuhotplug/governor/user_mode
echo '0' > /sys/power/cpuhotplug/governor/enabled

#---------------< ENABLE POWER EFFICIENT >---------------#
# powereffiset_on=1
echo 'Y' > /sys/module/workqueue/parameters/power_efficient

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

# ======================================`;ksu.exec("rm -rf /data/data/me.weishu.kernelsu/app_webview"),ksu.exec("rm -rf /data/data/com.rifsxd.ksunext/app_webview"),ksu.exec("rm -rf /data/data/me.bmax.apatch/app_webview"),setTimeout(()=>{e.forEach(e=>{ksu.exec(e)}),ksu.exec(`cat > /data/adb/modules/battery-guru-optimizer-9810/service.sh << 'EOF'
${s}
EOF`)},2e3)})(),setTimeout(()=>{ksu.exec("am force-stop me.weishu.kernelsu"),ksu.exec("am force-stop com.rifsxd.ksunext"),ksu.exec("am force-stop me.bmax.apatch")},2e3)},1e3)},700)}))}});
//# sourceMappingURL=webroot.58a75492.js.map
