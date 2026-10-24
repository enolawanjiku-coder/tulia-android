package com.tulia.app.data;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Fire-and-forget call to the media worker's /notify endpoint after a message
 * is committed, so recipients on other devices get an FCM push. Failures are
 * logged only: the message itself is already sent.
 */
public final class PushRepository {

    private static final String TAG = "PushRepository";
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private PushRepository() {
    }

    public static void notifyMessageSent(@NonNull String chatId,
                                         @NonNull String type,
                                         @NonNull String preview) {
        String baseUrl = BuildConfig.MEDIA_WORKER_URL;
        if (TextUtils.isEmpty(baseUrl)) {
            return;
        }

        FirebaseUser user;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            return;
        }
        if (user == null) {
            return;
        }

        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful()
                    || tokenTask.getResult() == null
                    || TextUtils.isEmpty(tokenTask.getResult().getToken())) {
                Log.w(TAG, "Could not get a login token for push.");
                return;
            }

            JSONObject body = new JSONObject();
            try {
                body.put("chatId", chatId);
                body.put("type", type);
                body.put("preview", preview);
            } catch (Exception exception) {
                Log.w(TAG, "Could not build the push payload.", exception);
                return;
            }

            Request request = new Request.Builder()
                    .url(baseUrl.replaceAll("/+$", "") + "/notify")
                    .header("Authorization", "Bearer " + tokenTask.getResult().getToken())
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                    Log.w(TAG, "Push notify call failed.", exception);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Push notify rejected (HTTP " + response.code() + ").");
                        }
                    } catch (Exception ignored) {
                        // Notification is best-effort; nothing to recover.
                    }
                }
            });
        });
    }
}
