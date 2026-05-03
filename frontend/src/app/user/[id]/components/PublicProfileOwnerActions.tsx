"use client";

import Link from "next/link";
import { useSelector } from "react-redux";
import { Settings } from "lucide-react";
import { RootState } from "@/stores";

interface Props {
  userId?: string | number;
}

/**
 * 公开主页所有者操作区
 */
export default function PublicProfileOwnerActions({ userId }: Props) {
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const isSelf = Boolean(loginUser?.id && userId && String(loginUser.id) === String(userId));

  if (!isSelf) {
    return null;
  }

  return (
    <Link
      href="/user/public-profile/settings"
      className="mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-2xl bg-primary px-4 text-sm font-black text-white shadow-lg shadow-primary/20 transition-all hover:scale-[1.02] active:scale-95"
    >
      <Settings className="h-4 w-4" />
      公开主页展示设置
    </Link>
  );
}
