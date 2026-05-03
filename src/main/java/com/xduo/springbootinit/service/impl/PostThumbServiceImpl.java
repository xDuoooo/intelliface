package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.PostThumbMapper;
import com.xduo.springbootinit.model.entity.Post;
import com.xduo.springbootinit.model.entity.PostThumb;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.PostService;
import com.xduo.springbootinit.service.PostThumbService;
import com.xduo.springbootinit.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 帖子点赞服务实现

 */
@Service
public class PostThumbServiceImpl extends ServiceImpl<PostThumbMapper, PostThumb>
        implements PostThumbService {

    @Resource
    private PostService postService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserService userService;

    /**
     * 点赞
     *
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostThumb(long postId, User loginUser) {
        // 判断实体是否存在，根据类别获取实体
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已点赞
        long userId = loginUser.getId();
        // 每个用户串行点赞
        // 锁必须要包裹住事务方法
        PostThumbService postThumbService = (PostThumbService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return postThumbService.doPostThumbInner(userId, postId);
        }
    }

    @Override
    public Page<Post> listThumbPostByPage(IPage<Post> page, Wrapper<Post> queryWrapper, long thumbUserId) {
        if (thumbUserId <= 0) {
            return new Page<>();
        }
        return baseMapper.listThumbPostByPage(page, queryWrapper, thumbUserId);
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
    public int doPostThumbInner(long userId, long postId) {
        PostThumb postThumb = new PostThumb();
        postThumb.setUserId(userId);
        postThumb.setPostId(postId);
        QueryWrapper<PostThumb> thumbQueryWrapper = new QueryWrapper<>(postThumb);
        PostThumb oldPostThumb = this.getOne(thumbQueryWrapper);
        boolean result;
        // 已点赞
        if (oldPostThumb != null) {
            result = this.remove(thumbQueryWrapper);
            if (result) {
                // 点赞数 - 1
                result = postService.update()
                        .eq("id", postId)
                        .gt("thumbNum", 0)
                        .setSql("thumbNum = thumbNum - 1")
                        .update();
                if (result) {
                    postService.syncPostToEs(postService.getById(postId));
                }
                return result ? -1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        } else {
            // 未点赞
            result = this.save(postThumb);
            if (result) {
                // 点赞数 + 1
                result = postService.update()
                        .eq("id", postId)
                        .setSql("thumbNum = thumbNum + 1")
                        .update();
                if (result) {
                    postService.syncPostToEs(postService.getById(postId));
                    sendPostThumbNotificationIfNeeded(userId, postId);
                }
                return result ? 1 : 0;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }

    private void sendPostThumbNotificationIfNeeded(long userId, long postId) {
        Post post = postService.getById(postId);
        if (post == null || post.getUserId() == null || post.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        String displayName = user == null ? "有用户" : StringUtils.defaultIfBlank(user.getUserName(), "有用户");
        String postTitle = StringUtils.defaultIfBlank(post.getTitle(), "这篇帖子");
        notificationService.sendNotification(
                post.getUserId(),
                "有人点赞了你的帖子",
                displayName + " 点赞了你的帖子：" + StringUtils.abbreviate(postTitle, 30),
                "post_thumb",
                postId
        );
    }

}

