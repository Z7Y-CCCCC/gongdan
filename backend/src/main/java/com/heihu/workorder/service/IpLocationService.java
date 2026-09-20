package com.heihu.workorder.service;

import com.heihu.workorder.util.IpAddressUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Service
public class IpLocationService {

    private static final Logger log = LoggerFactory.getLogger(IpLocationService.class);
    private static final String DEFAULT_XDB_RESOURCE = "ip2region.xdb";

    private byte[] cBuff;

    public IpLocationService() {
    }

    @PostConstruct
    public void init() {
        loadDatabase(DEFAULT_XDB_RESOURCE);
    }

    public synchronized void loadDatabase(String resourcePath) {
        try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
            this.cBuff = StreamUtils.copyToByteArray(is);
            log.info("ip2region.xdb 离线 IP 库加载成功，内存占用: {} 字节", cBuff.length);
        } catch (Exception e) {
            log.warn("未能从 classpath 加载 ip2region.xdb 资源文件: {}", e.getMessage());
        }
    }

    /**
     * 根据 IP 查询所在地理省市与运营商（例如：广东省广州市 电信）
     */
    public String searchLocation(String ip) {
        if (ip == null || ip.trim().isEmpty() || IpAddressUtils.isInternalIp(ip)) {
            return "内网IP";
        }

        if (this.cBuff == null) {
            return "内网IP";
        }

        Searcher searcher = null;
        try {
            searcher = Searcher.newWithBuffer(this.cBuff);
            String rawRegion = searcher.search(ip.trim());
            return formatRegion(rawRegion);
        } catch (Exception e) {
            log.debug("ip2region 解析 IP [{}] 异常: {}", ip, e.getMessage());
            return "未知地区";
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 格式化 ip2region 返回的五段格式 (国家|区域|省份|城市|ISP)
     */
    public String formatRegion(String region) {
        if (region == null || region.trim().isEmpty()) {
            return "未知地区";
        }
        String[] parts = region.split("\\|", -1);
        if (parts.length < 5) {
            return region;
        }

        String country = "0".equals(parts[0]) ? "" : parts[0].trim();
        String province = "0".equals(parts[2]) ? "" : parts[2].trim();
        String city = "0".equals(parts[3]) ? "" : parts[3].trim();
        String isp = "0".equals(parts[4]) ? "" : parts[4].trim();

        if ("内网IP".equals(isp) || "内网IP".equals(city) || "内网IP".equals(province)) {
            return "内网IP";
        }

        StringBuilder sb = new StringBuilder();
        if ("中国".equals(country)) {
            if (!province.isEmpty()) {
                sb.append(province);
            }
            if (!city.isEmpty()) {
                if (province.isEmpty()) {
                    sb.append(city);
                } else if (!city.equals(province) && !city.contains(province) && !province.contains(city)) {
                    sb.append(city);
                }
            }
            if (!isp.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(isp);
            }
        } else {
            if (!country.isEmpty()) {
                sb.append(country);
            }
            if (!province.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(province);
            }
            if (!city.isEmpty() && !city.equals(province)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(city);
            }
            if (!isp.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(isp);
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "未知地区" : result;
    }
}
