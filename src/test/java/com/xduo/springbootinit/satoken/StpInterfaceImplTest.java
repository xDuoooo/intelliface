package com.xduo.springbootinit.satoken;

import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private UserService userService;

    private StpInterfaceImpl stpInterface;

    @BeforeEach
    void setUp() {
        stpInterface = new StpInterfaceImpl();
        ReflectionTestUtils.setField(stpInterface, "userService", userService);
    }

    @Test
    void getPermissionListAlwaysReturnsEmptyList() {
        List<String> result = stpInterface.getPermissionList(1L, "login");

        assertTrue(result.isEmpty());
    }

    @Test
    void getRoleListReturnsEmptyListWhenUserMissing() {
        when(userService.getById(1L)).thenReturn(null);

        List<String> result = stpInterface.getRoleList(1L, "login");

        assertTrue(result.isEmpty());
    }

    @Test
    void getRoleListReturnsUserRoleWhenUserExists() {
        User user = new User();
        user.setId(1L);
        user.setUserRole("admin");
        when(userService.getById(1L)).thenReturn(user);

        List<String> result = stpInterface.getRoleList(1L, "login");

        assertEquals(List.of("admin"), result);
    }
}
