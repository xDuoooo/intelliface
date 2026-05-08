package com.xduo.springbootinit.blackfilter;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosListenerTest {

    @Mock
    private NacosConfigManager nacosConfigManager;

    @Mock
    private ConfigService configService;

    private NacosListener nacosListener;

    @BeforeEach
    void setUp() {
        nacosListener = new NacosListener();
        ReflectionTestUtils.setField(nacosListener, "nacosConfigManager", nacosConfigManager);
        ReflectionTestUtils.setField(nacosListener, "dataId", "black-ip.yaml");
        ReflectionTestUtils.setField(nacosListener, "group", "DEFAULT_GROUP");
        ReflectionTestUtils.setField(nacosListener, "timeout", 3000L);
    }

    @Test
    void afterPropertiesSetSyncsBlackIpConfig() throws Exception {
        String config = "blackIpList:\n  - 1.1.1.1";
        when(nacosConfigManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig("black-ip.yaml", "DEFAULT_GROUP", 3000L)).thenReturn(config);

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            nacosListener.afterPropertiesSet();

            blackIpUtilsMock.verify(() -> BlackIpUtils.rebuildBlackIp(config));
        }
    }

    @Test
    void syncBlackIpConfigSkipsRepeatedConfigContent() throws Exception {
        String config = "blackIpList:\n  - 2.2.2.2";
        when(nacosConfigManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig("black-ip.yaml", "DEFAULT_GROUP", 3000L)).thenReturn(config);

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            nacosListener.syncBlackIpConfig();
            nacosListener.syncBlackIpConfig();

            blackIpUtilsMock.verify(() -> BlackIpUtils.rebuildBlackIp(config), times(1));
        }
    }

    @Test
    void syncBlackIpConfigHandlesConfigServiceException() throws Exception {
        when(nacosConfigManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig("black-ip.yaml", "DEFAULT_GROUP", 3000L))
                .thenThrow(new RuntimeException("nacos down"));

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            assertDoesNotThrow(() -> nacosListener.syncBlackIpConfig());
            blackIpUtilsMock.verifyNoInteractions();
        }
    }

    @Test
    void syncBlackIpConfigRebuildsWhenConfigChanges() throws Exception {
        String firstConfig = "blackIpList:\n  - 3.3.3.3";
        String secondConfig = "blackIpList:\n  - 4.4.4.4";
        when(nacosConfigManager.getConfigService()).thenReturn(configService);
        when(configService.getConfig("black-ip.yaml", "DEFAULT_GROUP", 3000L))
                .thenReturn(firstConfig, secondConfig);

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            nacosListener.syncBlackIpConfig();
            nacosListener.syncBlackIpConfig();

            blackIpUtilsMock.verify(() -> BlackIpUtils.rebuildBlackIp(firstConfig));
            blackIpUtilsMock.verify(() -> BlackIpUtils.rebuildBlackIp(secondConfig));
        }
    }
}
