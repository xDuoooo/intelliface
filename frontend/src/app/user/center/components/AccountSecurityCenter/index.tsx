"use client";
import React, { useEffect, useMemo, useState } from "react";
import { Button, Input, List, Modal, Tag, message, Typography } from "antd";
import { 
  ShieldCheck, 
  Key, 
  Phone, 
  Mail, 
  MessageCircle,
  Unlink,
  RefreshCw,
  AlertTriangle
} from "lucide-react";
import Image from "next/image";
import { 
  bindEmailUsingPost,
  bindPhoneUsingPost,
  deleteMyAccountUsingPost,
  getLoginUserUsingGet,
  sendVerificationCodeUsingPost,
  unbindEmailUsingPost,
  unbindGithubUsingPost, 
  unbindGiteeUsingPost, 
  unbindGoogleUsingPost,
  unbindMpUsingPost,
  unbindPhoneUsingPost,
  updateMyUserUsingPost,
} from "@/api/userController";
import {
  bindByWxMpCodeUsingPost,
  createWxMpBindTicketUsingPost,
  type WxMpLoginStatusVO,
  type WxMpLoginTicketVO,
} from "@/api/wxMpController";
import { useDispatch } from "react-redux";
import { AppDispatch } from "@/stores";
import { setLoginUser } from "@/stores/loginUser";
import { getSocialAuthProviderLabel, getSocialAuthUrl } from "@/config/auth";
import PasswordChangeForm from "../PasswordChangeForm";
import request from "@/libs/request";
import { DEFAULT_USER } from "@/constants/user";
import { useRouter } from "next/navigation";

const { Text } = Typography;

interface Props {
  user: API.LoginUserVO;
}

/**
 * 账号安全中心组件
 */
