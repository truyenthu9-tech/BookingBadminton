package com.example.myapplication.ui.home;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.ui.adapter.CourtAdapter;
import com.example.myapplication.ui.booking.CourtDetailActivity;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.HomeViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private CourtAdapter courtAdapter;
    private String selectedDate; // yyyy-MM-dd

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Thanh chọn ngày
        List<String> dates = viewModel.getDates();
        String current = viewModel.getSelectedDate().getValue();
        int startIndex = Math.max(0, dates.indexOf(current));
        selectedDate = dates.get(startIndex);
        binding.rvDates.setAdapter(new DateAdapter(dates, startIndex, date -> {
            selectedDate = date;
            viewModel.selectDate(date);
        }));

        // Danh sách sân
        courtAdapter = new CourtAdapter(court -> {
            Intent i = new Intent(requireContext(), CourtDetailActivity.class);
            i.putExtra(CourtDetailActivity.EXTRA_COURT_ID, court.getId());
            i.putExtra(CourtDetailActivity.EXTRA_DATE, selectedDate);
            startActivity(i);
        });
        binding.rvCourts.setAdapter(courtAdapter);

        viewModel.getCourts().observe(getViewLifecycleOwner(), list -> {
            List<Court> visible = new ArrayList<>();
            if (list != null) for (Court c : list) if (!c.isHidden()) visible.add(c); // ẩn sân admin đã ẩn
            courtAdapter.submit(visible);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                binding.progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ---------- Adapter chọn ngày (item dựng bằng code, không cần layout riêng) ----------
    private static class DateAdapter extends RecyclerView.Adapter<DateAdapter.VH> {
        interface OnDateSelected { void onDate(String isoDate); }

        private final List<String> dates;
        private final OnDateSelected listener;
        private int selected;

        DateAdapter(List<String> dates, int selected, OnDateSelected listener) {
            this.dates = dates;
            this.selected = selected;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    UiUtils.dp(parent.getContext(), 64), UiUtils.dp(parent.getContext(), 64));
            int m = UiUtils.dp(parent.getContext(), 4);
            lp.setMargins(m, 0, m, 0);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(13);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String iso = dates.get(position);
            try {
                java.util.Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
                String dow = new SimpleDateFormat("EEE", new Locale("vi")).format(d);
                String dm = new SimpleDateFormat("dd/MM", Locale.US).format(d);
                h.tv.setText(dow + "\n" + dm);
            } catch (Exception e) {
                h.tv.setText(iso);
            }
            boolean sel = position == selected;
            android.content.Context c = h.tv.getContext();
            h.tv.setBackground(UiUtils.rounded(ContextCompat.getColor(c,
                    sel ? R.color.primary : R.color.surface), UiUtils.dp(c, 12)));
            h.tv.setTextColor(ContextCompat.getColor(c, sel ? R.color.white : R.color.text_primary));
            h.tv.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos == selected) return;
                int prev = selected;
                selected = pos;
                notifyItemChanged(prev);
                notifyItemChanged(selected);
                listener.onDate(dates.get(pos));
            });
        }

        @Override public int getItemCount() { return dates.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(TextView tv) { super(tv); this.tv = tv; }
        }
    }
}
