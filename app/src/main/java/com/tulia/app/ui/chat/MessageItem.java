package com.tulia.app.ui.chat;

import androidx.annotation.Nullable;

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
    public final String quote;
    @Nullable
    public final String reaction;
    @Nullable
    public final String imageUrl;

    private MessageItem(int type,
                          String id,
                          String text,
                          String time,
                          boolean lastInGroup,
                          int tick,
                          @Nullable String quote,
                          @Nullable String reaction,
                          @Nullable String imageUrl) {
        this.type = type;
        this.id = id;
        this.text = text;
        this.time = time;
        this.lastInGroup = lastInGroup;
        this.tick = tick;
        this.quote = quote;
        this.reaction = reaction;
        this.imageUrl = imageUrl;
    }

    public static MessageItem date(String id, String text) {
        return new MessageItem(TYPE_DATE, id, text, "", false, TICK_NONE,
                null, null, null);
    }

    public static MessageItem sent(String id, String text, String time,
                                     boolean lastInGroup, int tick) {
        return new MessageItem(TYPE_SENT, id, text, time, lastInGroup, tick,
                null, null, null);
    }

    public static MessageItem sent(String id, String text, String time,
                                     boolean lastInGroup, int tick,
                                     @Nullable String quote, @Nullable String reaction) {
        return new MessageItem(TYPE_SENT, id, text, time, lastInGroup, tick,
                quote, reaction, null);
    }

    public static MessageItem received(String id, String text, String time,
                                         boolean lastInGroup) {
        return new MessageItem(TYPE_RECEIVED, id, text, time, lastInGroup, TICK_NONE,
                null, null, null);
    }

    public static MessageItem received(String id, String text, String time,
                                         boolean lastInGroup,
                                         @Nullable String quote, @Nullable String reaction) {
        return new MessageItem(TYPE_RECEIVED, id, text, time, lastInGroup, TICK_NONE,
                quote, reaction, null);
    }

    public static MessageItem emoji(String id, String emoji) {
        return new MessageItem(TYPE_EMOJI, id, emoji, "", false, TICK_NONE,
                null, null, null);
    }

    public static MessageItem sentImage(String id, String imageUrl, String time,
                                          boolean lastInGroup, int tick) {
        return new MessageItem(TYPE_SENT, id, "", time, lastInGroup, tick,
                null, null, imageUrl);
    }

    public static MessageItem receivedImage(String id, String imageUrl, String time,
                                            boolean lastInGroup) {
        return new MessageItem(TYPE_RECEIVED, id, "", time, lastInGroup, TICK_NONE,
                null, null, imageUrl);
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
                && Objects.equals(id, message.id)
                && Objects.equals(text, message.text)
                && Objects.equals(time, message.time)
                && Objects.equals(quote, message.quote)
                && Objects.equals(reaction, message.reaction)
                && Objects.equals(imageUrl, message.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id, text, time, lastInGroup, tick, quote, reaction,
                imageUrl);
    }
}
