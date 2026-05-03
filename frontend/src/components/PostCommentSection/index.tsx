"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { AlertCircle, ChevronDown, Loader2, MessageCircle, Send, ThumbsUp, Trash2 } from "lucide-react";
import { Modal, message } from "antd";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import {
  addPostComment,
  deletePostComment,
  likePostComment,
  listPostCommentsByPage,
  PostCommentVO,
} from "@/api/postCommentController";
import UserAvatar from "@/components/UserAvatar";
import UserProfileHoverCard from "@/components/UserProfileHoverCard";
import { formatIpLocation } from "@/lib/location";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";

interface Props {
  postId: number | string;
}

type SortField = "createTime" | "likeNum";

function timeAgo(dateStr?: string) {
  if (!dateStr) {
    return "刚刚";
  }
  try {
    return formatDistanceToNow(new Date(dateStr), { addSuffix: true, locale: zhCN });
  } catch {
    return dateStr;
  }
}

function mapCommentTree(
  list: PostCommentVO[] = [],
  updater: (comment: PostCommentVO) => PostCommentVO,
): PostCommentVO[] {
  return list.map((comment) => {
    const nextComment = updater(comment);
    const replies = nextComment.replies ?? [];
    return {
      ...nextComment,
      replies: replies.length ? mapCommentTree(replies, updater) : [],
    };
  });
}

interface CommentInputProps {
  placeholder?: string;
  onSubmit: (content: string) => Promise<void>;
  onCancel?: () => void;
  autoFocus?: boolean;
}

function CommentInput({ placeholder, onSubmit, onCancel, autoFocus }: CommentInputProps) {
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (autoFocus && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [autoFocus]);

  const handleSubmit = async () => {
    if (!content.trim() || submitting) {
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(content.trim());
      setContent("");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-3">
      <textarea
        ref={textareaRef}
        rows={3}
        className="w-full resize-none rounded-2xl border border-slate-200 bg-slate-50/50 px-4 py-3 text-sm text-slate-800 placeholder-slate-400 outline-none transition-all focus:border-primary focus:ring-4 focus:ring-primary/5 focus:bg-white"
        placeholder={placeholder}
        maxLength={2000}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
            void handleSubmit();
          }
        }}
      />
      <div className="flex items-center justify-between">
        <span className={cn("text-xs font-medium tabular-nums", content.length > 1800 ? "text-red-400" : "text-slate-400")}>
          {content.length} / 2000
        </span>
        <div className="flex items-center gap-2">
          {onCancel ? (
            <button
              onClick={onCancel}
              className="h-9 rounded-xl px-4 text-sm font-bold text-slate-400 transition-all hover:bg-slate-100 hover:text-slate-600"
            >
              取消
            </button>
          ) : null}
          <button
            onClick={() => void handleSubmit()}
            disabled={!content.trim() || submitting}
            className="flex h-9 items-center gap-2 rounded-xl bg-primary px-5 text-sm font-black text-white shadow-md shadow-primary/20 transition-all hover:scale-105 active:scale-95 disabled:pointer-events-none disabled:opacity-40"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            {submitting ? "发布中" : "发布"}
          </button>
        </div>
      </div>
    </div>
  );
}

interface CommentCardProps {
  comment: PostCommentVO;
  loginUser: any;
  onLike: (id: number | string) => void;
  onDelete: (id: number | string) => void;
  onReply: (parentId: number | string, replyToId: number | string, replyToName: string) => void;
  depth?: number;
}

