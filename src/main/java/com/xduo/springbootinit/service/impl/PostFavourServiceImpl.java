package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.PostFavourMapper;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.PostFavour;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.PostFavourService;
import com.xduo.springbootinit.service.PostService;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子收藏服务实现

 */
@Service
public class PostFavourServiceImpl extends ServiceImpl<PostFavourMapper, PostFavour>
        implements PostFavourService {

    @Resource
    private PostService postService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserService userService;

    /**
     * 帖子收藏
     *
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostFavour(long postId, User loginUser) {
        // 判断是否存在
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已帖子收藏
        long userId = loginUser.getId();
        // 每个用户串行帖子收藏
        // 锁必须要包裹住事务方法
        PostFavourService postFavourService = (PostFavourService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return postFavourService.doPostFavourInner(userId, postId);
        }
    }

    @Override
    public Page<Post> listFavourPostByPage(IPage<Post> page, Wrapper<Post> queryWrapper, long favourUserId) {
        if (favourUserId <= 0) {
            return new Page<>();
        }
        return baseMapper.listFavourPostByPage(page, queryWrapper, favourUserId);
    }

    /**
     * 封装了事务的方法
     *
     * @param userId
     * @param postId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doPostFavourInner(long userId, long postId) {
        PostFavour postFavour = new PostFavour();
        postFavour.setUserId(userId);
        postFavour.setPostId(postId);
        QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>(postFavour);
        PostFavour oldPostFavour = this.getOne(postFavourQueryWrapper);
        boolean result;
        // 已收藏
        if (oldPostFavour != null) {
            result = this.remove(postFavourQueryWrapper);
            if (result) {
                // 帖子收藏数 - 1
                result = postService.update()
                        .eq("id", postId)
                        .gt("favourNum", 0)
                        .setSql("favourNum = favourNum - 1")
                        .update();
                if (result) {
                    postService.syncPostToEs(postService.getById(postId));
                }
                return result ? -1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        } else {
            // 未帖子收藏
            result = this.save(postFavour);
            if (result) {
                // 帖子收藏数 + 1
                result = postService.update()
                        .eq("id", postId)
                        .setSql("favourNum = favourNum + 1")
                        .update();
                if (result) {
                    postService.syncPostToEs(postService.getById(postId));
                    sendPostFavourNotificationIfNeeded(userId, postId);
                }
                return result ? 1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }

    private void sendPostFavourNotificationIfNeeded(long userId, long postId) {
        Post post = postService.getById(postId);
        if (post == null || post.getUserId() == null || post.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        String displayName = user == null ? "有用户" : StringUtils.defaultIfBlank(user.getUserName(), "有用户");
        String postTitle = StringUtils.defaultIfBlank(post.getTitle(), "这篇帖子");
        notificationService.sendNotification(
                post.getUserId(),
                "有人收藏了你的帖子",
                displayName + " 收藏了你的帖子：" + StringUtils.abbreviate(postTitle, 30),
                "post_favour",
                postId
        );
    }

}


