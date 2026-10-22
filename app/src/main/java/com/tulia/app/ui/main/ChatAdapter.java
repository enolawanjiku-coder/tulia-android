package com.tulia.app.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.tulia.app.R;
import com.tulia.app.data.model.Chat;
import com.tulia.app.databinding.ItemChatBinding;
import com.tulia.app.util.Initials;
import com.tulia.app.util.TimeFormat;

import java.util.Objects;

public class ChatAdapter extends ListAdapter<Chat, ChatAdapter.ChatHolder> {

    public interface Listener {
        void onChatClicked(@NonNull Chat chat);
    }

    private static final DiffUtil.ItemCallback<Chat> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
                    return oldItem.id.equals(newItem.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Chat oldItem, @NonNull Chat newItem) {
                    return Objects.equals(oldItem.lastMessage, newItem.lastMessage)
                            && Objects.equals(oldItem.lastMessageAt, newItem.lastMessageAt)
                            && Objects.equals(oldItem.lastMessageSenderId,
                            newItem.lastMessageSenderId)
                            && oldItem.unread.equals(newItem.unread)
                            && oldItem.memberNames.equals(newItem.memberNames)
                            && oldItem.memberPhotos.equals(newItem.memberPhotos)
                            && oldItem.lastMessageType.equals(newItem.lastMessageType);
                }
            };

    private final String currentUid;
    private final Listener listener;

    public ChatAdapter(@NonNull String currentUid, @NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.currentUid = currentUid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatBinding binding = ItemChatBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ChatHolder extends RecyclerView.ViewHolder {

        private final ItemChatBinding binding;

        ChatHolder(ItemChatBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Chat chat) {
            Context context = binding.getRoot().getContext();
            String otherUid = chat.otherUid(currentUid);
            String name = chat.nameFor(otherUid == null ? "" : otherUid, "Tulia user");
            int unread = chat.unreadFor(currentUid);
            boolean hasUnread = unread > 0;

            binding.avatar.setText(Initials.of(name));
            bindPhoto(chat.photoFor(otherUid == null ? "" : otherUid));
            binding.name.setText(name);
            binding.time.setText(TimeFormat.chatTime(context, chat.lastActivityMillis()));

            String body;
            if (chat.hasImagePreview()) {
                body = context.getString(R.string.photo_preview);
            } else if (chat.lastMessage.isEmpty()) {
                body = context.getString(R.string.chat_no_messages);
            } else {
                body = chat.lastMessage;
            }
            boolean hasMessage = chat.hasImagePreview() || !chat.lastMessage.isEmpty();
            String preview = hasMessage && currentUid.equals(chat.lastMessageSenderId)
                    ? context.getString(R.string.you_prefix, body)
                    : body;
            binding.preview.setText(preview);
            binding.preview.setTextColor(ContextCompat.getColor(context,
                    hasUnread ? R.color.tulia_ink : R.color.tulia_dust));
            binding.time.setTextColor(ContextCompat.getColor(context,
                    hasUnread ? R.color.tulia_red : R.color.tulia_dust));

            binding.unread.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            binding.unread.setText(unread > 99
                    ? context.getString(R.string.unread_overflow)
                    : java.text.NumberFormat.getIntegerInstance().format(unread));

            binding.getRoot().setOnClickListener(v -> listener.onChatClicked(chat));
        }

        private void bindPhoto(@Nullable String url) {
            binding.avatarPhoto.setImageDrawable(null);
            if (url == null) {
                Glide.with(binding.avatarPhoto).clear(binding.avatarPhoto);
                binding.avatarPhoto.setVisibility(View.GONE);
                return;
            }
            binding.avatarPhoto.setVisibility(View.VISIBLE);
            Glide.with(binding.avatarPhoto)
                    .load(url)
                    .into(binding.avatarPhoto);
        }
    }
}
