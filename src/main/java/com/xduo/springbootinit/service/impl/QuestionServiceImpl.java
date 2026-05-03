package com.xduo.springbootinit.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.constant.QuestionConstant;
import com.xduo.springbootinit.esdao.QuestionEsDao;
import com.xduo.springbootinit.manager.AiManager;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.mapper.QuestionMapper;
import com.xduo.springbootinit.mapper.UserQuestionHistoryMapper;
import com.xduo.springbootinit.model.dto.question.QuestionQueryRequest;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionBankQuestion;
import com.xduo.springbootinit.model.entity.QuestionFavour;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.entity.UserQuestionHistory;
import com.xduo.springbootinit.service.QuestionFavourService;
import com.xduo.springbootinit.model.vo.QuestionVO;
import com.xduo.springbootinit.model.vo.ResumeQuestionRecommendVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.QuestionBankQuestionService;
import com.xduo.springbootinit.service.EsSyncTaskService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.TagSyncService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.xduo.springbootinit.model.dto.question.QuestionEsDTO;
import com.xduo.springbootinit.exception.BusinessException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.ArrayList;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private static final int USER_INTERACTION_LIMIT = 120;
    private static final double MAX_COLLABORATIVE_SCORE = 3D;
    private static final int COLLABORATIVE_SCORE_SCALE = 12;
    private static final double MIN_COLLABORATIVE_SIMILARITY = 0.01D;

    @Resource
    private UserService userService;

    @Resource
    private ElasticsearchOperations elasticsearchOperations;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    @Lazy
    private QuestionFavourService questionFavourService;

    @Resource
    private UserQuestionHistoryMapper userQuestionHistoryMapper;

    @Resource
    private AiManager aiManager;

    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private EsSyncTaskService esSyncTaskService;

    @Resource
    private TagSyncService tagSyncService;

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        String title = question.getTitle();
        String content = question.getContent();
        String answer = question.getAnswer();
        String difficulty = question.getDifficulty();
        // 创建数据时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, answer), ErrorCode.PARAMS_ERROR, "题目标题、内容和题解不能为空");
        }
        // 修改数据时，有参数则校验
        if (StringUtils.isNotBlank(title)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (content != null) {
            ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "题目内容不能为空");
        }
        if (answer != null) {
            ThrowUtils.throwIf(StringUtils.isBlank(answer), ErrorCode.PARAMS_ERROR, "题解不能为空");
        }
        if (StringUtils.isNotBlank(difficulty)) {
            ThrowUtils.throwIf(!QuestionConstant.ALLOWED_DIFFICULTY_SET.contains(difficulty), ErrorCode.PARAMS_ERROR, "题目难度不合法");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String answer = questionQueryRequest.getAnswer();
        String difficulty = questionQueryRequest.getDifficulty();
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        Integer reviewStatus = questionQueryRequest.getReviewStatus();
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        queryWrapper.eq(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        if (reviewStatus != null) {
            if (QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus) {
                queryWrapper.and(qw -> qw.eq("reviewStatus", reviewStatus).or().isNull("reviewStatus"));
            } else {
                queryWrapper.eq("reviewStatus", reviewStatus);
            }
        }
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 对象转封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);
        // 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);
        Map<Long, Integer> favourCountMap = getQuestionFavourCountMap(Collections.singleton(question.getId()));
        questionVO.setFavourNum(favourCountMap.getOrDefault(question.getId(), 0));
        // 是否已收藏
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
            favourQueryWrapper.eq("questionId", question.getId());
            favourQueryWrapper.eq("userId", loginUser.getId());
            questionVO.setHasFavour(questionFavourService.getOne(favourQueryWrapper) != null);
            Map<Long, Integer> questionStatusMap = getUserQuestionStatusMap(Collections.singleton(question.getId()), loginUser.getId());
            questionVO.setQuestionStatus(questionStatusMap.getOrDefault(question.getId(), 0));
        } else {
            questionVO.setHasFavour(false);
        }
        return questionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(),
                questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        questionVOPage.setRecords(buildQuestionVOList(questionList, request, Collections.emptyMap()));
        return questionVOPage;
    }

    private List<QuestionVO> buildQuestionVOList(List<Question> questionList,
                                                 HttpServletRequest request,
                                                 Map<Long, String> recommendReasonMap) {
        if (CollUtil.isEmpty(questionList)) {
            return Collections.emptyList();
        }
        List<QuestionVO> questionVOList = questionList.stream()
                .map(QuestionVO::objToVo)
                .collect(Collectors.toList());
        Set<Long> userIdSet = questionList.stream()
                .map(Question::getUserId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIdSet.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (existing, replacement) -> existing));
        Set<Long> questionIdSet = questionList.stream()
                .map(Question::getId)
                .collect(Collectors.toSet());
        Map<Long, Integer> questionIdFavourNumMap = getQuestionFavourCountMap(questionIdSet);
        Map<Long, Boolean> questionIdHasFavourMap = new HashMap<>();
        Map<Long, Integer> questionIdStatusMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null && CollUtil.isNotEmpty(questionIdSet)) {
            QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
            favourQueryWrapper.in("questionId", questionIdSet);
            favourQueryWrapper.eq("userId", loginUser.getId());
            questionFavourService.list(favourQueryWrapper).forEach(favour ->
                    questionIdHasFavourMap.put(favour.getQuestionId(), true));
            questionIdStatusMap.putAll(getUserQuestionStatusMap(questionIdSet, loginUser.getId()));
        }
        questionVOList.forEach(questionVO -> {
            questionVO.setUser(userService.getUserVO(userMap.get(questionVO.getUserId())));
            questionVO.setHasFavour(questionIdHasFavourMap.getOrDefault(questionVO.getId(), false));
            questionVO.setQuestionStatus(questionIdStatusMap.getOrDefault(questionVO.getId(), 0));
            questionVO.setFavourNum(questionIdFavourNumMap.getOrDefault(questionVO.getId(), 0));
            if (recommendReasonMap != null && recommendReasonMap.containsKey(questionVO.getId())) {
                questionVO.setRecommendReason(recommendReasonMap.get(questionVO.getId()));
            }
        });
        return questionVOList;
    }

    private Map<Long, Integer> getQuestionFavourCountMap(Set<Long> questionIdSet) {
        if (CollUtil.isEmpty(questionIdSet)) {
            return Collections.emptyMap();
        }
        QueryWrapper<QuestionFavour> favourCountQueryWrapper = new QueryWrapper<>();
        favourCountQueryWrapper.in("questionId", questionIdSet);
        favourCountQueryWrapper.select("questionId", "count(*) as totalCount");
        favourCountQueryWrapper.groupBy("questionId");
        return questionFavourService.listMaps(favourCountQueryWrapper).stream()
                .collect(Collectors.toMap(
                        item -> Long.valueOf(String.valueOf(item.get("questionId"))),
                        item -> Integer.parseInt(String.valueOf(item.get("totalCount"))),
                        (existing, replacement) -> existing));
    }

    private Map<Long, Integer> getUserQuestionStatusMap(Set<Long> questionIdSet, Long userId) {
        if (CollUtil.isEmpty(questionIdSet) || userId == null || userId <= 0) {
            return Collections.emptyMap();
        }
        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.in("questionId", questionIdSet);
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.select("questionId", "status");
        return userQuestionHistoryMapper.selectList(historyQueryWrapper).stream()
                .filter(item -> item.getQuestionId() != null)
                .collect(Collectors.toMap(
                        UserQuestionHistory::getQuestionId,
                        item -> item.getStatus() == null ? 0 : item.getStatus(),
                        (existing, replacement) -> replacement));
    }

    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        // 获取参数
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String answer = questionQueryRequest.getAnswer();
        String difficulty = questionQueryRequest.getDifficulty();
        List<String> tags = questionQueryRequest.getTags();
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        Integer reviewStatus = questionQueryRequest.getReviewStatus();
        long requestedCurrent = Math.max(questionQueryRequest.getCurrent(), 1);
        int current = (int) (requestedCurrent - 1);
        int pageSize = Math.max(questionQueryRequest.getPageSize(), 1);
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        // 过滤
        boolQueryBuilder.filter(f -> f.term(t -> t.field("isDelete").value(0)));
        if (id != null) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("id").value(id)));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(m -> m.term(t -> t.field("id").value(notId)));
        }
        if (userId != null) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("userId").value(userId)));
        }
        if (reviewStatus != null) {
            if (QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus) {
                boolQueryBuilder.filter(f -> f.bool(b -> b
                        .should(s -> s.term(t -> t.field("reviewStatus").value(reviewStatus)))
                        .should(s -> s.bool(bb -> bb.mustNot(m -> m.exists(e -> e.field("reviewStatus")))))
                        .minimumShouldMatch("1")));
            } else {
                boolQueryBuilder.filter(f -> f.term(t -> t.field("reviewStatus").value(reviewStatus)));
            }
        }
        if (questionBankId != null) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("questionBankId").value(questionBankId)));
        }
        if (StringUtils.isNotBlank(title)) {
            boolQueryBuilder.must(m -> m.match(mm -> mm.field("title").query(title)));
        }
        if (StringUtils.isNotBlank(content)) {
            boolQueryBuilder.must(m -> m.match(mm -> mm.field("content").query(content)));
        }
        if (StringUtils.isNotBlank(answer)) {
            boolQueryBuilder.must(m -> m.match(mm -> mm.field("answer").query(answer)));
        }
        if (StringUtils.isNotBlank(difficulty)) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("difficulty").value(difficulty)));
        }
        // 必须包含所有标签
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(f -> f.term(t -> t.field("tags").value(tag)));
            }
        }
        // 按关键词检索
        if (StringUtils.isNotBlank(searchText)) {
            boolQueryBuilder.must(m -> m.bool(buildQuestionKeywordSearchQuery(searchText)));
        }
        // 排序
        SortOptions sortOptions;
        if (StringUtils.isNotBlank(sortField)) {
            sortOptions = SortOptions.of(s -> s.field(f -> f
                    .field(sortField)
                    .order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.Asc : SortOrder.Desc)));
        } else {
            sortOptions = SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        }

        // 构造 NativeQuery
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()))
                .withSort(sortOptions)
                .withPageable(org.springframework.data.domain.PageRequest.of(current, pageSize))
                .build();

        Page<Question> page = new Page<>(requestedCurrent, pageSize);
        List<Question> resourceList = new ArrayList<>();
        try {
            SearchHits<QuestionEsDTO> searchHits = elasticsearchOperations.search(nativeQuery, QuestionEsDTO.class);
            page.setTotal(searchHits.getTotalHits());

            if (searchHits.hasSearchHits()) {
                List<SearchHit<QuestionEsDTO>> searchHitList = searchHits.getSearchHits();
                for (SearchHit<QuestionEsDTO> hit : searchHitList) {
                    resourceList.add(QuestionEsDTO.dtoToObj(hit.getContent()));
                }
            }
        } catch (Exception e) {
            log.error("es search error", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询 ES 失败");
        }

        page.setRecords(resourceList);
        return page;
    }

    private BoolQuery buildQuestionKeywordSearchQuery(String rawKeyword) {
        String keyword = StringUtils.trimToEmpty(rawKeyword);
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        String escapedWildcardKeyword = escapeEsWildcardKeyword(normalizedKeyword);
        BoolQuery.Builder searchQueryBuilder = new BoolQuery.Builder();
        addKeywordShouldClauses(searchQueryBuilder, "title", keyword, escapedWildcardKeyword);
        addKeywordShouldClauses(searchQueryBuilder, "content", keyword, escapedWildcardKeyword);
        addKeywordShouldClauses(searchQueryBuilder, "answer", keyword, escapedWildcardKeyword);
        searchQueryBuilder.should(s -> s.term(t -> t.field("tags").value(keyword)));
        addKeywordShouldClauses(searchQueryBuilder, "tags", keyword, escapedWildcardKeyword);
        searchQueryBuilder.minimumShouldMatch("1");
        return searchQueryBuilder.build();
    }

    private void addKeywordShouldClauses(BoolQuery.Builder searchQueryBuilder,
                                         String field,
                                         String keyword,
                                         String escapedWildcardKeyword) {
        searchQueryBuilder.should(s -> s.match(m -> m.field(field).query(keyword)));
        searchQueryBuilder.should(s -> s.matchPhrasePrefix(m -> m.field(field).query(keyword)));
        searchQueryBuilder.should(s -> s.wildcard(w -> w
                .field(field)
                .value(escapedWildcardKeyword + "*")
                .caseInsensitive(true)));
        if (keyword.length() >= 2) {
            searchQueryBuilder.should(s -> s.wildcard(w -> w
                    .field(field)
                    .value("*" + escapedWildcardKeyword + "*")
                    .caseInsensitive(true)));
        }
    }

    private String escapeEsWildcardKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteQuestions(List<Long> questionIdList) {
        if (CollUtil.isEmpty(questionIdList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "要删除的题目列表为空");
        }
        Set<Long> distinctQuestionIdSet = questionIdList.stream()
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ThrowUtils.throwIf(CollUtil.isEmpty(distinctQuestionIdSet), ErrorCode.PARAMS_ERROR, "要删除的题目列表为空");

        LambdaQueryWrapper<QuestionBankQuestion> relationQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .in(QuestionBankQuestion::getQuestionId, distinctQuestionIdSet);
        // 题目可能还未加入任何题库，这里不应把 0 行删除视为异常。
        questionBankQuestionService.remove(relationQueryWrapper);
        for (Long questionId : distinctQuestionIdSet) {
            Question oldQuestion = this.getById(questionId);
            boolean result = this.removeById(questionId);
            if (!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除题目失败");
            }
            deleteQuestionFromEs(questionId);
            tagSyncService.syncQuestionTags(oldQuestion == null ? null : oldQuestion.getTags(), null);
        }
    }
    @Override
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 题目表的查询条件
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        // 根据题库查询题目列表接口
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if (questionBankId != null) {
            // 查询题库内的题目 id
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion::getQuestionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
            if (CollUtil.isEmpty(questionList)) {
                Page<Question> emptyPage = new Page<>(current, size, 0);
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage;
            }
            // 取出题目 id 集合
            Set<Long> questionIdSet = questionList.stream()
                    .map(QuestionBankQuestion::getQuestionId)
                    .collect(Collectors.toSet());
            // 复用原有题目表的查询条件
            queryWrapper.in("id", questionIdSet);
        }
        // 查询数据库
        Page<Question> questionPage = this.page(new Page<>(current, size), queryWrapper);
        return questionPage;
    }

    @Override
    public List<QuestionVO> listRecommendQuestionVOByUser(long userId, Long currentQuestionId, int size, HttpServletRequest request) {
        size = Math.max(1, Math.min(size, 12));
        Map<String, Integer> tagWeightMap = new HashMap<>();
        Map<Long, Integer> interactedQuestionWeightMap = buildUserInteractionWeightMap(userId);
        Set<Long> interactedQuestionIdSet = new HashSet<>(interactedQuestionWeightMap.keySet());

        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.orderByDesc("updateTime");
        historyQueryWrapper.last("limit " + USER_INTERACTION_LIMIT);
        List<UserQuestionHistory> historyList = userQuestionHistoryMapper.selectList(historyQueryWrapper);
        if (CollUtil.isNotEmpty(historyList)) {
            Set<Long> historyQuestionIdSet = historyList.stream()
                    .map(UserQuestionHistory::getQuestionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            interactedQuestionIdSet.addAll(historyQuestionIdSet);
            Map<Long, Integer> historyStatusMap = historyList.stream()
                    .filter(item -> item.getQuestionId() != null)
                    .collect(Collectors.toMap(UserQuestionHistory::getQuestionId, UserQuestionHistory::getStatus, (a, b) -> Math.max(a, b)));
            if (CollUtil.isNotEmpty(historyQuestionIdSet)) {
                this.listByIds(historyQuestionIdSet).forEach(question -> {
                    int weight = convertHistoryStatusToWeight(historyStatusMap.getOrDefault(question.getId(), 0));
                    addTagWeights(tagWeightMap, parseTagList(question.getTags()), weight);
                });
            }
        }

        QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
        favourQueryWrapper.eq("userId", userId);
        favourQueryWrapper.orderByDesc("createTime");
        favourQueryWrapper.last("limit " + USER_INTERACTION_LIMIT);
        List<QuestionFavour> favourList = questionFavourService.list(favourQueryWrapper);
        if (CollUtil.isNotEmpty(favourList)) {
            Set<Long> favourQuestionIdSet = favourList.stream()
                    .map(QuestionFavour::getQuestionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            interactedQuestionIdSet.addAll(favourQuestionIdSet);
            if (CollUtil.isNotEmpty(favourQuestionIdSet)) {
                this.listByIds(favourQuestionIdSet).forEach(question ->
                        addTagWeights(tagWeightMap, parseTagList(question.getTags()), 5));
            }
        }

        if (currentQuestionId != null && currentQuestionId > 0) {
            Question currentQuestion = this.getById(currentQuestionId);
            if (currentQuestion != null) {
                addTagWeights(tagWeightMap, parseTagList(currentQuestion.getTags()), 2);
            }
        }

        List<Question> candidateQuestionList = listRecommendationCandidateQuestions(300, currentQuestionId);
        if (CollUtil.isEmpty(candidateQuestionList)) {
            return Collections.emptyList();
        }

        Map<Long, CollaborativeRecommendationDetail> collaborativeScoreMap = buildCollaborativeScoreMap(
                interactedQuestionWeightMap,
                candidateQuestionList,
                loadQuestionTitleMap(interactedQuestionWeightMap.keySet())
        );
        String recommendedDifficulty = inferRecommendedDifficulty(userId);

        List<QuestionRecommendationScore> scoredList = candidateQuestionList.stream()
                .filter(question -> currentQuestionId == null || !question.getId().equals(currentQuestionId))
                .filter(question -> !interactedQuestionIdSet.contains(question.getId()))
                .map(question -> {
                    QuestionRecommendationScore contentScore = buildRecommendationScore(question, tagWeightMap, "偏好标签");
                    CollaborativeRecommendationDetail collaborativeDetail = collaborativeScoreMap.get(question.getId());
                    int finalScore = contentScore.getScore()
                            + (collaborativeDetail == null ? 0 : collaborativeDetail.getScore())
                            + buildDifficultyScore(question.getDifficulty(), recommendedDifficulty);
                    String finalReason = mergeRecommendationReason(
                            contentScore.getReason(),
                            collaborativeDetail == null ? "" : collaborativeDetail.getReason(),
                            buildDifficultyReason(question.getDifficulty(), recommendedDifficulty)
                    );
                    return new QuestionRecommendationScore(question, finalScore, finalReason);
                })
                .filter(item -> item.getScore() > 0)
                .sorted(Comparator
                        .comparingInt(QuestionRecommendationScore::getScore).reversed()
                        .thenComparing(QuestionRecommendationScore::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(scoredList)) {
            List<Question> fallbackList = candidateQuestionList.stream()
                    .filter(question -> currentQuestionId == null || !question.getId().equals(currentQuestionId))
                    .sorted(Comparator.comparing(Question::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(size)
                    .collect(Collectors.toList());
            Map<Long, String> recommendReasonMap = fallbackList.stream()
                    .collect(Collectors.toMap(Question::getId, question -> "根据最新题目为你补充推荐", (left, right) -> left));
            return buildQuestionVOList(fallbackList, request, recommendReasonMap);
        }

        List<Question> rankedQuestionList = scoredList.stream()
                .map(QuestionRecommendationScore::getQuestion)
                .collect(Collectors.toList());
        Map<Long, String> recommendReasonMap = scoredList.stream()
                .collect(Collectors.toMap(item -> item.getQuestion().getId(), QuestionRecommendationScore::getReason, (left, right) -> left));
        return buildQuestionVOList(rankedQuestionList, request, recommendReasonMap);
    }

    @Override
    public List<QuestionVO> listRelatedQuestionVO(long questionId, int size, HttpServletRequest request) {
        size = Math.max(1, Math.min(size, 12));
        Question currentQuestion = this.getById(questionId);
        if (currentQuestion == null) {
            return Collections.emptyList();
        }
        List<String> currentTagList = parseTagList(currentQuestion.getTags());
        List<Question> candidateQuestionList = listRecommendationCandidateQuestions(300, questionId);
        if (CollUtil.isEmpty(candidateQuestionList)) {
            return Collections.emptyList();
        }

        Map<Long, Integer> currentQuestionWeightMap = new HashMap<>();
        currentQuestionWeightMap.put(questionId, 1);
        Map<Long, CollaborativeRecommendationDetail> collaborativeScoreMap = buildCollaborativeScoreMap(
                currentQuestionWeightMap,
                candidateQuestionList,
                Collections.singletonMap(questionId, currentQuestion.getTitle())
        );

        List<QuestionRecommendationScore> scoredList = candidateQuestionList.stream()
                .filter(question -> !question.getId().equals(questionId))
                .map(question -> {
                    List<String> candidateTags = parseTagList(question.getTags());
                    List<String> overlapTags = candidateTags.stream()
                            .filter(currentTagList::contains)
                            .distinct()
                            .collect(Collectors.toList());
                    int score = overlapTags.size() * 100;
                    CollaborativeRecommendationDetail collaborativeDetail = collaborativeScoreMap.get(question.getId());
                    score += collaborativeDetail == null ? 0 : collaborativeDetail.getScore();
                    String reason = mergeRecommendationReason(
                            overlapTags.isEmpty() ? "" : "关联标签：" + String.join(" / ", overlapTags),
                            collaborativeDetail == null ? "" : collaborativeDetail.getReason()
                    );
                    return new QuestionRecommendationScore(question, score, reason);
                })
                .filter(item -> item.getScore() > 0)
                .sorted(Comparator
                        .comparingInt(QuestionRecommendationScore::getScore).reversed()
                        .thenComparing(QuestionRecommendationScore::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(scoredList)) {
            List<Question> fallbackList = candidateQuestionList.stream()
                    .filter(question -> !question.getId().equals(questionId))
                    .sorted(Comparator.comparing(Question::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(size)
                    .collect(Collectors.toList());
            Map<Long, String> recommendReasonMap = fallbackList.stream()
                    .collect(Collectors.toMap(Question::getId, question -> "为你补充同主题下的最新题目", (left, right) -> left));
            return buildQuestionVOList(fallbackList, request, recommendReasonMap);
        }

        List<Question> rankedQuestionList = scoredList.stream()
                .map(QuestionRecommendationScore::getQuestion)
                .collect(Collectors.toList());
        Map<Long, String> recommendReasonMap = scoredList.stream()
                .collect(Collectors.toMap(item -> item.getQuestion().getId(), QuestionRecommendationScore::getReason, (left, right) -> left));
        return buildQuestionVOList(rankedQuestionList, request, recommendReasonMap);
    }

    @Override
    public ResumeQuestionRecommendVO recommendQuestionsByResume(long userId, String resumeText, int size, HttpServletRequest request) {
        ThrowUtils.throwIf(StringUtils.isBlank(resumeText), ErrorCode.PARAMS_ERROR, "请先粘贴简历内容");
        String trimmedResumeText = resumeText.trim();
        ThrowUtils.throwIf(trimmedResumeText.length() < 20, ErrorCode.PARAMS_ERROR, "简历内容过短，请补充更多项目经历或技能描述");
        size = Math.max(1, Math.min(size, 12));

        List<Question> allQuestionList = listRecommendationCandidateQuestions(500, null);
        ResumeQuestionRecommendVO result = new ResumeQuestionRecommendVO();
        if (CollUtil.isEmpty(allQuestionList)) {
            result.setJobDirection("待识别");
            result.setExtractedTags(Collections.emptyList());
            result.setResumeText(trimmedResumeText);
            result.setAnalysisSummary("当前题库为空，暂时无法给出题目推荐。");
            result.setRecommendFocus("请先补充题目数据");
            result.setAnalysisSource("系统规则分析");
            result.setQuestionList(Collections.emptyList());
            return result;
        }

        Set<String> candidateTagSet = allQuestionList.stream()
                .flatMap(question -> parseTagList(question.getTags()).stream())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ResumeAnalysisProfile profile = buildResumeAnalysisProfile(trimmedResumeText, candidateTagSet);

        Map<String, Integer> tagWeightMap = new HashMap<>();
        addTagWeights(tagWeightMap, profile.getExtractedTags(), 6);
        addBehaviorPreferenceWeights(userId, tagWeightMap);
        String recommendedDifficulty = inferRecommendedDifficulty(userId);
        Set<Long> interactedQuestionIdSet = getInteractedQuestionIdSet(userId);

        List<QuestionRecommendationScore> scoredList = allQuestionList.stream()
                .filter(question -> !interactedQuestionIdSet.contains(question.getId()))
                .map(question -> buildResumeRecommendationScore(question, tagWeightMap, profile.getExtractedTags(), recommendedDifficulty))
                .filter(item -> item.getScore() > 0)
                .sorted(Comparator
                        .comparingInt(QuestionRecommendationScore::getScore).reversed()
                        .thenComparing(QuestionRecommendationScore::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(size)
                .collect(Collectors.toList());

        List<QuestionVO> questionVOList;
        if (CollUtil.isEmpty(scoredList)) {
            List<Question> fallbackList = allQuestionList.stream()
                    .sorted(Comparator.comparing(Question::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(size)
                    .collect(Collectors.toList());
            Map<Long, String> recommendReasonMap = fallbackList.stream()
                    .collect(Collectors.toMap(Question::getId, question -> "根据简历关键词为你补充推荐最新题目", (left, right) -> left));
            questionVOList = buildQuestionVOList(fallbackList, request, recommendReasonMap);
        } else {
            List<Question> rankedQuestionList = scoredList.stream()
                    .map(QuestionRecommendationScore::getQuestion)
                    .collect(Collectors.toList());
            Map<Long, String> recommendReasonMap = scoredList.stream()
                    .collect(Collectors.toMap(item -> item.getQuestion().getId(), QuestionRecommendationScore::getReason, (left, right) -> left));
            questionVOList = buildQuestionVOList(rankedQuestionList, request, recommendReasonMap);
        }

        result.setJobDirection(profile.getJobDirection());
        result.setExtractedTags(profile.getExtractedTags());
        result.setResumeText(trimmedResumeText);
        result.setAnalysisSummary(profile.getAnalysisSummary());
        result.setRecommendFocus(profile.getRecommendFocus());
        result.setAnalysisSource(profile.getAnalysisSource());
        result.setQuestionList(questionVOList);
        return result;
    }

    private int convertHistoryStatusToWeight(Integer status) {
        if (status == null) {
            return 1;
        }
        switch (status) {
            case 1:
                return 4;
            case 2:
                return 3;
            default:
                return 1;
        }
    }

    private void addTagWeights(Map<String, Integer> tagWeightMap, List<String> tagList, int weight) {
        if (CollUtil.isEmpty(tagList) || weight <= 0) {
            return;
        }
        for (String tag : tagList) {
            if (StringUtils.isBlank(tag)) {
                continue;
            }
            tagWeightMap.merge(tag.trim(), weight, Integer::sum);
        }
    }

    private List<String> parseTagList(String tags) {
        if (StringUtils.isBlank(tags)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(tags, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 推荐场景只取一批最新题目做候选，避免每次把整张题目表拉到应用层。
     */
    private List<Question> listRecommendationCandidateQuestions(int limit, Long excludeQuestionId) {
        int candidateLimit = Math.max(20, Math.min(limit, 500));
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(qw -> qw.eq("reviewStatus", QuestionConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
        queryWrapper.orderByDesc("updateTime");
        queryWrapper.last("limit " + (excludeQuestionId == null ? candidateLimit : candidateLimit + 1));
        List<Question> candidateList = this.list(queryWrapper);
        if (excludeQuestionId == null) {
            return candidateList;
        }
        return candidateList.stream()
                .filter(question -> !excludeQuestionId.equals(question.getId()))
                .limit(candidateLimit)
                .collect(Collectors.toList());
    }

    private QuestionRecommendationScore buildRecommendationScore(Question question, Map<String, Integer> tagWeightMap, String reasonPrefix) {
        List<String> matchedTagList = new ArrayList<>();
        int score = 0;
        for (String tag : parseTagList(question.getTags())) {
            Integer weight = tagWeightMap.get(tag);
            if (weight != null && weight > 0) {
                score += weight;
                matchedTagList.add(tag);
            }
        }
        String reason = matchedTagList.isEmpty() ? "" : reasonPrefix + "：" + String.join(" / ", matchedTagList.stream().distinct().limit(3).collect(Collectors.toList()));
        return new QuestionRecommendationScore(question, score, reason);
    }

    private QuestionRecommendationScore buildResumeRecommendationScore(Question question,
                                                                       Map<String, Integer> tagWeightMap,
                                                                       List<String> extractedTags,
                                                                       String recommendedDifficulty) {
        List<String> matchedTagList = new ArrayList<>();
        int score = 0;
        for (String tag : parseTagList(question.getTags())) {
            Integer weight = tagWeightMap.get(tag);
            if (weight != null && weight > 0) {
                score += weight;
                matchedTagList.add(tag);
            }
        }
        List<String> matchedKeywordList = new ArrayList<>();
        String normalizedQuestionText = normalizeKeyword(question.getTitle() + " " + question.getContent() + " " + question.getAnswer());
        for (String tag : extractedTags) {
            if (StringUtils.isBlank(tag) || matchedTagList.contains(tag)) {
                continue;
            }
            if (normalizedQuestionText.contains(normalizeKeyword(tag))) {
                score += 2;
                matchedKeywordList.add(tag);
            }
        }
        score += buildDifficultyScore(question.getDifficulty(), recommendedDifficulty);
        String reason = mergeRecommendationReason(
                buildResumeRecommendationReason(matchedTagList, matchedKeywordList),
                buildDifficultyReason(question.getDifficulty(), recommendedDifficulty)
        );
        return new QuestionRecommendationScore(question, score, reason);
    }

    private String buildResumeRecommendationReason(List<String> matchedTagList, List<String> matchedKeywordList) {
        List<String> reasonList = new ArrayList<>();
        if (CollUtil.isNotEmpty(matchedTagList)) {
            reasonList.add("简历技能匹配：" + String.join(" / ", matchedTagList.stream().distinct().limit(3).collect(Collectors.toList())));
        }
        if (CollUtil.isNotEmpty(matchedKeywordList)) {
            reasonList.add("内容关键词命中：" + String.join(" / ", matchedKeywordList.stream().distinct().limit(2).collect(Collectors.toList())));
        }
        return String.join("；", reasonList);
    }

    private void addBehaviorPreferenceWeights(long userId, Map<String, Integer> tagWeightMap) {
        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        List<UserQuestionHistory> historyList = userQuestionHistoryMapper.selectList(historyQueryWrapper);
        if (CollUtil.isNotEmpty(historyList)) {
            Set<Long> historyQuestionIdSet = historyList.stream()
                    .map(UserQuestionHistory::getQuestionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, Integer> historyStatusMap = historyList.stream()
                    .filter(item -> item.getQuestionId() != null)
                    .collect(Collectors.toMap(UserQuestionHistory::getQuestionId, UserQuestionHistory::getStatus, (a, b) -> Math.max(a, b)));
            if (CollUtil.isNotEmpty(historyQuestionIdSet)) {
                this.listByIds(historyQuestionIdSet).forEach(question -> {
                    int weight = Math.max(1, convertHistoryStatusToWeight(historyStatusMap.getOrDefault(question.getId(), 0)) - 1);
                    addTagWeights(tagWeightMap, parseTagList(question.getTags()), weight);
                });
            }
        }

        QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
        favourQueryWrapper.eq("userId", userId);
        List<QuestionFavour> favourList = questionFavourService.list(favourQueryWrapper);
        if (CollUtil.isNotEmpty(favourList)) {
            Set<Long> favourQuestionIdSet = favourList.stream()
                    .map(QuestionFavour::getQuestionId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (CollUtil.isNotEmpty(favourQuestionIdSet)) {
                this.listByIds(favourQuestionIdSet).forEach(question ->
                        addTagWeights(tagWeightMap, parseTagList(question.getTags()), 2));
            }
        }
    }

    private Set<Long> getInteractedQuestionIdSet(long userId) {
        return new HashSet<>(buildUserInteractionWeightMap(userId).keySet());
    }

    private Map<Long, Integer> buildUserInteractionWeightMap(long userId) {
        Map<Long, Integer> interactionWeightMap = new LinkedHashMap<>();

        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.orderByDesc("updateTime");
        historyQueryWrapper.last("limit " + USER_INTERACTION_LIMIT);
        List<UserQuestionHistory> historyList = userQuestionHistoryMapper.selectList(historyQueryWrapper);
        if (CollUtil.isNotEmpty(historyList)) {
            historyList.forEach(item -> mergeInteractionWeight(
                    interactionWeightMap,
                    item.getQuestionId(),
                    convertHistoryStatusToCollaborativeWeight(item.getStatus())
            ));
        }

        QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
        favourQueryWrapper.eq("userId", userId);
        favourQueryWrapper.orderByDesc("createTime");
        favourQueryWrapper.last("limit " + USER_INTERACTION_LIMIT);
        List<QuestionFavour> favourList = questionFavourService.list(favourQueryWrapper);
        if (CollUtil.isNotEmpty(favourList)) {
            favourList.forEach(item -> mergeInteractionWeight(interactionWeightMap, item.getQuestionId(), 4));
        }
        return interactionWeightMap;
    }

    private int convertHistoryStatusToCollaborativeWeight(Integer status) {
        if (status == null) {
            return 1;
        }
        switch (status) {
            case 1:
                return 3;
            case 2:
                return 2;
            default:
                return 1;
        }
    }

    private void mergeInteractionWeight(Map<Long, Integer> interactionWeightMap, Long questionId, int weight) {
        if (questionId == null || questionId <= 0 || weight <= 0) {
            return;
        }
        interactionWeightMap.merge(questionId, weight, Math::max);
    }

    private Map<Long, String> loadQuestionTitleMap(Set<Long> questionIdSet) {
        if (CollUtil.isEmpty(questionIdSet)) {
            return Collections.emptyMap();
        }
        return this.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(
                        Question::getId,
                        question -> StringUtils.defaultIfBlank(question.getTitle(), "未命名题目"),
                        (left, right) -> left
                ));
    }

    private Map<Long, CollaborativeRecommendationDetail> buildCollaborativeScoreMap(Map<Long, Integer> sourceQuestionWeightMap,
                                                                                    List<Question> candidateQuestionList,
                                                                                    Map<Long, String> sourceQuestionTitleMap) {
        if (sourceQuestionWeightMap == null || sourceQuestionWeightMap.isEmpty() || CollUtil.isEmpty(candidateQuestionList)) {
            return Collections.emptyMap();
        }
        Set<Long> relevantQuestionIdSet = new LinkedHashSet<>(sourceQuestionWeightMap.keySet());
        candidateQuestionList.stream()
                .map(Question::getId)
                .filter(ObjectUtils::isNotEmpty)
                .forEach(relevantQuestionIdSet::add);
        Map<Long, Map<Long, Integer>> questionUserWeightMap = buildQuestionUserWeightMap(relevantQuestionIdSet);
        if (questionUserWeightMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Double> questionRawScoreMap = new HashMap<>();
        Map<Long, Map<Long, Double>> contributionMap = new HashMap<>();
        for (Map.Entry<Long, Integer> sourceEntry : sourceQuestionWeightMap.entrySet()) {
            Long sourceQuestionId = sourceEntry.getKey();
            Map<Long, Integer> sourceVector = questionUserWeightMap.get(sourceQuestionId);
            if (sourceVector == null || sourceVector.isEmpty()) {
                continue;
            }
            int behaviorWeight = sourceEntry.getValue();
            for (Question candidateQuestion : candidateQuestionList) {
                Long candidateQuestionId = candidateQuestion.getId();
                if (candidateQuestionId == null || candidateQuestionId.equals(sourceQuestionId)) {
                    continue;
                }
                Map<Long, Integer> candidateVector = questionUserWeightMap.get(candidateQuestionId);
                if (candidateVector == null || candidateVector.isEmpty()) {
                    continue;
                }
                double similarity = calculateCosineSimilarity(sourceVector, candidateVector);
                if (similarity < MIN_COLLABORATIVE_SIMILARITY) {
                    continue;
                }
                double contribution = similarity * behaviorWeight;
                questionRawScoreMap.merge(candidateQuestionId, contribution, Double::sum);
                contributionMap.computeIfAbsent(candidateQuestionId, key -> new HashMap<>())
                        .merge(sourceQuestionId, contribution, Double::sum);
            }
        }

        if (questionRawScoreMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, CollaborativeRecommendationDetail> result = new HashMap<>();
        for (Question candidateQuestion : candidateQuestionList) {
            Long candidateQuestionId = candidateQuestion.getId();
            if (candidateQuestionId == null) {
                continue;
            }
            double rawScore = questionRawScoreMap.getOrDefault(candidateQuestionId, 0D);
            if (rawScore <= 0) {
                continue;
            }
            int normalizedScore = normalizeCollaborativeScore(rawScore);
            if (normalizedScore <= 0) {
                continue;
            }
            String reason = buildCollaborativeReason(contributionMap.get(candidateQuestionId), sourceQuestionTitleMap);
            result.put(candidateQuestionId, new CollaborativeRecommendationDetail(normalizedScore, reason));
        }
        return result;
    }

    private Map<Long, Map<Long, Integer>> buildQuestionUserWeightMap(Set<Long> questionIdSet) {
        if (CollUtil.isEmpty(questionIdSet)) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Long, Integer>> questionUserWeightMap = new HashMap<>();

        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.in("questionId", questionIdSet);
        historyQueryWrapper.select("userId", "questionId", "status");
        List<UserQuestionHistory> historyList = userQuestionHistoryMapper.selectList(historyQueryWrapper);
        if (CollUtil.isNotEmpty(historyList)) {
            historyList.forEach(item -> {
                if (item.getQuestionId() == null || item.getUserId() == null) {
                    return;
                }
                questionUserWeightMap
                        .computeIfAbsent(item.getQuestionId(), key -> new HashMap<>())
                        .merge(item.getUserId(), convertHistoryStatusToCollaborativeWeight(item.getStatus()), Math::max);
            });
        }

        QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
        favourQueryWrapper.in("questionId", questionIdSet);
        favourQueryWrapper.select("userId", "questionId");
        List<QuestionFavour> favourList = questionFavourService.list(favourQueryWrapper);
        if (CollUtil.isNotEmpty(favourList)) {
            favourList.forEach(item -> {
                if (item.getQuestionId() == null || item.getUserId() == null) {
                    return;
                }
                questionUserWeightMap
                        .computeIfAbsent(item.getQuestionId(), key -> new HashMap<>())
                        .merge(item.getUserId(), 4, Integer::sum);
            });
        }
        return questionUserWeightMap;
    }

    private double calculateCosineSimilarity(Map<Long, Integer> leftVector, Map<Long, Integer> rightVector) {
        if (leftVector == null || rightVector == null || leftVector.isEmpty() || rightVector.isEmpty()) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (Integer value : leftVector.values()) {
            if (value != null) {
                leftNorm += value * value;
            }
        }
        for (Integer value : rightVector.values()) {
            if (value != null) {
                rightNorm += value * value;
            }
        }
        if (leftNorm <= 0D || rightNorm <= 0D) {
            return 0D;
        }
        Map<Long, Integer> smallerVector = leftVector.size() <= rightVector.size() ? leftVector : rightVector;
        Map<Long, Integer> largerVector = smallerVector == leftVector ? rightVector : leftVector;
        for (Map.Entry<Long, Integer> entry : smallerVector.entrySet()) {
            Integer rightValue = largerVector.get(entry.getKey());
            if (rightValue != null && entry.getValue() != null) {
                dot += entry.getValue() * rightValue;
            }
        }
        if (dot <= 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private int normalizeCollaborativeScore(double rawScore) {
        return (int) Math.round(Math.min(rawScore, MAX_COLLABORATIVE_SCORE) * COLLABORATIVE_SCORE_SCALE);
    }

    private String buildCollaborativeReason(Map<Long, Double> sourceContributionMap, Map<Long, String> sourceQuestionTitleMap) {
        if (sourceContributionMap == null || sourceContributionMap.isEmpty()) {
            return "";
        }
        List<String> sourceTitleList = sourceContributionMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(entry -> StringUtils.abbreviate(
                        sourceQuestionTitleMap.getOrDefault(entry.getKey(), "相关题目"),
                        18
                ))
                .distinct()
                .limit(2)
                .collect(Collectors.toList());
        if (sourceTitleList.isEmpty()) {
            return "";
        }
        if (sourceTitleList.size() == 1) {
            return "协同过滤：与《" + sourceTitleList.get(0) + "》存在相似练习人群";
        }
        return "协同过滤：练过《" + sourceTitleList.get(0) + "》《" + sourceTitleList.get(1) + "》的相似用户也常练这题";
    }

    private String mergeRecommendationReason(String... reasonList) {
        return java.util.Arrays.stream(reasonList)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private int buildDifficultyScore(String questionDifficulty, String recommendedDifficulty) {
        if (StringUtils.isBlank(questionDifficulty) || StringUtils.isBlank(recommendedDifficulty)) {
            return 0;
        }
        if (StringUtils.equals(questionDifficulty, recommendedDifficulty)) {
            return 8;
        }
        return Math.abs(convertDifficultyToLevel(questionDifficulty) - convertDifficultyToLevel(recommendedDifficulty)) == 1 ? 3 : -2;
    }

    private String buildDifficultyReason(String questionDifficulty, String recommendedDifficulty) {
        if (StringUtils.isBlank(questionDifficulty) || StringUtils.isBlank(recommendedDifficulty)) {
            return "";
        }
        if (StringUtils.equals(questionDifficulty, recommendedDifficulty)) {
            return "当前更适合你的训练难度：" + recommendedDifficulty;
        }
        return "";
    }

    private String inferRecommendedDifficulty(long userId) {
        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.orderByDesc("updateTime");
        historyQueryWrapper.last("limit 40");
        List<UserQuestionHistory> historyList = userQuestionHistoryMapper.selectList(historyQueryWrapper);
        if (CollUtil.isEmpty(historyList)) {
            return QuestionConstant.DIFFICULTY_MEDIUM;
        }
        Set<Long> questionIdSet = historyList.stream()
                .map(UserQuestionHistory::getQuestionId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIdSet.isEmpty()
                ? Collections.emptyMap()
                : this.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question, (left, right) -> left));
        double totalLevel = 0D;
        int sampleCount = 0;
        long masteredCount = 0;
        long difficultCount = 0;
        for (UserQuestionHistory history : historyList) {
            Question question = questionMap.get(history.getQuestionId());
            totalLevel += convertDifficultyToLevel(question == null ? null : question.getDifficulty());
            sampleCount++;
            if (Integer.valueOf(1).equals(history.getStatus())) {
                masteredCount++;
            } else if (Integer.valueOf(2).equals(history.getStatus())) {
                difficultCount++;
            }
        }
        if (sampleCount == 0) {
            return QuestionConstant.DIFFICULTY_MEDIUM;
        }
        double avgDifficulty = totalLevel / sampleCount;
        double masteredRate = masteredCount * 1D / sampleCount;
        double difficultRate = difficultCount * 1D / sampleCount;
        if (masteredRate >= 0.55 && avgDifficulty >= 2.2) {
            return QuestionConstant.DIFFICULTY_HARD;
        }
        if (difficultRate >= 0.4 && avgDifficulty <= 2.1) {
            return QuestionConstant.DIFFICULTY_EASY;
        }
        return QuestionConstant.DIFFICULTY_MEDIUM;
    }

    private int convertDifficultyToLevel(String difficulty) {
        if (QuestionConstant.DIFFICULTY_HARD.equals(difficulty)) {
            return 3;
        }
        if (QuestionConstant.DIFFICULTY_EASY.equals(difficulty)) {
            return 1;
        }
        return 2;
    }

    private ResumeAnalysisProfile buildResumeAnalysisProfile(String resumeText, Set<String> candidateTagSet) {
        ResumeAnalysisProfile fallbackProfile = buildResumeAnalysisProfileByRule(resumeText, candidateTagSet);
        try {
            String systemPrompt = "你是一名技术面试教练，请从候选人的简历中提取岗位方向、核心技能标签和建议补强方向。"
                    + "请严格返回 JSON，不要输出额外解释。JSON 结构为："
                    + "{\"jobDirection\":\"\","
                    + "\"analysisSummary\":\"\","
                    + "\"suggestedTags\":[\"\"],"
                    + "\"recommendFocus\":\"\"}";
            String tagPoolText = candidateTagSet.stream().limit(80).collect(Collectors.joining("、"));
            String userPrompt = "候选标签池：" + tagPoolText + "\n"
                    + "请优先从标签池中选择最相关的技能标签，如果标签池不足也可以补充少量简洁标签。\n"
                    + "简历内容如下：\n" + resumeText;
            String aiResult = aiManager.doChat(systemPrompt, userPrompt);
            ResumeAnalysisProfile aiProfile = parseResumeAnalysisProfile(aiResult);
            if (CollUtil.isEmpty(aiProfile.getExtractedTags())) {
                aiProfile.setExtractedTags(fallbackProfile.getExtractedTags());
            }
            if (StringUtils.isBlank(aiProfile.getJobDirection())) {
                aiProfile.setJobDirection(fallbackProfile.getJobDirection());
            }
            if (StringUtils.isBlank(aiProfile.getAnalysisSummary())) {
                aiProfile.setAnalysisSummary(fallbackProfile.getAnalysisSummary());
            }
            if (StringUtils.isBlank(aiProfile.getRecommendFocus())) {
                aiProfile.setRecommendFocus(fallbackProfile.getRecommendFocus());
            }
            if (StringUtils.isBlank(aiProfile.getAnalysisSource())) {
                aiProfile.setAnalysisSource("AI 智能解析");
            }
            return aiProfile;
        } catch (Exception e) {
            log.info("简历 AI 解析失败，改用规则分析：{}", e.getMessage());
            return fallbackProfile;
        }
    }

    private ResumeAnalysisProfile parseResumeAnalysisProfile(String aiResult) {
        String jsonText = extractJsonText(aiResult);
        JSONObject jsonObject = JSONUtil.parseObj(jsonText);
        ResumeAnalysisProfile profile = new ResumeAnalysisProfile();
        profile.setJobDirection(jsonObject.getStr("jobDirection"));
        profile.setAnalysisSummary(jsonObject.getStr("analysisSummary"));
        profile.setRecommendFocus(jsonObject.getStr("recommendFocus"));
        profile.setAnalysisSource("AI 智能解析");
        profile.setExtractedTags(parseJsonStringList(jsonObject.get("suggestedTags")));
        return profile;
    }

    private ResumeAnalysisProfile buildResumeAnalysisProfileByRule(String resumeText, Set<String> candidateTagSet) {
        String normalizedResumeText = normalizeKeyword(resumeText);
        LinkedHashSet<String> matchedTagSet = new LinkedHashSet<>();
        for (String tag : candidateTagSet) {
            if (StringUtils.isBlank(tag)) {
                continue;
            }
            String normalizedTag = normalizeKeyword(tag);
            if (StringUtils.isNotBlank(normalizedTag) && normalizedResumeText.contains(normalizedTag)) {
                matchedTagSet.add(tag.trim());
            }
        }

        Map<String, List<String>> aliasMap = buildTagAliasMap();
        for (Map.Entry<String, List<String>> entry : aliasMap.entrySet()) {
            for (String alias : entry.getValue()) {
                if (normalizedResumeText.contains(normalizeKeyword(alias))) {
                    matchedTagSet.add(entry.getKey());
                    break;
                }
            }
        }

        List<String> extractedTags = matchedTagSet.stream().limit(8).collect(Collectors.toList());
        String jobDirection = inferJobDirection(extractedTags);
        String recommendFocus = extractedTags.isEmpty()
                ? "建议先补充目标岗位、项目经历和核心技术栈描述，系统才能给出更精准的推荐。"
                : "建议围绕 " + String.join(" / ", extractedTags.stream().limit(4).collect(Collectors.toList())) + " 做专项强化。";
        String analysisSummary = extractedTags.isEmpty()
                ? "系统暂未从简历中提取到明确技能关键词，先按通用高频面试题为你兜底推荐。"
                : "系统识别你更偏向 " + jobDirection + "，并从简历中提取出 " + String.join(" / ", extractedTags.stream().limit(5).collect(Collectors.toList())) + " 等技能关键词。";

        ResumeAnalysisProfile profile = new ResumeAnalysisProfile();
        profile.setJobDirection(jobDirection);
        profile.setExtractedTags(extractedTags);
        profile.setAnalysisSummary(analysisSummary);
        profile.setRecommendFocus(recommendFocus);
        profile.setAnalysisSource("系统规则分析");
        return profile;
    }

    private Map<String, List<String>> buildTagAliasMap() {
        Map<String, List<String>> aliasMap = new LinkedHashMap<>();
        aliasMap.put("Java", List.of("java", "jvm", "spring", "spring boot", "mybatis"));
        aliasMap.put("Spring Boot", List.of("spring boot", "springboot", "spring cloud"));
        aliasMap.put("MySQL", List.of("mysql", "sql", "innodb"));
        aliasMap.put("Redis", List.of("redis", "缓存", "cache"));
        aliasMap.put("消息队列", List.of("kafka", "rocketmq", "rabbitmq", "消息队列"));
        aliasMap.put("微服务", List.of("微服务", "spring cloud", "nacos", "gateway"));
        aliasMap.put("Linux", List.of("linux", "shell", "centos", "ubuntu"));
        aliasMap.put("Docker", List.of("docker", "容器"));
        aliasMap.put("前端", List.of("react", "vue", "next.js", "javascript", "typescript", "css", "html"));
        aliasMap.put("React", List.of("react", "react hooks", "next.js"));
        aliasMap.put("Vue", List.of("vue", "vue3", "nuxt"));
        aliasMap.put("JavaScript", List.of("javascript", "js", "es6"));
        aliasMap.put("TypeScript", List.of("typescript", "ts"));
        aliasMap.put("Python", List.of("python", "django", "flask", "fastapi"));
        aliasMap.put("算法", List.of("算法", "数据结构", "leetcode", "复杂度"));
        aliasMap.put("计算机网络", List.of("tcp", "udp", "http", "https", "网络"));
        aliasMap.put("操作系统", List.of("操作系统", "进程", "线程", "内存管理"));
        return aliasMap;
    }

    private String inferJobDirection(List<String> extractedTags) {
        List<String> lowerTagList = extractedTags.stream()
                .map(tag -> tag == null ? "" : tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        if (lowerTagList.stream().anyMatch(tag -> tag.contains("react") || tag.contains("vue") || tag.contains("javascript") || tag.contains("typescript") || tag.contains("前端"))) {
            return "前端开发";
        }
        if (lowerTagList.stream().anyMatch(tag -> tag.contains("python") || tag.contains("算法"))) {
            return "算法 / AI 岗位";
        }
        if (lowerTagList.stream().anyMatch(tag -> tag.contains("java") || tag.contains("spring") || tag.contains("redis") || tag.contains("mysql") || tag.contains("微服务"))) {
            return "Java 后端开发";
        }
        if (lowerTagList.stream().anyMatch(tag -> tag.contains("测试") || tag.contains("qa"))) {
            return "测试开发";
        }
        return "综合技术岗位";
    }

    private String normalizeKeyword(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-./()（）:：,，;；]+", "");
    }

    private String extractJsonText(String text) {
        if (StringUtils.isBlank(text)) {
            return "{}";
        }
        String cleanedText = text.trim();
        if (cleanedText.startsWith("```")) {
            cleanedText = cleanedText.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = cleanedText.indexOf('{');
        int end = cleanedText.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleanedText.substring(start, end + 1);
        }
        return cleanedText;
    }

    private List<String> parseJsonStringList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof JSONArray jsonArray) {
            return jsonArray.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        }
        String text = String.valueOf(value);
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(text.split("[,，/、\\s]+"))
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private static class QuestionRecommendationScore {
        private final Question question;
        private final int score;
        private final String reason;

        private QuestionRecommendationScore(Question question, int score, String reason) {
            this.question = question;
            this.score = score;
            this.reason = reason;
        }

        public Question getQuestion() {
            return question;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }

        public java.util.Date getUpdateTime() {
            return question.getUpdateTime();
        }
    }

    private static class ResumeAnalysisProfile {
        private String jobDirection;
        private List<String> extractedTags = Collections.emptyList();
        private String analysisSummary;
        private String recommendFocus;
        private String analysisSource;

        public String getJobDirection() {
            return jobDirection;
        }

        public void setJobDirection(String jobDirection) {
            this.jobDirection = jobDirection;
        }

        public List<String> getExtractedTags() {
            return extractedTags;
        }

        public void setExtractedTags(List<String> extractedTags) {
            this.extractedTags = extractedTags == null ? Collections.emptyList() : extractedTags.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        }

        public String getAnalysisSummary() {
            return analysisSummary;
        }

        public void setAnalysisSummary(String analysisSummary) {
            this.analysisSummary = analysisSummary;
        }

        public String getRecommendFocus() {
            return recommendFocus;
        }

        public void setRecommendFocus(String recommendFocus) {
            this.recommendFocus = recommendFocus;
        }

        public String getAnalysisSource() {
            return analysisSource;
        }

        public void setAnalysisSource(String analysisSource) {
            this.analysisSource = analysisSource;
        }
    }

    private static class CollaborativeRecommendationDetail {
        private final int score;
        private final String reason;

        private CollaborativeRecommendationDetail(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }

    @Override
    public boolean canViewQuestion(Question question, User loginUser) {
        if (question == null) {
            return false;
        }
        Integer reviewStatus = question.getReviewStatus();
        if (reviewStatus == null || QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus) {
            return true;
        }
        if (loginUser == null) {
            return false;
        }
        return userService.isAdmin(loginUser) || loginUser.getId().equals(question.getUserId());
    }

    @Override
    public void syncQuestionToEs(Question question) {
        if (question == null || question.getId() == null) {
            return;
        }
        try {
            QuestionEsDTO questionEsDTO = QuestionEsDTO.objToDto(question);
            questionEsDao.save(questionEsDTO);
            esSyncTaskService.clearTask("question", question.getId());
        } catch (Exception e) {
            log.error("sync question to es error, questionId={}", question.getId(), e);
            esSyncTaskService.recordUpsertFailure("question", question.getId(),
                    JSONUtil.toJsonStr(QuestionEsDTO.objToDto(question)), e);
        }
    }

    @Override
    public void deleteQuestionFromEs(Long questionId) {
        if (questionId == null || questionId <= 0) {
            return;
        }
        try {
            questionEsDao.deleteById(questionId);
            esSyncTaskService.clearTask("question", questionId);
        } catch (Exception e) {
            log.error("delete question from es error, questionId={}", questionId, e);
            esSyncTaskService.recordDeleteFailure("question", questionId, e);
        }
    }
}
