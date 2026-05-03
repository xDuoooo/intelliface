package com.xduo.springbootinit.model.vo;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 用户视图（脱敏）

 */
@Data
public class UserVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

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
    private List<String> interestTagList;

    /**
     * 公开主页可见字段
     */
    private List<String> profileVisibleFieldList;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 当前登录用户是否已关注该用户
     */
    private Boolean hasFollowed;

    private static final long serialVersionUID = 1L;
}
