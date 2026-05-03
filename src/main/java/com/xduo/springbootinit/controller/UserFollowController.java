package com.xduo.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.model.dto.userfollow.UserFollowQueryRequest;
import com.xduo.springbootinit.model.dto.userfollow.UserFollowRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.UserFollowService;
import com.xduo.springbootinit.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户关注关系接口
 */
@RestController
@RequestMapping("/user_follow")
public class UserFollowController {

    private static final List<String> DEFAULT_PROFILE_VISIBLE_FIELDS = List.of(
            "profile",
            "city",
            "career",
            "tags",
            "joinTime",
            "stats",
            "activity",
            "content",
            "relation",
            "relationList"
    );

    @Resource
    private UserFollowService userFollowService;

    @Resource
    private UserService userService;

    /**
     * 关注用户
     */
    @PostMapping("/follow")
    public BaseResponse<Boolean> followUser(@RequestBody UserFollowRequest userFollowRequest, HttpServletRequest request) {
        long followUserId = getFollowUserId(userFollowRequest);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userFollowService.followUser(loginUser.getId(), followUserId));
    }

    /**
     * 取消关注用户
     */
    @PostMapping("/unfollow")
    public BaseResponse<Boolean> unfollowUser(@RequestBody UserFollowRequest userFollowRequest, HttpServletRequest request) {
        long followUserId = getFollowUserId(userFollowRequest);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userFollowService.unfollowUser(loginUser.getId(), followUserId));
    }

    /**
     * 分页获取粉丝列表
     */
    @PostMapping("/follower/list/page/vo")
    public BaseResponse<Page<UserVO>> listFollowerUserVOByPage(@RequestBody UserFollowQueryRequest userFollowQueryRequest,
                                                               HttpServletRequest httpServletRequest) {
        UserFollowQueryRequest request = validateListRequest(userFollowQueryRequest);
        User loginUser = userService.getLoginUserPermitNull(httpServletRequest);
        ensureRelationListVisible(request.getUserId(), loginUser == null ? null : loginUser.getId());
        Page<UserVO> page = userFollowService.listFollowerUserVOByPage(
                request.getUserId(),
                request.getCurrent(),
                request.getPageSize(),
                loginUser == null ? null : loginUser.getId()
        );
        return ResultUtils.success(page);
    }

    /**
     * 分页获取关注列表
     */
    @PostMapping("/following/list/page/vo")
    public BaseResponse<Page<UserVO>> listFollowingUserVOByPage(@RequestBody UserFollowQueryRequest userFollowQueryRequest,
                                                                HttpServletRequest httpServletRequest) {
        UserFollowQueryRequest request = validateListRequest(userFollowQueryRequest);
        User loginUser = userService.getLoginUserPermitNull(httpServletRequest);
        ensureRelationListVisible(request.getUserId(), loginUser == null ? null : loginUser.getId());
        Page<UserVO> page = userFollowService.listFollowingUserVOByPage(
                request.getUserId(),
                request.getCurrent(),
                request.getPageSize(),
                loginUser == null ? null : loginUser.getId()
        );
        return ResultUtils.success(page);
    }

    private long getFollowUserId(UserFollowRequest userFollowRequest) {
        if (userFollowRequest == null || userFollowRequest.getFollowUserId() == null || userFollowRequest.getFollowUserId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userFollowRequest.getFollowUserId();
    }

    private UserFollowQueryRequest validateListRequest(UserFollowQueryRequest userFollowQueryRequest) {
        ThrowUtils.throwIf(userFollowQueryRequest == null || userFollowQueryRequest.getUserId() == null || userFollowQueryRequest.getUserId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(userFollowQueryRequest.getCurrent() < 1, ErrorCode.PARAMS_ERROR, "页码不合法");
        ThrowUtils.throwIf(userFollowQueryRequest.getPageSize() < 1 || userFollowQueryRequest.getPageSize() > 20, ErrorCode.PARAMS_ERROR, "每页数量需在 1 到 20 之间");
        return userFollowQueryRequest;
    }

    private void ensureRelationListVisible(long userId, Long loginUserId) {
        if (loginUserId != null && loginUserId.equals(userId)) {
            return;
        }
        User targetUser = userService.getById(userId);
        ThrowUtils.throwIf(targetUser == null || UserConstant.BAN_ROLE.equals(targetUser.getUserRole()),
                ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        List<String> visibleFieldList = parseProfileVisibleFieldList(targetUser.getProfileVisibleFields());
        ThrowUtils.throwIf(!visibleFieldList.contains("relationList"),
                ErrorCode.NO_AUTH_ERROR, "对方未公开关注关系列表");
    }

    private List<String> parseProfileVisibleFieldList(String profileVisibleFields) {
        if (StringUtils.isBlank(profileVisibleFields)) {
            return DEFAULT_PROFILE_VISIBLE_FIELDS;
        }
        try {
            Set<String> requestedFieldSet = JSONUtil.toList(profileVisibleFields, String.class).stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return DEFAULT_PROFILE_VISIBLE_FIELDS.stream()
                    .filter(requestedFieldSet::contains)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return DEFAULT_PROFILE_VISIBLE_FIELDS;
        }
    }
}
