package com.xduo.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.manager.CosManager;
import com.xduo.springbootinit.model.dto.file.UploadFileRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.enums.FileUploadBizEnum;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * File upload APIs.
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    private static final long USER_UPLOAD_LIMIT_PER_HOUR = 10L;

    private static final List<String> USER_AVATAR_SUFFIXES = Arrays.asList("jpeg", "jpg", "png", "webp");

    private static final List<String> QUESTION_BANK_COVER_SUFFIXES = Arrays.asList("jpeg", "jpg", "png", "webp");

    private static final RedisScript<Long> UPLOAD_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('incr', KEYS[1])\n"
                    + "if current == 1 then\n"
                    + "  redis.call('expire', KEYS[1], ARGV[1])\n"
                    + "end\n"
                    + "return current;",
            Long.class
    );

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.upload.local-fallback-enabled:true}")
    private boolean localUploadFallbackEnabled;

    @Value("${app.upload.local-dir:${user.dir}/uploads}")
    private String localUploadDir;

    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest,
                                           HttpServletRequest request) {
        if (uploadFileRequest == null || StringUtils.isBlank(uploadFileRequest.getBiz())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(uploadFileRequest.getBiz());
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        validFile(multipartFile, fileUploadBizEnum);

        String redisKey = "user:upload:limit:" + loginUser.getId();
        long uploadCount = increaseUploadCount(redisKey);
        if (uploadCount > USER_UPLOAD_LIMIT_PER_HOUR) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传过于频繁，请一小时后再试");
        }

        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String originalFilename = StringUtils.defaultIfBlank(FileUtil.getName(multipartFile.getOriginalFilename()), "file");
        String filename = uuid + "-" + originalFilename.replaceAll("[\\\\/\\r\\n\\t]+", "_");
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);

        if (cosManager.isCosConfigured()) {
            return uploadToCos(multipartFile, filepath, uuid, filename);
        }
        return uploadToLocal(multipartFile, fileUploadBizEnum, loginUser, filename, filepath);
    }

    private long increaseUploadCount(String redisKey) {
        Long uploadCount = stringRedisTemplate.execute(
                UPLOAD_LIMIT_SCRIPT,
                Collections.singletonList(redisKey),
                String.valueOf(TimeUnit.HOURS.toSeconds(1))
        );
        if (uploadCount == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传频率校验失败");
        }
        return uploadCount;
    }

    private BaseResponse<String> uploadToCos(MultipartFile multipartFile, String filepath, String uuid, String filename) {
        File file = null;
        try {
            String fileSuffix = FileUtil.getSuffix(filename);
            file = File.createTempFile("upload-" + uuid + "-", StringUtils.isBlank(fileSuffix) ? null : "." + fileSuffix);
            multipartFile.transferTo(file);
            cosManager.putObject(filepath, file);
            return ResultUtils.success(cosManager.resolveSignedUrl(filepath));
        } catch (Exception e) {
            log.error("COS upload error, filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传至云存储失败");
        } finally {
            if (file != null && file.exists() && !file.delete()) {
                log.warn("Temporary upload file delete failed: {}", file.getAbsolutePath());
            }
        }
    }

    private BaseResponse<String> uploadToLocal(MultipartFile multipartFile,
                                               FileUploadBizEnum fileUploadBizEnum,
                                               User loginUser,
                                               String filename,
                                               String filepath) {
        if (!localUploadFallbackEnabled) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前环境未开启本地文件存储，请配置 COS 后重试");
        }
        try {
            Path bizPath = Paths.get(localUploadDir, fileUploadBizEnum.getValue(), String.valueOf(loginUser.getId()))
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(bizPath);

            Path targetPath = bizPath.resolve(filename).normalize();
            if (!targetPath.startsWith(bizPath)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不合法");
            }
            Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return ResultUtils.success("/api/files" + filepath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Local upload error, filepath = {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "本地文件上传失败");
        }
    }

    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        long fileSize = multipartFile.getSize();
        String fileSuffix = StringUtils.lowerCase(FileUtil.getSuffix(multipartFile.getOriginalFilename()));
        final long oneMb = 1024 * 1024L;
        final long twoMb = 2 * oneMb;
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > oneMb) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像大小不能超过 1MB");
            }
            if (!USER_AVATAR_SUFFIXES.contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像仅支持 JPG、PNG、WebP");
            }
            return;
        }
        if (FileUploadBizEnum.QUESTION_BANK_COVER.equals(fileUploadBizEnum)) {
            if (fileSize > twoMb) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库封面不能超过 2MB");
            }
            if (!QUESTION_BANK_COVER_SUFFIXES.contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库封面仅支持 JPG、PNG、WebP");
            }
        }
    }
}
