package com.xduo.springbootinit.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.config.WechatMpConfig;
import com.xduo.springbootinit.constant.RedisConstant;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.dto.wxmp.WxMpCodeLoginRequest;
import com.xduo.springbootinit.model.vo.LoginUserVO;
import com.xduo.springbootinit.model.vo.WxMpLoginStatusVO;
import com.xduo.springbootinit.model.vo.WxMpLoginTicketVO;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.service.WxMpService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

/**
 * 微信公众号验证码登录服务
 */
@Service
@Slf4j
public class WxMpServiceImpl implements WxMpService {

    private static final String WX_MP_LOGIN_TICKET_SESSION_KEY = "wx_mp_login_ticket";
    private static final String WX_MP_BIND_TICKET_SESSION_KEY = "wx_mp_bind_ticket";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CODE_SENT = "code_sent";
    private static final String STATUS_USED = "used";
    private static final String STATUS_EXPIRED = "expired";
    private static final String SCENE_LOGIN = "login";
    private static final String SCENE_BIND = "bind";

    @Resource
    private WechatMpConfig wechatMpConfig;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Override
    public String verifyServer(String signature, String timestamp, String nonce, String echostr) {
        if (!isWechatMpConfigured()) {
            return "wechat mp login disabled";
        }
        if (!isSignatureValid(signature, timestamp, nonce)) {
            log.warn("微信公众号服务器校验失败: signature={}, timestamp={}, nonce={}", signature, timestamp, nonce);
            return "invalid";
        }
        return StringUtils.defaultString(echostr, "success");
    }

    @Override
    public String receiveMessage(String signature, String timestamp, String nonce, String requestBody) {
        if (!isWechatMpConfigured()) {
            return "success";
        }
        if (!isSignatureValid(signature, timestamp, nonce)) {
            log.warn("微信公众号消息签名校验失败");
            return "invalid";
        }
        if (StringUtils.isBlank(requestBody)) {
            return "success";
        }
        WxMpIncomingMessage message = parseIncomingMessage(requestBody);
        if (message == null) {
            return "success";
        }
        String replyContent = handleIncomingMessage(message);
        if (StringUtils.isBlank(replyContent)) {
            return "success";
        }
        return buildTextReply(message.getFromUserName(), message.getToUserName(), replyContent);
    }

    @Override
    public WxMpLoginTicketVO createLoginTicket(HttpServletRequest request) {
        ensureWechatMpEnabled();
        // 新模式：不再生成随机口令，只返回公众号展示信息（名称和二维码）
        // 用户扫码后直接发送"获取验证码"关键字，公众号会自动回复6位验证码
        WxMpLoginTicketVO vo = new WxMpLoginTicketVO();
        vo.setAccountName(StringUtils.defaultIfBlank(wechatMpConfig.getAccountName(), "公众号"));
        vo.setQrImageUrl(wechatMpConfig.getQrImageUrl());
        String loginKeyword = StringUtils.defaultIfBlank(wechatMpConfig.getLoginKeyword(), "登录");
        vo.setKeyword(loginKeyword); // 告知前端用户需要发送的关键字
        return vo;
    }

    @Override
    public WxMpLoginStatusVO getLoginStatus(HttpServletRequest request) {
        return getTicketStatus(request, WX_MP_LOGIN_TICKET_SESSION_KEY, "请先刷新页面获取登录口令", "登录口令已过期，请重新获取");
    }

    @Override
    public WxMpLoginTicketVO createBindTicket(HttpServletRequest request) {
        ensureWechatMpEnabled();
        userService.getLoginUser(request);
        WxMpLoginTicketVO vo = new WxMpLoginTicketVO();
        vo.setAccountName(StringUtils.defaultIfBlank(wechatMpConfig.getAccountName(), "公众号"));
        vo.setQrImageUrl(wechatMpConfig.getQrImageUrl());
        vo.setKeyword("绑定");
        return vo;
    }

    @Override
    public WxMpLoginStatusVO getBindStatus(HttpServletRequest request) {
        userService.getLoginUser(request);
        return buildStatus(STATUS_PENDING, false,
                "请在公众号中发送「绑定」获取 6 位验证码，然后回到网页输入完成绑定",
                null);
    }

