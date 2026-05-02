"use client";
import { Button, Form, Input, InputNumber, Progress, Select, Tag, message } from "antd";
import React, { useEffect, useMemo, useState } from "react";
import {
  addMockInterviewUsingPost,
  getMockInterviewByIdUsingGet,
} from "@/api/mockInterviewController";
import { useRouter, useSearchParams } from "next/navigation";
import { ClipboardCheck, Sparkles } from "lucide-react";
import "./index.css";

interface Props {}

const CREATE_DRAFT_STORAGE_KEY = "mockInterview:createDraft:v1";

function buildBackgroundTemplate(values?: Partial<API.MockInterviewAddRequest>) {
  const position = values?.jobPosition || "目标岗位";
  const techStack = values?.techStack || "核心技术栈";
  return `项目背景：我在一个面向 ${position} 的项目中，主要解决了什么业务问题。\n我的职责：我具体负责的模块、接口、数据流或协作范围。\n技术方案：使用 ${techStack} 做了哪些关键设计，为什么这样选。\n难点取舍：遇到的性能、稳定性、成本、复杂度或协作问题，以及我的处理方式。\n量化结果：上线后 QPS、耗时、错误率、成本、转化率或人效有什么变化。\n复盘改进：如果重新做一次，我会优先优化什么。`;
}

function buildQualityItems(values?: Partial<API.MockInterviewAddRequest>) {
  const resumeText = values?.resumeText?.trim() || "";
  return [
    {
      label: "岗位清晰",
      matched: Boolean(values?.jobPosition?.trim()),
      hint: "补目标岗位后，开场问题会更贴近真实招聘场景。",
    },
    {
      label: "技术方向",
      matched: Boolean(values?.techStack?.trim()),
      hint: "补技术栈后，AI 才能围绕具体技术点追问。",
    },
    {
      label: "项目背景",
      matched: resumeText.length >= 80,
      hint: "建议至少写清一个项目的背景、目标和业务场景。",
    },
    {
      label: "个人职责",
      matched: /(我|本人|负责|主导|参与|推进|落地)/.test(resumeText),
      hint: "说明你具体负责哪一块，避免面试官只能问团队层面的泛题。",
    },
    {
      label: "难点取舍",
      matched: /(难点|瓶颈|故障|性能|稳定性|取舍|权衡|风险|异常|优化)/.test(resumeText),
      hint: "补充难点和取舍后，项目深挖会更像真实面试。",
    },
    {
      label: "量化结果",
      matched: /(\d|%|\b(qps|rt|ms|sla|p95|p99)\b|秒|分钟|提升|降低|减少|增长|成本|耗时)/i.test(resumeText),
      hint: "补一个指标，最终反馈和追问都会更具体。",
    },
  ];
}

function buildInterviewPreview(values?: Partial<API.MockInterviewAddRequest>) {
  const interviewType = values?.interviewType || "技术深挖";
  const techStack = values?.techStack || values?.jobPosition || "你的核心项目";
  if (interviewType === "项目拷打") {
    return ["项目背景与职责边界", `${techStack} 的方案拆解和技术取舍`, "难点、量化结果和复盘改进"];
  }
  if (interviewType === "系统设计") {
    return ["需求澄清与容量估算", `${techStack} 场景下的架构拆分`, "高可用、扩展性、成本和降级方案"];
  }
  if (interviewType === "HR") {
    return ["自我介绍与求职动机", "具体事件里的协作、冲突和压力处理", "职业规划、稳定性和岗位匹配度"];
  }
  return ["自我介绍与代表性项目", `${techStack} 的原理和场景落地`, "性能、稳定性、边界条件和综合追问"];
}

/**
 * 创建 AI 模拟面试页面
 * @param props
 * @constructor
 */
