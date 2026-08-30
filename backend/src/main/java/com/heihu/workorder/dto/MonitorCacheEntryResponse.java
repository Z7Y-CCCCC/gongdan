package com.heihu.workorder.dto;

public class MonitorCacheEntryResponse {
    private String cacheName;
    private String cacheKey;
    private String cacheValue;

    public MonitorCacheEntryResponse() {}

    public MonitorCacheEntryResponse(String cacheName, String cacheKey, String cacheValue) {
        this.cacheName = cacheName;
        this.cacheKey = cacheKey;
        this.cacheValue = cacheValue;
    }

    public String getCacheName() { return cacheName; }
    public void setCacheName(String cacheName) { this.cacheName = cacheName; }
    public String getCacheKey() { return cacheKey; }
    public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
    public String getCacheValue() { return cacheValue; }
    public void setCacheValue(String cacheValue) { this.cacheValue = cacheValue; }
}
