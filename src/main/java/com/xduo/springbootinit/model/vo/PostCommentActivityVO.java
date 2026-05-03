package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户社区回复足迹视图
 */
@Data
public class PostCommentActivityVO implements Serializable {

    private Long id;

    private Long postId;

    private String postTitle;

    private Long parentId;

    private Long replyToId;

    private String content;

    private String ipLocation;

    private Integer status;

    private String reviewMessage;

    private Date createTime;

    private Date actionTime;

    private Boolean deleted;

    private UserVO user;

    private UserVO replyToUser;

    private static final long serialVersionUID = 1L;
}
