package com.tulia.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Chat {

    @NonNull
    public final String id;
    @NonNull
    public final List<String> members;
    @NonNull
    public final Map<String, String> memberNames;
    @NonNull
    public final Map<String, String> memberPhotos;
    @NonNull
    public final String lastMessage;
    @NonNull
    public final String lastMessageType;
    @Nullable
    public final String lastMessageSenderId;
    @Nullable
    public final Timestamp lastMessageAt;
    @Nullable
    public final Timestamp createdAt;
    @NonNull
    public final Map<String, Long> unread;
    @NonNull
    public final Map<String, Timestamp> lastRead;
    @NonNull
    public final Map<String, Long> typing;
    public final boolean pinned;
    public final boolean muted;
    public final boolean archived;

    private Chat(@NonNull String id,
                 @NonNull List<String> members,
                 @NonNull Map<String, String> memberNames,
                 @NonNull Map<String, String> memberPhotos,
                 @NonNull String lastMessage,
                 @NonNull String lastMessageType,
                 @Nullable String lastMessageSenderId,
                 @Nullable Timestamp lastMessageAt,
                 @Nullable Timestamp createdAt,
                 @NonNull Map<String, Long> unread,
                 @NonNull Map<String, Timestamp> lastRead,
                 @NonNull Map<String, Long> typing) {
        this(id, members, memberNames, memberPhotos, lastMessage, lastMessageType,
                lastMessageSenderId, lastMessageAt, createdAt, unread, lastRead, typing,
                false, false, false);
    }

    private Chat(@NonNull String id,
                 @NonNull List<String> members,
                 @NonNull Map<String, String> memberNames,
                 @NonNull Map<String, String> memberPhotos,
                 @NonNull String lastMessage,
                 @NonNull String lastMessageType,
                 @Nullable String lastMessageSenderId,
                 @Nullable Timestamp lastMessageAt,
                 @Nullable Timestamp createdAt,
                 @NonNull Map<String, Long> unread,
                 @NonNull Map<String, Timestamp> lastRead,
                 @NonNull Map<String, Long> typing,
                 boolean pinned,
                 boolean muted,
                 boolean archived) {
        this.id = id;
        this.members = members;
        this.memberNames = memberNames;
        this.memberPhotos = memberPhotos;
        this.lastMessage = lastMessage;
        this.lastMessageType = lastMessageType;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.unread = unread;
        this.lastRead = lastRead;
        this.typing = typing;
        this.pinned = pinned;
        this.muted = muted;
        this.archived = archived;
    }

    @NonNull
    public static Chat from(@NonNull DocumentSnapshot document) {
        List<String> members = new ArrayList<>();
        Object memberValue = document.get("members");
        if (memberValue instanceof List) {
            for (Object member : (List<?>) memberValue) {
                if (member instanceof String) {
                    members.add((String) member);
                }
            }
        }

        Map<String, String> memberNames = new HashMap<>();
        Object namesValue = document.get("memberNames");
        if (namesValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) namesValue).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    memberNames.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }

        Map<String, Long> unread = new HashMap<>();
        Object unreadValue = document.get("unread");
        if (unreadValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) unreadValue).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                    unread.put((String) entry.getKey(),
                            ((Number) entry.getValue()).longValue());
                }
            }
        }

        Map<String, Timestamp> lastRead = new HashMap<>();
        Object lastReadValue = document.get("lastRead");
        if (lastReadValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) lastReadValue).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Timestamp) {
                    lastRead.put((String) entry.getKey(), (Timestamp) entry.getValue());
                }
            }
        }

        Map<String, String> memberPhotos = new HashMap<>();
        Object photosValue = document.get("memberPhotos");
        if (photosValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) photosValue).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    memberPhotos.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }

        Map<String, Long> typing = new HashMap<>();
        Object typingValue = document.get("typing");
        if (typingValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) typingValue).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Timestamp) {
                    typing.put((String) entry.getKey(),
                            ((Timestamp) value).toDate().getTime());
                } else if (value instanceof Number) {
                    typing.put((String) entry.getKey(),
                            ((Number) value).longValue());
                }
            }
        }

        String lastMessage = document.getString("lastMessage");
        String lastMessageType = document.getString("lastMessageType");
        return new Chat(
                document.getId(),
                Collections.unmodifiableList(members),
                Collections.unmodifiableMap(memberNames),
                Collections.unmodifiableMap(memberPhotos),
                lastMessage == null ? "" : lastMessage,
                lastMessageType == null ? "text" : lastMessageType,
                document.getString("lastMessageSenderId"),
                document.getTimestamp("lastMessageAt"),
                document.getTimestamp("createdAt"),
                Collections.unmodifiableMap(unread),
                Collections.unmodifiableMap(lastRead),
                Collections.unmodifiableMap(typing));
    }

    @NonNull
    public Chat withFlags(boolean pinned, boolean muted, boolean archived) {
        if (this.pinned == pinned && this.muted == muted && this.archived == archived) {
            return this;
        }
        return new Chat(id, members, memberNames, memberPhotos, lastMessage,
                lastMessageType, lastMessageSenderId, lastMessageAt, createdAt, unread,
                lastRead, typing, pinned, muted, archived);
    }

    @NonNull
    public Chat withPrefs(@NonNull Map<String, Boolean> pinnedChats,
                          @NonNull Map<String, Boolean> mutedChats,
                          @NonNull Map<String, Boolean> archivedChats) {
        return withFlags(
                Boolean.TRUE.equals(pinnedChats.get(id)),
                Boolean.TRUE.equals(mutedChats.get(id)),
                Boolean.TRUE.equals(archivedChats.get(id)));
    }

    @Nullable
    public String otherUid(@NonNull String uid) {
        for (String member : members) {
            if (!member.equals(uid)) {
                return member;
            }
        }
        return null;
    }

    @NonNull
    public String nameFor(@NonNull String uid, @NonNull String fallback) {
        String name = memberNames.get(uid);
        return name == null || name.trim().isEmpty() ? fallback : name;
    }

    @Nullable
    public String photoFor(@NonNull String uid) {
        String photo = memberPhotos.get(uid);
        return photo == null || photo.trim().isEmpty() ? null : photo;
    }

    public boolean hasImagePreview() {
        return "image".equals(lastMessageType);
    }

    public int unreadFor(@NonNull String uid) {
        Long count = unread.get(uid);
        return count == null ? 0 : (int) Math.min(count, Integer.MAX_VALUE);
    }

    @Nullable
    public Timestamp lastReadFor(@NonNull String uid) {
        return lastRead.get(uid);
    }

    public boolean isTyping(@NonNull String uid, long nowMillis, long windowMillis) {
        Long timestamp = typing.get(uid);
        if (timestamp == null) {
            return false;
        }
        return nowMillis - timestamp <= windowMillis;
    }

    public long lastActivityMillis() {
        Timestamp timestamp = lastMessageAt != null ? lastMessageAt : createdAt;
        return timestamp == null ? 0L : timestamp.toDate().getTime();
    }
}
