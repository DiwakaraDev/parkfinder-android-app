package com.javainstitute.parkfinder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class BookingHistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private BookingHistoryAdapter adapter;
    private ArrayList<Booking> bookingList;
    private View emptyState;
    private FirebaseFirestore firestore;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_booking_history, container, false);

        recyclerView = view.findViewById(R.id.history_recycler_view);
        emptyState   = view.findViewById(R.id.history_empty_state);
        bookingList  = new ArrayList<>();
        firestore    = FirebaseFirestore.getInstance();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new BookingHistoryAdapter(bookingList);
        recyclerView.setAdapter(adapter);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("email", null);

        if (userEmail != null) {
            loadCompletedBookings(userEmail);
        }

        return view;
    }

    // ── Load only fully paid bookings (payment_status == "#2") ────────────────

    private void loadCompletedBookings(String email) {
        firestore.collection("bookings")
                .whereEqualTo("user_email",      email)
                .whereEqualTo("payment_status",  "#2") // fully confirmed bookings only
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    bookingList.clear();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String loc     = doc.getString("location_name");
                        String dateStr = doc.getString("date");
                        String time    = doc.getString("time");
                        Double amount  = doc.getDouble("payment_amount");

                        Booking booking = new Booking(
                                loc    != null ? loc    : "—",
                                dateStr != null ? dateStr : "—",
                                time   != null ? time   : "—",
                                amount != null ? amount : 0.0
                        );
                        booking.setDocId(doc.getId());
                        bookingList.add(booking);
                    }

                    adapter.notifyDataSetChanged();
                    toggleEmptyState();
                })
                .addOnFailureListener(e ->
                        Log.e("BookingHistory", "Error loading history", e));
    }

    // ── Show empty state when list is empty ───────────────────────────────────

    private void toggleEmptyState() {
        if (bookingList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }
}