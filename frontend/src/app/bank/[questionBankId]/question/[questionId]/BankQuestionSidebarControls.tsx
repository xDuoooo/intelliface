"use client";

import { Pagination } from "antd";
import { Search, X } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";

interface Props {
  current: number;
  pageSize: number;
  total: number;
  defaultQuery?: string;
}

const PAGE_PARAM = "listPage";
const QUERY_PARAM = "listQuery";

const BankQuestionSidebarControls = ({
  current,
  pageSize,
  total,
  defaultQuery = "",
}: Props) => {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [draftQuery, setDraftQuery] = useState(defaultQuery);

  useEffect(() => {
    setDraftQuery(defaultQuery);
  }, [defaultQuery]);

  const visibleCountText = useMemo(() => {
    if (total === 0) {
      return "当前没有匹配的题目";
    }
    const start = Math.min((current - 1) * pageSize + 1, total);
    const end = Math.min(current * pageSize, total);
    return `当前显示 ${start}-${end} / ${total} 道`;
  }, [current, pageSize, total]);

  const shouldShowPagination = total > pageSize || current > 1;

  const updateRoute = (query: string, page: number) => {
    const params = new URLSearchParams(searchParams?.toString() || "");
    const normalizedQuery = query.trim();

    if (normalizedQuery) {
      params.set(QUERY_PARAM, normalizedQuery);
    } else {
      params.delete(QUERY_PARAM);
    }

    if (page <= 1) {
      params.delete(PAGE_PARAM);
    } else {
      params.set(PAGE_PARAM, String(page));
    }

    const queryString = params.toString();
    router.push(`${pathname}${queryString ? `?${queryString}` : ""}`, {
      scroll: false,
    });
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    updateRoute(draftQuery, 1);
  };

  const handleClear = () => {
    setDraftQuery("");
    updateRoute("", 1);
  };

  return (
    <div className="space-y-4 border-b border-slate-100 px-4 py-4">
      <form onSubmit={handleSubmit} className="flex items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={draftQuery}
            onChange={(event) => setDraftQuery(event.target.value)}
            placeholder="搜索题目标题或内容"
            className="h-11 w-full rounded-2xl border border-slate-200 bg-slate-50 pl-10 pr-10 text-sm font-medium text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary/40 focus:bg-white focus:ring-4 focus:ring-primary/10"
          />
          {draftQuery ? (
            <button
              type="button"
              onClick={handleClear}
              className="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full text-slate-400 transition-colors hover:bg-slate-200/70 hover:text-slate-600"
              aria-label="清空搜索"
            >
              <X className="h-4 w-4" />
            </button>
          ) : null}
        </div>
        <button
          type="submit"
          className="inline-flex h-11 shrink-0 items-center justify-center rounded-2xl bg-primary px-4 text-sm font-bold text-primary-foreground transition-all hover:scale-[1.02] active:scale-95"
        >
          搜索
        </button>
      </form>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-xs font-medium text-slate-500">{visibleCountText}</div>
        {shouldShowPagination ? (
          <Pagination
            current={current}
            pageSize={pageSize}
            total={total}
            size="small"
            showSizeChanger={false}
            onChange={(page) => updateRoute(defaultQuery, page)}
          />
        ) : null}
      </div>
    </div>
  );
};

export default BankQuestionSidebarControls;
