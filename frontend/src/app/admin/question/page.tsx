"use client";

import React, { useRef, useState } from "react";
import dynamic from "next/dynamic";
import { useSearchParams } from "next/navigation";
import {
  batchDeleteQuestionsUsingPost,
  deleteQuestionUsingPost,
  listQuestionByPageUsingPost,
} from "@/api/questionController";
import { Plus, Trash2, Edit3, Database, Wand2, Link2 } from "lucide-react";
import ProTable from "@/components/DynamicProTable";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Button, message, Popconfirm, Skeleton, Space, Table, Tag } from "antd";
import TagList from "@/components/TagList";
import Link from "next/link";
import {
  QUESTION_DIFFICULTY_COLOR_MAP,
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_DIFFICULTY_VALUE_ENUM,
  QUESTION_REVIEW_STATUS_COLOR_MAP,
  QUESTION_REVIEW_STATUS_TEXT_MAP,
  QUESTION_REVIEW_STATUS_VALUE_ENUM,
} from "@/constants/question";

const CreateModal = dynamic(() => import("./components/CreateModal"));
const UpdateModal = dynamic(() => import("./components/UpdateModal"));
const UpdateBankModal = dynamic(() => import("./components/UpdateBankModal"));
const BatchAddQuestionsToBankModal = dynamic(() => import("./components/BatchAddQuestionsToBankModal"));
const BatchRemoveQuestionsFromBankModal = dynamic(() => import("./components/BatchRemoveQuestionsFromBankModal"));
const ReviewModal = dynamic(() => import("./components/ReviewModal"));
const QuestionCommentModerationPanel = dynamic(() => import("./components/QuestionCommentModerationPanel"), {
  ssr: false,
  loading: () => (
    <div className="space-y-6">
      <div className="rounded-[2.2rem] border border-slate-100 bg-slate-50/70 p-5">
        <Skeleton active paragraph={{ rows: 2 }} title={{ width: 160 }} />
      </div>
      <div className="rounded-[2.5rem] border border-slate-100 bg-white p-6 shadow-2xl shadow-slate-200/40">
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    </div>
  ),
});

const parseTagList = (tags?: string) => {
  if (!tags) {
    return [];
  }
  try {
    return JSON.parse(tags);
  } catch {
    return [];
  }
};

const normalizeStringArray = (value: unknown) => {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item ?? "").trim())
      .filter(Boolean);
  }
  if (typeof value === "string" && value.trim()) {
    return [value.trim()];
  }
  return undefined;
};

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

const buildQuestionQueryRequest = (
  params: Record<string, any>,
  sort: Record<string, "ascend" | "descend" | null>,
) => {
  const sortField = Object.keys(sort)?.[0];
  const sortOrder = sortField ? sort?.[sortField] ?? undefined : undefined;
  const reviewStatus = normalizeScalar(params.reviewStatus);
  const userId = normalizeScalar(params.userId);
  const id = normalizeScalar(params.id);
  const difficulty = normalizeScalar(params.difficulty);

  return {
    current: Number(params.current) || 1,
    pageSize: Number(params.pageSize) || 10,
    id,
    userId,
    title: typeof params.title === "string" ? params.title : undefined,
    content: typeof params.content === "string" ? params.content : undefined,
    answer: typeof params.answer === "string" ? params.answer : undefined,
    difficulty: typeof difficulty === "string" ? difficulty : undefined,
    reviewStatus: reviewStatus !== undefined ? Number(reviewStatus) : undefined,
    tags: normalizeStringArray(params.tags),
    sortField,
    sortOrder,
  } as API.QuestionQueryRequest;
};

/**
 * 题目管理页面
 * @constructor
 */
