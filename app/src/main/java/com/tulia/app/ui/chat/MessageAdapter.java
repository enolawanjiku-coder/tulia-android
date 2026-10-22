package com.tulia.app.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
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

public class MessageAdapter extends ListAdapter<MessageItem, RecyclerView.ViewHolder> {

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

    public MessageAdapter() {
        super(DIFF_CALLBACK);
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
        boolean hasImage = message.imageUrl != null && !message.imageUrl.isEmpty();
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

    private static void applyReactionSpace(View itemView, boolean hasReaction) {
        int bottom = hasReaction ? dp(itemView, 18) : 0;
        itemView.setPadding(
                itemView.getPaddingLeft(),
                itemView.getPaddingTop(),
                itemView.getPaddingRight(),
                bottom);
    }

    private static class DateHolder extends RecyclerView.ViewHolder {
        private final ItemDateChipBinding binding;

        DateHolder(ItemDateChipBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.dateText.setText(message.text);
        }
    }

    private static class EmojiHolder extends RecyclerView.ViewHolder {
        private final ItemMessageEmojiBinding binding;

        EmojiHolder(ItemMessageEmojiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.emojiText.setText(message.text);
        }
    }

    private static class SentHolder extends RecyclerView.ViewHolder {
        private final ItemMessageSentBinding binding;

        SentHolder(ItemMessageSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.sentBubble.setTail(message.lastInGroup);
            binding.messageText.setText(message.text);
            binding.messageText.setVisibility(message.text.isEmpty() ? View.GONE : View.VISIBLE);
            bindImage(itemView, binding.messageImage, message);
            binding.messageTime.setText(message.time);
            bindQuote(message);
            bindReaction(message);
            bindTicks(message);
        }

        private void bindQuote(MessageItem message) {
            boolean hasQuote = message.quote != null && !message.quote.isEmpty();
            binding.quoteContainer.setVisibility(hasQuote ? View.VISIBLE : View.GONE);
            if (hasQuote) {
                binding.quoteText.setText(message.quote);
            }
        }

        private void bindReaction(MessageItem message) {
            boolean hasReaction = message.reaction != null && !message.reaction.isEmpty();
            binding.reactionPill.setVisibility(hasReaction ? View.VISIBLE : View.GONE);
            if (hasReaction) {
                binding.reactionPill.setText(message.reaction);
                binding.reactionPill.setContentDescription(itemView.getContext().getString(
                        R.string.reaction_content_description, message.reaction));
            }
            applyReactionSpace(itemView, hasReaction);
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

    private static class ReceivedHolder extends RecyclerView.ViewHolder {
        private final ItemMessageReceivedBinding binding;

        ReceivedHolder(ItemMessageReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MessageItem message) {
            binding.receivedBubble.setTail(message.lastInGroup);
            binding.messageText.setText(message.text);
            binding.messageText.setVisibility(message.text.isEmpty() ? View.GONE : View.VISIBLE);
            bindImage(itemView, binding.messageImage, message);
            binding.messageTime.setText(message.time);
            bindQuote(message);
            bindReaction(message);
        }

        private void bindQuote(MessageItem message) {
            boolean hasQuote = message.quote != null && !message.quote.isEmpty();
            binding.quoteContainer.setVisibility(hasQuote ? View.VISIBLE : View.GONE);
            if (hasQuote) {
                binding.quoteText.setText(message.quote);
            }
        }

        private void bindReaction(MessageItem message) {
            boolean hasReaction = message.reaction != null && !message.reaction.isEmpty();
            binding.reactionPill.setVisibility(hasReaction ? View.VISIBLE : View.GONE);
            if (hasReaction) {
                binding.reactionPill.setText(message.reaction);
                binding.reactionPill.setContentDescription(itemView.getContext().getString(
                        R.string.reaction_content_description, message.reaction));
            }
            applyReactionSpace(itemView, hasReaction);
        }
    }
}
