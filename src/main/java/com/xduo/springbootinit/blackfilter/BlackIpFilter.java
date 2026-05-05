package com.xduo.springbootinit.blackfilter;

import com.xduo.springbootinit.utils.NetUtils;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@WebFilter(urlPatterns = "/*", filterName = "blackIpFilter", asyncSupported = true)
public class BlackIpFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        String ipAddress = NetUtils.getIpAddress((HttpServletRequest) servletRequest);
        // 如果是本地环回地址，统一转为 127.0.0.1 进行匹配
        String matchIp = "0:0:0:0:0:0:0:1".equals(ipAddress) ? "127.0.0.1" : ipAddress;

        if (BlackIpUtils.isBlackIp(matchIp)) {
            log.info("黑名单拦截记录: 原始IP={}, 匹配IP={}", ipAddress, matchIp);
            servletResponse.setContentType("text/json;charset=UTF-8");
            servletResponse.getWriter().write("{\"errorCode\":\"-1\",\"errorMsg\":\"黑名单IP，禁止访问\"}");
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

}
