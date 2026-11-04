package com.example.myapplication.data.api;

import com.example.myapplication.data.model.Booking;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.data.model.Post;
import com.example.myapplication.data.model.Slot;
import com.example.myapplication.data.model.User;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface BadmintonApiService {

    // ---------- Auth (đăng nhập bằng mã SV/GV + mật khẩu) ----------
    @POST("auth/login")
    Call<User> login(@Body User credentials);

    @POST("auth/register")
    Call<User> register(@Body User user);

    // ---------- Court ----------
    @GET("courts")
    Call<List<Court>> getCourts(@Query("date") String date);

    @GET("courts/{id}")
    Call<Court> getCourtDetail(@Path("id") int courtId);

    @GET("courts/{id}/slots")
    Call<List<Slot>> getSlots(@Path("id") int courtId, @Query("date") String date);

    // ---------- Booking / Ticket ----------
    // Gửi yêu cầu thanh toán -> booking ở trạng thái "pending"
    @POST("bookings")
    Call<Booking> createBooking(@Body Booking booking);

    @GET("bookings/{id}")
    Call<Booking> getBooking(@Path("id") int bookingId);

    @GET("users/{id}/bookings")
    Call<List<Booking>> getBookingsByUser(@Path("id") int userId);

    // ---------- Community (Tìm hội chơi) ----------
    @GET("posts")
    Call<List<Post>> getPosts();

    @POST("posts")
    Call<Post> createPost(@Body Post post);

    // ---------- Admin ----------
    @POST("admin/courts")
    Call<Court> createCourt(@Body Court court);

    @PUT("admin/courts/{id}")
    Call<Court> updateCourt(@Path("id") int courtId, @Body Court court);

    // Ẩn / hiện sân
    @PATCH("admin/courts/{id}/visibility")
    Call<Court> setCourtHidden(@Path("id") int courtId, @Query("hidden") boolean hidden);

    // Hóa đơn chờ kiểm duyệt
    @GET("admin/bookings")
    Call<List<Booking>> getBookingsByStatus(@Query("status") String status);

    @PATCH("admin/bookings/{id}/approve")
    Call<Booking> approveBooking(@Path("id") int bookingId);

    @PATCH("admin/bookings/{id}/cancel")
    Call<Booking> cancelBooking(@Path("id") int bookingId);

    // Xuất báo cáo
    @GET("admin/report")
    Call<ResponseBody> exportReport();
}
