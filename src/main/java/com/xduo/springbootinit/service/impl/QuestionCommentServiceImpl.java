package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.mapper.QuestionCommentLikeMapper;
import com.xduo.springbootinit.mapper.QuestionCommentMapper;
import com.xduo.springbootinit.mapper.QuestionCommentReportMapper;
import com.xduo.springbootinit.mapper.QuestionMapper;
import com.xduo.springbootinit.model.dto.comment.CommentAdminQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentAddRequest;
import com.xduo.springbootinit.model.dto.comment.CommentActivityQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentQueryRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReportRequest;
import com.xduo.springbootinit.model.dto.comment.CommentReviewRequest;
import com.xduo.springbootinit.model.entity.*;
import com.xduo.springbootinit.model.vo.CommentVO;
import com.xduo.springbootinit.model.vo.CommentSubmitResultVO;
import com.xduo.springbootinit.model.vo.UserCommentActivityVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.manager.AiManager;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.QuestionCommentService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.IpCityResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目评论服务实现
 */
@Service
@Slf4j
public class QuestionCommentServiceImpl extends ServiceImpl<QuestionCommentMapper, QuestionComment>
        implements QuestionCommentService {

    /** 内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 自动隐藏的举报次数阈值 */
    private static final int AUTO_HIDE_REPORT_NUM = 3;

    /** 审核通过 */
    private static final int COMMENT_STATUS_APPROVED = 0;

    /** 待审核 */
    private static final int COMMENT_STATUS_PENDING = 1;

    /** 已驳回 / 已隐藏 */
    private static final int COMMENT_STATUS_REJECTED = 2;

    @Resource
    private QuestionCommentLikeMapper commentLikeMapper;

    @Resource
    private QuestionCommentReportMapper commentReportMapper;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private UserService userService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private AiManager aiManager;

    @Resource
    private IpCityResolver ipCityResolver;

    // ----------------------------------------------------------------
    //  1. 发表评论
    // ----------------------------------------------------------------

    @Override
    public CommentSubmitResultVO addComment(CommentAddRequest request, User loginUser, HttpServletRequest httpRequest) {
        // 参数校验
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long questionId = request.getQuestionId();
        String content = request.getContent();
        ThrowUtils.throwIf(questionId == null || questionId <= 0, ErrorCode.PARAMS_ERROR, "题目 id 不合法");
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowUtils.throwIf(content.length() > MAX_CONTENT_LENGTH, ErrorCode.PARAMS_ERROR,
                "内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        ThrowUtils.throwIf(questionMapper.selectById(questionId) == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");

        // 深度校验：若有父评论则计算层级
        Long parentId = request.getParentId();
        if (parentId != null) {
            QuestionComment parent = getById(parentId);
            ThrowUtils.throwIf(parent == null || Objects.equals(parent.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "父评论不存在");
            ThrowUtils.throwIf(!questionId.equals(parent.getQuestionId()), ErrorCode.PARAMS_ERROR, "父评论与题目不匹配");
            // 评论列表只展示两层：回复子评论时仍挂到顶级评论下，replyToId 保留被回复对象。
            if (parent.getParentId() != null) {
                parentId = parent.getParentId();
            }
        }
        if (request.getReplyToId() != null) {
            QuestionComment replyToComment = getById(request.getReplyToId());
            ThrowUtils.throwIf(replyToComment == null || Objects.equals(replyToComment.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "回复目标不存在");
            ThrowUtils.throwIf(!questionId.equals(replyToComment.getQuestionId()), ErrorCode.PARAMS_ERROR, "回复目标与题目不匹配");
        }

        QuestionComment comment = new QuestionComment();
        comment.setQuestionId(questionId);
        comment.setUserId(loginUser.getId());
        comment.setParentId(parentId);
        comment.setReplyToId(request.getReplyToId());
        comment.setContent(content);
        comment.setIpLocation(resolveCommentIpLocation(httpRequest));
        comment.setLikeNum(0);
        comment.setReportNum(0);
        comment.setIsPinned(0);
        comment.setIsOfficial(0);
        CommentAutoReviewResult autoReviewResult = autoReviewComment(request.getContent());
        comment.setStatus(autoReviewResult.status());
        comment.setReviewMessage(autoReviewResult.reviewMessage());
        comment.setReviewUserId(null);
        comment.setReviewTime(autoReviewResult.status() == COMMENT_STATUS_APPROVED ? new Date() : null);

        boolean saved = save(comment);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);

        if (autoReviewResult.status() == COMMENT_STATUS_APPROVED) {
            sendReplyNotificationIfNeeded(comment, loginUser);
        }

        CommentSubmitResultVO resultVO = new CommentSubmitResultVO();
        resultVO.setId(comment.getId());
        resultVO.setStatus(comment.getStatus());
        resultVO.setReviewMessage(comment.getReviewMessage());
        return resultVO;
    }

    // ----------------------------------------------------------------
    //  2. 删除评论
    // ----------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);

        // 权限：仅本人或管理员
        boolean isOwner = comment.getUserId().equals(loginUser.getId());
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该评论");
        }

        // 软删本评论
        boolean result = removeById(commentId);

        // 级联软删所有子评论
        LambdaUpdateWrapper<QuestionComment> childWrapper = new LambdaUpdateWrapper<>();
        childWrapper.eq(QuestionComment::getParentId, commentId)
                    .set(QuestionComment::getIsDelete, 1);
        update(childWrapper);

        return result;
    }

    // ----------------------------------------------------------------
    //  3. 分页获取评论列表（含子评论树）
    // ----------------------------------------------------------------

    @Override
    public Page<CommentVO> listCommentVOByPage(CommentQueryRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || request.getQuestionId() == null, ErrorCode.PARAMS_ERROR);

        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 50);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        String sortField = StringUtils.isNotBlank(request.getSortField()) ? request.getSortField() : "createTime";
        String sortOrder = StringUtils.isNotBlank(request.getSortOrder()) ? request.getSortOrder() : CommonConstant.SORT_ORDER_DESC;
        // 获取当前登录用户（可为 null）
        User loginUser = userService.getLoginUserPermitNull(httpRequest);

        // 查顶级评论（parentId IS NULL，status=0 正常）
        LambdaQueryWrapper<QuestionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionComment::getQuestionId, request.getQuestionId())
               .isNull(QuestionComment::getParentId)
               .and(qw -> {
                   qw.eq(QuestionComment::getStatus, COMMENT_STATUS_APPROVED);
                   if (loginUser != null) {
                       qw.or(inner -> inner.eq(QuestionComment::getUserId, loginUser.getId())
                               .in(QuestionComment::getStatus, COMMENT_STATUS_PENDING, COMMENT_STATUS_REJECTED));
                   }
               })
               // 置顶评论优先
               .orderByDesc(QuestionComment::getIsPinned);

        if ("likeNum".equals(sortField)) {
            if (CommonConstant.SORT_ORDER_ASC.equals(sortOrder)) {
                wrapper.orderByAsc(QuestionComment::getLikeNum);
            } else {
                wrapper.orderByDesc(QuestionComment::getLikeNum);
            }
        } else {
            if (CommonConstant.SORT_ORDER_ASC.equals(sortOrder)) {
                wrapper.orderByAsc(QuestionComment::getCreateTime);
            } else {
                wrapper.orderByDesc(QuestionComment::getCreateTime);
            }
        }

        Page<QuestionComment> commentPage = page(new Page<>(current, pageSize), wrapper);

        // 转 VO
        List<CommentVO> voList = buildCommentVOTree(commentPage.getRecords(), loginUser);

        Page<CommentVO> voPage = new Page<>(current, pageSize, commentPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ----------------------------------------------------------------
    //  4. 点赞 / 取消点赞
    // ----------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> likeComment(Long commentId, User loginUser) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null || Objects.equals(comment.getIsDelete(), 1), ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        ThrowUtils.throwIf(!Objects.equals(comment.getStatus(), COMMENT_STATUS_APPROVED),
                ErrorCode.OPERATION_ERROR, "当前评论审核通过后才能点赞");

        Long userId = loginUser.getId();

        // 检查是否已点赞
        LambdaQueryWrapper<QuestionCommentLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(QuestionCommentLike::getCommentId, commentId)
                   .eq(QuestionCommentLike::getUserId, userId);
        QuestionCommentLike existingLike = commentLikeMapper.selectOne(likeWrapper);

        boolean liked;
        int delta;
        if (existingLike != null) {
            // 取消点赞
            commentLikeMapper.deleteById(existingLike.getId());
            liked = false;
            delta = -1;
        } else {
            // 新增点赞
            QuestionCommentLike like = new QuestionCommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            try {
                commentLikeMapper.insert(like);
                liked = true;
                delta = 1;
            } catch (DuplicateKeyException duplicateKeyException) {
                liked = true;
                delta = 0;
            }

            // 发送点赞通知（异步）
            if (delta > 0 && !comment.getUserId().equals(loginUser.getId())) {
                String displayName = StringUtils.defaultIfBlank(loginUser.getUserName(), "有用户");
                notificationService.sendNotification(
                    comment.getUserId(),
                    "有人给你点赞了",
                    displayName + " 点赞了你的评论：" + StringUtils.abbreviate(comment.getContent(), 20),
                    "like",
                    comment.getQuestionId()
                );
            }
        }

        // 更新冗余点赞数（乐观锁方式，保证不小于 0）
        LambdaUpdateWrapper<QuestionComment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionComment::getId, commentId)
                     .setSql("likeNum = GREATEST(0, likeNum + " + delta + ")");
        update(updateWrapper);

        QuestionComment latestComment = getById(commentId);
        int newLikeNum = latestComment == null || latestComment.getLikeNum() == null ? 0 : Math.max(0, latestComment.getLikeNum());
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeNum", newLikeNum);
        return result;
    }

    // ----------------------------------------------------------------
    //  5. 举报
    // ----------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reportComment(CommentReportRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || request.getCommentId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StringUtils.isBlank(request.getReason()), ErrorCode.PARAMS_ERROR, "举报原因不能为空");

        QuestionComment comment = getById(request.getCommentId());
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);

        // 防止重复举报（唯一索引也会兜底）
        LambdaQueryWrapper<QuestionCommentReport> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(QuestionCommentReport::getCommentId, request.getCommentId())
                     .eq(QuestionCommentReport::getUserId, loginUser.getId());
        if (commentReportMapper.selectCount(reportWrapper) > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已举报过该评论");
        }

        QuestionCommentReport report = new QuestionCommentReport();
        report.setCommentId(request.getCommentId());
        report.setUserId(loginUser.getId());
        report.setReason(request.getReason());
        report.setStatus(0);
        try {
            commentReportMapper.insert(report);
        } catch (DuplicateKeyException duplicateKeyException) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已举报过该评论");
        }

        // 更新举报数并检查阈值
        LambdaUpdateWrapper<QuestionComment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QuestionComment::getId, request.getCommentId())
                     .setSql("reportNum = reportNum + 1");

        // 若举报次数达到阈值，自动进入待审核
        int newReportNum = Optional.ofNullable(comment.getReportNum()).orElse(0) + 1;
        if (newReportNum >= AUTO_HIDE_REPORT_NUM) {
            updateWrapper.set(QuestionComment::getStatus, COMMENT_STATUS_PENDING);
            updateWrapper.set(QuestionComment::getReviewMessage, "社区举报触发人工复核");
        }
        update(updateWrapper);
        return true;
    }

    // ----------------------------------------------------------------
    //  6. 置顶
    // ----------------------------------------------------------------

    @Override
    public boolean pinComment(Long commentId, boolean pinned) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);
        comment.setIsPinned(pinned ? 1 : 0);
        return updateById(comment);
    }

    // ----------------------------------------------------------------
    //  7. 官方解答
    // ----------------------------------------------------------------

    @Override
    public boolean setOfficialAnswer(Long commentId, boolean official) {
        ThrowUtils.throwIf(commentId == null || commentId <= 0, ErrorCode.PARAMS_ERROR);
        QuestionComment comment = getById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);
        comment.setIsOfficial(official ? 1 : 0);
        boolean result = updateById(comment);
        if (result && official) {
            notificationService.sendNotification(
                comment.getUserId(),
                "恭喜！你的评论被设为官方解答",
                "你在题目 ID 为 " + comment.getQuestionId() + " 下发表的评论已被管理员设为官方解答。",
                "official_answer",
                comment.getQuestionId()
            );
        }
        return result;
    }

    // ----------------------------------------------------------------
    //  工具方法：组装 CommentVO 树
    // ----------------------------------------------------------------

    @Override
    public List<CommentVO> buildCommentVOTree(List<QuestionComment> topComments, User loginUser) {
        if (topComments == null || topComments.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集顶级评论 id
        List<Long> parentIds = topComments.stream().map(QuestionComment::getId).collect(Collectors.toList());

        // 批量查询子评论（第一层，最多前 50 条）
        LambdaQueryWrapper<QuestionComment> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.in(QuestionComment::getParentId, parentIds)
                    .and(qw -> {
                        qw.eq(QuestionComment::getStatus, COMMENT_STATUS_APPROVED);
                        if (loginUser != null) {
                            qw.or(inner -> inner.eq(QuestionComment::getUserId, loginUser.getId())
                                    .in(QuestionComment::getStatus, COMMENT_STATUS_PENDING, COMMENT_STATUS_REJECTED));
                        }
                    })
                    .orderByAsc(QuestionComment::getCreateTime);
        List<QuestionComment> childComments = list(childWrapper);

        // 以 parentId 分组
        Map<Long, List<QuestionComment>> childrenMap = childComments.stream()
                .collect(Collectors.groupingBy(QuestionComment::getParentId));

        // 收集所有用户 id（包括被回复人的 id）
        Set<Long> userIds = new HashSet<>();
        topComments.stream()
                .map(QuestionComment::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        childComments.stream()
                .map(QuestionComment::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        
        // 收集所有涉及到的评论 id 来查作者
        Set<Long> allInvolvedCommentIds = new HashSet<>();
        childComments.forEach(c -> {
            if (c.getReplyToId() != null) {
                allInvolvedCommentIds.add(c.getReplyToId());
            }
        });
        List<QuestionComment> replyToComments = Collections.emptyList();
        if (!allInvolvedCommentIds.isEmpty()) {
            replyToComments = listByIds(allInvolvedCommentIds);
            replyToComments.forEach(rc -> {
                if (rc != null && rc.getUserId() != null) {
                    userIds.add(rc.getUserId());
                }
            });
        }

        List<User> users = userIds.isEmpty() ? Collections.emptyList() : userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (left, right) -> left));

        // 若已登录，批量查询该用户对这些评论的点赞情况
        Set<Long> likedIds = new HashSet<>();
        if (loginUser != null) {
            List<Long> allCommentIds = new ArrayList<>(parentIds);
            childComments.forEach(c -> allCommentIds.add(c.getId()));
            if (!allCommentIds.isEmpty()) {
                LambdaQueryWrapper<QuestionCommentLike> likeWrapper = new LambdaQueryWrapper<>();
                likeWrapper.eq(QuestionCommentLike::getUserId, loginUser.getId())
                           .in(QuestionCommentLike::getCommentId, allCommentIds);
                commentLikeMapper.selectList(likeWrapper).forEach(l -> likedIds.add(l.getCommentId()));
            }
        }

        // 预查一份 commentId -> userId 的映射，方便找 replyToUser
        Map<Long, Long> commentToUserMap = new HashMap<>();
        topComments.forEach(c -> commentToUserMap.put(c.getId(), c.getUserId()));
        childComments.forEach(c -> commentToUserMap.put(c.getId(), c.getUserId()));
        replyToComments.forEach(rc -> commentToUserMap.put(rc.getId(), rc.getUserId()));

        // 转 VO
        return topComments.stream().map(c -> toVO(c, userMap, commentToUserMap, childrenMap, likedIds)).collect(Collectors.toList());
    }

    private CommentVO toVO(QuestionComment comment, Map<Long, User> userMap,
                           Map<Long, Long> commentToUserMap,
                           Map<Long, List<QuestionComment>> childrenMap, Set<Long> likedIds) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setQuestionId(comment.getQuestionId());
        vo.setParentId(comment.getParentId());
        vo.setReplyToId(comment.getReplyToId());
        vo.setContent(comment.getContent());
        vo.setIpLocation(comment.getIpLocation());
        vo.setLikeNum(comment.getLikeNum());
        vo.setIsPinned(comment.getIsPinned());
        vo.setIsOfficial(comment.getIsOfficial());
        vo.setStatus(comment.getStatus());
        vo.setReviewMessage(comment.getReviewMessage());
        vo.setCreateTime(comment.getCreateTime());
        vo.setDeleted(false);
        vo.setUser(userService.getUserVO(userMap.get(comment.getUserId())));
        vo.setHasLiked(likedIds.contains(comment.getId()));

        // 设置被回复人信息
        if (comment.getReplyToId() != null) {
            Long replyToUserId = commentToUserMap.get(comment.getReplyToId());
            if (replyToUserId != null) {
                vo.setReplyToUser(userService.getUserVO(userMap.get(replyToUserId)));
            }
        }

        // 组装子评论（不递归第三层，直接平铺）
        List<QuestionComment> children = childrenMap.getOrDefault(comment.getId(), Collections.emptyList());
        List<CommentVO> replyVOs = children.stream()
                .map(child -> {
                    CommentVO childVO = new CommentVO();
                    childVO.setId(child.getId());
                    childVO.setQuestionId(child.getQuestionId());
                    childVO.setParentId(child.getParentId());
                    childVO.setReplyToId(child.getReplyToId());
                    childVO.setContent(child.getContent());
                    childVO.setIpLocation(child.getIpLocation());
                    childVO.setLikeNum(child.getLikeNum());
                    childVO.setIsPinned(child.getIsPinned());
                    childVO.setIsOfficial(child.getIsOfficial());
                    childVO.setStatus(child.getStatus());
                    childVO.setReviewMessage(child.getReviewMessage());
                    childVO.setCreateTime(child.getCreateTime());
                    childVO.setDeleted(false);
                    childVO.setUser(userService.getUserVO(userMap.get(child.getUserId())));
                    childVO.setHasLiked(likedIds.contains(child.getId()));

                    // 设置被回复人信息
                    if (child.getReplyToId() != null) {
                        Long ruid = commentToUserMap.get(child.getReplyToId());
                        if (ruid != null) {
                            childVO.setReplyToUser(userService.getUserVO(userMap.get(ruid)));
                        }
                    }

                    childVO.setReplies(Collections.emptyList());
                    return childVO;
                })
                .collect(Collectors.toList());
        vo.setReplies(replyVOs);
        return vo;
    }

    @Override
    public Page<CommentVO> listAdminCommentVOByPage(CommentAdminQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 50);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR, "分页参数不合法");

        LambdaQueryWrapper<QuestionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getQuestionId() != null, QuestionComment::getQuestionId, request.getQuestionId())
                .eq(request.getUserId() != null, QuestionComment::getUserId, request.getUserId())
                .eq(request.getStatus() != null, QuestionComment::getStatus, request.getStatus())
                .like(StringUtils.isNotBlank(request.getContent()), QuestionComment::getContent, request.getContent())
                .orderByAsc(QuestionComment::getStatus)
                .orderByDesc(QuestionComment::getCreateTime);

        Page<QuestionComment> commentPage = page(new Page<>(current, pageSize), wrapper);
        List<QuestionComment> records = commentPage.getRecords();
        Set<Long> userIds = records.stream()
                .map(QuestionComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> replyToCommentIds = records.stream()
                .map(QuestionComment::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Long> commentToUserMap = new HashMap<>();
        if (!replyToCommentIds.isEmpty()) {
            List<QuestionComment> replyComments = listByIds(replyToCommentIds);
            replyComments.forEach(replyComment -> {
                if (replyComment == null) {
                    return;
                }
                if (replyComment.getUserId() != null) {
                    userIds.add(replyComment.getUserId());
                }
                commentToUserMap.put(replyComment.getId(), replyComment.getUserId());
            });
        }
        records.forEach(record -> commentToUserMap.put(record.getId(), record.getUserId()));
        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<CommentVO> commentVOList = records.stream().map(comment -> {
            CommentVO commentVO = new CommentVO();
            commentVO.setId(comment.getId());
            commentVO.setQuestionId(comment.getQuestionId());
            commentVO.setParentId(comment.getParentId());
            commentVO.setReplyToId(comment.getReplyToId());
            commentVO.setContent(comment.getContent());
            commentVO.setIpLocation(comment.getIpLocation());
            commentVO.setLikeNum(comment.getLikeNum());
            commentVO.setIsPinned(comment.getIsPinned());
            commentVO.setIsOfficial(comment.getIsOfficial());
            commentVO.setStatus(comment.getStatus());
            commentVO.setReviewMessage(comment.getReviewMessage());
            commentVO.setCreateTime(comment.getCreateTime());
            commentVO.setDeleted(comment.getIsDelete() != null && comment.getIsDelete() == 1);
            commentVO.setUser(userService.getUserVO(userMap.get(comment.getUserId())));
            if (comment.getReplyToId() != null) {
                Long replyToUserId = commentToUserMap.get(comment.getReplyToId());
                if (replyToUserId != null) {
                    commentVO.setReplyToUser(userService.getUserVO(userMap.get(replyToUserId)));
                }
            }
            commentVO.setHasLiked(false);
            commentVO.setReplies(Collections.emptyList());
            return commentVO;
        }).toList();

        Page<CommentVO> commentVOPage = new Page<>(current, pageSize, commentPage.getTotal());
        commentVOPage.setRecords(commentVOList);
        return commentVOPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewComment(CommentReviewRequest request, User adminUser) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Integer status = request.getStatus();
        ThrowUtils.throwIf(status == null || (status != COMMENT_STATUS_APPROVED && status != COMMENT_STATUS_REJECTED),
                ErrorCode.PARAMS_ERROR, "审核状态不合法");
        String reviewMessage = StringUtils.trimToNull(request.getReviewMessage());
        if (status == COMMENT_STATUS_REJECTED) {
            ThrowUtils.throwIf(StringUtils.isBlank(reviewMessage), ErrorCode.PARAMS_ERROR, "驳回时请填写审核意见");
        }
        ThrowUtils.throwIf(StringUtils.length(reviewMessage) > 512, ErrorCode.PARAMS_ERROR, "审核意见过长");

        QuestionComment comment = getById(request.getId());
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR);

        QuestionComment updateComment = new QuestionComment();
        updateComment.setId(comment.getId());
        updateComment.setStatus(status);
        updateComment.setReviewMessage(reviewMessage);
        updateComment.setReviewUserId(adminUser.getId());
        updateComment.setReviewTime(new Date());
        boolean updated = updateById(updateComment);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);

        User author = userService.getById(comment.getUserId());
        if (status == COMMENT_STATUS_APPROVED && author != null) {
            sendReplyNotificationIfNeeded(comment, author);
        }
        if (status == COMMENT_STATUS_REJECTED) {
            notificationService.sendNotification(
                    comment.getUserId(),
                    "你的评论未通过审核",
                    "你在题目下发布的评论未通过审核。" + (StringUtils.isNotBlank(reviewMessage) ? " 审核意见：" + reviewMessage : ""),
                    "comment_review",
                    comment.getQuestionId()
            );
        }
        return true;
    }

    @Override
    public Page<UserCommentActivityVO> listMyLikedCommentVOByPage(CommentActivityQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 20);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);

        LambdaQueryWrapper<QuestionCommentLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(QuestionCommentLike::getUserId, loginUser.getId())
                .orderByDesc(QuestionCommentLike::getCreateTime);
        Page<QuestionCommentLike> likePage = commentLikeMapper.selectPage(new Page<>(current, pageSize), likeWrapper);

        Page<UserCommentActivityVO> resultPage = new Page<>(current, pageSize, likePage.getTotal());
        List<QuestionCommentLike> likeRecords = likePage.getRecords();
        if (likeRecords == null || likeRecords.isEmpty()) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        LinkedHashMap<Long, Date> actionTimeMap = new LinkedHashMap<>();
        likeRecords.forEach(item -> actionTimeMap.put(item.getCommentId(), item.getCreateTime()));
        List<QuestionComment> commentList = listByIds(new ArrayList<>(actionTimeMap.keySet()));
        resultPage.setRecords(buildUserCommentActivityVOList(commentList, loginUser, actionTimeMap, true));
        return resultPage;
    }

    @Override
    public Page<UserCommentActivityVO> listMyReplyCommentVOByPage(CommentActivityQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long pageSize = Math.min(request.getPageSize(), 20);
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        String searchText = StringUtils.trimToNull(request.getSearchText());
        Integer status = request.getStatus();

        LambdaQueryWrapper<QuestionComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionComment::getUserId, loginUser.getId())
                .isNotNull(QuestionComment::getParentId)
                .like(StringUtils.isNotBlank(searchText), QuestionComment::getContent, searchText)
                .eq(status != null, QuestionComment::getStatus, status)
                .orderByDesc(QuestionComment::getCreateTime);
        Page<QuestionComment> commentPage = page(new Page<>(current, pageSize), wrapper);

        Page<UserCommentActivityVO> resultPage = new Page<>(current, pageSize, commentPage.getTotal());
        resultPage.setRecords(buildUserCommentActivityVOList(commentPage.getRecords(), loginUser, null, false));
        return resultPage;
    }

    private CommentAutoReviewResult autoReviewComment(String content) {
        String lowerCaseText = StringUtils.defaultString(content).toLowerCase();
        if (containsAny(lowerCaseText, "赌博", "色情", "外挂", "vpn", "代考", "作弊器", "刷单", "telegram", "qq群", "vx:", "微信:", "联系方式")) {
            return new CommentAutoReviewResult(COMMENT_STATUS_PENDING, "内容触发风险规则，已进入人工复核");
        }
        try {
            String aiResult = aiManager.doChat(
                    "你是评论审核助手。请仅输出一行 JSON，格式为 {\"decision\":\"approve|pending|reject\",\"reason\":\"...\"}。"
                            + "如果内容存在违规、广告导流、人身攻击、联系方式引流等风险，decision 输出 pending 或 reject；"
                            + "正常交流输出 approve。",
                    "请审核以下评论内容：\n" + content
            );
            String normalized = StringUtils.defaultString(aiResult).toLowerCase();
            if (normalized.contains("pending") || normalized.contains("reject")) {
                return new CommentAutoReviewResult(COMMENT_STATUS_PENDING, extractReviewMessage(aiResult, "评论已进入人工复核"));
            }
        } catch (Exception e) {
            log.warn("comment auto review fallback, reason={}", e.getMessage());
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

    private void sendReplyNotificationIfNeeded(QuestionComment comment, User authorUser) {
        String displayName = StringUtils.defaultIfBlank(authorUser.getUserName(), "有用户");
        if (comment.getReplyToId() != null) {
            QuestionComment replyToComment = getById(comment.getReplyToId());
            if (replyToComment != null && !replyToComment.getUserId().equals(comment.getUserId())) {
                notificationService.sendNotification(
                        replyToComment.getUserId(),
                        "有人回复了你的评论",
                        displayName + " 回复了你：" + StringUtils.abbreviate(comment.getContent(), 20),
                        "reply",
                        comment.getQuestionId()
                );
                return;
            }
        }
        if (comment.getParentId() != null) {
            QuestionComment parentComment = getById(comment.getParentId());
            if (parentComment != null && !parentComment.getUserId().equals(comment.getUserId())) {
                notificationService.sendNotification(
                        parentComment.getUserId(),
                        "你的评论有了新回复",
                        displayName + " 回复了你：" + StringUtils.abbreviate(comment.getContent(), 20),
                        "reply",
                        comment.getQuestionId()
                );
            }
            return;
        }
        Question question = questionMapper.selectById(comment.getQuestionId());
        if (question != null && !Objects.equals(question.getUserId(), comment.getUserId())) {
            notificationService.sendNotification(
                    question.getUserId(),
                    "你的题目有了新评论",
                    displayName + " 评论了你的题目：" + StringUtils.abbreviate(comment.getContent(), 20),
                    "question_comment",
                    comment.getQuestionId()
            );
        }
    }

    private List<UserCommentActivityVO> buildUserCommentActivityVOList(List<QuestionComment> commentList,
                                                                       User loginUser,
                                                                       Map<Long, Date> actionTimeMap,
                                                                       boolean likedContext) {
        if (commentList == null || commentList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, QuestionComment> commentMap = commentList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(QuestionComment::getId, item -> item, (a, b) -> a));

        Set<Long> userIds = commentList.stream()
                .map(QuestionComment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> replyToCommentIds = commentList.stream()
                .map(QuestionComment::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!replyToCommentIds.isEmpty()) {
            List<QuestionComment> replyToComments = listByIds(replyToCommentIds);
            replyToComments.forEach(replyComment -> {
                if (replyComment != null) {
                    if (replyComment.getUserId() != null) {
                        userIds.add(replyComment.getUserId());
                    }
                    commentMap.putIfAbsent(replyComment.getId(), replyComment);
                }
            });
        }

        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        Set<Long> questionIds = commentList.stream()
                .map(QuestionComment::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> questionTitleMap = questionIds.isEmpty()
                ? Collections.emptyMap()
                : questionMapper.selectBatchIds(questionIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Question::getId,
                        question -> StringUtils.defaultIfBlank(question.getTitle(), "题目已不可见"),
                        (a, b) -> a
                ));

        Set<Long> likedIds = Collections.emptySet();
        if (!likedContext) {
            Set<Long> commentIds = commentList.stream().map(QuestionComment::getId).collect(Collectors.toSet());
            if (!commentIds.isEmpty()) {
                LambdaQueryWrapper<QuestionCommentLike> likedWrapper = new LambdaQueryWrapper<>();
                likedWrapper.eq(QuestionCommentLike::getUserId, loginUser.getId())
                        .in(QuestionCommentLike::getCommentId, commentIds);
                likedIds = commentLikeMapper.selectList(likedWrapper).stream()
                        .map(QuestionCommentLike::getCommentId)
                        .collect(Collectors.toSet());
            }
        }

        List<QuestionComment> orderedComments = commentList;
        if (actionTimeMap != null && !actionTimeMap.isEmpty()) {
            orderedComments = actionTimeMap.keySet().stream()
                    .map(commentMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        Set<Long> finalLikedIds = likedIds;
        return orderedComments.stream().map(comment -> {
            UserCommentActivityVO vo = new UserCommentActivityVO();
            vo.setId(comment.getId());
            vo.setQuestionId(comment.getQuestionId());
            vo.setQuestionTitle(questionTitleMap.getOrDefault(comment.getQuestionId(), "题目已不可见"));
            vo.setParentId(comment.getParentId());
            vo.setReplyToId(comment.getReplyToId());
            vo.setContent(comment.getContent());
            vo.setIpLocation(comment.getIpLocation());
            vo.setLikeNum(comment.getLikeNum());
            vo.setStatus(comment.getStatus());
            vo.setReviewMessage(comment.getReviewMessage());
            vo.setCreateTime(comment.getCreateTime());
            vo.setActionTime(actionTimeMap != null
                    ? actionTimeMap.getOrDefault(comment.getId(), comment.getCreateTime())
                    : comment.getCreateTime());
            vo.setDeleted(comment.getIsDelete() != null && comment.getIsDelete() == 1);
            vo.setHasLiked(likedContext || finalLikedIds.contains(comment.getId()));

            User author = userMap.get(comment.getUserId());
            if (author != null) {
                vo.setUser(userService.getUserVO(author));
            }

            if (comment.getReplyToId() != null) {
                QuestionComment replyToComment = commentMap.get(comment.getReplyToId());
                if (replyToComment != null) {
                    User replyToUser = userMap.get(replyToComment.getUserId());
                    if (replyToUser != null) {
                        vo.setReplyToUser(userService.getUserVO(replyToUser));
                    }
                }
            }

            return vo;
        }).collect(Collectors.toList());
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
