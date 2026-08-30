package com.heihu.workorder.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DateTimeParser {

    private static final DateTimeFormatter SECOND_PRECISION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE_PRECISION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateTimeParser() {
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Fall through to the API's space-separated date formats.
        }

        String normalized = trimmed.replace('T', ' ');
        try {
            if (normalized.length() == 16) {
                return LocalDateTime.parse(normalized, MINUTE_PRECISION);
            }
            return LocalDateTime.parse(normalized, SECOND_PRECISION);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期时间格式无效，应为 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-ddTHH:mm", exception);
        }
    }
}