    @Override
    public LoginUserVO loginByCode(WxMpCodeLoginRequest request, HttpServletRequest httpServletRequest) {
        ensureWechatMpEnabled();
        String code = StringUtils.trimToNull(request == null ? null : request.getCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入公众号验证码");
        }
        // 新模式：直接通过6位验证码在 Redis 中反查 openId
        String directCodeKey = RedisConstant.getWxMpDirectCodeRedisKey(code);
        DirectCodePayload directCodePayload = parseDirectCodePayload(stringRedisTemplate.opsForValue().get(directCodeKey));
        if (directCodePayload == null || StringUtils.isBlank(directCodePayload.getOpenId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公众号验证码不存在或已过期，请在公众号重新发送\"登录\"获取");
        }
        if (StringUtils.equals(directCodePayload.getScene(), SCENE_BIND)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "这是公众号绑定验证码，请回到账号安全页完成绑定");
        }
        // 验证码一次性使用，立即删除
        stringRedisTemplate.delete(directCodeKey);
        return userService.userLoginByMpOpenId(directCodePayload.getOpenId(), httpServletRequest);
    }

    @Override
    public LoginUserVO bindByCode(WxMpCodeLoginRequest request, HttpServletRequest httpServletRequest) {
        ensureWechatMpEnabled();
        String code = StringUtils.trimToNull(request == null ? null : request.getCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入公众号验证码");
        }
        String directCodeKey = RedisConstant.getWxMpDirectCodeRedisKey(code);
        DirectCodePayload directCodePayload = parseDirectCodePayload(stringRedisTemplate.opsForValue().get(directCodeKey));
        if (directCodePayload == null || StringUtils.isBlank(directCodePayload.getOpenId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公众号验证码不存在或已过期，请在公众号重新发送\"绑定\"获取");
        }
        if (StringUtils.equals(directCodePayload.getScene(), SCENE_LOGIN)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "这是公众号登录验证码，请在登录页使用");
        }
        Long loginUserId = userService.getLoginUser(httpServletRequest).getId();
        stringRedisTemplate.delete(directCodeKey);
        userService.bindMpOpenId(loginUserId, directCodePayload.getOpenId());
        return userService.getLoginUserVO(userService.getById(loginUserId));
    }

    private String handleIncomingMessage(WxMpIncomingMessage message) {
        String openId = StringUtils.trimToEmpty(message.getFromUserName());

        // 关注/取关事件
        if ("event".equalsIgnoreCase(message.getMsgType())) {
            // 默认欢迎语（如关注事件）
            return buildWelcomeText();
        }

        if (!"text".equalsIgnoreCase(message.getMsgType())) {
            return buildHelpText();
        }

        String normalizedContent = StringUtils.trimToEmpty(message.getContent()).replaceAll("\\s+", " ");
        String keyword = StringUtils.defaultIfBlank(wechatMpConfig.getLoginKeyword(), "登录");
        String bindKeyword = "绑定";

        // --- 新模式：用户直接发"登录"获取验证码 ---
        if (StringUtils.equals(normalizedContent, keyword)) {
            return generateAndReplyDirectCode(openId, SCENE_LOGIN);
        }

        // --- 新模式：用户直接发"绑定"获取绑定验证码 ---
        if (StringUtils.equals(normalizedContent, bindKeyword)) {
            return generateAndReplyDirectCode(openId, SCENE_BIND);
        }

        // --- 绑定场景：用户发"绑定 TICKET"（兼容旧的票据机制）---
        if (normalizedContent.startsWith(bindKeyword + " ")) {
            String ticket = StringUtils.trimToNull(normalizedContent.substring(bindKeyword.length()));
            if (StringUtils.isNotBlank(ticket)) {
                WxMpLoginTicketState state = getTicketState(ticket);
                if (state == null || isExpired(state)) {
                    return "这个绑定口令已经失效了，请回到网页重新获取。";
                }
                if (!SCENE_BIND.equals(state.getScene())) {
                    return "这个口令不是绑定口令，请发送正确的绑定口令。";
                }
                if (StringUtils.isBlank(state.getOpenId())) {
                    state.setOpenId(openId);
                } else if (!StringUtils.equals(state.getOpenId(), openId)) {
                    return "这个绑定口令已经被其他微信会话使用，请回到网页重新获取新的口令。";
                }
                String code = RandomUtil.randomNumbers(6);
                long expireAt = System.currentTimeMillis() + Duration.ofSeconds(wechatMpConfig.getCodeExpireSeconds()).toMillis();
                state.setCode(code);
                state.setStatus(STATUS_CODE_SENT);
                state.setExpireAt(expireAt);
                saveTicketState(state, Duration.ofSeconds(wechatMpConfig.getCodeExpireSeconds()));
                return String.format("你的智面绑定验证码为：%s，%d 分钟内有效。请回到网页输入完成绑定。",
                        code, Math.max(1, wechatMpConfig.getCodeExpireSeconds() / 60));
            }
        }

        // 纯数字：可能是回复验证码（不处理，提示帮助）
        return buildHelpText();
    }

    /**
     * 直接验证码模式：为指定 openId 生成6位验证码，存入 Redis 并返回回复文本
     */
    private String generateAndReplyDirectCode(String openId, String scene) {
        if (StringUtils.isBlank(openId)) {
            return "获取验证码失败，请稍后重试。";
        }
        // 生成不冲突的6位数字验证码，存入 Redis，key 为验证码，value 为 openId。
        String code = generateUniqueDirectCode();
        String redisKey = RedisConstant.getWxMpDirectCodeRedisKey(code);
        DirectCodePayload directCodePayload = new DirectCodePayload();
        directCodePayload.setOpenId(openId);
        directCodePayload.setScene(scene);
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(directCodePayload),
                Duration.ofSeconds(wechatMpConfig.getCodeExpireSeconds()));
        long minutes = Math.max(1, wechatMpConfig.getCodeExpireSeconds() / 60);
        return String.format("你的智面%s验证码为：%s，%d 分钟内有效。请在网页输入完成%s。",
                SCENE_BIND.equals(scene) ? "绑定" : "登录",
                code, minutes,
                SCENE_BIND.equals(scene) ? "绑定" : "登录");
    }

    private String generateUniqueDirectCode() {
        for (int i = 0; i < 10; i++) {
            String code = RandomUtil.randomNumbers(6);
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.getWxMpDirectCodeRedisKey(code)))) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成公众号验证码失败，请稍后重试");
    }

    private WxMpIncomingMessage parseIncomingMessage(String requestBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(requestBody)));
            Element root = document.getDocumentElement();
            WxMpIncomingMessage message = new WxMpIncomingMessage();
            message.setToUserName(extractTagValue(root, "ToUserName"));
            message.setFromUserName(extractTagValue(root, "FromUserName"));
            message.setMsgType(extractTagValue(root, "MsgType"));
            message.setContent(extractTagValue(root, "Content"));
            message.setEvent(extractTagValue(root, "Event"));
            message.setEventKey(extractTagValue(root, "EventKey"));
            return message;
        } catch (Exception e) {
            log.error("解析微信公众号回调 XML 失败", e);
            return null;
        }
    }

    private String extractTagValue(Element root, String tagName) {
        if (root == null || StringUtils.isBlank(tagName)) {
            return null;
        }
        if (root.getElementsByTagName(tagName).getLength() == 0) {
            return null;
        }
        return StringUtils.trimToNull(root.getElementsByTagName(tagName).item(0).getTextContent());
    }

    private String buildTextReply(String toUser, String fromUser, String content) {
        long currentTime = System.currentTimeMillis() / 1000;
        return "<xml>"
                + "<ToUserName><![CDATA[" + escapeCdata(toUser) + "]]></ToUserName>"
                + "<FromUserName><![CDATA[" + escapeCdata(fromUser) + "]]></FromUserName>"
                + "<CreateTime>" + currentTime + "</CreateTime>"
                + "<MsgType><![CDATA[text]]></MsgType>"
                + "<Content><![CDATA[" + escapeCdata(content) + "]]></Content>"
                + "</xml>";
    }

    private String escapeCdata(String raw) {
        return StringUtils.defaultString(raw).replace("]]>", "]]]]><![CDATA[>");
    }

    private String buildWelcomeText() {
        return "欢迎来到智面公众号助手。登录请发送「"
                + StringUtils.defaultIfBlank(wechatMpConfig.getLoginKeyword(), "登录")
                + "」获取验证码；绑定账号请发送「绑定」获取验证码。";
    }

    private String buildHelpText() {
        return "登录请发送「"
                + StringUtils.defaultIfBlank(wechatMpConfig.getLoginKeyword(), "登录")
                + "」获取验证码；绑定账号请发送「绑定」获取验证码。";
    }

    private String buildTicketKeyword(String scene, String ticket) {
        if (SCENE_BIND.equals(scene)) {
            return "绑定 " + ticket;
        }
        return StringUtils.defaultIfBlank(wechatMpConfig.getLoginKeyword(), "登录") + " " + ticket;
    }

    // extractTicket 方法已废弃，绑定口令解析已内置于 handleIncomingMessage 中

    private String generateUniqueTicket() {
        for (int i = 0; i < 5; i++) {
            String ticket = RandomUtil.randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 8);
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.getWxMpLoginTicketRedisKey(ticket)))) {
                return ticket;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成公众号登录口令失败，请稍后重试");
    }

    private WxMpLoginTicketState getTicketState(String ticket) {
        if (StringUtils.isBlank(ticket)) {
            return null;
        }
        String redisValue = stringRedisTemplate.opsForValue().get(RedisConstant.getWxMpLoginTicketRedisKey(ticket));
        if (StringUtils.isBlank(redisValue)) {
            return null;
        }
        return JSONUtil.toBean(redisValue, WxMpLoginTicketState.class);
    }

    private void saveTicketState(WxMpLoginTicketState state, Duration ttl) {
        stringRedisTemplate.opsForValue().set(
                RedisConstant.getWxMpLoginTicketRedisKey(state.getTicket()),
                JSONUtil.toJsonStr(state),
                ttl
        );
    }

    private boolean isExpired(WxMpLoginTicketState state) {
        return state == null || state.getExpireAt() == null || state.getExpireAt() <= System.currentTimeMillis();
    }

    private boolean isWechatMpConfigured() {
        return wechatMpConfig.isEnabled() && StringUtils.isNotBlank(wechatMpConfig.getToken());
    }

    private void ensureWechatMpEnabled() {
        if (!isWechatMpConfigured()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "公众号验证码登录尚未配置完成");
        }
    }

    private boolean isSignatureValid(String signature, String timestamp, String nonce) {
        if (StringUtils.isAnyBlank(signature, timestamp, nonce, wechatMpConfig.getToken())) {
            return false;
        }
        String[] values = {wechatMpConfig.getToken(), timestamp, nonce};
        Arrays.sort(values);
        String raw = String.join("", values);
        String expected = DigestUtil.sha1Hex(raw.getBytes(StandardCharsets.UTF_8));
        return StringUtils.equalsIgnoreCase(signature, expected);
    }

    private WxMpLoginTicketVO createTicket(HttpServletRequest request, String sessionKey, String scene, Long bindUserId) {
        String ticket = generateUniqueTicket();
        long expireAt = System.currentTimeMillis() + Duration.ofSeconds(wechatMpConfig.getTicketExpireSeconds()).toMillis();
        WxMpLoginTicketState state = new WxMpLoginTicketState();
        state.setTicket(ticket);
        state.setScene(scene);
        state.setBindUserId(bindUserId);
        state.setStatus(STATUS_PENDING);
        state.setExpireAt(expireAt);
        saveTicketState(state, Duration.ofSeconds(wechatMpConfig.getTicketExpireSeconds()));
        request.getSession(true).setAttribute(sessionKey, ticket);

        WxMpLoginTicketVO ticketVO = new WxMpLoginTicketVO();
        ticketVO.setTicket(ticket);
        ticketVO.setKeyword(buildTicketKeyword(scene, ticket));
        ticketVO.setExpireAt(expireAt);
        ticketVO.setAccountName(StringUtils.defaultIfBlank(wechatMpConfig.getAccountName(), "你的公众号"));
        ticketVO.setQrImageUrl(wechatMpConfig.getQrImageUrl());
        return ticketVO;
    }

    private WxMpLoginStatusVO getTicketStatus(HttpServletRequest request, String sessionKey, String emptyMessage, String expiredMessage) {
        String ticket = getCurrentTicket(request, sessionKey);
        if (StringUtils.isBlank(ticket)) {
            return buildStatus(STATUS_EXPIRED, false, emptyMessage, null);
        }
        WxMpLoginTicketState state = getTicketState(ticket);
        if (state == null || isExpired(state)) {
            clearCurrentTicket(request, sessionKey);
            return buildStatus(STATUS_EXPIRED, false, expiredMessage, null);
        }
        if (STATUS_USED.equals(state.getStatus())) {
            return buildStatus(STATUS_USED, true,
                    SCENE_BIND.equals(state.getScene()) ? "本次公众号绑定验证码已使用，请重新获取新的口令" : "本次公众号验证码已使用，请重新获取新的口令",
                    state.getExpireAt());
        }
        if (STATUS_CODE_SENT.equals(state.getStatus())) {
            return buildStatus(STATUS_CODE_SENT, true,
                    SCENE_BIND.equals(state.getScene()) ? "已向公众号对话发送绑定验证码，请在网页中输入" : "已向公众号对话发送验证码，请在网页中输入",
                    state.getExpireAt());
        }
        return buildStatus(STATUS_PENDING, false,
                SCENE_BIND.equals(state.getScene()) ? "请在公众号中发送页面展示的绑定口令" : "请在公众号中发送页面展示的登录口令",
                state.getExpireAt());
    }

    private String getCurrentTicket(HttpServletRequest request, String sessionKey) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null) {
            return null;
        }
        Object ticket = session.getAttribute(sessionKey);
        return ticket == null ? null : String.valueOf(ticket);
    }

    private void clearCurrentTicket(HttpServletRequest request, String sessionKey) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session != null) {
            session.removeAttribute(sessionKey);
        }
    }

    private WxMpLoginStatusVO buildStatus(String status, boolean codeSent, String message, Long expireAt) {
        WxMpLoginStatusVO statusVO = new WxMpLoginStatusVO();
        statusVO.setStatus(status);
        statusVO.setCodeSent(codeSent);
        statusVO.setMessage(message);
        statusVO.setExpireAt(expireAt);
        return statusVO;
    }

    private DirectCodePayload parseDirectCodePayload(String rawValue) {
        String value = StringUtils.trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        try {
            if (value.startsWith("{")) {
                JSONObject jsonObject = JSONUtil.parseObj(value);
                DirectCodePayload directCodePayload = new DirectCodePayload();
                directCodePayload.setOpenId(StringUtils.trimToNull(jsonObject.getStr("openId")));
                directCodePayload.setScene(StringUtils.trimToNull(jsonObject.getStr("scene")));
                return StringUtils.isBlank(directCodePayload.getOpenId()) ? null : directCodePayload;
            }
        } catch (Exception e) {
            log.warn("解析公众号验证码载荷失败，rawValue={}", value, e);
            return null;
        }
        DirectCodePayload directCodePayload = new DirectCodePayload();
        directCodePayload.setOpenId(value);
        return directCodePayload;
    }

    @Data
    private static class WxMpIncomingMessage {
        private String toUserName;
        private String fromUserName;
        private String msgType;
        private String content;
        private String event;
        private String eventKey;
    }

    @Data
    private static class DirectCodePayload {
        private String openId;
        private String scene;
    }

    @Data
    private static class WxMpLoginTicketState {
        private String ticket;
        private String scene;
        private Long bindUserId;
        private String openId;
        private String code;
        private String status;
        private Long expireAt;
    }
}
