package com.example.myapplication.ui.ticket;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.data.model.PaymentInfo;
import com.example.myapplication.data.model.PaymentStatus;
import com.example.myapplication.databinding.ActivityTicketDetailBinding;
import com.example.myapplication.ui.components.QRCodeView;
import com.example.myapplication.ui.components.StatusBadge;
import com.example.myapplication.ui.payment.VnPayWebActivity;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TicketDetailActivity extends AppCompatActivity {
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_CODE = "code";
    private static final String EXTRA_COURT = "court";
    private static final String EXTRA_DATE = "date"; // yyyy-MM-dd
    private static final String EXTRA_START = "start";
    private static final String EXTRA_END = "end";
    private static final String EXTRA_STATUS = "status";
    private static final String EXTRA_PAYMENT = "payment";

    /** Mở màn chi tiết vé từ một Booking (không cần Booking implement Serializable). */
    public static void start(Context ctx, Booking b) {
        Intent i = new Intent(ctx, TicketDetailActivity.class);
        i.putExtra(EXTRA_ID, b.getId());
        i.putExtra(EXTRA_CODE, b.getCode());
        i.putExtra(EXTRA_COURT, b.getCourtName());
        i.putExtra(EXTRA_DATE, b.getDate());
        i.putExtra(EXTRA_START, b.getStartTime());
        i.putExtra(EXTRA_END, b.getEndTime());
        i.putExtra(EXTRA_STATUS, b.getStatus());
        i.putExtra(EXTRA_PAYMENT, b.getPaymentStatus());
        ctx.startActivity(i);
    }

    private ActivityTicketDetailBinding binding;
    private int bookingId;
    private String bookingStatus;
    private int payingBookingId = 0;

    private final ActivityResultLauncher<Intent> payLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (payingBookingId == 0) return;
                if (result.getResultCode() == RESULT_OK) {
                    checkStatus(payingBookingId);
                } else {
                    binding.btnPayVnPay.setEnabled(true);
                    Toast.makeText(this, R.string.msg_payment_cancelled, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent in = getIntent();
        bookingId = in.getIntExtra(EXTRA_ID, 0);
        String code = in.getStringExtra(EXTRA_CODE);
        bookingStatus = in.getStringExtra(EXTRA_STATUS);
        String date = UiUtils.toDisplayDate(in.getStringExtra(EXTRA_DATE));
        String start = in.getStringExtra(EXTRA_START);
        String end = in.getStringExtra(EXTRA_END);

        binding.tvBookingCode.setText(code);
        binding.tvCourtName.setText(in.getStringExtra(EXTRA_COURT));
        binding.tvDateSlot.setText(getString(R.string.ticket_date_slot, date, start, end));
        binding.tvValidUntil.setText(end + " " + date);
        StatusBadge.apply(binding.chipStatus, bookingStatus);
        QRCodeView.bind(binding.ivQrCode, code);

        renderPayment(in.getStringExtra(EXTRA_PAYMENT));
    }

    /** Cập nhật dòng trạng thái thanh toán + nút Thanh toán VNPay. */
    private void renderPayment(String payment) {
        binding.tvPaymentStatus.setText(paymentStatusLabel(payment));
        boolean payable = bookingId > 0
                && !"PAID".equalsIgnoreCase(payment)
                && ("pending".equalsIgnoreCase(bookingStatus) || "success".equalsIgnoreCase(bookingStatus))
                && !SessionStore.isAdmin(this); // admin xem vé thì không thanh toán ở màn này
        binding.btnPayVnPay.setVisibility(payable ? View.VISIBLE : View.GONE);
        binding.btnPayVnPay.setEnabled(payable);
        if (payable) {
            binding.btnPayVnPay.setOnClickListener(v -> startVnPay(bookingId));
        }
    }

    private String paymentStatusLabel(String payment) {
        if (payment == null) return getString(R.string.payment_status_unpaid);
        switch (payment.toUpperCase(java.util.Locale.ROOT)) {
            case "PAID":
                return getString(R.string.payment_status_paid);
            case "PENDING":
                return getString(R.string.payment_status_pending);
            case "FAILED":
                return getString(R.string.payment_status_failed);
            default:
                return getString(R.string.payment_status_unpaid);
        }
    }

    // ---------- Thanh toán VNPay ----------
    private void startVnPay(int bid) {
        binding.btnPayVnPay.setEnabled(false);
        Map<String, Object> body = new HashMap<>();
        body.put("booking_id", bid);
        ApiClient.getService().createVnPayPayment(body).enqueue(new Callback<PaymentInfo>() {
            @Override
            public void onResponse(@NonNull Call<PaymentInfo> call, @NonNull Response<PaymentInfo> response) {
                PaymentInfo info = response.body();
                if (response.isSuccessful() && info != null
                        && info.getPaymentUrl() != null && !info.getPaymentUrl().isEmpty()) {
                    payingBookingId = bid;
                    Intent i = new Intent(TicketDetailActivity.this, VnPayWebActivity.class);
                    i.putExtra(VnPayWebActivity.EXTRA_URL, info.getPaymentUrl());
                    payLauncher.launch(i);
                } else {
                    binding.btnPayVnPay.setEnabled(true);
                    toast("Tạo giao dịch thanh toán thất bại (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentInfo> call, @NonNull Throwable t) {
                binding.btnPayVnPay.setEnabled(true);
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void checkStatus(int bid) {
        ApiClient.getService().getPaymentStatus(bid).enqueue(new Callback<PaymentStatus>() {
            @Override
            public void onResponse(@NonNull Call<PaymentStatus> call, @NonNull Response<PaymentStatus> response) {
                if (response.isSuccessful() && response.body() != null) {
                    PaymentStatus ps = response.body();
                    if (ps.isPaid()) {
                        Toast.makeText(TicketDetailActivity.this, R.string.msg_payment_success, Toast.LENGTH_LONG).show();
                        renderPayment(PaymentStatus.PAID);
                    } else if (PaymentStatus.PENDING.equalsIgnoreCase(ps.getPaymentStatus())) {
                        Toast.makeText(TicketDetailActivity.this, R.string.msg_payment_pending, Toast.LENGTH_LONG).show();
                        binding.btnPayVnPay.setEnabled(true);
                    } else {
                        Toast.makeText(TicketDetailActivity.this, R.string.msg_payment_failed, Toast.LENGTH_LONG).show();
                        binding.btnPayVnPay.setEnabled(true);
                    }
                } else {
                    binding.btnPayVnPay.setEnabled(true);
                    toast("Không kiểm tra được trạng thái thanh toán (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentStatus> call, @NonNull Throwable t) {
                binding.btnPayVnPay.setEnabled(true);
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
