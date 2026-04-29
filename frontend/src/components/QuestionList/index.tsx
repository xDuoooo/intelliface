"use client";
import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ChevronDown, ChevronRight, FileQuestion } from "lucide-react";
import TagList from "@/components/TagList";

interface Props {
  questionBankId?: number;
  questionList: API.QuestionVO[];
  cardTitle?: string;
  collapsibleOnMobile?: boolean;
  mobileInitialCount?: number;
}

/**
 * 题目列表组件
 * @param props
 * @constructor
 */
const QuestionList = (props: Props) => {
  const {
    questionList = [],
    cardTitle,
    questionBankId,
    collapsibleOnMobile = false,
    mobileInitialCount = 6,
  } = props;
  const [isMobile, setIsMobile] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const mediaQuery = window.matchMedia("(max-width: 768px)");
    const updateIsMobile = (event?: MediaQueryListEvent) => {
      setIsMobile(event ? event.matches : mediaQuery.matches);
    };
    updateIsMobile();
    mediaQuery.addEventListener("change", updateIsMobile);
    return () => mediaQuery.removeEventListener("change", updateIsMobile);
  }, []);

  const shouldCollapseOnMobile =
    collapsibleOnMobile && isMobile && questionList.length > mobileInitialCount;

  const visibleQuestionList = useMemo(() => {
    if (!shouldCollapseOnMobile || expanded) {
      return questionList;
    }
    return questionList.slice(0, mobileInitialCount);
  }, [expanded, mobileInitialCount, questionList, shouldCollapseOnMobile]);

  return (
    <div className="space-y-4">
      {cardTitle && (
        <h2 className="text-2xl font-black text-foreground mb-6 pl-4 border-l-4 border-primary">
          {cardTitle}
        </h2>
      )}
      {questionList.length ? (
        <>
        <div className="grid gap-3">
          {visibleQuestionList.map((item) => (
            <Link
              key={item.id}
              href={
                questionBankId
                  ? `/bank/${questionBankId}/question/${item.id}`
                  : `/question/${item.id}`
              }
              className="group flex w-full min-w-0 flex-col justify-between overflow-hidden rounded-3xl border border-slate-100 bg-white p-5 transition-all duration-300 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 sm:flex-row sm:items-center"
            >
              <div className="flex min-w-0 flex-1 flex-col gap-2 pr-0 sm:pr-4">
                <span className="break-words text-base font-bold text-foreground transition-colors group-hover:text-primary sm:truncate">
                  {item.title}
                </span>
                <div className="scale-95 origin-left opacity-90 group-hover:opacity-100 transition-opacity">
                  <TagList tagList={item.tagList} />
                </div>
              </div>
              <div className="mt-4 flex h-10 w-10 shrink-0 items-center justify-center self-end rounded-2xl bg-slate-50 transition-colors group-hover:bg-primary/10 sm:mt-0 sm:self-auto">
                <ChevronRight className="h-5 w-5 text-muted-foreground group-hover:text-primary transition-transform group-hover:translate-x-0.5" />
              </div>
            </Link>
          ))}
        </div>
        {shouldCollapseOnMobile ? (
          <div className="rounded-[1.75rem] border border-slate-200 bg-slate-50/80 px-4 py-4 sm:hidden">
            <div className="flex flex-col gap-3">
              <div className="text-sm font-medium text-slate-500">
                当前显示 <span className="font-bold text-slate-700">{visibleQuestionList.length}</span> /{" "}
                <span className="font-bold text-slate-700">{questionList.length}</span> 道题目
              </div>
              <button
                type="button"
                onClick={() => setExpanded((prev) => !prev)}
                className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-primary/15 bg-white px-4 text-sm font-bold text-primary transition-all active:scale-95"
              >
                {expanded ? "收起题目列表" : "展开全部题目"}
                <ChevronDown
                  className={`h-4 w-4 transition-transform ${expanded ? "rotate-180" : ""}`}
                />
              </button>
            </div>
          </div>
        ) : null}
        </>
      ) : (
        <div className="flex min-h-44 flex-col items-center justify-center rounded-[2rem] border border-dashed border-slate-200 bg-white/70 px-6 py-12 text-center">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-50 text-slate-400">
            <FileQuestion className="h-7 w-7" />
          </div>
          <div className="text-base font-black text-slate-800">暂无题目</div>
          <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">当前没有可展示的题目，换个筛选条件或稍后再来看看。</p>
        </div>
      )}
    </div>
  );
};

export default QuestionList;
