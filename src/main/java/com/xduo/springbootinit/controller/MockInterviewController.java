package com.xduo.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.DeleteRequest;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.model.dto.mockinterview.MockInterviewAddRequest;
import com.xduo.springbootinit.model.dto.mockinterview.MockInterviewEventRequest;
import com.xduo.springbootinit.model.dto.mockinterview.MockInterviewQueryRequest;
import com.xduo.springbootinit.model.entity.MockInterview;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.MockInterviewService;
import com.xduo.springbootinit.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟面试接口
 */
@RestController
@RequestMapping("/mockInterview")
public class MockInterviewController {

    @Resource
    private MockInterviewService mockInterviewService;

    @Resource
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addMockInterview(@RequestBody MockInterviewAddRequest addRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = new MockInterview();
        BeanUtils.copyProperties(addRequest, mockInterview);
        mockInterviewService.validMockInterview(mockInterview, true);
        mockInterview.setUserId(loginUser.getId());
        mockInterview.setStatus(0);
        mockInterview.setCurrentRound(0);
        mockInterview.setMessages("[]");
        mockInterview.setReport(null);
        boolean result = mockInterviewService.save(mockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(mockInterview.getId());
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteMockInterview(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview oldMockInterview = getOwnedMockInterview(deleteRequest.getId(), loginUser, request);
        boolean result = mockInterviewService.removeById(oldMockInterview.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<MockInterview> getMockInterviewById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = getOwnedMockInterview(id, loginUser, request);
        return ResultUtils.success(mockInterview);
    }

    @PostMapping("/handleEvent")
    public BaseResponse<String> handleMockInterviewEvent(@RequestBody MockInterviewEventRequest eventRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(eventRequest == null || eventRequest.getId() == null || eventRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = getOwnedMockInterview(eventRequest.getId(), loginUser, request);
        String result = mockInterviewService.handleInterviewEvent(mockInterview, eventRequest.getEvent(), eventRequest.getMessage());
        return ResultUtils.success(result);
    }

    @PostMapping(value = "/stream/handleEvent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMockInterviewEvent(@RequestBody MockInterviewEventRequest eventRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(eventRequest == null || eventRequest.getId() == null || eventRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = getOwnedMockInterview(eventRequest.getId(), loginUser, request);
        return mockInterviewService.streamInterviewEvent(mockInterview, eventRequest.getEvent(), eventRequest.getMessage());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMockInterview(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = getOwnedMockInterview(id, loginUser, request);
        String markdown = mockInterviewService.exportInterviewReview(mockInterview);
        String encodedFileName = URLEncoder.encode(String.format("mock-interview-%d-review.md", id), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<String> transcribeMockInterviewAudio(@RequestParam("id") Long id,
                                                             @RequestPart("file") MultipartFile audioFile,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(audioFile == null || audioFile.isEmpty(), ErrorCode.PARAMS_ERROR, "请先录制语音后再转写");
        User loginUser = userService.getLoginUser(request);
        MockInterview mockInterview = getOwnedMockInterview(id, loginUser, request);
        String transcript = mockInterviewService.transcribeInterviewAudio(mockInterview, audioFile);
        return ResultUtils.success(transcript);
    }

    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<MockInterview>> listMockInterviewByPage(@RequestBody MockInterviewQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        validatePageRequest(queryRequest.getCurrent(), queryRequest.getPageSize());
        Page<MockInterview> page = mockInterviewService.page(new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()),
                mockInterviewService.getQueryWrapper(queryRequest));
        compactPageForList(page);
        return ResultUtils.success(page);
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<MockInterview>> listMyMockInterviewByPage(@RequestBody MockInterviewQueryRequest queryRequest,
                                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        validatePageRequest(queryRequest.getCurrent(), queryRequest.getPageSize());
        User loginUser = userService.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());
        Page<MockInterview> page = mockInterviewService.page(new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()),
                mockInterviewService.getQueryWrapper(queryRequest));
        compactPageForList(page);
        return ResultUtils.success(page);
    }

    private void validatePageRequest(long current, long pageSize) {
        ThrowUtils.throwIf(current < 1 || pageSize < 1 || pageSize > 50,
                ErrorCode.PARAMS_ERROR, "分页参数不合法");
    }

    private MockInterview getOwnedMockInterview(Long id, User loginUser, HttpServletRequest request) {
        MockInterview mockInterview = mockInterviewService.getById(id);
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.NOT_FOUND_ERROR);
        if (!mockInterview.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return mockInterview;
    }

    private void compactPageForList(Page<MockInterview> page) {
        List<MockInterview> records = page == null ? null : page.getRecords();
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MockInterview record : records) {
            if (record == null) {
                continue;
            }
            record.setMessages(null);
            record.setResumeText(null);
            record.setReport(compactReport(record.getReport()));
        }
    }

    private String compactReport(String report) {
        if (report == null || report.isBlank()) {
            return report;
        }
        try {
            Map<String, Object> reportMap = JSONUtil.toBean(report, LinkedHashMap.class);
            reportMap.remove("roundRecords");
            reportMap.remove("agenda");
            return JSONUtil.toJsonStr(reportMap);
        } catch (Exception e) {
            return report;
        }
    }
}
