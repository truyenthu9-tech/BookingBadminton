package com.example.myapplication.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myapplication.data.api.ApiClient;
import com.example.myapplication.data.api.BadmintonApiService;
import com.example.myapplication.data.model.Court;
import com.example.myapplication.data.model.Slot;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CourtRepository {

    private final BadmintonApiService api = ApiClient.getService();

    public LiveData<List<Court>> getCourts(String date) {
        MutableLiveData<List<Court>> result = new MutableLiveData<>();
        api.getCourts(date).enqueue(new Callback<List<Court>>() {
            @Override
            public void onResponse(@NonNull Call<List<Court>> call,
                                   @NonNull Response<List<Court>> response) {
                result.setValue(response.isSuccessful() ? response.body() : null);
            }

            @Override
            public void onFailure(@NonNull Call<List<Court>> call, @NonNull Throwable t) {
                result.setValue(null);
            }
        });
        return result;
    }

    public LiveData<Court> getCourtDetail(int courtId) {
        MutableLiveData<Court> result = new MutableLiveData<>();
        api.getCourtDetail(courtId).enqueue(new Callback<Court>() {
            @Override
            public void onResponse(@NonNull Call<Court> call, @NonNull Response<Court> response) {
                result.setValue(response.isSuccessful() ? response.body() : null);
            }

            @Override
            public void onFailure(@NonNull Call<Court> call, @NonNull Throwable t) {
                result.setValue(null);
            }
        });
        return result;
    }

    public LiveData<List<Slot>> getSlots(int courtId, String date) {
        MutableLiveData<List<Slot>> result = new MutableLiveData<>();
        api.getSlots(courtId, date).enqueue(new Callback<List<Slot>>() {
            @Override
            public void onResponse(@NonNull Call<List<Slot>> call,
                                   @NonNull Response<List<Slot>> response) {
                result.setValue(response.isSuccessful() ? response.body() : null);
            }

            @Override
            public void onFailure(@NonNull Call<List<Slot>> call, @NonNull Throwable t) {
                result.setValue(null);
            }
        });
        return result;
    }
}
