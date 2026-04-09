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
import java.util.Locale;
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

    private Booking selectedBooking;
    private String selectedBookingDocId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booked_parking, container, false);

        recyclerView = view.findViewById(R.id.booked_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        bookingList = new ArrayList<>();
        firestore   = FirebaseFirestore.getInstance();

        adapter = new BookingAdapter(bookingList, new BookingAdapter.OnBookingClickListener() {
            @Override
            public void onConfirmClick(Booking booking) {
                selectedBooking      = booking;
                selectedBookingDocId = booking.getDocId();
                initiatePayment(booking);
            }

            @Override
            public void onCancelClick(Booking booking) {
                Toast.makeText(getContext(),
                        "Cancelled booking at " + booking.getLocation(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        recyclerView.setAdapter(adapter);

        // ── PayHere result launcher ───────────────────────────────────────────
        payHereLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null && data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                        PHResponse<StatusResponse> response =
                                (PHResponse<StatusResponse>) data.getSerializableExtra(
                                        PHConstants.INTENT_EXTRA_RESULT);

                        if (result.getResultCode() == Activity.RESULT_OK) {
                            if (response != null && response.isSuccess()) {
                                Toast.makeText(getContext(), "Payment Successful!",
                                        Toast.LENGTH_SHORT).show();
                                if (selectedBooking != null && selectedBookingDocId != null) {
                                    updateBookingAfterPayment(
                                            selectedBookingDocId,
                                            selectedBooking.getBalance());
                                }
                            } else {
                                Toast.makeText(getContext(), "Payment Failed",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Payment Cancelled",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("email", null);
        if (userEmail != null) loadUserBookings(userEmail);

        return view;
    }

    // ── PayHere ───────────────────────────────────────────────────────────────

    private void initiatePayment(Booking booking) {
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("email", "customer@parkfinder.lk");

        InitRequest req = new InitRequest();
        req.setMerchantId("1221504");
        req.setCurrency("LKR");
        req.setAmount(booking.getBalance());
        req.setOrderId("ORDER_" + System.currentTimeMillis());
        req.setItemsDescription("Parking at " + booking.getLocation());

        req.getCustomer().setFirstName("Customer");
        req.getCustomer().setLastName("User");
        req.getCustomer().setEmail(userEmail);
        req.getCustomer().setPhone("+94770000000");
        req.getCustomer().getAddress().setAddress("Colombo");
        req.getCustomer().getAddress().setCity("Colombo");
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        req.getItems().add(new Item(null, "Parking Slot", 1, booking.getBalance()));

        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);

        Intent intent = new Intent(getContext(), PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        payHereLauncher.launch(intent);
    }

    // ── Firestore update + trigger QR receipt ─────────────────────────────────

    private void updateBookingAfterPayment(String docId, double paidAmount) {
        CollectionReference bookingsRef = firestore.collection("bookings");

        bookingsRef.document(docId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(getContext(), "Booking not found.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Double currentPayment = documentSnapshot.getDouble("payment_amount");
                    if (currentPayment == null) currentPayment = 0.0;

                    // Total = first half already paid + this second half
                    double totalAmount = currentPayment + paidAmount;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("payment_status",  "#2");
                    updates.put("payment_amount",   totalAmount);

                    bookingsRef.document(docId).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(),
                                        "Booking confirmed!", Toast.LENGTH_SHORT).show();

                                // Refresh list
                                SharedPreferences prefs = requireActivity()
                                        .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                                String email = prefs.getString("email", "");
                                if (!email.isEmpty()) loadUserBookings(email);

                                // Show QR receipt — fetch mobile first
                                showQrReceipt(email, totalAmount);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(),
                                        "Failed to update booking.", Toast.LENGTH_SHORT).show();
                                Log.e("FirestoreUpdate", "Error", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error finding booking.",
                            Toast.LENGTH_SHORT).show();
                    Log.e("FirestoreGet", "Error", e);
                });
    }

    // ── Fetch mobile number then show QR receipt dialog ───────────────────────

    private void showQrReceipt(String userEmail, double totalAmount) {
        if (selectedBooking == null) return;

        firestore.collection("register_locations")
                .whereEqualTo("name", selectedBooking.getLocation())
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    String mobile = "N/A";
                    if (!snap.isEmpty()) {
                        String m = snap.getDocuments().get(0).getString("mobile");
                        if (m != null && !m.isEmpty()) mobile = m;
                    }

                    // Show QR receipt dialog
                    QrReceiptDialog dialog = QrReceiptDialog.newInstance(
                            userEmail,
                            mobile,
                            selectedBooking.getLocation(),
                            selectedBooking.getDate(),
                            selectedBooking.getTime(),
                            totalAmount
                    );
                    dialog.show(getChildFragmentManager(), "qr_receipt");
                })
                .addOnFailureListener(e -> {
                    // Show dialog even if mobile fetch fails
                    QrReceiptDialog dialog = QrReceiptDialog.newInstance(
                            userEmail, "N/A",
                            selectedBooking.getLocation(),
                            selectedBooking.getDate(),
                            selectedBooking.getTime(),
                            totalAmount
                    );
                    dialog.show(getChildFragmentManager(), "qr_receipt");
                });
    }

    // ── Load bookings ─────────────────────────────────────────────────────────

    private void loadUserBookings(String email) {
        firestore.collection("bookings")
                .whereEqualTo("user_email", email)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    bookingList.clear();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    Date today = getOnlyDate(new Date());

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String paymentStatus = doc.getString("payment_status");
                        if ("#2".equals(paymentStatus)) continue; // already fully paid

                        String loc     = doc.getString("location_name");
                        String dateStr = doc.getString("date");
                        String time    = doc.getString("time");
                        Double balance = doc.getDouble("payment_amount");

                        try {
                            Date date = sdf.parse(dateStr);
                            if (date != null && !getOnlyDate(date).before(today)) {
                                Booking booking = new Booking(
                                        loc, dateStr, time, balance != null ? balance : 0);
                                booking.setDocId(doc.getId());
                                bookingList.add(booking);
                            }
                        } catch (ParseException e) {
                            Log.e("BookingParse", "Date parse error", e);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Log.e("FirestoreError", "Error loading bookings", e));
    }

    private Date getOnlyDate(Date fullDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fullDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE,      0);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}