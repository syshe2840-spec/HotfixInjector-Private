package com.example.hotfixinjector;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background service that verifies license with server every 5 minutes
 * This runs in the MODULE app context (has INTERNET permission)
 */
public class BackgroundLicenseService extends Service {

    private static final String TAG = "BgLicenseService";
    private static final int VERIFICATION_INTERVAL_MINUTES = 5;

    private ScheduledExecutorService scheduler;
    private LicenseClient licenseClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "🚀 Background License Service created");

        licenseClient = new LicenseClient(this);
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "🚀 Background License Service started");

        // IMMEDIATE verification on start
        verifyAndUpdateFile();

        // Schedule periodic verification every 5 minutes
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                verifyAndUpdateFile();
            }
        }, VERIFICATION_INTERVAL_MINUTES, VERIFICATION_INTERVAL_MINUTES, TimeUnit.MINUTES);

        // Keep service running
        return START_STICKY;
    }

    private void verifyAndUpdateFile() {
        try {
            Log.i(TAG, "🔍 [BACKGROUND] Starting online verification...");

            // Send HTTP request and update file
            LicenseClient.LicenseResult result = licenseClient.verify();

            if (result.success) {
                Log.i(TAG, "✅ [BACKGROUND] Verification SUCCESS - file updated");
            } else {
                Log.e(TAG, "❌ [BACKGROUND] Verification FAILED: " + result.message);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ [BACKGROUND] Verification exception: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🛑 Background License Service destroyed");

        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
