package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Một cột dữ liệu cho biểu đồ thống kê (Dashboard).
 * - label: nhãn trục X (ngày / tuần / tháng / tên sân)
 * - value: giá trị cột (doanh thu bằng VND, hoặc tỉ lệ % thành công)
 * - extra: thông tin phụ (ví dụ "3/4" = 3 thành công trên 4 lượt đặt)
 */
public class StatItem {

    @SerializedName("label")
    private String label;

    @SerializedName("value")
    private double value;

    @SerializedName("extra")
    private String extra;

    public StatItem() {
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
