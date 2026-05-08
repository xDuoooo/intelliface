package com.xduo.springbootinit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeleteRequestTest {

    @Test
    void gettersAndSettersStoreId() {
        DeleteRequest request = new DeleteRequest();

        request.setId(99L);

        assertEquals(99L, request.getId());
    }
}
