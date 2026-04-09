package com.javainstitute.parkfinder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookingHistoryAdapter
        extends RecyclerView.Adapter<BookingHistoryAdapter.ViewHolder> {

    private final List<Booking> bookingList;

    public BookingHistoryAdapter(List<Booking> bookingList) {
        this.bookingList = bookingList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Booking booking = bookingList.get(position);
        holder.location.setText(booking.getLocation());
        holder.date.setText("Date: " + booking.getDate());
        holder.time.setText("Time: " + booking.getTime());
        holder.price.setText("Total Paid: Rs. "
                + String.format("%.2f", booking.getBalance()));
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView location, date, time, price;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            location = itemView.findViewById(R.id.history_item_location);
            date     = itemView.findViewById(R.id.history_item_date);
            time     = itemView.findViewById(R.id.history_item_time);
            price    = itemView.findViewById(R.id.history_item_price);
        }
    }
}