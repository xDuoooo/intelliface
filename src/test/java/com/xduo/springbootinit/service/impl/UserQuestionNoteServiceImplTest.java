package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.UserQuestionNote;
import com.xduo.springbootinit.model.vo.QuestionVO;
import com.xduo.springbootinit.model.vo.UserQuestionNoteVO;
import com.xduo.springbootinit.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQuestionNoteServiceImplTest {

    @Mock
    private QuestionService questionService;

    private UserQuestionNoteServiceImpl userQuestionNoteService;

    @BeforeEach
    void setUp() {
        userQuestionNoteService = spy(new UserQuestionNoteServiceImpl());
        ReflectionTestUtils.setField(userQuestionNoteService, "questionService", questionService);
    }

    @Test
    void saveMyNoteRejectsInvalidQuestionId() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userQuestionNoteService.saveMyNote(1L, 0L, "content"));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void saveMyNoteRejectsBlankContent() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userQuestionNoteService.saveMyNote(1L, 2L, "  "));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void saveMyNoteRejectsTooLongContent() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userQuestionNoteService.saveMyNote(1L, 2L, "a".repeat(5001)));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void saveMyNoteRejectsMissingQuestion() {
        when(questionService.getById(2L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userQuestionNoteService.saveMyNote(1L, 2L, "content"));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void saveMyNoteUpdatesExistingNoteWithTrimmedContent() {
        UserQuestionNote oldNote = new UserQuestionNote();
        oldNote.setId(3L);
        oldNote.setUserId(1L);
        oldNote.setQuestionId(2L);
        oldNote.setContent("old");
        when(questionService.getById(2L)).thenReturn(new Question());
        doReturn(oldNote).when(userQuestionNoteService).getOne(any());
        doReturn(true).when(userQuestionNoteService).updateById(oldNote);

        boolean result = userQuestionNoteService.saveMyNote(1L, 2L, "  new content  ");

        assertTrue(result);
        assertEquals("new content", oldNote.getContent());
        verify(userQuestionNoteService).updateById(oldNote);
    }

    @Test
    void saveMyNoteCreatesNewNoteWithTrimmedContent() {
        when(questionService.getById(2L)).thenReturn(new Question());
        doReturn(null).when(userQuestionNoteService).getOne(any());
        doReturn(true).when(userQuestionNoteService).save(any(UserQuestionNote.class));

        boolean result = userQuestionNoteService.saveMyNote(1L, 2L, "  hello  ");

        assertTrue(result);
        ArgumentCaptor<UserQuestionNote> captor = ArgumentCaptor.forClass(UserQuestionNote.class);
        verify(userQuestionNoteService).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(2L, captor.getValue().getQuestionId());
        assertEquals("hello", captor.getValue().getContent());
    }

    @Test
    void getMyNoteByQuestionIdReturnsNullWhenNoteMissing() {
        doReturn(null).when(userQuestionNoteService).getOne(any());

        UserQuestionNoteVO result = userQuestionNoteService.getMyNoteByQuestionId(1L, 2L, null);

        assertNull(result);
    }

    @Test
    void getMyNoteByQuestionIdBuildsNoteVOWithoutQuestionDetails() {
        UserQuestionNote note = new UserQuestionNote();
        note.setId(1L);
        note.setUserId(2L);
        note.setQuestionId(3L);
        note.setContent("content");
        doReturn(note).when(userQuestionNoteService).getOne(any());

        UserQuestionNoteVO result = userQuestionNoteService.getMyNoteByQuestionId(2L, 3L, null);

        assertEquals(1L, result.getId());
        assertEquals(2L, result.getUserId());
        assertEquals(3L, result.getQuestionId());
        assertEquals("content", result.getContent());
        assertNull(result.getQuestion());
    }

    @Test
    void listMyNoteByPageReturnsEmptyPageWhenSourcePageEmpty() {
        Page<UserQuestionNote> notePage = new Page<>(2, 5, 0);
        notePage.setRecords(List.of());
        doReturn(notePage).when(userQuestionNoteService).page(any(Page.class), any());

        Page<UserQuestionNoteVO> result = userQuestionNoteService.listMyNoteByPage(1L, new Page<>(2, 5), null);

        assertEquals(2L, result.getCurrent());
        assertEquals(5L, result.getSize());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listMyNoteByPageMapsQuestionVOWhenQuestionExists() {
        UserQuestionNote note = new UserQuestionNote();
        note.setId(1L);
        note.setUserId(2L);
        note.setQuestionId(3L);
        note.setContent("content");
        Page<UserQuestionNote> notePage = new Page<>(1, 10, 1);
        notePage.setRecords(List.of(note));
        Question question = new Question();
        question.setId(3L);
        QuestionVO questionVO = new QuestionVO();
        questionVO.setId(3L);
        questionVO.setTitle("Question");
        doReturn(notePage).when(userQuestionNoteService).page(any(Page.class), any());
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(question));
        when(questionService.getQuestionVO(question, null)).thenReturn(questionVO);

        Page<UserQuestionNoteVO> result = userQuestionNoteService.listMyNoteByPage(2L, new Page<>(1, 10), null);

        assertEquals(1, result.getRecords().size());
        assertEquals("content", result.getRecords().get(0).getContent());
        assertEquals(3L, result.getRecords().get(0).getQuestion().getId());
    }

    @Test
    void listMyNoteByPageLeavesQuestionNullWhenQuestionMissing() {
        UserQuestionNote note = new UserQuestionNote();
        note.setId(1L);
        note.setUserId(2L);
        note.setQuestionId(3L);
        Page<UserQuestionNote> notePage = new Page<>(1, 10, 1);
        notePage.setRecords(List.of(note));
        doReturn(notePage).when(userQuestionNoteService).page(any(Page.class), any());
        when(questionService.listByIds(anyCollection())).thenReturn(List.of());

        Page<UserQuestionNoteVO> result = userQuestionNoteService.listMyNoteByPage(2L, new Page<>(1, 10), null);

        assertEquals(1, result.getRecords().size());
        assertNull(result.getRecords().get(0).getQuestion());
    }

    @Test
    void deleteMyNoteDelegatesToRemove() {
        doReturn(true).when(userQuestionNoteService).remove(any());

        boolean result = userQuestionNoteService.deleteMyNote(1L, 2L);

        assertTrue(result);
        verify(userQuestionNoteService).remove(any());
    }
}
