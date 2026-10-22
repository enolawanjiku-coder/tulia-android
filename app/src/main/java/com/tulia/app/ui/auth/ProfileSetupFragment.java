package com.tulia.app.ui.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.UserProfile;
import com.tulia.app.databinding.FragmentProfileSetupBinding;

import java.util.regex.Pattern;

public class ProfileSetupFragment extends Fragment {

    private static final String TAG = "ProfileSetupFragment";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,20}$");

    private FragmentProfileSetupBinding binding;
    private FirebaseAuth auth;
    private boolean leaving;

    public ProfileSetupFragment() {
        super(R.layout.fragment_profile_setup);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentProfileSetupBinding.bind(view);

        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            disableForm(R.string.auth_error_generic);
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            disableForm(R.string.auth_error_generic);
            return;
        }

        prefill(user);
        binding.continueButton.setOnClickListener(v -> saveProfile(user));
        binding.switchAccountButton.setOnClickListener(v -> switchAccount());
        binding.usernameEdit.setOnEditorActionListener((v, actionId, event) -> {
            saveProfile(user);
            return true;
        });
    }

    private void prefill(@NonNull FirebaseUser user) {
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail();
            name = email != null && email.contains("@")
                    ? email.substring(0, email.indexOf('@'))
                    : "";
        }
        binding.nameEdit.setText(name);

        UserRepository.getProfile(user.getUid(), new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                if (binding == null) {
                    return;
                }
                if (profile != null && profile.hasUsername()) {
                    navigateToChats();
                } else if (profile != null) {
                    binding.nameEdit.setText(profile.name);
                }
            }

            @Override
            public void onError(@NonNull Exception exception) {
                Log.w(TAG, "Could not load the profile.", exception);
            }
        });
    }

    private void saveProfile(@NonNull FirebaseUser user) {
        String name = textOf(binding.nameEdit).trim();
        String username = UserRepository.normalizeUsername(textOf(binding.usernameEdit));

        if (name.isEmpty()) {
            showError(R.string.name_required);
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            showError(R.string.username_invalid);
            return;
        }

        clearError();
        setLoading(true);
        binding.usernameEdit.setText(username);

        UserRepository.saveProfile(user, name, username, new UserRepository.SaveCallback() {
            @Override
            public void onSaved() {
                if (binding == null || !isAdded()) {
                    return;
                }
                setLoading(false);
                navigateToChats();
            }

            @Override
            public void onUsernameTaken() {
                if (binding == null || !isAdded()) {
                    return;
                }
                setLoading(false);
                showError(R.string.username_taken);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (binding == null || !isAdded()) {
                    return;
                }
                Log.w(TAG, "Could not save the profile.", exception);
                setLoading(false);
                showError(R.string.profile_save_error);
            }
        });
    }

    private void switchAccount() {
        if (auth != null) {
            auth.signOut();
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_global_to_welcome);
    }

    private void navigateToChats() {
        if (leaving || !isAdded()) {
            return;
        }
        leaving = true;
        NavHostFragment.findNavController(this).navigate(R.id.action_global_to_chats);
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.continueButton.setEnabled(!loading);
        binding.switchAccountButton.setEnabled(!loading);
        binding.nameEdit.setEnabled(!loading);
        binding.usernameEdit.setEnabled(!loading);
    }

    private void disableForm(int messageRes) {
        showError(messageRes);
        binding.continueButton.setEnabled(false);
        binding.switchAccountButton.setEnabled(false);
        binding.nameEdit.setEnabled(false);
        binding.usernameEdit.setEnabled(false);
    }

    private void showError(int messageRes) {
        binding.errorText.setText(messageRes);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
    }

    private static String textOf(com.google.android.material.textfield.TextInputEditText editText) {
        android.text.Editable editable = editText.getText();
        return editable == null ? "" : editable.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
