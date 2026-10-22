package com.tulia.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.gms.tasks.Task;
import com.tulia.app.data.model.Chat;
import com.tulia.app.data.model.Message;
import com.tulia.app.data.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatRepository {

    public interface ChatListCallback {
        void onChats(@NonNull List<Chat> chats);

        void onError(@NonNull Exception exception);
    }

    public interface ChatCallback {
        void onChat(@Nullable Chat chat);

        void onError(@NonNull Exception exception);
    }

    public interface MessagesCallback {
        void onMessages(@NonNull List<Message> messages);

        void onError(@NonNull Exception exception);
    }

    public interface ChatReadyCallback {
        void onReady(@NonNull String chatId);

        void onError(@NonNull Exception exception);
    }

    public interface SendImageCallback {
        void onSent();

        void onError(@NonNull Exception exception);
    }

    public interface UpdateCallback {
        void onUpdated();

        void onError(@NonNull Exception exception);
    }

    public interface ReactionsCallback {
        void onReactions(@NonNull Map<String, String> reactions);

        void onError(@NonNull Exception exception);
    }

    private ChatRepository() {
    }

    public static ListenerRegistration listenChats(@NonNull String uid,
                                                   @NonNull ChatListCallback callback) {
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .whereArrayContains("members", uid)
                .addSnapshotListener((snapshot, exception) -> {
                    if (exception != null) {
                        callback.onError(exception);
                        return;
                    }
                    List<Chat> chats = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            chats.add(Chat.from(document));
                        }
                    }
                    Collections.sort(chats, Comparator
                            .comparingLong(Chat::lastActivityMillis)
                            .reversed());
                    callback.onChats(chats);
                });
    }

    public static ListenerRegistration listenChat(@NonNull String chatId,
                                                  @NonNull ChatCallback callback) {
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .addSnapshotListener((snapshot, exception) -> {
                    if (exception != null) {
                        callback.onError(exception);
                        return;
                    }
                    callback.onChat(snapshot != null && snapshot.exists()
                            ? Chat.from(snapshot)
                            : null);
                });
    }

    public static ListenerRegistration listenMessages(@NonNull String chatId,
                                                      int limit,
                                                      @NonNull MessagesCallback callback) {
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener((snapshot, exception) -> {
                    if (exception != null) {
                        callback.onError(exception);
                        return;
                    }
                    List<Message> messages = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            messages.add(Message.from(document));
                        }
                    }
                    Collections.reverse(messages);
                    callback.onMessages(messages);
                });
    }

    public static void openDirectChat(@NonNull String myUid,
                                      @NonNull String myName,
                                      @Nullable String myPhotoUrl,
                                      @NonNull UserProfile other,
                                      @NonNull ChatReadyCallback callback) {
        String chatId = directChatId(myUid, other.uid);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference chatRef = database.collection("chats").document(chatId);

        database.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(chatRef);

            Map<String, Object> names = new HashMap<>();
            names.put(myUid, myName);
            names.put(other.uid, other.name);

            Map<String, Object> photos = new HashMap<>();
            photos.put(myUid, myPhotoUrl == null ? "" : myPhotoUrl);
            photos.put(other.uid, other.photoUrl == null ? "" : other.photoUrl);

            Map<String, Object> memberInfo = new HashMap<>();
            memberInfo.put("memberNames", names);
            memberInfo.put("memberPhotos", photos);

            if (existing.exists()) {
                transaction.set(chatRef, memberInfo, SetOptions.merge());
                return null;
            }

            Map<String, Long> unread = new HashMap<>();
            unread.put(myUid, 0L);
            unread.put(other.uid, 0L);

            Map<String, Object> chat = new HashMap<>();
            chat.put("members", java.util.Arrays.asList(myUid, other.uid));
            chat.put("memberNames", names);
            chat.put("memberPhotos", photos);
            chat.put("unread", unread);
            chat.put("lastMessage", "");
            chat.put("lastMessageType", "text");
            chat.put("createdAt", Timestamp.now());
            transaction.set(chatRef, chat);
            return null;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onReady(chatId);
            } else {
                callback.onError(task.getException() == null
                        ? new IllegalStateException("Could not start the chat.")
                        : task.getException());
            }
        });
    }

    public static Task<Void> sendText(@NonNull String chatId,
                                      @NonNull String senderId,
                                      @NonNull String recipientId,
                                      @NonNull String text) {
        return sendText(chatId, senderId, recipientId, text, null);
    }

    public static Task<Void> sendText(@NonNull String chatId,
                                      @NonNull String senderId,
                                      @NonNull String recipientId,
                                      @NonNull String text,
                                      @Nullable Map<String, Object> replyTo) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference chatRef = database.collection("chats").document(chatId);
        DocumentReference messageRef = chatRef.collection("messages").document();

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", senderId);
        message.put("text", text);
        message.put("createdAt", Timestamp.now());
        if (replyTo != null && !replyTo.isEmpty()) {
            message.put("replyTo", replyTo);
        }

        Map<String, Object> chat = new HashMap<>();
        chat.put("lastMessage", text);
        chat.put("lastMessageType", "text");
        chat.put("lastMessageSenderId", senderId);
        chat.put("lastMessageAt", Timestamp.now());
        chat.put("unread." + recipientId, FieldValue.increment(1));

        WriteBatch batch = database.batch();
        batch.set(messageRef, message);
        batch.update(chatRef, chat);
        return batch.commit();
    }

    public static void loadReactions(@NonNull String chatId,
                                      @NonNull String messageId,
                                      @NonNull ReactionsCallback callback) {
        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .collection("reactions")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, String> reactions = new HashMap<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String type = document.getString("type");
                        if (type != null && !type.trim().isEmpty()) {
                            reactions.put(document.getId(), type);
                        }
                    }
                    callback.onReactions(reactions);
                })
                .addOnFailureListener(callback::onError);
    }

    public static Task<Void> setReaction(@NonNull String chatId,
                                         @NonNull String messageId,
                                         @NonNull String uid,
                                         @Nullable String type) {
        DocumentReference reactionRef = FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .collection("reactions")
                .document(uid);
        if (type == null) {
            return reactionRef.delete();
        }
        Map<String, Object> reaction = new HashMap<>();
        reaction.put("type", type);
        reaction.put("createdAt", Timestamp.now());
        return reactionRef.set(reaction);
    }

    public static Task<Void> editMessage(@NonNull String chatId,
                                         @NonNull String messageId,
                                         @NonNull String text) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", text);
        updates.put("editedAt", Timestamp.now());
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update(updates);
    }

    public static Task<Void> deleteMessage(@NonNull String chatId,
                                            @NonNull String messageId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("text", "");
        updates.put("imageUrl", "");
        updates.put("deleted", true);
        updates.put("deletedAt", Timestamp.now());
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update(updates);
    }

    public static Task<Void> setTyping(@NonNull String chatId,
                                       @NonNull String uid,
                                       boolean typing) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("typing." + uid, typing ? Timestamp.now() : FieldValue.delete());
        return FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .update(updates);
    }

    public static void sendImage(@NonNull String chatId,
                                 @NonNull String senderId,
                                 @NonNull String recipientId,
                                 @NonNull byte[] bytes,
                                 @NonNull SendImageCallback callback) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference chatRef = database.collection("chats").document(chatId);
        DocumentReference messageRef = chatRef.collection("messages").document();

        MediaRepository.uploadChatPhoto(chatId, bytes,
                new MediaRepository.UploadCallback() {
                    @Override
                    public void onUploaded(@NonNull String url) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("senderId", senderId);
                        message.put("text", "");
                        message.put("imageUrl", url);
                        message.put("createdAt", Timestamp.now());

                        Map<String, Object> chat = new HashMap<>();
                        chat.put("lastMessage", "");
                        chat.put("lastMessageType", "image");
                        chat.put("lastMessageSenderId", senderId);
                        chat.put("lastMessageAt", Timestamp.now());
                        chat.put("unread." + recipientId, FieldValue.increment(1));

                        WriteBatch batch = database.batch();
                        batch.set(messageRef, message);
                        batch.update(chatRef, chat);
                        batch.commit().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                callback.onSent();
                            } else {
                                callback.onError(task.getException() == null
                                        ? new IllegalStateException("Could not send the photo.")
                                        : task.getException());
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        callback.onError(exception);
                    }
                });
    }

    public static void updateMemberInfo(@NonNull String uid,
                                        @NonNull String name,
                                        @Nullable String photoUrl,
                                        @NonNull UpdateCallback callback) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection("chats")
                .whereArrayContains("members", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onUpdated();
                        return;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("memberNames." + uid, name);
                    updates.put("memberPhotos." + uid, photoUrl == null
                            ? FieldValue.delete()
                            : photoUrl);

                    WriteBatch batch = database.batch();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        batch.update(document.getReference(), updates);
                    }
                    batch.commit().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            callback.onUpdated();
                        } else {
                            callback.onError(task.getException() == null
                                    ? new IllegalStateException("Could not update chat profiles.")
                                    : task.getException());
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    public static void markRead(@NonNull String chatId, @NonNull String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("unread." + uid, 0L);
        updates.put("lastRead." + uid, Timestamp.now());
        FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .update(updates);
    }

    @NonNull
    public static String directChatId(@NonNull String firstUid, @NonNull String secondUid) {
        return firstUid.compareTo(secondUid) < 0
                ? firstUid + "_" + secondUid
                : secondUid + "_" + firstUid;
    }
}
