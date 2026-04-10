package com.javainstitute.parkfinder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SplashScreen extends AppCompatActivity {

    private boolean timerDone = false;
    private boolean permsDone = false;
    private boolean isRetryRequest = false;

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResults
        );

        startSplashTimer();
        checkAndRequestPermissions();
    }

    // Timer

    private void startSplashTimer() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            timerDone = true;
            tryNavigate();
        }, 3000);
    }

    // Permissions

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        return permissions.toArray(new String[0]);
    }

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(permission);
            }
        }

        if (needed.isEmpty()) {
            permsDone = true;
            tryNavigate();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void handlePermissionResults(Map<String, Boolean> results) {
        List<String> denied = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : results.entrySet()) {
            if (!entry.getValue()) {
                denied.add(entry.getKey());
            }
        }

        if (denied.isEmpty()) {

            permsDone = true;
            tryNavigate();

        } else if (!isRetryRequest) {

            isRetryRequest = true;
            showRationaleDialog(denied.toArray(new String[0]));

        } else {

            boolean hasPermanentDenial = false;
            for (String permission : denied) {
                if (!shouldShowRequestPermissionRationale(permission)) {
                    hasPermanentDenial = true;
                    break;
                }
            }

            if (hasPermanentDenial) {
                showPermanentDenialDialog();
            } else {

                permsDone = true;
                tryNavigate();
            }
        }
    }

    private void showRationaleDialog(String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("ParkFinder needs Location, Camera, and Storage access to find nearby " +
                        "parking spots and manage your profile photo.")
                .setPositiveButton("Grant", (dialog, which) ->
                        permissionLauncher.launch(permissions))
                .setNegativeButton("Skip", (dialog, which) -> {
                    permsDone = true;
                    tryNavigate();
                })
                .setCancelable(false)
                .show();
    }

    private void showPermanentDenialDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permissions Blocked")
                .setMessage("Some permissions were permanently denied. Enable them from App " +
                        "Settings for full functionality.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)
                    );
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    // Continue — user can return from Settings with permissions granted
                    permsDone = true;
                    tryNavigate();
                })
                .setNegativeButton("Continue Anyway", (dialog, which) -> {
                    permsDone = true;
                    tryNavigate();
                })
                .setCancelable(false)
                .show();
    }

    // Navigation Gate

    private void tryNavigate() {
        if (!timerDone || !permsDone) return;

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        Intent intent = isLoggedIn
                ? new Intent(this, HomePage.class)
                : new Intent(this, StartPage.class);

        startActivity(intent);
        finish();
    }
}