"use client";

import Link from "next/link";
import dynamic from "next/dynamic";
import { Tag } from "antd";
import { CalendarClock } from "lucide-react";
import { formatIpLocation } from "@/lib/location";
import UserAvatar from "@/components/UserAvatar";
import UserProfileHoverCard from "@/components/UserProfileHoverCard";

const MdViewer = dynamic(() => import("@/components/MdViewer"), {
  loading: () => (
    <div className="rounded-[2rem] border border-slate-100 bg-slate-50/60 p-6 text-sm text-slate-400">
      正在渲染帖子内容...
    </div>
  ),
});

const PostActionBar = dynamic(() => import("@/components/PostActionBar"), {
  loading: () => (
    <div className="flex flex-wrap items-center gap-3">
      <div className="h-11 w-24 animate-pulse rounded-2xl bg-slate-100" />
      <div className="h-11 w-24 animate-pulse rounded-2xl bg-slate-100" />
      <div className="h-11 w-28 animate-pulse rounded-2xl bg-slate-100" />
    </div>
  ),
});

const PostList = dynamic(() => import("@/components/PostList"), {
  loading: () => (
    <div className="grid gap-4">
      <div className="h-28 animate-pulse rounded-[2rem] bg-slate-100" />
      <div className="h-28 animate-pulse rounded-[2rem] bg-slate-100" />
    </div>
  ),
});

const PostCommentSection = dynamic(() => import("@/components/PostCommentSection"), {
  loading: () => (
    <section className="rounded-[2rem] border border-slate-100 bg-white p-8 text-sm text-slate-400 shadow-xl shadow-slate-200/30">
      正在加载评论区...
    </section>
  ),
});

interface Props {
  post: API.PostVO;
  relatedPostList?: API.PostVO[];
}

export default function PostDetailContent({ post, relatedPostList = [] }: Props) {
  const authorCard = post.user ? (
    <div className="inline-flex items-center gap-3 rounded-2xl border border-slate-100 bg-slate-50/80 px-4 py-3">
      <UserAvatar src={post.user.userAvatar} name={post.user.userName} size={38} />
      <div className="min-w-0 text-left">
        <div className="truncate text-sm font-black text-slate-800">{post.user.userName || "匿名用户"}</div>
        <div className="mt-1 text-xs text-slate-400">经验分享者</div>
      </div>
    </div>
  ) : null;

  return (
    <div className="max-width-content space-y-10">
      <section className="rounded-[2.5rem] border border-slate-100 bg-white px-8 py-10 shadow-2xl shadow-slate-200/40">
        <div className="space-y-6">
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <Tag color="blue" className="rounded-full px-3 py-1">经验帖</Tag>
              {Number(post.reviewStatus ?? 1) === 0 ? (
                <Tag color="processing" className="rounded-full px-3 py-1">待审核</Tag>
              ) : null}
              {Number(post.reviewStatus ?? 1) === 2 ? (
                <Tag color="red" className="rounded-full px-3 py-1">已驳回</Tag>
              ) : null}
              {Number(post.isTop || 0) > 0 ? (
                <Tag color="gold" className="rounded-full px-3 py-1">置顶</Tag>
              ) : null}
              {Number(post.isFeatured || 0) > 0 ? (
                <Tag color="purple" className="rounded-full px-3 py-1">精选</Tag>
              ) : null}
              {post.tagList?.map((tag) => (
                <Tag key={tag} className="rounded-full px-3 py-1">{tag}</Tag>
              ))}
            </div>
            <h1 className="text-4xl font-black tracking-tight text-slate-900">{post.title}</h1>
            {post.reviewMessage && Number(post.reviewStatus ?? 1) !== 1 ? (
              <div className="rounded-2xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-700">
                当前帖子尚未公开。原因：{post.reviewMessage}
              </div>
            ) : null}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-6">
            {post.user?.id ? (
              <UserProfileHoverCard user={post.user} placement="bottomLeft">
                {authorCard}
              </UserProfileHoverCard>
            ) : authorCard}
            <div className="flex flex-wrap items-center gap-4 text-sm text-slate-400">
              <span className="inline-flex items-center gap-1">
                <CalendarClock className="h-4 w-4" />
                {post.createTime ? new Date(post.createTime).toLocaleString("zh-CN") : "刚刚"}
              </span>
              {post.ipLocation ? (
                <span className="text-xs font-medium text-slate-400">{formatIpLocation(post.ipLocation)}</span>
              ) : null}
              <PostActionBar
                postId={post.id ?? ""}
                initialThumbNum={post.thumbNum || 0}
                initialFavourNum={post.favourNum || 0}
                initialHasThumb={!!post.hasThumb}
                initialHasFavour={!!post.hasFavour}
                variant="meta"
                showReport={false}
              />
            </div>
          </div>

          <PostActionBar
            postId={post.id ?? ""}
            initialThumbNum={post.thumbNum || 0}
            initialFavourNum={post.favourNum || 0}
            initialHasThumb={!!post.hasThumb}
            initialHasFavour={!!post.hasFavour}
            showThumbAndFavour={false}
            showReport={false}
          />

          <div className="prose prose-slate max-w-none prose-headings:font-black prose-headings:text-slate-900">
            <MdViewer value={post.content} />
          </div>

          <PostActionBar
            postId={post.id ?? ""}
            initialThumbNum={post.thumbNum || 0}
            initialFavourNum={post.favourNum || 0}
            initialHasThumb={!!post.hasThumb}
            initialHasFavour={!!post.hasFavour}
            showThumbAndFavour={false}
          />
        </div>
      </section>

      <PostCommentSection postId={post.id ?? ""} />

      {relatedPostList.length ? (
        <section className="space-y-6">
          <div className="flex items-end justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Related Reading</div>
              <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-900">相关帖子</h2>
            </div>
            <Link href="/posts" className="text-sm font-black text-slate-400 transition-colors hover:text-primary">
              查看更多帖子
            </Link>
          </div>
          <PostList postList={relatedPostList} />
        </section>
      ) : null}
    </div>
  );
}
