package com.xduo.springbootinit.satoken;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceUtilsTest {

    @Test
    void getRequestDeviceRecognizesWechatMiniProgram() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.40 MiniProgram");

        assertEquals("miniProgram", DeviceUtils.getRequestDevice(request));
    }

    @Test
    void getRequestDeviceRecognizesPad() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (iPad; CPU OS 15_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1");

        assertEquals("pad", DeviceUtils.getRequestDevice(request));
    }

    @Test
    void getRequestDeviceRecognizesAndroidTablet() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        assertEquals("pad", DeviceUtils.getRequestDevice(request));
    }

    @Test
    void getRequestDeviceRecognizesMobilePhone() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1");

        assertEquals("mobile", DeviceUtils.getRequestDevice(request));
    }

    @Test
    void getRequestDeviceFallsBackToPc() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        assertEquals("pc", DeviceUtils.getRequestDevice(request));
    }

    @Test
    void getRequestDeviceTreatsWechatBrowserWithoutMiniProgramMarkerAsMobile() {
        MockHttpServletRequest request = requestWithUserAgent(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.40");

        assertEquals("mobile", DeviceUtils.getRequestDevice(request));
    }

    private MockHttpServletRequest requestWithUserAgent(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        return request;
    }
}
