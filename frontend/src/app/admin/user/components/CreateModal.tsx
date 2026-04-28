"use client";

import { addUserUsingPost } from "@/api/userController";
import { Button, Form, Modal, Space, message } from "antd";
import type { ProColumns } from "@ant-design/pro-components";
import React from "react";
import UserFormFields from "./UserFormFields";

interface Props {
  visible: boolean;
  columns: ProColumns<API.User>[];
  onSubmit: (values: API.UserAddRequest) => void;
  onCancel: () => void;
}

const handleAdd = async (fields: API.UserAddRequest) => {
  const hide = message.loading("正在添加");
  try {
    await addUserUsingPost(fields);
    hide();
    message.success("创建成功");
    return true;
  } catch (error: any) {
    hide();
    message.error("创建失败，" + error.message);
    return false;
  }
};

const CreateModal: React.FC<Props> = (props) => {
  const { visible, onSubmit, onCancel } = props;
  const [form] = Form.useForm<API.UserAddRequest>();
  const [avatarUrl, setAvatarUrl] = React.useState("");

  React.useEffect(() => {
    if (visible) {
      form.setFieldsValue({
        userRole: "user",
      });
      setAvatarUrl("");
    }
  }, [visible, form]);

  const submit = async () => {
    const values = await form.validateFields();
    const success = await handleAdd({
      ...values,
      userAvatar: avatarUrl || values.userAvatar,
    });
    if (success) {
      form.resetFields();
      setAvatarUrl("");
      onSubmit?.(values);
    }
  };

  return (
    <Modal
      destroyOnClose
      title="新增用户"
      open={visible}
      onCancel={onCancel}
      footer={
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={submit}>
            创建用户
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={{ userRole: "user" }}>
        <UserFormFields mode="create" avatarUrl={avatarUrl} setAvatarUrl={setAvatarUrl} />
      </Form>
    </Modal>
  );
};

export default CreateModal;
