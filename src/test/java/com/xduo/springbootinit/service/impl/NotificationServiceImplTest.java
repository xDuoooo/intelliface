package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.model.dto.notification.NotificationQueryRequest;
import com.xduo.springbootinit.model.entity.Notification;
import com.xduo.springbootinit.model.vo.NotificationVO;
import com.xduo.springbootinit.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private SystemConfigService systemConfigService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = spy(new NotificationServiceImpl());
        ReflectionTestUtils.setField(notificationService, "systemConfigService", systemConfigService);
    }

    @Test
    void getNotificationVOMapsTargetUrlForSupportedTypes() {
        String rejected = "\u672a\u901a\u8fc7";
        assertTargetUrl("question_bank_review", rejected, "", 12L, "/user/center?tab=banks");
        assertTargetUrl("question_bank_review", "approved", "", 12L, "/bank/12");
        assertTargetUrl("post_review", rejected, "", 66L, "/user/center?tab=posts");
        assertTargetUrl("post_reply", "", "", 88L, "/post/88#post-comment-section");
        assertTargetUrl("post_comment_like", "", "", 89L, "/post/89#post-comment-section");
        assertTargetUrl("post_comment_review", "", "", 90L, "/post/90#post-comment-section");
        assertTargetUrl("post_thumb", "", "", 91L, "/post/91");
        assertTargetUrl("post_favour", "", "", 92L, "/post/92");
        assertTargetUrl("question_review", rejected, "", 9L, "/user/center?tab=submission");
        assertTargetUrl("question_review", "approved", "", 10L, "/question/10");
        assertTargetUrl("reply", "", "", 11L, "/question/11#comment-section");
        assertTargetUrl("like", "", "", 12L, "/question/12#comment-section");
        assertTargetUrl("question_comment", "", "", 13L, "/question/13#comment-section");
        assertTargetUrl("comment_review", "", "", 14L, "/question/14#comment-section");
        assertTargetUrl("question_favour", "", "", 15L, "/question/15");
        assertTargetUrl("user_follow", "", "", 5L, "/user/5");
        assertTargetUrl("learning_goal_reminder", "", "", null, "/user/center?tab=record");
        assertTargetUrl("post_custom", "", "", 7L, "/post/7");
        assertTargetUrl("question_custom", "", "", 3L, "/question/3");
        assertTargetUrl("unknown", "", "", 3L, "/user/notifications");
    }

    @Test
    void getNotificationVOFallsBackToNotificationCenterWhenTargetIdIsMissing() {
        Notification notification = new Notification();
        notification.setType("post_thumb");

        NotificationVO notificationVO = notificationService.getNotificationVO(notification, null);

        assertEquals("/user/notifications", notificationVO.getTargetUrl());
    }

    @Test
    void getNotificationVOPageReturnsEmptyPageWhenSourcePageHasNoRecords() {
        Page<Notification> page = new Page<>(2, 20, 0);
        page.setRecords(List.of());

        Page<NotificationVO> result = notificationService.getNotificationVOPage(page, null);

        assertEquals(2L, result.getCurrent());
        assertEquals(20L, result.getSize());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void getNotificationVOPageMapsEachRecordToViewObject() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setType("user_follow");
        notification.setTargetId(9L);
        Page<Notification> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(notification));

        Page<NotificationVO> result = notificationService.getNotificationVOPage(page, null);

        assertEquals(1, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
        assertEquals("/user/9", result.getRecords().get(0).getTargetUrl());
    }

    @Test
    void getQueryWrapperFallsBackToCreateTimeDescWhenSortFieldIsInvalid() {
        NotificationQueryRequest request = new NotificationQueryRequest();
        request.setSortField("createTime desc");
        request.setSortOrder(CommonConstant.SORT_ORDER_ASC);

        String sqlSegment = String.valueOf(notificationService.getQueryWrapper(request).getSqlSegment())
                .toLowerCase(Locale.ROOT);

        assertTrue(sqlSegment.contains("order by"));
        assertTrue(sqlSegment.contains("createtime"));
        assertTrue(sqlSegment.contains("desc"));
    }

    @Test
    void getQueryWrapperUsesRequestedAscendingSortWhenSortFieldIsValid() {
        NotificationQueryRequest request = new NotificationQueryRequest();
        request.setSortField("title");
        request.setSortOrder(CommonConstant.SORT_ORDER_ASC);

        String sqlSegment = String.valueOf(notificationService.getQueryWrapper(request).getSqlSegment())
                .toLowerCase(Locale.ROOT);

        assertTrue(sqlSegment.contains("order by"));
        assertTrue(sqlSegment.contains("title"));
        assertTrue(sqlSegment.contains("asc"));
    }

    @Test
    void sendNotificationSkipsWhenUserIdIsInvalid() {
        notificationService.sendNotification(0L, "title", "content", "type", 1L);

        verifyNoInteractions(systemConfigService);
        verify(notificationService, never()).save(any(Notification.class));
    }

    @Test
    void sendNotificationSkipsWhenSiteNotificationIsDisabled() {
        when(systemConfigService.isEnableSiteNotification()).thenReturn(false);

        notificationService.sendNotification(1L, "title", "content", "type", 2L);

        verify(notificationService, never()).save(any(Notification.class));
    }

    @Test
    void sendNotificationPersistsNotificationWhenSiteNotificationIsEnabled() {
        when(systemConfigService.isEnableSiteNotification()).thenReturn(true);
        doReturn(true).when(notificationService).save(any(Notification.class));

        notificationService.sendNotification(8L, "System Notice", "Welcome", "system", 99L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(8L, saved.getUserId());
        assertEquals("System Notice", saved.getTitle());
        assertEquals("Welcome", saved.getContent());
        assertEquals("system", saved.getType());
        assertEquals(99L, saved.getTargetId());
        assertEquals(0, saved.getStatus());
    }

    private void assertTargetUrl(String type, String title, String content, Long targetId, String expectedUrl) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetId(targetId);

        NotificationVO notificationVO = notificationService.getNotificationVO(notification, null);

        assertEquals(expectedUrl, notificationVO.getTargetUrl());
    }
}
