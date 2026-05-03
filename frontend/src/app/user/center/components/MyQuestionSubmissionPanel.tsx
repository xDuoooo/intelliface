"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Alert, Button, Card, Empty, Form, Input, List, message, Modal, Pagination, Select, Space, Tag, Typography } from "antd";
import { addQuestionUsingPost, deleteQuestionUsingPost, editQuestionUsingPost, listMyQuestionVoByPageUsingPost, submitQuestionReviewUsingPost } from "@/api/questionController";
import TagSearchSelect from "@/components/TagSearchSelect";
import TagList from "@/components/TagList";
import {
  QUESTION_DIFFICULTY_COLOR_MAP,
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_REVIEW_STATUS_COLOR_MAP,
  QUESTION_REVIEW_STATUS_ENUM,
  QUESTION_REVIEW_STATUS_OPTIONS,
  QUESTION_REVIEW_STATUS_TEXT_MAP,
} from "@/constants/question";
import { formatDateTime } from "@/lib/utils";

const { Title, Paragraph, Text } = Typography;

type QuestionFormValues = {
  title: string;
  tags: string[];
  content: string;
  answer: string;
};

type QuestionFilterState = {
  title?: string;
  reviewStatus?: number;
  difficulty?: string;
  sortKey: string;
};

const SORT_OPTIONS = [
  { label: "最新提交", value: "createTime_desc" },
  { label: "最近更新", value: "updateTime_desc" },
  { label: "审核时间最新", value: "reviewTime_desc" },
  { label: "标题 A-Z", value: "title_asc" },
  { label: "标题 Z-A", value: "title_desc" },
];

const getSortParams = (sortKey: string) => {
  const [sortField, sortOrderKey] = sortKey.split("_");
  return {
    sortField,
    sortOrder: sortOrderKey === "asc" ? "ascend" : "descend",
  };
};

interface SubmissionModalProps {
  open: boolean;
  question?: API.QuestionVO;
  onCancel: () => void;
  onSuccess: () => void;
}

