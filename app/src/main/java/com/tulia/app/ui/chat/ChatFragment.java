package com.tulia.app.ui.chat;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.Chat;
import com.tulia.app.data.model.Message;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentChatBinding;
import com.tulia.app.util.ImageCompressor;
import com.tulia.app.util.Initials;
import com.tulia.app.util.TimeFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatFragment extends Fragment {

    public static final String ARG_CHAT_ID = "chat_id";
    public static final String ARG_OTHER_UID = "other_uid";
    public static final String ARG_OTHER_NAME = "other_name";

    private static final String TAG = "ChatFragment";
    private static final int MESSAGE_LIMIT = 300;
    private static final long TYPING_WRITE_INTERVAL_MS = 2000L;
    private static final long TYPING_IDLE_MS = 1500L;
    private static final long TYPING_WINDOW_MS = 5000L;

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Runnable typingIdleRunnable = this::clearTyping;
    private final Runnable typingRefreshRunnable = () -> {
        typingRefreshScheduled = false;
        renderHeaderStatus();
    };

    private FragmentChatBinding binding;
    private MessageAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ListenerRegistration chatRegistration;
    private ListenerRegistration messagesRegistration;

    private String chatId;
    private String uid;
    private String otherUid;
    private Chat chat;
    private List<Message> currentMessages = Collections.emptyList();
    private boolean firstMessages = true;
    private boolean chatMissingNotified;
    private boolean headerProfileLoaded;
    private boolean sendingPhoto;
    private boolean typingActive;
    private boolean typingRefreshScheduled;
    private boolean viewDestroyed;
    private long lastTypingWriteAt;
    private String otherUsername = "";
    private long lastMarkedReadAt;
    private ExecutorService imageExecutor;
    private Snackbar photoProgress;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    sendPhoto(uri);
                }
            });

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentChatBinding.bind(view);
        viewDestroyed = false;

        Bundle arguments = getArguments();
        chatId = arguments == null ? null : arguments.getString(ARG_CHAT_ID);
        otherUid = arguments == null ? null : arguments.getString(ARG_OTHER_UID);
        String otherName = arguments == null ? null : arguments.getString(ARG_OTHER_NAME);
        if (otherName == null || otherName.trim().isEmpty()) {
            otherName = getString(R.string.guest_name);
        }

        FirebaseUser user = currentUser();
        if (chatId == null || otherUid == null || user == null) {
            showError(R.string.chat_load_error);
            binding.inputMessage.setEnabled(false);
            binding.sendButton.setEnabled(false);
            binding.photoButton.setEnabled(false);
            binding.backButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp());
            return;
        }
        uid = user.getUid();

        binding.name.setText(otherName);
        binding.avatar.setText(Initials.of(otherName));
        binding.username.setText("");
        binding.backButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        adapter = new MessageAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        binding.messageList.setLayoutManager(layoutManager);
        binding.messageList.setAdapter(adapter);

        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.photoButton.setOnClickListener(v -> photoPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.inputMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return false;
        });
        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onInputChanged(s == null ? "" : s.toString());
            }
        });
        imageExecutor = Executors.newSingleThreadExecutor();

        loadOtherProfile();
        listenForChat();
        listenForMessages();
    }

    private void loadOtherProfile() {
        UserRepository.getProfile(otherUid, new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                if (binding == null) {
                    return;
                }
                headerProfileLoaded = true;
                if (profile == null) {
                    return;
                }
                binding.name.setText(profile.name);
                binding.avatar.setText(Initials.of(profile.name));
                otherUsername = profile.hasUsername()
                        ? getString(R.string.username_with_at, profile.username)
                        : "";
                renderHeaderStatus();
                bindAvatar(profile.photoUrl);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                headerProfileLoaded = true;
                Log.w(TAG, "Could not load the other profile.", exception);
            }
        });
    }

    private void listenForChat() {
        chatRegistration = ChatRepository.listenChat(chatId, new ChatRepository.ChatCallback() {
            @Override
            public void onChat(@Nullable Chat value) {
                if (binding == null) {
                    return;
                }
                chat = value;
                if (chat == null) {
                    if (!chatMissingNotified) {
                        chatMissingNotified = true;
                        showError(R.string.chat_load_error);
                    }
                    return;
                }
                chatMissingNotified = false;
                if (!headerProfileLoaded) {
                    bindAvatar(chat.photoFor(otherUid));
                }
                renderHeaderStatus();
                renderMessages(false);
                markReadIfNeeded();
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (binding == null) {
                    return;
                }
                Log.w(TAG, "Could not listen for the chat.", exception);
                showError(R.string.chat_load_error);
            }
        });
    }

    private void onInputChanged(@NonNull String text) {
        if (binding == null || viewDestroyed || chatId == null || uid == null) {
            return;
        }
        if (text.trim().isEmpty()) {
            clearTyping();
            return;
        }
        long now = System.currentTimeMillis();
        if (!typingActive || now - lastTypingWriteAt >= TYPING_WRITE_INTERVAL_MS) {
            typingActive = true;
            lastTypingWriteAt = now;
            ChatRepository.setTyping(chatId, uid, true);
        }
        typingHandler.removeCallbacks(typingIdleRunnable);
        typingHandler.postDelayed(typingIdleRunnable, TYPING_IDLE_MS);
    }

    private void clearTyping() {
        typingHandler.removeCallbacks(typingIdleRunnable);
        boolean wasActive = typingActive;
        typingActive = false;
        lastTypingWriteAt = 0L;
        if (wasActive && !viewDestroyed && binding != null
                && chatId != null && uid != null) {
            ChatRepository.setTyping(chatId, uid, false);
        }
    }

    private void renderHeaderStatus() {
        if (binding == null) {
            return;
        }
        boolean typing = chat != null && otherUid != null
                && chat.isTyping(otherUid, System.currentTimeMillis(), TYPING_WINDOW_MS);
        if (!typing) {
            if (typingRefreshScheduled) {
                typingHandler.removeCallbacks(typingRefreshRunnable);
                typingRefreshScheduled = false;
            }
            binding.username.setText(otherUsername);
            return;
        }
        binding.username.setText(R.string.typing);
        if (!typingRefreshScheduled) {
            typingRefreshScheduled = true;
            typingHandler.postDelayed(typingRefreshRunnable, TYPING_WINDOW_MS);
        }
    }

    private void listenForMessages() {
        messagesRegistration = ChatRepository.listenMessages(chatId, MESSAGE_LIMIT,
                new ChatRepository.MessagesCallback() {
                    @Override
                    public void onMessages(@NonNull List<Message> messages) {
                        if (binding == null) {
                            return;
                        }
                        boolean scroll = firstMessages || isNearBottom();
                        currentMessages = messages;
                        renderMessages(scroll);
                        firstMessages = false;
                        markReadIfNeeded();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        if (binding == null) {
                            return;
                        }
                        Log.w(TAG, "Could not listen for messages.", exception);
                        showError(R.string.chat_load_error);
                    }
                });
    }

    private void renderMessages(boolean shouldScroll) {
        if (binding == null) {
            return;
        }
        List<MessageItem> items = buildItems();
        adapter.submitList(items, () -> {
            if (binding != null && shouldScroll) {
                scrollToBottom();
            }
        });
    }

    @NonNull
    private List<MessageItem> buildItems() {
        if (currentMessages.isEmpty()) {
            return Collections.emptyList();
        }

        long otherReadAt = otherReadMillis();
        List<MessageItem> items = new ArrayList<>();
        long previousMillis = 0L;

        for (int i = 0; i < currentMessages.size(); i++) {
            Message message = currentMessages.get(i);
            long millis = displayMillis(message, previousMillis);
            boolean newDay = previousMillis == 0L
                    || !TimeFormat.isSameDay(previousMillis, millis);
            if (newDay) {
                items.add(MessageItem.date("date_" + message.id,
                        TimeFormat.dateLabel(requireContext(), millis)));
            }

            boolean sent = uid.equals(message.senderId);
            boolean lastInGroup = i == currentMessages.size() - 1
                    || !message.senderId.equals(currentMessages.get(i + 1).senderId)
                    || !TimeFormat.isSameDay(millis,
                    displayMillis(currentMessages.get(i + 1), millis));
            String time = TimeFormat.messageTime(millis);

            boolean hasImage = !message.imageUrl.isEmpty();
            if (sent) {
                int tick = otherReadAt >= millis
                        ? MessageItem.TICK_READ
                        : MessageItem.TICK_SENT;
                items.add(hasImage
                        ? MessageItem.sentImage(message.id, message.imageUrl, time,
                        lastInGroup, tick)
                        : MessageItem.sent(message.id, message.text, time, lastInGroup, tick));
            } else {
                items.add(hasImage
                        ? MessageItem.receivedImage(message.id, message.imageUrl, time,
                        lastInGroup)
                        : MessageItem.received(message.id, message.text, time, lastInGroup));
            }
            previousMillis = millis;
        }
        return items;
    }

    private long displayMillis(@NonNull Message message, long fallback) {
        long millis = message.createdMillis();
        if (millis > 0L) {
            return millis;
        }
        return fallback > 0L ? fallback : System.currentTimeMillis();
    }

    private long otherReadMillis() {
        if (chat == null) {
            return 0L;
        }
        Timestamp read = chat.lastReadFor(otherUid);
        return read == null ? 0L : read.toDate().getTime();
    }

    private void markReadIfNeeded() {
        if (chat == null || !isResumed()) {
            return;
        }
        Timestamp lastMessageAt = chat.lastMessageAt;
        if (lastMessageAt == null || uid.equals(chat.lastMessageSenderId)) {
            return;
        }
        long lastMillis = lastMessageAt.toDate().getTime();
        Timestamp myRead = chat.lastReadFor(uid);
        if (myRead != null && myRead.toDate().getTime() >= lastMillis) {
            return;
        }
        if (lastMillis <= lastMarkedReadAt) {
            return;
        }
        lastMarkedReadAt = lastMillis;
        ChatRepository.markRead(chatId, uid);
    }

    private void bindAvatar(@Nullable String url) {
        if (binding == null) {
            return;
        }
        binding.avatarPhoto.setImageDrawable(null);
        if (url == null || url.trim().isEmpty()) {
            Glide.with(binding.avatarPhoto).clear(binding.avatarPhoto);
            binding.avatarPhoto.setVisibility(View.GONE);
            return;
        }
        binding.avatarPhoto.setVisibility(View.VISIBLE);
        Glide.with(binding.avatarPhoto).load(url).into(binding.avatarPhoto);
    }

    private void sendPhoto(@NonNull Uri uri) {
        if (binding == null || sendingPhoto || chatId == null || uid == null
                || otherUid == null || imageExecutor == null) {
            return;
        }
        sendingPhoto = true;
        binding.photoButton.setEnabled(false);
        photoProgress = Snackbar.make(binding.getRoot(), R.string.photo_sending,
                Snackbar.LENGTH_INDEFINITE);
        photoProgress.show();

        Context appContext = requireContext().getApplicationContext();
        imageExecutor.execute(() -> {
            try {
                ImageCompressor.Result result =
                        ImageCompressor.compress(appContext, uri, 1600, 80);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> uploadPhoto(result));
            } catch (Exception exception) {
                Log.w(TAG, "Could not prepare the photo.", exception);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> photoFailed(exception));
            }
        });
    }

    private void uploadPhoto(@NonNull ImageCompressor.Result image) {
        if (binding == null) {
            return;
        }
        ChatRepository.sendImage(chatId, uid, otherUid, image.bytes,
                new ChatRepository.SendImageCallback() {
                    @Override
                    public void onSent() {
                        finishPhotoSend();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not send the photo.", exception);
                        photoFailed(exception);
                    }
                });
    }

    private void photoFailed(@NonNull Exception exception) {
        if (binding == null) {
            return;
        }
        finishPhotoSend();
        Snackbar.make(binding.getRoot(), R.string.photo_send_error,
                Snackbar.LENGTH_LONG).show();
    }

    private void finishPhotoSend() {
        sendingPhoto = false;
        if (binding != null) {
            binding.photoButton.setEnabled(true);
        }
        if (photoProgress != null) {
            photoProgress.dismiss();
            photoProgress = null;
        }
    }

    private void sendMessage() {
        if (binding == null || chatId == null || uid == null || otherUid == null) {
            return;
        }
        String text = textOf();
        if (text.trim().isEmpty()) {
            return;
        }

        clearTyping();
        binding.inputMessage.setText("");
        ChatRepository.sendText(chatId, uid, otherUid, text.trim())
                .addOnFailureListener(exception -> {
                    if (binding == null) {
                        return;
                    }
                    Log.w(TAG, "Could not send the message.", exception);
                    if (textOf().trim().isEmpty()) {
                        binding.inputMessage.setText(text);
                        binding.inputMessage.setSelection(binding.inputMessage.length());
                    }
                    Snackbar.make(binding.getRoot(), R.string.message_send_error,
                            Snackbar.LENGTH_LONG).show();
                });
    }

    @NonNull
    private String textOf() {
        android.text.Editable editable = binding.inputMessage.getText();
        return editable == null ? "" : editable.toString();
    }

    private boolean isNearBottom() {
        if (adapter == null || adapter.getItemCount() == 0) {
            return true;
        }
        int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        return lastVisible == RecyclerView.NO_POSITION
                || lastVisible >= adapter.getItemCount() - 2;
    }

    private void scrollToBottom() {
        if (binding != null && adapter != null && adapter.getItemCount() > 0) {
            binding.messageList.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void showError(int messageRes) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_LONG).show();
        }
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
    public void onResume() {
        super.onResume();
        markReadIfNeeded();
    }

    @Override
    public void onStop() {
        clearTyping();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        clearTyping();
        typingHandler.removeCallbacks(typingIdleRunnable);
        typingHandler.removeCallbacks(typingRefreshRunnable);
        typingRefreshScheduled = false;
        viewDestroyed = true;
        if (chatRegistration != null) {
            chatRegistration.remove();
            chatRegistration = null;
        }
        if (messagesRegistration != null) {
            messagesRegistration.remove();
            messagesRegistration = null;
        }
        if (photoProgress != null) {
            photoProgress.dismiss();
            photoProgress = null;
        }
        if (imageExecutor != null) {
            imageExecutor.shutdownNow();
            imageExecutor = null;
        }
        if (binding != null) {
            binding.messageList.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
    }
}
