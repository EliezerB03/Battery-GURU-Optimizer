package com.guru.batteryoptimizer.core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.topjohnwu.superuser.Shell
import java.io.File

class BootService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BootService::WakeLock")?.also { it.acquire(60_000) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            try {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER)
                        .setTimeout(10)
                )
                val shell = Shell.getShell()
                if (!shell.isRoot) {
                    if (shell.isAlive) shell.close()
                    return@Thread
                }
                val removeModuleScript = "/data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh"
                if (File(removeModuleScript).exists()) {
                    Shell.cmd("rm -f /data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh").exec()
                }
                val guruAppInstalled = checkGuruAppOnBoot()
                if (guruAppInstalled) {
                    val removeScript = "/data/data/com.guru.batteryoptimizer/guru.sh"
                    if (File(removeScript).exists()) {
                        Shell.cmd("rm -f /data/data/com.guru.batteryoptimizer/guru.sh").exec()
                        if (shell.isAlive) shell.close()
                        return@Thread
                    }
                    return@Thread
                }
                val script = "/data/data/com.guru.batteryoptimizer/guru.sh"
                if (File(script).exists()) {
                    Shell.cmd("chmod 755 $script", script).exec()
                    if (shell.isAlive) shell.close()
                }
            } catch (_: Exception) {
            } finally {
                wakeLock?.takeIf { it.isHeld }?.release()
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }
    private fun checkGuruAppOnBoot(): Boolean {
        return runCatching {
            val result = Shell.cmd("pm list packages").exec()
            result.out.any { it.trim() == "package:com.guru.batteryoptimizer.beta" }
        }.getOrElse { false }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}