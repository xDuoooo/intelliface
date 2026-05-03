package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户评论足迹视图
 */
@Data
public class UserCommentActivityVO implements Serializable {

    private Long id;

    private Long questionId;

    private String questionTitle;

    private Long parentId;

    private Long replyToId;

    private String content;

    private String ipLocation;

    private Integer likeNum;

    private Integer status;

    private String reviewMessage;

    private Date createTime;

    /**
     * 行为发生时间：
     * - 点赞记录使用点赞时间
     * - 回复记录使用评论创建时间
     */
    private Date actionTime;

    private Boolean deleted;

    private Boolean hasLiked;

    private UserVO user;

    private UserVO replyToUser;

    private static final long serialVersionUID = 1L;
}
