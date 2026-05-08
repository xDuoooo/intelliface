package com.xduo.springbootinit.common;

import com.xduo.springbootinit.constant.CommonConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageRequestTest {

    @Test
    void defaultValuesMatchProjectPagingConventions() {
        PageRequest pageRequest = new PageRequest();

        assertEquals(1, pageRequest.getCurrent());
        assertEquals(10, pageRequest.getPageSize());
        assertNull(pageRequest.getSortField());
        assertEquals(CommonConstant.SORT_ORDER_ASC, pageRequest.getSortOrder());
    }

    @Test
    void settersAllowOverridingPagingParameters() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setCurrent(3);
        pageRequest.setPageSize(50);
        pageRequest.setSortField("createTime");
        pageRequest.setSortOrder(CommonConstant.SORT_ORDER_DESC);

        assertEquals(3, pageRequest.getCurrent());
        assertEquals(50, pageRequest.getPageSize());
        assertEquals("createTime", pageRequest.getSortField());
        assertEquals(CommonConstant.SORT_ORDER_DESC, pageRequest.getSortOrder());
    }
}
