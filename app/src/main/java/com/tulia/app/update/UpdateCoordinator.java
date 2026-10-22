package com.tulia.app.update;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.tulia.app.R;

import java.io.File;

public final class UpdateCoordinator {

    private static final String PREFS_NAME = "tulia_update_prefs";
    private static final String PREF_LAST_CHECK = "last_check_at";

    private UpdateCoordinator() {
    }

    @Nullable
    private static View rootOf(@NonNull Context context) {
        if (context instanceof Activity) {
            return ((Activity) context).findViewById(android.R.id.content);
        }
        return null;
    }

    public static void check(@NonNull Context context, boolean interactive) {
        check(context, interactive, null);
    }

    public static void check(@NonNull Context context,
                             boolean interactive,
                             @Nullable Runnable onFinished) {
        UpdateChecker.check(new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(@NonNull UpdateInfo info) {
                showDialog(context, info);
                finish(onFinished);
            }

            @Override
            public void onUpToDate() {
                if (interactive) {
                    message(context, R.string.update_github_latest);
                }
                finish(onFinished);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (interactive) {
                    message(context, R.string.update_github_failed);
                }
                finish(onFinished);
            }
        });
    }

    public static void checkThrottled(@NonNull Context context,
                                      long intervalMillis,
                                      boolean interactive,
                                      @Nullable Runnable onFinished) {
        if (!shouldCheck(context, intervalMillis)) {
            finish(onFinished);
            return;
        }
        check(context, interactive, onFinished);
    }

    private static boolean shouldCheck(@NonNull Context context, long intervalMillis) {
        long now = System.currentTimeMillis();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
        long last = preferences.getLong(PREF_LAST_CHECK, 0L);
        if (now - last < intervalMillis) {
            return false;
        }
        preferences.edit().putLong(PREF_LAST_CHECK, now).apply();
        return true;
    }

    private static void finish(@Nullable Runnable onFinished) {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    public static void install(@NonNull Context context, @NonNull UpdateInfo info) {
        View root = rootOf(context);
        Snackbar progress = root == null
                ? null
                : Snackbar.make(root, R.string.update_downloading, Snackbar.LENGTH_INDEFINITE);
        if (progress != null) {
            progress.show();
        } else {
            message(context, R.string.update_downloading);
        }

        UpdateInstaller.download(context, info.downloadUrl,
                new UpdateInstaller.DownloadCallback() {
                    @Override
                    public void onDownloaded(@NonNull File apk) {
                        if (progress != null) {
                            progress.dismiss();
                        }
                        if (!UpdateInstaller.canInstallPackages(context)) {
                            UpdateInstaller.openInstallPermissionSettings(context);
                            message(context, R.string.update_install_permission);
                            return;
                        }
                        if (!UpdateInstaller.openInstaller(context, apk)) {
                            message(context, R.string.update_install_failed);
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        if (progress != null) {
                            progress.dismiss();
                        }
                        message(context, R.string.update_download_failed);
                    }
                });
    }

    private static void showDialog(@NonNull Context context, @NonNull UpdateInfo info) {
        String message = context.getString(R.string.update_github_available, info.versionName);
        if (!info.notes.isEmpty()) {
            message = message + "\n\n" + info.notes;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_available)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.update,
                        (dialog, which) -> install(context, info))
                .show();
    }

    private static void message(@NonNull Context context, int messageRes) {
        View root = rootOf(context);
        if (root != null) {
            Snackbar.make(root, messageRes, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show();
        }
    }
}
