package com.xduo.springbootinit.exception;

import com.xduo.springbootinit.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessExceptionTest {

    @Test
    void constructorWithCodeAndMessageUsesProvidedValues() {
        BusinessException exception = new BusinessException(40001, "bad request");

        assertEquals(40001, exception.getCode());
        assertEquals("bad request", exception.getMessage());
    }

    @Test
    void constructorWithErrorCodeUsesEnumValues() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_LOGIN_ERROR);

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), exception.getCode());
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getMessage(), exception.getMessage());
    }

    @Test
    void constructorWithErrorCodeAndMessageOverridesMessageOnly() {
        BusinessException exception = new BusinessException(ErrorCode.SYSTEM_ERROR, "db down");

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), exception.getCode());
        assertEquals("db down", exception.getMessage());
    }
}
