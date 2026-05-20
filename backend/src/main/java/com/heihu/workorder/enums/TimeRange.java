package com.heihu.workorder.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 时间范围枚举，用于主页仪表盘的时间区间查询。
 * 所有区间均为半开区间 [start, end)。
 */
public enum TimeRange {

    TODAY,
    YESTERDAY,
    THIS_MONTH;

    /**
     * 根据当前时间计算半开时间区间 [start, end)。
     *
     * @param now 当前时间
     * @return 包含 start（含）和 end（不含）的时间区间数组，index 0 为 start，index 1 为 end
     */
    public LocalDateTime[] toInterval(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        switch (this) {
            case TODAY:
                return new LocalDateTime[]{
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                };
            case YESTERDAY:
                return new LocalDateTime[]{
                        today.minusDays(1).atStartOfDay(),
                        today.atStartOfDay()
                };
            case THIS_MONTH:
                LocalDate firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
                LocalDate firstDayOfNextMonth = today.with(TemporalAdjusters.firstDayOfNextMonth());
                return new LocalDateTime[]{
                        firstDayOfMonth.atStartOfDay(),
                        firstDayOfNextMonth.atStartOfDay()
                };
            default:
                throw new IllegalStateException("Unknown TimeRange: " + this);
        }
    }

    /**
     * 从查询参数字符串解析 TimeRange。
     *
     * @param value 查询参数值（today, yesterday, this_month）
     * @return 对应的 TimeRange 枚举值
     * @throws IllegalArgumentException 如果参数值不合法
     */
    public static TimeRange fromParam(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TODAY;
        }
        switch (value.toLowerCase()) {
            case "today":
                return TODAY;
            case "yesterday":
                return YESTERDAY;
            case "this_month":
                return THIS_MONTH;
            default:
                throw new IllegalArgumentException("Invalid time range: " + value);
        }
    }
}
