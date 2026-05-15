package com.heihu.workorder.dto;

public class MobileWechatLoginRequest {
    private String code;
    private String mockOpenid;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMockOpenid() { return mockOpenid; }
    public void setMockOpenid(String mockOpenid) { this.mockOpenid = mockOpenid; }
}
