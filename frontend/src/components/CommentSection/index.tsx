"use client";
import React, { useState, useEffect, useCallback, useRef } from "react";
import { usePathname } from "next/navigation";
import { message, Modal } from "antd";
import {
  ThumbsUp, MessageCircle, MoreHorizontal, Flag, Trash2,
  Pin, ShieldCheck, ChevronDown, Send, Loader2, AlertCircle, Sparkles
} from "lucide-react";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import {
  CommentVO,
  addComment,
  deleteComment,
  listCommentsByPage,
  likeComment,
  reportComment,
  pinComment,
  setOfficialAnswer,
} from "@/api/commentController";
import { formatIpLocation } from "@/lib/location";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";
import UserAvatar from "@/components/UserAvatar";
import UserProfileHoverCard from "@/components/UserProfileHoverCard";

interface Props {
  questionId: string | number;
}

type SortField = "createTime" | "likeNum";

// ---------------------- 时间格式化 ----------------------
function timeAgo(dateStr: string) {
  try {
    return formatDistanceToNow(new Date(dateStr), { addSuffix: true, locale: zhCN });
  } catch {
    return dateStr;
  }
}

function mapCommentTree(
  list: CommentVO[] = [],
  updater: (comment: CommentVO) => CommentVO,
): CommentVO[] {
  return list.map((comment) => {
    const nextComment = updater(comment);
    const replies = nextComment.replies ?? [];
    return {
      ...nextComment,
      replies: replies.length ? mapCommentTree(replies, updater) : [],
    };
  });
}

function removeCommentFromTree(list: CommentVO[] = [], commentId: string | number): CommentVO[] {
  return list
    .filter((comment) => comment.id !== commentId)
    .map((comment) => ({
      ...comment,
      replies: removeCommentFromTree(comment.replies ?? [], commentId),
    }));
}

// ---------------------- 评论输入框 ----------------------
interface ReplyInputProps {
  placeholder?: string;
  onSubmit: (content: string) => Promise<void>;
  onCancel?: () => void;
  autoFocus?: boolean;
}

