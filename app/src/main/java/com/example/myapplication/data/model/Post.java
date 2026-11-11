package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

// Bài đăng "Tìm hội chơi" (MatchFinderFragment - rvPosts)
public class Post implements Serializable {

    @SerializedName("id")
    private int id;

    @SerializedName("user_id")
    private int userId;

    @SerializedName("author_name")
    private String authorName;

    @SerializedName("content")
    private String content;

    @SerializedName("created_at")
    private String createdAt;

    public Post() {
    }

    public Post(String content) {
        this.content = content;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
