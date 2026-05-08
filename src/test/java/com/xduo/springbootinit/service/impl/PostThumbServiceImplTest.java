package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.PostThumbMapper;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.PostThumb;
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
class PostThumbServiceImplTest {

    @Mock
    private PostService postService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private PostThumbMapper postThumbMapper;

    @Mock(answer = Answers.RETURNS_SELF)
    private UpdateChainWrapper<Post> postUpdateChainWrapper;

    private PostThumbServiceImpl postThumbService;

    @BeforeEach
    void setUp() {
        postThumbService = spy(new PostThumbServiceImpl());
        ReflectionTestUtils.setField(postThumbService, "postService", postService);
        ReflectionTestUtils.setField(postThumbService, "notificationService", notificationService);
        ReflectionTestUtils.setField(postThumbService, "userService", userService);
        ReflectionTestUtils.setField(postThumbService, "baseMapper", postThumbMapper);
    }

    @Test
    void doPostThumbRejectsMissingPost() {
        User loginUser = user(1L, "Alice");
        when(postService.getById(9L)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postThumbService.doPostThumb(9L, loginUser));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void listThumbPostByPageReturnsEmptyPageWhenThumbUserIdInvalid() {
        Page<Post> result = postThumbService.listThumbPostByPage(new Page<>(1, 10), null, 0L);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void listThumbPostByPageDelegatesToMapperWhenThumbUserIdValid() {
        Page<Post> page = new Page<>(1, 10);
        Page<Post> expected = new Page<>(1, 10, 1);
        when(postThumbMapper.listThumbPostByPage(eq(page), any(), eq(8L))).thenReturn(expected);

        Page<Post> result = postThumbService.listThumbPostByPage(page, null, 8L);

        assertSame(expected, result);
    }

    @Test
    void doPostThumbInnerThrowsWhenRemoveFails() {
        doReturn(new PostThumb()).when(postThumbService).getOne(any());
        doReturn(false).when(postThumbService).remove(any());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postThumbService.doPostThumbInner(1L, 9L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doPostThumbInnerReturnsMinusOneAndSyncsPostWhenUnthumbSucceeds() {
        Post syncedPost = post(9L, 2L, "Post");
        doReturn(new PostThumb()).when(postThumbService).getOne(any());
        doReturn(true).when(postThumbService).remove(any());
        stubPostUpdateChainForDecrement(true);
        when(postService.getById(9L)).thenReturn(syncedPost);

        int result = postThumbService.doPostThumbInner(1L, 9L);

        assertEquals(-1, result);
        verify(postService).syncPostToEs(syncedPost);
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doPostThumbInnerReturnsZeroWhenCounterUpdateFailsAfterUnthumb() {
        doReturn(new PostThumb()).when(postThumbService).getOne(any());
        doReturn(true).when(postThumbService).remove(any());
        stubPostUpdateChainForDecrement(false);

        int result = postThumbService.doPostThumbInner(1L, 9L);

        assertEquals(0, result);
        verify(postService, never()).syncPostToEs(any());
    }

    @Test
    void doPostThumbInnerThrowsWhenSaveFails() {
        doReturn(null).when(postThumbService).getOne(any());
        doReturn(false).when(postThumbService).save(any(PostThumb.class));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> postThumbService.doPostThumbInner(1L, 9L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), thrown.getCode());
    }

    @Test
    void doPostThumbInnerReturnsOneAndSendsNotificationWhenNewThumbSucceeds() {
        Post post = post(9L, 2L, "Interesting Post");
        User liker = user(1L, "Alice");
        doReturn(null).when(postThumbService).getOne(any());
        doReturn(true).when(postThumbService).save(any(PostThumb.class));
        stubPostUpdateChainForIncrement(true);
        when(postService.getById(9L)).thenReturn(post);
        when(userService.getById(1L)).thenReturn(liker);

        int result = postThumbService.doPostThumbInner(1L, 9L);

        assertEquals(1, result);
        verify(postService).syncPostToEs(post);
        verify(notificationService).sendNotification(eq(2L), anyString(), contains("Alice"), eq("post_thumb"), eq(9L));
    }

    @Test
    void doPostThumbInnerSkipsNotificationWhenThumbingOwnPost() {
        Post ownPost = post(9L, 1L, "My Post");
        doReturn(null).when(postThumbService).getOne(any());
        doReturn(true).when(postThumbService).save(any(PostThumb.class));
        stubPostUpdateChainForIncrement(true);
        when(postService.getById(9L)).thenReturn(ownPost);

        int result = postThumbService.doPostThumbInner(1L, 9L);

        assertEquals(1, result);
        verify(notificationService, never()).sendNotification(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doPostThumbInnerPersistsUserAndPostIdsWhenCreatingThumb() {
        doReturn(null).when(postThumbService).getOne(any());
        doReturn(true).when(postThumbService).save(any(PostThumb.class));
        stubPostUpdateChainForIncrement(false);

        postThumbService.doPostThumbInner(7L, 15L);

        ArgumentCaptor<PostThumb> captor = ArgumentCaptor.forClass(PostThumb.class);
        verify(postThumbService).save(captor.capture());
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
