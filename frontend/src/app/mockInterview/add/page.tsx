"use client";
import { Button, Form, Input, InputNumber, Select, message } from "antd";
import React, { useEffect, useMemo, useState } from "react";
import {
  addMockInterviewUsingPost,
  getMockInterviewByIdUsingGet,
} from "@/api/mockInterviewController";
import { useRouter, useSearchParams } from "next/navigation";
import "./index.css";

interface Props {}

/**
 * 创建 AI 模拟面试页面
 * @param props
 * @constructor
 */
const CreateMockInterviewPage: React.FC<Props> = (props) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [prefillLoading, setPrefillLoading] = useState(false);
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromInterviewId = useMemo(() => searchParams?.get("from") || "", [searchParams]);

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
        message.success("已为你带入上一场面试的配置");
      } catch (error: any) {
        message.error(error?.message || "读取上一场面试配置失败");
      } finally {
        setPrefillLoading(false);
      }
    };
    void hydrateFromInterview();
  }, [form, fromInterviewId]);

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

        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 24 }}
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
          <Button
            loading={loading}
            disabled={prefillLoading}
            style={{ width: 220 }}
            type="primary"
            htmlType="submit"
          >
            {prefillLoading ? "正在载入配置..." : "创建并进入模拟面试"}
          </Button>
        </Form.Item>
        </Form>
      </div>
    </div>
  );
};

export default CreateMockInterviewPage;
