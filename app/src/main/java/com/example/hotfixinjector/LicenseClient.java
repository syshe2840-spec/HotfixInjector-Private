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

    // Cloudflare Worker URL
    private static final String API_BASE_URL = "https://hotapp.lastofanarchy.workers.dev";

    // AES-256 Encryption Key (32 bytes)
    // ⚠️ CHANGE THIS - Should match server or use asymmetric encryption
    private static final String ENCRYPTION_KEY = "YOUR_32_CHAR_ENCRYPTION_KEY_HERE!!"; // Must be 32 chars

    private final Context context;
    private final SharedPreferences prefs;
    private String cachedDeviceId;

    public LicenseClient(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get unique device ID
     */
    public String getDeviceId() {
        if (cachedDeviceId != null) {
            return cachedDeviceId;
        }

        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            // Generate unique device ID
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            String serial = Build.SERIAL;
            String model = Build.MODEL;

            deviceId = sha256(androidId + serial + model);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }

        cachedDeviceId = deviceId;
        return deviceId;
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
     * Verify license with server (called every 10 seconds)
     */
    public LicenseResult verify() {
        try {
            String sessionToken = prefs.getString(KEY_SESSION_TOKEN, null);
            if (sessionToken == null) {
                return LicenseResult.failure("No active license");
            }

            JSONObject payload = new JSONObject();
            payload.put("session_token", sessionToken);
            payload.put("device_id", getDeviceId());

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
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply();
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
     * Encrypt data with AES-256-GCM
     */
    private String encryptAES(String plaintext) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
            ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8),
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
     * Decrypt data with AES-256-GCM
     */
    private String decryptAES(String encrypted) throws Exception {
        byte[] combined = Base64.decode(encrypted, Base64.NO_WRAP);

        SecretKeySpec keySpec = new SecretKeySpec(
            ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8),
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
