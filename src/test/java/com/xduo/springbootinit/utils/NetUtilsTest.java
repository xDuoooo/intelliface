package com.xduo.springbootinit.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NetUtilsTest {

    @Test
    void getIpAddressReturnsFirstValidForwardedIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown, 1.2.3.4, 5.6.7.8");

        String result = NetUtils.getIpAddress(request);

        assertEquals("1.2.3.4", result);
    }

    @Test
    void getIpAddressFallsBackToLaterHeaderWhenEarlierHeaderInvalid() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn(" 8.8.8.8 ");

        String result = NetUtils.getIpAddress(request);

        assertEquals("8.8.8.8", result);
    }

    @Test
    void getIpAddressReturnsRemoteAddrWhenNoProxyHeaderPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("9.9.9.9");

        String result = NetUtils.getIpAddress(request);

        assertEquals("9.9.9.9", result);
    }

    @Test
    void getIpAddressReturnsLoopbackWhenRemoteAddrMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(null);

        String result = NetUtils.getIpAddress(request);

        assertEquals("127.0.0.1", result);
    }

    @Test
    void getIpAddressUsesLaterProxyHeaderWhenPreviousHeadersBlank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn("unknown");
        when(request.getHeader("CF-Connecting-IP")).thenReturn("7.7.7.7");

        String result = NetUtils.getIpAddress(request);

        assertEquals("7.7.7.7", result);
    }

    @Test
    void getIpAddressResolvesMachineAddressWhenRemoteAddrIsLoopback() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        InetAddress inetAddress = mock(InetAddress.class);
        when(inetAddress.getHostAddress()).thenReturn("192.168.0.10");

        try (MockedStatic<InetAddress> inetAddressMock = mockStatic(InetAddress.class)) {
            inetAddressMock.when(InetAddress::getLocalHost).thenReturn(inetAddress);

            String result = NetUtils.getIpAddress(request);

            assertEquals("192.168.0.10", result);
        }
    }
}
