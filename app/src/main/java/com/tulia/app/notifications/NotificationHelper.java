package com.tulia.app.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.tulia.app.MainActivity;
import com.tulia.app.R;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "messages";
    public static final String GROUP_MESSAGES = "messages";
    public static final String EXTRA_CHAT_ID = "chat_id";
    public static final String EXTRA_OTHER_UID = "other_uid";
    public static final String EXTRA_OTHER_NAME = "other_name";

    public static final String PREFS_NOTIFICATIONS = "notification_prefs";
    public static final String PREF_POST_NOTIFICATIONS_REQUESTED =
            "post_notifications_requested";

    private static final String TAG = "NotificationHelper";

    private NotificationHelper() {
    }

    public static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setLightColor(ContextCompat.getColor(context, R.color.tulia_red));
        channel.setShowBadge(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static boolean areNotificationsEnabled(@NonNull Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                return channel == null
                        || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
            }
        }
        return true;
    }

    public static boolean hasRequestedNotificationPermission(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE)
                .getBoolean(PREF_POST_NOTIFICATIONS_REQUESTED, false);
    }

    public static void markNotificationPermissionRequested(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_POST_NOTIFICATIONS_REQUESTED, true)
                .apply();
    }

    public static boolean canPostNotifications(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return areNotificationsEnabled(context);
    }

    @SuppressLint("MissingPermission")
    public static void showMessage(@NonNull Context context,
                                   @NonNull String chatId,
                                   @Nullable String otherUid,
                                   @Nullable String otherName,
                                   @Nullable String senderName,
                                   @Nullable String preview) {
        ensureChannel(context);
        if (!canPostNotifications(context)) {
            return;
        }

        String title = senderName == null ? null : senderName.trim();
        if (title == null || title.isEmpty()) {
            title = otherName == null ? null : otherName.trim();
        }
        if (title == null || title.isEmpty()) {
            title = context.getString(R.string.app_name);
        }

        String text = preview == null ? null : preview.trim();
        if (text == null || text.isEmpty()) {
            text = context.getString(R.string.app_name);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        intent.putExtra(EXTRA_OTHER_UID, otherUid);
        intent.putExtra(EXTRA_OTHER_NAME, otherName);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(ContextCompat.getColor(context, R.color.tulia_red))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setGroup(GROUP_MESSAGES)
                .setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(context).notify(chatId.hashCode(), builder.build());
        } catch (SecurityException exception) {
            Log.w(TAG, "Notification permission is missing.", exception);
        }
    }

    public static void cancelMessage(@NonNull Context context, @NonNull String chatId) {
        NotificationManagerCompat.from(context).cancel(chatId.hashCode());
    }

    public static void openNotificationSettings(@NonNull Context context) {
        boolean permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!permissionMissing) {
                Intent channelIntent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID);
                if (launch(context, channelIntent)) {
                    return;
                }
            }
            Intent appIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            if (launch(context, appIntent)) {
                return;
            }
        }
        Intent detailsIntent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName()));
        launch(context, detailsIntent);
    }

    private static boolean launch(@NonNull Context context, @NonNull Intent intent) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException exception) {
            return false;
        }
    }

    @Nullable
    public static PendingChat readPendingChat(@Nullable Intent intent) {
        return PendingChat.from(intent);
    }

    public static void clearChatExtras(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        intent.removeExtra(EXTRA_CHAT_ID);
        intent.removeExtra(EXTRA_OTHER_UID);
        intent.removeExtra(EXTRA_OTHER_NAME);
    }

    public static final class PendingChat {

        @NonNull
        public final String chatId;
        @Nullable
        public final String otherUid;
        @Nullable
        public final String otherName;

        public PendingChat(@NonNull String chatId,
                           @Nullable String otherUid,
                           @Nullable String otherName) {
            this.chatId = chatId;
            this.otherUid = otherUid;
            this.otherName = otherName;
        }

        @Nullable
        public static PendingChat from(@Nullable Intent intent) {
            if (intent == null) {
                return null;
            }
            String chatId = intent.getStringExtra(EXTRA_CHAT_ID);
            if (chatId == null || chatId.trim().isEmpty()) {
                return null;
            }
            return new PendingChat(
                    chatId,
                    intent.getStringExtra(EXTRA_OTHER_UID),
                    intent.getStringExtra(EXTRA_OTHER_NAME));
        }
    }
}
