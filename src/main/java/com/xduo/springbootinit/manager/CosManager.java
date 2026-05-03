package com.xduo.springbootinit.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.xduo.springbootinit.config.CosClientConfig;
import java.io.File;
import java.net.URL;
import java.util.Date;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cos 对象存储操作

 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 签名 URL 有效期（分钟），默认 1
     */
    @Value("${cos.client.presignedExpireMinutes:1}")
    private int presignedExpireMinutes;

    /**
     * COS host（用于从旧的完整 URL 中提取 key）
     */
    @Value("${cos.client.host:}")
    private String cosHost;

    /**
     * 自定义 COS 域名（用于从旧的完整 URL 中提取 key）
     */
    @Value("${cos.client.customizedCosHost:}")
    private String customizedCosHost;

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param localFilePath 本地文件路径
     * @return
     */
    public PutObjectResult putObject(String key, String localFilePath) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                new File(localFilePath));
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传对象
     *
     * @param key 唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 生成带签名的临时下载 URL
     *
     * @param key           COS 对象 key（如 /user_avatar/123/xxx.jpg）
     * @param expireMinutes 过期时间（分钟）
     * @return 签名后的临时 URL
     */
    public String getPresignedDownloadUrl(String key, int expireMinutes) {
        // 去除 key 开头多余的 /，COS key 通常不以 / 开头
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(
                cosClientConfig.getBucket(), normalizedKey, HttpMethodName.GET);
        Date expiration = new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L);
        req.setExpiration(expiration);
        URL url = cosClient.generatePresignedUrl(req);
        return url.toString();
    }

    /**
     * 智能解析并签名文件 URL。
     * <p>
     * 支持三种输入格式：
     * 1. COS key（以 / 开头，非 http）→ 生成签名 URL
     * 2. 旧的完整 COS URL（包含 cosHost 或 customizedCosHost）→ 提取 key 后生成签名 URL
     * 3. 外部 URL（如 GitHub 头像）→ 原样返回
     *
     * @param rawUrl 数据库中存储的原始 URL 或 key
     * @return 签名后的 URL，或原样返回的外部 URL
     */
    public String resolveSignedUrl(String rawUrl) {
        if (StringUtils.isBlank(rawUrl)) {
            return rawUrl;
        }

        // 本地静态资源（如前端 public 下的默认 logo）应直接返回，不能误当成 COS key 去签名。
        if (isLocalStaticAsset(rawUrl)) {
            return rawUrl;
        }

        // 情况1：纯 COS key（以 / 开头但不以 http 开头）
        if (rawUrl.startsWith("/") && !rawUrl.startsWith("http")) {
            return getPresignedDownloadUrl(rawUrl, presignedExpireMinutes);
        }

        // 情况2：旧的完整 COS URL，尝试提取 key
        String key = extractCosKeyFromUrl(rawUrl);
        if (key != null) {
            return getPresignedDownloadUrl(key, presignedExpireMinutes);
        }

        // 情况3：外部 URL（GitHub、Gitee 头像等），原样返回
        return rawUrl;
    }

    private boolean isLocalStaticAsset(String rawUrl) {
        return rawUrl.startsWith("/assets/")
                || "/icon.png".equals(rawUrl)
                || "/favicon.ico".equals(rawUrl);
    }

    /**
     * 从完整的 COS URL 中提取 object key。
     * 支持 cosHost 和 customizedCosHost 两种域名格式。
     *
     * @param url 完整 URL
     * @return 提取出的 key，如果不是 COS URL 则返回 null
     */
    private String extractCosKeyFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }

        String key = null;

        // 尝试从 customizedCosHost 提取
        if (StringUtils.isNotBlank(customizedCosHost)) {
            String normalizedHost = StringUtils.removeEnd(customizedCosHost, "/");
            if (!normalizedHost.startsWith("http")) {
                if (url.startsWith("https://" + normalizedHost)) {
                    key = url.substring(("https://" + normalizedHost).length());
                } else if (url.startsWith("http://" + normalizedHost)) {
                    key = url.substring(("http://" + normalizedHost).length());
                } else if (url.startsWith(normalizedHost)) {
                    key = url.substring(normalizedHost.length());
                }
            } else if (url.startsWith(normalizedHost)) {
                key = url.substring(normalizedHost.length());
            }
        }

        // 尝试从 cosHost 提取
        if (key == null && StringUtils.isNotBlank(cosHost)) {
            String normalizedHost = StringUtils.removeEnd(cosHost, "/");
            if (!normalizedHost.startsWith("http")) {
                if (url.startsWith("https://" + normalizedHost)) {
                    key = url.substring(("https://" + normalizedHost).length());
                } else if (url.startsWith("http://" + normalizedHost)) {
                    key = url.substring(("http://" + normalizedHost).length());
                } else if (url.startsWith(normalizedHost)) {
                    key = url.substring(normalizedHost.length());
                }
            } else if (url.startsWith(normalizedHost)) {
                key = url.substring(normalizedHost.length());
            }
        }

        // 尝试从默认格式提取：https://{bucket}.cos.{region}.myqcloud.com
        if (key == null) {
            String defaultHost = String.format("https://%s.cos.%s.myqcloud.com",
                    cosClientConfig.getBucket(), cosClientConfig.getRegion());
            if (url.startsWith(defaultHost)) {
                key = url.substring(defaultHost.length());
            }
        }

        if (key != null) {
            int qIndex = key.indexOf("?");
            if (qIndex != -1) {
                key = key.substring(0, qIndex);
            }
            try {
                // 解决经过签名的中文 URL 被提取后再次去签名导致 "%E8" 变成 "%25E8" 的双重编码 BUG
                key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                log.error("URL decode error for key: " + key, e);
            }
        }

        return key;
    }
}