function PostCommentCard({ comment, loginUser, onLike, onDelete, onReply, depth = 0 }: CommentCardProps) {
  const isOwner = loginUser?.id === comment.user?.id;
  const isAdmin = loginUser?.userRole === "admin";
  const isApproved = Number(comment.status ?? 0) === 0;
  const isPending = Number(comment.status ?? 0) === 1;
  const isRejected = Number(comment.status ?? 0) === 2;

  if (comment.deleted) {
    return (
      <div className={cn("flex gap-3", depth > 0 && "ml-10")}>
        <div className="h-8 w-8 shrink-0 rounded-full bg-slate-100" />
        <p className="py-2 text-sm italic text-slate-400">该回复已被删除</p>
      </div>
    );
  }

  return (
    <div
      id={`post-comment-${comment.id}`}
      className={cn("group scroll-mt-28", depth > 0 && "mt-4 ml-10 border-l-2 border-slate-100 pl-6")}
    >
      <div className="flex items-start gap-3">
        <UserProfileHoverCard user={comment.user} placement="rightTop">
          <div>
            <UserAvatar
              src={comment.user?.userAvatar}
              name={comment.user?.userName}
              size={depth === 0 ? 40 : 32}
            />
          </div>
        </UserProfileHoverCard>

        <div className="min-w-0 flex-1">
          <div className="mb-1.5 flex flex-wrap items-center gap-2">
            <UserProfileHoverCard user={comment.user} placement="topLeft">
              <span className="text-sm font-black text-slate-800 transition-colors hover:text-primary">
                {comment.user?.userName || "匿名用户"}
              </span>
            </UserProfileHoverCard>
            {isPending ? (
              <span className="rounded-md bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-600">待审核</span>
            ) : null}
            {isRejected ? (
              <span className="rounded-md bg-red-100 px-1.5 py-0.5 text-[10px] font-bold text-red-600">已驳回</span>
            ) : null}
            {comment.ipLocation ? (
              <span className="text-xs font-medium text-slate-400">{formatIpLocation(comment.ipLocation)}</span>
            ) : null}
            <span className="ml-auto text-xs font-medium text-slate-400">{timeAgo(comment.createTime)}</span>
          </div>

          <div
            className={cn(
              "mb-3 rounded-2xl border p-4 text-sm leading-relaxed text-slate-700",
              isRejected
                ? "border-red-100 bg-red-50/70"
                : isPending
                  ? "border-amber-100 bg-amber-50/70"
                  : "border-slate-100 bg-slate-50/60",
            )}
          >
            {comment.replyToUser ? (
              <UserProfileHoverCard user={comment.replyToUser} placement="topLeft">
                <span className="mr-2 font-bold text-primary">回复 @{comment.replyToUser.userName}：</span>
              </UserProfileHoverCard>
            ) : null}
            {comment.content}
            {!isApproved && comment.reviewMessage ? (
              <div
                className={cn(
                  "mt-3 rounded-xl px-3 py-2 text-xs leading-5",
                  isRejected ? "bg-white/80 text-red-600" : "bg-white/80 text-amber-700",
                )}
              >
                {comment.reviewMessage}
              </div>
            ) : null}
          </div>

          <div className="flex items-center gap-4">
            {isApproved ? (
              <>
                <button
                  onClick={() => onLike(comment.id)}
                  className={cn(
                    "flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition-all",
                    comment.hasLiked
                      ? "bg-primary/10 text-primary hover:bg-primary/20"
                      : "text-slate-400 hover:bg-slate-100 hover:text-primary",
                  )}
                >
                  <ThumbsUp className={cn("h-3.5 w-3.5", comment.hasLiked && "fill-current")} />
                  {Number(comment.likeNum || 0) > 0 ? comment.likeNum : "点赞"}
                </button>
                {loginUser?.id ? (
                  <button
                    onClick={() =>
                      onReply(
                        depth === 0 ? comment.id : (comment.parentId ?? comment.id),
                        comment.id,
                        comment.user?.userName || "匿名",
                      )
                    }
                    className="flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold text-slate-400 transition-all hover:bg-slate-100 hover:text-primary"
                  >
                    <MessageCircle className="h-3.5 w-3.5" />
                    回复
                  </button>
                ) : null}
              </>
            ) : !isApproved ? (
              <div className="flex items-center gap-1.5 text-xs font-bold text-slate-400">
                <AlertCircle className="h-3.5 w-3.5" />
                {isRejected ? "这条内容当前仅你和管理员可见" : "审核通过后会对其他用户可见"}
              </div>
            ) : null}
            {(isOwner || isAdmin) ? (
              <button
                onClick={() => onDelete(comment.id)}
                className="ml-auto flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold text-red-400 transition-all hover:bg-red-50 hover:text-red-500"
              >
                <Trash2 className="h-3.5 w-3.5" />
                删除
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {comment.replies?.length ? (
        <div className="mt-2">
          {comment.replies.map((reply) => (
            <PostCommentCard
              key={String(reply.id)}
              comment={reply}
              loginUser={loginUser}
              onLike={onLike}
              onDelete={onDelete}
              onReply={onReply}
              depth={depth + 1}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function PostCommentSection({ postId }: Props) {
  const pathname = usePathname() || "/";
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const loginHref = `/user/login?redirect=${encodeURIComponent(pathname)}`;
  const [comments, setComments] = useState<PostCommentVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [sortField, setSortField] = useState<SortField>("createTime");
  const [replyState, setReplyState] = useState<{
    parentId: number | string;
    replyToId: number | string;
    replyToName: string;
  } | null>(null);

  const PAGE_SIZE = 10;
  const hasMore = comments.length < total;

  const handleLoginClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    if (typeof window === "undefined") {
      return;
    }
    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    window.location.href = `/user/login?redirect=${encodeURIComponent(currentPath || pathname)}`;
  };

  const fetchComments = useCallback(
    async (page = 1, sort = sortField, append = false) => {
      setLoading(true);
      try {
        const data = await listPostCommentsByPage({
          postId,
          current: page,
          pageSize: PAGE_SIZE,
          sortField: sort,
          sortOrder: "descend",
        });
        if (append) {
          setComments((prev) => [...prev, ...data.records]);
        } else {
          setComments(data.records);
        }
        setTotal(Number(data.total || 0));
        setCurrent(page);
      } catch (error) {
        console.error("获取帖子回复失败", error);
        message.error("获取帖子回复失败");
      } finally {
        setLoading(false);
      }
    },
    [postId, sortField],
  );

  useEffect(() => {
    void fetchComments(1, sortField, false);
  }, [fetchComments, sortField]);

  const handleAddComment = async (content: string) => {
    const result = await addPostComment({ postId, content });
    if (result.status === 0) {
      message.success("回复发布成功");
      await fetchComments(1, sortField, false);
    } else {
      message.success(`回复已提交审核${result.reviewMessage ? `：${result.reviewMessage}` : ""}`);
      await fetchComments(1, sortField, false);
    }
  };

  const handleAddReply = async (content: string) => {
    if (!replyState) {
      return;
    }
    const result = await addPostComment({
      postId,
      parentId: replyState.parentId,
      replyToId: replyState.replyToId,
      content,
    });
    setReplyState(null);
    if (result.status === 0) {
      message.success("回复发布成功");
    } else {
      message.success(`回复已提交审核${result.reviewMessage ? `：${result.reviewMessage}` : ""}`);
    }
    await fetchComments(1, sortField, false);
  };

  const handleLike = async (commentId: number | string) => {
    if (!loginUser?.id) {
      message.warning("请先登录后再点赞");
      return;
    }
    let previousComments: PostCommentVO[] = [];
    setComments((prev) => {
      previousComments = prev;
      return mapCommentTree(prev, (comment) => {
        if (comment.id !== commentId) {
          return comment;
        }
        const liked = !comment.hasLiked;
        const nextLikeNum = Math.max(0, Number(comment.likeNum || 0) + (liked ? 1 : -1));
        return { ...comment, hasLiked: liked, likeNum: nextLikeNum };
      });
    });
    try {
      const result = await likePostComment(commentId);
      setComments((prev) =>
        mapCommentTree(prev, (comment) =>
          comment.id === commentId
            ? { ...comment, hasLiked: result.liked, likeNum: Math.max(0, Number(result.likeNum || 0)) }
            : comment,
        ),
      );
    } catch {
      setComments(previousComments);
      message.error("点赞失败，请稍后重试");
    }
  };

  const handleDelete = (commentId: number | string) => {
    Modal.confirm({
      title: "确定要删除这条回复吗？",
      content: "删除后将无法恢复，请确认操作。",
      okText: "确认删除",
      cancelText: "取消",
      okButtonProps: {
        danger: true,
      },
      onOk: async () => {
        try {
          await deletePostComment(commentId);
          message.success("回复已删除");
          await fetchComments(1, sortField, false);
        } catch (error) {
          message.error("删除失败，请稍后重试");
        }
      },
    });
  };

  const SORT_OPTIONS = [
    { label: "最新", value: "createTime" as SortField },
    { label: "最热", value: "likeNum" as SortField },
  ];

  return (
    <section
      id="post-comment-section"
      className="space-y-10 rounded-[2.5rem] border border-slate-100 bg-white p-8 shadow-2xl shadow-slate-200/50 sm:p-12"
    >
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-8">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-blue-100 bg-blue-50">
            <MessageCircle className="h-6 w-6 text-blue-500" />
          </div>
          <div>
            <h2 className="text-2xl font-black tracking-tight text-slate-900">交流与回复</h2>
            <p className="mt-0.5 text-xs font-medium text-slate-400">{total} 条社区回复</p>
          </div>
        </div>
        <div className="flex rounded-2xl bg-slate-100 p-1">
          {SORT_OPTIONS.map((option) => (
            <button
              key={option.value}
              onClick={() => {
                if (sortField !== option.value) {
                  setSortField(option.value);
                }
              }}
              className={cn(
                "rounded-xl px-4 py-1.5 text-xs font-black transition-all",
                sortField === option.value
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-400 hover:text-slate-600",
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {replyState ? (
        <div className="flex items-center gap-2 rounded-xl border border-primary/10 bg-primary/5 px-4 py-2">
          <span className="text-xs font-black text-primary">回复 @{replyState.replyToName}</span>
          <button
            onClick={() => setReplyState(null)}
            className="ml-auto text-xs font-bold text-slate-400 hover:text-slate-600"
          >
            取消
          </button>
        </div>
      ) : null}

      {loginUser?.id ? (
        <div className="flex items-start gap-3">
          <UserAvatar src={loginUser.userAvatar} name={loginUser.userName} size={40} />
          <div className="flex-1">
            <CommentInput
              placeholder={
                replyState
                  ? `回复 @${replyState.replyToName}...`
                  : "分享你的经验、补充说明或者追问...（Ctrl+Enter 快速发布）"
              }
              onSubmit={replyState ? handleAddReply : handleAddComment}
              onCancel={replyState ? () => setReplyState(null) : undefined}
            />
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-center space-x-3 rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50 py-8">
          <AlertCircle className="h-5 w-5 text-slate-400" />
          <a href={loginHref} onClick={handleLoginClick} className="text-sm font-bold text-primary hover:underline">
            登录后
          </a>
          <span className="text-sm text-slate-400">才能参与社区回复</span>
        </div>
      )}

      <div className="space-y-8">
        {loading && comments.length === 0 ? (
          <div className="flex justify-center py-16">
            <Loader2 className="h-8 w-8 animate-spin text-primary/40" />
          </div>
        ) : comments.length === 0 ? (
          <div className="py-20 text-center">
            <p className="font-bold text-slate-400">还没有任何回复，来发表第一条吧！</p>
          </div>
        ) : (
          comments.map((comment) => (
            <PostCommentCard
              key={String(comment.id)}
              comment={comment}
              loginUser={loginUser}
              onLike={handleLike}
              onDelete={handleDelete}
              onReply={(parentId, replyToId, replyToName) =>
                setReplyState({ parentId, replyToId, replyToName })
              }
            />
          ))
        )}
      </div>

      {hasMore ? (
        <div className="flex justify-center pt-4">
          <button
            onClick={() => void fetchComments(current + 1, sortField, true)}
            disabled={loading}
            className="flex h-11 items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-8 text-sm font-bold text-slate-500 shadow-sm transition-all hover:border-primary hover:text-primary disabled:opacity-40"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronDown className="h-4 w-4" />}
            加载更多
          </button>
        </div>
      ) : null}
    </section>
  );
}
