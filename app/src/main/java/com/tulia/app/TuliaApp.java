package com.tulia.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.data.PresenceRepository;
import com.tulia.app.data.ThemePrefs;
import com.tulia.app.notifications.MessageNotifier;
import com.tulia.app.notifications.NotificationHelper;
import com.tulia.app.notifications.PushTokenRegistry;

public class TuliaApp extends Application
        implements Application.ActivityLifecycleCallbacks {

    private int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.applySavedMode(this);
        NotificationHelper.ensureChannel(this);
        MessageNotifier.getInstance().start(this);
        registerActivityLifecycleCallbacks(this);
        registerPushToken();
        startPresence();
    }

    private void registerPushToken() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            PushTokenRegistry.ensureRegistered(this);
            auth.addAuthStateListener(firebaseAuth -> {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    PushTokenRegistry.ensureRegistered(this);
                }
            });
        } catch (IllegalStateException ignored) {
            // Firebase is not configured yet; push registers on the next launch.
        }
    }

    private void startPresence() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                PresenceRepository.start(user.getUid());
            }
            auth.addAuthStateListener(firebaseAuth -> {
                FirebaseUser signedIn = firebaseAuth.getCurrentUser();
                if (signedIn != null) {
                    PresenceRepository.start(signedIn.getUid());
                    if (startedActivities > 0) {
                        PresenceRepository.setForeground(true);
                    }
                } else {
                    PresenceRepository.stop();
                }
            });
        } catch (IllegalStateException ignored) {
            // Firebase is not configured yet; presence starts on the next launch.
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities += 1;
        if (startedActivities == 1) {
            PresenceRepository.setForeground(true);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0) {
            PresenceRepository.setForeground(false);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
