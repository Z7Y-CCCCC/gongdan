package com.heihu.workorder.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Includes historical API aliases so existing orders keep their meaning. */
public final class WorkOrderStates {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "NOT_STARTED", "IN_PROGRESS", "EXECUTING", "PAUSED", "COMPLETED",
            "FINISHED", "CLOSED", "CANCELED", "CANCELLED"));
    private static final Set<String> FINISHED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("COMPLETED", "FINISHED", "CLOSED")));
    private static final Set<String> EXECUTING = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("EXECUTING", "IN_PROGRESS")));
    private static final Set<String> CANCELED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("CANCELED", "CANCELLED")));
    private static final Set<String> REPORTABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("NOT_STARTED", "EXECUTING", "IN_PROGRESS")));

    private WorkOrderStates() { }

    public static boolean isKnown(String status) { return KNOWN.contains(status); }

    public static boolean isFinished(String status) { return FINISHED.contains(status); }

    public static boolean isReportable(String status) {
        return "NOT_STARTED".equals(status) || EXECUTING.contains(status);
    }

    public static Set<String> reportableValues() { return REPORTABLE; }

    public static Set<String> filterValues(String status) {
        if (status == null || status.isEmpty() || "ALL".equals(status)) return Collections.emptySet();
        if (FINISHED.contains(status)) return FINISHED;
        if (EXECUTING.contains(status)) return EXECUTING;
        if (CANCELED.contains(status)) return CANCELED;
        if (!isKnown(status)) throw new IllegalArgumentException("无效的工单状态筛选");
        return Collections.singleton(status);
    }
}
