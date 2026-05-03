package com.xduo.springbootinit.manager;

import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.SystemConfigService;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 系统访问控制
 */
@Component
public class SystemAccessManager {

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private UserService userService;

    /**
     * 检查未登录用户是否允许访问题目模块。
     */
    public void ensureGuestQuestionAccessAllowed(HttpServletRequest request) {
        ensureGuestAccessAllowed(request, systemConfigService.isAllowGuestViewQuestion(), "题目");
    }

    /**
     * 检查未登录用户是否允许访问论坛模块。
     */
    public void ensureGuestPostAccessAllowed(HttpServletRequest request) {
        ensureGuestAccessAllowed(request, systemConfigService.isAllowGuestViewPost(), "论坛");
    }

    private void ensureGuestAccessAllowed(HttpServletRequest request, boolean allowGuestAccess, String moduleName) {
        if (allowGuestAccess) {
            return;
        }
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            return;
        }
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前系统已关闭未登录用户访问" + moduleName + "，请先登录");
    }
}
