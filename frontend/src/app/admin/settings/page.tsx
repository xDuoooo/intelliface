"use client";
import React, { useEffect, useState } from "react";
import { 
  Card, 
  Form, 
  Input, 
  Switch, 
  Button, 
  Typography,
  Divider, 
  message,
  Tabs,
  Alert,
  Skeleton
} from "antd";
import { 
  Settings as SettingsIcon, 
  ShieldCheck, 
  Bell, 
  Save,
  Monitor,
  Lock,
  Zap,
  BookOpen,
  MessageSquareText
} from "lucide-react";
import { APP_CONFIG } from "@/config/appConfig";
import { getSystemConfigUsingGet, updateSystemConfigUsingPost } from "@/api/systemConfigController";

const { Text } = Typography;

/**
 * 全局系统设置页面
 */
export default function AdminSettingsPage() {
  const [form] = Form.useForm<API.SystemConfigUpdateRequest>();
  const [loading, setLoading] = useState(false);
  const [initializing, setInitializing] = useState(true);

  const loadSystemConfig = async () => {
    setInitializing(true);
    try {
      const res = await getSystemConfigUsingGet();
      const data = res.data;
      form.setFieldsValue({
        siteName: data?.siteName ?? APP_CONFIG.adminDefaults.siteName,
        seoKeywords: data?.seoKeywords ?? APP_CONFIG.adminDefaults.seoKeywords,
        announcement: data?.announcement ?? APP_CONFIG.adminDefaults.announcement,
        allowRegister: data?.allowRegister ?? true,
        requireCaptcha: data?.requireCaptcha ?? true,
        maintenanceMode: data?.maintenanceMode ?? false,
        enableSiteNotification: data?.enableSiteNotification ?? true,
        enableEmailNotification: data?.enableEmailNotification ?? true,
        enableLearningGoalReminder: data?.enableLearningGoalReminder ?? true,
        allowGuestViewQuestion: data?.allowGuestViewQuestion ?? true,
        allowGuestViewPost: data?.allowGuestViewPost ?? true,
      });
    } catch (e: any) {
      message.error(e.message || "系统设置加载失败");
    } finally {
      setInitializing(false);
    }
  };

  useEffect(() => {
    loadSystemConfig();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onFinish = async (values: API.SystemConfigUpdateRequest) => {
    setLoading(true);
    try {
      await updateSystemConfigUsingPost(values);
      message.success("设置已更新并实时生效");
      await loadSystemConfig();
    } catch (e: any) {
      message.error(e.message || "设置保存失败");
    } finally {
      setLoading(false);
    }
  };

  const basicSettings = (
    <div className="space-y-6 animate-in slide-in-from-left-4 duration-500">
      <Form.Item label="站点名称" name="siteName" rules={[{ required: true, message: "请输入站点名称" }]}>
        <Input size="large" className="rounded-xl bg-slate-50 border-slate-100" />
      </Form.Item>
      <Form.Item label="SEO 关键词" name="seoKeywords">
        <Input size="large" className="rounded-xl bg-slate-50 border-slate-100" />
      </Form.Item>
      <Form.Item label="系统公告" name="announcement">
        <Input.TextArea rows={4} className="rounded-xl bg-slate-50 border-slate-100" />
      </Form.Item>
    </div>
  );

  const securitySettings = (
    <div className="space-y-8 animate-in slide-in-from-left-4 duration-500 pt-2">
      <div className="flex items-center justify-between">
         <div>
            <Text className="font-bold text-slate-800 block text-lg">开放注册</Text>
            <Text type="secondary">允许新用户自行注册账号</Text>
         </div>
         <Form.Item name="allowRegister" valuePropName="checked" noStyle>
            <Switch className="bg-slate-200" />
         </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between">
         <div>
            <Text className="font-bold text-slate-800 block text-lg">强制图形验证码</Text>
            <Text type="secondary">登录与注册时必须输入验证码（防刷）</Text>
         </div>
         <Form.Item name="requireCaptcha" valuePropName="checked" noStyle>
            <Switch className="bg-slate-200" />
         </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between">
         <div className="flex items-center gap-3">
            <div className="bg-amber-100 p-2 rounded-xl"><ShieldCheck className="h-5 w-5 text-amber-600" /></div>
            <div>
               <Text className="font-bold text-slate-800 block text-lg">系统维护模式</Text>
               <Text type="secondary">开启后仅管理员可新登录，验证码发送也只对管理员账号开放</Text>
            </div>
         </div>
         <Form.Item name="maintenanceMode" valuePropName="checked" noStyle>
            <Switch className="bg-slate-200" />
         </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between gap-4">
         <div className="flex items-center gap-3">
            <div className="bg-sky-100 p-2 rounded-xl"><BookOpen className="h-5 w-5 text-sky-600" /></div>
            <div>
               <Text className="font-bold text-slate-800 block text-lg">允许未登录访问题目</Text>
               <Text type="secondary">关闭后，游客访问题目列表、题目详情、题目搜索和题目评论时会被要求先登录。</Text>
            </div>
         </div>
         <Form.Item name="allowGuestViewQuestion" valuePropName="checked" noStyle>
            <Switch className="bg-slate-200" />
         </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between gap-4">
         <div className="flex items-center gap-3">
            <div className="bg-violet-100 p-2 rounded-xl"><MessageSquareText className="h-5 w-5 text-violet-600" /></div>
            <div>
               <Text className="font-bold text-slate-800 block text-lg">允许未登录访问论坛</Text>
               <Text type="secondary">关闭后，游客访问帖子列表、帖子详情、热门帖、精选帖和论坛评论时会被要求先登录。</Text>
            </div>
         </div>
         <Form.Item name="allowGuestViewPost" valuePropName="checked" noStyle>
            <Switch className="bg-slate-200" />
         </Form.Item>
      </div>
    </div>
  );

  const notificationSettings = (
    <div className="space-y-8 animate-in slide-in-from-left-4 duration-500 pt-2">
      <div className="flex items-center justify-between">
        <div>
          <Text className="font-bold text-slate-800 block text-lg">站内通知</Text>
          <Text type="secondary">控制评论、关注、审核结果等站内通知是否入库并展示给用户。</Text>
        </div>
        <Form.Item name="enableSiteNotification" valuePropName="checked" noStyle>
          <Switch className="bg-slate-200" />
        </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between">
        <div>
          <Text className="font-bold text-slate-800 block text-lg">邮件提醒</Text>
          <Text type="secondary">控制学习目标提醒邮件是否发送，不影响验证码和事务性邮件。</Text>
        </div>
        <Form.Item name="enableEmailNotification" valuePropName="checked" noStyle>
          <Switch className="bg-slate-200" />
        </Form.Item>
      </div>
      <Divider className="my-0 border-slate-100" />
      <div className="flex items-center justify-between">
        <div>
          <Text className="font-bold text-slate-800 block text-lg">学习目标提醒任务</Text>
          <Text type="secondary">关闭后，晚 8 点的刷题目标提醒任务将整体停用。</Text>
        </div>
        <Form.Item name="enableLearningGoalReminder" valuePropName="checked" noStyle>
          <Switch className="bg-slate-200" />
        </Form.Item>
      </div>
    </div>
  );

  return (
    <div className="max-w-5xl mx-auto space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      {/* Hero Header */}
      <section className="bg-white/70 backdrop-blur-xl rounded-[3rem] p-10 sm:p-14 border border-white shadow-2xl shadow-slate-200/50 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-12 opacity-5 rotate-12">
           <Zap className="h-40 w-40 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-4">
           <div className="inline-flex items-center gap-2 bg-primary/10 text-primary px-4 py-2 rounded-full font-black text-xs uppercase tracking-widest">
              <SettingsIcon className="h-3 w-3" />
              Dynamic Control Center
           </div>
           <h1 className="text-3xl sm:text-4xl font-black text-slate-900 tracking-tight">全局动态设置</h1>
           <p className="text-slate-500 font-medium max-w-xl text-lg">
             无需重启服务器即可实时修改平台核心参数，灵活、高效应对各种业务场景。
           </p>
        </div>
      </section>

      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Card className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 overflow-hidden">
          <div className="px-8 pt-8">
            <Alert
              type="info"
              showIcon
              className="rounded-2xl"
              message="这页配置已经接入真实后端，安全策略会直接作用到登录、验证码发送和注册链路。"
              description="题目 / 论坛的游客访问开关也在这里统一控制，保存后会实时生效。"
            />
          </div>
          <Tabs 
            className="admin-settings-tabs"
            defaultActiveKey="1"
            tabBarExtraContent={
              <Button 
                type="primary" 
                htmlType="submit" 
                loading={loading}
                className="h-12 px-8 rounded-xl font-black bg-primary border-none shadow-lg shadow-primary/25 flex items-center gap-2"
              >
                <Save className="h-4 w-4" /> 保存所有更改
              </Button>
            }
            items={[
              {
                key: '1',
                label: <span className="flex items-center gap-2 px-4 py-2 font-bold"><Monitor className="h-4 w-4" /> 基础配置</span>,
                children: <div className="p-8 pb-12">{initializing ? <Skeleton active paragraph={{ rows: 6 }} /> : basicSettings}</div>,
              },
              {
                key: '2',
                label: <span className="flex items-center gap-2 px-4 py-2 font-bold"><Lock className="h-4 w-4" /> 安全策略</span>,
                children: <div className="p-8 pb-12">{initializing ? <Skeleton active paragraph={{ rows: 5 }} /> : securitySettings}</div>,
              },
              {
                key: '3',
                label: <span className="flex items-center gap-2 px-4 py-2 font-bold"><Bell className="h-4 w-4" /> 消息推送</span>,
                children: <div className="p-8 pb-12">{initializing ? <Skeleton active paragraph={{ rows: 5 }} /> : notificationSettings}</div>,
              },
            ]}
          />
        </Card>
      </Form>
    </div>
  );
}
