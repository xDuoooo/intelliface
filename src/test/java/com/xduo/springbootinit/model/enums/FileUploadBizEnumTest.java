package com.xduo.springbootinit.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileUploadBizEnumTest {

    @Test
    void getValuesReturnsAllUploadBizValuesInDeclarationOrder() {
        assertEquals(java.util.List.of("user_avatar", "question_bank_cover"), FileUploadBizEnum.getValues());
    }

    @Test
    void getEnumByValueReturnsMatchingEnum() {
        assertEquals(FileUploadBizEnum.QUESTION_BANK_COVER,
                FileUploadBizEnum.getEnumByValue("question_bank_cover"));
    }

    @Test
    void getEnumByValueReturnsNullForBlankOrUnknownValue() {
        assertNull(FileUploadBizEnum.getEnumByValue(""));
        assertNull(FileUploadBizEnum.getEnumByValue(null));
        assertNull(FileUploadBizEnum.getEnumByValue("resume"));
    }
}
