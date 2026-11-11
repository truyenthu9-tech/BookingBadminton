package com.example.myapplication.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.databinding.ActivityAdminDashboardBinding;
import com.example.myapplication.ui.adapter.TicketAdapter;
import com.example.myapplication.ui.auth.LoginActivity;
import com.example.myapplication.ui.ticket.TicketDetailActivity;
import com.example.myapplication.utils.FileSaver;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.BookingViewModel;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboardActivity extends AppCompatActivity {
    private BookingViewModel viewModel;
    private ActivityAdminDashboardBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardManageCourts.setOnClickListener(v ->
                startActivity(new Intent(this, ManageCourtsActivity.class)));
        binding.cardDashboard.setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));
        binding.btnExportReport.setOnClickListener(v -> exportReport());

        // Hóa đơn chờ kiểm duyệt: bấm vào để xem / duyệt / hủy
        TicketAdapter adapter = new TicketAdapter(this::showInvoiceActions);
        binding.rvInvoices.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        viewModel.getPendingBookings().observe(this, adapter::submit);
        viewModel.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        addLogoutButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadPendingBookings();
    }

    // ---------- Duyệt / hủy hóa đơn ----------
    private void showInvoiceActions(Booking b) {
        String[] items = {
                getString(R.string.invoice_view_ticket),
                getString(R.string.invoice_approve),
                getString(R.string.invoice_cancel)};
        new AlertDialog.Builder(this)
                .setTitle(b.getCode() + " - " + b.getCourtName())
                .setItems(items, (d, which) -> {
                    if (which == 0) TicketDetailActivity.start(this, b);
                    else changeStatus(b, which == 1);
                })
                .show();
    }

    private void changeStatus(Booking b, boolean approve) {
        Call<Booking> call = approve
                ? ApiClient.getService().approveBooking(b.getId())
                : ApiClient.getService().cancelBooking(b.getId());
        call.enqueue(new Callback<Booking>() {
            @Override
            public void onResponse(@NonNull Call<Booking> c, @NonNull Response<Booking> r) {
                if (r.isSuccessful()) {
                    Toast.makeText(AdminDashboardActivity.this,
                            approve ? R.string.msg_invoice_approved : R.string.msg_invoice_cancelled,
                            Toast.LENGTH_SHORT).show();
                    viewModel.loadPendingBookings(); // danh sách chờ duyệt cập nhật lại
                } else {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Thao tác thất bại (" + r.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Booking> c, @NonNull Throwable t) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Xuất báo cáo (CSV, mở được bằng Excel) ----------
    private void exportReport() {
        binding.btnExportReport.setEnabled(false);
        ApiClient.getService().exportReport().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> c, @NonNull Response<ResponseBody> r) {
                binding.btnExportReport.setEnabled(true);
                if (!r.isSuccessful() || r.body() == null) {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Xuất báo cáo thất bại (" + r.code() + ")", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    String name = "bao_cao_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(new Date()) + ".csv";
                    String where = FileSaver.saveToDownloads(AdminDashboardActivity.this,
                            name, "text/csv", r.body().bytes());
                    Toast.makeText(AdminDashboardActivity.this,
                            getString(R.string.msg_report_saved, where), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Không lưu được file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> c, @NonNull Throwable t) {
                binding.btnExportReport.setEnabled(true);
                Toast.makeText(AdminDashboardActivity.this,
                        "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Đăng xuất (layout chưa có nút nên thêm bằng code) ----------
    private void addLogoutButton() {
        Button logout = new Button(this);
        logout.setText(R.string.btn_logout);
        logout.setTextColor(ContextCompat.getColor(this, R.color.white));
        logout.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 48));
        lp.topMargin = UiUtils.dp(this, 8);
        ((LinearLayout) binding.getRoot()).addView(logout, lp);
        logout.setOnClickListener(v -> {
            SessionStore.clear(this);
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }
}
