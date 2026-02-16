package com.example.hotfixinjector;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Background service that verifies license with server
 * Uses AlarmManager for reliable periodic verification
 * This runs in the MODULE app context (has INTERNET permission)
 */
public class BackgroundLicenseService extends Service {

    private static final String TAG = "BgLicenseService";
    private static final long VERIFICATION_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    public static final String ACTION_VERIFY = "com.example.hotfixinjector.ACTION_VERIFY";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "🚀 [SERVICE] onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "🚀 [SERVICE] onStartCommand() - Action: " +
            (intent != null ? intent.getAction() : "null"));

        String action = intent != null ? intent.getAction() : null;

        if (ACTION_VERIFY.equals(action)) {
            // Periodic verification triggered by AlarmManager
            Log.i(TAG, "📢 [SERVICE] Periodic verification triggered by AlarmManager");
            verifyAndUpdateFile();
        } else {
            // Initial start - do immediate verification and schedule periodic
            Log.i(TAG, "📢 [SERVICE] Initial start - immediate verification");
            verifyAndUpdateFile();
            schedulePeriodicVerification();
        }

        return START_STICKY;
    }

    /**
     * Schedule periodic verification using AlarmManager (more reliable than ScheduledExecutor)
     */
    private void schedulePeriodicVerification() {
        Log.i(TAG, "⏰ [SERVICE] Scheduling periodic verification every 5 minutes...");

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "❌ [SERVICE] AlarmManager is null!");
            return;
        }

        Intent intent = new Intent(this, BackgroundLicenseService.class);
        intent.setAction(ACTION_VERIFY);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            flags
        );

        // Cancel any existing alarms first to avoid duplicates
        alarmManager.cancel(pendingIntent);

        // Schedule repeating alarm every 5 minutes
        long triggerAtMillis = SystemClock.elapsedRealtime() + VERIFICATION_INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6.0+ use setExactAndAllowWhileIdle for better reliability
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                VERIFICATION_INTERVAL_MS,
                pendingIntent
            );
        } else {
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                VERIFICATION_INTERVAL_MS,
                pendingIntent
            );
        }

        Log.i(TAG, "✅ [SERVICE] Periodic verification scheduled successfully");
    }

    /**
     * Verify license and update file
     */
    private void verifyAndUpdateFile() {
        // Run in background thread to avoid blocking
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "🔍 [VERIFY] Starting online verification...");
                    Log.i(TAG, "🔍 [VERIFY] Current time: " + System.currentTimeMillis());

                    LicenseClient licenseClient = new LicenseClient(BackgroundLicenseService.this);

                    // Send HTTP request and update file
                    LicenseClient.LicenseResult result = licenseClient.verify();

                    if (result.success) {
                        Log.i(TAG, "✅ [VERIFY] SUCCESS - File updated");
                        Log.i(TAG, "✅ [VERIFY] Message: " + result.message);
                    } else {
                        Log.e(TAG, "❌ [VERIFY] FAILED: " + result.message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ [VERIFY] Exception: " + e.getMessage(), e);
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🛑 [SERVICE] onDestroy() - Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
