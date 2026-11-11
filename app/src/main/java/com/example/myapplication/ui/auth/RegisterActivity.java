package com.example.myapplication.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.User;
import com.example.myapplication.databinding.ActivityRegisterBinding;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;

    // Số điện thoại Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx / 84xxxxxxxxx
    private static final Pattern PHONE = Pattern.compile("^(\\+?84|0)\\d{9,10}$");
    private static final long OTP_TTL_MS = 120_000L; // 2 phút

    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnSendOtp.setOnClickListener(v -> sendOtp());
        binding.tvGoToLogin.setOnClickListener(v -> finish());
    }

    /** Bước 1: gửi mã OTP về số điện thoại đã nhập. */
    private void sendOtp() {
        String phone = text(binding.etPhone.getText());
        if (!PHONE.matcher(phone).matches()) {
            binding.tilPhone.setError(getString(R.string.err_phone_invalid));
            return;
        }
        binding.tilPhone.setError(null);
        binding.btnSendOtp.setEnabled(false);

        Map<String, String> body = new HashMap<>();
        body.put("phone", phone);
        ApiClient.getService().sendOtp(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, R.string.msg_otp_sent, Toast.LENGTH_LONG).show();
                    binding.etOtp.requestFocus();
                    startCountdown();
                } else {
                    binding.btnSendOtp.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, errorMessage(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                binding.btnSendOtp.setEnabled(true);
                Toast.makeText(RegisterActivity.this,
                        "Không kết nối được server: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Đếm ngược 2 phút, hết giờ mới cho gửi lại mã. */
    private void startCountdown() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(OTP_TTL_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = millisUntilFinished / 1000;
                binding.btnSendOtp.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
                binding.btnSendOtp.setEnabled(false);
            }

            @Override
            public void onFinish() {
                binding.btnSendOtp.setText(R.string.btn_resend_otp);
                binding.btnSendOtp.setEnabled(true);
            }
        };
        timer.start();
    }

    /** Bước 2: đăng ký (server sẽ xác thực lại OTP trong vòng 2 phút). */
    private void attemptRegister() {
        String name = text(binding.etFullName.getText());
        String id = text(binding.etStudentId.getText());
        String email = text(binding.etEmail.getText());
        String phone = text(binding.etPhone.getText());
        String otp = text(binding.etOtp.getText());
        String pw = text(binding.etPassword.getText());
        String confirm = text(binding.etConfirmPassword.getText());

        boolean ok = true;
        ok &= setError(binding.tilFullName, name.isEmpty() ? getString(R.string.err_required) : null);
        ok &= setError(binding.tilStudentId, id.isEmpty() ? getString(R.string.err_required) : null);
        ok &= setError(binding.tilEmail, email.isEmpty() ? getString(R.string.err_required)
                : !Patterns.EMAIL_ADDRESS.matcher(email).matches() ? getString(R.string.err_email_invalid) : null);
        ok &= setError(binding.tilPhone, phone.isEmpty() ? getString(R.string.err_required)
                : !PHONE.matcher(phone).matches() ? getString(R.string.err_phone_invalid) : null);
        ok &= setError(binding.tilPassword, pw.isEmpty() ? getString(R.string.err_required)
                : pw.length() < 6 ? getString(R.string.err_password_short) : null);
        ok &= setError(binding.tilConfirmPassword, !confirm.equals(pw) ? getString(R.string.err_password_mismatch) : null);
        ok &= setError(binding.tilOtp, otp.isEmpty() ? getString(R.string.err_otp_required) : null);
        if (!ok) return;

        binding.btnRegister.setEnabled(false);
        ApiClient.getService().register(new User(name, id, email, phone, pw, otp))
                .enqueue(new Callback<User>() {
                    @Override
                    public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                        binding.btnRegister.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, R.string.msg_register_success, Toast.LENGTH_LONG).show();
                            finish(); // quay lại màn đăng nhập
                            return;
                        }
                        String msg = errorMessage(response);
                        if (response.code() == 409 && msg.contains("sinh viên")) {
                            binding.tilStudentId.setError(msg);
                        } else if (response.code() == 409 && msg.contains("điện thoại")) {
                            binding.tilPhone.setError(msg);
                        } else if (response.code() == 400) {
                            binding.tilOtp.setError(msg);
                        } else {
                            Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                        binding.btnRegister.setEnabled(true);
                        Toast.makeText(RegisterActivity.this,
                                "Không kết nối được server: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Lấy thông báo "message" từ body lỗi JSON của server. */
    private String errorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                if (obj.has("message")) return obj.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "Yêu cầu thất bại (" + response.code() + ")";
    }

    /** @return true nếu không có lỗi. */
    private boolean setError(TextInputLayout til, String msg) {
        til.setError(msg);
        return msg == null;
    }

    private static String text(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
