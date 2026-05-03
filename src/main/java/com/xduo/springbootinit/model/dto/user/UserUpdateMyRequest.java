package com.xduo.springbootinit.model.dto.user;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户更新个人信息请求

 */
@Data
public class UserUpdateMyRequest implements Serializable {

    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

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
    private java.util.List<String> interestTags;

    /**
     * 公开主页可见字段
     */
    private java.util.List<String> profileVisibleFields;

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

    private static final long serialVersionUID = 1L;
}
