"use client";

import React, { useEffect, useState } from "react";
import { Button, message } from "antd";
import { useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import ACCESS_ENUM from "@/access/accessEnum";
import { followUserUsingPost, unfollowUserUsingPost } from "@/api/userFollowController";
import { cn } from "@/lib/utils";
import { RootState } from "@/stores";

interface Props {
  userId?: string | number;
  initialFollowed?: boolean;
  onChange?: (followed: boolean) => void;
  size?: "small" | "middle" | "large";
  className?: string;
}

/**
 * 用户关注按钮
 */
export default function UserFollowButton({
  userId,
  initialFollowed = false,
  onChange,
  size = "middle",
  className,
}: Props) {
  const router = useRouter();
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [followed, setFollowed] = useState(Boolean(initialFollowed));
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setFollowed(Boolean(initialFollowed));
  }, [initialFollowed, userId]);

  const isSelf = Boolean(loginUser?.id && userId && String(loginUser.id) === String(userId));
  if (!userId || isSelf) {
    return null;
  }

  const handleClick = async (event: React.MouseEvent<HTMLElement>) => {
    event.preventDefault();
    event.stopPropagation();

    if (!loginUser?.id || loginUser.userRole === ACCESS_ENUM.NOT_LOGIN) {
      router.push(`/user/login?redirect=${encodeURIComponent(window.location.href)}`);
      return;
    }

    setLoading(true);
    try {
      if (followed) {
        await unfollowUserUsingPost({ followUserId: userId });
        setFollowed(false);
        onChange?.(false);
        message.success("已取消关注");
      } else {
        await followUserUsingPost({ followUserId: userId });
        setFollowed(true);
        onChange?.(true);
        message.success("关注成功");
      }
    } catch (error: any) {
      message.error(error?.message || (followed ? "取消关注失败" : "关注失败"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button
      type={followed ? "default" : "primary"}
      size={size}
      loading={loading}
      onClick={handleClick}
      className={cn(
        "!inline-flex !items-center !justify-center !rounded-xl !px-4 !text-sm !font-bold !shadow-none transition-all active:!scale-95",
        followed
          ? "!border-slate-200 !bg-white !text-slate-700 hover:!border-primary/20 hover:!bg-white hover:!text-primary"
          : "!border-primary !bg-primary !text-white hover:!border-primary hover:!bg-primary/90 hover:!text-white",
        className,
      )}
    >
      {followed ? "已关注" : "关注 Ta"}
    </Button>
  );
}
