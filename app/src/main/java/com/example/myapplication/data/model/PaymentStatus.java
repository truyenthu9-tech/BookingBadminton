package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

/** Trạng thái thanh toán của một đơn: UNPAID / PENDING / PAID / FAILED. */
public class PaymentStatus {

    public static final String UNPAID = "UNPAID";
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";

    @SerializedName("booking_id")
    private int bookingId;

    @SerializedName("payment_status")
    private String paymentStatus;

    public PaymentStatus() {
    }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public boolean isPaid() { return PAID.equalsIgnoreCase(paymentStatus); }
}
