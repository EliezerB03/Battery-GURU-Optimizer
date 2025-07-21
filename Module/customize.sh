# BATTERY OPTIMIZER (ALL SETTINGS)
# Developed by @EliezerB03

#!/data/adb/magisk/busybox sh
set -o standalone
set -x

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
#ui_print "            Developed by EliezerB03           "
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
if [ "$(which magisk)" ]; then
ui_print "   MULTIPLE ROOT DETECTED! (ABORTING...)      "
abort
fi
elif [ "$BOOTMODE" ] && [ "$MAGISK_VER_CODE" ]; then
ui_print "   MAGISK IS NOT SUPPORTED! (ABORTING...)     "
ui_print "----------------------------------------------"
abort
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
sleep 0.8
ui_print "   * DONE!                                    "
sleep 0.5

# =================== CPU FREQ SETTINGS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "          APPLYING CPU FREQ SETTINGS          "
ui_print "----------------------------------------------"
sleep 0.6
ui_print "   * DONE!                                    "
sleep 0.5

# =================== CPU HOTPLUG OPTIMIZATIONS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "      APPLYING CPU HOTPLUG OPTIMIZATIONS      "
ui_print "----------------------------------------------"
sleep 1
ui_print "   * DONE!                                    "
sleep 0.5

# =================== THERMAL OPTIMIZATIONS ===================

#ui_print " "
#ui_print "----------------------------------------------"
#ui_print "        APPLYING THERMAL OPTIMIZATIONS        "
#ui_print "----------------------------------------------"
#sleep 1
#ui_print "   * DONE!                                    "
#sleep 0.5

# =================== STORAGE OPTIMIZATIONS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "        APPLYING STORAGE OPTIMIZATIONS        "
ui_print "----------------------------------------------"
sleep 1
ui_print "   * DONE!                                    "
sleep 0.5

# =================== GMS OPTIMIZATIONS (U-GMS-D by gloeyisk) ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "          APPLYING GMS OPTIMIZATIONS          "
ui_print "----------------------------------------------"
{
GMS0="\"com.google.android.gms"\"
STR1="allow-in-power-save package=$GMS0"
STR2="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
}
SYS_XML="$(
SXML="$(find /system_ext/* /system/* /product/* \
/vendor/* /india/* /my_bigball/* -type f -iname '*.xml' -print)"
for S in $SXML; do
if grep -qE "$STR1|$STR2" $ROOT$S 2> $NULL; then
echo "$S"
fi
done
)"

PATCH_SX() {
for SX in $SYS_XML; do
mkdir -p "$(dirname $MODPATH$SX)"
cp -af $ROOT$SX $MODPATH$SX
sed -i "/$STR1/d;/$STR2/d" $MODPATH/$SX
done

for P in product vendor; do
if [ -d $MODPATH/$P ]; then
mkdir -p $MODPATH/system/$P
mv -f $MODPATH/$P $MODPATH/system/
fi
done
}

MOD_XML="$(
MXML="$(find /data/adb/* -type f -iname "*.xml" -print)"
for M in $MXML; do
if grep -qE "$STR1|$STR2" $M; then
echo "$M"
fi
done
)"

PATCH_MX() {
for MX in $MOD_XML; do
MOD="$(echo "$MX" | awk -F'/' '{print $5}')"
sed -i "/$STR1/d;/$STR2/d" $MX
done
}

PATCH_SX && PATCH_MX

ADDON() {
mkdir -p $MODPATH/system/bin
mv -f $MODPATH/gmsc $MODPATH/system/bin/gmsc
}

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
set_perm $MODPATH/system/bin/gmsc 0 2000 0755
}
ui_print "   * DONE!                                    "
sleep 0.5

# =================== UNDERVOLT SETTINGS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "          APPLYING UNDERVOLT SETTINGS         "
ui_print "----------------------------------------------"
sleep 1.5
ui_print "   * DONE!                                    "
sleep 0.5

ui_print " "
ui_print " "
ui_print " "
ui_print "----------------------------------------------"
ui_print "BATTERY GURU OPTIMIZER SUCCESSFULLY INSTALLED!"
ui_print "----------------------------------------------"

ADDON && FINALIZE
