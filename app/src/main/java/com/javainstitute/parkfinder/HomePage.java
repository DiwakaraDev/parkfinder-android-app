package com.javainstitute.parkfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

public class HomePage extends AppCompatActivity {

    DrawerLayout drawerLayout;
    ImageButton home_button_drawer;
    NavigationView navigationView;

    // ── Shake to exit ─────────────────────────────────────────────────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        drawerLayout       = findViewById(R.id.main_home);
        home_button_drawer = findViewById(R.id.home_button_drawer_toggle);
        navigationView     = findViewById(R.id.home_navigation_view);

        home_button_drawer.setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START));

        // Load the default fragment
        loadFragment(new MainHomeFragment());

        // Load user data into drawer header
        loadUserDataToDrawerHeader();

        // Navigation drawer item selection
        navigationView.setNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new MainHomeFragment();

            } else if (itemId == R.id.nav_book_parking) {
                selectedFragment = new BookedParkingFragment();

            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new UserProfileFragment();

            } else if (itemId == R.id.nav_booking_history) {
                selectedFragment = new BookingHistoryFragment(); // ← new

            }  else if (itemId == R.id.nav_settings) {
                showLogoutConfirmDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // ── Shake detector setup ───────────────────────────────────────────────────────────────────────────────
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        shakeDetector = new ShakeDetector(() ->
                runOnUiThread(this::confirmAndClose));
    }

    // ── Shake detector lifecycle ─────────────

    @Override
    protected void onResume() {
        super.onResume();
        shakeDetector.start(sensorManager);
    }

    @Override
    protected void onPause() {
        shakeDetector.stop(sensorManager);
        super.onPause();
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    private void confirmAndClose() {

        if (isFinishing() || isDestroyed()) return;

        new AlertDialog.Builder(this)
                .setTitle("Close App")
                .setMessage("Shake detected. Do you want to exit ParkFinder?")
                .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
                .setNegativeButton("Stay", null)
                .setCancelable(true)
                .show();
    }

    // ── Fragment loader ───────────────────────────────────────────────────────

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.HomeMainfragmentContainerView, fragment);
        fragmentTransaction.commit();
    }

    // ── Drawer header data ────────────────────────────────────────────────────────────────────────────────────────────────

    private void loadUserDataToDrawerHeader() {
        View headerView = navigationView.getHeaderView(0);

        TextView userNameTextView  = headerView.findViewById(R.id.user_name);
        TextView userEmailTextView = headerView.findViewById(R.id.user_email);
        ImageView profileImageView = headerView.findViewById(R.id.profile_image);

        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String email = prefs.getString("email", null);

        if (email == null) {
            userNameTextView.setText("Guest");
            userEmailTextView.setText("");
            profileImageView.setImageResource(R.drawable.profile_img_upload_sape);
            return;
        }

        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        var document = queryDocumentSnapshots.getDocuments().get(0);

                        String fName         = document.getString("f_name");
                        String lName         = document.getString("l_name");
                        String profilePicUrl = document.getString("profile_picture");

                        userEmailTextView.setText(email);
                        userNameTextView.setText(
                                ((fName != null ? fName : "") + " " +
                                        (lName != null ? lName : "")).trim());

                        int targetUrl = (profilePicUrl != null && !profilePicUrl.isEmpty())
                                ? 0 : R.drawable.profile_img_upload_sape;

                        if (targetUrl == 0) {
                            Picasso.get()
                                    .load(profilePicUrl)
                                    .placeholder(R.drawable.profile_img_upload_sape)
                                    .resize(200, 200)
                                    .centerCrop()
                                    .transform(new CircleTransform())
                                    .into(profileImageView);
                        } else {
                            Picasso.get()
                                    .load(R.drawable.profile_img_upload_sape)
                                    .resize(200, 200)
                                    .centerCrop()
                                    .transform(new CircleTransform())
                                    .into(profileImageView);
                        }

                    } else {
                        userNameTextView.setText("Unknown User");
                        userEmailTextView.setText(email);
                        profileImageView.setImageResource(R.drawable.profile_img_upload_sape);
                    }
                })
                .addOnFailureListener(e -> {
                    userNameTextView.setText("Error");
                    userEmailTextView.setText(email);
                    profileImageView.setImageResource(R.drawable.profile_img_upload_sape);
                });
    }

    // ── Sign out with confirmation dialog ──────────────────────────

    private void showLogoutConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> signOut())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void signOut() {
        //Clear all SharedPreferences
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        //Navigate to StartPage
        Intent intent = new Intent(this, StartPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

//  CircleTransform
class CircleTransform implements Transformation {

    @Override
    public Bitmap transform(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x    = (source.getWidth()  - size) / 2;
        int y    = (source.getHeight() - size) / 2;

        Bitmap squared = Bitmap.createBitmap(source, x, y, size, size);
        if (squared != source) source.recycle();

        Bitmap.Config config = squared.getConfig() != null
                ? squared.getConfig() : Bitmap.Config.ARGB_8888;
        Bitmap output = Bitmap.createBitmap(size, size, config);

        Canvas canvas = new Canvas(output);
        Paint  paint  = new Paint();
        paint.setShader(new BitmapShader(
                squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        paint.setAntiAlias(true);

        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);

        squared.recycle();
        return output;
    }

    @Override
    public String key() { return "circle"; }
}
