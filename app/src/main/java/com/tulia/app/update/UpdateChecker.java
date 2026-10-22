package com.tulia.app.update;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tulia.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class UpdateChecker {

    public interface Callback {
        void onUpdateAvailable(@NonNull UpdateInfo info);

        void onUpToDate();

        void onError(@NonNull Exception exception);
    }

    private static final String API_URL = "https://api.github.com/repos/"
            + BuildConfig.UPDATE_REPO_OWNER + "/" + BuildConfig.UPDATE_REPO_NAME
            + "/releases/latest";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {
    }

    public static void check(@NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo info = fetch();
                if (info == null) {
                    MAIN.post(() -> callback.onError(
                            new IOException("The latest release has no APK asset.")));
                    return;
                }
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    MAIN.post(() -> callback.onUpdateAvailable(info));
                } else {
                    MAIN.post(callback::onUpToDate);
                }
            } catch (Exception exception) {
                MAIN.post(() -> callback.onError(exception));
            }
        });
    }

    @Nullable
    private static UpdateInfo fetch() throws IOException {
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Tulia-Android/" + BuildConfig.VERSION_NAME)
                .build();

        Response response = CLIENT.newCall(request).execute();
        ResponseBody body = response.body();
        String payload = body == null ? null : body.string();
        if (!response.isSuccessful() || payload == null) {
            throw new IOException("Update check failed with status " + response.code() + ".");
        }
        return parse(payload);
    }

    @Nullable
    private static UpdateInfo parse(@NonNull String payload) {
        try {
            JSONObject release = new JSONObject(payload);
            String tag = release.optString("tag_name", "");
            Integer versionCode = UpdateInfo.parseVersionCode(tag);
            if (versionCode == null) {
                return null;
            }
            String versionName = release.optString("name", "");
            if (versionName.trim().isEmpty()) {
                versionName = tag;
            }
            String notes = release.optString("body", "");
            String downloadUrl = findApk(release.optJSONArray("assets"));
            if (downloadUrl == null) {
                return null;
            }
            return new UpdateInfo(versionCode, versionName.trim(), notes.trim(), downloadUrl);
        } catch (Exception exception) {
            return null;
        }
    }

    @Nullable
    private static String findApk(@Nullable JSONArray assets) {
        if (assets == null) {
            return null;
        }
        String fallback = null;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            if (!name.toLowerCase().endsWith(".apk")) {
                continue;
            }
            String url = asset.optString("browser_download_url", "");
            if (url.trim().isEmpty()) {
                continue;
            }
            if (name.toLowerCase().contains("tulia")) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        return fallback;
    }
}
