package com.xduo.springbootinit.controller;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.xduo.springbootinit.annotation.RateLimit;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.model.dto.tag.TagEsDTO;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签联想接口
 */
@RestController
@RequestMapping("/tag")
@Slf4j
public class TagController {

    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIMIT = 30;
    private static final int QUERY_FETCH_LIMIT = 80;
    private static final Set<String> ALLOWED_SCENE_SET = Set.of("all", "question", "post", "interest");
    private static final List<String> DEFAULT_TAG_POOL = Arrays.asList(
            "Java", "Spring", "Spring Boot", "MySQL", "Redis", "JVM", "并发", "算法", "数据结构",
            "系统设计", "项目复盘", "前端", "后端", "React", "Next.js", "Vue", "TypeScript",
            "校招", "面经", "求职经验", "微服务", "网络", "操作系统", "索引", "事务"
    );

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @GetMapping("/suggest")
    @RateLimit(key = "tag:suggest", maxRequests = 180, windowSeconds = 60, message = "标签联想过于频繁，请稍后再试")
    public BaseResponse<List<String>> listTagSuggestions(String keyword, String scene, Integer limit) {
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        if (normalizedKeyword.length() > 20) {
            normalizedKeyword = normalizedKeyword.substring(0, 20);
        }
        final String finalKeyword = normalizedKeyword;
        String normalizedScene = StringUtils.defaultIfBlank(scene, "all").trim().toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!ALLOWED_SCENE_SET.contains(normalizedScene), ErrorCode.PARAMS_ERROR, "标签场景不合法");
        int safeLimit = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));

        Map<String, Integer> tagWeightMap = new LinkedHashMap<>();
        collectDefaultTags(finalKeyword, tagWeightMap);
        collectTagsFromTagIndex(finalKeyword, normalizedScene, tagWeightMap);

        List<String> tagList = new ArrayList<>(tagWeightMap.keySet());
        tagList.sort((left, right) -> {
            int matchCompare = Integer.compare(getMatchPriority(right, finalKeyword), getMatchPriority(left, finalKeyword));
            if (matchCompare != 0) {
                return matchCompare;
            }
            int weightCompare = Integer.compare(tagWeightMap.getOrDefault(right, 0), tagWeightMap.getOrDefault(left, 0));
            if (weightCompare != 0) {
                return weightCompare;
            }
            int lengthCompare = Integer.compare(left.length(), right.length());
            if (lengthCompare != 0) {
                return lengthCompare;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(left, right);
        });
        if (tagList.size() > safeLimit) {
            tagList = tagList.subList(0, safeLimit);
        }
        return ResultUtils.success(tagList);
    }

    private void collectDefaultTags(String keyword, Map<String, Integer> tagWeightMap) {
        for (String tag : DEFAULT_TAG_POOL) {
            mergeTag(tagWeightMap, tag, keyword, 1);
        }
    }

    private void collectTagsFromTagIndex(String keyword, String scene, Map<String, Integer> tagWeightMap) {
        try {
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
            boolean hasCondition = false;
            if (!"all".equals(scene)) {
                boolQueryBuilder.filter(f -> f.term(t -> t.field("scenes").value(scene)));
                hasCondition = true;
            }
            if (StringUtils.isNotBlank(keyword)) {
                String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
                String escapedKeyword = escapeWildcardKeyword(normalizedKeyword);
                boolQueryBuilder.must(m -> m.bool(b -> b
                        .should(s -> s.term(t -> t.field("normalizedName").value(normalizedKeyword)))
                        .should(s -> s.wildcard(w -> w.field("normalizedName").value(escapedKeyword + "*").caseInsensitive(true)))
                        .should(s -> s.wildcard(w -> w.field("normalizedName").value("*" + escapedKeyword + "*").caseInsensitive(true)))
                        .minimumShouldMatch("1")));
                hasCondition = true;
            }
            var queryBuilder = NativeQuery.builder()
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("useCount").order(SortOrder.Desc))))
                    .withSort(SortOptions.of(s -> s.field(f -> f.field("updateTime").order(SortOrder.Desc))))
                    .withPageable(PageRequest.of(0, QUERY_FETCH_LIMIT));
            if (hasCondition) {
                queryBuilder.withQuery(q -> q.bool(boolQueryBuilder.build()));
            } else {
                queryBuilder.withQuery(q -> q.matchAll(m -> m));
            }
            SearchHits<TagEsDTO> searchHits = elasticsearchOperations.search(queryBuilder.build(), TagEsDTO.class);
            if (!searchHits.hasSearchHits()) {
                return;
            }
            for (SearchHit<TagEsDTO> hit : searchHits.getSearchHits()) {
                TagEsDTO tagEsDTO = hit.getContent();
                if (tagEsDTO == null) {
                    continue;
                }
                mergeTag(tagWeightMap, tagEsDTO.getName(), keyword, tagEsDTO.getUseCount() == null ? 0 : tagEsDTO.getUseCount());
            }
        } catch (Exception e) {
            log.warn("标签联想查询 tag ES 失败，已降级到默认标签池: {}", e.getMessage());
        }
    }

    private void mergeTag(Map<String, Integer> tagWeightMap, String rawTag, String keyword, int weight) {
        String normalizedTag = StringUtils.trimToNull(rawTag);
        if (normalizedTag == null) {
            return;
        }
        if (StringUtils.isNotBlank(keyword) && !StringUtils.containsIgnoreCase(normalizedTag, keyword)) {
            return;
        }
        tagWeightMap.merge(normalizedTag, Math.max(weight, 0), Integer::sum);
    }

    private String escapeWildcardKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
    }

    private int getMatchPriority(String tag, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return 0;
        }
        if (StringUtils.equalsIgnoreCase(tag, keyword)) {
            return 3;
        }
        if (StringUtils.startsWithIgnoreCase(tag, keyword)) {
            return 2;
        }
        return StringUtils.containsIgnoreCase(tag, keyword) ? 1 : 0;
    }
}
