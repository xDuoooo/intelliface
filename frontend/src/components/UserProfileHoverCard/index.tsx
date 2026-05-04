"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Popover, Skeleton, type PopoverProps } from "antd";
import { useSelector } from "react-redux";
import { Activity, BookOpen, Flame, MapPin, PenSquare, Sparkles } from "lucide-react";
import { getUserProfileVoByIdUsingGet } from "@/api/userController";
import UserFollowButton from "@/components/UserFollowButton";
import { formatIpLocation } from "@/lib/location";
import { cn, formatDate } from "@/lib/utils";
import UserAvatar from "@/components/UserAvatar";
import { RootState } from "@/stores";

type PublicUser = Pick<
  API.UserVO,
  "id" | "userName" | "userAvatar" | "userProfile" | "userRole" | "city" | "createTime" | "careerDirection" | "interestTagList"
>;

type HoverCardUser = PublicUser & {
  userId?: string | number;
};

interface Props {
  user?: HoverCardUser | null;
  children: React.ReactNode;
  placement?: PopoverProps["placement"];
  triggerClassName?: string;
}

const profileCache = new Map<string, API.UserProfileVO>();
const profilePromiseCache = new Map<string, Promise<API.UserProfileVO | undefined>>();

function getProfileCacheKey(userId: string | number) {
  return String(userId);
}

function updateCachedProfile(userId: string | number, updater: (profile: API.UserProfileVO) => API.UserProfileVO) {
  const cacheKey = getProfileCacheKey(userId);
  const currentProfile = profileCache.get(cacheKey);
  if (!currentProfile) {
    return;
  }
  profileCache.set(cacheKey, updater(currentProfile));
}

async function fetchUserProfile(userId: string | number) {
  const cacheKey = getProfileCacheKey(userId);
  if (profileCache.has(cacheKey)) {
    return profileCache.get(cacheKey);
  }
  if (profilePromiseCache.has(cacheKey)) {
    return profilePromiseCache.get(cacheKey);
  }
  const task = getUserProfileVoByIdUsingGet({ id: userId })
    .then((res) => {
      if (res.data) {
        profileCache.set(cacheKey, res.data);
        return res.data;
      }
      return undefined;
    })
    .finally(() => {
      profilePromiseCache.delete(cacheKey);
    });
  profilePromiseCache.set(cacheKey, task);
  return task;
}

function formatJoinDate(date?: string) {
  if (!date) {
    return "最近加入";
  }
  return formatDate(date, date);
}

function isProfileFieldVisible(profile: API.UserProfileVO | undefined, field: string) {
  const visibleFields = profile?.profileVisibleFieldList;
  return !Array.isArray(visibleFields) || visibleFields.includes(field);
}

/**
 * 公开用户悬浮名片
 */
