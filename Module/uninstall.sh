# BATTERY GURU OPTIMIZER (UNISTALLER)
# Developed by @EliezerB03

#!/system/bin/sh

# ============================= GMS OPTIMIZATIONS (UNISTALLER) =============================

GMS="com.google.android.gms"
GC1="auth.managed.admin.DeviceAdminReceiver"
GC2="mdm.receivers.MdmDeviceAdminReceiver"
NLL="/dev/null"
for U in $(ls /data/user); do
for C in $GC1 $GC2 $GC3; do
pm enable --user $U "$GMS/$GMS.$C" &> $NLL
done
done
dumpsys deviceidle whitelist +com.google.android.gms &> $NLL
exit 0
)

# ============================= THERMAL OPTIMIZATIONS (UNISTALLER) =============================

system_table_unset activity_manager_constants
pm enable com.google.android.gms/.chimera.GmsIntentOperationService

rm -rf /data/adb/battery-guru-optimizer-9810
