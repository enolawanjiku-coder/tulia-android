package com.tulia.app.ui.chat;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.tulia.app.R;
import com.tulia.app.databinding.ItemDateChipBinding;
import com.tulia.app.databinding.ItemMessageEmojiBinding;
import com.tulia.app.databinding.ItemMessageReceivedBinding;
import com.tulia.app.databinding.ItemMessageSentBinding;
import com.tulia.app.databinding.ItemReactionChipBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MessageAdapter extends ListAdapter<MessageItem, RecyclerView.ViewHolder> {

    public interface Listener {
        void onMessageLongPressed(@NonNull MessageItem message);

        void onMessageVisible(@NonNull String messageId);

        void onReactionToggled(@NonNull String messageId, @NonNull String type);
    }

    private static final int VIEW_DATE = 0;
    private static final int VIEW_SENT = 1;
    private static final int VIEW_RECEIVED = 2;
    private static final int VIEW_EMOJI = 3;

    private static final DiffUtil.ItemCallback<MessageItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull MessageItem oldItem,
                                               @NonNull MessageItem newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull MessageItem oldItem,
                                                  @NonNull MessageItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

    @Nullable
    private final Listener listener;
    private final Set<String> visibleIds = new LinkedHashSet<>();

    public MessageAdapter(@Nullable Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem message = getItem(position);
        switch (message.type) {
            case MessageItem.TYPE_DATE:
                return VIEW_DATE;
            case MessageItem.TYPE_RECEIVED:
                return VIEW_RECEIVED;
            case MessageItem.TYPE_EMOJI:
                return VIEW_EMOJI;
            case MessageItem.TYPE_SENT:
            default:
                return VIEW_SENT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_DATE:
                return new DateHolder(ItemDateChipBinding.inflate(inflater, parent, false));
            case VIEW_RECEIVED:
                return new ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false));
            case VIEW_EMOJI:
                return new EmojiHolder(ItemMessageEmojiBinding.inflate(inflater, parent, false));
            case VIEW_SENT:
            default:
                return new SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageItem message = getItem(position);
        applyTopMargin(holder.itemView, position);

        if (holder instanceof DateHolder) {
            ((DateHolder) holder).bind(message);
        } else if (holder instanceof SentHolder) {
            ((SentHolder) holder).bind(message);
        } else if (holder instanceof ReceivedHolder) {
            ((ReceivedHolder) holder).bind(message);
        } else if (holder instanceof EmojiHolder) {
            ((EmojiHolder) holder).bind(message);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        MessageItem message = getItem(position);
        if (message.type == MessageItem.TYPE_DATE) {
            return;
        }
        visibleIds.add(message.id);
        if (listener != null) {
            listener.onMessageVisible(message.id);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        visibleIds.remove(getItem(position).id);
    }

    @NonNull
    public List<String> visibleMessageIds() {
        return new ArrayList<>(visibleIds);
    }

    private void applyTopMargin(View itemView, int position) {
        if (position == 0) {
            return;
        }
        MessageItem current = getItem(position);
        if (current.type == MessageItem.TYPE_DATE) {
            return;
        }
        MessageItem previous = getItem(position - 1);
        int sender = senderOf(current.type);
        int previousSender = senderOf(previous.type);
        int marginDp = (sender != 0 && sender == previousSender) ? 2 : 10;

        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (params instanceof RecyclerView.LayoutParams) {
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) params;
            layoutParams.topMargin = dp(itemView, marginDp);
            itemView.setLayoutParams(layoutParams);
        }
    }

    private static int senderOf(int type) {
        if (type == MessageItem.TYPE_SENT || type == MessageItem.TYPE_EMOJI) {
            return 1;
        }
        if (type == MessageItem.TYPE_RECEIVED) {
            return 2;
        }
        return 0;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static void bindImage(View itemView, ImageView imageView, MessageItem message) {
        boolean hasImage = !message.deleted
                && message.imageUrl != null && !message.imageUrl.isEmpty();
        imageView.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (!hasImage) {
            Glide.with(imageView).clear(imageView);
            imageView.setImageDrawable(null);
            return;
        }
        int radius = dp(itemView, 14);
        Glide.with(imageView)
                .load(message.imageUrl)
                .transform(new CenterCrop(), new RoundedCorners(radius))
                .into(imageView);
    }

    private void bindReactions(LinearLayout container, MessageItem message) {
        container.removeAllViews();
        if (message.reactions.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (MessageItem.Reaction reaction : message.reactions) {
            ItemReactionChipBinding chip = ItemReactionChipBinding.inflate(inflater, container, false);
            TextView view = chip.reactionChipText;
            view.setText(reaction.type + " " + reaction.count);
            view.setTypeface(null, reaction.mine ? Typeface.BOLD : Typeface.NORMAL);
            view.setTextColor(container.getContext().getColor(
                    reaction.mine ? R.color.tulia_red : R.color.tulia_ink));
            view.setContentDescription(container.getContext().getString(
                    R.string.chat_reaction_chip_description, reaction.type, reaction.count));
            view.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReactionToggled(message.id, reaction.type);
                }
            });
            container.addView(chip.getRoot());
        }
        container.setVisibility(View.VISIBLE);
    }

    private class DateHolder extends RecyclerView.ViewHolder {
        private final ItemDateChipBinding binding;

        DateHolder(ItemDateChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.dateText.setText(message.text);
        }
    }

    private class EmojiHolder extends RecyclerView.ViewHolder {
        private final ItemMessageEmojiBinding binding;

        EmojiHolder(ItemMessageEmojiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.emojiText.setText(message.text);
        }
    }

    private class SentHolder extends RecyclerView.ViewHolder {
        private final ItemMessageSentBinding binding;

        SentHolder(ItemMessageSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.sentBubble.setTail(message.lastInGroup);
            binding.sentBubble.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });
            boolean deleted = message.deleted;
            binding.messageText.setText(deleted
                    ? itemView.getContext().getString(R.string.chat_message_deleted)
                    : message.text);
            binding.messageText.setVisibility(message.text.isEmpty() && !deleted
                    ? View.GONE : View.VISIBLE);
            binding.messageText.setAlpha(deleted ? 0.75f : 1f);
            bindImage(itemView, binding.messageImage, message);
            binding.messageTime.setText(message.time);
            binding.editedLabel.setVisibility(message.edited ? View.VISIBLE : View.GONE);
            bindQuote(message);
            bindReactions(binding.reactionContainer, message);
            bindTicks(message);
        }

        private void bindQuote(MessageItem message) {
            boolean hasQuote = message.quoteName != null && !message.quoteName.isEmpty();
            binding.quoteContainer.setVisibility(hasQuote ? View.VISIBLE : View.GONE);
            if (!hasQuote) {
                return;
            }
            binding.quoteName.setText(message.quoteName);
            binding.quoteText.setText(message.quoteText == null ? "" : message.quoteText);
            binding.quoteText.setVisibility(
                    message.quoteText == null || message.quoteText.isEmpty()
                            ? View.GONE : View.VISIBLE);
        }

        private void bindTicks(MessageItem message) {
            int icon = R.drawable.ic_done;
            int description = R.string.ticks_sent;
            float alpha = 1f;

            switch (message.tick) {
                case MessageItem.TICK_DELIVERED:
                    icon = R.drawable.ic_done_all;
                    description = R.string.ticks_delivered;
                    alpha = 0.6f;
                    break;
                case MessageItem.TICK_READ:
                    icon = R.drawable.ic_done_all;
                    description = R.string.ticks_read;
                    alpha = 1f;
                    break;
                case MessageItem.TICK_PENDING:
                    icon = R.drawable.ic_schedule;
                    description = R.string.ticks_pending;
                    alpha = 0.75f;
                    break;
                case MessageItem.TICK_SENT:
                default:
                    break;
            }

            binding.messageTicks.setImageResource(icon);
            binding.messageTicks.setAlpha(alpha);
            binding.messageTicks.setContentDescription(itemView.getContext()
                    .getString(description));
        }
    }

    private class ReceivedHolder extends RecyclerView.ViewHolder {
        private final ItemMessageReceivedBinding binding;

        ReceivedHolder(ItemMessageReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.receivedBubble.setTail(message.lastInGroup);
            binding.receivedBubble.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongPressed(message);
                }
                return true;
            });
            boolean deleted = message.deleted;
            binding.messageText.setText(deleted
                    ? itemView.getContext().getString(R.string.chat_message_deleted)
                    : message.text);
            binding.messageText.setVisibility(message.text.isEmpty() && !deleted
                    ? View.GONE : View.VISIBLE);
            binding.messageText.setAlpha(deleted ? 0.75f : 1f);
            bindImage(itemView, binding.messageImage, message);
            binding.messageTime.setText(message.time);
            binding.editedLabel.setVisibility(message.edited ? View.VISIBLE : View.GONE);
            bindQuote(message);
            bindReactions(binding.reactionContainer, message);
        }

        private void bindQuote(MessageItem message) {
            boolean hasQuote = message.quoteName != null && !message.quoteName.isEmpty();
            binding.quoteContainer.setVisibility(hasQuote ? View.VISIBLE : View.GONE);
            if (!hasQuote) {
                return;
            }
            binding.quoteName.setText(message.quoteName);
            binding.quoteText.setText(message.quoteText == null ? "" : message.quoteText);
            binding.quoteText.setVisibility(
                    message.quoteText == null || message.quoteText.isEmpty()
                            ? View.GONE : View.VISIBLE);
        }
    }
}
