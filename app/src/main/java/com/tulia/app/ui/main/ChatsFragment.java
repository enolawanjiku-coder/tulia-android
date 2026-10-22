package com.tulia.app.ui.main;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.model.Chat;
import com.tulia.app.databinding.FragmentChatsBinding;
import com.tulia.app.ui.chat.ChatFragment;

import java.util.List;

public class ChatsFragment extends Fragment {

    private static final String TAG = "ChatsFragment";

    private FragmentChatsBinding binding;
    private ChatAdapter adapter;
    private ListenerRegistration chatsRegistration;
    private String uid;

    public ChatsFragment() {
        super(R.layout.fragment_chats);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentChatsBinding.bind(view);

        FirebaseUser user = currentUser();
        if (user == null) {
            Log.w(TAG, "No signed-in user.");
            return;
        }
        uid = user.getUid();

        adapter = new ChatAdapter(uid, this::openChat);
        binding.chatList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.chatList.setAdapter(adapter);

        binding.newChatFab.setOnClickListener(v -> openNewChat());
        binding.searchButton.setOnClickListener(v -> openNewChat());
        binding.menuButton.setOnClickListener(v ->
                Snackbar.make(binding.getRoot(), R.string.menu_soon, Snackbar.LENGTH_SHORT).show());

        listenForChats();
    }

    private void listenForChats() {
        chatsRegistration = ChatRepository.listenChats(uid, new ChatRepository.ChatListCallback() {
            @Override
            public void onChats(@NonNull List<Chat> chats) {
                if (binding == null) {
                    return;
                }
                adapter.submitList(chats);
                boolean empty = chats.isEmpty();
                binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.chatList.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (binding == null) {
                    return;
                }
                Log.w(TAG, "Could not listen for chats.", exception);
                Snackbar.make(binding.getRoot(), R.string.chat_list_error,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void openNewChat() {
        NavHostFragment.findNavController(this).navigate(R.id.action_chats_to_new_chat);
    }

    private void openChat(@NonNull Chat chat) {
        String otherUid = chat.otherUid(uid);
        if (otherUid == null) {
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putString(ChatFragment.ARG_CHAT_ID, chat.id);
        arguments.putString(ChatFragment.ARG_OTHER_UID, otherUid);
        arguments.putString(ChatFragment.ARG_OTHER_NAME,
                chat.nameFor(otherUid, getString(R.string.guest_name)));
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_chats_to_chat, arguments);
    }

    private FirebaseUser currentUser() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        if (chatsRegistration != null) {
            chatsRegistration.remove();
            chatsRegistration = null;
        }
        if (binding != null) {
            binding.chatList.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
    }
}
