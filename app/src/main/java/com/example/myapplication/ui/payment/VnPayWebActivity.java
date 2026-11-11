package com.example.myapplication.ui.payment;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.databinding.ActivityVnpayWebBinding;

/**
 * Màn hình chứa WebView để người dùng thanh toán trên trang VNPay.
 *
 * - Bật JavaScript + DOM storage, loadUrl() luôn chạy trên luồng chính.
 * - Theo dõi lỗi mạng / HTTP / SSL: hiện thông báo tiếng Việt + ghi Logcat (tag VNPayWeb)
 *   kèm URL và mã lỗi để dễ chẩn đoán trang trắng.
 * - Khi WebView tải xong trang /payments/vnpay/return (server đã ghi nhận kết quả)
 *   thì tự đóng và trả RESULT_OK về màn hình gọi.
 */
public class VnPayWebActivity extends AppCompatActivity {

    private static final String TAG = "VNPayWeb";
    public static final String EXTRA_URL = "payment_url";
    public static final String RETURN_PATH = "/payments/vnpay/return";

    private ActivityVnpayWebBinding binding;
    private boolean finished = false;
    private String paymentUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVnpayWebBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        paymentUrl = getIntent().getStringExtra(EXTRA_URL);
        Log.d(TAG, "onCreate payment_url=" + paymentUrl);

        if (paymentUrl == null || paymentUrl.isEmpty()) {
            Toast.makeText(this, R.string.msg_vnpay_url_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        configureWebView();
        binding.btnClose.setOnClickListener(v -> finishWithResult(RESULT_CANCELED));
        binding.btnBrowser.setOnClickListener(v -> openInBrowser());

        // Bắt buộc loadUrl trên luồng chính
        if (Looper.myLooper() == Looper.getMainLooper()) {
            binding.webView.loadUrl(paymentUrl);
        } else {
            runOnUiThread(() -> binding.webView.loadUrl(paymentUrl));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = binding.webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // Return URL của server test có thể là http (cleartext)
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        Log.d(TAG, "WebSettings: JavaScript=" + s.getJavaScriptEnabled()
                + ", DOM storage=" + s.getDomStorageEnabled()
                + ", mixedContent=" + s.getMixedContentMode()
                + ", userAgent=" + s.getUserAgentString());

        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Một số trang ngân hàng mở popup: chuyển popup về chính WebView hiện tại
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(binding.webView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.d(TAG, "progress=" + newProgress);
                if (newProgress >= 100 && binding != null) {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, "JS console: " + cm.message()
                        + " (" + cm.sourceId() + ":" + cm.lineNumber() + ")");
                return true;
            }
        });

        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + (request == null ? "?" : request.getUrl()));
                return false; // luôn tải trong WebView để server nhận request xử lý thanh toán
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "onPageStarted: " + url);
                binding.progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Log tiêu đề trang: nếu rỗng thì khả năng cao là trang trắng
                Log.d(TAG, "onPageFinished: " + url + " | title=" + view.getTitle());
                binding.progressBar.setVisibility(View.GONE);
                if (url != null && url.contains(RETURN_PATH)) {
                    finishWithResult(RESULT_OK);
                }
            }

            // Lỗi tải trang (API 23+)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null) return;
                int code = error == null ? -1 : error.getErrorCode();
                CharSequence desc = error == null ? null : error.getDescription();
                Log.e(TAG, "onReceivedError: url=" + request.getUrl() + " code=" + code
                        + " desc=" + desc + " mainFrame=" + request.isForMainFrame());
                if (!request.isForMainFrame()) return; // lỗi ảnh/css con không chặn cả trang
                binding.progressBar.setVisibility(View.GONE);
                showError("Không tải được trang thanh toán: " + describeNetworkError(code)
                        + ".\nMã lỗi: " + code);
            }

            // Bản cũ, giữ để tương thích các bản WebView cũ
            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "onReceivedError(old): url=" + failingUrl + " code=" + errorCode
                        + " desc=" + description);
                showError("Không tải được trang thanh toán: " + describeNetworkError(errorCode)
                        + ".\nMã lỗi: " + errorCode);
            }

            // Lỗi HTTP (4xx/5xx)
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request == null || errorResponse == null) return;
                int status = errorResponse.getStatusCode();
                String reason = errorResponse.getReasonPhrase();
                Log.e(TAG, "onReceivedHttpError: url=" + request.getUrl() + " status=" + status
                        + " reason=" + reason + " mainFrame=" + request.isForMainFrame());
                if (!request.isForMainFrame()) return;
                binding.progressBar.setVisibility(View.GONE);
                showError("Trang thanh toán trả về lỗi HTTP " + status
                        + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")") + ".\n"
                        + request.getUrl());
            }

            // Lỗi chứng chỉ SSL: không bỏ qua, báo rõ
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String url = error == null ? "?" : error.getUrl();
                int primary = error == null ? -1 : error.getPrimaryError();
                Log.e(TAG, "onReceivedSslError: url=" + url + " primaryError=" + primary);
                handler.cancel();
                binding.progressBar.setVisibility(View.GONE);
                showError("Lỗi chứng chỉ bảo mật (SSL) khi tải trang thanh toán.\nURL: " + url
                        + "\nMã lỗi SSL: " + primary);
            }
        });
    }

    /** Mô tả mã lỗi mạng của WebView bằng tiếng Việt. */
    private String describeNetworkError(int code) {
        switch (code) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                return "không phân giải được tên miền (DNS), hãy kiểm tra Wi-Fi/4G";
            case WebViewClient.ERROR_CONNECT:
                return "không kết nối được tới máy chủ thanh toán (có thể do mạng)";
            case WebViewClient.ERROR_TIMEOUT:
                return "hết thời gian chờ kết nối";
            case WebViewClient.ERROR_IO:
                return "mất kết nối mạng khi đang tải";
            case WebViewClient.ERROR_UNSUPPORTED_SCHEME:
                return "giao thức của đường dẫn không được hỗ trợ";
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return "bắt tay SSL thất bại";
            case WebViewClient.ERROR_REDIRECT_LOOP:
                return "bị vòng lặp chuyển hướng";
            case WebViewClient.ERROR_BAD_URL:
                return "đường dẫn thanh toán không hợp lệ";
            default:
                return "lỗi mạng không xác định";
        }
    }

    private void showError(String message) {
        if (binding == null) return;
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Mở payment_url bằng trình duyệt ngoài (dùng khi WebView không tải được). */
    private void openInBrowser() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private void finishWithResult(int resultCode) {
        if (finished) return;
        finished = true;
        setResult(resultCode);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Người dùng hủy giữa chừng: đơn vẫn giữ trạng thái PENDING ở server
        finishWithResult(RESULT_CANCELED);
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            binding.webView.stopLoading();
            binding.webView.destroy();
        }
        super.onDestroy();
        binding = null;
    }
}
