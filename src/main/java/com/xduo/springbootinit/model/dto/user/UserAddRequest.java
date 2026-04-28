package com.xduo.springbootinit.model.dto.user;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户创建请求

 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 用户角色: user, admin
     */
    private String userRole;

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

    private static final long serialVersionUID = 1L;
}
