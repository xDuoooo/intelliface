package com.xduo.springbootinit.manager;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 题目生成任务状态管理
 */
@Component
@Slf4j
public class QuestionAiGenerateTaskManager {

    private static final String TASK_KEY_PREFIX = "question:ai:generate:task:";
    private static final long ACTIVE_TASK_TTL_SECONDS = 5 * 60L;
    private static final long FINISHED_TASK_TTL_SECONDS = 30 * 60L;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public AiGenerateTaskStatus createTask(Long creatorUserId, String questionType, int totalCount) {
        AiGenerateTaskStatus taskStatus = new AiGenerateTaskStatus();
        long now = System.currentTimeMillis();
        taskStatus.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        taskStatus.setCreatorUserId(creatorUserId);
        taskStatus.setQuestionType(StringUtils.trimToEmpty(questionType));
        taskStatus.setTotalCount(Math.max(1, totalCount));
        taskStatus.setSuccessCount(0);
        taskStatus.setFailedCount(0);
        taskStatus.setStatus(STATUS_PENDING);
        taskStatus.setMessage("任务已创建，准备开始生成");
        taskStatus.setCreateTime(now);
        taskStatus.setUpdateTime(now);
        saveTask(taskStatus, ACTIVE_TASK_TTL_SECONDS);
        return taskStatus;
    }

    public AiGenerateTaskStatus getTask(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            return null;
        }
        String rawValue = stringRedisTemplate.opsForValue().get(buildTaskKey(taskId));
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        try {
            return JSONUtil.toBean(rawValue, AiGenerateTaskStatus.class);
        } catch (Exception e) {
            log.warn("解析 AI 题目生成任务状态失败，taskId={}", taskId, e);
            return null;
        }
    }

    public AiGenerateTaskStatus markRunning(String taskId, String message) {
        return updateActiveTask(taskId, taskStatus -> {
            taskStatus.setStatus(STATUS_RUNNING);
            taskStatus.setMessage(StringUtils.defaultIfBlank(message, "正在生成题目"));
        });
    }

    public AiGenerateTaskStatus heartbeat(String taskId, String message) {
        return updateActiveTask(taskId, taskStatus -> {
            if (!isFinished(taskStatus.getStatus())) {
                taskStatus.setStatus(STATUS_RUNNING);
            }
            if (StringUtils.isNotBlank(message)) {
                taskStatus.setMessage(message.trim());
            }
        });
    }

    public AiGenerateTaskStatus markProgress(String taskId, int successCount, int failedCount, String message) {
        return updateActiveTask(taskId, taskStatus -> {
            taskStatus.setStatus(STATUS_RUNNING);
            taskStatus.setSuccessCount(Math.max(0, successCount));
            taskStatus.setFailedCount(Math.max(0, failedCount));
            taskStatus.setMessage(StringUtils.defaultIfBlank(message, "正在生成题目"));
        });
    }

    public AiGenerateTaskStatus markSuccess(String taskId, int successCount, int failedCount, String message) {
        return updateFinishedTask(taskId, taskStatus -> {
            long now = System.currentTimeMillis();
            taskStatus.setStatus(STATUS_SUCCESS);
            taskStatus.setSuccessCount(Math.max(0, successCount));
            taskStatus.setFailedCount(Math.max(0, failedCount));
            taskStatus.setMessage(StringUtils.defaultIfBlank(message, "题目生成完成"));
            taskStatus.setFinishTime(now);
        });
    }

    public AiGenerateTaskStatus markFailed(String taskId, int successCount, int failedCount, String message) {
        return updateFinishedTask(taskId, taskStatus -> {
            long now = System.currentTimeMillis();
            taskStatus.setStatus(STATUS_FAILED);
            taskStatus.setSuccessCount(Math.max(0, successCount));
            taskStatus.setFailedCount(Math.max(0, failedCount));
            taskStatus.setMessage(StringUtils.defaultIfBlank(message, "题目生成失败"));
            taskStatus.setFinishTime(now);
        });
    }

    private AiGenerateTaskStatus updateActiveTask(String taskId, java.util.function.Consumer<AiGenerateTaskStatus> updater) {
        AiGenerateTaskStatus taskStatus = getTask(taskId);
        if (taskStatus == null) {
            return null;
        }
        updater.accept(taskStatus);
        taskStatus.setUpdateTime(System.currentTimeMillis());
        saveTask(taskStatus, ACTIVE_TASK_TTL_SECONDS);
        return taskStatus;
    }

    private AiGenerateTaskStatus updateFinishedTask(String taskId, java.util.function.Consumer<AiGenerateTaskStatus> updater) {
        AiGenerateTaskStatus taskStatus = getTask(taskId);
        if (taskStatus == null) {
            return null;
        }
        updater.accept(taskStatus);
        taskStatus.setUpdateTime(System.currentTimeMillis());
        saveTask(taskStatus, FINISHED_TASK_TTL_SECONDS);
        return taskStatus;
    }

    private void saveTask(AiGenerateTaskStatus taskStatus, long ttlSeconds) {
        if (taskStatus == null || StringUtils.isBlank(taskStatus.getTaskId())) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                buildTaskKey(taskStatus.getTaskId()),
                JSONUtil.toJsonStr(taskStatus),
                Math.max(30L, ttlSeconds),
                TimeUnit.SECONDS
        );
    }

    private boolean isFinished(String status) {
        return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status);
    }

    private String buildTaskKey(String taskId) {
        return TASK_KEY_PREFIX + taskId;
    }

    @Data
    public static class AiGenerateTaskStatus {
        private String taskId;
        private Long creatorUserId;
        private String questionType;
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private String status;
        private String message;
        private Long createTime;
        private Long updateTime;
        private Long finishTime;
    }
}
