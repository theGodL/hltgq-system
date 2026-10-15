package com.qgyun.hltgq.hltgqsystem.loginlog.service;

import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * IP 归属地解析（ip2region，与平台 qx-loghub 的 Ip2regionFilter 一致）。
 * <p>数据文件 classpath:data/ip2region.xdb；search 返回 "国家|区域|省份|城市|ISP"，取「省份,城市」。
 * 内网 IP 或解析失败返回 null。
 */
@Service
public class IpRegionService {

    private static final Logger log = LoggerFactory.getLogger(IpRegionService.class);

    private static Searcher searcher;

    static {
        try (InputStream in = new ClassPathResource("data/ip2region.xdb").getInputStream()) {
            searcher = Searcher.newWithBuffer(StreamUtils.copyToByteArray(in));
            log.info("ip2region 数据加载完成");
        } catch (Exception e) {
            log.warn("加载 ip2region.xdb 失败，登录地点解析不可用：{}", e.getMessage());
            searcher = null;
        }
    }

    /**
     * 解析 IP 归属地，返回「省份,城市」；内网/私网/失败返回 null。
     */
    public String region(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        if (isPrivate(ip.trim())) {
            return "内网";
        }
        if (searcher == null) {
            return null;
        }
        String raw;
        try {
            raw = searcher.search(ip.trim());
        } catch (Exception e) {
            return null;
        }
        if (!StringUtils.hasText(raw) || raw.contains("内网IP")) {
            return null;
        }
        // 国家|区域|省份|城市|ISP
        String[] parts = raw.split("\\|");
        if (parts.length != 5) {
            return null;
        }
        String province = normalize(parts[2]);
        String city = normalize(parts[3]);
        if (province == null) {
            return city;
        }
        if (city == null || city.equals(province)) {
            return province;
        }
        return province + "," + city;
    }

    /** 内网/本机 IP 判断 */
    public static boolean isPrivate(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        if (!StringUtils.hasText(s) || "0".equals(s)) {
            return null;
        }
        return s;
    }
}
