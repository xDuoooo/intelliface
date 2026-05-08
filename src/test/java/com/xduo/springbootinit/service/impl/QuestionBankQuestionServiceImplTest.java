package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionBank;
import com.xduo.springbootinit.model.entity.QuestionBankQuestion;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.QuestionBankQuestionVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.QuestionBankService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankQuestionServiceImplTest {

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private QuestionService questionService;

    @Mock
    private UserService userService;

    private QuestionBankQuestionServiceImpl questionBankQuestionService;

    @BeforeEach
    void setUp() {
        questionBankQuestionService = spy(new QuestionBankQuestionServiceImpl());
        ReflectionTestUtils.setField(questionBankQuestionService, "questionBankService", questionBankService);
        ReflectionTestUtils.setField(questionBankQuestionService, "questionService", questionService);
        ReflectionTestUtils.setField(questionBankQuestionService, "userService", userService);
    }

    @Test
    void getQueryWrapperUsesValidSortField() {
        QuestionBankQuestionQueryRequest request = new QuestionBankQuestionQueryRequest();
        request.setQuestionBankId(1L);
        request.setSortField("questionId");
        request.setSortOrder(CommonConstant.SORT_ORDER_ASC);

        String sqlSegment = String.valueOf(questionBankQuestionService.getQueryWrapper(request).getSqlSegment())
                .toLowerCase(Locale.ROOT);

        assertTrue(sqlSegment.contains("order by"));
        assertTrue(sqlSegment.contains("questionid"));
        assertTrue(sqlSegment.contains("asc"));
    }

    @Test
    void getQueryWrapperSkipsOrderingWhenSortFieldIsInvalid() {
        QuestionBankQuestionQueryRequest request = new QuestionBankQuestionQueryRequest();
        request.setSortField("questionId desc");

        String sqlSegment = String.valueOf(questionBankQuestionService.getQueryWrapper(request).getSqlSegment())
                .toLowerCase(Locale.ROOT);

        assertFalse(sqlSegment.contains("order by"));
    }

    @Test
    void validQuestionBankQuestionPassesWhenEntitiesExist() {
        QuestionBankQuestion relation = new QuestionBankQuestion();
        relation.setQuestionId(1L);
        relation.setQuestionBankId(2L);
        when(questionService.getById(1L)).thenReturn(new Question());
        when(questionBankService.getById(2L)).thenReturn(new QuestionBank());

        assertDoesNotThrow(() -> questionBankQuestionService.validQuestionBankQuestion(relation, true));
    }

    @Test
    void validQuestionBankQuestionRejectsMissingQuestion() {
        QuestionBankQuestion relation = new QuestionBankQuestion();
        relation.setQuestionId(1L);
        relation.setQuestionBankId(2L);
        when(questionService.getById(1L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.validQuestionBankQuestion(relation, true));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void validQuestionBankQuestionRejectsMissingQuestionBank() {
        QuestionBankQuestion relation = new QuestionBankQuestion();
        relation.setQuestionId(1L);
        relation.setQuestionBankId(2L);
        when(questionService.getById(1L)).thenReturn(new Question());
        when(questionBankService.getById(2L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.validQuestionBankQuestion(relation, true));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void batchRemoveQuestionsFromBankRejectsMissingRelation() {
        doReturn(0L).when(questionBankQuestionService).count(any());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.batchRemoveQuestionsFromBank(List.of(1L, 2L), 9L));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void batchRemoveQuestionsFromBankRejectsAllInvalidIds() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.batchRemoveQuestionsFromBank(Arrays.asList(null, -1L, 0L), 9L));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void batchRemoveQuestionsFromBankRemovesDistinctValidIds() {
        doReturn(2L).when(questionBankQuestionService).count(any());
        doReturn(true).when(questionBankQuestionService).remove(any());

        questionBankQuestionService.batchRemoveQuestionsFromBank(Arrays.asList(1L, 2L, 2L, null, -1L), 9L);

        org.mockito.Mockito.verify(questionBankQuestionService).count(any());
        org.mockito.Mockito.verify(questionBankQuestionService).remove(any());
    }

    @Test
    void batchAddQuestionsToBankInnerWrapsDuplicateConstraintFailure() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(questionBankQuestionService).saveBatch(anyCollection());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.batchAddQuestionsToBankInner(List.of(new QuestionBankQuestion())));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
        assertNotNull(thrown.getMessage());
        assertFalse(thrown.getMessage().isBlank());
    }

    @Test
    void batchAddQuestionsToBankInnerThrowsWhenSaveBatchReturnsFalse() {
        doReturn(false).when(questionBankQuestionService).saveBatch(anyCollection());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> questionBankQuestionService.batchAddQuestionsToBankInner(List.of(new QuestionBankQuestion())));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void getQuestionBankQuestionVOPageMapsUsersAndTags() {
        QuestionBankQuestion first = new QuestionBankQuestion();
        first.setId(1L);
        first.setQuestionId(101L);
        first.setUserId(201L);
        QuestionBankQuestion second = new QuestionBankQuestion();
        second.setId(2L);
        second.setQuestionId(102L);
        second.setUserId(202L);
        Page<QuestionBankQuestion> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(first, second));

        User firstUser = new User();
        firstUser.setId(201L);
        User secondUser = new User();
        secondUser.setId(202L);
        when(userService.listByIds(anyCollection())).thenReturn(List.of(firstUser, secondUser));
        when(userService.getUserVO(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user == null) {
                return null;
            }
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUserName("user-" + user.getId());
            return userVO;
        });

        Question firstQuestion = new Question();
        firstQuestion.setId(101L);
        firstQuestion.setTags("[\"java\",\"sql\"]");
        Question secondQuestion = new Question();
        secondQuestion.setId(102L);
        when(questionService.listByIds(anyCollection())).thenReturn(List.of(firstQuestion, secondQuestion));

        Page<QuestionBankQuestionVO> result = questionBankQuestionService.getQuestionBankQuestionVOPage(page, null);

        assertEquals(2, result.getRecords().size());
        assertEquals(201L, result.getRecords().get(0).getUser().getId());
        assertEquals(List.of("java", "sql"), result.getRecords().get(0).getTagList());
        assertEquals(202L, result.getRecords().get(1).getUser().getId());
        assertNull(result.getRecords().get(1).getTagList());
    }

    @Test
    void getQuestionBankQuestionVOMapsSingleRelationUserAndTags() {
        QuestionBankQuestion relation = new QuestionBankQuestion();
        relation.setId(1L);
        relation.setUserId(201L);
        relation.setQuestionId(301L);

        User user = new User();
        user.setId(201L);
        UserVO userVO = new UserVO();
        userVO.setId(201L);
        when(userService.getById(201L)).thenReturn(user);
        when(userService.getUserVO(user)).thenReturn(userVO);
        Question question = new Question();
        question.setId(301L);
        question.setTags("[\"redis\"]");
        when(questionService.getById(301L)).thenReturn(question);

        QuestionBankQuestionVO result = questionBankQuestionService.getQuestionBankQuestionVO(relation, null);

        assertEquals(201L, result.getUser().getId());
        assertEquals(List.of("redis"), result.getTagList());
    }

    @Test
    void getQuestionBankQuestionVOReturnsNullWhenRelationIsNull() {
        assertNull(questionBankQuestionService.getQuestionBankQuestionVO(null, null));
    }

    @Test
    void getQuestionBankQuestionVOPageReturnsEmptyPageWhenSourcePageHasNoRecords() {
        Page<QuestionBankQuestion> page = new Page<>(3, 5, 0);
        page.setRecords(List.of());

        Page<QuestionBankQuestionVO> result = questionBankQuestionService.getQuestionBankQuestionVOPage(page, null);

        assertEquals(3L, result.getCurrent());
        assertEquals(5L, result.getSize());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }
}
