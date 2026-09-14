package com.heihu.workorder.util;

import com.heihu.workorder.dto.WorkOrderRequest.TaskItem;
import com.heihu.workorder.model.FactoryUser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** The same task permission applies to the submitter and the selected reporter. */
public final class ReportPermission {
    private ReportPermission() { }

    public static boolean allows(TaskItem task, FactoryUser user) {
        if (task == null || user == null || !user.isEnabled() || Boolean.FALSE.equals(task.getEnabled())) {
            return false;
        }
        String permission = trim(task.getReportPermission());
        if (permission.isEmpty() || "所有人".equals(permission) || "all".equalsIgnoreCase(permission)) {
            return true;
        }
        Set<String> identities = new HashSet<>(Arrays.asList(trim(user.getName()), trim(user.getUsername())));
        if (user.getId() != null) identities.add(user.getId().toString());
        if (user.getRole() != null) {
            identities.add(user.getRole().name());
            identities.add(user.getRole().getLabel());
        }
        if (user.getDepartment() != null) identities.add(trim(user.getDepartment().getName()));
        identities.remove("");
        String assignee = trim(task.getAssignee());
        if (!assignee.isEmpty() && (assignee.equals(trim(user.getName())) || assignee.equals(trim(user.getUsername())))) {
            return true;
        }
        for (String allowed : permission.split("[,，]")) {
            if (identities.contains(allowed.trim())) return true;
        }
        return false;
    }

    private static String trim(String text) { return text == null ? "" : text.trim(); }
}
