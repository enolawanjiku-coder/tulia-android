package com.tulia.app.ui.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.databinding.FragmentWelcomeBinding;

public class WelcomeFragment extends Fragment {

    private static final String TAG = "WelcomeFragment";

    private FragmentWelcomeBinding binding;
    private FirebaseAuth auth;

    public WelcomeFragment() {
        super(R.layout.fragment_welcome);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentWelcomeBinding.bind(view);

        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured.", exception);
            binding.googleButton.setEnabled(false);
        }

        binding.continueButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_welcome_to_login));

        binding.googleButton.setOnClickListener(v -> signInWithGoogle());

        binding.createAccountButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_welcome_to_signup));
    }

    private void signInWithGoogle() {
        if (auth == null) {
            Snackbar.make(binding.getRoot(), R.string.auth_error_google, Snackbar.LENGTH_LONG).show();
            return;
        }

        setGoogleLoading(true);
        GoogleAuth.signIn(this, auth, new GoogleAuth.Callback() {
            @Override
            public void onSuccess(@NonNull FirebaseUser user) {
                if (binding == null) {
                    return;
                }
                setGoogleLoading(false);
                ProfileGate.route(WelcomeFragment.this, user);
            }

            @Override
            public void onError(int messageRes) {
                if (binding == null) {
                    return;
                }
                setGoogleLoading(false);
                Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onCancelled() {
                if (binding != null) {
                    setGoogleLoading(false);
                }
            }
        });
    }

    private void setGoogleLoading(boolean loading) {
        binding.googleButton.setEnabled(!loading);
        binding.googleButton.setText(loading
                ? R.string.signing_in
                : R.string.continue_with_google);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
