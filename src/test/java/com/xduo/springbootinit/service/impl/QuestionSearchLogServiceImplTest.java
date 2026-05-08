package com.xduo.springbootinit.service.impl;

import com.xduo.springbootinit.model.entity.QuestionSearchLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuestionSearchLogServiceImplTest {

    @Test
    void recordSearchSkipsBlankSearchText() {
        QuestionSearchLogServiceImpl service = spy(new QuestionSearchLogServiceImpl());

        service.recordSearch(1L, "   ", 3, "feed", "1.1.1.1");

        verify(service, never()).save(any(QuestionSearchLog.class));
    }

    @Test
    void recordSearchNormalizesValuesBeforeSaving() {
        QuestionSearchLogServiceImpl service = spy(new QuestionSearchLogServiceImpl());
        doReturn(true).when(service).save(any(QuestionSearchLog.class));

        service.recordSearch(8L, "  java backend interview  ", -2, " ", null);

        ArgumentCaptor<QuestionSearchLog> captor = ArgumentCaptor.forClass(QuestionSearchLog.class);
        verify(service).save(captor.capture());
        QuestionSearchLog savedLog = captor.getValue();
        assertEquals(8L, savedLog.getUserId());
        assertEquals("java backend interview", savedLog.getSearchText());
        assertEquals("question", savedLog.getSource());
        assertEquals(0, savedLog.getResultCount());
        assertEquals(1, savedLog.getHasNoResult());
        assertEquals("", savedLog.getIp());
    }

    @Test
    void recordSearchAbbreviatesLongTextAndMarksPositiveResult() {
        QuestionSearchLogServiceImpl service = spy(new QuestionSearchLogServiceImpl());
        doReturn(true).when(service).save(any(QuestionSearchLog.class));
        String longSearchText = "a".repeat(160);
        String longIp = "2".repeat(160);

        service.recordSearch(9L, longSearchText, 12, "recommend", longIp);

        ArgumentCaptor<QuestionSearchLog> captor = ArgumentCaptor.forClass(QuestionSearchLog.class);
        verify(service).save(captor.capture());
        QuestionSearchLog savedLog = captor.getValue();
        assertTrue(savedLog.getSearchText().length() <= 128);
        assertEquals("recommend", savedLog.getSource());
        assertEquals(12, savedLog.getResultCount());
        assertEquals(0, savedLog.getHasNoResult());
        assertTrue(savedLog.getIp().length() <= 128);
    }
}
