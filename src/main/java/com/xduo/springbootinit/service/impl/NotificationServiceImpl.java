package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.mapper.NotificationMapper;
import com.xduo.springbootinit.model.dto.notification.NotificationQueryRequest;
import com.xduo.springbootinit.model.entity.Notification;
import com.xduo.springbootinit.model.vo.NotificationVO;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.SystemConfigService;
import com.xduo.springbootinit.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务实现
 */
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @jakarta.annotation.Resource
    private SystemConfigService systemConfigService;

    @Override
    public NotificationVO getNotificationVO(Notification notification, HttpServletRequest request) {
        NotificationVO notificationVO = new NotificationVO();
        BeanUtils.copyProperties(notification, notificationVO);
        notificationVO.setTargetUrl(resolveNotificationTargetUrl(notification));
        return notificationVO;
    }

    @Override
    public Page<NotificationVO> getNotificationVOPage(Page<Notification> notificationPage, HttpServletRequest request) {
        List<Notification> notificationList = notificationPage.getRecords();
        Page<NotificationVO> notificationVOPage = new Page<>(notificationPage.getCurrent(), notificationPage.getSize(), notificationPage.getTotal());
        if (notificationList.isEmpty()) {
            return notificationVOPage;
        }
        List<NotificationVO> notificationVOList = notificationList.stream().map(notification -> {
            return getNotificationVO(notification, request);
        }).collect(Collectors.toList());
        notificationVOPage.setRecords(notificationVOList);
        return notificationVOPage;
    }

    @Override
    public QueryWrapper<Notification> getQueryWrapper(NotificationQueryRequest notificationQueryRequest) {
        QueryWrapper<Notification> queryWrapper = new QueryWrapper<>();
        if (notificationQueryRequest == null) {
            return queryWrapper;
        }
        Long id = notificationQueryRequest.getId();
        Long userId = notificationQueryRequest.getUserId();
        String title = notificationQueryRequest.getTitle();
        String content = notificationQueryRequest.getContent();
        String type = notificationQueryRequest.getType();
        Integer status = notificationQueryRequest.getStatus();
        String sortField = notificationQueryRequest.getSortField();
        String sortOrder = notificationQueryRequest.getSortOrder();
        Long targetId = notificationQueryRequest.getTargetId();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(userId != null && userId > 0, "userId", userId);
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.eq(StringUtils.isNotBlank(type), "type", type);
        queryWrapper.eq(status != null, "status", status);
        queryWrapper.eq(targetId != null, "targetId", targetId);
        queryWrapper.eq("isDelete", 0);
        if (SqlUtils.validSortField(sortField)) {
            queryWrapper.orderBy(true, CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    @Override
    @Async
    public void sendNotification(Long userId, String title, String content, String type, Long targetId) {
        if (userId == null || userId <= 0) {
            return;
        }
        if (!systemConfigService.isEnableSiteNotification()) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setTargetId(targetId);
        notification.setStatus(0);
        this.save(notification);
    }

    private String resolveNotificationTargetUrl(Notification notification) {
        if (notification == null) {
            return "/user/notifications";
        }
        String type = StringUtils.defaultString(notification.getType());
        String title = StringUtils.defaultString(notification.getTitle());
        String content = StringUtils.defaultString(notification.getContent());
        Long targetId = notification.getTargetId();

        switch (type) {
            case "question_bank_review":
                if (title.contains("未通过") || content.contains("未通过")) {
                    return "/user/center?tab=banks";
                }
                return targetId != null && targetId > 0 ? "/bank/" + targetId : "/user/center?tab=banks";
            case "post_review":
                if (title.contains("未通过") || content.contains("未通过")) {
                    return "/user/center?tab=posts";
                }
                return targetId != null && targetId > 0 ? "/post/" + targetId : "/user/center?tab=posts";
            case "post_reply":
            case "post_comment_like":
            case "post_comment_review":
                return targetId != null && targetId > 0 ? "/post/" + targetId + "#post-comment-section" : "/user/notifications";
            case "post_thumb":
            case "post_favour":
                return targetId != null && targetId > 0 ? "/post/" + targetId : "/user/notifications";
            case "question_review":
                if (title.contains("未通过") || content.contains("未通过")) {
                    return "/user/center?tab=submission";
                }
                return targetId != null && targetId > 0 ? "/question/" + targetId : "/user/center?tab=submission";
            case "reply":
            case "like":
            case "question_comment":
            case "comment_review":
                return targetId != null && targetId > 0 ? "/question/" + targetId + "#comment-section" : "/user/notifications";
            case "question_favour":
                return targetId != null && targetId > 0 ? "/question/" + targetId : "/user/notifications";
            case "user_follow":
                return targetId != null && targetId > 0 ? "/user/" + targetId : "/user/notifications";
            case "learning_goal_reminder":
                return "/user/center?tab=record";
            default:
                if (targetId != null && targetId > 0) {
                    if (type.startsWith("post")) {
                        return "/post/" + targetId;
                    }
                    if (type.startsWith("question")) {
                        return "/question/" + targetId;
                    }
                }
                return "/user/notifications";
        }
    }
}
