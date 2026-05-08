package com.xduo.springbootinit.model.vo;

import com.xduo.springbootinit.model.entity.QuestionBankQuestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestionBankQuestionVOTest {

    @Test
    void objToVoReturnsNullWhenSourceIsNull() {
        assertNull(QuestionBankQuestionVO.objToVo(null));
    }

    @Test
    void objToVoCopiesCoreFields() {
        QuestionBankQuestion relation = new QuestionBankQuestion();
        relation.setId(1L);
        relation.setQuestionBankId(2L);
        relation.setQuestionId(3L);
        relation.setUserId(4L);

        QuestionBankQuestionVO vo = QuestionBankQuestionVO.objToVo(relation);

        assertEquals(1L, vo.getId());
        assertEquals(2L, vo.getQuestionBankId());
        assertEquals(3L, vo.getQuestionId());
        assertEquals(4L, vo.getUserId());
    }
}
