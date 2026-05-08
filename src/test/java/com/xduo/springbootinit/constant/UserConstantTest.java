package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserConstantTest {

    @Test
    void userRoleConstantsKeepExpectedValues() {
        assertEquals("user_login", UserConstant.USER_LOGIN_STATE);
        assertEquals("user", UserConstant.DEFAULT_ROLE);
        assertEquals("admin", UserConstant.ADMIN_ROLE);
        assertEquals("ban", UserConstant.BAN_ROLE);
    }
}
