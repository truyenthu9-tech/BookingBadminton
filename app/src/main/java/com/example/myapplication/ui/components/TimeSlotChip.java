package com.example.myapplication.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.utils.UiUtils;

/** Ô ca giờ: xanh = trống, xám = đã đặt, cam = đang chọn (màu lấy từ colors.xml). */
public class TimeSlotChip extends AppCompatTextView {
    public enum State { AVAILABLE, BOOKED, SELECTED }

    public TimeSlotChip(Context context) { super(context); init(); }
    public TimeSlotChip(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setGravity(Gravity.CENTER);
        setTextSize(14);
        setTypeface(Typeface.DEFAULT_BOLD);
        setTextColor(ContextCompat.getColor(getContext(), R.color.white));
        setClickable(true);
        setFocusable(true);
    }

    public void setSlot(String label, State state) {
        setText(label);
        int colorRes;
        switch (state) {
            case BOOKED:   colorRes = R.color.slot_booked;   break;
            case SELECTED: colorRes = R.color.slot_selected; break;
            default:       colorRes = R.color.slot_available;
        }
        setBackground(UiUtils.rounded(ContextCompat.getColor(getContext(), colorRes),
                UiUtils.dp(getContext(), 10)));
        setEnabled(state != State.BOOKED);
    }
}
