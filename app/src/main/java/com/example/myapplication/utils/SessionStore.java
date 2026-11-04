package com.example.myapplication.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Lưu thông tin đăng nhập tối giản bằng SharedPreferences. */
public final class SessionStore {
    private static final String PREF = "session";
    private SessionStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String fullName, String studentId, boolean admin) {
        sp(c).edit().putString("full_name", fullName)
                .putString("student_id", studentId)
                .putBoolean("admin", admin).apply();
    }

    public static String fullName(Context c) { return sp(c).getString("full_name", ""); }
    public static String studentId(Context c) { return sp(c).getString("student_id", ""); }
    public static boolean isAdmin(Context c) { return sp(c).getBoolean("admin", false); }
    public static void saveUserId(Context c, int userId) {
        sp(c).edit().putInt("user_id", userId).apply();
    }

    public static int userId(Context c) { return sp(c).getInt("user_id", 0); }

    // Chỉ coi là đã đăng nhập khi có userId từ server
    public static boolean isLoggedIn(Context c) { return userId(c) > 0; }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
