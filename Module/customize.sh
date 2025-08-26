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
sleep 0.5

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
sleep 0.5
ui_print "  - CHECKING ANDROID VERSION...               "
sleep 0.8
if [ $API -ge 33 ]; then
     ui_print "   * ANDROID API: $API (PASSED)               "
else
     ui_print "   UNSUPPORTED ANDROID API: $API (ABORTING...)"
     ui_print "----------------------------------------------"
     abort
fi
sleep 0.5
ui_print "  - CHECKING ROOT IMPLEMENTATION...           "
sleep 1.1
if [ "$BOOTMODE" ] && [ "$KSU" ]; then
        ui_print "   * KERNELSU/NEXT DETECTED! (PASSED)         "
        ui_print "----------------------------------------------"
elif [ "$BOOTMODE" ] && [ "$APATCH" ]; then
        ui_print "   * APATCH DETECTED! (PASSED)                "
        ui_print "----------------------------------------------"
elif [ "$BOOTMODE" ] && [ -z "$KSU" ] && [ -z "$APATCH" ]; then
        ui_print "   * MAGISK DETECTED! (PASSED)                "
        ui_print "----------------------------------------------"
elif [ "$(which magisk)" ]; then
        ui_print "   * MAGISK DETECTED! (PASSED)                "
        ui_print "----------------------------------------------"
fi
sleep 0.5

ui_print " "
ui_print "----------------------------------------------"
ui_print "   WEBUI APP INSTALLER (ONLY FOR KSU/APATCH)  "
ui_print "----------------------------------------------"
sleep 0.5
ui_print "  - INSTALLING WEBUI APP...                   "
sleep 3.3
ui_print "   * DONE!                                    "
ui_print "----------------------------------------------"
sleep 0.5

# =============================================================
# =================== GENERAL OPTIMIZATIONS ===================

ui_print " "
ui_print "----------------------------------------------"
ui_print "        APPLYING DEFAULT OPTIMIZATIONS        "
ui_print "----------------------------------------------"
sleep 0.5
ui_print "  - DISABLING KERNEL DEGUGGING...             "
sleep 0.8
ui_print "   * DONE!                                    "
sleep 0.5
ui_print "  - APPLYING STORAGE OPTIMIZATIONS...         "
sleep 1.1
ui_print "   * DONE!                                    "
sleep 0.5
ui_print "  - APPLYING THERMAL OPTIMIZATIONS...         "
sleep 1.3
ui_print "   * DONE!                                    "
sleep 0.5
ui_print "  - APPLYING SWAP OPTIMIZATIONS...            "
sleep 1.2
ui_print "   * DONE!                                    "
sleep 0.5

# ======================= CPU SETTINGS ========================

ui_print "  - APPLYING CPU FREQ SETTINGS...             "
sleep 1.3
ui_print "   * DONE!                                    "
sleep 0.5
ui_print "  - APPLYING CPU HOTPLUG OPTIMIZATIONS...     "
sleep 1.1
ui_print "   * DONE!                                    "
sleep 0.5
ui_print "  - ENABLING POWER EFFICIENT...               "
sleep 1
ui_print "   * DONE!                                    "
sleep 0.5
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
