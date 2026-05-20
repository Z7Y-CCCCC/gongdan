package com.heihu.workorder.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeRangeTest {

    @Test
    @DisplayName("TODAY returns [today 00:00, tomorrow 00:00)")
    void todayInterval() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
        LocalDateTime[] interval = TimeRange.TODAY.toInterval(now);

        assertEquals(LocalDateTime.of(2024, 3, 15, 0, 0, 0), interval[0]);
        assertEquals(LocalDateTime.of(2024, 3, 16, 0, 0, 0), interval[1]);
    }

    @Test
    @DisplayName("YESTERDAY returns [yesterday 00:00, today 00:00)")
    void yesterdayInterval() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
        LocalDateTime[] interval = TimeRange.YESTERDAY.toInterval(now);

        assertEquals(LocalDateTime.of(2024, 3, 14, 0, 0, 0), interval[0]);
        assertEquals(LocalDateTime.of(2024, 3, 15, 0, 0, 0), interval[1]);
    }

    @Test
    @DisplayName("THIS_MONTH returns [first day of month 00:00, first day of next month 00:00)")
    void thisMonthInterval() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
        LocalDateTime[] interval = TimeRange.THIS_MONTH.toInterval(now);

        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0, 0), interval[0]);
        assertEquals(LocalDateTime.of(2024, 4, 1, 0, 0, 0), interval[1]);
    }

    @Test
    @DisplayName("THIS_MONTH at month boundary (last day)")
    void thisMonthAtLastDay() {
        LocalDateTime now = LocalDateTime.of(2024, 1, 31, 23, 59, 59);
        LocalDateTime[] interval = TimeRange.THIS_MONTH.toInterval(now);

        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), interval[0]);
        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0, 0), interval[1]);
    }

    @Test
    @DisplayName("THIS_MONTH at first day of month")
    void thisMonthAtFirstDay() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 1, 0, 0, 0);
        LocalDateTime[] interval = TimeRange.THIS_MONTH.toInterval(now);

        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0, 0), interval[0]);
        assertEquals(LocalDateTime.of(2024, 4, 1, 0, 0, 0), interval[1]);
    }

    @Test
    @DisplayName("fromParam parses valid values")
    void fromParamValid() {
        assertEquals(TimeRange.TODAY, TimeRange.fromParam("today"));
        assertEquals(TimeRange.YESTERDAY, TimeRange.fromParam("yesterday"));
        assertEquals(TimeRange.THIS_MONTH, TimeRange.fromParam("this_month"));
    }

    @Test
    @DisplayName("fromParam is case-insensitive")
    void fromParamCaseInsensitive() {
        assertEquals(TimeRange.TODAY, TimeRange.fromParam("TODAY"));
        assertEquals(TimeRange.YESTERDAY, TimeRange.fromParam("Yesterday"));
        assertEquals(TimeRange.THIS_MONTH, TimeRange.fromParam("THIS_MONTH"));
    }

    @Test
    @DisplayName("fromParam returns TODAY for null or empty")
    void fromParamDefault() {
        assertEquals(TimeRange.TODAY, TimeRange.fromParam(null));
        assertEquals(TimeRange.TODAY, TimeRange.fromParam(""));
        assertEquals(TimeRange.TODAY, TimeRange.fromParam("   "));
    }

    @Test
    @DisplayName("fromParam throws for invalid value")
    void fromParamInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TimeRange.fromParam("invalid"));
    }
}
