package com.javainstitute.parkfinder;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class QrReceiptDialog extends DialogFragment {

    private static final String ARG_EMAIL    = "email";
    private static final String ARG_MOBILE   = "mobile";
    private static final String ARG_LOCATION = "location";
    private static final String ARG_DATE     = "date";
    private static final String ARG_TIME     = "time";
    private static final String ARG_AMOUNT   = "amount";

    // ── Factory constructor — always use this, never new QrReceiptDialog() ────

    public static QrReceiptDialog newInstance(String email, String mobile,
                                              String location, String date,
                                              String time, double amount) {
        QrReceiptDialog dialog = new QrReceiptDialog();
        Bundle args = new Bundle();
        args.putString(ARG_EMAIL,    email);
        args.putString(ARG_MOBILE,   mobile);
        args.putString(ARG_LOCATION, location);
        args.putString(ARG_DATE,     date);
        args.putString(ARG_TIME,     time);
        args.putDouble(ARG_AMOUNT,   amount);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_qr_receipt, container, false);

        Bundle args     = requireArguments();
        String email    = args.getString(ARG_EMAIL,    "");
        String mobile   = args.getString(ARG_MOBILE,   "");
        String location = args.getString(ARG_LOCATION, "");
        String date     = args.getString(ARG_DATE,     "");
        String time     = args.getString(ARG_TIME,     "");
        double amount   = args.getDouble(ARG_AMOUNT,   0);

        ImageView qrImage   = view.findViewById(R.id.qr_image);
        TextView tvLocation = view.findViewById(R.id.receipt_location);
        TextView tvDate     = view.findViewById(R.id.receipt_date);
        TextView tvTime     = view.findViewById(R.id.receipt_time);
        TextView tvEmail    = view.findViewById(R.id.receipt_email);
        TextView tvMobile   = view.findViewById(R.id.receipt_mobile);
        TextView tvAmount   = view.findViewById(R.id.receipt_amount);
        Button downloadBtn  = view.findViewById(R.id.download_pdf_btn);
        Button closeBtn     = view.findViewById(R.id.close_receipt_btn);

        // ── Populate receipt fields ───────────────────────────────────────────
        tvLocation.setText(location);
        tvDate.setText(date);
        tvTime.setText(time);
        tvEmail.setText(email);
        tvMobile.setText(mobile);
        tvAmount.setText("Rs. " + String.format("%.2f", amount));

        // ── Generate QR ───────────────────────────────────────────────────────
        // QR content: structured text with all booking details
        String qrContent =
                "ParkFinder Booking Receipt" + "\n" +
                        "Location: "   + location    + "\n" +
                        "Date: "       + date        + "\n" +
                        "Time: "       + time        + "\n" +
                        "Email: "      + email       + "\n" +
                        "Mobile: "     + mobile      + "\n" +
                        "Total Paid: Rs. " + String.format("%.2f", amount);

        Bitmap qrBitmap = generateQrBitmap(qrContent, 512);
        if (qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        } else {
            qrImage.setVisibility(View.GONE);
        }

        // ── Download PDF ──────────────────────────────────────────────────────
        Bitmap finalQr = qrBitmap;
        downloadBtn.setOnClickListener(v ->
                savePdf(requireContext(), email, mobile, location,
                        date, time, amount, finalQr));

        closeBtn.setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make dialog full width
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ── QR bitmap generation via ZXing ────────────────────────────────────────

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ── PDF generation using Android PdfDocument API ──────────────────────────
    // No external library required. A4 size = 595 x 842 pts at 72dpi.

    private void savePdf(Context ctx, String email, String mobile,
                         String location, String date, String time,
                         double amount, Bitmap qrBitmap) {

        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas c = page.getCanvas();

        // ── Teal header ───────────────────────────────────────────────────────
        Paint headerBg = new Paint();
        headerBg.setColor(Color.parseColor("#00BCD4"));
        c.drawRect(0, 0, 595, 90, headerBg);

        Paint boldWhite = makePaint(Color.WHITE, 24f, true);
        c.drawText("ParkFinder", 40, 52, boldWhite);

        Paint subWhite = makePaint(Color.WHITE, 13f, false);
        c.drawText("Booking Receipt", 40, 74, subWhite);

        // ── Details table ─────────────────────────────────────────────────────
        String[][] rows = {
                {"Location",   location},
                {"Date",       date},
                {"Time",       time},
                {"Email",      email},
                {"Mobile",     mobile},
                {"Total Paid", "Rs. " + String.format("%.2f", amount)},
        };

        Paint labelPaint   = makePaint(Color.parseColor("#9E9E9E"), 12f, false);
        Paint valuePaint   = makePaint(Color.parseColor("#212121"), 13f, true);
        Paint amountPaint  = makePaint(Color.parseColor("#00BCD4"), 15f, true);
        Paint dividerPaint = makePaint(Color.parseColor("#F0F0F0"), 1f, false);
        dividerPaint.setStyle(Paint.Style.STROKE);

        int y = 130;
        for (int i = 0; i < rows.length; i++) {
            c.drawText(rows[i][0], 40, y, labelPaint);
            // Last row (Total Paid) uses teal color
            c.drawText(rows[i][1], 200, y, (i == rows.length - 1) ? amountPaint : valuePaint);
            c.drawLine(40, y + 12, 555, y + 12, dividerPaint);
            y += 36;
        }

        // ── QR code ───────────────────────────────────────────────────────────
        if (qrBitmap != null) {
            int qrSize = 180;
            Bitmap scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, false);
            int qrX = (595 - qrSize) / 2;
            c.drawBitmap(scaledQr, qrX, y + 16, null);

            Paint qrCaption = makePaint(Color.parseColor("#BDBDBD"), 11f, false);
            qrCaption.setTextAlign(Paint.Align.CENTER);
            c.drawText("Scan to verify booking", 297, y + qrSize + 36, qrCaption);
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Paint footerBg = new Paint();
        footerBg.setColor(Color.parseColor("#F5F5F5"));
        c.drawRect(0, 810, 595, 842, footerBg);

        Paint footerText = makePaint(Color.parseColor("#BDBDBD"), 10f, false);
        footerText.setTextAlign(Paint.Align.CENTER);
        c.drawText("Generated by ParkFinder  •  This is your official booking receipt",
                297, 830, footerText);

        pdf.finishPage(page);

        // ── Save PDF ──────────────────────────────────────────────────────────
        String fileName = "ParkFinder_Receipt_" + System.currentTimeMillis() + ".pdf";
        boolean saved = false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ — use MediaStore, no permission required
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = ctx.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                        pdf.writeTo(out);
                        saved = true;
                    }
                }
            } else {
                // API < 29 — use File API (WRITE_EXTERNAL_STORAGE declared in manifest)
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    pdf.writeTo(fos);
                    saved = true;
                }
            }
        } catch (Exception e) {
            Toast.makeText(ctx, "Failed to save PDF: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            pdf.close();
        }

        if (saved) {
            Toast.makeText(ctx,
                    "✅ PDF saved to Downloads: " + fileName,
                    Toast.LENGTH_LONG).show();
        }
    }

    // ── Paint factory helper ──────────────────────────────────────────────────

    private Paint makePaint(int color, float textSize, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(textSize);
        if (bold) p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        return p;
    }
}