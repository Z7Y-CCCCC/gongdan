package com.heihu.workorder.enums;

/**
 * 工单进度报表的状态标签枚举，对应前端 7 个切换标签。
 */
public enum WorkOrderStatusTab {

    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    DUE_WITHIN_3_DAYS("due_within_3_days"),
    OVERDUE_UNFINISHED("overdue_unfinished"),
    OVERDUE_FINISHED_THIS_MONTH("overdue_finished_this_month"),
    FINISHED_TODAY("finished_today"),
    FINISHED_THIS_MONTH("finished_this_month");

    private final String paramValue;

    WorkOrderStatusTab(String paramValue) {
        this.paramValue = paramValue;
    }

    /**
     * 获取对应的查询参数字符串值。
     */
    public String getParamValue() {
        return paramValue;
    }

    /**
     * 从查询参数字符串解析 WorkOrderStatusTab。
     *
     * @param value 查询参数值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果参数值不合法
     */
    public static WorkOrderStatusTab fromParam(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Status tab parameter is required");
        }
        for (WorkOrderStatusTab tab : values()) {
            if (tab.paramValue.equals(value.toLowerCase())) {
                return tab;
            }
        }
        throw new IllegalArgumentException("Invalid status tab: " + value);
    }
}
