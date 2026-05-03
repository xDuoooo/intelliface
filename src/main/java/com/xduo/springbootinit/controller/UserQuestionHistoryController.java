package com.xduo.springbootinit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.dto.userquestionhistory.UserQuestionHistoryAddRequest;
import com.xduo.springbootinit.model.dto.userquestionhistory.UserLearningGoalUpdateRequest;
import com.xduo.springbootinit.model.dto.userquestionhistory.UserQuestionStudySessionReportRequest;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.entity.UserQuestionHistory;
import com.xduo.springbootinit.model.entity.UserLearningGoal;
import com.xduo.springbootinit.model.vo.QuestionVO;
import com.xduo.springbootinit.model.vo.UserQuestionHistoryVO;
import com.xduo.springbootinit.service.QuestionRecommendLogService;
import com.xduo.springbootinit.service.UserLearningGoalService;
import com.xduo.springbootinit.service.UserQuestionHistoryService;
import com.xduo.springbootinit.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 刷题记录接口
 */
@RestController
@RequestMapping("/user_question_history")
@Slf4j
public class UserQuestionHistoryController {

    @Resource
    private UserQuestionHistoryService userQuestionHistoryService;

    @Resource
    private UserService userService;

    @Resource
    private UserLearningGoalService userLearningGoalService;

    @Resource
    private QuestionRecommendLogService questionRecommendLogService;

    /**
     * 添加/修改刷题记录
     *
     * @param historyAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Boolean> addQuestionHistory(@RequestBody UserQuestionHistoryAddRequest historyAddRequest,
                                                   HttpServletRequest request) {
        if (historyAddRequest == null || historyAddRequest.getQuestionId() == null || historyAddRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Integer status = historyAddRequest.getStatus();
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "刷题状态不合法");
        }
        final User loginUser = userService.getLoginUser(request);
        boolean result = userQuestionHistoryService.addQuestionHistory(
                loginUser.getId(),
                historyAddRequest.getQuestionId(),
                status
        );
        if (result) {
            questionRecommendLogService.logActionByRecentSource(loginUser.getId(), historyAddRequest.getQuestionId(), "practice");
            if (status == 1) {
                questionRecommendLogService.logActionByRecentSource(loginUser.getId(), historyAddRequest.getQuestionId(), "mastered");
            }
        }
        return ResultUtils.success(result);
    }

    /**
     * 上报一次题目学习时长
     */
    @PostMapping("/session/report")
    public BaseResponse<Boolean> reportStudySession(@RequestBody UserQuestionStudySessionReportRequest reportRequest,
                                                    HttpServletRequest request) {
        if (reportRequest == null || reportRequest.getQuestionId() == null || reportRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题目不存在");
        }
        Integer durationSeconds = reportRequest.getDurationSeconds();
        if (durationSeconds == null || durationSeconds < 10 || durationSeconds > 7200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习时长需在 10 秒到 2 小时之间");
        }
        final User loginUser = userService.getLoginUser(request);
        boolean result = userQuestionHistoryService.reportStudySession(
                loginUser.getId(),
                reportRequest.getQuestionId(),
                durationSeconds
        );
        return ResultUtils.success(result);
    }

    /**
     * 获取我收藏的题目分页
     */
    @GetMapping("/my/favour/list")
    public BaseResponse<Page<QuestionVO>> listMyFavourQuestionByPage(int current, int pageSize,
                                                                    HttpServletRequest request) {
        if (current <= 0 || pageSize <= 0 || pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        final User loginUser = userService.getLoginUser(request);
        Page<Question> page = new Page<>(current, pageSize);
        Page<QuestionVO> questionVOPage = userQuestionHistoryService.listMyFavourQuestionByPage(page, loginUser.getId(), request);
        return ResultUtils.success(questionVOPage);
    }

    /**
     * 获取我的刷题记录分页
     */
    @GetMapping("/my/history/list")
    public BaseResponse<Page<UserQuestionHistoryVO>> listMyQuestionHistoryByPage(int current, int pageSize, Integer status,
                                                                      HttpServletRequest request) {
        if (current <= 0 || pageSize <= 0 || pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        if (status != null && (status < 0 || status > 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "刷题状态不合法");
        }
        final User loginUser = userService.getLoginUser(request);
        Page<UserQuestionHistory> page = new Page<>(current, pageSize);
        Page<UserQuestionHistoryVO> voPage = userQuestionHistoryService.listMyQuestionHistoryByPage(page, loginUser.getId(), status, request);
        return ResultUtils.success(voPage);
    }

    /**
     * 获取我的刷题日历记录
     */
    @GetMapping("/my/history/record")
    public BaseResponse<List<Map<String, Object>>> getMyQuestionHistoryRecord(Integer year, HttpServletRequest request) {
        final User loginUser = userService.getLoginUser(request);
        List<Map<String, Object>> record = userQuestionHistoryService.getUserQuestionHistoryRecord(loginUser.getId(), year);
        return ResultUtils.success(record);
    }

    /**
     * 获取我的学习统计
     */
    @GetMapping("/my/stats")
    public BaseResponse<Map<String, Object>> getMyQuestionStats(HttpServletRequest request) {
        final User loginUser = userService.getLoginUser(request);
        Map<String, Object> stats = userQuestionHistoryService.getUserQuestionStats(loginUser.getId());
        return ResultUtils.success(stats);
    }

    /**
     * 获取我的学习目标配置
     */
    @GetMapping("/my/goal")
    public BaseResponse<Map<String, Object>> getMyLearningGoal(HttpServletRequest request) {
        final User loginUser = userService.getLoginUser(request);
        UserLearningGoal learningGoal = userLearningGoalService.getOrInitByUserId(loginUser.getId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("dailyTarget", learningGoal.getDailyTarget());
        result.put("reminderEnabled", learningGoal.getReminderEnabled() != null && learningGoal.getReminderEnabled() == 1);
        return ResultUtils.success(result);
    }

    /**
     * 更新我的学习目标配置
     */
    @PostMapping("/my/goal/update")
    public BaseResponse<Boolean> updateMyLearningGoal(@RequestBody UserLearningGoalUpdateRequest updateRequest,
                                                      HttpServletRequest request) {
        if (updateRequest == null || updateRequest.getDailyTarget() == null || updateRequest.getDailyTarget() < 1
                || updateRequest.getDailyTarget() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每日目标需在 1 到 200 之间");
        }
        final User loginUser = userService.getLoginUser(request);
        boolean result = userLearningGoalService.updateUserLearningGoal(loginUser.getId(),
                updateRequest.getDailyTarget(),
                Boolean.TRUE.equals(updateRequest.getReminderEnabled()));
        return ResultUtils.success(result);
    }
}
