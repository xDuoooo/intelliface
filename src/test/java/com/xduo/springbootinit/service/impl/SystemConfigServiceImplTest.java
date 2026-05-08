package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.RedisConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.dto.systemconfig.SystemConfigUpdateRequest;
import com.xduo.springbootinit.model.entity.SystemConfig;
import com.xduo.springbootinit.model.vo.SystemConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SystemConfigServiceImpl systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = spy(new SystemConfigServiceImpl());
        ReflectionTestUtils.setField(systemConfigService, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCurrentConfigReturnsCachedConfigWhenCacheHit() {
        when(valueOperations.get(RedisConstant.getSystemConfigCacheKey()))
                .thenReturn("{\"id\":1,\"siteName\":\"Cached Site\",\"allowRegister\":1,\"requireCaptcha\":1,"
                        + "\"maintenanceMode\":0,\"enableSiteNotification\":1,\"enableEmailNotification\":1,"
                        + "\"enableLearningGoalReminder\":1,\"allowGuestViewQuestion\":1,\"allowGuestViewPost\":1}");

        SystemConfig result = systemConfigService.getCurrentConfig();

        assertEquals(1L, result.getId());
        assertEquals("Cached Site", result.getSiteName());
    }

    @Test
    void getCurrentConfigReturnsDatabaseConfigWhenCacheMisses() {
        SystemConfig dbConfig = createConfig(4L, "Database Site");
        when(valueOperations.get(RedisConstant.getSystemConfigCacheKey())).thenReturn(null);
        doReturn(dbConfig).when(systemConfigService).getOne(any(QueryWrapper.class), eq(false));

        SystemConfig result = systemConfigService.getCurrentConfig();

        assertEquals(4L, result.getId());
        assertEquals("Database Site", result.getSiteName());
        verify(systemConfigService, never()).save(any(SystemConfig.class));
    }

    @Test
    void getCurrentConfigDeletesBrokenCacheAndLoadsFromDatabase() {
        SystemConfig dbConfig = createConfig(2L, "DB Site");
        when(valueOperations.get(RedisConstant.getSystemConfigCacheKey())).thenReturn("{invalid-json");
        doReturn(dbConfig).when(systemConfigService).getOne(any(QueryWrapper.class), eq(false));

        SystemConfig result = systemConfigService.getCurrentConfig();

        assertEquals(2L, result.getId());
        verify(stringRedisTemplate).delete(RedisConstant.getSystemConfigCacheKey());
        verify(valueOperations).set(
                eq(RedisConstant.getSystemConfigCacheKey()),
                argThat(json -> json.contains("DB Site")),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void getCurrentConfigInitializesDefaultConfigWhenNoCacheOrDatabaseRecordExists() {
        when(valueOperations.get(RedisConstant.getSystemConfigCacheKey())).thenReturn(null);
        doReturn((SystemConfig) null, (SystemConfig) null)
                .when(systemConfigService).getOne(any(QueryWrapper.class), eq(false));
        doAnswer(invocation -> {
            SystemConfig saved = invocation.getArgument(0);
            saved.setId(10L);
            return true;
        }).when(systemConfigService).save(any(SystemConfig.class));

        SystemConfig result = systemConfigService.getCurrentConfig();

        assertEquals(10L, result.getId());
        assertEquals(1, result.getAllowRegister());
        assertEquals(1, result.getRequireCaptcha());
        assertEquals(1, result.getEnableSiteNotification());
        verify(valueOperations).set(
                eq(RedisConstant.getSystemConfigCacheKey()),
                argThat(json -> json.contains("\"id\":10")),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void updateCurrentConfigRejectsBlankSiteName() {
        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest();
        request.setSiteName(" ");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> systemConfigService.updateCurrentConfig(request));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void updateCurrentConfigRejectsTooLongSiteName() {
        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest();
        request.setSiteName("a".repeat(65));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> systemConfigService.updateCurrentConfig(request));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void updateCurrentConfigUpdatesFlagsAndRefreshesCacheWhenSuccessful() {
        SystemConfig currentConfig = createConfig(5L, "Current Site");
        SystemConfig latestConfig = createConfig(5L, "Updated Site");
        latestConfig.setAllowRegister(0);
        latestConfig.setEnableSiteNotification(0);
        doReturn(currentConfig).when(systemConfigService).getCurrentConfig();
        doReturn(true).when(systemConfigService).updateById(any(SystemConfig.class));
        doReturn(latestConfig).when(systemConfigService).getById(5L);

        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest();
        request.setSiteName("Updated Site");
        request.setSeoKeywords("java,interview");
        request.setAnnouncement("hello");
        request.setAllowRegister(false);
        request.setRequireCaptcha(true);
        request.setMaintenanceMode(false);
        request.setEnableSiteNotification(false);
        request.setEnableEmailNotification(true);
        request.setEnableLearningGoalReminder(true);
        request.setAllowGuestViewQuestion(true);
        request.setAllowGuestViewPost(false);

        boolean updated = systemConfigService.updateCurrentConfig(request);

        assertTrue(updated);
        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigService).updateById(captor.capture());
        SystemConfig updateConfig = captor.getValue();
        assertEquals(5L, updateConfig.getId());
        assertEquals("Updated Site", updateConfig.getSiteName());
        assertEquals(0, updateConfig.getAllowRegister());
        assertEquals(1, updateConfig.getRequireCaptcha());
        assertEquals(0, updateConfig.getEnableSiteNotification());
        assertEquals(0, updateConfig.getAllowGuestViewPost());
        verify(valueOperations).set(
                eq(RedisConstant.getSystemConfigCacheKey()),
                argThat(json -> json.contains("Updated Site")),
                eq(Duration.ofMinutes(30))
        );
    }

    @Test
    void updateCurrentConfigEvictsCacheWhenReloadedConfigIsMissing() {
        SystemConfig currentConfig = createConfig(6L, "Current Site");
        doReturn(currentConfig).when(systemConfigService).getCurrentConfig();
        doReturn(true).when(systemConfigService).updateById(any(SystemConfig.class));
        doReturn(null).when(systemConfigService).getById(6L);

        SystemConfigUpdateRequest request = new SystemConfigUpdateRequest();
        request.setSiteName("Updated Site");

        boolean updated = systemConfigService.updateCurrentConfig(request);

        assertTrue(updated);
        verify(stringRedisTemplate).delete(RedisConstant.getSystemConfigCacheKey());
    }

    @Test
    void getSystemConfigVOConvertsIntegerFlagsToBooleans() {
        SystemConfig systemConfig = createConfig(7L, "Boolean Site");
        systemConfig.setAllowRegister(0);
        systemConfig.setRequireCaptcha(0);
        systemConfig.setMaintenanceMode(1);
        systemConfig.setEnableSiteNotification(0);
        systemConfig.setEnableEmailNotification(1);
        systemConfig.setEnableLearningGoalReminder(0);
        systemConfig.setAllowGuestViewQuestion(1);
        systemConfig.setAllowGuestViewPost(0);
        doReturn(systemConfig).when(systemConfigService).getCurrentConfig();

        SystemConfigVO result = systemConfigService.getSystemConfigVO();

        assertFalse(result.getAllowRegister());
        assertFalse(result.getRequireCaptcha());
        assertTrue(result.getMaintenanceMode());
        assertFalse(result.getEnableSiteNotification());
        assertTrue(result.getEnableEmailNotification());
        assertFalse(result.getEnableLearningGoalReminder());
        assertTrue(result.getAllowGuestViewQuestion());
        assertFalse(result.getAllowGuestViewPost());
    }

    @Test
    void getPublicSystemConfigVOHidesSensitiveFlags() {
        doReturn(createConfig(3L, "Public Site")).when(systemConfigService).getCurrentConfig();

        SystemConfigVO result = systemConfigService.getPublicSystemConfigVO();

        assertEquals("Public Site", result.getSiteName());
        assertNull(result.getEnableSiteNotification());
        assertNull(result.getEnableEmailNotification());
        assertNull(result.getEnableLearningGoalReminder());
        assertTrue(result.getAllowRegister());
    }

    private SystemConfig createConfig(Long id, String siteName) {
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.setId(id);
        systemConfig.setSiteName(siteName);
        systemConfig.setSeoKeywords("seo");
        systemConfig.setAnnouncement("announcement");
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
}
