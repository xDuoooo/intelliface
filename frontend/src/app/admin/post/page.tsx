"use client";

import React, { useRef, useState } from "react";
import dynamic from "next/dynamic";
import { useSearchParams } from "next/navigation";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Button, Input, message, Modal, Popconfirm, Radio, Skeleton, Switch, Tag } from "antd";
import Link from "next/link";
import { AlertTriangle, CheckCheck, Edit3, FilePlus2, ShieldCheck, Sparkles, Trash2 } from "lucide-react";
import ProTable from "@/components/DynamicProTable";
import {
  addPostUsingPost,
  deletePostUsingPost,
  editPostUsingPost,
  listPostVoByPageUsingPost,
  listPostReportVoByPageUsingPost,
  operatePostUsingPost,
  processPostReportUsingPost,
  reviewPostUsingPost,
} from "@/api/postController";
import { POST_REVIEW_STATUS_COLOR_MAP, POST_REVIEW_STATUS_TEXT_MAP } from "@/constants/post";
import { extractSortParams } from "@/lib/utils";

const PostEditorForm = dynamic(() => import("@/components/PostEditorForm"), {
  ssr: false,
  loading: () => (
    <div className="rounded-[2rem] border border-slate-100 bg-slate-50 p-6">
      <Skeleton active paragraph={{ rows: 10 }} />
    </div>
  ),
});

const PostCommentModerationPanel = dynamic(() => import("./components/PostCommentModerationPanel"), {
  ssr: false,
  loading: () => (
    <div className="space-y-6">
      <div className="rounded-[2.2rem] border border-slate-100 bg-slate-50/70 p-5">
        <Skeleton active paragraph={{ rows: 2 }} title={{ width: 180 }} />
      </div>
      <div className="rounded-[2.5rem] border border-slate-100 bg-white p-6 shadow-2xl shadow-slate-200/40">
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    </div>
  ),
});

