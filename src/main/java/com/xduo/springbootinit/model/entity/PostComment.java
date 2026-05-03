package com.xduo.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 帖子评论
 */
@Data
@TableName(value = "post_comment")
public class PostComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 帖子 id
     */
    private Long postId;

    /**
     * 发表者 id
     */
    private Long userId;

    /**
     * 父评论 id（null 表示顶级评论）
     */
    private Long parentId;

    /**
     * 回复的具体评论 id
     */
    private Long replyToId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 发布时 IP 归属地
     */
    private String ipLocation;

    /**
     * 点赞数（冗余字段）
     */
    private Integer likeNum;

    /**
     * 状态：0正常 1待审核 2已驳回
     */
    private Integer status;

    /**
     * 审核意见
     */
    private String reviewMessage;

    /**
     * 审核人 id
     */
    private Long reviewUserId;

    /**
     * 审核时间
     */
    private Date reviewTime;

    private Date editTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
