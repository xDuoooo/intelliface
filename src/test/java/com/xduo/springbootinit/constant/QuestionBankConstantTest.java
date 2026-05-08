package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionBankConstantTest {

    @Test
    void allowedReviewStatusSetContainsPrivateAndReviewStatuses() {
        assertEquals(4, QuestionBankConstant.ALLOWED_REVIEW_STATUS_SET.size());
        assertTrue(QuestionBankConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_PRIVATE));
        assertTrue(QuestionBankConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_PENDING));
        assertTrue(QuestionBankConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_APPROVED));
        assertTrue(QuestionBankConstant.ALLOWED_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_REJECTED));
    }

    @Test
    void adminAllowedReviewStatusSetContainsOnlyApprovedAndRejected() {
        assertEquals(2, QuestionBankConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.size());
        assertTrue(QuestionBankConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_APPROVED));
        assertTrue(QuestionBankConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(QuestionBankConstant.REVIEW_STATUS_REJECTED));
    }
}
