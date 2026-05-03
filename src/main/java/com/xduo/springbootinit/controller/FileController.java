package com.xduo.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.config.CosClientConfig;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.CosManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import com.xduo.springbootinit.model.dto.file.UploadFileRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.enums.FileUploadBizEnum;
import com.xduo.springbootinit.service.UserService;

import java.io.File;
import java.util.Arrays;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${cos.client.accessKey:xxx}")
    private String cosAccessKey;

    @Value("${app.upload.local-fallback-enabled:true}")
    private boolean localUploadFallbackEnabled;

    @Value("${app.upload.local-dir:${user.dir}/uploads}")
    private String localUploadDir;

    @Value("${cos.client.host:}")
    private String cosHost;

    @Value("${cos.client.customizedCosHost:}")
    private String customizedCosHost;
    /**
     * 文件上传
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        // 频率限制：单用户每小时限 10 次上传
        String redisKey = "user:upload:limit:" + loginUser.getId();
        String uploadCountStr = stringRedisTemplate.opsForValue().get(redisKey);
        int uploadCount = uploadCountStr == null ? 0 : Integer.parseInt(uploadCountStr);
        if (uploadCount >= 10) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传过于频繁，请一小时后再试");
        }

        validFile(multipartFile, fileUploadBizEnum);

        // 计数增加
        if (uploadCount == 0) {
            stringRedisTemplate.opsForValue().set(redisKey, "1", 1, TimeUnit.HOURS);
        } else {
            stringRedisTemplate.opsForValue().increment(redisKey);
        }

        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String originalFilename = StringUtils.defaultIfBlank(FileUtil.getName(multipartFile.getOriginalFilename()), "file");
        String filename = uuid + "-" + originalFilename.replaceAll("[\\\\/\\r\\n\\t]+", "_");
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);

        // 优先使用 COS，若未配置则使用本地存储
        if (cosAccessKey != null && !cosAccessKey.equals("xxx")) {
            File file = null;
            try {
                String fileSuffix = FileUtil.getSuffix(filename);
                file = File.createTempFile("upload-" + uuid + "-", StringUtils.isBlank(fileSuffix) ? null : "." + fileSuffix);
                multipartFile.transferTo(file);
                cosManager.putObject(filepath, file);
                // 返回签名过的临时 URL 供前端预览。虽然前端会将它提回更新资料接口，但后续解析逻辑可安全剥离签名参数并生成新签名。
                return ResultUtils.success(cosManager.resolveSignedUrl(filepath));
            } catch (Exception e) {
                log.error("COS upload error, filepath = " + filepath, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传至云存储失败");
            } finally {
                if (file != null) file.delete();
            }
        } else {
            if (!localUploadFallbackEnabled) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前环境未开启本地文件存储，请配置 COS 后重试");
            }
            // 本地存储兜底
            try {
                Path bizPath = Paths.get(localUploadDir, fileUploadBizEnum.getValue(), String.valueOf(loginUser.getId()));
                if (!Files.exists(bizPath)) {
                    Files.createDirectories(bizPath);
                }
                Path targetPath = bizPath.resolve(filename);
                Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                // 返回本地访问 URL (带 context-path /api)
                return ResultUtils.success("/api/files" + filepath);
            } catch (Exception e) {
                log.error("Local upload error, filepath = " + filepath, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "本地文件上传失败");
            }
        }
    }

    private String buildCosFileUrl(String filepath) {
        if(StringUtils.isNotBlank(customizedCosHost)){
            return StringUtils.removeEnd(customizedCosHost,"/") + filepath;
        }
        String baseUrl = StringUtils.trimToEmpty(cosHost);
        if (StringUtils.isBlank(baseUrl)) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(cosClientConfig.getBucket(), cosClientConfig.getRegion()),
                    ErrorCode.SYSTEM_ERROR, "COS 访问地址未配置");
            baseUrl = String.format("https://%s.cos.%s.myqcloud.com",
                    cosClientConfig.getBucket(), cosClientConfig.getRegion());
        }
        return StringUtils.removeEnd(baseUrl, "/") + filepath;
    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = StringUtils.lowerCase(FileUtil.getSuffix(multipartFile.getOriginalFilename()));
        final long ONE_M = 1024 * 1024L;
        final long TWO_M = 2 * ONE_M;
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            return;
        }
        if (FileUploadBizEnum.QUESTION_BANK_COVER.equals(fileUploadBizEnum)) {
            if (fileSize > TWO_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库封面不能超过 2M");
            }
            if (!Arrays.asList("jpeg", "jpg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库封面仅支持 JPG、PNG、WebP");
            }
        }
    }
}
