package com.tulia.app.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class UpdateInstaller {

    public interface DownloadCallback {
        void onDownloaded(@NonNull File apk);

        void onError(@NonNull Exception exception);
    }

    private static final String TAG = "UpdateInstaller";
    private static final String UPDATE_FOLDER = "updates";
    private static final String APK_NAME = "tulia-update.apk";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateInstaller() {
    }

    public static void download(@NonNull Context context,
                                @NonNull String url,
                                @NonNull DownloadCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                File folder = context.getExternalFilesDir(UPDATE_FOLDER);
                if (folder == null) {
                    folder = new File(context.getFilesDir(), UPDATE_FOLDER);
                }
                if (!folder.exists() && !folder.mkdirs()) {
                    throw new IOException("Could not create the update folder.");
                }
                File target = new File(folder, APK_NAME);
                if (target.exists() && !target.delete()) {
                    throw new IOException("Could not replace the downloaded update.");
                }

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Tulia-Android")
                        .build();
                Response response = CLIENT.newCall(request).execute();
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("Download failed with status " + response.code() + ".");
                }
                try (InputStream input = body.byteStream();
                     OutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                callback.onDownloaded(target);
            } catch (Exception exception) {
                Log.w(TAG, "Could not download the update.", exception);
                callback.onError(exception);
            }
        });
    }

    public static boolean canInstallPackages(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Exception exception) {
            return false;
        }
    }

    public static void openInstallPermissionSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception exception) {
            Log.w(TAG, "Could not open the install permission settings.", exception);
        }
    }

    public static boolean openInstaller(@NonNull Context context, @NonNull File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception exception) {
            Log.w(TAG, "Could not start the package installer.", exception);
            return false;
        }
    }

    @Nullable
    public static File updateFolder(@NonNull Context context) {
        File folder = context.getExternalFilesDir(UPDATE_FOLDER);
        if (folder == null) {
            return null;
        }
        return new File(folder, APK_NAME);
    }
}
