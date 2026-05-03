"use client";

import React, { useState } from "react";
import Link from "next/link";
import { message, Pagination, Spin } from "antd";
import { listMyQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import QuestionBankList from "@/components/QuestionBankList";

type DraftQuestionBankSection = {
  status: number;
  title: string;
  description: string;
  current: number;
  records: API.QuestionBankVO[];
  total: number;
};

interface Props {
  sections: DraftQuestionBankSection[];
  pageSize: number;
}

const MyDraftQuestionBankSections: React.FC<Props> = ({ sections, pageSize }) => {
  const [sectionStateList, setSectionStateList] = useState(sections);
  const [loadingStatusMap, setLoadingStatusMap] = useState<Record<number, boolean>>({});

  const handlePageChange = async (status: number, page: number) => {
    setLoadingStatusMap((prev) => ({
      ...prev,
      [status]: true,
    }));
    try {
      const res = await listMyQuestionBankVoByPageUsingPost({
        current: page,
        pageSize,
        reviewStatus: status,
        sortField: "updateTime",
        sortOrder: "descend",
      });
      setSectionStateList((prev) =>
        prev.map((section) =>
          section.status === status
            ? {
                ...section,
                current: page,
                records: res.data?.records || [],
                total: Number(res.data?.total) || 0,
              }
            : section,
        ),
      );
    } catch (error: any) {
      message.error(error?.message || "加载题库失败，请稍后重试");
    } finally {
      setLoadingStatusMap((prev) => ({
        ...prev,
        [status]: false,
      }));
    }
  };

  return (
    <section className="rounded-[3rem] border border-white bg-white/50 p-6 shadow-2xl shadow-slate-200/50 backdrop-blur-sm sm:p-10">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-2">
          <div className="text-sm font-black uppercase tracking-[0.32em] text-blue-500">
            My Workspace
          </div>
          <h2 className="text-2xl font-black text-slate-900">我自己的未公开题库</h2>
          <p className="max-w-2xl text-sm font-medium text-slate-500">
            公开题库页只展示审核通过的题库。你已登录时，这里会额外展示自己的私有、待审核和已驳回题库，方便继续沉淀和调整。
          </p>
        </div>
        <Link
          href="/user/center?tab=banks"
          className="inline-flex items-center justify-center rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:-translate-y-0.5 hover:bg-slate-800"
        >
          去我的题库管理
        </Link>
      </div>

      <div className="mt-8 space-y-8">
        {sectionStateList.map((section) => (
          <div
            key={section.status}
            className="space-y-4 rounded-[2rem] border border-slate-100 bg-white/70 p-5 shadow-sm shadow-slate-200/40"
          >
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <div className="text-lg font-black text-slate-900">{section.title}</div>
                <div className="mt-1 text-sm font-medium leading-6 text-slate-500">{section.description}</div>
              </div>
              <div className="rounded-2xl bg-slate-50 px-4 py-3 text-center ring-1 ring-slate-100">
                <div className="text-lg font-black text-slate-900">{section.total}</div>
                <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">总数量</div>
              </div>
            </div>

            <Spin spinning={!!loadingStatusMap[section.status]}>
              <QuestionBankList
                questionBankList={section.records}
                showReviewStatus
                showUpdateTime
              />
            </Spin>

            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div className="text-sm font-medium text-slate-500">
                当前显示{" "}
                <span className="font-bold text-slate-700">
                  {section.total === 0 ? 0 : (section.current - 1) * pageSize + 1}
                </span>
                {" - "}
                <span className="font-bold text-slate-700">
                  {Math.min(section.current * pageSize, section.total)}
                </span>
                {" / 共 "}
                <span className="font-bold text-slate-700">{section.total}</span>
                {" 个"}
              </div>

              {section.total > pageSize ? (
                <div className="flex justify-center sm:justify-end">
                <Pagination
                  current={section.current}
                  total={section.total}
                  pageSize={pageSize}
                  showSizeChanger={false}
                  showLessItems
                  disabled={!!loadingStatusMap[section.status]}
                  onChange={(page) => {
                    void handlePageChange(section.status, page);
                  }}
                />
                </div>
              ) : null}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
};

export default MyDraftQuestionBankSections;
