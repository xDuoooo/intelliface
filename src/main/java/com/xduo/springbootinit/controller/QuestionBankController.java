package com.xduo.springbootinit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
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
import com.xduo.springbootinit.constant.QuestionBankConstant;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.SystemAccessManager;
import com.xduo.springbootinit.model.dto.question.QuestionQueryRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankAddRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankEditRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankQueryRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankReviewRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankSubmitReviewRequest;
import com.xduo.springbootinit.model.dto.questionbank.QuestionBankUpdateRequest;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionBank;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.QuestionBankVO;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.QuestionBankService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 题库接口
 */
@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    QuestionService questionService;
    @Resource
    private UserService userService;
    @Resource
    private NotificationService notificationService;

    @Resource
    private SystemAccessManager systemAccessManager;

    /**
     * 创建题库
     *
     * @param questionBankAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankAddRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, true);
        User loginUser = userService.getLoginUser(request);
        questionBank.setUserId(loginUser.getId());
        questionBank.setReviewStatus(userService.isAdmin(loginUser)
                ? QuestionBankConstant.REVIEW_STATUS_APPROVED
                : QuestionBankConstant.REVIEW_STATUS_PRIVATE);
        // 写入数据库
        boolean result = questionBankService.save(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newQuestionBankId = questionBank.getId();
        return ResultUtils.success(newQuestionBankId);
    }

    /**
     * 删除题库
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest,
                                                    HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestionBank.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionBankService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑题库（用户使用）
     *
     * @param questionBankEditRequest 编辑请求
     * @param request 请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankEditRequest == null || questionBankEditRequest.getId() == null
                || questionBankEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long id = questionBankEditRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldQuestionBank.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankEditRequest, questionBank);
        questionBankService.validQuestionBank(questionBank, false);
        boolean isAdmin = userService.isAdmin(loginUser);
        int oldReviewStatus = getQuestionBankReviewStatus(oldQuestionBank);
        LambdaUpdateWrapper<QuestionBank> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionBank::getId, id)
                .set(QuestionBank::getTitle, questionBank.getTitle())
                .set(QuestionBank::getDescription, questionBank.getDescription())
                .set(QuestionBank::getPicture, questionBank.getPicture());
        if (!isAdmin && QuestionBankConstant.REVIEW_STATUS_APPROVED == oldReviewStatus) {
            updateWrapper.set(QuestionBank::getReviewStatus, QuestionBankConstant.REVIEW_STATUS_PENDING)
                    .set(QuestionBank::getReviewMessage, null)
                    .set(QuestionBank::getReviewUserId, null)
                    .set(QuestionBank::getReviewTime, null);
        }
        boolean result = questionBankService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题库（仅管理员可用）
     *
     * @param questionBankUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        if (questionBankUpdateRequest == null || questionBankUpdateRequest.getId() == null || questionBankUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankUpdateRequest, questionBank);
        // 数据校验
        questionBankService.validQuestionBank(questionBank, false);
        // 判断是否存在
        long id = questionBankUpdateRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionBankService.updateById(questionBank);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = questionBankQueryRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getLoginUserPermitNull(request);
        ThrowUtils.throwIf(!questionBankService.canViewQuestionBank(questionBank, loginUser), ErrorCode.NOT_FOUND_ERROR);
        // 查询题库封装类
        QuestionBankVO questionBankVO = questionBankService.getQuestionBankVO(questionBank, request);
        // 是否要关联查询题库下的题目列表
        boolean needQueryQuestionList = questionBankQueryRequest.isNeedQueryQuestionList();
        if (needQueryQuestionList) {
            systemAccessManager.ensureGuestQuestionAccessAllowed(request);
            QuestionQueryRequest questionQueryRequest = new QuestionQueryRequest();
            questionQueryRequest.setQuestionBankId(id);
            questionQueryRequest.setCurrent(questionBankQueryRequest.getCurrent());
            questionQueryRequest.setPageSize(questionBankQueryRequest.getPageSize());
            questionQueryRequest.setSortField(questionBankQueryRequest.getSortField());
            questionQueryRequest.setSortOrder(questionBankQueryRequest.getSortOrder());
            if (isApprovedQuestionBank(questionBank)
                    || loginUser == null
                    || (!userService.isAdmin(loginUser) && !loginUser.getId().equals(questionBank.getUserId()))) {
                questionQueryRequest.setReviewStatus(com.xduo.springbootinit.constant.QuestionConstant.REVIEW_STATUS_APPROVED);
            }
            Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
            questionBankVO.setQuestionPage(questionPage);
        }
        // 获取封装类
        return ResultUtils.success(questionBankVO);
    }


    /**
     * 分页获取题库列表（仅管理员可用）
     *
     * @param questionBankQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 100, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        // 查询数据库
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankPage);
    }

    /**
     * 分页获取题库列表（封装类）
     *
     * @param questionBankQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        Entry entry = null;
        String remoteAddr = request.getRemoteAddr();
        try {
            entry = SphU.entry("listQuestionBankVOByPage", EntryType.IN, 1, remoteAddr);
            // 限制爬虫
            ThrowUtils.throwIf(current < 1 || size < 1 || size > 100, ErrorCode.PARAMS_ERROR, "分页参数不合法");
            questionBankQueryRequest.setReviewStatus(QuestionBankConstant.REVIEW_STATUS_APPROVED);
            // 查询数据库
            Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                    questionBankService.getQueryWrapper(questionBankQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
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
                return ResultUtils.success(new Page<>());
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
     * 分页获取当前用户创建的题库列表
     *
     * @param questionBankQueryRequest 查询请求
     * @param request 请求
     * @return 题库分页
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        questionBankQueryRequest.setUserId(loginUser.getId());
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage, request));
    }

    /**
     * 用户主动提交题库审核
     */
    @PostMapping("/submit/review")
    public BaseResponse<Boolean> submitQuestionBankReview(@RequestBody QuestionBankSubmitReviewRequest submitReviewRequest,
                                                          HttpServletRequest request) {
        ThrowUtils.throwIf(submitReviewRequest == null || submitReviewRequest.getId() == null || submitReviewRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        QuestionBank oldQuestionBank = questionBankService.getById(submitReviewRequest.getId());
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldQuestionBank.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        int reviewStatus = getQuestionBankReviewStatus(oldQuestionBank);
        ThrowUtils.throwIf(QuestionBankConstant.REVIEW_STATUS_APPROVED == reviewStatus, ErrorCode.OPERATION_ERROR, "题库已公开，无需重复提交审核");
        ThrowUtils.throwIf(QuestionBankConstant.REVIEW_STATUS_PENDING == reviewStatus, ErrorCode.OPERATION_ERROR, "题库已在审核中，请耐心等待");
        LambdaUpdateWrapper<QuestionBank> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionBank::getId, oldQuestionBank.getId())
                .set(QuestionBank::getReviewStatus, QuestionBankConstant.REVIEW_STATUS_PENDING)
                .set(QuestionBank::getReviewMessage, null)
                .set(QuestionBank::getReviewUserId, null)
                .set(QuestionBank::getReviewTime, null);
        boolean result = questionBankService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 审核题库（仅管理员）
     */
    @PostMapping("/review")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewQuestionBank(@RequestBody QuestionBankReviewRequest questionBankReviewRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(questionBankReviewRequest == null || questionBankReviewRequest.getId() == null || questionBankReviewRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Integer reviewStatus = questionBankReviewRequest.getReviewStatus();
        ThrowUtils.throwIf(reviewStatus == null || !QuestionBankConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(reviewStatus),
                ErrorCode.PARAMS_ERROR,
                "审核状态不合法");
        String reviewMessage = StringUtils.trimToNull(questionBankReviewRequest.getReviewMessage());
        if (QuestionBankConstant.REVIEW_STATUS_REJECTED == reviewStatus) {
            ThrowUtils.throwIf(StringUtils.isBlank(reviewMessage), ErrorCode.PARAMS_ERROR, "驳回时请填写审核意见");
        }
        ThrowUtils.throwIf(StringUtils.length(reviewMessage) > 512, ErrorCode.PARAMS_ERROR, "审核意见过长");
        QuestionBank oldQuestionBank = questionBankService.getById(questionBankReviewRequest.getId());
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(QuestionBankConstant.REVIEW_STATUS_PRIVATE == getQuestionBankReviewStatus(oldQuestionBank),
                ErrorCode.OPERATION_ERROR,
                "作者尚未提交公开审核");
        User loginUser = userService.getLoginUser(request);
        LambdaUpdateWrapper<QuestionBank> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionBank::getId, oldQuestionBank.getId())
                .set(QuestionBank::getReviewStatus, reviewStatus)
                .set(QuestionBank::getReviewMessage, reviewMessage)
                .set(QuestionBank::getReviewUserId, loginUser.getId())
                .set(QuestionBank::getReviewTime, new Date());
        boolean result = questionBankService.update(null, updateWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        QuestionBank latestQuestionBank = questionBankService.getById(oldQuestionBank.getId());
        notificationService.sendNotification(
                latestQuestionBank.getUserId(),
                QuestionBankConstant.REVIEW_STATUS_APPROVED == reviewStatus ? "你的题库已审核通过" : "你的题库未通过审核",
                buildQuestionBankReviewNotificationContent(latestQuestionBank, reviewStatus, reviewMessage),
                "question_bank_review",
                latestQuestionBank.getId()
        );
        return ResultUtils.success(true);
    }

    private int getQuestionBankReviewStatus(QuestionBank questionBank) {
        return questionBank.getReviewStatus() == null ? QuestionBankConstant.REVIEW_STATUS_APPROVED : questionBank.getReviewStatus();
    }

    private boolean isApprovedQuestionBank(QuestionBank questionBank) {
        return questionBank != null
                && (questionBank.getReviewStatus() == null
                || QuestionBankConstant.REVIEW_STATUS_APPROVED == questionBank.getReviewStatus());
    }

    private String buildQuestionBankReviewNotificationContent(QuestionBank questionBank, Integer reviewStatus, String reviewMessage) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("题库《").append(StringUtils.defaultIfBlank(questionBank.getTitle(), "未命名题库")).append("》");
        if (QuestionBankConstant.REVIEW_STATUS_APPROVED == reviewStatus) {
            contentBuilder.append("已通过审核，现在会出现在公开题库列表中。");
        } else {
            contentBuilder.append("未通过审核，请根据审核意见调整后重新提交。");
        }
        if (StringUtils.isNotBlank(reviewMessage)) {
            contentBuilder.append(" 审核意见：").append(reviewMessage);
        }
        return contentBuilder.toString();
    }
}
