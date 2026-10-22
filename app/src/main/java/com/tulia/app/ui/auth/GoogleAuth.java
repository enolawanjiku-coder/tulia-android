package com.tulia.app.ui.auth;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.tulia.app.R;

import java.util.HashMap;
import java.util.Map;

public final class GoogleAuth {

    private static final String TAG = "GoogleAuth";

    public interface Callback {
        void onSuccess(@NonNull FirebaseUser user);

        void onError(@StringRes int messageRes);

        void onCancelled();
    }

    private GoogleAuth() {
    }

    public static void signIn(@NonNull Fragment fragment,
                              @NonNull FirebaseAuth auth,
                              @NonNull Callback callback) {
        Context context = fragment.requireContext();
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(R.string.google_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        try {
            CredentialManager.create(context).getCredentialAsync(
                    fragment.requireActivity(),
                    request,
                    null,
                    ContextCompat.getMainExecutor(context),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            if (!fragment.isAdded()) {
                                return;
                            }
                            Credential credential = result.getCredential();
                            if (!(credential instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(credential.getType())) {
                                callback.onError(R.string.auth_error_google);
                                return;
                            }
                            try {
                                GoogleIdTokenCredential googleCredential =
                                        GoogleIdTokenCredential.createFrom(credential.getData());
                                signInToFirebase(fragment, auth, googleCredential.getIdToken(),
                                        callback);
                            } catch (Exception exception) {
                                Log.w(TAG, "Could not parse the Google ID token.", exception);
                                callback.onError(R.string.auth_error_google);
                            }
                        }

                        @Override
                        public void onError(GetCredentialException exception) {
                            if (!fragment.isAdded()) {
                                return;
                            }
                            if (exception instanceof GetCredentialCancellationException) {
                                callback.onCancelled();
                            } else if (exception instanceof NoCredentialException) {
                                callback.onError(R.string.auth_error_no_google_account);
                            } else {
                                Log.w(TAG, "Google credential request failed.", exception);
                                callback.onError(R.string.auth_error_google);
                            }
                        }
                    });
        } catch (Exception exception) {
            Log.w(TAG, "Could not start the Google credential request.", exception);
            callback.onError(R.string.auth_error_google);
        }
    }

    private static void signInToFirebase(Fragment fragment,
                                         FirebaseAuth auth,
                                         String idToken,
                                         Callback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (!task.isSuccessful()) {
                Log.w(TAG, "Firebase Google sign-in failed.", task.getException());
                callback.onError(R.string.auth_error_google);
                return;
            }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                callback.onError(R.string.auth_error_generic);
                return;
            }
            writeProfile(user);
            callback.onSuccess(user);
        });
    }

    private static void writeProfile(FirebaseUser user) {
        Map<String, Object> profile = new HashMap<>();
        if (user.getDisplayName() != null) {
            profile.put("name", user.getDisplayName());
        }
        if (user.getEmail() != null) {
            profile.put("email", user.getEmail());
        }
        if (user.getPhotoUrl() != null) {
            profile.put("photoUrl", user.getPhotoUrl().toString());
        }
        if (profile.isEmpty()) {
            return;
        }

        try {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(profile, SetOptions.merge())
                    .addOnFailureListener(exception ->
                            Log.w(TAG, "Could not sync the Google profile.", exception));
        } catch (IllegalStateException exception) {
            Log.w(TAG, "Firestore is not configured.", exception);
        }
    }
}
