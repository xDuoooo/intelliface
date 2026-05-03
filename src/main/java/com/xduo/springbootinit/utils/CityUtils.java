package com.xduo.springbootinit.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 城市统一处理工具
 */
public final class CityUtils {

    private static final Set<String> SUPPORTED_CITY_SET = new LinkedHashSet<>();
    private static final Pattern CITY_PATTERN = Pattern.compile("([\\p{IsHan}]{2,20}?)(?:市|地区|盟|自治州|州)");
    private static final Pattern PROVINCE_PATTERN = Pattern.compile("([\\p{IsHan}]{2,20}?)(?:省|壮族自治区|回族自治区|维吾尔自治区|自治区)");
    private static final Set<String> NON_LOCATION_TOKEN_SET = Set.of(
            "0", "中国", "局域网", "内网ip", "保留地址", "iana",
            "电信", "联通", "移动", "铁通", "广电", "鹏博士"
    );

    static {
        // 直辖市 / 特别行政区
        addSupportedCities("北京", "上海", "天津", "重庆", "香港", "澳门");
        // 华北 / 东北
        addSupportedCities("石家庄", "太原", "呼和浩特", "沈阳", "大连", "长春", "哈尔滨");
        // 华东
        addSupportedCities("南京", "苏州", "无锡", "杭州", "宁波", "合肥", "福州", "厦门", "南昌", "济南", "青岛");
        // 华中 / 华南
        addSupportedCities("郑州", "武汉", "长沙", "广州", "深圳", "佛山", "东莞", "南宁", "海口");
        // 西南 / 西北
        addSupportedCities("成都", "贵阳", "昆明", "拉萨", "西安", "兰州", "西宁", "银川", "乌鲁木齐");
    }

    private CityUtils() {
    }

    private static void addSupportedCities(String... cities) {
        for (String city : cities) {
            SUPPORTED_CITY_SET.add(city);
        }
    }

    public static String normalizeCity(String city) {
        if (city == null) {
            return null;
        }
        String normalizedCity = city.trim();
        if (StringUtils.isBlank(normalizedCity)) {
            return null;
        }
        if (normalizedCity.endsWith("市")) {
            normalizedCity = normalizedCity.substring(0, normalizedCity.length() - 1);
        }
        return normalizedCity;
    }

    public static boolean isSupportedCity(String city) {
        String normalizedCity = normalizeCity(city);
        return normalizedCity != null && SUPPORTED_CITY_SET.contains(normalizedCity);
    }

    public static String normalizeLocationLabel(String locationLabel) {
        String normalizedLocationLabel = normalizeCity(locationLabel);
        if (normalizedLocationLabel == null) {
            return null;
        }
        if (normalizedLocationLabel.contains("香港")) {
            return "中国香港";
        }
        if (normalizedLocationLabel.contains("澳门")) {
            return "中国澳门";
        }
        if (normalizedLocationLabel.contains("台湾")) {
            return "中国台湾";
        }
        return normalizedLocationLabel;
    }

    public static String normalizeSupportedCity(String city) {
        String normalizedCity = normalizeCity(city);
        if (normalizedCity == null) {
            return null;
        }
        return isSupportedCity(normalizedCity) ? normalizedCity : null;
    }

    public static String extractSupportedCity(String locationText) {
        String normalizedCity = normalizeSupportedCity(locationText);
        if (normalizedCity != null) {
            return normalizedCity;
        }
        String normalizedLocationText = normalizeCity(locationText);
        if (normalizedLocationText == null) {
            return null;
        }
        String compactLocationText = normalizedLocationText.replace(" ", "");
        for (String supportedCity : SUPPORTED_CITY_SET) {
            if (compactLocationText.contains(supportedCity)) {
                return supportedCity;
            }
        }
        return null;
    }

    public static String extractLocationLabel(String locationText) {
        String normalizedLocationText = normalizeCity(locationText);
        if (normalizedLocationText == null) {
            return null;
        }
        String specialRegionLabel = extractSpecialRegionLabel(normalizedLocationText);
        if (specialRegionLabel != null) {
            return specialRegionLabel;
        }
        String supportedCity = extractSupportedCity(normalizedLocationText);
        if (supportedCity != null) {
            return supportedCity;
        }
        String chineseCity = extractChineseCity(normalizedLocationText);
        if (chineseCity != null) {
            return chineseCity;
        }
        List<String> tokenList = splitLocationTokens(normalizedLocationText);
        for (String token : tokenList) {
            String normalizedToken = normalizeLocationLabel(token);
            if (normalizedToken == null || isNonLocationToken(normalizedToken)) {
                continue;
            }
            if ("中国".equals(normalizedToken)) {
                continue;
            }
            String normalizedProvince = normalizeProvinceToken(normalizedToken);
            if (normalizedProvince != null) {
                return normalizedProvince;
            }
            return normalizedToken;
        }
        return null;
    }

    private static String extractSpecialRegionLabel(String locationText) {
        if (StringUtils.isBlank(locationText)) {
            return null;
        }
        String compactLocationText = locationText.replace(" ", "");
        if (compactLocationText.contains("香港")) {
            return "中国香港";
        }
        if (compactLocationText.contains("澳门")) {
            return "中国澳门";
        }
        if (compactLocationText.contains("台湾")) {
            return "中国台湾";
        }
        return null;
    }

    private static String extractChineseCity(String locationText) {
        Matcher matcher = CITY_PATTERN.matcher(locationText);
        String matchedCity = null;
        while (matcher.find()) {
            String candidate = normalizeCity(matcher.group(1));
            if (candidate != null && !isNonLocationToken(candidate)) {
                matchedCity = candidate;
            }
        }
        return matchedCity;
    }

    private static String normalizeProvinceToken(String locationToken) {
        if (StringUtils.isBlank(locationToken)) {
            return null;
        }
        Matcher matcher = PROVINCE_PATTERN.matcher(locationToken);
        if (!matcher.find()) {
            return null;
        }
        String province = matcher.group(1);
        return StringUtils.isBlank(province) ? null : province.trim();
    }

    private static List<String> splitLocationTokens(String locationText) {
        String compactLocationText = locationText
                .replace('|', ',')
                .replace('/', ',')
                .replace('\\', ',')
                .replace('，', ',')
                .replace(';', ',')
                .replace('；', ',');
        String[] rawTokenArray = compactLocationText.split("[,\\s]+");
        List<String> tokenList = new ArrayList<>();
        for (String token : rawTokenArray) {
            if (StringUtils.isNotBlank(token)) {
                tokenList.add(token.trim());
            }
        }
        return tokenList;
    }

    private static boolean isNonLocationToken(String locationToken) {
        return NON_LOCATION_TOKEN_SET.contains(StringUtils.lowerCase(locationToken));
    }
}
