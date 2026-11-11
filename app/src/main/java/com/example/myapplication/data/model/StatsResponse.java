package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Kết quả trả về từ các endpoint thống kê Dashboard: {"items":[...]}. */
public class StatsResponse {

    @SerializedName("items")
    private List<StatItem> items;

    public StatsResponse() {
    }

    public List<StatItem> getItems() { return items; }
    public void setItems(List<StatItem> items) { this.items = items; }
}
