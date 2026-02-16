package com.example.hotfixinjector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receiver to restart BackgroundLicenseService after device boot
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "📱 [BOOT] Device booted - checking license status...");

            // Check if license is active before starting service
            LicenseClient licenseClient = new LicenseClient(context);
            if (licenseClient.isLicenseActive()) {
                Log.i(TAG, "✅ [BOOT] License active - starting BackgroundLicenseService");

                Intent serviceIntent = new Intent(context, BackgroundLicenseService.class);
                context.startService(serviceIntent);
            } else {
                Log.w(TAG, "⚠️ [BOOT] License not active - service not started");
            }
        }
    }
}
