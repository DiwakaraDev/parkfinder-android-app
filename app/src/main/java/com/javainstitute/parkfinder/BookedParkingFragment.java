package com.javainstitute.parkfinder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;
import lk.payhere.androidsdk.model.StatusResponse;

public class BookedParkingFragment extends Fragment {

    private RecyclerView recyclerView;
    private BookingAdapter adapter;
    private ArrayList<Booking> bookingList;
    private FirebaseFirestore firestore;
    private ActivityResultLauncher<Intent> payHereLauncher;

    // We need to keep track of the booking currently in payment
    private Booking selectedBooking;
    private String selectedBookingDocId; // Firestore document ID

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booked_parking, container, false);

        recyclerView = view.findViewById(R.id.booked_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        bookingList = new ArrayList<>();

        adapter = new BookingAdapter(bookingList, new BookingAdapter.OnBookingClickListener() {
            @Override
            public void onConfirmClick(Booking booking) {
                selectedBooking = booking;
                selectedBookingDocId = booking.getDocId();
                initiatePayment(booking);
            }

            @Override
            public void onCancelClick(Booking booking) {
                Toast.makeText(getContext(), "Cancelled booking at " + booking.getLocation(), Toast.LENGTH_SHORT).show();
                // Optional: remove from Firestore if needed
            }
        });

        recyclerView.setAdapter(adapter);
        firestore = FirebaseFirestore.getInstance();

        payHereLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null && data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                        PHResponse<StatusResponse> response = (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            if (response != null && response.isSuccess()) {
                                Toast.makeText(getContext(), "Payment Successful!", Toast.LENGTH_SHORT).show();
                                if (selectedBooking != null && selectedBookingDocId != null) {
                                    updateBookingAfterPayment(selectedBookingDocId, selectedBooking.getBalance());
                                }
                            } else {
                                Toast.makeText(getContext(), "Payment Failed", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Payment Cancelled", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userEmail = sharedPreferences.getString("email", null);

        if (userEmail != null) {
            loadUserBookings(userEmail);
        }

        return view;
    }

    private void initiatePayment(Booking booking) {
        InitRequest req = new InitRequest();
        req.setMerchantId("1221504"); // Replace with actual merchant ID
        req.setCurrency("LKR");
        // Payment amount must be exactly payment_amount (balance)
        req.setAmount(booking.getBalance());
        req.setOrderId("ORDER_" + System.currentTimeMillis());
        req.setItemsDescription("Parking at " + booking.getLocation());

        req.getCustomer().setFirstName("John");
        req.getCustomer().setLastName("Doe");
        req.getCustomer().setEmail("john@example.com");
        req.getCustomer().setPhone("+94770000000");
        req.getCustomer().getAddress().setAddress("123 Main Street");
        req.getCustomer().getAddress().setCity("Colombo");
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        req.getItems().add(new Item(null, "Parking Slot", 1, booking.getBalance()));

        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL); // use LIVE_URL for production

        Intent intent = new Intent(getContext(), PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        payHereLauncher.launch(intent);
    }

    private void updateBookingAfterPayment(String docId, double paidAmount) {
        CollectionReference bookingsRef = firestore.collection("bookings");
        bookingsRef.document(docId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Double currentPaymentAmount = documentSnapshot.getDouble("payment_amount");
                if (currentPaymentAmount == null) currentPaymentAmount = 0.0;

                // Add paid amount to existing payment_amount
                double updatedPaymentAmount = currentPaymentAmount + paidAmount;

                // Prepare update map
                Map<String, Object> updates = new HashMap<>();
                updates.put("payment_status", "#2"); // Payment complete
                updates.put("payment_amount", updatedPaymentAmount);
                updates.put("seat_index", null); // Remove seat_index by setting null (Firestore will delete field)

                // Firestore does not delete fields with null automatically. Use FieldValue.delete()
                // So update using update() with FieldValue.delete()

                bookingsRef.document(docId)
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Booking updated after payment.", Toast.LENGTH_SHORT).show();
                            // Refresh list to reflect changes
                            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                            String userEmail = sharedPreferences.getString("email", null);
                            if (userEmail != null) {
                                loadUserBookings(userEmail);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(), "Failed to update booking.", Toast.LENGTH_SHORT).show();
                            Log.e("FirestoreUpdate", "Error updating booking", e);
                        });
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Booking not found for update.", Toast.LENGTH_SHORT).show();
            Log.e("FirestoreGet", "Error getting booking doc", e);
        });
    }

    private void loadUserBookings(String email) {
        firestore.collection("bookings")
                .whereEqualTo("user_email", email)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    bookingList.clear();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    Date today = getOnlyDate(new Date());

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String paymentStatus = doc.getString("payment_status");
                        // Skip bookings with payment_status "#2"
                        if ("#2".equals(paymentStatus)) {
                            continue;
                        }

                        String loc = doc.getString("location_name");
                        String dateStr = doc.getString("date");
                        String time = doc.getString("time");
                        Double balance = doc.getDouble("payment_amount");

                        try {
                            Date date = sdf.parse(dateStr);
                            if (date != null && !getOnlyDate(date).before(today)) {
                                Booking booking = new Booking(loc, dateStr, time, balance != null ? balance : 0);
                                booking.setDocId(doc.getId()); // Store Firestore doc ID for later update
                                bookingList.add(booking);
                            }
                        } catch (ParseException e) {
                            Log.e("BookingParse", "Error parsing date", e);
                        }
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e("FirestoreError", "Error loading bookings", e));
    }

    private Date getOnlyDate(Date fullDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fullDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
