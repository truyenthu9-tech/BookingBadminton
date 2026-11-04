package com.example.myapplication.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.Court;
import com.example.myapplication.data.repository.CourtRepository;
import com.example.myapplication.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private static final int DAYS_AHEAD = 7;

    private final CourtRepository repository = new CourtRepository();

    // Danh sách ngày cho rvDates (yyyy-MM-dd)
    private final List<String> dates = new ArrayList<>();

    private final MutableLiveData<String> selectedDate = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    // Tự tải lại danh sách sân mỗi khi đổi ngày
    private final LiveData<List<Court>> courts;

    public HomeViewModel() {
        for (int i = 0; i < DAYS_AHEAD; i++) {
            dates.add(FormatUtils.apiDate(i));
        }
        courts = Transformations.switchMap(selectedDate, date -> {
            loading.setValue(true);
            error.setValue(null);
            return Transformations.map(repository.getCourts(date), list -> {
                loading.setValue(false);
                if (list == null) {
                    error.setValue("Không tải được danh sách sân");
                }
                return list;
            });
        });
        selectedDate.setValue(dates.get(0));
    }

    public List<String> getDates() {
        return dates;
    }

    public LiveData<String> getSelectedDate() {
        return selectedDate;
    }

    /** Tải danh sách sân của một ngày (yyyy-MM-dd). */
    public void loadCourts(String date) {
        if (date != null) {
            selectedDate.setValue(date);
        }
    }

    public void selectDate(String date) {
        if (date != null && !date.equals(selectedDate.getValue())) {
            selectedDate.setValue(date);
        }
    }

    /** Tải lại (kéo để làm mới / thử lại khi lỗi). */
    public void refresh() {
        String current = selectedDate.getValue();
        if (current != null) {
            selectedDate.setValue(current);
        }
    }

    public LiveData<List<Court>> getCourts() {
        return courts;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }
}