package com.xduo.springbootinit.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserRoleEnumTest {

    @Test
    void getValuesReturnsAllRoleValuesInDeclarationOrder() {
        assertEquals(java.util.List.of("user", "admin", "ban"), UserRoleEnum.getValues());
    }

    @Test
    void getEnumByValueReturnsMatchingEnum() {
        assertEquals(UserRoleEnum.ADMIN, UserRoleEnum.getEnumByValue("admin"));
    }

    @Test
    void getEnumByValueReturnsNullForBlankOrUnknownValue() {
        assertNull(UserRoleEnum.getEnumByValue(""));
        assertNull(UserRoleEnum.getEnumByValue(null));
        assertNull(UserRoleEnum.getEnumByValue("guest"));
    }
}