const CreateMockInterviewPage: React.FC<Props> = (props) => {
  const [form] = Form.useForm();
  const formValues = Form.useWatch([], form);
  const [loading, setLoading] = useState(false);
  const [prefillLoading, setPrefillLoading] = useState(false);
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromInterviewId = useMemo(() => searchParams?.get("from") || "", [searchParams]);
  const qualityItems = useMemo(() => buildQualityItems(formValues), [formValues]);
  const qualityScore = Math.round((qualityItems.filter((item) => item.matched).length / qualityItems.length) * 100);
  const interviewPreview = useMemo(() => buildInterviewPreview(formValues), [formValues]);
  const missingQualityItems = qualityItems.filter((item) => !item.matched).slice(0, 3);

  const interviewTypeOptions = [
    { label: "技术深挖", value: "技术深挖" },
    { label: "项目拷打", value: "项目拷打" },
    { label: "系统设计", value: "系统设计" },
    { label: "HR", value: "HR" },
  ];

  const difficultyOptions = [
    { label: "初级", value: "初级" },
    { label: "中等", value: "中等" },
    { label: "高级", value: "高级" },
  ];

  useEffect(() => {
    if (typeof window === "undefined" || fromInterviewId) {
      return;
    }
    const savedDraft = window.localStorage.getItem(CREATE_DRAFT_STORAGE_KEY);
    if (!savedDraft) {
      return;
    }
    try {
      const parsedDraft = JSON.parse(savedDraft);
      form.setFieldsValue(parsedDraft);
      message.success("已恢复你上次未完成的面试配置");
    } catch {
      window.localStorage.removeItem(CREATE_DRAFT_STORAGE_KEY);
    }
  }, [form, fromInterviewId]);

  useEffect(() => {
    const hydrateFromInterview = async () => {
      if (!fromInterviewId) {
        return;
      }
      setPrefillLoading(true);
      try {
        const res = await getMockInterviewByIdUsingGet({ id: fromInterviewId });
        if (!res.data) {
          return;
        }
        form.setFieldsValue({
          jobPosition: res.data.jobPosition,
          workExperience: res.data.workExperience,
          interviewType: res.data.interviewType,
          techStack: res.data.techStack,
          resumeText: res.data.resumeText,
          difficulty: res.data.difficulty,
          expectedRounds: res.data.expectedRounds,
        });
        if (typeof window !== "undefined") {
          window.localStorage.removeItem(CREATE_DRAFT_STORAGE_KEY);
        }
        message.success("已为你带入上一场面试的配置");
      } catch (error: any) {
        message.error(error?.message || "读取上一场面试配置失败");
      } finally {
        setPrefillLoading(false);
      }
    };
    void hydrateFromInterview();
  }, [form, fromInterviewId]);

  useEffect(() => {
    if (typeof window === "undefined" || prefillLoading || fromInterviewId) {
      return;
    }
    const nextDraft = {
      jobPosition: formValues?.jobPosition,
      workExperience: formValues?.workExperience,
      interviewType: formValues?.interviewType,
      techStack: formValues?.techStack,
      resumeText: formValues?.resumeText,
      difficulty: formValues?.difficulty,
      expectedRounds: formValues?.expectedRounds,
    };
    const hasContent = Object.values(nextDraft).some((value) => {
      if (typeof value === "string") {
        return Boolean(value.trim());
      }
      return value !== undefined && value !== null;
    });
    if (!hasContent) {
      window.localStorage.removeItem(CREATE_DRAFT_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(CREATE_DRAFT_STORAGE_KEY, JSON.stringify(nextDraft));
  }, [formValues, fromInterviewId, prefillLoading]);

  /**
   * 提交表单
   *
   * @param values
   */
  const doSubmit = async (values: API.MockInterviewAddRequest) => {
    const hide = message.loading("正在创建模拟面试...");
    setLoading(true);
    try {
      const res = await addMockInterviewUsingPost(values);
      hide();
      message.success("模拟面试创建成功");
      if (typeof window !== "undefined") {
        window.localStorage.removeItem(CREATE_DRAFT_STORAGE_KEY);
      }
      form.resetFields(); // 重置表单
      // 跳转到模拟面试列表页面
      router.push("/mockInterview/chat/" + res.data);
    } catch (error: any) {
      hide();
      message.error("创建失败，" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div id="createMockInterviewPage">
      <div className="create-panel">
        <div className="create-header">
          <div className="eyebrow">AI Mock Interview</div>
          <h2>创建更像真实现场的模拟面试</h2>
          <p>
            补充岗位、面试类型、技术方向和项目背景后，系统会按轮次追问，并在结束时生成结构化复盘报告。
          </p>
          {fromInterviewId ? (
            <p>
              当前正在复用面试 #{fromInterviewId} 的配置，你可以微调岗位、技术栈或项目背景后再开始新一轮。
            </p>
          ) : null}
        </div>

        <div className="create-content-grid">
          <div className="create-form-column">
            <Form
              form={form}
              layout="vertical"
              onFinish={doSubmit}
              initialValues={{
                difficulty: "中等",
                interviewType: "技术深挖",
                expectedRounds: 5,
              }}
            >
        {/* 工作岗位 */}
        <Form.Item
          label="目标岗位"
          name="jobPosition"
          rules={[
            { required: true, message: "请输入目标岗位" },
            { max: 80, message: "目标岗位不能超过 80 个字符" },
          ]}
        >
          <Input maxLength={80} showCount placeholder="请输入工作岗位，例如：Java 开发工程师" />
        </Form.Item>

        {/* 工作年限 */}
        <Form.Item
          label="工作年限"
          name="workExperience"
          rules={[{ max: 40, message: "工作年限描述不能超过 40 个字符" }]}
        >
          <Input maxLength={40} showCount placeholder="请输入工作年限，例如：3 年" />
        </Form.Item>

        <div className="create-grid">
          <Form.Item label="面试类型" name="interviewType" rules={[{ max: 40, message: "面试类型过长" }]}>
            <Select options={interviewTypeOptions} placeholder="请选择面试类型" />
          </Form.Item>

          <Form.Item label="面试难度" name="difficulty" rules={[{ max: 40, message: "面试难度过长" }]}>
            <Select options={difficultyOptions} placeholder="请选择面试难度" />
          </Form.Item>

          <Form.Item label="计划轮次" name="expectedRounds">
            <InputNumber min={3} max={8} style={{ width: "100%" }} />
          </Form.Item>
        </div>

        <Form.Item
          label="技术方向 / 技术栈"
          name="techStack"
          rules={[{ max: 256, message: "技术方向不能超过 256 个字符" }]}
        >
          <Input
            maxLength={256}
            showCount
            placeholder="例如：Java、Spring Boot、MySQL、Redis、消息队列"
          />
        </Form.Item>

        <Form.Item
          label="简历 / 项目背景"
          name="resumeText"
          rules={[{ max: 4000, message: "简历 / 项目背景不能超过 4000 个字符" }]}
        >
          <Input.TextArea
            rows={6}
            maxLength={4000}
            showCount
            placeholder="建议粘贴项目经历、职责、业务场景、性能指标或最近准备的重点方向，系统会更像真实面试官一样围绕这些信息追问。"
          />
        </Form.Item>

        {/* 面试难度 */}
        <Form.Item>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <Button
              loading={loading}
              disabled={prefillLoading}
              style={{ width: 220 }}
              type="primary"
              htmlType="submit"
            >
              {prefillLoading ? "正在载入配置..." : "创建并进入模拟面试"}
            </Button>
            <Button
              disabled={prefillLoading}
              onClick={() => {
                form.resetFields();
                if (typeof window !== "undefined") {
                  window.localStorage.removeItem(CREATE_DRAFT_STORAGE_KEY);
                }
                message.success("已清空当前草稿");
              }}
            >
              清空草稿
            </Button>
            <Button
              disabled={prefillLoading}
              onClick={() => {
                form.setFieldsValue({ resumeText: buildBackgroundTemplate(form.getFieldsValue()) });
                message.success("已插入项目背景模板");
              }}
            >
              <ClipboardCheck size={16} />
              插入背景模板
            </Button>
          </div>
        </Form.Item>
            </Form>
          </div>

          <aside className="create-preview-panel">
            <div className="preview-card quality-card">
              <div className="preview-card-head">
                <div>
                  <span>Config Check</span>
                  <strong>配置质量</strong>
                </div>
                <Tag color={qualityScore >= 80 ? "green" : qualityScore >= 50 ? "blue" : "orange"}>
                  {qualityScore}%
                </Tag>
              </div>
              <Progress percent={qualityScore} showInfo={false} strokeColor={qualityScore >= 80 ? "#10b981" : "#1677ff"} />
              <div className="quality-list">
                {qualityItems.map((item) => (
                  <div className={`quality-item ${item.matched ? "done" : ""}`} key={item.label}>
                    <span>{item.label}</span>
                    <strong>{item.matched ? "已具备" : "待补充"}</strong>
                  </div>
                ))}
              </div>
              {missingQualityItems.length ? (
                <div className="quality-tip">
                  {missingQualityItems[0].hint}
                </div>
              ) : (
                <div className="quality-tip strong">当前配置已经足够支撑一场比较具体的模拟面试。</div>
              )}
            </div>

            <div className="preview-card">
              <div className="preview-card-head">
                <div>
                  <span>Interview Preview</span>
                  <strong>可能追问路径</strong>
                </div>
                <Sparkles size={18} />
              </div>
              <div className="preview-step-list">
                {interviewPreview.map((item, index) => (
                  <div className="preview-step" key={item}>
                    <span>{index + 1}</span>
                    <div>{item}</div>
                  </div>
                ))}
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
};

export default CreateMockInterviewPage;
