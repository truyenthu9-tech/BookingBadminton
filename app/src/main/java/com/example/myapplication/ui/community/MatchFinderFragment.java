package com.example.myapplication.ui.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.Post;
import com.example.myapplication.databinding.FragmentMatchFinderBinding;
import com.example.myapplication.utils.SessionStore;
import com.example.myapplication.utils.UiUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MatchFinderFragment extends Fragment {
    private FragmentMatchFinderBinding binding;
    private PostAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentMatchFinderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new PostAdapter();
        binding.rvPosts.setAdapter(adapter);

        binding.btnPost.setOnClickListener(v -> submitPost());
        loadPosts();
    }

    private void loadPosts() {
        ApiClient.getService().getPosts().enqueue(new Callback<List<Post>>() {
            @Override
            public void onResponse(@NonNull Call<List<Post>> call, @NonNull Response<List<Post>> response) {
                if (binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setAll(response.body());
                } else {
                    toast("Không tải được bài đăng (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Post>> call, @NonNull Throwable t) {
                if (binding != null) toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void submitPost() {
        CharSequence cs = binding.etPostContent.getText();
        String content = cs == null ? "" : cs.toString().trim();
        if (content.isEmpty()) {
            toast(getString(R.string.err_required));
            return;
        }
        Post post = new Post(content);
        post.setUserId(SessionStore.userId(requireContext()));
        post.setAuthorName(SessionStore.fullName(requireContext()));

        binding.btnPost.setEnabled(false);
        ApiClient.getService().createPost(post).enqueue(new Callback<Post>() {
            @Override
            public void onResponse(@NonNull Call<Post> call, @NonNull Response<Post> response) {
                if (binding == null) return;
                binding.btnPost.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    adapter.addFirst(response.body());
                    binding.rvPosts.scrollToPosition(0);
                    binding.etPostContent.setText("");
                    toast(getString(R.string.msg_posted));
                } else {
                    toast("Đăng bài thất bại (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Post> call, @NonNull Throwable t) {
                if (binding == null) return;
                binding.btnPost.setEnabled(true);
                toast("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ---------- Adapter bài đăng (item dựng bằng code) ----------
    private static class PostAdapter extends RecyclerView.Adapter<PostAdapter.VH> {
        private final List<Post> posts = new ArrayList<>();

        void setAll(List<Post> list) {
            posts.clear();
            posts.addAll(list);
            notifyDataSetChanged();
        }

        void addFirst(Post p) {
            posts.add(0, p);
            notifyItemInserted(0);
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.content.Context c = parent.getContext();
            MaterialCardView card = new MaterialCardView(c);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = UiUtils.dp(c, 6);
            lp.setMargins(m, m, m, m);
            card.setLayoutParams(lp);
            card.setRadius(UiUtils.dp(c, 16));
            card.setCardElevation(UiUtils.dp(c, 3));
            card.setCardBackgroundColor(ContextCompat.getColor(c, R.color.surface));

            android.widget.LinearLayout col = new android.widget.LinearLayout(c);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            int p = UiUtils.dp(c, 14);
            col.setPadding(p, p, p, p);

            TextView author = new TextView(c);
            author.setTextColor(ContextCompat.getColor(c, R.color.primary_dark));
            author.setTypeface(author.getTypeface(), android.graphics.Typeface.BOLD);
            col.addView(author);

            TextView content = new TextView(c);
            content.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
            content.setTextSize(14);
            android.widget.LinearLayout.LayoutParams clp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = UiUtils.dp(c, 4);
            col.addView(content, clp);

            card.addView(col);
            return new VH(card, author, content);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Post p = posts.get(position);
            h.author.setText(p.getAuthorName());
            h.content.setText(p.getContent());
        }

        @Override public int getItemCount() { return posts.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView author, content;
            VH(MaterialCardView v, TextView author, TextView content) {
                super(v);
                this.author = author;
                this.content = content;
            }
        }
    }
}
