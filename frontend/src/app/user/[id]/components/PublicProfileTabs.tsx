"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Activity, BookOpen, NotebookPen, UsersRound } from "lucide-react";
import QuestionBankList from "@/components/QuestionBankList";
import TagList from "@/components/TagList";
import PublicAchievementStrip from "@/app/user/[id]/components/PublicAchievementStrip";
import PublicLearningHeatmap from "@/app/user/[id]/components/PublicLearningHeatmap";
import PublicLearningInsights from "@/app/user/[id]/components/PublicLearningInsights";
import UserRelationPanel from "@/app/user/[id]/components/UserRelationPanel";

type TabKey = "learning" | "activity" | "content" | "relation";

interface Props {
  profile: API.UserProfileVO;
  questionList: API.QuestionVO[];
  questionBankList: API.QuestionBankVO[];
  showStats: boolean;
  showActivity: boolean;
  showContent: boolean;
  showRelation: boolean;
  showRelationList: boolean;
}

function formatDate(date?: string) {
  if (!date) {
    return "最近";
  }
  try {
    return new Date(date).toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    });
  } catch {
    return date;
  }
}

export default function PublicProfileTabs({
  profile,
  questionList,
  questionBankList,
  showStats,
  showActivity,
  showContent,
  showRelation,
  showRelationList,
}: Props) {
  const tabs = useMemo(
    () => [
      showStats
        ? {
            key: "learning" as const,
            label: "学习概览",
            icon: <BookOpen className="h-4 w-4" />,
          }
        : null,
      showActivity
        ? {
            key: "activity" as const,
            label: "学习动态",
            icon: <Activity className="h-4 w-4" />,
          }
        : null,
      showContent
        ? {
            key: "content" as const,
            label: "公开内容",
            icon: <NotebookPen className="h-4 w-4" />,
          }
        : null,
      showRelation
        ? {
            key: "relation" as const,
            label: "关注关系",
            icon: <UsersRound className="h-4 w-4" />,
          }
        : null,
    ].filter(Boolean) as Array<{ key: TabKey; label: string; icon: JSX.Element }>,
    [showActivity, showContent, showRelation, showStats],
  );
  const [activeTab, setActiveTab] = useState<TabKey | undefined>(tabs[0]?.key);

  useEffect(() => {
    if (!tabs.length) {
      setActiveTab(undefined);
      return;
    }
    if (!activeTab || !tabs.some((item) => item.key === activeTab)) {
      setActiveTab(tabs[0].key);
    }
  }, [activeTab, tabs]);

  if (!tabs.length) {
    return (
      <section className="rounded-[2.5rem] border border-dashed border-slate-200 bg-white px-8 py-16 text-center text-sm text-slate-400 shadow-xl shadow-slate-200/30">
        这位用户暂时没有公开栏目。
      </section>
    );
  }

  return (
    <section className="rounded-[2.5rem] border border-slate-100 bg-white p-4 shadow-2xl shadow-slate-200/40 sm:p-6">
      <div className="overflow-x-auto pb-2">
        <div className="flex min-w-max gap-2 rounded-[1.6rem] bg-slate-50 p-2">
          {tabs.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setActiveTab(item.key)}
              className={`inline-flex h-11 items-center justify-center gap-2 rounded-[1.15rem] px-5 text-sm font-black transition-all ${
                activeTab === item.key
                  ? "bg-white text-primary shadow-lg shadow-slate-200/70"
                  : "text-slate-500 hover:bg-white/70 hover:text-slate-900"
              }`}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-5">
        {activeTab === "learning" ? (
          <div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">
                  Learning Profile
                </div>
                <h2 className="mt-3 text-2xl font-black tracking-tight text-slate-900">
                  成就进度与刷题轨迹
                </h2>
              </div>
              <div className="text-sm text-slate-400">
                {new Date().getFullYear()} 年
              </div>
            </div>

            <div className="mt-6">
              <PublicLearningInsights profile={profile} />
            </div>

            <div className="mt-5 grid min-w-0 gap-5 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
              <div className="min-w-0">
                <PublicAchievementStrip achievementList={profile.achievementList} />
              </div>
              <div className="min-w-0">
                <PublicLearningHeatmap recordList={profile.questionHistoryRecordList} />
              </div>
            </div>
          </div>
        ) : null}

        {activeTab === "activity" ? (
          <div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">
                  Recent Activity
                </div>
                <h2 className="mt-3 text-2xl font-black tracking-tight text-slate-900">
                  公开学习动态
                </h2>
              </div>
              <div className="text-sm text-slate-400">
                最近动态
              </div>
            </div>

            <div className="mt-6 grid gap-4">
              {profile.recentActivityList?.length ? (
                profile.recentActivityList.map((activity, index) => (
                  <div
                    key={`${activity.type}-${activity.targetId || index}`}
                    className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 px-5 py-5"
                  >
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-black uppercase tracking-wider text-primary">
                            {activity.badge || "动态"}
                          </span>
                          <div className="whitespace-normal break-words text-lg font-black text-slate-900">
                            {activity.title}
                          </div>
                        </div>
                        <div className="mt-3 text-sm leading-7 text-slate-500">
                          {activity.description}
                        </div>
                        {activity.targetUrl ? (
                          <Link
                            href={activity.targetUrl}
                            className="mt-4 inline-flex items-center gap-2 text-sm font-bold text-primary"
                          >
                            查看详情
                            <NotebookPen className="h-4 w-4" />
                          </Link>
                        ) : null}
                      </div>
                      <div className="shrink-0 text-sm text-slate-400">
                        {formatDate(activity.activityTime)}
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="rounded-[1.75rem] border border-dashed border-slate-200 px-6 py-12 text-center text-sm text-slate-400">
                  这位用户暂时还没有公开学习动态。
                </div>
              )}
            </div>
          </div>
        ) : null}

        {activeTab === "content" ? (
          <div>
            {questionBankList.length ? (
              <div className="mb-10 space-y-6">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">
                      Public Banks
                    </div>
                    <h2 className="mt-3 text-2xl font-black tracking-tight text-slate-900">
                      公开题库
                    </h2>
                  </div>
                  <div className="text-sm text-slate-400">
                    {questionBankList.length} 个题库
                  </div>
                </div>
                <QuestionBankList questionBankList={questionBankList} />
              </div>
            ) : null}

            <div className={questionBankList.length ? "border-t border-slate-100 pt-10" : ""}>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">
                    Latest Contributions
                  </div>
                  <h2 className="mt-3 text-2xl font-black tracking-tight text-slate-900">
                    最近公开题目
                  </h2>
                </div>
                <div className="text-sm text-slate-400">
                  {questionList.length} 道题目
                </div>
              </div>

              <div className="mt-6 grid grid-cols-1 gap-4">
                {questionList.length ? (
                  questionList.map((question) => (
                    <Link
                      key={question.id}
                      href={`/question/${question.id}`}
                      className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 px-5 py-5 transition-all hover:-translate-y-0.5 hover:border-primary/20 hover:bg-white hover:shadow-xl hover:shadow-slate-200/30"
                    >
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div className="min-w-0">
                          <div className="whitespace-normal break-words text-lg font-black text-slate-900">
                            {question.title}
                          </div>
                          <div className="mt-3 line-clamp-2 text-sm leading-6 text-slate-500">
                            {question.content}
                          </div>
                          <div className="mt-4">
                            <TagList tagList={question.tagList} />
                          </div>
                          {question.difficulty ? (
                            <div className="mt-3">
                              <span
                                className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${
                                  question.difficulty === "简单"
                                    ? "bg-emerald-50 text-emerald-700"
                                    : question.difficulty === "困难"
                                      ? "bg-orange-50 text-orange-700"
                                      : "bg-blue-50 text-blue-700"
                                }`}
                              >
                                题目难度：{question.difficulty}
                              </span>
                            </div>
                          ) : null}
                        </div>
                        <div className="shrink-0 text-sm text-slate-400">
                          发布于 {formatDate(question.createTime)}
                        </div>
                      </div>
                    </Link>
                  ))
                ) : (
                  <div className="rounded-[1.75rem] border border-dashed border-slate-200 px-6 py-12 text-center text-sm text-slate-400">
                    这位用户暂时还没有公开题目。
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : null}

        {activeTab === "relation" ? (
          <UserRelationPanel
            user={profile.user}
            initialFollowerCount={profile.followerCount}
            initialFollowingCount={profile.followingCount}
            initialHasFollowed={profile.hasFollowed}
            canViewRelationList={showRelationList}
          />
        ) : null}
      </div>
    </section>
  );
}
