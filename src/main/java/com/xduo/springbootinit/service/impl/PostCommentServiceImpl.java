package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.AiManager;
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.mapper.PostCommentLikeMapper;
import com.xduo.springbootinit.mapper.PostCommentMapper;
import com.xduo.springbootinit.mapper.PostMapper;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAddRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentQueryRequest;
import com.xduo.springbootinit.model.dto.postcomment.PostCommentReviewRequest;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.PostComment;
import com.xduo.springbootinit.model.entity.PostCommentLike;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.PostCommentActivityVO;
import com.xduo.springbootinit.model.vo.PostCommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.PostCommentVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.PostCommentService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.IpCityResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子评论服务实现
 */
@Service
@Slf4j
public class PostCommentServiceImpl extends ServiceImpl<PostCommentMapper, PostComment> implements PostCommentService {

    private static final int COMMENT_STATUS_APPROVED = 0;
    private static final int COMMENT_STATUS_PENDING = 1;
    private static final int COMMENT_STATUS_REJECTED = 2;
    private static final int MAX_CONTENT_LENGTH = 2000;

    @Resource
    private PostMapper postMapper;

    @Resource
    private PostCommentLikeMapper postCommentLikeMapper;

    @Resource
    private UserService userService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private AiManager aiManager;

    @Resource
    private IpCityResolver ipCityResolver;

