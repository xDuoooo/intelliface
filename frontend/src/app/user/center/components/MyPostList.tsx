"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Button, Empty, message, Modal, Pagination, Popconfirm, Spin, Tag } from "antd";
import { CalendarClock, Edit3, FilePlus2, MessageSquareText, Trash2 } from "lucide-react";
import {
  deletePostUsingPost,
  editPostUsingPost,
  listMyPostVoByPageUsingPost,
} from "@/api/postController";
import PostEditorForm from "@/components/PostEditorForm";
import { formatIpLocation } from "@/lib/location";
import { POST_REVIEW_STATUS_COLOR_MAP, POST_REVIEW_STATUS_TEXT_MAP } from "@/constants/post";
import RecordFilterToolbar from "./RecordFilterToolbar";

type ReviewStatusFilter = "all" | 0 | 1 | 2;
type SortKey =
  | "latest_create"
  | "latest_update"
  | "most_liked"
  | "most_favoured"
  | "title_asc"
  | "title_desc";

const REVIEW_STATUS_OPTIONS = [
  { label: "全部状态", value: "all" },
  { label: "待审核", value: 0 },
  { label: "已通过", value: 1 },
  { label: "已驳回", value: 2 },
];

const SORT_OPTIONS = [
  { label: "最新提交", value: "latest_create" },
  { label: "最近更新", value: "latest_update" },
  { label: "点赞最多", value: "most_liked" },
  { label: "收藏最多", value: "most_favoured" },
  { label: "标题 A-Z", value: "title_asc" },
  { label: "标题 Z-A", value: "title_desc" },
];

function resolveSort(sortKey: SortKey) {
  switch (sortKey) {
    case "latest_update":
      return { sortField: "updateTime", sortOrder: "descend" as const };
    case "most_liked":
      return { sortField: "thumbNum", sortOrder: "descend" as const };
    case "most_favoured":
      return { sortField: "favourNum", sortOrder: "descend" as const };
    case "title_asc":
      return { sortField: "title", sortOrder: "ascend" as const };
    case "title_desc":
      return { sortField: "title", sortOrder: "descend" as const };
    case "latest_create":
    default:
      return { sortField: "createTime", sortOrder: "descend" as const };
  }
}

