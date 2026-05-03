package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 公开用户主页视图
 */
@Data
public class UserProfileVO implements Serializable {

    /**
     * 用户公开信息
     */
    private UserVO user;

    /**
     * 公开主页可见字段
     */
    private List<String> profileVisibleFieldList;

    /**
     * 累计刷题数
     */
    private Long totalQuestionCount;

    /**
     * 已掌握题目数
     */
    private Long masteredQuestionCount;

    /**
     * 活跃天数
     */
    private Long activeDays;

    /**
     * 连续学习天数
     */
    private Long currentStreak;

    /**
     * 收藏题目数
     */
    private Long favourCount;

    /**
     * 今日刷题数
     */
    private Long todayCount;

    /**
     * 每日目标题数
     */
    private Long dailyTarget;

    /**
     * 今日是否完成目标
     */
    private Boolean goalCompletedToday;

    /**
     * 推荐练习难度
     */
    private String recommendedDifficulty;

    /**
     * 累计学习时长（秒）
     */
    private Long totalStudyDurationSeconds;

    /**
     * 今日学习时长（秒）
     */
    private Long todayStudyDurationSeconds;

    /**
     * 学习会话数
     */
    private Long studySessionCount;

    /**
     * 平均学习时长（秒）
     */
    private Long averageStudyDurationSeconds;

    /**
     * 成就进度
     */
    private List<Map<String, Object>> achievementList;

    /**
     * 刷题热力图记录
     */
    private List<Map<String, Object>> questionHistoryRecordList;

    /**
     * 已通过审核的公开题目数
     */
    private Long approvedQuestionCount;

    /**
     * 已通过审核的公开题库数
     */
    private Long approvedQuestionBankCount;

    /**
     * 粉丝数
     */
    private Long followerCount;

    /**
     * 关注数
     */
    private Long followingCount;

    /**
     * 当前登录用户是否已关注该用户
     */
    private Boolean hasFollowed;

    /**
     * 最近动态
     */
    private List<UserActivityVO> recentActivityList;

    private static final long serialVersionUID = 1L;
}
