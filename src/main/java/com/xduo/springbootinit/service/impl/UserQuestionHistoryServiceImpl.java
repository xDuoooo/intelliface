package com.xduo.springbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.mapper.UserQuestionHistoryMapper;
import com.xduo.springbootinit.constant.QuestionConstant;
import com.xduo.springbootinit.model.entity.UserQuestionHistory;
import com.xduo.springbootinit.service.UserQuestionHistoryService;
import org.springframework.stereotype.Service;

import java.util.Date;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.QuestionFavour;
import com.xduo.springbootinit.model.entity.UserLearningGoal;
import com.xduo.springbootinit.model.vo.QuestionVO;
import com.xduo.springbootinit.model.vo.UserQuestionHistoryVO;
import com.xduo.springbootinit.service.QuestionFavourService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.UserLearningGoalService;
import com.xduo.springbootinit.service.UserQuestionStudySessionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 用户刷题轨迹服务实现
 */
@Service
public class UserQuestionHistoryServiceImpl extends ServiceImpl<UserQuestionHistoryMapper, UserQuestionHistory>
        implements UserQuestionHistoryService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SQL_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private QuestionFavourService questionFavourService;

    @Resource
    private QuestionService questionService;

    @Resource
    private UserLearningGoalService userLearningGoalService;

    @Resource
    private UserQuestionStudySessionService userQuestionStudySessionService;

    @Override
    public boolean addQuestionHistory(long userId, long questionId, int status) {
        if (status < 0 || status > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "刷题状态不合法");
        }
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 先查询是否已经有记录
        QueryWrapper<UserQuestionHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.eq("questionId", questionId);
        UserQuestionHistory oldHistory = this.getOne(queryWrapper);

        if (oldHistory != null) {
            oldHistory.setStatus(status);
            oldHistory.setUpdateTime(new Date());
            return this.updateById(oldHistory);
        } else {
            // 插入新记录
            UserQuestionHistory newHistory = new UserQuestionHistory();
            newHistory.setUserId(userId);
            newHistory.setQuestionId(questionId);
            newHistory.setStatus(status);
            return this.save(newHistory);
        }
    }

    @Override
    public boolean recordQuestionView(long userId, long questionId) {
        if (userId <= 0 || questionId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "刷题记录参数不合法");
        }
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        QueryWrapper<UserQuestionHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.eq("questionId", questionId);
        UserQuestionHistory oldHistory = this.getOne(queryWrapper);
        if (oldHistory != null) {
            oldHistory.setUpdateTime(new Date());
            return this.updateById(oldHistory);
        }
        UserQuestionHistory newHistory = new UserQuestionHistory();
        newHistory.setUserId(userId);
        newHistory.setQuestionId(questionId);
        newHistory.setStatus(0);
        return this.save(newHistory);
    }

    @Override
    public Page<QuestionVO> listMyFavourQuestionByPage(Page<Question> page, long userId, HttpServletRequest request) {
        // 先查询收藏记录
        QueryWrapper<QuestionFavour> favourQueryWrapper = new QueryWrapper<>();
        favourQueryWrapper.eq("userId", userId);
        favourQueryWrapper.orderByDesc("createTime");
        Page<QuestionFavour> favourPage = questionFavourService.page(new Page<>(page.getCurrent(), page.getSize()), favourQueryWrapper);
        
        List<QuestionFavour> favourList = favourPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(favourPage.getCurrent(), favourPage.getSize(), favourPage.getTotal());
        if (favourList.isEmpty()) {
            return questionVOPage;
        }
        
        // 根据题目 id 查询题目详情
        Set<Long> questionIdSet = favourList.stream()
                .map(QuestionFavour::getQuestionId)
                .filter(questionId -> questionId != null && questionId > 0)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIdSet.isEmpty()
                ? java.util.Collections.emptyMap()
                : questionService.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question, (a, b) -> a));
        List<Question> questionList = favourList.stream()
                .map(favour -> questionMap.get(favour.getQuestionId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        
        // 转换为 VO 分页
        Page<Question> questionPage = new Page<>(favourPage.getCurrent(), favourPage.getSize(), favourPage.getTotal());
        questionPage.setRecords(questionList);
        return questionService.getQuestionVOPage(questionPage, request);
    }

    @Override
    public Page<UserQuestionHistoryVO> listMyQuestionHistoryByPage(Page<UserQuestionHistory> page, long userId, Integer status, HttpServletRequest request) {
        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.eq(status != null, "status", status);
        historyQueryWrapper.orderByDesc("updateTime");
        Page<UserQuestionHistory> historyPage = this.page(page, historyQueryWrapper);
        
        List<UserQuestionHistory> historyList = historyPage.getRecords();
        Page<UserQuestionHistoryVO> voPage = new Page<>(historyPage.getCurrent(), historyPage.getSize(), historyPage.getTotal());
        if (historyList.isEmpty()) {
            return voPage;
        }
        
        Set<Long> questionIdSet = historyList.stream()
                .map(UserQuestionHistory::getQuestionId)
                .filter(questionId -> questionId != null && questionId > 0)
                .collect(Collectors.toSet());
        List<Question> questionList = questionIdSet.isEmpty()
                ? java.util.Collections.emptyList()
                : questionService.listByIds(questionIdSet);
        Map<Long, List<Question>> questionMap = questionList.stream().collect(Collectors.groupingBy(Question::getId));
        
        List<UserQuestionHistoryVO> voList = historyList.stream().map(history -> {
            UserQuestionHistoryVO vo = new UserQuestionHistoryVO();
            org.springframework.beans.BeanUtils.copyProperties(history, vo);
            Long qId = history.getQuestionId();
            if (questionMap.containsKey(qId)) {
                vo.setQuestion(questionService.getQuestionVO(questionMap.get(qId).get(0), request));
            }
            return vo;
        }).collect(Collectors.toList());
        
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<Map<String, Object>> getUserQuestionHistoryRecord(long userId, Integer year) {
        if (year == null) {
            year = java.time.LocalDate.now().getYear();
        }
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = startDate.plusYears(1);
        QueryWrapper<UserQuestionHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DATE(updateTime) as date", "count(*) as count");
        queryWrapper.eq("userId", userId);
        queryWrapper.ge("updateTime", toDate(startDate, true));
        queryWrapper.lt("updateTime", toDate(endDate, true));
        queryWrapper.groupBy("DATE(updateTime)");
        return this.listMaps(queryWrapper);
    }

    @Override
    public Map<String, Object> getUserQuestionStats(long userId) {
        Map<String, Object> stats = new java.util.HashMap<>();
        Map<String, Long> questionOverviewStats = getQuestionOverviewStats(userId);

        // 总刷题量 / 已掌握 / 今日刷题量
        long totalCount = questionOverviewStats.getOrDefault("totalCount", 0L);
        stats.put("totalCount", totalCount);
        long masteredCount = questionOverviewStats.getOrDefault("masteredCount", 0L);
        stats.put("masteredCount", masteredCount);

        // 收藏数量
        QueryWrapper<QuestionFavour> favourWrapper = new QueryWrapper<>();
        favourWrapper.eq("userId", userId);
        long favourCount = questionFavourService.count(favourWrapper);
        stats.put("favourCount", favourCount);

        // 活跃天数、连续天数
        List<LocalDate> activeDateList = getActiveDateList(userId);
        long activeDays = activeDateList.size();
        long currentStreak = calculateCurrentStreak(activeDateList);
        stats.put("activeDays", activeDays);
        stats.put("currentStreak", currentStreak);

        // 今日刷题量与学习目标
        long todayCount = questionOverviewStats.getOrDefault("todayCount", 0L);
        UserLearningGoal learningGoal = userLearningGoalService.getOrInitByUserId(userId);
        int dailyTarget = learningGoal.getDailyTarget() == null ? 3 : learningGoal.getDailyTarget();
        boolean reminderEnabled = learningGoal.getReminderEnabled() != null && learningGoal.getReminderEnabled() == 1;
        stats.put("todayCount", todayCount);
        stats.put("dailyTarget", dailyTarget);
        stats.put("reminderEnabled", reminderEnabled);
        stats.put("goalCompletedToday", todayCount >= dailyTarget);
        stats.put("todayProgress", Math.min(todayCount, dailyTarget));
        stats.put("recommendedDifficulty", inferRecommendedDifficulty(userId));

        Map<String, Long> studyStats = userQuestionStudySessionService.getStudyStats(userId);
        long totalStudyDurationSeconds = studyStats.getOrDefault("totalDurationSeconds", 0L);
        long todayStudyDurationSeconds = studyStats.getOrDefault("todayDurationSeconds", 0L);
        long studySessionCount = studyStats.getOrDefault("sessionCount", 0L);
        long averageStudyDurationSeconds = studySessionCount > 0 ? Math.round((double) totalStudyDurationSeconds / studySessionCount) : 0;
        stats.put("totalStudyDurationSeconds", totalStudyDurationSeconds);
        stats.put("todayStudyDurationSeconds", todayStudyDurationSeconds);
        stats.put("studySessionCount", studySessionCount);
        stats.put("averageStudyDurationSeconds", averageStudyDurationSeconds);

        // 成就列表
        stats.put("achievementList", buildAchievementList(totalCount, masteredCount, favourCount, activeDays, currentStreak));
        return stats;
    }

    @Override
    public long getTodayQuestionCount(long userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        QueryWrapper<UserQuestionHistory> todayWrapper = new QueryWrapper<>();
        todayWrapper.eq("userId", userId);
        todayWrapper.ge("updateTime", toDate(today, true));
        todayWrapper.lt("updateTime", toDate(today.plusDays(1), true));
        return this.count(todayWrapper);
    }

    @Override
    public boolean reportStudySession(long userId, long questionId, int durationSeconds) {
        if (userId <= 0 || questionId <= 0 || durationSeconds <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习时长参数不合法");
        }
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        return userQuestionStudySessionService.recordStudySession(userId, questionId, durationSeconds);
    }

    private List<LocalDate> getActiveDateList(long userId) {
        QueryWrapper<UserQuestionHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("DATE(updateTime) as date");
        queryWrapper.eq("userId", userId);
        queryWrapper.groupBy("DATE(updateTime)");
        queryWrapper.orderByDesc("DATE(updateTime)");
        List<Map<String, Object>> dateMapList = this.listMaps(queryWrapper);
        List<LocalDate> activeDateList = new ArrayList<>();
        for (Map<String, Object> item : dateMapList) {
            Object dateObj = item.get("date");
            if (dateObj != null) {
                activeDateList.add(LocalDate.parse(String.valueOf(dateObj)));
            }
        }
        return activeDateList;
    }

    private Map<String, Long> getQuestionOverviewStats(long userId) {
        LocalDate today = LocalDate.now(ZONE_ID);
        String todayStart = SQL_DATE_TIME_FORMATTER.format(today.atStartOfDay());
        String tomorrowStart = SQL_DATE_TIME_FORMATTER.format(today.plusDays(1).atStartOfDay());
        QueryWrapper<UserQuestionHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(
                "COUNT(*) AS totalCount",
                "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS masteredCount",
                "COALESCE(SUM(CASE WHEN updateTime >= '" + todayStart + "' AND updateTime < '" + tomorrowStart + "' THEN 1 ELSE 0 END), 0) AS todayCount"
        );
        queryWrapper.eq("userId", userId);
        Map<String, Object> result = this.getMap(queryWrapper);
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalCount", parseLong(result == null ? null : result.get("totalCount")));
        stats.put("masteredCount", parseLong(result == null ? null : result.get("masteredCount")));
        stats.put("todayCount", parseLong(result == null ? null : result.get("todayCount")));
        return stats;
    }

    private Date toDate(LocalDate localDate, boolean startOfDay) {
        LocalDateTime localDateTime = startOfDay ? localDate.atStartOfDay() : localDate.plusDays(1).atStartOfDay();
        return Date.from(localDateTime.atZone(ZONE_ID).toInstant());
    }

    private long calculateCurrentStreak(List<LocalDate> activeDateList) {
        if (activeDateList.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate latestActiveDate = activeDateList.get(0);
        if (latestActiveDate.isBefore(today.minusDays(1))) {
            return 0;
        }
        long streak = 1;
        LocalDate previousDate = latestActiveDate;
        for (int i = 1; i < activeDateList.size(); i++) {
            LocalDate currentDate = activeDateList.get(i);
            if (currentDate.equals(previousDate.minusDays(1))) {
                streak++;
                previousDate = currentDate;
            } else {
                break;
            }
        }
        return streak;
    }

    private List<Map<String, Object>> buildAchievementList(long totalCount, long masteredCount, long favourCount,
                                                           long activeDays, long currentStreak) {
        List<Map<String, Object>> achievementList = new ArrayList<>();
        achievementList.add(buildTieredAchievement(
                "practice_path",
                "累计刷题",
                "累计完成题目数量的阶段成长成就。",
                "道",
                totalCount,
                new long[]{1, 10, 50, 100, 200, 500},
                new String[]{"初试锋芒", "刷题新秀", "题感渐入", "百题进阶", "双百冲刺", "题海大师"}));
        achievementList.add(buildTieredAchievement(
                "master_path",
                "掌握进阶",
                "聚焦真正掌握的题目数量，衡量知识沉淀深度。",
                "道",
                masteredCount,
                new long[]{5, 20, 50, 100, 200},
                new String[]{"理解入门", "知识掌握者", "稳定掌握", "高频稳固", "知识壁垒"}));
        achievementList.add(buildTieredAchievement(
                "favour_path",
                "收藏积累",
                "把值得反复回看的题目沉淀成自己的精选题单。",
                "道",
                favourCount,
                new long[]{5, 10, 30, 50, 100},
                new String[]{"收藏起步", "收藏达人", "精选题单", "高频沉淀", "题库策展人"}));
        achievementList.add(buildTieredAchievement(
                "active_path",
                "活跃天数",
                "累计活跃学习天数越多，说明节奏越稳定。",
                "天",
                activeDays,
                new long[]{3, 7, 15, 30, 60, 100},
                new String[]{"连线状态", "持续学习者", "节奏建立", "月度稳定", "长期坚持", "学习常驻"}));
        achievementList.add(buildTieredAchievement(
                "streak_path",
                "连续冲刺",
                "连续学习天数越长，越能体现阶段性爆发力。",
                "天",
                currentStreak,
                new long[]{3, 7, 14, 21, 30},
                new String[]{"进入状态", "连续冲刺", "双周连击", "三周稳推", "满月打卡"}));
        return achievementList;
    }

    private Map<String, Object> buildAchievement(String key, String title, String description, long current, long target) {
        Map<String, Object> achievement = new HashMap<>();
        achievement.put("key", key);
        achievement.put("title", title);
        achievement.put("description", description);
        achievement.put("current", current);
        achievement.put("target", target);
        achievement.put("achieved", current >= target);
        achievement.put("progress", Math.min(current, target));
        return achievement;
    }

    private Map<String, Object> buildTieredAchievement(String key, String title, String description, String unit,
                                                       long current, long[] milestones, String[] stageTitles) {
        Map<String, Object> achievement = new HashMap<>();
        int currentLevel = 0;
        for (long milestone : milestones) {
            if (current >= milestone) {
                currentLevel++;
            }
        }
        int totalLevels = milestones.length;
        boolean maxLevel = currentLevel >= totalLevels;
        long previousTarget = currentLevel <= 0 ? 0 : milestones[Math.min(currentLevel - 1, totalLevels - 1)];
        long nextTarget = maxLevel ? milestones[totalLevels - 1] : milestones[currentLevel];
        long segmentCurrent = maxLevel ? nextTarget : Math.max(0, current - previousTarget);
        long segmentTarget = maxLevel ? nextTarget : Math.max(1, nextTarget - previousTarget);
        int percent = segmentTarget <= 0 ? 100 : (int) Math.min(100, Math.round(segmentCurrent * 100.0 / segmentTarget));

        List<Map<String, Object>> milestoneList = new ArrayList<>();
        int focusIndex = maxLevel ? totalLevels - 1 : currentLevel;
        for (int i = 0; i < milestones.length; i++) {
            Map<String, Object> milestone = new HashMap<>();
            milestone.put("level", i + 1);
            milestone.put("target", milestones[i]);
            milestone.put("title", stageTitles[Math.min(i, stageTitles.length - 1)]);
            milestone.put("achieved", current >= milestones[i]);
            milestone.put("current", i == focusIndex);
            milestoneList.add(milestone);
        }

        String currentStageTitle = currentLevel > 0
                ? stageTitles[Math.min(currentLevel - 1, stageTitles.length - 1)]
                : "尚未解锁";
        String nextStageTitle = maxLevel
                ? currentStageTitle
                : stageTitles[Math.min(currentLevel, stageTitles.length - 1)];
        String statusText = maxLevel
                ? "已完成全部阶段，继续保持当前节奏。"
                : String.format("距离 %s 还差 %d%s。", nextStageTitle, Math.max(0, nextTarget - current), unit);

        achievement.put("key", key);
        achievement.put("title", title);
        achievement.put("description", description);
        achievement.put("unit", unit);
        achievement.put("current", current);
        achievement.put("target", nextTarget);
        achievement.put("achieved", currentLevel > 0);
        achievement.put("progress", segmentCurrent);
        achievement.put("percent", percent);
        achievement.put("currentLevel", currentLevel);
        achievement.put("totalLevels", totalLevels);
        achievement.put("currentStageTitle", currentStageTitle);
        achievement.put("nextStageTitle", nextStageTitle);
        achievement.put("nextTarget", nextTarget);
        achievement.put("maxLevel", maxLevel);
        achievement.put("statusText", statusText);
        achievement.put("milestones", milestoneList);
        return achievement;
    }

    private String inferRecommendedDifficulty(long userId) {
        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.orderByDesc("updateTime");
        historyQueryWrapper.last("limit 40");
        List<UserQuestionHistory> historyList = this.list(historyQueryWrapper);
        if (historyList.isEmpty()) {
            return QuestionConstant.DIFFICULTY_MEDIUM;
        }
        Set<Long> questionIdSet = historyList.stream()
                .map(UserQuestionHistory::getQuestionId)
                .filter(questionId -> questionId != null && questionId > 0)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIdSet.isEmpty()
                ? java.util.Collections.emptyMap()
                : questionService.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question, (left, right) -> left));
        double totalDifficultyLevel = 0D;
        int sampleCount = 0;
        long masteredCount = 0;
        long difficultCount = 0;
        for (UserQuestionHistory history : historyList) {
            Question question = questionMap.get(history.getQuestionId());
            totalDifficultyLevel += convertDifficultyToLevel(question == null ? null : question.getDifficulty());
            sampleCount++;
            if (Integer.valueOf(1).equals(history.getStatus())) {
                masteredCount++;
            } else if (Integer.valueOf(2).equals(history.getStatus())) {
                difficultCount++;
            }
        }
        if (sampleCount == 0) {
            return QuestionConstant.DIFFICULTY_MEDIUM;
        }
        double avgDifficulty = totalDifficultyLevel / sampleCount;
        double masteredRate = masteredCount * 1D / sampleCount;
        double difficultRate = difficultCount * 1D / sampleCount;
        if (masteredRate >= 0.55 && avgDifficulty >= 2.2) {
            return QuestionConstant.DIFFICULTY_HARD;
        }
        if (difficultRate >= 0.4 && avgDifficulty <= 2.1) {
            return QuestionConstant.DIFFICULTY_EASY;
        }
        return QuestionConstant.DIFFICULTY_MEDIUM;
    }

    private int convertDifficultyToLevel(String difficulty) {
        if (QuestionConstant.DIFFICULTY_HARD.equals(difficulty)) {
            return 3;
        }
        if (QuestionConstant.DIFFICULTY_EASY.equals(difficulty)) {
            return 1;
        }
        return 2;
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
