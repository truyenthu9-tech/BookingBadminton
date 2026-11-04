package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Slot implements Serializable {

    @SerializedName("id")
    private int id;

    @SerializedName("court_id")
    private int courtId;

    // Ví dụ: "08:00"
    @SerializedName("start_time")
    private String startTime;

    // Ví dụ: "09:00"
    @SerializedName("end_time")
    private String endTime;

    @SerializedName("booked")
    private boolean booked;

    // Trạng thái chọn phía giao diện, không map JSON
    private transient boolean selected;

    public Slot() {
    }

    public Slot(int id, int courtId, String startTime, String endTime, boolean booked) {
        this.id = id;
        this.courtId = courtId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.booked = booked;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourtId() { return courtId; }
    public void setCourtId(int courtId) { this.courtId = courtId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isBooked() { return booked; }
    public void setBooked(boolean booked) { this.booked = booked; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getTimeRange() {
        return startTime + " - " + endTime;
    }
}
