package com.xduo.springbootinit.model.vo;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 帖子评论视图
 */
@Data
public class PostCommentVO implements Serializable {

    private Long id;

    private Long postId;

    private Long parentId;

    private Long replyToId;

    private String content;

    private String ipLocation;

    private Integer likeNum;

    private Boolean hasLiked;

    /**
     * 0正常 1待审核 2已驳回
     */
    private Integer status;

    private String reviewMessage;

    private Date createTime;

    private Boolean deleted;

    private UserVO user;

    private UserVO replyToUser;

    private List<PostCommentVO> replies;

    private static final long serialVersionUID = 1L;
}
