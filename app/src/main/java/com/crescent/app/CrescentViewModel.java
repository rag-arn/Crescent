package com.crescent.app;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class CrescentViewModel extends AndroidViewModel {
    public final CrescentRepository repository;
    public CrescentViewModel(@NonNull Application application) { super(application); repository = new CrescentRepository(application); }
}
