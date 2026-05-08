package com.xduo.springbootinit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseResponseTest {

    @Test
    void constructorWithCodeDataAndMessageStoresAllFields() {
        BaseResponse<String> response = new BaseResponse<>(200, "data", "message");

        assertEquals(200, response.getCode());
        assertEquals("data", response.getData());
        assertEquals("message", response.getMessage());
    }

    @Test
    void constructorWithCodeAndDataUsesEmptyMessage() {
        BaseResponse<Integer> response = new BaseResponse<>(201, 1);

        assertEquals(201, response.getCode());
        assertEquals(1, response.getData());
        assertEquals("", response.getMessage());
    }

    @Test
    void constructorWithErrorCodeBuildsErrorResponse() {
        BaseResponse<Object> response = new BaseResponse<>(ErrorCode.FORBIDDEN_ERROR);

        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), response.getCode());
        assertNull(response.getData());
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getMessage(), response.getMessage());
    }
}
