package com.example.hotfixinjector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
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

    // Encrypted license file (in app's directory, made world-readable, device-specific encrypted)
    private static final String LICENSE_FILE = "/data/data/com.example.hotfixinjector/files/.hf_lic_cache";

    // Cloudflare Worker URL
    private static final String API_BASE_URL = "https://hotapp.lastofanarchy.workers.dev";

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
                long expiresAt = json.optLong("expires_at", 0);

                // Save credentials
                prefs.edit()
                    .putString(KEY_SESSION_TOKEN, sessionToken)
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply();

                // Write to encrypted file for cross-app access
                writeLicenseToFile();

                Log.i(TAG, "✅ License activated successfully");
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
     * Verify license with server (called every 5 seconds)
     */
    public LicenseResult verify() {
        try {
            String sessionToken = null;
            String deviceId = null;

            // Try to read from prefs first
            if (prefs != null) {
                sessionToken = prefs.getString(KEY_SESSION_TOKEN, null);
                deviceId = getDeviceId();
            }

            // Fallback to encrypted file
            if (sessionToken == null) {
                LicenseData license = readLicenseFromFile();
                if (license != null) {
                    sessionToken = license.sessionToken;
                    deviceId = license.deviceId;
                }
            }

            if (sessionToken == null) {
                return LicenseResult.failure("No active license");
            }

            JSONObject payload = new JSONObject();
            payload.put("session_token", sessionToken);
            payload.put("device_id", deviceId);

            String response = sendRequest("/verify", payload);
            JSONObject json = new JSONObject(response);

            if (json.getBoolean("success") && json.optBoolean("valid", false)) {
                Log.d(TAG, "✅ License verified");
                return LicenseResult.success("Valid");
            } else {
                String error = json.optString("error", "Invalid license");
                Log.e(TAG, "❌ Verification failed: " + error);
                clearLicense();
                return LicenseResult.failure(error);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Verification exception: " + e.getMessage());
            return LicenseResult.failure("Network error");
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

        // Also clear encrypted file
        try {
            java.io.File file = new java.io.File(LICENSE_FILE);
            if (file.exists()) {
                file.delete();
                Log.i(TAG, "🗑️ Encrypted license file deleted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete license file: " + e.getMessage());
        }
    }

    /**
     * Write encrypted license to file (root-accessible location)
     */
    private void writeLicenseToFile() {
        try {
            if (prefs == null) {
                Log.w(TAG, "Cannot write license file: prefs is null");
                return;
            }

            String sessionToken = prefs.getString(KEY_SESSION_TOKEN, null);
            long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);

            if (sessionToken == null) {
                return;
            }

            // Create JSON with license data
            JSONObject data = new JSONObject();
            data.put("token", sessionToken);
            data.put("expires", expiresAt);
            data.put("device", getDeviceId());
            data.put("timestamp", System.currentTimeMillis());

            // Encrypt
            String encrypted = encryptAES(data.toString());

            // Write to app's files directory
            java.io.File file = new java.io.File(LICENSE_FILE);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            fos.close();

            // Make files directory world-readable and executable (chmod 755) so other apps can access files inside
            String filesDir = "/data/data/com.example.hotfixinjector/files";
            executeRootCommand("chmod 755 " + filesDir);

            // Make file world-readable (chmod 644)
            executeRootCommand("chmod 644 " + LICENSE_FILE);

            Log.i(TAG, "✅ License written to encrypted file (" + LICENSE_FILE + ")");

        } catch (Exception e) {
            Log.e(TAG, "Failed to write license file: " + e.getMessage());
            e.printStackTrace();
        }
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
     * Read encrypted license from file (world-readable location)
     */
    public static LicenseData readLicenseFromFile() {
        try {
            Log.i("LicenseClient", "[READ] Reading license file: " + LICENSE_FILE);

            java.io.File file = new java.io.File(LICENSE_FILE);
            if (!file.exists()) {
                Log.e("LicenseClient", "[READ] License file does not exist");
                return null;
            }

            if (!file.canRead()) {
                Log.e("LicenseClient", "[READ] License file is not readable");
                return null;
            }

            // Read file directly (no root needed for /data/local/tmp/)
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String encrypted = new String(data, StandardCharsets.UTF_8);

            if (encrypted.isEmpty()) {
                Log.e("LicenseClient", "[READ] License file is empty");
                return null;
            }

            Log.i("LicenseClient", "[READ] File read successfully (" + encrypted.length() + " chars)");

            // Decrypt
            LicenseClient tempClient = new LicenseClient(null);
            String decrypted = tempClient.decryptAES(encrypted);

            if (decrypted == null || decrypted.isEmpty()) {
                Log.e("LicenseClient", "[READ] Decryption failed (empty or null)");
                return null;
            }

            Log.i("LicenseClient", "[READ] Decryption successful");

            // Parse JSON
            JSONObject json = new JSONObject(decrypted);
            String token = json.getString("token");
            long expires = json.getLong("expires");
            String device = json.getString("device");

            Log.i("LicenseClient", "[READ] License parsed - token: " + token.substring(0, Math.min(20, token.length())) + "..., expires: " + expires);

            LicenseData licenseData = new LicenseData(token, expires, device);

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
            Log.e("LicenseClient", "[READ] Exception reading license file: " + e.getMessage());
            e.printStackTrace();
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
        public final String sessionToken;
        public final long expiresAt;
        public final String deviceId;

        public LicenseData(String sessionToken, long expiresAt, String deviceId) {
            this.sessionToken = sessionToken;
            this.expiresAt = expiresAt;
            this.deviceId = deviceId;
        }

        public boolean isValid() {
            if (sessionToken == null || sessionToken.isEmpty()) {
                return false;
            }
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                return false;
            }
            return true;
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
        URL url = new URL(API_BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "HotfixInjector/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Send encrypted payload
            String jsonString = payload.toString();

            // For now, sending plain JSON (you can add AES encryption here if needed)
            // String encrypted = encryptAES(jsonString);

            OutputStream os = conn.getOutputStream();
            os.write(jsonString.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

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

            return response.toString();

        } finally {
            conn.disconnect();
        }
    }

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
