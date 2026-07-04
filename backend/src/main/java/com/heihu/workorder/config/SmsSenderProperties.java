package com.heihu.workorder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public class SmsSenderProperties {

    private String sender = "log";
    private boolean logCode = true;
    private Http http = new Http();

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public boolean isLogCode() { return logCode; }
    public void setLogCode(boolean logCode) { this.logCode = logCode; }
    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }

    public static class Http {
        private String url = "";
        private String authHeaderName = "";
        private String authHeaderValue = "";
        private String template = "login_code";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAuthHeaderName() { return authHeaderName; }
        public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
        public String getAuthHeaderValue() { return authHeaderValue; }
        public void setAuthHeaderValue(String authHeaderValue) { this.authHeaderValue = authHeaderValue; }
        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}
