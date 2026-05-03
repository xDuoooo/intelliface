package com.xduo.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xduo.springbootinit.constant.PostConstant;
import com.xduo.springbootinit.constant.QuestionConstant;
import com.xduo.springbootinit.esdao.TagEsDao;
import com.xduo.springbootinit.mapper.PostMapper;
import com.xduo.springbootinit.mapper.QuestionMapper;
import com.xduo.springbootinit.mapper.UserMapper;
import com.xduo.springbootinit.model.dto.tag.TagEsDTO;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.TagSyncService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

/**
 * 标签 ES 索引同步服务
 */
@Service
@Slf4j
public class TagSyncServiceImpl implements TagSyncService {

    private static final String SCENE_QUESTION = "question";
    private static final String SCENE_POST = "post";
    private static final String SCENE_INTEREST = "interest";

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TagEsDao tagEsDao;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void syncQuestionTags(String oldTagsJson, String newTagsJson) {
        syncChangedTags(oldTagsJson, newTagsJson);
    }

    @Override
    public void syncPostTags(String oldTagsJson, String newTagsJson) {
        syncChangedTags(oldTagsJson, newTagsJson);
    }

    @Override
    public void syncInterestTags(String oldTagsJson, String newTagsJson) {
        syncChangedTags(oldTagsJson, newTagsJson);
    }

    @Override
    public void rebuildAllTagIndex() {
        try {
            ensureIndexReady();
            Map<String, TagAggregateStats> aggregateMap = new LinkedHashMap<>();
            collectQuestionTagAggregates(aggregateMap);
            collectPostTagAggregates(aggregateMap);
            collectInterestTagAggregates(aggregateMap);
            List<TagEsDTO> documentList = new ArrayList<>();
            Date now = new Date();
            for (TagAggregateStats aggregateStats : aggregateMap.values()) {
                TagEsDTO tagEsDTO = new TagEsDTO();
                tagEsDTO.setId(aggregateStats.getNormalizedName());
                tagEsDTO.setName(aggregateStats.chooseDisplayName());
                tagEsDTO.setNormalizedName(aggregateStats.getNormalizedName());
                tagEsDTO.setScenes(aggregateStats.getScenes());
                tagEsDTO.setQuestionCount(aggregateStats.getQuestionCount());
                tagEsDTO.setPostCount(aggregateStats.getPostCount());
                tagEsDTO.setInterestCount(aggregateStats.getInterestCount());
                tagEsDTO.setUseCount(aggregateStats.getQuestionCount() + aggregateStats.getPostCount() + aggregateStats.getInterestCount());
                tagEsDTO.setUpdateTime(now);
                documentList.add(tagEsDTO);
            }
            try {
                tagEsDao.deleteAll();
            } catch (Exception e) {
                log.debug("重建标签索引前清空旧索引失败，可能是首次创建: {}", e.getMessage());
            }
            if (!documentList.isEmpty()) {
                tagEsDao.saveAll(documentList);
            }
            log.info("重建 tag 索引完成，总标签数={}", documentList.size());
        } catch (Exception e) {
            log.error("重建 tag 索引失败", e);
        }
    }

    private void syncChangedTags(String oldTagsJson, String newTagsJson) {
        try {
            ensureIndexReady();
            Map<String, String> preferredNameMap = new LinkedHashMap<>();
            Set<String> normalizedTagSet = new LinkedHashSet<>();
            collectChangedTags(oldTagsJson, preferredNameMap, normalizedTagSet);
            collectChangedTags(newTagsJson, preferredNameMap, normalizedTagSet);
            for (String normalizedTag : normalizedTagSet) {
                refreshSingleTagDocument(normalizedTag, preferredNameMap.get(normalizedTag));
            }
        } catch (Exception e) {
            log.error("同步 tag 索引失败", e);
        }
    }

    private void collectChangedTags(String rawTagsJson, Map<String, String> preferredNameMap, Set<String> normalizedTagSet) {
        for (String tag : parseTagList(rawTagsJson)) {
            String normalizedTag = normalizeTag(tag);
            if (normalizedTag == null) {
                continue;
            }
            normalizedTagSet.add(normalizedTag);
            preferredNameMap.put(normalizedTag, tag.trim());
        }
    }

