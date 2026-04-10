package com.javainstitute.parkfinder;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class UserProfileFragment extends Fragment {

    private EditText firstName, lastName, emailFeald, mobile, password;
    private Button updateButton;
    private FirebaseFirestore db;
    private ShapeableImageView profileImageView;
    private static final int PICK_IMAGE_REQUEST = 1;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private String userMobile;
    private static final String PROFILE_IMAGE_FILENAME = "profile_image.jpg";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference("profile_images");

        firstName = view.findViewById(R.id.user_Profile_f_name);
        lastName = view.findViewById(R.id.user_profile_acc_l_name);
        emailFeald = view.findViewById(R.id.user_profile_acc_email);
        mobile = view.findViewById(R.id.user_profile_acc_mobile);
        password = view.findViewById(R.id.user_profile_acc_pw);
        updateButton = view.findViewById(R.id.user_profile_update_button);
        profileImageView = view.findViewById(R.id.UserProfileImageView);

        profileImageView.setOnClickListener(v -> openImageGallery());

        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", getContext().MODE_PRIVATE);
        String userEmail = sharedPreferences.getString("email", null);

        Bitmap localProfileImage = loadProfileImageFromLocal();
        if (localProfileImage != null) {
            profileImageView.setImageBitmap(localProfileImage);
        }

        if (userEmail != null) {
            loadUserData(userEmail);
        } else {
            Toast.makeText(getContext(), "User not logged in!", Toast.LENGTH_SHORT).show();
        }

        updateButton.setOnClickListener(v -> updateUserProfile(userEmail));
    }

    private void openImageGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap selectedImage = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                profileImageView.setImageBitmap(selectedImage);

                // Save the image to local storage
                saveProfileImageToLocal(selectedImage);

                // Save the image to Firebase Storage
                uploadImageToFirebase(imageUri);

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadUserData(String email) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            firstName.setText(document.getString("f_name"));
                            lastName.setText(document.getString("l_name"));
                            emailFeald.setText(document.getString("email"));
                            mobile.setText(document.getString("mobile"));
                            password.setText(document.getString("password"));
                            userMobile = document.getString("mobile"); // Save mobile for later use
                            loadProfileImage(document.getString("profile_picture"));
                        }
                    } else {
                        Toast.makeText(getContext(), "User data not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error fetching data!", Toast.LENGTH_SHORT).show());
    }

    private void updateUserProfile(String email) {
        if (email == null) {
            Toast.makeText(getContext(), "Error: User email not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        String updatedFirstName = firstName.getText().toString().trim();
        String updatedLastName = lastName.getText().toString().trim();
        String updatedPassword = password.getText().toString().trim();

        if (updatedFirstName.isEmpty() || updatedLastName.isEmpty() || updatedPassword.isEmpty()) {
            Toast.makeText(getContext(), "All fields must be filled", Toast.LENGTH_SHORT).show();
            return;
        }
        if (updatedPassword.length() < 6) {
            Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentReference userRef = db.collection("users")
                                .document(task.getResult().getDocuments().get(0).getId());

                        Map<String, Object> updatedData = new HashMap<>();
                        updatedData.put("f_name", updatedFirstName);
                        updatedData.put("l_name", updatedLastName);
                        updatedData.put("password", updatedPassword);

                        userRef.update(updatedData)
                                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Profile Updated Successfully!", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Update Failed! " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(getContext(), "User not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error fetching user for update", Toast.LENGTH_SHORT).show());
    }

    private void uploadImageToFirebase(Uri imageUri) {
        if (imageUri != null && userMobile != null) {
            StorageReference fileReference = storageReference.child(userMobile + ".jpg");
            fileReference.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveImageUrlToFirestore(uri.toString());
                    }))
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(getContext(), "No image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageUrlToFirestore(String imageUrl) {
        db.collection("users").whereEqualTo("mobile", userMobile).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentReference userDoc = task.getResult().getDocuments().get(0).getReference();
                        userDoc.update("profile_picture", imageUrl)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                                    loadProfileImage(imageUrl);
                                })
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update Firestore", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // Save bitmap to local storage
    private void saveProfileImageToLocal(Bitmap bitmap) {
        try {
            File file = new File(requireContext().getFilesDir(), PROFILE_IMAGE_FILENAME);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap loadProfileImageFromLocal() {

        try {
            File file = new File(requireContext().getFilesDir(), PROFILE_IMAGE_FILENAME);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(fis);
                fis.close();
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadProfileImage(String imageUrl) {

        Bitmap localProfileImage = loadProfileImageFromLocal();
        if (localProfileImage != null) {
            profileImageView.setImageBitmap(localProfileImage);
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            Picasso.get().load(imageUrl).into(profileImageView);
        }
    }
}
