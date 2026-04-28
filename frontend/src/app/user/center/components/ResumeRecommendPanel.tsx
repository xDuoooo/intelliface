"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { Button, Card, Empty, Input, List, Space, Tag, Typography, Upload, message } from "antd";
import type { UploadProps } from "antd";
import { useDispatch } from "react-redux";
import { ArrowRight, CheckCheck, FileSearch, Paperclip, Sparkles, Target, UploadCloud, X } from "lucide-react";
import {
  logRecommendClickUsingPost,
  recommendQuestionsByResumeFileUsingPost,
  recommendQuestionsByResumeUsingPost,
} from "@/api/questionController";
import { getLoginUserUsingGet, updateMyUserUsingPost } from "@/api/userController";
import TagList from "@/components/TagList";
import { QUESTION_DIFFICULTY_COLOR_MAP } from "@/constants/question";
import { RootState } from "@/stores";
import { useSelector, useDispatch } from "react-redux";
import { QUESTION_DIFFICULTY_COLOR_MAP } from "@/constants/question";
import type { AppDispatch } from "@/stores";
import { setLoginUser } from "@/stores/loginUser";

const { Paragraph, Text, Title } = Typography;
const { CheckableTag } = Tag;

const DEMO_RESUME_TEXT = [
  "应聘方向：Java 后端开发工程师",
  "项目经历：负责基于 Spring Boot、MyBatis-Plus、MySQL、Redis 的校园面试题库系统开发，参与接口设计、权限控制和缓存优化。",
  "技术栈：Java、Spring Boot、Redis、MySQL、RabbitMQ、Linux、Docker。",
  "能力描述：熟悉常见数据结构与算法，了解 JVM、并发编程、微服务基础，做过接口限流与登录鉴权。",
].join("\n");

/**
 * 简历驱动推荐面板
 */
