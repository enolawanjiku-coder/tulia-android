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

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.tulia.app.R;
import com.tulia.app.databinding.FragmentSignupBinding;

import java.util.HashMap;
import java.util.Map;

public class SignupFragment extends Fragment {

    private static final String TAG = "SignupFragment";

    private FragmentSignupBinding binding;
    private FirebaseAuth auth;

    public SignupFragment() {
        super(R.layout.fragment_signup);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentSignupBinding.bind(view);

        try {
            auth = FirebaseAuth.getInstance();
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firebase is not configured yet.", exception);
            binding.createButton.setEnabled(false);
            binding.googleButton.setEnabled(false);
            showError(R.string.auth_error_generic);
        }

        binding.backButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.signInLink.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_signup_to_login));

        binding.createButton.setOnClickListener(v -> createAccount());

        binding.googleButton.setOnClickListener(v -> signUpWithGoogle());

        binding.passwordEdit.setOnEditorActionListener((v, actionId, event) -> {
            createAccount();
            return true;
        });
    }

    private void createAccount() {
        if (auth == null) {
            showError(R.string.auth_error_generic);
            return;
        }

        String name = textOf(binding.nameEdit).trim();
        String email = textOf(binding.emailEdit).trim();
        String password = textOf(binding.passwordEdit);

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(R.string.auth_error_missing);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(R.string.auth_error_invalid_email);
            return;
        }
        if (password.length() < 8) {
            showError(R.string.auth_error_weak_password);
            return;
        }

        clearError();
        hideKeyboard();
        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (binding == null || !isAdded()) {
                        return;
                    }
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        showError(messageFor(task.getException()));
                        return;
                    }

                    FirebaseUser user = task.getResult().getUser();
                    if (user == null) {
                        setLoading(false);
                        showError(R.string.auth_error_generic);
                        return;
                    }

                    UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    user.updateProfile(request).addOnCompleteListener(profileTask -> {
                        if (profileTask.isSuccessful() || profileTask.getException() == null) {
                            Log.d(TAG, "Profile name updated.");
                        } else {
                            Log.w(TAG, "Could not update display name.", profileTask.getException());
                        }
                        writeProfile(user, name, email);
                    });
                });
    }

    private void writeProfile(FirebaseUser user, String name, String email) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("about", "");
        profile.put("createdAt", FieldValue.serverTimestamp());

        try {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(profile)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Could not create profile document.", task.getException());
                        }
                        finishSignup(user);
                    });
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firestore is not configured yet.", exception);
            finishSignup(user);
        }
    }

    private void finishSignup(FirebaseUser user) {
        if (binding == null || !isAdded()) {
            return;
        }
        setLoading(false);
        ProfileGate.route(this, user);
    }

    private void signUpWithGoogle() {
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
                ProfileGate.route(SignupFragment.this, user);
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
        binding.createButton.setEnabled(!loading);
        binding.googleButton.setEnabled(!loading);
        binding.nameEdit.setEnabled(!loading);
        binding.emailEdit.setEnabled(!loading);
        binding.passwordEdit.setEnabled(!loading);
        binding.createButton.setText(loading ? R.string.creating_account : R.string.create_account);
    }

    private void showError(int messageRes) {
        binding.errorText.setText(messageRes);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
    }

    private int messageFor(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return R.string.auth_error_email_in_use;
        }
        if (exception instanceof FirebaseAuthWeakPasswordException) {
            return R.string.auth_error_weak_password;
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return R.string.auth_error_invalid_email;
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
