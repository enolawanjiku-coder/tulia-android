package com.tulia.app.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.data.UserRepository;
import com.tulia.app.data.model.UserProfile;

public final class ProfileGate {

    private ProfileGate() {
    }

    public static void route(@NonNull Fragment fragment, @NonNull FirebaseUser user) {
        UserRepository.getProfile(user.getUid(), new UserRepository.ProfileCallback() {
            @Override
            public void onResult(@Nullable UserProfile profile) {
                if (!fragment.isAdded()) {
                    return;
                }
                boolean needsSetup = profile == null || !profile.hasUsername();
                NavHostFragment.findNavController(fragment).navigate(needsSetup
                        ? R.id.action_global_to_profile_setup
                        : R.id.action_global_to_chats);
            }

            @Override
            public void onError(@NonNull Exception exception) {
                if (!fragment.isAdded()) {
                    return;
                }
                NavHostFragment.findNavController(fragment)
                        .navigate(R.id.action_global_to_profile_setup);
            }
        });
    }
}
