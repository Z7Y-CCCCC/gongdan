package com.heihu.workorder.dto;

import java.util.List;
import java.util.Map;

public class MonitorCacheMetricsResponse {
    private Map<String, String> basicInfo;
    private List<MonitorCommandStatResponse> commandStats;
    private Map<String, Object> memoryInfo;

    public Map<String, String> getBasicInfo() { return basicInfo; }
    public void setBasicInfo(Map<String, String> basicInfo) { this.basicInfo = basicInfo; }
    public List<MonitorCommandStatResponse> getCommandStats() { return commandStats; }
    public void setCommandStats(List<MonitorCommandStatResponse> commandStats) { this.commandStats = commandStats; }
    public Map<String, Object> getMemoryInfo() { return memoryInfo; }
    public void setMemoryInfo(Map<String, Object> memoryInfo) { this.memoryInfo = memoryInfo; }
}
