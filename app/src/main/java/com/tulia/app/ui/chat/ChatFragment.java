package com.tulia.app.ui.chat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.PresenceRepository;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.Chat;
import com.tulia.app.data.model.Message;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentChatBinding;
import com.tulia.app.databinding.ItemReactionEmojiBinding;
import com.tulia.app.util.ImageCompressor;
import com.tulia.app.util.Initials;
import com.tulia.app.util.TimeFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatFragment extends Fragment implements MessageAdapter.Listener {

    public static final String ARG_CHAT_ID = "chat_id";
    public static final String ARG_OTHER_UID = "other_uid";
    public static final String ARG_OTHER_NAME = "other_name";

    private static final String TAG = "ChatFragment";
    private static final int MESSAGE_LIMIT = 300;
    private static final int MAX_REACTION_CHIPS = 3;
    private static final long TYPING_WRITE_INTERVAL_MS = 2000L;
    private static final long TYPING_IDLE_MS = 1500L;
    private static final long TYPING_WINDOW_MS = 5000L;
    private static final int REPLY_PREVIEW_MAX_CHARS = 120;
    private static final int REACTION_ROWS = 2;
    private static final int REACTION_COLUMNS = 3;
    private static final long REACTION_TTL_MS = 10000L;
    private static final long MIN_VOICE_MILLIS = 900L;
    private static final float RECORD_SLIDE_CANCEL_DP = 120f;

    private static final int ACTION_REPLY = 0;
    private static final int ACTION_REACT = 1;
    private static final int ACTION_EDIT = 2;
    private static final int ACTION_DELETE = 3;

    private static final String[] REACTION_EMOJIS = {
            "\uD83D\uDC4D",
            "\u2764\uFE0F",
            "\uD83D\uDE02",
            "\uD83D\uDE2E",
            "\uD83D\uDE22",
            "\uD83D\uDE4F"
    };

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Runnable typingIdleRunnable = this::clearTyping;
    private final Handler recordingHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingTickRunnable = this::updateRecordingTimer;
    private final Runnable typingRefreshRunnable = () -> {
        typingRefreshScheduled = false;
        renderHeaderStatus();
    };
    private final Handler reactionHandler = new Handler(Looper.getMainLooper());
    private final Runnable reactionRefreshRunnable = () -> renderMessages(false);
    private final Map<String, Map<String, String>> reactionCache = new HashMap<>();
    private final Map<String, Long> reactionLoadedAt = new HashMap<>();
    private final Set<String> loadingReactions = new HashSet<>();

    private FragmentChatBinding binding;
    private MessageAdapter adapter;
    private LinearLayoutManager layoutManager;
    private ListenerRegistration chatRegistration;
    private ListenerRegistration messagesRegistration;
    private PresenceRepository.PresenceHandle presenceHandle;
    private boolean otherOnline;
    private long otherLastSeen;

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
    private boolean sendingVoice;
    private boolean recording;
    private boolean recordingCancelled;
    private float recordingDownX;
    private long recordingStartedAt;
    @Nullable
    private MediaRecorder recorder;
    @Nullable
    private File recordingFile;
    private String otherUsername = "";
    private String otherName = "";
    private String myName = "";
    private Set<String> blockedUids = new HashSet<>();
    private boolean otherBlocked;
    private long lastMarkedReadAt;
    private ExecutorService imageExecutor;
    private Snackbar photoProgress;
    private View replyBar;
    private TextView replyBarLabel;
    private Message pendingReply;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    sendPhoto(uri);
                }
            });

    private final ActivityResultLauncher<String> recordAudioPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    Toast.makeText(requireContext(), R.string.record_permission_denied,
                            Toast.LENGTH_SHORT).show();
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
        String otherDisplayName = arguments == null ? null : arguments.getString(ARG_OTHER_NAME);
        if (otherDisplayName == null || otherDisplayName.trim().isEmpty()) {
            otherDisplayName = getString(R.string.guest_name);
        }
        otherName = otherDisplayName;

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
        myName = user.getDisplayName() == null ? "" : user.getDisplayName().trim();

        binding.name.setText(otherDisplayName);
        binding.avatar.setText(Initials.of(otherDisplayName));
        binding.username.setText("");
        binding.backButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        binding.chatMenuButton.setOnClickListener(v -> showChatMenu());

        adapter = new MessageAdapter(this);
        layoutManager = new LinearLayoutManager(requireContext());
        binding.messageList.setLayoutManager(layoutManager);
        binding.messageList.setAdapter(adapter);

        buildReplyBar();

        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.photoButton.setOnClickListener(v -> photoPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.micButton.setOnTouchListener(micTouchListener);
        binding.cancelRecordButton.setOnClickListener(v -> {
            recordingCancelled = true;
            stopRecording(false);
        });
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

        updateSendMicVisibility();
        loadOtherProfile();
        loadBlockedState();
        listenForChat();
        listenForMessages();
        listenForPresence();
    }

    private void loadBlockedState() {
        UserRepository.getBlockedUids(uid, new UserRepository.BlockedCallback() {
            @Override
            public void onResult(@NonNull List<String> blocked) {
                blockedUids = new HashSet<>(blocked);
                otherBlocked = blockedUids.contains(otherUid);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load the block list.", exception);
            }
        });
    }

    private void showChatMenu() {
        String name = displayName();
        String[] items = new String[]{
                getString(otherBlocked ? R.string.unblock_user : R.string.block_user, name),
                getString(R.string.report_user, name)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        if (otherBlocked) {
                            unblockOther();
                        } else {
                            confirmBlock(name);
                        }
                    } else {
                        showReportDialog(name);
                    }
                })
                .show();
    }

    private void confirmBlock(@NonNull String name) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.block_confirm_title, name))
                .setMessage(getString(R.string.block_confirm_body, name))
                .setPositiveButton(R.string.block_action, (dialog, which) ->
                        setOtherBlocked(true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unblockOther() {
        setOtherBlocked(false);
    }

    private void setOtherBlocked(boolean blocked) {
        UserRepository.setBlocked(uid, otherUid, blocked)
                .addOnSuccessListener(unused -> {
                    if (blocked) {
                        blockedUids.add(otherUid);
                    } else {
                        blockedUids.remove(otherUid);
                    }
                    otherBlocked = blocked;
                    String name = displayName();
                    Toast.makeText(requireContext(), getString(
                            blocked ? R.string.block_done_toast : R.string.unblock_done_toast,
                            name), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(exception -> {
                    Log.w(TAG, "Could not update the block list.", exception);
                    showError(R.string.block_report_failed);
                });
    }

    private void showReportDialog(@NonNull String name) {
        String[] reasons = new String[]{
                getString(R.string.report_reason_spam),
                getString(R.string.report_reason_harassment),
                getString(R.string.report_reason_fake),
                getString(R.string.report_reason_other)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.report_title, name))
                .setItems(reasons, (dialog, which) ->
                        submitReport(reasons[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submitReport(@NonNull String reason) {
        UserRepository.reportUser(uid, otherUid, reason, chatId,
                new UserRepository.SimpleCallback() {
                    @Override
                    public void onDone() {
                        Toast.makeText(requireContext(),
                                R.string.report_sent_toast,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not send the report.", exception);
                        showError(R.string.block_report_failed);
                    }
                });
    }

    @NonNull
    private String displayName() {
        return otherName == null || otherName.trim().isEmpty()
                ? getString(R.string.guest_name)
                : otherName;
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
                otherName = profile.name;
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
        updateSendMicVisibility();
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

    private void updateSendMicVisibility() {
        if (binding == null) {
            return;
        }
        boolean hasText = binding.inputMessage.getText() != null
                && !binding.inputMessage.getText().toString().trim().isEmpty();
        binding.sendButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        binding.micButton.setVisibility(hasText ? View.GONE : View.VISIBLE);
    }

    private final View.OnTouchListener micTouchListener = (view, event) -> {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                recordingDownX = event.getRawX();
                recordingCancelled = false;
                if (hasRecordPermission()) {
                    startRecording();
                } else {
                    recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (recording && !recordingCancelled
                        && event.getRawX() < recordingDownX
                        - RECORD_SLIDE_CANCEL_DP
                        * view.getResources().getDisplayMetrics().density) {
                    recordingCancelled = true;
                    if (binding != null) {
                        binding.recordingHint.setText(R.string.record_release_cancel);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (recording) {
                    stopRecording(!recordingCancelled);
                }
                return true;
            default:
                return false;
        }
    };

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startRecording() {
        if (recording || binding == null || viewDestroyed) {
            return;
        }
        try {
            File dir = new File(requireContext().getCacheDir(), "voice");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Could not create the recording folder.");
            }
            recordingFile = new File(dir, "voice_" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioChannels(1);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recordingStartedAt = System.currentTimeMillis();
            showRecordingUi(true);
            updateRecordingTimer();
        } catch (Exception exception) {
            Log.w(TAG, "Could not start recording.", exception);
            releaseRecorder();
            recordingFile = null;
            recording = false;
            showRecordingUi(false);
            Toast.makeText(requireContext(), R.string.record_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording(boolean send) {
        if (!recording) {
            return;
        }
        recording = false;
        recordingHandler.removeCallbacks(recordingTickRunnable);
        long duration = System.currentTimeMillis() - recordingStartedAt;
        boolean stopped = false;
        MediaRecorder finished = recorder;
        if (finished != null) {
            try {
                finished.stop();
                stopped = true;
            } catch (RuntimeException ignored) {
                // Recording was too short to produce a file.
            }
        }
        releaseRecorder();
        showRecordingUi(false);
        File file = recordingFile;
        recordingFile = null;
        if (send && stopped && duration >= MIN_VOICE_MILLIS && file != null && file.exists()) {
            sendVoiceNote(file, duration);
        } else {
            deleteQuietly(file);
            if (send) {
                Toast.makeText(requireContext(), R.string.record_too_short,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (RuntimeException ignored) {
                // Already released by the system.
            }
            recorder = null;
        }
    }

    private void showRecordingUi(boolean isRecording) {
        if (binding == null) {
            return;
        }
        binding.photoButton.setVisibility(isRecording ? View.GONE : View.VISIBLE);
        binding.inputMessage.setVisibility(isRecording ? View.GONE : View.VISIBLE);
        binding.recordingBar.setVisibility(isRecording ? View.VISIBLE : View.GONE);
        if (isRecording) {
            binding.recordingHint.setText(R.string.record_hint);
            binding.recordingTimer.setText("0:00");
        }
        updateSendMicVisibility();
    }

    private void updateRecordingTimer() {
        if (!recording || binding == null) {
            return;
        }
        long seconds = Math.max(0L,
                (System.currentTimeMillis() - recordingStartedAt) / 1000L);
        binding.recordingTimer.setText(String.format(java.util.Locale.getDefault(),
                "%d:%02d", seconds / 60, seconds % 60));
        recordingHandler.postDelayed(recordingTickRunnable, 250L);
    }

    private void sendVoiceNote(@NonNull File file, long durationMillis) {
        if (binding == null || sendingVoice || chatId == null || uid == null
                || otherUid == null || imageExecutor == null) {
            deleteQuietly(file);
            return;
        }
        sendingVoice = true;
        photoProgress = Snackbar.make(binding.getRoot(), R.string.voice_sending,
                Snackbar.LENGTH_INDEFINITE);
        photoProgress.show();

        imageExecutor.execute(() -> {
            final byte[] payload;
            try {
                payload = readAllBytes(file);
            } catch (Exception exception) {
                Log.w(TAG, "Could not read the recording.", exception);
                deleteQuietly(file);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(this::voiceSendFailed);
                return;
            }
            deleteQuietly(file);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                ChatRepository.sendVoiceNote(chatId, uid, otherUid, payload, durationMillis,
                        new ChatRepository.SendImageCallback() {
                            @Override
                            public void onSent() {
                                finishVoiceSend();
                            }

                            @Override
                            public void onError(@NonNull Exception exception) {
                                Log.w(TAG, "Could not send the voice note.", exception);
                                voiceSendFailed();
                            }
                        });
            });
        });
    }

    private void voiceSendFailed() {
        finishVoiceSend();
        if (binding != null) {
            Snackbar.make(binding.getRoot(), R.string.voice_send_error,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void finishVoiceSend() {
        sendingVoice = false;
        if (photoProgress != null) {
            photoProgress.dismiss();
            photoProgress = null;
        }
    }

    @Nullable
    private static byte[] readAllBytes(@NonNull File file) throws java.io.IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return bytes;
    }

    private static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w("ChatFragment", "Could not delete the temp recording.");
        }
    }

    private void listenForPresence() {
        if (otherUid == null) {
            return;
        }
        presenceHandle = PresenceRepository.listen(otherUid, (online, lastSeen) -> {
            if (binding == null) {
                return;
            }
            otherOnline = online;
            otherLastSeen = lastSeen;
            renderHeaderStatus();
        });
    }

    private void renderHeaderStatus() {
        if (binding == null) {
            return;
        }
        boolean typing = chat != null && otherUid != null
                && chat.isTyping(otherUid, System.currentTimeMillis(), TYPING_WINDOW_MS);
        String status;
        int color;
        if (typing) {
            status = getString(R.string.typing);
            color = R.color.tulia_red;
            if (!typingRefreshScheduled) {
                typingRefreshScheduled = true;
                typingHandler.postDelayed(typingRefreshRunnable, TYPING_WINDOW_MS);
            }
        } else {
            if (typingRefreshScheduled) {
                typingHandler.removeCallbacks(typingRefreshRunnable);
                typingRefreshScheduled = false;
            }
            if (otherOnline) {
                status = getString(R.string.status_online);
                color = R.color.tulia_red;
            } else if (otherLastSeen > 0L) {
                status = getString(R.string.status_last_seen,
                        TimeFormat.chatTime(requireContext(), otherLastSeen));
                color = R.color.tulia_dust;
            } else {
                status = otherUsername;
                color = R.color.tulia_dust;
            }
        }
        binding.username.setText(status);
        binding.username.setTextColor(ContextCompat.getColor(requireContext(), color));
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
                        refreshVisibleReactions();
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

            String quoteName = null;
            String quoteText = null;
            if (message.hasReply() && message.replyTo != null) {
                quoteName = message.replyTo.senderName;
                quoteText = replyPreview(message.replyTo);
            }
            List<MessageItem.Reaction> reactions = reactionsFor(message.id);
            boolean edited = message.isEdited();
            boolean deleted = message.deleted;

            boolean hasImage = !message.imageUrl.isEmpty();
            boolean hasAudio = message.hasAudio();
            if (sent) {
                int tick = otherReadAt >= millis
                        ? MessageItem.TICK_READ
                        : MessageItem.TICK_SENT;
                if (hasAudio) {
                    items.add(MessageItem.sentAudio(message.id, message.audioUrl,
                            message.durationMillis, time, lastInGroup, tick,
                            quoteName, quoteText, edited, deleted, reactions));
                } else {
                    items.add(hasImage
                            ? MessageItem.sentImage(message.id, message.imageUrl, time,
                            lastInGroup, tick, quoteName, quoteText, edited, deleted,
                            reactions)
                            : MessageItem.sent(message.id, message.text, time, lastInGroup,
                            tick, quoteName, quoteText, edited, deleted, reactions));
                }
            } else {
                if (hasAudio) {
                    items.add(MessageItem.receivedAudio(message.id, message.audioUrl,
                            message.durationMillis, time, lastInGroup,
                            quoteName, quoteText, edited, deleted, reactions));
                } else {
                    items.add(hasImage
                            ? MessageItem.receivedImage(message.id, message.imageUrl, time,
                            lastInGroup, quoteName, quoteText, edited, deleted, reactions)
                            : MessageItem.received(message.id, message.text, time,
                            lastInGroup, quoteName, quoteText, edited, deleted, reactions));
                }
            }
            previousMillis = millis;
        }
        return items;
    }

    @NonNull
    private String replyPreview(@NonNull Message.ReplyTo reply) {
        String text = reply.text.trim();
        if (!text.isEmpty()) {
            return text.length() > REPLY_PREVIEW_MAX_CHARS
                    ? text.substring(0, REPLY_PREVIEW_MAX_CHARS) + "..."
                    : text;
        }
        if (reply.hasImage()) {
            return getString(R.string.photo_preview);
        }
        return "";
    }

    @NonNull
    private List<MessageItem.Reaction> reactionsFor(@NonNull String messageId) {
        Map<String, String> reactions = reactionCache.get(messageId);
        if (reactions == null || reactions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : reactions.values()) {
            counts.merge(type, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((first, second) -> second.getValue() - first.getValue());

        List<MessageItem.Reaction> result = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < MAX_REACTION_CHIPS; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            String type = entry.getKey();
            boolean mine = false;
            for (Map.Entry<String, String> reaction : reactions.entrySet()) {
                if (uid != null && uid.equals(reaction.getKey())
                        && type.equals(reaction.getValue())) {
                    mine = true;
                    break;
                }
            }
            result.add(new MessageItem.Reaction(type, entry.getValue(), mine));
        }
        return result;
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
        ChatRepository.sendText(chatId, uid, otherUid, text.trim(), replyToFor())
                .addOnSuccessListener(task -> clearReply())
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

    @Nullable
    private Map<String, Object> replyToFor() {
        Message reply = pendingReply;
        if (reply == null) {
            return null;
        }
        String previewText = reply.text;
        if (previewText.trim().isEmpty() && reply.hasAudio()) {
            previewText = getString(R.string.voice_message);
        }
        Map<String, Object> replyTo = new HashMap<>();
        replyTo.put("messageId", reply.id);
        replyTo.put("senderId", reply.senderId);
        replyTo.put("senderName", displayNameFor(reply.senderId));
        replyTo.put("text", previewText);
        replyTo.put("imageUrl", reply.imageUrl);
        return replyTo;
    }

    @NonNull
    private String textOf() {
        Editable editable = binding.inputMessage.getText();
        return editable == null ? "" : editable.toString();
    }

    private void buildReplyBar() {
        if (binding == null || replyBar != null) {
            return;
        }
        Context context = requireContext();
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(8), dp(8));
        Drawable quote = context.getDrawable(R.drawable.bg_quote_received);
        bar.setBackground(new LayerDrawable(new Drawable[]{
                new ColorDrawable(context.getColor(R.color.tulia_surface)),
                quote
        }));
        bar.setVisibility(View.GONE);

        TextView label = new TextView(context);
        label.setTextAppearance(R.style.TextAppearance_Tulia_BodySmall);
        label.setTextColor(context.getColor(R.color.tulia_ink));
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_reply);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                context.getColor(R.color.tulia_red)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.setMarginEnd(dp(8));
        bar.addView(icon, iconParams);

        bar.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView cancel = new TextView(context);
        cancel.setText("\u2715");
        cancel.setTextSize(16f);
        cancel.setTextColor(context.getColor(R.color.tulia_dust));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(12), dp(4), dp(12), dp(4));
        cancel.setContentDescription(getString(R.string.chat_reply_cancel));
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setOnClickListener(v -> clearReply());
        bar.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        replyBar = bar;
        replyBarLabel = label;
        attachReplyBar(bar);
    }

    private void attachReplyBar(@NonNull View bar) {
        if (!(binding.getRoot() instanceof ViewGroup)) {
            return;
        }
        ViewGroup root = (ViewGroup) binding.getRoot();
        View inputRow = findInputRow(root);
        if (inputRow == null) {
            root.addView(bar, root.getChildCount());
            return;
        }
        root.addView(bar, root.indexOfChild(inputRow));
    }

    @Nullable
    private View findInputRow(@NonNull ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup
                    && ((ViewGroup) child).findViewById(R.id.input_message)
                    == binding.inputMessage) {
                return child;
            }
        }
        return null;
    }

    private void startReply(@NonNull Message message) {
        if (binding == null) {
            return;
        }
        pendingReply = message;
        if (replyBarLabel != null) {
            replyBarLabel.setText(getString(R.string.chat_replying_to,
                    displayNameFor(message.senderId)));
        }
        if (replyBar != null) {
            replyBar.setVisibility(View.VISIBLE);
        }
        binding.inputMessage.requestFocus();
    }

    private void clearReply() {
        pendingReply = null;
        if (replyBarLabel != null) {
            replyBarLabel.setText("");
        }
        if (replyBar != null) {
            replyBar.setVisibility(View.GONE);
        }
    }

    @NonNull
    private String displayNameFor(@NonNull String senderId) {
        if (uid != null && uid.equals(senderId)) {
            return myName.isEmpty() ? getString(R.string.chat_you) : myName;
        }
        if (chat != null) {
            return chat.nameFor(senderId, otherName);
        }
        return otherName;
    }

    @Nullable
    private Message findMessage(@NonNull String id) {
        for (Message message : currentMessages) {
            if (message.id.equals(id)) {
                return message;
            }
        }
        return null;
    }

    @Override
    public void onMessageLongPressed(@NonNull MessageItem item) {
        showMessageActions(item);
    }

    @Override
    public void onMessageVisible(@NonNull String messageId) {
        loadReactionsFor(messageId, false);
    }

    @Override
    public void onReactionToggled(@NonNull String messageId, @NonNull String type) {
        toggleReaction(messageId, type);
    }

    private void showMessageActions(@NonNull MessageItem item) {
        if (binding == null) {
            return;
        }
        Message message = findMessage(item.id);
        if (message == null || message.deleted) {
            return;
        }
        Context context = requireContext();
        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        labels.add(getString(R.string.chat_action_reply));
        actions.add(ACTION_REPLY);
        labels.add(getString(R.string.chat_action_react));
        actions.add(ACTION_REACT);
        if (item.type == MessageItem.TYPE_SENT) {
            labels.add(getString(R.string.chat_action_edit));
            actions.add(ACTION_EDIT);
            labels.add(getString(R.string.chat_action_delete));
            actions.add(ACTION_DELETE);
        }

        String[] items = labels.toArray(new String[0]);
        new MaterialAlertDialogBuilder(context)
                .setItems(items, (dialog, which) -> {
                    if (which < 0 || which >= actions.size()) {
                        return;
                    }
                    int action = actions.get(which);
                    if (action == ACTION_REPLY) {
                        startReply(message);
                    } else if (action == ACTION_REACT) {
                        showReactionPicker(message);
                    } else if (action == ACTION_EDIT) {
                        showEditDialog(message);
                    } else if (action == ACTION_DELETE) {
                        showDeleteDialog(message);
                    }
                })
                .show();
    }

    private void showReactionPicker(@NonNull Message message) {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER);
        LayoutInflater inflater = LayoutInflater.from(context);
        List<TextView> emojiViews = new ArrayList<>();
        for (int row = 0; row < REACTION_ROWS; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            line.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int column = 0; column < REACTION_COLUMNS; column++) {
                int index = row * REACTION_COLUMNS + column;
                if (index >= REACTION_EMOJIS.length) {
                    break;
                }
                String emoji = REACTION_EMOJIS[index];
                ItemReactionEmojiBinding item =
                        ItemReactionEmojiBinding.inflate(inflater, line, false);
                item.reactionEmojiText.setText(emoji);
                item.reactionEmojiText.setContentDescription(
                        getString(R.string.chat_react_with, emoji));
                item.reactionEmojiText.setTag(emoji);
                emojiViews.add(item.reactionEmojiText);
                line.addView(item.getRoot());
            }
            grid.addView(line);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.chat_action_react)
                .setView(grid)
                .create();
        dialog.show();
        for (TextView emojiView : emojiViews) {
            Object tag = emojiView.getTag();
            if (!(tag instanceof String)) {
                continue;
            }
            String emoji = (String) tag;
            emojiView.setOnClickListener(v -> {
                dialog.dismiss();
                toggleReaction(message.id, emoji);
            });
        }
    }

    private void toggleReaction(@NonNull String messageId, @NonNull String type) {
        if (chatId == null || uid == null) {
            return;
        }
        Map<String, String> reactions = reactionCache.get(messageId);
        String current = reactions == null ? null : reactions.get(uid);
        writeReaction(messageId, type.equals(current) ? null : type);
    }

    private void writeReaction(@NonNull String messageId, @Nullable String type) {
        if (chatId == null || uid == null) {
            return;
        }
        Map<String, String> reactions = new HashMap<>();
        Map<String, String> cached = reactionCache.get(messageId);
        if (cached != null) {
            reactions.putAll(cached);
        }
        if (type == null) {
            reactions.remove(uid);
        } else {
            reactions.put(uid, type);
        }
        reactionCache.put(messageId, reactions);
        renderMessages(false);

        ChatRepository.setReaction(chatId, messageId, uid, type)
                .addOnFailureListener(exception -> {
                    Log.w(TAG, "Could not save the reaction.", exception);
                    if (binding == null) {
                        return;
                    }
                    Snackbar.make(binding.getRoot(), R.string.chat_reaction_failed,
                            Snackbar.LENGTH_LONG).show();
                    loadReactionsFor(messageId, true);
                });
    }

    private void refreshVisibleReactions() {
        if (adapter == null) {
            return;
        }
        for (String messageId : adapter.visibleMessageIds()) {
            loadReactionsFor(messageId, true);
        }
    }

    private void loadReactionsFor(@NonNull String messageId, boolean force) {
        if (binding == null || chatId == null) {
            return;
        }
        if (!force) {
            Long loadedAt = reactionLoadedAt.get(messageId);
            if (loadedAt != null
                    && System.currentTimeMillis() - loadedAt < REACTION_TTL_MS) {
                return;
            }
        }
        if (!loadingReactions.add(messageId)) {
            return;
        }
        ChatRepository.loadReactions(chatId, messageId,
                new ChatRepository.ReactionsCallback() {
                    @Override
                    public void onReactions(@NonNull Map<String, String> reactions) {
                        loadingReactions.remove(messageId);
                        if (binding == null) {
                            return;
                        }
                        reactionCache.put(messageId, reactions);
                        reactionLoadedAt.put(messageId, System.currentTimeMillis());
                        scheduleReactionRefresh();
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        loadingReactions.remove(messageId);
                        Log.w(TAG, "Could not load the reactions.", exception);
                    }
                });
    }

    private void scheduleReactionRefresh() {
        reactionHandler.removeCallbacks(reactionRefreshRunnable);
        reactionHandler.post(reactionRefreshRunnable);
    }

    private void showEditDialog(@NonNull Message message) {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        TextInputLayout container = new TextInputLayout(context);
        container.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        container.setBoxCornerRadii(16f, 16f, 16f, 16f);
        container.setHint(getString(R.string.chat_edit_hint));

        TextInputEditText input = new TextInputEditText(context);
        input.setText(message.text);
        input.setSelection(message.text.length());
        container.addView(input);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.chat_edit_title)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.chat_edit_save, (dialog, which) -> {
                    Editable editable = input.getText();
                    String text = editable == null ? "" : editable.toString().trim();
                    if (text.isEmpty()) {
                        return;
                    }
                    saveEditedText(message, text);
                })
                .show();
    }

    private void saveEditedText(@NonNull Message message, @NonNull String text) {
        if (chatId == null) {
            return;
        }
        ChatRepository.editMessage(chatId, message.id, text)
                .addOnFailureListener(exception -> {
                    Log.w(TAG, "Could not edit the message.", exception);
                    if (binding == null) {
                        return;
                    }
                    Snackbar.make(binding.getRoot(), R.string.chat_edit_failed,
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private void showDeleteDialog(@NonNull Message message) {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.chat_delete_title)
                .setMessage(R.string.chat_delete_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.chat_delete_confirm, (shown, which) ->
                        deleteMessage(message))
                .create();
        dialog.setOnShowListener(shown -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(context.getColor(R.color.tulia_red_deep));
            }
        });
        dialog.show();
    }

    private void deleteMessage(@NonNull Message message) {
        if (chatId == null) {
            return;
        }
        ChatRepository.deleteMessage(chatId, message.id)
                .addOnFailureListener(exception -> {
                    Log.w(TAG, "Could not delete the message.", exception);
                    if (binding == null) {
                        return;
                    }
                    Snackbar.make(binding.getRoot(), R.string.chat_delete_failed,
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        if (recording) {
            recordingCancelled = true;
            stopRecording(false);
        }
        if (adapter != null) {
            adapter.stopAudioPlayback();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        clearTyping();
        typingHandler.removeCallbacks(typingIdleRunnable);
        typingHandler.removeCallbacks(typingRefreshRunnable);
        typingRefreshScheduled = false;
        recordingHandler.removeCallbacks(recordingTickRunnable);
        if (recording) {
            recordingCancelled = true;
            stopRecording(false);
        }
        reactionHandler.removeCallbacks(reactionRefreshRunnable);
        reactionCache.clear();
        reactionLoadedAt.clear();
        loadingReactions.clear();
        viewDestroyed = true;
        if (presenceHandle != null) {
            presenceHandle.remove();
            presenceHandle = null;
        }
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
        replyBar = null;
        replyBarLabel = null;
        pendingReply = null;
    }
}