const ResumeRecommendPanel: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const [resumeText, setResumeText] = useState("");
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingTags, setSavingTags] = useState(false);
  const [result, setResult] = useState<API.ResumeQuestionRecommendVO>();
  const [selectedExtractedTags, setSelectedExtractedTags] = useState<string[]>([]);

  const loginUser = useSelector((state: RootState) => state.loginUser);
  const existingTags = useMemo(() => loginUser?.interestTagList || [], [loginUser?.interestTagList]);

  const allAvailableTags = useMemo(
    () => Array.from(new Set([
      ...existingTags,
      ...(result?.extractedTags || []).map((tag) => tag?.trim()).filter(Boolean) as string[],
    ])),
    [existingTags, result?.extractedTags],
  );

  const applyResult = (nextResult?: API.ResumeQuestionRecommendVO) => {
    setResult(nextResult);
    const parsedTags = Array.from(
      new Set(((nextResult?.extractedTags || []).map((tag) => tag?.trim()).filter(Boolean) as string[])),
    );
    const combined = Array.from(new Set([...existingTags, ...parsedTags]));
    setSelectedExtractedTags(combined.slice(0, 8)); // 默认选中最多前8个
  };

  const handleRecommend = async () => {
    const trimmedText = resumeText.trim();
    if (trimmedText.length < 20) {
      message.warning("请粘贴更完整的简历或项目经历描述");
      return;
    }
    setLoading(true);
    try {
      const res = await recommendQuestionsByResumeUsingPost({
        resumeText: trimmedText,
        size: 4,
      });
      applyResult(res.data);
      message.success("简历解析完成");
    } catch (error: any) {
      message.error("解析失败：" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  const handleRecommendByFile = async () => {
    if (!resumeFile) {
      message.warning("请先选择简历文件");
      return;
    }
    setLoading(true);
    try {
      const res = await recommendQuestionsByResumeFileUsingPost(resumeFile, 4);
      applyResult(res.data);
      message.success("简历文件解析完成");
    } catch (error: any) {
      message.error("文件解析失败：" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  const handleMergeTagsToProfile = async () => {
    const tagList = selectedExtractedTags.filter(Boolean);
    if (!tagList.length) {
      message.warning("请先选择要加入资料的技能标签");
      return;
    }
    setSavingTags(true);
    try {
      const res = await updateMyUserUsingPost({ interestTags: tagList });
      if (res.data) {
        const userRes = await getLoginUserUsingGet();
        if (userRes.data) {
          dispatch(setLoginUser(userRes.data));
        }
      }
      message.success("技能标签已更新");
    } catch (error: any) {
      message.error("更新失败：" + (error?.message || "请稍后重试"));
    } finally {
      setSavingTags(false);
    }
  };

  const toggleExtractedTag = (tag: string, checked: boolean) => {
    setSelectedExtractedTags((current) => {
      if (checked) {
        if (current.length >= 8) {
          message.warning("最多只能保留 8 个技能标签");
          return current;
        }
        return Array.from(new Set([...current, tag]));
      }
      return current.filter((item) => item !== tag);
    });
  };

  const uploadProps: UploadProps = {
    accept: ".txt,.md,.markdown,.docx,.pdf",
    maxCount: 1,
    showUploadList: false,
    beforeUpload: (file) => {
      setResumeFile(file as File);
      return false;
    },
  };

  return (
    <Card
      bordered={false}
      className="overflow-hidden rounded-[2rem] border border-slate-100 shadow-2xl shadow-slate-200/40"
    >
      <div className="flex flex-col gap-6">
        <div className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 p-6">
          <div className="flex items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 text-lg font-black text-slate-800">
                <FileSearch className="h-5 w-5 text-primary" />
                简历解析推荐
              </div>
              <Paragraph className="!mb-0 !mt-2 text-slate-500">
                你可以直接粘贴简历文本，也可以上传 txt、md、docx、pdf 简历文件。系统会自动提取技能标签，并推荐更适合当前岗位方向的面试题。
              </Paragraph>
            </div>
            <Tag color="blue">AI + 规则双驱动</Tag>
          </div>

          <Input.TextArea
            value={resumeText}
            onChange={(e) => setResumeText(e.target.value)}
            rows={10}
            maxLength={2500}
            showCount
            placeholder="粘贴你的简历文本、项目经历、技术栈和求职方向，例如：熟悉 Java / Spring Boot / Redis / MySQL，负责过后端接口开发与缓存优化..."
            className="!mt-5 rounded-3xl bg-white/80"
          />

          <div className="mt-5 rounded-[1.5rem] border border-dashed border-slate-200 bg-white/70 p-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0">
                <div className="flex items-center gap-2 font-semibold text-slate-800">
                  <Paperclip className="h-4 w-4 text-slate-500" />
                  上传简历文件
                </div>
                <Text className="mt-1 block text-sm text-slate-500">
                  支持 `txt / md / docx / pdf`，单个文件不超过 2MB。
                </Text>
              </div>
              <Upload {...uploadProps}>
                <Button className="rounded-2xl" icon={<UploadCloud className="h-4 w-4" />}>
                  选择文件
                </Button>
              </Upload>
            </div>

            {resumeFile ? (
              <div className="mt-4 flex flex-col gap-3 rounded-2xl border border-slate-100 bg-slate-50/80 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="truncate font-semibold text-slate-800">{resumeFile.name}</div>
                  <div className="mt-1 text-xs text-slate-500">
                    {(resumeFile.size / 1024).toFixed(1)} KB
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button type="primary" loading={loading} onClick={handleRecommendByFile} className="rounded-2xl">
                    解析上传文件
                  </Button>
                  <Button
                    onClick={() => setResumeFile(null)}
                    className="rounded-2xl"
                    icon={<X className="h-4 w-4" />}
                  >
                    移除文件
                  </Button>
                </div>
              </div>
            ) : null}
          </div>

          <Space size="middle" className="!mt-5 flex flex-wrap">
            <Button type="primary" loading={loading} onClick={handleRecommend} className="rounded-2xl">
              解析粘贴内容
            </Button>
            <Button onClick={() => setResumeText(DEMO_RESUME_TEXT)} className="rounded-2xl">
              填入示例简历
            </Button>
            <Button
              onClick={() => {
                setResumeText("");
                setResumeFile(null);
                applyResult(undefined);
              }}
              className="rounded-2xl"
            >
              清空内容
            </Button>
          </Space>
        </div>

        <div className="rounded-[1.75rem] border border-slate-100 bg-white p-6">
          <div className="flex items-center gap-2 text-lg font-black text-slate-800">
            <Sparkles className="h-5 w-5 text-amber-500" />
            解析结果
          </div>

          {!result && !loading ? (
            <div className="py-10">
              <Empty
                description="输入简历后即可查看岗位方向、识别标签和推荐题目"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            </div>
          ) : null}

          {result ? (
            <div className="mt-5 space-y-5">
              <div className="flex flex-wrap items-center gap-3">
                <Tag color="geekblue">{result.analysisSource || "系统分析"}</Tag>
                <Tag color="green">{result.jobDirection || "综合技术岗位"}</Tag>
              </div>

              <div>
                <Title level={5} style={{ marginBottom: 8 }}>分析摘要</Title>
                <Paragraph className="!mb-0 text-slate-500">
                  {result.analysisSummary || "系统已根据你的简历信息完成题目方向分析。"}
                </Paragraph>
              </div>

              <div>
                <div className="mb-2 flex items-center gap-2 font-semibold text-slate-800">
                  <Target className="h-4 w-4 text-emerald-500" />
                  推荐补强方向
                </div>
                <Text className="text-slate-500">
                  {result.recommendFocus || "建议继续围绕核心技术栈做专项刷题。"}
                </Text>
              </div>

              <div>
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                  <div className="font-semibold text-slate-800">自主选择技能标签 (最多 8 个)</div>
                  {allAvailableTags.length ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        size="small"
                        onClick={() => setSelectedExtractedTags(allAvailableTags.slice(0, 8))}
                        className="rounded-full"
                      >
                        全选/拉满
                      </Button>
                      <Button
                        size="small"
                        onClick={() => setSelectedExtractedTags([])}
                        className="rounded-full"
                      >
                        清空
                      </Button>
                      <Button
                        size="small"
                        type="primary"
                        icon={<CheckCheck className="h-4 w-4" />}
                        loading={savingTags}
                        onClick={handleMergeTagsToProfile}
                        className="rounded-full"
                      >
                        更新至个人资料
                      </Button>
                    </div>
                  ) : null}
                </div>
                {allAvailableTags.length ? (
                  <div className="space-y-3">
                    <Text className="text-sm text-slate-500">
                      已选择 {selectedExtractedTags.length} / {allAvailableTags.length} 个标签加入个人资料（包含你已有的标签）
                    </Text>
                    <div className="flex flex-wrap gap-2">
                      {allAvailableTags.map((tag) => (
                        <CheckableTag
                          key={tag}
                          checked={selectedExtractedTags.includes(tag)}
                          onChange={(checked) => toggleExtractedTag(tag, checked)}
                          className={`!m-0 rounded-full border px-3 py-1 text-sm font-semibold transition ${existingTags.includes(tag) && !selectedExtractedTags.includes(tag) ? 'border-dashed border-rose-300 text-slate-400' : ''}`}
                        >
                          {tag}
                        </CheckableTag>
                      ))}
                    </div>
                  </div>
                ) : (
                  <Text type="secondary">暂未识别到明确标签</Text>
                )}
              </div>

              <div>
                <div className="mb-3 font-semibold text-slate-800">推荐题目</div>
                {result.questionList?.length ? (
                  <List
                    dataSource={result.questionList}
                    split={false}
                    renderItem={(item) => (
                      <List.Item className="!px-0 !py-2">
                        <Link
                          href={`/question/${item.id}`}
                          className="block w-full"
                          onClick={() => {
                            if (item.id) {
                              void logRecommendClickUsingPost({ questionId: item.id, source: "resume" }).catch(() => undefined);
                            }
                          }}
                        >
                          <div className="rounded-2xl border border-slate-100 bg-slate-50/80 p-4 transition-all hover:border-primary/30 hover:bg-white hover:shadow-sm">
                            <div className="flex items-start justify-between gap-3">
                              <div className="min-w-0">
                                <div className="line-clamp-2 font-semibold text-slate-800">{item.title}</div>
                                {item.recommendReason ? (
                                  <Paragraph className="!mb-0 !mt-2 text-sm text-slate-500">
                                    {item.recommendReason}
                                  </Paragraph>
                                ) : null}
                              </div>
                              <ArrowRight className="h-4 w-4 shrink-0 text-slate-300" />
                            </div>
                            {item.tagList?.length || item.difficulty ? (
                              <div className="mt-3 flex flex-wrap items-center gap-2">
                                {item.tagList?.length ? <TagList tagList={item.tagList.slice(0, 4)} /> : null}
                                {item.difficulty ? (
                                  <Tag color={QUESTION_DIFFICULTY_COLOR_MAP[item.difficulty] || "default"} className="rounded-full">
                                    {item.difficulty}
                                  </Tag>
                                ) : null}
                              </div>
                            ) : null}
                          </div>
                        </Link>
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty description="暂时还没有可推荐的题目" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </Card>
  );
};

export default ResumeRecommendPanel;
