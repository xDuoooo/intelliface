package com.xduo.springbootinit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAddRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentReviewRequest;
import com.xduo.springbootinit.model.entity.PostComment;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.PostCommentActivityVO;
import com.xduo.springbootinit.model.vo.PostCommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.PostCommentVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 帖子评论服务
 */
public interface PostCommentService extends IService<PostComment> {

    PostCommentSubmitResultVO addComment(PostCommentAddRequest request, User loginUser, HttpServletRequest httpRequest);

    boolean deleteComment(Long commentId, User loginUser);

    Map<String, Object> likeComment(Long commentId, User loginUser);

    Page<PostCommentVO> listCommentVOByPage(PostCommentQueryRequest request, HttpServletRequest httpRequest);

    List<PostCommentVO> buildCommentVOTree(List<PostComment> comments, User loginUser);

    Page<PostCommentVO> listAdminCommentVOByPage(PostCommentAdminQueryRequest request);

    boolean reviewComment(PostCommentReviewRequest request, User adminUser);

    Page<PostCommentActivityVO> listMyReplyCommentVOByPage(CommentActivityQueryRequest request, User loginUser);
}
