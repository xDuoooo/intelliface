"use client";

import React, { useCallback, useEffect, useState, useMemo } from "react";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import Link from "next/link";
import { Card, Empty, List, Spin, Tag, Typography, message } from "antd";
import { ArrowRight, Compass, Sparkles } from "lucide-react";
import {
  listPersonalRecommendQuestionVoUsingGet,
  listRelatedQuestionVoUsingGet,
  logRecommendClickUsingPost,
} from "@/api/questionController";
import TagList from "@/components/TagList";
import { QUESTION_DIFFICULTY_COLOR_MAP } from "@/constants/question";

const { Title, Paragraph, Text } = Typography;

interface Props {
  questionId: string | number;
}

/**
 * 题目推荐面板
 */
export default function QuestionRecommendPanel({ questionId }: Props) {
  const [loading, setLoading] = useState(true);
  const [personalList, setPersonalList] = useState<API.QuestionVO[]>([]);
  const [relatedList, setRelatedList] = useState<API.QuestionVO[]>([]);

  const loginUser = useSelector((state: RootState) => state.loginUser);
  const isLogin = useMemo(() => Boolean(loginUser?.id), [loginUser?.id]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const promises: Promise<any>[] = [
        listRelatedQuestionVoUsingGet({ questionId, size: 4 }),
      ];
      if (isLogin) {
        promises.unshift(listPersonalRecommendQuestionVoUsingGet({ questionId, size: 4 }));
      } else {
        promises.unshift(Promise.resolve({ data: [] } as any));
      }
      
      const [personalRes, relatedRes] = await Promise.allSettled(promises);
      
      if (personalRes.status === "fulfilled") {
        setPersonalList(personalRes?.value?.data || []);
      } else {
        setPersonalList([]);
      }
      if (relatedRes.status === "fulfilled") {
        setRelatedList(relatedRes.value.data || []);
      } else {
        setRelatedList([]);
      }
      if (personalRes.status === "rejected" && relatedRes.status === "rejected") {
        message.error("加载推荐题目失败，请稍后重试");
      } else if (personalRes.status === "rejected" || relatedRes.status === "rejected") {
        message.warning("部分推荐结果加载失败，已展示可用内容");
      }
    } catch (error: any) {
      message.error("加载推荐题目失败：" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  }, [isLogin, questionId]);

  useEffect(() => {
    if (!questionId) {
      return;
    }
    void loadData();
  }, [loadData, questionId]);

  const trackClick = (questionId?: string | number, source?: string) => {
    if (!questionId || !source) {
      return;
    }
    void logRecommendClickUsingPost({ questionId, source }).catch(() => undefined);
  };

  const renderQuestionList = (dataList: API.QuestionVO[], emptyText: string, source: string) => {
    if (loading) {
      return (
        <div className="py-14 text-center">
          <Spin />
        </div>
      );
    }
    if (!dataList.length) {
      return <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }
    return (
      <List
        dataSource={dataList}
        split={false}
        renderItem={(item) => (
          <List.Item className="!px-0">
            <Link
              href={`/question/${item.id}`}
              className="block w-full"
              onClick={() => trackClick(item.id, source)}
            >
              <div className="rounded-2xl border border-slate-100 bg-slate-50/70 p-4 transition-all hover:border-primary/30 hover:bg-white hover:shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="font-semibold text-slate-800 line-clamp-2">{item.title}</div>
                    {item.recommendReason && (
                      <Paragraph className="!mb-0 !mt-2 text-sm text-slate-500">
                        {item.recommendReason}
                      </Paragraph>
                    )}
                  </div>
                  <ArrowRight className="h-4 w-4 shrink-0 text-slate-300" />
                </div>
                {item.tagList?.length || item.difficulty ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    {item.tagList?.length ? <TagList tagList={item.tagList.slice(0, 3)} /> : null}
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
    );
  };

  return (
    <section className="grid grid-cols-1 gap-6 xl:grid-cols-2">
      <Card
        className="rounded-[2rem] border border-slate-100 shadow-2xl shadow-slate-200/40"
        title={
          <div className="flex items-center gap-2 font-black text-lg text-slate-800">
            <Sparkles className="h-5 w-5 text-primary" />
            猜你喜欢
          </div>
        }
        extra={<Tag color="blue">混合推荐</Tag>}
      >
        <Paragraph className="text-slate-500">
          结合你的刷题记录、收藏偏好、题目标签和协同过滤结果，推荐下一步更值得继续攻克的题目。
        </Paragraph>
        {renderQuestionList(personalList, isLogin ? "暂时还没有可推荐的题目" : "登录后即可获取专属个性化推荐", "personal")}
      </Card>

      <Card
        className="rounded-[2rem] border border-slate-100 shadow-2xl shadow-slate-200/40"
        title={
          <div className="flex items-center gap-2 font-black text-lg text-slate-800">
            <Compass className="h-5 w-5 text-emerald-500" />
            相关题目
          </div>
        }
        extra={<Tag color="green">标签 + 协同过滤</Tag>}
      >
        <Text className="text-slate-500">
          根据当前题目的核心标签和相似练习人群行为，筛出更适合延伸练习的关联题目。
        </Text>
        <div className="mt-4">
          {renderQuestionList(relatedList, "当前题目暂未找到更多关联题目", "related")}
        </div>
      </Card>
    </section>
  );
}
