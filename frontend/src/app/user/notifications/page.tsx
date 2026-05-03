"use client";

import React, { useCallback, useEffect, useState } from "react";
import { List, Card, Badge, Button, Space, Typography, message, Pagination, Empty, Segmented, Select, Tag, Popconfirm } from "antd";
import { Bell, CheckCheck, ChevronRight, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  deleteNotificationUsingPost,
  deleteReadNotificationUsingPost,
  listMyNotificationVOByPageUsingPost,
  readAllNotificationUsingPost,
  readNotificationUsingPost,
} from "@/api/notificationController";
import dayjs from "dayjs";
import {
  getNotificationTargetUrl,
  getNotificationTypeLabel,
  NOTIFICATION_TYPE_COLOR_MAP,
  NOTIFICATION_TYPE_OPTIONS,
} from "@/lib/notification";

const { Title, Text } = Typography;

const STATUS_FILTER_OPTIONS = [
  { label: "全部消息", value: "all" },
  { label: "未读", value: "unread" },
  { label: "已读", value: "read" },
] as const;

const TYPE_FILTER_OPTIONS = [{ label: "全部类型", value: "all" }, ...NOTIFICATION_TYPE_OPTIONS] as const;

/**
 * 我的通知页面
 * @constructor
 */
const UserNotificationsPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [dataList, setDataList] = useState<API.NotificationVO[]>([]);
  const [total, setTotal] = useState(0);
  const [readTotal, setReadTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [statusFilter, setStatusFilter] = useState<"all" | "unread" | "read">("all");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const pageSize = 10;
  const router = useRouter();

  // 加载通知数据
  const loadData = useCallback(async (
    page = 1,
    nextStatus: "all" | "unread" | "read" = statusFilter,
    nextType = typeFilter,
  ) => {
    setLoading(true);
    try {
      const [pageRes, readRes] = await Promise.all([
        listMyNotificationVOByPageUsingPost({
          current: page,
          pageSize,
          sortField: "createTime",
          sortOrder: "descend",
          status: nextStatus === "all" ? undefined : nextStatus === "unread" ? 0 : 1,
          type: nextType === "all" ? undefined : nextType,
        }),
        listMyNotificationVOByPageUsingPost({
          current: 1,
          pageSize: 1,
          sortField: "createTime",
          sortOrder: "descend",
          status: 1,
        }),
      ]);
      const pageData = pageRes.data as API.PageNotificationVO_ | undefined;
      const readData = readRes.data as API.PageNotificationVO_ | undefined;
      setDataList(pageData?.records || []);
      setTotal(pageData?.total || 0);
      setReadTotal(readData?.total || 0);
    } catch (e: any) {
      message.error("加载通知失败：" + e.message);
    } finally {
      setLoading(false);
    }
  }, [pageSize, statusFilter, typeFilter]);

  useEffect(() => {
    void loadData(current);
  }, [current, loadData]);

  // 标记单条已读并跳转
  const handleRead = async (item: API.NotificationVO) => {
    const { id, status } = item;
    if (!id) return;
    
    // 如果未读，先标已读
    if (status === 0) {
      try {
        await readNotificationUsingPost({ id });
        void loadData(current);
      } catch (e) {
        // 忽略错误，优先跳转
      }
    }

    router.push(getNotificationTargetUrl(item));
  };

  // 全部标记已读
  const handleReadAll = async () => {
    try {
      const res = await readAllNotificationUsingPost();
      if (res.data) {
        message.success("已将所有通知标记为已读");
        void loadData(1);
        setCurrent(1);
      }
    } catch (e: any) {
      message.error("操作失败：" + e.message);
    }
  };

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    try {
      const res = await deleteNotificationUsingPost({ id });
      if (res.data) {
        message.success("通知已删除");
        const nextPage = dataList.length === 1 && current > 1 ? current - 1 : current;
        if (nextPage !== current) {
          setCurrent(nextPage);
        } else {
          void loadData(nextPage);
        }
      }
    } catch (e: any) {
      message.error("删除失败：" + e.message);
    }
  };

  const handleDeleteRead = async () => {
    try {
      const res = await deleteReadNotificationUsingPost();
      message.success(`已清理 ${res.data ?? 0} 条已读通知`);
      setCurrent(1);
      void loadData(1);
    } catch (e: any) {
      message.error("清理失败：" + e.message);
    }
  };

  const handleStatusFilterChange = (value: string | number) => {
    const nextValue = value as "all" | "unread" | "read";
    setStatusFilter(nextValue);
    setCurrent(1);
  };

  const handleTypeFilterChange = (value: string) => {
    setTypeFilter(value);
    setCurrent(1);
  };

  return (
    <div style={{ maxWidth: 1120, margin: "0 auto", padding: "24px 16px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <Space size="middle">
          <div style={{ 
            width: 40, 
            height: 40, 
            borderRadius: 8, 
            backgroundColor: "#e6f7ff", 
            display: "flex", 
            alignItems: "center", 
            justifyContent: "center",
            color: "#1890ff"
          }}>
            <Bell size={24} />
          </div>
          <Title level={3} style={{ margin: 0 }}>我的通知</Title>
        </Space>
        {(total > 0 || readTotal > 0) && (
          <Space wrap>
            <Popconfirm
              title="确认清空已读通知吗？"
              description="仅会删除已经读过的通知。"
              onConfirm={handleDeleteRead}
              okText="清空"
              cancelText="取消"
              disabled={readTotal === 0}
            >
              <Button
                icon={<Trash2 size={16} />}
                disabled={loading || readTotal === 0}
              >
                清空已读
              </Button>
            </Popconfirm>
            <Button
              icon={<CheckCheck size={16} />}
              onClick={handleReadAll}
              disabled={loading}
            >
              全部标记已读
            </Button>
          </Space>
        )}
      </div>

      <Card
        bordered={false}
        style={{ marginBottom: 16 }}
        bodyStyle={{ padding: 20 }}
      >
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-col gap-2">
            <Text strong className="text-slate-700">筛选通知</Text>
            <Segmented
              options={STATUS_FILTER_OPTIONS as any}
              value={statusFilter}
              onChange={handleStatusFilterChange}
            />
          </div>
          <div className="flex flex-col gap-2 md:min-w-[220px]">
            <Text strong className="text-slate-700">消息类型</Text>
            <Select
              value={typeFilter}
              onChange={handleTypeFilterChange}
              options={TYPE_FILTER_OPTIONS as any}
            />
          </div>
        </div>
      </Card>

      <Card bodyStyle={{ padding: 0 }} bordered={false} className="overflow-hidden">
        <List
          loading={loading}
          dataSource={dataList}
          locale={{
            emptyText: <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          }}
          renderItem={(item) => (
            <List.Item
              className="hover:bg-gray-50 transition-colors cursor-pointer"
              style={{ padding: "20px 28px", opacity: item.status === 1 ? 0.7 : 1 }}
              onClick={() => handleRead(item)}
              extra={(
                <Space size={8}>
                  <Popconfirm
                    title="确认删除这条通知吗？"
                    description="删除后将无法恢复。"
                    onConfirm={() => handleDelete(item.id)}
                    okText="删除"
                    cancelText="取消"
                  >
                    <Button
                      type="text"
                      danger
                      icon={<Trash2 size={16} />}
                      onClick={(event) => event.stopPropagation()}
                    />
                  </Popconfirm>
                  <ChevronRight size={18} className="text-gray-300" />
                </Space>
              )}
            >
              <List.Item.Meta
                avatar={
                  <Badge dot={item.status === 0} offset={[-2, 2]}>
                    <div style={{ 
                      width: 44, 
                      height: 44, 
                      borderRadius: "50%", 
                      backgroundColor: item.status === 0 ? "#1890ff15" : "#f5f5f5",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: item.status === 0 ? "#1890ff" : "#bfbfbf"
                    }}>
                      <Bell size={20} />
                    </div>
                  </Badge>
                }
                title={
                  <Space wrap>
                    <Text strong={item.status === 0} style={{ fontSize: 16 }}>{item.title}</Text>
                    {item.type ? (
                      <Tag color={NOTIFICATION_TYPE_COLOR_MAP[item.type] || "default"} className="rounded-full">
                        {getNotificationTypeLabel(item.type)}
                      </Tag>
                    ) : null}
                    <Text type="secondary" style={{ fontSize: 13, marginLeft: 8 }}>
                      {dayjs(item.createTime).format("YYYY-MM-DD HH:mm")}
                    </Text>
                  </Space>
                }
                description={
                  <div style={{ marginTop: 4, color: "rgba(0, 0, 0, 0.65)", fontSize: 14 }}>
                    {item.content}
                  </div>
                }
              />
            </List.Item>
          )}
        />
        {total > pageSize && (
          <div style={{ padding: "24px", textAlign: "right", borderTop: "1px solid #f0f0f0" }}>
            <Pagination
              current={current}
              total={total}
              pageSize={pageSize}
              onChange={(page) => setCurrent(page)}
              showSizeChanger={false}
            />
          </div>
        )}
      </Card>
    </div>
  );
};

export default UserNotificationsPage;