const PostAdminPage: React.FC = () => {
  const searchParams = useSearchParams();
  const activeView = searchParams?.get("view") === "replies" ? "replies" : "posts";
  const actionRef = useRef<ActionType>();
  const [editingPost, setEditingPost] = useState<API.PostVO | undefined>();
  const [createVisible, setCreateVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [reviewingPost, setReviewingPost] = useState<API.PostVO | undefined>();
  const [operatingPost, setOperatingPost] = useState<API.PostVO | undefined>();
  const [reviewStatus, setReviewStatus] = useState<number>(1);
  const [reviewMessage, setReviewMessage] = useState("");
  const [topValue, setTopValue] = useState(false);
  const [featuredValue, setFeaturedValue] = useState(false);
  const [reportingPost, setReportingPost] = useState<API.PostVO | undefined>();
  const [reportRecords, setReportRecords] = useState<API.PostReportVO[]>([]);
  const [reportListLoading, setReportListLoading] = useState(false);
  const [reportProcessingId, setReportProcessingId] = useState<string | number>();

  const normalizeScalar = (value: unknown) => {
    if (Array.isArray(value)) {
      return value[0];
    }
    return value === "" ? undefined : value;
  };

  const buildPostQueryRequest = (params: Record<string, any>, sort: Record<string, any>) => {
    const { sortField, sortOrder } = extractSortParams(sort);
    return {
      current: Number(params.current) || 1,
      pageSize: Number(params.pageSize) || 10,
      title: normalizeScalar(params.title),
      userId: normalizeScalar(params.userId),
      reviewStatus: normalizeScalar(params.reviewStatus),
      sortField,
      sortOrder,
    } as API.PostQueryRequest;
  };

  const governanceHighlights = [
    {
      title: "自动审核",
      description: "普通发帖会先走规则和 AI 预审，降低人工筛查压力。",
      badge: "内容预筛",
      tone: "border-blue-100 bg-blue-50/70 text-blue-700",
    },
    {
      title: "人工复核",
      description: "命中风险规则或被举报的帖子会进入二审，避免误放或误杀。",
      badge: "二次审核",
      tone: "border-amber-100 bg-amber-50/70 text-amber-700",
    },
    {
      title: "精选运营",
      description: "支持精选与置顶，方便把高质量内容稳定展示在社区前排。",
      badge: "内容运营",
      tone: "border-violet-100 bg-violet-50/70 text-violet-700",
    },
  ];

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在删除帖子...");
    try {
      await deletePostUsingPost({ id });
      hide();
      message.success("删除成功");
      actionRef.current?.reload();
    } catch (error: any) {
      hide();
      message.error(error?.message || "删除失败");
    }
  };

  const openReviewModal = (record: API.PostVO) => {
    setReviewingPost(record);
    setReviewStatus(Number(record.reviewStatus ?? 1) === 2 ? 2 : 1);
    setReviewMessage(record.reviewMessage || "");
  };

  const openOperateModal = (record: API.PostVO) => {
    setOperatingPost(record);
    setTopValue(Number(record.isTop || 0) > 0);
    setFeaturedValue(Number(record.isFeatured || 0) > 0);
  };

  const loadPostReports = async (postId?: string | number) => {
    if (!postId) {
      setReportRecords([]);
      return;
    }
    setReportListLoading(true);
    try {
      const res = await listPostReportVoByPageUsingPost({
        current: 1,
        pageSize: 20,
        postId,
      });
      setReportRecords(res.data?.records || []);
    } catch (error: any) {
      message.error(error?.message || "获取举报列表失败");
    } finally {
      setReportListLoading(false);
    }
  };

  const openReportModal = async (record: API.PostVO) => {
    setReportingPost(record);
    await loadPostReports(record.id);
  };

  const handleProcessReport = async (reportId?: string | number, status?: number) => {
    if (!reportId || !status) {
      return;
    }
    setReportProcessingId(reportId);
    try {
      await processPostReportUsingPost({
        id: reportId,
        status,
      });
      message.success(status === 2 ? "已采纳举报" : "已驳回举报");
      await loadPostReports(reportingPost?.id);
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(error?.message || "处理举报失败");
    } finally {
      setReportProcessingId(undefined);
    }
  };

  const columns: ProColumns<API.PostVO>[] = [
    {
      title: "ID",
      dataIndex: "id",
      width: 90,
      hideInForm: true,
    },
    {
      title: "标题",
      dataIndex: "title",
      valueType: "text",
      width: 260,
      render: (_, record) => (
        <Link
          href={`/post/${record.id}`}
          className="block max-w-[380px] text-sm font-black leading-6 text-slate-800 transition-colors hover:text-primary"
        >
          <span className="line-clamp-2">{record.title}</span>
        </Link>
      ),
    },
    {
      title: "标签",
      dataIndex: "tags",
      hideInForm: true,
      hideInSearch: true,
      render: (_, record) => (
        <div className="flex flex-wrap gap-2">
          {(record.tagList || []).slice(0, 3).map((tag) => (
            <Tag key={tag} className="m-0 rounded-full border-slate-200 bg-slate-50 px-3 py-1">
              {tag}
            </Tag>
          ))}
          {(record.tagList?.length || 0) > 3 ? (
            <Tag className="m-0 rounded-full border-slate-200 bg-white px-3 py-1 text-slate-500">
              +{(record.tagList?.length || 0) - 3}
            </Tag>
          ) : null}
        </div>
      ),
    },
    {
      title: "作者",
      dataIndex: ["user", "userName"],
      width: 140,
      hideInSearch: true,
      render: (_, record) => record.user?.userName || `用户 ${record.userId || "-"}`,
    },
    {
      title: "作者 ID",
      dataIndex: "userId",
      valueType: "digit",
      hideInTable: true,
    },
    {
      title: "点赞",
      dataIndex: "thumbNum",
      valueType: "digit",
      width: 90,
      hideInSearch: true,
    },
    {
      title: "收藏",
      dataIndex: "favourNum",
      valueType: "digit",
      width: 90,
      hideInSearch: true,
    },
    {
      title: "举报",
      dataIndex: "reportNum",
      valueType: "digit",
      width: 90,
      hideInSearch: true,
      render: (_, record) => {
        const count = Number(record.reportNum || 0);
        if (count <= 0) {
          return <span className="text-slate-300">0</span>;
        }
        return (
          <button
            onClick={() => void openReportModal(record)}
            className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-3 py-1 text-xs font-black text-amber-700 transition hover:bg-amber-100"
          >
            <AlertTriangle className="h-3.5 w-3.5" />
            {count}
          </button>
        );
      },
    },
    {
      title: "审核状态",
      dataIndex: "reviewStatus",
      valueType: "select",
      width: 110,
      valueEnum: {
        0: { text: "待审核" },
        1: { text: "已通过" },
        2: { text: "已驳回" },
      },
      render: (_, record) => (
        <Tag color={POST_REVIEW_STATUS_COLOR_MAP[Number(record.reviewStatus ?? 1)] || "default"} className="rounded-full px-3 py-1 font-bold">
          {POST_REVIEW_STATUS_TEXT_MAP[Number(record.reviewStatus ?? 1)] || "未知状态"}
        </Tag>
      ),
    },
    {
      title: "运营标签",
      dataIndex: "operation",
      hideInSearch: true,
      render: (_, record) => (
        <div className="flex flex-wrap gap-2">
          {Number(record.isTop || 0) > 0 ? <Tag color="gold" className="m-0 rounded-full px-3 py-1 font-bold">置顶</Tag> : null}
          {Number(record.isFeatured || 0) > 0 ? <Tag color="purple" className="m-0 rounded-full px-3 py-1 font-bold">精选</Tag> : null}
          {Number(record.isTop || 0) === 0 && Number(record.isFeatured || 0) === 0 ? (
            <span className="text-slate-300">-</span>
          ) : null}
        </div>
      ),
    },
    {
      title: "审核意见",
      dataIndex: "reviewMessage",
      hideInSearch: true,
      width: 220,
      render: (text) =>
        text ? (
          <div className="max-w-[260px] whitespace-normal break-words leading-6 line-clamp-3 text-slate-600">{text}</div>
        ) : (
          <span className="text-slate-300">-</span>
        ),
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      valueType: "dateTime",
      hideInSearch: true,
      width: 180,
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      width: 380,
      render: (_, record) => (
        <div className="flex max-w-[380px] flex-wrap gap-2.5">
          <button
            onClick={() => setEditingPost(record)}
            className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1.5 text-xs font-bold text-blue-700 transition hover:bg-blue-100"
          >
            <Edit3 className="h-4 w-4" />
            编辑
          </button>
          <button
            onClick={() => openReviewModal(record)}
            className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700 transition hover:bg-emerald-100"
          >
            <CheckCheck className="h-4 w-4" />
            审核
          </button>
          <button
            onClick={() => openOperateModal(record)}
            className="inline-flex items-center gap-1.5 rounded-full bg-violet-50 px-3 py-1.5 text-xs font-bold text-violet-700 transition hover:bg-violet-100"
          >
            <Sparkles className="h-4 w-4" />
            运营
          </button>
          {Number(record.reportNum || 0) > 0 ? (
            <button
              onClick={() => void openReportModal(record)}
              className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-700 transition hover:bg-amber-100"
            >
              <AlertTriangle className="h-4 w-4" />
              举报
            </button>
          ) : null}
          <Popconfirm
            title="确认删除帖子"
            description="删除后无法恢复，请谨慎操作。"
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(record.id)}
          >
            <button className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1.5 text-xs font-bold text-red-600 transition hover:bg-red-100">
              <Trash2 className="h-4 w-4" />
              删除
            </button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-700">
      <div className="rounded-[2.5rem] border border-white bg-white/70 p-8 shadow-2xl shadow-slate-200/40 backdrop-blur-xl">
        <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-xs font-black uppercase tracking-[0.2em] text-primary">
              <span className="h-2 w-2 rounded-full bg-primary" />
              Post Community
            </div>
            <h1 className="text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">
              {activeView === "replies" ? "社区回复审核" : "社区管理"}
            </h1>
            <p className="text-lg font-medium text-slate-500">
              {activeView === "replies"
                ? "把帖子评论与二级回复审核收进社区管理工作台，治理和运营放在同一页处理。"
                : "统一维护经验帖内容、举报治理与精选运营能力。"}
            </p>
            <div className="flex flex-wrap gap-3 pt-2">
              <Link
                href="/admin/post"
                className={`rounded-full px-4 py-2 text-sm font-black transition ${
                  activeView === "posts"
                    ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
                    : "bg-white text-slate-600 ring-1 ring-slate-200 hover:text-slate-900"
                }`}
              >
                帖子管理
              </Link>
              <Link
                href="/admin/post?view=replies"
                className={`rounded-full px-4 py-2 text-sm font-black transition ${
                  activeView === "replies"
                    ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
                    : "bg-white text-slate-600 ring-1 ring-slate-200 hover:text-slate-900"
                }`}
              >
                社区回复审核
              </Link>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            {activeView === "posts" ? (
              <>
                <Link href="/posts/create">
                  <Button className="h-12 rounded-2xl px-6 font-black">
                    前台发帖页
                  </Button>
                </Link>
                <Button
                  type="primary"
                  icon={<FilePlus2 size={16} />}
                  className="h-12 rounded-2xl px-6 font-black"
                  onClick={() => setCreateVisible(true)}
                >
                  新建帖子
                </Button>
              </>
            ) : (
              <Link href="/admin/post">
                <Button type="primary" className="h-12 rounded-2xl px-6 font-black">
                  返回帖子管理
                </Button>
              </Link>
            )}
          </div>
        </div>

        {activeView === "posts" ? (
          <div className="mt-6 grid gap-3 lg:grid-cols-3">
            {governanceHighlights.map((item) => (
              <div
                key={item.title}
                className={`rounded-[1.6rem] border px-5 py-4 shadow-sm ${item.tone}`}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="text-base font-black">{item.title}</div>
                  <div className="inline-flex items-center gap-1 rounded-full bg-white/80 px-3 py-1 text-[11px] font-black uppercase tracking-[0.16em]">
                    <ShieldCheck className="h-3.5 w-3.5" />
                    {item.badge}
                  </div>
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-600">{item.description}</p>
              </div>
            ))}
          </div>
        ) : null}
      </div>

      {activeView === "posts" ? (
        <div className="rounded-[2.5rem] border border-slate-100 bg-white p-4 shadow-2xl shadow-slate-200/40 sm:p-6">
          <ProTable<API.PostVO>
            headerTitle={null}
            actionRef={actionRef}
            rowKey="id"
            search={{ labelWidth: 80, defaultCollapsed: false }}
            scroll={{ x: 1560 }}
            columns={columns}
            request={async (params, sort) => {
              try {
                const res = await listPostVoByPageUsingPost(buildPostQueryRequest(params, sort as Record<string, any>));
                return {
                  data: res.data?.records || [],
                  success: true,
                  total: Number(res.data?.total || 0),
                };
              } catch (error: any) {
                message.error(error?.message || "加载社区管理数据失败");
                return {
                  data: [],
                  success: false,
                  total: 0,
                };
              }
            }}
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        </div>
      ) : (
        <PostCommentModerationPanel />
      )}

      <Modal
        title={null}
        open={createVisible}
        onCancel={() => setCreateVisible(false)}
        footer={null}
        width={960}
        destroyOnClose
      >
        <div className="space-y-6 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Create Post</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">新增经验帖</h3>
          </div>
          <PostEditorForm
            submitText="创建帖子"
            submitting={saving}
            onSubmit={async (values) => {
              setSaving(true);
              try {
                await addPostUsingPost(values);
                message.success("帖子创建成功");
                setCreateVisible(false);
                actionRef.current?.reload();
              } catch (error: any) {
                message.error(error?.message || "创建失败");
              } finally {
                setSaving(false);
              }
            }}
          />
        </div>
      </Modal>

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
            <h3 className="mt-2 text-2xl font-black text-slate-900">编辑帖子</h3>
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
                actionRef.current?.reload();
              } catch (error: any) {
                message.error(error?.message || "更新失败");
              } finally {
                setSaving(false);
              }
            }}
          />
        </div>
      </Modal>

      <Modal
        title={null}
        open={!!reviewingPost}
        onCancel={() => setReviewingPost(undefined)}
        onOk={async () => {
          if (!reviewingPost?.id) {
            return;
          }
          setSaving(true);
          try {
            await reviewPostUsingPost({
              id: reviewingPost.id,
              reviewStatus,
              reviewMessage,
            });
            message.success("帖子审核结果已更新");
            setReviewingPost(undefined);
            actionRef.current?.reload();
          } catch (error: any) {
            message.error(error?.message || "审核失败");
          } finally {
            setSaving(false);
          }
        }}
        confirmLoading={saving}
        okText="提交审核"
        cancelText="取消"
      >
        <div className="space-y-6 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Review Post</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">审核帖子</h3>
          </div>
          <Radio.Group value={reviewStatus} onChange={(e) => setReviewStatus(e.target.value)} className="flex gap-4">
            <Radio.Button value={1}>审核通过</Radio.Button>
            <Radio.Button value={2}>驳回</Radio.Button>
          </Radio.Group>
          <Input.TextArea
            rows={4}
            value={reviewMessage}
            onChange={(e) => setReviewMessage(e.target.value)}
            placeholder={reviewStatus === 2 ? "驳回时请填写审核意见" : "可以填写审核备注，留空则使用系统默认说明"}
            maxLength={512}
            showCount
          />
        </div>
      </Modal>

      <Modal
        title={null}
        open={!!reportingPost}
        onCancel={() => {
          setReportingPost(undefined);
          setReportRecords([]);
        }}
        footer={null}
        width={820}
        destroyOnClose
      >
        <div className="space-y-6 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-amber-600">Post Reports</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">帖子举报处理</h3>
            <p className="mt-2 break-words text-sm text-slate-500">
              当前帖子：{reportingPost?.title || "未命名帖子"}。你可以查看举报原因，并逐条采纳或驳回。
            </p>
          </div>

          {reportListLoading ? (
            <div className="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-10 text-center text-sm text-slate-400">
              正在加载举报记录...
            </div>
          ) : reportRecords.length ? (
            <div className="space-y-4">
              {reportRecords.map((report) => {
                const status = Number(report.status ?? 0);
                const statusTag =
                  status === 2 ? (
                    <Tag color="red" className="m-0 rounded-full px-3 py-1 font-bold">已采纳</Tag>
                  ) : status === 1 ? (
                    <Tag color="default" className="m-0 rounded-full px-3 py-1 font-bold">已驳回</Tag>
                  ) : (
                    <Tag color="processing" className="m-0 rounded-full px-3 py-1 font-bold">待处理</Tag>
                  );
                return (
                  <div key={report.id} className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="space-y-1">
                        <div className="text-sm font-black text-slate-800">
                          举报用户：{report.reporter?.userName || `用户 ${report.userId || "-"}`}
                        </div>
                        <div className="text-xs text-slate-400">
                          {report.createTime ? new Date(report.createTime).toLocaleString("zh-CN") : "刚刚"}
                        </div>
                      </div>
                      {statusTag}
                    </div>
                    <div className="mt-4 rounded-2xl bg-white px-4 py-3 text-sm leading-7 text-slate-600">
                      {report.reason}
                    </div>
                    {status === 0 ? (
                      <div className="mt-4 flex flex-wrap gap-3">
                        <Button
                          danger
                          loading={reportProcessingId === report.id}
                          onClick={() => handleProcessReport(report.id, 2)}
                          className="rounded-2xl font-black"
                        >
                          采纳举报
                        </Button>
                        <Button
                          loading={reportProcessingId === report.id}
                          onClick={() => handleProcessReport(report.id, 1)}
                          className="rounded-2xl font-black"
                        >
                          驳回举报
                        </Button>
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-400">
              暂无举报记录
            </div>
          )}
        </div>
      </Modal>

      <Modal
        title={null}
        open={!!operatingPost}
        onCancel={() => setOperatingPost(undefined)}
        onOk={async () => {
          if (!operatingPost?.id) {
            return;
          }
          setSaving(true);
          try {
            await operatePostUsingPost({
              id: operatingPost.id,
              isTop: topValue ? 1 : 0,
              isFeatured: featuredValue ? 1 : 0,
            });
            message.success("帖子运营状态已更新");
            setOperatingPost(undefined);
            actionRef.current?.reload();
          } catch (error: any) {
            message.error(error?.message || "更新失败");
          } finally {
            setSaving(false);
          }
        }}
        confirmLoading={saving}
        okText="保存运营设置"
        cancelText="取消"
      >
        <div className="space-y-6 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Operate Post</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">精选 / 置顶设置</h3>
          </div>
          <div className="flex items-center justify-between rounded-2xl border border-slate-100 bg-slate-50/80 px-4 py-4">
            <div>
              <div className="font-black text-slate-800">置顶帖子</div>
              <div className="mt-1 text-sm text-slate-500">置顶内容会优先出现在社区列表前面。</div>
            </div>
            <Switch checked={topValue} onChange={setTopValue} />
          </div>
          <div className="flex items-center justify-between rounded-2xl border border-slate-100 bg-slate-50/80 px-4 py-4">
            <div>
              <div className="font-black text-slate-800">设为精选</div>
              <div className="mt-1 text-sm text-slate-500">精选内容会获得更明显的展示标签。</div>
            </div>
            <Switch checked={featuredValue} onChange={setFeaturedValue} />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default PostAdminPage;
