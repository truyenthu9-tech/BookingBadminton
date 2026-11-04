# Phần UI - Đặt sân cầu lông HaUI

Copy thư mục `app/src/main/...` vào dự án (giữ nguyên package com.example.myapplication).

## build.gradle (module app)
android { buildFeatures { viewBinding true } }
dependencies {
  implementation "com.google.android.material:material:1.12.0"
  implementation "androidx.constraintlayout:constraintlayout:2.1.4"
  implementation "androidx.navigation:navigation-fragment:2.7.7"
  implementation "androidx.navigation:navigation-ui:2.7.7"
  implementation "androidx.lifecycle:lifecycle-viewmodel:2.8.4"
  implementation "androidx.lifecycle:lifecycle-livedata:2.8.4"
  implementation "com.google.zxing:core:3.5.3"
}

## AndroidManifest
- Khai báo: LoginActivity (LAUNCHER), RegisterActivity, MainActivity, CourtDetailActivity,
  PaymentActivity, TicketDetailActivity, AdminDashboardActivity, ManageCourtsActivity
- <uses-permission android:name="android.permission.INTERNET"/>
- android:theme="@style/Theme.BadmintonBooking"

## API tầng data mà UI đang gọi (sửa tên cho khớp nếu code của bạn khác)
Court:   getId() int, getName(), getLocation(), getPrice() double, getDescription(), isAvailable()
Slot:    getId() int, getStartTime(), getEndTime(), isBooked()
Booking: getCode(), getCourtName(), getDate() "yyyy-MM-dd", getStartTime(), getEndTime(), getStatus() PENDING|SUCCESS|CANCELLED
HomeViewModel:    LiveData<List<Court>> getCourts(); LiveData<Boolean> getLoading(); LiveData<String> getError(); loadCourts(String date)
BookingViewModel: LiveData<Court> getCourt(); LiveData<List<Slot>> getSlots(); LiveData<Booking> getBooking();
                  LiveData<List<Booking>> getMyBookings(); LiveData<List<Booking>> getPendingBookings();
                  LiveData<Boolean> getLoading(); LiveData<String> getError();
                  loadCourtDetail(int courtId, String date); createBooking(int courtId, int slotId, String date);
                  loadMyBookings(); loadPendingBookings()
