package com.tulia.app.ui.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.data.ChatRepository;
import com.tulia.app.data.MediaRepository;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentProfileEditBinding;
import com.tulia.app.util.ImageCompressor;
import com.tulia.app.util.Initials;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileEditFragment extends Fragment {

    private static final String TAG = "ProfileEditFragment";

    private FragmentProfileEditBinding binding;
    private FirebaseAuth auth;
    private FirebaseUser user;
    private ExecutorService imageExecutor;
    private Uri selectedPhotoUri;
    private String currentPhotoUrl;
    private boolean saving;
    private boolean leaving;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) {
                    return;
                }
                selectedPhotoUri = uri;
                bindAvatar();
            });

    public ProfileEditFragment() {
        super(R.layout.fragment_profile_edit);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentProfileEditBinding.bind(view);
        imageExecutor = Executors.newSingleThreadExecutor();

        try {
            auth = FirebaseAuth.getInstance();
            user = auth.getCurrentUser();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
        }
        if (user == null) {
            showError(R.string.auth_error_generic);
            setLoading(true);
            binding.backButton.setOnClickListener(v -> navigateBack());
            return;
        }

        binding.backButton.setOnClickListener(v -> navigateBack());
        binding.changePhotoButton.setOnClickListener(v -> photoPicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.saveButton.setOnClickListener(v -> save());

        loadProfile();
    }

    private void loadProfile() {
        UserRepository.getProfile(user.getUid(), new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                if (binding == null) {
                    return;
                }
                if (profile == null) {
                    prefillFromAuth();
                    return;
                }
                binding.nameEdit.setText(profile.name);
                binding.usernameValue.setText(profile.hasUsername()
                        ? getString(R.string.username_with_at, profile.username)
                        : getString(R.string.username_placeholder));
                binding.aboutEdit.setText(profile.about);
                binding.avatar.setText(Initials.of(profile.name));
                currentPhotoUrl = profile.photoUrl;
                bindAvatar();
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load the profile.", exception);
                if (binding != null) {
                    prefillFromAuth();
                }
            }
        });
    }

    private void prefillFromAuth() {
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail();
            name = email != null && email.contains("@")
                    ? email.substring(0, email.indexOf('@'))
                    : "";
        }
        binding.nameEdit.setText(name);
        binding.usernameValue.setText(R.string.username_placeholder);
        binding.avatar.setText(Initials.of(name));
        currentPhotoUrl = user.getPhotoUrl() == null
                ? null
                : user.getPhotoUrl().toString();
        bindAvatar();
    }

    private void bindAvatar() {
        if (binding == null) {
            return;
        }
        binding.avatarPhoto.setImageDrawable(null);
        Object model = selectedPhotoUri != null ? selectedPhotoUri : currentPhotoUrl;
        if (model == null) {
            Glide.with(binding.avatarPhoto).clear(binding.avatarPhoto);
            binding.avatarPhoto.setVisibility(View.GONE);
            return;
        }
        binding.avatarPhoto.setVisibility(View.VISIBLE);
        Glide.with(binding.avatarPhoto).load(model).into(binding.avatarPhoto);
    }

    private void save() {
        if (binding == null || saving || user == null) {
            return;
        }
        String name = textOf(binding.nameEdit).trim();
        String about = textOf(binding.aboutEdit).trim();
        if (name.isEmpty()) {
            showError(R.string.name_required);
            return;
        }

        clearError();
        setLoading(true);
        if (selectedPhotoUri == null) {
            updateProfile(name, about, currentPhotoUrl);
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        imageExecutor.execute(() -> {
            try {
                ImageCompressor.Result image =
                        ImageCompressor.compress(appContext, selectedPhotoUri, 1024, 85);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> uploadAvatar(name, about, image));
            } catch (Exception exception) {
                Log.w(TAG, "Could not prepare the profile photo.", exception);
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    showError(R.string.photo_upload_error);
                });
            }
        });
    }

    private void uploadAvatar(@NonNull String name,
                              @NonNull String about,
                              @NonNull ImageCompressor.Result image) {
        if (binding == null) {
            return;
        }
        MediaRepository.uploadAvatar(user.getUid(), image.bytes,
                new MediaRepository.UploadCallback() {
                    @Override
                    public void onUploaded(@NonNull String url) {
                        currentPhotoUrl = url;
                        updateProfile(name, about, url);
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not upload the profile photo.", exception);
                        if (binding == null) {
                            return;
                        }
                        setLoading(false);
                        showError(R.string.photo_upload_error);
                    }
                });
    }

    private void updateProfile(@NonNull String name,
                               @NonNull String about,
                               @Nullable String photoUrl) {
        if (binding == null) {
            return;
        }
        UserRepository.updateProfile(user, name, about, photoUrl,
                new UserRepository.UpdateCallback() {
                    @Override
                    public void onUpdated() {
                        if (binding == null) {
                            return;
                        }
                        ChatRepository.updateMemberInfo(user.getUid(), name, photoUrl,
                                new ChatRepository.UpdateCallback() {
                                    @Override
                                    public void onUpdated() {
                                        navigateBack();
                                    }

                                    @Override
                                    public void onError(@NonNull Exception exception) {
                                        Log.w(TAG, "Could not update chat profiles.",
                                                exception);
                                        navigateBack();
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull Exception exception) {
                        Log.w(TAG, "Could not update the profile.", exception);
                        if (binding == null) {
                            return;
                        }
                        setLoading(false);
                        showError(R.string.profile_save_error);
                    }
                });
    }

    private void navigateBack() {
        if (leaving || !isAdded()) {
            return;
        }
        leaving = true;
        setLoading(false);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void setLoading(boolean loading) {
        saving = loading;
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.saveButton.setEnabled(!loading);
        binding.changePhotoButton.setEnabled(!loading);
        binding.nameEdit.setEnabled(!loading);
        binding.aboutEdit.setEnabled(!loading);
        binding.saveButton.setText(loading ? R.string.saving_profile : R.string.save_changes);
    }

    private void showError(int messageRes) {
        if (binding == null) {
            return;
        }
        binding.errorText.setText(messageRes);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        if (binding != null) {
            binding.errorText.setVisibility(View.GONE);
        }
    }

    @NonNull
    private static String textOf(
            @NonNull com.google.android.material.textfield.TextInputEditText editText) {
        android.text.Editable editable = editText.getText();
        return editable == null ? "" : editable.toString();
    }

    @Override
    public void onDestroyView() {
        if (imageExecutor != null) {
            imageExecutor.shutdownNow();
            imageExecutor = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
