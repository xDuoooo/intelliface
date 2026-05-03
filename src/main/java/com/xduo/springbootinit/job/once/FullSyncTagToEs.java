package com.xduo.springbootinit.job.once;

import com.xduo.springbootinit.service.TagSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时全量重建标签索引
 */
@Component
@ConditionalOnProperty(prefix = "app.es-sync.tag", name = "full-sync-on-start", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FullSyncTagToEs implements CommandLineRunner {

    @Resource
    private TagSyncService tagSyncService;

    @Override
    public void run(String... args) {
        log.info("FullSyncTagToEs start");
        tagSyncService.rebuildAllTagIndex();
        log.info("FullSyncTagToEs end");
    }
}
