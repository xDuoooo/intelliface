package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.PostFavourMapper;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.PostFavour;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.PostService;
import com.xduo.springbootinit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostFavourServiceImplTest {

    @Mock
    private PostService postService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private PostFavourMapper postFavourMapper;

    @Mock(answer = Answers.RETURNS_SELF)
    private UpdateChainWrapper<Post> postUpdateChainWrapper;

    private PostFavourServiceImpl postFavourService;

    @BeforeEach
    void setUp() {
        postFavourService = spy(new PostFavourServiceImpl());
        ReflectionTestUtils.setField(postFavourService, "postService", postService);
        ReflectionTestUtils.setField(postFavourService, "notificationService", notificationService);
        ReflectionTestUtils.setField(postFavourService, "userService", userService);
        ReflectionTestUtils.setField(postFavourService, "baseMapper", postFavourMapper);
    }

    @Test
    void doPostFavourRejectsMissingPost() {
        when(postService.getById(9L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postFavourService.doPostFavour(9L, user(1L, "Alice")));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void listFavourPostByPageReturnsEmptyPageWhenFavourUserIdInvalid() {
        Page<Post> result = postFavourService.listFavourPostByPage(new Page<>(1, 10), null, -1L);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listFavourPostByPageDelegatesToMapperWhenFavourUserIdValid() {
        Page<Post> page = new Page<>(1, 10);
        Page<Post> expected = new Page<>(1, 10, 1);
        when(postFavourMapper.listFavourPostByPage(eq(page), any(), eq(8L))).thenReturn(expected);

        Page<Post> result = postFavourService.listFavourPostByPage(page, null, 8L);

        assertSame(expected, result);
    }

    @Test
    void doPostFavourInnerThrowsWhenRemoveFails() {
        doReturn(new PostFavour()).when(postFavourService).getOne(any());
        doReturn(false).when(postFavourService).remove(any());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postFavourService.doPostFavourInner(1L, 9L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doPostFavourInnerReturnsMinusOneAndSyncsPostWhenUnfavourSucceeds() {
        Post syncedPost = post(9L, 2L, "Post");
        doReturn(new PostFavour()).when(postFavourService).getOne(any());
        doReturn(true).when(postFavourService).remove(any());
        stubPostUpdateChainForDecrement(true);
        when(postService.getById(9L)).thenReturn(syncedPost);

        int result = postFavourService.doPostFavourInner(1L, 9L);

        assertEquals(-1, result);
        verify(postService).syncPostToEs(syncedPost);
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doPostFavourInnerReturnsZeroWhenCounterUpdateFailsAfterUnfavour() {
        doReturn(new PostFavour()).when(postFavourService).getOne(any());
        doReturn(true).when(postFavourService).remove(any());
        stubPostUpdateChainForDecrement(false);

        int result = postFavourService.doPostFavourInner(1L, 9L);

        assertEquals(0, result);
        verify(postService, never()).syncPostToEs(any());
    }

    @Test
    void doPostFavourInnerThrowsWhenSaveFails() {
        doReturn(null).when(postFavourService).getOne(any());
        doReturn(false).when(postFavourService).save(any(PostFavour.class));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postFavourService.doPostFavourInner(1L, 9L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doPostFavourInnerReturnsOneAndSendsNotificationWhenNewFavourSucceeds() {
        Post post = post(9L, 2L, "Interesting Post");
        User favourUser = user(1L, "Alice");
        doReturn(null).when(postFavourService).getOne(any());
        doReturn(true).when(postFavourService).save(any(PostFavour.class));
        stubPostUpdateChainForIncrement(true);
        when(postService.getById(9L)).thenReturn(post);
        when(userService.getById(1L)).thenReturn(favourUser);

        int result = postFavourService.doPostFavourInner(1L, 9L);

        assertEquals(1, result);
        verify(postService).syncPostToEs(post);
        verify(notificationService).sendNotification(eq(2L), anyString(), contains("Alice"), eq("post_favour"), eq(9L));
    }

    @Test
    void doPostFavourInnerSkipsNotificationWhenFavouringOwnPost() {
        Post ownPost = post(9L, 1L, "My Post");
        doReturn(null).when(postFavourService).getOne(any());
        doReturn(true).when(postFavourService).save(any(PostFavour.class));
        stubPostUpdateChainForIncrement(true);
        when(postService.getById(9L)).thenReturn(ownPost);

        int result = postFavourService.doPostFavourInner(1L, 9L);

        assertEquals(1, result);
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doPostFavourInnerPersistsUserAndPostIdsWhenCreatingFavour() {
        doReturn(null).when(postFavourService).getOne(any());
        doReturn(true).when(postFavourService).save(any(PostFavour.class));
        stubPostUpdateChainForIncrement(false);

        postFavourService.doPostFavourInner(7L, 15L);

        ArgumentCaptor<PostFavour> captor = ArgumentCaptor.forClass(PostFavour.class);
        verify(postFavourService).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(15L, captor.getValue().getPostId());
    }

    private void stubPostUpdateChainForDecrement(boolean updateResult) {
        when(postService.update()).thenReturn(postUpdateChainWrapper);
        doReturn(postUpdateChainWrapper).when(postUpdateChainWrapper).eq(any(), any());
        doReturn(postUpdateChainWrapper).when(postUpdateChainWrapper).gt(any(), any());
        doReturn(postUpdateChainWrapper).when(postUpdateChainWrapper).setSql(anyString(), any(Object[].class));
        when(postUpdateChainWrapper.update()).thenReturn(updateResult);
    }

    private void stubPostUpdateChainForIncrement(boolean updateResult) {
        when(postService.update()).thenReturn(postUpdateChainWrapper);
        doReturn(postUpdateChainWrapper).when(postUpdateChainWrapper).eq(any(), any());
        doReturn(postUpdateChainWrapper).when(postUpdateChainWrapper).setSql(anyString(), any(Object[].class));
        when(postUpdateChainWrapper.update()).thenReturn(updateResult);
    }

    private Post post(Long id, Long userId, String title) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setTitle(title);
        return post;
    }

    private User user(Long id, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        return user;
    }
}
