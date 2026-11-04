package com.example.myapplication.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.databinding.ItemCourtCardBinding;
import com.example.myapplication.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Danh sách sân, dùng layout item_court_card. */
public class CourtAdapter extends RecyclerView.Adapter<CourtAdapter.VH> {

    public interface OnCourtClickListener { void onCourtClick(Court court); }

    private final List<Court> items = new ArrayList<>();
    private final OnCourtClickListener listener;

    public CourtAdapter(OnCourtClickListener listener) { this.listener = listener; }

    public void submit(List<Court> courts) {
        items.clear();
        if (courts != null) items.addAll(courts);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemCourtCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Court c = items.get(position);
        ItemCourtCardBinding b = h.binding;
        String name = c.isHidden()
                ? c.getName() + b.getRoot().getContext().getString(R.string.court_hidden_suffix)
                : c.getName();
        b.tvCourtName.setText(name);
        b.getRoot().setAlpha(c.isHidden() ? 0.5f : 1f);
        b.tvCourtLocation.setText(c.getLocation());
        b.tvCourtPrice.setText(b.getRoot().getContext()
                .getString(R.string.price_per_slot, UiUtils.formatPrice(c.getPrice())));

        boolean available = c.isAvailable();
        b.chipCourtStatus.setText(available ? R.string.court_status_available : R.string.court_status_booked);
        b.chipCourtStatus.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(
                b.getRoot().getContext(), available ? R.color.slot_available : R.color.slot_booked)));

        b.getRoot().setOnClickListener(v -> { if (listener != null) listener.onCourtClick(c); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemCourtCardBinding binding;
        VH(ItemCourtCardBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
