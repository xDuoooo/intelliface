package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.UserFollowMapper;
import com.xduo.springbootinit.mapper.UserMapper;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.entity.UserFollow;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserFollowMapper userFollowMapper;

    private UserFollowServiceImpl userFollowService;

    @BeforeEach
    void setUp() {
        userFollowService = spy(new UserFollowServiceImpl());
        ReflectionTestUtils.setField(userFollowService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userFollowService, "userService", userService);
        ReflectionTestUtils.setField(userFollowService, "notificationService", notificationService);
        ReflectionTestUtils.setField(userFollowService, "baseMapper", userFollowMapper);
    }

    @Test
    void hasFollowedReturnsFalseForInvalidInput() {
        assertFalse(userFollowService.hasFollowed(null, 1L));
        assertFalse(userFollowService.hasFollowed(1L, null));
        assertFalse(userFollowService.hasFollowed(1L, 1L));
        verify(userFollowService, never()).count(any());
    }

    @Test
    void hasFollowedReturnsTrueWhenRelationExists() {
        doReturn(1L).when(userFollowService).count(any());

        boolean result = userFollowService.hasFollowed(1L, 2L);

        assertTrue(result);
    }

    @Test
    void listFollowerUserVOByPageReturnsEmptyPageWhenNoFollowerExists() {
        when(userFollowMapper.countVisibleFollower(9L)).thenReturn(0L);

        Page<UserVO> result = userFollowService.listFollowerUserVOByPage(9L, 1L, 10L, 100L);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listFollowerUserVOByPageBuildsOrderedViewAndMarksFollowedUsers() {
        when(userFollowMapper.countVisibleFollower(9L)).thenReturn(2L);
        when(userFollowMapper.listVisibleFollowerUserIds(9L, 0L, 10L)).thenReturn(List.of(2L, 1L));

        User firstUser = new User();
        firstUser.setId(1L);
        User secondUser = new User();
        secondUser.setId(2L);
        when(userService.listByIds(anyCollection())).thenReturn(List.of(firstUser, secondUser));
        when(userService.getUserVO(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUserName("user-" + user.getId());
            return userVO;
        });
        UserFollow followed = new UserFollow();
        followed.setFollowUserId(1L);
        doReturn(List.of(followed)).when(userFollowService).list(any(QueryWrapper.class));

        Page<UserVO> result = userFollowService.listFollowerUserVOByPage(9L, 1L, 10L, 100L);

        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getRecords().get(0).getId());
        assertFalse(result.getRecords().get(0).getHasFollowed());
        assertEquals(1L, result.getRecords().get(1).getId());
        assertTrue(result.getRecords().get(1).getHasFollowed());
    }

    @Test
    void listFollowingUserVOByPageBuildsOrderedViewAndMarksFollowedUsers() {
        when(userFollowMapper.countVisibleFollowing(9L)).thenReturn(2L);
        when(userFollowMapper.listVisibleFollowingUserIds(9L, 0L, 10L)).thenReturn(List.of(3L, 1L));

        User firstUser = new User();
        firstUser.setId(1L);
        User secondUser = new User();
        secondUser.setId(3L);
        when(userService.listByIds(anyCollection())).thenReturn(List.of(firstUser, secondUser));
        when(userService.getUserVO(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUserName("user-" + user.getId());
            return userVO;
        });
        UserFollow followed = new UserFollow();
        followed.setFollowUserId(1L);
        doReturn(List.of(followed)).when(userFollowService).list(any(QueryWrapper.class));

        Page<UserVO> result = userFollowService.listFollowingUserVOByPage(9L, 1L, 10L, 100L);

        assertEquals(2, result.getRecords().size());
        assertEquals(3L, result.getRecords().get(0).getId());
        assertFalse(result.getRecords().get(0).getHasFollowed());
        assertEquals(1L, result.getRecords().get(1).getId());
        assertTrue(result.getRecords().get(1).getHasFollowed());
    }

    @Test
    void followUserRejectsSelfFollow() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userFollowService.followUser(1L, 1L));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void followUserRejectsBannedTargetUser() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.BAN_ROLE, "Target"));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userFollowService.followUser(1L, 2L));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void followUserReturnsTrueWhenRelationAlreadyExists() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        doReturn(1L).when(userFollowService).count(any());

        boolean result = userFollowService.followUser(1L, 2L);

        assertTrue(result);
        verify(userFollowService, never()).save(any(UserFollow.class));
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void followUserReturnsTrueWhenDuplicateKeyExceptionOccurs() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        doReturn(0L).when(userFollowService).count(any());
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
                .when(userFollowService).save(any(UserFollow.class));

        boolean result = userFollowService.followUser(1L, 2L);

        assertTrue(result);
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void followUserCreatesRelationAndSendsNotification() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        when(userMapper.selectById(1L)).thenReturn(activeUser(1L, UserConstant.DEFAULT_ROLE, "Alice"));
        doReturn(0L).when(userFollowService).count(any());
        doReturn(true).when(userFollowService).save(any(UserFollow.class));

        boolean result = userFollowService.followUser(1L, 2L);

        assertTrue(result);
        ArgumentCaptor<UserFollow> relationCaptor = ArgumentCaptor.forClass(UserFollow.class);
        verify(userFollowService).save(relationCaptor.capture());
        assertEquals(1L, relationCaptor.getValue().getUserId());
        assertEquals(2L, relationCaptor.getValue().getFollowUserId());
        verify(notificationService).sendNotification(eq(2L), any(), contains("Alice"), eq("user_follow"), eq(1L));
    }

    @Test
    void followUserSkipsNotificationWhenCurrentUserIsMissing() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        when(userMapper.selectById(1L)).thenReturn(null);
        doReturn(0L).when(userFollowService).count(any());
        doReturn(true).when(userFollowService).save(any(UserFollow.class));

        boolean result = userFollowService.followUser(1L, 2L);

        assertTrue(result);
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void unfollowUserReturnsTrueWhenRelationDoesNotExist() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        doReturn(0L).when(userFollowService).count(any());

        boolean result = userFollowService.unfollowUser(1L, 2L);

        assertTrue(result);
        verify(userFollowService, never()).remove(any());
    }

    @Test
    void unfollowUserThrowsWhenRemoveFails() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L, UserConstant.DEFAULT_ROLE, "Target"));
        doReturn(1L).when(userFollowService).count(any());
        doReturn(false).when(userFollowService).remove(any());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> userFollowService.unfollowUser(1L, 2L));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), thrown.getCode());
    }

    private User activeUser(Long id, String role, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setUserName(userName);
        return user;
    }
}
