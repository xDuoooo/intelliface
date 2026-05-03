package com.xduo.springbootinit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xduo.springbootinit.model.dto.comment.CommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentAddRequest;
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReportRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReviewRequest;
import com.xduo.springbootinit.model.entity.QuestionComment;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.CommentVO;
import com.xduo.springbootinit.model.vo.CommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.UserCommentActivityVO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 题目评论服务
 */
public interface QuestionCommentService extends IService<QuestionComment> {

    /**
     * 发表评论（含回复）
     */
    CommentSubmitResultVO addComment(CommentAddRequest request, User loginUser, HttpServletRequest httpRequest);

    /**
     * 删除评论（本人或管理员，级联软删子评论）
     */
    boolean deleteComment(Long commentId, User loginUser);

    /**
     * 分页获取顶级评论列表（含子评论树组装）
     */
    Page<CommentVO> listCommentVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest);

    /**
     * 点赞 / 取消点赞
     * @return {liked: boolean, likeNum: int}
     */
    Map<String, Object> likeComment(Long commentId, User loginUser);

    /**
     * 举报评论
     */
    boolean reportComment(CommentReportRequest request, User loginUser);

    /**
     * 置顶 / 取消置顶（仅管理员）
     */
    boolean pinComment(Long commentId, boolean pinned);

    /**
     * 设为/取消官方解答（仅管理员）
     */
    boolean setOfficialAnswer(Long commentId, boolean official);

    /**
     * 将 QuestionComment 列表转换为 CommentVO 列表，组装嵌套回复
     */
    List<CommentVO> buildCommentVOTree(List<QuestionComment> comments, User loginUser);

    /**
     * 后台分页获取评论
     */
    Page<CommentVO> listAdminCommentVOByPage(CommentAdminQueryRequest request);

    /**
     * 管理员审核评论
     */
    boolean reviewComment(CommentReviewRequest request, User adminUser);

    /**
     * 分页获取我点赞过的评论
     */
    Page<UserCommentActivityVO> listMyLikedCommentVOByPage(CommentActivityQueryRequest request, User loginUser);

    /**
     * 分页获取我回复过的评论
     */
    Page<UserCommentActivityVO> listMyReplyCommentVOByPage(CommentActivityQueryRequest request, User loginUser);
}
