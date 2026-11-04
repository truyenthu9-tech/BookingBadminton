package com.example.myapplication.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.data.model.Slot;
import com.example.myapplication.databinding.ActivityCourtDetailBinding;
import com.example.myapplication.ui.adapter.SlotAdapter;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.BookingViewModel;

public class CourtDetailActivity extends AppCompatActivity {
    public static final String EXTRA_COURT_ID = "court_id";
    public static final String EXTRA_DATE = "date"; // yyyy-MM-dd

    private ActivityCourtDetailBinding binding;
    private BookingViewModel viewModel;
    private SlotAdapter slotAdapter;

    private Court court;
    private Slot selectedSlot;
    private String date;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCourtDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int courtId = getIntent().getIntExtra(EXTRA_COURT_ID, -1);
        date = getIntent().getStringExtra(EXTRA_DATE);

        slotAdapter = new SlotAdapter(slot -> {
            selectedSlot = slot;
            binding.btnContinue.setEnabled(slot != null);
        });
        binding.rvSlots.setAdapter(slotAdapter);

        viewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        viewModel.getCourt().observe(this, c -> {
            court = c;
            if (c == null) return;
            binding.tvCourtName.setText(c.getName());
            binding.tvCourtLocation.setText(c.getLocation());
            binding.tvCourtPrice.setText(getString(R.string.price_per_slot, UiUtils.formatPrice(c.getPrice())));
            binding.tvCourtDescription.setText(c.getDescription());
        });
        viewModel.getSlots().observe(this, slotAdapter::submit);
        viewModel.getLoading().observe(this, loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        binding.btnContinue.setOnClickListener(v -> {
            if (court == null || selectedSlot == null) return;
            Intent i = new Intent(this, PaymentActivity.class);
            i.putExtra(PaymentActivity.EXTRA_COURT_ID, court.getId());
            i.putExtra(PaymentActivity.EXTRA_COURT_NAME, court.getName());
            i.putExtra(PaymentActivity.EXTRA_DATE, date);
            i.putExtra(PaymentActivity.EXTRA_SLOT_ID, selectedSlot.getId());
            i.putExtra(PaymentActivity.EXTRA_SLOT_LABEL,
                    selectedSlot.getStartTime() + " - " + selectedSlot.getEndTime());
            i.putExtra(PaymentActivity.EXTRA_TOTAL, court.getPrice());
            startActivity(i);
        });

        viewModel.loadCourtDetail(courtId, date);
    }
}