    private void refreshSingleTagDocument(String normalizedTag, String preferredName) {
        if (StringUtils.isBlank(normalizedTag)) {
            return;
        }
        SceneTagStats questionStats = queryQuestionStats(normalizedTag);
        SceneTagStats postStats = queryPostStats(normalizedTag);
        SceneTagStats interestStats = queryInterestStats(normalizedTag);
        int questionCount = questionStats.getCount();
        int postCount = postStats.getCount();
        int interestCount = interestStats.getCount();
        int totalCount = questionCount + postCount + interestCount;
        if (totalCount <= 0) {
            tagEsDao.deleteById(normalizedTag);
            return;
        }
        TagEsDTO existingTag = tagEsDao.findById(normalizedTag).orElse(null);
        String displayName = chooseDisplayName(preferredName, existingTag, questionStats, postStats, interestStats, normalizedTag);
        TagEsDTO tagEsDTO = new TagEsDTO();
        tagEsDTO.setId(normalizedTag);
        tagEsDTO.setName(displayName);
        tagEsDTO.setNormalizedName(normalizedTag);
        tagEsDTO.setScenes(buildScenes(questionCount, postCount, interestCount));
        tagEsDTO.setQuestionCount(questionCount);
        tagEsDTO.setPostCount(postCount);
        tagEsDTO.setInterestCount(interestCount);
        tagEsDTO.setUseCount(totalCount);
        tagEsDTO.setUpdateTime(new Date());
        tagEsDao.save(tagEsDTO);
    }

    private List<String> buildScenes(int questionCount, int postCount, int interestCount) {
        List<String> sceneList = new ArrayList<>(3);
        if (questionCount > 0) {
            sceneList.add(SCENE_QUESTION);
        }
        if (postCount > 0) {
            sceneList.add(SCENE_POST);
        }
        if (interestCount > 0) {
            sceneList.add(SCENE_INTEREST);
        }
        return sceneList;
    }

    private String chooseDisplayName(String preferredName, TagEsDTO existingTag, SceneTagStats questionStats,
                                     SceneTagStats postStats, SceneTagStats interestStats, String normalizedTag) {
        if (StringUtils.isNotBlank(preferredName)) {
            return preferredName.trim();
        }
        String candidate = mostFrequentDisplayName(questionStats, postStats, interestStats);
        if (StringUtils.isNotBlank(candidate)) {
            return candidate;
        }
        if (existingTag != null && StringUtils.isNotBlank(existingTag.getName())) {
            return existingTag.getName();
        }
        return normalizedTag;
    }

