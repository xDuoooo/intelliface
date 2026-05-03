package com.xduo.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 题目评论
 */
@Data
@TableName(value = "question_comment")
public class QuestionComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 题目 id */
    private Long questionId;

    /** 发表者 id */
    private Long userId;

    /** 父评论 id（null 表示顶级评论） */
    private Long parentId;

    /** 回复的具体评论 id（用于 @ 提及） */
    private Long replyToId;

    /** 内容（纯文本，最长 2000 字） */
    private String content;

    /** 发布时 IP 归属地 */
    private String ipLocation;

    /** 点赞数（冗余字段） */
    private Integer likeNum;

    /** 被举报次数 */
    private Integer reportNum;

    /** 是否置顶：0否 1是 */
    private Integer isPinned;

    /** 是否官方解答：0否 1是 */
    private Integer isOfficial;

    /** 状态：0正常 1待审核 2已隐藏 */
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
