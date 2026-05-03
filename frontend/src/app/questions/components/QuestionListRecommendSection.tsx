"use client";

import Link from "next/link";
import { ArrowRight, LockKeyhole, Sparkles } from "lucide-react";
import { Tag } from "antd";
import { logRecommendClickUsingPost } from "@/api/questionController";
import TagList from "@/components/TagList";
import { QUESTION_DIFFICULTY_COLOR_MAP } from "@/constants/question";

interface Props {
  questionList: API.QuestionVO[];
  isLoggedIn: boolean;
  hasActiveSearch: boolean;
}

function trackRecommendClick(questionId?: string | number) {
  if (!questionId) {
    return;
  }
  void logRecommendClickUsingPost({
    questionId,
    source: "personal",
  }).catch(() => undefined);
}

export default function QuestionListRecommendSection({
  questionList,
  isLoggedIn,
  hasActiveSearch,
}: Props) {
  const title = hasActiveSearch ? "搜索之外，也值得继续刷的题目" : "为你推荐下一题";
  const description = hasActiveSearch
    ? "你在筛题时，我们也顺手把更适合继续练的题目放在前面，方便随时换个方向继续刷。"
    : "结合你的刷题记录、收藏偏好和难度分布，给你挑几道更适合接着练的题。";

  return (
    <section className="rounded-[3rem] border border-primary/10 bg-[linear-gradient(135deg,rgba(239,246,255,0.95),rgba(255,255,255,0.98),rgba(245,243,255,0.95))] p-6 shadow-xl shadow-slate-200/40 sm:p-8">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm font-black uppercase tracking-[0.28em] text-primary">
            <Sparkles className="h-4 w-4" />
            Personalized Picks
          </div>
          <div className="text-2xl font-black text-slate-900">{title}</div>
          <p className="max-w-2xl text-sm font-medium leading-6 text-slate-500">{description}</p>
        </div>
        <div className="inline-flex items-center justify-center self-start rounded-2xl border border-white/80 bg-white/80 px-4 py-2 text-sm font-bold text-slate-600 shadow-sm">
          {isLoggedIn
            ? questionList.length
              ? `已为你准备 ${questionList.length} 道候选题`
              : "推荐正在生成中"
            : "登录后解锁个性化推荐"}
        </div>
      </div>

      {!isLoggedIn ? (
        <div className="mt-6 flex flex-col gap-4 rounded-[2rem] border border-dashed border-slate-200 bg-white/80 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-base font-black text-slate-900">
              <LockKeyhole className="h-4 w-4 text-primary" />
              登录后，这里会直接出现你的题目推荐
            </div>
            <p className="text-sm font-medium leading-6 text-slate-500">
              系统会根据你的刷题轨迹、收藏偏好和常练标签，帮你把更适合继续攻克的题目提前挑出来。
            </p>
          </div>
          <Link
            href="/user/login"
            className="inline-flex items-center justify-center rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:-translate-y-0.5 hover:bg-slate-800"
          >
            去登录
          </Link>
        </div>
      ) : questionList.length ? (
        <div className="mt-6 grid gap-4 xl:grid-cols-2">
          {questionList.map((question) => (
            <Link
              key={question.id}
              href={`/question/${question.id}`}
              className="group rounded-[2rem] border border-white/90 bg-white/90 p-5 shadow-sm shadow-slate-200/40 transition hover:-translate-y-0.5 hover:border-primary/20 hover:shadow-xl hover:shadow-primary/5"
              onClick={() => trackRecommendClick(question.id)}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="line-clamp-2 text-lg font-black leading-7 text-slate-900 transition-colors group-hover:text-primary">
                    {question.title}
                  </div>
                  {question.recommendReason ? (
                    <p className="mt-2 line-clamp-2 text-sm font-medium leading-6 text-slate-500">
                      {question.recommendReason}
                    </p>
                  ) : null}
                </div>
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-50 text-slate-400 transition group-hover:bg-primary group-hover:text-white">
                  <ArrowRight className="h-4 w-4" />
                </div>
              </div>

              {question.tagList?.length || question.difficulty ? (
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  {question.tagList?.length ? <TagList tagList={question.tagList.slice(0, 4)} /> : null}
                  {question.difficulty ? (
                    <Tag
                      color={QUESTION_DIFFICULTY_COLOR_MAP[question.difficulty] || "default"}
                      className="rounded-full"
                    >
                      {question.difficulty}
                    </Tag>
                  ) : null}
                </div>
              ) : null}
            </Link>
          ))}
        </div>
      ) : (
        <div className="mt-6 rounded-[2rem] border border-dashed border-slate-200 bg-white/80 p-6">
          <div className="text-base font-black text-slate-900">推荐数据还在养成中</div>
          <p className="mt-2 text-sm font-medium leading-6 text-slate-500">
            你先继续刷几道题、点几个收藏，系统就会更快学到你的偏好，然后把更适合的下一题推荐到这里。
          </p>
        </div>
      )}
    </section>
  );
}
