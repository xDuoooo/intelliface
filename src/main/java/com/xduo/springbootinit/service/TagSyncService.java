package com.xduo.springbootinit.service;

/**
 * 标签索引同步服务
 */
public interface TagSyncService {

    /**
     * 同步题目标签变更到标签索引
     */
    void syncQuestionTags(String oldTagsJson, String newTagsJson);

    /**
     * 同步帖子标签变更到标签索引
     */
    void syncPostTags(String oldTagsJson, String newTagsJson);

    /**
     * 同步用户兴趣标签变更到标签索引
     */
    void syncInterestTags(String oldTagsJson, String newTagsJson);

    /**
     * 全量重建标签索引
     */
    void rebuildAllTagIndex();
}
