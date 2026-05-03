package com.xduo.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xduo.springbootinit.model.entity.PostCommentLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子评论点赞 Mapper
 */
@Mapper
public interface PostCommentLikeMapper extends BaseMapper<PostCommentLike> {
}
