"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  deleteNotificationUsingPost,
  listNotificationByPageUsingPost,
} from "@/api/notificationController";
import {
  getNotificationTypeLabel,
  NOTIFICATION_TYPE_COLOR_MAP,
  NOTIFICATION_TYPE_OPTIONS,
} from "@/lib/notification";
import { formatDateTime } from "@/lib/utils";
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import { Megaphone } from "lucide-react";
import AdminTableEllipsis from "@/components/AdminTableEllipsis";

const { Text } = Typography;

interface NotificationRecord {
  id: number;
  userId: number;
  title: string;
  content: string;
  type: string;
  status: number;
  targetId?: number;
  createTime?: string;
}

interface NotificationQueryState {
  current: number;
  pageSize: number;
  userId?: number;
  title?: string;
  type?: string;
  status?: number;
}

const DEFAULT_QUERY: NotificationQueryState = {
  current: 1,
  pageSize: 10,
};

interface Props {
  refreshToken?: number;
}

export default function NotificationRecordCard({ refreshToken = 0 }: Props) {
  const [queryForm] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<NotificationQueryState>(DEFAULT_QUERY);
  const [data, setData] = useState<NotificationRecord[]>([]);
  const [total, setTotal] = useState(0);

  const statusOptions = useMemo(
    () => [
      { label: "未读", value: 0 },
      { label: "已读", value: 1 },
    ],
    [],
  );

  const loadData = async (nextQuery?: Partial<NotificationQueryState>) => {
    const mergedQuery = { ...query, ...nextQuery };
    setLoading(true);
    try {
      const res: any = await listNotificationByPageUsingPost({
        current: mergedQuery.current,
        pageSize: mergedQuery.pageSize,
        userId: mergedQuery.userId,
        title: mergedQuery.title,
        type: mergedQuery.type,
        status: mergedQuery.status,
        sortField: "createTime",
        sortOrder: "descend",
      });
      const pageData = res?.data;
      setData(pageData?.records || []);
      setTotal(pageData?.total || 0);
      setQuery(mergedQuery);
    } catch (error: any) {
      message.error(error?.message || "加载通知记录失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData(DEFAULT_QUERY);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshToken]);

  const handleDelete = async (id: number) => {
    try {
      await deleteNotificationUsingPost({ id });
      message.success("通知删除成功");
      void loadData();
    } catch (error: any) {
      message.error(error?.message || "删除通知失败");
    }
  };

  const columns = [
    {
      title: "通知 ID",
      dataIndex: "id",
      width: 110,
    },
    {
      title: "接收用户",
      dataIndex: "userId",
      width: 110,
      render: (value: number) => <Text strong>#{value}</Text>,
    },
    {
      title: "标题 / 内容",
      dataIndex: "title",
      width: 340,
      ellipsis: true,
      render: (_: any, record: NotificationRecord) => (
        <div className="min-w-0 space-y-1">
          <AdminTableEllipsis value={record.title} className="font-bold text-slate-800" />
          <AdminTableEllipsis value={record.content} className="text-slate-500" />
        </div>
      ),
    },
    {
      title: "类型",
      dataIndex: "type",
      width: 150,
      render: (value: string) => (
        <Tag color={NOTIFICATION_TYPE_COLOR_MAP[value] || "blue"}>
          {getNotificationTypeLabel(value)}
        </Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      render: (value: number) =>
        value === 1 ? <Tag color="green">已读</Tag> : <Tag color="gold">未读</Tag>,
    },
    {
      title: "目标 ID",
      dataIndex: "targetId",
      width: 110,
      render: (value?: number) => value || "-",
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: "操作",
      dataIndex: "action",
      width: 110,
      render: (_: any, record: NotificationRecord) => (
        <Popconfirm
          title="确认删除这条通知？"
          okText="删除"
          cancelText="取消"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button danger type="link">
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      className="rounded-[2rem] border-0 shadow-lg shadow-slate-200/50"
      title={
        <span className="flex items-center gap-2 text-lg font-black text-slate-800">
          <Megaphone className="h-5 w-5 text-cyan-500" />
          通知记录
        </span>
      }
      extra={<Text type="secondary">共 {total} 条</Text>}
    >
      <Form
        form={queryForm}
        layout="vertical"
        onFinish={(values) => void loadData({ ...DEFAULT_QUERY, ...values, current: 1 })}
      >
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Form.Item className="mb-0" label="用户 ID" name="userId">
            <InputNumber className="!w-full" min={1} placeholder="全部用户" />
          </Form.Item>
          <Form.Item className="mb-0" label="标题关键词" name="title">
            <Input placeholder="支持模糊搜索" />
          </Form.Item>
          <Form.Item className="mb-0" label="通知类型" name="type">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={NOTIFICATION_TYPE_OPTIONS as any}
              placeholder="全部类型"
            />
          </Form.Item>
          <Form.Item className="mb-0" label="状态" name="status">
            <Select allowClear options={statusOptions} placeholder="全部状态" />
          </Form.Item>
        </div>
        <Space className="mt-4">
          <Button type="primary" htmlType="submit" loading={loading}>
            查询记录
          </Button>
          <Button
            onClick={() => {
              queryForm.resetFields();
              void loadData(DEFAULT_QUERY);
            }}
          >
            重置
          </Button>
        </Space>
      </Form>

      <Table<NotificationRecord>
        className="mt-6"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 980 }}
        pagination={{
          current: query.current,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => void loadData({ ...query, current: page, pageSize }),
        }}
      />
    </Card>
  );
}
