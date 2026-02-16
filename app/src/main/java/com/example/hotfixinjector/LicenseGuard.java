package com.example.hotfixinjector;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * License Guard - Continuously verifies license every 5 seconds
 * If verification fails, crashes the target application
 */
public class LicenseGuard {

    private static final String TAG = "LicenseGuard";
    private static final int VERIFICATION_INTERVAL = 5000; // 5 seconds

    private static LicenseGuard instance;
    private final LicenseClient licenseClient;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread guardThread;
    private Context targetContext;

    private LicenseGuard(Context context) {
        this.licenseClient = new LicenseClient(context);
    }

    /**
     * Get singleton instance
     */
    public static synchronized LicenseGuard getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseGuard(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Start license verification loop
     * This should be called AFTER successful initial activation
     */
    public void startGuard(Context targetContext) {
        if (isRunning.get()) {
            Log.w(TAG, "Guard already running");
            return;
        }

        this.targetContext = targetContext;
        isRunning.set(true);

        guardThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "🛡️ License Guard started - verifying every 5 seconds");

                int failureCount = 0;
                final int MAX_FAILURES = 2; // Allow 2 consecutive failures before crash

                while (isRunning.get()) {
                    try {
                        Thread.sleep(VERIFICATION_INTERVAL);

                        Log.d(TAG, "🔍 Verifying license...");

                        LicenseClient.LicenseResult result = licenseClient.verify();

                        if (result.success) {
                            Log.d(TAG, "✅ License valid");
                            failureCount = 0; // Reset failure count
                        } else {
                            failureCount++;
                            Log.e(TAG, "❌ License verification failed (" + failureCount + "/" + MAX_FAILURES + "): " + result.message);

                            if (failureCount >= MAX_FAILURES) {
                                Log.e(TAG, "💣 MAXIMUM FAILURES REACHED - TERMINATING APPLICATION");
                                crashApplication("License verification failed: " + result.message);
                                break;
                            }
                        }

                    } catch (InterruptedException e) {
                        Log.w(TAG, "Guard thread interrupted");
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Guard exception: " + e.getMessage());
                        failureCount++;

                        if (failureCount >= MAX_FAILURES) {
                            crashApplication("Guard error: " + e.getMessage());
                            break;
                        }
                    }
                }

                Log.i(TAG, "🛡️ License Guard stopped");
            }
        }, "LicenseGuardThread");

        guardThread.setDaemon(true);
        guardThread.start();
    }

    /**
     * Stop license verification loop
     */
    public void stopGuard() {
        Log.i(TAG, "Stopping License Guard...");
        isRunning.set(false);

        if (guardThread != null) {
            guardThread.interrupt();
            try {
                guardThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop guard thread");
            }
        }
    }

    /**
     * Crash the target application
     * This will forcefully terminate the scoped app
     */
    private void crashApplication(String reason) {
        Log.e(TAG, "💥💥💥 CRASHING APPLICATION 💥💥💥");
        Log.e(TAG, "Reason: " + reason);

        // Clear license data so user must re-activate
        licenseClient.clearLicense();
        Log.e(TAG, "🗑️ License data cleared");

        try {
            // Method 1: Kill the process
            if (targetContext != null) {
                String packageName = targetContext.getPackageName();
                Log.e(TAG, "Terminating package: " + packageName);
            }

            // Method 2: Throw uncaught exception
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    Log.e(TAG, "💣 UNCAUGHT EXCEPTION - APP TERMINATED");
                    Process.killProcess(Process.myPid());
                    System.exit(1);
                }
            });

            // Throw exception to trigger crash
            throw new SecurityException("LICENSE_VERIFICATION_FAILED: " + reason);

        } catch (SecurityException e) {
            // This will be caught by the uncaught exception handler above
            throw e;
        }
    }

    /**
     * Check if guard is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Force immediate verification
     */
    public boolean verifyNow() {
        Log.i(TAG, "🔍 Forced verification...");
        LicenseClient.LicenseResult result = licenseClient.verify();

        if (!result.success) {
            Log.e(TAG, "❌ Forced verification failed: " + result.message);
        }

        return result.success;
    }
}
