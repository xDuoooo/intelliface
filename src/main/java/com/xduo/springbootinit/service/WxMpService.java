package com.xduo.springbootinit.service;

import com.xduo.springbootinit.model.dto.wxmp.WxMpCodeLoginRequest;
import com.xduo.springbootinit.model.vo.LoginUserVO;
import com.xduo.springbootinit.model.vo.WxMpLoginStatusVO;
import com.xduo.springbootinit.model.vo.WxMpLoginTicketVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 微信公众号验证码登录服务
 */
public interface WxMpService {

    /**
     * 处理微信服务器校验请求
     */
    String verifyServer(String signature, String timestamp, String nonce, String echostr);

    /**
     * 处理公众号回调消息
     */
    String receiveMessage(String signature, String timestamp, String nonce, String requestBody);

    /**
     * 创建网页登录票据
     */
    WxMpLoginTicketVO createLoginTicket(HttpServletRequest request);

    /**
     * 查询当前会话的票据状态
     */
    WxMpLoginStatusVO getLoginStatus(HttpServletRequest request);

    /**
     * 创建公众号绑定口令
     */
    WxMpLoginTicketVO createBindTicket(HttpServletRequest request);

    /**
     * 查询当前会话的公众号绑定状态
     */
    WxMpLoginStatusVO getBindStatus(HttpServletRequest request);

    /**
     * 使用公众号验证码登录
     */
    LoginUserVO loginByCode(WxMpCodeLoginRequest request, HttpServletRequest httpServletRequest);

    /**
     * 使用公众号验证码绑定当前账号
     */
    LoginUserVO bindByCode(WxMpCodeLoginRequest request, HttpServletRequest httpServletRequest);

}
