import Link from "next/link";
import {
  listMyQuestionVoByPageUsingPost,
  listPersonalRecommendQuestionVoUsingGet,
  searchQuestionVoByPageUsingPost,
} from "@/api/questionController";
import { getLoginUserUsingGet } from "@/api/userController";
import QuestionTable from "@/components/QuestionTable";
import {
  QUESTION_REVIEW_STATUS_ENUM,
  QUESTION_REVIEW_STATUS_TEXT_MAP,
} from "@/constants/question";
import { Sparkles } from "lucide-react";
import { buildServerRequestOptions, type ServerRequestOptions } from "@/libs/serverRequestOptions";
import QuestionListRecommendSection from "./components/QuestionListRecommendSection";

export const dynamic = "force-dynamic";

const MY_DRAFT_SECTIONS = [
  {
    status: QUESTION_REVIEW_STATUS_ENUM.PRIVATE,
    title: "我保存的私有题目",
    description: "这些题目只有你自己和管理员可见，准备好后再提交审核。",
    accentClassName: "border-slate-200 bg-slate-50/80",
  },
  {
    status: QUESTION_REVIEW_STATUS_ENUM.PENDING,
    title: "我提交审核中的题目",
    description: "这些题目已经进入审核流程，审核通过后会进入公开题目列表。",
    accentClassName: "border-amber-200 bg-amber-50/80",
  },
  {
    status: QUESTION_REVIEW_STATUS_ENUM.REJECTED,
    title: "我被驳回的题目",
    description: "可以根据审核意见修改后重新提交，让公开质量更稳一些。",
    accentClassName: "border-rose-200 bg-rose-50/80",
  },
] as const;

function getSingleParam(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value[0] || "";
  }
  return value || "";
}

function parseTagParam(value?: string | string[]) {
  const rawValue = getSingleParam(value);
  if (!rawValue) {
    return undefined;
  }
  const tagList = rawValue
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  return tagList.length ? tagList : undefined;
}

function normalizeSortField(value?: string | string[]) {
  const sortField = getSingleParam(value);
  const allowedSortFieldSet = new Set(["createTime", "updateTime", "title"]);
  return allowedSortFieldSet.has(sortField) ? sortField : "createTime";
}

function normalizeSortOrder(value?: string | string[]) {
  const sortOrder = getSingleParam(value);
  return sortOrder === "ascend" ? "ascend" : "descend";
}

function buildQuestionTableKey(params: API.QuestionQueryRequest) {
  return JSON.stringify({
    searchText: params.searchText || "",
    title: params.title || "",
    content: params.content || "",
    answer: params.answer || "",
    difficulty: params.difficulty || "",
    tags: params.tags || [],
    sortField: params.sortField || "createTime",
    sortOrder: params.sortOrder || "descend",
    current: params.current || 1,
  });
}

function hasActiveSearchFilters(params: API.QuestionQueryRequest) {
  return Boolean(
    params.searchText ||
      params.title ||
      params.content ||
      params.answer ||
      params.difficulty ||
      params.tags?.length,
  );
}

async function loadMyDraftQuestions(requestOptions: ServerRequestOptions) {
  const sectionResults = await Promise.all(
    MY_DRAFT_SECTIONS.map(async (section) => {
      const res = (await listMyQuestionVoByPageUsingPost(
        {
          current: 1,
          pageSize: 3,
          reviewStatus: section.status,
          sortField: "updateTime",
          sortOrder: "descend",
        },
        requestOptions,
      )) as API.BaseResponsePageQuestionVO_;
      return {
        ...section,
        records: res.data?.records || [],
        total: Number(res.data?.total) || 0,
      };
    }),
  );
  return sectionResults.filter((section) => section.total > 0);
}

/**
 * 题目列表页面
 * @constructor
 */
