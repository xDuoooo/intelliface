import React from "react";
import { getQuestionVoByIdUsingGet } from "@/api/questionController";
import { getLoginUserUsingGet } from "@/api/userController";
import QuestionCard from "@/components/QuestionCard";
import Link from "next/link";
import { Sparkles } from "lucide-react";
import QuestionOwnerPanel from "./QuestionOwnerPanel";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

/**
 * 题目详情页
 * @constructor
 */
export default async function QuestionPage({ params }: { params: { questionId: string } }) {
  const { questionId } = params;
  let question: API.QuestionVO | undefined = undefined;
  let loginUser: API.LoginUserVO | undefined = undefined;
  const requestOptions = buildServerRequestOptions();

  const [questionResult, loginUserResult] = await Promise.allSettled([
    getQuestionVoByIdUsingGet(
      {
        id: questionId,
      },
      requestOptions,
    ),
    getLoginUserUsingGet(requestOptions),
  ]);

  if (questionResult.status === "fulfilled") {
    const res = questionResult.value as unknown as API.BaseResponseQuestionVO_;
    question = res.data;
  } else {
    console.error("获取题目详情失败", questionResult.reason);
  }

  if (loginUserResult.status === "fulfilled") {
    const loginUserRes = loginUserResult.value as unknown as API.BaseResponseLoginUserVO_;
    loginUser = loginUserRes.data;
  }
  
  // 错误处理
  if (!question) {
    const questionErrorMessage =
      questionResult.status === "rejected"
        ? String((questionResult.reason as Error | undefined)?.message || "")
        : "";
    const isNotLogin = !loginUser?.id && /401|登录|未登录/i.test(questionErrorMessage);
    const errorTitle = isNotLogin ? "您还没有登录" : "获取题目详情失败";
    const errorDesc = isNotLogin ? "请先登录后再查看题目详情" : "该题目可能已被移除或权限不足";
    
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] space-y-4">
        <div className="h-20 w-20 rounded-full bg-red-50 flex items-center justify-center border border-red-100">
           <span className="text-4xl text-primary"><Sparkles className="h-10 w-10" /></span>
        </div>
        <h1 className="text-xl font-bold text-foreground">{errorTitle}</h1>
        <p className="text-muted-foreground">{errorDesc}</p>
        <Link 
          href={isNotLogin ? `/user/login?redirect=/question/${questionId}` : "/questions"} 
          className="h-11 px-8 rounded-full bg-primary text-primary-foreground font-bold flex items-center justify-center transition-all shadow-lg shadow-primary/20 hover:scale-105 active:scale-95"
        >
          {isNotLogin ? "立即登录" : "返回题目列表"}
        </Link>
      </div>
    );
  }

  const isOwner = Boolean(loginUser?.id) && String(loginUser?.id) === String(question.userId);
  const isAdmin = loginUser?.userRole === "admin";

  return (
    <div id="questionPage" className="max-w-5xl mx-auto pb-20">
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
    </div>
  );
}
