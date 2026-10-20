package com.guru.batteryoptimizer.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.topjohnwu.superuser.Shell
import java.io.File

// Boot Action Behavior
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { context.startService(Intent(context, BootService::class.java)) }
        }
    }
}
// Boot Service Behavior
class BootService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private companion object {
        const val SCRIPT = "/data/data/com.guru.batteryoptimizer/guru.sh"
        const val MODULE_SCRIPT = "/data/adb/modules/battery-guru-optimizer-9810/boot-completed.sh"
        const val BETA_PKG = "package:com.guru.batteryoptimizer.beta"
    }
    private fun releaseWakeLock() { wakeLock?.takeIf { it.isHeld }?.release() }
    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BootService::WakeLock")?.also { it.acquire(60_000) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            try {
                Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(10))
                val shell = Shell.getShell()
                val closeShell = { if (shell.isAlive) shell.close() }
                if (!shell.isRoot) { closeShell(); return@Thread }
                if (File(MODULE_SCRIPT).exists()) Shell.cmd("rm -f $MODULE_SCRIPT").exec()
                val scriptExists = File(SCRIPT).exists()
                if (checkGuruAppOnBoot()) {
                    if (scriptExists) { Shell.cmd("rm -f $SCRIPT").exec(); closeShell() }
                    return@Thread
                }
                if (scriptExists) { Shell.cmd("chmod 755 $SCRIPT", SCRIPT).exec(); closeShell() }
            } catch (_: Exception) {
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }
    private fun checkGuruAppOnBoot(): Boolean =
        runCatching { Shell.cmd("pm list packages").exec().out.any { it.trim() == BETA_PKG } }.getOrElse { false }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }
}