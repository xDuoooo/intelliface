"use client";

import React, { useEffect, useState } from "react";
import { Badge, Button, Drawer, Empty, List, Popover, Space, Typography, message } from "antd";
import { Bell } from "lucide-react";
import { useRouter } from "next/navigation";
import dayjs from "dayjs";
import {
  listMyNotificationVOByPageUsingPost,
  readAllNotificationUsingPost,
  readNotificationUsingPost,
} from "@/api/notificationController";
import { getNotificationTargetUrl } from "@/lib/notification";

const { Text } = Typography;

function NotificationListContent({
  notifications,
  unreadCount,
  loading,
  onReadAll,
  onOpenAll,
  onRead,
}: {
  notifications: API.NotificationVO[];
  unreadCount: number;
  loading: boolean;
  onReadAll: () => void;
  onOpenAll: () => void;
  onRead: (item: API.NotificationVO) => void;
}) {
  return (
    <div className="w-[min(94vw,640px)]">
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 8,
          paddingBottom: 8,
          borderBottom: "1px solid #f0f0f0",
        }}
      >
        <Text strong style={{ fontSize: 16 }}>我的通知</Text>
        {unreadCount > 0 ? (
          <Button type="link" size="small" onClick={onReadAll} style={{ padding: 0 }}>
            全部标记已读
          </Button>
        ) : null}
      </div>

      <List
        loading={loading}
        itemLayout="horizontal"
        dataSource={notifications}
        className="max-h-[min(60vh,420px)] overflow-y-auto"
        locale={{
          emptyText: <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        renderItem={(item) => (
          <List.Item
            style={{
              cursor: "pointer",
              padding: "14px 0",
              opacity: item.status === 1 ? 0.6 : 1,
              transition: "background 0.3s",
            }}
            onMouseEnter={(event) => {
              event.currentTarget.style.backgroundColor = "#fafafa";
            }}
            onMouseLeave={(event) => {
              event.currentTarget.style.backgroundColor = "transparent";
            }}
            onClick={() => onRead(item)}
          >
            <List.Item.Meta
              title={
                <Space align="start">
                  {!item.status ? <Badge dot color="red" offset={[2, 4]} /> : null}
                  <Text strong={!item.status} style={{ fontSize: 14 }}>
                    {item.title}
                  </Text>
                </Space>
              }
              description={
                <div style={{ paddingLeft: item.status ? 0 : 12 }}>
                  <div style={{ color: "rgba(0, 0, 0, 0.65)", marginBottom: 4, fontSize: 13 }}>
                    {item.content}
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {dayjs(item.createTime).format("YYYY-MM-DD HH:mm")}
                  </Text>
                </div>
              }
            />
          </List.Item>
        )}
      />

      {notifications.length > 0 ? (
        <div
          style={{
            textAlign: "center",
            marginTop: 4,
            borderTop: "1px solid #f0f0f0",
            paddingTop: 8,
          }}
        >
          <Button type="link" size="small" style={{ fontSize: 13 }} onClick={onOpenAll}>
            查看所有通知
          </Button>
        </div>
      ) : null}
    </div>
  );
}

const NotificationPopover: React.FC = () => {
  const [notifications, setNotifications] = useState<API.NotificationVO[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const router = useRouter();

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const res = await listMyNotificationVOByPageUsingPost({
        current: 1,
        pageSize: 5,
        sortField: "createTime",
        sortOrder: "descend",
      });
      if (res.data) {
        const data = res.data as API.PageNotificationVO_;
        setNotifications(data.records || []);
      }
    } catch (error) {
      message.error("获取通知失败");
    } finally {
      setLoading(false);
    }
  };

  const fetchUnreadCount = async () => {
    try {
      const res = await listMyNotificationVOByPageUsingPost({
        current: 1,
        pageSize: 1,
        status: 0,
      });
      if (res.data) {
        const data = res.data as API.PageNotificationVO_;
        setUnreadCount(data.total || 0);
      }
    } catch (error) {
      console.error("获取未读通知数量失败", error);
    }
  };

  useEffect(() => {
    void fetchUnreadCount();
    const timer = setInterval(() => {
      void fetchUnreadCount();
    }, 60000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") {
      return undefined;
    }
    const mediaQuery = window.matchMedia("(max-width: 768px)");
    const syncIsMobile = (matches: boolean) => setIsMobile(matches);
    syncIsMobile(mediaQuery.matches);
    const listener = (event: MediaQueryListEvent) => syncIsMobile(event.matches);
    mediaQuery.addEventListener("change", listener);
    return () => mediaQuery.removeEventListener("change", listener);
  }, []);

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (nextOpen) {
      void fetchNotifications();
    }
  };

  const handleReadAll = async () => {
    try {
      const res = await readAllNotificationUsingPost();
      if (res.data) {
        message.success("全部标记为已读");
        void fetchNotifications();
        void fetchUnreadCount();
      }
    } catch (error) {
      message.error("操作失败");
    }
  };

  const handleRead = async (item: API.NotificationVO) => {
    const { id } = item;
    if (!id) {
      return;
    }
    const targetUrl = getNotificationTargetUrl(item);
    try {
      const res = await readNotificationUsingPost({ id });
      if (res.data) {
        void fetchNotifications();
        void fetchUnreadCount();
      }
    } catch (error) {
      message.error("已为你打开对应内容，通知状态稍后会自动同步");
    }
    setOpen(false);
    router.push(targetUrl);
  };

  const openAllNotifications = () => {
    setOpen(false);
    router.push("/user/notifications");
  };

  const contentNode = (
    <NotificationListContent
      notifications={notifications}
      unreadCount={unreadCount}
      loading={loading}
      onReadAll={() => void handleReadAll()}
      onOpenAll={openAllNotifications}
      onRead={handleRead}
    />
  );

  const triggerNode = (
    <button
      type="button"
      aria-label="我的通知"
      onClick={isMobile ? () => handleOpenChange(true) : undefined}
      className="relative cursor-pointer group"
    >
      <Badge count={unreadCount} size="small" offset={[-2, 6]}>
        <div className="flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground transition-colors group-hover:text-foreground hover:bg-muted">
          <Bell className="h-5 w-5" />
        </div>
      </Badge>
    </button>
  );

  if (isMobile) {
    return (
      <>
        {triggerNode}
        <Drawer
          title="我的通知"
          placement="right"
          width="min(100vw, 520px)"
          open={open}
          onClose={() => handleOpenChange(false)}
          destroyOnClose
        >
          {contentNode}
        </Drawer>
      </>
    );
  }

  return (
    <Popover
      content={contentNode}
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={handleOpenChange}
      overlayStyle={{ paddingTop: 12 }}
      overlayInnerStyle={{ width: "min(94vw, 640px)" }}
      getPopupContainer={(triggerNode) => triggerNode.parentElement ?? document.body}
    >
      {triggerNode}
    </Popover>
  );
};

export default NotificationPopover;
