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
            } catch (Exception e) {
                log.warn("题目搜索 ES 不可用，已降级到数据库查询，searchText={}", questionQueryRequest.getSearchText(), e);
                questionPage = questionService.listQuestionByPage(questionQueryRequest);
                fallbackToDb = true;
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
     * AI 批量生成题目
     *
     * @param questionAiGenerateRequest
     * @return
     */
    @PostMapping("/ai/generate/question")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> batchGenerateQuestionsByAi(@RequestBody QuestionAIGenerateRequest questionAiGenerateRequest,
                                                            HttpServletRequest request) {
        ThrowUtils.throwIf(questionAiGenerateRequest == null, ErrorCode.PARAMS_ERROR);
        String questionType = questionAiGenerateRequest.getQuestionType();
        Integer number = questionAiGenerateRequest.getNumber();
        ThrowUtils.throwIf(StringUtils.isBlank(questionType), ErrorCode.PARAMS_ERROR, "题目方向不能为空");
        ThrowUtils.throwIf(number == null || number < 1 || number > 20, ErrorCode.PARAMS_ERROR, "生成数量需在 1 到 20 之间");

        // 获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 构造 Prompt
        String systemPrompt = "你是一位资深技术面试官，负责为面试题库生产可直接入库的高质量题目。"
                + "请严格输出 JSON 数组，不要输出 Markdown 代码块，不要输出额外解释。"
                + "数组中的每个对象必须包含 title、content、tags、answer 四个字段。"
                + "其中 tags 必须是字符串数组，title 简洁明确，content 描述题目要求，answer 给出结构化参考答案。";
        String userPrompt = String.format("知识点：%s，数量：%d", questionType, number);

        // 调用 AI
        String result = aiManager.doChat(systemPrompt, userPrompt);

        try {
            List<AiGeneratedQuestion> generatedQuestionList = parseAiGeneratedQuestions(result);
            int successCount = 0;
            for (AiGeneratedQuestion generatedQuestion : generatedQuestionList) {
                if (StringUtils.isAnyBlank(generatedQuestion.getTitle(), generatedQuestion.getContent(), generatedQuestion.getAnswer())) {
                    continue;
                }
                Question question = new Question();
                question.setTitle(generatedQuestion.getTitle().trim());
                question.setContent(generatedQuestion.getContent().trim());
                question.setAnswer(generatedQuestion.getAnswer().trim());
                if (generatedQuestion.getTags() != null) {
                    question.setTags(JSONUtil.toJsonStr(generatedQuestion.getTags()));
                }
                question.setUserId(loginUser.getId());
                question.setReviewStatus(QuestionConstant.REVIEW_STATUS_APPROVED);
                questionService.validQuestion(question, true);
                questionService.save(question);
                questionService.syncQuestionToEs(question);
                tagSyncService.syncQuestionTags(null, question.getTags());
                successCount++;
            }
            ThrowUtils.throwIf(successCount == 0, ErrorCode.SYSTEM_ERROR, "AI 未生成可保存的题目");
            return ResultUtils.success(successCount);
        } catch (Exception e) {
            log.error("AI 结果解析失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成数据格式不正确");
        }
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
        try {
            String aiContent = aiManager.doChat(buildAnswerEvaluateSystemPrompt(), buildAnswerEvaluateUserPrompt(question, answerContent));
            QuestionAnswerEvaluateVO aiEvaluateResult = parseQuestionAnswerEvaluate(aiContent);
            aiEvaluateResult.setAnalysisSource("ai");
            return aiEvaluateResult;
        } catch (Exception e) {
            log.warn("AI 判题失败，已自动切换到本地兜底分析，questionId={}", question.getId(), e);
            return buildHeuristicEvaluateResult(question, answerContent);
        }
    }

    private String buildAnswerEvaluateSystemPrompt() {
        return "你是一位严格但鼓励式的技术面试官，请对候选人的单题作答做结构化评估。"
                + "请严格输出 JSON 对象，不要输出 Markdown 代码块，不要输出额外解释。"
                + "JSON 必须包含 score、level、summary、strengthList、improvementList、missedPointList、followUpQuestionList、referenceSuggestion 八个字段。"
                + "score 为 0 到 100 的整数；level 只能是“优秀”“良好”“合格”“待加强”之一；"
                + "strengthList、improvementList、missedPointList、followUpQuestionList 都必须是字符串数组，每个数组给出 2 到 4 条内容。"
                + "请重点对比标准答案与用户回答之间的覆盖度、结构性、表达清晰度和技术深度。";
    }

    private String buildAnswerEvaluateUserPrompt(Question question, String answerContent) {
        List<String> tagList = JSONUtil.toList(StringUtils.defaultIfBlank(question.getTags(), "[]"), String.class);
        return "题目标题：\n" + StringUtils.defaultString(question.getTitle())
                + "\n\n题目难度：\n" + StringUtils.defaultIfBlank(question.getDifficulty(), "未设置")
                + "\n\n题目标签：\n" + String.join("、", tagList)
                + "\n\n题目内容：\n" + safeAiText(question.getContent(), 2500)
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

    @Data
    private static class AiGeneratedQuestion {
        private String title;
        private String content;
        private List<String> tags;
        private String answer;
    }

}