const AccountSecurityCenter: React.FC<Props> = ({ user }) => {
  const router = useRouter();
  const dispatch = useDispatch<AppDispatch>();
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [bindModalVisible, setBindModalVisible] = useState(false);
  const [accountModalVisible, setAccountModalVisible] = useState(false);
  const [bindType, setBindType] = useState<"phone" | "email" | null>(null);
  const [bindTarget, setBindTarget] = useState("");
  const [bindCode, setBindCode] = useState("");
  const [accountValue, setAccountValue] = useState("");
  const [captchaInput, setCaptchaInput] = useState("");
  const [captchaData, setCaptchaData] = useState<{ image: string; uuid: string } | null>(null);
  const [sendCodeLoading, setSendCodeLoading] = useState(false);
  const [bindLoading, setBindLoading] = useState(false);
  const [accountLoading, setAccountLoading] = useState(false);
  const [unbindLoadingType, setUnbindLoadingType] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(0);
  const [wxMpBindModalVisible, setWxMpBindModalVisible] = useState(false);
  const [wxMpBindCode, setWxMpBindCode] = useState("");
  const [wxMpBindTicketInfo, setWxMpBindTicketInfo] = useState<WxMpLoginTicketVO | null>(null);
  const [wxMpBindStatus, setWxMpBindStatus] = useState<WxMpLoginStatusVO | null>(null);
  const [wxMpBindTicketLoading, setWxMpBindTicketLoading] = useState(false);
  const [wxMpBindLoading, setWxMpBindLoading] = useState(false);
  const hasPasswordConfigured = Number(user.passwordConfigured || 0) === 1;

  // 脱敏显示
  const maskPhone = (phone?: string) => phone ? phone.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2") : "未绑定";
  const maskEmail = (email?: string) => email ? email.replace(/(.{2}).+(.{2}@.+)/, "$1****$2") : "未绑定";

  const bindConfig = useMemo(() => {
    if (bindType === "phone") {
      return {
        label: "手机号",
        type: 2,
        placeholder: "请输入 11 位手机号",
        actionText: user.phone ? "更换手机号" : "绑定手机号",
        pattern: /^1[3-9]\d{9}$/,
        invalidMessage: "请输入有效的 11 位手机号",
      };
    }
    return {
      label: "邮箱",
      type: 1,
      placeholder: "请输入常用邮箱",
      actionText: user.email ? "更换邮箱" : "绑定邮箱",
      pattern: /^[\w.+-]+@[\w-]+\.[\w.]+$/,
      invalidMessage: "请输入有效的邮箱地址",
    };
  }, [bindType, user.email, user.phone]);

  const refreshLoginUser = async () => {
    const res = await getLoginUserUsingGet();
    if (res.data) {
      const latestUser = res.data as API.LoginUserVO;
      dispatch(setLoginUser(latestUser));
      return latestUser;
    }
    return undefined;
  };

  const refreshCaptcha = async () => {
    try {
      const res: any = await request.get("/api/captcha/get");
      if (res.code === 0) {
        setCaptchaData(res.data);
      }
    } catch (error) {
      console.error("获取图形验证码失败", error);
      message.error("获取图形验证码失败，请稍后再试");
    }
  };

  useEffect(() => {
    if (!bindModalVisible) {
      return;
    }
    void refreshCaptcha();
    setBindCode("");
    setCaptchaInput("");
  }, [bindModalVisible, bindType]);

  useEffect(() => {
    if (countdown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => {
      setCountdown((current) => current - 1);
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const openBindModal = (type: "phone" | "email") => {
    setBindType(type);
    setBindTarget(type === "phone" ? user.phone || "" : user.email || "");
    setBindCode("");
    setCaptchaInput("");
    setCountdown(0);
    setBindModalVisible(true);
  };

  const openWxMpBindModal = () => {
    setWxMpBindCode("");
    setWxMpBindTicketInfo(null);
    setWxMpBindStatus(null);
    setWxMpBindModalVisible(true);
  };

  const openAccountModal = () => {
    setAccountValue(user.userAccount || "");
    setAccountModalVisible(true);
  };

  const createWxMpBindTicket = async (silent = false) => {
    setWxMpBindTicketLoading(true);
    try {
      const res = await createWxMpBindTicketUsingPost();
      setWxMpBindTicketInfo(res.data ?? null);
      setWxMpBindStatus({
        status: "pending",
        codeSent: false,
        message: "请在公众号中发送「绑定」获取 6 位验证码，然后回到这里输入完成绑定",
      });
      setWxMpBindCode("");
    } catch (error: any) {
      setWxMpBindTicketInfo(null);
      setWxMpBindStatus({
        status: "expired",
        codeSent: false,
        message: error?.message || "获取绑定口令失败，请稍后重试",
      });
      if (!silent) {
        message.error(error?.message || "获取绑定口令失败");
      }
    } finally {
      setWxMpBindTicketLoading(false);
    }
  };

  useEffect(() => {
    if (!wxMpBindModalVisible || wxMpBindTicketInfo || wxMpBindTicketLoading || wxMpBindStatus) {
      return;
    }
    void createWxMpBindTicket(true);
  }, [wxMpBindModalVisible, wxMpBindTicketInfo, wxMpBindTicketLoading, wxMpBindStatus]);

  const handleSendCode = async () => {
    const target = bindTarget.trim();
    if (!bindType) {
      return;
    }
    if (!target) {
      message.warning(`请先输入${bindConfig.label}`);
      return;
    }
    if (!bindConfig.pattern.test(target)) {
      message.warning(bindConfig.invalidMessage);
      return;
    }
    if (!captchaInput.trim()) {
      message.warning("请先输入图形验证码");
      return;
    }
    setSendCodeLoading(true);
    try {
      await sendVerificationCodeUsingPost({
        target,
        type: bindConfig.type,
        captcha: captchaInput.trim(),
        captchaUuid: captchaData?.uuid,
      });
      message.success("验证码已发送，请注意查收");
      setCountdown(60);
      setCaptchaInput("");
      await refreshCaptcha();
    } catch (error: any) {
      message.error(error?.message || "发送验证码失败");
      setCaptchaInput("");
      await refreshCaptcha();
    } finally {
      setSendCodeLoading(false);
    }
  };

  const handleBindSubmit = async () => {
    const target = bindTarget.trim();
    const code = bindCode.trim();
    if (!bindType) {
      return;
    }
    if (!target) {
      message.warning(`请先输入${bindConfig.label}`);
      return;
    }
    if (!bindConfig.pattern.test(target)) {
      message.warning(bindConfig.invalidMessage);
      return;
    }
    if (!code) {
      message.warning("请输入验证码");
      return;
    }
    setBindLoading(true);
    try {
      if (bindType === "phone") {
        await bindPhoneUsingPost({ target, code });
      } else {
        await bindEmailUsingPost({ target, code });
      }
      await refreshLoginUser();
      message.success(`${bindConfig.actionText}成功`);
      setBindModalVisible(false);
      setBindCode("");
      setCaptchaInput("");
    } catch (error: any) {
      message.error(error?.message || `${bindConfig.actionText}失败`);
    } finally {
      setBindLoading(false);
    }
  };

  const handleUnbindContact = async (type: "phone" | "email") => {
    const label = type === "phone" ? "手机号" : "邮箱";
    Modal.confirm({
      title: `确认解绑${label}`,
      content: `解绑后你将不能再通过${label}验证码登录，请确认当前账号还保留至少一种其他登录方式。`,
      okButtonProps: { danger: true, loading: unbindLoadingType === type },
      onOk: async () => {
        setUnbindLoadingType(type);
        try {
          if (type === "phone") {
            await unbindPhoneUsingPost();
          } else {
            await unbindEmailUsingPost();
          }
          await refreshLoginUser();
          message.success(`${label}解绑成功`);
        } catch (error: any) {
          message.error(error?.message || `${label}解绑失败`);
        } finally {
          setUnbindLoadingType(null);
        }
      },
    });
  };

  const handleWxMpBindSubmit = async () => {
    const code = wxMpBindCode.trim();
    if (!code) {
      message.warning("请输入公众号验证码");
      return;
    }
    setWxMpBindLoading(true);
    try {
      await bindByWxMpCodeUsingPost({ code });
      await refreshLoginUser();
      message.success("公众号绑定成功");
      setWxMpBindModalVisible(false);
      setWxMpBindCode("");
      setWxMpBindTicketInfo(null);
      setWxMpBindStatus(null);
    } catch (error: any) {
      message.error(error?.message || "公众号绑定失败");
    } finally {
      setWxMpBindLoading(false);
    }
  };

  const handleUnbindMp = () => {
    Modal.confirm({
      title: "确认解绑公众号",
      content: "解绑后你将不能再通过公众号验证码登录，请确认当前账号仍保留至少一种其他登录方式。",
      okButtonProps: { danger: true, loading: unbindLoadingType === "mp" },
      onOk: async () => {
        setUnbindLoadingType("mp");
        try {
          await unbindMpUsingPost();
          await refreshLoginUser();
          message.success("公众号解绑成功");
        } catch (error: any) {
          message.error(error?.message || "公众号解绑失败");
        } finally {
          setUnbindLoadingType(null);
        }
      },
    });
  };

  const handleUpdateAccount = async () => {
    const nextAccount = accountValue.trim();
    if (!nextAccount) {
      message.warning("请输入登录账号");
      return;
    }
    if (nextAccount.length < 4 || nextAccount.length > 20) {
      message.warning("账号长度需在 4 到 20 个字符之间");
      return;
    }
    if (!/^[A-Za-z0-9_]+$/.test(nextAccount)) {
      message.warning("账号仅支持字母、数字和下划线");
      return;
    }
    setAccountLoading(true);
    try {
      await updateMyUserUsingPost({ userAccount: nextAccount });
      await refreshLoginUser();
      setAccountModalVisible(false);
      message.success("登录账号修改成功");
    } catch (error: any) {
      message.error(error?.message || "登录账号修改失败");
    } finally {
      setAccountLoading(false);
    }
  };

  const handleDeleteMyAccount = () => {
    Modal.confirm({
      title: "确认注销当前账号",
      content:
        "注销后你的登录状态会立即失效，个人资料将无法恢复。已发布的公开内容会保留，但账号本身会被标记为已注销。",
      okText: "确认注销",
      cancelText: "取消",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMyAccountUsingPost();
          dispatch(setLoginUser(DEFAULT_USER));
          message.success("账号已注销");
          router.replace("/");
        } catch (error: any) {
          message.error(error?.message || "注销账号失败");
        }
      },
    });
  };

  // 解绑逻辑
  const handleUnbind = async (type: "github" | "gitee" | "google") => {
    const providerLabel = getSocialAuthProviderLabel(type);
    Modal.confirm({
      title: "确认解绑",
      content: `确定要解绑您的 ${providerLabel} 账号吗？解绑后将无法通过该方式登录。`,
      okButtonProps: { danger: true, loading: unbindLoadingType === type },
      onOk: async () => {
        setUnbindLoadingType(type);
        try {
          if (type === "github") await unbindGithubUsingPost();
          if (type === "gitee") await unbindGiteeUsingPost();
          if (type === "google") await unbindGoogleUsingPost();
          const latestUser = await refreshLoginUser();
          const onlyPasswordLoginLeft =
            Number(latestUser?.passwordConfigured || 0) === 1 &&
            !latestUser?.phone &&
            !latestUser?.email &&
            !latestUser?.mpOpenId &&
            !latestUser?.githubId &&
            !latestUser?.giteeId &&
            !latestUser?.googleId;
          if (onlyPasswordLoginLeft && latestUser?.userAccount) {
            message.success(`解绑成功，后续请使用账号 ${latestUser.userAccount} 和密码登录`);
          } else {
            message.success("解绑成功");
          }
        } catch (error: any) {
          message.error("解绑失败：" + (error?.message || "请稍后重试"));
        } finally {
          setUnbindLoadingType(null);
        }
      },
    });
  };

  const securityItems = [
    {
      key: "account",
      title: "登录账号",
      description: user.userAccount
        ? `当前登录账号：${user.userAccount}`
        : "当前账号暂无可展示的登录账号",
      status: user.userAccount
        ? <Tag color={hasPasswordConfigured ? "processing" : "default"}>{hasPasswordConfigured ? "可用于密码登录" : "已生成账号"}</Tag>
        : <Tag>未生成</Tag>,
      icon: <Key size={20} className="text-slate-500" />,
      action: user.userAccount ? (
        <div className="flex items-center gap-2">
          <Button type="link" onClick={openAccountModal}>修改账号</Button>
          <Text
            copyable={{ text: user.userAccount, tooltips: ["复制账号", "已复制"] }}
            className="text-sm text-primary"
          >
            复制账号
          </Text>
        </div>
      ) : (
        <Button type="link" onClick={openAccountModal}>设置账号</Button>
      ),
    },
    {
      key: "password",
      title: "登录密码",
      description: hasPasswordConfigured
        ? "定期更换密码可以提高账号安全性"
        : "当前账号尚未设置独立密码，建议先设置密码再解绑第三方账号",
      status: hasPasswordConfigured ? <Tag color="success">已设置</Tag> : <Tag color="warning">未设置</Tag>,
      icon: <Key size={20} className="text-blue-500" />,
      action: <Button type="link" onClick={() => setPasswordModalVisible(true)}>{hasPasswordConfigured ? "修改密码" : "设置密码"}</Button>
    },
    {
      key: "phone",
      title: "手机绑定",
      description: `当前绑定：${maskPhone(user.phone)}`,
      status: user.phone ? <Tag color="success">已绑定</Tag> : <Tag>未绑定</Tag>,
      icon: <Phone size={20} className="text-green-500" />,
      action: user.phone ? (
        <div className="flex items-center gap-2">
          <Button type="link" onClick={() => openBindModal("phone")}>更换手机</Button>
          <Button type="text" danger icon={<Unlink size={16} />} onClick={() => handleUnbindContact("phone")} />
        </div>
      ) : (
        <Button type="link" onClick={() => openBindModal("phone")}>立即绑定</Button>
      )
    },
    {
      key: "email",
      title: "邮箱绑定",
      description: `当前绑定：${maskEmail(user.email)}`,
      status: user.email ? <Tag color="success">已绑定</Tag> : <Tag>未绑定</Tag>,
      icon: <Mail size={20} className="text-orange-500" />,
      action: user.email ? (
        <div className="flex items-center gap-2">
          <Button type="link" onClick={() => openBindModal("email")}>更换邮箱</Button>
          <Button type="text" danger icon={<Unlink size={16} />} onClick={() => handleUnbindContact("email")} />
        </div>
      ) : (
        <Button type="link" onClick={() => openBindModal("email")}>立即绑定</Button>
      )
    },
    {
      key: "github",
      title: "GitHub 账号",
      description: user.githubId ? "已关联 GitHub 账号" : "关联后支持快捷登录",
      status: user.githubId ? <Tag color="success">已关联</Tag> : <Tag>未关联</Tag>,
      icon: <Image src="/assets/github-logo.png" width={20} height={20} alt="GitHub" />,
      action: user.githubId ? (
        <Button title="解绑" type="text" danger icon={<Unlink size={16}/>} onClick={() => handleUnbind("github")} />
      ) : (
        <Button type="link" href={getSocialAuthUrl("github", "bind")}>立即关联</Button>
      )
    },
    {
      key: "gitee",
      title: "Gitee 账号",
      description: user.giteeId ? "已关联 Gitee 账号" : "关联后支持快捷登录",
      status: user.giteeId ? <Tag color="success">已关联</Tag> : <Tag>未关联</Tag>,
      icon: <Image src="/assets/gitee-logo.png" width={20} height={20} alt="Gitee" />,
      action: user.giteeId ? (
        <Button title="解绑" type="text" danger icon={<Unlink size={16}/>} onClick={() => handleUnbind("gitee")} />
      ) : (
        <Button type="link" href={getSocialAuthUrl("gitee", "bind")}>立即关联</Button>
      )
    },
    {
      key: "google",
      title: "Google 账号",
      description: user.googleId ? "已关联 Google 账号" : "关联后支持快捷登录",
      status: user.googleId ? <Tag color="success">已关联</Tag> : <Tag>未关联</Tag>,
      icon: <Image src="/assets/google-logo.png" width={20} height={20} alt="Google" />,
      action: user.googleId ? (
        <Button title="解绑" type="text" danger icon={<Unlink size={16}/>} onClick={() => handleUnbind("google")} />
      ) : (
        <Button type="link" href={getSocialAuthUrl("google", "bind")}>立即关联</Button>
      )
    },
    {
      key: "wxMp",
      title: "公众号登录",
      description: user.mpOpenId ? "已关联公众号，可通过公众号验证码快捷登录" : "关联后支持公众号验证码快捷登录",
      status: user.mpOpenId ? <Tag color="success">已关联</Tag> : <Tag>未关联</Tag>,
      icon: <MessageCircle size={20} className="text-emerald-500" />,
      action: user.mpOpenId ? (
        <Button title="解绑" type="text" danger icon={<Unlink size={16}/>} onClick={handleUnbindMp} />
      ) : (
        <Button type="link" onClick={openWxMpBindModal}>立即关联</Button>
      )
    },
    {
      key: "danger",
      title: "危险操作",
      description: user.userRole === "admin"
        ? "管理员账号不支持在个人中心直接注销，请通过后台统一处理。"
        : "如果你确认不再使用当前账号，可以在这里执行注销。该操作不可恢复。",
      status: <Tag color="error">高风险</Tag>,
      icon: <AlertTriangle size={20} className="text-red-500" />,
      action: (
        <Button
          danger
          type="primary"
          onClick={handleDeleteMyAccount}
          disabled={user.userRole === "admin"}
        >
          注销账号
        </Button>
      ),
    }
  ];

  return (
    <div className="account-security-center">
      <div className="flex items-center gap-3 mb-8 px-2">
        <div className="p-3 bg-blue-50 rounded-2xl">
          <ShieldCheck size={28} className="text-primary" />
        </div>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>账号安全中心</Typography.Title>
          <Text type="secondary" className="text-sm">管理您的账号安全与第三方服务关联</Text>
        </div>
      </div>

      <List
        className="bg-white rounded-2xl border-none"
        itemLayout="horizontal"
        dataSource={securityItems}
        renderItem={(item) => (
          <List.Item
            className="px-4 py-6 hover:bg-slate-50/50 transition-colors border-b border-slate-100 last:border-b-0"
          >
            <div className="flex w-full min-w-0 flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div className="flex min-w-0 flex-1 items-start gap-3 sm:gap-4">
                <div className="shrink-0 rounded-xl border border-slate-100 bg-white p-2.5 shadow-sm">
                  {item.icon}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-slate-800">
                    <span className="whitespace-nowrap font-semibold">{item.title}</span>
                    {item.status}
                  </div>
                  <div className="mt-1 text-sm leading-6 text-slate-500 break-words">
                    {item.description}
                  </div>
                </div>
              </div>
              <div className="flex min-w-0 flex-wrap items-center gap-2 sm:ml-4 sm:max-w-[45%] sm:justify-end">
                {item.action}
              </div>
            </div>
          </List.Item>
        )}
      />

      <Modal
        title="修改登录账号"
        open={accountModalVisible}
        onCancel={() => setAccountModalVisible(false)}
        onOk={handleUpdateAccount}
        okText="保存账号"
        cancelText="取消"
        confirmLoading={accountLoading}
        destroyOnClose
        centered
      >
        <div className="space-y-4 pt-4">
          <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-500">
            登录账号用于账号密码登录。建议改成你容易记住的名称，支持字母、数字和下划线。
          </div>
          <Input
            value={accountValue}
            placeholder="例如：xduo_java"
            maxLength={20}
            onChange={(e) => setAccountValue(e.target.value)}
          />
        </div>
      </Modal>

      <Modal
        title={hasPasswordConfigured ? "修改登录密码" : "设置登录密码"}
        open={passwordModalVisible}
        onCancel={() => setPasswordModalVisible(false)}
        footer={null}
        destroyOnClose
        centered
      >
        <div className="pt-4">
          <PasswordChangeForm
            passwordConfigured={hasPasswordConfigured}
            onSuccess={() => setPasswordModalVisible(false)}
          />
        </div>
      </Modal>

      <Modal
        title={bindConfig.actionText}
        open={bindModalVisible}
        onCancel={() => setBindModalVisible(false)}
        onOk={handleBindSubmit}
        okText={bindConfig.actionText}
        cancelText="取消"
        confirmLoading={bindLoading}
        destroyOnClose
        centered
      >
        <div className="space-y-4 pt-4">
          <div className="space-y-2">
            <Text strong>{bindConfig.label}</Text>
            <Input
              value={bindTarget}
              placeholder={bindConfig.placeholder}
              onChange={(e) => setBindTarget(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Text strong>图形验证码</Text>
            <div className="flex items-center gap-3">
              <Input
                value={captchaInput}
                placeholder="请输入图形验证码"
                onChange={(e) => setCaptchaInput(e.target.value)}
              />
              {captchaData ? (
                <Image
                  src={captchaData.image}
                  alt="captcha"
                  width={130}
                  height={48}
                  className="h-12 w-[130px] cursor-pointer rounded-lg border border-slate-200 bg-white object-cover"
                  onClick={() => void refreshCaptcha()}
                />
              ) : (
                <Button icon={<RefreshCw size={14} />} onClick={() => void refreshCaptcha()}>
                  刷新
                </Button>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Text strong>短信 / 邮件验证码</Text>
            <div className="flex items-center gap-3">
              <Input
                value={bindCode}
                placeholder="请输入 6 位验证码"
                onChange={(e) => setBindCode(e.target.value)}
              />
              <Button loading={sendCodeLoading} disabled={countdown > 0} onClick={handleSendCode}>
                {countdown > 0 ? `${countdown}s 后重试` : "发送验证码"}
              </Button>
            </div>
          </div>

          <Text type="secondary" className="text-xs">
            绑定后可作为登录方式与安全找回方式使用。
          </Text>
        </div>
      </Modal>

      <Modal
        title="绑定公众号登录"
        open={wxMpBindModalVisible}
        onCancel={() => {
          setWxMpBindModalVisible(false);
          setWxMpBindTicketInfo(null);
          setWxMpBindStatus(null);
          setWxMpBindCode("");
        }}
        onOk={handleWxMpBindSubmit}
        okText="确认绑定"
        cancelText="取消"
        confirmLoading={wxMpBindLoading}
        destroyOnClose
        centered
      >
        <div className="space-y-4 pt-4">
          <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-500">
            打开公众号会话后发送固定关键词「绑定」，收到 6 位验证码后回到这里输入，就能把公众号绑定到当前账号。
          </div>

          <div className="flex items-center justify-between gap-3 rounded-2xl border border-slate-100 bg-white px-4 py-3">
            <div>
              <div className="text-sm font-semibold text-slate-800">
                {wxMpBindTicketInfo?.accountName || "你的公众号"}
              </div>
              <Text type="secondary" className="text-xs">
                若二维码失效，可重新加载公众号信息
              </Text>
            </div>
            <Button
              type="link"
              loading={wxMpBindTicketLoading}
              onClick={() => void createWxMpBindTicket()}
            >
              {wxMpBindTicketLoading ? "刷新中..." : "刷新信息"}
            </Button>
          </div>

          <div className="flex flex-col items-center gap-3 rounded-3xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-6">
            {wxMpBindTicketInfo?.qrImageUrl ? (
              <Image
                src={wxMpBindTicketInfo.qrImageUrl}
                alt="公众号二维码"
                width={168}
                height={168}
                className="rounded-2xl border border-slate-200 bg-white p-2"
              />
            ) : (
              <div className="flex h-44 w-44 items-center justify-center rounded-2xl border border-slate-200 bg-white text-sm text-slate-400">
                暂无公众号二维码
              </div>
            )}

            <div className="text-center">
              <Text type="secondary" className="text-xs uppercase tracking-[0.18em]">
                发送给公众号的关键词
              </Text>
              <div className="mt-2 rounded-2xl bg-white px-4 py-3 font-mono text-lg font-semibold tracking-[0.18em] text-slate-800 shadow-sm">
                {wxMpBindTicketInfo?.keyword || "绑定"}
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-3 text-sm text-emerald-700">
            {wxMpBindStatus?.message || "请在公众号中发送「绑定」获取验证码"}
          </div>

          <div className="space-y-2">
            <Text strong>公众号验证码</Text>
            <Input
              value={wxMpBindCode}
              placeholder="请输入 6 位验证码"
              maxLength={6}
              onChange={(e) => setWxMpBindCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
          </div>

          <Text type="secondary" className="text-xs">
            绑定成功后，你可以直接通过公众号验证码登录这个账号，也能在保留其他登录方式时安全解绑。
          </Text>
        </div>
      </Modal>
    </div>
  );
};

export default AccountSecurityCenter;
