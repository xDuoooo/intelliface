package com.xduo.springbootinit.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUtilsTest {

    @Test
    void validSortFieldReturnsTrueForSafeIdentifiers() {
        assertTrue(SqlUtils.validSortField("id"));
        assertTrue(SqlUtils.validSortField("createTime"));
        assertTrue(SqlUtils.validSortField("field_1"));
        assertTrue(SqlUtils.validSortField("USER_123"));
    }

    @Test
    void validSortFieldRejectsBlankLikeValues() {
        assertFalse(SqlUtils.validSortField(null));
        assertFalse(SqlUtils.validSortField(""));
        assertFalse(SqlUtils.validSortField(" "));
        assertFalse(SqlUtils.validSortField("\t"));
        assertFalse(SqlUtils.validSortField("undefined"));
        assertFalse(SqlUtils.validSortField("NULL"));
    }

    @Test
    void validSortFieldRejectsPotentialInjectionPatterns() {
        assertFalse(SqlUtils.validSortField("id desc"));
        assertFalse(SqlUtils.validSortField("user name"));
        assertFalse(SqlUtils.validSortField("id=1"));
        assertFalse(SqlUtils.validSortField("count(*)"));
        assertFalse(SqlUtils.validSortField("name;drop"));
    }
}
