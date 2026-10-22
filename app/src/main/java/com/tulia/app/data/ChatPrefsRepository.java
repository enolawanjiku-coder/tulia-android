package com.tulia.app.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatPrefsRepository {

    private static final String TAG = "ChatPrefsRepository";

    private static final String USERS = "users";
    private static final String CHATS = "chats";
    private static final String FIELD_PINNED = "pinnedChats";
    private static final String FIELD_MUTED = "mutedChats";
    private static final String FIELD_ARCHIVED = "archivedChats";

    public interface LoadCallback {
        void onLoaded();

        void onError(@NonNull Exception exception);
    }

    public interface PrefsListener {
        void onPrefsChanged();
    }

    public interface PrefsCallback {
        void onUpdated();

        void onError(@NonNull Exception exception);
    }

    private static final Object LOCK = new Object();

    private static final Map<String, Boolean> pinnedChats = new HashMap<>();
    private static final Map<String, Boolean> mutedChats = new HashMap<>();
    private static final Map<String, Boolean> archivedChats = new HashMap<>();
    private static final List<PrefsListener> listeners = new ArrayList<>();

    private static String cachedUid;
    private static String listeningUid;
    private static ListenerRegistration snapshotRegistration;

    private ChatPrefsRepository() {
    }

    public static void load(@NonNull String uid, @NonNull LoadCallback callback) {
        try {
            FirebaseFirestore.getInstance()
                    .collection(USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(document -> {
                        applyDocument(uid, document);
                        callback.onLoaded();
                    })
                    .addOnFailureListener(callback::onError);
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            applyDocument(uid, null);
            callback.onError(exception);
        }
    }

    public static ListenerRegistration listen(@NonNull String uid,
                                             @NonNull PrefsListener listener) {
        addListener(listener);
        startListening(uid);
        return new ListenerRegistration() {
            @Override
            public void remove() {
                removeListener(listener);
            }
        };
    }

    public static void setPinned(@NonNull String uid, @NonNull String chatId, boolean pinned) {
        setPinned(uid, chatId, pinned, null);
    }

    public static void setPinned(@NonNull String uid,
                                 @NonNull String chatId,
                                 boolean pinned,
                                 @Nullable PrefsCallback callback) {
        setFlag(uid, chatId, FIELD_PINNED, pinned, callback);
    }

    public static void setMuted(@NonNull String uid, @NonNull String chatId, boolean muted) {
        setMuted(uid, chatId, muted, null);
    }

    public static void setMuted(@NonNull String uid,
                                @NonNull String chatId,
                                boolean muted,
                                @Nullable PrefsCallback callback) {
        setFlag(uid, chatId, FIELD_MUTED, muted, callback);
    }

    public static void setArchived(@NonNull String uid,
                                   @NonNull String chatId,
                                   boolean archived) {
        setArchived(uid, chatId, archived, null);
    }

    public static void setArchived(@NonNull String uid,
                                   @NonNull String chatId,
                                   boolean archived,
                                   @Nullable PrefsCallback callback) {
        setFlag(uid, chatId, FIELD_ARCHIVED, archived, callback);
    }

    public static boolean isPinned(@NonNull String uid, @NonNull String chatId) {
        synchronized (LOCK) {
            return uid.equals(cachedUid) && pinnedChats.containsKey(chatId);
        }
    }

    public static boolean isMuted(@NonNull String uid, @NonNull String chatId) {
        synchronized (LOCK) {
            return uid.equals(cachedUid) && mutedChats.containsKey(chatId);
        }
    }

    public static boolean isArchived(@NonNull String uid, @NonNull String chatId) {
        synchronized (LOCK) {
            return uid.equals(cachedUid) && archivedChats.containsKey(chatId);
        }
    }

    @NonNull
    public static Map<String, Boolean> pinnedChats(@NonNull String uid) {
        synchronized (LOCK) {
            if (!uid.equals(cachedUid)) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new HashMap<>(pinnedChats));
        }
    }

    @NonNull
    public static Map<String, Boolean> mutedChats(@NonNull String uid) {
        synchronized (LOCK) {
            if (!uid.equals(cachedUid)) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new HashMap<>(mutedChats));
        }
    }

    @NonNull
    public static Map<String, Boolean> archivedChats(@NonNull String uid) {
        synchronized (LOCK) {
            if (!uid.equals(cachedUid)) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new HashMap<>(archivedChats));
        }
    }

    public static void markRead(@NonNull String chatId, @NonNull String uid) {
        ChatRepository.markRead(chatId, uid);
    }

    public static void markUnread(@NonNull String chatId, @NonNull String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("unread." + uid, 1L);
        try {
            FirebaseFirestore.getInstance()
                    .collection(CHATS)
                    .document(chatId)
                    .update(updates);
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
        }
    }

    private static void setFlag(@NonNull String uid,
                                @NonNull String chatId,
                                @NonNull String field,
                                boolean enabled,
                                @Nullable PrefsCallback callback) {
        boolean changed = applyLocalFlag(uid, field, chatId, enabled);
        if (changed) {
            notifyListeners();
        }

        try {
            Task<Void> task = enabled
                    ? addFlag(uid, chatId, field)
                    : removeFlag(uid, chatId, field);
            task.addOnCompleteListener(completed -> {
                if (completed.isSuccessful()) {
                    if (callback != null) {
                        callback.onUpdated();
                    }
                    return;
                }
                Exception exception = completed.getException() == null
                        ? new IllegalStateException("Could not update the chat preferences.")
                        : completed.getException();
                Log.w(TAG, "Could not update the chat preferences.", exception);
                if (applyLocalFlag(uid, field, chatId, !enabled)) {
                    notifyListeners();
                }
                if (callback != null) {
                    callback.onError(exception);
                }
            });
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            if (applyLocalFlag(uid, field, chatId, !enabled)) {
                notifyListeners();
            }
            if (callback != null) {
                callback.onError(exception);
            }
        }
    }

    @NonNull
    private static Task<Void> addFlag(@NonNull String uid,
                                      @NonNull String chatId,
                                      @NonNull String field) {
        Map<String, Object> flags = new HashMap<>();
        flags.put(chatId, Boolean.TRUE);
        Map<String, Object> data = new HashMap<>();
        data.put(field, flags);
        return FirebaseFirestore.getInstance()
                .collection(USERS)
                .document(uid)
                .set(data, SetOptions.merge());
    }

    @NonNull
    private static Task<Void> removeFlag(@NonNull String uid,
                                         @NonNull String chatId,
                                         @NonNull String field) {
        Map<String, Object> update = new HashMap<>();
        update.put(field + "." + chatId, FieldValue.delete());
        return FirebaseFirestore.getInstance()
                .collection(USERS)
                .document(uid)
                .update(update);
    }

    private static boolean applyLocalFlag(@NonNull String uid,
                                           @NonNull String field,
                                           @NonNull String chatId,
                                           boolean enabled) {
        synchronized (LOCK) {
            if (!uid.equals(cachedUid)) {
                return false;
            }
            Map<String, Boolean> target = mapFor(field);
            if (enabled) {
                if (target.containsKey(chatId)) {
                    return false;
                }
                target.put(chatId, Boolean.TRUE);
                return true;
            }
            return target.remove(chatId) != null;
        }
    }

    @NonNull
    private static Map<String, Boolean> mapFor(@NonNull String field) {
        if (FIELD_PINNED.equals(field)) {
            return pinnedChats;
        }
        if (FIELD_MUTED.equals(field)) {
            return mutedChats;
        }
        return archivedChats;
    }

    private static void startListening(@NonNull String uid) {
        synchronized (LOCK) {
            if (snapshotRegistration != null && uid.equals(listeningUid)) {
                return;
            }
            if (snapshotRegistration != null) {
                snapshotRegistration.remove();
                snapshotRegistration = null;
            }
            listeningUid = uid;
        }
        try {
            ListenerRegistration registration = FirebaseFirestore.getInstance()
                    .collection(USERS)
                    .document(uid)
                    .addSnapshotListener((snapshot, exception) -> {
                        if (exception != null) {
                            Log.w(TAG, "Could not listen for the chat preferences.", exception);
                            return;
                        }
                        applyDocument(uid, snapshot);
                        notifyListeners();
                    });
            synchronized (LOCK) {
                snapshotRegistration = registration;
                listeningUid = uid;
            }
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            synchronized (LOCK) {
                snapshotRegistration = null;
            }
        }
    }

    private static void addListener(@NonNull PrefsListener listener) {
        synchronized (LOCK) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    private static void removeListener(@NonNull PrefsListener listener) {
        synchronized (LOCK) {
            listeners.remove(listener);
            if (!listeners.isEmpty() || snapshotRegistration == null) {
                return;
            }
            snapshotRegistration.remove();
            snapshotRegistration = null;
            listeningUid = null;
        }
    }

    private static void applyDocument(@NonNull String uid,
                                       @Nullable DocumentSnapshot document) {
        Map<String, Boolean> pinned = readMap(document, FIELD_PINNED);
        Map<String, Boolean> muted = readMap(document, FIELD_MUTED);
        Map<String, Boolean> archived = readMap(document, FIELD_ARCHIVED);
        synchronized (LOCK) {
            cachedUid = uid;
            pinnedChats.clear();
            pinnedChats.putAll(pinned);
            mutedChats.clear();
            mutedChats.putAll(muted);
            archivedChats.clear();
            archivedChats.putAll(archived);
        }
    }

    @NonNull
    private static Map<String, Boolean> readMap(@Nullable DocumentSnapshot document,
                                                @NonNull String field) {
        Map<String, Boolean> values = new HashMap<>();
        if (document == null || !document.exists()) {
            return values;
        }
        Object raw = document.get(field);
        if (!(raw instanceof Map)) {
            return values;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getKey() instanceof String && Boolean.TRUE.equals(entry.getValue())) {
                values.put((String) entry.getKey(), Boolean.TRUE);
            }
        }
        return values;
    }

    private static void notifyListeners() {
        List<PrefsListener> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<>(listeners);
        }
        for (PrefsListener listener : snapshot) {
            listener.onPrefsChanged();
        }
    }
}
