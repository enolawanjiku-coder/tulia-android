package com.tulia.app.ui.main;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.tulia.app.R;
import com.tulia.app.data.ChatPrefsRepository;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.model.Chat;
import com.tulia.app.databinding.FragmentChatsBinding;
import com.tulia.app.ui.chat.ChatFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChatsFragment extends Fragment {

    private static final String TAG = "ChatsFragment";

    private static final String STATE_FILTER = "chats_filter";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_UNREAD = 1;
    private static final int FILTER_PINNED = 2;
    private static final int FILTER_ARCHIVED = 3;

    private FragmentChatsBinding binding;
    private ChatAdapter adapter;
    private ListenerRegistration chatsRegistration;
    private ListenerRegistration prefsRegistration;
    private List<Chat> chats = Collections.emptyList();
    private int filter = FILTER_ALL;
    private String uid;

    public ChatsFragment() {
        super(R.layout.fragment_chats);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentChatsBinding.bind(view);

        if (savedInstanceState != null) {
            filter = savedInstanceState.getInt(STATE_FILTER, FILTER_ALL);
        }

        FirebaseUser user = currentUser();
        if (user == null) {
            Log.w(TAG, "No signed-in user.");
            return;
        }
        uid = user.getUid();

        adapter = new ChatAdapter(uid, new ChatAdapter.Listener() {
            @Override
            public void onChatClicked(@NonNull Chat chat) {
                openChat(chat);
            }

            @Override
            public void onChatLongPressed(@NonNull Chat chat) {
                showChatActions(chat);
            }
        });
        binding.chatList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.chatList.setAdapter(adapter);

        binding.newChatFab.setOnClickListener(v -> openNewChat());
        binding.searchButton.setOnClickListener(v -> openNewChat());
        binding.menuButton.setOnClickListener(v ->
                Snackbar.make(binding.getRoot(), R.string.menu_soon, Snackbar.LENGTH_SHORT).show());

        setupFilters();
        listenForChats();
        listenForPrefs();
    }

    private void setupFilters() {
        binding.filterGroup.check(filterChip(filter));
        binding.filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            filter = filterFor(checkedIds);
            renderChats();
        });
    }

    private int filterFor(@NonNull List<Integer> checkedIds) {
        if (checkedIds.isEmpty()) {
            return FILTER_ALL;
        }
        int chipId = checkedIds.get(0);
        if (chipId == R.id.filter_unread) {
            return FILTER_UNREAD;
        }
        if (chipId == R.id.filter_pinned) {
            return FILTER_PINNED;
        }
        if (chipId == R.id.filter_archived) {
            return FILTER_ARCHIVED;
        }
        return FILTER_ALL;
    }

    private int filterChip(int filter) {
        if (filter == FILTER_UNREAD) {
            return R.id.filter_unread;
        }
        if (filter == FILTER_PINNED) {
            return R.id.filter_pinned;
        }
        if (filter == FILTER_ARCHIVED) {
            return R.id.filter_archived;
        }
        return R.id.filter_all;
    }

    private void listenForChats() {
        chatsRegistration = ChatRepository.listenChats(uid, new ChatRepository.ChatListCallback() {
            @Override
            public void onChats(@NonNull List<Chat> loaded) {
                if (binding == null) {
                    return;
                }
                chats = loaded;
                renderChats();
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

    private void listenForPrefs() {
        ChatPrefsRepository.load(uid, new ChatPrefsRepository.LoadCallback() {
            @Override
            public void onLoaded() {
                renderChats();
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load the chat preferences.", exception);
            }
        });
        prefsRegistration = ChatPrefsRepository.listen(uid, this::renderChats);
    }

    private void renderChats() {
        if (binding == null || adapter == null) {
            return;
        }
        Map<String, Boolean> pinned = ChatPrefsRepository.pinnedChats(uid);
        Map<String, Boolean> muted = ChatPrefsRepository.mutedChats(uid);
        Map<String, Boolean> archived = ChatPrefsRepository.archivedChats(uid);

        List<Chat> visible = new ArrayList<>();
        for (Chat chat : chats) {
            Chat flagged = chat.withPrefs(pinned, muted, archived);
            if (matchesFilter(flagged)) {
                visible.add(flagged);
            }
        }
        Collections.sort(visible, (first, second) -> {
            if (first.pinned != second.pinned) {
                return first.pinned ? -1 : 1;
            }
            return Long.compare(second.lastActivityMillis(), first.lastActivityMillis());
        });

        adapter.submitList(visible);
        boolean empty = visible.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.chatList.setVisibility(empty ? View.GONE : View.VISIBLE);
        renderEmptyState();
    }

    private boolean matchesFilter(@NonNull Chat chat) {
        switch (filter) {
            case FILTER_UNREAD:
                return !chat.archived && chat.unreadFor(uid) > 0;
            case FILTER_PINNED:
                return chat.pinned && !chat.archived;
            case FILTER_ARCHIVED:
                return chat.archived;
            case FILTER_ALL:
            default:
                return !chat.archived;
        }
    }

    private void renderEmptyState() {
        if (binding == null) {
            return;
        }
        if (filter == FILTER_ALL) {
            binding.emptyTitle.setText(R.string.chats_empty_title);
            binding.emptyBody.setText(R.string.chats_empty_body);
            return;
        }
        binding.emptyTitle.setText(R.string.chats_empty_filtered_title);
        binding.emptyBody.setText(R.string.chats_empty_filtered_body);
    }

    private void showChatActions(@NonNull Chat chat) {
        if (binding == null || !isAdded()) {
            return;
        }
        String otherUid = chat.otherUid(uid);
        String name = chat.nameFor(otherUid == null ? "" : otherUid,
                getString(R.string.guest_name));
        String[] actions = new String[]{
                getString(chat.pinned ? R.string.chats_action_unpin : R.string.chats_action_pin),
                getString(chat.muted ? R.string.chats_action_unmute : R.string.chats_action_mute),
                getString(chat.archived
                        ? R.string.chats_action_unarchive : R.string.chats_action_archive),
                getString(chat.unreadFor(uid) > 0
                        ? R.string.chats_action_mark_read : R.string.chats_action_mark_unread)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(name)
                .setItems(actions, (dialog, which) -> onChatAction(chat, which))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onChatAction(@NonNull Chat chat, int which) {
        if (which == 0) {
            updatePinned(chat);
        } else if (which == 1) {
            updateMuted(chat);
        } else if (which == 2) {
            updateArchived(chat);
        } else {
            updateRead(chat);
        }
    }

    private void updatePinned(@NonNull Chat chat) {
        ChatPrefsRepository.setPinned(uid, chat.id, !chat.pinned,
                new ChatPrefsRepository.PrefsCallback() {
                    @Override
                    public void onUpdated() {
                        showMessage(R.string.chats_action_done);
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not pin the chat.", exception);
                        showMessage(R.string.chats_action_failed);
                    }
                });
    }

    private void updateMuted(@NonNull Chat chat) {
        ChatPrefsRepository.setMuted(uid, chat.id, !chat.muted,
                new ChatPrefsRepository.PrefsCallback() {
                    @Override
                    public void onUpdated() {
                        showMessage(R.string.chats_action_done);
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not mute the chat.", exception);
                        showMessage(R.string.chats_action_failed);
                    }
                });
    }

    private void updateArchived(@NonNull Chat chat) {
        ChatPrefsRepository.setArchived(uid, chat.id, !chat.archived,
                new ChatPrefsRepository.PrefsCallback() {
                    @Override
                    public void onUpdated() {
                        showMessage(R.string.chats_action_done);
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not archive the chat.", exception);
                        showMessage(R.string.chats_action_failed);
                    }
                });
    }

    private void updateRead(@NonNull Chat chat) {
        if (chat.unreadFor(uid) > 0) {
            ChatPrefsRepository.markRead(chat.id, uid);
            return;
        }
        ChatPrefsRepository.markUnread(chat.id, uid);
    }

    private void showMessage(int messageRes) {
        if (binding == null) {
            return;
        }
        Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_SHORT).show();
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filter);
    }

    @Override
    public void onDestroyView() {
        if (chatsRegistration != null) {
            chatsRegistration.remove();
            chatsRegistration = null;
        }
        if (prefsRegistration != null) {
            prefsRegistration.remove();
            prefsRegistration = null;
        }
        if (binding != null) {
            binding.chatList.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
    }
}
