package com.guru.batteryoptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                Intent serviceIntent = new Intent(context, BootService.class);
                context.startService(serviceIntent);
            } catch (Exception e) {}
        }
    }
}