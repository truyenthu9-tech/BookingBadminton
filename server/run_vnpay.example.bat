@echo off
REM ============================================================
REM  Mẫu file chạy server kèm cấu hình VNPay (SANDBOX).
REM  Bước 1: copy file này thành run_vnpay.bat
REM  Bước 2: điền giá trị THẬT vào 2 dòng bên dưới.
REM  Bước 3: chạy run_vnpay.bat
REM  TUYỆT ĐỐI KHÔNG commit file chứa khóa thật lên git!
REM ============================================================
set VNP_TMN_CODE=YOUR_TMN_CODE
set VNP_HASH_SECRET=YOUR_HASH_SECRET

REM Return URL phải là địa chỉ mà WebView trên điện thoại/emulator
REM truy cập được tới server của bạn.
REM   - Emulator + server trên máy:  http://10.0.2.2:8000/api/payments/vnpay/return
REM   - Điện thoại thật cùng Wi-Fi:  http://<IP-may-tinh>:8000/api/payments/vnpay/return
REM   - Nếu bỏ dòng này: server tự sinh từ Host header (với ngrok sẽ dùng https).
REM set VNP_RETURN_URL=http://10.0.2.2:8000/api/payments/vnpay/return

set PORT=8000
py server.py
