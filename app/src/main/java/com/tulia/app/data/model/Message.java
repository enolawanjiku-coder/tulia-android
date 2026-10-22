package com.tulia.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public final class Message {

    @NonNull
    public final String id;
    @NonNull
    public final String senderId;
    @NonNull
    public final String text;
    @NonNull
    public final String imageUrl;
    @Nullable
    public final Timestamp createdAt;

    public Message(@NonNull String id,
                   @NonNull String senderId,
                   @NonNull String text,
                   @NonNull String imageUrl,
                   @Nullable Timestamp createdAt) {
        this.id = id;
        this.senderId = senderId;
        this.text = text;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }

    @NonNull
    public static Message from(@NonNull DocumentSnapshot document) {
        String senderId = document.getString("senderId");
        String text = document.getString("text");
        String imageUrl = document.getString("imageUrl");
        return new Message(
                document.getId(),
                senderId == null ? "" : senderId,
                text == null ? "" : text,
                imageUrl == null ? "" : imageUrl,
                document.getTimestamp("createdAt"));
    }

    public long createdMillis() {
        return createdAt == null ? 0L : createdAt.toDate().getTime();
    }
}
