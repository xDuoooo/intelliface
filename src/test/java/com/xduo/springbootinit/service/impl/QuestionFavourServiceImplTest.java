package com.xduo.springbootinit.service.impl;

import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.QuestionMapper;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionFavour;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.QuestionRecommendLogService;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionFavourServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionRecommendLogService questionRecommendLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private QuestionFavourServiceImpl questionFavourService;

    @BeforeEach
    void setUp() {
        questionFavourService = spy(new QuestionFavourServiceImpl());
        ReflectionTestUtils.setField(questionFavourService, "questionMapper", questionMapper);
        ReflectionTestUtils.setField(questionFavourService, "questionRecommendLogService", questionRecommendLogService);
        ReflectionTestUtils.setField(questionFavourService, "notificationService", notificationService);
        ReflectionTestUtils.setField(questionFavourService, "userService", userService);
    }

    @Test
    void doQuestionFavourRejectsMissingQuestion() {
        when(questionMapper.selectById(8L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionFavourService.doQuestionFavour(8L, user(1L, "Alice")));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doQuestionFavourInnerReturnsMinusOneWhenRemovingExistingFavour() {
        doReturn(new QuestionFavour()).when(questionFavourService).getOne(any());
        doReturn(true).when(questionFavourService).remove(any());

        int result = questionFavourService.doQuestionFavourInner(1L, 8L);

        assertEquals(-1, result);
        verify(questionRecommendLogService, never()).logActionByRecentSource(any(), any(), anyString());
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doQuestionFavourInnerThrowsWhenRemoveFails() {
        doReturn(new QuestionFavour()).when(questionFavourService).getOne(any());
        doReturn(false).when(questionFavourService).remove(any());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionFavourService.doQuestionFavourInner(1L, 8L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doQuestionFavourInnerThrowsWhenSaveFails() {
        doReturn(null).when(questionFavourService).getOne(any());
        doReturn(false).when(questionFavourService).save(any(QuestionFavour.class));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionFavourService.doQuestionFavourInner(1L, 8L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doQuestionFavourInnerReturnsOneAndSendsNotification() {
        Question question = question(8L, 2L, "System Design");
        doReturn(null).when(questionFavourService).getOne(any());
        doReturn(true).when(questionFavourService).save(any(QuestionFavour.class));
        doNothing().when(questionRecommendLogService).logActionByRecentSource(1L, 8L, "favour");
        when(questionMapper.selectById(8L)).thenReturn(question);
        when(userService.getById(1L)).thenReturn(user(1L, "Alice"));

        int result = questionFavourService.doQuestionFavourInner(1L, 8L);

        assertEquals(1, result);
        verify(questionRecommendLogService).logActionByRecentSource(1L, 8L, "favour");
        verify(notificationService).sendNotification(eq(2L), anyString(), contains("Alice"), eq("question_favour"), eq(8L));
    }

    @Test
    void doQuestionFavourInnerSkipsNotificationWhenQuestionBelongsToCurrentUser() {
        Question question = question(8L, 1L, "System Design");
        doReturn(null).when(questionFavourService).getOne(any());
        doReturn(true).when(questionFavourService).save(any(QuestionFavour.class));
        when(questionMapper.selectById(8L)).thenReturn(question);

        int result = questionFavourService.doQuestionFavourInner(1L, 8L);

        assertEquals(1, result);
        verify(questionRecommendLogService).logActionByRecentSource(1L, 8L, "favour");
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doQuestionFavourInnerSkipsNotificationWhenQuestionReloadFails() {
        doReturn(null).when(questionFavourService).getOne(any());
        doReturn(true).when(questionFavourService).save(any(QuestionFavour.class));
        when(questionMapper.selectById(8L)).thenReturn(null);

        int result = questionFavourService.doQuestionFavourInner(1L, 8L);

        assertEquals(1, result);
        verify(questionRecommendLogService).logActionByRecentSource(1L, 8L, "favour");
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doQuestionFavourInnerPersistsUserAndQuestionIdsWhenCreatingFavour() {
        doReturn(null).when(questionFavourService).getOne(any());
        doReturn(true).when(questionFavourService).save(any(QuestionFavour.class));
        when(questionMapper.selectById(8L)).thenReturn(null);

        questionFavourService.doQuestionFavourInner(4L, 8L);

        ArgumentCaptor<QuestionFavour> captor = ArgumentCaptor.forClass(QuestionFavour.class);
        verify(questionFavourService).save(captor.capture());
        assertEquals(4L, captor.getValue().getUserId());
        assertEquals(8L, captor.getValue().getQuestionId());
    }

    private Question question(Long id, Long userId, String title) {
        Question question = new Question();
        question.setId(id);
        question.setUserId(userId);
        question.setTitle(title);
        return question;
    }

    private User user(Long id, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        return user;
    }
}
