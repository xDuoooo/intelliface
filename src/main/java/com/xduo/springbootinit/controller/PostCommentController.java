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
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAddRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentReviewRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.PostCommentActivityVO;
import com.xduo.springbootinit.model.vo.PostCommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.PostCommentVO;
import com.xduo.springbootinit.service.PostCommentService;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 帖子评论接口
 */
@RestController
@RequestMapping("/post/comment")
@Slf4j
public class PostCommentController {

    @Resource
    private PostCommentService postCommentService;

    @Resource
    private UserService userService;

    @Resource
    private SystemAccessManager systemAccessManager;

    @PostMapping("/add")
    public BaseResponse<PostCommentSubmitResultVO> addComment(@RequestBody PostCommentAddRequest request,
                                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(postCommentService.addComment(request, loginUser, httpRequest));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest httpRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(postCommentService.deleteComment(deleteRequest.getId(), loginUser));
    }

    @PostMapping("/like")
    public BaseResponse<Map<String, Object>> likeComment(@RequestParam Long commentId,
                                                         HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(postCommentService.likeComment(commentId, loginUser));
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PostCommentVO>> listCommentVOByPage(@RequestBody PostCommentQueryRequest request,
                                                                 HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        systemAccessManager.ensureGuestPostAccessAllowed(httpRequest);
        ThrowUtils.throwIf(request.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        return ResultUtils.success(postCommentService.listCommentVOByPage(request, httpRequest));
    }

    @PostMapping("/admin/list/page/vo")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<PostCommentVO>> listAdminCommentVOByPage(@RequestBody PostCommentAdminQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "每页最多 50 条");
        return ResultUtils.success(postCommentService.listAdminCommentVOByPage(request));
    }

    @PostMapping("/my/replied/page/vo")
    public BaseResponse<Page<PostCommentActivityVO>> listMyReplyCommentVOByPage(
            @RequestBody CommentActivityQueryRequest request,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(postCommentService.listMyReplyCommentVOByPage(request, loginUser));
    }

    @PostMapping("/review")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reviewComment(@RequestBody PostCommentReviewRequest request,
                                               HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User adminUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(postCommentService.reviewComment(request, adminUser));
    }
}
