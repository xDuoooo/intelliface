import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { CalendarClock, MessageCircleReply } from "lucide-react";
import { Empty, Pagination, Spin, Tag, message } from "antd";
import {
  listMyRepliedPostCommentsByPage,
  type PostCommentActivityVO,
} from "@/api/postCommentController";
import { formatIpLocation } from "@/lib/location";
import RecordFilterToolbar from "./RecordFilterToolbar";

type StatusFilter = "all" | 0 | 1 | 2;

const STATUS_OPTIONS = [
  { label: "全部状态", value: "all" },
  { label: "已通过", value: 0 },
  { label: "待审核", value: 1 },
  { label: "已驳回", value: 2 },
];

function buildPostCommentLink(item: PostCommentActivityVO) {
  return `/post/${item.postId}#post-comment-${item.id}`;
}

export default function MyReplyPostCommentList() {
  const [commentList, setCommentList] = useState<PostCommentActivityVO[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize] = useState(8);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");

  const fetchCommentList = useCallback(async (
    page = 1,
    nextKeyword = "",
    nextStatus: StatusFilter = "all",
  ) => {
    setLoading(true);
    try {
      const res = await listMyRepliedPostCommentsByPage({
        current: page,
        pageSize,
        searchText: nextKeyword.trim() || undefined,
        status: nextStatus === "all" ? undefined : Number(nextStatus),
      });
      setCommentList(res.records || []);
      setTotal(Number(res.total || 0));
      setCurrent(page);
    } catch (error: any) {
      message.error(error?.message || "获取社区回复失败");
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    void fetchCommentList(1, "", "all");
  }, [fetchCommentList]);

  return (
    <div className="space-y-5">
      <RecordFilterToolbar
        keyword={keyword}
        keywordPlaceholder="按回复内容搜索社区互动"
        onKeywordChange={setKeyword}
        onSearch={() => {
          void fetchCommentList(1, keyword, statusFilter);
        }}
        onReset={() => {
          setKeyword("");
          setStatusFilter("all");
          void fetchCommentList(1, "", "all");
        }}
        loading={loading}
        statusOptions={STATUS_OPTIONS}
        statusValue={statusFilter}
        onStatusChange={(value) => {
          const nextValue = value as StatusFilter;
          setStatusFilter(nextValue);
          void fetchCommentList(1, keyword, nextValue);
        }}
      />
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
                    <div className="text-xs font-black uppercase tracking-[0.18em] text-slate-400">所属帖子</div>
                    <Link
                      href={buildPostCommentLink(item)}
                      className="mt-2 block text-lg font-black leading-7 text-slate-900 transition-colors hover:text-primary"
                    >
                      {item.postTitle || "帖子已不可见"}
                    </Link>
                  </div>
                  <Tag className="m-0 rounded-full border-blue-100 bg-blue-50 px-4 py-1.5 text-sm font-bold text-blue-600">
                    我回复过
                  </Tag>
                </div>

                <div className="rounded-2xl border border-slate-100 bg-slate-50/70 px-4 py-4 text-sm leading-7 text-slate-700">
                  {item.replyToUser ? (
                    <span className="mr-2 font-bold text-primary">回复 @{item.replyToUser.userName}：</span>
                  ) : null}
                  {item.deleted ? "该社区回复已删除" : item.content}
                </div>

                <div className="flex flex-wrap items-center gap-4 text-sm text-slate-400">
                  <span className="inline-flex items-center gap-1.5">
                    <CalendarClock size={14} />
                    {item.actionTime ? new Date(item.actionTime).toLocaleString("zh-CN") : "刚刚"}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <MessageCircleReply size={14} />
                    社区回复
                  </span>
                  {item.ipLocation ? (
                    <span className="inline-flex items-center gap-1.5">
                      <MessageCircleReply size={14} />
                      {formatIpLocation(item.ipLocation)}
                    </span>
                  ) : null}
                  {typeof item.status === "number" && item.status !== 0 ? (
                    <Tag
                      className={`m-0 rounded-full px-3 py-1 text-xs font-bold ${
                        item.status === 1
                          ? "border-amber-100 bg-amber-50 text-amber-600"
                          : "border-red-100 bg-red-50 text-red-600"
                      }`}
                    >
                      {item.status === 1 ? "待审核" : "已驳回"}
                    </Tag>
                  ) : null}
                </div>

                {item.reviewMessage && item.status && item.status !== 0 ? (
                  <div className="rounded-2xl border border-amber-100 bg-amber-50/80 px-4 py-3 text-xs leading-6 text-amber-700">
                    审核说明：{item.reviewMessage}
                  </div>
                ) : null}
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
                void fetchCommentList(page, keyword, statusFilter);
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
                <div className="text-base font-bold text-slate-700">还没有回复过社区内容</div>
                <div className="text-sm text-slate-400">你在社区里的交流和追问，会统一沉淀在这里。</div>
              </div>
            }
          />
          <Link
            href="/posts"
            className="mt-4 inline-flex items-center justify-center rounded-2xl bg-primary px-6 py-3 font-black text-white transition hover:opacity-90"
          >
            去社区互动
          </Link>
        </div>
      )}
    </Spin>
    </div>
  );
}
