package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonConstantTest {

    @Test
    void sortOrderConstantsKeepExpectedValues() {
        assertEquals("ascend", CommonConstant.SORT_ORDER_ASC);
        assertEquals("descend", CommonConstant.SORT_ORDER_DESC);
    }
}
