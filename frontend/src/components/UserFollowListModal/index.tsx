"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Empty, List, Modal, Pagination, Skeleton } from "antd";
import { ChevronRight } from "lucide-react";
import { useSelector } from "react-redux";
import {
  listFollowerUserVoByPageUsingPost,
  listFollowingUserVoByPageUsingPost,
} from "@/api/userFollowController";
import UserAvatar from "@/components/UserAvatar";
import UserFollowButton from "@/components/UserFollowButton";
import { RootState } from "@/stores";

interface Props {
  open: boolean;
  onCancel: () => void;
  userId?: string | number;
  type: "follower" | "following";
}

/**
 * 粉丝 / 关注列表弹窗
 */
export default function UserFollowListModal({ open, onCancel, userId, type }: Props) {
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [pageSize] = useState(8);
  const [total, setTotal] = useState(0);
  const [records, setRecords] = useState<API.UserVO[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const viewingOwnFollowingList = Boolean(
    type === "following" && userId && loginUser?.id && String(loginUser.id) === String(userId),
  );

  const handleFollowChange = (targetUserId: string | number | undefined, followed: boolean) => {
    if (!targetUserId || followed) {
      if (targetUserId) {
        setRecords((prev) =>
          prev.map((item) =>
            item.id === targetUserId
              ? {
                  ...item,
                  hasFollowed: followed,
                }
              : item,
          ),
        );
      }
      return;
    }
    if (viewingOwnFollowingList) {
      setRecords((prev) => prev.filter((item) => item.id !== targetUserId));
      setTotal((prev) => Math.max(0, prev - 1));
      return;
    }
    setRecords((prev) =>
      prev.map((item) =>
        item.id === targetUserId
          ? {
              ...item,
              hasFollowed: false,
            }
          : item,
      ),
    );
  };

  useEffect(() => {
    if (open) {
      setCurrent(1);
    }
  }, [open, type, userId]);

  useEffect(() => {
    if (!open) {
      setCurrent(1);
      setErrorMessage("");
      return;
    }
    if (!userId) {
      setRecords([]);
      setTotal(0);
      setErrorMessage("");
      return;
    }
    const loadData = async () => {
      setLoading(true);
      try {
        setErrorMessage("");
        const requestBody = {
          userId,
          current,
          pageSize,
        };
        const res =
          type === "follower"
            ? await listFollowerUserVoByPageUsingPost(requestBody)
            : await listFollowingUserVoByPageUsingPost(requestBody);
        setRecords(res.data?.records || []);
        setTotal(Number(res.data?.total || 0));
      } catch (error: any) {
        setRecords([]);
        setTotal(0);
        setErrorMessage(error?.message || "暂时无法查看列表");
      } finally {
        setLoading(false);
      }
    };
    void loadData();
  }, [current, open, pageSize, type, userId]);

  useEffect(() => {
    if (current > 1 && !loading && records.length === 0 && total > 0) {
      setCurrent((prev) => Math.max(1, prev - 1));
    }
  }, [current, loading, records.length, total]);

  return (
    <Modal
      open={open}
      onCancel={onCancel}
      footer={null}
      title={type === "follower" ? "粉丝列表" : "关注列表"}
      width={640}
      destroyOnClose
    >
      {loading ? (
        <div className="space-y-4 py-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton active title={false} paragraph={{ rows: 2 }} key={index} />
          ))}
        </div>
      ) : records.length ? (
        <div className="space-y-4">
          <List
            dataSource={records}
            renderItem={(item) => (
              <List.Item className="!px-0">
                <div className="flex w-full items-center justify-between gap-4">
                  <Link
                    href={`/user/${item.id}`}
                    onClick={onCancel}
                    className="flex min-w-0 flex-1 items-center gap-3 rounded-2xl px-2 py-2 transition-colors hover:bg-slate-50 hover:no-underline"
                  >
                    <UserAvatar src={item.userAvatar} name={item.userName} size={46} />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-black text-slate-900">
                        {item.userName || "匿名用户"}
                      </div>
                      <div className="mt-1 line-clamp-1 text-xs text-slate-500">
                        {item.userProfile || "这个人还没有填写个人简介。"}
                      </div>
                    </div>
                    <ChevronRight className="h-4 w-4 text-slate-300" />
                  </Link>
                  <UserFollowButton
                    userId={item.id}
                    initialFollowed={viewingOwnFollowingList || Boolean(item.hasFollowed)}
                    onChange={(followed) => handleFollowChange(item.id, followed)}
                    size="small"
                    className="shrink-0 rounded-xl"
                  />
                </div>
              </List.Item>
            )}
          />

          {total > pageSize ? (
            <div className="flex justify-end">
              <Pagination
                current={current}
                pageSize={pageSize}
                total={total}
                size="small"
                onChange={(page) => setCurrent(page)}
                showSizeChanger={false}
              />
            </div>
          ) : null}
        </div>
      ) : (
        <div className="py-10">
          <Empty description={errorMessage || (type === "follower" ? "还没有粉丝" : "还没有关注任何人")} />
        </div>
      )}
    </Modal>
  );
}
