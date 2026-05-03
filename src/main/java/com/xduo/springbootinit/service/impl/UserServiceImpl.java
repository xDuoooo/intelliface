package com.xduo.springbootinit.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.constant.CommonConstant;
import com.xduo.springbootinit.constant.RedisConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.manager.CosManager;
import com.xduo.springbootinit.mapper.UserMapper;
import com.xduo.springbootinit.model.dto.user.UserQueryRequest;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.model.enums.UserRoleEnum;
import com.xduo.springbootinit.model.vo.LoginUserVO;
import com.xduo.springbootinit.model.vo.UserVO;
import com.xduo.springbootinit.satoken.DeviceUtils;
import com.xduo.springbootinit.service.SystemConfigService;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.AliyunSmsVerifyUtils;
import com.xduo.springbootinit.utils.IpCityResolver;
import com.xduo.springbootinit.utils.NetUtils;
import com.xduo.springbootinit.utils.SqlUtils;
import com.xduo.springbootinit.utils.TencentEmailUtils;
import com.xduo.springbootinit.exception.ThrowUtils;
import org.redisson.api.RAtomicLong;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.DateUnit;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import static com.xduo.springbootinit.constant.UserConstant.*;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "xduo";

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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private TencentEmailUtils tencentEmailUtils;

    @Resource
    private AliyunSmsVerifyUtils aliyunSmsVerifyUtils;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private CosManager cosManager;

    @Resource
    private IpCityResolver ipCityResolver;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword,
            HttpServletRequest request) {
        ensureRegisterAllowed();
        userAccount = StringUtils.trim(userAccount);
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        validateUserAccount(userAccount);
        if (userPassword.length() < 8 || !userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短或两次密码不一致");
        }
        synchronized (userAccount.intern()) {
            long count = this.count(new QueryWrapper<User>().eq("userAccount", userAccount));
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
            }
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes()));
            user.setPasswordConfigured(1);
            user.setUserName("智面用户_" + RandomUtil.randomNumbers(4));
            user.setUserRole(UserRoleEnum.USER.getValue());
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, String captcha, String captchaUuid, HttpServletRequest request) {
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        
        // 1. 图形码校验
        if (systemConfigService.isRequireCaptcha()) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(captcha, captchaUuid), ErrorCode.PARAMS_ERROR,
                    "参数不全，请完成图形码验证");
            String captchaKey = RedisConstant.getUserCaptchaRedisKey(captchaUuid);
            String savedCaptcha = stringRedisTemplate.opsForValue().get(captchaKey);
            if (savedCaptcha == null || !savedCaptcha.equalsIgnoreCase(captcha)) {
                log.warn("图形验证码匹配失败: target={}, input={}, saved={}", userAccount, captcha, savedCaptcha);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图形验证码错误或已过期");
            }
            stringRedisTemplate.delete(captchaKey);
        }

        String loginIdentifier = StringUtils.trimToEmpty(userAccount);
        String normalizedEmailIdentifier = normalizeEmailTarget(loginIdentifier);
        ensurePasswordLoginNotBlocked(loginIdentifier);
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        QueryWrapper<User> queryWrapper = new QueryWrapper<User>()
                .eq("userPassword", encryptPassword)
                .and(wrapper -> wrapper.eq("userAccount", loginIdentifier)
                        .or()
                        .eq("phone", loginIdentifier)
                        .or()
                        .eq("email", normalizedEmailIdentifier));
        User user = this.getOne(queryWrapper);
        if (user == null) {
            recordPasswordLoginFailure(loginIdentifier);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        ensureUserAvailable(user);
        clearPasswordLoginFailure(loginIdentifier);
        ensureMaintenanceLoginAllowed(user);
        user = syncUserCityFromRequest(user, request);
        if (!hasUsablePasswordLogin(user)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未设置可用密码，请使用验证码或第三方方式登录");
        }
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO userLoginByMpOpenId(String mpOpenId, HttpServletRequest request) {
        String normalizedMpOpenId = StringUtils.trimToNull(mpOpenId);
        ThrowUtils.throwIf(normalizedMpOpenId == null, ErrorCode.PARAMS_ERROR, "公众号身份不能为空");
        synchronized (("wxmp:" + normalizedMpOpenId).intern()) {
            User user = this.getOne(new QueryWrapper<User>().eq("mpOpenId", normalizedMpOpenId));
            if (user == null) {
                ensureRegisterAllowed();
                user = new User();
                user.setMpOpenId(normalizedMpOpenId);
                user.setUserAccount("wx_" + RandomUtil.randomString(8));
                user.setUserName("智面微信用户_" + RandomUtil.randomNumbers(4));
                user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
                user.setPasswordConfigured(0);
                user.setUserRole(UserRoleEnum.USER.getValue());
                this.save(user);
            }
            ensureUserAvailable(user);
            ensureMaintenanceLoginAllowed(user);
            user = syncUserCityFromRequest(user, request);
            StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
            StpUtil.getSession().set(USER_LOGIN_STATE, user);
            return this.getLoginUserVO(user);
        }
    }

    @Override
    public LoginUserVO userLoginBySocial(String platform, String socialId, String nickname, String avatar,
            HttpServletRequest request) {
        String normalizedPlatform = normalizeSocialPlatform(platform);
        socialId = StringUtils.trimToEmpty(socialId);
        ThrowUtils.throwIf(StringUtils.isBlank(socialId), ErrorCode.PARAMS_ERROR, "第三方账号标识不能为空");
        nickname = normalizeSocialUserName(nickname, normalizedPlatform);
        avatar = StringUtils.trimToNull(avatar);
        String socialField = resolveSocialPlatformField(normalizedPlatform);
        User user = this.getOne(new QueryWrapper<User>().eq(socialField, socialId).last("limit 1"));
        if (user == null) {
            ensureRegisterAllowed();
            user = new User();
            applySocialPlatformId(user, normalizedPlatform, socialId);
            user.setUserAccount("u_" + RandomUtil.randomString(8));
            user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
            user.setPasswordConfigured(0);
            user.setUserName(nickname);
            user.setUserAvatar(avatar);
            user.setUserRole(UserRoleEnum.USER.getValue());
            this.save(user);
        }
        ensureUserAvailable(user);
        ensureMaintenanceLoginAllowed(user);
        user = syncUserCityFromRequest(user, request);
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public void sendVerificationCode(com.xduo.springbootinit.model.dto.user.UserSendCodeRequest userSendCodeRequest,
            HttpServletRequest request) {
        String target = userSendCodeRequest.getTarget();
        Integer type = userSendCodeRequest.getType();
        String captcha = userSendCodeRequest.getCaptcha();
        String captchaUuid = userSendCodeRequest.getCaptchaUuid();

        ThrowUtils.throwIf(StringUtils.isBlank(target) || type == null, ErrorCode.PARAMS_ERROR, "参数不全");
        validateLoginTargetType(type);
        target = normalizeCodeLoginTarget(target, type);

        // 1. 图形码校验
        if (systemConfigService.isRequireCaptcha()) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(captcha, captchaUuid), ErrorCode.PARAMS_ERROR,
                    "参数不全，请完成图形码验证");
            String captchaKey = RedisConstant.getUserCaptchaRedisKey(captchaUuid);
            String savedCaptcha = stringRedisTemplate.opsForValue().get(captchaKey);
            if (savedCaptcha == null || !savedCaptcha.equalsIgnoreCase(captcha)) {
                log.warn("图形验证码匹配失败: target={}, input={}, saved={}", target, captcha, savedCaptcha);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图形验证码错误或已过期");
            }
            stringRedisTemplate.delete(captchaKey);
        }

        // 2. 格式校验
        validateVerificationTargetFormat(target, type);
        validateCodeLoginEntrance(target, type);

        // 3. 限流校验 (IP 10/日, 目标 5/日)
        String ip = NetUtils.getIpAddress(request);
        RAtomicLong ipCounter = redissonClient.getAtomicLong(RedisConstant.getUserIpLimitRedisKey(ip));
        RAtomicLong targetCounter = redissonClient.getAtomicLong(RedisConstant.getUserPhoneLimitRedisKey(target));
        if (ipCounter.get() >= 10) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "今日发送次数已达上限");
        }
        if (targetCounter.get() >= 5) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    Objects.equals(type, 1) ? "该邮箱今日发送次数已达上限" : "该号码今日发送次数已达上限");
        }

        // 4. 60s 频率限制
        String limitKey = RedisConstant.getUserCodeSendLimitRedisKey(target);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "发送过于频繁，请 60s 后重试");
        }

        // 5. 发送逻辑
        boolean sendResult;
        if (type == 1) {
            String code = RandomUtil.randomNumbers(6);
            sendResult = sendEmail(target, code);
            if (!sendResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "邮件发送失败，请检查腾讯云邮件模板、发信地址是否已认证及账号配置");
            }
            stringRedisTemplate.opsForValue().set(RedisConstant.getUserLoginCodeRedisKey(target), code, 5,
                    java.util.concurrent.TimeUnit.MINUTES);
        } else {
            AliyunSmsVerifyUtils.SmsSendResult smsSendResult = aliyunSmsVerifyUtils.sendVerifyCode(target);
            sendResult = smsSendResult.success();
            if (!sendResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "短信发送失败，请检查阿里云号码验证服务配置、签名和模板");
            }
            ThrowUtils.throwIf(StringUtils.isBlank(smsSendResult.outId()), ErrorCode.SYSTEM_ERROR,
                    "短信发送失败，阿里云未返回验证码会话标识");
            stringRedisTemplate.opsForValue().set(RedisConstant.getUserPhoneVerifyOutIdRedisKey(target),
                    smsSendResult.outId(), aliyunSmsVerifyUtils.getValidTimeSeconds(),
                    java.util.concurrent.TimeUnit.SECONDS);
            if (smsSendResult.mock() && StringUtils.isNotBlank(smsSendResult.localCode())) {
                stringRedisTemplate.opsForValue().set(RedisConstant.getUserLoginCodeRedisKey(target),
                        smsSendResult.localCode(), aliyunSmsVerifyUtils.getValidTimeSeconds(),
                        java.util.concurrent.TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.delete(RedisConstant.getUserLoginCodeRedisKey(target));
            }
        }

        // 6. 更新限流
        stringRedisTemplate.opsForValue().set(limitKey, "1", 60, java.util.concurrent.TimeUnit.SECONDS);

        long secondsToMidnight = DateUtil.between(new Date(), DateUtil.endOfDay(new Date()), DateUnit.SECOND);
        ipCounter.incrementAndGet();
        ipCounter.expire(java.time.Duration.ofSeconds(secondsToMidnight));
        targetCounter.incrementAndGet();
        targetCounter.expire(java.time.Duration.ofSeconds(secondsToMidnight));
    }

    @Override
    public LoginUserVO userCodeLogin(com.xduo.springbootinit.model.dto.user.UserCodeLoginRequest userCodeLoginRequest,
            HttpServletRequest request) {
        String target = userCodeLoginRequest.getTarget();
        String code = userCodeLoginRequest.getCode();
        Integer type = userCodeLoginRequest.getType();
        ThrowUtils.throwIf(StringUtils.isAnyBlank(target, code) || type == null, ErrorCode.PARAMS_ERROR, "参数不全");
        validateLoginTargetType(type);
        target = normalizeCodeLoginTarget(target, type);

        if (Objects.equals(type, 1)) {
            String codeKey = RedisConstant.getUserLoginCodeRedisKey(target);
            String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
            if (cachedCode == null || !cachedCode.equals(code)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
            }
            stringRedisTemplate.delete(codeKey);
        } else {
            verifyPhoneCodeOrThrow(target, code);
        }

        synchronized (target.intern()) {
            User user = this.getOne(new QueryWrapper<User>().eq(type == 1 ? "email" : "phone", target));
            if (user == null) {
                ensureRegisterAllowed();
                user = new User();
                if (type == 1)
                    user.setEmail(target);
                else
                    user.setPhone(target);
                user.setUserAccount("u_" + RandomUtil.randomString(8));
                user.setUserName("智面用户_" + RandomUtil.randomNumbers(4));
                user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
                user.setPasswordConfigured(0);
                user.setUserRole(UserRoleEnum.USER.getValue());
                this.save(user);
            }
            ensureUserAvailable(user);
            ensureMaintenanceLoginAllowed(user);
            user = syncUserCityFromRequest(user, request);
            StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
            StpUtil.getSession().set(USER_LOGIN_STATE, user);
            return this.getLoginUserVO(user);
        }
    }

    private boolean sendEmail(String email, String code) {
        return tencentEmailUtils.sendVerificationCodeEmail(email, code);
    }

    private void verifyPhoneCodeOrThrow(String target, String code) {
        String codeKey = RedisConstant.getUserLoginCodeRedisKey(target);
        if (aliyunSmsVerifyUtils.isMockEnabled()) {
            String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
            if (cachedCode == null || !cachedCode.equals(code)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
            }
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(RedisConstant.getUserPhoneVerifyOutIdRedisKey(target));
            return;
        }
        String outIdKey = RedisConstant.getUserPhoneVerifyOutIdRedisKey(target);
        String outId = stringRedisTemplate.opsForValue().get(outIdKey);
        if (StringUtils.isBlank(outId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        AliyunSmsVerifyUtils.SmsCheckResult smsCheckResult = aliyunSmsVerifyUtils.checkVerifyCode(target, code, outId);
        if (!smsCheckResult.success()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "短信验证码核验失败，请检查阿里云号码验证服务配置");
        }
        if (!smsCheckResult.verified()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    StringUtils.defaultIfBlank(smsCheckResult.message(), "验证码错误或已过期"));
        }
        stringRedisTemplate.delete(outIdKey);
        stringRedisTemplate.delete(codeKey);
    }

    @Override
    public void checkUserNameUnique(String userName, Long userId) {
        if (StringUtils.isBlank(userName))
            return;
        long count = this.count(new QueryWrapper<User>().eq("userName", userName).ne(userId != null, "id", userId));
        if (count > 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称已存在");
    }

    @Override
    public void checkUserAccountUnique(String userAccount, Long userId) {
        if (StringUtils.isBlank(userAccount)) {
            return;
        }
        validateUserAccount(userAccount);
        long count = this.count(new QueryWrapper<User>().eq("userAccount", userAccount).ne(userId != null, "id", userId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null)
            return null;
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        loginUserVO.setInterestTagList(parseInterestTagList(user.getInterestTags()));
        loginUserVO.setProfileVisibleFieldList(parseProfileVisibleFieldList(user.getProfileVisibleFields()));
        loginUserVO.setPasswordConfigured(hasUsablePasswordLogin(user) ? 1 : 0);
        // 对 COS 头像 URL 进行签名，防止永久 URL 暴露
        loginUserVO.setUserAvatar(cosManager.resolveSignedUrl(user.getUserAvatar()));
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            UserVO deletedUserVO = new UserVO();
            deletedUserVO.setUserName("已注销用户");
            deletedUserVO.setUserProfile("该用户已注销，公开内容仍会保留。");
            deletedUserVO.setUserRole("deleted");
            return deletedUserVO;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setInterestTagList(parseInterestTagList(user.getInterestTags()));
        userVO.setProfileVisibleFieldList(parseProfileVisibleFieldList(user.getProfileVisibleFields()));
        // 对 COS 头像 URL 进行签名，防止永久 URL 暴露
        userVO.setUserAvatar(cosManager.resolveSignedUrl(user.getUserAvatar()));
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList))
            return new ArrayList<>();
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        Long id = userQueryRequest.getId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String careerDirection = userQueryRequest.getCareerDirection();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StringUtils.isNotBlank(careerDirection), "careerDirection", careerDirection);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder == null || sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        return queryWrapper;
    }

    private List<String> parseInterestTagList(String interestTags) {
        if (StringUtils.isBlank(interestTags)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(interestTags, String.class).stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseProfileVisibleFieldList(String profileVisibleFields) {
        if (StringUtils.isBlank(profileVisibleFields)) {
            return DEFAULT_PROFILE_VISIBLE_FIELDS;
        }
        try {
            Set<String> requestedFieldSet = new HashSet<>(JSONUtil.toList(profileVisibleFields, String.class));
            return DEFAULT_PROFILE_VISIBLE_FIELDS.stream()
                    .filter(requestedFieldSet::contains)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return DEFAULT_PROFILE_VISIBLE_FIELDS;
        }
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = this.getById((Serializable) StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        ensureUserAvailable(user);
        user = syncUserCityFromRequest(user, request);
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return user;
    }

    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            return null;
        }
        User user = this.getById((Serializable) StpUtil.getLoginIdAsLong());
        if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
            StpUtil.logout();
            return null;
        }
        if (user != null) {
            user = syncUserCityFromRequest(user, request);
            StpUtil.getSession().set(USER_LOGIN_STATE, user);
        }
        return user;
    }

    @Override
    public boolean isAdmin(HttpServletRequest request) {
        User user = this.getLoginUserPermitNull(request);
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
            return true;
        }
        return false;
    }

    @Override
    public boolean addUserSignIn(long userId) {
        LocalDate today = LocalDate.now();
        String key = RedisConstant.getUserSignInRedisKey(today.getYear(), userId);
        long offset = today.getDayOfYear() - 1L;
        Boolean signed = stringRedisTemplate.opsForValue().getBit(key, offset);
        if (Boolean.TRUE.equals(signed)) {
            return false;
        }
        stringRedisTemplate.opsForValue().setBit(key, offset, true);
        log.info("用户签到成功: userId={}, dayOfYear={}, key={}", userId, today.getDayOfYear(), key);
        return true;
    }

    @Override
    public List<Integer> getUserSignInRecord(long userId, Integer year) {
        int targetYear = year == null ? LocalDate.now().getYear() : year;
        ThrowUtils.throwIf(targetYear < 1970 || targetYear > 2999, ErrorCode.PARAMS_ERROR, "年份不合法");
        int daysInYear = LocalDate.of(targetYear, 1, 1).lengthOfYear();
        String key = RedisConstant.getUserSignInRedisKey(targetYear, userId);
        List<Integer> recordList = new ArrayList<>(daysInYear);
        for (int dayOffset = 0; dayOffset < daysInYear; dayOffset++) {
            Boolean signed = stringRedisTemplate.opsForValue().getBit(key, dayOffset);
            recordList.add(Boolean.TRUE.equals(signed) ? 1 : 0);
        }
        return recordList;
    }

    @Override
    public void changePassword(com.xduo.springbootinit.model.dto.user.UserChangePasswordRequest userChangePasswordRequest, User loginUser) {
        String oldPassword = userChangePasswordRequest.getOldPassword();
        String newPassword = userChangePasswordRequest.getNewPassword();
        String checkPassword = userChangePasswordRequest.getCheckPassword();
        if (StringUtils.isBlank(newPassword) || newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能少于 8 位");
        }
        if (!Objects.equals(newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");
        }
        if (hasUsablePasswordLogin(loginUser)) {
            boolean canResetWithoutOldPassword = hasOtherLoginMethod(loginUser);
            if (StringUtils.isNotBlank(oldPassword)) {
                String encryptOld = DigestUtils.md5DigestAsHex((SALT + oldPassword).getBytes());
                if (!loginUser.getUserPassword().equals(encryptOld)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码错误");
                }
            } else if (!canResetWithoutOldPassword) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入旧密码");
            }
        }
        User user = new User();
        user.setId(loginUser.getId());
        user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + newPassword).getBytes()));
        user.setPasswordConfigured(1);
        this.updateById(user);
        StpUtil.logout(); // 修改密码后强制重新登录
    }

    @Override
    public void bindPhone(String target, String code, User loginUser) {
        target = normalizePhoneTarget(target);
        validateVerificationTargetFormat(target, 2);
        // 1. 校验验证码
        verifyPhoneCodeOrThrow(target, code);

        // 2. 校验手机号是否已被占用
        long count = this.count(new QueryWrapper<User>().eq("phone", target).ne("id", loginUser.getId()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该手机号已被其他账号绑定");
        }

        // 3. 更新
        User user = new User();
        user.setId(loginUser.getId());
        user.setPhone(target);
        boolean result = this.updateById(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "绑定失败，服务器开小差了");
        }
        refreshLoginSession(loginUser.getId());
    }

    @Override
    public void bindEmail(String target, String code, User loginUser) {
        target = normalizeEmailTarget(target);
        validateVerificationTargetFormat(target, 1);
        // 1. 校验验证码
        String codeKey = RedisConstant.getUserLoginCodeRedisKey(target);
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }
        stringRedisTemplate.delete(codeKey);

        // 2. 校验邮箱是否已被占用
        long count = this.count(new QueryWrapper<User>().eq("email", target).ne("id", loginUser.getId()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该邮箱已被其他账号绑定");
        }

        // 3. 更新
        User user = new User();
        user.setId(loginUser.getId());
        user.setEmail(target);
        boolean result = this.updateById(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "绑定失败，服务器开小差了");
        }
        refreshLoginSession(loginUser.getId());
    }

    @Override
    public User githubLogin(String githubId, String userName, String userAvatar, HttpServletRequest request) {
        // 1. 检查是否存在该 GitHub ID
        User user = this.getOne(new QueryWrapper<User>().eq("githubId", githubId));
        if (user == null) {
            ensureRegisterAllowed();
            // 2. 静默注册
            synchronized (githubId.intern()) {
                // 再查一遍防止并发冲突
                user = this.getOne(new QueryWrapper<User>().eq("githubId", githubId));
                if (user == null) {
                    user = new User();
                    user.setGithubId(githubId);
                    user.setUserAccount("gh_" + RandomUtil.randomString(8));
                    user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
                    user.setPasswordConfigured(0);
                    user.setUserName(normalizeSocialUserName(userName, "github"));
                    user.setUserAvatar(StringUtils.trimToNull(userAvatar));
                    user.setUserRole(UserRoleEnum.USER.getValue());
                    boolean saveResult = this.save(user);
                    if (!saveResult) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "GitHub 自动注册失败");
                    }
                    // 刷新 user 以获取数据库自动生成的 createTime 等字段
                    user = this.getById(user.getId());
                }
            }
        }
        ensureUserAvailable(user);
        ensureMaintenanceLoginAllowed(user);
        user = syncUserCityFromRequest(user, request);
        // 3. 建立登录态
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return user;
    }

    @Override
    public User giteeLogin(String giteeId, String userName, String userAvatar, HttpServletRequest request) {
        // 1. 检查是否存在该 Gitee ID
        User user = this.getOne(new QueryWrapper<User>().eq("giteeId", giteeId));
        if (user == null) {
            ensureRegisterAllowed();
            // 2. 静默注册
            synchronized (giteeId.intern()) {
                // 再查一遍防止并发冲突
                user = this.getOne(new QueryWrapper<User>().eq("giteeId", giteeId));
                if (user == null) {
                    user = new User();
                    user.setGiteeId(giteeId);
                    user.setUserAccount("gt_" + RandomUtil.randomString(8));
                    user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
                    user.setPasswordConfigured(0);
                    user.setUserName(normalizeSocialUserName(userName, "gitee"));
                    user.setUserAvatar(StringUtils.trimToNull(userAvatar));
                    user.setUserRole(UserRoleEnum.USER.getValue());
                    boolean saveResult = this.save(user);
                    if (!saveResult) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Gitee 自动注册失败");
                    }
                    // 刷新 user 以获取数据库自动生成的 createTime 等字段
                    user = this.getById(user.getId());
                }
            }
        }
        ensureUserAvailable(user);
        ensureMaintenanceLoginAllowed(user);
        user = syncUserCityFromRequest(user, request);
        // 3. 建立登录态
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return user;
    }

    @Override
    public User googleLogin(String googleId, String userName, String userAvatar, HttpServletRequest request) {
        // 1. 检查是否存在该 Google ID
        User user = this.getOne(new QueryWrapper<User>().eq("googleId", googleId));
        if (user == null) {
            ensureRegisterAllowed();
            // 2. 静默注册
            synchronized (googleId.intern()) {
                // 再查一遍防止并发冲突
                user = this.getOne(new QueryWrapper<User>().eq("googleId", googleId));
                if (user == null) {
                    user = new User();
                    user.setGoogleId(googleId);
                    user.setUserAccount("gl_" + RandomUtil.randomString(8));
                    user.setUserPassword(DigestUtils.md5DigestAsHex((SALT + RandomUtil.randomString(16)).getBytes()));
                    user.setPasswordConfigured(0);
                    user.setUserName(normalizeSocialUserName(userName, "google"));
                    user.setUserAvatar(StringUtils.trimToNull(userAvatar));
                    user.setUserRole(UserRoleEnum.USER.getValue());
                    boolean saveResult = this.save(user);
                    if (!saveResult) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Google 自动注册失败");
                    }
                    // 刷新 user 以获取数据库自动生成的 createTime 等字段
                    user = this.getById(user.getId());
                }
            }
        }
        ensureUserAvailable(user);
        ensureMaintenanceLoginAllowed(user);
        user = syncUserCityFromRequest(user, request);
        // 3. 建立登录态
        StpUtil.login(user.getId(), DeviceUtils.getRequestDevice(request));
        StpUtil.getSession().set(USER_LOGIN_STATE, user);
        return user;
    }

    @Override
    public void bindGithub(long userId, String githubId) {
        githubId = StringUtils.trimToEmpty(githubId);
        ThrowUtils.throwIf(StringUtils.isBlank(githubId), ErrorCode.PARAMS_ERROR, "GitHub 标识不能为空");
        long count = this.count(new QueryWrapper<User>().eq("githubId", githubId).ne("id", userId));
        if (count > 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "该 GitHub 账号已被其他账号绑定");
        User user = new User();
        user.setId(userId);
        user.setGithubId(githubId);
        this.updateById(user);
        refreshLoginSession(userId);
    }

    @Override
    public void bindGitee(long userId, String giteeId) {
        giteeId = StringUtils.trimToEmpty(giteeId);
        ThrowUtils.throwIf(StringUtils.isBlank(giteeId), ErrorCode.PARAMS_ERROR, "Gitee 标识不能为空");
        long count = this.count(new QueryWrapper<User>().eq("giteeId", giteeId).ne("id", userId));
        if (count > 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "该 Gitee 账号已被其他账号绑定");
        User user = new User();
        user.setId(userId);
        user.setGiteeId(giteeId);
        this.updateById(user);
        refreshLoginSession(userId);
    }

    @Override
    public void bindGoogle(long userId, String googleId) {
        googleId = StringUtils.trimToEmpty(googleId);
        ThrowUtils.throwIf(StringUtils.isBlank(googleId), ErrorCode.PARAMS_ERROR, "Google 标识不能为空");
        long count = this.count(new QueryWrapper<User>().eq("googleId", googleId).ne("id", userId));
        if (count > 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "该 Google 账号已被其他账号绑定");
        User user = new User();
        user.setId(userId);
        user.setGoogleId(googleId);
        this.updateById(user);
        refreshLoginSession(userId);
    }

    @Override
    public void bindMpOpenId(long userId, String mpOpenId) {
        mpOpenId = StringUtils.trimToEmpty(mpOpenId);
        ThrowUtils.throwIf(StringUtils.isBlank(mpOpenId), ErrorCode.PARAMS_ERROR, "公众号标识不能为空");
        User currentUser = this.getById(userId);
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isNotBlank(currentUser.getMpOpenId()) && !StringUtils.equals(currentUser.getMpOpenId(), mpOpenId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前账号已绑定其他公众号，请先解绑后再试");
        }
        long count = this.count(new QueryWrapper<User>().eq("mpOpenId", mpOpenId).ne("id", userId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该公众号已被其他账号绑定");
        }
        User user = new User();
        user.setId(userId);
        user.setMpOpenId(mpOpenId);
        this.updateById(user);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindPhone(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getPhone())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定手机号");
        }
        checkLastLoginMethod(user, "phone");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setPhone("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindEmail(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定邮箱");
        }
        checkLastLoginMethod(user, "email");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setEmail("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindGithub(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getGithubId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定 GitHub");
        }
        // 校验：不能是唯一的登录方式
        checkLastLoginMethod(user, "githubId");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setGithubId("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindGitee(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getGiteeId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定 Gitee");
        }
        checkLastLoginMethod(user, "giteeId");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setGiteeId("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindGoogle(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getGoogleId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定 Google");
        }
        checkLastLoginMethod(user, "googleId");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setGoogleId("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void unbindMpOpenId(long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        if (StringUtils.isBlank(user.getMpOpenId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前账号尚未绑定公众号");
        }
        checkLastLoginMethod(user, "mpOpenId");
        User updateRes = new User();
        updateRes.setId(userId);
        updateRes.setMpOpenId("");
        this.updateById(updateRes);
        refreshLoginSession(userId);
    }

    @Override
    public void deleteMyAccount(long userId) {
        User user = this.getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "当前账号不存在");
        if (UserRoleEnum.ADMIN.getValue().equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "管理员账号请在后台处理，不支持在个人中心直接注销");
        }
        boolean removed = this.removeById(userId);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "注销账号失败");
        if (StpUtil.isLogin() && Objects.equals(StpUtil.getLoginIdAsLong(), userId)) {
            StpUtil.logout();
        }
    }

    /**
     * 校验是否为唯一的登录方式
     */
    private void checkLastLoginMethod(User user, String currentField) {
        int methods = 0;
        if (hasUsablePasswordLogin(user) && !"password".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getPhone()) && !"phone".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getEmail()) && !"email".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getMpOpenId()) && !"mpOpenId".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getGithubId()) && !"githubId".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getGiteeId()) && !"giteeId".equals(currentField)) methods++;
        if (StringUtils.isNotBlank(user.getGoogleId()) && !"googleId".equals(currentField)) methods++;
        
        if (methods == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解绑失败，请至少保留一种登录方式");
        }
    }

    private boolean hasUsablePasswordLogin(User user) {
        if (user == null || StringUtils.isBlank(user.getUserPassword())) {
            return false;
        }
        if (user.getPasswordConfigured() != null) {
            return Integer.valueOf(1).equals(user.getPasswordConfigured());
        }
        return !isGeneratedVirtualAccount(user.getUserAccount());
    }

    private boolean hasOtherLoginMethod(User user) {
        if (user == null) {
            return false;
        }
        return StringUtils.isNotBlank(user.getPhone())
                || StringUtils.isNotBlank(user.getEmail())
                || StringUtils.isNotBlank(user.getMpOpenId())
                || StringUtils.isNotBlank(user.getGithubId())
                || StringUtils.isNotBlank(user.getGiteeId())
                || StringUtils.isNotBlank(user.getGoogleId());
    }

    private void validateUserAccount(String userAccount) {
        String trimmedUserAccount = StringUtils.trim(userAccount);
        if (StringUtils.isBlank(trimmedUserAccount) || trimmedUserAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (trimmedUserAccount.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过长");
        }
        if (!trimmedUserAccount.matches("^[A-Za-z0-9_]+$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号仅支持字母、数字和下划线");
        }
    }

    private boolean isGeneratedVirtualAccount(String userAccount) {
        if (StringUtils.isBlank(userAccount)) {
            return true;
        }
        return userAccount.matches("^(u|gh|gt|gl|wx)_[A-Za-z0-9]{8}$");
    }

    private void ensurePasswordLoginNotBlocked(String loginIdentifier) {
        String normalizedIdentifier = normalizeLoginIdentifier(loginIdentifier);
        String blockKey = RedisConstant.getUserPasswordLoginBlockRedisKey(normalizedIdentifier);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blockKey))) {
            Long ttl = stringRedisTemplate.getExpire(blockKey);
            long waitMinutes = ttl == null || ttl <= 0 ? 5 : Math.max(1L, (ttl + 59) / 60);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "密码登录失败次数过多，请约 " + waitMinutes + " 分钟后再试");
        }
    }

    private void recordPasswordLoginFailure(String loginIdentifier) {
        String normalizedIdentifier = normalizeLoginIdentifier(loginIdentifier);
        String failKey = RedisConstant.getUserPasswordLoginFailRedisKey(normalizedIdentifier);
        Long failCount = stringRedisTemplate.opsForValue().increment(failKey);
        if (failCount == null) {
            return;
        }
        if (failCount == 1L) {
            stringRedisTemplate.expire(failKey, java.time.Duration.ofMinutes(10));
        }
        if (failCount >= 6) {
            String blockKey = RedisConstant.getUserPasswordLoginBlockRedisKey(normalizedIdentifier);
            stringRedisTemplate.opsForValue().set(blockKey, "1", 5, java.util.concurrent.TimeUnit.MINUTES);
        }
    }

    private void clearPasswordLoginFailure(String loginIdentifier) {
        String normalizedIdentifier = normalizeLoginIdentifier(loginIdentifier);
        stringRedisTemplate.delete(RedisConstant.getUserPasswordLoginFailRedisKey(normalizedIdentifier));
        stringRedisTemplate.delete(RedisConstant.getUserPasswordLoginBlockRedisKey(normalizedIdentifier));
    }

    private String normalizeLoginIdentifier(String loginIdentifier) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(loginIdentifier));
    }

    private void validateLoginTargetType(Integer type) {
        if (!Objects.equals(type, 1) && !Objects.equals(type, 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码登录类型不合法");
        }
    }

    private void refreshLoginSession(long userId) {
        if (StpUtil.isLogin() && Objects.equals(StpUtil.getLoginIdAsLong(), userId)) {
            User latestUser = this.getById(userId);
            if (latestUser != null) {
                StpUtil.getSession().set(USER_LOGIN_STATE, latestUser);
            }
        }
    }

    private User syncUserCityFromRequest(User user, HttpServletRequest request) {
        if (user == null || request == null) {
            return user;
        }
        String resolvedCity = ipCityResolver.resolveLocationLabel(request);
        if (StringUtils.isBlank(resolvedCity) || StringUtils.equals(resolvedCity, user.getCity())) {
            return user;
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setCity(resolvedCity);
        boolean updated = this.updateById(updateUser);
        if (!updated) {
            return user;
        }
        log.info("用户最近登录城市已更新: userId={}, city={}", user.getId(), resolvedCity);
        User latestUser = this.getById(user.getId());
        return latestUser == null ? user : latestUser;
    }

    private void ensureUserAvailable(User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        if (UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
            if (StpUtil.isLogin() && Objects.equals(StpUtil.getLoginIdAsLong(), user.getId())) {
                StpUtil.logout();
            }
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "当前账号已被封禁");
        }
    }

    private void ensureRegisterAllowed() {
        if (systemConfigService.isMaintenanceMode()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统维护中，仅管理员可登录");
        }
        if (!systemConfigService.isAllowRegister()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前未开放注册");
        }
    }

    private void ensureMaintenanceLoginAllowed(User user) {
        if (systemConfigService.isMaintenanceMode() && !isAdmin(user)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统维护中，仅管理员可登录");
        }
    }

    private void validateCodeLoginEntrance(String target, Integer type) {
        boolean maintenanceMode = systemConfigService.isMaintenanceMode();
        boolean allowRegister = systemConfigService.isAllowRegister();
        User targetUser = this.getOne(new QueryWrapper<User>().eq(type == 1 ? "email" : "phone", target));
        if (targetUser != null) {
            ensureUserAvailable(targetUser);
            if (maintenanceMode && !isAdmin(targetUser)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统维护中，仅管理员可登录");
            }
            return;
        }
        if (maintenanceMode) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "系统维护中，仅管理员可登录");
        }
        if (!allowRegister) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前未开放注册，仅已绑定邮箱或手机号的账号可使用验证码登录");
        }
    }

    private void validateVerificationTargetFormat(String target, Integer type) {
        if (Objects.equals(type, 1)) {
            ThrowUtils.throwIf(!target.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$"),
                    ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
            return;
        }
        ThrowUtils.throwIf(!target.matches("^1[3-9]\\d{9}$"),
                ErrorCode.PARAMS_ERROR, "手机号格式不正确");
    }

    private String normalizeCodeLoginTarget(String target, Integer type) {
        if (Objects.equals(type, 1)) {
            return normalizeEmailTarget(target);
        }
        return normalizePhoneTarget(target);
    }

    private String normalizeEmailTarget(String email) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(email));
    }

    private String normalizePhoneTarget(String phone) {
        return StringUtils.trimToEmpty(phone);
    }

    private String normalizeSocialUserName(String userName, String platform) {
        String normalizedUserName = StringUtils.trimToNull(userName);
        if (StringUtils.isNotBlank(normalizedUserName)) {
            return StringUtils.abbreviate(normalizedUserName, 32);
        }
        return "智面" + StringUtils.upperCase(StringUtils.defaultIfBlank(platform, "USER")) + "用户_" + RandomUtil.randomNumbers(4);
    }

    private String normalizeSocialPlatform(String platform) {
        String normalizedPlatform = StringUtils.lowerCase(StringUtils.trimToEmpty(platform));
        ThrowUtils.throwIf(StringUtils.isBlank(normalizedPlatform), ErrorCode.PARAMS_ERROR, "第三方登录平台不能为空");
        return normalizedPlatform;
    }

    private String resolveSocialPlatformField(String platform) {
        switch (platform) {
            case "github":
                return "githubId";
            case "gitee":
                return "giteeId";
            case "google":
                return "googleId";
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该第三方登录平台");
        }
    }

    private void applySocialPlatformId(User user, String platform, String socialId) {
        switch (platform) {
            case "github":
                user.setGithubId(socialId);
                return;
            case "gitee":
                user.setGiteeId(socialId);
                return;
            case "google":
                user.setGoogleId(socialId);
                return;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该第三方登录平台");
        }
    }
}
