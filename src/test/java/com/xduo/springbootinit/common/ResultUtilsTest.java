package com.xduo.springbootinit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultUtilsTest {

    @Test
    void successBuildsOkResponse() {
        BaseResponse<String> response = ResultUtils.success("payload");

        assertEquals(0, response.getCode());
        assertEquals("payload", response.getData());
        assertEquals("ok", response.getMessage());
    }

    @Test
    void errorWithErrorCodeBuildsResponseFromEnum() {
        BaseResponse response = ResultUtils.error(ErrorCode.NOT_FOUND_ERROR);

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), response.getCode());
        assertNull(response.getData());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getMessage(), response.getMessage());
    }

    @Test
    void errorWithCodeAndMessageUsesProvidedValues() {
        BaseResponse response = ResultUtils.error(418, "teapot");

        assertEquals(418, response.getCode());
        assertNull(response.getData());
        assertEquals("teapot", response.getMessage());
    }

    @Test
    void errorWithErrorCodeAndCustomMessageOverridesDefaultMessage() {
        BaseResponse response = ResultUtils.error(ErrorCode.OPERATION_ERROR, "retry later");

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), response.getCode());
        assertNull(response.getData());
        assertEquals("retry later", response.getMessage());
    }
}
