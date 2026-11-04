package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class Booking implements Serializable {

    // Khớp strings: status_pending / status_success / status_cancelled
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_CANCELLED = "cancelled";

    @SerializedName("id")
    private int id;

    // Mã đặt sân hiển thị trên vé (tvBookingCode)
    @SerializedName("booking_code")
    private String bookingCode;

    @SerializedName("user_id")
    private int userId;

    @SerializedName("user_name")
    private String userName;

    @SerializedName("court_id")
    private int courtId;

    @SerializedName("court_name")
    private String courtName;

    // Định dạng yyyy-MM-dd
    @SerializedName("date")
    private String date;

    @SerializedName("slot_ids")
    private List<Integer> slotIds;

    // Ví dụ: "08:00 - 09:00, 09:00 - 10:00"
    @SerializedName("slots_text")
    private String slotsText;

    // Giờ bắt đầu / kết thúc của cả lượt đặt (nếu server không trả thì tự suy ra từ slotsText)
    @SerializedName("start_time")
    private String startTime;

    @SerializedName("end_time")
    private String endTime;

    @SerializedName("total_price")
    private double totalPrice;

    // "pending" (chờ admin kiểm duyệt) | "success" | "cancelled"
    @SerializedName("status")
    private String status;

    // Nội dung sinh mã QR check-in
    @SerializedName("qr_code")
    private String qrCode;

    // Có hiệu lực đến (tvValidUntil)
    @SerializedName("valid_until")
    private String validUntil;

    public Booking() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    // Tên ngắn cho getBookingCode()
    public String getCode() { return bookingCode; }
    public void setCode(String code) { this.bookingCode = code; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public int getCourtId() { return courtId; }
    public void setCourtId(int courtId) { this.courtId = courtId; }

    public String getCourtName() { return courtName; }
    public void setCourtName(String courtName) { this.courtName = courtName; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public List<Integer> getSlotIds() { return slotIds; }
    public void setSlotIds(List<Integer> slotIds) { this.slotIds = slotIds; }

    public String getSlotsText() { return slotsText; }
    public void setSlotsText(String slotsText) { this.slotsText = slotsText; }

    public String getStartTime() {
        if (startTime != null && !startTime.isEmpty()) return startTime;
        // Suy ra từ slotsText: lấy giờ đầu của ca đầu tiên
        if (slotsText == null || slotsText.isEmpty()) return "";
        String firstSlot = slotsText.split(",")[0].trim();
        return firstSlot.split(" - ")[0].trim();
    }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() {
        if (endTime != null && !endTime.isEmpty()) return endTime;
        // Suy ra từ slotsText: lấy giờ cuối của ca cuối cùng
        if (slotsText == null || slotsText.isEmpty()) return "";
        String[] slots = slotsText.split(",");
        String[] parts = slots[slots.length - 1].trim().split(" - ");
        return parts[parts.length - 1].trim();
    }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public String getValidUntil() { return validUntil; }
    public void setValidUntil(String validUntil) { this.validUntil = validUntil; }

    // Dùng cho tvDateSlot trong màn Chi tiết vé
    public String getDateSlotText() {
        return date + " | " + slotsText;
    }

    public boolean isPending() { return STATUS_PENDING.equalsIgnoreCase(status); }
    public boolean isSuccess() { return STATUS_SUCCESS.equalsIgnoreCase(status); }
    public boolean isCancelled() { return STATUS_CANCELLED.equalsIgnoreCase(status); }
}