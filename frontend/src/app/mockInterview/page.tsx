"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  Button,
  Card,
  Empty,
  List,
  Pagination,
  Popconfirm,
  Progress,
  Segmented,
  Spin,
  Tag,
  Typography,
  message,
} from "antd";
import {
  ArrowRight,
  BrainCircuit,
  Briefcase,
  Clock3,
  Download,
  RefreshCw,
  Sparkles,
  Trash2,
} from "lucide-react";
import {
  deleteMockInterviewUsingPost,
  downloadMockInterviewReviewUsingGet,
  listMockInterviewVoByPageUsingPost,
} from "@/api/mockInterviewController";

const { Title, Paragraph, Text } = Typography;

const PAGE_SIZE = 6;

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: "待开始", color: "orange" },
  1: { text: "进行中", color: "green" },
  2: { text: "已结束", color: "red" },
  3: { text: "已暂停", color: "gold" },
};

const statusFilterOptions = [
  { label: "全部", value: "all" },
  { label: "待开始", value: "0" },
  { label: "进行中", value: "1" },
  { label: "已结束", value: "2" },
  { label: "已暂停", value: "3" },
] as const;

type StatusFilterValue = (typeof statusFilterOptions)[number]["value"];

interface InterviewReportPreview {
  overallScore?: number;
  readinessLevel?: string;
  currentFocus?: string;
  nextActionHint?: string;
  summary?: string;
  improvements?: string[];
  practicePlan?: string[];
}

