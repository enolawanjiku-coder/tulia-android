package com.tulia.app.notifications;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tulia.app.R;

import java.util.Map;

/**
 * Receives FCM pushes from the media worker when the app process is not
 * running its own chat listener. When MessageNotifier's snapshot listener is
 * alive it already posts local notifications, so this service stays quiet to
 * avoid duplicates.
 */
public class TuliaMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String chatId = data.get("chatId");
        if (TextUtils.isEmpty(chatId)) {
            return;
        }

        FirebaseUser user;
        try {
            user = FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            return;
        }
        if (user == null || MessageNotifier.getInstance().isListening()) {
            return;
        }

        String sender = data.get("sender");
        String senderUid = data.get("senderUid");
        String preview = data.get("preview");
        if (TextUtils.isEmpty(preview)) {
            String type = data.get("type");
            if ("image".equals(type)) {
                preview = getString(R.string.photo_message);
            } else if ("audio".equals(type)) {
                preview = getString(R.string.voice_message);
            } else {
                preview = getString(R.string.app_name);
            }
        }

        NotificationHelper.showMessage(this, chatId, senderUid, sender, sender, preview);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        PushTokenRegistry.saveToken(getApplicationContext(), token);
    }
}
