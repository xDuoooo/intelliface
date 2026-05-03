package com.xduo.springbootinit.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于请求头解析用户城市
 */
@Component
@Slf4j
public class IpCityResolver {

    private static final List<String> CITY_HEADER_CANDIDATES = List.of(
            "X-Appengine-City",
            "CF-IPCity",
            "X-City",
            "X-Real-City",
            "X-Geo-City",
            "X-Tencent-Geoip-City",
            "Ali-Cdn-Real-Ip-City",
            "X-CDN-IP-City",
            "X-Location-City",
            "X-Client-City"
    );

    private final ResourceLoader resourceLoader;

    public IpCityResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Value("${app.ip-location.dev-city:}")
    private String devCity;

    @Value("${app.ip-location.ip2region.enabled:true}")
    private boolean ip2RegionEnabled;

    @Value("${app.ip-location.ip2region.v4-xdb-location:classpath:ip2region/ip2region_v4.xdb}")
    private String ip2RegionV4XdbLocation;

    @Value("${app.ip-location.ip2region.v6-xdb-location:}")
    private String ip2RegionV6XdbLocation;

    @Value("${app.ip-location.ip2region.searchers:20}")
    private int ip2RegionSearchers;

    @Value("${app.ip-location.lookup-enabled:false}")
    private boolean lookupEnabled;

    @Value("${app.ip-location.lookup-url-template:https://whois.pconline.com.cn/ipJson.jsp?json=true&ip={ip}}")
    private String lookupUrlTemplate;

    @Value("${app.ip-location.lookup-timeout-ms:1500}")
    private int lookupTimeoutMs;

    private volatile Ip2Region ip2Region;

    @PostConstruct
    public void initIp2Region() {
        if (!ip2RegionEnabled) {
            return;
        }
        InputStream v4InputStream = null;
        InputStream v6InputStream = null;
        try {
            v4InputStream = openXdbInputStream(ip2RegionV4XdbLocation);
            v6InputStream = openXdbInputStream(ip2RegionV6XdbLocation);
            Config v4Config = v4InputStream == null ? null : Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setSearchers(Math.max(1, ip2RegionSearchers))
                    .setXdbInputStream(v4InputStream)
                    .asV4();
            Config v6Config = v6InputStream == null ? null : Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setSearchers(Math.max(1, ip2RegionSearchers))
                    .setXdbInputStream(v6InputStream)
                    .asV6();
            if (v4Config == null && v6Config == null) {
                log.warn("ip2region 离线库未加载：未配置可用 xdb 文件，将回退到在线 IP 查询");
                return;
            }
            ip2Region = Ip2Region.create(v4Config, v6Config);
            log.info("ip2region 离线库加载完成: v4={}, v6={}",
                    v4Config != null,
                    v6Config != null);
        } catch (Exception e) {
            log.warn("ip2region 离线库初始化失败，将回退到在线 IP 查询: {}", e.getMessage());
            closeIp2RegionQuietly();
        } finally {
            closeQuietly(v4InputStream);
            closeQuietly(v6InputStream);
        }
    }

    @PreDestroy
    public void destroy() {
        closeIp2RegionQuietly();
    }

