package com.javainstitute.parkfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class UserLoginFragment extends Fragment {

    private FirebaseFirestore db;
    private EditText emailField, passwordField;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_user_login, container, false);

        emailField = view.findViewById(R.id.user_login_enter_email);
        passwordField = view.findViewById(R.id.user_login_enater_pw);

        Button loginButton = view.findViewById(R.id.user_login_btn);
        Button createAccountButton = view.findViewById(R.id.goto_crate_acc_button);

        // Load animations
        Animation focusIn = AnimationUtils.loadAnimation(getContext(), R.anim.forcus_in_input);
        Animation focusOut = AnimationUtils.loadAnimation(getContext(), R.anim.forcus_out_input);

        EditText[] inputFields = {emailField, passwordField};
        for (EditText editText : inputFields) {
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    editText.startAnimation(focusIn);
                } else {
                    editText.startAnimation(focusOut);
                }
            });
        }

        // Check if user is already logged in
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", getContext().MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            redirectToHome();
        }

        loginButton.setOnClickListener(v -> checkUserCredentials());

        createAccountButton.setOnClickListener(v -> {
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container_get_start_page, new CreateAccFragment());
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            transaction.addToBackStack(null);
            transaction.commit();
        });

        return view;
    }

    private void checkUserCredentials() {
        String emailText = emailField.getText().toString().trim();
        String passwordText = passwordField.getText().toString().trim();

        if (emailText.isEmpty() || passwordText.isEmpty()) {
            Toast.makeText(getContext(), "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("email", emailText)
                .whereEqualTo("password", passwordText)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Save login data in SharedPreferences
                            saveLoginData(emailText);
                            Toast.makeText(getContext(), "Login Successful!", Toast.LENGTH_SHORT).show();
                            redirectToHome();
                            return;
                        }
                    } else {
                        Toast.makeText(getContext(), "Invalid email or password", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveLoginData(String email) {
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", getContext().MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("email", email);
        editor.putBoolean("isLoggedIn", true);
        editor.apply();
    }

    private void redirectToHome() {
        // Redirect to HomeActivity
        Intent intent = new Intent(getActivity(), HomePage.class);
        startActivity(intent);
        requireActivity().finish();
    }
}
