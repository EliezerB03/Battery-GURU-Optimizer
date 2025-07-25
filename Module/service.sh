# BATTERY GURU OPTIMIZER (SERVICES)
# Developed by @EliezerB03

#!/sbin/sh

# =================== GMS OPTIMIZATIONS ===================

(   
until [ $(resetprop sys.boot_completed) -eq 1 ] &&
[ -d /sdcard ]; do
sleep 100
done
GMS="com.google.android.gms"
GC1="auth.managed.admin.DeviceAdminReceiver"
GC2="mdm.receivers.MdmDeviceAdminReceiver"
NLL="/dev/null"
for U in $(ls /data/user); do
for C in $GC1 $GC2 $GC3; do
pm disable --user $U "$GMS/$GMS.$C" &> $NLL
done
done
dumpsys deviceidle whitelist -com.google.android.gms &> $NLL
exit 0
)

