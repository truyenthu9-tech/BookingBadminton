package com.example.myapplication.ui.admin;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.StatItem;
import com.example.myapplication.data.model.StatsResponse;
import com.example.myapplication.databinding.ActivityDashboardBinding;
import com.example.myapplication.ui.components.BarChartView;
import com.example.myapplication.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Dashboard: biểu đồ doanh thu (tuần/tháng/năm) + tỉ lệ đặt thành công theo sân. */
public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;

    private static final int[] COURT_COLORS = {
            R.color.primary, R.color.accent_orange, R.color.status_success,
            R.color.slot_selected, R.color.status_pending, R.color.primary_dark
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.togglePeriod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String period = "week";
            if (checkedId == R.id.btnMonth) period = "month";
            else if (checkedId == R.id.btnYear) period = "year";
            loadRevenue(period);
        });

        loadRevenue("week");
        loadCourts();
    }

    private void loadRevenue(String period) {
        ApiClient.getService().getRevenueStats(period).enqueue(new Callback<StatsResponse>() {
            @Override
            public void onResponse(@NonNull Call<StatsResponse> call, @NonNull Response<StatsResponse> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    toast("Tải doanh thu thất bại (" + r.code() + ")");
                    return;
                }
                List<StatItem> items = r.body().getItems();
                List<BarChartView.Bar> bars = new ArrayList<>();
                double total = 0;
                if (items != null) {
                    for (StatItem it : items) {
                        double v = it.getValue();
                        total += v;
                        bars.add(new BarChartView.Bar(
                                it.getLabel(), (float) v, shortMoney(v),
                                ContextCompat.getColor(DashboardActivity.this, R.color.primary)));
                    }
                }
                binding.chartRevenue.setBars(bars);
                binding.tvRevenueTotal.setText(getString(R.string.label_total) + ": " + FormatUtils.formatPrice(total));
            }

            @Override
            public void onFailure(@NonNull Call<StatsResponse> call, @NonNull Throwable t) {
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void loadCourts() {
        ApiClient.getService().getCourtStats().enqueue(new Callback<StatsResponse>() {
            @Override
            public void onResponse(@NonNull Call<StatsResponse> call, @NonNull Response<StatsResponse> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    toast("Tải tỉ lệ sân thất bại (" + r.code() + ")");
                    return;
                }
                List<StatItem> items = r.body().getItems();
                List<BarChartView.Bar> bars = new ArrayList<>();
                if (items != null) {
                    int i = 0;
                    for (StatItem it : items) {
                        double rate = it.getValue();
                        String extra = it.getExtra() == null ? "" : it.getExtra();
                        String valueText = String.format(Locale.US, "%.0f%%", rate)
                                + (extra.isEmpty() ? "" : " · " + extra);
                        int color = ContextCompat.getColor(DashboardActivity.this,
                                COURT_COLORS[i % COURT_COLORS.length]);
                        bars.add(new BarChartView.Bar(it.getLabel(), (float) rate, valueText, color));
                        i++;
                    }
                }
                binding.chartCourts.setBars(bars);
            }

            @Override
            public void onFailure(@NonNull Call<StatsResponse> call, @NonNull Throwable t) {
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    /** 80000 -> "80k", 1200000 -> "1.2tr" (nhãn ngắn cho biểu đồ). */
    private String shortMoney(double v) {
        if (v >= 1_000_000) {
            double m = v / 1_000_000;
            return (m >= 10 ? String.format(Locale.US, "%.0f", m) : String.format(Locale.US, "%.1f", m)) + "tr";
        }
        if (v >= 1000) {
            return String.format(Locale.US, "%.0f", v / 1000) + "k";
        }
        return String.format(Locale.US, "%.0f", v);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
