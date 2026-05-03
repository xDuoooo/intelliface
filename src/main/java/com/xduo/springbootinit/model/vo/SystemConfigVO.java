package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统配置视图
 */
@Data
public class SystemConfigVO implements Serializable {

    private Long id;

    private String siteName;

    private String seoKeywords;

    private String announcement;

    private Boolean allowRegister;

    private Boolean requireCaptcha;

    private Boolean maintenanceMode;

    private Boolean enableSiteNotification;

    private Boolean enableEmailNotification;

    private Boolean enableLearningGoalReminder;

    private Boolean allowGuestViewQuestion;

    private Boolean allowGuestViewPost;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
