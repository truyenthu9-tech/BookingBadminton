package com.example.myapplication.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.model.Booking;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.data.model.PaymentInfo;
import com.example.myapplication.data.model.PaymentStatus;
import com.example.myapplication.data.model.Slot;
import com.example.myapplication.data.repository.CourtRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dùng chung cho 3 màn: CourtDetailActivity (chọn ca),
 * PaymentActivity (gửi yêu cầu thanh toán) và TicketDetailActivity (xem vé).
 * Mỗi Activity có một instance riêng, dữ liệu truyền qua Intent (Court, Booking là Serializable).
 */
public class BookingViewModel extends ViewModel {
    private final MutableLiveData<List<Booking>> pendingBookings = new MutableLiveData<>();
    private final MutableLiveData<List<Booking>> myBookings = new MutableLiveData<>();

    public LiveData<List<Booking>> getPendingBookings() { return pendingBookings; }
    public LiveData<List<Booking>> getMyBookings() { return myBookings; }

    /** Admin: hóa đơn chờ kiểm duyệt. */
    public void loadPendingBookings() {
        loadList(ApiClient.getService().getBookingsByStatus(Booking.STATUS_PENDING), pendingBookings);
    }

    /** Sinh viên: vé của tôi. */
    public void loadMyBookings(int userId) {
        loadList(ApiClient.getService().getBookingsByUser(userId), myBookings);
    }

