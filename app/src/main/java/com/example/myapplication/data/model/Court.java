package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Court implements Serializable {

    // Khớp strings: court_status_available / court_status_booked
    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_BOOKED = "booked";

    @SerializedName("id")
    private int id;

    @SerializedName("name")
    private String name;

    @SerializedName("location")
    private String location;

    @SerializedName("price_per_hour")
    private double pricePerHour;

    @SerializedName("description")
    private String description;

    @SerializedName("image_url")
    private String imageUrl;

    // "available" | "booked"
    @SerializedName("status")
    private String status;

    // Admin có thể ẩn sân ("Thêm, sửa hoặc ẩn sân")
    @SerializedName("hidden")
    private boolean hidden;

    public Court() {
    }

    public Court(int id, String name, String location, double pricePerHour,
                 String description, String imageUrl, String status, boolean hidden) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.pricePerHour = pricePerHour;
        this.description = description;
        this.imageUrl = imageUrl;
        this.status = status;
        this.hidden = hidden;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }
    public double getPrice() { return pricePerHour; }
    public void setPrice(double price) { this.pricePerHour = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isAvailable() {
        return STATUS_AVAILABLE.equalsIgnoreCase(status);
    }
}
