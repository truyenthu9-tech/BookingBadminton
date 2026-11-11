package com.example.myapplication.data.api;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.utils.SessionStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    // URL lấy từ BuildConfig (ghi đè bằng -PapiBaseUrl=... khi build)
    private static final String BASE_URL = BuildConfig.API_BASE_URL;

    private static Context appContext;
    private static Retrofit retrofit;

    private ApiClient() {
    }

    /** Gọi 1 lần từ Application.onCreate để interceptor đọc được token. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    private static Retrofit getRetrofit() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(ApiClient::authInterceptor)
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** Tự động gắn "Authorization: Bearer <token>" vào mọi request khi đã đăng nhập. */
    @NonNull
    private static Response authInterceptor(@NonNull Interceptor.Chain chain) throws IOException {
        Request original = chain.request();
        String token = appContext == null ? "" : SessionStore.token(appContext);
        if (token.isEmpty()) return chain.proceed(original);
        Request withAuth = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
        return chain.proceed(withAuth);
    }

    public static BadmintonApiService getService() {
        return getRetrofit().create(BadmintonApiService.class);
    }
}
