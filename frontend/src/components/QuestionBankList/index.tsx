"use client";
import React from "react";
import Link from "next/link";
import Image from "next/image";
import { LibraryBig } from "lucide-react";
import { validateImageSrc } from "@/lib/utils";
import {
    QUESTION_REVIEW_STATUS_ENUM,
    QUESTION_REVIEW_STATUS_TEXT_MAP,
} from "@/constants/question";

interface Props {
    questionBankList: API.QuestionBankVO[];
    showReviewStatus?: boolean;
    showUpdateTime?: boolean;
}

const REVIEW_STATUS_CLASS_MAP: Record<number, string> = {
    [QUESTION_REVIEW_STATUS_ENUM.PRIVATE]: "bg-slate-100 text-slate-600 ring-slate-200",
    [QUESTION_REVIEW_STATUS_ENUM.PENDING]: "bg-amber-50 text-amber-700 ring-amber-100",
    [QUESTION_REVIEW_STATUS_ENUM.APPROVED]: "bg-emerald-50 text-emerald-700 ring-emerald-100",
    [QUESTION_REVIEW_STATUS_ENUM.REJECTED]: "bg-rose-50 text-rose-700 ring-rose-100",
};

function formatDate(date?: string) {
    if (!date) {
        return "";
    }
    try {
        return new Date(date).toLocaleDateString("zh-CN", {
            month: "2-digit",
            day: "2-digit",
        });
    } catch {
        return date;
    }
}

/**
 * 题库列表组件
 * @param props
 * @constructor
 */
const QuestionBankList = (props: Props) => {
    const {questionBankList = [], showReviewStatus = false, showUpdateTime = false} = props;

    if (!questionBankList.length) {
        return (
            <div className="flex min-h-48 flex-col items-center justify-center rounded-[2rem] border border-dashed border-slate-200 bg-white/70 px-6 py-12 text-center">
                <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-50 text-slate-400">
                    <LibraryBig className="h-7 w-7" />
                </div>
                <div className="text-base font-black text-slate-800">暂无题库</div>
                <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">当前没有可展示的题库，换个筛选条件或稍后再来看看。</p>
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {questionBankList.map((bank) => (
                <Link
                    key={bank.id}
                    href={`/bank/${bank.id}`}
                    className="group relative flex flex-col items-center p-6 rounded-[2rem] bg-white ring-1 ring-slate-200 hover:ring-primary/50 hover:shadow-xl hover:shadow-primary/10 transition-all duration-500"
                >
                    {showReviewStatus ? (
                        <div className="absolute left-4 top-4 z-10">
                            <span
                                className={`inline-flex rounded-full px-3 py-1 text-xs font-black ring-1 ${
                                    REVIEW_STATUS_CLASS_MAP[
                                        Number(bank.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED)
                                    ] || "bg-slate-100 text-slate-600 ring-slate-200"
                                }`}
                            >
                                {QUESTION_REVIEW_STATUS_TEXT_MAP[
                                    Number(bank.reviewStatus ?? QUESTION_REVIEW_STATUS_ENUM.APPROVED)
                                ] || "未知状态"}
                            </span>
                        </div>
                    ) : null}
                    <div
                        className="relative h-20 w-20 mb-4 rounded-3xl overflow-hidden ring-4 ring-slate-50 group-hover:ring-primary/20 transition-all shadow-inner">
                        <Image
                            src={validateImageSrc(bank.picture)}
                            fill
                            alt={bank.title || "题库"}
                            className="object-cover group-hover:scale-110 transition-transform duration-500"
                        />
                    </div>
                    <h3 className="line-clamp-2 break-words text-center text-lg font-black text-foreground transition-colors group-hover:text-primary mb-2">
                        {bank.title}
                    </h3>
                    <p className="text-sm text-muted-foreground line-clamp-2 break-words text-center font-medium opacity-80 group-hover:opacity-100 transition-opacity">
                        {bank.description}
                    </p>
                    {showUpdateTime && bank.updateTime ? (
                        <div className="mt-4 rounded-full bg-slate-50 px-3 py-1 text-xs font-bold text-slate-400">
                            更新于 {formatDate(bank.updateTime)}
                        </div>
                    ) : null}
                </Link>
            ))}
        </div>
    );
};

export default QuestionBankList;
