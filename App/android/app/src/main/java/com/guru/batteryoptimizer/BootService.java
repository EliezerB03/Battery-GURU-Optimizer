package com.guru.batteryoptimizer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.File;
import com.topjohnwu.superuser.Shell;

public class BootService extends Service {
    private PowerManager.WakeLock wakeLock;
    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BootService::WakeLock");
            wakeLock.acquire(60000);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                Shell.setDefaultBuilder(Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10));
                Shell shell = Shell.getShell();
                boolean isRoot = shell.isRoot();
                if (!isRoot) {
                    return;
                }
                String script = "/data/data/com.guru.batteryoptimizer/guru.sh";
                if (shell != null && shell.isRoot() && new File(script).exists()) {
                    Shell.cmd("chmod 755 " + script, script).exec();
                    Shell.cmd("rm -f /data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh").exec();
                    if (shell != null && shell.isAlive()) {
                        shell.close();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}