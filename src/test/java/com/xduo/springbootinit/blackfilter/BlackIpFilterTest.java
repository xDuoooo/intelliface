package com.xduo.springbootinit.blackfilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackIpFilterTest {

    private final BlackIpFilter blackIpFilter = new BlackIpFilter();

    @Test
    void doFilterBlocksBlacklistedLoopbackRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("0:0:0:0:0:0:0:1");
        ServletResponse response = mock(ServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain filterChain = mock(FilterChain.class);

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            blackIpUtilsMock.when(() -> BlackIpUtils.isBlackIp("127.0.0.1")).thenReturn(true);

            blackIpFilter.doFilter(request, response, filterChain);

            verify(response).setContentType("text/json;charset=UTF-8");
            verify(filterChain, never()).doFilter(any(), any());
            assertTrue(body.toString().contains("errorCode"));
        }
    }

    @Test
    void doFilterPassesThroughNonBlacklistedRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        ServletResponse response = mock(ServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        try (MockedStatic<BlackIpUtils> blackIpUtilsMock = mockStatic(BlackIpUtils.class)) {
            blackIpUtilsMock.when(() -> BlackIpUtils.isBlackIp("8.8.8.8")).thenReturn(false);

            blackIpFilter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
