package com.xduo.springbootinit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    void gettersExposeExpectedCodeAndMessage() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals("ok", ErrorCode.SUCCESS.getMessage());
        assertEquals(40000, ErrorCode.PARAMS_ERROR.getCode());
    }

    @Test
    void valuesContainsKnownErrorCodes() {
        ErrorCode[] values = ErrorCode.values();

        assertEquals(8, values.length);
        assertTrue(java.util.Arrays.asList(values).contains(ErrorCode.NOT_FOUND_ERROR));
        assertTrue(java.util.Arrays.asList(values).contains(ErrorCode.OPERATION_ERROR));
    }
}
