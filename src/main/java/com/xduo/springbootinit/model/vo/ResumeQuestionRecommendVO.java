package com.xduo.springbootinit.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 简历驱动推荐结果
 */
@Data
public class ResumeQuestionRecommendVO implements Serializable {

    /**
     * 岗位方向
     */
    private String jobDirection;

    /**
     * 识别出的技能标签
     */
    private List<String> extractedTags;

    /**
     * 分析摘要
     */
    private String analysisSummary;

    /**
     * 解析后的简历文本
     */
    private String resumeText;

    /**
     * 推荐补强方向
     */
    private String recommendFocus;

    /**
     * 分析来源
     */
    private String analysisSource;

    /**
     * 推荐题目
     */
    private List<QuestionVO> questionList;

    private static final long serialVersionUID = 1L;
}
