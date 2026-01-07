# BATTERY GURU OPTIMIZER (CHECKS/INITS)
# Developed by @EliezerB03

#!/sbin/sh

ui_print " "
ui_print "============================================"
ui_print "    _                                       "
ui_print " __| |__                                    "
ui_print "|       |  ______  _     _  _____   _     _ "
ui_print "|   _   | / _____|| |   | ||  _  \ | |   | |"
ui_print "| _| |_ || |  ___ | |   | || |_| | | |   | |"
ui_print "||_   _||| | |_  || |   | ||  _  / | |   | |"
ui_print "|  |_|  || |___| || |___| || | \ \ | |___| |"
ui_print "|       | \______| \_____/ |_|  \_\ \_____/ "
ui_print "|_______|         BATTERY OPTIMIZER         "
ui_print "                                            "
ui_print "============================================"
ui_print " "
sleep 0.5

# =================== CHECKING REQUIREMENTS ===================
ui_print "--------------------------------------------"
ui_print "          CHECKING REQUIREMENTS             "
ui_print "--------------------------------------------"
sleep 0.5
ui_print "- CHECKING DEVICE MODEL...                  "
sleep 0.8
current_device=$(getprop ro.boot.em.model)
if [ "$current_device" = "SM-G960F" ]; then
        ui_print " * GALAXY S9 DETECTED! (PASSED)             "
elif [ "$current_device" = "SM-G965F" ]; then
        ui_print " * GALAXY S9+ DETECTED! (PASSED)            "
elif [ "$current_device" = "SM-N960F" ]; then
        ui_print " * GALAXY NOTE9 DETECTED! (PASSED)          "
elif [ "$current_device" = "SM-G960N" ]; then
        ui_print " * GALAXY S9 DETECTED! (PASSED)             "
elif [ "$current_device" = "SM-G965N" ]; then
        ui_print " * GALAXY S9+ DETECTED! (PASSED)            "
elif [ "$current_device" = "SM-N960N" ]; then
        ui_print " * GALAXY NOTE9 DETECTED! (PASSED)          "
elif [ "$current_device" = "SM-N770F" ]; then
        ui_print " * GALAXY NOTE10 LITE DETECTED! (PASSED)    "
else
        ui_print " UNSUPPORTED DEVICE! (ABORTING...)        "
        ui_print ""
        ui_print " Check Device Support on Github Source    "
        ui_print "------------------------------------------"
        abort
fi
sleep 0.5
ui_print "- CHECKING ANDROID VERSION...               "
sleep 0.8
if [ $API -ge 33 ]; then
     ui_print " * ANDROID API: $API (PASSED)               "
else
     ui_print " UNSUPPORTED ANDROID API: $API (ABORTING...)"
     ui_print ""
     ui_print " Check Android Support on Github Source     "
     ui_print "--------------------------------------------"
     abort
fi
sleep 0.5
ui_print "- CHECKING ROOT IMPLEMENTATION...           "
sleep 1.1
if [ "$BOOTMODE" ] && [ "$KSU" ]; then
        ui_print " * KERNELSU/NEXT DETECTED! (PASSED)         "
        ui_print "--------------------------------------------"
elif [ "$BOOTMODE" ] && [ "$APATCH" ]; then
        ui_print " * APATCH DETECTED! (PASSED)                "
        ui_print "--------------------------------------------"
elif [ "$BOOTMODE" ] && [ -z "$KSU" ] && [ -z "$APATCH" ]; then
        ui_print " * MAGISK DETECTED! (PASSED)                "
        ui_print "--------------------------------------------"
elif [ "$(which magisk)" ]; then
        ui_print " * MAGISK DETECTED! (PASSED)                "
        ui_print "--------------------------------------------"
fi
sleep 0.5

ui_print " "
ui_print "--------------------------------------------"
ui_print "  WEBUI APP INSTALLER (ONLY FOR KSU/APATCH) "
ui_print "--------------------------------------------"
sleep 0.5
ui_print "- INSTALLING WEBUI APP...                   "
sleep 2.3
ui_print " * DONE!                                    "
ui_print "--------------------------------------------"
sleep 0.5

# =============================================================
# =================== ALL OPTIMIZATIONS ===================

ui_print " "
ui_print "--------------------------------------------"
ui_print "           APPLYING OPTIMIZATIONS           "
ui_print "--------------------------------------------"
rm -f $MODPATH/service.sh
sleep 0.5

ui_print "- APPLYING SYSTEM SETTINGS...               "
mkdir -p $MODPATH/system/product/etc/sysconfig/
sleep 0.5
cp /system/product/etc/sysconfig/google.xml $MODPATH/system/product/etc/sysconfig/google.xml
sleep 0.5
sed -i '/allow-in-power-save.*com.google.android.gms/d' $MODPATH/system/product/etc/sysconfig/google.xml
sleep 0.5
sed -i '/allow-in-data-usage-save.*com.google.android.gms/d' $MODPATH/system/product/etc/sysconfig/google.xml
sleep 0.1
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING CPU SETTINGS...                  "
sleep 1.9
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING GPU SETTINGS...                  "
sleep 1.6
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING MEMORY SETTINGS...               "
sleep 1.3
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING STORAGE SETTINGS...              "
sleep 1.1
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING CPU FREQ SETTINGS...             "
sleep 1.4
ui_print " * DONE!                                    "
sleep 0.5

ui_print "- APPLYING UNDERVOLT SETTINGS...            "
sleep 2.1
ui_print " * DONE!                                    "
ui_print "--------------------------------------------"
sleep 0.5

# =============================================================

ui_print " "
ui_print " "
ui_print " "
ui_print "--------------------------------------------"
ui_print "BATTERY GURU OPTIMIZER INSTALLED!           "
ui_print "--------------------------------------------"
ui_print " * REBOOT YOUR DEVICE TO APPLY!             "
ui_print " "
