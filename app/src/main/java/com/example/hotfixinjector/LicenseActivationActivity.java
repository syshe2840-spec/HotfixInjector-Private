package com.example.hotfixinjector;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * License Activation Activity
 * Allows user to enter and activate license key
 */
public class LicenseActivationActivity extends Activity {

    private LicenseClient licenseClient;
    private EditText licenseKeyInput;
    private TextView statusText;
    private TextView deviceIdText;
    private TextView activateButton;
    private TextView clearButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        licenseClient = new LicenseClient(this);
        createUI();
    }

    private void createUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(30, 50, 30, 50);

        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{
                Color.parseColor("#0a0614"),
                Color.parseColor("#120820"),
                Color.parseColor("#1a0c28")
            }
        );
        root.setBackground(bg);

        // Title
        TextView title = new TextView(this);
        title.setText("🔐 License Activation");
        title.setTextSize(32);
        title.setTextColor(Color.parseColor("#ff6600"));
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(30, 0, 0, Color.parseColor("#ff3300"));

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 40);
        title.setLayoutParams(titleParams);

        // Status Card
        LinearLayout statusCard = createStatusCard();

        // Device ID Card
        LinearLayout deviceIdCard = createDeviceIdCard();

        // License Input Card
        LinearLayout inputCard = createInputCard();

        root.addView(title);
        root.addView(statusCard);
        root.addView(deviceIdCard);
        root.addView(inputCard);

        scrollView.addView(root);
        setContentView(scrollView);

        // Check if already activated
        updateStatus();
    }

    private LinearLayout createStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 20);
        card.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20);
        bg.setColor(Color.parseColor("#1a0f28"));
        bg.setStroke(3, Color.parseColor("#888888"));
        card.setBackground(bg);

        TextView label = new TextView(this);
        label.setText("LICENSE STATUS");
        label.setTextSize(14);
        label.setTextColor(Color.parseColor("#aaaaaa"));
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.1f);

        statusText = new TextView(this);
        statusText.setText("Checking...");
        statusText.setTextSize(18);
        statusText.setTextColor(Color.WHITE);
        statusText.setTypeface(null, Typeface.BOLD);
        statusText.setPadding(0, 10, 0, 0);

        card.addView(label);
        card.addView(statusText);

        return card;
    }

    private LinearLayout createDeviceIdCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 20);
        card.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20);
        bg.setColor(Color.parseColor("#1a0f28"));
        bg.setStroke(3, Color.parseColor("#0088ff"));
        card.setBackground(bg);

        TextView label = new TextView(this);
        label.setText("YOUR DEVICE ID");
        label.setTextSize(14);
        label.setTextColor(Color.parseColor("#0088ff"));
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.1f);

        deviceIdText = new TextView(this);
        deviceIdText.setText(licenseClient.getCopyableDeviceId());
        deviceIdText.setTextSize(11);
        deviceIdText.setTextColor(Color.parseColor("#cccccc"));
        deviceIdText.setPadding(0, 10, 0, 0);
        deviceIdText.setTypeface(Typeface.MONOSPACE);
        deviceIdText.setTextIsSelectable(true); // Make it copyable!

        TextView hint = new TextView(this);
        hint.setText("(Tap to select and copy • Hardware-based ID)");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setPadding(0, 5, 0, 0);

        card.addView(label);
        card.addView(deviceIdText);
        card.addView(hint);

        return card;
    }

    private LinearLayout createInputCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        card.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20);
        bg.setColor(Color.parseColor("#1a0f28"));
        bg.setStroke(3, Color.parseColor("#ff6600"));
        card.setBackground(bg);

        TextView label = new TextView(this);
        label.setText("ENTER LICENSE KEY");
        label.setTextSize(14);
        label.setTextColor(Color.parseColor("#ff6600"));
        label.setTypeface(null, Typeface.BOLD);
        label.setLetterSpacing(0.1f);

        // License Input
        licenseKeyInput = new EditText(this);
        licenseKeyInput.setHint("XXXXX-XXXXX-XXXXX-XXXXX");
        licenseKeyInput.setHintTextColor(Color.parseColor("#555555"));
        licenseKeyInput.setTextColor(Color.WHITE);
        licenseKeyInput.setTextSize(16);
        licenseKeyInput.setTypeface(Typeface.MONOSPACE);
        licenseKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        licenseKeyInput.setPadding(20, 20, 20, 20);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 15, 0, 15);
        licenseKeyInput.setLayoutParams(inputParams);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(12);
        inputBg.setColor(Color.parseColor("#0f0820"));
        inputBg.setStroke(2, Color.parseColor("#ff6600"));
        licenseKeyInput.setBackground(inputBg);

        // Progress Bar
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.gravity = Gravity.CENTER;
        progressParams.setMargins(0, 10, 0, 10);
        progressBar.setLayoutParams(progressParams);

        // Activate Button
        activateButton = createButton("ACTIVATE LICENSE", "#ff4400", "#ff6600");
        activateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activateLicense();
            }
        });

        // Clear Button
        clearButton = createButton("CLEAR LICENSE", "#aa0000", "#cc0000");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLicense();
            }
        });

        card.addView(label);
        card.addView(licenseKeyInput);
        card.addView(progressBar);
        card.addView(activateButton);
        card.addView(clearButton);

        return card;
    }

    private TextView createButton(String text, String color1, String color2) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(16);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(30, 20, 30, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 10, 0, 0);
        btn.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(15);
        bg.setColors(new int[]{
            Color.parseColor(color1),
            Color.parseColor(color2)
        });
        bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        bg.setStroke(2, Color.parseColor(color2));
        btn.setBackground(bg);

        return btn;
    }

    private void activateLicense() {
        final String licenseKey = licenseKeyInput.getText().toString().trim().toUpperCase();

        if (licenseKey.isEmpty()) {
            showStatus("❌ Please enter a license key", "#ff0000");
            return;
        }

        // Disable UI
        licenseKeyInput.setEnabled(false);
        activateButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        showStatus("⏳ Activating...", "#ffaa00");

        // Activate in background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                final LicenseClient.LicenseResult result = licenseClient.activate(licenseKey);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        licenseKeyInput.setEnabled(true);
                        activateButton.setEnabled(true);

                        if (result.success) {
                            showStatus("✅ License Activated Successfully!", "#00ff00");

                            // 🚀 Start Background License Service
                            Intent serviceIntent = new Intent(LicenseActivationActivity.this, BackgroundLicenseService.class);
                            startService(serviceIntent);

                            // Wait 1 second then go to MainActivity
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish(); // Close activation screen
                                    // MainActivity will be shown automatically
                                }
                            }, 1000);
                        } else {
                            showStatus("❌ Activation Failed: " + result.message, "#ff0000");
                        }
                    }
                });
            }
        }).start();
    }

    private void clearLicense() {
        licenseClient.clearLicense();
        showStatus("🗑️ License Cleared", "#ffaa00");
        updateStatus();
    }

    private void updateStatus() {
        if (licenseClient.isLicenseActive()) {
            long expiresAt = getSharedPreferences("license_prefs", MODE_PRIVATE)
                .getLong("expires_at", 0);

            if (expiresAt > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String expiryDate = sdf.format(new Date(expiresAt));
                statusText.setText("✅ ACTIVE\n\nExpires: " + expiryDate);
            } else {
                statusText.setText("✅ ACTIVE (No Expiration)");
            }

            statusText.setTextColor(Color.parseColor("#00ff00"));
        } else {
            statusText.setText("❌ NOT ACTIVATED");
            statusText.setTextColor(Color.parseColor("#ff3300"));
        }
    }

    private void showStatus(final String message, final String color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(message);
                statusText.setTextColor(Color.parseColor(color));
            }
        });
    }
}
