package com.heihu.workorder.service;

import com.heihu.workorder.util.IpAddressUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class IpLocationServiceTest {

    private IpLocationService ipLocationService;

    @BeforeEach
    void setUp() {
        ipLocationService = new IpLocationService();
        ipLocationService.init();
    }

    @Test
    void testInternalIpIdentification() {
        assertTrue(IpAddressUtils.isInternalIp("127.0.0.1"));
        assertTrue(IpAddressUtils.isInternalIp("::1"));
        assertTrue(IpAddressUtils.isInternalIp("0:0:0:0:0:0:0:1"));
        assertTrue(IpAddressUtils.isInternalIp("192.168.1.100"));
        assertTrue(IpAddressUtils.isInternalIp("10.10.10.10"));
        assertTrue(IpAddressUtils.isInternalIp("172.18.0.8"));
        assertTrue(IpAddressUtils.isInternalIp("169.254.1.1"));

        assertFalse(IpAddressUtils.isInternalIp("114.114.114.114"));
        assertFalse(IpAddressUtils.isInternalIp("103.151.173.206"));
    }

    @Test
    void testClientIpExtractionFromHeaders() {
        // 1. CF-Connecting-IP 优先
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.addHeader("CF-Connecting-IP", "103.151.173.206");
        request1.addHeader("X-Forwarded-For", "172.18.0.1, 10.0.0.2");
        request1.setRemoteAddr("172.18.0.8");
        assertEquals("103.151.173.206", IpAddressUtils.getClientIp(request1));

        // 2. X-Forwarded-For 挑选公网 IP
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addHeader("X-Forwarded-For", "103.151.173.206, 172.18.0.8");
        request2.setRemoteAddr("172.18.0.8");
        assertEquals("103.151.173.206", IpAddressUtils.getClientIp(request2));

        // 3. 回环地址自动格式化为 127.0.0.1
        MockHttpServletRequest request3 = new MockHttpServletRequest();
        request3.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", IpAddressUtils.getClientIp(request3));
    }

    @Test
    void testSearchLocationInternal() {
        assertEquals("内网IP", ipLocationService.searchLocation("127.0.0.1"));
        assertEquals("内网IP", ipLocationService.searchLocation("192.168.0.1"));
        assertEquals("内网IP", ipLocationService.searchLocation("172.18.0.8"));
        assertEquals("内网IP", ipLocationService.searchLocation(null));
        assertEquals("内网IP", ipLocationService.searchLocation(""));
    }

    @Test
    void testSearchLocationPublic() {
        // 114.114.114.114 为江苏省南京市公共DNS
        String location114 = ipLocationService.searchLocation("114.114.114.114");
        assertNotNull(location114);
        assertNotEquals("未知地区", location114);
        assertNotEquals("内网IP", location114);
        assertTrue(location114.contains("江苏") || location114.contains("南京"), "114.114.114.114 应包含江苏或南京，实际为: " + location114);

        // 223.5.5.5 为阿里DNS
        String locationAli = ipLocationService.searchLocation("223.5.5.5");
        assertNotNull(locationAli);
        assertFalse(locationAli.contains("|"), "结果不应含有原始竖线分隔符");
        assertFalse(locationAli.contains("0"), "结果不应含有未解析的数字0");
    }

    @Test
    void testFormatRegion() {
        assertEquals("广东省广州市 电信", ipLocationService.formatRegion("中国|0|广东省|广州市|电信"));
        assertEquals("北京市 联通", ipLocationService.formatRegion("中国|0|北京市|北京市|联通"));
        assertEquals("内网IP", ipLocationService.formatRegion("中国|0|0|0|内网IP"));
        assertEquals("美国 加利福尼亚 旧金山", ipLocationService.formatRegion("美国|0|加利福尼亚|旧金山|0"));
        assertEquals("未知地区", ipLocationService.formatRegion(null));
    }
}
