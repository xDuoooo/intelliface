package com.xduo.springbootinit.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 已登录用户视图（脱敏）

 **/
@Data
public class LoginUserVO implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 公众号 openId
     */
    private String mpOpenId;

    /**
     * 所在城市
     */
    private String city;

    /**
     * 就业方向
     */
    private String careerDirection;

    /**
     * 兴趣标签
     */
    private java.util.List<String> interestTagList;

    /**
     * 公开主页可见字段
     */
    private java.util.List<String> profileVisibleFieldList;

    /**
     * GitHub 唯一标识
     */
    private String githubId;

    /**
     * Gitee 唯一标识
     */
    private String giteeId;

    /**
     * Google 唯一标识
     */
    private String googleId;

    /**
     * 是否已设置可用登录密码：0-未设置，1-已设置
     */
    private Integer passwordConfigured;

    /**
     * 更新时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
