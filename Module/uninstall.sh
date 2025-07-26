# BATTERY GURU OPTIMIZER (UNISTALLER)
# Developed by @EliezerB03

#!/system/bin/sh

# ============================= THERMAL OPTIMIZATIONS (UNISTALLER) =============================
system_table_unset activity_manager_constants
pm enable com.google.android.gms/.chimera.GmsIntentOperationService

# ========================================== CLEANER ===========================================
rm -rf /data/adb/battery-guru-optimizer-9810
