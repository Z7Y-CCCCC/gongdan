package com.heihu.workorder.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderStatusTabTest {

    @Test
    @DisplayName("All enum values have correct param values")
    void paramValues() {
        assertEquals("not_started", WorkOrderStatusTab.NOT_STARTED.getParamValue());
        assertEquals("in_progress", WorkOrderStatusTab.IN_PROGRESS.getParamValue());
        assertEquals("due_within_3_days", WorkOrderStatusTab.DUE_WITHIN_3_DAYS.getParamValue());
        assertEquals("overdue_unfinished", WorkOrderStatusTab.OVERDUE_UNFINISHED.getParamValue());
        assertEquals("overdue_finished_this_month", WorkOrderStatusTab.OVERDUE_FINISHED_THIS_MONTH.getParamValue());
        assertEquals("finished_today", WorkOrderStatusTab.FINISHED_TODAY.getParamValue());
        assertEquals("finished_this_month", WorkOrderStatusTab.FINISHED_THIS_MONTH.getParamValue());
    }

    @Test
    @DisplayName("fromParam parses all valid values")
    void fromParamValid() {
        assertEquals(WorkOrderStatusTab.NOT_STARTED, WorkOrderStatusTab.fromParam("not_started"));
        assertEquals(WorkOrderStatusTab.IN_PROGRESS, WorkOrderStatusTab.fromParam("in_progress"));
        assertEquals(WorkOrderStatusTab.DUE_WITHIN_3_DAYS, WorkOrderStatusTab.fromParam("due_within_3_days"));
        assertEquals(WorkOrderStatusTab.OVERDUE_UNFINISHED, WorkOrderStatusTab.fromParam("overdue_unfinished"));
        assertEquals(WorkOrderStatusTab.OVERDUE_FINISHED_THIS_MONTH, WorkOrderStatusTab.fromParam("overdue_finished_this_month"));
        assertEquals(WorkOrderStatusTab.FINISHED_TODAY, WorkOrderStatusTab.fromParam("finished_today"));
        assertEquals(WorkOrderStatusTab.FINISHED_THIS_MONTH, WorkOrderStatusTab.fromParam("finished_this_month"));
    }

    @Test
    @DisplayName("fromParam is case-insensitive")
    void fromParamCaseInsensitive() {
        assertEquals(WorkOrderStatusTab.NOT_STARTED, WorkOrderStatusTab.fromParam("NOT_STARTED"));
        assertEquals(WorkOrderStatusTab.IN_PROGRESS, WorkOrderStatusTab.fromParam("In_Progress"));
    }

    @Test
    @DisplayName("fromParam throws for null")
    void fromParamNull() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderStatusTab.fromParam(null));
    }

    @Test
    @DisplayName("fromParam throws for empty string")
    void fromParamEmpty() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderStatusTab.fromParam(""));
    }

    @Test
    @DisplayName("fromParam throws for invalid value")
    void fromParamInvalid() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderStatusTab.fromParam("invalid_tab"));
    }

    @Test
    @DisplayName("Enum has exactly 7 values")
    void enumCount() {
        assertEquals(7, WorkOrderStatusTab.values().length);
    }
}
