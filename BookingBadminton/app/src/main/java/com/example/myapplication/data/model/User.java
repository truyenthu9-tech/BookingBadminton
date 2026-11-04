package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class User implements Serializable {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    @SerializedName("id")
    private int id;

    @SerializedName("full_name")
    private String fullName;

    // Mã sinh viên / giảng viên - dùng để đăng nhập
    @SerializedName("student_id")
    private String studentId;

    @SerializedName("email")
    private String email;

    // Chỉ dùng khi gửi request đăng nhập / đăng ký
    @SerializedName("password")
    private String password;

    @SerializedName("role")
    private String role;

    @SerializedName("token")
    private String token;

    public User() {
    }

    // Dùng cho đăng nhập
    public User(String studentId, String password) {
        this.studentId = studentId;
        this.password = password;
    }

    // Dùng cho đăng ký
    public User(String fullName, String studentId, String email, String password) {
        this.fullName = fullName;
        this.studentId = studentId;
        this.email = email;
        this.password = password;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }
}
