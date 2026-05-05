package com.xduo.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.DeleteRequest;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.QuestionConstant;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.SystemAccessManager;
import com.xduo.springbootinit.manager.QuestionAiGenerateTaskManager;
import com.xduo.springbootinit.mapper.CounterManager;
import com.xduo.springbootinit.model.dto.question.*;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.QuestionAnswerEvaluateVO;
import com.xduo.springbootinit.model.vo.QuestionVO;
import com.xduo.springbootinit.model.vo.ResumeQuestionRecommendVO;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.QuestionRecommendLogService;
import com.xduo.springbootinit.service.QuestionSearchLogService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.SecurityAlertService;
import com.xduo.springbootinit.service.TagSyncService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.NetUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import com.xduo.springbootinit.service.UserQuestionHistoryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.xduo.springbootinit.manager.AiManager;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    private static final long MAX_RESUME_FILE_SIZE = 2 * 1024 * 1024L;
    private static final long MAX_AUDIO_FILE_SIZE = 8 * 1024 * 1024L;
    private static final int MAX_ANSWER_EVALUATE_LENGTH = 5000;
    private static final int AI_GENERATE_QUESTION_BATCH_SIZE = 4;
    private static final int AI_GENERATE_QUESTION_MAX_ROUNDS = 6;
    private static final int AI_GENERATE_ANSWER_BATCH_SIZE = 3;
    private static final int MIN_GENERATED_QUESTION_CONTENT_LENGTH = 28;
    private static final int MIN_GENERATED_ANSWER_LENGTH = 320;
    private static final int MIN_HIGH_CONFIDENCE_GENERATED_ANSWER_LENGTH = 560;
    private static final int MIN_GENERATED_ANSWER_REVIEW_SCORE = 85;
    private static final int MAX_GENERATED_TAGS = 5;
    private static final Set<String> SUPPORTED_RESUME_FILE_SUFFIX_SET = Set.of("txt", "md", "markdown", "docx", "pdf");
    private static final Set<String> SUPPORTED_AUDIO_FILE_SUFFIX_SET = Set.of("webm", "wav", "mp3", "m4a", "mp4", "ogg", "oga");

    @Resource
    private AiManager aiManager;

    @Resource
    private QuestionService questionService;

    @Resource
    private UserQuestionHistoryService userQuestionHistoryService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionSearchLogService questionSearchLogService;

    @Resource
    private SecurityAlertService securityAlertService;

    @Resource
    private CounterManager counterManager;

    @Resource
    private NotificationService notificationService;

    @Resource
    private QuestionRecommendLogService questionRecommendLogService;

    @Resource
    private TagSyncService tagSyncService;

    @Resource
    private QuestionAiGenerateTaskManager questionAiGenerateTaskManager;

    @Resource
    private SystemAccessManager systemAccessManager;

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest,
                                          HttpServletRequest request) {
        //参数校验(非空)
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        //构建实例
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        //封装Tag
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, true);
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        question.setReviewStatus(userService.isAdmin(loginUser)
                ? QuestionConstant.REVIEW_STATUS_APPROVED
                : QuestionConstant.REVIEW_STATUS_PRIVATE);
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        questionService.syncQuestionToEs(question);
        tagSyncService.syncQuestionTags(null, question.getTags());
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        questionService.batchDeleteQuestions(List.of(id));
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        Question latestQuestion = questionService.getById(id);
        questionService.syncQuestionToEs(latestQuestion);
        tagSyncService.syncQuestionTags(oldQuestion.getTags(), latestQuestion == null ? null : latestQuestion.getTags());
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestQuestionAccessAllowed(request);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUserPermitNull(request);
        ThrowUtils.throwIf(!questionService.canViewQuestion(question, loginUser), ErrorCode.NOT_FOUND_ERROR);
        if (loginUser != null && QuestionConstant.REVIEW_STATUS_APPROVED == getQuestionReviewStatus(question)) {
            crawlerDetect(loginUser, request);
            userQuestionHistoryService.recordQuestionView(loginUser.getId(), id);
        }
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 获取个性化推荐题目
     */
    @GetMapping("/recommend/personal")
    public BaseResponse<List<QuestionVO>> listPersonalRecommendQuestionVO(Long questionId,
                                                                          @RequestParam(defaultValue = "6") Integer size,
                                                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<QuestionVO> questionVOList = questionService.listRecommendQuestionVOByUser(loginUser.getId(), questionId, size, request);
        logRecommendationExposure(loginUser.getId(), "personal", questionVOList);
        return ResultUtils.success(questionVOList);
    }

    /**
     * 获取相关题目推荐
     */
    @GetMapping("/recommend/related")
    public BaseResponse<List<QuestionVO>> listRelatedQuestionVO(@RequestParam Long questionId,
                                                                @RequestParam(defaultValue = "6") Integer size,
                                                                HttpServletRequest request) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestQuestionAccessAllowed(request);
        User loginUser = userService.getLoginUserPermitNull(request);
        List<QuestionVO> questionVOList = questionService.listRelatedQuestionVO(questionId, size, request);
        logRecommendationExposure(loginUser == null ? null : loginUser.getId(), "related", questionVOList);
        return ResultUtils.success(questionVOList);
    }

    /**
     * 基于简历内容推荐题目
     */
    @PostMapping("/recommend/resume")
    public BaseResponse<ResumeQuestionRecommendVO> recommendQuestionsByResume(@RequestBody QuestionResumeRecommendRequest resumeRecommendRequest,
                                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(resumeRecommendRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int size = resumeRecommendRequest.getSize() == null ? 6 : resumeRecommendRequest.getSize();
        ResumeQuestionRecommendVO result = questionService.recommendQuestionsByResume(
                loginUser.getId(),
                resumeRecommendRequest.getResumeText(),
                size,
                request
        );
        logRecommendationExposure(loginUser.getId(), "resume", result.getQuestionList());
        return ResultUtils.success(result);
    }

    /**
     * 基于上传简历文件推荐题目
     */
    @PostMapping("/recommend/resume/file")
    public BaseResponse<ResumeQuestionRecommendVO> recommendQuestionsByResumeFile(@RequestPart("file") MultipartFile multipartFile,
                                                                                  @RequestParam(required = false) Integer size,
                                                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "请先上传简历文件");
        User loginUser = userService.getLoginUser(request);
        String resumeText = extractResumeText(multipartFile);
        ResumeQuestionRecommendVO result = questionService.recommendQuestionsByResume(
                loginUser.getId(),
                resumeText,
                size == null ? 6 : size,
                request
        );
        logRecommendationExposure(loginUser.getId(), "resume", result.getQuestionList());
        return ResultUtils.success(result);
    }

    /**
     * AI 判题与答题反馈
     */
    @PostMapping("/ai/evaluate")
    public BaseResponse<QuestionAnswerEvaluateVO> evaluateQuestionAnswer(@RequestBody QuestionAnswerEvaluateRequest evaluateRequest,
                                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(evaluateRequest == null || evaluateRequest.getQuestionId() == null || evaluateRequest.getQuestionId() <= 0,
                ErrorCode.PARAMS_ERROR);
        String answerContent = StringUtils.trimToEmpty(evaluateRequest.getAnswerContent());
        ThrowUtils.throwIf(answerContent.length() < 5, ErrorCode.PARAMS_ERROR, "回答内容至少需要 5 个字符");
        ThrowUtils.throwIf(answerContent.length() > MAX_ANSWER_EVALUATE_LENGTH,
                ErrorCode.PARAMS_ERROR,
                "回答内容不能超过 5000 个字符");
        User loginUser = userService.getLoginUser(request);
        Question question = questionService.getById(evaluateRequest.getQuestionId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!questionService.canViewQuestion(question, loginUser), ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(buildQuestionAnswerEvaluateVO(question, answerContent));
    }

    /**
     * 语音答题后直接 AI 判题
     */
    @PostMapping(value = "/ai/evaluate/audio", consumes = "multipart/form-data")
    public BaseResponse<QuestionAnswerEvaluateVO> evaluateQuestionAnswerByAudio(@RequestParam("questionId") Long questionId,
                                                                                @RequestPart("file") MultipartFile audioFile,
                                                                                HttpServletRequest request) {
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目不存在");
        ThrowUtils.throwIf(audioFile == null || audioFile.isEmpty(), ErrorCode.PARAMS_ERROR, "请先录制语音后再判题");
        User loginUser = userService.getLoginUser(request);
        Question question = questionService.getById(questionId);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!questionService.canViewQuestion(question, loginUser), ErrorCode.NOT_FOUND_ERROR);
        String transcript = extractAudioAnswerText(audioFile, question);
        ThrowUtils.throwIf(transcript.length() < 5, ErrorCode.PARAMS_ERROR, "语音内容过短，请重新录制");
        QuestionAnswerEvaluateVO evaluateVO = buildQuestionAnswerEvaluateVO(question, transcript);
        evaluateVO.setTranscript(transcript);
        return ResultUtils.success(evaluateVO);
    }

    @PostMapping("/recommend/click")
    public BaseResponse<Boolean> logRecommendClick(@RequestBody QuestionRecommendClickRequest clickRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(clickRequest == null || clickRequest.getQuestionId() == null || clickRequest.getQuestionId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUserPermitNull(request);
        questionRecommendLogService.logClick(loginUser == null ? null : loginUser.getId(), clickRequest.getSource(), clickRequest.getQuestionId());
        return ResultUtils.success(true);
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 100, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestQuestionAccessAllowed(request);
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Entry entry = null;
        String remoteAddr = request.getRemoteAddr();
        try {
            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, remoteAddr);
            // 被保护的业务逻辑

            // 限制爬虫
            ThrowUtils.throwIf(current < 1 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR, "分页参数不合法");
            if (!userService.isAdmin(request)) {
                questionQueryRequest.setReviewStatus(QuestionConstant.REVIEW_STATUS_APPROVED);
            }
            // 查询数据库
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch (Throwable ex) {
            // 业务异常
            if (!BlockException.isBlockException(ex)) {
                if (ex instanceof BusinessException businessException) {
                    throw businessException;
                }
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }
            // 降级操作
            if (ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }
            // 限流操作
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
        } finally {
            if (entry != null) {
                entry.exit(1, remoteAddr);
            }
        }
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest,
                                              HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean isAdmin = userService.isAdmin(loginUser);
        int oldReviewStatus = getQuestionReviewStatus(oldQuestion);
        LambdaUpdateWrapper<Question> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Question::getId, id)
                .set(Question::getTitle, question.getTitle())
                .set(Question::getContent, question.getContent())
                .set(Question::getTags, question.getTags())
                .set(Question::getAnswer, question.getAnswer())
                .set(Question::getDifficulty, question.getDifficulty());
        if (!isAdmin) {
            if (QuestionConstant.REVIEW_STATUS_APPROVED == oldReviewStatus) {
                updateWrapper.set(Question::getReviewStatus, QuestionConstant.REVIEW_STATUS_PENDING)
                        .set(Question::getReviewMessage, null)
                        .set(Question::getReviewUserId, null)
                        .set(Question::getReviewTime, null);
            }
        }
        boolean result = questionService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        Question latestQuestion = questionService.getById(id);
        questionService.syncQuestionToEs(latestQuestion);
        tagSyncService.syncQuestionTags(oldQuestion.getTags(), latestQuestion == null ? null : latestQuestion.getTags());
        return ResultUtils.success(true);
    }

    /**
     * 用户主动提交题目审核
     */
    @PostMapping("/submit/review")
    public BaseResponse<Boolean> submitQuestionReview(@RequestBody QuestionSubmitReviewRequest submitReviewRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(submitReviewRequest == null || submitReviewRequest.getId() == null || submitReviewRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Question oldQuestion = questionService.getById(submitReviewRequest.getId());
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        int reviewStatus = getQuestionReviewStatus(oldQuestion);
        ThrowUtils.throwIf(QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus, ErrorCode.OPERATION_ERROR, "题目已公开，无需重复提交审核");
        ThrowUtils.throwIf(QuestionConstant.REVIEW_STATUS_PENDING == reviewStatus, ErrorCode.OPERATION_ERROR, "题目已在审核中，请耐心等待");
        LambdaUpdateWrapper<Question> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Question::getId, oldQuestion.getId())
                .set(Question::getReviewStatus, QuestionConstant.REVIEW_STATUS_PENDING)
                .set(Question::getReviewMessage, null)
                .set(Question::getReviewUserId, null)
                .set(Question::getReviewTime, null);
        boolean result = questionService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        Question latestQuestion = questionService.getById(oldQuestion.getId());
        questionService.syncQuestionToEs(latestQuestion);
        tagSyncService.syncQuestionTags(oldQuestion.getTags(), latestQuestion == null ? null : latestQuestion.getTags());
        return ResultUtils.success(true);
    }

    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestQuestionAccessAllowed(request);
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        if (!userService.isAdmin(request)) {
            questionQueryRequest.setReviewStatus(QuestionConstant.REVIEW_STATUS_APPROVED);
        }
        Page<Question> questionPage;
        boolean fallbackToDb = false;
        if (shouldSearchQuestionFromEs(questionQueryRequest)) {
            try {
                questionPage = questionService.searchFromEs(questionQueryRequest);
                if (questionPage.getTotal() == 0) {
                    questionPage = questionService.listQuestionByPage(questionQueryRequest);
                    fallbackToDb = true;
                    syncQuestionPageToEs(questionPage);
                }
            } catch (Exception e) {
                log.warn("题目搜索 ES 不可用，已降级到数据库查询，searchText={}", questionQueryRequest.getSearchText(), e);
                questionPage = questionService.listQuestionByPage(questionQueryRequest);
                fallbackToDb = true;
                syncQuestionPageToEs(questionPage);
            }
        } else {
            questionPage = questionService.listQuestionByPage(questionQueryRequest);
        }
        if (StringUtils.isNotBlank(questionQueryRequest.getSearchText())) {
            try {
                User loginUser = userService.getLoginUserPermitNull(request);
                questionSearchLogService.recordSearch(
                        loginUser == null ? null : loginUser.getId(),
                        questionQueryRequest.getSearchText(),
                        questionPage.getTotal(),
                        fallbackToDb ? "question_fallback" : "question",
                        NetUtils.getIpAddress(request)
                );
            } catch (Exception e) {
                log.warn("记录题目搜索日志失败，searchText={}", questionQueryRequest.getSearchText(), e);
            }
        }
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    private boolean shouldSearchQuestionFromEs(QuestionQueryRequest questionQueryRequest) {
        return StringUtils.isNotBlank(questionQueryRequest.getSearchText())
                || StringUtils.isNotBlank(questionQueryRequest.getTitle())
                || StringUtils.isNotBlank(questionQueryRequest.getContent())
                || StringUtils.isNotBlank(questionQueryRequest.getAnswer());
    }

    private void syncQuestionPageToEs(Page<Question> questionPage) {
        if (questionPage == null || questionPage.getRecords() == null || questionPage.getRecords().isEmpty()) {
            return;
        }
        for (Question question : questionPage.getRecords()) {
            try {
                questionService.syncQuestionToEs(question);
            } catch (Exception e) {
                log.warn("搜索兜底后补写题目到 ES 失败，questionId={}", question == null ? null : question.getId(), e);
            }
        }
    }

    /**
     * 审核题目（仅管理员）
     */
    @PostMapping("/review")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewQuestion(@RequestBody QuestionReviewRequest questionReviewRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(questionReviewRequest == null || questionReviewRequest.getId() == null || questionReviewRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Integer reviewStatus = questionReviewRequest.getReviewStatus();
        ThrowUtils.throwIf(reviewStatus == null || !QuestionConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(reviewStatus),
                ErrorCode.PARAMS_ERROR,
                "审核状态不合法");
        String reviewMessage = StringUtils.trimToNull(questionReviewRequest.getReviewMessage());
        if (QuestionConstant.REVIEW_STATUS_REJECTED == reviewStatus) {
            ThrowUtils.throwIf(StringUtils.isBlank(reviewMessage), ErrorCode.PARAMS_ERROR, "驳回时请填写审核意见");
        }
        ThrowUtils.throwIf(StringUtils.length(reviewMessage) > 512, ErrorCode.PARAMS_ERROR, "审核意见过长");
        Question oldQuestion = questionService.getById(questionReviewRequest.getId());
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(QuestionConstant.REVIEW_STATUS_PRIVATE == getQuestionReviewStatus(oldQuestion),
                ErrorCode.OPERATION_ERROR,
                "作者尚未提交公开审核");

        User loginUser = userService.getLoginUser(request);
        LambdaUpdateWrapper<Question> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Question::getId, oldQuestion.getId())
                .set(Question::getReviewStatus, reviewStatus)
                .set(Question::getReviewMessage, reviewMessage)
                .set(Question::getReviewUserId, loginUser.getId())
                .set(Question::getReviewTime, new java.util.Date());
        boolean result = questionService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        Question latestQuestion = questionService.getById(oldQuestion.getId());
        questionService.syncQuestionToEs(latestQuestion);
        tagSyncService.syncQuestionTags(oldQuestion.getTags(), latestQuestion == null ? null : latestQuestion.getTags());
        notificationService.sendNotification(
                latestQuestion.getUserId(),
                QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus ? "你的题目已审核通过" : "你的题目未通过审核",
                buildQuestionReviewNotificationContent(latestQuestion, reviewStatus, reviewMessage),
                "question_review",
                latestQuestion.getId()
        );
        return ResultUtils.success(true);
    }

    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        return ResultUtils.success(true);
    }

    /**
     * AI 批量生成题目（异步任务）
     *
     * @param questionAiGenerateRequest
     * @param request
     * @return
     */
    @PostMapping("/ai/generate/question/task")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<QuestionAiGenerateTaskManager.AiGenerateTaskStatus> startGenerateQuestionsTask(
            @RequestBody QuestionAIGenerateRequest questionAiGenerateRequest,
            HttpServletRequest request) {
        validateAiGenerateRequest(questionAiGenerateRequest);
        User loginUser = userService.getLoginUser(request);
        String questionType = questionAiGenerateRequest.getQuestionType().trim();
        int targetCount = questionAiGenerateRequest.getNumber();
        QuestionAiGenerateTaskManager.AiGenerateTaskStatus taskStatus =
                questionAiGenerateTaskManager.createTask(loginUser.getId(), questionType, targetCount);
        CompletableFuture.runAsync(() -> runGenerateQuestionsTask(taskStatus.getTaskId(), questionType, targetCount, loginUser.getId()));
        return ResultUtils.success(taskStatus);
    }

    /**
     * 查询 AI 批量生成题目任务状态
     *
     * @param taskId
     * @param request
     * @return
     */
    @GetMapping("/ai/generate/question/task")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<QuestionAiGenerateTaskManager.AiGenerateTaskStatus> getGenerateQuestionsTask(
            @RequestParam String taskId,
            HttpServletRequest request) {
        ThrowUtils.throwIf(StringUtils.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        QuestionAiGenerateTaskManager.AiGenerateTaskStatus taskStatus = questionAiGenerateTaskManager.getTask(taskId);
        ThrowUtils.throwIf(taskStatus == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在或已过期");
        ThrowUtils.throwIf(
                taskStatus.getCreatorUserId() != null
                        && !Objects.equals(taskStatus.getCreatorUserId(), loginUser.getId())
                        && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR
        );
        return ResultUtils.success(taskStatus);
    }

    /**
     * AI 批量生成题目
     *
     * @param questionAiGenerateRequest
     * @return
     */
    @PostMapping("/ai/generate/question")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> batchGenerateQuestionsByAi(@RequestBody QuestionAIGenerateRequest questionAiGenerateRequest,
                                                            HttpServletRequest request) {
        validateAiGenerateRequest(questionAiGenerateRequest);
        User loginUser = userService.getLoginUser(request);
        String questionType = questionAiGenerateRequest.getQuestionType().trim();
        int targetCount = questionAiGenerateRequest.getNumber();

        try {
            int successCount = generateAndPersistQuestionsByAi(questionType, targetCount, loginUser.getId(), null);
            ThrowUtils.throwIf(successCount == 0, ErrorCode.SYSTEM_ERROR, "AI 未生成可保存的题目");
            return ResultUtils.success(successCount);
        } catch (Exception e) {
            log.error("AI 结果解析失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成数据格式不正确");
        }
    }

    private void validateAiGenerateRequest(QuestionAIGenerateRequest questionAiGenerateRequest) {
        ThrowUtils.throwIf(questionAiGenerateRequest == null, ErrorCode.PARAMS_ERROR);
        String questionType = questionAiGenerateRequest.getQuestionType();
        Integer number = questionAiGenerateRequest.getNumber();
        ThrowUtils.throwIf(StringUtils.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题目方向不能为空");
        ThrowUtils.throwIf(number == null || number < 1 || number > 20, ErrorCode.PARAMS_ERROR, "生成数量需在 1 到 20 之间");
    }

    private void runGenerateQuestionsTask(String taskId, String questionType, int targetCount, Long userId) {
        int successCount = 0;
        int failedCount = 0;
        try {
            questionAiGenerateTaskManager.markRunning(taskId, "正在准备生成任务");
            successCount = generateAndPersistQuestionsByAi(questionType, targetCount, userId, taskId);
            failedCount = Math.max(0, targetCount - successCount);
            ThrowUtils.throwIf(successCount <= 0, ErrorCode.SYSTEM_ERROR, "AI 未生成出可保存的题目");
            String successMessage = successCount >= targetCount
                    ? String.format("已完成，成功生成 %d/%d 道题目", successCount, targetCount)
                    : String.format("已完成，成功生成 %d/%d 道题目。建议缩小方向后再补一次", successCount, targetCount);
            questionAiGenerateTaskManager.markSuccess(taskId, successCount, failedCount, successMessage);
        } catch (Exception e) {
            log.error("AI 异步增题任务失败，taskId={}, questionType={}", taskId, questionType, e);
            String errorMessage = StringUtils.defaultIfBlank(e.getMessage(), "AI 增题失败，请稍后重试");
            questionAiGenerateTaskManager.markFailed(taskId, successCount, failedCount, errorMessage);
        }
    }

    private int generateAndPersistQuestionsByAi(String questionType, int targetCount, Long userId, String taskId) {
        List<AiGeneratedQuestion> acceptedQuestionList = new ArrayList<>();
        int successCount = 0;
        for (int round = 0; round < AI_GENERATE_QUESTION_MAX_ROUNDS && successCount < targetCount; round++) {
            int remaining = targetCount - successCount;
            int requestCount = Math.min(AI_GENERATE_QUESTION_BATCH_SIZE, Math.max(1, remaining + 1));
            try {
                updateGenerateTaskHeartbeat(taskId, String.format(
                        "正在生成第 %d/%d 到第 %d/%d 道题的题干",
                        successCount + 1,
                        targetCount,
                        Math.min(targetCount, successCount + requestCount),
                        targetCount
                ));
                String result = aiManager.doChat(
                        buildQuestionGenerationSystemPrompt(),
                        buildQuestionGenerationUserPrompt(questionType, requestCount, acceptedQuestionList)
                );
                List<AiGeneratedQuestion> parsedQuestionList = parseAiGeneratedQuestions(result);
                List<AiGeneratedQuestion> normalizedQuestionList = normalizeAndFilterGeneratedQuestions(
                        parsedQuestionList,
                        questionType,
                        acceptedQuestionList
                );
                if (normalizedQuestionList.isEmpty()) {
                    continue;
                }
                updateGenerateTaskHeartbeat(taskId, String.format(
                        "正在补全第 %d/%d 到第 %d/%d 道题的参考答案",
                        successCount + 1,
                        targetCount,
                        Math.min(targetCount, successCount + normalizedQuestionList.size()),
                        targetCount
                ));
                enrichGeneratedQuestionAnswers(normalizedQuestionList);
                for (AiGeneratedQuestion generatedQuestion : normalizedQuestionList) {
                    if (successCount >= targetCount) {
                        break;
                    }
                    if (!isSavableGeneratedQuestion(generatedQuestion)) {
                        continue;
                    }
                    Question question = buildGeneratedQuestionEntity(generatedQuestion, userId);
                    questionService.validQuestion(question, true);
                    questionService.save(question);
                    questionService.syncQuestionToEs(question);
                    tagSyncService.syncQuestionTags(null, question.getTags());
                    acceptedQuestionList.add(generatedQuestion);
                    successCount++;
                    updateGenerateTaskProgress(taskId, successCount, targetCount);
                }
            } catch (Exception e) {
                log.warn("AI 题目异步生成失败，questionType={}, round={}", questionType, round + 1, e);
                updateGenerateTaskHeartbeat(taskId, "本轮生成遇到波动，正在尝试下一轮补齐");
            }
        }
        return successCount;
    }

    private boolean isSavableGeneratedQuestion(AiGeneratedQuestion generatedQuestion) {
        return generatedQuestion != null
                && StringUtils.isNoneBlank(generatedQuestion.getTitle(), generatedQuestion.getContent(), generatedQuestion.getAnswer());
    }

    private Question buildGeneratedQuestionEntity(AiGeneratedQuestion generatedQuestion, Long userId) {
        Question question = new Question();
        question.setTitle(generatedQuestion.getTitle().trim());
        question.setContent(generatedQuestion.getContent().trim());
        question.setAnswer(generatedQuestion.getAnswer().trim());
        question.setDifficulty(normalizeGeneratedDifficulty(generatedQuestion.getDifficulty()));
        if (generatedQuestion.getTags() != null) {
            question.setTags(JSONUtil.toJsonStr(generatedQuestion.getTags()));
        }
        question.setUserId(userId);
        question.setReviewStatus(QuestionConstant.REVIEW_STATUS_APPROVED);
        return question;
    }

    private void updateGenerateTaskHeartbeat(String taskId, String message) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        questionAiGenerateTaskManager.heartbeat(taskId, message);
    }

    private void updateGenerateTaskProgress(String taskId, int successCount, int targetCount) {
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        String message = successCount >= targetCount
                ? String.format("已生成 %d/%d，正在收尾", successCount, targetCount)
                : String.format("已生成 %d/%d，正在继续生成下一道", successCount, targetCount);
        questionAiGenerateTaskManager.markProgress(taskId, successCount, 0, message);
    }

    private List<AiGeneratedQuestion> generateQuestionSkeletons(String questionType, int targetCount) {
        List<AiGeneratedQuestion> acceptedQuestionList = new ArrayList<>();
        for (int round = 0; round < AI_GENERATE_QUESTION_MAX_ROUNDS && acceptedQuestionList.size() < targetCount; round++) {
            int remaining = targetCount - acceptedQuestionList.size();
            int requestCount = Math.min(AI_GENERATE_QUESTION_BATCH_SIZE, Math.max(1, remaining + 1));
            try {
                String result = aiManager.doChat(
                        buildQuestionGenerationSystemPrompt(),
                        buildQuestionGenerationUserPrompt(questionType, requestCount, acceptedQuestionList)
                );
                List<AiGeneratedQuestion> parsedQuestionList = parseAiGeneratedQuestions(result);
                List<AiGeneratedQuestion> normalizedQuestionList = normalizeAndFilterGeneratedQuestions(
                        parsedQuestionList,
                        questionType,
                        acceptedQuestionList
                );
                for (AiGeneratedQuestion question : normalizedQuestionList) {
                    if (acceptedQuestionList.size() >= targetCount) {
                        break;
                    }
                    acceptedQuestionList.add(question);
                }
            } catch (Exception e) {
                log.warn("AI 题目骨架生成失败，questionType={}, round={}", questionType, round + 1, e);
            }
        }
        ThrowUtils.throwIf(acceptedQuestionList.isEmpty(), ErrorCode.SYSTEM_ERROR, "AI 未生成出可用题目，请尝试细化技术方向后重试");
        return acceptedQuestionList;
    }

    private String buildQuestionGenerationSystemPrompt() {
        return "你是一位负责技术面试题库建设的资深面试官兼内容编辑。"
                + "你的目标不是凑数量，而是产出能真实区分候选人水平、能被面试官继续深挖的高质量题目。"
                + "请严格输出 JSON 数组，不要输出 Markdown 代码块，不要输出额外解释。"
                + "数组中的每个对象必须包含 title、content、tags、difficulty 四个字段。"
                + "title 要直接点明考点和场景，不要带编号、引号、【】、'题目'、'请简述' 这类空话。"
                + "content 必须是真正可直接入库的题干，至少体现业务场景、技术约束、排查目标、设计目标、取舍要求中的两项，避免只有一句“什么是 xx”或“请介绍 xx”。"
                + "tags 必须是 2 到 5 个简洁标签组成的字符串数组。"
                + "difficulty 必须且只能是：简单、中等、困难 三者之一，并根据题目考察深度、经验要求和作答复杂度合理判断。"
                + "如果一次生成多道题，请主动拉开题型分布，优先覆盖原理机制、工程场景、系统设计、性能优化、故障排查、边界取舍等不同方向，避免重复题和换皮题。";
    }

    private String buildQuestionGenerationUserPrompt(String questionType, int requestCount,
                                                     List<AiGeneratedQuestion> existingQuestionList) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("技术方向：").append(questionType)
                .append("\n本轮需要新增 ").append(requestCount).append(" 道题目。");
        promptBuilder.append("\n请优先生成能用于中高级技术面试、适合配详细参考答案、具备继续追问空间的题目。");
        promptBuilder.append("\n如果方向本身比较基础，请主动放到真实工程语境中，不要只出纯概念定义题。");
        promptBuilder.append("\n优先考虑这些题型：原理机制、线上故障排查、性能或稳定性优化、系统设计与扩展、方案取舍、项目复盘。");
        if (existingQuestionList != null && !existingQuestionList.isEmpty()) {
            promptBuilder.append("\n下面这些题目已经生成过了，新题不要重复、不要近似、不要只换个说法：");
            for (AiGeneratedQuestion question : existingQuestionList) {
                promptBuilder.append("\n- ").append(StringUtils.defaultString(question.getTitle()));
            }
        }
        return promptBuilder.toString();
    }

    private List<AiGeneratedQuestion> normalizeAndFilterGeneratedQuestions(List<AiGeneratedQuestion> parsedQuestionList,
                                                                           String questionType,
                                                                           List<AiGeneratedQuestion> existingQuestionList) {
        List<AiGeneratedQuestion> acceptedQuestionList = new ArrayList<>();
        if (parsedQuestionList == null || parsedQuestionList.isEmpty()) {
            return acceptedQuestionList;
        }
        List<AiGeneratedQuestion> referenceQuestionList = new ArrayList<>();
        if (existingQuestionList != null) {
            referenceQuestionList.addAll(existingQuestionList);
        }
        for (AiGeneratedQuestion rawQuestion : parsedQuestionList) {
            AiGeneratedQuestion normalizedQuestion = normalizeGeneratedQuestion(rawQuestion, questionType);
            if (!isGeneratedQuestionUsable(normalizedQuestion)) {
                continue;
            }
            if (isDuplicateGeneratedQuestion(normalizedQuestion, referenceQuestionList)
                    || isDuplicateGeneratedQuestion(normalizedQuestion, acceptedQuestionList)) {
                continue;
            }
            acceptedQuestionList.add(normalizedQuestion);
            referenceQuestionList.add(normalizedQuestion);
        }
        return acceptedQuestionList;
    }

    private AiGeneratedQuestion normalizeGeneratedQuestion(AiGeneratedQuestion rawQuestion, String questionType) {
        AiGeneratedQuestion normalizedQuestion = new AiGeneratedQuestion();
        normalizedQuestion.setTitle(cleanGeneratedTitle(rawQuestion == null ? null : rawQuestion.getTitle()));
        normalizedQuestion.setContent(cleanGeneratedContent(rawQuestion == null ? null : rawQuestion.getContent()));
        normalizedQuestion.setDifficulty(normalizeGeneratedDifficulty(rawQuestion == null ? null : rawQuestion.getDifficulty()));
        normalizedQuestion.setTags(normalizeGeneratedTags(rawQuestion == null ? null : rawQuestion.getTags(), questionType));
        return normalizedQuestion;
    }

    private String cleanGeneratedTitle(String title) {
        String cleaned = StringUtils.trimToEmpty(title)
                .replaceAll("^题目\\s*[:：]\\s*", "")
                .replaceAll("^[0-9一二三四五六七八九十]+[.、)）]\\s*", "")
                .replaceAll("^[\"“'‘【\\[]+", "")
                .replaceAll("[\"”'’】\\]]+$", "")
                .trim();
        return StringUtils.abbreviate(cleaned, 80);
    }

    private String cleanGeneratedContent(String content) {
        String cleaned = StringUtils.trimToEmpty(content)
                .replaceAll("^题干\\s*[:：]\\s*", "")
                .replaceAll("^[0-9一二三四五六七八九十]+[.、)）]\\s*", "")
                .replace("\r", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned;
    }

    private List<String> normalizeGeneratedTags(List<String> rawTags, String questionType) {
        LinkedHashSet<String> tagSet = new LinkedHashSet<>();
        if (rawTags != null) {
            rawTags.stream()
                    .map(StringUtils::trimToEmpty)
                    .filter(StringUtils::isNotBlank)
                    .map(tag -> StringUtils.abbreviate(tag, 20))
                    .forEach(tagSet::add);
        }
        if (tagSet.isEmpty()) {
            splitCandidateText(questionType).stream()
                    .limit(3)
                    .forEach(tagSet::add);
        }
        return new ArrayList<>(tagSet).stream().limit(MAX_GENERATED_TAGS).collect(Collectors.toList());
    }

    private boolean isGeneratedQuestionUsable(AiGeneratedQuestion question) {
        if (question == null) {
            return false;
        }
        String title = StringUtils.trimToEmpty(question.getTitle());
        String content = StringUtils.trimToEmpty(question.getContent());
        if (StringUtils.isAnyBlank(title, content)) {
            return false;
        }
        if (title.length() < 4 || content.length() < MIN_GENERATED_QUESTION_CONTENT_LENGTH) {
            return false;
        }
        if (normalizeGeneratedQuestionText(title).equals(normalizeGeneratedQuestionText(content))) {
            return false;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        return !(content.length() < 42
                && (lowerContent.contains("什么是")
                || lowerContent.contains("请介绍")
                || lowerContent.contains("请简述")
                || lowerContent.contains("解释一下")
                || lowerContent.contains("谈谈你对")));
    }

    private boolean isDuplicateGeneratedQuestion(AiGeneratedQuestion candidate, List<AiGeneratedQuestion> questionList) {
        if (candidate == null || questionList == null || questionList.isEmpty()) {
            return false;
        }
        String candidateTitle = normalizeGeneratedQuestionText(candidate.getTitle());
        String candidateKey = buildGeneratedQuestionDuplicateKey(candidate);
        for (AiGeneratedQuestion existingQuestion : questionList) {
            if (existingQuestion == null) {
                continue;
            }
            String existingTitle = normalizeGeneratedQuestionText(existingQuestion.getTitle());
            if (StringUtils.isBlank(existingTitle)) {
                continue;
            }
            if (candidateKey.equals(buildGeneratedQuestionDuplicateKey(existingQuestion))) {
                return true;
            }
            if (candidateTitle.equals(existingTitle)) {
                return true;
            }
            if (candidateTitle.length() >= 8 && existingTitle.length() >= 8
                    && (candidateTitle.contains(existingTitle) || existingTitle.contains(candidateTitle))) {
                return true;
            }
        }
        return false;
    }

    private String buildGeneratedQuestionDuplicateKey(AiGeneratedQuestion question) {
        String normalizedTitle = normalizeGeneratedQuestionText(question == null ? null : question.getTitle());
        String normalizedContent = normalizeGeneratedQuestionText(StringUtils.left(
                StringUtils.defaultString(question == null ? null : question.getContent()),
                120
        ));
        return normalizedTitle + "#" + normalizedContent;
    }

    private String normalizeGeneratedQuestionText(String text) {
        return StringUtils.defaultString(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s`'\"“”‘’《》【】()（）,.，。:：;；!?！？、/\\\\\\-_|]+", "")
                .replace("请介绍", "")
                .replace("请简述", "")
                .replace("请说明", "")
                .replace("解释一下", "")
                .replace("什么是", "")
                .trim();
    }

    private List<String> splitCandidateText(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(text.split("[、,，/|\\s]+"))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private void enrichGeneratedQuestionAnswers(List<AiGeneratedQuestion> generatedQuestionList) {
        if (generatedQuestionList == null || generatedQuestionList.isEmpty()) {
            return;
        }
        for (int start = 0; start < generatedQuestionList.size(); start += AI_GENERATE_ANSWER_BATCH_SIZE) {
            int end = Math.min(generatedQuestionList.size(), start + AI_GENERATE_ANSWER_BATCH_SIZE);
            List<AiGeneratedQuestion> batch = generatedQuestionList.subList(start, end);
            List<AiGeneratedAnswerPlan> answerPlanList = generateAnswerPlans(batch);
            List<AiGeneratedAnswerResult> answerResultList = generateAnswersByPlans(batch, answerPlanList);
            for (int i = 0; i < batch.size(); i++) {
                AiGeneratedQuestion question = batch.get(i);
                AiGeneratedAnswerPlan answerPlan = findAnswerPlanByIndex(answerPlanList, i);
                if (answerPlan == null) {
                    answerPlan = buildFallbackAnswerPlan(i, question);
                }
                AiGeneratedAnswerResult answerResult = findAnswerResultByIndex(answerResultList, i);
                if (answerResult != null && StringUtils.isNotBlank(answerResult.getAnswer())) {
                    question.setAnswer(answerResult.getAnswer().trim());
                }
                if (!isGeneratedAnswerUsable(question.getAnswer())) {
                    String retriedAnswer = generateSingleAnswerByPlan(question, answerPlan);
                    if (isGeneratedAnswerUsable(retriedAnswer)) {
                        question.setAnswer(retriedAnswer.trim());
                    }
                }
                if (shouldReviewGeneratedAnswer(question, answerPlan)) {
                    String improvedAnswer = reviewAndImproveGeneratedAnswer(question, answerPlan);
                    if (isGeneratedAnswerUsable(improvedAnswer)) {
                        question.setAnswer(improvedAnswer.trim());
                    }
                }
            }
        }
    }

    private List<AiGeneratedAnswerPlan> generateAnswerPlans(List<AiGeneratedQuestion> questionBatch) {
        String systemPrompt = "你是一位技术题解编辑，当前任务不是直接写答案，而是先判断每道题最适合拆成几部分回答。"
                + "请严格输出 JSON 数组，不要输出 Markdown 代码块，不要输出额外解释。"
                + "数组中的每个对象必须包含 index、questionType、partTitles、mustCoverPoints、guidance、answerStyle 六个字段。"
                + "index 是题目序号；questionType 只能从 principle、scenario、design、troubleshooting、comparison 中选择；"
                + "partTitles 是字符串数组，表示这道题最适合拆分成的回答部分标题，数量建议 2 到 6 个；"
                + "mustCoverPoints 是字符串数组，表示答案必须覆盖的关键点；"
                + "guidance 是一句话说明这道题作答时的重点；"
                + "answerStyle 是一句话说明最终答案应该采用什么表达风格，例如“先给结论再展开机制”“按排查时间线推进”“按对比维度逐项展开”。"
                + "请根据每道题的题型自定义 partTitles 和 mustCoverPoints，不要所有题都复用同一套标题。"
                + "原理题要突出概念、本质、机制、适用边界；设计题要突出目标、约束、方案、取舍、扩展；"
                + "场景题要突出背景、分析、动作、结果；故障排查题要突出症状、定位路径、根因、修复和复盘；对比选型题要突出维度、差异、适用场景和取舍。";
        List<AiGeneratedAnswerPlan> planList = new ArrayList<>();
        try {
            String userPrompt = buildAnswerPlanUserPrompt(questionBatch);
            String result = aiManager.doChat(systemPrompt, userPrompt);
            planList.addAll(parseAiGeneratedAnswerPlans(result));
        } catch (Exception e) {
            log.warn("AI 参考答案拆分方案生成失败，已切换到本地兜底", e);
        }
        for (int i = 0; i < questionBatch.size(); i++) {
            AiGeneratedAnswerPlan plan = findAnswerPlanByIndex(planList, i);
            AiGeneratedAnswerPlan fallbackPlan = buildFallbackAnswerPlan(i, questionBatch.get(i));
            if (plan == null || plan.getPartTitles() == null || plan.getPartTitles().isEmpty()) {
                planList.add(fallbackPlan);
                continue;
            }
            if (StringUtils.isBlank(plan.getQuestionType())) {
                plan.setQuestionType(fallbackPlan.getQuestionType());
            }
            if (plan.getMustCoverPoints() == null || plan.getMustCoverPoints().isEmpty()) {
                plan.setMustCoverPoints(fallbackPlan.getMustCoverPoints());
            }
            if (StringUtils.isBlank(plan.getGuidance())) {
                plan.setGuidance(fallbackPlan.getGuidance());
            }
            if (StringUtils.isBlank(plan.getAnswerStyle())) {
                plan.setAnswerStyle(fallbackPlan.getAnswerStyle());
            }
        }
        return planList;
    }

    private List<AiGeneratedAnswerResult> generateAnswersByPlans(List<AiGeneratedQuestion> questionBatch,
                                                                 List<AiGeneratedAnswerPlan> answerPlanList) {
        String systemPrompt = "你是一位资深技术面试官，负责为题库撰写详细参考答案。"
                + "你会先收到每道题的回答拆分方案，请基于对应的 questionType、partTitles、mustCoverPoints、guidance 和 answerStyle 写答案。"
                + "请严格输出 JSON 数组，不要输出 Markdown 代码块，不要输出额外解释。"
                + "数组中的每个对象必须包含 index、answer 两个字段。"
                + "在不胡编、不脱离题意的前提下，answer 越详细越好，宁可展开充分，也不要写得过短。"
                + "answer 要尽可能详细、可直接用于学习复习和面试作答。简单题通常也不要低于 450 字，中等题优先 650 字以上，困难题优先 800 字以上。"
                + "不要只停留在定义或概念，要主动补齐为什么、怎么做、什么时候适合、有什么代价、不这么做会怎样、线上会踩什么坑。"
                + "每道题的结构要贴合它自己的题型，不要所有题都套用同一组标题。"
                + "可以使用小标题、编号或自然分段，但要围绕对应的 partTitles 展开，写清原因、步骤、取舍、边界条件、工程示例、排查路径、验证方式和常见坑点。"
                + "除非题目本身极其简单，否则请至少给出一个具体场景、一个工程化细节和一个常见误区或风险点。";
        try {
            String userPrompt = buildAnswerGenerationUserPrompt(questionBatch, answerPlanList);
            String result = aiManager.doChat(systemPrompt, userPrompt);
            return parseAiGeneratedAnswerResults(result);
        } catch (Exception e) {
            log.warn("AI 批量参考答案生成失败，后续将尝试单题补写", e);
            return new ArrayList<>();
        }
    }

    private String buildAnswerPlanUserPrompt(List<AiGeneratedQuestion> questionBatch) {
        StringBuilder promptBuilder = new StringBuilder("请为下面每道题设计最适合的回答拆分方案：\n");
        for (int i = 0; i < questionBatch.size(); i++) {
            AiGeneratedQuestion question = questionBatch.get(i);
            promptBuilder.append("\n题目序号：").append(i)
                    .append("\n标题：").append(StringUtils.defaultString(question.getTitle()))
                    .append("\n建议题型：").append(detectGeneratedQuestionType(question))
                    .append("\n难度：").append(normalizeGeneratedDifficulty(question.getDifficulty()))
                    .append("\n题干：").append(StringUtils.defaultString(question.getContent()))
                    .append("\n标签：").append(question.getTags() == null ? "[]" : JSONUtil.toJsonStr(question.getTags()))
                    .append("\n");
        }
        return promptBuilder.toString();
    }

    private String buildAnswerGenerationUserPrompt(List<AiGeneratedQuestion> questionBatch,
                                                   List<AiGeneratedAnswerPlan> answerPlanList) {
        StringBuilder promptBuilder = new StringBuilder("请根据每道题的回答拆分方案生成最终参考答案：\n");
        for (int i = 0; i < questionBatch.size(); i++) {
            AiGeneratedQuestion question = questionBatch.get(i);
            AiGeneratedAnswerPlan answerPlan = findAnswerPlanByIndex(answerPlanList, i);
            if (answerPlan == null) {
                answerPlan = buildFallbackAnswerPlan(i, question);
            }
            promptBuilder.append("\n题目序号：").append(i)
                    .append("\n标题：").append(StringUtils.defaultString(question.getTitle()))
                    .append("\n难度：").append(normalizeGeneratedDifficulty(question.getDifficulty()))
                    .append("\n题干：").append(StringUtils.defaultString(question.getContent()))
                    .append("\n标签：").append(question.getTags() == null ? "[]" : JSONUtil.toJsonStr(question.getTags()))
                    .append("\n题型：").append(StringUtils.defaultIfBlank(answerPlan.getQuestionType(), detectGeneratedQuestionType(question)))
                    .append("\n回答分段：").append(JSONUtil.toJsonStr(answerPlan.getPartTitles()))
                    .append("\n必须覆盖点：").append(answerPlan.getMustCoverPoints() == null ? "[]" : JSONUtil.toJsonStr(answerPlan.getMustCoverPoints()))
                    .append("\n作答重点：").append(StringUtils.defaultString(answerPlan.getGuidance()))
                    .append("\n表达风格：").append(StringUtils.defaultString(answerPlan.getAnswerStyle()))
                    .append("\n").append(buildGeneratedAnswerLengthInstruction(question, answerPlan))
                    .append("\n");
        }
        return promptBuilder.toString();
    }

    private AiGeneratedAnswerPlan buildFallbackAnswerPlan(int index, AiGeneratedQuestion question) {
        String questionType = detectGeneratedQuestionType(question);
        List<String> partTitles = new ArrayList<>();
        List<String> mustCoverPoints = new ArrayList<>();
        String guidance;
        String answerStyle;
        if ("design".equals(questionType)) {
            partTitles.add("问题背景与目标");
            partTitles.add("核心方案设计");
            partTitles.add("关键取舍与风险");
            partTitles.add("扩展与优化方向");
            mustCoverPoints.add("业务约束");
            mustCoverPoints.add("核心链路");
            mustCoverPoints.add("扩展性");
            mustCoverPoints.add("容灾或高可用");
            mustCoverPoints.add("容量预估或性能瓶颈");
            mustCoverPoints.add("验证方案是否可行");
            guidance = "重点写清约束条件、方案结构、优缺点、容量与扩展性，并补一个贴近真实业务的落地示例。";
            answerStyle = "先交代目标和约束，再展开方案、取舍与演进方向。";
        } else if ("troubleshooting".equals(questionType)) {
            partTitles.add("现象与影响范围");
            partTitles.add("排查路径");
            partTitles.add("根因定位与修复");
            partTitles.add("复盘与预防");
            mustCoverPoints.add("排查顺序");
            mustCoverPoints.add("日志或监控线索");
            mustCoverPoints.add("根因");
            mustCoverPoints.add("防再发措施");
            mustCoverPoints.add("验证修复是否生效");
            mustCoverPoints.add("容易误判的线索");
            guidance = "重点写清先看什么、怎么缩小范围、如何定位根因，以及修复后的复盘动作和防再发机制。";
            answerStyle = "按排查时间线展开，体现观察、验证、定位、修复和复盘。";
        } else if ("comparison".equals(questionType)) {
            partTitles.add("比较前提与核心维度");
            partTitles.add("关键差异");
            partTitles.add("适用场景与选型建议");
            partTitles.add("落地注意事项");
            mustCoverPoints.add("对比维度");
            mustCoverPoints.add("优缺点");
            mustCoverPoints.add("适用场景");
            mustCoverPoints.add("取舍依据");
            mustCoverPoints.add("迁移或落地成本");
            mustCoverPoints.add("常见误选场景");
            guidance = "重点写清比较维度、差异点、适用边界、选型逻辑，以及在真实项目里的迁移或维护成本。";
            answerStyle = "按维度逐项对比，再给出明确的选型建议。";
        } else if ("scenario".equals(questionType)) {
            partTitles.add("场景与核心问题");
            partTitles.add("分析思路");
            partTitles.add("解决动作");
            partTitles.add("风险与实践建议");
            mustCoverPoints.add("判断过程");
            mustCoverPoints.add("处理步骤");
            mustCoverPoints.add("结果验证");
            mustCoverPoints.add("风险控制");
            mustCoverPoints.add("业务影响与收益");
            mustCoverPoints.add("常见坑点");
            guidance = "重点写清判断过程、处理步骤、落地动作、结果验证和工程化经验，尽量带出业务收益。";
            answerStyle = "先交代场景，再按分析、动作、结果和复盘展开。";
        } else {
            partTitles.add("核心结论");
            partTitles.add("底层机制");
            partTitles.add("典型场景");
            partTitles.add("常见误区与边界");
            mustCoverPoints.add("概念本质");
            mustCoverPoints.add("运行机制");
            mustCoverPoints.add("使用场景");
            mustCoverPoints.add("边界条件");
            mustCoverPoints.add("为什么这样设计");
            mustCoverPoints.add("工程实践中的注意点");
            guidance = "重点写清原理解释、适用场景、边界条件和常见误区，并补充一段工程实践视角。";
            answerStyle = "先给结论，再解释 why/how，最后补场景和边界。";
        }
        AiGeneratedAnswerPlan plan = new AiGeneratedAnswerPlan();
        plan.setIndex(index);
        plan.setQuestionType(questionType);
        plan.setPartTitles(partTitles);
        plan.setMustCoverPoints(mustCoverPoints);
        plan.setGuidance(guidance);
        plan.setAnswerStyle(answerStyle);
        return plan;
    }

    private AiGeneratedAnswerPlan findAnswerPlanByIndex(List<AiGeneratedAnswerPlan> answerPlanList, int index) {
        if (answerPlanList == null) {
            return null;
        }
        for (AiGeneratedAnswerPlan answerPlan : answerPlanList) {
            if (answerPlan != null && Objects.equals(answerPlan.getIndex(), index)) {
                return answerPlan;
            }
        }
        return null;
    }

    private AiGeneratedAnswerResult findAnswerResultByIndex(List<AiGeneratedAnswerResult> answerResultList, int index) {
        if (answerResultList == null) {
            return null;
        }
        for (AiGeneratedAnswerResult answerResult : answerResultList) {
            if (answerResult != null && Objects.equals(answerResult.getIndex(), index)) {
                return answerResult;
            }
        }
        return null;
    }

    private String generateSingleAnswerByPlan(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        String safeTitle = StringUtils.defaultString(question == null ? null : question.getTitle());
        try {
            String systemPrompt = "你是一位资深技术面试官兼题解编辑，现在只需要为一道题写最终参考答案。"
                    + "请严格输出 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。"
                    + "JSON 对象必须包含 answer 字段。"
                    + "在不胡编、不脱离题意的前提下，answer 越详细越好，宁可展开充分，也不要只写成短答案。"
                    + "answer 必须是可以直接入库的完整参考答案，不要只给提纲，不要只给几句概括。"
                    + "请根据题目的 difficulty、tags、questionType、partTitles、mustCoverPoints、guidance 和 answerStyle 写出一篇明显充分展开的高质量答案。"
                    + "答案要尽量减少模板味，贴合题型展开：原理题讲清 why/how 和边界，设计题讲清约束、方案、取舍和扩展，场景题讲清分析路径、落地动作、风险点和复盘，排查题讲清定位线索和排查顺序，对比题讲清维度和选型依据。"
                    + "除非题目本身非常简单，否则必须覆盖：一个具体场景或例子、一个工程实践细节、一个风险或误区、一个验证结果或判断依据。";
            String userPrompt = buildSingleAnswerGenerationUserPrompt(question, answerPlan);
            String result = aiManager.doChat(systemPrompt, userPrompt);
            return extractSingleGeneratedAnswer(result);
        } catch (Exception e) {
            log.warn("AI 单题参考答案补写失败，title={}", safeTitle, e);
            return "";
        }
    }

    private String buildSingleAnswerGenerationUserPrompt(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        return "标题：" + StringUtils.defaultString(question == null ? null : question.getTitle())
                + "\n难度：" + normalizeGeneratedDifficulty(question == null ? null : question.getDifficulty())
                + "\n题干：" + StringUtils.defaultString(question == null ? null : question.getContent())
                + "\n标签：" + (question == null || question.getTags() == null ? "[]" : JSONUtil.toJsonStr(question.getTags()))
                + "\n题型：" + StringUtils.defaultIfBlank(answerPlan == null ? null : answerPlan.getQuestionType(), detectGeneratedQuestionType(question))
                + "\n回答分段：" + (answerPlan == null || answerPlan.getPartTitles() == null ? "[]" : JSONUtil.toJsonStr(answerPlan.getPartTitles()))
                + "\n必须覆盖点：" + (answerPlan == null || answerPlan.getMustCoverPoints() == null ? "[]" : JSONUtil.toJsonStr(answerPlan.getMustCoverPoints()))
                + "\n作答重点：" + StringUtils.defaultString(answerPlan == null ? null : answerPlan.getGuidance())
                + "\n表达风格：" + StringUtils.defaultString(answerPlan == null ? null : answerPlan.getAnswerStyle())
                + "\n" + buildGeneratedAnswerLengthInstruction(question, answerPlan)
                + "\n请输出最终参考答案。";
    }

    private String extractSingleGeneratedAnswer(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        if (jsonText.startsWith("{")) {
            JSONObject jsonObject = JSONUtil.parseObj(jsonText);
            return StringUtils.trimToEmpty(firstNonBlank(jsonObject.getStr("answer"), jsonObject.getStr("content")));
        }
        if (jsonText.startsWith("[")) {
            List<AiGeneratedAnswerResult> answerResultList = JSONUtil.toList(jsonText, AiGeneratedAnswerResult.class);
            if (answerResultList != null && !answerResultList.isEmpty()) {
                return StringUtils.trimToEmpty(answerResultList.get(0).getAnswer());
            }
        }
        return StringUtils.trimToEmpty(jsonText);
    }

    private boolean isGeneratedAnswerUsable(String answer) {
        String normalizedAnswer = StringUtils.trimToEmpty(answer);
        if (normalizedAnswer.length() < MIN_GENERATED_ANSWER_LENGTH) {
            return false;
        }
        String lowerAnswer = normalizedAnswer.toLowerCase(Locale.ROOT);
        return !(lowerAnswer.contains("待补充")
                || lowerAnswer.contains("暂无")
                || lowerAnswer.contains("示例答案")
                || lowerAnswer.contains("参考提纲"));
    }

    private boolean shouldReviewGeneratedAnswer(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        String answer = StringUtils.trimToEmpty(question == null ? null : question.getAnswer());
        if (!isGeneratedAnswerUsable(answer)) {
            return true;
        }
        int expectedMinLength = expectedGeneratedAnswerMinLength(question, answerPlan);
        if (answer.length() < Math.max(MIN_HIGH_CONFIDENCE_GENERATED_ANSWER_LENGTH, expectedMinLength)) {
            return true;
        }
        List<String> expectedKeywordList = buildExpectedAnswerKeywords(question, answerPlan);
        int keywordHitCount = countAnswerKeywordHits(answer, expectedKeywordList);
        int expectedHitThreshold = expectedKeywordList.isEmpty()
                ? 0
                : Math.min(expectedKeywordList.size(), Math.max(2, Math.min(5, expectedKeywordList.size() / 3 + 1)));
        if (!expectedKeywordList.isEmpty() && keywordHitCount < expectedHitThreshold) {
            return true;
        }
        boolean hasStructure = containsAnyIgnoreCase(answer, "首先", "然后", "最后", "一是", "二是", "三是", "1.", "2.", "3.", "第一", "第二");
        boolean hasScenario = containsAnyIgnoreCase(answer, "例如", "比如", "场景", "案例", "实践", "线上", "落地");
        boolean hasBoundary = containsAnyIgnoreCase(answer, "边界", "风险", "注意", "误区", "坑", "异常", "限制");
        boolean hasTradeOff = containsAnyIgnoreCase(answer, "取舍", "权衡", "优点", "缺点", "成本", "收益");
        boolean hasValidation = containsAnyIgnoreCase(answer, "验证", "指标", "监控", "压测", "结果", "观察", "确认");
        String questionType = StringUtils.defaultIfBlank(answerPlan == null ? null : answerPlan.getQuestionType(), detectGeneratedQuestionType(question));
        if ("design".equals(questionType)) {
            return !hasTradeOff || !hasBoundary || !hasScenario || !hasValidation || answer.length() < Math.max(680, expectedMinLength);
        }
        if ("troubleshooting".equals(questionType)) {
            return !containsAnyIgnoreCase(answer, "排查", "定位", "日志", "监控", "根因", "复盘")
                    || !hasValidation
                    || !hasScenario
                    || answer.length() < Math.max(660, expectedMinLength);
        }
        if ("comparison".equals(questionType)) {
            return (!containsAnyIgnoreCase(answer, "区别", "差异", "适合", "不适合", "选型") || !hasTradeOff)
                    || !hasScenario
                    || !hasBoundary
                    || answer.length() < Math.max(620, expectedMinLength);
        }
        if ("scenario".equals(questionType)) {
            return !hasScenario
                    || !containsAnyIgnoreCase(answer, "步骤", "处理", "先", "再", "验证", "复盘")
                    || !hasValidation
                    || answer.length() < Math.max(620, expectedMinLength);
        }
        return !hasStructure
                || !containsAnyIgnoreCase(answer, "原理", "机制", "流程", "原因", "本质")
                || !hasScenario
                || !hasBoundary
                || answer.length() < Math.max(560, expectedMinLength);
    }

    private String reviewAndImproveGeneratedAnswer(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        String currentAnswer = StringUtils.trimToEmpty(question == null ? null : question.getAnswer());
        String safeTitle = StringUtils.defaultString(question == null ? null : question.getTitle());
        try {
            String systemPrompt = buildAnswerReviewSystemPrompt();
            String userPrompt = buildAnswerReviewUserPrompt(question, answerPlan, currentAnswer);
            String result = aiManager.doChat(systemPrompt, userPrompt);
            AiGeneratedAnswerReview review = parseAiGeneratedAnswerReview(result);
            if (review == null) {
                return currentAnswer;
            }
            String rewrittenAnswer = StringUtils.trimToEmpty(review.getRewriteAnswer());
            if (isGeneratedAnswerUsable(rewrittenAnswer)
                    && (!Boolean.TRUE.equals(review.getPass())
                    || review.getScore() == null
                    || review.getScore() < MIN_GENERATED_ANSWER_REVIEW_SCORE
                    || rewrittenAnswer.length() > currentAnswer.length() + 40)) {
                return rewrittenAnswer;
            }
            return currentAnswer;
        } catch (Exception e) {
            log.warn("AI 参考答案审稿重写失败，title={}", safeTitle, e);
            return currentAnswer;
        }
    }

    private String buildAnswerReviewSystemPrompt() {
        return "你是一位严格但专业的技术题库总编，负责审稿和重写参考答案。"
                + "请严格输出 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。"
                + "JSON 对象必须包含 pass、score、problems、rewriteAnswer 四个字段。"
                + "pass 表示当前答案是否已经达到可直接入库的质量；score 为 0 到 100 的整数；problems 是字符串数组；rewriteAnswer 是改写后的完整答案。"
                + "评分时重点看：是否足够详细、是否贴合题型、是否覆盖关键知识点、是否讲清为什么和怎么做、是否有场景/边界/取舍/风险/示例。"
                + "只要还能在不胡编的前提下写得更详细，就不要轻易判定 pass。"
                + "如果当前答案已经很好，也要返回 score 和 problems，但 rewriteAnswer 可以返回空字符串。"
                + "如果当前答案偏短、偏泛、像提纲、缺少分析过程、缺少工程细节或缺少关键覆盖点，就必须给出完整 rewriteAnswer。"
                + "不要只做轻微润色，重写时要把答案显著写得更充实。";
    }

    private String buildAnswerReviewUserPrompt(AiGeneratedQuestion question,
                                               AiGeneratedAnswerPlan answerPlan,
                                               String currentAnswer) {
        return "请审查下面这道题的参考答案，并在需要时直接重写："
                + "\n标题：" + StringUtils.defaultString(question == null ? null : question.getTitle())
                + "\n难度：" + normalizeGeneratedDifficulty(question == null ? null : question.getDifficulty())
                + "\n题干：" + StringUtils.defaultString(question == null ? null : question.getContent())
                + "\n标签：" + (question == null || question.getTags() == null ? "[]" : JSONUtil.toJsonStr(question.getTags()))
                + "\n题型：" + StringUtils.defaultIfBlank(answerPlan == null ? null : answerPlan.getQuestionType(), detectGeneratedQuestionType(question))
                + "\n回答分段：" + (answerPlan == null || answerPlan.getPartTitles() == null ? "[]" : JSONUtil.toJsonStr(answerPlan.getPartTitles()))
                + "\n必须覆盖点：" + (answerPlan == null || answerPlan.getMustCoverPoints() == null ? "[]" : JSONUtil.toJsonStr(answerPlan.getMustCoverPoints()))
                + "\n作答重点：" + StringUtils.defaultString(answerPlan == null ? null : answerPlan.getGuidance())
                + "\n表达风格：" + StringUtils.defaultString(answerPlan == null ? null : answerPlan.getAnswerStyle())
                + "\n" + buildGeneratedAnswerLengthInstruction(question, answerPlan)
                + "\n当前答案：" + currentAnswer
                + "\n要求：如果答案不够好，请直接输出完整 rewriteAnswer，不要只列修改建议。";
    }

    private AiGeneratedAnswerReview parseAiGeneratedAnswerReview(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        if (jsonText.startsWith("[")) {
            JSONArray jsonArray = JSONUtil.parseArray(jsonText);
            if (jsonArray.isEmpty()) {
                return null;
            }
            jsonText = JSONUtil.toJsonStr(jsonArray.get(0));
        }
        if (jsonText.startsWith("{")) {
            JSONObject jsonObject = JSONUtil.parseObj(jsonText);
            AiGeneratedAnswerReview review = new AiGeneratedAnswerReview();
            review.setPass(jsonObject.getBool("pass"));
            review.setScore(jsonObject.getInt("score"));
            JSONArray problems = jsonObject.getJSONArray("problems");
            if (problems == null) {
                problems = jsonObject.getJSONArray("issues");
            }
            review.setProblems(problems == null ? new ArrayList<>() : problems.toList(String.class));
            review.setRewriteAnswer(StringUtils.trimToEmpty(firstNonBlank(
                    jsonObject.getStr("rewriteAnswer"),
                    jsonObject.getStr("rewrite"),
                    jsonObject.getStr("answer")
            )));
            return review;
        }
        if (StringUtils.isNotBlank(jsonText)) {
            AiGeneratedAnswerReview review = new AiGeneratedAnswerReview();
            review.setPass(false);
            review.setScore(0);
            review.setProblems(List.of("AI 审稿返回了非 JSON 内容"));
            review.setRewriteAnswer(StringUtils.trimToEmpty(jsonText));
            return review;
        }
        return null;
    }

    private List<String> buildExpectedAnswerKeywords(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        LinkedHashSet<String> keywordSet = new LinkedHashSet<>();
        if (question != null && question.getTags() != null) {
            question.getTags().stream()
                    .map(StringUtils::trimToEmpty)
                    .filter(keyword -> keyword.length() >= 2 && keyword.length() <= 24)
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .forEach(keywordSet::add);
        }
        if (answerPlan != null && answerPlan.getPartTitles() != null) {
            answerPlan.getPartTitles().stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(partTitle -> {
                        keywordSet.add(partTitle.toLowerCase(Locale.ROOT));
                        tokenizeText(partTitle).forEach(keywordSet::add);
                    });
        }
        if (answerPlan != null && answerPlan.getMustCoverPoints() != null) {
            answerPlan.getMustCoverPoints().stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(point -> {
                        keywordSet.add(point.toLowerCase(Locale.ROOT));
                        tokenizeText(point).forEach(keywordSet::add);
                    });
        }
        tokenizeText(question == null ? null : question.getTitle()).stream().limit(6).forEach(keywordSet::add);
        tokenizeText(question == null ? null : question.getContent()).stream().limit(10).forEach(keywordSet::add);
        return new ArrayList<>(keywordSet).stream().limit(16).collect(Collectors.toList());
    }

    private int countAnswerKeywordHits(String answer, List<String> expectedKeywordList) {
        if (StringUtils.isBlank(answer) || expectedKeywordList == null || expectedKeywordList.isEmpty()) {
            return 0;
        }
        String lowerAnswer = answer.toLowerCase(Locale.ROOT);
        int hitCount = 0;
        for (String keyword : expectedKeywordList) {
            String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
            if (normalizedKeyword.length() >= 2 && lowerAnswer.contains(normalizedKeyword)) {
                hitCount++;
            }
        }
        return hitCount;
    }

    private String detectGeneratedQuestionType(AiGeneratedQuestion question) {
        String titleAndContent = StringUtils.defaultString(question == null ? null : question.getTitle())
                + " " + StringUtils.defaultString(question == null ? null : question.getContent());
        if (question != null && question.getTags() != null && !question.getTags().isEmpty()) {
            titleAndContent += " " + String.join(" ", question.getTags());
        }
        if (containsAnyIgnoreCase(titleAndContent, "对比", "比较", "区别", "优缺点", "选型", "tradeoff")) {
            return "comparison";
        }
        if (containsAnyIgnoreCase(titleAndContent, "排查", "定位", "故障", "异常", "报警", "线上", "事故", "根因")) {
            return "troubleshooting";
        }
        if (containsAnyIgnoreCase(titleAndContent, "设计", "架构", "系统", "高可用", "扩展", "容量", "容灾", "一致性")) {
            return "design";
        }
        if (containsAnyIgnoreCase(titleAndContent, "场景", "如何", "怎么", "如果", "实践", "落地", "优化")) {
            return "scenario";
        }
        return "principle";
    }

    private int expectedGeneratedAnswerMinLength(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        String difficulty = normalizeGeneratedDifficulty(question == null ? null : question.getDifficulty());
        int baseLength = switch (difficulty) {
            case "简单" -> 450;
            case "困难" -> 800;
            default -> 620;
        };
        String questionType = StringUtils.defaultIfBlank(
                answerPlan == null ? null : answerPlan.getQuestionType(),
                detectGeneratedQuestionType(question)
        );
        int bonus = switch (questionType) {
            case "design", "troubleshooting" -> 120;
            case "scenario", "comparison" -> 80;
            default -> 40;
        };
        return baseLength + bonus;
    }

    private String buildGeneratedAnswerLengthInstruction(AiGeneratedQuestion question, AiGeneratedAnswerPlan answerPlan) {
        int minLength = expectedGeneratedAnswerMinLength(question, answerPlan);
        int preferredLength = Math.min(1400, minLength + 260);
        return String.format("字数目标：尽量写到 %d 到 %d 字，除非题目极其简单，否则不要明显低于 %d 字。",
                minLength,
                preferredLength,
                minLength);
    }

    /**
     * 检测爬虫
     *
     * @param loginUserId
     */
    private void crawlerDetect(User loginUser, HttpServletRequest request) {
        long loginUserId = loginUser.getId();
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 超过多少次封号
        final int BAN_COUNT = 20;
        String ip = NetUtils.getIpAddress(request);
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
        // 是否封号
        if (count > BAN_COUNT) {
            securityAlertService.recordAlert(
                    loginUserId,
                    loginUser.getUserName(),
                    "HIGH_FREQUENCY_ACCESS_BAN",
                    "high",
                    "用户在 1 分钟内访问题目详情次数过多，已自动封禁",
                    String.format("当前访问次数：%d，阈值：%d", count, BAN_COUNT),
                    ip
            );
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            User updateUser = new User();
            updateUser.setId(loginUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            securityAlertService.recordAlert(
                    loginUserId,
                    loginUser.getUserName(),
                    "HIGH_FREQUENCY_ACCESS_WARN",
                    "medium",
                    "用户在 1 分钟内高频访问题目详情，已触发预警",
                    String.format("当前访问次数：%d，预警阈值：%d", count, WARN_COUNT),
                    ip
            );
            log.warn("用户访问频率过高，userId={}, count={}", loginUserId, count);
        }
    }

    private String extractResumeText(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile.getSize() > MAX_RESUME_FILE_SIZE,
                ErrorCode.PARAMS_ERROR,
                "简历文件不能超过 2MB");
        String fileSuffix = StringUtils.lowerCase(FileUtil.getSuffix(multipartFile.getOriginalFilename()));
        ThrowUtils.throwIf(StringUtils.isBlank(fileSuffix) || !SUPPORTED_RESUME_FILE_SUFFIX_SET.contains(fileSuffix),
                ErrorCode.PARAMS_ERROR,
                "目前仅支持 txt、md、docx、pdf 简历文件");
        String resumeText;
        switch (fileSuffix) {
            case "txt":
            case "md":
            case "markdown":
                resumeText = extractPlainText(multipartFile);
                break;
            case "docx":
                resumeText = extractDocxText(multipartFile);
                break;
            case "pdf":
                resumeText = extractPdfText(multipartFile);
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该文件类型");
        }
        String trimmedResumeText = StringUtils.trimToEmpty(resumeText);
        ThrowUtils.throwIf(trimmedResumeText.length() < 20, ErrorCode.PARAMS_ERROR, "简历内容过短，请上传更完整的简历文件");
        return trimmedResumeText;
    }

    private String extractPlainText(MultipartFile multipartFile) {
        try {
            return new String(multipartFile.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取简历文件失败");
        }
    }

    private String extractDocxText(MultipartFile multipartFile) {
        try (XWPFDocument document = new XWPFDocument(multipartFile.getInputStream());
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            log.error("解析 docx 简历失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析 docx 简历失败");
        }
    }

    private String extractPdfText(MultipartFile multipartFile) {
        try (PDDocument document = Loader.loadPDF(multipartFile.getBytes())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            return textStripper.getText(document);
        } catch (IOException e) {
            log.error("解析 pdf 简历失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析 pdf 简历失败");
        }
    }

    /**
     * listQuestionVOByPage 降级操作：直接返回本地数据
     */
    public BaseResponse<Page<QuestionVO>> handleFallback(QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request, Throwable ex) {
        log.warn("listQuestionVOByPage 触发降级，已自动回退到数据库查询", ex);
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    private int getQuestionReviewStatus(Question question) {
        return question.getReviewStatus() == null ? QuestionConstant.REVIEW_STATUS_APPROVED : question.getReviewStatus();
    }

    private String buildQuestionReviewNotificationContent(Question question, Integer reviewStatus, String reviewMessage) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("题目《").append(StringUtils.defaultIfBlank(question.getTitle(), "未命名题目")).append("》");
        if (QuestionConstant.REVIEW_STATUS_APPROVED == reviewStatus) {
            contentBuilder.append("已通过审核，现在可以被其他用户浏览和学习。");
        } else {
            contentBuilder.append("未通过审核，请根据审核意见修改后重新提交。");
        }
        if (StringUtils.isNotBlank(reviewMessage)) {
            contentBuilder.append(" 审核意见：").append(reviewMessage);
        }
        return contentBuilder.toString();
    }

    private QuestionAnswerEvaluateVO buildQuestionAnswerEvaluateVO(Question question, String answerContent) {
        QuestionAnswerEvaluateVO evaluateResult;
        try {
            String aiContent = aiManager.doChat(buildAnswerEvaluateSystemPrompt(), buildAnswerEvaluateUserPrompt(question, answerContent));
            evaluateResult = parseQuestionAnswerEvaluate(aiContent);
            evaluateResult.setAnalysisSource("ai");
        } catch (Exception e) {
            log.warn("AI 判题失败，已自动切换到本地兜底分析，questionId={}", question.getId(), e);
            evaluateResult = buildHeuristicEvaluateResult(question, answerContent);
        }
        applyAnswerEvaluateGuard(evaluateResult, question, answerContent);
        return evaluateResult;
    }

    private String buildAnswerEvaluateSystemPrompt() {
        return "你是一位严格但鼓励式的技术面试官，请对候选人的单题作答做结构化评估。"
                + "请严格输出 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。"
                + "JSON 必须包含 score、level、summary、strengthList、improvementList、missedPointList、followUpQuestionList、referenceSuggestion 八个字段。"
                + "score 为 0 到 100 的整数；level 只能是“优秀”“良好”“合格”“待加强”之一；"
                + "strengthList、improvementList、missedPointList、followUpQuestionList 都必须是字符串数组，每个数组给出 2 到 4 条内容。"
                + "请重点对比标准答案与用户回答之间的覆盖度、结构性、表达清晰度和技术深度。"
                + "评分必须保守且有证据：如果用户只是罗列关键词、数据类型、结论或名词，没有分别展开解释、步骤、场景或取舍，即使列对了一部分，score 通常也不应高于 45。"
                + "如果题目本身包含多个子问题，例如“有哪些 + 应用场景”“原理 + 优缺点”“是什么 + 怎么做”，少答任何一个维度都不能给 80 分以上。"
                + "80 分以上只给覆盖完整、解释清楚且有一定技术细节的回答。";
    }

    private String buildAnswerEvaluateUserPrompt(Question question, String answerContent) {
        List<String> tagList = JSONUtil.toList(StringUtils.defaultIfBlank(question.getTags(), "[]"), String.class);
        String focusHint = buildAnswerEvaluateFocusHint(question);
        return "题目标题：\n" + StringUtils.defaultString(question.getTitle())
                + "\n\n题目难度：\n" + StringUtils.defaultIfBlank(question.getDifficulty(), "未设置")
                + "\n\n题目标签：\n" + String.join("、", tagList)
                + "\n\n题目内容：\n" + safeAiText(question.getContent(), 2500)
                + focusHint
                + "\n\n参考答案：\n" + safeAiText(question.getAnswer(), 2500)
                + "\n\n用户回答：\n" + safeAiText(answerContent, 2500);
    }

    private String safeAiText(String text, int maxLength) {
        return StringUtils.abbreviate(StringUtils.trimToEmpty(text), maxLength);
    }

    private QuestionAnswerEvaluateVO parseQuestionAnswerEvaluate(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        JSONObject jsonObject = JSONUtil.parseObj(jsonText);
        QuestionAnswerEvaluateVO evaluateVO = new QuestionAnswerEvaluateVO();
        Integer score = jsonObject.getInt("score");
        ThrowUtils.throwIf(score == null, ErrorCode.SYSTEM_ERROR, "AI 判题未返回分数");
        evaluateVO.setScore(Math.max(0, Math.min(100, score)));
        evaluateVO.setLevel(normalizeEvaluateLevel(jsonObject.getStr("level"), evaluateVO.getScore()));
        evaluateVO.setSummary(StringUtils.defaultIfBlank(
                jsonObject.getStr("summary"),
                "这次回答已经覆盖部分重点，但还可以继续提升结构化表达和关键细节。"
        ));
        evaluateVO.setStrengthList(limitStringList(readStringList(jsonObject, "strengthList", "strengths"), 4));
        evaluateVO.setImprovementList(limitStringList(readStringList(jsonObject, "improvementList", "improvements"), 4));
        evaluateVO.setMissedPointList(limitStringList(readStringList(jsonObject, "missedPointList", "missedPoints"), 4));
        evaluateVO.setFollowUpQuestionList(limitStringList(readStringList(jsonObject, "followUpQuestionList", "followUps"), 4));
        evaluateVO.setReferenceSuggestion(StringUtils.defaultIfBlank(
                firstNonBlank(jsonObject.getStr("referenceSuggestion"), jsonObject.getStr("referenceAdvice")),
                "建议结合参考答案，对照补充缺失知识点并重新组织表达。"
        ));
        if (evaluateVO.getStrengthList().isEmpty()) {
            evaluateVO.setStrengthList(List.of("回答已经体现出一定的作答思路，建议继续保留结构化表达。"));
        }
        if (evaluateVO.getImprovementList().isEmpty()) {
            evaluateVO.setImprovementList(List.of("建议围绕核心原理、关键步骤和场景取舍再补充 2 到 3 个关键点。"));
        }
        if (evaluateVO.getMissedPointList().isEmpty()) {
            evaluateVO.setMissedPointList(List.of("建议对照参考答案，自查是否遗漏了关键原理、边界场景和性能取舍。"));
        }
        if (evaluateVO.getFollowUpQuestionList().isEmpty()) {
            evaluateVO.setFollowUpQuestionList(List.of("如果面试官继续追问，请你补充为什么这样设计以及有哪些替代方案。"));
        }
        return evaluateVO;
    }

    private String buildAnswerEvaluateFocusHint(Question question) {
        String questionText = (StringUtils.defaultString(question.getTitle()) + " " + StringUtils.defaultString(question.getContent()))
                .toLowerCase(Locale.ROOT);
        List<String> focusList = new ArrayList<>();
        if (containsAnyIgnoreCase(questionText, "哪些", "列举", "枚举", "类型", "分类")) {
            focusList.add("先尽量列全核心项");
        }
        if (containsAnyIgnoreCase(questionText, "分别", "各自", "对应")) {
            focusList.add("不要只给总括性结论，要把每一项分别说明");
        }
        if (containsAnyIgnoreCase(questionText, "场景", "用途", "适用", "什么情况下", "应用")) {
            focusList.add("说明每一项适合什么场景或用来解决什么问题");
        }
        if (containsAnyIgnoreCase(questionText, "原理", "机制", "为什么", "底层")) {
            focusList.add("解释为什么这样设计或底层机制是什么");
        }
        if (containsAnyIgnoreCase(questionText, "实现", "怎么做", "如何", "步骤", "流程")) {
            focusList.add("说明具体做法、关键步骤或执行流程");
        }
        if (containsAnyIgnoreCase(questionText, "区别", "对比", "优缺点", "取舍", "选型")) {
            focusList.add("补充比较维度、优缺点或技术取舍");
        }
        if (focusList.isEmpty()) {
            return "";
        }
        return "\n\n本题显式要求至少覆盖：\n- " + String.join("\n- ", focusList);
    }

    private void applyAnswerEvaluateGuard(QuestionAnswerEvaluateVO evaluateVO, Question question, String answerContent) {
        if (evaluateVO == null) {
            return;
        }
        int currentScore = Math.max(0, Math.min(100, evaluateVO.getScore() == null ? 0 : evaluateVO.getScore()));
        SingleQuestionAnswerSignals signals = analyzeSingleQuestionAnswer(question, answerContent);
        int scoreCap = 100;
        List<String> blockingIssues = new ArrayList<>();
        List<String> missingDimensionHints = new ArrayList<>();

        if (signals.getAnswerLength() < 12) {
            scoreCap = Math.min(scoreCap, 28);
            blockingIssues.add("当前回答只有极少几个词，还没有形成可得高分的完整作答。");
        } else if (signals.getAnswerLength() < 24) {
            scoreCap = Math.min(scoreCap, 38);
            blockingIssues.add("当前回答过短，更像关键词罗列，面试里通常只能拿到较低分。");
        } else if (signals.getAnswerLength() < 40 && !signals.isHasExplanation()) {
            scoreCap = Math.min(scoreCap, 46);
            blockingIssues.add("回答虽然提到了一些点，但还没有展开说明这些点分别是什么意思。");
        } else if (signals.getAnswerLength() < 80
                && (!signals.isHasExplanation() || (!signals.isHasStructure() && !signals.isHasUseCaseExpression()))) {
            scoreCap = Math.min(scoreCap, 64);
            blockingIssues.add("当前作答还比较简略，细节和结构都不足以支撑高分。");
        }

        if (signals.isListOnlyAnswer()) {
            scoreCap = Math.min(scoreCap, 42);
            blockingIssues.add("当前回答更像在罗列名词，没有把每一项分别展开说明。");
        }

        if (signals.isRequiresSeparateCoverage() && signals.getAnswerTokenCount() <= 2) {
            scoreCap = Math.min(scoreCap, 45);
            blockingIssues.add("题目明显要求分项作答，但当前覆盖到的点还太少。");
        }

        if (signals.isRequiresUseCase() && !signals.isHasUseCaseExpression()) {
            scoreCap = Math.min(scoreCap, signals.getAnswerLength() < 60 ? 46 : 58);
            blockingIssues.add("题目要求说明应用场景，但当前还没有讲清“什么场景用什么”。");
            missingDimensionHints.add("应用场景 / 适用条件");
        }

        if (signals.isRequiresReasonOrMechanism() && !signals.isHasExplanation()) {
            scoreCap = Math.min(scoreCap, 58);
            blockingIssues.add("题目要求解释原理或原因，但当前回答还停留在结论层。");
            missingDimensionHints.add("原理 / 原因解释");
        }

        if (signals.isRequiresProcedure() && !signals.isHasProcess()) {
            scoreCap = Math.min(scoreCap, 60);
            blockingIssues.add("题目要求说明做法或步骤，但当前没有把流程讲出来。");
            missingDimensionHints.add("步骤 / 流程");
        }

        if (signals.isRequiresComparison() && !signals.isHasComparison()) {
            scoreCap = Math.min(scoreCap, 60);
            blockingIssues.add("题目要求比较或取舍，但当前没有给出比较维度。");
            missingDimensionHints.add("对比维度 / 技术取舍");
        }

        if (signals.getReferenceKeywordCount() >= 6) {
            if (signals.getHitKeywordCount() <= 1) {
                scoreCap = Math.min(scoreCap, 52);
                blockingIssues.add("和参考答案相比，当前覆盖到的关键点还非常少。");
            } else if (signals.getKeywordCoverageRatio() < 0.35d) {
                scoreCap = Math.min(scoreCap, 68);
                blockingIssues.add("当前只覆盖了部分关键点，暂时不适合给到高分。");
            }
        }

        if (blockingIssues.size() >= 2) {
            scoreCap = Math.min(scoreCap, 58);
        }

        if (currentScore > scoreCap) {
            evaluateVO.setScore(scoreCap);
            evaluateVO.setLevel(normalizeEvaluateLevel(null, scoreCap));
            evaluateVO.setSummary(buildGuardedEvaluateSummary(signals, blockingIssues, scoreCap));
        }

        if (!blockingIssues.isEmpty()) {
            evaluateVO.setImprovementList(mergePriorityItems(blockingIssues, evaluateVO.getImprovementList(), 4));
        }
        if (!missingDimensionHints.isEmpty()) {
            List<String> missingPointList = new ArrayList<>(mergePriorityItems(
                    List.of("建议优先补齐这些题目要求的维度：" + String.join("、", new LinkedHashSet<>(missingDimensionHints))),
                    evaluateVO.getMissedPointList(),
                    4
            ));
            evaluateVO.setMissedPointList(missingPointList);
        }
        if (StringUtils.isBlank(evaluateVO.getReferenceSuggestion()) || currentScore > scoreCap) {
            evaluateVO.setReferenceSuggestion(buildGuardedReferenceSuggestion(signals, missingDimensionHints));
        }
    }

    private SingleQuestionAnswerSignals analyzeSingleQuestionAnswer(Question question, String answerContent) {
        String normalizedAnswer = StringUtils.trimToEmpty(answerContent);
        String normalizedQuestion = (StringUtils.defaultString(question.getTitle()) + " " + StringUtils.defaultString(question.getContent()))
                .toLowerCase(Locale.ROOT);
        List<String> referenceKeywordList = buildReferenceKeywordList(question);
        String lowerCaseAnswer = normalizedAnswer.toLowerCase(Locale.ROOT);
        int hitKeywordCount = (int) referenceKeywordList.stream().filter(lowerCaseAnswer::contains).count();
        int referenceKeywordCount = referenceKeywordList.size();
        int answerTokenCount = tokenizeText(answerContent).size();
        boolean hasStructure = containsAnyIgnoreCase(normalizedAnswer, "首先", "然后", "最后", "一是", "二是", "三是", "1.", "2.", "3.", "第一", "第二", "第三");
        boolean hasProcess = containsAnyIgnoreCase(normalizedAnswer, "步骤", "流程", "先", "再", "然后", "最后", "落地", "执行", "实现");
        boolean hasComparison = containsAnyIgnoreCase(normalizedAnswer, "优点", "缺点", "区别", "相比", "对比", "取舍", "成本", "风险", "更适合");
        boolean hasExplanation = containsAnyIgnoreCase(normalizedAnswer, "因为", "所以", "本质", "原理", "机制", "通过", "会", "可以", "用于", "用来", "适合", "保存", "表示", "实现", "支持", "说明", "代表", "负责");
        boolean hasUseCaseExpression = containsAnyIgnoreCase(normalizedAnswer, "用于", "用来", "适合", "场景", "比如", "例如", "常用", "通常", "一般", "保存", "存储", "存", "缓存", "队列", "消息", "对象", "会话", "排行", "计数", "时间线");
        int separatorCount = countOccurrences(normalizedAnswer, ',')
                + countOccurrences(normalizedAnswer, '，')
                + countOccurrences(normalizedAnswer, '、')
                + countOccurrences(normalizedAnswer, ';')
                + countOccurrences(normalizedAnswer, '；')
                + countOccurrences(normalizedAnswer, '/');
        boolean listOnlyAnswer = normalizedAnswer.length() < 60
                && answerTokenCount >= 2
                && separatorCount > 0
                && !hasProcess
                && !hasComparison
                && !hasUseCaseExpression
                && !hasExplanation;

        SingleQuestionAnswerSignals signals = new SingleQuestionAnswerSignals();
        signals.setAnswerLength(normalizedAnswer.length());
        signals.setAnswerTokenCount(answerTokenCount);
        signals.setHasStructure(hasStructure);
        signals.setHasProcess(hasProcess);
        signals.setHasComparison(hasComparison);
        signals.setHasExplanation(hasExplanation);
        signals.setHasUseCaseExpression(hasUseCaseExpression);
        signals.setListOnlyAnswer(listOnlyAnswer);
        signals.setHitKeywordCount(hitKeywordCount);
        signals.setReferenceKeywordCount(referenceKeywordCount);
        signals.setKeywordCoverageRatio(referenceKeywordCount == 0 ? 0d : hitKeywordCount * 1.0d / referenceKeywordCount);
        signals.setRequiresSeparateCoverage(containsAnyIgnoreCase(normalizedQuestion, "分别", "各自", "对应", "逐个", "依次"));
        signals.setRequiresUseCase(containsAnyIgnoreCase(normalizedQuestion, "场景", "用途", "应用", "适用", "什么时候用", "什么场景"));
        signals.setRequiresReasonOrMechanism(containsAnyIgnoreCase(normalizedQuestion, "原理", "机制", "为什么", "底层"));
        signals.setRequiresProcedure(containsAnyIgnoreCase(normalizedQuestion, "如何", "怎么", "实现", "步骤", "流程"));
        signals.setRequiresComparison(containsAnyIgnoreCase(normalizedQuestion, "区别", "对比", "优缺点", "取舍", "选型", "比较"));
        return signals;
    }

    private String buildGuardedEvaluateSummary(SingleQuestionAnswerSignals signals, List<String> blockingIssues, int score) {
        if (signals.isListOnlyAnswer() || signals.getAnswerLength() < 24) {
            return "当前回答更像在列关键词，还没有形成一段能在面试里拿高分的完整作答。建议把每个点分别解释清楚，再补上题目要求的场景、原理或做法。";
        }
        if (!blockingIssues.isEmpty() && score <= 58) {
            return "当前回答已经有一点方向，但离合格的面试作答还有明显距离。主要问题不是完全答错，而是覆盖不全、展开不够，很多关键维度还没讲出来。";
        }
        return "这次回答已经碰到部分重点，但还没有把题目要求的关键维度讲完整。建议先补齐漏答点，再用更结构化的方式重新组织一遍答案。";
    }

    private String buildGuardedReferenceSuggestion(SingleQuestionAnswerSignals signals, List<String> missingDimensionHints) {
        if (signals.isListOnlyAnswer() || signals.getAnswerLength() < 24) {
            return "建议不要只列关键词。可以按“先列全核心项 -> 每一项各自是什么意思 -> 分别适用在什么场景 -> 最后补一句取舍”这个顺序重答一遍。";
        }
        if (!missingDimensionHints.isEmpty()) {
            return "建议先补齐这些题目要求的维度：" + String.join("、", new LinkedHashSet<>(missingDimensionHints))
                    + "，再对照参考答案检查是否覆盖了关键原理、步骤和适用场景。";
        }
        return "建议对照参考答案重新补齐关键点，并把“是什么、怎么做、什么场景用、有什么取舍”这几层意思明确拆开。";
    }

    private List<String> mergePriorityItems(List<String> priorityItems, List<String> originalItems, int limit) {
        LinkedHashSet<String> mergedSet = new LinkedHashSet<>();
        priorityItems.stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .forEach(mergedSet::add);
        if (originalItems != null) {
            originalItems.stream()
                    .map(StringUtils::trimToEmpty)
                    .filter(StringUtils::isNotBlank)
                    .forEach(mergedSet::add);
        }
        return mergedSet.stream().limit(limit).collect(Collectors.toList());
    }

    private int countOccurrences(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private String extractAudioAnswerText(MultipartFile audioFile, Question question) {
        ThrowUtils.throwIf(audioFile.getSize() > MAX_AUDIO_FILE_SIZE, ErrorCode.PARAMS_ERROR, "音频文件过大，请控制在 8MB 以内");
        String fileSuffix = StringUtils.lowerCase(FileUtil.getSuffix(audioFile.getOriginalFilename()));
        ThrowUtils.throwIf(StringUtils.isBlank(fileSuffix) || !SUPPORTED_AUDIO_FILE_SUFFIX_SET.contains(fileSuffix),
                ErrorCode.PARAMS_ERROR, "仅支持 webm、wav、mp3、m4a、mp4、ogg 音频");
        try {
            String transcript = aiManager.transcribeAudio(
                    audioFile.getOriginalFilename(),
                    audioFile.getBytes(),
                    audioFile.getContentType(),
                    "zh",
                    buildAudioEvaluatePrompt(question)
            );
            return sanitizeTranscript(transcript);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("题目语音判题转写失败，questionId={}", question.getId(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音转写失败，请稍后重试");
        }
    }

    private String buildAudioEvaluatePrompt(Question question) {
        return "请把这段中文技术答题语音准确转写成简洁文本，保留技术名词、英文缩写、数字指标。"
                + "题目标题：" + StringUtils.defaultIfBlank(question.getTitle(), "未命名题目")
                + "。题目难度：" + StringUtils.defaultIfBlank(question.getDifficulty(), "未设置")
                + "。不要补充解释，不要总结，只返回转写结果。";
    }

    private String sanitizeTranscript(String transcript) {
        return StringUtils.trimToEmpty(transcript)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private List<String> readStringList(JSONObject jsonObject, String... keyCandidates) {
        for (String key : keyCandidates) {
            JSONArray jsonArray = jsonObject.getJSONArray(key);
            if (jsonArray != null && !jsonArray.isEmpty()) {
                return JSONUtil.toList(jsonArray, String.class).stream()
                        .map(StringUtils::trimToEmpty)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    private List<String> limitStringList(List<String> source, int limit) {
        return source.stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String normalizeEvaluateLevel(String level, int score) {
        String normalizedLevel = StringUtils.trimToEmpty(level);
        if (Set.of("优秀", "良好", "合格", "待加强").contains(normalizedLevel)) {
            return normalizedLevel;
        }
        if (score >= 85) {
            return "优秀";
        }
        if (score >= 70) {
            return "良好";
        }
        if (score >= 55) {
            return "合格";
        }
        return "待加强";
    }

    private QuestionAnswerEvaluateVO buildHeuristicEvaluateResult(Question question, String answerContent) {
        String normalizedAnswer = StringUtils.trimToEmpty(answerContent);
        String lowerCaseAnswer = normalizedAnswer.toLowerCase(Locale.ROOT);
        int answerLength = normalizedAnswer.length();
        List<String> referenceKeywordList = buildReferenceKeywordList(question);
        List<String> hitKeywordList = referenceKeywordList.stream()
                .filter(lowerCaseAnswer::contains)
                .collect(Collectors.toList());
        List<String> missedKeywordList = referenceKeywordList.stream()
                .filter(keyword -> !lowerCaseAnswer.contains(keyword))
                .limit(4)
                .collect(Collectors.toList());
        boolean hasStructure = containsAnyIgnoreCase(normalizedAnswer, "首先", "然后", "最后", "一是", "二是", "三是", "1.", "2.", "3.");
        boolean hasScenario = containsAnyIgnoreCase(normalizedAnswer, "例如", "比如", "场景", "案例", "实践", "线上");
        boolean hasTradeOff = containsAnyIgnoreCase(normalizedAnswer, "优点", "缺点", "权衡", "取舍", "成本", "风险");
        int baseScore = answerLength >= 240 ? 62 : answerLength >= 120 ? 52 : answerLength >= 60 ? 42 : 30;
        int keywordScore = referenceKeywordList.isEmpty()
                ? 12
                : Math.min(26, (int) Math.round(hitKeywordList.size() * 26.0 / referenceKeywordList.size()));
        int structureScore = hasStructure ? 6 : 0;
        int sceneScore = hasScenario ? 4 : 0;
        int tradeOffScore = hasTradeOff ? 4 : 0;
        int score = Math.max(25, Math.min(95, baseScore + keywordScore + structureScore + sceneScore + tradeOffScore));

        List<String> strengthList = new ArrayList<>();
        if (answerLength >= 120) {
            strengthList.add("回答篇幅比较充实，已经具备继续展开细节的基础。");
        }
        if (!hitKeywordList.isEmpty()) {
            strengthList.add("命中了 " + hitKeywordList.size() + " 个核心关键词，例如：" + String.join("、", hitKeywordList.stream().limit(3).collect(Collectors.toList())) + "。");
        }
        if (hasStructure) {
            strengthList.add("回答中带有结构化表达痕迹，便于面试官快速理解你的思路。");
        }
        if (hasScenario || hasTradeOff) {
            strengthList.add("已经开始补充场景或取舍分析，这会让回答更贴近真实面试。");
        }
        if (strengthList.isEmpty()) {
            strengthList.add("已经尝试直接作答，这本身就是很好的训练起点。");
        }

        List<String> improvementList = new ArrayList<>();
        if (answerLength < 120) {
            improvementList.add("当前回答偏短，建议至少补齐“原理 + 步骤 + 适用场景”三部分。");
        }
        if (!hasStructure) {
            improvementList.add("建议用“首先 / 然后 / 最后”或分点形式组织答案，避免想到哪里答到哪里。");
        }
        if (!hasScenario) {
            improvementList.add("可以加入一个具体场景或例子，说明这道题在工程实践中怎么落地。");
        }
        if (!hasTradeOff) {
            improvementList.add("建议补充方案的优缺点、边界条件或性能取舍，这通常是面试追问重点。");
        }
        if (!missedKeywordList.isEmpty()) {
            improvementList.add("可以重点补充这些知识点：" + String.join("、", missedKeywordList));
        }
        improvementList = limitStringList(improvementList, 4);

        List<String> followUpQuestionList = new ArrayList<>();
        String title = StringUtils.defaultIfBlank(question.getTitle(), "这道题");
        followUpQuestionList.add("如果把“" + title + "”放到真实项目里，你会优先关注哪些风险或边界场景？");
        if (!missedKeywordList.isEmpty()) {
            followUpQuestionList.add("请继续展开说明“" + missedKeywordList.get(0) + "”在这道题里的作用和实现细节。");
        }
        followUpQuestionList.add("如果面试官要求你比较两种可选方案，你会怎么做技术取舍？");
        followUpQuestionList = limitStringList(followUpQuestionList, 4);

        QuestionAnswerEvaluateVO evaluateVO = new QuestionAnswerEvaluateVO();
        evaluateVO.setScore(score);
        evaluateVO.setLevel(normalizeEvaluateLevel(null, score));
        evaluateVO.setSummary(buildHeuristicSummary(score, answerLength, hitKeywordList.size(), referenceKeywordList.size()));
        evaluateVO.setStrengthList(strengthList);
        evaluateVO.setImprovementList(improvementList);
        evaluateVO.setMissedPointList(missedKeywordList.isEmpty()
                ? List.of("建议对照参考答案进一步补充关键原理、步骤拆解和工程场景。")
                : missedKeywordList);
        evaluateVO.setFollowUpQuestionList(followUpQuestionList);
        evaluateVO.setReferenceSuggestion("建议先补齐漏答点，再对照推荐答案检查是否覆盖了核心原理、实现步骤和适用场景。");
        evaluateVO.setAnalysisSource("heuristic");
        return evaluateVO;
    }

    private String buildHeuristicSummary(int score, int answerLength, int hitCount, int keywordCount) {
        if (score >= 80) {
            return "你的回答已经比较接近面试中的合格表达，核心要点覆盖较好，下一步重点是把细节和取舍讲得更扎实。";
        }
        if (score >= 65) {
            return "你的回答已经具备一定基础，但还可以进一步补齐关键知识点，并把结构和场景表达得更清楚。";
        }
        String keywordHint = keywordCount > 0
                ? " 当前回答长度约 " + answerLength + " 字，命中核心关键词 " + hitCount + "/" + keywordCount + "。"
                : " 当前回答长度约 " + answerLength + " 字，建议重点补充关键原理、步骤和场景。";
        return "当前回答还处于初步作答阶段，建议先补充核心原理、实现步骤和应用场景，再重新组织一遍答案。"
                + keywordHint;
    }

    private List<String> buildReferenceKeywordList(Question question) {
        LinkedHashSet<String> keywordSet = new LinkedHashSet<>();
        List<String> tagList = JSONUtil.toList(StringUtils.defaultIfBlank(question.getTags(), "[]"), String.class);
        tagList.stream()
                .map(StringUtils::trimToEmpty)
                .filter(tag -> tag.length() >= 2)
                .forEach(keywordSet::add);
        tokenizeText(question.getTitle()).stream().limit(4).forEach(keywordSet::add);
        tokenizeText(question.getAnswer()).stream().limit(10).forEach(keywordSet::add);
        return new ArrayList<>(keywordSet).stream().limit(12).collect(Collectors.toList());
    }

    private List<String> tokenizeText(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(text.split("[\\s,，。；;：:\\n\\r\\t()（）【】\\[\\]、/]+"))
                .map(StringUtils::trimToEmpty)
                .filter(token -> token.length() >= 2 && token.length() <= 24)
                .filter(token -> !StringUtils.isNumeric(token))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean containsAnyIgnoreCase(String text, String... candidates) {
        String normalizedText = StringUtils.defaultString(text).toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedText::contains);
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void logRecommendationExposure(Long userId, String source, List<QuestionVO> questionVOList) {
        if (questionVOList == null || questionVOList.isEmpty()) {
            return;
        }
        List<Long> questionIdList = questionVOList.stream()
                .map(QuestionVO::getId)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toList());
        questionRecommendLogService.logExposure(userId, source, questionIdList);
    }

    private List<AiGeneratedQuestion> parseAiGeneratedQuestions(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        if (jsonText.startsWith("[")) {
            return JSONUtil.toList(jsonText, AiGeneratedQuestion.class);
        }
        if (jsonText.startsWith("{")) {
            JSONObject jsonObject = JSONUtil.parseObj(jsonText);
            JSONArray questions = jsonObject.getJSONArray("questions");
            ThrowUtils.throwIf(questions == null || questions.isEmpty(), ErrorCode.SYSTEM_ERROR, "AI 未返回题目数组");
            return JSONUtil.toList(questions, AiGeneratedQuestion.class);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回内容不是合法 JSON");
    }

    private String normalizeAiJson(String rawContent) {
        String content = StringUtils.trimToEmpty(rawContent);
        if (content.startsWith("```")) {
            int firstLineBreak = content.indexOf('\n');
            if (firstLineBreak > -1) {
                content = content.substring(firstLineBreak + 1);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.lastIndexOf("```"));
            }
        }
        return content.trim();
    }

    private List<AiGeneratedAnswerPlan> parseAiGeneratedAnswerPlans(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        if (jsonText.startsWith("[")) {
            return JSONUtil.toList(jsonText, AiGeneratedAnswerPlan.class);
        }
        if (jsonText.startsWith("{")) {
            JSONObject jsonObject = JSONUtil.parseObj(jsonText);
            JSONArray plans = jsonObject.getJSONArray("plans");
            ThrowUtils.throwIf(plans == null || plans.isEmpty(), ErrorCode.SYSTEM_ERROR, "AI 未返回答案拆分方案");
            return JSONUtil.toList(plans, AiGeneratedAnswerPlan.class);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回的答案拆分方案不是合法 JSON");
    }

    private List<AiGeneratedAnswerResult> parseAiGeneratedAnswerResults(String rawContent) {
        String jsonText = normalizeAiJson(rawContent);
        if (jsonText.startsWith("[")) {
            return JSONUtil.toList(jsonText, AiGeneratedAnswerResult.class);
        }
        if (jsonText.startsWith("{")) {
            JSONObject jsonObject = JSONUtil.parseObj(jsonText);
            JSONArray answers = jsonObject.getJSONArray("answers");
            ThrowUtils.throwIf(answers == null || answers.isEmpty(), ErrorCode.SYSTEM_ERROR, "AI 未返回参考答案");
            return JSONUtil.toList(answers, AiGeneratedAnswerResult.class);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回的参考答案不是合法 JSON");
    }

    private String normalizeGeneratedDifficulty(String difficulty) {
        String normalized = StringUtils.trimToEmpty(difficulty);
        if (StringUtils.isBlank(normalized)) {
            return QuestionConstant.DIFFICULTY_MEDIUM;
        }
        if (QuestionConstant.ALLOWED_DIFFICULTY_SET.contains(normalized)) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.contains("简") || lower.contains("easy") || lower.contains("junior") || lower.contains("basic")) {
            return QuestionConstant.DIFFICULTY_EASY;
        }
        if (normalized.contains("难") || lower.contains("hard") || lower.contains("senior") || lower.contains("advanced")) {
            return QuestionConstant.DIFFICULTY_HARD;
        }
        return QuestionConstant.DIFFICULTY_MEDIUM;
    }

    @Data
    private static class AiGeneratedQuestion {
        private String title;
        private String content;
        private List<String> tags;
        private String answer;
        private String difficulty;
    }

    @Data
    private static class SingleQuestionAnswerSignals {
        private int answerLength;
        private int answerTokenCount;
        private boolean hasStructure;
        private boolean hasProcess;
        private boolean hasComparison;
        private boolean hasExplanation;
        private boolean hasUseCaseExpression;
        private boolean listOnlyAnswer;
        private int hitKeywordCount;
        private int referenceKeywordCount;
        private double keywordCoverageRatio;
        private boolean requiresSeparateCoverage;
        private boolean requiresUseCase;
        private boolean requiresReasonOrMechanism;
        private boolean requiresProcedure;
        private boolean requiresComparison;
    }

    @Data
    private static class AiGeneratedAnswerPlan {
        private Integer index;
        private String questionType;
        private List<String> partTitles;
        private List<String> mustCoverPoints;
        private String guidance;
        private String answerStyle;
    }

    @Data
    private static class AiGeneratedAnswerResult {
        private Integer index;
        private String answer;
    }

    @Data
    private static class AiGeneratedAnswerReview {
        private Boolean pass;
        private Integer score;
        private List<String> problems;
        private String rewriteAnswer;
    }

}
