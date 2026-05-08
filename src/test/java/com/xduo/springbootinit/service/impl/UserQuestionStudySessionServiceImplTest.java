package com.xduo.springbootinit.service.impl;

import com.xduo.springbootinit.model.entity.UserQuestionStudySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserQuestionStudySessionServiceImplTest {

    private UserQuestionStudySessionServiceImpl userQuestionStudySessionService;

    @BeforeEach
    void setUp() {
        userQuestionStudySessionService = spy(new UserQuestionStudySessionServiceImpl());
    }

    @Test
    void recordStudySessionReturnsFalseForInvalidInput() {
        assertFalse(userQuestionStudySessionService.recordStudySession(0L, 1L, 10));
        assertFalse(userQuestionStudySessionService.recordStudySession(1L, 0L, 10));
        assertFalse(userQuestionStudySessionService.recordStudySession(1L, 1L, 0));
        verify(userQuestionStudySessionService, never()).save(any(UserQuestionStudySession.class));
    }

    @Test
    void recordStudySessionSavesSessionForValidInput() {
        doReturn(true).when(userQuestionStudySessionService).save(any(UserQuestionStudySession.class));

        boolean result = userQuestionStudySessionService.recordStudySession(1L, 2L, 120);

        assertTrue(result);
        ArgumentCaptor<UserQuestionStudySession> captor = ArgumentCaptor.forClass(UserQuestionStudySession.class);
        verify(userQuestionStudySessionService).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(2L, captor.getValue().getQuestionId());
        assertEquals(120, captor.getValue().getDurationSeconds());
    }

    @Test
    void getTotalStudyDurationSecondsReturnsZeroWhenAggregateMissing() {
        doReturn(null).when(userQuestionStudySessionService).getMap(any());

        long result = userQuestionStudySessionService.getTotalStudyDurationSeconds(1L);

        assertEquals(0L, result);
    }

    @Test
    void getTotalStudyDurationSecondsReturnsParsedAggregateValue() {
        doReturn(Map.of("totalDuration", "240")).when(userQuestionStudySessionService).getMap(any());

        long result = userQuestionStudySessionService.getTotalStudyDurationSeconds(1L);

        assertEquals(240L, result);
    }

    @Test
    void getTodayStudyDurationSecondsReturnsParsedAggregateValue() {
        doReturn(Map.of("totalDuration", 90)).when(userQuestionStudySessionService).getMap(any());

        long result = userQuestionStudySessionService.getTodayStudyDurationSeconds(1L);

        assertEquals(90L, result);
    }

    @Test
    void countStudySessionsDelegatesToCount() {
        doReturn(5L).when(userQuestionStudySessionService).count(any());

        long result = userQuestionStudySessionService.countStudySessions(1L);

        assertEquals(5L, result);
    }

    @Test
    void getStudyStatsReturnsZeroValuesWhenAggregateMissing() {
        doReturn(null).when(userQuestionStudySessionService).getMap(any());

        Map<String, Long> result = userQuestionStudySessionService.getStudyStats(1L);

        assertEquals(0L, result.get("totalDurationSeconds"));
        assertEquals(0L, result.get("todayDurationSeconds"));
        assertEquals(0L, result.get("sessionCount"));
    }

    @Test
    void getStudyStatsParsesAggregateValues() {
        doReturn(Map.of(
                "totalDurationSeconds", "360",
                "todayDurationSeconds", 120,
                "sessionCount", "6"
        )).when(userQuestionStudySessionService).getMap(any());

        Map<String, Long> result = userQuestionStudySessionService.getStudyStats(1L);

        assertEquals(360L, result.get("totalDurationSeconds"));
        assertEquals(120L, result.get("todayDurationSeconds"));
        assertEquals(6L, result.get("sessionCount"));
    }
}
