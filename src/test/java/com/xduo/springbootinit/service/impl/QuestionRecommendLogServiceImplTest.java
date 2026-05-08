package com.xduo.springbootinit.service.impl;

import com.xduo.springbootinit.model.entity.QuestionRecommendLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendLogServiceImplTest {

    @Test
    void logExposureSkipsWhenQuestionIdsMissingOrSourceBlank() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());

        service.logExposure(1L, "feed", null);
        service.logExposure(1L, " ", List.of(1L, 2L));

        verify(service, never()).saveBatch(anyCollection());
    }

    @Test
    void logExposureSavesDistinctPositiveQuestionIds() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());
        doReturn(true).when(service).saveBatch(anyCollection());

        service.logExposure(5L, "  feed  ", Arrays.asList(3L, null, 3L, 0L, -1L, 8L));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(service).saveBatch(captor.capture());
        @SuppressWarnings("unchecked")
        List<QuestionRecommendLog> logList = (List<QuestionRecommendLog>) captor.getValue();
        assertEquals(2, logList.size());
        assertEquals(3L, logList.get(0).getQuestionId());
        assertEquals(8L, logList.get(1).getQuestionId());
        assertTrue(logList.stream().allMatch(log -> log.getUserId().equals(5L)));
        assertTrue(logList.stream().allMatch(log -> "feed".equals(log.getSource())));
        assertTrue(logList.stream().allMatch(log -> "exposure".equals(log.getAction())));
    }

    @Test
    void logExposureDoesNothingWhenAllQuestionIdsInvalid() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());

        service.logExposure(5L, "feed", Arrays.asList(null, 0L, -1L));

        verify(service, never()).saveBatch(anyCollection());
    }

    @Test
    void logClickSkipsInvalidQuestionId() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());

        service.logClick(1L, "feed", 0L);
        service.logClick(1L, " ", 8L);

        verify(service, never()).save(any(QuestionRecommendLog.class));
    }

    @Test
    void logClickSavesNormalizedLog() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());
        doReturn(true).when(service).save(any(QuestionRecommendLog.class));

        service.logClick(2L, "  related  ", 11L);

        ArgumentCaptor<QuestionRecommendLog> captor = ArgumentCaptor.forClass(QuestionRecommendLog.class);
        verify(service).save(captor.capture());
        QuestionRecommendLog savedLog = captor.getValue();
        assertEquals(2L, savedLog.getUserId());
        assertEquals(11L, savedLog.getQuestionId());
        assertEquals("related", savedLog.getSource());
        assertEquals("click", savedLog.getAction());
    }

    @Test
    void logActionByRecentSourceSkipsWhenArgumentsInvalid() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());

        service.logActionByRecentSource(null, 1L, "practice");
        service.logActionByRecentSource(1L, 0L, "practice");
        service.logActionByRecentSource(1L, 1L, " ");

        verify(service, never()).getOne(any());
        verify(service, never()).save(any(QuestionRecommendLog.class));
    }

    @Test
    void logActionByRecentSourceSkipsWhenRecentSourceMissing() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());
        doReturn(null).when(service).getOne(any());

        service.logActionByRecentSource(3L, 7L, "practice");

        verify(service).getOne(any());
        verify(service, never()).save(any(QuestionRecommendLog.class));
    }

    @Test
    void logActionByRecentSourceSkipsWhenRecentLogSourceBlank() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());
        QuestionRecommendLog recentLog = new QuestionRecommendLog();
        recentLog.setSource(" ");
        doReturn(recentLog).when(service).getOne(any());

        service.logActionByRecentSource(3L, 7L, "practice");

        verify(service).getOne(any());
        verify(service, never()).save(any(QuestionRecommendLog.class));
    }

    @Test
    void logActionByRecentSourceSavesUsingLatestSource() {
        QuestionRecommendLogServiceImpl service = spy(new QuestionRecommendLogServiceImpl());
        QuestionRecommendLog recentLog = new QuestionRecommendLog();
        recentLog.setSource("resume");
        doReturn(recentLog).when(service).getOne(any());
        doReturn(true).when(service).save(any(QuestionRecommendLog.class));

        service.logActionByRecentSource(4L, 9L, "mastered");

        ArgumentCaptor<QuestionRecommendLog> captor = ArgumentCaptor.forClass(QuestionRecommendLog.class);
        verify(service).save(captor.capture());
        QuestionRecommendLog savedLog = captor.getValue();
        assertEquals(4L, savedLog.getUserId());
        assertEquals(9L, savedLog.getQuestionId());
        assertEquals("resume", savedLog.getSource());
        assertEquals("mastered", savedLog.getAction());
    }
}
