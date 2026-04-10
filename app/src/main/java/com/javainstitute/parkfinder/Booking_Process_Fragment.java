package com.javainstitute.parkfinder;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.model.InitRequest;

public class Booking_Process_Fragment extends Fragment {

    private TextView bookingLocationNameInput;
    private EditText bookingVehicleNumberInput;
    private TextView bookingDatePicker;
    private TextView bookingTimePicker;
    private GridLayout seatGrid;
    private Button bookNowBtn;
    private Button callButton, messageButton;

    private String locationName;
    private String locationMobileNumber = "";
    private int fullCount = 0;

    private final int MAX_COLUMNS = 5;
    private final Set<Integer> selectedSeats = new HashSet<>();
    private final Set<Integer> bookedSeats   = new HashSet<>();

    private FirebaseFirestore db;

    private double halfPayment           = 0;
    private String bookingVehicleNumber  = "";
    private String bookingDate           = "";
    private String bookingTime           = "";
    private String bookingUserEmail      = "";
    private final List<Integer> bookingSelectedSeats = new ArrayList<>();
    private Booking currentBookingForPayment;

    // ── Intent-source flags ───────────────────────────────────────────────────

    private boolean callRequested = false;
    private boolean smsRequested  = false;

    // ── PayHere result launcher ────────────────────────────────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> paymentResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            String status = result.getData()
                                    .getStringExtra(PHConstants.INTENT_EXTRA_RESULT);
                            Toast.makeText(getContext(),
                                    "Payment successful: " + status,
                                    Toast.LENGTH_SHORT).show();
                            Log.d("PayHereLog", "Payment successful: " + status);

