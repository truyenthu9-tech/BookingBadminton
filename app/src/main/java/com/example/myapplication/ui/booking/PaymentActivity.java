package com.example.myapplication.ui.booking;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.databinding.ActivityPaymentBinding;
import com.example.myapplication.ui.ticket.TicketDetailActivity;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.BookingViewModel;

public class PaymentActivity extends AppCompatActivity {
    public static final String EXTRA_COURT_ID = "court_id";
    public static final String EXTRA_COURT_NAME = "court_name";
    public static final String EXTRA_DATE = "date"; // yyyy-MM-dd
    public static final String EXTRA_SLOT_ID = "slot_id";
    public static final String EXTRA_SLOT_LABEL = "slot_label";
    public static final String EXTRA_TOTAL = "total";

    private ActivityPaymentBinding binding;
    private BookingViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int courtId = getIntent().getIntExtra(EXTRA_COURT_ID, -1);
        int slotId = getIntent().getIntExtra(EXTRA_SLOT_ID, -1);
        String date = getIntent().getStringExtra(EXTRA_DATE);

        binding.tvCourtName.setText(getIntent().getStringExtra(EXTRA_COURT_NAME));
        binding.tvDate.setText(UiUtils.toDisplayDate(date));
        binding.tvSlot.setText(getIntent().getStringExtra(EXTRA_SLOT_LABEL));
        binding.tvTotal.setText(UiUtils.formatPrice(getIntent().getDoubleExtra(EXTRA_TOTAL, 0)));

        viewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        viewModel.getLoading().observe(this, loading -> {
            boolean l = Boolean.TRUE.equals(loading);
            binding.progressBar.setVisibility(l ? View.VISIBLE : View.GONE);
            binding.btnPay.setEnabled(!l);
        });
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        viewModel.getBooking().observe(this, booking -> {
            if (booking == null) return;
            Toast.makeText(this, R.string.msg_booking_success, Toast.LENGTH_LONG).show();
            TicketDetailActivity.start(this, booking);
            finish();
        });

        binding.btnPay.setOnClickListener(v -> {
            List<Integer> ids = new ArrayList<>();
            ids.add(slotId);

            Booking draft = new Booking();
            draft.setUserId(SessionStore.userId(this));
            draft.setCourtId(courtId);
            draft.setCourtName(getIntent().getStringExtra(EXTRA_COURT_NAME));
            draft.setDate(date);
            draft.setSlotIds(ids);
            draft.setSlotsText(getIntent().getStringExtra(EXTRA_SLOT_LABEL));
            draft.setTotalPrice(getIntent().getDoubleExtra(EXTRA_TOTAL, 0));
            draft.setStatus(Booking.STATUS_PENDING);
            viewModel.submitBooking(draft);
        });
    }
}
