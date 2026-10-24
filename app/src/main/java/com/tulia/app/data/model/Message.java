package com.tulia.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

public final class Message {

    @NonNull
    public final String id;
    @NonNull
    public final String senderId;
    @NonNull
    public final String text;
    @NonNull
    public final String imageUrl;
    @NonNull
    public final String audioUrl;
    public final long durationMillis;
    @Nullable
    public final Timestamp createdAt;
    @Nullable
    public final ReplyTo replyTo;
    @Nullable
    public final Timestamp editedAt;
    public final boolean deleted;

    public Message(@NonNull String id,
                   @NonNull String senderId,
                   @NonNull String text,
                   @NonNull String imageUrl,
                   @Nullable Timestamp createdAt) {
        this(id, senderId, text, imageUrl, createdAt, null, null, false);
    }

    public Message(@NonNull String id,
                   @NonNull String senderId,
                   @NonNull String text,
                   @NonNull String imageUrl,
                   @Nullable Timestamp createdAt,
                   @Nullable ReplyTo replyTo,
                   @Nullable Timestamp editedAt,
                   boolean deleted) {
        this(id, senderId, text, imageUrl, "", 0L, createdAt, replyTo, editedAt, deleted);
    }

    public Message(@NonNull String id,
                   @NonNull String senderId,
                   @NonNull String text,
                   @NonNull String imageUrl,
                   @NonNull String audioUrl,
                   long durationMillis,
                   @Nullable Timestamp createdAt,
                   @Nullable ReplyTo replyTo,
                   @Nullable Timestamp editedAt,
                   boolean deleted) {
        this.id = id;
        this.senderId = senderId;
        this.text = text;
        this.imageUrl = imageUrl;
        this.audioUrl = audioUrl;
        this.durationMillis = durationMillis;
        this.createdAt = createdAt;
        this.replyTo = replyTo;
        this.editedAt = editedAt;
        this.deleted = deleted;
    }

    @NonNull
    public static Message from(@NonNull DocumentSnapshot document) {
        String senderId = document.getString("senderId");
        String text = document.getString("text");
        String imageUrl = document.getString("imageUrl");
        String audioUrl = document.getString("audioUrl");
        Long duration = document.getLong("duration");
        return new Message(
                document.getId(),
                senderId == null ? "" : senderId,
                text == null ? "" : text,
                imageUrl == null ? "" : imageUrl,
                audioUrl == null ? "" : audioUrl,
                duration == null ? 0L : duration,
                document.getTimestamp("createdAt"),
                ReplyTo.from(document.get("replyTo")),
                document.getTimestamp("editedAt"),
                Boolean.TRUE.equals(document.getBoolean("deleted")));
    }

    public boolean hasReply() {
        return replyTo != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    public boolean hasAudio() {
        return !audioUrl.trim().isEmpty();
    }

    public long createdMillis() {
        return createdAt == null ? 0L : createdAt.toDate().getTime();
    }

    public static final class ReplyTo {

        @NonNull
        public final String messageId;
        @NonNull
        public final String senderId;
        @NonNull
        public final String senderName;
        @NonNull
        public final String text;
        @NonNull
        public final String imageUrl;

        public ReplyTo(@NonNull String messageId,
                       @NonNull String senderId,
                       @NonNull String senderName,
                       @NonNull String text,
                       @NonNull String imageUrl) {
            this.messageId = messageId;
            this.senderId = senderId;
            this.senderName = senderName;
            this.text = text;
            this.imageUrl = imageUrl;
        }

        @Nullable
        public static ReplyTo from(@Nullable Object value) {
            if (!(value instanceof Map)) {
                return null;
            }
            Map<?, ?> map = (Map<?, ?>) value;
            String messageId = textOf(map.get("messageId"));
            if (messageId.isEmpty()) {
                return null;
            }
            return new ReplyTo(
                    messageId,
                    textOf(map.get("senderId")),
                    textOf(map.get("senderName")),
                    textOf(map.get("text")),
                    textOf(map.get("imageUrl")));
        }

        public boolean hasImage() {
            return !imageUrl.trim().isEmpty();
        }

        @NonNull
        private static String textOf(@Nullable Object value) {
            return value instanceof String ? (String) value : "";
        }
    }
}
