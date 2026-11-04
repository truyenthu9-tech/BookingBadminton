package com.example.myapplication.ui.ticket;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.data.model.Booking;
import com.example.myapplication.databinding.ActivityTicketDetailBinding;
import com.example.myapplication.ui.components.QRCodeView;
import com.example.myapplication.ui.components.StatusBadge;
import com.example.myapplication.utils.UiUtils;

public class TicketDetailActivity extends AppCompatActivity {
    private static final String EXTRA_CODE = "code";
    private static final String EXTRA_COURT = "court";
    private static final String EXTRA_DATE = "date"; // yyyy-MM-dd
    private static final String EXTRA_START = "start";
    private static final String EXTRA_END = "end";
    private static final String EXTRA_STATUS = "status";

    /** Mở màn chi tiết vé từ một Booking (không cần Booking implement Serializable). */
    public static void start(Context ctx, Booking b) {
        Intent i = new Intent(ctx, TicketDetailActivity.class);
        i.putExtra(EXTRA_CODE, b.getCode());
        i.putExtra(EXTRA_COURT, b.getCourtName());
        i.putExtra(EXTRA_DATE, b.getDate());
        i.putExtra(EXTRA_START, b.getStartTime());
        i.putExtra(EXTRA_END, b.getEndTime());
        i.putExtra(EXTRA_STATUS, b.getStatus());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityTicketDetailBinding binding = ActivityTicketDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent in = getIntent();
        String code = in.getStringExtra(EXTRA_CODE);
        String date = UiUtils.toDisplayDate(in.getStringExtra(EXTRA_DATE));
        String start = in.getStringExtra(EXTRA_START);
        String end = in.getStringExtra(EXTRA_END);

        binding.tvBookingCode.setText(code);
        binding.tvCourtName.setText(in.getStringExtra(EXTRA_COURT));
        binding.tvDateSlot.setText(getString(com.example.myapplication.R.string.ticket_date_slot, date, start, end));
        binding.tvValidUntil.setText(end + " " + date);
        StatusBadge.apply(binding.chipStatus, in.getStringExtra(EXTRA_STATUS));
        QRCodeView.bind(binding.ivQrCode, code);
    }
}