                            if (currentBookingForPayment != null) {
                                for (Integer seatIndex : bookingSelectedSeats) {
                                    Booking booking = new Booking(
                                            locationName, bookingVehicleNumber,
                                            bookingDate, bookingTime,
                                            bookingUserEmail, seatIndex, "#1", halfPayment);
                                    db.collection("bookings").add(booking)
                                            .addOnSuccessListener(ref ->
                                                    Toast.makeText(getContext(),
                                                            "Booking successful!",
                                                            Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(getContext(),
                                                            "Booking failed: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT).show());
                                }
                            }
                            loadBookedSeatsForDate(bookingDate);
                            selectedSeats.clear();
                            bookingVehicleNumberInput.setText("");
                            bookingTimePicker.setText("Select Time");

                        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            Toast.makeText(getContext(), "Payment was canceled",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Payment failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    // ── Location permission launcher (gates Book Now only) ──────────────────────────────────────────

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            bookSelectedSeats();
                        } else {
                            if (shouldShowRequestPermissionRationale(
                                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                                Toast.makeText(getContext(),
                                        "Location permission is required to complete a booking.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                showSettingsDialog(
                                        "Location permission is permanently denied. " +
                                                "Enable it from App Settings to complete bookings.");
                            }
                        }
                    }
            );

    // ── CALL_PHONE permission launcher ─────────────────────────────────────────────────────────────────────────
    // Permission is requested first.

    private final ActivityResultLauncher<String> callPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!callRequested) return;
                        callRequested = false;

                        if (granted) {
                            openDialer();
                        } else {
                            if (!shouldShowRequestPermissionRationale(
                                    Manifest.permission.CALL_PHONE)) {
                                showSettingsDialog(
                                        "Call permission is permanently denied. " +
                                                "Enable it from App Settings to use this feature.");
                            } else {
                                Toast.makeText(getContext(),
                                        "Call permission is required to use this feature.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );

    // ── SEND_SMS permission launcher ─────────────────────────────────────────────────────────────────────────────────────────────────
    // Permission is requested first.
    private final ActivityResultLauncher<String> smsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!smsRequested) return;
                        smsRequested = false;

                        if (granted) {
                            openSmsApp();
                        } else {
                            if (!shouldShowRequestPermissionRationale(
                                    Manifest.permission.SEND_SMS)) {
                                showSettingsDialog(
                                        "SMS permission is permanently denied. " +
                                                "Enable it from App Settings to use this feature.");
                            } else {
                                Toast.makeText(getContext(),
                                        "SMS permission is required to use this feature.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );

    public Booking_Process_Fragment() {}

    // ── View setup ──────────────────────────────────────────────────────────────────────────────────

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_booking__process_, container, false);

        bookingLocationNameInput  = view.findViewById(R.id.booking_location_name_input);
        bookingVehicleNumberInput = view.findViewById(R.id.booking_vehicle_number_input);
        bookingDatePicker         = view.findViewById(R.id.booking_date_picker);
        bookingTimePicker         = view.findViewById(R.id.booking_time_picker);
        seatGrid                  = view.findViewById(R.id.seat_grid);
        bookNowBtn                = view.findViewById(R.id.book_now_btn);
        callButton                = view.findViewById(R.id.call_button);
        messageButton             = view.findViewById(R.id.message_button);

        db = FirebaseFirestore.getInstance();

        bookingDatePicker.setOnClickListener(v -> showDatePicker());
        bookingTimePicker.setOnClickListener(v -> showTimePicker());

        // ── Book Now ───────────────────────────────────────────────────────────────────────────────────────────
        bookNowBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                bookSelectedSeats();
            } else {

                locationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        if (getArguments() != null) {
            locationName = getArguments().getString("location_name", "");
            if (!locationName.isEmpty()) {
                bookingLocationNameInput.setText(locationName);
                loadLocationMobileNumber(locationName);
                loadFullCountAndCreateSeats(locationName);
            }
        }

        // ── Call button ───────────────────────────────────────────────────────

        callButton.setOnClickListener(v -> {
            if (locationMobileNumber.isEmpty()) {
                Toast.makeText(getContext(), "Phone number not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED) {
                openDialer();
            } else {
                callRequested = true; // set BEFORE launch so launcher can verify
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
            }
        });

        // ── Message button ─────────────────────────────────────────────────────────────────────────────────────

        messageButton.setOnClickListener(v -> {
            if (locationMobileNumber.isEmpty()) {
                Toast.makeText(getContext(), "Phone number not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                openSmsApp();
            } else {
                smsRequested = true; // set BEFORE launch so launcher can verify
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            }
        });

        return view;
    }

    // ── Open dialer ─────────────────────────────────────────────────────────────────────

    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + locationMobileNumber));
        startActivity(intent);
    }

    // ── Open SMS app ─────────────────────────────────────────────────────────────────────────────

    private void openSmsApp() {
        String message = "Hello, I'm contacting about the parking booking at "
                + locationName;
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + locationMobileNumber));
        intent.putExtra("sms_body", message);
        startActivity(intent);
    }

    // ── Shared settings dialog ───────────────────────────────────────────────────────

    private void showSettingsDialog(String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage(message)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package",
                                    requireActivity().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Firestore ───────────────────────────────────────────────────────────────────────────────────

    private void loadLocationMobileNumber(String locationName) {
        db.collection("register_locations")
                .whereEqualTo("name", locationName)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String mobile = querySnapshot.getDocuments()
                                .get(0).getString("mobile");
                        if (mobile != null && !mobile.isEmpty()) {
                            locationMobileNumber = mobile;
                        } else {
                            Toast.makeText(getContext(), "Mobile number not found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(getContext(),
                (DatePicker view, int y, int m, int d) -> {
                    String date = d + "/" + (m + 1) + "/" + y;
                    bookingDatePicker.setText(date);
                    loadBookedSeatsForDate(date);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)) {{
            getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        }}.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(getContext(),
                (TimePicker view, int h, int min) ->
                        bookingTimePicker.setText(String.format("%02d:%02d", h, min)),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE), true)
                .show();
    }

    private void loadFullCountAndCreateSeats(String locationName) {
        db.collection("register_locations")
                .whereEqualTo("name", locationName)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        Long countLong = snap.getDocuments().get(0).getLong("fullCount");
                        if (countLong != null) {
                            fullCount = countLong.intValue();
                            createSeatBoxes(fullCount);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void createSeatBoxes(int count) {
        seatGrid.removeAllViews();
        seatGrid.setColumnCount(MAX_COLUMNS);
        int seatSize = getResources().getDimensionPixelSize(R.dimen.seat_size);
        selectedSeats.clear();
        bookedSeats.clear();

        for (int i = 0; i < count; i++) {
            final int seatIndex = i;
            View seat = new View(getContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width  = seatSize;
            params.height = seatSize;
            params.setMargins(8, 8, 8, 8);
            params.columnSpec = GridLayout.spec(i % MAX_COLUMNS);
            params.rowSpec    = GridLayout.spec(i / MAX_COLUMNS);
            seat.setLayoutParams(params);
            seat.setBackgroundResource(R.drawable.booking_available);
            seat.setTag("available");

            seat.setOnClickListener(v -> {
                if (bookedSeats.contains(seatIndex)) {
                    Toast.makeText(getContext(), "Seat already booked",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                for (Integer selected : selectedSeats) {
                    View prev = seatGrid.getChildAt(selected);
                    if (prev != null)
                        prev.setBackgroundResource(R.drawable.booking_available);
                }
                selectedSeats.clear();
                selectedSeats.add(seatIndex);
                seat.setBackgroundResource(R.drawable.booking_selected);
            });

            seatGrid.addView(seat);
        }
    }

    private void loadBookedSeatsForDate(String date) {
        if (locationName == null || locationName.isEmpty()) return;
        bookedSeats.clear();

        for (int i = 0; i < seatGrid.getChildCount(); i++) {
            View seat = seatGrid.getChildAt(i);
            seat.setBackgroundResource(R.drawable.booking_available);
            seat.setTag("available");
            seat.setEnabled(true);
        }
        selectedSeats.clear();

        db.collection("bookings")
                .whereEqualTo("location_name", locationName)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        Long idx = doc.getLong("seat_index");
                        if (idx != null) {
                            int seatIndex = idx.intValue();
                            bookedSeats.add(seatIndex);
                            if (seatIndex >= 0 && seatIndex < seatGrid.getChildCount()) {
                                View seat = seatGrid.getChildAt(seatIndex);
                                seat.setBackgroundResource(R.drawable.booked);
                                seat.setTag("booked");
                                seat.setEnabled(false);
                            }
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Error loading bookings: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void bookSelectedSeats() {
        String vehicleNumber = bookingVehicleNumberInput.getText().toString().trim();
        String date          = bookingDatePicker.getText().toString().trim();
        String time          = bookingTimePicker.getText().toString().trim();

        if (locationName == null || locationName.isEmpty()) {
            Toast.makeText(getContext(), "Location not set",
                    Toast.LENGTH_SHORT).show(); return;
        }
        if (vehicleNumber.isEmpty()) {
            Toast.makeText(getContext(), "Enter vehicle number",
                    Toast.LENGTH_SHORT).show(); return;
        }
        if (date.isEmpty() || date.equals("Select Date")) {
            Toast.makeText(getContext(), "Select a date",
                    Toast.LENGTH_SHORT).show(); return;
        }
        if (time.isEmpty() || time.equals("Select Time")) {
            Toast.makeText(getContext(), "Select a time",
                    Toast.LENGTH_SHORT).show(); return;
        }
        if (selectedSeats.isEmpty()) {
            Toast.makeText(getContext(), "Select at least one seat",
                    Toast.LENGTH_SHORT).show(); return;
        }

        SharedPreferences prefs = requireContext()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("email", "");
        if (userEmail.isEmpty()) {
            Toast.makeText(getContext(), "User email not found. Please login.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("register_locations")
                .whereEqualTo("name", locationName)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        Double fullPaymentVal = snap.getDocuments()
                                .get(0).getDouble("fullPayment");
                        if (fullPaymentVal != null) {
                            halfPayment          = fullPaymentVal / 2.0;
                            bookingVehicleNumber = vehicleNumber;
                            bookingDate          = date;
                            bookingTime          = time;
                            bookingUserEmail     = userEmail;
                            bookingSelectedSeats.clear();
                            bookingSelectedSeats.addAll(selectedSeats);

                            String orderId = userEmail + "_" + System.currentTimeMillis();
                            currentBookingForPayment = new Booking(
                                    locationName, vehicleNumber, date, time,
                                    userEmail, -1, "#1", halfPayment);
                            initiatePayment(currentBookingForPayment, orderId,
                                    halfPayment, userEmail, bookingSelectedSeats.size());
                        } else {
                            Toast.makeText(getContext(), "Payment amount not found",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Error fetching payment details: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void initiatePayment(Booking booking, String orderId,
                                 double amount, String userEmail, int seatCount) {
        Activity activity = null;
        Context context = getContext();
        if (context instanceof Activity) {
            activity = (Activity) context;
        } else if (context instanceof ContextWrapper) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base instanceof Activity) activity = (Activity) base;
        }
        if (activity == null) {
            Toast.makeText(context, "Error: Unable to process payment",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        InitRequest req = new InitRequest();
        req.setMerchantId("1221504");
        req.setCurrency("LKR");
        req.setAmount(amount);
        req.setOrderId(orderId);
        req.setItemsDescription(
                "Parking Space Booking - Half Payment (" + seatCount + " seats)");
        req.getCustomer().setFirstName("Customer");
        req.getCustomer().setLastName("User");
        req.getCustomer().setEmail(userEmail);
        req.getCustomer().setPhone("");
        req.getCustomer().getAddress().setAddress("No.1, Colombo");
        req.getCustomer().getAddress().setCity("Colombo");
        req.getCustomer().getAddress().setCountry("Sri Lanka");
        req.setNotifyUrl("https://your-notify-url.com");

        Intent intent = new Intent(activity, PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);
        paymentResultLauncher.launch(intent);
    }

    // ── Booking POJO ────────────────────────────────────────────────────────────────────────────────

    public static class Booking {
        public String location_name, vehicle_number, date, time,
                user_email, payment_status;
        public int    seat_index;
        public double payment_amount;

        public Booking() {}

        public Booking(String location_name, String vehicle_number,
                       String date, String time, String user_email,
                       int seat_index, String payment_status, double payment_amount) {
            this.location_name  = location_name;
            this.vehicle_number = vehicle_number;
            this.date           = date;
            this.time           = time;
            this.user_email     = user_email;
            this.seat_index     = seat_index;
            this.payment_status = payment_status;
            this.payment_amount = payment_amount;
        }
    }
}