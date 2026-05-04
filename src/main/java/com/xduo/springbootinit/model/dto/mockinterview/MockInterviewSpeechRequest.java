package com.xduo.springbootinit.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试语音播报请求
 */
@Data
public class MockInterviewSpeechRequest implements Serializable {

    private Long id;

    private String text;

    private static final long serialVersionUID = 1L;
}
