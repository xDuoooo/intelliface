"use client";

import Link from "next/link";
import { Tag } from "antd";
import TagList from "@/components/TagList";
import UserAvatar from "@/components/UserAvatar";
import { formatIpLocation } from "@/lib/location";
import { CalendarClock, ChevronRight, Heart, MessageSquareText, ThumbsUp } from "lucide-react";
import { POST_REVIEW_STATUS_COLOR_MAP, POST_REVIEW_STATUS_TEXT_MAP } from "@/constants/post";

interface Props {
  postList: API.PostVO[];
  getHref?: (post: API.PostVO) => string;
}

export default function PostList({ postList = [], getHref }: Props) {
  if (!postList.length) {
    return (
      <div className="flex min-h-48 flex-col items-center justify-center rounded-[2rem] border border-dashed border-slate-200 bg-white/70 px-6 py-12 text-center">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-50 text-slate-400">
          <MessageSquareText className="h-7 w-7" />
        </div>
        <div className="text-base font-black text-slate-800">暂无帖子</div>
        <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">当前没有可展示的帖子，换个筛选条件或稍后再来看看。</p>
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      {postList.map((item) => (
        <Link
          key={item.id}
          href={getHref?.(item) || `/post/${item.id}`}
          className="group flex flex-col gap-4 rounded-[2rem] border border-slate-100 bg-white p-6 transition-all duration-300 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5"
        >
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                {Number(item.reviewStatus ?? 1) !== 1 ? (
                  <Tag
                    color={POST_REVIEW_STATUS_COLOR_MAP[Number(item.reviewStatus ?? 1)] || "default"}
                    className="m-0 rounded-full px-3 py-1 font-bold"
                  >
                    {POST_REVIEW_STATUS_TEXT_MAP[Number(item.reviewStatus ?? 1)] || "待处理"}
                  </Tag>
                ) : null}
                {Number(item.isTop || 0) > 0 ? (
                  <Tag color="gold" className="m-0 rounded-full px-3 py-1 font-bold">
                    置顶
                  </Tag>
                ) : null}
                {Number(item.isFeatured || 0) > 0 ? (
                  <Tag color="purple" className="m-0 rounded-full px-3 py-1 font-bold">
                    精选
                  </Tag>
                ) : null}
              </div>
              <div className="line-clamp-2 text-xl font-black text-slate-900 transition-colors group-hover:text-primary">
                {item.title}
              </div>
              <div className="mt-3 line-clamp-3 text-sm leading-6 text-slate-500">
                {item.content}
              </div>
              {Number(item.reviewStatus ?? 1) !== 1 && item.reviewMessage ? (
                <div className="mt-3 rounded-2xl border border-amber-100 bg-amber-50 px-4 py-3 text-xs leading-6 text-amber-700">
                  {item.reviewMessage}
                </div>
              ) : null}
            </div>
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-50 transition-colors group-hover:bg-primary/10">
              <ChevronRight className="h-5 w-5 text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <TagList tagList={item.tagList} />
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <div className="flex items-center gap-3">
              <UserAvatar src={item.user?.userAvatar} name={item.user?.userName} size={34} />
              <div className="min-w-0">
                <div className="truncate text-sm font-bold text-slate-700">{item.user?.userName || "匿名用户"}</div>
                <div className="mt-1 flex items-center gap-1 text-xs text-slate-400">
                  <CalendarClock className="h-3.5 w-3.5" />
                  {item.createTime ? new Date(item.createTime).toLocaleDateString("zh-CN") : "刚刚"}
                </div>
                {item.ipLocation ? (
                  <div className="mt-1 text-xs font-medium text-slate-400">{formatIpLocation(item.ipLocation)}</div>
                ) : null}
              </div>
            </div>
            <div className="flex items-center gap-4 text-sm text-slate-400">
              <span className="inline-flex items-center gap-1">
                <ThumbsUp className="h-4 w-4" />
                {item.thumbNum || 0}
              </span>
              <span className="inline-flex items-center gap-1">
                <Heart className="h-4 w-4" />
                {item.favourNum || 0}
              </span>
            </div>
          </div>
        </Link>
      ))}
    </div>
  );
}
