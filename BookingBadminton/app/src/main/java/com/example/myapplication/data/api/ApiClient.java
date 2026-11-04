package com.example.myapplication.data.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    // Emulator truy cập localhost của máy tính qua 10.0.2.2
    private static final String BASE_URL = "http://192.168.1.51:8000/api/";    private static Retrofit retrofit;

    private ApiClient() {
    }

    private static Retrofit getRetrofit() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static BadmintonApiService getService() {
        return getRetrofit().create(BadmintonApiService.class);
    }
}
