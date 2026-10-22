package com.tulia.app.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.model.Chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageNotifier {

    private static final String TAG = "MessageNotifier";
    private static final MessageNotifier INSTANCE = new MessageNotifier();

    private final Map<String, Long> lastTimestamps = new HashMap<>();

    private Context appContext;
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authListener;
    private ListenerRegistration chatsRegistration;
    private String uid;
    private boolean seeded;
    private boolean foreground;
    private String activeChatId;

    private MessageNotifier() {
    }

    public static MessageNotifier getInstance() {
        return INSTANCE;
    }

    public void start(@NonNull Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
        if (authListener != null) {
            return;
        }
        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            return;
        }
        authListener = firebaseAuth -> onAuthChanged(firebaseAuth.getCurrentUser());
        auth.addAuthStateListener(authListener);
    }

    public void stop() {
        if (auth != null && authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
        auth = null;
        authListener = null;
        stopListening();
    }

    public void setActiveChatId(@Nullable String chatId) {
        activeChatId = chatId;
    }

    public void setAppForeground(boolean foreground) {
        this.foreground = foreground;
    }

    private void onAuthChanged(@Nullable FirebaseUser user) {
        if (user == null) {
            stopListening();
            return;
        }
        if (user.getUid().equals(uid) && chatsRegistration != null) {
            return;
        }
        startListening(user.getUid());
    }

    private void startListening(@NonNull String newUid) {
        stopListening();
        uid = newUid;
        try {
            chatsRegistration = ChatRepository.listenChats(uid,
                    new ChatRepository.ChatListCallback() {
                        @Override
                        public void onChats(@NonNull List<Chat> chats) {
                            handleChats(chats);
                        }

                        @Override
                        public void onError(@NonNull Exception exception) {
                            Log.w(TAG, "Could not listen for chats.", exception);
                        }
                    });
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            chatsRegistration = null;
        }
    }

    private void stopListening() {
        if (chatsRegistration != null) {
            chatsRegistration.remove();
            chatsRegistration = null;
        }
        uid = null;
        seeded = false;
        lastTimestamps.clear();
    }

    private void handleChats(@NonNull List<Chat> chats) {
        if (uid == null) {
            return;
        }
        if (!seeded) {
            for (Chat chat : chats) {
                long millis = lastMessageMillis(chat);
                if (millis > 0L) {
                    lastTimestamps.put(chat.id, millis);
                }
            }
            seeded = true;
            return;
        }

        for (Chat chat : chats) {
            long millis = lastMessageMillis(chat);
            if (millis <= 0L) {
                continue;
            }
            Long stored = lastTimestamps.get(chat.id);
            boolean newer = stored == null || millis > stored;
            lastTimestamps.put(chat.id, millis);
            if (!newer) {
                continue;
            }

            String senderId = chat.lastMessageSenderId;
            if (senderId != null && senderId.equals(uid)) {
                continue;
            }
            if (foreground && chat.id.equals(activeChatId)) {
                continue;
            }
            notifyFor(chat, senderId);
        }
    }

    private void notifyFor(@NonNull Chat chat, @Nullable String senderId) {
        if (appContext == null) {
            return;
        }

        String fallback = appContext.getString(R.string.guest_name);
        String senderName = senderId == null ? fallback : chat.nameFor(senderId, fallback);
        String otherUid = chat.otherUid(uid);
        if (otherUid == null) {
            otherUid = senderId;
        }
        String otherName = otherUid == null ? senderName : chat.nameFor(otherUid, senderName);

        String preview = chat.hasImagePreview()
                ? appContext.getString(R.string.photo_message)
                : chat.lastMessage;
        if (preview == null || preview.trim().isEmpty()) {
            preview = appContext.getString(R.string.app_name);
        }

        NotificationHelper.showMessage(appContext, chat.id, otherUid, otherName,
                senderName, preview);
    }

    private static long lastMessageMillis(@NonNull Chat chat) {
        return chat.lastMessageAt == null ? 0L : chat.lastMessageAt.toDate().getTime();
    }
}
