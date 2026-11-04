package com.example.myapplication.ui.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.example.myapplication.R;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.ui.components.StatusBadge;
import com.example.myapplication.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Danh sách vé / hóa đơn. res chưa có item_ticket.xml nên item dựng bằng code,
 * dùng đúng các màu và bo góc của item_court_card.
 */
public class TicketAdapter extends RecyclerView.Adapter<TicketAdapter.VH> {

    public interface OnTicketClickListener { void onTicketClick(Booking booking); }

    private final List<Booking> items = new ArrayList<>();
    private final OnTicketClickListener listener;

    public TicketAdapter(OnTicketClickListener listener) { this.listener = listener; }

    public void submit(List<Booking> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();

        MaterialCardView card = new MaterialCardView(c);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = UiUtils.dp(c, 6);
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);
        card.setRadius(UiUtils.dp(c, 16));
        card.setCardElevation(UiUtils.dp(c, 3));
        card.setCardBackgroundColor(ContextCompat.getColor(c, R.color.surface));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = UiUtils.dp(c, 14);
        col.setPadding(p, p, p, p);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvCode = new TextView(c);
        tvCode.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        tvCode.setTextSize(16);
        tvCode.setTypeface(tvCode.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(tvCode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        StatusBadge badge = new StatusBadge(c);
        row.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(row);

        TextView tvCourt = new TextView(c);
        tvCourt.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        tvCourt.setTextSize(15);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.topMargin = UiUtils.dp(c, 6);
        col.addView(tvCourt, l2);

        TextView tvTime = new TextView(c);
        tvTime.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
        tvTime.setTextSize(14);
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l3.topMargin = UiUtils.dp(c, 2);
        col.addView(tvTime, l3);

        card.addView(col);
        return new VH(card, tvCode, badge, tvCourt, tvTime);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Booking b = items.get(position);
        h.tvCode.setText(b.getBookingCode());
        h.badge.setStatus(b.getStatus());
        h.tvCourt.setText(b.getCourtName());
        h.tvTime.setText(h.itemView.getContext().getString(
                R.string.ticket_time_format,
                UiUtils.toDisplayDate(b.getDate()),
                b.getSlotsText()));
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onTicketClick(b); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvCode, tvCourt, tvTime;
        final StatusBadge badge;
        VH(MaterialCardView v, TextView code, StatusBadge badge, TextView court, TextView time) {
            super(v);
            this.tvCode = code; this.badge = badge; this.tvCourt = court; this.tvTime = time;
        }
    }
}