export default function MyPostList() {
  const [postList, setPostList] = useState<API.PostVO[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize] = useState(6);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [editingPost, setEditingPost] = useState<API.PostVO | undefined>();
  const [saving, setSaving] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [reviewStatus, setReviewStatus] = useState<ReviewStatusFilter>("all");
  const [sortKey, setSortKey] = useState<SortKey>("latest_create");

  const fetchPostList = useCallback(async (
    page = 1,
    nextKeyword = "",
    nextReviewStatus: ReviewStatusFilter = "all",
    nextSortKey: SortKey = "latest_create",
  ) => {
    setLoading(true);
    try {
      const sortConfig = resolveSort(nextSortKey);
      const res = await listMyPostVoByPageUsingPost({
        current: page,
        pageSize,
        searchText: nextKeyword.trim() || undefined,
        reviewStatus: nextReviewStatus === "all" ? undefined : Number(nextReviewStatus),
        sortField: sortConfig.sortField,
        sortOrder: sortConfig.sortOrder,
      });
      setPostList(res.data?.records || []);
      setTotal(Number(res.data?.total || 0));
      setCurrent(page);
    } catch (error: any) {
      message.error(error?.message || "获取我的帖子失败");
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    void fetchPostList(1, "", "all", "latest_create");
  }, [fetchPostList]);

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在删除帖子...");
    try {
      await deletePostUsingPost({ id });
      hide();
      message.success("删除成功");
      const nextCurrent = postList.length === 1 && current > 1 ? current - 1 : current;
      await fetchPostList(nextCurrent, keyword, reviewStatus, sortKey);
    } catch (error: any) {
      hide();
      message.error(error?.message || "删除失败");
    }
  };

  return (
    <div className="space-y-6">
      <div className="rounded-[2rem] border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/30">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">My Posts</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">我的经验帖</h3>
            <p className="mt-2 text-sm leading-7 text-slate-500">
              把面试经历、项目复盘和踩坑总结沉淀下来，它们会成为你个人主页里最有分量的内容。
            </p>
          </div>
          <Link href="/posts/create">
            <Button type="primary" icon={<FilePlus2 size={16} />} className="h-11 rounded-2xl px-6 font-black">
              发布经验帖
            </Button>
          </Link>
        </div>
      </div>

      <RecordFilterToolbar
        keyword={keyword}
        keywordPlaceholder="按标题或内容搜索我的帖子"
        onKeywordChange={setKeyword}
        onSearch={() => {
          void fetchPostList(1, keyword, reviewStatus, sortKey);
        }}
        onReset={() => {
          setKeyword("");
          setReviewStatus("all");
          setSortKey("latest_create");
          void fetchPostList(1, "", "all", "latest_create");
        }}
        loading={loading}
        statusOptions={REVIEW_STATUS_OPTIONS}
        statusValue={reviewStatus}
        onStatusChange={(value) => {
          const nextValue = value as ReviewStatusFilter;
          setReviewStatus(nextValue);
          void fetchPostList(1, keyword, nextValue, sortKey);
        }}
        sortOptions={SORT_OPTIONS}
        sortValue={sortKey}
        onSortChange={(value) => {
          const nextValue = value as SortKey;
          setSortKey(nextValue);
          void fetchPostList(1, keyword, reviewStatus, nextValue);
        }}
      />

      <Spin spinning={loading}>
        {postList.length ? (
          <div className="space-y-4">
            {postList.map((item) => {
              const isPublicPost = Number(item.reviewStatus ?? 1) === 1;
              return (
              <div
                key={item.id}
                className="rounded-[2rem] border border-slate-100 bg-white p-6 shadow-lg shadow-slate-200/20"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <Tag color={POST_REVIEW_STATUS_COLOR_MAP[Number(item.reviewStatus ?? 1)] || "default"} className="m-0 rounded-full px-3 py-1 font-bold">
                        {POST_REVIEW_STATUS_TEXT_MAP[Number(item.reviewStatus ?? 1)] || "未知状态"}
                      </Tag>
                      {Number(item.isTop || 0) > 0 ? (
                        <Tag color="gold" className="m-0 rounded-full px-3 py-1 font-bold">置顶</Tag>
                      ) : null}
                      {Number(item.isFeatured || 0) > 0 ? (
                        <Tag color="purple" className="m-0 rounded-full px-3 py-1 font-bold">精选</Tag>
                      ) : null}
                      {item.tagList?.map((tag) => (
                        <Tag key={tag} className="m-0 rounded-full border-slate-200 bg-slate-50 px-3 py-1">
                          {tag}
                        </Tag>
                      ))}
                    </div>
                    {isPublicPost ? (
                      <Link
                        href={`/post/${item.id}`}
                        className="block text-2xl font-black tracking-tight text-slate-900 transition-colors hover:text-primary"
                      >
                        {item.title}
                      </Link>
                    ) : (
                      <div className="text-2xl font-black tracking-tight text-slate-900">{item.title}</div>
                    )}
                    <p className="line-clamp-3 max-w-4xl text-sm leading-7 text-slate-500">
                      {item.content}
                    </p>
                    {item.reviewMessage ? (
                      <div className="rounded-2xl border border-amber-100 bg-amber-50/80 px-4 py-3 text-sm text-amber-700">
                        审核说明：{item.reviewMessage}
                      </div>
                    ) : null}
                    <div className="flex flex-wrap items-center gap-4 text-xs font-semibold text-slate-400">
                      <span className="inline-flex items-center gap-1">
                        <CalendarClock className="h-4 w-4" />
                        {item.createTime ? new Date(item.createTime).toLocaleString("zh-CN") : "刚刚发布"}
                      </span>
                      {item.ipLocation ? <span>{formatIpLocation(item.ipLocation)}</span> : null}
                      <span>点赞 {item.thumbNum || 0}</span>
                      <span>收藏 {item.favourNum || 0}</span>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-3">
                    {isPublicPost ? (
                      <Link href={`/post/${item.id}`}>
                        <Button className="h-10 rounded-2xl font-bold">查看</Button>
                      </Link>
                    ) : (
                      <Button
                        className="h-10 rounded-2xl font-bold"
                        onClick={() => setEditingPost(item)}
                      >
                        查看并修改
                      </Button>
                    )}
                    <Button
                      icon={<Edit3 size={16} />}
                      className="h-10 rounded-2xl font-bold"
                      onClick={() => setEditingPost(item)}
                    >
                      编辑
                    </Button>
                    <Popconfirm
                      title="确认删除这篇帖子？"
                      description="删除后无法恢复，请谨慎操作。"
                      okText="确认删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => handleDelete(item.id)}
                    >
                      <Button danger icon={<Trash2 size={16} />} className="h-10 rounded-2xl font-bold">
                        删除
                      </Button>
                    </Popconfirm>
                  </div>
                </div>
              </div>
              );
            })}

            <div className="flex justify-end pt-2">
              <Pagination
                current={current}
                pageSize={pageSize}
                total={total}
                showSizeChanger={false}
              onChange={(page) => {
                  void fetchPostList(page, keyword, reviewStatus, sortKey);
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
                  <div className="text-base font-bold text-slate-700">你还没有发布过经验帖</div>
                  <div className="text-sm text-slate-400">项目复盘、面经总结和系统设计思路都很适合沉淀在这里。</div>
                </div>
              }
            />
            <Link href="/posts/create">
              <Button type="primary" icon={<MessageSquareText size={16} />} className="mt-4 h-11 rounded-2xl px-6 font-black">
                现在去写一篇
              </Button>
            </Link>
          </div>
        )}
      </Spin>

      <Modal
        title={null}
        open={!!editingPost}
        onCancel={() => setEditingPost(undefined)}
        footer={null}
        width={960}
        destroyOnClose
      >
        <div className="space-y-6 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Edit Post</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">编辑经验帖</h3>
          </div>
          <PostEditorForm
            initialValues={{
              title: editingPost?.title || "",
              content: editingPost?.content || "",
              tags: editingPost?.tagList || [],
            }}
            submitText="保存修改"
            submitting={saving}
            onSubmit={async (values) => {
              if (!editingPost?.id) {
                return;
              }
              setSaving(true);
              try {
                await editPostUsingPost({
                  id: editingPost.id,
                  title: values.title,
                  content: values.content,
                  tags: values.tags,
                });
                message.success("帖子更新成功");
                setEditingPost(undefined);
                await fetchPostList(current, keyword, reviewStatus, sortKey);
              } catch (error: any) {
                message.error(error?.message || "更新失败");
              } finally {
                setSaving(false);
              }
            }}
          />
        </div>
      </Modal>
    </div>
  );
}
