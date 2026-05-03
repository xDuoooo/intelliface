package com.xduo.springbootinit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.DeleteRequest;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.SystemAccessManager;
import com.xduo.springbootinit.model.dto.comment.CommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentAddRequest;
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReportRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReviewRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.CommentVO;
import com.xduo.springbootinit.model.vo.CommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.UserCommentActivityVO;
import com.xduo.springbootinit.service.QuestionCommentService;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 题目评论接口
 */
@RestController
@RequestMapping("/question/comment")
@Slf4j
public class QuestionCommentController {

    @Resource
    private QuestionCommentService questionCommentService;

    @Resource
    private UserService userService;

    @Resource
    private SystemAccessManager systemAccessManager;

    /**
     * 发表评论（含回复）
     */
    @PostMapping("/add")
    public BaseResponse<CommentSubmitResultVO> addComment(@RequestBody CommentAddRequest request,
                                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        CommentSubmitResultVO result = questionCommentService.addComment(request, loginUser, httpRequest);
        return ResultUtils.success(result);
    }

    /**
     * 删除评论（本人或管理员）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = questionCommentService.deleteComment(deleteRequest.getId(), loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 分页获取评论树（含子评论）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<CommentVO>> listCommentVOByPage(@RequestBody CommentQueryRequest request,
                                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestQuestionAccessAllowed(httpRequest);
        // 最多每页 50 条
        ThrowUtils.throwIf(request.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        Page<CommentVO> page = questionCommentService.listCommentVOByPage(request, httpRequest);
        return ResultUtils.success(page);
    }

    /**
     * 点赞 / 取消点赞
     */
    @PostMapping("/like")
    public BaseResponse<Map<String, Object>> likeComment(@RequestParam Long commentId,
                                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        Map<String, Object> result = questionCommentService.likeComment(commentId, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 获取我点赞过的评论
     */
    @PostMapping("/my/liked/page/vo")
    public BaseResponse<Page<UserCommentActivityVO>> listMyLikedCommentVOByPage(
            @RequestBody CommentActivityQueryRequest request,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(questionCommentService.listMyLikedCommentVOByPage(request, loginUser));
    }

    /**
     * 获取我回复过的评论
     */
    @PostMapping("/my/replied/page/vo")
    public BaseResponse<Page<UserCommentActivityVO>> listMyReplyCommentVOByPage(
            @RequestBody CommentActivityQueryRequest request,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(questionCommentService.listMyReplyCommentVOByPage(request, loginUser));
    }

    /**
     * 举报评论
     */
    @PostMapping("/report")
    public BaseResponse<Boolean> reportComment(@RequestBody CommentReportRequest request,
                                               HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = questionCommentService.reportComment(request, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 置顶 / 取消置顶（仅管理员）
     */
    @PostMapping("/pin")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> pinComment(@RequestParam Long commentId,
                                             @RequestParam(defaultValue = "true") boolean pinned) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        boolean result = questionCommentService.pinComment(commentId, pinned);
        return ResultUtils.success(result);
    }

    /**
     * 设为 / 取消官方解答（仅管理员）
     */
    @PostMapping("/official")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> setOfficialAnswer(@RequestParam Long commentId,
                                                    @RequestParam(defaultValue = "true") boolean official) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        boolean result = questionCommentService.setOfficialAnswer(commentId, official);
        return ResultUtils.success(result);
    }

    /**
     * 后台分页获取评论
     */
    @PostMapping("/admin/list/page/vo")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<CommentVO>> listAdminCommentVOByPage(@RequestBody CommentAdminQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        return ResultUtils.success(questionCommentService.listAdminCommentVOByPage(request));
    }

    /**
     * 审核评论（仅管理员）
     */
    @PostMapping("/review")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewComment(@RequestBody CommentReviewRequest request,
                                               HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User adminUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(questionCommentService.reviewComment(request, adminUser));
    }
}
