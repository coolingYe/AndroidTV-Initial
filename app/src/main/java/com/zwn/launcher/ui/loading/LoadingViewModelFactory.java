package com.zwn.launcher.ui.loading;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zwn.launcher.data.DataRepository;

public class LoadingViewModelFactory implements ViewModelProvider.Factory {

    private final DataRepository dataRepository;

    public LoadingViewModelFactory(DataRepository dataRepository) {
        this.dataRepository = dataRepository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new LoadingViewModel(dataRepository);
    }
}
