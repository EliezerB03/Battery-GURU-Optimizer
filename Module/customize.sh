# BATTERY GURU OPTIMIZER (CHECKS/INITS)
# Developed by @EliezerB03

#!/sbin/sh

ui_print " "
ui_print "=============================================="
ui_print "     _                                        "
ui_print "  __| |__                                     "
ui_print " |       |  ______  _     _  _____   _     _  "
ui_print " |   _   | / _____|| |   | ||  _  \ | |   | | "
ui_print " | _| |_ || |  ___ | |   | || |_| | | |   | | "
ui_print " ||_   _||| | |_  || |   | ||  _  / | |   | | "
ui_print " |  |_|  || |___| || |___| || | \ \ | |___| | "
ui_print " |       | \______| \_____/ |_|  \_\ \_____/  "
ui_print " |_______|         BATTERY OPTIMIZER          "
ui_print "                                              "
ui_print "=============================================="
ui_print " "

# =================== CHECKING REQUIREMENTS ===================
ui_print "----------------------------------------------"
ui_print "            CHECKING REQUIREMENTS             "
ui_print "----------------------------------------------"
sleep 0.5
ui_print "  - CHECKING DEVICE MODEL...                  "
sleep 0.8
current_device=$(getprop ro.boot.em.model)
if [ "$current_device" = "SM-G960F" ]; then
        ui_print "   * GALAXY S9 DETECTED! (PASSED)             "
elif [ "$current_device" = "SM-G965F" ]; then
        ui_print "   * GALAXY S9+ DETECTED! (PASSED)            "
elif [ "$current_device" = "SM-N960F" ]; then
        ui_print "   * GALAXY NOTE9 DETECTED! (PASSED)          "
elif [ "$current_device" = "SM-G960N" ]; then
        ui_print "   * GALAXY S9 DETECTED! (PASSED)             "
elif [ "$current_device" = "SM-G965N" ]; then
        ui_print "   * GALAXY S9+ DETECTED! (PASSED)            "
elif [ "$current_device" = "SM-N960N" ]; then
        ui_print "   * GALAXY NOTE9 DETECTED! (PASSED)          "
else
        ui_print "   UNSUPPORTED DEVICE! (ABORTING...)          "
        ui_print "----------------------------------------------"
        abort
fi
sleep 1.0
ui_print "  - CHECKING ROOT IMPLEMENTATION...           "
sleep 0.8
if [ "$BOOTMODE" ] && [ "$KSU" ]; then
        ui_print "   * KERNELSU/NEXT DETECTED! (PASSED)         "
elif [ "$BOOTMODE" ] && [ "$APATCH" ]; then
        ui_print "   * APATCH DETECTED! (PASSED)                "
elif [ "$BOOTMODE" ] && [ -z "$KSU" ] && [ -z "$APATCH" ]; then
        ui_print "   * MAGISK DETECTED! (PASSED)                "
elif [ "$(which magisk)" ]; then
        ui_print "   * MAGISK DETECTED! (PASSED)                "
fi
sleep 1.0
ui_print "  - CHECKING ANDROID VERSION...               "
sleep 0.5
if [ $API -ge 33 ]; then
     ui_print "   * ANDROID API: $API (PASSED)               "
     ui_print "----------------------------------------------"
else
     ui_print "   UNSUPPORTED ANDROID API: $API (ABORTING...)"
     ui_print "----------------------------------------------"
     abort
fi
sleep 1.0

# =============================================================
# =================== GENERAL OPTIMIZATIONS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "        APPLYING GENERAL OPTIMIZATIONS        "
ui_print "----------------------------------------------"
sleep 0.5
ui_print "  - DISABLING KERNEL DEGUGGING...             "
sleep 0.8
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - APPLYING STORAGE OPTIMIZATIONS...         "
sleep 1.1
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - APPLYING THERMAL OPTIMIZATIONS...         "
sleep 1.3
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - APPLYING GMS OPTIMIZATIONS...             "
{
GMS0="\"com.google.android.gms"\"
STR1="allow-in-power-save package=$GMS0"
STR2="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
}
SYS_XML="$(
SXML="$(find /system_ext/* /system/* /product/* /vendor/* -type f -iname '*.xml')"
for S in $SXML; do
echo "$S"
done
)"
PATCH_SX() {
for SX in $SYS_XML; 
do
mkdir -p "$(dirname $MODPATH$SX)"
cp -af $ROOT$SX $MODPATH$SX
sed -i "/$STR1/d;/$STR2/d" $MODPATH/$SX
done
mkdir -p $MODPATH/system/product
mv -f $MODPATH/product $MODPATH/system/
}
MOD_XML="$(
MXML="$(find /data/adb/* -type f -iname "*.xml")"
for M in $MXML; do
echo "$M"
done
)"
PATCH_MX() {
for MX in $MOD_XML; do
MOD="$(echo "$MX" | awk -f'/' '{$5}')"
sed -i "/$STR1/d;/$STR2/d" $MX
done
}
PATCH_SX && PATCH_MX

cd /data/data
find . -type f -name '*gms*' -delete

FINALIZE() {
find $MODPATH/* -maxdepth 0 \
! -name 'module.prop' \
! -name 'post-fs-data.sh' \
! -name 'service.sh' \
! -name 'system' \
-exec rm -rf {} \;
set_perm_recursive $MODPATH 0 0 0755 0755
}
FINALIZE

ui_print "   * DONE!                                    "
ui_print "----------------------------------------------"
sleep 0.5

# ======================= CPU SETTINGS ========================

ui_print " "
ui_print "----------------------------------------------"
ui_print "        APPLYING CPU/UNDERVOLT SETTINGS       "
ui_print "----------------------------------------------"
sleep 0.5
ui_print "  - APPLYING CPU FREQ SETTINGS...             "
sleep 1.2
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - APPLYING CPU HOTPLUG OPTIMIZATIONS...     "
sleep 0.8
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - ENABLING POWER EFFICIENT...               "
sleep 0.7
ui_print "   * DONE!                                    "
sleep 0.3
ui_print "  - APPLYING UNDERVOLT SETTINGS               "
sleep 1.8
ui_print "   * DONE!                                    "
ui_print "----------------------------------------------"
sleep 0.5

# =============================================================

ui_print " "
ui_print " "
ui_print " "
ui_print "----------------------------------------------"
ui_print "BATTERY GURU OPTIMIZER SUCCESSFULLY INSTALLED!"
ui_print "----------------------------------------------"
ui_print "   * REBOOT YOUR DEVICE TO APPLY!             "
ui_print " "
