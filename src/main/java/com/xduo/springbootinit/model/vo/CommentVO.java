package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 评论视图（响应封装）
 */
@Data
public class CommentVO implements Serializable {

    /** 评论 id */
    private Long id;

    /** 题目 id */
    private Long questionId;

    /** 父评论 id（null=顶级） */
    private Long parentId;

    /** 回复的具体评论 id */
    private Long replyToId;

    /** 内容（已被删除时为 null） */
    private String content;

    /** 发布时 IP 归属地 */
    private String ipLocation;

    /** 点赞数 */
    private Integer likeNum;

    /** 是否置顶 */
    private Integer isPinned;

    /** 是否官方解答 */
    private Integer isOfficial;

    /** 状态：0正常 1待审核 2已隐藏 */
    private Integer status;

    /** 审核意见 */
    private String reviewMessage;

    /** 创建时间 */
    private Date createTime;

    /** 是否已删除（true 时前端显示"该评论已删除"） */
    private Boolean deleted;

    /** 发表人信息（脱敏） */
    private UserVO user;

    /** 被回复人信息（脱敏） */
    private UserVO replyToUser;

    /** 当前登录用户是否已点赞 */
    private Boolean hasLiked;

    /** 子评论列表（最多2级） */
    private List<CommentVO> replies;

    private static final long serialVersionUID = 1L;
}