export default function UserProfileHoverCard({
  user,
  children,
  placement = "top",
  triggerClassName,
}: Props) {
  const actionButtonClass =
    "h-11 w-full rounded-xl px-4 text-sm font-bold shadow-none transition-all active:scale-95";

  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [isTouchDevice, setIsTouchDevice] = useState(false);
  const targetUserId = useMemo(() => user?.id ?? user?.userId, [user?.id, user?.userId]);
  const [profile, setProfile] = useState<API.UserProfileVO | undefined>(() =>
    targetUserId ? profileCache.get(getProfileCacheKey(targetUserId)) : undefined,
  );
  const [loadError, setLoadError] = useState<string>("");

  useEffect(() => {
    setProfile(targetUserId ? profileCache.get(getProfileCacheKey(targetUserId)) : undefined);
    setLoadError("");
  }, [targetUserId]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return undefined;
    }
    const mediaQuery = window.matchMedia("(hover: none), (pointer: coarse)");
    const syncTouchState = (matches: boolean) => setIsTouchDevice(matches);
    syncTouchState(mediaQuery.matches);
    const listener = (event: MediaQueryListEvent) => syncTouchState(event.matches);
    mediaQuery.addEventListener("change", listener);
    return () => mediaQuery.removeEventListener("change", listener);
  }, []);

  const canOpen = Boolean(targetUserId);
  const displayUser = useMemo(() => profile?.user || user, [profile?.user, user]);
  const resolvedDisplayUserId = displayUser?.id ?? targetUserId;
  const isSelf = Boolean(
    loginUser?.id && resolvedDisplayUserId && String(loginUser.id) === String(resolvedDisplayUserId),
  );
  const hasResolvedProfile = Boolean(profile);
  const showProfile = hasResolvedProfile && isProfileFieldVisible(profile, "profile");
  const showStats = hasResolvedProfile && isProfileFieldVisible(profile, "stats");
  const showCity = hasResolvedProfile && isProfileFieldVisible(profile, "city");
  const showJoinTime = hasResolvedProfile && isProfileFieldVisible(profile, "joinTime");
  const showCareer = hasResolvedProfile && isProfileFieldVisible(profile, "career");
  const showTags = hasResolvedProfile && isProfileFieldVisible(profile, "tags");
  const showRelation = hasResolvedProfile && isProfileFieldVisible(profile, "relation");
  const showContent = hasResolvedProfile && isProfileFieldVisible(profile, "content");

  const stats = useMemo(
    () => showStats ? [
      {
        key: "practice",
        label: "刷题",
        value: profile?.totalQuestionCount ?? 0,
        icon: <BookOpen className="h-4 w-4 text-primary" />,
      },
      {
        key: "mastered",
        label: "掌握",
        value: profile?.masteredQuestionCount ?? 0,
        icon: <Sparkles className="h-4 w-4 text-emerald-500" />,
      },
      {
        key: "active",
        label: "活跃",
        value: profile?.activeDays ?? 0,
        icon: <Activity className="h-4 w-4 text-sky-500" />,
      },
      {
        key: "streak",
        label: "连续",
        value: profile?.currentStreak ?? 0,
        icon: <Flame className="h-4 w-4 text-rose-500" />,
      },
    ] : [],
    [profile, showStats],
  );

  const loadProfile = async () => {
    if (!targetUserId || loading || profile) {
      return;
    }
    setLoading(true);
    setLoadError("");
    try {
      const nextProfile = await fetchUserProfile(targetUserId);
      setProfile(nextProfile);
      if (!nextProfile) {
        setLoadError("暂时无法加载该用户资料");
      }
    } catch (error: any) {
      setLoadError(error?.message || "加载用户资料失败");
    } finally {
      setLoading(false);
    }
  };

  const handleFollowChange = (nextFollowed: boolean) => {
    if (!targetUserId) {
      return;
    }
    setProfile((currentProfile) => {
      if (!currentProfile) {
        return currentProfile;
      }
      const nextProfile = {
        ...currentProfile,
        hasFollowed: nextFollowed,
        followerCount: Math.max(0, Number(currentProfile.followerCount || 0) + (nextFollowed ? 1 : -1)),
      };
      updateCachedProfile(targetUserId, () => nextProfile);
      return nextProfile;
    });
  };

  const content = (
    <div className="w-[320px] space-y-4 rounded-[28px] bg-white">
      <div className="flex items-start gap-3">
        <UserAvatar
          src={displayUser?.userAvatar}
          name={displayUser?.userName}
          size={52}
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <div className="truncate text-base font-black text-slate-900">
              {displayUser?.userName || "匿名用户"}
            </div>
            {displayUser?.userRole === "admin" ? (
              <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-black uppercase tracking-wider text-amber-600">
                ADMIN
              </span>
            ) : null}
          </div>
          {showProfile ? (
            <p className="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
              {displayUser?.userProfile || "这个人还没有填写个人简介。"}
            </p>
          ) : null}
        </div>
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 3 }} title={false} />
      ) : (
        <>
          {showStats ? (
          <div className="grid grid-cols-2 gap-3">
            {stats.map((item) => (
              <div
                key={item.key}
                className="rounded-2xl border border-slate-100 bg-slate-50/70 px-3 py-3"
              >
                <div className="flex items-center gap-2 text-xs font-bold text-slate-400">
                  {item.icon}
                  <span>{item.label}</span>
                </div>
                <div className="mt-2 text-lg font-black text-slate-900">{item.value}</div>
              </div>
            ))}
          </div>
          ) : null}

          {showCity || showJoinTime ? (
          <div className="flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3 text-xs font-medium text-slate-500">
            {showCity ? (
            <span className="inline-flex items-center gap-1.5">
              <MapPin className="h-3.5 w-3.5" />
              {formatIpLocation(displayUser?.city)}
            </span>
            ) : <span />}
            {showJoinTime ? <span>加入于 {formatJoinDate(displayUser?.createTime)}</span> : null}
          </div>
          ) : null}

          {(showCareer && displayUser?.careerDirection) || (showTags && displayUser?.interestTagList?.length) ? (
            <div className="rounded-2xl border border-dashed border-slate-200 px-4 py-3">
              {showCareer && displayUser.careerDirection ? (
                <div className="text-sm font-semibold text-slate-700">方向：{displayUser.careerDirection}</div>
              ) : null}
              {showTags && displayUser.interestTagList?.length ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {displayUser.interestTagList.slice(0, 5).map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-500"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}

          {showRelation ? (
          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-2xl border border-slate-100 bg-white px-4 py-3">
              <div className="text-xs font-bold text-slate-400">粉丝</div>
              <div className="mt-2 text-lg font-black text-slate-900">{profile?.followerCount ?? 0}</div>
            </div>
            <div className="rounded-2xl border border-slate-100 bg-white px-4 py-3">
              <div className="text-xs font-bold text-slate-400">关注</div>
              <div className="mt-2 text-lg font-black text-slate-900">{profile?.followingCount ?? 0}</div>
            </div>
          </div>
          ) : null}

          {showContent ? (
          <div className="rounded-2xl border border-dashed border-slate-200 px-4 py-3 text-sm text-slate-500">
            公开题目 {profile?.approvedQuestionCount ?? 0} 道，点击主页可以查看完整公开资料和最近题目。
          </div>
          ) : null}
        </>
      )}

      {loadError ? <div className="text-sm text-red-400">{loadError}</div> : null}

      <div className={cn("grid gap-3", isSelf ? "grid-cols-1" : "grid-cols-2")}>
        {!isSelf ? (
          <UserFollowButton
            userId={resolvedDisplayUserId}
            initialFollowed={Boolean(profile?.hasFollowed)}
            onChange={handleFollowChange}
            className={cn("!h-11 !w-full !rounded-xl", actionButtonClass)}
          />
        ) : null}
        <Link
          href={`/user/${targetUserId}`}
          prefetch={false}
          className={cn(
            "inline-flex items-center justify-center border border-slate-200 bg-white text-slate-700 hover:border-primary/20 hover:text-primary",
            actionButtonClass,
          )}
        >
          进入主页
        </Link>
      </div>
    </div>
  );

  if (!canOpen) {
    return <>{children}</>;
  }

  const triggerNode = (
    <span
      role="button"
      tabIndex={0}
      className={cn(
        "inline-flex min-w-0 cursor-pointer hover:no-underline",
        triggerClassName,
      )}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          setOpen(true);
          void loadProfile();
        }
      }}
    >
      {children}
    </span>
  );

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (nextOpen) {
          void loadProfile();
        }
      }}
      trigger={isTouchDevice ? ["click"] : ["hover"]}
      placement={placement}
      mouseEnterDelay={0.12}
      getPopupContainer={() => document.body}
      zIndex={1600}
      overlayClassName="user-profile-hover-card"
      overlayInnerStyle={{
        padding: 16,
        backgroundColor: "#ffffff",
        borderRadius: 28,
      }}
      content={content}
    >
      {triggerNode}
    </Popover>
  );
}
