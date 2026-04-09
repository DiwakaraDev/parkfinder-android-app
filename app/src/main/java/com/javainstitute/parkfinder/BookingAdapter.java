package com.javainstitute.parkfinder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    private List<Booking> bookings;
    private OnBookingClickListener onBookingClickListener;

    public interface OnBookingClickListener {
        void onConfirmClick(Booking booking);
        void onCancelClick(Booking booking);
    }

    public BookingAdapter(List<Booking> bookings, OnBookingClickListener listener) {
        this.bookings = bookings;
        this.onBookingClickListener = listener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = bookings.get(position);
        holder.location.setText(booking.getLocation());
        holder.date.setText("Date: " + booking.getDate());
        holder.time.setText("Time: " + booking.getTime());
        holder.price.setText("Balance: Rs. " + booking.getBalance());

        holder.btnConfirm.setOnClickListener(v -> onBookingClickListener.onConfirmClick(booking));
        holder.btnCancel.setOnClickListener(v -> onBookingClickListener.onCancelClick(booking));
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView location, date, time, price;
        Button btnCancel, btnConfirm;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            location = itemView.findViewById(R.id.item_location);
            date = itemView.findViewById(R.id.item_date);
            time = itemView.findViewById(R.id.item_time);
            price = itemView.findViewById(R.id.item_price);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
            btnConfirm = itemView.findViewById(R.id.btn_confirm);
        }
    }
}
