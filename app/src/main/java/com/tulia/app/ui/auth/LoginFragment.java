package com.tulia.app.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.tulia.app.R;
import com.tulia.app.databinding.FragmentLoginBinding;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";

    private FragmentLoginBinding binding;
    private FirebaseAuth auth;

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentLoginBinding.bind(view);

        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            binding.signInButton.setEnabled(false);
            binding.googleButton.setEnabled(false);
            showError(R.string.auth_error_generic);
        }

        binding.backButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.createAccountLink.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_signup));

        binding.signInButton.setOnClickListener(v -> signIn());

        binding.googleButton.setOnClickListener(v -> signInWithGoogle());

        binding.passwordEdit.setOnEditorActionListener((v, actionId, event) -> {
            signIn();
            return true;
        });
    }

    private void signIn() {
        if (auth == null) {
            showError(R.string.auth_error_generic);
            return;
        }

        String email = textOf(binding.emailEdit).trim();
        String password = textOf(binding.passwordEdit);

        if (email.isEmpty() || password.isEmpty()) {
            showError(R.string.auth_error_missing);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.auth_error_invalid_email);
            return;
        }

        clearError();
        hideKeyboard();
        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (binding == null || !isAdded()) {
                        return;
                    }
                    setLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            ProfileGate.route(this, user);
                        }
                    } else {
                        showError(messageFor(task.getException()));
                    }
                });
    }

    private void signInWithGoogle() {
        if (auth == null) {
            showError(R.string.auth_error_generic);
            return;
        }

        clearError();
        hideKeyboard();
        setLoading(true);

        GoogleAuth.signIn(this, auth, new GoogleAuth.Callback() {
            @Override
            public void onSuccess(@NonNull FirebaseUser user) {
                if (binding == null || !isAdded()) {
                    return;
                }
                setLoading(false);
                ProfileGate.route(LoginFragment.this, user);
            }

            @Override
            public void onError(int messageRes) {
                if (binding == null || !isAdded()) {
                    return;
                }
                setLoading(false);
                showError(messageRes);
            }

            @Override
            public void onCancelled() {
                if (binding == null || !isAdded()) {
                    return;
                }
                setLoading(false);
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.signInButton.setEnabled(!loading);
        binding.googleButton.setEnabled(!loading);
        binding.emailEdit.setEnabled(!loading);
        binding.passwordEdit.setEnabled(!loading);
        binding.signInButton.setText(loading ? R.string.signing_in : R.string.sign_in);
    }

    private void showError(int messageRes) {
        binding.errorText.setText(messageRes);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
    }

    private int messageFor(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidUserException
                || exception instanceof FirebaseAuthInvalidCredentialsException) {
            return R.string.auth_error_credentials;
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return R.string.auth_error_email_in_use;
        }
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return R.string.auth_error_weak_password;
        }
        if (exception instanceof FirebaseNetworkException) {
            return R.string.auth_error_network;
        }
        return R.string.auth_error_generic;
    }

    private void hideKeyboard() {
        View focus = requireActivity().getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager manager = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private static String textOf(TextInputEditText editText) {
        Editable editable = editText.getText();
        return editable == null ? "" : editable.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