const QuestionAdminPage: React.FC = () => {
  const searchParams = useSearchParams();
  const activeView = searchParams?.get("view") === "comments" ? "comments" : "questions";
  const [createModalVisible, setCreateModalVisible] = useState<boolean>(false);
  const [updateModalVisible, setUpdateModalVisible] = useState<boolean>(false);
  const [updateBankModalVisible, setUpdateBankModalVisible] = useState<boolean>(false);
  const [batchAddQuestionsToBankModalVisible, setBatchAddQuestionsToBankModalVisible] = useState<boolean>(false);
  const [batchRemoveQuestionsFromBankModalVisible, setBatchRemoveQuestionsFromBankModalVisible] = useState<boolean>(false);
  const [reviewModalVisible, setReviewModalVisible] = useState<boolean>(false);
  const [selectedQuestionIdList, setSelectedQuestionIdList] = useState<number[]>([]);
  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.Question>();

  const handleDelete = async (row: API.Question) => {
    const hide = message.loading("正在删除");
    if (!row) {
      hide();
      return true;
    }
    try {
      await deleteQuestionUsingPost({ id: row.id as any });
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

  const handleBatchDelete = async (questionIdList: number[]) => {
    const hide = message.loading("正在操作");
    try {
      await batchDeleteQuestionsUsingPost({ questionIdList });
      hide();
      message.success("操作成功");
      actionRef?.current?.reload();
    } catch (error: any) {
      hide();
      message.error("操作失败，" + error.message);
    }
  };

  const columns: ProColumns<API.Question>[] = [
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
      title: "内容",
      dataIndex: "content",
      valueType: "textarea",
      hideInTable: true,
      hideInSearch: true,
    },
    {
      title: "标签",
      dataIndex: "tags",
      valueType: "select",
      fieldProps: { mode: "tags" },
      render: (_, record) => {
        const tagList = parseTagList(record.tags);
        return <TagList tagList={tagList} />;
      },
    },
    {
      title: "难度",
      dataIndex: "difficulty",
      valueType: "select",
      valueEnum: QUESTION_DIFFICULTY_VALUE_ENUM,
      fieldProps: {
        options: QUESTION_DIFFICULTY_OPTIONS,
      },
      width: 100,
      render: (_, record) =>
        record.difficulty ? (
          <Tag color={QUESTION_DIFFICULTY_COLOR_MAP[record.difficulty] || "default"} className="rounded-full px-3 py-1 font-semibold">
            {record.difficulty}
          </Tag>
        ) : (
          <span className="text-slate-300">未设置</span>
        ),
    },
    {
      title: "题解",
      dataIndex: "answer",
      valueType: "textarea",
      hideInTable: true,
      hideInSearch: true,
    },
    {
      title: "出题用户",
      dataIndex: "userId",
      valueType: "digit",
      width: 100,
      hideInForm: true,
    },
    {
      title: "审核状态",
      dataIndex: "reviewStatus",
      valueType: "select",
      width: 120,
      hideInForm: true,
      valueEnum: QUESTION_REVIEW_STATUS_VALUE_ENUM,
      render: (_, record) => {
        const reviewStatus = Number(record.reviewStatus ?? 1);
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
      hideInForm: true,
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
          <button
            onClick={() => {
              setCurrentRow(record);
              setUpdateBankModalVisible(true);
            }}
            className="flex items-center gap-1.5 text-orange-500 hover:text-orange-600 font-bold transition-colors"
          >
            <Link2 className="h-4 w-4" />
            修改题库
          </button>
          <button
            onClick={() => {
              setCurrentRow(record);
              setReviewModalVisible(true);
            }}
            className="flex items-center gap-1.5 text-emerald-600 hover:text-emerald-700 font-bold transition-colors"
          >
            审核
          </button>
          <Popconfirm
            title="确认删除题目"
            description="删除后题目、题库关联和搜索索引都会一起移除，请谨慎操作。"
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(record)}
          >
            <button
              className="flex items-center gap-1.5 text-red-500 hover:text-red-600 font-bold transition-colors"
            >
              <Trash2 className="h-4 w-4" />
              删除
            </button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-700">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 bg-white/70 backdrop-blur-xl rounded-[2.5rem] p-8 sm:p-12 border border-white shadow-2xl shadow-slate-200/50 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-8 opacity-5">
           <Database className="h-32 w-32 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-3">
           <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-xs">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              Question Repository
           </div>
           <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900">
             {activeView === "comments" ? "题目评论审核" : "题目库管理"}
           </h1>
           <p className="text-slate-500 font-medium text-lg">
             {activeView === "comments"
               ? "把题目评论审核收进题目管理工作台，处理内容时不用再切换孤立页面。"
               : "高效组织、编辑和批量维护平台的面试题目内容。"}
           </p>
          <div className="flex flex-wrap gap-3 pt-2">
            <Link
              href="/admin/question"
              className={`rounded-full px-4 py-2 text-sm font-black transition ${
                activeView === "questions"
                  ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
                  : "bg-white text-slate-600 ring-1 ring-slate-200 hover:text-slate-900"
              }`}
            >
              题目列表
            </Link>
            <Link
              href="/admin/question?view=comments"
              className={`rounded-full px-4 py-2 text-sm font-black transition ${
                activeView === "comments"
                  ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
                  : "bg-white text-slate-600 ring-1 ring-slate-200 hover:text-slate-900"
              }`}
            >
              评论审核
            </Link>
          </div>
        </div>
        <div className="relative z-10 flex flex-wrap gap-3">
          {activeView === "questions" ? (
            <>
              <Link
                href="/admin/question/ai"
                className="flex items-center gap-2 bg-slate-100 hover:bg-slate-200 text-slate-700 h-14 px-8 rounded-2xl font-black text-lg transition-all border border-slate-200 shadow-sm"
              >
                <Wand2 className="h-6 w-6 text-primary" />
                AI 生成
              </Link>
              <button
                onClick={() => setCreateModalVisible(true)}
                className="flex items-center gap-2 bg-primary hover:bg-primary/90 text-primary-foreground h-14 px-8 rounded-2xl font-black text-lg transition-all shadow-xl shadow-primary/25"
              >
                <Plus className="h-6 w-6" />
                新建题目
              </button>
            </>
          ) : (
            <Link
              href="/admin/question"
              className="flex items-center gap-2 bg-primary hover:bg-primary/90 text-primary-foreground h-14 px-8 rounded-2xl font-black text-lg transition-all shadow-xl shadow-primary/25"
            >
              返回题目列表
            </Link>
          )}
        </div>
      </div>

      {activeView === "questions" ? (
        <div className="bg-white rounded-[2.5rem] border border-slate-100 shadow-2xl shadow-slate-200/50 overflow-hidden p-4 sm:p-6 pb-12 ant-table-premium">
          <ProTable<API.Question>
            headerTitle={null}
            actionRef={actionRef}
            rowKey="id"
            rowSelection={{
              selections: [Table.SELECTION_ALL, Table.SELECTION_INVERT],
            }}
            tableAlertOptionRender={({ selectedRowKeys }) => (
              <Space size={16}>
                <Button
                  type="primary"
                  ghost
                  onClick={() => {
                    setSelectedQuestionIdList(selectedRowKeys as number[]);
                    setBatchAddQuestionsToBankModalVisible(true);
                  }}
                  className="rounded-xl font-bold h-10 border-primary text-primary"
                >
                  批量加入题库
                </Button>
                <Button
                  danger
                  ghost
                  onClick={() => {
                    setSelectedQuestionIdList(selectedRowKeys as number[]);
                    setBatchRemoveQuestionsFromBankModalVisible(true);
                  }}
                  className="rounded-xl font-bold h-10"
                >
                  从题库移除
                </Button>
                <Popconfirm
                  title="确认删除"
                  onConfirm={() => handleBatchDelete(selectedRowKeys as number[])}
                >
                  <Button danger className="rounded-xl font-bold h-10">批量删除</Button>
                </Popconfirm>
              </Space>
            )}
            request={async (params, sort, filter) => {
              try {
                const queryRequest = buildQuestionQueryRequest(
                  {
                    ...params,
                    ...filter,
                  },
                  sort as Record<string, "ascend" | "descend" | null>,
                );
                const { data, code } = (await listQuestionByPageUsingPost(
                  queryRequest,
                )) as unknown as API.BaseResponsePageQuestion_;
                return {
                  success: code === 0,
                  data: data?.records || [],
                  total: Number(data?.total) || 0,
                };
              } catch (error: any) {
                message.error(error?.message || "加载题目管理数据失败");
                return {
                  success: false,
                  data: [],
                  total: 0,
                };
              }
            }}
            columns={columns}
            scroll={{ x: true }}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
            }}
          />
        </div>
      ) : (
        <QuestionCommentModerationPanel />
      )}

      {activeView === "questions" && createModalVisible && (
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
      {activeView === "questions" && updateModalVisible && currentRow && (
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
      {activeView === "questions" && updateBankModalVisible && currentRow?.id && (
        <UpdateBankModal
          visible={updateBankModalVisible}
          questionId={currentRow.id}
          onCancel={() => setUpdateBankModalVisible(false)}
        />
      )}
      {activeView === "questions" && reviewModalVisible && currentRow && (
        <ReviewModal
          open={reviewModalVisible}
          question={currentRow}
          onCancel={() => {
            setReviewModalVisible(false);
            setCurrentRow(undefined);
          }}
          onSuccess={() => {
            setReviewModalVisible(false);
            setCurrentRow(undefined);
            actionRef.current?.reload();
          }}
        />
      )}
      {activeView === "questions" && batchAddQuestionsToBankModalVisible && (
        <BatchAddQuestionsToBankModal
          visible={batchAddQuestionsToBankModalVisible}
          questionIdList={selectedQuestionIdList}
          onSubmit={() => {
            setBatchAddQuestionsToBankModalVisible(false);
          }}
          onCancel={() => setBatchAddQuestionsToBankModalVisible(false)}
        />
      )}
      {activeView === "questions" && batchRemoveQuestionsFromBankModalVisible && (
        <BatchRemoveQuestionsFromBankModal
          visible={batchRemoveQuestionsFromBankModalVisible}
          questionIdList={selectedQuestionIdList}
          onSubmit={() => {
            setBatchRemoveQuestionsFromBankModalVisible(false);
          }}
          onCancel={() => setBatchRemoveQuestionsFromBankModalVisible(false)}
        />
      )}
    </div>
  );
};

export default QuestionAdminPage;
