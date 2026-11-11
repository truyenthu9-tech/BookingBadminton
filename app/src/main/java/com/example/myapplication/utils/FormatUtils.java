package com.example.myapplication.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FormatUtils {

    private static final String API_DATE_PATTERN = "yyyy-MM-dd";

    private FormatUtils() {
    }

    /** 150000 -> "150.000đ" */
    public static String formatPrice(double price) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,###", symbols).format(price) + "đ";
    }

    /** Ngày hôm nay + offset ngày, định dạng yyyy-MM-dd (dùng gọi API). */
    public static String apiDate(int offsetDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat(API_DATE_PATTERN, Locale.US).format(cal.getTime());
    }

    /** "2026-11-05" -> "05/11/2026" (dùng hiển thị). */
    public static String displayDate(String apiDate) {
        if (apiDate == null) return "";
        try {
            Date d = new SimpleDateFormat(API_DATE_PATTERN, Locale.US).parse(apiDate);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d);
        } catch (ParseException e) {
            return apiDate;
        }
    }
}
