"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  Alert,
  Badge,
  Button,
  Form,
  Input,
  InputNumber,
  Tag,
  Typography,
  message,
} from "antd";
import {
  ArrowLeft,
  BrainCircuit,
  CheckCircle2,
  Database,
  Sparkles,
  Wand2,
} from "lucide-react";
import { aiGenerateQuestionsUsingPost } from "@/api/questionController";

const { Title, Text, Paragraph } = Typography;

const quickCounts = [5, 10, 15, 20];

const workflowHighlights = [
  {
    title: "批量生成",
    value: "1-20",
    description: "一次生成多道结构化题目，适合快速补齐题库。",
  },
  {
    title: "自动入库",
    value: "JSON",
    description: "题目、标签、难度、参考答案会一起落库，减少人工整理成本。",
  },
  {
    title: "结果可追溯",
    value: "Admin",
    description: "生成后可直接回到题目管理查看、审核和继续编辑。",
  },
];

const generateRules = [
  "尽量输入具体方向，例如“Redis 缓存雪崩治理”会比“Redis”更稳定。",
  "单次建议生成 5-15 道，既能保证质量，也不容易超时。",
  "如果想覆盖多个主题，建议拆成多次生成，而不是一次写很长的关键词。",
];

const exampleTopics = [
  "Spring Boot 接口幂等设计",
  "Redis 分布式锁实现",
  "MySQL 索引最左前缀原则",
  "Kafka 消息可靠性保障",
  "Go 并发调度模型",
  "系统设计中的限流熔断",
];

/**
 * AI 题目智能生成页面
 */
const AiGenerateQuestionPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [successCount, setSuccessCount] = useState<number | null>(null);
  const selectedCount = Form.useWatch("number", form);

  const doSubmit = async (values: API.QuestionAIGenerateRequest) => {
    setLoading(true);
    setSuccessCount(null);
    try {
      // @ts-ignore
      const res = await aiGenerateQuestionsUsingPost(values);
      if (res.data) {
        setSuccessCount(Number(res.data));
        message.success(`成功生成 ${res.data} 道题目！`);
        form.resetFields();
      }
    } catch (error: any) {
      message.error("生成失败：" + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div className="space-y-2">
          <Link
            href="/admin/question"
            className="group inline-flex items-center gap-2 text-sm font-bold text-slate-500 transition-colors hover:text-primary"
          >
            <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
            返回题目管理
          </Link>
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <Title level={2} className="!mb-0 !text-3xl !font-black tracking-tight !text-slate-900">
                AI 智能增题
              </Title>
              <Badge count="Beta" color="#2563eb" />
            </div>
            <Paragraph className="!mb-0 !mt-2 max-w-4xl text-base font-medium leading-7 text-slate-500">
              这是给题目管理用的批量生成工作台。输入一个明确方向，系统会直接生成题目、标签、难度和详细参考答案并入库，适合补齐专题内容后再回到题目管理做筛选和复核。
            </Paragraph>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-600 shadow-sm">
            <Sparkles className="h-4 w-4 text-primary" />
            生成后直接进入题目管理
          </div>
          <Link
            href="/admin/question"
            className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-900 px-4 py-3 text-sm font-bold text-white transition hover:bg-slate-800"
          >
            查看题目列表
          </Link>
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        {workflowHighlights.map((item) => (
          <section
            key={item.title}
            className="rounded-[1.75rem] border border-slate-200 bg-white px-5 py-5 shadow-sm shadow-slate-200/50"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">{item.title}</div>
              <div className="text-lg font-black tracking-tight text-slate-900">{item.value}</div>
            </div>
            <div className="mt-3 text-sm font-medium leading-6 text-slate-500">{item.description}</div>
          </section>
        ))}
      </div>

      {successCount !== null && (
        <Alert
          message={
            <div className="flex items-start gap-3 py-1">
              <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-500" />
              <div>
                <div className="text-base font-black text-emerald-800">生成成功</div>
                <div className="mt-1 text-sm font-medium leading-6 text-emerald-700">
                  已成功生成 {successCount} 道题目。建议现在回到题目管理查看结果，优先筛掉重复题和表达不稳的题干。
                </div>
              </div>
            </div>
          }
          type="success"
          className="rounded-[1.75rem] border-emerald-200 bg-emerald-50"
          showIcon={false}
        />
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section className="rounded-[2.25rem] border border-slate-200 bg-white px-6 py-6 shadow-xl shadow-slate-200/40 sm:px-8 sm:py-8">
          <div className="mb-8 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">
                <BrainCircuit className="h-3.5 w-3.5 text-primary" />
                生成配置
              </div>
              <Title level={3} className="!mb-1 !mt-3 !text-2xl !font-black tracking-tight !text-slate-900">
                配置本次题目生成任务
              </Title>
              <Text className="text-sm font-medium leading-6 text-slate-500">
                先确定一个足够具体的技术方向，再决定本次数量。这个页面更适合少量多次生成，方便你后续筛题和复核答案质量。
              </Text>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-600">
              默认会同时生成题干、标签、难度和详细参考答案
            </div>
          </div>

          <Form
            form={form}
            layout="vertical"
            onFinish={doSubmit}
            initialValues={{ number: 10 }}
            requiredMark={false}
          >
            <Form.Item
              label={<span className="font-bold text-slate-700">知识点 / 技术方向</span>}
              name="questionType"
              extra="输入越具体，生成结果越稳定。优先使用明确专题，不建议只写很大的技术名词。"
              rules={[{ required: true, message: "请输入题目方向，如 Java、Spring Boot" }]}
            >
              <Input
                placeholder="例如：Redis 分布式锁实现、Kafka 消息可靠性保障..."
                className="h-14 rounded-2xl border-slate-200 bg-slate-50 px-5 text-base font-medium transition-colors focus:bg-white"
              />
            </Form.Item>

            <div className="grid gap-6 lg:grid-cols-[220px_minmax(0,1fr)]">
              <Form.Item
                label={<span className="font-bold text-slate-700">生成数量</span>}
                name="number"
                extra="单次支持 1 - 20 道"
                rules={[{ required: true }]}
              >
                <InputNumber
                  min={1}
                  max={20}
                  className="h-14 w-full rounded-2xl border-slate-200 bg-slate-50 px-4"
                  size="large"
                />
              </Form.Item>

              <div className="rounded-[1.75rem] border border-slate-200 bg-slate-50/80 p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-black text-slate-800">快速数量</div>
                    <div className="mt-1 text-xs font-medium text-slate-500">常用批量规模，点一下即可填充到表单</div>
                  </div>
                  <div className="rounded-full bg-white px-3 py-1 text-xs font-bold text-slate-500 ring-1 ring-slate-200">
                    当前 {selectedCount || 10} 道
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {quickCounts.map((count) => {
                    const active = selectedCount === count;
                    return (
                      <button
                        key={count}
                        type="button"
                        onClick={() => form.setFieldValue("number", count)}
                        className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                          active
                            ? "bg-slate-900 text-white"
                            : "border border-slate-200 bg-white text-slate-600 hover:border-primary/30 hover:text-primary"
                        }`}
                      >
                        {count} 道
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="mt-2 rounded-[1.75rem] border border-slate-200 bg-slate-50/70 p-5">
              <div className="text-sm font-black text-slate-800">这次会一起生成</div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {[
                  "结构清晰的题目标题",
                  "便于直接入库的题干内容",
                  "可用于检索和筛选的标签",
                  "简单 / 中等 / 困难 难度",
                  "尽量详细的结构化参考答案",
                ].map((item) => (
                  <div
                    key={item}
                    className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3"
                  >
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                    <span className="text-sm font-medium leading-6 text-slate-600">{item}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex flex-col gap-4 pt-8 lg:flex-row lg:items-center lg:justify-between">
              <div className="text-sm font-medium leading-6 text-slate-500">
                建议先生成一小批，筛掉不满意的题目后，再继续补相邻专题。
              </div>
              <Button
                loading={loading}
                type="primary"
                htmlType="submit"
                size="large"
                className="h-14 min-w-[220px] rounded-2xl border-none bg-slate-900 px-8 text-base font-black shadow-lg shadow-slate-300/60 hover:bg-slate-800 [&>span]:inline-flex [&>span]:items-center [&>span]:gap-2"
              >
                {loading ? "正在生成题目..." : (
                  <span className="inline-flex items-center gap-2 whitespace-nowrap">
                    <Wand2 className="h-5 w-5" />
                    开始生成
                  </span>
                )}
              </Button>
            </div>
          </Form>
        </section>

        <aside className="space-y-6">
          <section className="rounded-[2rem] border border-slate-200 bg-white px-5 py-5 shadow-lg shadow-slate-200/40">
            <div className="flex items-center gap-2">
              <Database className="h-5 w-5 text-primary" />
              <Title level={4} className="!mb-0 !text-lg !font-black !text-slate-800">
                使用建议
              </Title>
            </div>
            <div className="mt-5 space-y-3">
              {generateRules.map((rule, index) => (
                <div key={rule} className="flex gap-3 rounded-[1.3rem] border border-slate-200 bg-slate-50/80 px-4 py-4">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-900 text-xs font-black text-white">
                    {index + 1}
                  </div>
                  <Text className="text-sm font-medium leading-7 text-slate-600">{rule}</Text>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-[2rem] border border-slate-200 bg-white px-5 py-5 shadow-lg shadow-slate-200/40">
            <div className="text-[11px] font-black uppercase tracking-[0.2em] text-slate-400">Example Topics</div>
            <Title level={4} className="!mb-0 !mt-2 !text-lg !font-black !text-slate-800">
              推荐输入示例
            </Title>
            <div className="mt-4 flex flex-wrap gap-2">
              {exampleTopics.map((topic) => (
                <Tag
                  key={topic}
                  className="cursor-pointer rounded-full border-slate-200 px-3 py-1 text-sm font-semibold text-slate-600 transition hover:border-primary/30 hover:text-primary"
                  onClick={() => form.setFieldValue("questionType", topic)}
                >
                  {topic}
                </Tag>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
};

export default AiGenerateQuestionPage;
