"use client";

import Image from "next/image";
import { Button, Form, Input, Select, Typography, Upload, message } from "antd";
import type { UploadChangeParam } from "antd/es/upload";
import { Camera, Loader2, UploadCloud } from "lucide-react";
import { CITY_GROUP_OPTIONS } from "@/config/cityOptions";
import { buildApiUrl } from "@/libs/request";
import { validateImageSrc } from "@/lib/utils";
import React from "react";

const { Text } = Typography;

export const CAREER_DIRECTION_OPTIONS = [
  { label: "Java 后端", value: "Java 后端" },
  { label: "前端开发", value: "前端开发" },
  { label: "大数据 / 数据开发", value: "大数据 / 数据开发" },
  { label: "算法工程师", value: "算法工程师" },
  { label: "测试开发", value: "测试开发" },
  { label: "运维 / 云原生", value: "运维 / 云原生" },
  { label: "产品 / 运营技术", value: "产品 / 运营技术" },
];

const USER_ROLE_OPTIONS = [
  { label: "普通用户", value: "user" },
  { label: "管理员", value: "admin" },
  { label: "封禁用户", value: "ban" },
];

type Props = {
  mode: "create" | "update";
  avatarUrl: string;
  setAvatarUrl: (value: string) => void;
};

export default function UserFormFields({ mode, avatarUrl, setAvatarUrl }: Props) {
  const form = Form.useFormInstance();
  const [uploadLoading, setUploadLoading] = React.useState(false);

  const beforeUpload = (file: File) => {
    const isSupportedType =
      file.type === "image/jpeg" || file.type === "image/png" || file.type === "image/webp";
    if (!isSupportedType) {
      message.error("仅支持 JPG/PNG/WebP 格式图片");
    }
    const isLt1M = file.size / 1024 / 1024 < 1;
    if (!isLt1M) {
      message.error("图片大小不能超过 1MB");
    }
    return isSupportedType && isLt1M;
  };

  const handleUploadChange = (info: UploadChangeParam) => {
    if (info.file.status === "uploading") {
      setUploadLoading(true);
      return;
    }
    if (info.file.status === "done") {
      const { code, data, message: responseMessage } = (info.file.response || {}) as {
        code?: number;
        data?: string;
        message?: string;
      };
      if (code === 0 && data) {
        setAvatarUrl(data);
        form.setFieldValue("userAvatar", data);
        message.success("头像上传成功");
      } else {
        message.error(responseMessage || "头像上传失败");
      }
      setUploadLoading(false);
      return;
    }
    if (info.file.status === "error") {
      message.error("服务器响应错误，上传失败");
      setUploadLoading(false);
    }
  };

  return (
    <>
      <Form.Item name="userAvatar" hidden>
        <Input />
      </Form.Item>

      <div className="mb-8 flex flex-col items-center">
        <Upload
          name="file"
          listType="picture-circle"
          className="avatar-uploader"
          showUploadList={false}
          action={buildApiUrl("/api/file/upload?biz=user_avatar")}
          beforeUpload={beforeUpload}
          onChange={handleUploadChange}
          withCredentials
        >
          {avatarUrl ? (
            <div className="relative h-full w-full overflow-hidden rounded-full border-2 border-slate-100 p-1">
              <Image
                src={validateImageSrc(avatarUrl)}
                alt="avatar"
                fill
                className="rounded-full object-cover"
              />
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/40 opacity-0 transition-opacity hover:opacity-100">
                {uploadLoading ? (
                  <Loader2 size={24} className="animate-spin text-white" />
                ) : (
                  <Camera size={24} className="text-white" />
                )}
                <span className="mt-1 text-[10px] font-bold text-white">更换头像</span>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center space-y-2">
              {uploadLoading ? (
                <Loader2 size={24} className="animate-spin text-primary" />
              ) : (
                <UploadCloud size={24} className="text-slate-400" />
              )}
              <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400">上传头像</div>
            </div>
          )}
        </Upload>
        <Text type="secondary" className="mt-3 text-[11px] font-bold uppercase tracking-widest text-slate-400">
          支持 JPG, PNG, WebP，最大 1MB
        </Text>
      </div>

      <Form.Item
        label="账号"
        name="userAccount"
        rules={[
          { required: true, message: "请输入账号" },
          { min: 4, message: "账号至少 4 位" },
          { max: 16, message: "账号最多 16 位" },
        ]}
      >
        <Input placeholder="请输入登录账号" />
      </Form.Item>

      {mode === "create" ? (
        <Form.Item
          label="初始密码"
          name="userPassword"
          rules={[
            { required: true, message: "请输入初始密码" },
            { min: 8, message: "密码至少 8 位" },
          ]}
          extra="管理员创建用户时直接设置密码，用户后续可自行修改。"
        >
          <Input.Password placeholder="至少 8 位" />
        </Form.Item>
      ) : null}

      <Form.Item
        label="用户名"
        name="userName"
        rules={[
          { required: true, message: "请输入用户名" },
          { max: 20, message: "用户名最多 20 个字符" },
        ]}
      >
        <Input placeholder="请输入用户昵称" maxLength={20} showCount />
      </Form.Item>

      <Form.Item label="角色" name="userRole" rules={[{ required: true, message: "请选择角色" }]}>
        <Select options={USER_ROLE_OPTIONS} placeholder="请选择用户角色" />
      </Form.Item>

      <Form.Item label="最近登录城市" name="city">
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          options={CITY_GROUP_OPTIONS}
          popupMatchSelectWidth={false}
          placeholder="请选择城市"
        />
      </Form.Item>

      <Form.Item label="就业方向" name="careerDirection">
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          options={CAREER_DIRECTION_OPTIONS}
          placeholder="请选择就业方向"
        />
      </Form.Item>

      {mode === "create" ? null : (
        <Form.Item label="个人简介" name="userProfile" rules={[{ max: 200, message: "简介最多 200 个字符" }]}>
          <Input.TextArea rows={4} placeholder="可选，补充用户的个人简介" maxLength={200} showCount />
        </Form.Item>
      )}

      <div className="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-500">
        管理员可以在这里直接设置基础资料。头像会走和个人中心相同的上传链路，避免外链失效。
      </div>
    </>
  );
}
