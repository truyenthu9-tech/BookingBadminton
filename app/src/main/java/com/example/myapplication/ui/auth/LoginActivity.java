package com.example.myapplication.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.User;
import com.example.myapplication.databinding.ActivityLoginBinding;
import com.example.myapplication.ui.admin.AdminDashboardActivity;
import com.example.myapplication.utils.SessionStore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionStore.isLoggedIn(this)) { openHome(); return; }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String id = text(binding.etStudentId.getText());
        String pw = text(binding.etPassword.getText());

        binding.tilStudentId.setError(id.isEmpty() ? getString(R.string.err_required) : null);
        binding.tilPassword.setError(pw.isEmpty() ? getString(R.string.err_required) : null);
        if (id.isEmpty() || pw.isEmpty()) return;

        binding.btnLogin.setEnabled(false);
        ApiClient.getService().login(new User(id, pw)).enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                binding.btnLogin.setEnabled(true);
                User u = response.body();
                if (response.isSuccessful() && u != null) {
                    SessionStore.saveFull(LoginActivity.this, u.getFullName(), u.getStudentId(),
                            u.getPhone(), u.getEmail(), u.getAvatar(), u.isAdmin());
                    SessionStore.saveUserId(LoginActivity.this, u.getId());
                    SessionStore.saveToken(LoginActivity.this, u.getToken());
                    openHome();
                } else if (response.code() == 401) {
                    Toast.makeText(LoginActivity.this, "Sai mã sinh viên hoặc mật khẩu", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this, "Đăng nhập thất bại (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                binding.btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Không kết nối được server: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openHome() {
        Class<?> target = SessionStore.isAdmin(this) ? AdminDashboardActivity.class : MainActivity.class;
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private static String text(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
