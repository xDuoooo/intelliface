"use client";
import React, { useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { searchQuestionVoByPageUsingPost } from "@/api/questionController";
import TagSearchSelect from "@/components/TagSearchSelect";
import TagList from "@/components/TagList";
import { ChevronLeft, ChevronRight, Filter, Loader2, Search, Sparkles } from "lucide-react";
import { Button, Input, Select, Tag, message } from "antd";
import { QUESTION_DIFFICULTY_COLOR_MAP, QUESTION_DIFFICULTY_OPTIONS } from "@/constants/question";

interface Props {
  defaultQuestionList?: API.QuestionVO[];
  defaultTotal?: number;
  defaultSearchParams?: API.QuestionQueryRequest;
}

const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { label: "最新发布", value: "createTime_descend", sortField: "createTime", sortOrder: "descend" },
  { label: "最近更新", value: "updateTime_descend", sortField: "updateTime", sortOrder: "descend" },
  { label: "标题 A-Z", value: "title_ascend", sortField: "title", sortOrder: "ascend" },
];

function getSortValue(sortField?: string, sortOrder?: string) {
  const matchedSort = SORT_OPTIONS.find(
    (item) => item.sortField === sortField && item.sortOrder === sortOrder,
  );
  return matchedSort?.value || "createTime_descend";
}

function getSortMeta(sortValue: string) {
  return SORT_OPTIONS.find((item) => item.value === sortValue) || SORT_OPTIONS[0];
}

function cleanText(value?: string) {
  const nextValue = value?.trim();
  return nextValue ? nextValue : undefined;
}

/**
 * 题目表格组件
 * @constructor
 */
