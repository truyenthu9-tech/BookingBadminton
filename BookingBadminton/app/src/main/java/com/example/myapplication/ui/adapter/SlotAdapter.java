package com.example.myapplication.ui.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.data.model.Slot;
import com.example.myapplication.ui.components.TimeSlotChip;
import com.example.myapplication.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/** Lưới ca giờ (3 cột). Không có layout item riêng: view tạo bằng TimeSlotChip. Chọn 1 ca tại một thời điểm. */
public class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.VH> {

    public interface OnSlotSelectedListener {
        /** slot = null khi người dùng bỏ chọn. */
        void onSlotSelected(@Nullable Slot slot);
    }

    private final List<Slot> slots = new ArrayList<>();
    private int selected = -1;
    private final OnSlotSelectedListener listener;

    public SlotAdapter(OnSlotSelectedListener listener) { this.listener = listener; }

    public void submit(List<Slot> list) {
        slots.clear();
        if (list != null) slots.addAll(list);
        selected = -1;
        notifyDataSetChanged();
        if (listener != null) listener.onSlotSelected(null);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TimeSlotChip chip = new TimeSlotChip(parent.getContext());
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(parent.getContext(), 48));
        int m = UiUtils.dp(parent.getContext(), 4);
        lp.setMargins(m, m, m, m);
        chip.setLayoutParams(lp);
        return new VH(chip);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Slot s = slots.get(position);
        TimeSlotChip.State state = s.isBooked() ? TimeSlotChip.State.BOOKED
                : position == selected ? TimeSlotChip.State.SELECTED : TimeSlotChip.State.AVAILABLE;
        h.chip.setSlot(s.getStartTime() + " - " + s.getEndTime(), state);

        h.chip.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || slots.get(pos).isBooked()) return;
            int prev = selected;
            selected = (selected == pos) ? -1 : pos;
            if (prev >= 0) notifyItemChanged(prev);
            if (selected >= 0) notifyItemChanged(selected);
            if (listener != null) listener.onSlotSelected(selected >= 0 ? slots.get(selected) : null);
        });
    }

    @Override public int getItemCount() { return slots.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TimeSlotChip chip;
        VH(TimeSlotChip chip) { super(chip); this.chip = chip; }
    }
}
