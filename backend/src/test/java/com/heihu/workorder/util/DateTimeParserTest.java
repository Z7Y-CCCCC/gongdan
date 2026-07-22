package com.heihu.workorder.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeParserTest {

    @Test
    void acceptsHtmlDateTimeLocalValue() {
        assertEquals(
                LocalDateTime.of(2026, 7, 22, 9, 30),
                DateTimeParser.parse("2026-07-22T09:30")
        );
    }

    @Test
    void acceptsApiDateTimeWithSeconds() {
        assertEquals(
                LocalDateTime.of(2026, 7, 22, 9, 30, 15),
                DateTimeParser.parse("2026-07-22 09:30:15")
        );
    }

    @Test
    void preservesIsoFractionalSecondsForIncrementalPolling() {
        assertEquals(
                LocalDateTime.of(2026, 7, 22, 9, 30, 15, 123_456_000),
                DateTimeParser.parse("2026-07-22T09:30:15.123456")
        );
    }

    @Test
    void rejectsInvalidDateTimeWithClientError() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("2026/07/22 09:30"));
    }
}
