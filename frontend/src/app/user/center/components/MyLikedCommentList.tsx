import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CalendarClock, Heart, MessageCircle } from "lucide-react";
import { Empty, Pagination, Spin, Tag, message } from "antd";
import { listMyLikedCommentsByPage, type UserCommentActivityVO } from "@/api/commentController";
import { formatIpLocation } from "@/lib/location";

function buildCommentLink(item: UserCommentActivityVO) {
  return `/question/${item.questionId}#comment-${item.id}`;
}

export default function MyLikedCommentList() {
  const [commentList, setCommentList] = useState<UserCommentActivityVO[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize] = useState(8);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const fetchCommentList = useCallback(async (page = 1) => {
    setLoading(true);
    try {
      const res = await listMyLikedCommentsByPage({
        current: page,
        pageSize,
      });
      setCommentList(res.records || []);
      setTotal(Number(res.total || 0));
      setCurrent(page);
    } catch (error: any) {
      message.error(error?.message || "获取点赞评论失败");
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    void fetchCommentList(1);
  }, [fetchCommentList]);

  return (
    <Spin spinning={loading}>
      {commentList.length ? (
        <div className="space-y-5">
          {commentList.map((item) => (
            <div
              key={String(item.id)}
              className="rounded-[1.75rem] border border-slate-100 bg-white p-6 shadow-sm shadow-slate-100"
            >
              <div className="flex flex-col gap-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-xs font-black uppercase tracking-[0.18em] text-slate-400">所属题目</div>
                    <Link
                      href={buildCommentLink(item)}
                      className="mt-2 block text-lg font-black leading-7 text-slate-900 transition-colors hover:text-primary"
                    >
                      {item.questionTitle || "题目已不可见"}
                    </Link>
                  </div>
                  <Tag className="m-0 rounded-full border-rose-100 bg-rose-50 px-4 py-1.5 text-sm font-bold text-rose-600">
                    已点赞
                  </Tag>
                </div>

                <div className="rounded-2xl border border-slate-100 bg-slate-50/70 px-4 py-4 text-sm leading-7 text-slate-700">
                  {item.replyToUser ? (
                    <span className="mr-2 font-bold text-primary">回复 @{item.replyToUser.userName}：</span>
                  ) : null}
                  {item.deleted ? "该评论已删除" : item.content}
                </div>

                <div className="flex flex-wrap items-center gap-4 text-sm text-slate-400">
                  <span className="inline-flex items-center gap-1.5">
                    <CalendarClock size={14} />
                    {item.actionTime ? new Date(item.actionTime).toLocaleString("zh-CN") : "刚刚"}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <Heart size={14} />
                    点赞 {item.likeNum || 0}
                  </span>
                  {item.ipLocation ? (
                    <span className="inline-flex items-center gap-1.5">
                      <MessageCircle size={14} />
                      {formatIpLocation(item.ipLocation)}
                    </span>
                  ) : null}
                  {typeof item.status === "number" && item.status !== 0 ? (
                    <Tag className="m-0 rounded-full border-amber-100 bg-amber-50 px-3 py-1 text-xs font-bold text-amber-600">
                      {item.status === 1 ? "待审核" : "已驳回"}
                    </Tag>
                  ) : null}
                </div>
              </div>
            </div>
          ))}

          <div className="flex justify-end pt-2">
            <Pagination
              current={current}
              pageSize={pageSize}
              total={total}
              showSizeChanger={false}
              onChange={(page) => {
                void fetchCommentList(page);
              }}
            />
          </div>
        </div>
      ) : (
        <div className="rounded-[2rem] border border-dashed border-slate-200 bg-slate-50/60 px-8 py-14 text-center">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div className="space-y-2">
                <div className="text-base font-bold text-slate-700">还没有点赞过评论</div>
                <div className="text-sm text-slate-400">看到有共鸣的解题思路时点个赞，这里会自动留下记录。</div>
              </div>
            }
          />
          <Link
            href="/questions"
            className="mt-4 inline-flex items-center justify-center rounded-2xl bg-primary px-6 py-3 font-black text-white transition hover:opacity-90"
          >
            去题库看看
          </Link>
        </div>
      )}
    </Spin>
  );
}