    @Override
    public PostCommentSubmitResultVO addComment(PostCommentAddRequest request, User loginUser, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long postId = request.getPostId();
        String content = request.getContent();
        ThrowUtils.throwIf(postId == null || postId <= 0, ErrorCode.PARAMS_ERROR, "帖子 id 不合法");
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowUtils.throwIf(content.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR,
                "内容不能超过 " + MAX_CONTENT_LENGTH + " 字");

        Post post = postMapper.selectById(postId);
        ThrowUtils.throwIf(post == null || Objects.equals(post.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(post.getReviewStatus() != null && post.getReviewStatus() != 1
                        && !Objects.equals(post.getUserId(), loginUser.getId())
                        && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "当前帖子暂不可评论");

        Long parentId = request.getParentId();
        if (parentId != null) {
            PostComment parent = getById(parentId);
            ThrowUtils.throwIf(parent == null || Objects.equals(parent.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "父回复不存在");
            ThrowUtils.throwIf(!postId.equals(parent.getPostId()), ErrorCode.PARAMS_ERROR, "父回复与帖子不匹配");
            // 回复子评论时平铺到顶级评论下，否则列表树只取一层子评论会漏展示。
            if (parent.getParentId() != null) {
                parentId = parent.getParentId();
            }
        }
        if (request.getReplyToId() != null) {
            PostComment replyToComment = getById(request.getReplyToId());
            ThrowUtils.throwIf(replyToComment == null || Objects.equals(replyToComment.getIsDelete(), 1),
                    ErrorCode.NOT_FOUND_ERROR, "回复目标不存在");
            ThrowUtils.throwIf(!postId.equals(replyToComment.getPostId()), ErrorCode.PARAMS_ERROR, "回复目标与帖子不匹配");
        }

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(loginUser.getId());
        comment.setParentId(parentId);
        comment.setReplyToId(request.getReplyToId());
        comment.setContent(content);
        comment.setIpLocation(resolveCommentIpLocation(httpRequest));
        comment.setLikeNum(0);
        CommentAutoReviewResult autoReviewResult = autoReviewComment(content);
        comment.setStatus(autoReviewResult.status());
        comment.setReviewMessage(autoReviewResult.reviewMessage());
        comment.setReviewUserId(null);
        comment.setReviewTime(autoReviewResult.status() == COMMENT_STATUS_APPROVED ? new Date() : null);

        boolean saved = save(comment);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);

        if (autoReviewResult.status() == COMMENT_STATUS_APPROVED) {
            sendReplyNotificationIfNeeded(comment, loginUser, post);
        }

        PostCommentSubmitResultVO resultVO = new PostCommentSubmitResultVO();
        resultVO.setId(comment.getId());
        resultVO.setStatus(comment.getStatus());
        resultVO.setReviewMessage(comment.getReviewMessage());
        return resultVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        PostComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);

        boolean isOwner = Objects.equals(comment.getUserId(), loginUser.getId());
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该回复");
        }

        boolean result = removeById(commentId);

        LambdaUpdateWrapper<PostComment> childWrapper = new LambdaUpdateWrapper<>();
        childWrapper.eq(PostComment::getParentId, commentId)
                .set(PostComment::getIsDelete, 1);
        update(childWrapper);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> likeComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        PostComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null || Objects.equals(comment.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "回复不存在");
        ThrowUtils.throwIf(!Objects.equals(comment.getStatus(), COMMENT_STATUS_APPROVED),
                ErrorCode.OPERATION_ERROR, "当前回复审核通过后才能点赞");

        Long userId = loginUser.getId();
        LambdaQueryWrapper<PostCommentLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(PostCommentLike::getCommentId, commentId)
                .eq(PostCommentLike::getUserId, userId);
        PostCommentLike existingLike = postCommentLikeMapper.selectOne(likeWrapper);

        boolean liked;
        int delta;
        if (existingLike != null) {
            postCommentLikeMapper.deleteById(existingLike.getId());
            liked = false;
            delta = -1;
        } else {
            PostCommentLike like = new PostCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            try {
                postCommentLikeMapper.insert(like);
                liked = true;
                delta = 1;
            } catch (DuplicateKeyException duplicateKeyException) {
                liked = true;
                delta = 0;
            }
            if (delta > 0 && !Objects.equals(comment.getUserId(), loginUser.getId())) {
                String displayName = StringUtils.defaultIfBlank(loginUser.getUserName(), "有用户");
                notificationService.sendNotification(
                        comment.getUserId(),
                        "有人给你的社区回复点赞",
                        displayName + " 点赞了你的社区回复：" + StringUtils.abbreviate(comment.getContent(), 20),
                        "post_comment_like",
                        comment.getPostId()
                );
            }
        }

        if (delta != 0) {
            LambdaUpdateWrapper<PostComment> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(PostComment::getId, commentId)
                    .setSql("likeNum = GREATEST(0, IFNULL(likeNum, 0) + " + delta + ")");
            update(updateWrapper);
        }

        PostComment latestComment = getById(commentId);
        int newLikeNum = latestComment == null || latestComment.getLikeNum() == null
                ? 0
                : Math.max(0, latestComment.getLikeNum());
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeNum", newLikeNum);
        return result;
    }

