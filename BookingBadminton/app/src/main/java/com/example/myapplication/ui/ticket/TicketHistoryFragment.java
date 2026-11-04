package com.example.myapplication.ui.ticket;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.ui.adapter.TicketAdapter;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;
import com.example.myapplication.viewmodel.BookingViewModel;

/** Tab "Vé của tôi" - nav_graph khai báo nhưng chưa có layout, nên dựng bằng code. */
public class TicketHistoryFragment extends Fragment {
    private BookingViewModel viewModel;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        android.content.Context c = requireContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(c, R.color.background));

        TextView title = new TextView(c);
        title.setText(R.string.nav_tickets);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
        int p = UiUtils.dp(c, 16);
        title.setPadding(p, p, p, p);
        root.addView(title);

        FrameLayout body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setClipToPadding(false);
        int pad = UiUtils.dp(c, 12);
        rv.setPadding(pad, 0, pad, pad);
        body.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = new TextView(c);
        empty.setText(R.string.empty_tickets);
        empty.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
        empty.setVisibility(View.GONE);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER);
        body.addView(empty, elp);

        TicketAdapter adapter = new TicketAdapter(b -> TicketDetailActivity.start(requireContext(), b));
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        viewModel.getMyBookings().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadMyBookings(SessionStore.userId(requireContext())); // tải lại sau khi vừa đặt sân xong
    }
}
