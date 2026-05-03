package com.xduo.springbootinit.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.manager.AiManager;
import com.xduo.springbootinit.mapper.MockInterviewMapper;
import com.xduo.springbootinit.model.dto.mockinterview.MockInterviewQueryRequest;
import com.xduo.springbootinit.model.entity.MockInterview;
import com.xduo.springbootinit.service.MockInterviewService;
import com.xduo.springbootinit.utils.SqlUtils;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 模拟面试服务实现
 */
@Service
public class MockInterviewServiceImpl extends ServiceImpl<MockInterviewMapper, MockInterview>
        implements MockInterviewService {

    private static final int DEFAULT_ROUNDS = 5;
    private static final int MIN_ROUNDS = 3;
    private static final int MAX_ROUNDS = 8;
    private static final int SHORT_ANSWER_THRESHOLD = 35;
    private static final long MAX_AUDIO_FILE_SIZE = 8L * 1024 * 1024;
    private static final int MAX_PROMPT_BACKGROUND_CHARS = 1200;
    private static final int MAX_PROMPT_TRANSCRIPT_MESSAGES = 14;
    private static final int MAX_PROMPT_TRANSCRIPT_CHARS = 3200;
    private static final int MAX_PROMPT_MESSAGE_CHARS = 280;
    private static final int MAX_USER_MESSAGE_CHARS = 4000;
    private static final int MAX_NEXT_QUESTION_CHARS = 160;
    private static final int MAX_FEEDBACK_TEXT_CHARS = 220;
    private static final int MAX_SUMMARY_TEXT_CHARS = 520;
    private static final int MAX_DISPLAY_TEXT_CHARS = 900;
    private static final int MAX_LIST_ITEMS = 4;
    private static final int MAX_LIST_ITEM_CHARS = 90;
    private static final int MAX_PRACTICE_PLAN_ITEMS = 3;
    private static final int MAX_PRACTICE_PLAN_ITEM_CHARS = 140;
    private static final int MIN_ROUNDS_BEFORE_AI_FINISH = 3;
    private static final List<String> SUPPORTED_AUDIO_SUFFIX_LIST = List.of("webm", "wav", "mp3", "m4a", "mp4", "ogg", "oga");

    @Resource
    private AiManager aiManager;

    @Override
    public void validMockInterview(MockInterview mockInterview, boolean add) {
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.PARAMS_ERROR);
        normalizeInterviewFields(mockInterview);
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(mockInterview.getJobPosition()), ErrorCode.PARAMS_ERROR, "岗位不能为空");
        }
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getJobPosition()) > 80, ErrorCode.PARAMS_ERROR, "岗位过长");
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getWorkExperience()) > 40, ErrorCode.PARAMS_ERROR, "工作年限过长");
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getInterviewType()) > 40, ErrorCode.PARAMS_ERROR, "面试类型过长");
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getTechStack()) > 256, ErrorCode.PARAMS_ERROR, "技术方向过长");
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getResumeText()) > 4000, ErrorCode.PARAMS_ERROR, "简历/项目背景过长");
        ThrowUtils.throwIf(StringUtils.length(mockInterview.getDifficulty()) > 40, ErrorCode.PARAMS_ERROR, "难度描述过长");
        if (mockInterview.getExpectedRounds() == null) {
            mockInterview.setExpectedRounds(DEFAULT_ROUNDS);
        }
        ThrowUtils.throwIf(mockInterview.getExpectedRounds() < MIN_ROUNDS || mockInterview.getExpectedRounds() > MAX_ROUNDS,
                ErrorCode.PARAMS_ERROR, "面试轮次需在 3 到 8 轮之间");
    }

    private void normalizeInterviewFields(MockInterview mockInterview) {
        mockInterview.setJobPosition(StringUtils.trimToNull(mockInterview.getJobPosition()));
        mockInterview.setWorkExperience(StringUtils.trimToNull(mockInterview.getWorkExperience()));
        mockInterview.setInterviewType(StringUtils.trimToNull(mockInterview.getInterviewType()));
        mockInterview.setTechStack(StringUtils.trimToNull(mockInterview.getTechStack()));
        mockInterview.setResumeText(StringUtils.trimToNull(mockInterview.getResumeText()));
        mockInterview.setDifficulty(StringUtils.trimToNull(mockInterview.getDifficulty()));
    }

    @Override
    public QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest queryRequest) {
        QueryWrapper<MockInterview> queryWrapper = new QueryWrapper<>();
        if (queryRequest == null) {
            return queryWrapper;
        }
        queryWrapper.eq(queryRequest.getId() != null && queryRequest.getId() > 0, "id", queryRequest.getId());
        queryWrapper.eq(queryRequest.getUserId() != null && queryRequest.getUserId() > 0, "userId", queryRequest.getUserId());
        queryWrapper.eq(queryRequest.getStatus() != null, "status", queryRequest.getStatus());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getJobPosition()), "jobPosition", queryRequest.getJobPosition());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getWorkExperience()), "workExperience", queryRequest.getWorkExperience());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getInterviewType()), "interviewType", queryRequest.getInterviewType());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getTechStack()), "techStack", queryRequest.getTechStack());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getDifficulty()), "difficulty", queryRequest.getDifficulty());
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    @Override
    public String handleInterviewEvent(MockInterview mockInterview, String event, String userMessage) {
        ThrowUtils.throwIf(mockInterview == null || mockInterview.getId() == null, ErrorCode.PARAMS_ERROR);
        String eventType = StringUtils.lowerCase(StringUtils.defaultIfBlank(event, "chat").trim());
        List<InterviewMessage> messageList = parseMessages(mockInterview.getMessages());

        switch (eventType) {
            case "start":
                ThrowUtils.throwIf(mockInterview.getStatus() != null && mockInterview.getStatus() != 0,
                        ErrorCode.OPERATION_ERROR, "面试已经开始");
                int firstRound = 1;
                InterviewReport initReport = initReport(mockInterview);
                InterviewPlanItem firstPlan = getPlanItem(initReport, firstRound);
                messageList.add(new InterviewMessage(
                        buildOpeningIntro(mockInterview),
                        true,
                        System.currentTimeMillis(),
                        0,
                        "opening"
                ));
                String openingQuestion = buildOpeningQuestion(mockInterview, firstPlan);
                messageList.add(new InterviewMessage(openingQuestion, true, System.currentTimeMillis() + 1, firstRound, "question"));
                applyCurrentPlan(initReport, firstPlan, "请先把项目背景、职责边界和技术方案主线讲清楚。");
                mockInterview.setStatus(1);
                mockInterview.setCurrentRound(0);
                mockInterview.setExpectedRounds(normalizeExpectedRounds(mockInterview.getExpectedRounds()));
                mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                mockInterview.setReport(JSONUtil.toJsonStr(initReport));
                this.updateById(mockInterview);
                return openingQuestion;
            case "chat":
                ThrowUtils.throwIf(mockInterview.getStatus() == null || mockInterview.getStatus() == 0,
                        ErrorCode.OPERATION_ERROR, "请先开始面试");
                ThrowUtils.throwIf(mockInterview.getStatus() == 3, ErrorCode.OPERATION_ERROR, "当前面试已暂停，请先继续面试");
                ThrowUtils.throwIf(mockInterview.getStatus() == 2, ErrorCode.OPERATION_ERROR, "当前面试已结束");
                String trimmedAnswer = StringUtils.trimToEmpty(userMessage);
                ThrowUtils.throwIf(StringUtils.isBlank(trimmedAnswer), ErrorCode.PARAMS_ERROR, "回答不能为空");
                ThrowUtils.throwIf(StringUtils.length(trimmedAnswer) > MAX_USER_MESSAGE_CHARS,
                        ErrorCode.PARAMS_ERROR, "回答内容过长，请控制在 4000 字以内");

                int answerRound = getNextRoundNumber(messageList);
                InterviewMessage latestQuestionMessage = getLatestQuestionMessage(messageList);
                String latestQuestion = latestQuestionMessage == null ? "" : latestQuestionMessage.content;
                boolean probeAnswer = latestQuestionMessage != null && "probe".equals(latestQuestionMessage.stage);
                Integer responseSeconds = calculateResponseSeconds(messageList);
                InterviewReport interviewReport = parseReport(mockInterview.getReport(), mockInterview);
                InterviewPlanItem currentPlan = getPlanItem(interviewReport, answerRound);
                messageList.add(new InterviewMessage(trimmedAnswer, false, System.currentTimeMillis(), answerRound, "answer"));

                RoundAnalysis roundAnalysis = buildRoundAnalysis(mockInterview, messageList, answerRound, currentPlan);
                boolean hasProbedThisRound = countAiMessagesByRoundAndStage(messageList, answerRound, "probe") > 0;
                if (shouldForceElaboration(mockInterview, trimmedAnswer, roundAnalysis.getScore()) && !hasProbedThisRound) {
                    roundAnalysis = buildElaborationAnalysis(mockInterview, currentPlan, answerRound, trimmedAnswer, roundAnalysis);
                } else if (roundAnalysis.isProbe() && hasProbedThisRound) {
                    roundAnalysis.setProbe(false);
                }

                upsertRoundRecord(interviewReport, new RoundRecord(
                        answerRound,
                        latestQuestion,
                        trimmedAnswer,
                        roundAnalysis.getShortComment(),
                        roundAnalysis.getFocus(),
                        roundAnalysis.getScore(),
                        roundAnalysis.getCommunicationScore(),
                        roundAnalysis.getTechnicalScore(),
                        roundAnalysis.getProblemSolvingScore(),
                        roundAnalysis.getScoreReasons(),
                        roundAnalysis.getQuestionStyle(),
                        roundAnalysis.getRecommendedAnswerSeconds(),
                        responseSeconds,
                        roundAnalysis.getVerdict(),
                        roundAnalysis.getImprovementTags(),
                        roundAnalysis.getMissingPoints(),
                        roundAnalysis.getAnswerQualitySignals(),
                        roundAnalysis.getAnswerRewriteSuggestion(),
                        roundAnalysis.getFollowUpReason()
                ), probeAnswer);
                interviewReport.setCompletedRounds(answerRound);
                mockInterview.setCurrentRound(answerRound);

                boolean shouldFinish = answerRound >= getExpectedRounds(mockInterview)
                        || (roundAnalysis.isShouldFinish() && canAiFinishInterview(mockInterview, answerRound));
                if (shouldFinish) {
                    SummaryResult summaryResult = buildSummary(mockInterview, messageList, interviewReport);
                    fillInterviewReportFromSummary(interviewReport, summaryResult);
                    clearCurrentPlan(interviewReport);
                    String finalMessage = summaryResult.getDisplayText();
                    messageList.add(new InterviewMessage(finalMessage, true, System.currentTimeMillis() + 1, answerRound, "summary"));
                    mockInterview.setStatus(2);
                    mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                    mockInterview.setReport(JSONUtil.toJsonStr(interviewReport));
                    this.updateById(mockInterview);
                    return finalMessage;
                }

                InterviewPlanItem nextPlan = roundAnalysis.isProbe() ? currentPlan : getPlanItem(interviewReport, answerRound + 1);
                applyCurrentPlan(
                        interviewReport,
                        nextPlan,
                        roundAnalysis.isProbe()
                                ? "这一次请你把背景、关键技术决策、结果指标和复盘都讲完整。"
                                : buildNextActionHint(nextPlan)
                );
                String nextQuestion = roundAnalysis.getNextQuestion();
                messageList.add(new InterviewMessage(
                        nextQuestion,
                        true,
                        System.currentTimeMillis() + 1,
                        roundAnalysis.isProbe() ? answerRound : answerRound + 1,
                        roundAnalysis.isProbe() ? "probe" : "question"
                ));
                mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                mockInterview.setReport(JSONUtil.toJsonStr(interviewReport));
                this.updateById(mockInterview);
                return nextQuestion;
            case "pause":
                ThrowUtils.throwIf(mockInterview.getStatus() == null || mockInterview.getStatus() != 1,
                        ErrorCode.OPERATION_ERROR, "只有进行中的面试才能暂停");
                mockInterview.setStatus(3);
                messageList.add(new InterviewMessage("好的，我们先暂停一下。你准备好后可以继续，我会接着当前考察点追问。",
                        true, System.currentTimeMillis(), mockInterview.getCurrentRound(), "pause"));
                mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                this.updateById(mockInterview);
                return "好的，我们先暂停一下。你准备好后可以继续，我会接着当前考察点追问。";
            case "resume":
                ThrowUtils.throwIf(mockInterview.getStatus() == null || mockInterview.getStatus() != 3,
                        ErrorCode.OPERATION_ERROR, "当前面试不处于暂停状态");
                InterviewReport resumeReport = parseReport(mockInterview.getReport(), mockInterview);
                mockInterview.setStatus(1);
                String resumeMessage = buildResumePrompt(resumeReport);
                messageList.add(new InterviewMessage(resumeMessage, true, System.currentTimeMillis(),
                        Math.max(mockInterview.getCurrentRound() == null ? 1 : mockInterview.getCurrentRound(), 1), "resume"));
                mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                mockInterview.setReport(JSONUtil.toJsonStr(resumeReport));
                this.updateById(mockInterview);
                return resumeMessage;
            case "end":
                ThrowUtils.throwIf(mockInterview.getStatus() != null && mockInterview.getStatus() == 2,
                        ErrorCode.OPERATION_ERROR, "当前面试已结束");
                InterviewReport endReport = parseReport(mockInterview.getReport(), mockInterview);
                endReport.setCompletedRounds((int) countCandidateAnswers(messageList));
                messageList.add(new InterviewMessage("我想先结束这场面试，请给我最终反馈。", false, System.currentTimeMillis(), endReport.getCompletedRounds(), "end"));
                SummaryResult summaryResult = buildSummary(mockInterview, messageList, endReport);
                fillInterviewReportFromSummary(endReport, summaryResult);
                clearCurrentPlan(endReport);
                messageList.add(new InterviewMessage(summaryResult.getDisplayText(), true, System.currentTimeMillis() + 1, endReport.getCompletedRounds(), "summary"));
                mockInterview.setStatus(2);
                mockInterview.setCurrentRound(endReport.getCompletedRounds());
                mockInterview.setMessages(JSONUtil.toJsonStr(messageList));
                mockInterview.setReport(JSONUtil.toJsonStr(endReport));
                this.updateById(mockInterview);
                return summaryResult.getDisplayText();
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的事件类型");
        }
    }

    @Override
    public SseEmitter streamInterviewEvent(MockInterview mockInterview, String event, String userMessage) {
        ThrowUtils.throwIf(mockInterview == null || mockInterview.getId() == null, ErrorCode.PARAMS_ERROR);
        List<InterviewMessage> beforeMessages = parseMessages(mockInterview.getMessages());
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, "status", buildStatusPayload("received", "已收到请求，正在同步面试状态。"));
                sendSseEvent(emitter, "status", buildStatusPayload("thinking", "面试官正在组织追问与反馈。"));
                String result = handleInterviewEvent(mockInterview, event, userMessage);
                MockInterview latestInterview = this.getById(mockInterview.getId());
                if (latestInterview == null) {
                    latestInterview = mockInterview;
                }
                List<InterviewMessage> afterMessages = parseMessages(latestInterview.getMessages());
                InterviewMessage streamTarget = getLatestGeneratedAiMessage(beforeMessages, afterMessages, result);
                if (streamTarget != null) {
                    sendSseEvent(emitter, "status", buildStatusPayload("streaming", "面试官正在输出回复。"));
                    streamAssistantMessage(emitter, streamTarget);
                }
                LinkedHashMap<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("message", result);
                donePayload.put("interview", latestInterview);
                sendSseEvent(emitter, "done", donePayload);
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendSseEvent(emitter, "error", buildStatusPayload("error",
                            StringUtils.defaultIfBlank(e.getMessage(), "流式面试处理失败")));
                } catch (Exception ignored) {
                    // ignore secondary send failure
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    @Override
    public String exportInterviewReview(MockInterview mockInterview) {
        ThrowUtils.throwIf(mockInterview == null || mockInterview.getId() == null, ErrorCode.PARAMS_ERROR);
        MockInterview latestInterview = this.getById(mockInterview.getId());
        if (latestInterview != null) {
            mockInterview = latestInterview;
        }
        List<InterviewMessage> messageList = parseMessages(mockInterview.getMessages());
        InterviewReport interviewReport = parseReport(mockInterview.getReport(), mockInterview);
        StringBuilder markdownBuilder = new StringBuilder(2048);

        markdownBuilder.append("# 模拟面试逐题复盘\n\n");
        markdownBuilder.append("## 面试信息\n\n");
        appendMarkdownBullet(markdownBuilder, "面试编号", String.valueOf(mockInterview.getId()));
        appendMarkdownBullet(markdownBuilder, "目标岗位", StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "-"));
        appendMarkdownBullet(markdownBuilder, "面试类型", StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖"));
        appendMarkdownBullet(markdownBuilder, "工作年限", StringUtils.defaultIfBlank(mockInterview.getWorkExperience(), "不限"));
        appendMarkdownBullet(markdownBuilder, "难度", StringUtils.defaultIfBlank(mockInterview.getDifficulty(), "中等"));
        appendMarkdownBullet(markdownBuilder, "技术方向", StringUtils.defaultIfBlank(mockInterview.getTechStack(), "通用后端"));
        appendMarkdownBullet(markdownBuilder, "计划轮次", String.valueOf(interviewReport.getExpectedRounds()));
        appendMarkdownBullet(markdownBuilder, "完成轮次", String.valueOf(interviewReport.getCompletedRounds()));
        appendMarkdownBullet(markdownBuilder, "创建时间", formatDateTime(mockInterview.getCreateTime()));
        if (StringUtils.isNotBlank(mockInterview.getResumeText())) {
            markdownBuilder.append("\n### 简历 / 项目背景\n\n");
            appendMarkdownQuote(markdownBuilder, mockInterview.getResumeText());
        }

        markdownBuilder.append("\n## 综合结论\n\n");
        appendMarkdownBullet(markdownBuilder, "综合评分", String.valueOf(interviewReport.getOverallScore()));
        appendMarkdownBullet(markdownBuilder, "表达能力", String.valueOf(interviewReport.getCommunicationScore()));
        appendMarkdownBullet(markdownBuilder, "技术深度", String.valueOf(interviewReport.getTechnicalScore()));
        appendMarkdownBullet(markdownBuilder, "问题分析", String.valueOf(interviewReport.getProblemSolvingScore()));
        appendMarkdownBullet(markdownBuilder, "当前就绪度", StringUtils.defaultIfBlank(interviewReport.getReadinessLevel(), "-"));
        markdownBuilder.append('\n');
        appendMarkdownQuote(markdownBuilder, StringUtils.defaultIfBlank(interviewReport.getSummary(), "面试总结暂未生成。"));
        if (StringUtils.isNotBlank(interviewReport.getRecommendedNextAction())) {
            markdownBuilder.append("\n### 下一步建议\n\n");
            appendMarkdownQuote(markdownBuilder, interviewReport.getRecommendedNextAction());
        }

        appendMarkdownStringList(markdownBuilder, "亮点总结", interviewReport.getStrengths());
        appendMarkdownStringList(markdownBuilder, "改进建议", interviewReport.getImprovements());
        appendMarkdownStringList(markdownBuilder, "建议继续准备", interviewReport.getSuggestedTopics());
        appendMarkdownStringList(markdownBuilder, "下一步训练计划", interviewReport.getPracticePlan());

        markdownBuilder.append("\n## 逐题复盘\n");
        if (interviewReport.getRoundRecords() == null || interviewReport.getRoundRecords().isEmpty()) {
            markdownBuilder.append("\n暂无逐题复盘记录。\n");
        } else {
            for (RoundRecord roundRecord : interviewReport.getRoundRecords()) {
                markdownBuilder.append("\n### 第 ")
                        .append(roundRecord.getRound() == null ? "-" : roundRecord.getRound())
                        .append(" 轮\n\n");
                appendMarkdownBullet(markdownBuilder, "考察重点", StringUtils.defaultIfBlank(roundRecord.getFocus(), "-"));
                appendMarkdownBullet(markdownBuilder, "题型", StringUtils.defaultIfBlank(roundRecord.getQuestionStyle(), "-"));
                appendMarkdownBullet(markdownBuilder, "建议作答时长",
                        roundRecord.getRecommendedAnswerSeconds() == null ? "-" : roundRecord.getRecommendedAnswerSeconds() + " 秒");
                appendMarkdownBullet(markdownBuilder, "实际作答用时",
                        roundRecord.getResponseSeconds() == null ? "-" : roundRecord.getResponseSeconds() + " 秒");
                appendMarkdownBullet(markdownBuilder, "本轮评分",
                        roundRecord.getScore() == null ? "-" : String.valueOf(roundRecord.getScore()));
                appendMarkdownBullet(markdownBuilder, "本轮结论", StringUtils.defaultIfBlank(roundRecord.getVerdict(), "-"));
                if (roundRecord.getImprovementTags() != null && !roundRecord.getImprovementTags().isEmpty()) {
                    appendMarkdownBullet(markdownBuilder, "重点提醒", String.join("、", roundRecord.getImprovementTags()));
                }
                if (roundRecord.getMissingPoints() != null && !roundRecord.getMissingPoints().isEmpty()) {
                    appendMarkdownBullet(markdownBuilder, "本轮缺口", String.join("、", roundRecord.getMissingPoints()));
                }
                if (roundRecord.getScoreReasons() != null && !roundRecord.getScoreReasons().isEmpty()) {
                    appendMarkdownBullet(markdownBuilder, "评分依据", String.join("；", roundRecord.getScoreReasons()));
                }
                if (roundRecord.getAnswerQualitySignals() != null && !roundRecord.getAnswerQualitySignals().isEmpty()) {
                    appendMarkdownBullet(markdownBuilder, "诊断信号", String.join("、", roundRecord.getAnswerQualitySignals()));
                }
                if (StringUtils.isNotBlank(roundRecord.getFollowUpReason())) {
                    appendMarkdownBullet(markdownBuilder, "追问理由", roundRecord.getFollowUpReason());
                }
                markdownBuilder.append('\n').append("#### 面试问题\n\n");
                appendMarkdownQuote(markdownBuilder, StringUtils.defaultIfBlank(roundRecord.getQuestion(), "暂无问题记录"));
                markdownBuilder.append("\n#### 候选人回答\n\n");
                appendMarkdownQuote(markdownBuilder, StringUtils.defaultIfBlank(roundRecord.getAnswer(), "暂无回答记录"));
                markdownBuilder.append("\n#### 面试官评语\n\n");
                appendMarkdownQuote(markdownBuilder, StringUtils.defaultIfBlank(roundRecord.getShortComment(), "暂无评语"));
                if (StringUtils.isNotBlank(roundRecord.getAnswerRewriteSuggestion())) {
                    markdownBuilder.append("\n#### 改进版回答骨架\n\n");
                    appendMarkdownQuote(markdownBuilder, roundRecord.getAnswerRewriteSuggestion());
                }
            }
        }

        markdownBuilder.append("\n## 完整对话\n");
        if (messageList.isEmpty()) {
            markdownBuilder.append("\n暂无对话记录。\n");
        } else {
            for (InterviewMessage interviewMessage : messageList) {
                markdownBuilder.append("\n### ")
                        .append(interviewMessage.isAI ? "面试官" : "候选人");
                if (interviewMessage.round != null && interviewMessage.round > 0) {
                    markdownBuilder.append(" · 第 ").append(interviewMessage.round).append(" 轮");
                }
                if (StringUtils.isNotBlank(interviewMessage.stage)) {
                    markdownBuilder.append(" · ").append(interviewMessage.stage);
                }
                markdownBuilder.append(" · ").append(formatTimestamp(interviewMessage.timestamp)).append("\n\n");
                appendMarkdownQuote(markdownBuilder, StringUtils.defaultIfBlank(interviewMessage.content, ""));
            }
        }
        return markdownBuilder.toString();
    }

    @Override
    public String transcribeInterviewAudio(MockInterview mockInterview, MultipartFile audioFile) {
        ThrowUtils.throwIf(mockInterview == null || mockInterview.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(audioFile == null || audioFile.isEmpty(), ErrorCode.PARAMS_ERROR, "请先录制语音后再转写");
        ThrowUtils.throwIf(audioFile.getSize() > MAX_AUDIO_FILE_SIZE, ErrorCode.PARAMS_ERROR, "语音文件过大，请控制在 8MB 以内");
        String suffix = StringUtils.lowerCase(FileUtil.getSuffix(audioFile.getOriginalFilename()));
        ThrowUtils.throwIf(StringUtils.isBlank(suffix) || !SUPPORTED_AUDIO_SUFFIX_LIST.contains(suffix),
                ErrorCode.PARAMS_ERROR, "仅支持 webm、wav、mp3、m4a、mp4、ogg 音频");
        try {
            String transcript = aiManager.transcribeAudio(
                    audioFile.getOriginalFilename(),
                    audioFile.getBytes(),
                    audioFile.getContentType(),
                    "zh",
                    buildTranscriptionPrompt(mockInterview),
                    mockInterview.getUserId()
            );
            String cleanedTranscript = sanitizeTranscript(transcript);
            ThrowUtils.throwIf(StringUtils.isBlank(cleanedTranscript), ErrorCode.SYSTEM_ERROR, "转写结果为空，请重试");
            return cleanedTranscript;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "语音转写失败，请稍后重试");
        }
    }

    private void fillInterviewReportFromSummary(InterviewReport interviewReport, SummaryResult summaryResult) {
        interviewReport.setOverallScore(summaryResult.getOverallScore());
        interviewReport.setSummary(summaryResult.getSummary());
        interviewReport.setStrengths(summaryResult.getStrengths());
        interviewReport.setImprovements(summaryResult.getImprovements());
        interviewReport.setSuggestedTopics(summaryResult.getSuggestedTopics());
        interviewReport.setCommunicationScore(summaryResult.getCommunicationScore());
        interviewReport.setTechnicalScore(summaryResult.getTechnicalScore());
        interviewReport.setProblemSolvingScore(summaryResult.getProblemSolvingScore());
        interviewReport.setReadinessLevel(summaryResult.getReadinessLevel());
        interviewReport.setRecommendedNextAction(summaryResult.getRecommendedNextAction());
        interviewReport.setPracticePlan(summaryResult.getPracticePlan());
    }

    private void applyCurrentPlan(InterviewReport interviewReport, InterviewPlanItem planItem, String nextActionHint) {
        interviewReport.setCurrentFocus(planItem == null ? null : planItem.getFocusTopic());
        interviewReport.setCurrentQuestionStyle(planItem == null ? null : planItem.getQuestionStyle());
        interviewReport.setRecommendedAnswerSeconds(planItem == null ? null : planItem.getRecommendedAnswerSeconds());
        interviewReport.setNextActionHint(nextActionHint);
        interviewReport.setAnswerChecklist(buildAnswerChecklist(planItem));
    }

    private void clearCurrentPlan(InterviewReport interviewReport) {
        interviewReport.setCurrentFocus(null);
        interviewReport.setCurrentQuestionStyle(null);
        interviewReport.setRecommendedAnswerSeconds(null);
        interviewReport.setNextActionHint(null);
        interviewReport.setAnswerChecklist(new ArrayList<>());
    }

    private List<InterviewMessage> parseMessages(String messages) {
        if (StringUtils.isBlank(messages)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(messages, InterviewMessage.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private InterviewReport parseReport(String report, MockInterview mockInterview) {
        if (StringUtils.isBlank(report)) {
            return initReport(mockInterview);
        }
        try {
            InterviewReport interviewReport = JSONUtil.toBean(report, InterviewReport.class);
            if (interviewReport.getRoundRecords() == null) {
                interviewReport.setRoundRecords(new ArrayList<>());
            }
            if (interviewReport.getStrengths() == null) {
                interviewReport.setStrengths(new ArrayList<>());
            }
            if (interviewReport.getImprovements() == null) {
                interviewReport.setImprovements(new ArrayList<>());
            }
            if (interviewReport.getSuggestedTopics() == null) {
                interviewReport.setSuggestedTopics(new ArrayList<>());
            }
            if (interviewReport.getPracticePlan() == null) {
                interviewReport.setPracticePlan(new ArrayList<>());
            }
            if (StringUtils.isBlank(interviewReport.getReadinessLevel())) {
                interviewReport.setReadinessLevel("继续训练中");
            }
            if (StringUtils.isBlank(interviewReport.getRecommendedNextAction())) {
                interviewReport.setRecommendedNextAction("继续围绕量化结果、技术取舍和稳定性思路做表达训练。");
            }
            List<InterviewPlanItem> agenda = normalizeAgenda(interviewReport.getAgenda(), mockInterview);
            interviewReport.setAgenda(agenda);
            if (interviewReport.getExpectedRounds() == null || interviewReport.getExpectedRounds() <= 0) {
                interviewReport.setExpectedRounds(normalizeExpectedRounds(mockInterview.getExpectedRounds()));
            }
            if (interviewReport.getAnswerChecklist() == null) {
                InterviewPlanItem currentPlan = getPlanItem(interviewReport,
                        Math.max(1, interviewReport.getCompletedRounds() == null ? 1 : interviewReport.getCompletedRounds() + 1));
                interviewReport.setAnswerChecklist(buildAnswerChecklist(currentPlan));
            }
            return interviewReport;
        } catch (Exception e) {
            return initReport(mockInterview);
        }
    }

    private long countCandidateAnswers(List<InterviewMessage> messageList) {
        Set<Integer> answeredRoundSet = new HashSet<>();
        long answerCountWithoutRound = 0;
        for (InterviewMessage message : messageList) {
            if (message.isAI || "end".equals(message.stage)) {
                continue;
            }
            if (message.round != null && message.round > 0) {
                answeredRoundSet.add(message.round);
            } else {
                answerCountWithoutRound++;
            }
        }
        return answeredRoundSet.size() + answerCountWithoutRound;
    }

    private void upsertRoundRecord(InterviewReport interviewReport, RoundRecord nextRecord, boolean mergeWithExistingRound) {
        if (interviewReport.getRoundRecords() == null) {
            interviewReport.setRoundRecords(new ArrayList<>());
        }
        if (!mergeWithExistingRound || nextRecord == null || nextRecord.getRound() == null) {
            interviewReport.getRoundRecords().add(nextRecord);
            return;
        }
        for (int i = interviewReport.getRoundRecords().size() - 1; i >= 0; i--) {
            RoundRecord existingRecord = interviewReport.getRoundRecords().get(i);
            if (existingRecord == null || !nextRecord.getRound().equals(existingRecord.getRound())) {
                continue;
            }
            existingRecord.setQuestion(mergeLabeledText(existingRecord.getQuestion(), "追问", nextRecord.getQuestion()));
            existingRecord.setAnswer(mergeLabeledText(existingRecord.getAnswer(), "补充回答", nextRecord.getAnswer()));
            existingRecord.setShortComment(mergeLabeledText(existingRecord.getShortComment(), "追问评语", nextRecord.getShortComment()));
            existingRecord.setFocus(nextRecord.getFocus());
            existingRecord.setScore(nextRecord.getScore());
            existingRecord.setCommunicationScore(nextRecord.getCommunicationScore());
            existingRecord.setTechnicalScore(nextRecord.getTechnicalScore());
            existingRecord.setProblemSolvingScore(nextRecord.getProblemSolvingScore());
            existingRecord.setScoreReasons(limitStringList(
                    mergeStringList(existingRecord.getScoreReasons(), nextRecord.getScoreReasons()),
                    3,
                    MAX_LIST_ITEM_CHARS));
            existingRecord.setQuestionStyle(nextRecord.getQuestionStyle());
            existingRecord.setRecommendedAnswerSeconds(nextRecord.getRecommendedAnswerSeconds());
            existingRecord.setResponseSeconds(nextRecord.getResponseSeconds());
            existingRecord.setVerdict(nextRecord.getVerdict());
            existingRecord.setImprovementTags(limitStringList(
                    mergeStringList(existingRecord.getImprovementTags(), nextRecord.getImprovementTags()),
                    MAX_LIST_ITEMS,
                    32));
            existingRecord.setMissingPoints(limitStringList(
                    mergeStringList(existingRecord.getMissingPoints(), nextRecord.getMissingPoints()),
                    MAX_LIST_ITEMS,
                    MAX_LIST_ITEM_CHARS));
            existingRecord.setAnswerQualitySignals(limitStringList(
                    mergeStringList(existingRecord.getAnswerQualitySignals(), nextRecord.getAnswerQualitySignals()),
                    MAX_LIST_ITEMS,
                    MAX_LIST_ITEM_CHARS));
            existingRecord.setAnswerRewriteSuggestion(mergeLabeledText(existingRecord.getAnswerRewriteSuggestion(),
                    "补充改法", nextRecord.getAnswerRewriteSuggestion()));
            existingRecord.setFollowUpReason(nextRecord.getFollowUpReason());
            return;
        }
        interviewReport.getRoundRecords().add(nextRecord);
    }

    private String mergeLabeledText(String existingText, String label, String nextText) {
        String safeNextText = StringUtils.trimToEmpty(nextText);
        if (StringUtils.isBlank(safeNextText)) {
            return existingText;
        }
        String safeExistingText = StringUtils.trimToEmpty(existingText);
        if (StringUtils.isBlank(safeExistingText)) {
            return safeNextText;
        }
        if (safeExistingText.contains(safeNextText)) {
            return safeExistingText;
        }
        return safeExistingText + "\n\n" + label + "：" + safeNextText;
    }

    private List<String> mergeStringList(List<String> firstList, List<String> secondList) {
        List<String> result = new ArrayList<>();
        appendDistinctStrings(result, firstList);
        appendDistinctStrings(result, secondList);
        return result;
    }

    private void appendDistinctStrings(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String item : source) {
            String normalizedItem = StringUtils.trimToEmpty(item);
            if (StringUtils.isNotBlank(normalizedItem) && !target.contains(normalizedItem)) {
                target.add(normalizedItem);
            }
        }
    }

    private List<String> buildAnswerChecklist(InterviewPlanItem planItem) {
        String questionStyle = planItem == null ? "" : StringUtils.defaultString(planItem.getQuestionStyle());
        List<String> checklist = new ArrayList<>();
        if (questionStyle.contains("架构") || questionStyle.contains("设计") || questionStyle.contains("扩展")) {
            checklist.add("先澄清业务目标、约束和量级");
            checklist.add("给出核心模块、数据流和关键接口");
            checklist.add("说明瓶颈、降级、监控和成本取舍");
            return checklist;
        }
        if (questionStyle.contains("数据") || questionStyle.contains("性能") || questionStyle.contains("稳定性")
                || questionStyle.contains("安全") || questionStyle.contains("成本")) {
            checklist.add("先说清目标指标、现状基线和约束");
            checklist.add("说明定位过程、关键动作和技术取舍");
            checklist.add("补充风险兜底、监控验证和最终效果");
            return checklist;
        }
        if (questionStyle.contains("行为") || questionStyle.contains("压力") || questionStyle.contains("协作")
                || questionStyle.contains("冲突") || questionStyle.contains("动机") || questionStyle.contains("规划")) {
            checklist.add("用一个具体事件回答，避免只讲观点");
            checklist.add("说清当时背景、你的行动和冲突处理");
            checklist.add("补充结果、反思和下一次会怎么做");
            return checklist;
        }
        if (questionStyle.contains("原理")) {
            checklist.add("先给出核心概念和适用场景");
            checklist.add("补充关键机制、边界条件和常见坑");
            checklist.add("结合项目里一次真实使用或排障经验");
            return checklist;
        }
        if (questionStyle.contains("结果") || questionStyle.contains("复盘") || questionStyle.contains("压测")) {
            checklist.add("给出目标指标、基线和最终变化");
            checklist.add("说清你的动作、问题定位和关键取舍");
            checklist.add("补充失败风险、复盘结论和后续优化");
            return checklist;
        }
        checklist.add("讲清业务背景、目标和你的职责边界");
        checklist.add("解释技术方案、关键取舍和为什么这样做");
        checklist.add("给出量化结果、问题复盘和后续优化");
        return checklist;
    }

    private String buildInterviewerPersona(MockInterview mockInterview) {
        String interviewType = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getInterviewType(), "技术深挖");
        String difficulty = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getDifficulty(), "中等");
        String persona = switch (interviewType) {
            case "项目拷打" -> "你是一位真实的一线技术面试官，风格偏项目深挖，会围绕候选人亲历细节、职责边界、技术决策、量化结果和复盘持续追问。";
            case "系统设计" -> "你是一位系统设计面试官，风格冷静、结构化，会围绕需求澄清、容量估算、架构拆分、数据模型、瓶颈、容灾和成本取舍追问。";
            case "HR" -> "你是一位有经验的 HR 面试官，风格温和但敏锐，会用行为面试法追问动机、协作、冲突、压力、稳定性和岗位匹配度。";
            default -> "你是一位高标准但克制的真实技术面试官，会围绕基础原理、项目经验、场景落地、边界条件和技术取舍追问。";
        };
        return persona + switch (difficulty) {
            case "初级" -> "难度为初级：多用引导式问题，允许候选人先讲思路，但仍要追问基础概念是否真正理解。";
            case "高级" -> "难度为高级：按中高级候选人标准提问，重点追问复杂场景、线上故障、设计取舍、数据指标、跨团队推进和复盘深度。";
            default -> "难度为中等：问题要贴近真实业务，既看基础准确性，也看项目落地和分析能力。";
        };
    }

    private String buildQuestionPolicy(MockInterview mockInterview) {
        String interviewType = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getInterviewType(), "技术深挖");
        return switch (interviewType) {
            case "项目拷打" -> "追问策略：优先抓候选人回答里的具体名词、指标或方案继续问；如果回答只说“我们做了”，要追问“你具体负责哪一块、为什么这样做、结果怎么证明”。";
            case "系统设计" -> "追问策略：先逼候选人明确约束和量级，再进入架构；如果回答缺少容量估算、关键接口、数据一致性或降级方案，要优先追问这些缺口。";
            case "HR" -> "追问策略：优先要求候选人用具体事件回答；如果回答是价值观口号，要追问当时背景、行动、冲突、结果和复盘。";
            default -> "追问策略：每次只问一个具体问题，优先围绕候选人最新回答里的薄弱点继续问，避免一次性罗列多个方向。";
        };
    }

    private String buildScoringPolicy(MockInterview mockInterview) {
        String difficulty = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getDifficulty(), "中等");
        return switch (difficulty) {
            case "初级" -> "评分策略：初级难度下，能讲清基础概念、基本流程和个人参与部分可给中等分；但概念错误、答非所问或完全没有实践细节仍要扣分。";
            case "高级" -> "评分策略：高级难度下，只有回答包含真实项目细节、关键指标、技术取舍、边界场景、故障处理或复盘改进时才可给高分；泛泛而谈通常不超过 65 分。";
            default -> "评分策略：评分要真实，不要默认给高分。候选人回答空泛、没有量化结果、没有技术取舍、没有边界场景时，应明显扣分。";
        };
    }

    private int getShortAnswerThreshold(MockInterview mockInterview) {
        String difficulty = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getDifficulty(), "中等");
        return switch (difficulty) {
            case "初级" -> 30;
            case "高级" -> 60;
            default -> SHORT_ANSWER_THRESHOLD;
        };
    }

    private int getLowScoreProbeThreshold(MockInterview mockInterview) {
        String difficulty = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getDifficulty(), "中等");
        return switch (difficulty) {
            case "初级" -> 52;
            case "高级" -> 65;
            default -> 58;
        };
    }

    private int getDifficultyScoreBias(MockInterview mockInterview) {
        String difficulty = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getDifficulty(), "中等");
        return switch (difficulty) {
            case "初级" -> 4;
            case "高级" -> -8;
            default -> 0;
        };
    }

    private String buildOpeningIntro(MockInterview mockInterview) {
        return String.format("你好，欢迎参加这场 %s 模拟面试。岗位是 %s，难度按%s处理。我会按真实面试节奏，围绕你的项目经历、技术深度和分析能力逐步追问。",
                StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术"),
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "目标岗位"),
                StringUtils.defaultIfBlank(mockInterview.getDifficulty(), "中等"));
    }

    private String buildOpeningQuestion(MockInterview mockInterview, InterviewPlanItem planItem) {
        String systemPrompt = buildInterviewerPersona(mockInterview)
                + buildQuestionPolicy(mockInterview)
                + "要求：只问一个问题；像真人面试官口头提问，优先引用候选人背景中的项目或技术词；不要输出 markdown；不要附带答案。"
                + "问题长度控制在 80 个中文字符内。除非是 HR 面试，否则不要只问泛泛的自我介绍。";
        String userPrompt = String.format(
                "岗位：%s；工作年限：%s；难度：%s；面试类型：%s；技术方向：%s；候选人背景：%s。"
                        + "第一轮考察重点：%s；问题类型：%s。请给出开场问题。",
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "技术岗位"),
                StringUtils.defaultIfBlank(mockInterview.getWorkExperience(), "不限"),
                StringUtils.defaultIfBlank(mockInterview.getDifficulty(), "中等"),
                StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖"),
                StringUtils.defaultIfBlank(mockInterview.getTechStack(), "通用后端"),
                buildCandidateBackgroundContext(mockInterview),
                planItem == null ? "项目背景与自我介绍" : planItem.getFocusTopic(),
                planItem == null ? "开场问题" : planItem.getQuestionStyle());
        String fallback = buildOpeningFallback(mockInterview, planItem);
        return normalizeNextQuestion(chatWithFallback(mockInterview, systemPrompt, userPrompt, fallback), fallback);
    }

    private RoundAnalysis buildRoundAnalysis(MockInterview mockInterview,
                                             List<InterviewMessage> messageList,
                                             int completedRounds,
                                             InterviewPlanItem currentPlan) {
        InterviewPlanItem nextPlan = getPlanByRound(mockInterview, completedRounds + 1);
        String latestQuestion = getLatestQuestion(messageList);
        String latestAnswer = getLatestCandidateAnswer(messageList);
        String askedQuestionSummary = buildAskedQuestionSummary(messageList);
        String answerQualitySignals = buildAnswerQualitySignals(latestAnswer);
        int probeCount = (int) countAiMessagesByRoundAndStage(messageList, completedRounds, "probe");
        boolean canProbe = probeCount == 0;
        String systemPrompt = buildInterviewerPersona(mockInterview)
                + buildQuestionPolicy(mockInterview)
                + buildScoringPolicy(mockInterview)
                + "请严格输出 JSON 对象，不要输出 markdown。"
                + "字段必须包含：shortComment、focus、score、communicationScore、technicalScore、problemSolvingScore、scoreReasons、questionStyle、recommendedAnswerSeconds、verdict、improvementTags、missingPoints、answerRewriteSuggestion、followUpReason、nextActionHint、nextQuestion、shouldFinish、probe。"
                + "shortComment 必须引用候选人回答里的具体表现或缺失信息；missingPoints 输出 2 到 4 个本轮缺口；answerRewriteSuggestion 给出一段可直接照着练的改进版回答骨架。"
                + "scoreReasons 必须输出 3 条，分别解释表达能力、技术深度、问题分析为什么得到当前分数，每条必须有证据，不能只写结论。"
                + "nextQuestion 必须只包含一个口语化问题，长度不超过 100 个中文字符，要结合候选人最新回答追问具体细节，不要机械复述题纲，不要连续问多个问题。"
                + "followUpReason 用一句话说明为什么问下一题，方便前端展示面试官意图。"
                + "下一问不能重复已问过问题的表达和考察角度；如果需要继续深挖，必须换一个更具体的证据点。"
                + "如果候选人表达“不知道、没做过、不确定”，不要只给空泛安慰，要追问其理解、推理路径、验证方法或学习补齐计划。"
                + "probe=true 表示继续深挖本轮同一考点；只有当本轮还没有追问过、且最新回答缺少关键判断依据时才允许 probe=true。"
                + "如果本轮已经追问过一次，必须 probe=false 并进入下一考点。";
        String userPrompt = String.format(
                "岗位：%s；工作年限：%s；难度：%s；面试类型：%s；技术方向：%s；计划轮次：%d；当前已完成轮次：%d；候选人背景：%s。"
                        + "当前轮考察重点：%s；当前问题类型：%s；下一轮建议考察重点：%s；本轮已追问次数：%d；是否还能继续深挖本轮：%s。"
                        + "已问过的问题：%s\n回答质量信号：%s\n最新问题：%s\n最新回答：%s\n"
                        + "请根据以下完整对话，对候选人最新回答做专业判断，并给出下一问。\n%s",
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "技术岗位"),
                StringUtils.defaultIfBlank(mockInterview.getWorkExperience(), "不限"),
                StringUtils.defaultIfBlank(mockInterview.getDifficulty(), "中等"),
                StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖"),
                StringUtils.defaultIfBlank(mockInterview.getTechStack(), "通用后端"),
                getExpectedRounds(mockInterview),
                completedRounds,
                buildCandidateBackgroundContext(mockInterview),
                currentPlan == null ? "技术深挖" : currentPlan.getFocusTopic(),
                currentPlan == null ? "追问" : currentPlan.getQuestionStyle(),
                nextPlan == null ? "总结归纳" : nextPlan.getFocusTopic(),
                probeCount,
                canProbe ? "是" : "否",
                askedQuestionSummary,
                answerQualitySignals,
                StringUtils.defaultIfBlank(latestQuestion, "暂无"),
                StringUtils.defaultIfBlank(latestAnswer, "暂无"),
                buildPromptTranscript(messageList, MAX_PROMPT_TRANSCRIPT_MESSAGES, MAX_PROMPT_TRANSCRIPT_CHARS));
        String fallbackJson = JSONUtil.toJsonStr(buildRoundFallback(mockInterview, completedRounds, currentPlan, nextPlan));
        return parseRoundAnalysis(chatWithFallback(mockInterview, systemPrompt, userPrompt, fallbackJson),
                mockInterview, completedRounds, currentPlan, nextPlan, canProbe, latestQuestion, latestAnswer);
    }

    private SummaryResult buildSummary(MockInterview mockInterview, List<InterviewMessage> messageList, InterviewReport interviewReport) {
        String systemPrompt = buildInterviewerPersona(mockInterview)
                + buildScoringPolicy(mockInterview)
                + "请严格输出 JSON 对象，不要输出 markdown。"
                + "字段必须包含：overallScore、summary、strengths、improvements、suggestedTopics、communicationScore、technicalScore、problemSolvingScore、readinessLevel、recommendedNextAction、practicePlan、displayText。"
                + "practicePlan 输出 3 条下一步训练动作，每条必须具体到练什么和如何验收。displayText 必须以【面试结束】开头。总结要具体指出 2 到 3 个真实短板，不要写空泛鼓励。";
        String userPrompt = String.format(
                "岗位：%s；工作年限：%s；难度：%s；面试类型：%s；技术方向：%s；计划轮次：%d；实际完成轮次：%d；候选人背景：%s。"
                        + "请总结以下面试记录，并结合已有轮次评语给出结构化终评。\n轮次评语：%s\n对话记录：\n%s",
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "技术岗位"),
                StringUtils.defaultIfBlank(mockInterview.getWorkExperience(), "不限"),
                StringUtils.defaultIfBlank(mockInterview.getDifficulty(), "中等"),
                StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖"),
                StringUtils.defaultIfBlank(mockInterview.getTechStack(), "通用后端"),
                getExpectedRounds(mockInterview),
                interviewReport.getCompletedRounds(),
                buildCandidateBackgroundContext(mockInterview),
                JSONUtil.toJsonStr(interviewReport.getRoundRecords()),
                buildPromptTranscript(messageList, MAX_PROMPT_TRANSCRIPT_MESSAGES + 4, MAX_PROMPT_TRANSCRIPT_CHARS + 800));
        String fallback = JSONUtil.toJsonStr(buildSummaryFallback(interviewReport));
        return parseSummaryResult(chatWithFallback(mockInterview, systemPrompt, userPrompt, fallback), interviewReport);
    }

    private String buildTranscript(List<InterviewMessage> messageList) {
        StringBuilder transcriptBuilder = new StringBuilder();
        for (InterviewMessage message : messageList) {
            transcriptBuilder.append(message.isAI ? "面试官：" : "候选人：")
                    .append(message.content)
                    .append('\n');
        }
        return transcriptBuilder.toString();
    }

    private String buildPromptTranscript(List<InterviewMessage> messageList, int maxMessages, int maxChars) {
        if (messageList == null || messageList.isEmpty()) {
            return "";
        }
        int safeMaxMessages = Math.max(1, maxMessages);
        int startIndex = Math.max(0, messageList.size() - safeMaxMessages);
        StringBuilder transcriptBuilder = new StringBuilder();
        if (startIndex > 0) {
            transcriptBuilder.append("（已省略更早的对话，仅保留最近关键轮次）\n");
        }
        for (int i = startIndex; i < messageList.size(); i++) {
            InterviewMessage message = messageList.get(i);
            transcriptBuilder.append(message.isAI ? "面试官：" : "候选人：")
                    .append(abbreviateText(message.content, MAX_PROMPT_MESSAGE_CHARS))
                    .append('\n');
        }
        String transcript = transcriptBuilder.toString();
        return abbreviateText(transcript, Math.max(400, maxChars));
    }

    private String buildAskedQuestionSummary(List<InterviewMessage> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return "暂无";
        }
        List<String> questions = new ArrayList<>();
        for (InterviewMessage message : messageList) {
            if (message.isAI && ("question".equals(message.stage) || "probe".equals(message.stage))) {
                String question = abbreviateText(message.content, 90);
                if (StringUtils.isNotBlank(question)) {
                    questions.add(String.format("第%s轮：%s",
                            message.round == null ? "-" : message.round,
                            question));
                }
            }
        }
        if (questions.isEmpty()) {
            return "暂无";
        }
        int startIndex = Math.max(0, questions.size() - 6);
        return String.join("；", questions.subList(startIndex, questions.size()));
    }

    private List<String> buildAnswerQualitySignalList(String answer) {
        String normalizedAnswer = StringUtils.trimToEmpty(answer);
        if (StringUtils.isBlank(normalizedAnswer)) {
            return new ArrayList<>(List.of("空回答"));
        }
        List<String> signals = new ArrayList<>();
        if (looksLikeNonAnswer(normalizedAnswer)) {
            signals.add("候选人表达不知道或没做过，需要追问理解、推理和补齐计划");
        }
        if (normalizedAnswer.length() < SHORT_ANSWER_THRESHOLD) {
            signals.add("回答偏短");
        }
        if (!hasQuantitativeSignal(normalizedAnswer)) {
            signals.add("缺少量化指标");
        }
        if (!hasResponsibilitySignal(normalizedAnswer)) {
            signals.add("个人职责不清楚");
        }
        if (!hasReasoningSignal(normalizedAnswer)) {
            signals.add("技术取舍或推理链不足");
        }
        if (!hasReviewSignal(normalizedAnswer)) {
            signals.add("缺少复盘或风险意识");
        }
        return signals;
    }

    private String buildAnswerQualitySignals(String answer) {
        List<String> signals = buildAnswerQualitySignalList(answer);
        return signals.isEmpty() ? "回答信息相对完整，可继续按计划深挖" : String.join("；", signals);
    }

    private boolean hasQuantitativeSignal(String answer) {
        return StringUtils.defaultString(answer)
                .matches(".*(\\d|%|QPS|qps|RT|rt|ms|SLA|sla|p95|p99|秒|分钟|提升|降低|减少|增长|成本|耗时).*");
    }

    private boolean hasResponsibilitySignal(String answer) {
        return StringUtils.defaultString(answer)
                .matches(".*(我|本人|负责|主导|参与|推进|落地).*");
    }

    private boolean hasReasoningSignal(String answer) {
        return StringUtils.defaultString(answer)
                .matches(".*(因为|所以|权衡|取舍|方案|设计|架构|选型|考虑).*");
    }

    private boolean hasReviewSignal(String answer) {
        return StringUtils.defaultString(answer)
                .matches(".*(复盘|优化|改进|风险|异常|降级|监控|告警|如果重来).*");
    }

    private String buildCandidateBackgroundContext(MockInterview mockInterview) {
        String background = mockInterview == null ? null : mockInterview.getResumeText();
        if (StringUtils.isBlank(background)) {
            return "暂无额外背景";
        }
        return abbreviateText(background, MAX_PROMPT_BACKGROUND_CHARS);
    }

    private String abbreviateText(String content, int maxChars) {
        String normalized = StringUtils.defaultString(content)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        int keepLength = Math.max(0, maxChars - 1);
        return normalized.substring(0, keepLength).trim() + "…";
    }

    private InterviewReport initReport(MockInterview mockInterview) {
        InterviewReport report = new InterviewReport();
        report.setExpectedRounds(normalizeExpectedRounds(mockInterview == null ? null : mockInterview.getExpectedRounds()));
        report.setCompletedRounds(0);
        report.setOverallScore(0);
        report.setSummary("");
        report.setCommunicationScore(0);
        report.setTechnicalScore(0);
        report.setProblemSolvingScore(0);
        report.setStrengths(new ArrayList<>());
        report.setImprovements(new ArrayList<>());
        report.setSuggestedTopics(new ArrayList<>());
        report.setPracticePlan(new ArrayList<>());
        report.setRoundRecords(new ArrayList<>());
        report.setAgenda(buildInterviewAgenda(mockInterview));
        report.setAnswerChecklist(buildAnswerChecklist(getPlanItem(report, 1)));
        report.setReadinessLevel("继续训练中");
        report.setRecommendedNextAction("建议先把项目背景、技术方案、量化结果和复盘四段式表达练顺。");
        return report;
    }

    private int normalizeExpectedRounds(Integer expectedRounds) {
        if (expectedRounds == null) {
            return DEFAULT_ROUNDS;
        }
        return Math.min(MAX_ROUNDS, Math.max(MIN_ROUNDS, expectedRounds));
    }

    private int getExpectedRounds(MockInterview mockInterview) {
        int expectedRounds = normalizeExpectedRounds(mockInterview.getExpectedRounds());
        mockInterview.setExpectedRounds(expectedRounds);
        return expectedRounds;
    }

    private boolean canAiFinishInterview(MockInterview mockInterview, int completedRounds) {
        return completedRounds >= Math.min(getExpectedRounds(mockInterview), MIN_ROUNDS_BEFORE_AI_FINISH);
    }

    private int getNextRoundNumber(List<InterviewMessage> messageList) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            InterviewMessage interviewMessage = messageList.get(i);
            if (interviewMessage.isAI
                    && ("question".equals(interviewMessage.stage) || "probe".equals(interviewMessage.stage))
                    && interviewMessage.round != null
                    && interviewMessage.round > 0) {
                return interviewMessage.round;
            }
        }
        return (int) countCandidateAnswers(messageList) + 1;
    }

    private Integer calculateResponseSeconds(List<InterviewMessage> messageList) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            InterviewMessage interviewMessage = messageList.get(i);
            if (interviewMessage.isAI && ("question".equals(interviewMessage.stage) || "probe".equals(interviewMessage.stage))) {
                long diff = Math.max(0, System.currentTimeMillis() - interviewMessage.timestamp);
                return (int) Math.max(1, diff / 1000);
            }
        }
        return null;
    }

    private String getLatestQuestion(List<InterviewMessage> messageList) {
        InterviewMessage latestQuestionMessage = getLatestQuestionMessage(messageList);
        return latestQuestionMessage == null ? "" : latestQuestionMessage.content;
    }

    private InterviewMessage getLatestQuestionMessage(List<InterviewMessage> messageList) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            InterviewMessage interviewMessage = messageList.get(i);
            if (interviewMessage.isAI && ("question".equals(interviewMessage.stage) || "probe".equals(interviewMessage.stage))) {
                return interviewMessage;
            }
        }
        return null;
    }

    private String getLatestCandidateAnswer(List<InterviewMessage> messageList) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            InterviewMessage interviewMessage = messageList.get(i);
            if (!interviewMessage.isAI && !"end".equals(interviewMessage.stage)) {
                return interviewMessage.content;
            }
        }
        return "";
    }

    private long countAiMessagesByRoundAndStage(List<InterviewMessage> messageList, Integer round, String stage) {
        if (round == null || round <= 0 || StringUtils.isBlank(stage)) {
            return 0;
        }
        return messageList.stream()
                .filter(message -> message.isAI)
                .filter(message -> round.equals(message.round))
                .filter(message -> stage.equals(message.stage))
                .count();
    }

    private String buildResumePrompt(InterviewReport interviewReport) {
        String focus = StringUtils.defaultIfBlank(interviewReport.getCurrentFocus(), "当前轮次重点");
        String style = StringUtils.defaultIfBlank(interviewReport.getCurrentQuestionStyle(), "继续追问");
        String hint = StringUtils.defaultIfBlank(interviewReport.getNextActionHint(), "这次请尽量把背景、方案、结果和复盘讲完整。");
        return String.format("我们继续。刚才停在“%s”这一块，我会按“%s”的方式继续追问。%s", focus, style, hint);
    }

    private String buildOpeningFallback(MockInterview mockInterview, InterviewPlanItem planItem) {
        String interviewType = StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖");
        if ("项目拷打".equals(interviewType)) {
            return "先挑一个你最有代表性的项目，讲清楚它的业务目标、你的职责以及你认为最难的一次技术决策。";
        }
        if ("系统设计".equals(interviewType)) {
            return "我们先做一道系统设计题。请你设计一个支持高并发的消息通知系统，并先说说你的总体思路。";
        }
        if ("HR".equals(interviewType)) {
            return "先做一个简短自我介绍，并说说你为什么会投递这个岗位。";
        }
        if (planItem != null && StringUtils.isNotBlank(planItem.getFocusTopic())) {
            return String.format("先从 %s 说起，请你结合最近的项目经历展开回答。", planItem.getFocusTopic());
        }
        return String.format("请先做一个简短自我介绍，并说明你在 %s 相关项目中最有代表性的一次实践。",
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "当前岗位"));
    }

    private String buildFallbackNextQuestion(MockInterview mockInterview,
                                             InterviewPlanItem currentPlan,
                                             InterviewPlanItem nextPlan) {
        InterviewPlanItem targetPlan = nextPlan == null ? currentPlan : nextPlan;
        String topic = targetPlan == null
                ? StringUtils.defaultIfBlank(mockInterview.getTechStack(), mockInterview.getJobPosition())
                : targetPlan.getFocusTopic();
        String style = targetPlan == null ? "" : StringUtils.defaultString(targetPlan.getQuestionStyle());
        if (style.contains("架构") || style.contains("设计") || style.contains("扩展")) {
            return String.format("围绕 %s，你会先怎么拆核心模块和数据流？", StringUtils.defaultIfBlank(topic, "这个系统"));
        }
        if (style.contains("原理")) {
            return String.format("你刚才提到的 %s，核心机制和最容易踩坑的边界是什么？", StringUtils.defaultIfBlank(topic, "这个技术点"));
        }
        if (style.contains("行为") || style.contains("压力") || style.contains("协作")) {
            return "请举一个具体事件，说说你当时怎么推进、遇到什么阻力、最后结果如何？";
        }
        if (style.contains("结果") || style.contains("复盘")) {
            return "这件事最后拿到了什么可量化结果，如果重来你会优先改哪一点？";
        }
        return String.format("围绕 %s，你当时最关键的技术取舍是什么，为什么这样选？",
                StringUtils.defaultIfBlank(topic, "这个场景"));
    }

    private RoundAnalysis buildRoundFallback(MockInterview mockInterview,
                                             int completedRounds,
                                             InterviewPlanItem currentPlan,
                                             InterviewPlanItem nextPlan) {
        int difficultyBias = getDifficultyScoreBias(mockInterview);
        RoundAnalysis roundAnalysis = new RoundAnalysis();
        roundAnalysis.setShortComment(completedRounds <= 2 ? "回答方向基本正确，但还可以再深入一些细节。" : "回答有一定深度，建议继续补充边界场景和量化结果。");
        roundAnalysis.setFocus(currentPlan == null ? "继续深挖项目细节、技术取舍和稳定性思路" : currentPlan.getFocusTopic());
        roundAnalysis.setScore(Math.max(50, Math.min(88, 66 + completedRounds * 4 + difficultyBias)));
        roundAnalysis.setCommunicationScore(Math.max(50, Math.min(86, 64 + completedRounds * 3 + difficultyBias)));
        roundAnalysis.setTechnicalScore(Math.max(50, Math.min(90, 65 + completedRounds * 4 + difficultyBias)));
        roundAnalysis.setProblemSolvingScore(Math.max(50, Math.min(88, 63 + completedRounds * 4 + difficultyBias)));
        roundAnalysis.setScoreReasons(new ArrayList<>(List.of(
                "表达：回答能够围绕问题展开，但结构和重点还可以更清晰",
                "技术：有一定技术相关信息，但缺少更具体的实现细节或边界说明",
                "分析：能继续讨论方案，但量化验证、风险判断和取舍依据还不够充分"
        )));
        roundAnalysis.setQuestionStyle(nextPlan == null ? "总结收束" : nextPlan.getQuestionStyle());
        roundAnalysis.setRecommendedAnswerSeconds(nextPlan == null ? 90 : nextPlan.getRecommendedAnswerSeconds());
        roundAnalysis.setNextActionHint(buildNextActionHint(nextPlan));
        roundAnalysis.setVerdict(completedRounds <= 2 ? "回答方向基本正确，但深度还不够" : "回答较稳，但还缺更强的量化和取舍表达");
        roundAnalysis.setImprovementTags(new ArrayList<>(List.of("补量化结果", "补设计取舍", "补异常场景")));
        roundAnalysis.setMissingPoints(new ArrayList<>(List.of("缺少可验证指标", "技术取舍理由不够明确", "异常和边界场景覆盖不足")));
        roundAnalysis.setAnswerQualitySignals(new ArrayList<>(List.of("缺少可验证指标", "技术取舍或推理链不足")));
        roundAnalysis.setAnswerRewriteSuggestion("可以按“背景目标 -> 我的职责 -> 关键方案和取舍 -> 量化结果 -> 复盘改进”的顺序重答一遍。");
        roundAnalysis.setFollowUpReason("继续确认候选人是否能把方案落到真实业务指标和边界场景。");
        roundAnalysis.setNextQuestion(buildFallbackNextQuestion(mockInterview, currentPlan, nextPlan));
        roundAnalysis.setShouldFinish(false);
        roundAnalysis.setProbe(false);
        return roundAnalysis;
    }

    private SummaryResult buildSummaryFallback(InterviewReport interviewReport) {
        SummaryResult summaryResult = new SummaryResult();
        int completedRounds = Math.max(1, interviewReport.getCompletedRounds());
        int fallbackCommunicationScore = Math.max(60, Math.min(90, 68 + completedRounds * 2));
        int fallbackTechnicalScore = Math.max(62, Math.min(92, 70 + completedRounds * 2));
        int fallbackProblemSolvingScore = Math.max(60, Math.min(90, 67 + completedRounds * 2));
        int communicationScore = averageRoundScore(interviewReport, RoundRecord::getCommunicationScore, fallbackCommunicationScore);
        int technicalScore = averageRoundScore(interviewReport, RoundRecord::getTechnicalScore, fallbackTechnicalScore);
        int problemSolvingScore = averageRoundScore(interviewReport, RoundRecord::getProblemSolvingScore, fallbackProblemSolvingScore);
        summaryResult.setOverallScore(averageRoundScore(interviewReport,
                RoundRecord::getScore,
                (communicationScore + technicalScore + problemSolvingScore) / 3));
        summaryResult.setSummary("整体表现较稳，具备一定项目经验和表达基础，但在量化结果、系统设计取舍与边界场景分析上还有提升空间。");
        summaryResult.setStrengths(new ArrayList<>(List.of("能够围绕问题作答", "具备一定项目实践经验", "回答结构相对完整")));
        summaryResult.setImprovements(new ArrayList<>(List.of("补充更多量化结果", "进一步说明设计取舍与稳定性策略", "回答时增加边界场景和异常处理说明")));
        summaryResult.setSuggestedTopics(new ArrayList<>(List.of("高并发场景设计", "数据库与缓存优化", "异常处理与稳定性治理")));
        summaryResult.setCommunicationScore(communicationScore);
        summaryResult.setTechnicalScore(technicalScore);
        summaryResult.setProblemSolvingScore(problemSolvingScore);
        summaryResult.setReadinessLevel("具备一轮技术面试基础，建议继续冲刺表达和系统设计");
        summaryResult.setRecommendedNextAction("优先复盘回答太短的轮次，并围绕量化结果、技术选型理由和稳定性方案做二次演练。");
        summaryResult.setPracticePlan(new ArrayList<>(List.of(
                "挑 2 道回答最短的题，用 2 分钟版本重录，并检查是否包含背景、职责、方案、结果、复盘",
                "为核心项目补 3 个量化指标，例如 QPS、耗时、成本、稳定性或业务收益",
                "准备 2 个失败或线上问题案例，练习说明定位过程、取舍和复盘改进"
        )));
        summaryResult.setDisplayText("【面试结束】总体评价：回答较完整，具备一定技术基础。亮点：能结合项目经验展开说明。改进建议：继续补充量化结果、设计取舍和异常场景细节。");
        return summaryResult;
    }

    private RoundAnalysis parseRoundAnalysis(String content,
                                             MockInterview mockInterview,
                                             int completedRounds,
                                             InterviewPlanItem currentPlan,
                                             InterviewPlanItem nextPlan,
                                             boolean canProbe,
                                             String latestQuestion,
                                             String latestAnswer) {
        try {
            Map<?, ?> result = JSONUtil.toBean(extractJsonContent(content), Map.class);
            RoundAnalysis fallback = buildRoundFallback(mockInterview, completedRounds, currentPlan, nextPlan);
            RoundAnalysis roundAnalysis = new RoundAnalysis();
            roundAnalysis.setShortComment(getLimitedString(result.get("shortComment"), fallback.getShortComment(), MAX_FEEDBACK_TEXT_CHARS));
            roundAnalysis.setFocus(getLimitedString(result.get("focus"), fallback.getFocus(), MAX_FEEDBACK_TEXT_CHARS));
            roundAnalysis.setScore(normalizeScore(result.get("score"), fallback.getScore()));
            roundAnalysis.setCommunicationScore(normalizeScore(result.get("communicationScore"), fallback.getCommunicationScore()));
            roundAnalysis.setTechnicalScore(normalizeScore(result.get("technicalScore"), fallback.getTechnicalScore()));
            roundAnalysis.setProblemSolvingScore(normalizeScore(result.get("problemSolvingScore"), fallback.getProblemSolvingScore()));
            roundAnalysis.setScoreReasons(getLimitedStringList(result.get("scoreReasons"),
                    fallback.getScoreReasons(), 3, MAX_LIST_ITEM_CHARS));
            roundAnalysis.setQuestionStyle(getLimitedString(result.get("questionStyle"), fallback.getQuestionStyle(), 40));
            roundAnalysis.setRecommendedAnswerSeconds(normalizeRecommendedAnswerSeconds(result.get("recommendedAnswerSeconds"),
                    fallback.getRecommendedAnswerSeconds()));
            roundAnalysis.setNextActionHint(getLimitedString(result.get("nextActionHint"), fallback.getNextActionHint(), MAX_FEEDBACK_TEXT_CHARS));
            roundAnalysis.setVerdict(getLimitedString(result.get("verdict"), fallback.getVerdict(), MAX_FEEDBACK_TEXT_CHARS));
            roundAnalysis.setImprovementTags(getLimitedStringList(result.get("improvementTags"),
                    fallback.getImprovementTags(), MAX_LIST_ITEMS, 32));
            roundAnalysis.setMissingPoints(getLimitedStringList(result.get("missingPoints"),
                    fallback.getMissingPoints(), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
            roundAnalysis.setAnswerRewriteSuggestion(getLimitedString(result.get("answerRewriteSuggestion"),
                    fallback.getAnswerRewriteSuggestion(), MAX_FEEDBACK_TEXT_CHARS));
            roundAnalysis.setFollowUpReason(getLimitedString(result.get("followUpReason"),
                    fallback.getFollowUpReason(), MAX_FEEDBACK_TEXT_CHARS));
            boolean shouldFinish = Boolean.parseBoolean(String.valueOf(result.get("shouldFinish")));
            boolean probe = canProbe && Boolean.parseBoolean(String.valueOf(result.get("probe")));
            String nextQuestion = normalizeNextQuestion(getString(result.get("nextQuestion"), fallback.getNextQuestion()),
                    fallback.getNextQuestion());
            roundAnalysis.setNextQuestion(ensureFreshNextQuestion(
                    nextQuestion,
                    latestQuestion,
                    mockInterview,
                    currentPlan,
                    nextPlan,
                    probe));
            roundAnalysis.setShouldFinish(shouldFinish);
            roundAnalysis.setProbe(probe);
            applyAnswerQualityGuardrails(roundAnalysis, mockInterview, latestAnswer);
            return roundAnalysis;
        } catch (Exception e) {
            RoundAnalysis fallback = buildRoundFallback(mockInterview, completedRounds, currentPlan, nextPlan);
            applyAnswerQualityGuardrails(fallback, mockInterview, latestAnswer);
            return fallback;
        }
    }

    private SummaryResult parseSummaryResult(String content, InterviewReport interviewReport) {
        try {
            Map<?, ?> result = JSONUtil.toBean(extractJsonContent(content), Map.class);
            SummaryResult fallback = buildSummaryFallback(interviewReport);
            SummaryResult summaryResult = new SummaryResult();
            summaryResult.setOverallScore(normalizeScore(result.get("overallScore"), fallback.getOverallScore()));
            summaryResult.setSummary(getLimitedString(result.get("summary"), fallback.getSummary(), MAX_SUMMARY_TEXT_CHARS));
            summaryResult.setStrengths(getLimitedStringList(result.get("strengths"),
                    fallback.getStrengths(), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
            summaryResult.setImprovements(getLimitedStringList(result.get("improvements"),
                    fallback.getImprovements(), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
            summaryResult.setSuggestedTopics(getLimitedStringList(result.get("suggestedTopics"),
                    fallback.getSuggestedTopics(), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
            summaryResult.setCommunicationScore(normalizeScore(result.get("communicationScore"), fallback.getCommunicationScore()));
            summaryResult.setTechnicalScore(normalizeScore(result.get("technicalScore"), fallback.getTechnicalScore()));
            summaryResult.setProblemSolvingScore(normalizeScore(result.get("problemSolvingScore"), fallback.getProblemSolvingScore()));
            summaryResult.setReadinessLevel(getLimitedString(result.get("readinessLevel"),
                    fallback.getReadinessLevel(), 120));
            summaryResult.setRecommendedNextAction(getLimitedString(result.get("recommendedNextAction"),
                    fallback.getRecommendedNextAction(), MAX_FEEDBACK_TEXT_CHARS));
            summaryResult.setPracticePlan(getLimitedStringList(result.get("practicePlan"),
                    fallback.getPracticePlan(), MAX_PRACTICE_PLAN_ITEMS, MAX_PRACTICE_PLAN_ITEM_CHARS));
            summaryResult.setDisplayText(getLimitedString(result.get("displayText"),
                    fallback.getDisplayText(), MAX_DISPLAY_TEXT_CHARS));
            if (!summaryResult.getDisplayText().startsWith("【面试结束】")) {
                summaryResult.setDisplayText("【面试结束】" + summaryResult.getDisplayText());
            }
            applySummaryCompletenessGuardrails(summaryResult, interviewReport);
            return summaryResult;
        } catch (Exception e) {
            SummaryResult fallback = buildSummaryFallback(interviewReport);
            applySummaryCompletenessGuardrails(fallback, interviewReport);
            return fallback;
        }
    }

    private void applySummaryCompletenessGuardrails(SummaryResult summaryResult, InterviewReport interviewReport) {
        if (summaryResult == null || interviewReport == null) {
            return;
        }
        int completedRounds = Math.max(0, interviewReport.getCompletedRounds() == null ? 0 : interviewReport.getCompletedRounds());
        int expectedRounds = Math.max(1, interviewReport.getExpectedRounds() == null ? DEFAULT_ROUNDS : interviewReport.getExpectedRounds());
        int minimumReliableRounds = Math.min(MIN_ROUNDS, expectedRounds);
        if (completedRounds <= 0) {
            summaryResult.setOverallScore(0);
            summaryResult.setCommunicationScore(0);
            summaryResult.setTechnicalScore(0);
            summaryResult.setProblemSolvingScore(0);
            summaryResult.setSummary("本次面试尚未产生有效作答，无法给出可信能力判断。建议至少完成 3 轮后再查看综合评分。");
            summaryResult.setStrengths(new ArrayList<>());
            summaryResult.setImprovements(new ArrayList<>(List.of("先完成至少 3 轮有效作答", "回答时补充职责、方案、指标和复盘")));
            summaryResult.setSuggestedTopics(new ArrayList<>(List.of("代表性项目介绍", "核心技术原理", "项目结果与复盘")));
            summaryResult.setReadinessLevel("尚未完成有效作答");
            summaryResult.setRecommendedNextAction("重新开始一场面试，至少完成 3 轮后再结束，这样复盘才有参考价值。");
            summaryResult.setPracticePlan(new ArrayList<>(List.of(
                    "先准备一个 2 分钟项目介绍，覆盖背景、职责、方案、结果和复盘",
                    "选择 3 个核心技术点，各准备一个项目里的真实使用案例",
                    "完成至少 3 轮模拟面试后再查看综合评分"
            )));
            summaryResult.setDisplayText("【面试结束】本次尚未完成有效作答，无法给出可信评分。建议重新开始并至少完成 3 轮回答。");
            return;
        }

        if (completedRounds < minimumReliableRounds) {
            capSummaryScores(summaryResult, 60);
            summaryResult.setSummary("本次面试完成轮次偏少，当前评分只适合作为早期参考。建议继续完成更多轮次后再判断稳定水平。"
                    + StringUtils.defaultString(summaryResult.getSummary()));
            summaryResult.setReadinessLevel("样本偏少，建议继续完成更多轮次");
            summaryResult.setRecommendedNextAction("建议至少完成 " + minimumReliableRounds + " 轮有效作答后再看综合评分，并优先补齐回答里的量化结果和技术取舍。");
            summaryResult.setImprovements(limitStringList(mergeStringList(
                    summaryResult.getImprovements(),
                    List.of("完成更多轮次以获得更可信评分")), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
            summaryResult.setPracticePlan(limitStringList(mergeStringList(
                    summaryResult.getPracticePlan(),
                    List.of("补完至少 " + minimumReliableRounds + " 轮模拟面试，并复盘每轮是否包含背景、职责、方案、结果")), MAX_PRACTICE_PLAN_ITEMS, MAX_PRACTICE_PLAN_ITEM_CHARS));
        } else if (completedRounds * 1.0 / expectedRounds < 0.6) {
            capSummaryScores(summaryResult, 75);
            summaryResult.setRecommendedNextAction("当前完成度不足六成，建议继续完成计划轮次，让评分覆盖更多题型和追问场景。");
        }
    }

    private void capSummaryScores(SummaryResult summaryResult, int scoreCap) {
        int safeCap = Math.max(0, Math.min(100, scoreCap));
        summaryResult.setOverallScore(Math.min(summaryResult.getOverallScore() == null ? safeCap : summaryResult.getOverallScore(), safeCap));
        summaryResult.setCommunicationScore(Math.min(summaryResult.getCommunicationScore() == null ? safeCap : summaryResult.getCommunicationScore(), safeCap));
        summaryResult.setTechnicalScore(Math.min(summaryResult.getTechnicalScore() == null ? safeCap : summaryResult.getTechnicalScore(), safeCap));
        summaryResult.setProblemSolvingScore(Math.min(summaryResult.getProblemSolvingScore() == null ? safeCap : summaryResult.getProblemSolvingScore(), safeCap));
    }

    private void applyAnswerQualityGuardrails(RoundAnalysis roundAnalysis,
                                              MockInterview mockInterview,
                                              String latestAnswer) {
        String answer = StringUtils.trimToEmpty(latestAnswer);
        if (roundAnalysis == null || StringUtils.isBlank(answer)) {
            return;
        }
        boolean hrInterview = "HR".equals(StringUtils.defaultString(mockInterview == null ? null : mockInterview.getInterviewType()));
        List<String> improvementTags = new ArrayList<>(roundAnalysis.getImprovementTags() == null
                ? Collections.emptyList()
                : roundAnalysis.getImprovementTags());
        List<String> missingPoints = new ArrayList<>(roundAnalysis.getMissingPoints() == null
                ? Collections.emptyList()
                : roundAnalysis.getMissingPoints());

        int scoreCap = 100;
        if (looksLikeNonAnswer(answer)) {
            scoreCap = Math.min(scoreCap, 48);
            appendDistinctStrings(improvementTags, List.of("补基础理解", "补验证方法"));
            appendDistinctStrings(missingPoints, List.of("没有说明已有理解", "缺少推理或验证路径"));
            roundAnalysis.setVerdict("当前回答还没有形成可判断的技术思路");
            roundAnalysis.setNextActionHint("请先说出你的初步理解、推理过程、验证方法和学习补齐计划。");
        } else {
            if (answer.length() < SHORT_ANSWER_THRESHOLD) {
                scoreCap = Math.min(scoreCap, 60);
                appendDistinctStrings(improvementTags, List.of("扩展回答结构"));
                appendDistinctStrings(missingPoints, List.of("回答信息量偏少"));
            }
            if (!hrInterview && !hasQuantitativeSignal(answer)) {
                scoreCap = Math.min(scoreCap, 82);
                appendDistinctStrings(improvementTags, List.of("补量化指标"));
                appendDistinctStrings(missingPoints, List.of("缺少可验证指标"));
            }
            if (!hasResponsibilitySignal(answer)) {
                scoreCap = Math.min(scoreCap, 78);
                appendDistinctStrings(improvementTags, List.of("补职责边界"));
                appendDistinctStrings(missingPoints, List.of("个人职责边界不清楚"));
            }
            if (!hasReasoningSignal(answer)) {
                scoreCap = Math.min(scoreCap, 78);
                appendDistinctStrings(improvementTags, List.of("补设计取舍"));
                appendDistinctStrings(missingPoints, List.of("技术取舍或推理链不足"));
            }
            if (!hrInterview && !hasReviewSignal(answer)) {
                scoreCap = Math.min(scoreCap, 85);
                appendDistinctStrings(improvementTags, List.of("补复盘风险"));
                appendDistinctStrings(missingPoints, List.of("缺少复盘或风险意识"));
            }
            if (looksUncertain(answer)) {
                scoreCap = Math.min(scoreCap, 68);
                appendDistinctStrings(improvementTags, List.of("补事实证据"));
                appendDistinctStrings(missingPoints, List.of("不确定表达较多，缺少事实支撑"));
            }
        }

        capRoundScores(roundAnalysis, scoreCap);
        List<String> answerQualitySignals = buildAnswerQualitySignalList(answer);
        if (answerQualitySignals.isEmpty()) {
            answerQualitySignals = new ArrayList<>(List.of("回答结构相对完整"));
        }
        if (roundAnalysis.getScoreReasons() == null || roundAnalysis.getScoreReasons().isEmpty()) {
            roundAnalysis.setScoreReasons(buildScoreReasonFallback(roundAnalysis, answerQualitySignals));
        }
        roundAnalysis.setAnswerQualitySignals(limitStringList(answerQualitySignals, MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
        roundAnalysis.setImprovementTags(limitStringList(improvementTags, MAX_LIST_ITEMS, 32));
        roundAnalysis.setMissingPoints(limitStringList(missingPoints, MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
    }

    private List<String> buildScoreReasonFallback(RoundAnalysis roundAnalysis, List<String> answerQualitySignals) {
        String signals = String.join("；", answerQualitySignals == null ? Collections.emptyList() : answerQualitySignals);
        if (StringUtils.isBlank(signals)) {
            signals = "回答结构相对完整";
        }
        return new ArrayList<>(List.of(
                "表达：" + (roundAnalysis.getCommunicationScore() == null ? "-" : roundAnalysis.getCommunicationScore()) + " 分，依据是" + signals,
                "技术：" + (roundAnalysis.getTechnicalScore() == null ? "-" : roundAnalysis.getTechnicalScore()) + " 分，重点看技术细节、边界和实践证据",
                "分析：" + (roundAnalysis.getProblemSolvingScore() == null ? "-" : roundAnalysis.getProblemSolvingScore()) + " 分，重点看推理链、取舍和验证方式"
        ));
    }

    private void capRoundScores(RoundAnalysis roundAnalysis, int scoreCap) {
        int safeCap = Math.max(0, Math.min(100, scoreCap));
        roundAnalysis.setScore(Math.min(roundAnalysis.getScore() == null ? safeCap : roundAnalysis.getScore(), safeCap));
        roundAnalysis.setCommunicationScore(Math.min(roundAnalysis.getCommunicationScore() == null
                ? safeCap
                : roundAnalysis.getCommunicationScore(), safeCap));
        roundAnalysis.setTechnicalScore(Math.min(roundAnalysis.getTechnicalScore() == null
                ? safeCap
                : roundAnalysis.getTechnicalScore(), safeCap));
        roundAnalysis.setProblemSolvingScore(Math.min(roundAnalysis.getProblemSolvingScore() == null
                ? safeCap
                : roundAnalysis.getProblemSolvingScore(), safeCap));
    }

    private RoundAnalysis buildElaborationAnalysis(MockInterview mockInterview,
                                                   InterviewPlanItem currentPlan,
                                                   int currentRound,
                                                   String userAnswer,
                                                   RoundAnalysis baseAnalysis) {
        boolean nonAnswer = looksLikeNonAnswer(userAnswer);
        RoundAnalysis roundAnalysis = new RoundAnalysis();
        roundAnalysis.setShortComment(nonAnswer
                ? "这轮回答出现了“不知道”或“不确定”表达，面试官会先确认你的理解和推理路径。"
                : "这轮回答还偏简略，面试官会继续追问你把关键细节讲完整。");
        roundAnalysis.setFocus(currentPlan == null ? "继续补充背景、方案和结果指标" : currentPlan.getFocusTopic() + "（补充细节）");
        roundAnalysis.setScore(Math.min(baseAnalysis.getScore(), 62));
        roundAnalysis.setCommunicationScore(Math.min(baseAnalysis.getCommunicationScore(), 60));
        roundAnalysis.setTechnicalScore(Math.min(baseAnalysis.getTechnicalScore(), 62));
        roundAnalysis.setProblemSolvingScore(Math.min(baseAnalysis.getProblemSolvingScore(), 60));
        roundAnalysis.setScoreReasons(nonAnswer
                ? new ArrayList<>(List.of(
                "表达：回答停留在不熟悉表达，缺少可继续判断的信息",
                "技术：没有给出概念理解、实现细节或项目证据",
                "分析：需要补充拆解思路、验证方法和学习补齐计划"))
                : new ArrayList<>(List.of(
                "表达：回答偏短，背景、职责和结果没有展开",
                "技术：关键方案、实现细节和边界条件不足",
                "分析：缺少为什么这样做、怎么验证和如何复盘的说明")));
        roundAnalysis.setQuestionStyle("追问补充");
        roundAnalysis.setRecommendedAnswerSeconds(Math.max(90, currentPlan == null ? 90 : currentPlan.getRecommendedAnswerSeconds()));
        roundAnalysis.setNextActionHint(nonAnswer
                ? "可以诚实说明不熟，但要补上你的理解、推理过程、验证方法和学习补齐计划。"
                : "请补充业务背景、你的职责、关键技术方案、量化结果以及复盘。");
        roundAnalysis.setVerdict(nonAnswer
                ? "当前回答还没有形成可判断的技术思路"
                : "这一轮回答偏简略，面试官还拿不到足够多的判断依据");
        roundAnalysis.setImprovementTags(nonAnswer
                ? new ArrayList<>(List.of("补基础理解", "补推理路径", "补验证方法", "补学习计划"))
                : new ArrayList<>(List.of("补背景", "补关键决策", "补量化结果", "补复盘")));
        roundAnalysis.setMissingPoints(nonAnswer
                ? new ArrayList<>(List.of("没有说明已有理解", "缺少推理或排查路径", "没有给出如何验证", "缺少补齐计划"))
                : new ArrayList<>(List.of("业务背景不清楚", "个人职责边界不清楚", "缺少量化结果", "缺少复盘")));
        roundAnalysis.setAnswerQualitySignals(limitStringList(buildAnswerQualitySignalList(userAnswer), MAX_LIST_ITEMS, MAX_LIST_ITEM_CHARS));
        roundAnalysis.setAnswerRewriteSuggestion(nonAnswer
                ? "可以按“这个点我不完全熟 -> 我的初步理解是 -> 我会先验证哪些假设 -> 落到项目会关注哪些风险 -> 后续怎么补齐”来回答。"
                : "请按“当时要解决什么问题 -> 我负责什么 -> 我怎么做 -> 最后指标变化 -> 复盘得到什么”补一版。");
        roundAnalysis.setFollowUpReason(nonAnswer
                ? "需要确认候选人在不熟悉问题下是否具备基本推理、拆解和学习能力。"
                : "当前回答信息量不足，需要先补齐事实依据再判断能力深度。");
        roundAnalysis.setNextQuestion(buildElaborationQuestion(mockInterview, currentPlan, currentRound, userAnswer));
        roundAnalysis.setShouldFinish(false);
        roundAnalysis.setProbe(true);
        return roundAnalysis;
    }

    private String buildElaborationQuestion(MockInterview mockInterview, InterviewPlanItem currentPlan, int currentRound, String userAnswer) {
        boolean nonAnswer = looksLikeNonAnswer(userAnswer);
        String systemPrompt = buildInterviewerPersona(mockInterview)
                + buildQuestionPolicy(mockInterview)
                + (nonAnswer
                ? "候选人表达了不知道或没做过。请输出一句自然的追问，要求候选人说明已有理解、推理路径或验证方法，不要输出 markdown。"
                : "候选人的回答偏简略。请输出一句自然的追问，让候选人补充关键细节，不要输出 markdown。");
        String userPrompt = String.format(
                "岗位：%s；面试类型：%s；当前轮次：%d；当前考察重点：%s；候选人刚才的回答：%s。"
                        + "请给出一个补充追问，要求：%s。",
                StringUtils.defaultIfBlank(mockInterview.getJobPosition(), "技术岗位"),
                StringUtils.defaultIfBlank(mockInterview.getInterviewType(), "技术深挖"),
                currentRound,
                currentPlan == null ? "项目细节" : currentPlan.getFocusTopic(),
                userAnswer,
                nonAnswer
                        ? "候选人说出自己的初步理解、会如何拆解问题、会用什么方式验证"
                        : "候选人把背景、方案、量化结果和复盘说完整");
        String fallback = nonAnswer
                ? "如果这个点你不熟，那你先说说你的初步理解，以及你会怎么验证自己的判断？"
                : "这部分我想继续深挖一下：这件事里你具体负责哪一块，最后用什么指标证明效果？";
        return normalizeNextQuestion(chatWithFallback(mockInterview, systemPrompt, userPrompt, fallback), fallback);
    }

    private boolean shouldForceElaboration(MockInterview mockInterview, String userAnswer, Integer score) {
        int answerLengthThreshold = getShortAnswerThreshold(mockInterview);
        int scoreThreshold = getLowScoreProbeThreshold(mockInterview);
        return looksLikeNonAnswer(userAnswer)
                || StringUtils.length(StringUtils.trimToEmpty(userAnswer)) < answerLengthThreshold
                || (score != null && score < scoreThreshold);
    }

    private boolean looksLikeNonAnswer(String userAnswer) {
        String normalized = StringUtils.trimToEmpty(userAnswer);
        return StringUtils.isNotBlank(normalized)
                && normalized.matches(".*(不会|不知道|不清楚|不了解|没接触|没做过|不太懂|答不上来).*");
    }

    private boolean looksUncertain(String userAnswer) {
        String normalized = StringUtils.trimToEmpty(userAnswer);
        return normalized.length() < 120
                && normalized.matches(".*(可能|大概|应该|也许|好像|不确定).*");
    }

    private String extractJsonContent(String content) {
        String text = StringUtils.trimToEmpty(content);
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").trim();
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String getString(Object value, String defaultValue) {
        String text = value == null ? null : String.valueOf(value).trim();
        return StringUtils.isBlank(text) ? defaultValue : text;
    }

    private List<String> getStringList(Object value, List<String> defaultValue) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String text = item == null ? null : String.valueOf(item).trim();
                if (StringUtils.isNotBlank(text)) {
                    result.add(text);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return new ArrayList<>(defaultValue == null ? Collections.emptyList() : defaultValue);
    }

    private String getLimitedString(Object value, String defaultValue, int maxChars) {
        return abbreviateText(getString(value, defaultValue), maxChars);
    }

    private List<String> getLimitedStringList(Object value, List<String> defaultValue, int maxItems, int maxChars) {
        return limitStringList(getStringList(value, defaultValue), maxItems, maxChars);
    }

    private List<String> limitStringList(List<String> source, int maxItems, int maxChars) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : source) {
            String text = abbreviateText(item, maxChars);
            if (StringUtils.isNotBlank(text) && !result.contains(text)) {
                result.add(text);
            }
            if (result.size() >= maxItems) {
                break;
            }
        }
        return result;
    }

    private int normalizeScore(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return Math.max(0, Math.min(100, number.intValue()));
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(value))));
        } catch (Exception e) {
            return Math.max(0, Math.min(100, defaultValue));
        }
    }

    private int normalizeRecommendedAnswerSeconds(Object value, Integer defaultValue) {
        int fallback = defaultValue == null ? 120 : defaultValue;
        if (value instanceof Number number) {
            return Math.max(45, Math.min(240, number.intValue()));
        }
        try {
            return Math.max(45, Math.min(240, Integer.parseInt(String.valueOf(value))));
        } catch (Exception e) {
            return Math.max(45, Math.min(240, fallback));
        }
    }

    private String normalizeNextQuestion(String question, String fallbackQuestion) {
        String normalized = StringUtils.trimToEmpty(question)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^[\\-•\\d.、)）\\s]+", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = StringUtils.defaultIfBlank(fallbackQuestion, "请继续结合一个真实项目，讲清楚你的方案、取舍和结果。");
        }
        int firstQuestionMark = findFirstQuestionMark(normalized);
        if (firstQuestionMark >= 0 && firstQuestionMark < normalized.length() - 1) {
            normalized = normalized.substring(0, firstQuestionMark + 1).trim();
        }
        if (normalized.length() > MAX_NEXT_QUESTION_CHARS) {
            normalized = normalized.substring(0, MAX_NEXT_QUESTION_CHARS - 1).trim() + "？";
        }
        return normalized;
    }

    private String ensureFreshNextQuestion(String nextQuestion,
                                           String latestQuestion,
                                           MockInterview mockInterview,
                                           InterviewPlanItem currentPlan,
                                           InterviewPlanItem nextPlan,
                                           boolean probe) {
        if (!isRepeatedQuestion(nextQuestion, latestQuestion)) {
            return nextQuestion;
        }
        InterviewPlanItem targetPlan = probe ? currentPlan : nextPlan;
        String topic = targetPlan == null
                ? StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getTechStack(), "这个场景")
                : targetPlan.getFocusTopic();
        String style = targetPlan == null ? "" : StringUtils.defaultString(targetPlan.getQuestionStyle());
        String fallback;
        if (probe) {
            fallback = String.format("换个角度追一下，围绕 %s，你当时最关键的判断依据是什么？",
                    StringUtils.defaultIfBlank(topic, "刚才这个回答"));
        } else if (style.contains("结果") || style.contains("复盘")) {
            fallback = "我们换到结果复盘上，这件事最后最能证明价值的指标是什么？";
        } else {
            fallback = buildFallbackNextQuestion(mockInterview, currentPlan, nextPlan);
        }
        return normalizeNextQuestion(fallback, "换个角度继续追问，你能补一个更具体的项目证据吗？");
    }

    private boolean isRepeatedQuestion(String nextQuestion, String latestQuestion) {
        String next = normalizeQuestionForCompare(nextQuestion);
        String latest = normalizeQuestionForCompare(latestQuestion);
        if (StringUtils.isAnyBlank(next, latest)) {
            return false;
        }
        if (next.equals(latest)) {
            return true;
        }
        int shorterLength = Math.min(next.length(), latest.length());
        return shorterLength >= 18 && (next.contains(latest) || latest.contains(next));
    }

    private String normalizeQuestionForCompare(String question) {
        return StringUtils.defaultString(question)
                .replaceAll("[\\s，。！？?、；;：:,.]", "")
                .trim();
    }

    private int findFirstQuestionMark(String text) {
        int chineseQuestionMarkIndex = text.indexOf('？');
        int englishQuestionMarkIndex = text.indexOf('?');
        if (chineseQuestionMarkIndex < 0) {
            return englishQuestionMarkIndex;
        }
        if (englishQuestionMarkIndex < 0) {
            return chineseQuestionMarkIndex;
        }
        return Math.min(chineseQuestionMarkIndex, englishQuestionMarkIndex);
    }

    private int averageRoundScore(InterviewReport report, Function<RoundRecord, Integer> getter, int defaultValue) {
        List<RoundRecord> roundRecords = report == null ? Collections.emptyList() : report.getRoundRecords();
        int sum = 0;
        int count = 0;
        for (RoundRecord roundRecord : roundRecords) {
            Integer score = getter.apply(roundRecord);
            if (score != null) {
                sum += score;
                count++;
            }
        }
        return count == 0 ? defaultValue : Math.max(0, Math.min(100, sum / count));
    }

    private String chatWithFallback(MockInterview mockInterview, String systemPrompt, String userPrompt, String fallback) {
        try {
            String content = aiManager.doChat(systemPrompt, userPrompt, mockInterview == null ? null : mockInterview.getUserId());
            return StringUtils.isBlank(content) ? fallback : content.trim();
        } catch (Exception e) {
            log.warn("模拟面试 AI 调用失败，使用兜底回复: " + e.getMessage());
            return fallback;
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(JSONUtil.toJsonStr(data)));
    }

    private LinkedHashMap<String, Object> buildStatusPayload(String phase, String message) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", phase);
        payload.put("message", message);
        return payload;
    }

    private InterviewMessage getLatestGeneratedAiMessage(List<InterviewMessage> beforeMessages,
                                                         List<InterviewMessage> afterMessages,
                                                         String fallbackContent) {
        int startIndex = Math.min(beforeMessages.size(), afterMessages.size());
        for (int i = afterMessages.size() - 1; i >= startIndex; i--) {
            InterviewMessage interviewMessage = afterMessages.get(i);
            if (interviewMessage.isAI) {
                return interviewMessage;
            }
        }
        for (int i = afterMessages.size() - 1; i >= 0; i--) {
            InterviewMessage interviewMessage = afterMessages.get(i);
            if (interviewMessage.isAI) {
                return interviewMessage;
            }
        }
        if (StringUtils.isBlank(fallbackContent)) {
            return null;
        }
        return new InterviewMessage(fallbackContent, true, System.currentTimeMillis(), null, "generated");
    }

    private void streamAssistantMessage(SseEmitter emitter, InterviewMessage interviewMessage) throws Exception {
        String content = StringUtils.defaultString(interviewMessage.content);
        StringBuilder accumulated = new StringBuilder();
        for (String chunk : splitForStreaming(content)) {
            accumulated.append(chunk);
            LinkedHashMap<String, Object> deltaPayload = new LinkedHashMap<>();
            deltaPayload.put("content", chunk);
            deltaPayload.put("accumulated", accumulated.toString());
            deltaPayload.put("round", interviewMessage.round);
            deltaPayload.put("stage", interviewMessage.stage);
            deltaPayload.put("timestamp", interviewMessage.timestamp);
            sendSseEvent(emitter, "delta", deltaPayload);
            Thread.sleep(30L);
        }
    }

    private List<String> splitForStreaming(String content) {
        if (StringUtils.isBlank(content)) {
            return Collections.singletonList("");
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char ch : content.toCharArray()) {
            current.append(ch);
            boolean reachBoundary = current.length() >= 18
                    || "。！？；，,.!?;\n".indexOf(ch) >= 0;
            if (reachBoundary) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? Collections.singletonList(content) : chunks;
    }

    private void appendMarkdownBullet(StringBuilder markdownBuilder, String label, String value) {
        markdownBuilder.append("- ")
                .append(label)
                .append("：")
                .append(sanitizeInlineMarkdown(value))
                .append('\n');
    }

    private void appendMarkdownStringList(StringBuilder markdownBuilder, String title, List<String> items) {
        markdownBuilder.append("\n### ").append(title).append("\n\n");
        if (items == null || items.isEmpty()) {
            markdownBuilder.append("- 暂无\n");
            return;
        }
        for (String item : items) {
            markdownBuilder.append("- ").append(sanitizeInlineMarkdown(item)).append('\n');
        }
    }

    private void appendMarkdownQuote(StringBuilder markdownBuilder, String content) {
        String[] lines = StringUtils.defaultString(content).split("\\R", -1);
        if (lines.length == 0) {
            markdownBuilder.append("> \n");
            return;
        }
        for (String line : lines) {
            markdownBuilder.append("> ").append(line).append('\n');
        }
    }

    private String sanitizeInlineMarkdown(String value) {
        return StringUtils.defaultString(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("|", "\\|")
                .trim();
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "-";
        }
        return formatDateTime(new Date(timestamp));
    }

    private List<InterviewPlanItem> buildInterviewAgenda(MockInterview mockInterview) {
        int rounds = normalizeExpectedRounds(mockInterview == null ? null : mockInterview.getExpectedRounds());
        String interviewType = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getInterviewType(), "技术深挖");
        String coreTopic = StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getTechStack(),
                StringUtils.defaultIfBlank(mockInterview == null ? null : mockInterview.getJobPosition(), "当前岗位"));
        List<InterviewPlanItem> agenda = new ArrayList<>();

        switch (interviewType) {
            case "项目拷打":
                agenda.add(new InterviewPlanItem(1, "项目背景", "项目背景、职责分工与业务目标", "项目开场", 120));
                agenda.add(new InterviewPlanItem(2, "方案拆解", coreTopic + " 方案设计与技术选型", "方案追问", 150));
                agenda.add(new InterviewPlanItem(3, "难点取舍", "关键难点、踩坑复盘与设计取舍", "难点深挖", 150));
                agenda.add(new InterviewPlanItem(4, "性能稳定性", "性能优化、监控告警与稳定性治理", "场景追问", 150));
                agenda.add(new InterviewPlanItem(5, "量化结果", "量化结果、业务价值和复盘提升", "结果复盘", 120));
                agenda.add(new InterviewPlanItem(6, "横向扩展", "如果继续扩容或复用到别的业务场景会怎么做", "延展追问", 120));
                agenda.add(new InterviewPlanItem(7, "协作推进", "团队协作、跨部门推进和沟通方式", "协作追问", 90));
                agenda.add(new InterviewPlanItem(8, "压力追问", "高压情况下的优先级与决策判断", "压力测试", 90));
                break;
            case "系统设计":
                agenda.add(new InterviewPlanItem(1, "需求澄清", "需求澄清、边界定义与核心目标", "需求澄清", 90));
                agenda.add(new InterviewPlanItem(2, "总体架构", coreTopic + " 场景下的总体架构设计", "架构设计", 180));
                agenda.add(new InterviewPlanItem(3, "数据模型", "数据流、存储模型与关键接口设计", "数据设计", 150));
                agenda.add(new InterviewPlanItem(4, "高并发扩展", "扩容、热点、缓存与并发控制", "性能扩展", 150));
                agenda.add(new InterviewPlanItem(5, "可用性容灾", "高可用、降级、容灾与监控方案", "稳定性设计", 150));
                agenda.add(new InterviewPlanItem(6, "安全合规", "权限、安全、审计与风控考虑", "安全设计", 120));
                agenda.add(new InterviewPlanItem(7, "成本取舍", "成本、复杂度与上线节奏的平衡", "取舍追问", 120));
                agenda.add(new InterviewPlanItem(8, "复盘提升", "如果重做一遍会如何优化设计", "复盘追问", 90));
                break;
            case "HR":
                agenda.add(new InterviewPlanItem(1, "自我介绍", "自我介绍与关键经历概览", "开场介绍", 90));
                agenda.add(new InterviewPlanItem(2, "求职动机", "选择岗位和公司的动机", "动机追问", 90));
                agenda.add(new InterviewPlanItem(3, "项目亮点", "最有成就感的项目与个人贡献", "经历深挖", 120));
                agenda.add(new InterviewPlanItem(4, "冲突协作", "协作冲突和沟通推进方式", "行为面试", 120));
                agenda.add(new InterviewPlanItem(5, "压力应对", "高压任务下的决策与抗压方式", "压力行为题", 90));
                agenda.add(new InterviewPlanItem(6, "成长复盘", "失败经历、复盘方式与成长变化", "复盘题", 90));
                agenda.add(new InterviewPlanItem(7, "职业规划", "未来 2 到 3 年的职业规划", "规划题", 90));
                agenda.add(new InterviewPlanItem(8, "反问意识", "岗位匹配度与反问能力", "收尾题", 60));
                break;
            default:
                agenda.add(new InterviewPlanItem(1, "自我介绍", "自我介绍与代表性项目背景", "开场介绍", 90));
                agenda.add(new InterviewPlanItem(2, "核心原理", coreTopic + " 的核心原理理解", "原理追问", 120));
                agenda.add(new InterviewPlanItem(3, "场景分析", coreTopic + " 在真实业务场景中的落地方式", "场景追问", 150));
                agenda.add(new InterviewPlanItem(4, "性能优化", "性能瓶颈定位与优化策略", "性能追问", 150));
                agenda.add(new InterviewPlanItem(5, "稳定性治理", "异常处理、稳定性与线上故障排查", "稳定性追问", 150));
                agenda.add(new InterviewPlanItem(6, "系统设计延展", "如果规模继续扩大该怎么设计", "设计延展", 150));
                agenda.add(new InterviewPlanItem(7, "压测复盘", "压测结果、容量预估和上线复盘", "压测复盘", 120));
                agenda.add(new InterviewPlanItem(8, "综合追问", "综合判断与取舍复盘", "综合追问", 90));
                break;
        }
        return new ArrayList<>(agenda.subList(0, rounds));
    }

    private List<InterviewPlanItem> normalizeAgenda(List<InterviewPlanItem> agenda, MockInterview mockInterview) {
        if (agenda == null || agenda.isEmpty()) {
            return buildInterviewAgenda(mockInterview);
        }
        int rounds = normalizeExpectedRounds(mockInterview == null ? null : mockInterview.getExpectedRounds());
        List<InterviewPlanItem> normalized = new ArrayList<>(agenda);
        if (normalized.size() < rounds) {
            List<InterviewPlanItem> rebuilt = buildInterviewAgenda(mockInterview);
            normalized = new ArrayList<>(rebuilt.subList(0, rounds));
        } else if (normalized.size() > rounds) {
            normalized = new ArrayList<>(normalized.subList(0, rounds));
        }
        return normalized;
    }

    private InterviewPlanItem getPlanItem(InterviewReport interviewReport, int round) {
        if (interviewReport == null || interviewReport.getAgenda() == null || round <= 0 || round > interviewReport.getAgenda().size()) {
            return null;
        }
        return interviewReport.getAgenda().get(round - 1);
    }

    private InterviewPlanItem getPlanByRound(MockInterview mockInterview, int round) {
        if (round <= 0 || round > getExpectedRounds(mockInterview)) {
            return null;
        }
        List<InterviewPlanItem> agenda = buildInterviewAgenda(mockInterview);
        return round <= agenda.size() ? agenda.get(round - 1) : null;
    }

    private String buildTranscriptionPrompt(MockInterview mockInterview) {
        StringBuilder promptBuilder = new StringBuilder("请把这段中文模拟面试回答准确转写成简洁文本，保留技术名词、英文缩写、数字指标和中英文混排。");
        if (mockInterview != null) {
            if (StringUtils.isNotBlank(mockInterview.getJobPosition())) {
                promptBuilder.append(" 当前岗位：").append(mockInterview.getJobPosition()).append("。");
            }
            if (StringUtils.isNotBlank(mockInterview.getInterviewType())) {
                promptBuilder.append(" 面试类型：").append(mockInterview.getInterviewType()).append("。");
            }
            if (StringUtils.isNotBlank(mockInterview.getTechStack())) {
                promptBuilder.append(" 技术方向：").append(mockInterview.getTechStack()).append("。");
            }
        }
        promptBuilder.append(" 不要补充解释，不要总结，只返回转写结果。");
        return promptBuilder.toString();
    }

    private String sanitizeTranscript(String transcript) {
        if (StringUtils.isBlank(transcript)) {
            return "";
        }
        return transcript
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String buildNextActionHint(InterviewPlanItem planItem) {
        if (planItem == null) {
            return "这一轮结束后会直接进入最终总结，请把重点讲清楚。";
        }
        return switch (StringUtils.defaultIfBlank(planItem.getQuestionStyle(), "")) {
            case "架构设计" -> "建议你先说整体架构，再补充核心模块、数据流和扩展思路。";
            case "原理追问" -> "建议从原理、适用场景、优缺点和线上经验四个层面回答。";
            case "行为面试", "压力行为题" -> "建议用背景、行动、结果、复盘的结构来回答。";
            default -> "建议把背景、方案、结果和复盘都讲完整，尽量给出量化指标。";
        };
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InterviewMessage {
        private String content;
        private boolean isAI;
        private long timestamp;
        private Integer round;
        private String stage;
    }

    @Data
    @NoArgsConstructor
    private static class RoundAnalysis {
        private String shortComment;
        private String focus;
        private Integer score;
        private Integer communicationScore;
        private Integer technicalScore;
        private Integer problemSolvingScore;
        private List<String> scoreReasons;
        private String questionStyle;
        private Integer recommendedAnswerSeconds;
        private String nextActionHint;
        private String verdict;
        private List<String> improvementTags;
        private List<String> missingPoints;
        private List<String> answerQualitySignals;
        private String answerRewriteSuggestion;
        private String followUpReason;
        private String nextQuestion;
        private boolean shouldFinish;
        private boolean probe;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RoundRecord {
        private Integer round;
        private String question;
        private String answer;
        private String shortComment;
        private String focus;
        private Integer score;
        private Integer communicationScore;
        private Integer technicalScore;
        private Integer problemSolvingScore;
        private List<String> scoreReasons;
        private String questionStyle;
        private Integer recommendedAnswerSeconds;
        private Integer responseSeconds;
        private String verdict;
        private List<String> improvementTags;
        private List<String> missingPoints;
        private List<String> answerQualitySignals;
        private String answerRewriteSuggestion;
        private String followUpReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InterviewPlanItem {
        private Integer round;
        private String label;
        private String focusTopic;
        private String questionStyle;
        private Integer recommendedAnswerSeconds;
    }

    @Data
    @NoArgsConstructor
    private static class InterviewReport {
        private Integer expectedRounds;
        private Integer completedRounds;
        private Integer overallScore;
        private String summary;
        private Integer communicationScore;
        private Integer technicalScore;
        private Integer problemSolvingScore;
        private String currentFocus;
        private String currentQuestionStyle;
        private Integer recommendedAnswerSeconds;
        private String nextActionHint;
        private List<String> strengths;
        private List<String> improvements;
        private List<String> suggestedTopics;
        private List<String> practicePlan;
        private List<RoundRecord> roundRecords;
        private List<InterviewPlanItem> agenda;
        private List<String> answerChecklist;
        private String readinessLevel;
        private String recommendedNextAction;
    }

    @Data
    @NoArgsConstructor
    private static class SummaryResult {
        private Integer overallScore;
        private String summary;
        private Integer communicationScore;
        private Integer technicalScore;
        private Integer problemSolvingScore;
        private List<String> strengths;
        private List<String> improvements;
        private List<String> suggestedTopics;
        private List<String> practicePlan;
        private String readinessLevel;
        private String recommendedNextAction;
        private String displayText;
    }
}
