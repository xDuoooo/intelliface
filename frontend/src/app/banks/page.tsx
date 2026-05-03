import Link from "next/link";
import { listMyQuestionBankVoByPageUsingPost, listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { getLoginUserUsingGet } from "@/api/userController";
import {
  QUESTION_REVIEW_STATUS_ENUM,
} from "@/constants/question";
import { Compass } from "lucide-react";
import BanksExplorer from "./components/BanksExplorer";
import MyDraftQuestionBankSections from "./components/MyDraftQuestionBankSections";
import { buildServerRequestOptions, type ServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";
const MY_DRAFT_BANK_PAGE_SIZE = 4;

const MY_DRAFT_BANK_SECTIONS = [
  {
    status: QUESTION_REVIEW_STATUS_ENUM.PRIVATE,
    title: "我保存的私有题库",
    description: "这些题库只对你自己和管理员可见，可以先慢慢整理结构和内容。",
  },
  {
    status: QUESTION_REVIEW_STATUS_ENUM.PENDING,
    title: "我提交审核中的题库",
    description: "这些题库已经在审核队列中，通过后才会进入公开题库列表。",
  },
  {
    status: QUESTION_REVIEW_STATUS_ENUM.REJECTED,
    title: "我被驳回的题库",
    description: "你可以根据审核意见继续修改题库，再决定何时重新提交公开。",
  },
] as const;

async function loadMyDraftQuestionBankPageByStatus(
  requestOptions: ServerRequestOptions,
  reviewStatus: number,
  current = 1,
) {
  const res = (await listMyQuestionBankVoByPageUsingPost(
    {
      current,
      pageSize: MY_DRAFT_BANK_PAGE_SIZE,
      reviewStatus,
      sortField: "updateTime",
      sortOrder: "descend",
    },
    requestOptions,
  )) as API.BaseResponsePageQuestionBankVO_;

  return {
    current,
    records: res.data?.records || [],
    total: Number(res.data?.total) || 0,
  };
}

async function loadMyDraftQuestionBanks(requestOptions: ServerRequestOptions) {
  const sectionResults = await Promise.all(
    MY_DRAFT_BANK_SECTIONS.map(async (section) => {
      const res = await loadMyDraftQuestionBankPageByStatus(requestOptions, section.status);
      return {
        ...section,
        current: res.current,
        records: res.records,
        total: res.total,
      };
    }),
  );
  return sectionResults.filter((section) => section.total > 0);
}

/**
 * 题库列表页面
 * @constructor
 */
export default async function BanksPage() {
  const requestOptions = buildServerRequestOptions();
  let questionBankList: API.QuestionBankVO[] = [];
  let total = 0;
  let myDraftSections: Array<
    (typeof MY_DRAFT_BANK_SECTIONS)[number] & { current: number; records: API.QuestionBankVO[]; total: number }
  > = [];
  const pageSize = 12;

  try {
    const [publicRes, loginRes] = await Promise.allSettled([
      listQuestionBankVoByPageUsingPost(
        {
          pageSize,
          reviewStatus: QUESTION_REVIEW_STATUS_ENUM.APPROVED,
          sortField: "createTime",
          sortOrder: "descend",
        },
        requestOptions,
      ),
      getLoginUserUsingGet(requestOptions),
    ]);
    const res =
      publicRes.status === "fulfilled"
        ? (publicRes.value as unknown as API.BaseResponsePageQuestionBankVO_)
        : undefined;
    const records = res?.data?.records ?? [];
    questionBankList = records;
    total = Number(res?.data?.total) || 0;
    if (loginRes.status === "fulfilled" && loginRes.value?.data?.id) {
      myDraftSections = await loadMyDraftQuestionBanks(requestOptions);
    }
  } catch (e) {
    console.error("获取题库列表失败", e);
  }

  return (
    <div id="banksPage" className="space-y-12 pb-20">
       {/* Header Section */}
      <section className="relative overflow-hidden rounded-[3rem] bg-white border border-slate-100 p-8 sm:p-16 text-slate-900 shadow-2xl shadow-slate-200/50">
         <div className="absolute top-0 right-0 -mt-10 -mr-10 h-64 w-64 rounded-full bg-primary/5 blur-3xl opacity-60" />
         <div className="relative z-10 space-y-4">
            <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-sm">
               <Compass className="h-4 w-4" />
               <span>Curated Collections</span>
            </div>
            <h1 className="text-4xl sm:text-6xl font-black tracking-tight text-slate-900">
               面试题库
            </h1>
            <p className="text-lg text-slate-500 font-medium max-w-2xl">
               按领域划分的专业题库，系统化攻克大厂面试知识点。
            </p>
         </div>
      </section>

      {myDraftSections.length ? (
        <MyDraftQuestionBankSections sections={myDraftSections} pageSize={MY_DRAFT_BANK_PAGE_SIZE} />
      ) : null}

      <BanksExplorer initialQuestionBankList={questionBankList} initialTotal={total} initialPageSize={pageSize} />
    </div>
  );
}