    @Override
    public Page<PostCommentVO> listCommentVOByPage(PostCommentQueryRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || request.getPostId() == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 20);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        User loginUser = userService.getLoginUserPermitNull(httpRequest);
        String sortField = StringUtils.defaultIfBlank(request.getSortField(), "createTime");
        String sortOrder = StringUtils.defaultIfBlank(request.getSortOrder(), CommonConstant.SORT_ORDER_DESC);

        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostComment::getPostId, request.getPostId())
                .isNull(PostComment::getParentId)
                .and(qw -> {
                    qw.eq(PostComment::getStatus, COMMENT_STATUS_APPROVED);
                    if (loginUser != null) {
                        qw.or(inner -> inner.eq(PostComment::getUserId, loginUser.getId())
                                .in(PostComment::getStatus, COMMENT_STATUS_PENDING, COMMENT_STATUS_REJECTED));
                    }
                });
        if ("likeNum".equals(sortField)) {
            if (CommonConstant.SORT_ORDER_ASC.equals(sortOrder)) {
                wrapper.orderByAsc(PostComment::getLikeNum);
            } else {
                wrapper.orderByDesc(PostComment::getLikeNum);
            }
        } else {
            if (CommonConstant.SORT_ORDER_ASC.equals(sortOrder)) {
                wrapper.orderByAsc(PostComment::getCreateTime);
            } else {
                wrapper.orderByDesc(PostComment::getCreateTime);
            }
        }

        Page<PostComment> commentPage = page(new Page<>(current, pageSize), wrapper);
        Page<PostCommentVO> resultPage = new Page<>(current, pageSize, commentPage.getTotal());
        resultPage.setRecords(buildCommentVOTree(commentPage.getRecords(), loginUser));
        return resultPage;
    }

    @Override
    public List<PostCommentVO> buildCommentVOTree(List<PostComment> topComments, User loginUser) {
        if (topComments == null || topComments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> parentIds = topComments.stream().map(PostComment::getId).collect(Collectors.toList());
        LambdaQueryWrapper<PostComment> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.in(PostComment::getParentId, parentIds)
                .and(qw -> {
                    qw.eq(PostComment::getStatus, COMMENT_STATUS_APPROVED);
                    if (loginUser != null) {
                        qw.or(inner -> inner.eq(PostComment::getUserId, loginUser.getId())
                                .in(PostComment::getStatus, COMMENT_STATUS_PENDING, COMMENT_STATUS_REJECTED));
                    }
                })
                .orderByAsc(PostComment::getCreateTime);
        List<PostComment> childComments = list(childWrapper);
        Map<Long, List<PostComment>> childrenMap = childComments.stream()
                .collect(Collectors.groupingBy(PostComment::getParentId));

        Set<Long> userIds = new HashSet<>();
        topComments.stream()
                .map(PostComment::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        childComments.stream()
                .map(PostComment::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        Set<Long> replyToCommentIds = childComments.stream()
                .map(PostComment::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PostComment> replyToMap = new HashMap<>();
        if (!replyToCommentIds.isEmpty()) {
            listByIds(replyToCommentIds).forEach(comment -> {
                if (comment != null) {
                    replyToMap.put(comment.getId(), comment);
                    if (comment.getUserId() != null) {
                        userIds.add(comment.getUserId());
                    }
                }
            });
        }

        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        Set<Long> likedIds = new HashSet<>();
        if (loginUser != null && loginUser.getId() != null) {
            List<Long> allCommentIds = new ArrayList<>(parentIds);
            childComments.stream()
                    .map(PostComment::getId)
                    .filter(Objects::nonNull)
                    .forEach(allCommentIds::add);
            if (!allCommentIds.isEmpty()) {
                LambdaQueryWrapper<PostCommentLike> likeWrapper = new LambdaQueryWrapper<>();
                likeWrapper.eq(PostCommentLike::getUserId, loginUser.getId())
                        .in(PostCommentLike::getCommentId, allCommentIds);
                postCommentLikeMapper.selectList(likeWrapper)
                        .forEach(like -> likedIds.add(like.getCommentId()));
            }
        }

        return topComments.stream()
                .map(comment -> toVO(comment, userMap, replyToMap, childrenMap, likedIds))
                .collect(Collectors.toList());
    }

    @Override
    public Page<PostCommentVO> listAdminCommentVOByPage(PostCommentAdminQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 50);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);

        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getPostId() != null, PostComment::getPostId, request.getPostId())
                .eq(request.getUserId() != null, PostComment::getUserId, request.getUserId())
                .eq(request.getStatus() != null, PostComment::getStatus, request.getStatus())
                .like(StringUtils.isNotBlank(request.getContent()), PostComment::getContent, request.getContent())
                .orderByAsc(PostComment::getStatus)
                .orderByDesc(PostComment::getCreateTime);

        Page<PostComment> commentPage = page(new Page<>(current, pageSize), wrapper);
        Page<PostCommentVO> resultPage = new Page<>(current, pageSize, commentPage.getTotal());

        List<PostComment> records = commentPage.getRecords();
        if (records == null || records.isEmpty()) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        Set<Long> userIds = records.stream()
                .map(PostComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> replyToCommentIds = records.stream()
                .map(PostComment::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, PostComment> replyToMap = new HashMap<>();
        if (!replyToCommentIds.isEmpty()) {
            listByIds(replyToCommentIds).forEach(comment -> {
                if (comment != null) {
                    replyToMap.put(comment.getId(), comment);
                    if (comment.getUserId() != null) {
                        userIds.add(comment.getUserId());
                    }
                }
            });
        }
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        resultPage.setRecords(records.stream()
                .map(comment -> toFlatVO(comment, userMap, replyToMap))
                .collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    public Page<PostCommentActivityVO> listMyReplyCommentVOByPage(CommentActivityQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 20);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        String searchText = StringUtils.trimToNull(request.getSearchText());
        Integer status = request.getStatus();

        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostComment::getUserId, loginUser.getId())
                .isNotNull(PostComment::getParentId)
                .like(StringUtils.isNotBlank(searchText), PostComment::getContent, searchText)
                .eq(status != null, PostComment::getStatus, status)
                .orderByDesc(PostComment::getCreateTime);
        Page<PostComment> commentPage = page(new Page<>(current, pageSize), wrapper);

        Page<PostCommentActivityVO> resultPage = new Page<>(current, pageSize, commentPage.getTotal());
        resultPage.setRecords(buildReplyActivityVOList(commentPage.getRecords()));
        return resultPage;
    }

    @Override
    public boolean reviewComment(PostCommentReviewRequest request, User adminUser) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Integer status = request.getStatus();
        ThrowUtils.throwIf(status == null || (status != COMMENT_STATUS_APPROVED && status != COMMENT_STATUS_REJECTED),
                ErrorCode.PARAMS_ERROR, "审核状态不合法");
        String reviewMessage = StringUtils.trimToNull(request.getReviewMessage());
        if (status == COMMENT_STATUS_REJECTED) {
            ThrowUtils.throwIf(StringUtils.isBlank(reviewMessage), ErrorCode.PARAMS_ERROR, "驳回时请填写审核意见");
        }
        ThrowUtils.throwIf(StringUtils.length(reviewMessage) > 512, ErrorCode.PARAMS_ERROR, "审核意见过长");

        PostComment comment = getById(request.getId());
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);

        PostComment updateComment = new PostComment();
        updateComment.setId(comment.getId());
        updateComment.setStatus(status);
        updateComment.setReviewMessage(reviewMessage);
        updateComment.setReviewUserId(adminUser.getId());
        updateComment.setReviewTime(new Date());
        boolean updated = updateById(updateComment);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);

        Post post = postMapper.selectById(comment.getPostId());
        User author = userService.getById(comment.getUserId());
        if (status == COMMENT_STATUS_APPROVED && author != null && post != null) {
            sendReplyNotificationIfNeeded(comment, author, post);
        }
        if (status == COMMENT_STATUS_REJECTED) {
            notificationService.sendNotification(
                    comment.getUserId(),
                    "你的社区回复未通过审核",
                    "你在帖子下发布的回复未通过审核。" + (StringUtils.isNotBlank(reviewMessage) ? " 审核意见：" + reviewMessage : ""),
                    "post_comment_review",
                    comment.getPostId()
            );
        }
        return true;
    }

    private PostCommentVO toVO(PostComment comment,
                               Map<Long, User> userMap,
                               Map<Long, PostComment> replyToMap,
                               Map<Long, List<PostComment>> childrenMap,
                               Set<Long> likedIds) {
        PostCommentVO vo = baseVO(comment, userMap, replyToMap, likedIds);
        List<PostCommentVO> replyVOList = childrenMap.getOrDefault(comment.getId(), Collections.emptyList()).stream()
                .map(child -> {
                    PostCommentVO childVO = baseVO(child, userMap, replyToMap, likedIds);
                    childVO.setReplies(Collections.emptyList());
                    return childVO;
                })
                .collect(Collectors.toList());
        vo.setReplies(replyVOList);
        return vo;
    }

    private PostCommentVO toFlatVO(PostComment comment,
                                   Map<Long, User> userMap,
                                   Map<Long, PostComment> replyToMap) {
        PostCommentVO vo = baseVO(comment, userMap, replyToMap, Collections.emptySet());
        vo.setReplies(Collections.emptyList());
        return vo;
    }

    private PostCommentVO baseVO(PostComment comment,
                                 Map<Long, User> userMap,
                                 Map<Long, PostComment> replyToMap,
                                 Set<Long> likedIds) {
        PostCommentVO vo = new PostCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setReplyToId(comment.getReplyToId());
        vo.setContent(comment.getContent());
        vo.setIpLocation(comment.getIpLocation());
        vo.setLikeNum(comment.getLikeNum() == null ? 0 : Math.max(0, comment.getLikeNum()));
        vo.setHasLiked(likedIds != null && likedIds.contains(comment.getId()));
        vo.setStatus(comment.getStatus());
        vo.setReviewMessage(comment.getReviewMessage());
        vo.setCreateTime(comment.getCreateTime());
        vo.setDeleted(Objects.equals(comment.getIsDelete(), 1));
        vo.setUser(userService.getUserVO(userMap.get(comment.getUserId())));
        if (comment.getReplyToId() != null) {
            PostComment replyToComment = replyToMap.get(comment.getReplyToId());
            if (replyToComment != null) {
                vo.setReplyToUser(userService.getUserVO(userMap.get(replyToComment.getUserId())));
            }
        }
        return vo;
    }

    private List<PostCommentActivityVO> buildReplyActivityVOList(List<PostComment> commentList) {
        if (commentList == null || commentList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, PostComment> commentMap = commentList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(PostComment::getId, item -> item, (a, b) -> a));

        Set<Long> userIds = commentList.stream()
                .map(PostComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> replyToCommentIds = commentList.stream()
                .map(PostComment::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!replyToCommentIds.isEmpty()) {
            listByIds(replyToCommentIds).forEach(replyComment -> {
                if (replyComment != null) {
                    commentMap.putIfAbsent(replyComment.getId(), replyComment);
                    if (replyComment.getUserId() != null) {
                        userIds.add(replyComment.getUserId());
                    }
                }
            });
        }

        Map<Long, UserVO> userVOMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, userService::getUserVO, (a, b) -> a));

        Set<Long> postIds = commentList.stream()
                .map(PostComment::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postTitleMap = postIds.isEmpty()
                ? Collections.emptyMap()
                : postMapper.selectBatchIds(postIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Post::getId,
                        post -> StringUtils.defaultIfBlank(post.getTitle(), "帖子已不可见"),
                        (a, b) -> a
                ));

        return commentList.stream().map(comment -> {
            PostCommentActivityVO vo = new PostCommentActivityVO();
            vo.setId(comment.getId());
            vo.setPostId(comment.getPostId());
            vo.setPostTitle(postTitleMap.getOrDefault(comment.getPostId(), "帖子已不可见"));
            vo.setParentId(comment.getParentId());
            vo.setReplyToId(comment.getReplyToId());
            vo.setContent(comment.getContent());
            vo.setIpLocation(comment.getIpLocation());
            vo.setStatus(comment.getStatus());
            vo.setReviewMessage(comment.getReviewMessage());
            vo.setCreateTime(comment.getCreateTime());
            vo.setActionTime(comment.getCreateTime());
            vo.setDeleted(comment.getIsDelete() != null && comment.getIsDelete() == 1);
            vo.setUser(userVOMap.get(comment.getUserId()));

            if (comment.getReplyToId() != null) {
                PostComment replyToComment = commentMap.get(comment.getReplyToId());
                if (replyToComment != null) {
                    vo.setReplyToUser(userVOMap.get(replyToComment.getUserId()));
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private CommentAutoReviewResult autoReviewComment(String content) {
        String lowerCaseText = StringUtils.defaultString(content).toLowerCase();
        if (containsAny(lowerCaseText, "赌博", "色情", "外挂", "vpn", "代考", "作弊器", "刷单", "telegram", "qq群", "vx:", "微信:", "联系方式")) {
            return new CommentAutoReviewResult(COMMENT_STATUS_PENDING, "内容触发风险规则，已进入人工复核");
        }
        try {
            String aiResult = aiManager.doChat(
                    "你是社区回复审核助手。请仅输出一行 JSON，格式为 {\"decision\":\"approve|pending|reject\",\"reason\":\"...\"}。"
                            + "如果内容存在违规、广告导流、人身攻击、联系方式引流等风险，decision 输出 pending 或 reject；"
                            + "正常交流输出 approve。",
                    "请审核以下帖子回复内容：\n" + content
            );
            String normalized = StringUtils.defaultString(aiResult).toLowerCase();
            if (normalized.contains("pending") || normalized.contains("reject")) {
                return new CommentAutoReviewResult(COMMENT_STATUS_PENDING, extractReviewMessage(aiResult, "回复已进入人工复核"));
            }
        } catch (Exception e) {
            log.warn("post comment auto review fallback, reason={}", e.getMessage());
        }
        return new CommentAutoReviewResult(COMMENT_STATUS_APPROVED, "系统自动审核通过");
    }

    private String extractReviewMessage(String aiResult, String defaultMessage) {
        if (StringUtils.isBlank(aiResult)) {
            return defaultMessage;
        }
        int reasonIndex = aiResult.indexOf("\"reason\"");
        if (reasonIndex < 0) {
            return defaultMessage;
        }
        String text = aiResult.substring(reasonIndex);
        int colonIndex = text.indexOf(':');
        if (colonIndex < 0) {
            return defaultMessage;
        }
        String value = text.substring(colonIndex + 1).replaceAll("[\"{}]", "").trim();
        return StringUtils.isBlank(value) ? defaultMessage : StringUtils.abbreviate(value, 120);
    }

    private boolean containsAny(String text, String... keywords) {
        if (StringUtils.isBlank(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.isNotBlank(keyword) && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void sendReplyNotificationIfNeeded(PostComment comment, User authorUser, Post post) {
        String displayName = StringUtils.defaultIfBlank(authorUser.getUserName(), "有用户");
        if (comment.getReplyToId() != null) {
            PostComment replyToComment = getById(comment.getReplyToId());
            if (replyToComment != null && !Objects.equals(replyToComment.getUserId(), comment.getUserId())) {
                notificationService.sendNotification(
                        replyToComment.getUserId(),
                        "有人回复了你的社区回复",
                        displayName + " 回复了你：" + StringUtils.abbreviate(comment.getContent(), 20),
                        "post_reply",
                        comment.getPostId()
                );
                return;
            }
        }
        if (comment.getParentId() != null) {
            PostComment parentComment = getById(comment.getParentId());
            if (parentComment != null && !Objects.equals(parentComment.getUserId(), comment.getUserId())) {
                notificationService.sendNotification(
                        parentComment.getUserId(),
                        "你的社区回复有了新回复",
                        displayName + " 回复了你：" + StringUtils.abbreviate(comment.getContent(), 20),
                        "post_reply",
                        comment.getPostId()
                );
                return;
            }
        }
        if (post != null && !Objects.equals(post.getUserId(), comment.getUserId())) {
            notificationService.sendNotification(
                    post.getUserId(),
                    "你的帖子有了新回复",
                    displayName + " 评论了你的帖子：" + StringUtils.abbreviate(comment.getContent(), 20),
                    "post_reply",
                    comment.getPostId()
            );
        }
    }

    private record CommentAutoReviewResult(int status, String reviewMessage) {
    }

    private String resolveCommentIpLocation(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        String resolvedLocation = StringUtils.trimToNull(ipCityResolver.resolveLocationLabel(httpRequest));
        return StringUtils.abbreviate(resolvedLocation, 64);
    }
}
