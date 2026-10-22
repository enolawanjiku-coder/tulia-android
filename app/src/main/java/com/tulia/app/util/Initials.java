package com.tulia.app.util;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class Initials {

    private Initials() {
    }

    @NonNull
    public static String of(@NonNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "T";
        }
        String[] parts = trimmed.split("\\s+");
        StringBuilder initials = new StringBuilder();
        initials.append(parts[0].charAt(0));
        if (parts.length > 1) {
            initials.append(parts[parts.length - 1].charAt(0));
        }
        return initials.toString().toUpperCase(Locale.ROOT);
    }
}
