package com.example.myapplication.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.data.model.PaymentStatus;
import com.example.myapplication.databinding.ActivityPaymentBinding;
import com.example.myapplication.ui.payment.VnPayWebActivity;
import com.example.myapplication.ui.ticket.TicketDetailActivity;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.BookingViewModel;

import java.util.ArrayList;
import java.util.List;

public class PaymentActivity extends AppCompatActivity {
    public static final String EXTRA_COURT_ID = "court_id";
    public static final String EXTRA_COURT_NAME = "court_name";
    public static final String EXTRA_DATE = "date"; // yyyy-MM-dd
    public static final String EXTRA_SLOT_ID = "slot_id";
    public static final String EXTRA_SLOT_LABEL = "slot_label";
    public static final String EXTRA_TOTAL = "total";

    private ActivityPaymentBinding binding;
    private BookingViewModel viewModel;

    private Booking createdBooking;
    private boolean vnPayFlow = false; // true khi người dùng chọn thanh toán VNPay

    private final ActivityResultLauncher<Intent> vnpayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (createdBooking == null) {
                    finish();
                    return;
                }
                if (result.getResultCode() == RESULT_OK) {
                    // WebView đã về tới trang return -> hỏi server trạng thái cuối cùng
                    viewModel.checkPaymentStatus(createdBooking.getId());
                } else {
                    Toast.makeText(this, R.string.msg_payment_cancelled, Toast.LENGTH_LONG).show();
                    TicketDetailActivity.start(this, createdBooking);
                    finish();
                }
            });

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
            binding.btnPayVnPay.setEnabled(!l);
        });
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        // Đơn được tạo xong -> đi tiếp theo luồng đã chọn
        viewModel.getBooking().observe(this, booking -> {
            if (booking == null) return;
            createdBooking = booking;
            if (vnPayFlow) {
                startVnPay(booking);
            } else {
                Toast.makeText(this, R.string.msg_booking_success, Toast.LENGTH_LONG).show();
                TicketDetailActivity.start(this, booking);
                finish();
            }
        });

        // Nhận được payment_url -> mở WebView VNPay
        viewModel.getPaymentUrl().observe(this, url -> {
            if (url == null || url.isEmpty()) return;
            Intent i = new Intent(this, VnPayWebActivity.class);
            i.putExtra(VnPayWebActivity.EXTRA_URL, url);
            vnpayLauncher.launch(i);
        });

        // Kết quả kiểm tra trạng thái thanh toán
        viewModel.getPaymentStatus().observe(this, ps -> {
            if (ps == null) return;
            if (ps.isPaid()) {
                Toast.makeText(this, R.string.msg_payment_success, Toast.LENGTH_LONG).show();
            } else if (PaymentStatus.PENDING.equalsIgnoreCase(ps.getPaymentStatus())) {
                Toast.makeText(this, R.string.msg_payment_pending, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.msg_payment_failed, Toast.LENGTH_LONG).show();
            }
            if (createdBooking != null) TicketDetailActivity.start(this, createdBooking);
            finish();
        });

        binding.btnPay.setOnClickListener(v -> {
            vnPayFlow = false;
            binding.btnPay.setEnabled(false);
            viewModel.submitBooking(buildDraft(courtId, slotId, date));
        });

        binding.btnPayVnPay.setOnClickListener(v -> {
            vnPayFlow = true;
            binding.btnPayVnPay.setEnabled(false);
            if (createdBooking != null) {
                startVnPay(createdBooking); // đơn đã tạo rồi, thanh toán luôn
            } else {
                viewModel.submitBooking(buildDraft(courtId, slotId, date));
            }
        });
    }

    private Booking buildDraft(int courtId, int slotId, String date) {
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
        return draft;
    }

    private void startVnPay(Booking booking) {
        binding.btnPayVnPay.setEnabled(false);
        viewModel.createVnPayPayment(booking.getId());
    }
}
