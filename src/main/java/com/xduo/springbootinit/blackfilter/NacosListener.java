package com.xduo.springbootinit.blackfilter;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnBean(NacosConfigManager.class)
@ConditionalOnProperty(prefix = "nacos.config", name = "sync-enabled", havingValue = "true")
public class NacosListener implements InitializingBean {

    @Resource
    private NacosConfigManager nacosConfigManager;

    @Value("${nacos.config.data-id}")
    private String dataId;

    @Value("${nacos.config.group}")
    private String group;

    @Value("${nacos.config.timeout:30000}")
    private long timeout;

    private volatile String lastConfig;

    @Override
    public void afterPropertiesSet() {
        log.info("nacos 黑名单同步器启动，模式：定时拉取");
        syncBlackIpConfig();
    }

    @Scheduled(
            fixedDelayString = "${nacos.config.poll-fixed-delay-ms:30000}",
            initialDelayString = "${nacos.config.poll-initial-delay-ms:30000}"
    )
    public void syncBlackIpConfig() {
        try {
            ConfigService configService = nacosConfigManager.getConfigService();
            String config = configService.getConfig(dataId, group, timeout);
            if (Objects.equals(lastConfig, config)) {
                return;
            }
            BlackIpUtils.rebuildBlackIp(config);
            lastConfig = config;
            log.info("nacos 黑名单配置已同步，dataId={}, group={}", dataId, group);
        } catch (Exception e) {
            log.warn("nacos 黑名单配置同步失败，dataId={}, group={}", dataId, group, e);
        }
    }
}
