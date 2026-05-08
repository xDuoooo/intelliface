package com.xduo.springbootinit.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionHandlerReturnsBusinessCodeAndMessage() {
        BaseResponse<?> response = handler.businessExceptionHandler(
                new BusinessException(ErrorCode.PARAMS_ERROR, "bad request"));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
        assertEquals("bad request", response.getMessage());
    }

    @Test
    void notRoleExceptionHandlerReturnsNoAuthError() {
        BaseResponse<?> response = handler.notRoleExceptionHandler(new NotRoleException("admin"));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), response.getCode());
        assertFalse(response.getMessage().isBlank());
    }

    @Test
    void notLoginExceptionHandlerReturnsNotLoginError() {
        BaseResponse<?> response = handler.notLoginExceptionHandler(
                new NotLoginException(NotLoginException.NOT_TOKEN, "login", null));

        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), response.getCode());
        assertFalse(response.getMessage().isBlank());
    }

    @Test
    void maxUploadSizeExceededExceptionHandlerReturnsParamsError() {
        BaseResponse<?> response = handler.maxUploadSizeExceededExceptionHandler(
                new MaxUploadSizeExceededException(1024));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
        assertFalse(response.getMessage().isBlank());
    }

    @Test
    void requestParamExceptionHandlerReturnsParamsError() {
        BaseResponse<?> response = handler.requestParamExceptionHandler(new MultipartException("bad multipart"));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
        assertFalse(response.getMessage().isBlank());
    }

    @Test
    void runtimeExceptionHandlerReturnsSystemError() {
        BaseResponse<?> response = handler.runtimeExceptionHandler(new RuntimeException("boom"));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertFalse(response.getMessage().isBlank());
    }
}