const QuestionTable: React.FC<Props> = (props) => {
  const { defaultQuestionList, defaultTotal, defaultSearchParams = {} } = props;
  const router = useRouter();
  const pathname = usePathname();

  const [questionList, setQuestionList] = useState<API.QuestionVO[]>(defaultQuestionList || []);
  const [total, setTotal] = useState<number>(defaultTotal || 0);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState(defaultSearchParams.searchText || "");
  const [titleKeyword, setTitleKeyword] = useState(defaultSearchParams.title || "");
  const [contentKeyword, setContentKeyword] = useState(defaultSearchParams.content || "");
  const [answerKeyword, setAnswerKeyword] = useState(defaultSearchParams.answer || "");
  const [selectedTags, setSelectedTags] = useState<string[]>(defaultSearchParams.tags || []);
  const [difficulty, setDifficulty] = useState(defaultSearchParams.difficulty);
  const [sortValue, setSortValue] = useState(
    getSortValue(defaultSearchParams.sortField, defaultSearchParams.sortOrder),
  );
  const [params, setParams] = useState<API.QuestionQueryRequest>({
    current: defaultSearchParams.current || 1,
    pageSize: defaultSearchParams.pageSize || PAGE_SIZE,
    sortField: defaultSearchParams.sortField || "createTime",
    sortOrder: defaultSearchParams.sortOrder || "descend",
    searchText: defaultSearchParams.searchText,
    title: defaultSearchParams.title,
    content: defaultSearchParams.content,
    answer: defaultSearchParams.answer,
    tags: defaultSearchParams.tags,
    difficulty: defaultSearchParams.difficulty,
  });

  const activeFilterCount = useMemo(() => {
    return [
      cleanText(searchText),
      cleanText(titleKeyword),
      cleanText(contentKeyword),
      cleanText(answerKeyword),
      selectedTags.length ? "tags" : "",
      difficulty || "",
      sortValue !== "createTime_descend" ? sortValue : "",
    ].filter(Boolean).length;
  }, [answerKeyword, contentKeyword, difficulty, searchText, selectedTags, sortValue, titleKeyword]);

  const syncUrl = (nextParams: API.QuestionQueryRequest) => {
    const currentPathname = pathname || "/questions";
    const searchParam = new URLSearchParams();
    if (nextParams.searchText) {
      searchParam.set("q", nextParams.searchText);
    }
    if (nextParams.title) {
      searchParam.set("title", nextParams.title);
    }
    if (nextParams.content) {
      searchParam.set("content", nextParams.content);
    }
    if (nextParams.answer) {
      searchParam.set("answer", nextParams.answer);
    }
    if (nextParams.tags?.length) {
      searchParam.set("tags", nextParams.tags.join(","));
    }
    if (nextParams.difficulty) {
      searchParam.set("difficulty", nextParams.difficulty);
    }
    if (nextParams.sortField && nextParams.sortField !== "createTime") {
      searchParam.set("sortField", nextParams.sortField);
    }
    if (nextParams.sortOrder && nextParams.sortOrder !== "descend") {
      searchParam.set("sortOrder", nextParams.sortOrder);
    }
    if ((nextParams.current || 1) > 1) {
      searchParam.set("page", String(nextParams.current));
    }
    const queryString = searchParam.toString();
    if (typeof window !== "undefined") {
      window.dispatchEvent(
        new CustomEvent("question-search-sync", {
          detail: {
            keyword: nextParams.searchText || "",
          },
        }),
      );
    }
    router.replace(queryString ? `${currentPathname}?${queryString}` : currentPathname, { scroll: false });
  };

  const buildRequestParams = (current = 1): API.QuestionQueryRequest => {
    const sortMeta = getSortMeta(sortValue);
    return {
      current,
      pageSize: params.pageSize || PAGE_SIZE,
      searchText: cleanText(searchText),
      title: cleanText(titleKeyword),
      content: cleanText(contentKeyword),
      answer: cleanText(answerKeyword),
      difficulty,
      tags: selectedTags.length ? selectedTags : undefined,
      sortField: sortMeta.sortField,
      sortOrder: sortMeta.sortOrder,
    };
  };

  const fetchData = async (requestParams: API.QuestionQueryRequest) => {
    setLoading(true);
    try {
      const res = (await searchQuestionVoByPageUsingPost(requestParams)) as unknown as API.BaseResponsePageQuestionVO_;
      setQuestionList(res.data?.records || []);
      setTotal(Number(res.data?.total) || 0);
      setParams(requestParams);
      syncUrl(requestParams);
    } catch (error: any) {
      console.error("获取题目失败", error);
      message.error("获取题目失败，" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (e?: React.FormEvent) => {
    e?.preventDefault();
    await fetchData(buildRequestParams(1));
  };

  const handleReset = async () => {
    setSearchText("");
    setTitleKeyword("");
    setContentKeyword("");
    setAnswerKeyword("");
    setSelectedTags([]);
    setDifficulty(undefined);
    setSortValue("createTime_descend");
    await fetchData({
      current: 1,
      pageSize: PAGE_SIZE,
      sortField: "createTime",
      sortOrder: "descend",
    });
  };

  const handlePageChange = async (newPage: number) => {
    await fetchData(buildRequestParams(newPage));
  };

  return (
    <div className="space-y-10">
      <form onSubmit={handleSearch} className="relative group max-w-3xl mx-auto">
        <Search className="absolute left-6 top-1/2 -translate-y-1/2 h-6 w-6 text-muted-foreground group-focus-within:text-primary transition-colors" />
        <input
          type="text"
          placeholder="搜索您感兴趣的面试题..."
          className="w-full h-16 pl-16 pr-36 rounded-[2rem] bg-white border border-slate-200 shadow-2xl shadow-slate-200/40 focus:border-primary focus:ring-8 focus:ring-primary/5 outline-none transition-all font-bold text-lg"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        <button
          type="submit"
          disabled={loading}
          className="absolute right-3 top-1/2 -translate-y-1/2 h-10 px-8 rounded-2xl bg-primary text-primary-foreground font-black text-sm hover:scale-105 active:scale-95 transition-all shadow-lg shadow-primary/25 disabled:opacity-50"
        >
          {loading ? "搜索中..." : "搜索"}
        </button>
      </form>

      <section className="rounded-[2.5rem] border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/35">
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <Filter className="h-5 w-5" />
              </div>
              <div>
                <div className="text-lg font-black text-slate-900">高级筛选</div>
                <div className="mt-1 text-sm text-slate-500">
                  关键词不够时，可以继续按标题、内容、题解和标签精确筛选。
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Tag className="rounded-full border-slate-200 bg-slate-50 px-3 py-1 text-slate-500">
                已启用 {activeFilterCount} 项筛选
              </Tag>
              <Button onClick={() => void handleReset()} disabled={loading}>
                清空筛选
              </Button>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
            <Input
              size="large"
              value={titleKeyword}
              onChange={(e) => setTitleKeyword(e.target.value)}
              placeholder="标题关键词"
              allowClear
            />
            <Input
              size="large"
              value={contentKeyword}
              onChange={(e) => setContentKeyword(e.target.value)}
              placeholder="内容关键词"
              allowClear
            />
            <Input
              size="large"
              value={answerKeyword}
              onChange={(e) => setAnswerKeyword(e.target.value)}
              placeholder="题解关键词"
              allowClear
            />
            <Select
              size="large"
              value={difficulty}
              onChange={setDifficulty}
              options={QUESTION_DIFFICULTY_OPTIONS}
              placeholder="难度筛选"
              allowClear
            />
            <Select
              size="large"
              value={sortValue}
              onChange={setSortValue}
              options={SORT_OPTIONS}
              placeholder="排序方式"
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_160px] lg:items-center">
            <TagSearchSelect
              scene="question"
              value={selectedTags}
              onChange={(value) => setSelectedTags(value)}
              tokenSeparators={[",", " "]}
              placeholder="输入一个或多个标签，例如：MySQL、索引、Java"
            />
            <Button
              type="primary"
              size="large"
              className="h-11 rounded-2xl font-black"
              onClick={() => void handleSearch()}
              loading={loading}
            >
              应用筛选
            </Button>
          </div>
        </div>
      </section>

      <div className="relative min-h-[400px]">
        {loading && (
          <div className="absolute inset-0 z-20 flex items-center justify-center rounded-[3rem] bg-white/40 backdrop-blur-[1px]">
            <Loader2 className="h-12 w-12 animate-spin text-primary" />
          </div>
        )}

        <div className="grid gap-5">
          {questionList.map((item) => (
            <Link
              key={item.id}
              href={`/question/${item.id}`}
              className="group flex w-full min-w-0 flex-col justify-between overflow-hidden rounded-[2.5rem] border border-slate-100/80 bg-white p-6 transition-all duration-500 hover:border-primary/40 hover:shadow-2xl hover:shadow-primary/5 sm:flex-row sm:items-center sm:p-8"
            >
              <div className="min-w-0 flex-1 pr-0 sm:pr-6">
                <div className="flex flex-wrap items-center gap-3">
                  <h3 className="break-words text-xl font-black text-foreground transition-colors group-hover:text-primary sm:text-2xl sm:truncate">
                    {item.title}
                  </h3>
                  <div className="flex flex-wrap items-center gap-2">
                    {item.difficulty ? (
                      <Tag color={QUESTION_DIFFICULTY_COLOR_MAP[item.difficulty] || "default"} className="rounded-full">
                        {item.difficulty}
                      </Tag>
                    ) : null}
                    {item.recommendReason ? (
                      <Tag className="rounded-full border-primary/15 bg-primary/5 px-3 py-1 text-primary">
                        {item.recommendReason}
                      </Tag>
                    ) : null}
                  </div>
                </div>
                <div className="mt-3 scale-100 origin-left">
                  <TagList tagList={item.tagList} />
                </div>
              </div>

              <div className="mt-6 flex shrink-0 items-center gap-5 self-end sm:mt-0 sm:self-auto">
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-50 text-slate-400 shadow-inner transition-all duration-300 group-hover:bg-primary group-hover:text-white group-hover:shadow-lg group-hover:shadow-primary/30">
                  <ChevronRight className="h-6 w-6 transition-transform group-hover:translate-x-0.5" />
                </div>
              </div>
            </Link>
          ))}

          {questionList.length === 0 && !loading && (
            <div className="space-y-6 rounded-[3rem] border-2 border-dashed border-slate-200 bg-white/50 py-24 text-center">
              <div className="text-primary opacity-20 grayscale">
                <Sparkles className="mx-auto h-20 w-20" />
              </div>
              <div className="space-y-1">
                <p className="text-xl font-black text-foreground">没有找到相关题目</p>
                <p className="text-sm font-medium text-muted-foreground">
                  试试减少筛选条件，或者换个关键词重新搜索。
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {total > (params.pageSize || PAGE_SIZE) && (
        <div className="flex items-center justify-center gap-6 pt-12">
          <button
            onClick={() => void handlePageChange((params.current || 1) - 1)}
            disabled={(params.current || 1) === 1 || loading}
            className="flex h-14 w-14 items-center justify-center rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-200/50 transition-all active:scale-90 disabled:opacity-30 disabled:hover:border-slate-200 hover:border-primary hover:text-primary"
          >
            <ChevronLeft className="h-6 w-6" />
          </button>

          <div className="flex items-center gap-3">
            <span className="text-2xl font-black text-foreground">{params.current}</span>
            <span className="text-xl font-bold text-slate-300">/</span>
            <span className="text-xl font-black text-muted-foreground">
              {Math.ceil(total / (params.pageSize || PAGE_SIZE))}
            </span>
          </div>

          <button
            onClick={() => void handlePageChange((params.current || 1) + 1)}
            disabled={(params.current || 1) * (params.pageSize || PAGE_SIZE) >= total || loading}
            className="flex h-14 w-14 items-center justify-center rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-200/50 transition-all active:scale-90 disabled:opacity-30 disabled:hover:border-slate-200 hover:border-primary hover:text-primary"
          >
            <ChevronRight className="h-6 w-6" />
          </button>
        </div>
      )}
    </div>
  );
};

export default QuestionTable;
