import React from "react";
import Link from "next/link";
import { getQuestionBankVoByIdUsingGet } from "@/api/questionBankController";
import { getQuestionVoInBankUsingGet } from "@/api/questionBankQuestionController";
import { getLoginUserUsingGet } from "@/api/userController";
import QuestionCard from "@/components/QuestionCard";
import QuestionOwnerPanel from "@/app/question/[questionId]/QuestionOwnerPanel";
import { cn } from "@/lib/utils";
import { ChevronLeft, ListFilter, Bookmark, Sparkles } from "lucide-react";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

/**
 * 题库题目详情页
 * @constructor
 */
export default async function BankQuestionPage({ params }: { params: { questionBankId: string, questionId: string } }) {
  const { questionBankId, questionId } = params;
  const requestOptions = buildServerRequestOptions();

  // 获取题库详情
  let bank: API.QuestionBankVO | undefined = undefined;
  let question: API.QuestionVO | undefined = undefined;
  let loginUser: API.LoginUserVO | undefined = undefined;

  const [bankResult, questionResult, loginUserResult] = await Promise.allSettled([
    getQuestionBankVoByIdUsingGet(
      {
        id: questionBankId,
        needQueryQuestionList: true,
        pageSize: 200,
      },
      requestOptions,
    ),
    getQuestionVoInBankUsingGet(
      {
        questionBankId,
        questionId,
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

  if (questionResult.status === "fulfilled") {
    const res = questionResult.value as unknown as API.BaseResponseQuestionVO_;
    question = res.data;
  } else {
    console.error("获取题目详情失败", questionResult.reason);
  }

  if (loginUserResult.status === "fulfilled") {
    const res = loginUserResult.value as unknown as API.BaseResponseLoginUserVO_;
    loginUser = res.data;
  }

  if (!bank) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-4">
        <div className="h-20 w-20 rounded-full bg-red-50 flex items-center justify-center border border-red-100">
          <span className="text-4xl text-primary"><Sparkles className="h-10 w-10" /></span>
        </div>
        <h1 className="text-xl font-bold text-foreground">获取题库详情失败</h1>
        <p className="text-muted-foreground">该题库可能已被移除或权限不足</p>
        <Link href="/" className="text-primary font-bold hover:underline">返回首页</Link>
      </div>
    );
  }

  if (!question) {
    const questionErrorMessage =
      questionResult.status === "rejected"
        ? String((questionResult.reason as Error | undefined)?.message || "")
        : "";
    const isNotLogin = !loginUser?.id && /401|登录|未登录/i.test(questionErrorMessage);
    const errorTitle = isNotLogin ? "您还没有登录" : "获取题目详情失败";
    const errorDesc = isNotLogin ? "请先登录后再查看题目详情" : "该题目可能已被移除、不属于当前题库，或权限不足";

    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-4">
        <div className="h-20 w-20 rounded-full bg-red-50 flex items-center justify-center border border-red-100">
          <span className="text-4xl text-primary"><Sparkles className="h-10 w-10" /></span>
        </div>
        <h1 className="text-xl font-bold text-foreground">{errorTitle}</h1>
        <p className="text-muted-foreground">{errorDesc}</p>
        <Link
          href={isNotLogin ? `/user/login?redirect=/bank/${questionBankId}/question/${questionId}` : `/bank/${questionBankId}`}
          className="h-11 px-8 rounded-full bg-primary text-primary-foreground font-bold flex items-center justify-center transition-all shadow-lg shadow-primary/20 hover:scale-105 active:scale-95"
        >
          {isNotLogin ? "立即登录" : "返回题库"}
        </Link>
      </div>
    );
  }

  const isOwner = Boolean(loginUser?.id) && String(loginUser?.id) === String(question.userId);
  const isAdmin = loginUser?.userRole === "admin";

  return (
    <div id="bankQuestionPage" className="flex flex-col lg:flex-row gap-8 pb-20">
      {/* Sidebar - Question Navigation */}
      <aside className="lg:w-80 shrink-0">
        <div className="sticky top-24 space-y-6">
          <Link
            href={`/bank/${questionBankId}`}
            className="group flex items-center gap-2 text-sm font-bold text-muted-foreground hover:text-primary transition-colors px-2"
          >
            <ChevronLeft className="h-4 w-4 group-hover:-translate-x-1 transition-transform" />
            返回题库主页
          </Link>

          <div className="bg-white rounded-[2rem] border border-slate-100 shadow-xl shadow-slate-200/50 overflow-hidden">
            <div className="p-6 border-b border-slate-50 bg-slate-50/50 flex items-center gap-2">
              <ListFilter className="h-4 w-4 text-primary" />
              <h2 className="font-black text-foreground truncate max-w-[200px]" title={bank.title}>
                {bank.title}
              </h2>
            </div>

            <nav className="p-2 max-h-[calc(100vh-250px)] overflow-y-auto custom-scrollbar">
              {(bank.questionPage?.records || []).map((q) => (
                <Link
                  key={q.id}
                  href={`/bank/${questionBankId}/question/${q.id}`}
                  className={cn(
                    "flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-bold transition-all mb-1",
                    String(questionId) === String(q.id)
                      ? "bg-primary text-primary-foreground shadow-lg shadow-primary/20"
                      : "text-muted-foreground hover:bg-slate-50 hover:text-foreground"
                  )}
                >
                  <Bookmark className={cn("h-4 w-4 shrink-0", String(questionId) === String(q.id) ? "fill-current" : "opacity-40")} />
                  <span className="truncate">{q.title}</span>
                </Link>
              ))}
            </nav>
          </div>
        </div>
      </aside>

      {/* Main Content - Question Card */}
      <main className="flex-1 min-w-0">
        {isOwner || isAdmin ? (
          <div className="mb-8">
            <QuestionOwnerPanel
              questionId={questionId}
              reviewStatus={question.reviewStatus}
              reviewMessage={question.reviewMessage}
              reviewTime={question.reviewTime}
              isOwner={isOwner}
              isAdmin={isAdmin}
            />
          </div>
        ) : null}
        <QuestionCard question={question} />
      </main>
    </div>
  );
}
