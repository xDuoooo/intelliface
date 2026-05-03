package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.QuestionMapper;
import com.xduo.springbootinit.mapper.QuestionFavourMapper;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionFavour;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.NotificationService;
import com.xduo.springbootinit.service.QuestionRecommendLogService;
import com.xduo.springbootinit.service.QuestionFavourService;
import com.xduo.springbootinit.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

/**
 * 题目收藏服务实现
 */
@Service
public class QuestionFavourServiceImpl extends ServiceImpl<QuestionFavourMapper, QuestionFavour>
        implements QuestionFavourService {

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionRecommendLogService questionRecommendLogService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserService userService;

    /**
     * 题目收藏
     *
     * @param questionId
     * @param loginUser
     * @return
     */
    @Override
    public int doQuestionFavour(long questionId, User loginUser) {
        // 判断是否存在
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已题目收藏
        long userId = loginUser.getId();
        // 每个用户串行题目收藏
        // 锁当前的资源，由于一个用户同时只能收藏一个题目，所以可以用 userId 锁
        synchronized (String.valueOf(userId).intern()) {
            QuestionFavourService questionFavourService = (QuestionFavourService) AopContext.currentProxy();
            return questionFavourService.doQuestionFavourInner(userId, questionId);
        }
    }

    /**
     * 封装了事务的题目收藏
     *
     * @param userId
     * @param questionId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doQuestionFavourInner(long userId, long questionId) {
        QuestionFavour questionFavour = new QuestionFavour();
        questionFavour.setUserId(userId);
        questionFavour.setQuestionId(questionId);
        QueryWrapper<QuestionFavour> questionFavourQueryWrapper = new QueryWrapper<>(questionFavour);
        QuestionFavour oldQuestionFavour = this.getOne(questionFavourQueryWrapper);
        boolean result;
        // 已收藏
        if (oldQuestionFavour != null) {
            result = this.remove(questionFavourQueryWrapper);
            if (result) {
                // 题目收藏数 -1
                return -1;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        } else {
            // 未题目收藏
            result = this.save(questionFavour);
            if (result) {
                questionRecommendLogService.logActionByRecentSource(userId, questionId, "favour");
                sendQuestionFavourNotificationIfNeeded(userId, questionId);
                // 题目收藏数 +1
                return 1;
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }
    }

    private void sendQuestionFavourNotificationIfNeeded(long userId, long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getUserId() == null || question.getUserId().equals(userId)) {
            return;
        }
        User user = userService.getById(userId);
        String displayName = user == null ? "有用户" : StringUtils.defaultIfBlank(user.getUserName(), "有用户");
        String questionTitle = StringUtils.defaultIfBlank(question.getTitle(), "这道题目");
        notificationService.sendNotification(
                question.getUserId(),
                "有人收藏了你的题目",
                displayName + " 收藏了你的题目：" + StringUtils.abbreviate(questionTitle, 30),
                "question_favour",
                questionId
        );
    }
}
