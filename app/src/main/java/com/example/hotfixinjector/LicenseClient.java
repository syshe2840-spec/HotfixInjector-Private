package com.example.hotfixinjector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure License Client with AES-256-GCM Encryption
 * Communicates with Cloudflare Worker API
 */
public class LicenseClient {

    private static final String TAG = "LicenseClient";
    private static final String PREFS_NAME = "license_prefs";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_EXPIRES_AT = "expires_at";

    // License file in ROOT directory - accessible by Xposed module with root privileges
    // Encrypted and device-specific
    private static final String LICENSE_FILE = "/data/adb/.hf_license";

    // Cloudflare Worker URL
    private static final String API_BASE_URL = "https://hotapp.lastofanarchy.workers.dev";

    // ⚡ HARDCODED LICENSE KEY - USER MUST SET THIS!
    // Put your license key here so module can verify without file/SharedPreferences
    // Example: "HOTFIX-XXXX-XXXX-XXXX-XXXX"
    // Module will use this key to verify with server every time
    private static final String HARDCODED_LICENSE_KEY = "";

    // Base seed for encryption key generation
    private static final String ENCRYPTION_SEED = "HotFix_License_Key_Seed_v1";

    private final Context context;
    private final SharedPreferences prefs;
    private String cachedDeviceId;

    public LicenseClient(Context context) {
        if (context != null) {
            this.context = context.getApplicationContext();
            this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } else {
            this.context = null;
            this.prefs = null;
        }
    }

    /**
     * Get unique device ID based on REAL hardware (cannot be faked by Device ID Changer)
     */
    public String getDeviceId() {
        if (cachedDeviceId != null) {
            return cachedDeviceId;
        }

        if (prefs == null) {
            // Generate without caching (for temp instances)
            return generateDeviceId();
        }

        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = generateDeviceId();

            // Save permanently
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();

            Log.i(TAG, "🔑 Generated Hardware-Based Device ID");
        }

