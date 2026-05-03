package com.xduo.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.RedisConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.SystemConfigMapper;
import com.xduo.springbootinit.model.dto.systemconfig.SystemConfigUpdateRequest;
import com.xduo.springbootinit.model.entity.SystemConfig;
import com.xduo.springbootinit.model.vo.SystemConfigVO;
import com.xduo.springbootinit.service.SystemConfigService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 系统配置服务实现
 */
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {

    private static final Object INIT_LOCK = new Object();

    private static final String DEFAULT_SITE_NAME = "IntelliFace 智面";

    private static final String DEFAULT_SEO_KEYWORDS = "面试, 刷题, Java, 互联网";

    private static final String DEFAULT_ANNOUNCEMENT = "欢迎来到智面 1.0 版本，体验 AI 智能面经！";

    private static final Duration SYSTEM_CONFIG_CACHE_TTL = Duration.ofMinutes(30);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public SystemConfig getCurrentConfig() {
        SystemConfig cachedConfig = getCachedSystemConfig();
        if (cachedConfig != null) {
            return cachedConfig;
        }
        QueryWrapper<SystemConfig> queryWrapper = buildSingleConfigQueryWrapper();
        SystemConfig systemConfig = this.getOne(queryWrapper, false);
        if (systemConfig != null) {
            cacheSystemConfig(systemConfig);
            return systemConfig;
        }
        synchronized (INIT_LOCK) {
            SystemConfig latestConfig = this.getOne(queryWrapper, false);
            if (latestConfig != null) {
                cacheSystemConfig(latestConfig);
                return latestConfig;
            }
            SystemConfig defaultConfig = buildDefaultConfig();
            boolean saved = this.save(defaultConfig);
            if (!saved) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化系统配置失败");
            }
            cacheSystemConfig(defaultConfig);
            return defaultConfig;
        }
    }

    @Override
    public SystemConfigVO getSystemConfigVO() {
        return toVO(getCurrentConfig());
    }

    @Override
    public SystemConfigVO getPublicSystemConfigVO() {
        SystemConfigVO systemConfigVO = toVO(getCurrentConfig());
        systemConfigVO.setEnableSiteNotification(null);
        systemConfigVO.setEnableEmailNotification(null);
        systemConfigVO.setEnableLearningGoalReminder(null);
        return systemConfigVO;
    }

    @Override
    public boolean updateCurrentConfig(SystemConfigUpdateRequest systemConfigUpdateRequest) {
        if (systemConfigUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String siteName = StringUtils.trimToEmpty(systemConfigUpdateRequest.getSiteName());
        if (StringUtils.isBlank(siteName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "站点名称不能为空");
        }
        if (siteName.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "站点名称不能超过 64 个字符");
        }
        String seoKeywords = StringUtils.trimToEmpty(systemConfigUpdateRequest.getSeoKeywords());
        if (seoKeywords.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SEO 关键词不能超过 512 个字符");
        }
        String announcement = StringUtils.trimToEmpty(systemConfigUpdateRequest.getAnnouncement());
        if (announcement.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "系统公告不能超过 1024 个字符");
        }
        SystemConfig currentConfig = getCurrentConfig();
        SystemConfig updateConfig = new SystemConfig();
        updateConfig.setId(currentConfig.getId());
        updateConfig.setSiteName(siteName);
        updateConfig.setSeoKeywords(seoKeywords);
        updateConfig.setAnnouncement(announcement);
        updateConfig.setAllowRegister(Boolean.TRUE.equals(systemConfigUpdateRequest.getAllowRegister()) ? 1 : 0);
        updateConfig.setRequireCaptcha(Boolean.TRUE.equals(systemConfigUpdateRequest.getRequireCaptcha()) ? 1 : 0);
        updateConfig.setMaintenanceMode(Boolean.TRUE.equals(systemConfigUpdateRequest.getMaintenanceMode()) ? 1 : 0);
        updateConfig.setEnableSiteNotification(Boolean.TRUE.equals(systemConfigUpdateRequest.getEnableSiteNotification()) ? 1 : 0);
        updateConfig.setEnableEmailNotification(Boolean.TRUE.equals(systemConfigUpdateRequest.getEnableEmailNotification()) ? 1 : 0);
        updateConfig.setEnableLearningGoalReminder(Boolean.TRUE.equals(systemConfigUpdateRequest.getEnableLearningGoalReminder()) ? 1 : 0);
        updateConfig.setAllowGuestViewQuestion(Boolean.TRUE.equals(systemConfigUpdateRequest.getAllowGuestViewQuestion()) ? 1 : 0);
        updateConfig.setAllowGuestViewPost(Boolean.TRUE.equals(systemConfigUpdateRequest.getAllowGuestViewPost()) ? 1 : 0);
        boolean updated = this.updateById(updateConfig);
        if (updated) {
            SystemConfig latestConfig = this.getById(currentConfig.getId());
            if (latestConfig != null) {
                cacheSystemConfig(latestConfig);
            } else {
                evictSystemConfigCache();
            }
        }
        return updated;
    }

    @Override
    public boolean isAllowRegister() {
        return Integer.valueOf(1).equals(getCurrentConfig().getAllowRegister());
    }

    @Override
    public boolean isRequireCaptcha() {
        return Integer.valueOf(1).equals(getCurrentConfig().getRequireCaptcha());
    }

    @Override
    public boolean isMaintenanceMode() {
        return Integer.valueOf(1).equals(getCurrentConfig().getMaintenanceMode());
    }

    @Override
    public boolean isEnableSiteNotification() {
        return Integer.valueOf(1).equals(getCurrentConfig().getEnableSiteNotification());
    }

    @Override
    public boolean isEnableEmailNotification() {
        return Integer.valueOf(1).equals(getCurrentConfig().getEnableEmailNotification());
    }

    @Override
    public boolean isEnableLearningGoalReminder() {
        return Integer.valueOf(1).equals(getCurrentConfig().getEnableLearningGoalReminder());
    }

    @Override
    public boolean isAllowGuestViewQuestion() {
        return Integer.valueOf(1).equals(getCurrentConfig().getAllowGuestViewQuestion());
    }

    @Override
    public boolean isAllowGuestViewPost() {
        return Integer.valueOf(1).equals(getCurrentConfig().getAllowGuestViewPost());
    }

    private SystemConfig buildDefaultConfig() {
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.setSiteName(DEFAULT_SITE_NAME);
        systemConfig.setSeoKeywords(DEFAULT_SEO_KEYWORDS);
        systemConfig.setAnnouncement(DEFAULT_ANNOUNCEMENT);
        systemConfig.setAllowRegister(1);
        systemConfig.setRequireCaptcha(1);
        systemConfig.setMaintenanceMode(0);
        systemConfig.setEnableSiteNotification(1);
        systemConfig.setEnableEmailNotification(1);
        systemConfig.setEnableLearningGoalReminder(1);
        systemConfig.setAllowGuestViewQuestion(1);
        systemConfig.setAllowGuestViewPost(1);
        return systemConfig;
    }

    private SystemConfigVO toVO(SystemConfig systemConfig) {
        SystemConfigVO systemConfigVO = new SystemConfigVO();
        BeanUtils.copyProperties(systemConfig, systemConfigVO);
        systemConfigVO.setAllowRegister(Integer.valueOf(1).equals(systemConfig.getAllowRegister()));
        systemConfigVO.setRequireCaptcha(Integer.valueOf(1).equals(systemConfig.getRequireCaptcha()));
        systemConfigVO.setMaintenanceMode(Integer.valueOf(1).equals(systemConfig.getMaintenanceMode()));
        systemConfigVO.setEnableSiteNotification(Integer.valueOf(1).equals(systemConfig.getEnableSiteNotification()));
        systemConfigVO.setEnableEmailNotification(Integer.valueOf(1).equals(systemConfig.getEnableEmailNotification()));
        systemConfigVO.setEnableLearningGoalReminder(Integer.valueOf(1).equals(systemConfig.getEnableLearningGoalReminder()));
        systemConfigVO.setAllowGuestViewQuestion(Integer.valueOf(1).equals(systemConfig.getAllowGuestViewQuestion()));
        systemConfigVO.setAllowGuestViewPost(Integer.valueOf(1).equals(systemConfig.getAllowGuestViewPost()));
        return systemConfigVO;
    }

    private QueryWrapper<SystemConfig> buildSingleConfigQueryWrapper() {
        QueryWrapper<SystemConfig> queryWrapper = Wrappers.query();
        queryWrapper.orderByAsc("id").last("limit 1");
        return queryWrapper;
    }

    private SystemConfig getCachedSystemConfig() {
        String cacheKey = RedisConstant.getSystemConfigCacheKey();
        String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isBlank(cacheValue)) {
            return null;
        }
        try {
            return JSONUtil.toBean(cacheValue, SystemConfig.class);
        } catch (Exception e) {
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void cacheSystemConfig(SystemConfig systemConfig) {
        if (systemConfig == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                RedisConstant.getSystemConfigCacheKey(),
                JSONUtil.toJsonStr(systemConfig),
                SYSTEM_CONFIG_CACHE_TTL
        );
    }

    private void evictSystemConfigCache() {
        stringRedisTemplate.delete(RedisConstant.getSystemConfigCacheKey());
    }
}
