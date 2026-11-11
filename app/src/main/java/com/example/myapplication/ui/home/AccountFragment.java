package com.example.myapplication.ui.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.User;
import com.example.myapplication.databinding.FragmentAccountBinding;
import com.example.myapplication.ui.auth.LoginActivity;
import com.example.myapplication.utils.SessionStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Tab "Tài khoản": xem/sửa thông tin cá nhân và đổi ảnh đại diện. */
public class AccountFragment extends Fragment {

    private static final Pattern PHONE = Pattern.compile("^(\\+?84|0)\\d{9,10}$");

    private FragmentAccountBinding binding;
    private boolean uploadingAvatar = false; // chống gửi 2 yêu cầu tải ảnh cùng lúc

    private final ActivityResultLauncher<PickVisualMediaRequest> pickAvatar =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) onAvatarPicked(uri);
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context c = requireContext();

        renderProfile(c);

        View.OnClickListener picker = v -> pickAvatar.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        binding.ivAvatar.setOnClickListener(picker);
        binding.btnChangeAvatar.setOnClickListener(picker);

        binding.btnEdit.setOnClickListener(v -> showEditDialog(c));
        binding.btnLogout.setOnClickListener(v -> {
            SessionStore.clear(requireContext());
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }

    private void renderProfile(Context c) {
        binding.tvName.setText(SessionStore.fullName(c));
        binding.tvStudentId.setText(getString(R.string.hint_student_id) + ": " + SessionStore.studentId(c));
        binding.tvEmail.setText(SessionStore.email(c));
        String phone = SessionStore.phone(c);
        binding.tvPhone.setText(phone.isEmpty() ? getString(R.string.hint_phone) + ": -"
                : getString(R.string.hint_phone) + ": " + phone);

        Bitmap bmp = decodeAvatar(SessionStore.avatar(c));
        if (bmp != null) {
            binding.ivAvatar.setImageBitmap(bmp);
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_badminton_court);
        }
    }

    // ---------- Ảnh đại diện ----------
    private void onAvatarPicked(Uri uri) {
        if (uploadingAvatar) return;
        Bitmap bmp = readScaledBitmap(uri, 512);
        if (bmp == null) {
            Toast.makeText(requireContext(), R.string.err_avatar_pick, Toast.LENGTH_LONG).show();
            return;
        }
        byte[] jpeg = toJpeg(bmp, 256);
        if (jpeg == null) {
            Toast.makeText(requireContext(), R.string.err_avatar_pick, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("image_base64", Base64.encodeToString(jpeg, Base64.NO_WRAP));

        uploadingAvatar = true;
        ApiClient.getService().uploadAvatar(SessionStore.userId(requireContext()), body)
                .enqueue(new Callback<User>() {
                    @Override
                    public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                        uploadingAvatar = false;
                        if (response.isSuccessful() && response.body() != null) {
                            String avatar = response.body().getAvatar();
                            SessionStore.updateAvatar(requireContext(), avatar);
                            Bitmap b = decodeAvatar(avatar);
                            if (b != null) binding.ivAvatar.setImageBitmap(b);
                            Toast.makeText(requireContext(), R.string.msg_avatar_uploaded, Toast.LENGTH_SHORT).show();
                        } else {
                            toast("Tải ảnh lên thất bại (" + response.code() + ")");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                        uploadingAvatar = false;
                        toast("Lỗi kết nối: " + t.getMessage());
                    }
                });
    }

    // ---------- Sửa thông tin ----------
    private void showEditDialog(Context c) {
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * c.getResources().getDisplayMetrics().density);
        form.setPadding(pad, (int) (8 * c.getResources().getDisplayMetrics().density), pad, 0);

        EditText etName = field(c, R.string.hint_full_name, InputType.TYPE_CLASS_TEXT);
        EditText etEmail = field(c, R.string.hint_email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText etPhone = field(c, R.string.hint_account_phone, InputType.TYPE_CLASS_PHONE);
        EditText etPw = field(c, R.string.hint_new_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        etName.setText(SessionStore.fullName(c));
        etEmail.setText(SessionStore.email(c));
        etPhone.setText(SessionStore.phone(c));

        form.addView(etName);
        form.addView(etEmail);
        form.addView(etPhone);
        form.addView(etPw);

        AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle(R.string.account_edit)
                .setView(form)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.show();

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String pw = etPw.getText().toString();

            if (name.isEmpty()) {
                etName.setError(getString(R.string.err_required));
                return;
            }
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                etPhone.setError(getString(R.string.err_phone_invalid));
                return;
            }

            User u = new User();
            u.setFullName(name);
            u.setEmail(email);
            u.setPhone(phone);
            if (!pw.isEmpty()) u.setPassword(pw);

            android.widget.Button saveBtn = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            saveBtn.setEnabled(false); // chống bấm Lưu 2 lần
            ApiClient.getService().updateUser(SessionStore.userId(c), u).enqueue(new Callback<User>() {
                @Override
                public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                    saveBtn.setEnabled(true);
                    if (response.isSuccessful()) {
                        SessionStore.updateProfile(c, name, phone, email);
                        renderProfile(c);
                        dlg.dismiss();
                        Toast.makeText(c, R.string.msg_account_saved, Toast.LENGTH_SHORT).show();
                    } else {
                        toast(extractMessage(response) + " (" + response.code() + ")");
                    }
                }

                @Override
                public void onFailure(@NonNull Call<User> call, @NonNull Throwable t) {
                    saveBtn.setEnabled(true);
                    toast("Lỗi kết nối: " + t.getMessage());
                }
            });
        });
    }

    private EditText field(Context c, int hintRes, int inputType) {
        EditText et = new EditText(c);
        et.setHint(hintRes);
        et.setInputType(inputType);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (4 * c.getResources().getDisplayMetrics().density);
        et.setLayoutParams(lp);
        return et;
    }

    // ---------- Xử lý ảnh ----------
    private Bitmap readScaledBitmap(Uri uri, int maxSize) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            int sample = 1;
            while (opts.outWidth / sample > maxSize || opts.outHeight / sample > maxSize) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            try (InputStream in2 = requireContext().getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in2, null, decode);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] toJpeg(Bitmap bmp, int maxSize) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        float scale = Math.min(1f, (float) maxSize / Math.max(w, h));
        Bitmap scaled = (scale < 1f)
                ? Bitmap.createScaledBitmap(bmp, Math.round(w * scale), Math.round(h * scale), true)
                : bmp;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out);
        return out.toByteArray();
    }

    /** Hỗ trợ cả data URI ("data:image/...;base64,...") lẫn base64 thuần. */
    private Bitmap decodeAvatar(String avatar) {
        if (avatar == null || avatar.isEmpty()) return null;
        String b64 = avatar;
        int comma = avatar.indexOf(',');
        if (avatar.startsWith("data:") && comma >= 0) {
            b64 = avatar.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                com.google.gson.JsonObject obj =
                        com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                if (obj.has("message")) return obj.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "Thao tác thất bại";
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
