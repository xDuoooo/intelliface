import React from "react";
import Image from "next/image";
import Link from "next/link";
import { getQuestionBankLeaderboardUsingGet } from "@/api/leaderboardController";
import { getQuestionBankVoByIdUsingGet } from "@/api/questionBankController";
import { getLoginUserUsingGet } from "@/api/userController";
import QuestionList from "@/components/QuestionList";
import QuestionBankLeaderboardCard from "@/components/QuestionBankLeaderboardCard";
import QuestionBankOwnerPanel from "./QuestionBankOwnerPanel";
import BankQuestionPagination from "./BankQuestionPagination";
import { Play, BookOpen, Clock, Users, Sparkles } from "lucide-react";
import { cn, validateImageSrc } from "@/lib/utils";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

const QUESTION_PAGE_SIZE = 20;

function getSingleParam(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value[0] || "";
  }
  return value || "";
}

/**
 * 题库详情页
 * @constructor
 */
export default async function BankPage({
  params,
  searchParams,
}: {
  params: { questionBankId: string };
  searchParams: {
    page?: string | string[];
  };
}) {
  const { questionBankId } = params;
  const current = Math.max(1, Number(getSingleParam(searchParams.page)) || 1);
  let bank: API.QuestionBankVO | undefined = undefined;
  let leaderboard: API.QuestionBankLeaderboardVO | undefined = undefined;
  let loginUser: API.LoginUserVO | undefined = undefined;

  const requestOptions = buildServerRequestOptions();

  const [bankResult, leaderboardResult, loginUserResult] = await Promise.allSettled([
    getQuestionBankVoByIdUsingGet(
      {
        id: questionBankId,
        needQueryQuestionList: true,
        current,
        pageSize: QUESTION_PAGE_SIZE,
      },
      requestOptions,
    ),
    getQuestionBankLeaderboardUsingGet(
      {
        questionBankId: questionBankId as any,
      },
      requestOptions,
    ),
    getLoginUserUsingGet(requestOptions),
  ]);

  if (bankResult.status === "fulfilled") {
    const res = bankResult.value as unknown as API.BaseResponseQuestionBankVO_;
    bank = res.data;
  } else {
    console.error("获取题库详情失败", bankResult.reason);
  }

  if (leaderboardResult.status === "fulfilled") {
    const leaderboardRes = leaderboardResult.value as unknown as API.BaseResponseQuestionBankLeaderboardVO_;
    leaderboard = leaderboardRes.data;
  } else {
    console.error("获取题库榜单失败", leaderboardResult.reason);
  }

  if (loginUserResult.status === "fulfilled") {
    const loginUserRes = loginUserResult.value as unknown as API.BaseResponseLoginUserVO_;
    loginUser = loginUserRes.data;
  }

  if (!bank) {
    const bankErrorMessage =
      bankResult.status === "rejected"
        ? String((bankResult.reason as Error | undefined)?.message || "")
        : "";
    const isNotLogin = !loginUser?.id && /401|登录|未登录/i.test(bankErrorMessage);
    const errorTitle = isNotLogin ? "您还没有登录" : "获取题库详情失败";
    const errorDesc = isNotLogin ? "请先登录后再查看题库详情" : "该题库可能已被移除或权限不足";

    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-4">
        <div className="h-20 w-20 rounded-full bg-red-50 flex items-center justify-center border border-red-100">
          <span className="text-4xl text-primary"><Sparkles className="h-10 w-10" /></span>
        </div>
        <h1 className="text-xl font-bold text-foreground">{errorTitle}</h1>
        <p className="text-muted-foreground">{errorDesc}</p>
        <Link
          href={isNotLogin ? `/user/login?redirect=/bank/${questionBankId}` : "/"}
          className="h-11 px-8 rounded-full bg-primary text-primary-foreground font-bold flex items-center justify-center transition-all shadow-lg shadow-primary/20 hover:scale-105 active:scale-95"
        >
          {isNotLogin ? "立即登录" : "返回首页"}
        </Link>
      </div>
    );
  }

  const isOwner = Boolean(loginUser?.id) && String(loginUser?.id) === String(bank.userId);
  const isAdmin = loginUser?.userRole === "admin";
  const firstQuestionId = bank.questionPage?.records?.[0]?.id;
  const total = Number(bank.questionPage?.total) || 0;

  return (
    <div id="bankPage" className="space-y-10 pb-20">
      {/* Bank Hero Card */}
      <section className="relative overflow-hidden rounded-[3rem] bg-white border border-slate-100 shadow-2xl shadow-slate-200/50 p-8 sm:p-12">
        <div className="flex flex-col md:flex-row gap-10 items-start">
          <div className="relative h-32 w-32 sm:h-48 sm:w-48 rounded-[2.5rem] overflow-hidden shadow-2xl ring-4 ring-slate-50 shrink-0">
            <Image
              src={validateImageSrc(bank.picture)}
              fill
              alt={bank.title || "题库"}
              className="object-cover"
            />
          </div>

          <div className="flex-1 space-y-6">
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-xs">
                <BookOpen className="h-4 w-4" />
                <span>Premium Collection</span>
              </div>
              <h1 className="text-3xl sm:text-5xl font-black text-foreground leading-tight">
                {bank.title}
              </h1>
              <p className="text-lg text-muted-foreground font-medium leading-relaxed max-w-2xl">
                {bank.description}
              </p>
            </div>

            <div className="flex flex-wrap gap-6 pt-2">
              <div className="flex items-center gap-2 px-4 py-2 rounded-2xl bg-slate-50 border border-slate-100">
                <Clock className="h-4 w-4 text-primary" />
                <span className="text-sm font-bold text-slate-600">更新时间：{new Date(bank.updateTime || Date.now()).toLocaleDateString()}</span>
              </div>
              <div className="flex items-center gap-2 px-4 py-2 rounded-2xl bg-slate-50 border border-slate-100">
                <Users className="h-4 w-4 text-primary" />
                <span className="text-sm font-bold text-slate-600">{bank.questionPage?.total || 0} 道核心真题</span>
              </div>
            </div>

            <div className="pt-4">
              <Link
                href={`/bank/${questionBankId}/question/${firstQuestionId}`}
                className={cn(
                  "inline-flex h-14 px-10 rounded-2xl bg-primary text-primary-foreground font-black text-lg items-center gap-3 transition-all shadow-xl shadow-primary/20",
                  !firstQuestionId ? "opacity-50 pointer-events-none grayscale" : "hover:scale-105 active:scale-95"
                )}
              >
                <Play className="h-6 w-6 fill-current" />
                立即开始刷题
              </Link>
            </div>
          </div>
        </div>
      </section>

      {isOwner || isAdmin ? (
        <QuestionBankOwnerPanel
          questionBankId={questionBankId}
          reviewStatus={bank.reviewStatus}
          reviewMessage={bank.reviewMessage}
          reviewTime={bank.reviewTime}
          isOwner={isOwner}
          isAdmin={isAdmin}
        />
      ) : null}

      <QuestionBankLeaderboardCard leaderboard={leaderboard} />

      {/* Questions Explorer */}
      <section id="question-list" className="space-y-8">
        <QuestionList
          questionBankId={questionBankId as any}
          questionList={bank.questionPage?.records ?? []}
          cardTitle={`题目列表 (${total})`}
          collapsibleOnMobile
          mobileInitialCount={6}
        />
        {total > QUESTION_PAGE_SIZE ? (
          <BankQuestionPagination
            current={current}
            pageSize={QUESTION_PAGE_SIZE}
            total={total}
            anchorId="question-list"
          />
        ) : null}
      </section>
    </div>
  );
}
