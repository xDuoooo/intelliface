package com.xduo.springbootinit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xduo.springbootinit.model.entity.UserQuestionHistory;

/**
 * 用户刷题轨迹服务
 */
public interface UserQuestionHistoryService extends IService<UserQuestionHistory> {

    /**
     * 添加/更新刷题记录
     * @param userId
     * @param questionId
     * @param status 0-浏览, 1-掌握, 2-困难
     * @return
     */
    boolean addQuestionHistory(long userId, long questionId, int status);

    /**
     * 记录用户浏览题目，不覆盖已有的掌握/困难状态
     */
    boolean recordQuestionView(long userId, long questionId);

    /**
     * 获取我收藏的题目分页
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.xduo.springbootinit.model.vo.QuestionVO> listMyFavourQuestionByPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.xduo.springbootinit.model.entity.Question> page,
            long userId, jakarta.servlet.http.HttpServletRequest request);

    /**
     * 获取我的刷题记录分页
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.xduo.springbootinit.model.vo.UserQuestionHistoryVO> listMyQuestionHistoryByPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.xduo.springbootinit.model.entity.UserQuestionHistory> page,
            long userId, Integer status, jakarta.servlet.http.HttpServletRequest request);

    /**
     * 获取用户刷题日历记录（每年的每日刷题量）
     * @param userId
     * @param year
     * @return 每日刷题数列表（DATE -> Count）
     */
    java.util.List<java.util.Map<String, Object>> getUserQuestionHistoryRecord(long userId, Integer year);

    /**
     * 获取用户的学习统计信息
     */
    java.util.Map<String, Object> getUserQuestionStats(long userId);

    /**
     * 获取用户今日刷题数
     */
    long getTodayQuestionCount(long userId);

    /**
     * 上报一次学习时长会话
     */
    boolean reportStudySession(long userId, long questionId, int durationSeconds);
}
