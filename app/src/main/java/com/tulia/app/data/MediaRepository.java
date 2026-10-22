package com.tulia.app.data;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public final class MediaRepository {

    public interface UploadCallback {
        void onUploaded(@NonNull String url);

        void onError(@NonNull Exception exception);
    }

    private static final MediaType JPEG = MediaType.parse("image/jpeg");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private MediaRepository() {
    }

    public static void uploadAvatar(@NonNull String uid,
                                    @NonNull byte[] bytes,
                                    @NonNull UploadCallback callback) {
        upload("avatar", null, bytes, callback);
    }

    public static void uploadChatPhoto(@NonNull String chatId,
                                       @NonNull byte[] bytes,
                                       @NonNull UploadCallback callback) {
        upload("chat", chatId, bytes, callback);
    }

    private static void upload(@NonNull String kind,
                               @Nullable String chatId,
                               @NonNull byte[] bytes,
                               @NonNull UploadCallback callback) {
        String baseUrl = BuildConfig.MEDIA_WORKER_URL;
        if (TextUtils.isEmpty(baseUrl)) {
            callback.onError(new IOException("Media service is not configured."));
            return;
        }

        FirebaseUser user;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            callback.onError(exception);
            return;
        }
        if (user == null) {
            callback.onError(new IOException("Not signed in."));
            return;
        }

        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful()
                    || tokenTask.getResult() == null
                    || TextUtils.isEmpty(tokenTask.getResult().getToken())) {
                postError(callback, tokenTask.getException() == null
                        ? new IOException("Could not get a login token.")
                        : tokenTask.getException());
                return;
            }
            send(baseUrl, kind, chatId, tokenTask.getResult().getToken(), bytes, callback);
        });
    }

    private static void send(@NonNull String baseUrl,
                             @NonNull String kind,
                             @Nullable String chatId,
                             @NonNull String token,
                             @NonNull byte[] bytes,
                             @NonNull UploadCallback callback) {
        MultipartBody.Builder body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("kind", kind)
                .addFormDataPart("file", "photo.jpg", new JpegBody(bytes));
        if (chatId != null) {
            body.addFormDataPart("chatId", chatId);
        }

        String endpoint = baseUrl.replaceAll("/+$", "") + "/upload";
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + token)
                .post(body.build())
                .build();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException exception) {
                postError(callback, exception);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    String payload = response.body() == null
                            ? ""
                            : response.body().string();
                    if (!response.isSuccessful()) {
                        postError(callback, new IOException(readError(payload,
                                "Upload failed (HTTP " + response.code() + ").")));
                        return;
                    }
                    String url = new JSONObject(payload).optString("url");
                    if (TextUtils.isEmpty(url)) {
                        postError(callback, new IOException("Upload returned no URL."));
                        return;
                    }
                    MAIN.post(() -> callback.onUploaded(url));
                } catch (Exception exception) {
                    postError(callback, exception);
                }
            }
        });
    }

    private static String readError(String payload, String fallback) {
        try {
            String message = new JSONObject(payload).optString("error");
            return TextUtils.isEmpty(message) ? fallback : message;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void postError(UploadCallback callback, Exception exception) {
        MAIN.post(() -> callback.onError(exception));
    }

    private static final class JpegBody extends RequestBody {
        private final byte[] bytes;

        JpegBody(byte[] bytes) {
            this.bytes = bytes;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return JPEG;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public void writeTo(@NonNull BufferedSink sink) throws IOException {
            sink.write(bytes);
        }
    }
}