export default async function QuestionsPage({
  searchParams,
}: {
  searchParams: {
    q?: string | string[];
    title?: string | string[];
    content?: string | string[];
    answer?: string | string[];
    difficulty?: string | string[];
    tags?: string | string[];
    sortField?: string | string[];
    sortOrder?: string | string[];
    page?: string | string[];
  };
}) {
  const requestOptions = buildServerRequestOptions();
  const defaultSearchParams: API.QuestionQueryRequest = {
    searchText: getSingleParam(searchParams.q),
    title: getSingleParam(searchParams.title),
    content: getSingleParam(searchParams.content),
    answer: getSingleParam(searchParams.answer),
    difficulty: getSingleParam(searchParams.difficulty),
    tags: parseTagParam(searchParams.tags),
    sortField: normalizeSortField(searchParams.sortField),
    sortOrder: normalizeSortOrder(searchParams.sortOrder),
    current: Number(getSingleParam(searchParams.page)) || 1,
    pageSize: 12,
  };
  // 题目列表和总数
  let questionList: API.QuestionVO[] = [];
  let total = 0;
  let personalRecommendQuestionList: API.QuestionVO[] = [];
  let isLoggedIn = false;
  let myDraftSections: Array<
    (typeof MY_DRAFT_SECTIONS)[number] & { records: API.QuestionVO[]; total: number }
  > = [];

  try {
    const [publicRes, loginRes] = await Promise.allSettled([
      searchQuestionVoByPageUsingPost(defaultSearchParams, requestOptions),
      getLoginUserUsingGet(requestOptions),
    ]);
    const res =
      publicRes.status === "fulfilled"
        ? (publicRes.value as unknown as API.BaseResponsePageQuestionVO_)
        : undefined;
    questionList = res?.data?.records ?? [];
    total = res?.data?.total ?? 0;
    if (loginRes.status === "fulfilled" && loginRes.value?.data?.id) {
      isLoggedIn = true;
      const [draftSectionResult, personalRecommendResult] = await Promise.allSettled([
        loadMyDraftQuestions(requestOptions),
        listPersonalRecommendQuestionVoUsingGet(
          {
            size: 4,
          },
          requestOptions,
        ),
      ]);
      if (draftSectionResult.status === "fulfilled") {
        myDraftSections = draftSectionResult.value;
      }
      if (personalRecommendResult.status === "fulfilled") {
        personalRecommendQuestionList = personalRecommendResult.value.data || [];
      }
    }
  } catch (e) {
    console.error("获取题目列表失败", e);
  }

  return (
    <div id="questionsPage" className="space-y-12 pb-20">
      {/* Header Section */}
      <section className="relative overflow-hidden rounded-[3rem] bg-white border border-slate-100 p-8 sm:p-16 text-slate-900 shadow-2xl shadow-slate-200/50">
         <div className="absolute top-0 right-0 -mt-10 -mr-10 h-64 w-64 rounded-full bg-primary/5 blur-3xl opacity-60" />
         <div className="relative z-10 space-y-4">
            <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-sm">
               <Sparkles className="h-4 w-4" />
               <span>Over 10,000+ Questions</span>
            </div>
            <h1 className="text-4xl sm:text-6xl font-black tracking-tight text-slate-900">
               题目大全
            </h1>
            <p className="text-lg text-slate-500 font-medium max-w-2xl">
               探索精选的面试真题，涵盖前端、后端、架构、算法等多个领域。
            </p>
         </div>
      </section>

      {myDraftSections.length ? (
        <section className="rounded-[3rem] border border-blue-100 bg-blue-50/70 p-6 sm:p-8 shadow-xl shadow-blue-100/40">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-2">
              <div className="text-sm font-black uppercase tracking-[0.32em] text-blue-500">
                My Workspace
              </div>
              <h2 className="text-2xl font-black text-slate-900">我自己的未公开题目</h2>
              <p className="max-w-2xl text-sm font-medium text-slate-500">
                公开题目列表只展示审核通过的内容。你已登录时，这里会额外展示自己的私有、待审核和已驳回题目，方便继续完善。
              </p>
            </div>
            <Link
              href="/user/center?tab=submission"
              className="inline-flex items-center justify-center rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:-translate-y-0.5 hover:bg-slate-800"
            >
              去我的题目管理
            </Link>
          </div>

          <div className="mt-6 grid gap-4 xl:grid-cols-3">
            {myDraftSections.map((section) => (
              <div
                key={section.status}
                className={`rounded-[2rem] border p-5 shadow-sm shadow-slate-200/40 ${section.accentClassName}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="text-base font-black text-slate-900">{section.title}</div>
                    <div className="mt-1 text-sm text-slate-500">{section.description}</div>
                  </div>
                  <div className="rounded-2xl bg-white/90 px-3 py-2 text-center shadow-sm">
                    <div className="text-lg font-black text-slate-900">{section.total}</div>
                    <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">当前数量</div>
                  </div>
                </div>

                <div className="mt-4 space-y-3">
                  {section.records.map((question) => (
                    <Link
                      key={question.id}
                      href={`/question/${question.id}`}
                      className="block rounded-[1.5rem] border border-white/90 bg-white/85 p-4 transition hover:-translate-y-0.5 hover:border-primary/20 hover:shadow-lg hover:shadow-slate-200/50"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-sm font-black text-slate-900">{question.title}</div>
                          <div className="mt-1 text-xs font-medium text-slate-500">
                            {QUESTION_REVIEW_STATUS_TEXT_MAP[Number(question.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED)] || "未知状态"}
                            {question.updateTime ? ` · ${new Date(question.updateTime).toLocaleDateString("zh-CN")}` : ""}
                          </div>
                        </div>
                        {question.difficulty ? (
                          <span className="shrink-0 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-500">
                            {question.difficulty}
                          </span>
                        ) : null}
                      </div>
                      {Number(question.reviewStatus) === QUESTION_REVIEW_STATUS_ENUM.REJECTED && question.reviewMessage ? (
                        <div className="mt-3 rounded-2xl bg-rose-50 px-3 py-2 text-xs font-medium text-rose-600">
                          驳回原因：{question.reviewMessage}
                        </div>
                      ) : null}
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      {/* Main Content Area */}
      <section className="bg-white/50 backdrop-blur-sm rounded-[3rem] p-6 sm:p-10 border border-white shadow-2xl shadow-slate-200/50">
        <QuestionTable
          key={buildQuestionTableKey(defaultSearchParams)}
          defaultQuestionList={questionList}
          defaultTotal={total}
          defaultSearchParams={defaultSearchParams}
        />
      </section>

      <QuestionListRecommendSection
        questionList={personalRecommendQuestionList}
        isLoggedIn={isLoggedIn}
        hasActiveSearch={hasActiveSearchFilters(defaultSearchParams)}
      />
    </div>
  );
}
