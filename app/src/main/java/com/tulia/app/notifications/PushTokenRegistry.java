package com.tulia.app.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps this device's FCM token in Firestore at pushTokens/{uid} so the media
 * worker can reach the device with push notifications. That collection is
 * write-only for clients; only the worker's service account can read it.
 */
public final class PushTokenRegistry {

    private static final String TAG = "PushTokenRegistry";
    private static final String PREFS = "push_prefs";
    private static final String KEY_TOKEN = "fcm_token";

    private PushTokenRegistry() {
    }

    public static void ensureRegistered(@NonNull Context context) {
        FirebaseUser user = currentUser();
        if (user == null) {
            return;
        }
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            saveToken(context, task.getResult());
                        }
                    });
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
        }
    }

    public static void saveToken(@NonNull Context context, @NonNull String token) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TOKEN, token)
                .apply();

        FirebaseUser user = currentUser();
        if (user == null) {
            return;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("tokens", FieldValue.arrayUnion(token));
            data.put("updatedAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance()
                    .collection("pushTokens")
                    .document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnFailureListener(exception ->
                            Log.w(TAG, "Could not save the push token.", exception));
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
        }
    }

    private static FirebaseUser currentUser() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}
