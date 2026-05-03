"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
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
    description: "题目、标签、参考答案会一起落库，减少人工整理成本。",
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
    <div className="mx-auto max-w-6xl space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-3">
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
            <Paragraph className="!mb-0 !mt-2 max-w-3xl text-base font-medium text-slate-500">
              用更清晰的输入方向快速批量生成题目、标签和参考答案，直接补充到平台题库里。
            </Paragraph>
          </div>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full border border-primary/15 bg-primary/5 px-4 py-2 text-xs font-black uppercase tracking-[0.18em] text-primary">
          <Sparkles className="h-3.5 w-3.5" />
          Intelligent Content Generator
        </div>
      </div>

      <Card
        className="overflow-hidden rounded-[2.5rem] border border-slate-200/70 bg-gradient-to-br from-slate-50 via-white to-sky-50 shadow-xl shadow-slate-200/40"
        bodyStyle={{ padding: "2rem" }}
      >
        <div className="grid gap-6 xl:grid-cols-[1.25fr_0.95fr]">
          <div className="space-y-5">
            <div className="inline-flex items-center gap-2 rounded-full bg-slate-900 px-4 py-2 text-xs font-black uppercase tracking-[0.18em] text-white">
              <BrainCircuit className="h-3.5 w-3.5" />
              生成工作台
            </div>
            <div className="space-y-3">
              <Text className="block text-xl font-black text-slate-900 sm:text-2xl">
                一次配置，完成题目生成、标签整理和答案入库
              </Text>
              <Text className="block max-w-2xl text-sm font-medium leading-7 text-slate-500 sm:text-base">
                这页更适合做“明确方向的小批量生成”。输入越具体，生成结果越稳定，后续人工筛选和审核的成本也越低。
              </Text>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
            {workflowHighlights.map((item) => (
              <div
                key={item.title}
                className="rounded-[1.75rem] border border-slate-200/80 bg-white/85 p-4 shadow-sm shadow-slate-100"
              >
                <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">{item.title}</div>
                <div className="mt-2 text-2xl font-black tracking-tight text-slate-900">{item.value}</div>
                <div className="mt-1 text-xs font-semibold leading-6 text-slate-500">{item.description}</div>
              </div>
            ))}
          </div>
        </div>
      </Card>

      <Row gutter={[24, 24]} align="stretch">
        <Col xs={24} xl={15}>
          <Card
            className="h-full rounded-[2.5rem] border-slate-100 shadow-xl shadow-slate-200/50"
            bodyStyle={{ padding: "2rem" }}
          >
            <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="text-[11px] font-black uppercase tracking-[0.2em] text-slate-400">Generation Form</div>
                <Title level={3} className="!mb-1 !mt-2 !text-2xl !font-black tracking-tight !text-slate-900">
                  配置本次题目生成任务
                </Title>
                <Text className="text-sm font-medium text-slate-500">
                  先输入题目方向，再决定本次生成数量。建议少量多次，方便挑选优质题目。
                </Text>
              </div>
              <div className="rounded-full border border-emerald-200 bg-emerald-50 px-4 py-2 text-xs font-bold text-emerald-600">
                结果会直接进入题目管理
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
                rules={[{ required: true, message: "请输入题目方向，如 Java、Spring Boot" }]}
              >
                <Input
                  placeholder="例如：Redis 分布式锁实现、Kafka 消息可靠性保障..."
                  className="h-14 rounded-2xl border-slate-200 bg-slate-50 px-5 text-base font-medium transition-colors focus:bg-white"
                />
              </Form.Item>

              <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_220px]">
                <Form.Item
                  label={<span className="font-bold text-slate-700">生成数量</span>}
                  name="number"
                  rules={[{ required: true }]}
                >
                  <InputNumber
                    min={1}
                    max={20}
                    className="h-14 w-full rounded-2xl border-slate-200 bg-slate-50 px-4"
                    size="large"
                  />
                </Form.Item>

                <div className="rounded-[1.75rem] border border-slate-200 bg-slate-50/80 p-4">
                  <div className="text-xs font-black uppercase tracking-[0.18em] text-slate-400">快速数量</div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {quickCounts.map((count) => {
                      const active = selectedCount === count;
                      return (
                        <button
                          key={count}
                          type="button"
                          onClick={() => form.setFieldValue("number", count)}
                          className={`rounded-full px-3 py-2 text-sm font-bold transition ${
                            active
                              ? "bg-primary text-white shadow-lg shadow-primary/20"
                              : "border border-slate-200 bg-white text-slate-500 hover:border-primary/30 hover:text-primary"
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
                <div className="text-xs font-black uppercase tracking-[0.18em] text-slate-400">生成建议</div>
                <div className="mt-3 grid gap-3 sm:grid-cols-3">
                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                    <div className="text-sm font-black text-slate-800">输入越聚焦越好</div>
                    <div className="mt-1 text-xs font-medium leading-6 text-slate-500">优先输入具体专题，不要只写大类技术名词。</div>
                  </div>
                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                    <div className="text-sm font-black text-slate-800">建议分批生成</div>
                    <div className="mt-1 text-xs font-medium leading-6 text-slate-500">一次 5-15 道更容易控制质量，也方便审核。</div>
                  </div>
                  <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3">
                    <div className="text-sm font-black text-slate-800">生成后继续筛题</div>
                    <div className="mt-1 text-xs font-medium leading-6 text-slate-500">生成结果会自动入库，但仍建议回到题目管理做复核。</div>
                  </div>
                </div>
              </div>

              <div className="pt-8">
                <Button
                  loading={loading}
                  type="primary"
                  htmlType="submit"
                  size="large"
                  className="h-16 w-full whitespace-nowrap rounded-[1.3rem] border-none bg-primary text-lg font-black shadow-xl shadow-primary/20 hover:scale-[1.01] active:scale-[0.99] [&>span]:inline-flex [&>span]:items-center [&>span]:justify-center [&>span]:gap-2"
                >
                  {loading ? "正在生成题目..." : (
                    <span className="inline-flex items-center gap-2 whitespace-nowrap">
                      <Wand2 className="h-5 w-5" />
                      立即开始生成
                    </span>
                  )}
                </Button>
              </div>
            </Form>
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <div className="space-y-6">
            {successCount !== null && (
              <Alert
                message={
                  <div className="flex items-start gap-3 py-1">
                    <CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-500" />
                    <div>
                      <div className="text-base font-black text-emerald-800">生成成功</div>
                      <div className="mt-1 text-sm font-medium leading-6 text-emerald-700">
                        已成功生成 {successCount} 道题目，建议直接回到题目管理查看生成结果并做进一步筛选。
                      </div>
                    </div>
                  </div>
                }
                type="success"
                className="rounded-[1.75rem] border-emerald-200 bg-emerald-50"
                showIcon={false}
              />
            )}

            <Card
              className="rounded-[2rem] border-slate-100 shadow-xl shadow-slate-200/40"
              bodyStyle={{ padding: "1.75rem" }}
            >
              <div className="flex items-center gap-2">
                <Database className="h-5 w-5 text-primary" />
                <Title level={4} className="!mb-0 !text-lg !font-black !text-slate-800">
                  使用指南
                </Title>
              </div>
              <div className="mt-5 space-y-4">
                {generateRules.map((rule, index) => (
                  <div key={rule} className="flex gap-3 rounded-[1.3rem] border border-slate-200 bg-slate-50/80 px-4 py-4">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-900 text-xs font-black text-white">
                      {index + 1}
                    </div>
                    <Text className="text-sm font-medium leading-7 text-slate-600">{rule}</Text>
                  </div>
                ))}
              </div>
            </Card>

            <Card
              className="rounded-[2rem] border-slate-100 shadow-xl shadow-slate-200/40"
              bodyStyle={{ padding: "1.75rem" }}
            >
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
            </Card>
          </div>
        </Col>
      </Row>
    </div>
  );
};

export default AiGenerateQuestionPage;
