package com.heihu.workorder.dto;

public class MobileBindRequest {
    private String openidTicket;
    private String factoryCode;
    private String username;
    private String password;

    public String getOpenidTicket() { return openidTicket; }
    public void setOpenidTicket(String openidTicket) { this.openidTicket = openidTicket; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
