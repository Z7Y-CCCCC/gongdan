package com.heihu.workorder.dto;

public class MobileAuthResponse {
    private boolean bindRequired;
    private String openidTicket;
    private String token;
    private UserResponse user;

    public static MobileAuthResponse needBind(String ticket) {
        MobileAuthResponse response = new MobileAuthResponse();
        response.setBindRequired(true);
        response.setOpenidTicket(ticket);
        return response;
    }

    public static MobileAuthResponse loggedIn(String token, UserResponse user) {
        MobileAuthResponse response = new MobileAuthResponse();
        response.setBindRequired(false);
        response.setToken(token);
        response.setUser(user);
        return response;
    }

    public boolean isBindRequired() { return bindRequired; }
    public void setBindRequired(boolean bindRequired) { this.bindRequired = bindRequired; }
    public String getOpenidTicket() { return openidTicket; }
    public void setOpenidTicket(String openidTicket) { this.openidTicket = openidTicket; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public UserResponse getUser() { return user; }
    public void setUser(UserResponse user) { this.user = user; }
}
