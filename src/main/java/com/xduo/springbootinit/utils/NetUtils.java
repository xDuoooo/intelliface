package com.xduo.springbootinit.utils;

import java.net.InetAddress;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 网络工具类

 */
public class NetUtils {

    private static final String[] CLIENT_IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP",
            "True-Client-IP",
            "X-Client-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    /**
     * 获取客户端 IP 地址
     *
     * @param request
     * @return
     */
    public static String getIpAddress(HttpServletRequest request) {
        for (String headerName : CLIENT_IP_HEADER_CANDIDATES) {
            String ip = extractFirstValidIp(request.getHeader(headerName));
            if (ip != null) {
                return ip;
            }
        }
        String ip = request.getRemoteAddr();
        if ("127.0.0.1".equals(ip)) {
            // 根据网卡取本机配置的 IP
            InetAddress inet = null;
            try {
                inet = InetAddress.getLocalHost();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (inet != null) {
                ip = inet.getHostAddress();
            }
        }
        if (ip == null) {
            return "127.0.0.1";
        }
        return ip;
    }

    private static String extractFirstValidIp(String headerValue) {
        if (headerValue == null || headerValue.length() == 0) {
            return null;
        }
        String[] ipList = headerValue.split(",");
        for (String ip : ipList) {
            String normalizedIp = ip == null ? "" : ip.trim();
            if (normalizedIp.length() > 0 && !"unknown".equalsIgnoreCase(normalizedIp)) {
                return normalizedIp;
            }
        }
        return null;
    }
}