    private String mostFrequentDisplayName(SceneTagStats... statsArray) {
        Map<String, Integer> displayCountMap = new LinkedHashMap<>();
        for (SceneTagStats stats : statsArray) {
            if (stats == null) {
                continue;
            }
            stats.getDisplayNameCountMap().forEach((displayName, count) ->
                    displayCountMap.merge(displayName, count, Integer::sum));
        }
        String bestDisplayName = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : displayCountMap.entrySet()) {
            String displayName = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && bestDisplayName != null && displayName.length() < bestDisplayName.length())) {
                bestDisplayName = displayName;
                bestCount = count;
            }
        }
        return bestDisplayName;
    }

    private void ensureIndexReady() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(TagEsDTO.class);
        if (indexOperations.exists()) {
            return;
        }
        boolean created = indexOperations.create();
        if (created) {
            indexOperations.putMapping(indexOperations.createMapping(TagEsDTO.class));
        }
    }

    private void collectQuestionTagAggregates(Map<String, TagAggregateStats> aggregateMap) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("tags")
                .isNotNull("tags")
                .ne("tags", "[]")
                .and(qw -> qw.eq("reviewStatus", QuestionConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
        questionMapper.selectList(queryWrapper).forEach(question ->
                mergeAggregateTags(aggregateMap, parseTagList(question.getTags()), SCENE_QUESTION));
    }

    private void collectPostTagAggregates(Map<String, TagAggregateStats> aggregateMap) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("tags")
                .isNotNull("tags")
                .ne("tags", "[]")
                .and(qw -> qw.eq("reviewStatus", PostConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
        postMapper.selectList(queryWrapper).forEach(post ->
                mergeAggregateTags(aggregateMap, parseTagList(post.getTags()), SCENE_POST));
    }

    private void collectInterestTagAggregates(Map<String, TagAggregateStats> aggregateMap) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("interestTags")
                .isNotNull("interestTags")
                .ne("interestTags", "[]");
        userMapper.selectList(queryWrapper).forEach(user ->
                mergeAggregateTags(aggregateMap, parseTagList(user.getInterestTags()), SCENE_INTEREST));
    }

    private void mergeAggregateTags(Map<String, TagAggregateStats> aggregateMap, List<String> tagList, String scene) {
        if (tagList.isEmpty()) {
            return;
        }
        Set<String> normalizedSeenSet = new LinkedHashSet<>();
        for (String tag : tagList) {
            String normalizedTag = normalizeTag(tag);
            if (normalizedTag == null || !normalizedSeenSet.add(normalizedTag)) {
                continue;
            }
            String displayName = tag.trim();
            TagAggregateStats aggregateStats = aggregateMap.computeIfAbsent(normalizedTag, TagAggregateStats::new);
            aggregateStats.increaseScene(scene, displayName);
        }
    }

    private SceneTagStats queryQuestionStats(String normalizedTag) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("tags")
                .isNotNull("tags")
                .ne("tags", "[]")
                .and(qw -> qw.eq("reviewStatus", QuestionConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"))
                .like("tags", "\"" + normalizedTag + "\"");
        List<Question> questionList = questionMapper.selectList(queryWrapper);
        return buildSceneTagStats(questionList.stream().map(Question::getTags).toList(), normalizedTag);
    }

    private SceneTagStats queryPostStats(String normalizedTag) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("tags")
                .isNotNull("tags")
                .ne("tags", "[]")
                .and(qw -> qw.eq("reviewStatus", PostConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"))
                .like("tags", "\"" + normalizedTag + "\"");
        List<Post> postList = postMapper.selectList(queryWrapper);
        return buildSceneTagStats(postList.stream().map(Post::getTags).toList(), normalizedTag);
    }

    private SceneTagStats queryInterestStats(String normalizedTag) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("interestTags")
                .isNotNull("interestTags")
                .ne("interestTags", "[]")
                .like("interestTags", "\"" + normalizedTag + "\"");
        List<User> userList = userMapper.selectList(queryWrapper);
        return buildSceneTagStats(userList.stream().map(User::getInterestTags).toList(), normalizedTag);
    }

    private SceneTagStats buildSceneTagStats(List<String> tagJsonList, String normalizedTag) {
        int count = 0;
        Map<String, Integer> displayNameCountMap = new LinkedHashMap<>();
        for (String rawTagJson : tagJsonList) {
            Set<String> matchedNameSet = new LinkedHashSet<>();
            for (String tag : parseTagList(rawTagJson)) {
                String currentNormalizedTag = normalizeTag(tag);
                if (!StringUtils.equals(currentNormalizedTag, normalizedTag)) {
                    continue;
                }
                matchedNameSet.add(tag.trim());
            }
            if (matchedNameSet.isEmpty()) {
                continue;
            }
            count++;
            for (String displayName : matchedNameSet) {
                displayNameCountMap.merge(displayName, 1, Integer::sum);
            }
        }
        return new SceneTagStats(count, displayNameCountMap);
    }

    private List<String> parseTagList(String rawTagsJson) {
        if (StringUtils.isBlank(rawTagsJson) || "[]".equals(rawTagsJson)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(rawTagsJson, String.class);
        } catch (Exception e) {
            log.debug("parse tag json error: {}", rawTagsJson, e);
            return Collections.emptyList();
        }
    }

    private String normalizeTag(String rawTag) {
        String normalizedTag = StringUtils.trimToNull(rawTag);
        if (normalizedTag == null) {
            return null;
        }
        return normalizedTag.toLowerCase(Locale.ROOT);
    }

    private static final class SceneTagStats {

        private final int count;

        private final Map<String, Integer> displayNameCountMap;

        private SceneTagStats(int count, Map<String, Integer> displayNameCountMap) {
            this.count = count;
            this.displayNameCountMap = displayNameCountMap;
        }

        public int getCount() {
            return count;
        }

        public Map<String, Integer> getDisplayNameCountMap() {
            return displayNameCountMap;
        }
    }

    private static final class TagAggregateStats {

        private final String normalizedName;

        private int questionCount;

        private int postCount;

        private int interestCount;

        private final Map<String, Integer> displayNameCountMap = new HashMap<>();

        private TagAggregateStats(String normalizedName) {
            this.normalizedName = normalizedName;
        }

        public void increaseScene(String scene, String displayName) {
            if (SCENE_QUESTION.equals(scene)) {
                questionCount++;
            } else if (SCENE_POST.equals(scene)) {
                postCount++;
            } else if (SCENE_INTEREST.equals(scene)) {
                interestCount++;
            }
            displayNameCountMap.merge(displayName, 1, Integer::sum);
        }

        public String getNormalizedName() {
            return normalizedName;
        }

        public int getQuestionCount() {
            return questionCount;
        }

        public int getPostCount() {
            return postCount;
        }

        public int getInterestCount() {
            return interestCount;
        }

        public List<String> getScenes() {
            List<String> sceneList = new ArrayList<>(3);
            if (questionCount > 0) {
                sceneList.add(SCENE_QUESTION);
            }
            if (postCount > 0) {
                sceneList.add(SCENE_POST);
            }
            if (interestCount > 0) {
                sceneList.add(SCENE_INTEREST);
            }
            return sceneList;
        }

        public String chooseDisplayName() {
            String bestDisplayName = null;
            int bestCount = -1;
            for (Map.Entry<String, Integer> entry : displayNameCountMap.entrySet()) {
                String displayName = entry.getKey();
                int count = entry.getValue();
                if (count > bestCount || (count == bestCount && bestDisplayName != null && displayName.length() < bestDisplayName.length())) {
                    bestDisplayName = displayName;
                    bestCount = count;
                }
            }
            return Optional.ofNullable(bestDisplayName).orElse(normalizedName);
        }
    }
}
