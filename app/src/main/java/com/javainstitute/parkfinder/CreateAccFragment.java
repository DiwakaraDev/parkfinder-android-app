package com.javainstitute.parkfinder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateAccFragment extends Fragment {

    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_create_acc, container, false);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Get input fields
        EditText firstName = view.findViewById(R.id.create_acc_f_name);
        EditText lastName = view.findViewById(R.id.user_profile_acc_l_name);
        EditText email = view.findViewById(R.id.user_profile_acc_email);
        EditText mobile = view.findViewById(R.id.user_profile_acc_mobile);
        EditText password = view.findViewById(R.id.user_profile_acc_pw);

        // Get signup btn
        Button signUpButton = view.findViewById(R.id.user_profile_update_button);

        signUpButton.setOnClickListener(v -> {
            String fName = firstName.getText().toString().trim();
            String lName = lastName.getText().toString().trim();
            String emailText = email.getText().toString().trim();
            String mobileText = mobile.getText().toString().trim();
            String passwordText = password.getText().toString().trim();

            // Validate fields
            if (fName.isEmpty() || lName.isEmpty() || emailText.isEmpty() || mobileText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!mobileText.matches("^\\d{10}$")) {
                Toast.makeText(getContext(), "Enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (passwordText.length() < 6) {
                Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            // Check if the email or mobile number already exists
            checkIfEmailOrMobileExists(emailText, mobileText, fName, lName, passwordText);
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button goto_login_btn = view.findViewById(R.id.go_to_login_button);
        goto_login_btn.setOnClickListener(v -> {
            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container_get_start_page, new UserLoginFragment()); // Use the single container ID
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            transaction.addToBackStack(null);
            transaction.commit();
        });
    }

    private void checkIfEmailOrMobileExists(String email, String mobile, String fName, String lName, String PW) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {

                        Toast.makeText(getContext(), "This email is already registered", Toast.LENGTH_SHORT).show();
                    } else {

                        db.collection("users")
                                .whereEqualTo("mobile", mobile)
                                .get()
                                .addOnCompleteListener(mobileTask -> {
                                    if (mobileTask.isSuccessful() && !mobileTask.getResult().isEmpty()) {

                                        Toast.makeText(getContext(), "This mobile number is already registered", Toast.LENGTH_SHORT).show();
                                    } else {

                                        String userId = UUID.randomUUID().toString();
                                        saveUserData(userId, fName, lName, email, mobile, PW);
                                    }
                                });
                    }
                });
    }

    private void saveUserData(String userId, String fName, String lName, String email, String mobile, String PW) {

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("f_name", fName);
        userMap.put("l_name", lName);
        userMap.put("mobile", mobile);
        userMap.put("password", PW);

        // Save to Firestore
        db.collection("users").document(userId)
                .set(userMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Account Created Successfully!", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                    // Redirect to Login
                    redirectToLoginFragment();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void clearInputFields() {

        EditText firstName = getView().findViewById(R.id.create_acc_f_name);
        EditText lastName = getView().findViewById(R.id.user_profile_acc_l_name);
        EditText email = getView().findViewById(R.id.user_profile_acc_email);
        EditText mobile = getView().findViewById(R.id.user_profile_acc_mobile);
        EditText password = getView().findViewById(R.id.user_profile_acc_pw);

        firstName.setText("");
        lastName.setText("");
        email.setText("");
        mobile.setText("");
        password.setText("");
    }

    private void redirectToLoginFragment() {

        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container_get_start_page, new UserLoginFragment());
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        transaction.addToBackStack(null);
        transaction.commit();
    }
}
