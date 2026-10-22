package com.tulia.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.tulia.app.data.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UserRepository {

    public interface ProfileCallback {
        void onResult(@Nullable UserProfile profile);

        void onError(@NonNull Exception exception);
    }

    public interface SearchCallback {
        void onResults(@NonNull List<UserProfile> users);

        void onError(@NonNull Exception exception);
    }

    public interface SaveCallback {
        void onSaved();

        void onUsernameTaken();

        void onError(@NonNull Exception exception);
    }

    public interface UpdateCallback {
        void onUpdated();

        void onError(@NonNull Exception exception);
    }

    private UserRepository() {
    }

    public static void getProfile(@NonNull String uid, @NonNull ProfileCallback callback) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> callback.onResult(
                        document.exists() ? UserProfile.from(document) : null))
                .addOnFailureListener(callback::onError);
    }

    public static void searchByUsername(@NonNull String rawQuery,
                                        @NonNull String excludeUid,
                                        @NonNull SearchCallback callback) {
        String query = normalizeUsername(rawQuery);
        if (query.length() < 2) {
            callback.onResults(Collections.emptyList());
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .orderBy("usernameLower")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<UserProfile> results = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (document.getId().equals(excludeUid)) {
                            continue;
                        }
                        UserProfile profile = UserProfile.from(document);
                        if (profile.hasUsername()) {
                            results.add(profile);
                        }
                    }
                    callback.onResults(results);
                })
                .addOnFailureListener(callback::onError);
    }

    public static void saveProfile(@NonNull FirebaseUser user,
                                   @NonNull String name,
                                   @NonNull String rawUsername,
                                   @NonNull SaveCallback callback) {
        String username = normalizeUsername(rawUsername);
        String uid = user.getUid();
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference userRef = database.collection("users").document(uid);
        DocumentReference usernameRef = database.collection("usernames").document(username);

        database.runTransaction(transaction -> {
            DocumentSnapshot usernameDocument = transaction.get(usernameRef);
            boolean usernameExists = usernameDocument.exists();
            if (usernameExists) {
                String owner = usernameDocument.getString("uid");
                if (!uid.equals(owner)) {
                    throw new UsernameTakenException();
                }
            }

            DocumentSnapshot userDocument = transaction.get(userRef);
            Map<String, Object> profile = new HashMap<>();
            profile.put("name", name);
            profile.put("username", username);
            profile.put("usernameLower", username);
            if (user.getEmail() != null) {
                profile.put("email", user.getEmail());
            }
            if (user.getPhotoUrl() != null) {
                profile.put("photoUrl", user.getPhotoUrl().toString());
            }
            if (!userDocument.exists()) {
                profile.put("about", "");
                profile.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            }
            transaction.set(userRef, profile, SetOptions.merge());

            if (!usernameExists) {
                Map<String, Object> usernameData = new HashMap<>();
                usernameData.put("uid", uid);
                usernameData.put("username", username);
                usernameData.put("createdAt",
                        com.google.firebase.firestore.FieldValue.serverTimestamp());
                transaction.set(usernameRef, usernameData);
            }
            return null;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                updateDisplayName(user, name);
                callback.onSaved();
            } else if (isUsernameTaken(task.getException())) {
                callback.onUsernameTaken();
            } else {
                callback.onError(task.getException() == null
                        ? new IllegalStateException("Could not save the profile.")
                        : task.getException());
            }
        });
    }

    public static void updateProfile(@NonNull FirebaseUser user,
                                     @NonNull String name,
                                     @NonNull String about,
                                     @Nullable String photoUrl,
                                     @NonNull UpdateCallback callback) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("about", about);
        if (photoUrl != null) {
            profile.put("photoUrl", photoUrl);
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(profile, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateDisplayName(user, name);
                        callback.onUpdated();
                    } else {
                        callback.onError(task.getException() == null
                                ? new IllegalStateException("Could not update the profile.")
                                : task.getException());
                    }
                });
    }

    @NonNull
    public static String normalizeUsername(@NonNull String rawUsername) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        return username.startsWith("@") ? username.substring(1) : username;
    }

    private static void updateDisplayName(@NonNull FirebaseUser user, @NonNull String name) {
        if (name.equals(user.getDisplayName())) {
            return;
        }
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        user.updateProfile(request);
    }

    private static boolean isUsernameTaken(@Nullable Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UsernameTakenException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class UsernameTakenException extends RuntimeException {
    }
}
