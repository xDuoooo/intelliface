package com.xduo.springbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 模拟面试
 */
@TableName(value = "mock_interview")
@Data
public class MockInterview implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 目标岗位
     */
    private String jobPosition;

    /**
     * 工作年限
     */
    private String workExperience;

    /**
     * 面试类型
     */
    private String interviewType;

    /**
     * 技术方向 / 技术栈
     */
    private String techStack;

    /**
     * 简历 / 项目背景
     */
    private String resumeText;

    /**
     * 面试难度
     */
    private String difficulty;

    /**
     * 计划轮次
     */
    private Integer expectedRounds;

    /**
     * 当前已完成轮次
     */
    private Integer currentRound;

    /**
     * 面试消息记录（json 数组）
     */
    private String messages;

    /**
     * 结构化面试报告（json）
     */
    private String report;

    /**
     * 状态：0-待开始, 1-进行中, 2-已结束, 3-已暂停
     */
    private Integer status;

    /**
     * 创建用户 id
     */
    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
