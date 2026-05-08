package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionConstantTest {

    @Test
    void allowedReviewStatusSetContainsExpectedQuestionStatuses() {
        assertEquals(4, QuestionConstant.ALLOWED_REVIEW_STATUS_SET.size());
        assertTrue(QuestionConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_PRIVATE));
        assertTrue(QuestionConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_PENDING));
        assertTrue(QuestionConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_APPROVED));
        assertTrue(QuestionConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_REJECTED));
    }

    @Test
    void adminAllowedReviewStatusSetContainsApprovedAndRejected() {
        assertEquals(2, QuestionConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.size());
        assertTrue(QuestionConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_APPROVED));
        assertTrue(QuestionConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(QuestionConstant.REVIEW_STATUS_REJECTED));
    }

    @Test
    void allowedDifficultySetContainsAllDifficultyConstants() {
        assertEquals(3, QuestionConstant.ALLOWED_DIFFICULTY_SET.size());
        assertTrue(QuestionConstant.ALLOWED_DIFFICULTY_SET.contains(QuestionConstant.DIFFICULTY_EASY));
        assertTrue(QuestionConstant.ALLOWED_DIFFICULTY_SET.contains(QuestionConstant.DIFFICULTY_MEDIUM));
        assertTrue(QuestionConstant.ALLOWED_DIFFICULTY_SET.contains(QuestionConstant.DIFFICULTY_HARD));
    }
}
