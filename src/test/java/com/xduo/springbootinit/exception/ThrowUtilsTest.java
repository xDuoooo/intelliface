package com.xduo.springbootinit.exception;

import com.xduo.springbootinit.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThrowUtilsTest {

    @Test
    void throwIfThrowsSameRuntimeExceptionInstance() {
        RuntimeException runtimeException = new IllegalStateException("boom");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ThrowUtils.throwIf(true, runtimeException));

        assertSame(runtimeException, thrown);
    }

    @Test
    void throwIfWrapsErrorCodeIntoBusinessException() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getMessage(), thrown.getMessage());
    }

    @Test
    void throwIfUsesCustomMessageWhenProvided() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> ThrowUtils.throwIf(true, ErrorCode.NO_AUTH_ERROR, "custom message"));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), thrown.getCode());
        assertEquals("custom message", thrown.getMessage());
    }

    @Test
    void throwIfDoesNothingWhenConditionIsFalse() {
        assertDoesNotThrow(() -> ThrowUtils.throwIf(false, ErrorCode.SYSTEM_ERROR));
    }
}
