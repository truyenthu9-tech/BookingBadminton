package com.example.myapplication.ui.admin;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.ui.adapter.CourtAdapter;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.HomeViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Quản lý sân: danh sách + thêm (nút +), bấm vào sân để sửa hoặc ẩn/hiện. */
public class ManageCourtsActivity extends AppCompatActivity {
    private HomeViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.admin_manage_courts);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        int p = UiUtils.dp(this, 16);
        title.setPadding(p, p, p, p);
        column.addView(title);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(UiUtils.dp(this, 12), 0, UiUtils.dp(this, 12), UiUtils.dp(this, 88));
        column.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FloatingActionButton fab = new FloatingActionButton(this);
        fab.setImageResource(android.R.drawable.ic_input_add);
        fab.setContentDescription(getString(R.string.cd_add_court));
        fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary)));
        fab.setColorFilter(ContextCompat.getColor(this, R.color.white));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        int m = UiUtils.dp(this, 16);
        fl.setMargins(m, m, m, m);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(fab, fl);
        setContentView(root);

        CourtAdapter adapter = new CourtAdapter(this::showCourtActions);
        rv.setAdapter(adapter);
        fab.setOnClickListener(v -> showCourtForm(null));

        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        vm.getCourts().observe(this, adapter::submit);
        vm.getError().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        // HomeViewModel tự tải danh sách sân khi khởi tạo
    }

    // ---------- Bấm vào sân: sửa hoặc ẩn/hiện ----------
    private void showCourtActions(Court court) {
        String[] items = {
                getString(R.string.action_edit),
                getString(court.isHidden() ? R.string.action_show : R.string.action_hide)};
        new AlertDialog.Builder(this)
                .setTitle(court.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) showCourtForm(court);
                    else toggleHidden(court);
                })
                .show();
    }

    private void toggleHidden(Court court) {
        boolean newHidden = !court.isHidden();
        ApiClient.getService().setCourtHidden(court.getId(), newHidden).enqueue(new Callback<Court>() {
            @Override
            public void onResponse(@NonNull Call<Court> c, @NonNull Response<Court> r) {
                if (r.isSuccessful()) {
                    Toast.makeText(ManageCourtsActivity.this,
                            newHidden ? R.string.msg_court_hidden : R.string.msg_court_shown,
                            Toast.LENGTH_SHORT).show();
                    vm.refresh();
                } else {
                    toast("Thao tác thất bại (" + r.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Court> c, @NonNull Throwable t) {
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // ---------- Form thêm / sửa sân ----------
    /** court = null: thêm mới; khác null: sửa sân đó. */
    private void showCourtForm(Court court) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 20);
        form.setPadding(pad, UiUtils.dp(this, 8), pad, 0);

        EditText etName = field(R.string.hint_court_name, InputType.TYPE_CLASS_TEXT);
        EditText etLoc = field(R.string.hint_court_location, InputType.TYPE_CLASS_TEXT);
        EditText etPrice = field(R.string.hint_court_price, InputType.TYPE_CLASS_NUMBER);
        EditText etDesc = field(R.string.hint_court_desc,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (court != null) {
            etName.setText(court.getName());
            etLoc.setText(court.getLocation());
            etPrice.setText(String.valueOf((long) court.getPricePerHour()));
            etDesc.setText(court.getDescription());
        }
        form.addView(etName);
        form.addView(etLoc);
        form.addView(etPrice);
        form.addView(etDesc);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(court == null ? R.string.court_form_add : R.string.court_form_edit)
                .setView(form)
                .setPositiveButton(R.string.btn_save, null) // gán listener bên dưới để không tự đóng khi nhập sai
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.show();

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError(getString(R.string.err_required));
                return;
            }
            double price;
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                etPrice.setError(getString(R.string.err_price_invalid));
                return;
            }
            if (price <= 0) {
                etPrice.setError(getString(R.string.err_price_invalid));
                return;
            }

            Court target = court != null ? court : new Court();
            target.setName(name);
            target.setLocation(etLoc.getText().toString().trim());
            target.setPricePerHour(price);
            target.setDescription(etDesc.getText().toString().trim());
            saveCourt(target, court == null, dlg);
        });
    }

    private void saveCourt(Court court, boolean isNew, AlertDialog dlg) {
        Call<Court> call = isNew
                ? ApiClient.getService().createCourt(court)
                : ApiClient.getService().updateCourt(court.getId(), court);
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); // chống bấm Lưu 2 lần
        call.enqueue(new Callback<Court>() {
            @Override
            public void onResponse(@NonNull Call<Court> c, @NonNull Response<Court> r) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if (r.isSuccessful()) {
                    dlg.dismiss();
                    Toast.makeText(ManageCourtsActivity.this, R.string.msg_court_saved, Toast.LENGTH_SHORT).show();
                    vm.refresh();
                } else {
                    toast("Lưu thất bại (" + r.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Court> c, @NonNull Throwable t) {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private EditText field(int hintRes, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hintRes);
        et.setInputType(inputType);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(this, 4);
        et.setLayoutParams(lp);
        return et;
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
