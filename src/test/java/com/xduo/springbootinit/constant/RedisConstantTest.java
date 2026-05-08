package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisConstantTest {

    @Test
    void buildsUserSigninKeyWithYearAndUserId() {
        assertEquals("user:signins:2026:123", RedisConstant.getUserSignInRedisKey(2026, 123L));
    }

    @Test
    void buildsSystemConfigCacheKey() {
        assertEquals("system:config:current", RedisConstant.getSystemConfigCacheKey());
    }

    @Test
    void buildsUserLoginCodeKey() {
        assertEquals("user:login:code:demo@example.com",
                RedisConstant.getUserLoginCodeRedisKey("demo@example.com"));
    }

    @Test
    void buildsUserPhoneAndCaptchaRelatedKeys() {
        assertEquals("user:phone:verify:out-id:13800138000",
                RedisConstant.getUserPhoneVerifyOutIdRedisKey("13800138000"));
        assertEquals("user:login:limit:13800138000",
                RedisConstant.getUserCodeSendLimitRedisKey("13800138000"));
        assertEquals("user:captcha:captcha-1",
                RedisConstant.getUserCaptchaRedisKey("captcha-1"));
        assertEquals("user:ip:limit:127.0.0.1",
                RedisConstant.getUserIpLimitRedisKey("127.0.0.1"));
        assertEquals("user:phone:limit:13800138000",
                RedisConstant.getUserPhoneLimitRedisKey("13800138000"));
    }

    @Test
    void buildsPasswordLoginAndWechatKeys() {
        assertEquals("user:password:login:fail:account-1",
                RedisConstant.getUserPasswordLoginFailRedisKey("account-1"));
        assertEquals("user:password:login:block:account-1",
                RedisConstant.getUserPasswordLoginBlockRedisKey("account-1"));
        assertEquals("wxmp:direct:code:code-123",
                RedisConstant.getWxMpDirectCodeRedisKey("code-123"));
        assertEquals("wxmp:login:ticket:ticket-123",
                RedisConstant.getWxMpLoginTicketRedisKey("ticket-123"));
    }
}