    /**
     * 解析系统支持的城市名称
     */
    public String resolveLocationLabel(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String headerName : CITY_HEADER_CANDIDATES) {
            String headerValue = request.getHeader(headerName);
            String locationLabel = CityUtils.extractLocationLabel(headerValue);
            if (StringUtils.isNotBlank(locationLabel)) {
                return locationLabel;
            }
        }
        String regionHint = String.join(" ",
                StringUtils.defaultString(request.getHeader("X-Province")),
                StringUtils.defaultString(request.getHeader("X-Region")),
                StringUtils.defaultString(request.getHeader("X-Area")),
                StringUtils.defaultString(request.getHeader("X-City-Name")));
        String locationLabel = CityUtils.extractLocationLabel(regionHint);
        if (StringUtils.isNotBlank(locationLabel)) {
            return locationLabel;
        }
        String ip = NetUtils.getIpAddress(request);
        boolean localOrPrivateIp = isLocalOrPrivateIp(ip);
        if (localOrPrivateIp) {
            String devLocationLabel = CityUtils.normalizeLocationLabel(devCity);
            if (StringUtils.isBlank(devLocationLabel)) {
                logUnresolvedCityDiagnostics(request, ip, true, regionHint, null, "private-ip-without-dev-city");
            }
            return devLocationLabel;
        }
        String ip2RegionRegion = searchIp2Region(ip);
        String offlineResolvedLocation = CityUtils.extractLocationLabel(ip2RegionRegion);
        if (StringUtils.isNotBlank(offlineResolvedLocation)) {
            return offlineResolvedLocation;
        }
        String onlineResolvedLocation = resolveLocationLabelByPublicIp(ip);
        if (StringUtils.isBlank(onlineResolvedLocation)) {
            logUnresolvedCityDiagnostics(request, ip, false, regionHint, ip2RegionRegion, "no-supported-city");
        }
        return onlineResolvedLocation;
    }

    public String resolveSupportedCity(HttpServletRequest request) {
        return resolveLocationLabel(request);
    }

    private InputStream openXdbInputStream(String location) {
        if (StringUtils.isBlank(location)) {
            return null;
        }
        try {
            Resource resource = resourceLoader.getResource(location.trim());
            if (!resource.exists()) {
                log.warn("ip2region xdb 文件不存在: {}", location);
                return null;
            }
            return resource.getInputStream();
        } catch (Exception e) {
            log.warn("ip2region xdb 文件读取失败: {}, message={}", location, e.getMessage());
            return null;
        }
    }

    private boolean isLocalOrPrivateIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return true;
        }
        String normalizedIp = ip.trim();
        if ("127.0.0.1".equals(normalizedIp)
                || "::1".equals(normalizedIp)
                || normalizedIp.startsWith("10.")
                || normalizedIp.startsWith("192.168.")
                || normalizedIp.startsWith("169.254.")
                || normalizedIp.startsWith("fd")
                || normalizedIp.startsWith("fe80")) {
            return true;
        }
        if (normalizedIp.startsWith("172.")) {
            String[] parts = normalizedIp.split("\\.");
            if (parts.length > 1) {
                try {
                    int secondSegment = Integer.parseInt(parts[1]);
                    return secondSegment >= 16 && secondSegment <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private String resolveSupportedCityByIp2Region(String ip) {
        String region = searchIp2Region(ip);
        String locationLabel = CityUtils.extractLocationLabel(region);
        if (StringUtils.isBlank(locationLabel) && StringUtils.isNotBlank(region)) {
            log.debug("ip2region 未命中支持城市: ip={}, region={}", ip, region);
        }
        return locationLabel;
    }

    private String searchIp2Region(String ip) {
        Ip2Region currentIp2Region = ip2Region;
        if (!ip2RegionEnabled || currentIp2Region == null || StringUtils.isBlank(ip)) {
            return null;
        }
        try {
            return currentIp2Region.search(ip.trim());
        } catch (Exception e) {
            log.warn("ip2region 城市解析异常: ip={}, message={}", ip, e.getMessage());
            return null;
        }
    }

    private String resolveLocationLabelByPublicIp(String ip) {
        if (!lookupEnabled || StringUtils.isBlank(ip) || StringUtils.isBlank(lookupUrlTemplate)) {
            return null;
        }
        try {
            String lookupUrl = buildLookupUrl(ip);
            HttpResponse response = HttpRequest.get(lookupUrl)
                    .timeout(Math.max(500, lookupTimeoutMs))
                    .execute();
            if (!response.isOk()) {
                log.warn("IP 城市解析失败: ip={}, status={}", ip, response.getStatus());
                return null;
            }
            JSONObject payload = parseLookupPayload(response.body());
            if (payload == null) {
                return null;
            }
            String resolvedLocation = extractLocationLabelFromPayload(payload);
            if (StringUtils.isBlank(resolvedLocation)) {
                log.debug("IP 城市解析未命中支持城市: ip={}, payload={}", ip, payload);
                return null;
            }
            return resolvedLocation;
        } catch (Exception e) {
            log.warn("IP 城市解析异常: ip={}, message={}", ip, e.getMessage());
            return null;
        }
    }

    private String buildLookupUrl(String ip) {
        String encodedIp = java.net.URLEncoder.encode(ip, StandardCharsets.UTF_8);
        if (lookupUrlTemplate.contains("{ip}")) {
            return lookupUrlTemplate.replace("{ip}", encodedIp);
        }
        return lookupUrlTemplate + encodedIp;
    }

    private JSONObject parseLookupPayload(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        String trimmedBody = responseBody.trim();
        int jsonStart = trimmedBody.indexOf('{');
        int jsonEnd = trimmedBody.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return null;
        }
        String jsonText = trimmedBody.substring(jsonStart, jsonEnd + 1);
        return JSONUtil.parseObj(jsonText);
    }

    private String extractLocationLabelFromPayload(JSONObject payload) {
        List<String> candidates = new ArrayList<>();
        candidates.add(payload.getStr("country"));
        candidates.add(payload.getStr("city"));
        candidates.add(payload.getStr("region"));
        candidates.add(payload.getStr("regionNames"));
        candidates.add(payload.getStr("pro"));
        candidates.add(payload.getStr("province"));
        candidates.add(payload.getStr("addr"));
        JSONObject data = payload.getJSONObject("data");
        if (data != null) {
            candidates.add(data.getStr("country"));
            candidates.add(data.getStr("city"));
            candidates.add(data.getStr("district"));
            candidates.add(data.getStr("prov"));
            candidates.add(data.getStr("province"));
            candidates.add(data.getStr("addr"));
        }
        for (String candidate : candidates) {
            String locationLabel = CityUtils.extractLocationLabel(candidate);
            if (StringUtils.isNotBlank(locationLabel)) {
                return locationLabel;
            }
        }
        return null;
    }

    private void logUnresolvedCityDiagnostics(HttpServletRequest request, String ip, boolean localOrPrivateIp,
                                              String regionHint, String ip2RegionRegion, String reason) {
        log.info("IP 城市解析未命中: reason={}, clientIp={}, privateIp={}, remoteAddr={}, xForwardedFor={}, "
                        + "xRealIp={}, cfConnectingIp={}, trueClientIp={}, xClientIp={}, cityHeaders={}, "
                        + "regionHint={}, ip2regionEnabled={}, ip2regionLoaded={}, ip2regionRegion={}, "
                        + "lookupEnabled={}, devCity={}",
                reason,
                maskIpForLog(ip),
                localOrPrivateIp,
                maskIpForLog(request.getRemoteAddr()),
                maskIpHeaderForLog(request.getHeader("X-Forwarded-For")),
                maskIpHeaderForLog(request.getHeader("X-Real-IP")),
                maskIpHeaderForLog(request.getHeader("CF-Connecting-IP")),
                maskIpHeaderForLog(request.getHeader("True-Client-IP")),
                maskIpHeaderForLog(request.getHeader("X-Client-IP")),
                collectCityHeadersForLog(request),
                StringUtils.trimToEmpty(regionHint),
                ip2RegionEnabled,
                ip2Region != null,
                StringUtils.defaultIfBlank(ip2RegionRegion, "<empty>"),
                lookupEnabled,
                StringUtils.defaultIfBlank(devCity, "<empty>"));
    }

    private String collectCityHeadersForLog(HttpServletRequest request) {
        List<String> headerList = new ArrayList<>();
        for (String headerName : CITY_HEADER_CANDIDATES) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.isNotBlank(headerValue)) {
                headerList.add(headerName + "=" + headerValue);
            }
        }
        return headerList.isEmpty() ? "<empty>" : String.join(",", headerList);
    }

    private String maskIpHeaderForLog(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return "<empty>";
        }
        String[] ipList = headerValue.split(",");
        List<String> maskedIpList = new ArrayList<>();
        for (String item : ipList) {
            maskedIpList.add(maskIpForLog(item));
        }
        return String.join(",", maskedIpList);
    }

    private String maskIpForLog(String ip) {
        if (StringUtils.isBlank(ip)) {
            return "<empty>";
        }
        String normalizedIp = ip.trim();
        String[] ipv4Parts = normalizedIp.split("\\.");
        if (ipv4Parts.length == 4) {
            return ipv4Parts[0] + "." + ipv4Parts[1] + ".x.x";
        }
        int colonIndex = normalizedIp.indexOf(':');
        if (colonIndex > 0) {
            return normalizedIp.substring(0, colonIndex) + ":****";
        }
        return normalizedIp;
    }

    private void closeIp2RegionQuietly() {
        Ip2Region currentIp2Region = ip2Region;
        ip2Region = null;
        if (currentIp2Region == null) {
            return;
        }
        try {
            currentIp2Region.close();
        } catch (Exception e) {
            log.debug("关闭 ip2region 查询服务失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (Exception ignored) {
        }
    }
}
