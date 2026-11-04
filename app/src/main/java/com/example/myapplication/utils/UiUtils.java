package com.example.myapplication.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Các hàm tiện ích dùng chung cho tầng UI. */
public final class UiUtils {
    private UiUtils() {}

    public static int dp(Context c, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    /** 80000 -> "80.000 đ" */
    public static String formatPrice(double price) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        return nf.format(price) + " đ";
    }

    /** "2026-11-05" -> "05/11/2026". Nếu parse lỗi trả lại chuỗi gốc. */
    public static String toDisplayDate(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }
}
