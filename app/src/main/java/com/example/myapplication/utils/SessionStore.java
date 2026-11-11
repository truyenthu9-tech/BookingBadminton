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

    /** Lưu đầy đủ thông tin người dùng sau khi đăng nhập. */
    public static void saveFull(Context c, String fullName, String studentId,
                                String phone, String email, String avatar, boolean admin) {
        sp(c).edit().putString("full_name", fullName)
                .putString("student_id", studentId)
                .putString("phone", phone == null ? "" : phone)
                .putString("email", email == null ? "" : email)
                .putString("avatar", avatar == null ? "" : avatar)
                .putBoolean("admin", admin).apply();
    }

    public static String fullName(Context c) { return sp(c).getString("full_name", ""); }
    public static String studentId(Context c) { return sp(c).getString("student_id", ""); }
    public static String phone(Context c) { return sp(c).getString("phone", ""); }
    public static String email(Context c) { return sp(c).getString("email", ""); }
    public static String avatar(Context c) { return sp(c).getString("avatar", ""); }
    public static boolean isAdmin(Context c) { return sp(c).getBoolean("admin", false); }

    /** Token xác thực do server cấp khi đăng nhập. */
    public static void saveToken(Context c, String token) {
        sp(c).edit().putString("token", token == null ? "" : token).apply();
    }

    public static String token(Context c) { return sp(c).getString("token", ""); }

    public static void saveUserId(Context c, int userId) {
        sp(c).edit().putInt("user_id", userId).apply();
    }

    /** Cập nhật các trường có thể sửa ở màn Tài khoản (không cần đăng nhập lại). */
    public static void updateProfile(Context c, String fullName, String phone, String email) {
        sp(c).edit().putString("full_name", fullName)
                .putString("phone", phone == null ? "" : phone)
                .putString("email", email == null ? "" : email).apply();
    }

    public static void updateAvatar(Context c, String avatar) {
        sp(c).edit().putString("avatar", avatar == null ? "" : avatar).apply();
    }

    public static int userId(Context c) { return sp(c).getInt("user_id", 0); }

    // Chỉ coi là đã đăng nhập khi có userId từ server
    public static boolean isLoggedIn(Context c) { return userId(c) > 0; }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
