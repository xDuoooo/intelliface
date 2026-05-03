"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  List,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
  Upload,
} from "antd";
import {
  addQuestionBankUsingPost,
  deleteQuestionBankUsingPost,
  editQuestionBankUsingPost,
  listMyQuestionBankVoByPageUsingPost,
  submitQuestionBankReviewUsingPost,
} from "@/api/questionBankController";
import { BookOpen, Image as ImageIcon, PencilLine, Plus, Trash2 } from "lucide-react";
import { formatDateTime, validateImageSrc } from "@/lib/utils";
import { buildApiUrl } from "@/libs/request";
import ManageQuestionBankQuestionsModal from "./ManageQuestionBankQuestionsModal";
import {
  QUESTION_REVIEW_STATUS_COLOR_MAP,
  QUESTION_REVIEW_STATUS_ENUM,
  QUESTION_REVIEW_STATUS_OPTIONS,
  QUESTION_REVIEW_STATUS_TEXT_MAP,
} from "@/constants/question";

const { Title, Paragraph, Text } = Typography;

type QuestionBankFormValues = {
  title: string;
  description?: string;
  picture?: string;
};

type FilterState = {
  searchText?: string;
  reviewStatus?: number;
  sortKey: string;
};

const SORT_OPTIONS = [
  { label: "最新创建", value: "createTime_desc" },
  { label: "最近更新", value: "updateTime_desc" },
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

interface QuestionBankModalProps {
  open: boolean;
  questionBank?: API.QuestionBankVO;
  onCancel: () => void;
  onSuccess: () => void;
}

const QuestionBankModal: React.FC<QuestionBankModalProps> = ({
  open,
  questionBank,
  onCancel,
  onSuccess,
}) => {
  const [form] = Form.useForm<QuestionBankFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [pictureUrl, setPictureUrl] = useState("");
  const isEdit = Boolean(questionBank?.id);

  useEffect(() => {
    if (open) {
      const nextPicture = questionBank?.picture || "";
      form.setFieldsValue({
        title: questionBank?.title || "",
        description: questionBank?.description || "",
        picture: nextPicture,
      });
      setPictureUrl(nextPicture);
    } else {
      form.resetFields();
      setPictureUrl("");
      setUploadLoading(false);
    }
  }, [form, open, questionBank]);

  const beforeUpload = (file: File) => {
    const isSupportedType =
      file.type === "image/jpeg" || file.type === "image/png" || file.type === "image/webp";
    if (!isSupportedType) {
      message.error("仅支持 JPG / PNG / WebP 格式封面");
    }
    const isLt2M = file.size / 1024 / 1024 < 2;
    if (!isLt2M) {
      message.error("封面图片不能超过 2MB");
    }
    return isSupportedType && isLt2M;
  };

  const handleUploadChange = (info: any) => {
    if (info.file.status === "uploading") {
      setUploadLoading(true);
      return;
    }
    if (info.file.status === "done") {
      const { code, data, message: msg } = info.file.response || {};
      if (code === 0 && data) {
        form.setFieldValue("picture", data);
        setPictureUrl(data);
        message.success("题库封面上传成功");
      } else {
        message.error(msg || "封面上传失败");
      }
      setUploadLoading(false);
    } else if (info.file.status === "error") {
      message.error("服务器响应错误，封面上传失败");
      setUploadLoading(false);
    }
  };

  const handleClearPicture = () => {
    form.setFieldValue("picture", "");
    setPictureUrl("");
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    const hide = message.loading(isEdit ? "正在保存题库修改" : "正在创建题库");
    try {
      if (isEdit && questionBank?.id) {
        await editQuestionBankUsingPost({
          id: questionBank.id,
          ...values,
        });
      } else {
        await addQuestionBankUsingPost(values);
      }
      hide();
      if (isEdit) {
        message.success(
          Number(questionBank?.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.APPROVED
            ? "题库修改成功，已重新进入审核"
            : "题库修改成功",
        );
      } else {
        message.success("题库已创建为私有，你可以先慢慢整理，再决定是否公开");
      }
      onSuccess();
    } catch (error: any) {
      hide();
      message.error((isEdit ? "保存失败，" : "创建失败，") + (error?.message || "请稍后重试"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={isEdit ? "修改题库" : "新增题库"}
      open={open}
      onCancel={onCancel}
      onOk={handleSubmit}
      okText={isEdit ? "保存修改" : "保存为私有"}
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnClose
      width={720}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="题库标题"
          name="title"
          rules={[
            { required: true, message: "请输入题库标题" },
            { max: 80, message: "题库标题不能超过 80 个字符" },
          ]}
        >
          <Input placeholder="例如：Java 后端高频面试题库" />
        </Form.Item>
        <Form.Item
          label="题库简介"
          name="description"
          rules={[{ max: 300, message: "题库简介不能超过 300 个字符" }]}
        >
          <Input.TextArea
            rows={5}
            placeholder="可以描述题库适用方向、包含内容和适合人群，方便自己后续管理。"
          />
        </Form.Item>
        <Form.Item
          label="封面图片"
          extra="推荐直接上传封面图，也支持继续填写公开图片链接。"
        >
            <div className="space-y-4">
              <div className="flex flex-col gap-4 rounded-2xl border border-slate-100 bg-slate-50/70 p-4 sm:flex-row sm:items-center">
              <div className="relative flex h-28 w-full shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-white sm:w-40">
                {pictureUrl ? (
                  <Image
                    src={validateImageSrc(pictureUrl)}
                    alt="题库封面预览"
                    fill
                    className="object-cover"
                  />
                ) : (
                  <div className="flex flex-col items-center gap-2 text-slate-400">
                    <ImageIcon className="h-6 w-6" />
                    <span className="text-xs font-semibold">暂无封面</span>
                  </div>
                )}
              </div>
              <div className="flex-1 space-y-3">
                <div className="flex flex-wrap gap-3">
                  <Upload
                    name="file"
                    showUploadList={false}
                    action={buildApiUrl("/api/file/upload?biz=question_bank_cover")}
                    beforeUpload={beforeUpload}
                    onChange={handleUploadChange}
                    withCredentials
                  >
                    <Button
                      className="rounded-2xl"
                      icon={<ImageIcon size={16} />}
                      loading={uploadLoading}
                    >
                      上传封面
                    </Button>
                  </Upload>
                  <Button
                    className="rounded-2xl"
                    onClick={handleClearPicture}
                    disabled={!pictureUrl}
                  >
                    清空封面
                  </Button>
                </div>
                <div className="text-xs leading-6 text-slate-400">
                  支持 JPG / PNG / WebP，大小不超过 2MB。建议使用清晰的横向图片，题库列表里会更好看。
                </div>
              </div>
            </div>
            <Form.Item
              name="picture"
              noStyle
              rules={[{ type: "url", warningOnly: true, message: "建议填写合法的图片链接地址" }]}
            >
              <Input
                placeholder="也可以直接粘贴图片链接，例如 https://example.com/question-bank-cover.png"
                onChange={(event) => setPictureUrl(event.target.value.trim())}
              />
            </Form.Item>
          </div>
        </Form.Item>
      </Form>
    </Modal>
  );
};

const MyQuestionBankPanel: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(6);
  const [total, setTotal] = useState(0);
  const [modalVisible, setModalVisible] = useState(false);
  const [currentQuestionBank, setCurrentQuestionBank] = useState<API.QuestionBankVO>();
  const [manageModalVisible, setManageModalVisible] = useState(false);
  const [managedQuestionBank, setManagedQuestionBank] = useState<API.QuestionBankVO>();
  const [filters, setFilters] = useState<FilterState>({
    sortKey: "createTime_desc",
  });

  const loadData = async (
    nextCurrent = current,
    nextPageSize = pageSize,
    nextFilters: FilterState = filters,
  ) => {
    setLoading(true);
    try {
      const { sortField, sortOrder } = getSortParams(nextFilters.sortKey);
      const res = await listMyQuestionBankVoByPageUsingPost({
        current: nextCurrent,
        pageSize: nextPageSize,
        searchText: nextFilters.searchText?.trim() || undefined,
        reviewStatus: nextFilters.reviewStatus,
        sortField,
        sortOrder,
      });
      setQuestionBankList(res.data?.records || []);
      setTotal(Number(res.data?.total) || 0);
      setCurrent(nextCurrent);
      setPageSize(nextPageSize);
    } catch (error: any) {
      message.error("加载我的题库失败，" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData(1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const helperText = useMemo(() => {
    if (!questionBankList.length) {
      return "你还没有创建自己的题库，可以先新建一个方向题库，把相关题目慢慢沉淀进去。";
    }
    const privateCount = questionBankList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.PRIVATE).length;
    const pendingCount = questionBankList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.PENDING).length;
    const rejectedCount = questionBankList.filter((item) => Number(item.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED) === QUESTION_REVIEW_STATUS_ENUM.REJECTED).length;
    return `当前页有 ${privateCount} 个私有题库、${pendingCount} 个待审核题库、${rejectedCount} 个已驳回题库。公开题库只有审核通过后才会在首页和题库列表里出现。`;
  }, [questionBankList]);

  const handleSubmitReview = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在提交题库审核");
    try {
      await submitQuestionBankReviewUsingPost({ id });
      hide();
      message.success("题库已提交审核");
      await loadData(current, pageSize, filters);
    } catch (error: any) {
      hide();
      message.error("提交审核失败，" + (error?.message || "请稍后重试"));
    }
  };

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在删除题库");
    try {
      await deleteQuestionBankUsingPost({ id });
      hide();
      message.success("题库已删除");
      const nextCurrent = questionBankList.length === 1 && current > 1 ? current - 1 : current;
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
              我的题库
            </Title>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              这里适合先沉淀你自己整理的专项题库，准备公开时再主动提交审核，审核通过后才会出现在公共题库里。
            </Paragraph>
          </div>
          <Button
            type="primary"
            icon={<Plus className="h-4 w-4" />}
            onClick={() => {
              setCurrentQuestionBank(undefined);
              setModalVisible(true);
            }}
          >
            新增题库
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
                支持按关键词、审核状态查找题库，并切换列表排序方式。
              </Paragraph>
            </div>
            <Text type="secondary">共 {total} 个题库</Text>
          </div>
          <div className="grid gap-3 xl:grid-cols-[1.4fr_1fr_1fr_auto_auto]">
            <Input
              allowClear
              placeholder="搜索题库标题或简介"
              value={filters.searchText}
              onChange={(event) => {
                setFilters((prev) => ({
                  ...prev,
                  searchText: event.target.value,
                }));
              }}
              onPressEnter={() => void loadData(1, pageSize, filters)}
            />
            <Select
              allowClear
              placeholder="审核状态"
              value={filters.reviewStatus}
              options={QUESTION_REVIEW_STATUS_OPTIONS}
              onChange={(value) => {
                setFilters((prev) => ({
                  ...prev,
                  reviewStatus: value,
                }));
              }}
            />
            <Select
              value={filters.sortKey}
              options={SORT_OPTIONS}
              onChange={(value) => {
                const nextFilters = {
                  ...filters,
                  sortKey: value,
                };
                setFilters(nextFilters);
                void loadData(1, pageSize, nextFilters);
              }}
            />
            <Button
              onClick={() => void loadData(1, pageSize, filters)}
              loading={loading}
            >
              搜索
            </Button>
            <Button
              onClick={() => {
                const nextFilters: FilterState = {
                  sortKey: "createTime_desc",
                };
                setFilters(nextFilters);
                void loadData(1, pageSize, nextFilters);
              }}
            >
              重置
            </Button>
          </div>
        </div>
      </Card>

      <Alert
        type="info"
        showIcon
        message="题库管理提示"
        description={helperText}
        className="rounded-[1.5rem] border border-blue-100/70 bg-blue-50/70"
      />

      <Card className="rounded-[2rem] border border-slate-100 shadow-lg shadow-slate-200/40">
        <List
          loading={loading}
          locale={{
            emptyText: (
              <Empty
                description="还没有创建题库"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
          dataSource={questionBankList}
          renderItem={(questionBank) => {
            const reviewStatus = Number(questionBank.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED);
            const canSubmitReview =
              reviewStatus === QUESTION_REVIEW_STATUS_ENUM.PRIVATE || reviewStatus === QUESTION_REVIEW_STATUS_ENUM.REJECTED;
            return (
            <List.Item className="!px-0">
              <Card className="w-full rounded-[1.75rem] border border-slate-100 shadow-sm shadow-slate-200/30">
                <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                  <div className="flex min-w-0 flex-1 gap-4">
                    <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-[1.5rem] border border-slate-100 bg-slate-50">
                      {questionBank.picture ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={questionBank.picture}
                          alt={questionBank.title || "题库封面"}
                          className="h-full w-full object-cover"
                        />
                      ) : (
                        <BookOpen className="h-8 w-8 text-slate-300" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1 space-y-3">
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-3">
                          <Title level={5} style={{ margin: 0 }}>
                            {questionBank.title}
                          </Title>
                          <Tag color={QUESTION_REVIEW_STATUS_COLOR_MAP[reviewStatus] || "default"} className="rounded-full px-3 py-1 font-semibold">
                            {QUESTION_REVIEW_STATUS_TEXT_MAP[reviewStatus] || "未知状态"}
                          </Tag>
                        </div>
                        <Paragraph type="secondary" className="!mb-0">
                          {questionBank.description || "这个题库还没有补充简介，后续可以补充适用方向和内容范围。"}
                        </Paragraph>
                      </div>
                      <Space wrap size={[8, 8]}>
                        <Text type="secondary">创建于 {formatDateTime(questionBank.createTime)}</Text>
                        <Text type="secondary">更新于 {formatDateTime(questionBank.updateTime)}</Text>
                        {questionBank.picture ? (
                          <Text type="secondary" className="inline-flex items-center gap-1">
                            <ImageIcon className="h-3.5 w-3.5" /> 已配置封面
                          </Text>
                        ) : null}
                        {questionBank.reviewTime ? (
                          <Text type="secondary">审核于 {formatDateTime(questionBank.reviewTime)}</Text>
                        ) : null}
                      </Space>
                      {questionBank.reviewMessage ? (
                        <div className="rounded-2xl border border-amber-100 bg-amber-50/70 px-4 py-3 text-sm text-amber-800">
                          <div className="mb-1 font-semibold">审核意见</div>
                          <div>{questionBank.reviewMessage}</div>
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <Space wrap>
                    <Link href={`/bank/${questionBank.id}`}>
                      <Button>查看题库</Button>
                    </Link>
                    {canSubmitReview ? (
                      <Button
                        type="primary"
                        ghost
                        onClick={() => void handleSubmitReview(questionBank.id)}
                      >
                        {reviewStatus === QUESTION_REVIEW_STATUS_ENUM.REJECTED ? "重新提交审核" : "提交审核"}
                      </Button>
                    ) : null}
                    <Button
                      type="primary"
                      ghost
                      onClick={() => {
                        setManagedQuestionBank(questionBank);
                        setManageModalVisible(true);
                      }}
                    >
                      管理题目
                    </Button>
                    <Button
                      icon={<PencilLine className="h-4 w-4" />}
                      onClick={() => {
                        setCurrentQuestionBank(questionBank);
                        setModalVisible(true);
                      }}
                    >
                      编辑
                    </Button>
                    <Popconfirm
                      title="确认删除这个题库吗？"
                      description="删除后题库本身会移除，请确认内容已备份。"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => handleDelete(questionBank.id)}
                    >
                      <Button danger icon={<Trash2 className="h-4 w-4" />}>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                </div>
              </Card>
            </List.Item>
          )}}
        />

        {total > pageSize ? (
          <div className="mt-6 flex justify-end">
            <Pagination
              current={current}
              pageSize={pageSize}
              total={total}
              showSizeChanger
              pageSizeOptions={["6", "12", "18"]}
              onChange={(page, size) => {
                void loadData(page, size, filters);
              }}
            />
          </div>
        ) : null}
      </Card>

      <QuestionBankModal
        open={modalVisible}
        questionBank={currentQuestionBank}
        onCancel={() => setModalVisible(false)}
        onSuccess={() => {
          setModalVisible(false);
          setCurrentQuestionBank(undefined);
          void loadData(1, pageSize, filters);
        }}
      />

      <ManageQuestionBankQuestionsModal
        open={manageModalVisible}
        questionBank={managedQuestionBank}
        onCancel={() => {
          setManageModalVisible(false);
          setManagedQuestionBank(undefined);
        }}
      />
    </div>
  );
};

export default MyQuestionBankPanel;
