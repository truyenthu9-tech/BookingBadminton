package com.example.myapplication;

import android.app.Application;

import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.utils.SessionStore;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ApiClient.init(this);
        // Mỗi lần app khởi động lại thì yêu cầu đăng nhập lại
        SessionStore.clear(this);
    }
}
