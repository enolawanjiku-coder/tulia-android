package com.tulia.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.firebase.auth.FirebaseAuth;
import com.tulia.app.databinding.ActivityMainBinding;
import com.tulia.app.notifications.MessageNotifier;
import com.tulia.app.notifications.NotificationHelper;
import com.tulia.app.update.UpdateCoordinator;
import com.tulia.app.ui.chat.ChatFragment;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_POST_NOTIFICATIONS = 1001;
    private static final long AUTO_UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String STATE_PENDING_CHAT_ID = "pending_chat_id";
    private static final String STATE_PENDING_OTHER_UID = "pending_other_uid";
    private static final String STATE_PENDING_OTHER_NAME = "pending_other_name";

    private ActivityMainBinding binding;
    private NavController navController;
    private NotificationHelper.PendingChat pendingChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        pendingChat = NotificationHelper.readPendingChat(getIntent());
        NotificationHelper.clearChatExtras(getIntent());
        if (pendingChat == null && savedInstanceState != null) {
            String chatId = savedInstanceState.getString(STATE_PENDING_CHAT_ID);
            if (chatId != null && !chatId.trim().isEmpty()) {
                pendingChat = new NotificationHelper.PendingChat(
                        chatId,
                        savedInstanceState.getString(STATE_PENDING_OTHER_UID),
                        savedInstanceState.getString(STATE_PENDING_OTHER_NAME));
            }
        }
        checkForAppUpdate();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        FragmentContainerView host = findViewById(R.id.nav_host);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(host.getId());
        if (navHostFragment == null) {
            return;
        }

        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean authDestination = id == R.id.authGateFragment
                    || id == R.id.welcomeFragment
                    || id == R.id.loginFragment
                    || id == R.id.signupFragment
                    || id == R.id.profileSetupFragment
                    || id == R.id.newChatFragment
                    || id == R.id.chatFragment
                    || id == R.id.profileEditFragment;
            binding.bottomNav.setVisibility(authDestination ? View.GONE : View.VISIBLE);

            if (id == R.id.chatFragment) {
                String chatId = arguments == null
                        ? null
                        : arguments.getString(ChatFragment.ARG_CHAT_ID);
                MessageNotifier.getInstance().setActiveChatId(chatId);
                if (chatId != null) {
                    NotificationHelper.cancelMessage(this, chatId);
                }
            } else {
                MessageNotifier.getInstance().setActiveChatId(null);
            }

            if (id == R.id.chatsFragment) {
                requestNotificationsOnce();
            }
            handlePendingChat(id);
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingChat != null) {
            outState.putString(STATE_PENDING_CHAT_ID, pendingChat.chatId);
            outState.putString(STATE_PENDING_OTHER_UID, pendingChat.otherUid);
            outState.putString(STATE_PENDING_OTHER_NAME, pendingChat.otherName);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        NotificationHelper.PendingChat incoming = NotificationHelper.readPendingChat(intent);
        NotificationHelper.clearChatExtras(intent);
        if (incoming == null) {
            return;
        }
        pendingChat = incoming;
        if (navController == null || navController.getCurrentDestination() == null) {
            return;
        }
        handlePendingChat(navController.getCurrentDestination().getId());
    }

    @Override
    protected void onStart() {
        super.onStart();
        MessageNotifier.getInstance().setAppForeground(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingChat != null && navController != null
                && navController.getCurrentDestination() != null) {
            handlePendingChat(navController.getCurrentDestination().getId());
        }
    }

    @Override
    protected void onStop() {
        MessageNotifier.getInstance().setAppForeground(false);
        super.onStop();
    }

    private void handlePendingChat(int destinationId) {
        if (pendingChat == null || navController == null) {
            return;
        }
        if (destinationId == R.id.chatsFragment) {
            openPendingChat();
            return;
        }
        if (!isSignedIn() || !canLeaveForChats(destinationId)) {
            return;
        }
        try {
            navController.navigate(R.id.action_global_to_chats);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Log.w(TAG, "Could not open chats from a notification.", exception);
        }
    }

    private boolean canLeaveForChats(int destinationId) {
        return destinationId != R.id.authGateFragment
                && destinationId != R.id.welcomeFragment
                && destinationId != R.id.loginFragment
                && destinationId != R.id.signupFragment
                && destinationId != R.id.profileSetupFragment;
    }

    private void openPendingChat() {
        if (pendingChat == null || navController == null) {
            return;
        }
        NotificationHelper.PendingChat chat = pendingChat;
        pendingChat = null;
        Bundle arguments = new Bundle();
        arguments.putString(ChatFragment.ARG_CHAT_ID, chat.chatId);
        arguments.putString(ChatFragment.ARG_OTHER_UID, chat.otherUid);
        arguments.putString(ChatFragment.ARG_OTHER_NAME, chat.otherName);
        try {
            navController.navigate(R.id.action_chats_to_chat, arguments);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Log.w(TAG, "Could not open the chat from a notification.", exception);
            pendingChat = chat;
        }
    }

    private void requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!isSignedIn()) {
            return;
        }
        if (NotificationHelper.hasRequestedNotificationPermission(this)) {
            return;
        }
        NotificationHelper.markNotificationPermissionRequested(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }

    private boolean isSignedIn() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser() != null;
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            return false;
        }
    }

    private void checkForAppUpdate() {
        UpdateCoordinator.checkThrottled(this, AUTO_UPDATE_CHECK_INTERVAL_MS, false, null);
    }
}
