package com.tulia.app.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tulia.app.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeFormat {

    private static final String TIME_PATTERN = "HH:mm";
    private static final String DAY_PATTERN = "d MMM";
    private static final String FULL_DAY_PATTERN = "EEEE, d MMM";
    private static final String DATED_PATTERN = "d MMM yyyy";

    private TimeFormat() {
    }

    @NonNull
    public static String chatTime(@NonNull Context context, long millis) {
        if (millis <= 0) {
            return "";
        }
        LocalDate date = localDate(millis);
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return formatter(TIME_PATTERN).format(localDateTime(millis));
        }
        if (date.equals(today.minusDays(1))) {
            return context.getString(R.string.date_yesterday);
        }
        if (date.getYear() == today.getYear()) {
            return formatter(DAY_PATTERN).format(date);
        }
        return formatter(DATED_PATTERN).format(date);
    }

    @NonNull
    public static String messageTime(long millis) {
        return millis <= 0 ? "" : formatter(TIME_PATTERN).format(localDateTime(millis));
    }

    @NonNull
    public static String dateLabel(@NonNull Context context, long millis) {
        LocalDate date = localDate(millis);
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return context.getString(R.string.date_today);
        }
        if (date.equals(today.minusDays(1))) {
            return context.getString(R.string.date_yesterday);
        }
        if (date.getYear() == today.getYear()) {
            return formatter(FULL_DAY_PATTERN).format(date);
        }
        return formatter(DATED_PATTERN).format(date);
    }

    public static boolean isSameDay(long firstMillis, long secondMillis) {
        return localDate(firstMillis).equals(localDate(secondMillis));
    }

    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault());
    }

    private static LocalDate localDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDateTime localDateTime(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
