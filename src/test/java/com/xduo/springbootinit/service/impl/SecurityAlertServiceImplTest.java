package com.xduo.springbootinit.service.impl;

import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.entity.SecurityAlert;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAlertServiceImplTest {

    @Mock
    private UserService userService;

    private SecurityAlertServiceImpl securityAlertService;

    @BeforeEach
    void setUp() {
        securityAlertService = spy(new SecurityAlertServiceImpl());
        ReflectionTestUtils.setField(securityAlertService, "userService", userService);
    }

    @Test
    void recordAlertUsesDefaultsAndAbbreviatesFields() {
        doReturn(true).when(securityAlertService).save(any(SecurityAlert.class));

        securityAlertService.recordAlert(1L, "", "", "", "a".repeat(600), null, "1".repeat(200));

        ArgumentCaptor<SecurityAlert> captor = ArgumentCaptor.forClass(SecurityAlert.class);
        verify(securityAlertService).save(captor.capture());
        SecurityAlert alert = captor.getValue();
        assertEquals(1L, alert.getUserId());
        assertFalse(alert.getUserName().isBlank());
        assertFalse(alert.getAlertType().isBlank());
        assertEquals("medium", alert.getRiskLevel());
        assertTrue(alert.getReason().length() <= 512);
        assertEquals("", alert.getDetail());
        assertTrue(alert.getIp().length() <= 128);
        assertEquals(0, alert.getStatus());
    }

    @Test
    void ignoreAlertMarksPendingAlertAsIgnored() {
        SecurityAlert alert = pendingAlert(1L, 8L);
        doReturn(alert).when(securityAlertService).getById(1L);
        doReturn(true).when(securityAlertService).updateById(any(SecurityAlert.class));

        securityAlertService.ignoreAlert(1L, 99L);

        assertEquals(2, alert.getStatus());
        assertEquals(99L, alert.getHandlerUserId());
        assertEquals("ignore", alert.getHandleAction());
        assertNotNull(alert.getHandleTime());
        verify(securityAlertService).updateById(alert);
    }

    @Test
    void ignoreAlertRejectsProcessedAlert() {
        SecurityAlert alert = pendingAlert(1L, 8L);
        alert.setStatus(1);
        doReturn(alert).when(securityAlertService).getById(1L);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.ignoreAlert(1L, 99L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void ignoreAlertRejectsInvalidAlertId() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.ignoreAlert(0L, 99L));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertRejectsAlertWithoutLinkedUser() {
        SecurityAlert alert = pendingAlert(1L, null);
        doReturn(alert).when(securityAlertService).getById(1L);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.banUserByAlert(1L, 100L));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertRejectsSelfBan() {
        SecurityAlert alert = pendingAlert(1L, 100L);
        doReturn(alert).when(securityAlertService).getById(1L);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.banUserByAlert(1L, 100L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertRejectsMissingTargetUser() {
        SecurityAlert alert = pendingAlert(1L, 88L);
        doReturn(alert).when(securityAlertService).getById(1L);
        when(userService.getById(88L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.banUserByAlert(1L, 100L));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertRejectsAdminTarget() {
        SecurityAlert alert = pendingAlert(1L, 88L);
        User adminUser = new User();
        adminUser.setId(88L);
        adminUser.setUserRole(UserConstant.ADMIN_ROLE);
        doReturn(alert).when(securityAlertService).getById(1L);
        when(userService.getById(88L)).thenReturn(adminUser);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.banUserByAlert(1L, 100L));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertThrowsWhenUserRoleUpdateFails() {
        SecurityAlert alert = pendingAlert(1L, 88L);
        User user = new User();
        user.setId(88L);
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        doReturn(alert).when(securityAlertService).getById(1L);
        when(userService.getById(88L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(false);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> securityAlertService.banUserByAlert(1L, 100L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void banUserByAlertUpdatesUserRoleAndAlertStateWhenSuccessful() {
        SecurityAlert alert = pendingAlert(1L, 88L);
        User user = new User();
        user.setId(88L);
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        doReturn(alert).when(securityAlertService).getById(1L);
        doReturn(true).when(securityAlertService).updateById(any(SecurityAlert.class));
        when(userService.getById(88L)).thenReturn(user);
        when(userService.updateById(any(User.class))).thenReturn(true);

        securityAlertService.banUserByAlert(1L, 100L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).updateById(userCaptor.capture());
        User updateUser = userCaptor.getValue();
        assertEquals(88L, updateUser.getId());
        assertEquals(UserConstant.BAN_ROLE, updateUser.getUserRole());
        assertEquals(1, alert.getStatus());
        assertEquals(100L, alert.getHandlerUserId());
        assertEquals("ban_user", alert.getHandleAction());
        assertNotNull(alert.getHandleTime());
        verify(securityAlertService).updateById(alert);
    }

    private SecurityAlert pendingAlert(Long id, Long userId) {
        SecurityAlert alert = new SecurityAlert();
        alert.setId(id);
        alert.setUserId(userId);
        alert.setStatus(0);
        return alert;
    }
}
