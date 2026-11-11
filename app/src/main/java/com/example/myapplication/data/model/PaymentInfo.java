package com.example.myapplication.data.model;

import com.google.gson.annotations.SerializedName;

/** Kết quả tạo giao dịch VNPay: URL thanh toán + mã tham chiếu. */
public class PaymentInfo {

    @SerializedName("payment_url")
    private String paymentUrl;

    @SerializedName("txn_ref")
    private String txnRef;

    public PaymentInfo() {
    }

    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }

    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
}
