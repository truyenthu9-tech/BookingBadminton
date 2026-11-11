package com.example.myapplication.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.example.myapplication.R;

import java.util.Locale;

/** Chip hiển thị trạng thái giao dịch: PENDING / SUCCESS / CANCELLED / EXPIRED. */
public class StatusBadge extends Chip {
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    public StatusBadge(Context context) { super(context); }
    public StatusBadge(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setStatus(String status) { apply(this, status); }

    /** Dùng được cho cả Chip thường trong layout (vd. chipStatus ở activity_ticket_detail). */
    public static void apply(Chip chip, String status) {
        int textRes = R.string.status_pending;
        int colorRes = R.color.status_pending;
        String s = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (SUCCESS.equals(s)) {
            textRes = R.string.status_success;
            colorRes = R.color.status_success;
        } else if (CANCELLED.equals(s)) {
            textRes = R.string.status_cancelled;
            colorRes = R.color.status_cancelled;
        } else if (EXPIRED.equals(s)) {
            textRes = R.string.status_expired;
            colorRes = R.color.slot_booked;
        }
        Context c = chip.getContext();
        chip.setText(textRes);
        chip.setTextColor(ContextCompat.getColor(c, R.color.white));
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(c, colorRes)));
    }
}
