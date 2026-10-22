package com.tulia.app.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MessageItem {

    public static final int TYPE_DATE = 0;
    public static final int TYPE_SENT = 1;
    public static final int TYPE_RECEIVED = 2;
    public static final int TYPE_EMOJI = 3;

    public static final int TICK_NONE = 0;
    public static final int TICK_SENT = 1;
    public static final int TICK_DELIVERED = 2;
    public static final int TICK_READ = 3;
    public static final int TICK_PENDING = 4;

    public final int type;
    public final String id;
    public final String text;
    public final String time;
    public final boolean lastInGroup;
    public final int tick;
    @Nullable
    public final String quoteName;
    @Nullable
    public final String quoteText;
    public final boolean edited;
    public final boolean deleted;
    @NonNull
    public final List<Reaction> reactions;
    @Nullable
    public final String imageUrl;

    private MessageItem(int type,
                          String id,
                          String text,
                          String time,
                          boolean lastInGroup,
                          int tick,
                          @Nullable String quoteName,
                          @Nullable String quoteText,
                          boolean edited,
                          boolean deleted,
                          @NonNull List<Reaction> reactions,
                          @Nullable String imageUrl) {
        this.type = type;
        this.id = id;
        this.text = text;
        this.time = time;
        this.lastInGroup = lastInGroup;
        this.tick = tick;
        this.quoteName = quoteName;
        this.quoteText = quoteText;
        this.edited = edited;
        this.deleted = deleted;
        this.reactions = reactions;
        this.imageUrl = imageUrl;
    }

    public static MessageItem date(String id, String text) {
        return new MessageItem(TYPE_DATE, id, text, "", false, TICK_NONE,
                null, null, false, false, Collections.emptyList(), null);
    }

    public static MessageItem emoji(String id, String emoji) {
        return new MessageItem(TYPE_EMOJI, id, emoji, "", false, TICK_NONE,
                null, null, false, false, Collections.emptyList(), null);
    }

    public static MessageItem sent(String id, String text, String time,
                                     boolean lastInGroup, int tick,
                                     @Nullable String quoteName,
                                     @Nullable String quoteText,
                                     boolean edited, boolean deleted,
                                     @NonNull List<Reaction> reactions) {
        return new MessageItem(TYPE_SENT, id, text, time, lastInGroup, tick,
                quoteName, quoteText, edited, deleted, reactions, null);
    }

    public static MessageItem received(String id, String text, String time,
                                         boolean lastInGroup,
                                         @Nullable String quoteName,
                                         @Nullable String quoteText,
                                         boolean edited, boolean deleted,
                                         @NonNull List<Reaction> reactions) {
        return new MessageItem(TYPE_RECEIVED, id, text, time, lastInGroup, TICK_NONE,
                quoteName, quoteText, edited, deleted, reactions, null);
    }

    public static MessageItem sentImage(String id, String imageUrl, String time,
                                          boolean lastInGroup, int tick,
                                          @Nullable String quoteName,
                                          @Nullable String quoteText,
                                          boolean edited, boolean deleted,
                                          @NonNull List<Reaction> reactions) {
        return new MessageItem(TYPE_SENT, id, "", time, lastInGroup, tick,
                quoteName, quoteText, edited, deleted, reactions, imageUrl);
    }

    public static MessageItem receivedImage(String id, String imageUrl, String time,
                                             boolean lastInGroup,
                                             @Nullable String quoteName,
                                             @Nullable String quoteText,
                                             boolean edited, boolean deleted,
                                             @NonNull List<Reaction> reactions) {
        return new MessageItem(TYPE_RECEIVED, id, "", time, lastInGroup, TICK_NONE,
                quoteName, quoteText, edited, deleted, reactions, imageUrl);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageItem)) {
            return false;
        }
        MessageItem message = (MessageItem) other;
        return type == message.type
                && lastInGroup == message.lastInGroup
                && tick == message.tick
                && edited == message.edited
                && deleted == message.deleted
                && Objects.equals(id, message.id)
                && Objects.equals(text, message.text)
                && Objects.equals(time, message.time)
                && Objects.equals(quoteName, message.quoteName)
                && Objects.equals(quoteText, message.quoteText)
                && Objects.equals(reactions, message.reactions)
                && Objects.equals(imageUrl, message.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, text, time, lastInGroup, tick, quoteName,
                quoteText, edited, deleted, reactions, imageUrl);
    }

    public static final class Reaction {

        @NonNull
        public final String type;
        public final int count;
        public final boolean mine;

        public Reaction(@NonNull String type, int count, boolean mine) {
            this.type = type;
            this.count = count;
            this.mine = mine;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Reaction)) {
                return false;
            }
            Reaction reaction = (Reaction) other;
            return count == reaction.count
                    && mine == reaction.mine
                    && Objects.equals(type, reaction.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, count, mine);
        }
    }
}
