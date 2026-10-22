package com.tulia.app.update;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class UpdateInfo {

    @NonNull
    public final int versionCode;
    @NonNull
    public final String versionName;
    @NonNull
    public final String notes;
    @NonNull
    public final String downloadUrl;

    public UpdateInfo(@NonNull int versionCode,
                      @NonNull String versionName,
                      @NonNull String notes,
                      @NonNull String downloadUrl) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.notes = notes;
        this.downloadUrl = downloadUrl;
    }

    @Nullable
    public static Integer parseVersionCode(@Nullable String tag) {
        if (tag == null) {
            return null;
        }
        String cleaned = tag.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        int separator = cleaned.indexOf('-');
        if (separator > 0) {
            cleaned = cleaned.substring(0, separator);
        }
        try {
            return Integer.valueOf(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