    private void loadList(Call<List<Booking>> call, MutableLiveData<List<Booking>> target) {
        loading.setValue(true);
        call.enqueue(new Callback<List<Booking>>() {
            @Override
            public void onResponse(@NonNull Call<List<Booking>> c, @NonNull Response<List<Booking>> r) {
                loading.setValue(false);
                if (r.isSuccessful() && r.body() != null) {
                    target.setValue(r.body());
                } else {
                    error.setValue("Không tải được danh sách (" + r.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Booking>> c, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    private static class SlotQuery {
        final int courtId;
        final String date;

        SlotQuery(int courtId, String date) {
            this.courtId = courtId;
            this.date = date;
        }
    }


    private final CourtRepository courtRepository = new CourtRepository();

    // ----- Chi tiết sân & ca giờ -----
    private final MutableLiveData<Court> court = new MutableLiveData<>();
    private final MutableLiveData<SlotQuery> slotQuery = new MutableLiveData<>();
    private final LiveData<List<Slot>> slots;
    private final MutableLiveData<List<Slot>> selectedSlots = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Double> totalPrice = new MutableLiveData<>(0d);
    private String date;

    // ----- Thanh toán / vé -----
    private final MutableLiveData<Booking> booking = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    // ----- Thanh toán VNPay -----
    private final MutableLiveData<String> paymentUrl = new MutableLiveData<>();
    private final MutableLiveData<PaymentStatus> paymentStatus = new MutableLiveData<>();

    public BookingViewModel() {
        slots = Transformations.switchMap(slotQuery, q -> {
            loading.setValue(true);
            return Transformations.map(courtRepository.getSlots(q.courtId, q.date), list -> {
                loading.setValue(false);
                if (list == null) {
                    error.setValue("Không tải được danh sách ca giờ");
                }
                return list;
            });
        });
    }

    // =============== CourtDetailActivity ===============

    /** Gọi khi mở màn chi tiết sân. court lấy từ Intent, date dạng yyyy-MM-dd. */
    public void init(Court court, String date) {
        this.date = date;
        this.court.setValue(court);
        reloadSlots();
    }
    public void loadCourtDetail(int courtId, String date) {
        this.date = date;
        loading.setValue(true);
        ApiClient.getService().getCourtDetail(courtId).enqueue(new Callback<Court>() {
            @Override
            public void onResponse(@NonNull Call<Court> call, @NonNull Response<Court> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    court.setValue(response.body());
                    reloadSlots();
                } else {
                    error.setValue("Không tải được thông tin sân (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Court> call, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }
    public void reloadSlots() {
        Court c = court.getValue();
        if (c == null || date == null) return;
        selectedSlots.setValue(new ArrayList<>());
        totalPrice.setValue(0d);
        slotQuery.setValue(new SlotQuery(c.getId(), date));
    }

    /** Bấm vào một ca: bỏ qua nếu đã có người đặt. */
    public void toggleSlot(Slot slot) {
        if (slot == null || slot.isBooked()) return;
        slot.setSelected(!slot.isSelected());
        recalculate();
    }

    private void recalculate() {
        List<Slot> all = slots.getValue();
        List<Slot> chosen = new ArrayList<>();
        if (all != null) {
            for (Slot s : all) {
                if (s.isSelected()) chosen.add(s);
            }
        }
        selectedSlots.setValue(chosen);
        Court c = court.getValue();
        // Mỗi ca = 1 giờ
        totalPrice.setValue(c == null ? 0d : c.getPricePerHour() * chosen.size());
    }

    public boolean hasSelection() {
        List<Slot> chosen = selectedSlots.getValue();
        return chosen != null && !chosen.isEmpty();
    }

    /** Tạo bản nháp booking để truyền sang PaymentActivity qua Intent. */
    public Booking buildDraft(int userId) {
        Court c = court.getValue();
        List<Slot> chosen = selectedSlots.getValue();
        if (c == null || chosen == null || chosen.isEmpty()) return null;

        List<Integer> ids = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Slot s : chosen) {
            ids.add(s.getId());
            if (text.length() > 0) text.append(", ");
            text.append(s.getTimeRange());
        }

        Booking b = new Booking();
        b.setUserId(userId);
        b.setCourtId(c.getId());
        b.setCourtName(c.getName());
        b.setDate(date);
        b.setSlotIds(ids);
        b.setSlotsText(text.toString());
        b.setTotalPrice(c.getPricePerHour() * chosen.size());
        b.setStatus(Booking.STATUS_PENDING);
        return b;
    }
    /** Tạo yêu cầu đặt 1 ca từ dữ liệu màn Thanh toán, trạng thái chờ admin duyệt. */
    public void createBooking(int courtId, int slotId, String date) {
        List<Integer> ids = new ArrayList<>();
        ids.add(slotId);

        Booking draft = new Booking();
        draft.setCourtId(courtId);
        draft.setDate(date);
        draft.setSlotIds(ids);
        draft.setStatus(Booking.STATUS_PENDING);

        submitBooking(draft);
    }
    // =============== PaymentActivity ===============

    /** Gửi yêu cầu thanh toán -> server tạo booking "pending" chờ admin duyệt. */
    public void submitBooking(Booking draft) {
        if (draft == null) return;
        loading.setValue(true);
        ApiClient.getService().createBooking(draft).enqueue(new Callback<Booking>() {
            @Override
            public void onResponse(@NonNull Call<Booking> call, @NonNull Response<Booking> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    booking.setValue(response.body());
                } else {
                    error.setValue("Gửi yêu cầu thanh toán thất bại (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Booking> call, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // =============== TicketDetailActivity ===============

    public void loadBooking(int bookingId) {
        loading.setValue(true);
        ApiClient.getService().getBooking(bookingId).enqueue(new Callback<Booking>() {
            @Override
            public void onResponse(@NonNull Call<Booking> call, @NonNull Response<Booking> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    booking.setValue(response.body());
                } else {
                    error.setValue("Không tải được vé (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<Booking> call, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    // =============== Getters ===============

    public LiveData<Court> getCourt() { return court; }

    public LiveData<List<Slot>> getSlots() { return slots; }

    public LiveData<List<Slot>> getSelectedSlots() { return selectedSlots; }

    public LiveData<Double> getTotalPrice() { return totalPrice; }

    public LiveData<Booking> getBooking() { return booking; }

    public LiveData<Boolean> getLoading() { return loading; }

    public LiveData<String> getError() { return error; }

    public LiveData<String> getPaymentUrl() { return paymentUrl; }

    public LiveData<PaymentStatus> getPaymentStatus() { return paymentStatus; }

    public String getDate() { return date; }

    // =============== Thanh toán VNPay ===============

    /** Tạo giao dịch VNPay cho đơn (server tự tính số tiền từ DB). */
    public void createVnPayPayment(int bookingId) {
        loading.setValue(true);
        Map<String, Object> body = new HashMap<>();
        body.put("booking_id", bookingId);
        ApiClient.getService().createVnPayPayment(body).enqueue(new Callback<PaymentInfo>() {
            @Override
            public void onResponse(@NonNull Call<PaymentInfo> call, @NonNull Response<PaymentInfo> response) {
                loading.setValue(false);
                PaymentInfo info = response.body();
                if (response.isSuccessful() && info != null
                        && info.getPaymentUrl() != null && !info.getPaymentUrl().isEmpty()) {
                    paymentUrl.setValue(info.getPaymentUrl());
                } else {
                    error.setValue("Tạo giao dịch thanh toán thất bại (" + response.code() + ")");
                    paymentUrl.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentInfo> call, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
                paymentUrl.setValue(null);
            }
        });
    }

    /** Kiểm tra trạng thái thanh toán hiện tại của đơn. */
    public void checkPaymentStatus(int bookingId) {
        loading.setValue(true);
        ApiClient.getService().getPaymentStatus(bookingId).enqueue(new Callback<PaymentStatus>() {
            @Override
            public void onResponse(@NonNull Call<PaymentStatus> call, @NonNull Response<PaymentStatus> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    paymentStatus.setValue(response.body());
                } else {
                    error.setValue("Không kiểm tra được trạng thái thanh toán (" + response.code() + ")");
                    // Vẫn báo về màn hình để không bị kẹt; coi như chưa rõ kết quả
                    PaymentStatus unknown = new PaymentStatus();
                    unknown.setBookingId(bookingId);
                    unknown.setPaymentStatus(PaymentStatus.PENDING);
                    paymentStatus.setValue(unknown);
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentStatus> call, @NonNull Throwable t) {
                loading.setValue(false);
                error.setValue("Lỗi kết nối: " + t.getMessage());
                PaymentStatus unknown = new PaymentStatus();
                unknown.setBookingId(bookingId);
                unknown.setPaymentStatus(PaymentStatus.PENDING);
                paymentStatus.setValue(unknown);
            }
        });
    }
}
