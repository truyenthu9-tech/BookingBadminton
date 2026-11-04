package com.example.myapplication.ui.home;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.example.myapplication.ui.auth.LoginActivity;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;

/** Tab "Tài khoản" - nav_graph khai báo nhưng chưa có layout, nên dựng bằng code. */
public class AccountFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        android.content.Context c = requireContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(c, R.color.background));
        int p = UiUtils.dp(c, 16);
        root.setPadding(p, p, p, p);

        root.addView(text(c, getString(R.string.nav_account), 22, R.color.text_primary, true));

        TextView name = text(c, SessionStore.fullName(c), 18, R.color.text_primary, true);
        setTopMargin(name, 24);
        root.addView(name);

        TextView id = text(c, getString(R.string.hint_student_id) + ": " + SessionStore.studentId(c),
                14, R.color.text_secondary, false);
        setTopMargin(id, 4);
        root.addView(id);

        Button logout = new Button(c);
        logout.setText(R.string.btn_logout);
        logout.setTextColor(ContextCompat.getColor(c, R.color.white));
        logout.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(c, 52));
        lp.topMargin = UiUtils.dp(c, 32);
        logout.setLayoutParams(lp);
        logout.setOnClickListener(v -> {
            SessionStore.clear(requireContext());
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        root.addView(logout);
        return root;
    }

    private TextView text(android.content.Context c, String s, int sp, int colorRes, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(ContextCompat.getColor(c, colorRes));
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private void setTopMargin(View v, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(v.getContext(), dp);
        v.setLayoutParams(lp);
    }
}
