import Link from "next/link";
import { Activity, ArrowLeft, BookOpen, BriefcaseBusiness, CalendarClock, Flame, MapPin, NotebookPen, PenSquare, Sparkles } from "lucide-react";
import { getUserProfileVoByIdUsingGet } from "@/api/userController";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { listQuestionVoByPageUsingPost } from "@/api/questionController";
import UserAvatar from "@/components/UserAvatar";
import PublicProfileOwnerActions from "@/app/user/[id]/components/PublicProfileOwnerActions";
import PublicProfileTabs from "@/app/user/[id]/components/PublicProfileTabs";
import { formatIpLocation } from "@/lib/location";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

function isProfileFieldVisible(profile: API.UserProfileVO | undefined, field: string) {
  const visibleFields = profile?.profileVisibleFieldList;
  return !Array.isArray(visibleFields) || visibleFields.includes(field);
}

function formatDate(date?: string) {
  if (!date) {
    return "最近加入";
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

export default async function PublicUserProfilePage({
  params,
}: {
  params: { id: string };
}) {
  const userId = params.id;
  if (!userId || !/^\d+$/.test(userId)) {
    return (
      <div className="py-24 text-center text-slate-400">
        无效的用户主页地址
      </div>
    );
  }

  const requestOptions = buildServerRequestOptions();

  const [profileResult, questionResult, questionBankResult] = await Promise.allSettled([
    getUserProfileVoByIdUsingGet({ id: userId }, requestOptions),
    listQuestionVoByPageUsingPost(
      {
        userId: userId as any,
        reviewStatus: 1,
        pageSize: 6,
        sortField: "createTime",
        sortOrder: "descend",
      },
      requestOptions,
    ),
    listQuestionBankVoByPageUsingPost(
      {
        userId: userId as any,
        reviewStatus: 1,
        pageSize: 6,
        sortField: "createTime",
        sortOrder: "descend",
      },
      requestOptions,
    ),
  ]);

  let profile: API.UserProfileVO | undefined;
  let questionList: API.QuestionVO[] = [];
  let questionBankList: API.QuestionBankVO[] = [];

  if (profileResult.status === "fulfilled") {
    const res = profileResult.value as API.BaseResponseUserProfileVO_;
    profile = res.data;
  }

  if (questionResult.status === "fulfilled") {
    const res = questionResult.value as API.BaseResponsePageQuestionVO_;
    questionList = res.data?.records || [];
  }

  if (questionBankResult.status === "fulfilled") {
    const res = questionBankResult.value as API.BaseResponsePageQuestionBankVO_;
    questionBankList = res.data?.records || [];
  }

  if (!profile?.user) {
    return (
      <div className="space-y-6 py-24 text-center">
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full border border-slate-100 bg-white shadow-xl shadow-slate-200/40">
          <Sparkles className="h-9 w-9 text-primary" />
        </div>
        <div className="space-y-2">
          <h1 className="text-2xl font-black text-slate-900">这个主页暂时无法访问</h1>
          <p className="text-slate-500">该用户可能不存在，或者当前资料未公开。</p>
        </div>
        <Link
          href="/"
          className="inline-flex h-11 items-center justify-center rounded-full bg-primary px-6 font-bold text-white shadow-lg shadow-primary/20 transition-all hover:scale-105 active:scale-95"
        >
          返回首页
        </Link>
      </div>
    );
  }

  const statCards = [
    isProfileFieldVisible(profile, "stats") ? {
      key: "practice",
      label: "累计刷题",
      value: profile.totalQuestionCount || 0,
      icon: <BookOpen className="h-5 w-5 text-primary" />,
    } : null,
    isProfileFieldVisible(profile, "stats") ? {
      key: "mastered",
      label: "已掌握题目",
      value: profile.masteredQuestionCount || 0,
      icon: <Sparkles className="h-5 w-5 text-emerald-500" />,
    } : null,
    isProfileFieldVisible(profile, "stats") ? {
      key: "active",
      label: "活跃天数",
      value: profile.activeDays || 0,
      icon: <Activity className="h-5 w-5 text-sky-500" />,
    } : null,
    isProfileFieldVisible(profile, "stats") ? {
      key: "streak",
      label: "连续学习",
      value: profile.currentStreak || 0,
      icon: <Flame className="h-5 w-5 text-rose-500" />,
    } : null,
    isProfileFieldVisible(profile, "content") ? {
      key: "submission",
      label: "公开题目",
      value: profile.approvedQuestionCount || 0,
      icon: <PenSquare className="h-5 w-5 text-amber-500" />,
    } : null,
    isProfileFieldVisible(profile, "content") ? {
      key: "bank",
      label: "公开题库",
      value: profile.approvedQuestionBankCount || 0,
      icon: <NotebookPen className="h-5 w-5 text-violet-500" />,
    } : null,
  ].filter(Boolean);
  const hasProfileBasics =
    isProfileFieldVisible(profile, "city") ||
    isProfileFieldVisible(profile, "career") ||
    isProfileFieldVisible(profile, "joinTime");
  const showActivity = isProfileFieldVisible(profile, "activity");
  const showContent = isProfileFieldVisible(profile, "content");
  const showRelation = isProfileFieldVisible(profile, "relation");
  const showRelationList = isProfileFieldVisible(profile, "relationList");
  const showStats = isProfileFieldVisible(profile, "stats");

  return (
    <div className="space-y-8 pb-20">
      <Link
        href="/"
        className="inline-flex items-center gap-2 px-2 text-sm font-bold text-slate-400 transition-colors hover:text-primary"
      >
        <ArrowLeft className="h-4 w-4" />
        返回首页
      </Link>

      <section className="overflow-hidden rounded-[2.5rem] border border-slate-100 bg-white p-8 shadow-2xl shadow-slate-200/40 sm:p-12">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex min-w-0 flex-1 items-start gap-5">
            <UserAvatar
              src={profile.user.userAvatar}
              name={profile.user.userName}
              size={88}
              className="ring-4 ring-slate-50"
            />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-3xl font-black tracking-tight text-slate-900 whitespace-normal break-words sm:text-4xl">
                  {profile.user.userName || "匿名用户"}
                </h1>
                {profile.user.userRole === "admin" ? (
                  <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-black uppercase tracking-wider text-amber-600">
                    ADMIN
                  </span>
                ) : null}
              </div>
              {isProfileFieldVisible(profile, "profile") ? (
                <p className="mt-4 max-w-3xl text-base leading-8 text-slate-500">
                  {profile.user.userProfile || "这位用户还没有填写个人简介。"}
                </p>
              ) : null}
              {hasProfileBasics ? (
                <div className="mt-5 flex flex-wrap gap-3 text-sm font-medium text-slate-500">
                {isProfileFieldVisible(profile, "city") ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-4 py-2">
                    <MapPin className="h-4 w-4 text-primary" />
                    {formatIpLocation(profile.user.city)}
                  </span>
                ) : null}
                {isProfileFieldVisible(profile, "career") && profile.user.careerDirection ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-violet-50 px-4 py-2 text-violet-700">
                    <BriefcaseBusiness className="h-4 w-4" />
                    {profile.user.careerDirection}
                  </span>
                ) : null}
                {isProfileFieldVisible(profile, "joinTime") ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-50 px-4 py-2">
                    <CalendarClock className="h-4 w-4 text-primary" />
                    加入于 {formatDate(profile.user.createTime)}
                  </span>
                ) : null}
                </div>
              ) : null}
              {isProfileFieldVisible(profile, "tags") && profile.user.interestTagList?.length ? (
                <div className="mt-4 flex flex-wrap gap-2">
                  {profile.user.interestTagList.map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          </div>

          <div className="rounded-[2rem] border border-primary/10 bg-primary/5 px-5 py-4 text-sm leading-7 text-slate-600 lg:max-w-sm">
            这里展示的是用户选择公开的资料、学习数据和内容贡献。部分模块可能会根据对方的公开主页设置隐藏。
            <PublicProfileOwnerActions userId={profile.user.id} />
          </div>
        </div>

        {statCards.length ? (
        <div className="mt-8 grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
          {statCards.map((item) => (
            <div
              key={item!.key}
              className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 px-5 py-5"
            >
              <div className="flex items-center gap-2 text-sm font-bold text-slate-400">
                {item!.icon}
                <span>{item!.label}</span>
              </div>
              <div className="mt-3 text-3xl font-black tracking-tight text-slate-900">
                {item!.value}
              </div>
            </div>
          ))}
        </div>
        ) : null}
      </section>

      <PublicProfileTabs
        profile={profile}
        questionList={questionList}
        questionBankList={questionBankList}
        showStats={showStats}
        showActivity={showActivity}
        showContent={showContent}
        showRelation={showRelation}
        showRelationList={showRelationList}
      />
    </div>
  );
}