        cachedDeviceId = deviceId;
        return deviceId;
    }

    /**
     * Generate device-specific encryption key (32 bytes)
     * Key is unique per device but deterministic (same device = same key)
     */
    private static String getDeviceEncryptionKey() {
        // Generate hardware fingerprint
        StringBuilder hwInfo = new StringBuilder();
        hwInfo.append(Build.BOARD).append("|");
        hwInfo.append(Build.BRAND).append("|");
        hwInfo.append(Build.DEVICE).append("|");
        hwInfo.append(Build.HARDWARE).append("|");
        hwInfo.append(Build.MANUFACTURER).append("|");
        hwInfo.append(Build.MODEL).append("|");
        hwInfo.append(Build.PRODUCT).append("|");
        hwInfo.append(ENCRYPTION_SEED);

        // Generate SHA-256 hash and take first 32 bytes
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwInfo.toString().getBytes(StandardCharsets.UTF_8));

            // Convert first 32 bytes to hex string (32 bytes = 64 hex chars, take first 32)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 16; i++) { // 16 bytes = 32 hex chars = 32 bytes when converted back
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // Fallback to fixed key
            return "Kh7Gm2Qp5Rt8Wx4Zv1Nc9Bs6Yf3Dj0A";
        }
    }

    /**
     * Generate device ID from hardware info
     */
    private String generateDeviceId() {
        // Generate unique device ID using REAL hardware info
        // These CANNOT be changed by Device ID Changer apps!
        StringBuilder hwInfo = new StringBuilder();

        // Hardware-level identifiers (unchangeable)
        hwInfo.append(Build.BOARD).append("|");        // Motherboard
        hwInfo.append(Build.BRAND).append("|");        // Brand
        hwInfo.append(Build.DEVICE).append("|");       // Device codename
        hwInfo.append(Build.HARDWARE).append("|");     // Hardware name
        hwInfo.append(Build.MANUFACTURER).append("|"); // Manufacturer
        hwInfo.append(Build.MODEL).append("|");        // Model
        hwInfo.append(Build.PRODUCT).append("|");      // Product name
        hwInfo.append(Build.SERIAL).append("|");       // Serial number

        // Android ID (as backup) - only if context available
        if (context != null) {
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            hwInfo.append(androidId);
        }

        // Generate SHA-256 hash of all hardware info
        return sha256(hwInfo.toString());
    }

    /**
     * Get copyable device ID for user
     */
    public String getCopyableDeviceId() {
        String id = getDeviceId();
        // Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (easier to copy)
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append("-");
            }
            formatted.append(id.charAt(i));
        }
        return formatted.toString().toUpperCase();
    }

    /**
     * Activate license with server
     */
    public LicenseResult activate(String licenseKey) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("license_key", licenseKey);
            payload.put("device_id", getDeviceId());
            payload.put("device_info", getDeviceInfo());

            String response = sendRequest("/activate", payload);
            JSONObject json = new JSONObject(response);

            if (json.getBoolean("success")) {
                String sessionToken = json.getString("session_token");
                String nonce = json.getString("nonce");  // ⚡ NEW: Get nonce from server
                long expiresAt = json.optLong("expires_at", 0);

                Log.i(TAG, "🔑 [ACTIVATE] Received nonce from server");
                Log.d(TAG, "🔑 [ACTIVATE] Nonce length: " + nonce.length());

                // Save credentials (including license_key and nonce)
                prefs.edit()
                    .putString("license_key", licenseKey)
                    .putString(KEY_SESSION_TOKEN, sessionToken)
                    .putString("nonce", nonce)  // ⚡ NEW: Store nonce
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply();

                // Write to encrypted file for cross-app access
                writeLicenseToFile();

                Log.i(TAG, "✅ License activated successfully with nonce");
                return LicenseResult.success("License activated");
            } else {
                String error = json.optString("error", "Unknown error");
                Log.e(TAG, "❌ Activation failed: " + error);
                return LicenseResult.failure(error);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Activation exception: " + e.getMessage());
            return LicenseResult.failure("Network error: " + e.getMessage());
        }
    }

    /**
     * Verify license OFFLINE - Just read file, no HTTP request!
     * Used by Hook (fast, doesn't need INTERNET permission)
     */
    public LicenseResult verifyOffline() {
        try {
            Log.i(TAG, "[VERIFY-OFFLINE] Reading cached status from file...");

            LicenseData license = readLicenseFromFile();

            if (license == null) {
                Log.e(TAG, "[VERIFY-OFFLINE] ❌ No license file");
                return LicenseResult.failure("No active license");
            }

            // ⚡ CHECK 1: Nonce must exist (one-time token validation)
            if (license.nonce == null || license.nonce.isEmpty()) {
                Log.e(TAG, "[VERIFY-OFFLINE] ❌ NONCE MISSING - License tampered!");
                clearLicense();
                return LicenseResult.failure("Security token missing");
            }

            if (license.isBurned()) {
                Log.e(TAG, "[VERIFY-OFFLINE] 🔥 License is BURNED");
                clearLicense();
                return LicenseResult.failure("License burned");
            }

            if ("valid".equals(license.status)) {
                long age = (System.currentTimeMillis() - license.lastCheck) / 1000;
                Log.i(TAG, "[VERIFY-OFFLINE] ✅ Status: VALID (checked " + age + "s ago)");
                return LicenseResult.success("Valid");
            } else {
                Log.e(TAG, "[VERIFY-OFFLINE] ❌ Status: " + license.status);
                return LicenseResult.failure("Invalid");
            }

        } catch (Exception e) {
            Log.e(TAG, "[VERIFY-OFFLINE] ❌ Exception: " + e.getMessage());
            return LicenseResult.failure("Read error");
        }
    }

    /**
     * Verify license ONLINE - Send HTTP request and update file
     * Used by Background Guard (needs INTERNET permission)
     *
     * Logic:
     * 1. Read license file
     * 2. Check if burned → delete file, fail
     * 3. Send HTTP request
     * 4. Update file with new status and timestamp
     */
    public LicenseResult verify() {
        try {
            Log.i(TAG, "[VERIFY] ========================================");
            Log.i(TAG, "[VERIFY] Starting ALWAYS-ONLINE verification...");

            // 1. Read license from file
            LicenseData license = readLicenseFromFile();

            if (license == null) {
                Log.e(TAG, "[VERIFY] ❌ No license file found");
                return LicenseResult.failure("No active license");
            }

            // 2. Check if BURNED (from previous check)
            if (license.isBurned()) {
                Log.e(TAG, "[VERIFY] 🔥 LICENSE IS BURNED (cached)!");
                Log.e(TAG, "[VERIFY] Deleting burned license file...");
                clearLicense();
                return LicenseResult.failure("License burned");
            }

            // ⚡ CHECK 2: Nonce must exist
            if (license.nonce == null || license.nonce.isEmpty()) {
                Log.e(TAG, "[VERIFY] ❌ NONCE MISSING - Cannot verify!");
                clearLicense();
                return LicenseResult.failure("Security token missing");
            }

            // 3. ALWAYS perform online verification (no cache!)
            Log.i(TAG, "[VERIFY] Sending request to server with nonce...");
            Log.d(TAG, "[VERIFY] Current nonce length: " + license.nonce.length());

            JSONObject payload = new JSONObject();
            payload.put("license_key", license.licenseKey);  // ⚡ NEW: Include license_key for XOR
            payload.put("session_token", license.sessionToken);
            payload.put("nonce", license.nonce);  // ⚡ NEW: Send current nonce
            payload.put("device_id", license.deviceId);

            String response = sendRequest("/verify", payload);
            JSONObject json = new JSONObject(response);

            String newStatus = "invalid";
            String newNonce = null;

            if (json.getBoolean("success") && json.optBoolean("valid", false)) {
                newStatus = "valid";
                newNonce = json.getString("nonce");  // ⚡ NEW: Get new nonce from server
                Log.i(TAG, "[VERIFY] ✅ Server verification SUCCESS");
                Log.i(TAG, "[VERIFY] 🔑 Received new nonce from server");
                Log.d(TAG, "[VERIFY] New nonce length: " + newNonce.length());
            } else {
                String error = json.optString("error", "");
                // Check if server says it's burned
                if (error.contains("burned") || error.contains("revoked")) {
                    newStatus = "burned";
                    Log.e(TAG, "[VERIFY] 🔥 Server says license is BURNED!");
                } else {
                    Log.e(TAG, "[VERIFY] ❌ Server verification FAILED: " + error);
                }
            }

            // 4. Update file with new status, timestamp, and nonce (ALWAYS!)
            updateLicenseStatus(newStatus, newNonce);

            // If burned, delete file
            if ("burned".equals(newStatus)) {
                clearLicense();
                return LicenseResult.failure("License burned");
            }

            if ("valid".equals(newStatus)) {
                return LicenseResult.success("Valid");
            } else {
                return LicenseResult.failure("Invalid");
            }

        } catch (Exception e) {
            Log.e(TAG, "[VERIFY] ❌❌❌ VERIFICATION EXCEPTION ❌❌❌");
            Log.e(TAG, "[VERIFY] Exception type: " + e.getClass().getName());
            Log.e(TAG, "[VERIFY] Exception message: " + e.getMessage());
            e.printStackTrace();
            return LicenseResult.failure("Network error: " + e.getMessage());
        }
    }

    /**
     * Verify license with server using pre-loaded license data (for Xposed module use)
     */
    public LicenseResult verifyWithData(LicenseData licenseData) {
        try {
            if (licenseData == null || licenseData.sessionToken == null) {
                return LicenseResult.failure("No license data provided");
            }

            // Check expiration (offline check first)
            if (!licenseData.isValid()) {
                return LicenseResult.failure("License expired");
            }

            JSONObject payload = new JSONObject();
            payload.put("session_token", licenseData.sessionToken);
            payload.put("device_id", licenseData.deviceId);

            String response = sendRequest("/verify", payload);
            JSONObject json = new JSONObject(response);

            if (json.getBoolean("success") && json.optBoolean("valid", false)) {
                Log.d(TAG, "✅ License verified (with data)");
                return LicenseResult.success("Valid");
            } else {
                String error = json.optString("error", "Invalid license");
                Log.e(TAG, "❌ Verification failed (with data): " + error);
                return LicenseResult.failure(error);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Verification exception (with data): " + e.getMessage());
            return LicenseResult.failure("Network error");
        }
    }

    /**
     * Update license status in file (after online verification)
     */
    private void updateLicenseStatus(String newStatus, String newNonce) {
        try {
            Log.i(TAG, "[UPDATE] Updating license status to: " + newStatus);
            if (newNonce != null) {
                Log.i(TAG, "[UPDATE] Updating nonce (length: " + newNonce.length() + ")");
            }

            // Read current file
            LicenseData oldLicense = readLicenseFromFile();
            if (oldLicense == null) {
                Log.e(TAG, "[UPDATE] ❌ Cannot update - no license file");
                return;
            }

            // Use new nonce if provided, otherwise keep old nonce
            String nonceToSave = (newNonce != null) ? newNonce : oldLicense.nonce;

            // Create updated JSON
            JSONObject data = new JSONObject();
            data.put("license_key", oldLicense.licenseKey);
            data.put("token", oldLicense.sessionToken);
            data.put("nonce", nonceToSave);  // ⚡ NEW: Save nonce
            data.put("status", newStatus);
            data.put("last_check", System.currentTimeMillis());
            data.put("expires", oldLicense.expiresAt);
            data.put("device", oldLicense.deviceId);

            // Also update SharedPreferences with new nonce
            if (prefs != null && newNonce != null) {
                prefs.edit().putString("nonce", newNonce).apply();
            }

            // Encrypt and write
            String encrypted = encryptAES(data.toString());

            // Write to ROOT directory
            writeLicenseToRootFile(encrypted);

            Log.i(TAG, "[UPDATE] ✅ License status and nonce updated successfully");

        } catch (Exception e) {
            Log.e(TAG, "[UPDATE] ❌ Failed to update license status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if license is activated and not expired (offline check)
     */
    public boolean isLicenseActive() {
        String sessionToken = prefs.getString(KEY_SESSION_TOKEN, null);
        if (sessionToken == null) {
            return false;
        }

        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            clearLicense();
            return false;
        }

        return true;
    }

    /**
     * Clear license data
     */
    public void clearLicense() {
        // Clear SharedPreferences if available
        if (prefs != null) {
            prefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .apply();
        }

        // Also clear encrypted file from root
        try {
            executeRootCommand("rm -f " + LICENSE_FILE);
            Log.i(TAG, "🗑️ Root license file deleted");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete license file: " + e.getMessage());
        }
    }

    /**
     * Write encrypted license to file (external storage - no root needed!)
     */
    private void writeLicenseToFile() {
        try {
            if (prefs == null) {
                Log.w(TAG, "Cannot write license file: prefs is null");
                return;
            }

            String sessionToken = prefs.getString(KEY_SESSION_TOKEN, null);
            String nonce = prefs.getString("nonce", null);  // ⚡ NEW: Get nonce
            long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);

            if (sessionToken == null) {
                return;
            }

            if (nonce == null || nonce.isEmpty()) {
                Log.w(TAG, "[WRITE] ⚠️ Nonce is missing! This may cause verification to fail.");
            }

            // Create JSON with license data (NEW FORMAT with nonce)
            JSONObject data = new JSONObject();
            data.put("license_key", prefs.getString("license_key", ""));
            data.put("token", sessionToken);
            data.put("nonce", nonce != null ? nonce : "");  // ⚡ NEW: Store nonce
            data.put("status", "valid");
            data.put("last_check", System.currentTimeMillis());
            data.put("expires", expiresAt);
            data.put("device", getDeviceId());

            // Encrypt
            String encrypted = encryptAES(data.toString());

            // Write to ROOT directory using su command
            Log.i(TAG, "[WRITE] Writing to root: " + LICENSE_FILE);

            writeLicenseToRootFile(encrypted);

            Log.i(TAG, "✅ License written to root successfully!");
            Log.i(TAG, "✅ File path: " + LICENSE_FILE);

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to write license file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Write license data to root file using su
     */
    private void writeLicenseToRootFile(String encrypted) throws Exception {
        // Write encrypted data to temp file first
        java.io.File tempFile = new java.io.File(context.getCacheDir(), ".hf_temp");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
        fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
        fos.close();

        Log.i(TAG, "[ROOT] Temp file written: " + tempFile.getAbsolutePath());

        // Use su to copy to root location
        Process process = Runtime.getRuntime().exec("su");
        java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());

        // Make /data/adb directory accessible (755 = rwxr-xr-x)
        os.writeBytes("chmod 755 /data/adb\n");
        // Copy temp file to root location
        os.writeBytes("cp " + tempFile.getAbsolutePath() + " " + LICENSE_FILE + "\n");
        // Set file permissions to world-readable (666 = rw-rw-rw-)
        os.writeBytes("chmod 666 " + LICENSE_FILE + "\n");
        os.writeBytes("exit\n");
        os.flush();

        int exitCode = process.waitFor();

        // Delete temp file
        tempFile.delete();

        if (exitCode != 0) {
            throw new Exception("Root command failed with exit code: " + exitCode);
        }

        Log.i(TAG, "[ROOT] ✅ License copied to root successfully");
    }

    /**
     * Execute command with root privileges
     */
    private void executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "Root command failed: " + e.getMessage());
        }
    }

    /**
     * Read encrypted license from ROOT file (accessible by Xposed module)
     */
    public static LicenseData readLicenseFromFile() {
        try {
            Log.i("LicenseClient", "[READ] ========================================");
            Log.i("LicenseClient", "[READ] Starting file read from: " + LICENSE_FILE);

            // Read directly WITHOUT su (file has 666 permissions)
            java.io.File file = new java.io.File(LICENSE_FILE);
            Log.i("LicenseClient", "[READ] File object created");

            Log.i("LicenseClient", "[READ] Checking if file exists...");
            boolean exists = file.exists();
            Log.i("LicenseClient", "[READ] File exists: " + exists);

            if (!exists) {
                Log.e("LicenseClient", "[READ] ❌ FAILED - File doesn't exist!");
                return null;
            }

            Log.i("LicenseClient", "[READ] Checking if file is readable...");
            boolean canRead = file.canRead();
            Log.i("LicenseClient", "[READ] File canRead: " + canRead);
            Log.i("LicenseClient", "[READ] File path: " + file.getAbsolutePath());
            Log.i("LicenseClient", "[READ] File length: " + file.length());

            if (!canRead) {
                Log.e("LicenseClient", "[READ] ❌ FAILED - File is not readable!");
                Log.e("LicenseClient", "[READ] This might be SELinux or permission issue");
                return null;
            }

            Log.i("LicenseClient", "[READ] Opening FileInputStream...");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            Log.i("LicenseClient", "[READ] FileInputStream opened successfully");

            Log.i("LicenseClient", "[READ] Reading file data...");
            byte[] data = new byte[(int) file.length()];
            int bytesRead = fis.read(data);
            fis.close();
            Log.i("LicenseClient", "[READ] Bytes read: " + bytesRead);

            String encrypted = new String(data, StandardCharsets.UTF_8);

            if (encrypted.isEmpty()) {
                Log.e("LicenseClient", "[READ] ❌ License file is empty");
                return null;
            }

            Log.i("LicenseClient", "[READ] ✅ License read successfully (" + encrypted.length() + " chars)");

            // Decrypt
            LicenseClient tempClient = new LicenseClient(null);
            String decrypted = tempClient.decryptAES(encrypted);

            if (decrypted == null || decrypted.isEmpty()) {
                Log.e("LicenseClient", "[READ] Decryption failed (empty or null)");
                return null;
            }

            Log.i("LicenseClient", "[READ] Decryption successful");

            // Parse JSON (NEW FORMAT with nonce, status and last_check)
            JSONObject json = new JSONObject(decrypted);
            String licenseKey = json.optString("license_key", "");
            String token = json.getString("token");
            String nonce = json.optString("nonce", null);  // ⚡ NEW: Read nonce
            String status = json.optString("status", "valid");
            long lastCheck = json.optLong("last_check", 0);
            long expires = json.getLong("expires");
            String device = json.getString("device");

            Log.i("LicenseClient", "[READ] License parsed:");
            Log.i("LicenseClient", "[READ]   - token: " + token.substring(0, Math.min(20, token.length())) + "...");
            Log.i("LicenseClient", "[READ]   - nonce: " + (nonce != null ? "YES (" + nonce.length() + " chars)" : "MISSING"));
            Log.i("LicenseClient", "[READ]   - status: " + status);
            Log.i("LicenseClient", "[READ]   - last_check: " + lastCheck);
            Log.i("LicenseClient", "[READ]   - expires: " + expires);

            LicenseData licenseData = new LicenseData(licenseKey, token, nonce, status, lastCheck, expires, device);

            if (!licenseData.isValid()) {
                Log.e("LicenseClient", "[READ] License data is INVALID (expired or empty token)");
                if (expires > 0) {
                    Log.e("LicenseClient", "[READ] Expiration check - now: " + System.currentTimeMillis() + ", expires: " + expires);
                }
            } else {
                Log.i("LicenseClient", "[READ] ✅ License data is VALID");
            }

            return licenseData;

        } catch (Exception e) {
            Log.e("LicenseClient", "[READ] ❌❌❌ EXCEPTION CAUGHT ❌❌❌");
            Log.e("LicenseClient", "[READ] Exception type: " + e.getClass().getName());
            Log.e("LicenseClient", "[READ] Exception message: " + e.getMessage());
            Log.e("LicenseClient", "[READ] Stack trace:");
            e.printStackTrace();

            // Also print to XposedBridge log if available
            try {
                Class<?> xposedBridge = Class.forName("de.robv.android.xposed.XposedBridge");
                java.lang.reflect.Method log = xposedBridge.getMethod("log", String.class);
                log.invoke(null, "[READ] ❌ Exception: " + e.getClass().getName() + ": " + e.getMessage());
            } catch (Exception ignored) {}

            return null;
        }
    }

    /**
     * Read file content using root (su cat)
     */
    private static String readFileWithRoot(String filePath) {
        try {
            Log.i("LicenseClient", "[ROOT] Executing: su -c 'cat " + filePath + "'");

            Process process = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
            os.writeBytes("cat " + filePath + "\n");
            os.writeBytes("exit\n");
            os.flush();

            // Read output
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Read errors
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
            );
            StringBuilder errors = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            reader.close();
            errorReader.close();
            os.close();

            if (exitCode != 0) {
                Log.e("LicenseClient", "[ROOT] Command failed with exit code: " + exitCode);
                if (errors.length() > 0) {
                    Log.e("LicenseClient", "[ROOT] Errors: " + errors.toString());
                }
                return null;
            }

            if (errors.length() > 0) {
                Log.w("LicenseClient", "[ROOT] Warnings: " + errors.toString());
            }

            String result = output.toString().trim();
            if (result.isEmpty()) {
                Log.e("LicenseClient", "[ROOT] Read result is empty");
                return null;
            }

            Log.i("LicenseClient", "[ROOT] Read successful (" + result.length() + " chars)");
            return result;

        } catch (Exception e) {
            Log.e("LicenseClient", "[ROOT] Exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * License data holder
     */
    public static class LicenseData {
        public final String licenseKey;        // License key (not session token!)
        public final String sessionToken;      // Session token from server
        public final String nonce;             // One-time use token (updated after each verification)
        public final String status;            // "valid" | "invalid" | "burned"
        public final long lastCheck;           // Last online verification timestamp
        public final long expiresAt;
        public final String deviceId;

        public LicenseData(String licenseKey, String sessionToken, String nonce, String status, long lastCheck, long expiresAt, String deviceId) {
            this.licenseKey = licenseKey;
            this.sessionToken = sessionToken;
            this.nonce = nonce;
            this.status = status;
            this.lastCheck = lastCheck;
            this.expiresAt = expiresAt;
            this.deviceId = deviceId;
        }

        // Legacy constructor for backward compatibility
        public LicenseData(String sessionToken, long expiresAt, String deviceId) {
            this.licenseKey = null;
            this.sessionToken = sessionToken;
            this.nonce = null;
            this.status = "valid";
            this.lastCheck = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.deviceId = deviceId;
        }

        public boolean isValid() {
            // Check if burned
            if ("burned".equals(status)) {
                return false;
            }
            // Check if invalid
            if ("invalid".equals(status)) {
                return false;
            }
            // Check session token
            if (sessionToken == null || sessionToken.isEmpty()) {
                return false;
            }
            // Check expiration
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                return false;
            }
            return true;
        }

        /**
         * Check if license needs online verification (last check > 5 minutes ago)
         */
        public boolean needsOnlineCheck() {
            long now = System.currentTimeMillis();
            long fiveMinutes = 5 * 60 * 1000; // 5 minutes in milliseconds
            return (now - lastCheck) > fiveMinutes;
        }

        /**
         * Check if status is burned
         */
        public boolean isBurned() {
            return "burned".equals(status);
        }

        /**
         * Check if cache is fresh (< 5 minutes old)
         */
        public boolean isCacheFresh() {
            return !needsOnlineCheck();
        }
    }

    /**
     * Get device information
     */
    private String getDeviceInfo() {
        return String.format("%s %s, Android %s (SDK %d)",
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT
        );
    }

    /**
     * Send encrypted request to server
     */
    private String sendRequest(String endpoint, JSONObject payload) throws Exception {
        Log.i(TAG, "[HTTP] ========================================");
        Log.i(TAG, "[HTTP] Preparing HTTP request");
        Log.i(TAG, "[HTTP] Endpoint: " + endpoint);
        Log.i(TAG, "[HTTP] Full URL: " + API_BASE_URL + endpoint);

        URL url = new URL(API_BASE_URL + endpoint);
        Log.i(TAG, "[HTTP] URL object created");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        Log.i(TAG, "[HTTP] Connection opened");

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "HotfixInjector/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            Log.i(TAG, "[HTTP] Request configured (timeout: 10s)");

            // ==================== XOR ENCRYPTION ====================
            // Encrypt payload with XOR before sending
            String jsonString = payload.toString();
            Log.i(TAG, "[HTTP] Original payload size: " + jsonString.length() + " bytes");

            // Get license_key from payload or from file
            String licenseKey = payload.optString("license_key", null);
            if (licenseKey == null) {
                // Try to get from saved data
                LicenseData license = readLicenseFromFile();
                if (license != null) {
                    licenseKey = license.licenseKey;
                }
            }

            // Generate XOR key and encrypt
            String xorKey = generateXORKey(licenseKey);
            String encryptedPayload = xorEncrypt(jsonString, xorKey);
            Log.i(TAG, "[HTTP] Encrypted payload size: " + encryptedPayload.length() + " bytes");

            // Wrap encrypted data in JSON
            JSONObject wrapper = new JSONObject();
            wrapper.put("encrypted", encryptedPayload);
            String wrappedPayload = wrapper.toString();

            Log.i(TAG, "[HTTP] Writing encrypted payload...");
            OutputStream os = conn.getOutputStream();
            os.write(wrappedPayload.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            Log.i(TAG, "[HTTP] Encrypted payload sent successfully");

            Log.i(TAG, "[HTTP] Waiting for response...");
            int responseCode = conn.getResponseCode();
            Log.i(TAG, "[HTTP] Response code: " + responseCode);

            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseStr = response.toString();
            Log.i(TAG, "[HTTP] Encrypted response size: " + responseStr.length() + " bytes");

            // ==================== XOR DECRYPTION ====================
            // Decrypt response with XOR
            try {
                JSONObject responseWrapper = new JSONObject(responseStr);
                if (responseWrapper.has("encrypted")) {
                    String encryptedResponse = responseWrapper.getString("encrypted");

                    // Get license_key for decryption
                    String licenseKey = payload.optString("license_key", null);
                    if (licenseKey == null) {
                        LicenseData license = readLicenseFromFile();
                        if (license != null) {
                            licenseKey = license.licenseKey;
                        }
                    }

                    String xorKey = generateXORKey(licenseKey);
                    String decryptedResponse = xorDecrypt(encryptedResponse, xorKey);
                    Log.i(TAG, "[HTTP] Decrypted response size: " + decryptedResponse.length() + " bytes");

                    return decryptedResponse;
                } else {
                    // Fallback: response is not encrypted (shouldn't happen)
                    Log.w(TAG, "[HTTP] Response is not encrypted!");
                    return responseStr;
                }
            } catch (Exception e) {
                Log.e(TAG, "[HTTP] Failed to decrypt response: " + e.getMessage());
                // Return original response as fallback
                return responseStr;
            }

        } finally {
            conn.disconnect();
        }
    }

    // ==================== XOR ENCRYPTION ====================
    // Used for request/response encryption with server
    // Key is combination of last 8 digits + first 8 digits of device_id

    /**
     * Generate XOR encryption key from device_id and license_key
     * Key = last 8 chars of device_id + first 8 chars of license_key
     */
    private String generateXORKey(String licenseKey) {
        String deviceId = getDeviceId();

        // Take last 8 characters of device_id
        String lastPart = deviceId.length() >= 8
            ? deviceId.substring(deviceId.length() - 8)
            : deviceId;

        // Take first 8 characters of license_key (or device_id if no license_key)
        String firstPart;
        if (licenseKey != null && licenseKey.length() >= 8) {
            firstPart = licenseKey.substring(0, 8);
        } else if (deviceId.length() >= 8) {
            firstPart = deviceId.substring(0, 8);
        } else {
            firstPart = deviceId;
        }

        String key = lastPart + firstPart;
        Log.d(TAG, "[XOR] Generated XOR key length: " + key.length());
        return key;
    }

    /**
     * XOR encrypt/decrypt data (XOR is symmetric - same for both)
     * Returns Base64 encoded result
     */
    private String xorEncrypt(String data, String key) {
        if (data == null || data.isEmpty() || key == null || key.isEmpty()) {
            Log.e(TAG, "[XOR] Invalid data or key");
            return data;
        }

        try {
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[dataBytes.length];

            for (int i = 0; i < dataBytes.length; i++) {
                result[i] = (byte) (dataBytes[i] ^ keyBytes[i % keyBytes.length]);
            }

            String encoded = Base64.encodeToString(result, Base64.NO_WRAP);
            Log.d(TAG, "[XOR] Encrypted data length: " + data.length() + " -> " + encoded.length());
            return encoded;
        } catch (Exception e) {
            Log.e(TAG, "[XOR] Encryption failed: " + e.getMessage());
            return data;
        }
    }

    /**
     * XOR decrypt data (same as encrypt for XOR)
     * Input is Base64 encoded
     */
    private String xorDecrypt(String encodedData, String key) {
        if (encodedData == null || encodedData.isEmpty() || key == null || key.isEmpty()) {
            Log.e(TAG, "[XOR] Invalid encoded data or key");
            return encodedData;
        }

        try {
            byte[] dataBytes = Base64.decode(encodedData, Base64.NO_WRAP);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[dataBytes.length];

            for (int i = 0; i < dataBytes.length; i++) {
                result[i] = (byte) (dataBytes[i] ^ keyBytes[i % keyBytes.length]);
            }

            String decrypted = new String(result, StandardCharsets.UTF_8);
            Log.d(TAG, "[XOR] Decrypted data length: " + encodedData.length() + " -> " + decrypted.length());
            return decrypted;
        } catch (Exception e) {
            Log.e(TAG, "[XOR] Decryption failed: " + e.getMessage());
            return encodedData;
        }
    }

    // ==================== AES ENCRYPTION ====================

    /**
     * Encrypt data with AES-256-GCM (device-specific key)
     */
    private String encryptAES(String plaintext) throws Exception {
        String deviceKey = getDeviceEncryptionKey();
        SecretKeySpec keySpec = new SecretKeySpec(
            deviceKey.getBytes(StandardCharsets.UTF_8),
            "AES"
        );

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine IV + Ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /**
     * Decrypt data with AES-256-GCM (device-specific key)
     */
    private String decryptAES(String encrypted) throws Exception {
        byte[] combined = Base64.decode(encrypted, Base64.NO_WRAP);

        String deviceKey = getDeviceEncryptionKey();
        SecretKeySpec keySpec = new SecretKeySpec(
            deviceKey.getBytes(StandardCharsets.UTF_8),
            "AES"
        );

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        byte[] iv = new byte[12];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

        byte[] ciphertext = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * SHA-256 hash
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * License result class
     */
    public static class LicenseResult {
        public final boolean success;
        public final String message;

        private LicenseResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static LicenseResult success(String message) {
            return new LicenseResult(true, message);
        }

        public static LicenseResult failure(String message) {
            return new LicenseResult(false, message);
        }
    }
}