function CommentInput({ placeholder = "分享你的见解...", onSubmit, onCancel, autoFocus }: ReplyInputProps) {
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (autoFocus && textareaRef.current) textareaRef.current.focus();
  }, [autoFocus]);

  const handleSubmit = async () => {
    if (!content.trim() || submitting) return;
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
          if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) handleSubmit();
        }}
      />
      <div className="flex items-center justify-between">
        <span className={cn("text-xs font-medium tabular-nums", content.length > 1800 ? "text-red-400" : "text-slate-400")}>
          {content.length} / 2000
        </span>
        <div className="flex items-center gap-2">
          {onCancel && (
            <button onClick={onCancel} className="h-9 px-4 rounded-xl text-sm font-bold text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-all">
              取消
            </button>
          )}
          <button
            onClick={handleSubmit}
            disabled={!content.trim() || submitting}
            className="h-9 px-5 rounded-xl bg-primary text-white text-sm font-black flex items-center gap-2 hover:scale-105 active:scale-95 transition-all shadow-md shadow-primary/20 disabled:opacity-40 disabled:pointer-events-none"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            {submitting ? "发布中" : "发布"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------------------- 单条评论卡片 ----------------------
interface CommentCardProps {
  comment: CommentVO;
  loginUser: any;
  onLike: (id: string | number) => void;
  onDelete: (id: string | number) => void;
  onPin: (id: string | number, pinned: boolean) => void;
  onOfficial: (id: string | number, official: boolean) => void;
  onReply: (parentId: string | number, replyToId: string | number, replyToName: string) => void;
  depth?: number;
}

function CommentCard({ comment, loginUser, onLike, onDelete, onPin, onOfficial, onReply, depth = 0 }: CommentCardProps) {
  const [showMenu, setShowMenu] = useState(false);
  const [showReport, setShowReport] = useState(false);
  const [reportReason, setReportReason] = useState("");
  const [reporting, setReporting] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const isAdmin = loginUser?.userRole === "admin";
  const isOwner = loginUser?.id === comment.user?.id;
  const isApproved = comment.status === 0;
  const isPending = comment.status === 1;
  const isRejected = comment.status === 2;

  const REPORT_REASONS = ["广告或垃圾内容", "辱骂或骚扰", "与话题无关", "违法违规内容", "其他"];

  const handleReport = async () => {
    if (!reportReason) return;
    setReporting(true);
    try {
      await reportComment({ commentId: comment.id, reason: reportReason });
      setShowReport(false);
      setShowMenu(false);
      message.success("举报已提交，感谢你的反馈");
    } catch {
      message.error("举报失败，请稍后重试");
    } finally {
      setReporting(false);
    }
  };

  if (comment.deleted) {
    return (
      <div className={cn("flex gap-3", depth > 0 && "ml-10")}>
        <div className="w-8 h-8 rounded-full bg-slate-100 shrink-0" />
        <p className="text-sm text-slate-400 italic py-2">该评论已被删除</p>
      </div>
    );
  }

  return (
    <div id={`comment-${comment.id}`} className={cn("group scroll-mt-28", depth > 0 && "ml-10 border-l-2 border-slate-100 pl-6 mt-4")}>
      <div className="flex gap-3 items-start">
        <UserProfileHoverCard user={comment.user} placement="rightTop">
          <div>
            <UserAvatar
              src={comment.user?.userAvatar}
              name={comment.user?.userName}
              size={depth === 0 ? 40 : 32}
            />
          </div>
        </UserProfileHoverCard>

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex items-center gap-2 flex-wrap mb-1.5">
            <UserProfileHoverCard user={comment.user} placement="topLeft">
              <span className="text-sm font-black text-slate-800 transition-colors hover:text-primary">
                {comment.user?.userName || "匿名用户"}
              </span>
            </UserProfileHoverCard>
            {comment.user?.userRole === "admin" && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-primary/10 text-primary">ADMIN</span>
            )}
            {isPending && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-amber-100 text-amber-600">
                待审核
              </span>
            )}
            {isRejected && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-red-100 text-red-600">
                已驳回
              </span>
            )}
            {comment.isOfficial === 1 && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-green-100 text-green-600 flex items-center gap-1">
                <ShieldCheck className="h-3 w-3" /> 官方解答
              </span>
            )}
            {comment.isPinned === 1 && (
              <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-amber-100 text-amber-600 flex items-center gap-1">
                <Pin className="h-3 w-3" /> 已置顶
              </span>
            )}
            {comment.ipLocation ? (
              <span className="text-xs font-medium text-slate-400">{formatIpLocation(comment.ipLocation)}</span>
            ) : null}
            <span className="text-xs text-slate-400 font-medium ml-auto">{timeAgo(comment.createTime)}</span>
          </div>

          {/* Content */}
          <div className={cn(
            "text-sm text-slate-700 leading-relaxed rounded-2xl p-4 mb-3",
            isRejected ? "bg-red-50/70 border border-red-100" :
            isPending ? "bg-amber-50/70 border border-amber-100" :
            comment.isOfficial === 1 ? "bg-green-50/60 border border-green-100" :
            comment.isPinned === 1 ? "bg-amber-50/60 border border-amber-100" :
            "bg-slate-50/60 border border-slate-100"
          )}>
            {comment.replyToUser && (
              <UserProfileHoverCard user={comment.replyToUser} placement="topLeft">
                <span className="mr-2 text-primary font-bold">
                  回复 @{comment.replyToUser.userName}：
                </span>
              </UserProfileHoverCard>
            )}
            {comment.content}
            {!isApproved && comment.reviewMessage ? (
              <div className={cn(
                "mt-3 rounded-xl px-3 py-2 text-xs leading-5",
                isRejected ? "bg-white/80 text-red-600" : "bg-white/80 text-amber-700"
              )}>
                {comment.reviewMessage}
              </div>
            ) : null}
          </div>

          {/* Actions */}
          <div className="flex items-center gap-4">
            {isApproved ? (
              <>
                <button
                  onClick={() => onLike(comment.id)}
                  className={cn(
                    "flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-xl transition-all",
                    comment.hasLiked
                      ? "text-primary bg-primary/10 hover:bg-primary/20"
                      : "text-slate-400 hover:text-primary hover:bg-slate-100"
                  )}
                >
                  <ThumbsUp className={cn("h-3.5 w-3.5", comment.hasLiked && "fill-current")} />
                  {comment.likeNum > 0 ? comment.likeNum : "点赞"}
                </button>

                {loginUser?.id && (
                  <button
                    onClick={() => onReply(
                      depth === 0 ? comment.id : (comment.parentId ?? comment.id),
                      comment.id,
                      comment.user?.userName || "匿名"
                    )}
                    className="flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-xl text-slate-400 hover:text-primary hover:bg-slate-100 transition-all"
                  >
                    <MessageCircle className="h-3.5 w-3.5" />
                    回复
                  </button>
                )}
              </>
            ) : (
              <div className="flex items-center gap-1.5 text-xs font-bold text-slate-400">
                <AlertCircle className="h-3.5 w-3.5" />
                {isRejected ? "这条内容当前仅你和管理员可见" : "审核通过后会对其他用户可见"}
              </div>
            )}

            {/* More menu */}
            <div className="ml-auto relative" ref={menuRef}>
              <button
                onClick={() => setShowMenu(!showMenu)}
                className="h-7 w-7 rounded-xl flex items-center justify-center text-slate-300 hover:text-slate-600 hover:bg-slate-100 transition-all opacity-0 group-hover:opacity-100"
              >
                <MoreHorizontal className="h-4 w-4" />
              </button>

              {showMenu && (
                <div className="absolute right-0 top-8 z-20 w-44 rounded-2xl bg-white border border-slate-200 shadow-xl p-1.5 animate-in fade-in zoom-in-95 duration-150">
                  {loginUser?.id && (
                    <button
                      onClick={() => { setShowMenu(false); setShowReport(true); }}
                      className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-bold text-slate-500 hover:bg-slate-50 transition-colors"
                    >
                      <Flag className="h-3.5 w-3.5" /> 举报
                    </button>
                  )}
                  {(isOwner || isAdmin) && (
                    <button
                      onClick={() => { setShowMenu(false); onDelete(comment.id); }}
                      className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-bold text-red-400 hover:bg-red-50 transition-colors"
                    >
                      <Trash2 className="h-3.5 w-3.5" /> 删除
                    </button>
                  )}
                  {isAdmin && (
                    <>
                      <div className="h-px bg-slate-100 my-1" />
                      <button
                        onClick={() => { setShowMenu(false); onPin(comment.id, comment.isPinned !== 1); }}
                        className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-bold text-amber-500 hover:bg-amber-50 transition-colors"
                      >
                        <Pin className="h-3.5 w-3.5" />
                        {comment.isPinned === 1 ? "取消置顶" : "置顶"}
                      </button>
                      <button
                        onClick={() => { setShowMenu(false); onOfficial(comment.id, comment.isOfficial !== 1); }}
                        className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-bold text-green-500 hover:bg-green-50 transition-colors"
                      >
                        <ShieldCheck className="h-3.5 w-3.5" />
                        {comment.isOfficial === 1 ? "取消官方解答" : "设为官方解答"}
                      </button>
                    </>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 举报弹框 */}
      {showReport && (
        <div className="ml-12 mt-3 p-4 bg-red-50 border border-red-100 rounded-2xl space-y-3">
          <p className="text-xs font-black text-slate-700">选择举报原因</p>
          <div className="space-y-1">
            {REPORT_REASONS.map((r) => (
              <label key={r} className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio" name="reportReason" value={r}
                  checked={reportReason === r}
                  onChange={() => setReportReason(r)}
                  className="accent-red-400"
                />
                <span className="text-xs font-medium text-slate-600">{r}</span>
              </label>
            ))}
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setShowReport(false)}
              className="h-8 px-3 rounded-xl text-xs font-bold text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-all"
            >
              取消
            </button>
            <button
              onClick={handleReport}
              disabled={!reportReason || reporting}
              className="h-8 px-4 rounded-xl bg-red-400 text-white text-xs font-black flex items-center gap-1 hover:bg-red-500 active:scale-95 transition-all disabled:opacity-40"
            >
              {reporting && <Loader2 className="h-3 w-3 animate-spin" />}
              提交举报
            </button>
          </div>
        </div>
      )}

      {/* 子评论 */}
      {comment.replies && comment.replies.length > 0 && (
        <div className="mt-2">
          {comment.replies.map((reply) => (
            <CommentCard
              key={reply.id}
              comment={reply}
              loginUser={loginUser}
              onLike={onLike}
              onDelete={onDelete}
              onPin={onPin}
              onOfficial={onOfficial}
              onReply={onReply}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------------- 主评论区组件 ----------------------
export default function CommentSection({ questionId }: Props) {
  const pathname = usePathname() || "/";
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const loginHref = `/user/login?redirect=${encodeURIComponent(pathname)}`;

  const [comments, setComments] = useState<CommentVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [sortField, setSortField] = useState<SortField>("createTime");

  // 回复状态
  const [replyState, setReplyState] = useState<{
    parentId: string | number; replyToId: string | number; replyToName: string;
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

  const fetchComments = useCallback(async (page = 1, sort = sortField, append = false) => {
    setLoading(true);
    try {
      const data = await listCommentsByPage({
        questionId,
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
      setTotal(data.total);
      setCurrent(page);
    } catch (e) {
      console.error("获取评论失败", e);
    } finally {
      setLoading(false);
    }
  }, [questionId, sortField]);

  useEffect(() => {
    void fetchComments(1, sortField, false);
  }, [fetchComments, sortField]);

  // ---- 发表顶级评论 ----
  const handleAddComment = async (content: string) => {
    const result = await addComment({ questionId, content });
    if (result.status === 0) {
      message.success("评论发布成功");
    } else {
      message.success(`评论已提交审核${result.reviewMessage ? `：${result.reviewMessage}` : ""}`);
    }
    await fetchComments(1, sortField, false);
  };

  // ---- 发表回复 ----
  const handleAddReply = async (content: string) => {
    if (!replyState) return;
    const result = await addComment({
      questionId,
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

  // ---- 点赞（乐观更新） ----
  const handleLike = async (commentId: string | number) => {
    if (!loginUser?.id) {
      message.warning("请先登录后再点赞");
      return;
    }
    let previousComments: CommentVO[] = [];
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
      const result = await likeComment(commentId);
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

  // ---- 删除 ----
  const handleDelete = async (commentId: string | number) => {
    Modal.confirm({
      title: "确定要删除这条评论吗？",
      content: "删除后将无法恢复，请确认操作。",
      okText: "确认删除",
      cancelText: "取消",
      okButtonProps: {
        danger: true,
      },
      onOk: async () => {
        setComments((prev) => removeCommentFromTree(prev, commentId));
        try {
          await deleteComment(commentId);
          message.success("评论已删除");
        } catch {
          await fetchComments(current, sortField, false);
          message.error("删除失败，请稍后重试");
        }
      },
    });
  };

  // ---- 置顶 ----
  const handlePin = async (commentId: string | number, pinned: boolean) => {
    setComments((prev) =>
      mapCommentTree(prev, (comment) =>
        comment.id === commentId ? { ...comment, isPinned: pinned ? 1 : 0 } : comment,
      ),
    );
    try {
      await pinComment(commentId, pinned);
    } catch {
      await fetchComments(current, sortField, false);
      message.error("置顶操作失败，请稍后重试");
    }
  };

  // ---- 官方解答 ----
  const handleOfficial = async (commentId: string | number, official: boolean) => {
    setComments((prev) =>
      mapCommentTree(prev, (comment) =>
        comment.id === commentId ? { ...comment, isOfficial: official ? 1 : 0 } : comment,
      ),
    );
    try {
      await setOfficialAnswer(commentId, official);
    } catch {
      await fetchComments(current, sortField, false);
      message.error("官方解答操作失败，请稍后重试");
    }
  };

  const SORT_OPTIONS = [
    { label: "最新", value: "createTime" as SortField },
    { label: "最热", value: "likeNum" as SortField },
  ];

  return (
    <section id="comment-section" className="bg-white rounded-[2.5rem] border border-slate-100 shadow-2xl shadow-slate-200/50 p-8 sm:p-12 space-y-10">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-100 pb-8">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-blue-50 flex items-center justify-center border border-blue-100">
            <MessageCircle className="h-6 w-6 text-blue-500" />
          </div>
          <div>
            <h2 className="text-2xl font-black tracking-tight text-slate-900">笔记与讨论</h2>
            <p className="text-xs font-medium text-slate-400 mt-0.5">{total} 条讨论</p>
          </div>
        </div>

        {/* Sort tabs */}
        <div className="flex bg-slate-100 p-1 rounded-2xl gap-1">
          {SORT_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              onClick={() => { if (sortField !== opt.value) { setSortField(opt.value); } }}
              className={cn(
                "px-4 py-1.5 rounded-xl text-xs font-black transition-all",
                sortField === opt.value
                  ? "bg-white text-slate-900 shadow-sm"
                  : "text-slate-400 hover:text-slate-600"
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Reply indicator */}
      {replyState && (
        <div className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary/5 border border-primary/10">
          <span className="text-xs font-black text-primary">回复 @{replyState.replyToName}</span>
          <button
            onClick={() => setReplyState(null)}
            className="ml-auto text-xs font-bold text-slate-400 hover:text-slate-600"
          >
            取消
          </button>
        </div>
      )}

      {/* Input box */}
      {loginUser?.id ? (
        <div className="flex gap-3 items-start">
          <UserAvatar src={loginUser.userAvatar} name={loginUser.userName} size={40} />
          <div className="flex-1">
            <CommentInput
              placeholder={replyState ? `回复 @${replyState.replyToName}...` : "分享你对这道题的见解、笔记或疑惑...  (Ctrl+Enter 快速发布)"}
              onSubmit={replyState ? handleAddReply : handleAddComment}
              onCancel={replyState ? () => setReplyState(null) : undefined}
            />
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-center py-8 rounded-2xl bg-slate-50 border-2 border-dashed border-slate-200 space-x-3">
          <AlertCircle className="h-5 w-5 text-slate-400" />
          <a href={loginHref} onClick={handleLoginClick} className="text-sm font-bold text-primary hover:underline">登录后</a>
          <span className="text-sm text-slate-400">才能参与讨论</span>
        </div>
      )}

      {/* Comments list */}
      <div className="space-y-8">
        {loading && comments.length === 0 ? (
          <div className="flex justify-center py-16">
            <Loader2 className="h-8 w-8 animate-spin text-primary/40" />
          </div>
        ) : comments.length === 0 ? (
          <div className="py-20 flex flex-col items-center gap-4 text-center">
            <Sparkles className="h-12 w-12 text-slate-200" />
            <p className="text-slate-400 font-bold">还没有任何讨论，来发表第一条吧！</p>
          </div>
        ) : (
          comments.map((comment) => (
            <CommentCard
              key={comment.id}
              comment={comment}
              loginUser={loginUser}
              onLike={handleLike}
              onDelete={handleDelete}
              onPin={handlePin}
              onOfficial={handleOfficial}
              onReply={(parentId, replyToId, replyToName) =>
                setReplyState({ parentId, replyToId, replyToName })
              }
            />
          ))
        )}
      </div>

      {/* 加载更多 */}
      {hasMore && (
        <div className="flex justify-center pt-4">
          <button
            onClick={() => fetchComments(current + 1, sortField, true)}
            disabled={loading}
            className="flex items-center gap-2 h-11 px-8 rounded-2xl bg-slate-50 border border-slate-200 text-sm font-bold text-slate-500 hover:border-primary hover:text-primary transition-all disabled:opacity-40 shadow-sm"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ChevronDown className="h-4 w-4" />}
            加载更多
          </button>
        </div>
      )}
    </section>
  );
}
