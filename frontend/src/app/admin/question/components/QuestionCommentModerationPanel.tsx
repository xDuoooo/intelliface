"use client";

import React, { useRef, useState } from "react";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Input, message, Modal, Radio, Tag } from "antd";
import { CheckCheck } from "lucide-react";
import AdminTableEllipsis from "@/app/admin/components/AdminTableEllipsis";
import ProTable from "@/components/DynamicProTable";
import { listAdminCommentsByPage, reviewComment, type CommentVO } from "@/api/commentController";

const COMMENT_STATUS_TEXT_MAP: Record<number, string> = {
  0: "已通过",
  1: "待审核",
  2: "已驳回",
};

const COMMENT_STATUS_COLOR_MAP: Record<number, string> = {
  0: "success",
  1: "processing",
  2: "error",
};

export default function QuestionCommentModerationPanel() {
  const actionRef = useRef<ActionType>();
  const [reviewingComment, setReviewingComment] = useState<CommentVO | undefined>();
  const [reviewStatus, setReviewStatus] = useState<number>(0);
  const [reviewMessage, setReviewMessage] = useState("");
  const [saving, setSaving] = useState(false);

  const columns: ProColumns<CommentVO>[] = [
    {
      title: "评论 ID",
      dataIndex: "id",
      width: 110,
      hideInSearch: true,
    },
    {
      title: "题目 ID",
      dataIndex: "questionId",
      width: 100,
      valueType: "digit",
    },
    {
      title: "用户 ID",
      dataIndex: "userId",
      width: 100,
      valueType: "digit",
      hideInTable: true,
    },
    {
      title: "评论用户",
      dataIndex: ["user", "userName"],
      hideInSearch: true,
      render: (_, record) => record.user?.userName || `用户 ${record.user?.id || "-"}`,
    },
    {
      title: "状态",
      dataIndex: "status",
      valueType: "select",
      width: 110,
      valueEnum: {
        0: { text: "已通过" },
        1: { text: "待审核" },
        2: { text: "已驳回" },
      },
      render: (_, record) => (
        <Tag color={COMMENT_STATUS_COLOR_MAP[Number(record.status ?? 0)] || "default"} className="whitespace-nowrap rounded-full px-3 py-1 font-bold">
          {COMMENT_STATUS_TEXT_MAP[Number(record.status ?? 0)] || "未知"}
        </Tag>
      ),
    },
    {
      title: "评论内容",
      dataIndex: "content",
      width: 420,
      ellipsis: true,
      render: (text, record) => (
        <div className="max-w-[520px] min-w-0 space-y-1">
          <AdminTableEllipsis value={text} className="text-sm text-slate-700" />
          {record.replyToUser?.userName ? (
            <div className="text-xs text-slate-400">回复 @{record.replyToUser.userName}</div>
          ) : null}
        </div>
      ),
    },
    {
      title: "审核意见",
      dataIndex: "reviewMessage",
      hideInSearch: true,
      width: 220,
      ellipsis: true,
      render: (text) => <AdminTableEllipsis value={text} className="text-slate-600" />,
    },
    {
      title: "时间",
      dataIndex: "createTime",
      valueType: "dateTime",
      hideInSearch: true,
      width: 180,
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      width: 140,
      render: (_, record) => (
        <button
          onClick={() => {
            setReviewingComment(record);
            setReviewStatus(Number(record.status ?? 0) === 2 ? 2 : 0);
            setReviewMessage(record.reviewMessage || "");
          }}
          className="flex items-center gap-1.5 font-bold text-emerald-600 transition-colors hover:text-emerald-700"
        >
          <CheckCheck className="h-4 w-4" />
          审核
        </button>
      ),
    },
  ];

  return (
    <>
      <div className="rounded-[2.2rem] border border-slate-100 bg-slate-50/70 p-5">
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-xs font-black uppercase tracking-[0.2em] text-primary">
            <span className="h-2 w-2 rounded-full bg-primary" />
            Question Comment Review
          </div>
          <h2 className="text-2xl font-black tracking-tight text-slate-900">评论审核</h2>
          <p className="text-sm font-medium leading-6 text-slate-500">
            在题目管理内统一处理自动审核未通过的评论与回复，减少后台入口分散。
          </p>
        </div>
      </div>

      <div className="rounded-[2.5rem] border border-slate-100 bg-white p-4 shadow-2xl shadow-slate-200/40 sm:p-6">
        <ProTable<CommentVO>
          headerTitle={null}
          actionRef={actionRef}
          rowKey="id"
          search={{ labelWidth: 80 }}
          columns={columns}
          request={async (params) => {
            try {
              const res = await listAdminCommentsByPage({
                current: params.current,
                pageSize: params.pageSize,
                questionId: params.questionId,
                userId: params.userId,
                content: params.content,
                status: params.status,
              });
              return {
                data: res.records || [],
                success: true,
                total: Number(res.total || 0),
              };
            } catch (error: any) {
              message.error(error?.message || "加载评论审核数据失败");
              return {
                data: [],
                success: false,
                total: 0,
              };
            }
          }}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1280 }}
        />
      </div>

      <Modal
        title={null}
        open={!!reviewingComment}
        onCancel={() => setReviewingComment(undefined)}
        onOk={async () => {
          if (!reviewingComment?.id) {
            return;
          }
          setSaving(true);
          try {
            await reviewComment({
              id: reviewingComment.id,
              status: reviewStatus,
              reviewMessage,
            });
            message.success("评论审核结果已更新");
            setReviewingComment(undefined);
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
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Review Comment</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">审核评论</h3>
            <p className="mt-2 rounded-2xl bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-600">
              {reviewingComment?.content}
            </p>
          </div>
          <Radio.Group value={reviewStatus} onChange={(e) => setReviewStatus(e.target.value)} className="flex gap-4">
            <Radio.Button value={0}>审核通过</Radio.Button>
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
    </>
  );
}
