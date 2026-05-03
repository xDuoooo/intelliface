"use client";

import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import UserFollowButton from "@/components/UserFollowButton";
import UserFollowListModal from "@/components/UserFollowListModal";

interface Props {
  user?: API.UserVO;
  initialFollowerCount?: number;
  initialFollowingCount?: number;
  initialHasFollowed?: boolean;
  canViewRelationList?: boolean;
}

/**
 * 公开主页关注关系面板
 */
export default function UserRelationPanel({
  user,
  initialFollowerCount = 0,
  initialFollowingCount = 0,
  initialHasFollowed = false,
  canViewRelationList = true,
}: Props) {
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [followerCount, setFollowerCount] = useState(Number(initialFollowerCount || 0));
  const [followingCount, setFollowingCount] = useState(Number(initialFollowingCount || 0));
  const [hasFollowed, setHasFollowed] = useState(Boolean(initialHasFollowed));
  const [modalType, setModalType] = useState<"follower" | "following" | null>(null);

  useEffect(() => {
    setFollowerCount(Number(initialFollowerCount || 0));
    setFollowingCount(Number(initialFollowingCount || 0));
    setHasFollowed(Boolean(initialHasFollowed));
  }, [initialFollowerCount, initialFollowingCount, initialHasFollowed, user?.id]);

  const isSelf = Boolean(loginUser?.id && user?.id && loginUser.id === user.id);
  const canOpenRelationList = canViewRelationList || isSelf;

  const handleFollowChange = (nextFollowed: boolean) => {
    setHasFollowed(nextFollowed);
    setFollowerCount((current) => Math.max(0, current + (nextFollowed ? 1 : -1)));
  };

  const relationCards = [
    {
      key: "follower" as const,
      label: "粉丝",
      value: followerCount,
      helper: "查看谁在关注 Ta",
    },
    {
      key: "following" as const,
      label: "关注",
      value: followingCount,
      helper: "查看 Ta 正在关注谁",
    },
  ];

  return (
    <>
      <div className="mt-8 grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-stretch">
        <div className="grid grid-cols-2 gap-4">
          {relationCards.map((item) => (
            <button
              key={item.key}
              type="button"
              disabled={!canOpenRelationList}
              onClick={() => canOpenRelationList && setModalType(item.key)}
              className={`rounded-[1.75rem] border border-slate-100 bg-slate-50/70 px-5 py-5 text-left transition-all ${
                canOpenRelationList
                  ? "hover:-translate-y-0.5 hover:border-primary/20 hover:bg-white hover:shadow-xl hover:shadow-slate-200/30"
                  : "cursor-not-allowed opacity-80"
              }`}
            >
              <div className="text-sm font-bold text-slate-400">{item.label}</div>
              <div className="mt-3 text-3xl font-black tracking-tight text-slate-900">{item.value}</div>
              <div className="mt-2 text-xs text-slate-400">
                {canOpenRelationList ? item.helper : "列表未公开"}
              </div>
            </button>
          ))}
        </div>

        <div className="flex flex-col justify-between rounded-[1.75rem] border border-primary/10 bg-primary/5 px-5 py-5 lg:min-w-[240px]">
          <div className="text-sm leading-7 text-slate-600">
            {isSelf
              ? "这里展示的是你的公开关注关系。其他用户可以通过主页和头像悬浮卡快速认识你。"
              : hasFollowed
                ? "你已经关注了 Ta，后续可以在这里查看 Ta 的粉丝和关注关系。"
                : "如果你觉得 Ta 的内容有帮助，可以直接关注，对方会收到一条站内提醒。"}
          </div>
          {!isSelf ? (
            <UserFollowButton
              userId={user?.id}
              initialFollowed={hasFollowed}
              onChange={handleFollowChange}
              size="large"
              className="mt-5 h-12 rounded-2xl px-6 font-bold"
            />
          ) : null}
        </div>
      </div>

      <UserFollowListModal
        open={modalType !== null}
        onCancel={() => setModalType(null)}
        userId={user?.id}
        type={modalType || "follower"}
      />
    </>
  );
}
