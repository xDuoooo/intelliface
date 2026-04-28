"use client";

import { updateUserUsingPost } from "@/api/userController";
import { Button, Form, Modal, Space, message } from "antd";
import type { ProColumns } from "@ant-design/pro-components";
import React from "react";
import UserFormFields from "./UserFormFields";

interface Props {
  oldData?: API.User;
  visible: boolean;
  columns: ProColumns<API.User>[];
  onSubmit: (values: API.UserAddRequest) => void;
  onCancel: () => void;
}

const handleUpdate = async (fields: API.UserUpdateRequest) => {
  const hide = message.loading("正在更新");
  try {
    await updateUserUsingPost(fields);
    hide();
    message.success("更新成功");
    return true;
  } catch (error: any) {
    hide();
    message.error("更新失败，" + error.message);
    return false;
  }
};

const UpdateModal: React.FC<Props> = (props) => {
  const { oldData, visible, onSubmit, onCancel } = props;
  const [form] = Form.useForm<API.UserUpdateRequest>();
  const [avatarUrl, setAvatarUrl] = React.useState("");

  React.useEffect(() => {
    if (visible && oldData) {
      form.setFieldsValue({
        userAccount: oldData.userAccount,
        userAvatar: oldData.userAvatar,
        userName: oldData.userName,
        userProfile: oldData.userProfile,
        userRole: oldData.userRole,
        city: oldData.city,
        careerDirection: oldData.careerDirection,
      });
      setAvatarUrl(oldData.userAvatar || "");
    }
  }, [visible, oldData, form]);

  if (!oldData) {
    return null;
  }

  const submit = async () => {
    const values = await form.validateFields();
    const success = await handleUpdate({
      ...values,
      id: oldData.id as any,
      userAvatar: avatarUrl || values.userAvatar,
    });
    if (success) {
      onSubmit?.(values as API.UserAddRequest);
    }
  };

  return (
    <Modal
      destroyOnClose
      title="更新用户"
      open={visible}
      onCancel={onCancel}
      footer={
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={submit}>
            保存修改
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <UserFormFields mode="update" avatarUrl={avatarUrl} setAvatarUrl={setAvatarUrl} />
      </Form>
    </Modal>
  );
};

export default UpdateModal;