const SubmissionModal: React.FC<SubmissionModalProps> = ({ open, question, onCancel, onSuccess }) => {
  const [form] = Form.useForm<QuestionFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const isEdit = Boolean(question?.id);

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        title: question?.title || "",
        tags: question?.tagList || [],
        content: question?.content || "",
        answer: question?.answer || "",
      });
    } else {
      form.resetFields();
    }
  }, [form, open, question]);

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    const hide = message.loading(isEdit ? "正在保存题目修改" : "正在提交题目");
    try {
      if (isEdit && question?.id) {
        await editQuestionUsingPost({
          id: question.id,
          ...values,
        });
      } else {
        await addQuestionUsingPost(values);
      }
      hide();
      if (isEdit) {
        message.success(
          Number(question?.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.APPROVED
            ? "修改已保存，题目已重新进入审核"
            : "修改已保存",
        );
      } else {
        message.success("题目已保存到我的题目，准备公开时可再提交审核");
      }
      onSuccess();
    } catch (error: any) {
      hide();
      message.error((isEdit ? "保存失败，" : "提交失败，") + (error?.message || "请稍后重试"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={isEdit ? "修改题目" : "新增题目与题解"}
      open={open}
      onCancel={onCancel}
      onOk={handleSubmit}
      okText={isEdit ? "保存修改" : "保存为私有"}
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnClose
      width={760}
    >
      <Form form={form} layout="vertical">
        <Form.Item label="题目标题" name="title" rules={[{ required: true, message: "请输入题目标题" }, { max: 80, message: "标题不能超过 80 个字符" }]}>
          <Input placeholder="例如：Spring 事务失效的常见原因有哪些？" />
        </Form.Item>
        <Form.Item
          label="标签"
          name="tags"
          rules={[{ required: true, message: "请至少填写一个标签" }]}
          extra="按 Enter 可继续添加标签，例如：Java、Spring、MySQL"
        >
          <TagSearchSelect scene="question" placeholder="请输入题目标签" tokenSeparators={[","]} />
        </Form.Item>
        <Form.Item label="题目内容" name="content" rules={[{ required: true, message: "请输入题目内容" }]}>
          <Input.TextArea rows={7} placeholder="请完整描述题目背景、要求和考察点，支持 Markdown 文本。" />
        </Form.Item>
        <Form.Item label="参考题解" name="answer" rules={[{ required: true, message: "请输入参考题解" }]}>
          <Input.TextArea rows={8} placeholder="请填写结构化题解或参考答案，支持 Markdown 文本。" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

const MyQuestionSubmissionPanel: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [questionList, setQuestionList] = useState<API.QuestionVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [modalVisible, setModalVisible] = useState(false);
  const [currentQuestion, setCurrentQuestion] = useState<API.QuestionVO>();
  const [filters, setFilters] = useState<QuestionFilterState>({
    sortKey: "createTime_desc",
  });

  const loadData = async (
    nextCurrent = current,
    nextPageSize = pageSize,
    nextFilters: QuestionFilterState = filters,
  ) => {
    setLoading(true);
    try {
      const { sortField, sortOrder } = getSortParams(nextFilters.sortKey);
      const res = (await listMyQuestionVoByPageUsingPost({
        current: nextCurrent,
        pageSize: nextPageSize,
        sortField,
        sortOrder,
        title: nextFilters.title?.trim() || undefined,
        reviewStatus: nextFilters.reviewStatus,
        difficulty: nextFilters.difficulty,
      })) as API.BaseResponsePageQuestionVO_;
      setQuestionList(res.data?.records || []);
      setTotal(Number(res.data?.total) || 0);
      setCurrent(nextCurrent);
      setPageSize(nextPageSize);
    } catch (error: any) {
      message.error("加载题目记录失败，" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData(1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reviewSummary = useMemo(() => {
    const privateCount = questionList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.PRIVATE).length;
    const pendingCount = questionList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.PENDING).length;
    const rejectedCount = questionList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.REJECTED).length;
    if (privateCount > 0 || pendingCount > 0 || rejectedCount > 0) {
      return `当前页有 ${privateCount} 道私有题目、${pendingCount} 道待审核题目、${rejectedCount} 道已驳回题目。准备公开时再主动提交审核会更稳。`;
    }
    return "新增题目默认先保存为私有，只有审核通过的题目才会出现在公开题库与题目列表里。";
  }, [questionList]);

  const handleSubmitReview = async (questionId?: string | number) => {
    if (!questionId) {
      return;
    }
    const hide = message.loading("正在提交审核");
    try {
      await submitQuestionReviewUsingPost({ id: questionId });
      hide();
      message.success("题目已提交审核");
      await loadData(current, pageSize, filters);
    } catch (error: any) {
      hide();
      message.error("提交审核失败，" + (error?.message || "请稍后重试"));
    }
  };

  const handleDelete = async (questionId?: string | number) => {
    if (!questionId) {
      return;
    }
    const hide = message.loading("正在删除题目");
    try {
      await deleteQuestionUsingPost({ id: questionId });
      hide();
      message.success("题目已删除");
      const nextCurrent = questionList.length === 1 && current > 1 ? current - 1 : current;
      await loadData(nextCurrent, pageSize, filters);
    } catch (error: any) {
      hide();
      message.error("删除失败，" + (error?.message || "请稍后重试"));
    }
  };

  return (
    <div className="space-y-6">
      <Card className="rounded-[2rem] border border-slate-100 shadow-lg shadow-slate-200/40">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <Title level={4} style={{ margin: 0 }}>
              我的题目
            </Title>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              题目默认先保存在你自己的空间里，准备公开时再主动提交审核，审核通过后才会出现在公开题库与题目列表里。
            </Paragraph>
          </div>
          <Button
            type="primary"
            onClick={() => {
              setCurrentQuestion(undefined);
              setModalVisible(true);
            }}
          >
            新增题目
          </Button>
        </div>
      </Card>

      <Card className="rounded-[2rem] border border-slate-100 shadow-lg shadow-slate-200/40">
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <Title level={5} style={{ margin: 0 }}>
                排序与筛选
              </Title>
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                支持按标题、审核状态、难度筛选，并切换题目排序方式。
              </Paragraph>
            </div>
            <Text type="secondary">共 {total} 道题目</Text>
          </div>
          <div className="grid gap-3 xl:grid-cols-[1.6fr_1fr_1fr_1fr_auto]">
            <Input
              allowClear
              placeholder="按题目标题搜索"
              value={filters.title}
              onChange={(e) => setFilters((prev) => ({ ...prev, title: e.target.value }))}
              onPressEnter={() => void loadData(1, pageSize, filters)}
            />
            <Select
              allowClear
              placeholder="审核状态"
              options={QUESTION_REVIEW_STATUS_OPTIONS}
              value={filters.reviewStatus}
              onChange={(value) => setFilters((prev) => ({ ...prev, reviewStatus: value }))}
            />
            <Select
              allowClear
              placeholder="难度"
              options={QUESTION_DIFFICULTY_OPTIONS}
              value={filters.difficulty}
              onChange={(value) => setFilters((prev) => ({ ...prev, difficulty: value }))}
            />
            <Select
              placeholder="排序方式"
              options={SORT_OPTIONS}
              value={filters.sortKey}
              onChange={(value) => {
                const nextFilters = { ...filters, sortKey: value };
                setFilters(nextFilters);
                void loadData(1, pageSize, nextFilters);
              }}
            />
            <Space wrap>
              <Button type="primary" onClick={() => void loadData(1, pageSize, filters)}>
                应用筛选
              </Button>
              <Button
                onClick={() => {
                  const nextFilters: QuestionFilterState = { sortKey: "createTime_desc" };
                  setFilters(nextFilters);
                  void loadData(1, pageSize, nextFilters);
                }}
              >
                重置
              </Button>
            </Space>
          </div>
        </div>
      </Card>

      <Alert
        type="info"
        showIcon
        message="审核说明"
        description={reviewSummary}
        className="rounded-2xl border-blue-100 bg-blue-50/60"
      />

      <Card className="rounded-[2rem] border border-slate-100 shadow-lg shadow-slate-200/40">
        {questionList.length === 0 && !loading ? (
          <Empty description="你还没有自己的题目，试着新增第一道题吧" image={Empty.PRESENTED_IMAGE_SIMPLE}>
            <Button
              type="primary"
              onClick={() => {
                setCurrentQuestion(undefined);
                setModalVisible(true);
              }}
            >
              立即新增
            </Button>
          </Empty>
        ) : (
          <List
            loading={loading}
            dataSource={questionList}
            renderItem={(item) => {
              const reviewStatus = Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED);
              const canSubmitReview =
                reviewStatus === QUESTION_REVIEW_STATUS_ENUM.PRIVATE || reviewStatus === QUESTION_REVIEW_STATUS_ENUM.REJECTED;
              return (
                <List.Item className="px-0 py-4">
                  <Card className="w-full rounded-[1.5rem] border border-slate-100 shadow-sm">
                    <div className="space-y-4">
                      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                        <div className="space-y-3">
                          <div className="flex flex-wrap items-center gap-3">
                            <Link href={`/question/${item.id}`} className="text-lg font-black text-slate-900 hover:text-primary">
                              {item.title}
                            </Link>
                            {item.difficulty ? (
                              <Tag color={QUESTION_DIFFICULTY_COLOR_MAP[item.difficulty] || "default"} className="rounded-full px-3 py-1 font-semibold">
                                {item.difficulty}
                              </Tag>
                            ) : null}
                            <Tag color={QUESTION_REVIEW_STATUS_COLOR_MAP[reviewStatus] || "default"} className="rounded-full px-3 py-1 font-semibold">
                              {QUESTION_REVIEW_STATUS_TEXT_MAP[reviewStatus] || "未知状态"}
                            </Tag>
                          </div>
                          <TagList tagList={item.tagList || []} />
                          <div className="flex flex-wrap gap-4 text-sm text-slate-500">
                            <Text type="secondary">提交时间：{formatDateTime(item.createTime)}</Text>
                            <Text type="secondary">最近更新：{formatDateTime(item.updateTime)}</Text>
                            {item.reviewTime ? <Text type="secondary">审核时间：{formatDateTime(item.reviewTime)}</Text> : null}
                          </div>
                        </div>
                        <Space wrap>
                          <Link href={`/question/${item.id}`}>
                            <Button>预览</Button>
                          </Link>
                          {canSubmitReview ? (
                            <Button
                              type="primary"
                              ghost
                              onClick={() => void handleSubmitReview(item.id)}
                            >
                              {reviewStatus === QUESTION_REVIEW_STATUS_ENUM.REJECTED ? "重新提交审核" : "提交审核"}
                            </Button>
                          ) : null}
                          <Button
                            onClick={() => {
                              setCurrentQuestion(item);
                              setModalVisible(true);
                            }}
                          >
                            编辑
                          </Button>
                          <Button
                            danger
                            onClick={() =>
                              Modal.confirm({
                                title: "确认删除这道题目吗？",
                                content: "删除后将无法恢复，对应题目会从列表中移除。",
                                okText: "确认删除",
                                cancelText: "取消",
                                okButtonProps: { danger: true },
                                onOk: () => handleDelete(item.id),
                              })
                            }
                          >
                            删除
                          </Button>
                        </Space>
                      </div>
                      {item.reviewMessage ? (
                        <div className="rounded-2xl border border-amber-100 bg-amber-50/70 px-4 py-3 text-sm text-amber-800">
                          <div className="mb-1 font-semibold">审核意见</div>
                          <div>{item.reviewMessage}</div>
                        </div>
                      ) : null}
                    </div>
                  </Card>
                </List.Item>
              );
            }}
          />
        )}
        {total > pageSize ? (
          <div className="mt-6 flex justify-end">
            <Pagination
              current={current}
              pageSize={pageSize}
              total={total}
              showSizeChanger
              onChange={(page, size) => {
                void loadData(page, size, filters);
              }}
            />
          </div>
        ) : null}
      </Card>

      <SubmissionModal
        open={modalVisible}
        question={currentQuestion}
        onCancel={() => {
          setModalVisible(false);
          setCurrentQuestion(undefined);
        }}
        onSuccess={() => {
          setModalVisible(false);
          setCurrentQuestion(undefined);
          void loadData(current, pageSize, filters);
        }}
      />
    </div>
  );
};

export default MyQuestionSubmissionPanel;
