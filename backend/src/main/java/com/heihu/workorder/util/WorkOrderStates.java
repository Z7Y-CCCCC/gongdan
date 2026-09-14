package com.heihu.workorder.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Includes historical API aliases so existing orders keep their meaning. */
public final class WorkOrderStates {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "NOT_STARTED", "IN_PROGRESS", "EXECUTING", "PAUSED", "COMPLETED",
            "FINISHED", "CLOSED", "CANCELED", "CANCELLED"));
    private static final Set<String> FINISHED = new HashSet<>(Arrays.asList("COMPLETED", "FINISHED", "CLOSED"));

    private WorkOrderStates() { }

    public static boolean isKnown(String status) { return KNOWN.contains(status); }

    public static boolean isFinished(String status) { return FINISHED.contains(status); }

    public static boolean isReportable(String status) {
        return "NOT_STARTED".equals(status) || "EXECUTING".equals(status) || "IN_PROGRESS".equals(status);
    }
}