function safeParseJson<T>(value?: string | null): T | null {
  if (!value) {
    return null;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

function abbreviateText(text?: string, maxLength = 96) {
  const normalized = (text || "").replace(/\s+/g, " ").trim();
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, Math.max(0, maxLength - 1)).trim()}…`;
}

export default function MockInterviewHomePage() {
  const [loading, setLoading] = useState(true);
  const [interviewList, setInterviewList] = useState<API.MockInterview[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>("all");
  const [exportingId, setExportingId] = useState<string | number>();
  const [deletingId, setDeletingId] = useState<string | number>();

  const loadMyInterviews = async (
    nextCurrent = current,
    nextStatusFilter: StatusFilterValue = statusFilter,
  ) => {
    setLoading(true);
    try {
      const res = await listMockInterviewVoByPageUsingPost({
        current: nextCurrent,
        pageSize: PAGE_SIZE,
        status: nextStatusFilter === "all" ? undefined : Number(nextStatusFilter),
        sortField: "updateTime",
        sortOrder: "descend",
      });
      setInterviewList(res.data?.records || []);
      setTotal(Number(res.data?.total || 0));
      setCurrent(nextCurrent);
      setStatusFilter(nextStatusFilter);
    } catch (error: any) {
      message.error(error?.message || "加载模拟面试记录失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadMyInterviews(1, "all");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleExport = async (id?: string | number) => {
    if (!id) {
      return;
    }
    setExportingId(id);
    try {
      const { blob, fileName } = await downloadMockInterviewReviewUsingGet(id);
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(objectUrl);
      message.success("逐题复盘已导出");
    } catch (error: any) {
      message.error(error?.message || "导出复盘失败");
    } finally {
      setExportingId(undefined);
    }
  };

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    setDeletingId(id);
    try {
      await deleteMockInterviewUsingPost({ id });
      message.success("模拟面试记录已删除");
      const fallbackPage = interviewList.length === 1 && current > 1 ? current - 1 : current;
      await loadMyInterviews(fallbackPage, statusFilter);
    } catch (error: any) {
      message.error(error?.message || "删除失败");
    } finally {
      setDeletingId(undefined);
    }
  };

  return (
    <div className="max-width-content space-y-8">
      <Card className="rounded-[2rem] border border-slate-100 shadow-2xl shadow-slate-200/50 overflow-hidden">
        <div className="relative px-2 py-4 sm:p-6">
          <div className="absolute right-0 top-0 opacity-5 p-8">
            <BrainCircuit className="h-32 w-32 text-slate-900" />
          </div>
          <div className="relative z-10 max-w-3xl space-y-5">
            <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-xs font-black uppercase tracking-widest text-primary">
              <Sparkles className="h-3.5 w-3.5" />
              AI Mock Interview
            </div>
            <Title level={2} className="!mb-0 !font-black !text-slate-900">
              用完整对话流模拟真实技术面试
            </Title>
            <Paragraph className="!mb-0 text-base font-medium text-slate-500">
              选择目标岗位、经验和难度后，系统会生成连续追问，并在结束时自动给出评价建议，方便你把“刷题”真正转成“答题表达能力”。
            </Paragraph>
            <div className="flex flex-wrap gap-4 pt-2">
              <Link href="/mockInterview/add">
                <Button type="primary" size="large" className="h-12 rounded-2xl px-6 font-bold">
                  发起新的模拟面试
                </Button>
              </Link>
              <Link href="/questions">
                <Button size="large" className="h-12 rounded-2xl px-6 font-bold">
                  先去刷题热身
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </Card>

      <Card
        title={<span className="text-lg font-black text-slate-800">我的模拟面试记录</span>}
        className="rounded-[2rem] border border-slate-100 shadow-xl shadow-slate-200/40"
        extra={
          <div className="flex flex-wrap items-center justify-end gap-3">
            <Segmented
              options={statusFilterOptions as any}
              value={statusFilter}
              onChange={(value) => void loadMyInterviews(1, value as StatusFilterValue)}
            />
            <Text className="text-slate-400">共 {total} 条</Text>
          </div>
        }
      >
        {loading ? (
          <div className="py-16 text-center">
            <Spin />
          </div>
        ) : interviewList.length === 0 ? (
          <Empty
            description="还没有模拟面试记录，先创建一场试试看"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Link href="/mockInterview/add">
              <Button type="primary">立即创建</Button>
            </Link>
          </Empty>
        ) : (
          <>
            <List
              dataSource={interviewList}
              split={false}
              renderItem={(item) => {
                const status = statusMap[item.status ?? 0] || statusMap[0];
                const report = safeParseJson<InterviewReportPreview>(item.report);
                const progressPercent = Math.min(
                  100,
                  Math.round(((item.currentRound || 0) / Math.max(1, item.expectedRounds || 5)) * 100),
                );
                const practicePlan = (report?.practicePlan || []).slice(0, 2);
                const improvementTags = (report?.improvements || []).slice(0, 3);
                return (
                  <List.Item className="!px-0">
                    <Card className="w-full rounded-2xl border border-slate-100 shadow-sm">
                      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                        <div className="min-w-0 flex-1 space-y-3">
                          <div className="flex flex-wrap items-center gap-3">
                            <Title level={4} className="!mb-0 !text-slate-900">
                              {item.jobPosition || "未命名模拟面试"}
                            </Title>
                            <Tag color={status.color}>{status.text}</Tag>
                          </div>
                          <div className="flex flex-wrap gap-4 text-sm text-slate-500">
                            <span className="inline-flex items-center gap-1.5">
                              <Briefcase size={14} />
                              {item.workExperience || "经验不限"}
                            </span>
                            <span className="inline-flex items-center gap-1.5">
                              <BrainCircuit size={14} />
                              {item.interviewType || "技术深挖"}
                            </span>
                            <span className="inline-flex items-center gap-1.5">
                              <Clock3 size={14} />
                              难度：{item.difficulty || "中等"}
                            </span>
                            <span className="inline-flex items-center gap-1.5">
                              <Sparkles size={14} />
                              轮次：{item.currentRound || 0}/{item.expectedRounds || 5}
                            </span>
                          </div>
                          <div className="max-w-xl">
                            <div className="mb-1 flex items-center justify-between gap-3 text-xs font-bold text-slate-400">
                              <span>轮次进度</span>
                              <span>{progressPercent}%</span>
                            </div>
                            <Progress percent={progressPercent} showInfo={false} strokeColor="#1677ff" />
                          </div>
                          {report?.readinessLevel ? (
                            <div className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-bold text-primary">
                              当前就绪度：{report.readinessLevel}
                            </div>
                          ) : null}
                          {report?.currentFocus && item.status !== 2 ? (
                            <Text className="block text-slate-500">
                              当前停留重点：{abbreviateText(report.currentFocus, 88)}
                            </Text>
                          ) : null}
                          {report?.nextActionHint && item.status !== 2 ? (
                            <Text className="block text-slate-500">
                              下一步回答抓手：{abbreviateText(report.nextActionHint, 96)}
                            </Text>
                          ) : null}
                          {report?.summary && item.status === 2 ? (
                            <Text className="block text-slate-500">
                              复盘摘要：{abbreviateText(report.summary, 120)}
                            </Text>
                          ) : null}
                          {item.status === 2 && (practicePlan.length || improvementTags.length || report?.overallScore) ? (
                            <div className="grid gap-3 rounded-2xl bg-slate-50 p-3 md:grid-cols-[96px_minmax(0,1fr)]">
                              <div className="flex items-center gap-3 md:block">
                                <div className="text-xs font-black uppercase tracking-widest text-slate-400">Score</div>
                                <div className="text-2xl font-black text-slate-900">{report?.overallScore || 0}</div>
                              </div>
                              <div className="min-w-0 space-y-2">
                                {improvementTags.length ? (
                                  <div className="flex flex-wrap gap-2">
                                    {improvementTags.map((tag) => (
                                      <span
                                        className="rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-700"
                                        key={tag}
                                      >
                                        {abbreviateText(tag, 28)}
                                      </span>
                                    ))}
                                  </div>
                                ) : null}
                                {practicePlan.length ? (
                                  <div className="space-y-1 text-xs font-semibold leading-6 text-slate-500">
                                    {practicePlan.map((plan, index) => (
                                      <div key={`${index}-${plan}`}>
                                        训练 {index + 1}：{abbreviateText(plan, 72)}
                                      </div>
                                    ))}
                                  </div>
                                ) : null}
                              </div>
                            </div>
                          ) : null}
                          <Text className="text-slate-400">
                            最近更新时间：{item.updateTime ? new Date(item.updateTime).toLocaleString() : "-"}
                          </Text>
                        </div>
                        <div className="flex flex-wrap gap-3">
                          <Link href={`/mockInterview/add?from=${item.id}`}>
                            <Button className="h-11 rounded-2xl px-5 font-bold">
                              <RefreshCw size={16} />
                              再来一场
                            </Button>
                          </Link>
                          {item.status === 2 ? (
                            <Button
                              className="h-11 rounded-2xl px-5 font-bold"
                              loading={exportingId === item.id}
                              onClick={() => void handleExport(item.id)}
                            >
                              <Download size={16} />
                              导出复盘
                            </Button>
                          ) : null}
                          <Link href={`/mockInterview/chat/${item.id}`}>
                            <Button type="primary" className="h-11 rounded-2xl px-5 font-bold">
                              {item.status === 3 ? "继续会话" : item.status === 2 ? "查看复盘" : "进入会话"}
                              <ArrowRight size={16} />
                            </Button>
                          </Link>
                          <Popconfirm
                            title="确认删除这条模拟面试记录？"
                            description="删除后无法恢复，逐题复盘也会一起移除。"
                            okText="确认删除"
                            cancelText="取消"
                            okButtonProps={{ danger: true }}
                            onConfirm={() => void handleDelete(item.id)}
                          >
                            <Button
                              danger
                              className="h-11 rounded-2xl px-5 font-bold"
                              loading={deletingId === item.id}
                            >
                              <Trash2 size={16} />
                              删除
                            </Button>
                          </Popconfirm>
                        </div>
                      </div>
                    </Card>
                  </List.Item>
                );
              }}
            />
            {total > PAGE_SIZE ? (
              <div className="mt-6 flex justify-center">
                <Pagination
                  current={current}
                  pageSize={PAGE_SIZE}
                  total={total}
                  showSizeChanger={false}
                  onChange={(page) => void loadMyInterviews(page, statusFilter)}
                />
              </div>
            ) : null}
          </>
        )}
      </Card>
    </div>
  );
}
