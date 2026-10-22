package com.tulia.app;

import android.app.Application;

import com.tulia.app.data.ThemePrefs;
import com.tulia.app.notifications.MessageNotifier;
import com.tulia.app.notifications.NotificationHelper;

public class TuliaApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.applySavedMode(this);
        NotificationHelper.ensureChannel(this);
        MessageNotifier.getInstance().start(this);
    }
}
