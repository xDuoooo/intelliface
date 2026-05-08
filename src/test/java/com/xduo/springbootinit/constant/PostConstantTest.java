package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostConstantTest {

    @Test
    void allowedReviewStatusSetContainsAllPostStatuses() {
        assertEquals(3, PostConstant.ALLOWED_REVIEW_STATUS_SET.size());
        assertTrue(PostConstant.ALLOWED_REVIEW_STATUS_SET.contains(PostConstant.REVIEW_STATUS_PENDING));
        assertTrue(PostConstant.ALLOWED_REVIEW_STATUS_SET.contains(PostConstant.REVIEW_STATUS_APPROVED));
        assertTrue(PostConstant.ALLOWED_REVIEW_STATUS_SET.contains(PostConstant.REVIEW_STATUS_REJECTED));
    }

    @Test
    void adminAllowedReviewStatusSetExcludesPendingStatus() {
        assertEquals(2, PostConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.size());
        assertTrue(PostConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(PostConstant.REVIEW_STATUS_APPROVED));
        assertTrue(PostConstant.ALLOWED_ADMIN_REVIEW_STATUS_SET.contains(PostConstant.REVIEW_STATUS_REJECTED));
    }
}
