package com.tulia.app.data;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePrefs {

    private static final String PREFS = "tulia_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemePrefs() {
    }

    public static int getMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setMode(Context context, int mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void applySavedMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context));
    }
}
