package com.xduo.springbootinit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.dto.adminoperationlog.AdminOperationLogQueryRequest;
import com.xduo.springbootinit.model.entity.AdminOperationLog;
import com.xduo.springbootinit.service.AdminOperationLogService;
import com.xduo.springbootinit.utils.SqlUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Admin operation log APIs.
 */
@RestController
@RequestMapping("/admin/log")
@Slf4j
public class AdminOperationLogController {

    @Resource
    private AdminOperationLogService adminOperationLogService;

    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminOperationLog>> listAdminOperationLogByPage(@RequestBody AdminOperationLogQueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        if (current <= 0 || size <= 0 || size > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }

        QueryWrapper<AdminOperationLog> queryWrapper = buildQueryWrapper(queryRequest);
        Page<AdminOperationLog> logPage = adminOperationLogService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(logPage);
    }

    @PostMapping("/export")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public void exportAdminOperationLog(@RequestBody(required = false) AdminOperationLogQueryRequest queryRequest,
                                        HttpServletResponse response) throws IOException {
        AdminOperationLogQueryRequest safeQuery = queryRequest == null ? new AdminOperationLogQueryRequest() : queryRequest;
        QueryWrapper<AdminOperationLog> queryWrapper = buildQueryWrapper(safeQuery);
        queryWrapper.last("limit 1000");

        String fileName = URLEncoder.encode("admin-operation-log.csv", StandardCharsets.UTF_8);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        StringBuilder csvBuilder = new StringBuilder("\uFEFF");
        csvBuilder.append("ID,操作员ID,操作员名称,操作描述,方法名,IP地址,操作时间,请求参数\n");
        adminOperationLogService.list(queryWrapper).forEach(item -> csvBuilder
                .append(csvEscape(item.getId()))
                .append(',')
                .append(csvEscape(item.getUserId()))
                .append(',')
                .append(csvEscape(item.getUserName()))
                .append(',')
                .append(csvEscape(item.getOperation()))
                .append(',')
                .append(csvEscape(item.getMethod()))
                .append(',')
                .append(csvEscape(item.getIp()))
                .append(',')
                .append(csvEscape(formatDateTime(item.getCreateTime())))
                .append(',')
                .append(csvEscape(item.getParams()))
                .append('\n'));
        response.getWriter().write(csvBuilder.toString());
        response.getWriter().flush();
    }

    private QueryWrapper<AdminOperationLog> buildQueryWrapper(AdminOperationLogQueryRequest queryRequest) {
        QueryWrapper<AdminOperationLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getUserName()), "userName", queryRequest.getUserName());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getOperation()), "operation", queryRequest.getOperation());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getMethod()), "method", queryRequest.getMethod());
        queryWrapper.like(StringUtils.isNotBlank(queryRequest.getIp()), "ip", queryRequest.getIp());
        Date startTime = parseDate(queryRequest.getStartTime(), false);
        boolean endDateOnly = isDateOnly(queryRequest.getEndTime());
        Date endTime = parseDate(queryRequest.getEndTime(), endDateOnly);
        queryWrapper.ge(startTime != null, "createTime", startTime);
        if (endTime != null) {
            if (endDateOnly) {
                queryWrapper.lt("createTime", endTime);
            } else {
                queryWrapper.le("createTime", endTime);
            }
        }
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        if (SqlUtils.validSortField(sortField)) {
            queryWrapper.orderBy(true, CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    private Date parseDate(String dateStr, boolean endOfDateRange) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        String trimDateStr = dateStr.trim();
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
            dateFormat.setLenient(false);
            ParsePosition parsePosition = new ParsePosition(0);
            Date parsedDate = dateFormat.parse(trimDateStr, parsePosition);
            if (parsedDate != null && parsePosition.getIndex() == trimDateStr.length()) {
                if (endOfDateRange && "yyyy-MM-dd".equals(pattern)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(parsedDate);
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    return calendar.getTime();
                }
                return parsedDate;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间格式错误");
    }

    private boolean isDateOnly(String dateStr) {
        return StringUtils.isNotBlank(dateStr) && dateStr.trim().matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String csvEscape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        text = neutralizeCsvFormula(text);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String neutralizeCsvFormula(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        String trimmed = StringUtils.stripStart(text, null);
        if (StringUtils.isBlank(trimmed)) {
            return text;
        }
        char firstChar = trimmed.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
            return "'" + text;
        }
        return text;
    }
}
