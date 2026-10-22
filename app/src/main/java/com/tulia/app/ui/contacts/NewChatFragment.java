package com.tulia.app.ui.contacts;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentNewChatBinding;
import com.tulia.app.ui.chat.ChatFragment;

import java.util.Collections;
import java.util.List;

public class NewChatFragment extends Fragment {

    private static final String TAG = "NewChatFragment";
    private static final long SEARCH_DELAY_MS = 300L;

    private FragmentNewChatBinding binding;
    private UserSearchAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::runSearch;
    private String latestQuery = "";
    private String uid;
    private boolean openingChat;

    public NewChatFragment() {
        super(R.layout.fragment_new_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentNewChatBinding.bind(view);

        FirebaseUser user = currentUser();
        if (user == null) {
            showEmpty(R.string.auth_error_generic);
            return;
        }
        uid = user.getUid();

        adapter = new UserSearchAdapter(this::openChatWith);
        binding.resultsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.resultsList.setAdapter(adapter);
        binding.backButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        binding.searchEdit.addTextChangedListener(new SimpleTextWatcher(this::onQueryChanged));
        binding.searchEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(searchRunnable);
                runSearch();
                return true;
            }
            return false;
        });
    }

    private void onQueryChanged(@NonNull String text) {
        latestQuery = UserRepository.normalizeUsername(text);
        handler.removeCallbacks(searchRunnable);

        if (latestQuery.length() < 2) {
            binding.progress.setVisibility(View.GONE);
            adapter.submitList(Collections.emptyList());
            showEmpty(R.string.user_search_hint);
            return;
        }

        binding.emptyText.setVisibility(View.GONE);
        binding.progress.setVisibility(View.VISIBLE);
        handler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
    }

    private void runSearch() {
        if (binding == null) {
            return;
        }
        String query = latestQuery;
        if (query.length() < 2) {
            return;
        }

        UserRepository.searchByUsername(query, uid, new UserRepository.SearchCallback() {
            @Override
            public void onResults(@NonNull List<UserProfile> users) {
                if (binding == null || !query.equals(latestQuery)) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                adapter.submitList(users);
                if (users.isEmpty()) {
                    showEmpty(R.string.user_search_empty);
                } else {
                    binding.emptyText.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (binding == null || !query.equals(latestQuery)) {
                    return;
                }
                Log.w(TAG, "User search failed.", exception);
                binding.progress.setVisibility(View.GONE);
                adapter.submitList(Collections.emptyList());
                showEmpty(R.string.user_search_error);
            }
        });
    }

    private void openChatWith(@NonNull UserProfile other) {
        if (openingChat || binding == null) {
            return;
        }
        openingChat = true;
        binding.progress.setVisibility(View.VISIBLE);
        binding.resultsList.setAlpha(0.5f);

        UserRepository.getProfile(uid, new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                String myName = profile == null ? fallbackName() : profile.name;
                String myPhoto = profile == null ? authPhotoUrl() : profile.photoUrl;
                startChat(myName, myPhoto, other);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load my profile.", exception);
                startChat(fallbackName(), authPhotoUrl(), other);
            }
        });
    }

    private void startChat(@NonNull String myName,
                           @Nullable String myPhoto,
                           @NonNull UserProfile other) {
        if (binding == null || !isAdded()) {
            return;
        }
        ChatRepository.openDirectChat(uid, myName, myPhoto, other,
                new ChatRepository.ChatReadyCallback() {
            @Override
            public void onReady(@NonNull String chatId) {
                if (binding == null) {
                    return;
                }
                openingChat = false;
                binding.progress.setVisibility(View.GONE);
                binding.resultsList.setAlpha(1f);

                Bundle arguments = new Bundle();
                arguments.putString(ChatFragment.ARG_CHAT_ID, chatId);
                arguments.putString(ChatFragment.ARG_OTHER_UID, other.uid);
                arguments.putString(ChatFragment.ARG_OTHER_NAME, other.name);
                NavHostFragment.findNavController(NewChatFragment.this)
                        .navigate(R.id.action_new_chat_to_chat, arguments);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (binding == null) {
                    return;
                }
                Log.w(TAG, "Could not start the chat.", exception);
                openingChat = false;
                binding.progress.setVisibility(View.GONE);
                binding.resultsList.setAlpha(1f);
                Snackbar.make(binding.getRoot(), R.string.start_chat_error,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    private String authPhotoUrl() {
        FirebaseUser user = currentUser();
        return user == null || user.getPhotoUrl() == null
                ? null
                : user.getPhotoUrl().toString();
    }

    @NonNull
    private String fallbackName() {
        FirebaseUser user = currentUser();
        if (user == null) {
            return getString(R.string.guest_name);
        }
        String name = user.getDisplayName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return getString(R.string.guest_name);
    }

    private void showEmpty(int messageRes) {
        binding.emptyText.setText(messageRes);
        binding.emptyText.setVisibility(View.VISIBLE);
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
        handler.removeCallbacks(searchRunnable);
        if (binding != null) {
            binding.resultsList.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        interface Listener {
            void onChanged(@NonNull String text);
        }

        private final Listener listener;

        SimpleTextWatcher(@NonNull Listener listener) {
            this.listener = listener;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            listener.onChanged(s == null ? "" : s.toString());
        }
    }
}
