package com.heihu.workorder.dto;

public class MonitorCacheNameResponse {
    private String cacheName;
    private String remark;
    private int keyCount;

    public MonitorCacheNameResponse() {}

    public MonitorCacheNameResponse(String cacheName, String remark, int keyCount) {
        this.cacheName = cacheName;
        this.remark = remark;
        this.keyCount = keyCount;
    }

    public String getCacheName() { return cacheName; }
    public void setCacheName(String cacheName) { this.cacheName = cacheName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public int getKeyCount() { return keyCount; }
    public void setKeyCount(int keyCount) { this.keyCount = keyCount; }
}
