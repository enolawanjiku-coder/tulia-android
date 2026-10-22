package com.tulia.app.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.BuildConfig;
import com.tulia.app.R;
import com.tulia.app.data.ThemePrefs;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentSettingsBinding;
import com.tulia.app.notifications.NotificationHelper;
import com.tulia.app.update.UpdateCoordinator;
import com.tulia.app.util.Initials;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";

    private FragmentSettingsBinding binding;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) {
                    return;
                }
                if (granted) {
                    NotificationHelper.openNotificationSettings(requireContext());
                } else {
                    showNotificationsBlocked();
                }
                updateNotificationValue();
            });

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSettingsBinding.bind(view);

        binding.rowAppearance.setOnClickListener(v -> showAppearanceDialog());
        binding.rowAccount.setOnClickListener(v -> openProfileEditor());
        binding.rowUpdate.setOnClickListener(v -> checkForUpdates());
        binding.rowPrivacy.setOnClickListener(v -> showComingSoon());
        binding.rowNotifications.setOnClickListener(v -> onNotificationsClicked());
        binding.rowStorage.setOnClickListener(v -> showComingSoon());
        binding.signOutButton.setOnClickListener(v -> confirmSignOut());
        binding.versionLabel.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        binding.updateValue.setText(BuildConfig.VERSION_NAME);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) {
            return;
        }
        updateProfile();
        updateAppearanceValue();
        updateNotificationValue();
    }

    private void onNotificationsClicked() {
        Context context = requireContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            NotificationHelper.markNotificationPermissionRequested(context);
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        if (!NotificationHelper.areNotificationsEnabled(context)) {
            showNotificationsBlocked();
            return;
        }
        NotificationHelper.openNotificationSettings(context);
    }

    private void updateNotificationValue() {
        if (binding == null) {
            return;
        }
        binding.notificationValue.setText(NotificationHelper.canPostNotifications(
                requireContext()) ? R.string.notifications_on : R.string.notifications_off);
    }

    private void showNotificationsBlocked() {
        if (binding == null || !isAdded()) {
            return;
        }
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.notifications_blocked_title)
                .setMessage(R.string.notifications_blocked_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.notifications_open_settings,
                        (dialog, which) -> NotificationHelper.openNotificationSettings(context))
                .show();
    }

    private void updateProfile() {
        FirebaseUser user = currentUser();
        if (user == null) {
            showGuestProfile();
            return;
        }

        UserRepository.getProfile(user.getUid(), new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                if (binding == null) {
                    return;
                }
                if (profile == null) {
                    showAuthProfile(user);
                    return;
                }
                binding.profileAvatar.setText(Initials.of(profile.name));
                binding.profileName.setText(profile.name);
                binding.profileSubtitle.setText(profile.hasUsername()
                        ? getString(R.string.username_with_at, profile.username)
                        : user.getEmail());
                bindPhoto(profile.photoUrl);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load the profile.", exception);
                if (binding != null) {
                    showAuthProfile(user);
                }
            }
        });
    }

    private void showGuestProfile() {
        binding.profileAvatar.setText(R.string.avatar_initial_guest);
        binding.profileName.setText(R.string.guest_name);
        binding.profileSubtitle.setText(R.string.guest_subtitle);
        bindPhoto(null);
    }

    private void showAuthProfile(@NonNull FirebaseUser user) {
        String email = user.getEmail();
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            if (email != null && email.contains("@")) {
                name = email.substring(0, email.indexOf('@'));
            } else {
                name = getString(R.string.guest_name);
            }
        }
        binding.profileAvatar.setText(Initials.of(name));
        binding.profileName.setText(name);
        binding.profileSubtitle.setText(email != null ? email : getString(R.string.guest_subtitle));
        bindPhoto(user.getPhotoUrl() == null ? null : user.getPhotoUrl().toString());
    }

    private void bindPhoto(@Nullable String url) {
        binding.profileAvatarPhoto.setImageDrawable(null);
        if (url == null || url.trim().isEmpty()) {
            Glide.with(binding.profileAvatarPhoto).clear(binding.profileAvatarPhoto);
            binding.profileAvatarPhoto.setVisibility(View.GONE);
            return;
        }
        binding.profileAvatarPhoto.setVisibility(View.VISIBLE);
        Glide.with(binding.profileAvatarPhoto).load(url)
                .into(binding.profileAvatarPhoto);
    }

    private void openProfileEditor() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_settings_to_profileEdit);
    }

    private void checkForUpdates() {
        binding.updateValue.setText(R.string.updates_checking);
        UpdateCoordinator.check(requireContext(), true, () -> {
            if (binding != null) {
                binding.updateValue.setText(BuildConfig.VERSION_NAME);
            }
        });
    }

    private void updateAppearanceValue() {
        int mode = ThemePrefs.getMode(requireContext());
        int label;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            label = R.string.theme_light;
        } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            label = R.string.theme_dark;
        } else {
            label = R.string.theme_system;
        }
        binding.appearanceValue.setText(label);
    }

    private void showAppearanceDialog() {
        int mode = ThemePrefs.getMode(requireContext());
        int checked;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            checked = 1;
        } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            checked = 2;
        } else {
            checked = 0;
        }

        String[] labels = new String[]{
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_appearance)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    ThemePrefs.setMode(requireContext(), modeFor(which));
                    dialog.dismiss();
                })
                .show();
    }

    private int modeFor(int index) {
        switch (index) {
            case 1:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case 2:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case 0:
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    private void confirmSignOut() {
        FirebaseUser user = currentUser();
        if (user == null) {
            navigateToWelcome();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sign_out_confirm)
                .setMessage(R.string.sign_out_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sign_out, (dialog, which) -> {
                    try {
                        FirebaseAuth.getInstance().signOut();
                    } catch (IllegalStateException exception) {
                        Log.w(TAG, "Firebase is not configured yet.", exception);
                    }
                    navigateToWelcome();
                })
                .show();
    }

    private void navigateToWelcome() {
        NavHostFragment.findNavController(this).navigate(R.id.action_settings_to_welcome);
    }

    private void showComingSoon() {
        Snackbar.make(binding.getRoot(), R.string.settings_soon, Snackbar.LENGTH_SHORT).show();
    }

    private FirebaseUser currentUser() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
