# BATTERY GURU OPTIMIZER (SERVICES)
# Developed by @EliezerB03

#!/sbin/sh

# =================== GMS OPTIMIZATIONS ===================
sleep 100
for i in $(ls /data/user/)
do
pm disable com.google.android.gms/com.google.android.gms.auth.managed.admin.DeviceAdminReceiver
pm disable com.google.android.gms/com.google.android.gms.mdm.receivers.MdmDeviceAdminReceiver
pm disable com.google.android.gms/.chimera.GmsIntentOperationService

cmd appops set com.google.android.gms BOOT_COMPLETED ignore
cmd appops set com.google.android.ims BOOT_COMPLETED ignore
cmd appops set com.google.android.gms BOOT_COMPLETED ignore
cmd appops set com.google.android.ims BOOT_COMPLETED ignore
cmd appops set com.google.android.gms.location.history BOOT_COMPLETED ignore
cmd appops set com.google.android.gm BOOT_COMPLETED ignore
cmd appops set com.google.android.marvin.talkback BOOT_COMPLETED ignore
cmd appops set com.google.android.apps.googleassistant BOOT_COMPLETED ignore
cmd appops set com.google.android.apps.carrier.log BOOT_COMPLETED ignore
cmd appops set com.android.providers.partnerbookmarks BOOT_COMPLETED ignore
cmd appops set com.google.android.apps.wellbeing BOOT_COMPLETED ignore
cmd appops set com.google.android.as BOOT_COMPLETED ignore
cmd appops set com.android.connectivity.metrics BOOT_COMPLETED ignore
cmd appops set com.android.bips BOOT_COMPLETED ignore
cmd appops set com.google.android.printservice.recommendation BOOT_COMPLETED ignore
cmd appops set com.android.hotwordenrollment.xgoogle BOOT_COMPLETED ignore
cmd appops set com.google.android.printservice.recommendation BOOT_COMPLETED ignore
cmd appops set com.android.hotwordenrollment.xgoogle BOOT_COMPLETED ignore
done
exit 0
