package com.xduo.springbootinit.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileConstantTest {

    @Test
    void cosHostConstantKeepsExpectedValue() {
        assertEquals("https://xduo.icu", FileConstant.COS_HOST);
    }
}
