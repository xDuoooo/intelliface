package com.xduo.springbootinit.utils;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringContextUtilsTest {

    @Test
    void getBeanByNameReturnsBeanFromStoredContext() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SpringContextUtils springContextUtils = new SpringContextUtils();
        when(applicationContext.getBean("demoBean")).thenReturn("value");
        springContextUtils.setApplicationContext(applicationContext);

        Object result = SpringContextUtils.getBean("demoBean");

        assertEquals("value", result);
    }

    @Test
    void getBeanByClassReturnsTypedBeanFromStoredContext() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SpringContextUtils springContextUtils = new SpringContextUtils();
        when(applicationContext.getBean(String.class)).thenReturn("typed");
        springContextUtils.setApplicationContext(applicationContext);

        String result = SpringContextUtils.getBean(String.class);

        assertEquals("typed", result);
    }

    @Test
    void getBeanByNameAndClassReturnsTypedBeanFromStoredContext() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SpringContextUtils springContextUtils = new SpringContextUtils();
        when(applicationContext.getBean("demoBean", String.class)).thenReturn("named-typed");
        springContextUtils.setApplicationContext(applicationContext);

        String result = SpringContextUtils.getBean("demoBean", String.class);

        assertEquals("named-typed", result);
    }
}
