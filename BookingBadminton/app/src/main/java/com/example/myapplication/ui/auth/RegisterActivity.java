package com.example.myapplication.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.User;
import com.example.myapplication.databinding.ActivityRegisterBinding;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = text(binding.etFullName.getText());
        String id = text(binding.etStudentId.getText());
        String email = text(binding.etEmail.getText());
        String pw = text(binding.etPassword.getText());
        String confirm = text(binding.etConfirmPassword.getText());

        boolean ok = true;
        ok &= setError(binding.tilFullName, name.isEmpty() ? getString(R.string.err_required) : null);
        ok &= setError(binding.tilStudentId, id.isEmpty() ? getString(R.string.err_required) : null);
        ok &= setError(binding.tilEmail, email.isEmpty() ? getString(R.string.err_required)
                : !Patterns.EMAIL_ADDRESS.matcher(email).matches() ? getString(R.string.err_email_invalid) : null);
        ok &= setError(binding.tilPassword, pw.isEmpty() ? getString(R.string.err_required)
                : pw.length() < 6 ? getString(R.string.err_password_short) : null);
        ok &= setError(binding.tilConfirmPassword, !confirm.equals(pw) ? getString(R.string.err_password_mismatch) : null);
        if (!ok) return;

        binding.btnRegister.setEnabled(false);
        ApiClient.getService().register(new User(name, id, email, pw)).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                binding.btnRegister.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, R.string.msg_register_success, Toast.LENGTH_LONG).show();
                    finish(); // quay lại màn đăng nhập
                } else if (response.code() == 409) {
                    binding.tilStudentId.setError("Mã sinh viên đã tồn tại");
                } else {
                    Toast.makeText(RegisterActivity.this, "Đăng ký thất bại (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                binding.btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Không kết nối được server: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** @return true nếu không có lỗi. */
    private boolean setError(TextInputLayout til, String msg) {
        til.setError(msg);
        return msg == null;
    }

    private static String text(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
