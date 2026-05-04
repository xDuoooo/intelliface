package com.xduo.springbootinit.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.config.CosClientConfig;
import com.xduo.springbootinit.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Tencent COS object storage operations.
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    private volatile COSClient cosClient;

    @Value("${cos.client.presignedExpireMinutes:1}")
    private int presignedExpireMinutes;

    @Value("${cos.client.host:}")
    private String cosHost;

    @Value("${cos.client.customizedCosHost:}")
    private String customizedCosHost;

    public boolean isCosConfigured() {
        return isCosConfiguredWithoutClient();
    }

    public PutObjectResult putObject(String key, String localFilePath) {
        return putObject(key, new File(localFilePath));
    }

    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return getRequiredCosClient().putObject(putObjectRequest);
    }

    public String getPresignedDownloadUrl(String key, int expireMinutes) {
        if (StringUtils.isBlank(key)) {
            return key;
        }
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                cosClientConfig.getBucket(), normalizedKey, HttpMethodName.GET);
        Date expiration = new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L);
        req.setExpiration(expiration);
        URL url = getRequiredCosClient().generatePresignedUrl(req);
        return url.toString();
    }

    public String resolveSignedUrl(String rawUrl) {
        if (StringUtils.isBlank(rawUrl) || isLocalStaticAsset(rawUrl)) {
            return rawUrl;
        }

        if (rawUrl.startsWith("/") && !rawUrl.startsWith("http")) {
            return isCosConfigured() ? getPresignedDownloadUrl(rawUrl, presignedExpireMinutes) : rawUrl;
        }

        String key = extractCosKeyFromUrl(rawUrl);
        if (key != null) {
            return isCosConfigured() ? getPresignedDownloadUrl(key, presignedExpireMinutes) : rawUrl;
        }

        return rawUrl;
    }

    public String normalizeStoredPath(String rawUrl) {
        if (StringUtils.isBlank(rawUrl) || isLocalStaticAsset(rawUrl)) {
            return rawUrl;
        }
        if (rawUrl.startsWith("/") && !rawUrl.startsWith("http")) {
            return rawUrl;
        }
        String key = extractCosKeyFromUrl(rawUrl);
        return key != null ? key : rawUrl;
    }

    private COSClient getRequiredCosClient() {
        if (!isCosConfiguredWithoutClient()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "COS 未正确配置，无法执行文件操作");
        }
        COSClient current = cosClient;
        if (current == null) {
            synchronized (this) {
                current = cosClient;
                if (current == null) {
                    current = cosClientConfig.createCosClient();
                    cosClient = current;
                }
            }
        }
        return current;
    }

    @PreDestroy
    public void destroy() {
        COSClient current = cosClient;
        if (current != null) {
            current.shutdown();
        }
    }

    private boolean isCosConfiguredWithoutClient() {
        return isRealConfig(cosClientConfig.getAccessKey())
                && isRealConfig(cosClientConfig.getSecretKey())
                && isRealConfig(cosClientConfig.getRegion())
                && isRealConfig(cosClientConfig.getBucket());
    }

    private boolean isRealConfig(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        return StringUtils.isNotBlank(normalized)
                && !"xxx".equalsIgnoreCase(normalized)
                && !StringUtils.startsWithIgnoreCase(normalized, "your_");
    }

    private boolean isLocalStaticAsset(String rawUrl) {
        return rawUrl.startsWith("/api/files/")
                || rawUrl.startsWith("/files/")
                || rawUrl.startsWith("/assets/")
                || "/icon.png".equals(rawUrl)
                || "/favicon.ico".equals(rawUrl);
    }

    private String extractCosKeyFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }

        String key = extractKeyByHost(url, customizedCosHost);
        if (key == null) {
            key = extractKeyByHost(url, cosHost);
        }

        if (key == null && StringUtils.isNoneBlank(cosClientConfig.getBucket(), cosClientConfig.getRegion())) {
            String defaultHost = String.format("https://%s.cos.%s.myqcloud.com",
                    cosClientConfig.getBucket(), cosClientConfig.getRegion());
            if (url.startsWith(defaultHost)) {
                key = url.substring(defaultHost.length());
            }
        }

        if (key == null) {
            return null;
        }

        int qIndex = key.indexOf("?");
        if (qIndex != -1) {
            key = key.substring(0, qIndex);
        }
        try {
            return java.net.URLDecoder.decode(key, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.error("URL decode error for key: {}", key, e);
            return key;
        }
    }

    private String extractKeyByHost(String url, String host) {
        if (StringUtils.isBlank(host)) {
            return null;
        }
        String normalizedHost = StringUtils.removeEnd(host, "/");
        if (normalizedHost.startsWith("http")) {
            return url.startsWith(normalizedHost) ? url.substring(normalizedHost.length()) : null;
        }
        if (url.startsWith("https://" + normalizedHost)) {
            return url.substring(("https://" + normalizedHost).length());
        }
        if (url.startsWith("http://" + normalizedHost)) {
            return url.substring(("http://" + normalizedHost).length());
        }
        return url.startsWith(normalizedHost) ? url.substring(normalizedHost.length()) : null;
    }
}
