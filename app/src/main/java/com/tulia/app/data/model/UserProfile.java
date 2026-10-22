package com.tulia.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

public final class UserProfile {

    @NonNull
    public final String uid;
    @NonNull
    public final String name;
    @NonNull
    public final String username;
    @NonNull
    public final String about;
    @Nullable
    public final String email;
    @Nullable
    public final String photoUrl;

    public UserProfile(@NonNull String uid,
                       @NonNull String name,
                       @NonNull String username,
                       @NonNull String about,
                       @Nullable String email,
                       @Nullable String photoUrl) {
        this.uid = uid;
        this.name = name;
        this.username = username;
        this.about = about;
        this.email = email;
        this.photoUrl = photoUrl;
    }

    @NonNull
    public static UserProfile from(@NonNull DocumentSnapshot document) {
        String name = document.getString("name");
        String username = document.getString("username");
        return new UserProfile(
                document.getId(),
                name == null || name.trim().isEmpty() ? "Tulia user" : name,
                username == null ? "" : username,
                document.getString("about") == null ? "" : document.getString("about"),
                document.getString("email"),
                document.getString("photoUrl"));
    }

    public boolean hasUsername() {
        return !username.trim().isEmpty();
    }
}
