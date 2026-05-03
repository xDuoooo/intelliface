"use client";

import React, { useRef, useState } from "react";
import dynamic from "next/dynamic";
import {
  deleteQuestionBankUsingPost,
  listQuestionBankVoByPageUsingPost,
  reviewQuestionBankUsingPost,
} from "@/api/questionBankController";
import { Plus, Trash2, Edit3, Briefcase } from "lucide-react";
import ProTable from "@/components/DynamicProTable";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { message, Space, Image, Input, Modal, Radio, Tag } from "antd";
import {
  QUESTION_REVIEW_STATUS_COLOR_MAP,
  QUESTION_REVIEW_STATUS_ENUM,
  QUESTION_REVIEW_STATUS_TEXT_MAP,
  QUESTION_REVIEW_STATUS_VALUE_ENUM,
} from "@/constants/question";

const CreateModal = dynamic(() => import("./components/CreateModal"));
const UpdateModal = dynamic(() => import("./components/UpdateModal"));

const normalizeScalar = (value: unknown) => {
  if (Array.isArray(value)) {
    const firstValue = value[0];
    if (firstValue === undefined || firstValue === null || firstValue === "") {
      return undefined;
    }
    return firstValue;
  }
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  return value;
};

const buildQuestionBankQueryRequest = (
  params: Record<string, any>,
  sort: Record<string, "ascend" | "descend" | null>,
) => {
  const sortField = Object.keys(sort)?.[0];
  const sortOrder = sortField ? sort?.[sortField] ?? undefined : undefined;
  const reviewStatus = normalizeScalar(params.reviewStatus);
  const id = normalizeScalar(params.id);

  return {
    current: Number(params.current) || 1,
    pageSize: Number(params.pageSize) || 10,
    id,
    title: typeof params.title === "string" ? params.title : undefined,
    description: typeof params.description === "string" ? params.description : undefined,
    reviewStatus: reviewStatus !== undefined ? Number(reviewStatus) : undefined,
    sortField,
    sortOrder,
  } as API.QuestionBankQueryRequest;
};

/**
 * 题库管理页面
 * @constructor
 */
