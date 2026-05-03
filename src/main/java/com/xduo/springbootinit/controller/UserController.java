package com.xduo.springbootinit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONUtil;
import com.xduo.springbootinit.common.BaseResponse;
import com.xduo.springbootinit.common.DeleteRequest;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.common.ResultUtils;
import com.xduo.springbootinit.constant.QuestionBankConstant;
import com.xduo.springbootinit.constant.QuestionConstant;
import com.xduo.springbootinit.constant.UserConstant;
import com.xduo.springbootinit.annotation.RateLimit;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.exception.ThrowUtils;
import com.xduo.springbootinit.model.dto.user.UserAddRequest;
import com.xduo.springbootinit.model.dto.user.UserInterestTagsMergeRequest;
import com.xduo.springbootinit.model.dto.user.UserLoginRequest;
import com.xduo.springbootinit.model.dto.user.UserQueryRequest;
import com.xduo.springbootinit.model.dto.user.UserRegisterRequest;
import com.xduo.springbootinit.model.dto.user.UserUpdateMyRequest;
import com.xduo.springbootinit.model.dto.user.UserUpdateRequest;
import com.xduo.springbootinit.model.entity.QuestionBank;
import com.xduo.springbootinit.model.entity.Question;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.entity.UserQuestionHistory;
import com.xduo.springbootinit.model.vo.LoginUserVO;
import com.xduo.springbootinit.model.vo.UserActivityVO;
import com.xduo.springbootinit.model.vo.UserProfileVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.service.QuestionBankService;
import com.xduo.springbootinit.service.QuestionService;
import com.xduo.springbootinit.service.TagSyncService;
import com.xduo.springbootinit.service.UserFollowService;
import com.xduo.springbootinit.service.UserQuestionHistoryService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.CityUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.xduo.springbootinit.service.impl.UserServiceImpl.SALT;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

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
    private UserService userService;

    @Resource
    private TagSyncService tagSyncService;

    @Resource
    private UserQuestionHistoryService userQuestionHistoryService;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private UserFollowService userFollowService;

    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest, HttpServletRequest request) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword, request);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, userLoginRequest.getCaptcha(), userLoginRequest.getCaptchaUuid(), request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 发送验证码
     *
     * @param userSendCodeRequest
     * @param request
     * @return
     */
    @PostMapping("/send_code")
    public BaseResponse<Boolean> sendVerificationCode(@RequestBody com.xduo.springbootinit.model.dto.user.UserSendCodeRequest userSendCodeRequest,
                                                        HttpServletRequest request) {
        if (userSendCodeRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        userService.sendVerificationCode(userSendCodeRequest, request);
        return ResultUtils.success(true);
    }

    /**
     * 验证码登录/注册
     *
     * @param userCodeLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login/code")
    public BaseResponse<LoginUserVO> userCodeLogin(@RequestBody com.xduo.springbootinit.model.dto.user.UserCodeLoginRequest userCodeLoginRequest,
                                                    HttpServletRequest request) {
        if (userCodeLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userCodeLogin(userCodeLoginRequest, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    @RateLimit(key = "user:getLogin", maxRequests = 600, windowSeconds = 60, message = "请求过于频繁，请稍后再试")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUserPermitNull(request);
        if (user == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * @param userAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        String userAccount = StringUtils.trimToNull(userAddRequest.getUserAccount());
        ThrowUtils.throwIf(userAccount == null, ErrorCode.PARAMS_ERROR, "账号不能为空");
        userService.checkUserAccountUnique(userAccount, null);
        user.setUserAccount(userAccount);
        String userPassword = StringUtils.trimToNull(userAddRequest.getUserPassword());
        ThrowUtils.throwIf(userPassword == null, ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码至少 8 位");
        String userName = normalizeOptionalUserName(userAddRequest.getUserName());
        userService.checkUserNameUnique(userName, null);
        user.setUserName(userName);
        user.setUserAvatar(StringUtils.trimToNull(userAddRequest.getUserAvatar()));
        user.setCity(normalizeOptionalSupportedCity(userAddRequest.getCity(), false));
        user.setCareerDirection(normalizeCareerDirection(userAddRequest.getCareerDirection()));
        user.setInterestTags(normalizeInterestTags(userAddRequest.getInterestTags()));
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        user.setUserPassword(encryptPassword);
        user.setPasswordConfigured(1);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        tagSyncService.syncInterestTags(null, user.getInterestTags());
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser.getId().equals(deleteRequest.getId()), ErrorCode.OPERATION_ERROR, "不支持删除当前登录账号");
        User targetUser = userService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        if (UserConstant.ADMIN_ROLE.equals(targetUser.getUserRole())) {
            QueryWrapper<User> adminQueryWrapper = new QueryWrapper<>();
            adminQueryWrapper.eq("userRole", UserConstant.ADMIN_ROLE);
            ThrowUtils.throwIf(userService.count(adminQueryWrapper) <= 1, ErrorCode.OPERATION_ERROR, "至少保留一个管理员账号");
        }
        boolean b = userService.removeById(deleteRequest.getId());
        if (b) {
            tagSyncService.syncInterestTags(targetUser.getInterestTags(), null);
        }
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                            HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User oldUser = userService.getById(userUpdateRequest.getId());
        ThrowUtils.throwIf(oldUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        if (userUpdateRequest.getUserAccount() != null) {
            String userAccount = StringUtils.trimToNull(userUpdateRequest.getUserAccount());
            ThrowUtils.throwIf(userAccount == null, ErrorCode.PARAMS_ERROR, "账号不能为空");
            userService.checkUserAccountUnique(userAccount, userUpdateRequest.getId());
            user.setUserAccount(userAccount);
        }
        if (userUpdateRequest.getUserName() != null) {
            String userName = normalizeOptionalUserName(userUpdateRequest.getUserName());
            userService.checkUserNameUnique(userName, userUpdateRequest.getId());
            user.setUserName(userName);
        }
        user.setCity(normalizeOptionalSupportedCity(userUpdateRequest.getCity(), false));
        user.setCareerDirection(normalizeCareerDirection(userUpdateRequest.getCareerDirection()));
        user.setInterestTags(normalizeInterestTags(userUpdateRequest.getInterestTags()));
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        User latestUser = userService.getById(userUpdateRequest.getId());
        tagSyncService.syncInterestTags(oldUser.getInterestTags(), latestUser == null ? null : latestUser.getInterestTags());
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    @RateLimit(key = "user:getVO", maxRequests = 600, windowSeconds = 60)
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        User user = getPublicUserById(id);
        UserVO userVO = userService.getUserVO(user);
        applyProfileVisibility(userVO, parseProfileVisibleFieldList(user.getProfileVisibleFields()));
        return ResultUtils.success(userVO);
    }

    /**
     * 获取公开用户主页摘要
     *
     * @param id 用户 id
     * @return 公开资料与学习摘要
     */
    @GetMapping("/profile/vo")
    @RateLimit(key = "user:profileVO", maxRequests = 300, windowSeconds = 60)
    public BaseResponse<UserProfileVO> getUserProfileVOById(long id, HttpServletRequest request) {
        User user = getPublicUserById(id);
        User loginUser = userService.getLoginUserPermitNull(request);
        return ResultUtils.success(buildUserProfileVO(user, loginUser));
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 100, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        userPage.getRecords().forEach(user -> {
            user.setUserAvatar(userService.getUserVO(user).getUserAvatar());
        });
        return ResultUtils.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @RateLimit(key = "user:listPageVO", maxRequests = 300, windowSeconds = 60)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                       HttpServletRequest request) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(current < 1 || size < 1 || size > 20, ErrorCode.PARAMS_ERROR, "分页参数不合法");
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    /**
     * 更新个人信息
     *
     * @param userUpdateMyRequest
     * @param request
     * @return
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User currentUser = userService.getById(loginUser.getId());
        if (currentUser != null) {
            loginUser = currentUser;
        }
        if (userUpdateMyRequest.getUserAccount() != null) {
            String userAccount = StringUtils.trimToNull(userUpdateMyRequest.getUserAccount());
            ThrowUtils.throwIf(userAccount == null, ErrorCode.PARAMS_ERROR, "登录账号不能为空");
            userService.checkUserAccountUnique(userAccount, loginUser.getId());
            userUpdateMyRequest.setUserAccount(userAccount);
        }
        if (userUpdateMyRequest.getUserName() != null) {
            String userName = StringUtils.trimToNull(userUpdateMyRequest.getUserName());
            ThrowUtils.throwIf(userName == null, ErrorCode.PARAMS_ERROR, "昵称不能为空");
            ThrowUtils.throwIf(userName.length() > 20, ErrorCode.PARAMS_ERROR, "昵称最多 20 个字符");
            userUpdateMyRequest.setUserName(userName);
        }
        if (userUpdateMyRequest.getCity() != null) {
            String requestCity = CityUtils.normalizeCity(userUpdateMyRequest.getCity());
            ThrowUtils.throwIf(!StringUtils.equals(requestCity, loginUser.getCity()),
                    ErrorCode.PARAMS_ERROR,
                    "城市由系统根据最近登录 IP 自动识别，不支持手动修改");
        }
        userUpdateMyRequest.setCity(loginUser.getCity());
        if (userUpdateMyRequest.getUserProfile() != null) {
            String userProfile = StringUtils.trimToEmpty(userUpdateMyRequest.getUserProfile());
            ThrowUtils.throwIf(userProfile.length() > 200, ErrorCode.PARAMS_ERROR, "个人简介最多 200 个字符");
            userUpdateMyRequest.setUserProfile(userProfile);
        }
        if (userUpdateMyRequest.getCareerDirection() != null) {
            userUpdateMyRequest.setCareerDirection(normalizeCareerDirection(userUpdateMyRequest.getCareerDirection()));
        }
        if (userUpdateMyRequest.getInterestTags() != null) {
            userUpdateMyRequest.setInterestTags(parseInterestTagList(normalizeInterestTags(userUpdateMyRequest.getInterestTags())));
        }
        String profileVisibleFields = null;
        if (userUpdateMyRequest.getProfileVisibleFields() != null) {
            profileVisibleFields = normalizeProfileVisibleFields(userUpdateMyRequest.getProfileVisibleFields());
        }
        // 校验昵称唯一性
        userService.checkUserNameUnique(userUpdateMyRequest.getUserName(), loginUser.getId());

        User user = new User();
        // 仅允许修改账号、昵称、头像、简介、城市
        BeanUtils.copyProperties(userUpdateMyRequest, user, "phone", "email", "mpOpenId", "githubId", "giteeId", "googleId", "profileVisibleFields");
        user.setInterestTags(normalizeInterestTags(userUpdateMyRequest.getInterestTags()));
        user.setProfileVisibleFields(profileVisibleFields);
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        User latestUser = userService.getById(loginUser.getId());
        tagSyncService.syncInterestTags(currentUser == null ? null : currentUser.getInterestTags(),
                latestUser == null ? null : latestUser.getInterestTags());
        return ResultUtils.success(true);
    }

    /**
     * 将简历解析出的技能标签合并到个人资料兴趣标签
     *
     * @param mergeRequest 标签合并请求
     * @param request      HTTP 请求
     * @return 最新登录用户信息
     */
    @PostMapping("/interest_tags/merge")
    @RateLimit(key = "user:interestTagsMerge", maxRequests = 30, windowSeconds = 60)
    public BaseResponse<LoginUserVO> mergeMyInterestTags(@RequestBody UserInterestTagsMergeRequest mergeRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(mergeRequest == null || mergeRequest.getInterestTags() == null,
                ErrorCode.PARAMS_ERROR, "请选择要添加的技能标签");
        User loginUser = userService.getLoginUser(request);
        User latestUser = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(latestUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        LinkedHashSet<String> mergedTagSet = new LinkedHashSet<>(parseInterestTagList(latestUser.getInterestTags()));
        mergeRequest.getInterestTags().stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .forEach(mergedTagSet::add);
        ThrowUtils.throwIf(mergedTagSet.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要添加的技能标签");

        User user = new User();
        user.setId(latestUser.getId());
        user.setInterestTags(normalizeInterestTags(new ArrayList<>(mergedTagSet)));
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        tagSyncService.syncInterestTags(latestUser.getInterestTags(), user.getInterestTags());
        return ResultUtils.success(userService.getLoginUserVO(userService.getById(latestUser.getId())));
    }

    private String normalizeOptionalSupportedCity(String city, boolean allowEmpty) {
        if (city == null) {
            return null;
        }
        String normalizedCity = CityUtils.normalizeCity(city);
        if (normalizedCity == null) {
            ThrowUtils.throwIf(!allowEmpty, ErrorCode.PARAMS_ERROR, "城市不能为空");
            return null;
        }
        ThrowUtils.throwIf(!CityUtils.isSupportedCity(normalizedCity), ErrorCode.PARAMS_ERROR, "请选择系统支持的城市");
        return normalizedCity;
    }

    private String normalizeOptionalUserName(String userName) {
        String normalizedUserName = StringUtils.trimToNull(userName);
        if (normalizedUserName == null) {
            return null;
        }
        ThrowUtils.throwIf(normalizedUserName.length() > 20, ErrorCode.PARAMS_ERROR, "昵称最多 20 个字符");
        return normalizedUserName;
    }

    private String normalizeCareerDirection(String careerDirection) {
        String normalizedDirection = StringUtils.trimToNull(careerDirection);
        if (normalizedDirection == null) {
            return null;
        }
        ThrowUtils.throwIf(normalizedDirection.length() > 30, ErrorCode.PARAMS_ERROR, "就业方向最多 30 个字符");
        return normalizedDirection;
    }

    private String normalizeInterestTags(List<String> interestTagList) {
        if (interestTagList == null) {
            return null;
        }
        List<String> normalizedTagList = interestTagList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
        ThrowUtils.throwIf(normalizedTagList.stream().anyMatch(tag -> tag.length() > 20),
                ErrorCode.PARAMS_ERROR, "兴趣标签单项不能超过 20 个字符");
        return normalizedTagList.isEmpty() ? null : JSONUtil.toJsonStr(normalizedTagList);
    }

    private String normalizeProfileVisibleFields(List<String> profileVisibleFieldList) {
        if (profileVisibleFieldList == null) {
            return null;
        }
        Set<String> requestedFieldSet = profileVisibleFieldList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> normalizedFieldList = DEFAULT_PROFILE_VISIBLE_FIELDS.stream()
                .filter(requestedFieldSet::contains)
                .collect(Collectors.toList());
        return JSONUtil.toJsonStr(normalizedFieldList);
    }

    private List<String> parseInterestTagList(String interestTags) {
        if (StringUtils.isBlank(interestTags)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(interestTags, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
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

    private boolean isProfileFieldVisible(List<String> visibleFieldList, String field) {
        return visibleFieldList != null && visibleFieldList.contains(field);
    }

    /**
     * 修改密码
     *
     * @param userChangePasswordRequest
     * @param request
     * @return
     */
    @PostMapping("/change_password")
    public BaseResponse<Boolean> changePassword(@RequestBody com.xduo.springbootinit.model.dto.user.UserChangePasswordRequest userChangePasswordRequest,
                                                HttpServletRequest request) {
        if (userChangePasswordRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        userService.changePassword(userChangePasswordRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 绑定手机号
     *
     * @param userBindRequest
     * @param request
     * @return
     */
    @PostMapping("/bind/phone")
    public BaseResponse<Boolean> bindPhone(@RequestBody com.xduo.springbootinit.model.dto.user.UserBindRequest userBindRequest,
                                           HttpServletRequest request) {
        if (userBindRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        userService.bindPhone(userBindRequest.getTarget(), userBindRequest.getCode(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 绑定邮箱
     *
     * @param userBindRequest
     * @param request
     * @return
     */
    @PostMapping("/bind/email")
    public BaseResponse<Boolean> bindEmail(@RequestBody com.xduo.springbootinit.model.dto.user.UserBindRequest userBindRequest,
                                           HttpServletRequest request) {
        if (userBindRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        userService.bindEmail(userBindRequest.getTarget(), userBindRequest.getCode(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 解绑手机号
     */
    @PostMapping("/unbind/phone")
    public BaseResponse<Boolean> unbindPhone(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindPhone(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 解绑邮箱
     */
    @PostMapping("/unbind/email")
    public BaseResponse<Boolean> unbindEmail(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindEmail(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 注销当前账号
     */
    @PostMapping("/delete/my")
    public BaseResponse<Boolean> deleteMyAccount(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.deleteMyAccount(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 用户签到
     *
     * @param request
     * @return
     */
    @PostMapping("/add/sign_in")
    public BaseResponse<Boolean> addUserSignIn(HttpServletRequest request) {
        // 必须要登录才能签到
        User loginUser = userService.getLoginUser(request);
        boolean result = userService.addUserSignIn(loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 获取用户签到记录
     *
     * @param year    年份（为空表示当前年份）
     * @param request
     * @return 签到记录映射
     */
    @GetMapping("/get/sign_in")
    public BaseResponse<List<Integer>> getUserSignInRecord(Integer year, HttpServletRequest request) {
        // 必须要登录才能获取
        User loginUser = userService.getLoginUser(request);
        List<Integer> userSignInRecord = userService.getUserSignInRecord(loginUser.getId(), year);
        return ResultUtils.success(userSignInRecord);
    }

    /**
     * 解绑 GitHub
     */
    @PostMapping("/unbind/github")
    public BaseResponse<Boolean> unbindGithub(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindGithub(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 解绑 Gitee
     */
    @PostMapping("/unbind/gitee")
    public BaseResponse<Boolean> unbindGitee(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindGitee(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 解绑 Google
     */
    @PostMapping("/unbind/google")
    public BaseResponse<Boolean> unbindGoogle(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindGoogle(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 解绑公众号
     */
    @PostMapping("/unbind/mp")
    public BaseResponse<Boolean> unbindMp(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        userService.unbindMpOpenId(loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 获取公开可见的用户
     */
    private User getPublicUserById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(UserConstant.BAN_ROLE.equals(user.getUserRole()), ErrorCode.NOT_FOUND_ERROR);
        return user;
    }

    /**
     * 组装公开主页摘要
     */
    private UserProfileVO buildUserProfileVO(User user, User loginUser) {
        UserProfileVO userProfileVO = new UserProfileVO();
        List<String> visibleFieldList = parseProfileVisibleFieldList(user.getProfileVisibleFields());
        UserVO userVO = userService.getUserVO(user);
        applyProfileVisibility(userVO, visibleFieldList);
        userProfileVO.setUser(userVO);
        userProfileVO.setProfileVisibleFieldList(visibleFieldList);

        if (isProfileFieldVisible(visibleFieldList, "stats")) {
            java.util.Map<String, Object> stats = userQuestionHistoryService.getUserQuestionStats(user.getId());
            userProfileVO.setTotalQuestionCount(getLongValue(stats.get("totalCount")));
            userProfileVO.setMasteredQuestionCount(getLongValue(stats.get("masteredCount")));
            userProfileVO.setActiveDays(getLongValue(stats.get("activeDays")));
            userProfileVO.setCurrentStreak(getLongValue(stats.get("currentStreak")));
            userProfileVO.setFavourCount(getLongValue(stats.get("favourCount")));
            userProfileVO.setTodayCount(getLongValue(stats.get("todayCount")));
            userProfileVO.setDailyTarget(getLongValue(stats.get("dailyTarget")));
            userProfileVO.setGoalCompletedToday(Boolean.TRUE.equals(stats.get("goalCompletedToday")));
            userProfileVO.setRecommendedDifficulty((String) stats.get("recommendedDifficulty"));
            userProfileVO.setTotalStudyDurationSeconds(getLongValue(stats.get("totalStudyDurationSeconds")));
            userProfileVO.setTodayStudyDurationSeconds(getLongValue(stats.get("todayStudyDurationSeconds")));
            userProfileVO.setStudySessionCount(getLongValue(stats.get("studySessionCount")));
            userProfileVO.setAverageStudyDurationSeconds(getLongValue(stats.get("averageStudyDurationSeconds")));
            userProfileVO.setAchievementList(getMapListValue(stats.get("achievementList")));
            userProfileVO.setQuestionHistoryRecordList(userQuestionHistoryService.getUserQuestionHistoryRecord(
                    user.getId(),
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).getYear()
            ));
        }

        if (isProfileFieldVisible(visibleFieldList, "content")) {
            QueryWrapper<Question> questionQueryWrapper = new QueryWrapper<>();
            questionQueryWrapper.eq("userId", user.getId());
            questionQueryWrapper.and(qw -> qw.eq("reviewStatus", QuestionConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
            userProfileVO.setApprovedQuestionCount(questionService.count(questionQueryWrapper));
            QueryWrapper<QuestionBank> questionBankQueryWrapper = new QueryWrapper<>();
            questionBankQueryWrapper.eq("userId", user.getId());
            questionBankQueryWrapper.and(qw -> qw.eq("reviewStatus", QuestionBankConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
            userProfileVO.setApprovedQuestionBankCount(questionBankService.count(questionBankQueryWrapper));
        }
        if (isProfileFieldVisible(visibleFieldList, "relation")) {
            userProfileVO.setFollowerCount(userFollowService.getFollowerCount(user.getId()));
            userProfileVO.setFollowingCount(userFollowService.getFollowingCount(user.getId()));
        }
        userProfileVO.setHasFollowed(userFollowService.hasFollowed(loginUser == null ? null : loginUser.getId(), user.getId()));
        userProfileVO.setRecentActivityList(isProfileFieldVisible(visibleFieldList, "activity")
                ? buildRecentActivityList(user.getId())
                : Collections.emptyList());
        return userProfileVO;
    }

    private void applyProfileVisibility(UserVO userVO, List<String> visibleFieldList) {
        if (userVO == null) {
            return;
        }
        userVO.setProfileVisibleFieldList(visibleFieldList);
        if (!isProfileFieldVisible(visibleFieldList, "profile")) {
            userVO.setUserProfile(null);
        }
        if (!isProfileFieldVisible(visibleFieldList, "city")) {
            userVO.setCity(null);
        }
        if (!isProfileFieldVisible(visibleFieldList, "career")) {
            userVO.setCareerDirection(null);
        }
        if (!isProfileFieldVisible(visibleFieldList, "tags")) {
            userVO.setInterestTagList(Collections.emptyList());
        }
        if (!isProfileFieldVisible(visibleFieldList, "joinTime")) {
            userVO.setCreateTime(null);
        }
    }

    private List<UserActivityVO> buildRecentActivityList(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        List<UserActivityVO> activityList = new ArrayList<>();

        QueryWrapper<UserQuestionHistory> historyQueryWrapper = new QueryWrapper<>();
        historyQueryWrapper.eq("userId", userId);
        historyQueryWrapper.in("status", List.of(1, 2));
        historyQueryWrapper.orderByDesc("updateTime");
        historyQueryWrapper.last("limit 5");
        List<UserQuestionHistory> historyList = userQuestionHistoryService.list(historyQueryWrapper);
        Set<Long> questionIdSet = historyList.stream()
                .map(UserQuestionHistory::getQuestionId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Question> historyQuestionMap = questionIdSet.isEmpty()
                ? Collections.emptyMap()
                : questionService.listByIds(questionIdSet).stream()
                .collect(Collectors.toMap(Question::getId, question -> question, (left, right) -> left));
        historyList.forEach(history -> {
            Question question = historyQuestionMap.get(history.getQuestionId());
            if (question == null) {
                return;
            }
            UserActivityVO activityVO = new UserActivityVO();
            activityVO.setType("practice");
            activityVO.setBadge("刷题");
            activityVO.setTargetId(question.getId());
            activityVO.setTargetUrl("/question/" + question.getId());
            activityVO.setActivityTime(history.getUpdateTime());
            if (Integer.valueOf(1).equals(history.getStatus())) {
                activityVO.setTitle("掌握了一道题目");
                activityVO.setDescription("将《" + question.getTitle() + "》标记为已掌握");
            } else {
                activityVO.setTitle("记录了一道困难题");
                activityVO.setDescription("把《" + question.getTitle() + "》标记为需要继续攻克");
            }
            activityList.add(activityVO);
        });

        QueryWrapper<Question> questionQueryWrapper = new QueryWrapper<>();
        questionQueryWrapper.eq("userId", userId);
        questionQueryWrapper.and(qw -> qw.eq("reviewStatus", QuestionConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
        questionQueryWrapper.orderByDesc("reviewTime").orderByDesc("createTime");
        questionQueryWrapper.last("limit 4");
        List<Question> approvedQuestionList = questionService.list(questionQueryWrapper);
        approvedQuestionList.forEach(question -> {
            UserActivityVO activityVO = new UserActivityVO();
            activityVO.setType("submission");
            activityVO.setBadge("题目");
            activityVO.setTargetId(question.getId());
            activityVO.setTargetUrl("/question/" + question.getId());
            activityVO.setActivityTime(question.getReviewTime() != null ? question.getReviewTime() : question.getCreateTime());
            activityVO.setTitle("发布了一道公开题目");
            activityVO.setDescription("题目《" + question.getTitle() + "》已通过审核并公开展示");
            activityList.add(activityVO);
        });

        QueryWrapper<QuestionBank> questionBankQueryWrapper = new QueryWrapper<>();
        questionBankQueryWrapper.eq("userId", userId);
        questionBankQueryWrapper.and(qw -> qw.eq("reviewStatus", QuestionBankConstant.REVIEW_STATUS_APPROVED).or().isNull("reviewStatus"));
        questionBankQueryWrapper.orderByDesc("reviewTime").orderByDesc("createTime");
        questionBankQueryWrapper.last("limit 4");
        List<QuestionBank> approvedQuestionBankList = questionBankService.list(questionBankQueryWrapper);
        approvedQuestionBankList.forEach(questionBank -> {
            UserActivityVO activityVO = new UserActivityVO();
            activityVO.setType("bank_submission");
            activityVO.setBadge("题库");
            activityVO.setTargetId(questionBank.getId());
            activityVO.setTargetUrl("/bank/" + questionBank.getId());
            activityVO.setActivityTime(questionBank.getReviewTime() != null ? questionBank.getReviewTime() : questionBank.getCreateTime());
            activityVO.setTitle("发布了一个公开题库");
            activityVO.setDescription("题库《" + questionBank.getTitle() + "》已通过审核并公开展示");
            activityList.add(activityVO);
        });

        return activityList.stream()
                .sorted(Comparator.comparing(UserActivityVO::getActivityTime, Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(8)
                .collect(Collectors.toList());
    }

    private long getLongValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMapListValue(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        return ((List<?>) value).stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.toList());
    }
}
