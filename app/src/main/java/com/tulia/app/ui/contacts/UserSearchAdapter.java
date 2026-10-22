package com.tulia.app.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.tulia.app.R;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.ItemUserSearchBinding;
import com.tulia.app.util.Initials;

import java.util.Objects;

public class UserSearchAdapter
        extends ListAdapter<UserProfile, UserSearchAdapter.UserHolder> {

    public interface Listener {
        void onUserClicked(@NonNull UserProfile user);
    }

    private static final DiffUtil.ItemCallback<UserProfile> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull UserProfile oldItem,
                                               @NonNull UserProfile newItem) {
                    return oldItem.uid.equals(newItem.uid);
                }

                @Override
                public boolean areContentsTheSame(@NonNull UserProfile oldItem,
                                                  @NonNull UserProfile newItem) {
                    return Objects.equals(oldItem.name, newItem.name)
                            && Objects.equals(oldItem.username, newItem.username);
                }
            };

    private final Listener listener;

    public UserSearchAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserSearchBinding binding = ItemUserSearchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new UserHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class UserHolder extends RecyclerView.ViewHolder {

        private final ItemUserSearchBinding binding;

        UserHolder(ItemUserSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull UserProfile user) {
            binding.avatar.setText(Initials.of(user.name));
            binding.avatarPhoto.setImageDrawable(null);
            if (user.photoUrl == null || user.photoUrl.trim().isEmpty()) {
                Glide.with(binding.avatarPhoto).clear(binding.avatarPhoto);
                binding.avatarPhoto.setVisibility(View.GONE);
            } else {
                binding.avatarPhoto.setVisibility(View.VISIBLE);
                Glide.with(binding.avatarPhoto).load(user.photoUrl)
                        .into(binding.avatarPhoto);
            }
            binding.name.setText(user.name);
            binding.username.setText(binding.getRoot().getContext()
                    .getString(R.string.username_with_at, user.username));
            binding.getRoot().setOnClickListener(v -> listener.onUserClicked(user));
        }
    }
}