const QuestionBankAdminPage: React.FC = () => {
  const [createModalVisible, setCreateModalVisible] = useState<boolean>(false);
  const [updateModalVisible, setUpdateModalVisible] = useState<boolean>(false);
  const [reviewModalVisible, setReviewModalVisible] = useState<boolean>(false);
  const [reviewStatus, setReviewStatus] = useState<number>(QUESTION_REVIEW_STATUS_ENUM.APPROVED);
  const [reviewMessage, setReviewMessage] = useState("");
  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.QuestionBank>();

  /**
   * 删除节点
   */
  const handleDelete = async (row: API.QuestionBank) => {
    const hide = message.loading("正在删除");
    if (!row) return true;
    try {
      await deleteQuestionBankUsingPost({ id: row.id as any });
      hide();
      message.success("删除成功");
      actionRef?.current?.reload();
      return true;
    } catch (error: any) {
      hide();
      message.error("删除失败，" + error.message);
      return false;
    }
  };

  const handleReview = async () => {
    if (!currentRow?.id) {
      return;
    }
    const hide = message.loading("正在提交审核");
    try {
      await reviewQuestionBankUsingPost({
        id: currentRow.id,
        reviewStatus,
        reviewMessage,
      });
      hide();
      message.success("题库审核结果已保存");
      setReviewModalVisible(false);
      setCurrentRow(undefined);
      setReviewStatus(QUESTION_REVIEW_STATUS_ENUM.APPROVED);
      setReviewMessage("");
      actionRef?.current?.reload();
    } catch (error: any) {
      hide();
      message.error("审核失败，" + (error?.message || "请稍后重试"));
    }
  };

  /**
   * 表格列配置
   */
  const columns: ProColumns<API.QuestionBank>[] = [
    {
      title: "ID",
      dataIndex: "id",
      valueType: "text",
      hideInForm: true,
      width: 80,
    },
    {
      title: "标题",
      dataIndex: "title",
      valueType: "text",
      render: (text) => <span className="font-bold text-slate-700">{text}</span>,
    },
    {
      title: "描述",
      dataIndex: "description",
      valueType: "text",
      ellipsis: true,
    },
    {
      title: "审核状态",
      dataIndex: "reviewStatus",
      valueType: "select",
      valueEnum: QUESTION_REVIEW_STATUS_VALUE_ENUM,
      width: 120,
      render: (_, record) => {
        const reviewStatus = Number(record.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED);
        return (
          <Tag color={QUESTION_REVIEW_STATUS_COLOR_MAP[reviewStatus] || "default"} className="rounded-full px-3 py-1 font-semibold">
            {QUESTION_REVIEW_STATUS_TEXT_MAP[reviewStatus] || "未知状态"}
          </Tag>
        );
      },
    },
    {
      title: "审核意见",
      dataIndex: "reviewMessage",
      valueType: "textarea",
      hideInSearch: true,
      ellipsis: true,
      render: (text) => text || <span className="text-slate-300">-</span>,
    },
    {
      title: "审核时间",
      dataIndex: "reviewTime",
      valueType: "dateTime",
      hideInSearch: true,
      hideInForm: true,
    },
    {
      title: "图片",
      dataIndex: "picture",
      valueType: "image",
      fieldProps: { width: 64 },
      hideInSearch: true,
      render: (_, record) => (
        <Image 
          src={record.picture} 
          alt={record.title || "题库图片"}
          width={64} 
          className="rounded-xl border border-slate-100 shadow-sm object-cover" 
          fallback="https://placehold.co/100x100?text=No+Image"
        />
      ),
    },
    {
      title: "创建时间",
      sorter: true,
      dataIndex: "createTime",
      valueType: "dateTime",
      hideInSearch: true,
      hideInForm: true,
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      render: (_, record) => (
        <Space size="middle">
          <button
            onClick={() => {
              setCurrentRow(record);
              setUpdateModalVisible(true);
            }}
            className="flex items-center gap-1.5 text-primary hover:text-primary/80 font-bold transition-colors"
          >
            <Edit3 className="h-4 w-4" />
            修改
          </button>
          {Number(record.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) !== QUESTION_REVIEW_STATUS_ENUM.PRIVATE ? (
            <button
              onClick={() => {
                setCurrentRow(record);
                setReviewStatus(
                  Number(record.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.REJECTED
                    ? QUESTION_REVIEW_STATUS_ENUM.REJECTED
                    : QUESTION_REVIEW_STATUS_ENUM.APPROVED,
                );
                setReviewMessage(record.reviewMessage || "");
                setReviewModalVisible(true);
              }}
              className="flex items-center gap-1.5 text-emerald-600 hover:text-emerald-700 font-bold transition-colors"
            >
              审核
            </button>
          ) : (
            <span className="text-slate-300 font-bold">待用户提交</span>
          )}
          <button
            onClick={() => handleDelete(record)}
            className="flex items-center gap-1.5 text-red-500 hover:text-red-600 font-bold transition-colors"
          >
            <Trash2 className="h-4 w-4" />
            删除
          </button>
        </Space>
      ),
    },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-700">
      {/* Premium Admin Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 bg-white/70 backdrop-blur-xl rounded-[2.5rem] p-8 sm:p-12 border border-white shadow-2xl shadow-slate-200/50 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-8 opacity-5">
           <Briefcase className="h-32 w-32 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-3">
           <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-xs">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              Content Resource Center
           </div>
           <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900">题库资源管理</h1>
           <p className="text-slate-500 font-medium text-lg">精心维护平台核心题库内容，打造高质量的面试知识库。</p>
        </div>
        <div className="relative z-10">
          <button
            onClick={() => setCreateModalVisible(true)}
            className="flex items-center gap-2 bg-primary hover:bg-primary/90 text-primary-foreground h-14 px-8 rounded-2xl font-black text-lg transition-all shadow-xl shadow-primary/25 hover:scale-105 active:scale-95"
          >
            <Plus className="h-6 w-6" />
            创建题库
          </button>
        </div>
      </div>

      {/* Table Container */}
      <div className="bg-white rounded-[2.5rem] border border-slate-100 shadow-2xl shadow-slate-200/50 overflow-hidden p-4 sm:p-6 pb-12 ant-table-premium">
        <ProTable<API.QuestionBank>
          headerTitle={null}
          actionRef={actionRef}
          rowKey="id"
          search={{
            labelWidth: "auto",
            defaultCollapsed: false,
            className: "admin-search-form",
          }}
          request={async (params, sort, filter) => {
            try {
              const queryRequest = buildQuestionBankQueryRequest(
                {
                  ...params,
                  ...filter,
                },
                sort as Record<string, "ascend" | "descend" | null>,
              );
              const res = (await listQuestionBankVoByPageUsingPost(
                queryRequest,
              )) as unknown as API.BaseResponsePageQuestionBankVO_;
              return {
                success: res.code === 0,
                data: res.data?.records || [],
                total: Number(res.data?.total) || 0,
              };
            } catch (error: any) {
              message.error(error?.message || "加载题库管理数据失败");
              return {
                success: false,
                data: [],
                total: 0,
              };
            }
          }}
          columns={columns}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
        />
      </div>

      {createModalVisible && (
        <CreateModal
          visible={createModalVisible}
          columns={columns}
          onSubmit={() => {
            setCreateModalVisible(false);
            actionRef.current?.reload();
          }}
          onCancel={() => setCreateModalVisible(false)}
        />
      )}
      {updateModalVisible && currentRow && (
        <UpdateModal
          visible={updateModalVisible}
          columns={columns}
          oldData={currentRow}
          onSubmit={() => {
            setUpdateModalVisible(false);
            setCurrentRow(undefined);
            actionRef.current?.reload();
          }}
          onCancel={() => setUpdateModalVisible(false)}
        />
      )}
      <Modal
        title={`审核题库${currentRow?.title ? `：${currentRow.title}` : ""}`}
        open={reviewModalVisible}
        onCancel={() => {
          setReviewModalVisible(false);
          setCurrentRow(undefined);
          setReviewStatus(QUESTION_REVIEW_STATUS_ENUM.APPROVED);
          setReviewMessage("");
        }}
        onOk={handleReview}
        okText="提交审核"
        cancelText="取消"
        destroyOnClose
      >
        <div className="space-y-4">
          <div>
            <div className="mb-2 text-sm font-semibold text-slate-500">审核结果</div>
            <Radio.Group value={reviewStatus} onChange={(event) => setReviewStatus(event.target.value)} className="flex gap-4">
              <Radio value={QUESTION_REVIEW_STATUS_ENUM.APPROVED}>通过</Radio>
              <Radio value={QUESTION_REVIEW_STATUS_ENUM.REJECTED}>驳回</Radio>
            </Radio.Group>
          </div>
          <div>
            <div className="mb-2 text-sm font-semibold text-slate-500">审核意见</div>
            <Input.TextArea
              rows={4}
              value={reviewMessage}
              onChange={(event) => setReviewMessage(event.target.value)}
              placeholder={reviewStatus === QUESTION_REVIEW_STATUS_ENUM.REJECTED ? "驳回时请填写审核意见" : "通过时可留空，也可以补充审核备注"}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default QuestionBankAdminPage;
