"use client";

import React, { useState } from "react";
import { Button, Card, Empty, Input, Pagination, Select, Typography, message } from "antd";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import QuestionBankList from "@/components/QuestionBankList";
import { QUESTION_REVIEW_STATUS_ENUM } from "@/constants/question";

const { Title, Paragraph, Text } = Typography;

type Props = {
  initialQuestionBankList: API.QuestionBankVO[];
  initialTotal: number;
  initialCurrent?: number;
  initialPageSize?: number;
};

type FilterState = {
  searchText?: string;
  sortKey: string;
};

const SORT_OPTIONS = [
  { label: "最新公开", value: "createTime_desc" },
  { label: "最近更新", value: "updateTime_desc" },
  { label: "标题 A-Z", value: "title_asc" },
  { label: "标题 Z-A", value: "title_desc" },
];

const getSortParams = (sortKey: string) => {
  const [sortField, sortOrderKey] = sortKey.split("_");
  return {
    sortField,
    sortOrder: sortOrderKey === "asc" ? "ascend" : "descend",
  };
};

const BanksExplorer: React.FC<Props> = ({
  initialQuestionBankList,
  initialTotal,
  initialCurrent = 1,
  initialPageSize = 12,
}) => {
  const [loading, setLoading] = useState(false);
  const [questionBankList, setQuestionBankList] = useState<API.QuestionBankVO[]>(initialQuestionBankList);
  const [total, setTotal] = useState(initialTotal);
  const [current, setCurrent] = useState(initialCurrent);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [filters, setFilters] = useState<FilterState>({
    sortKey: "createTime_desc",
  });

  const loadData = async (
    nextCurrent = current,
    nextPageSize = pageSize,
    nextFilters: FilterState = filters,
  ) => {
    setLoading(true);
    try {
      const { sortField, sortOrder } = getSortParams(nextFilters.sortKey);
      const res = await listQuestionBankVoByPageUsingPost({
        current: nextCurrent,
        pageSize: nextPageSize,
        reviewStatus: QUESTION_REVIEW_STATUS_ENUM.APPROVED,
        searchText: nextFilters.searchText?.trim() || undefined,
        sortField,
        sortOrder,
      });
      setQuestionBankList(res.data?.records || []);
      setTotal(Number(res.data?.total) || 0);
      setCurrent(nextCurrent);
      setPageSize(nextPageSize);
    } catch (error: any) {
      message.error("加载题库失败，" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="bg-white/50 backdrop-blur-sm rounded-[3rem] p-6 sm:p-10 border border-white shadow-2xl shadow-slate-200/50 space-y-6">
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Title level={4} style={{ margin: 0 }}>
              公开题库浏览
            </Title>
            <Paragraph type="secondary" style={{ marginBottom: 0 }}>
              支持按关键词搜索公开题库，并按最新、更新或标题排序。
            </Paragraph>
          </div>
          <Text type="secondary">共 {total} 个公开题库</Text>
        </div>
        <div className="grid gap-3 xl:grid-cols-[1.8fr_1fr_auto_auto]">
          <Input
            allowClear
            placeholder="搜索题库标题或简介"
            value={filters.searchText}
            onChange={(event) => {
              setFilters((prev) => ({
                ...prev,
                searchText: event.target.value,
              }));
            }}
            onPressEnter={() => void loadData(1, pageSize, filters)}
          />
          <Select
            value={filters.sortKey}
            options={SORT_OPTIONS}
            onChange={(value) => {
              const nextFilters = {
                ...filters,
                sortKey: value,
              };
              setFilters(nextFilters);
              void loadData(1, pageSize, nextFilters);
            }}
          />
          <Button type="primary" loading={loading} onClick={() => void loadData(1, pageSize, filters)}>
            搜索
          </Button>
          <Button
            onClick={() => {
              const nextFilters: FilterState = { sortKey: "createTime_desc" };
              setFilters(nextFilters);
              void loadData(1, pageSize, nextFilters);
            }}
          >
            重置
          </Button>
        </div>
      </div>

      {questionBankList.length ? (
        <QuestionBankList questionBankList={questionBankList} />
      ) : (
        <Card className="rounded-[2rem] border border-dashed border-slate-200 bg-slate-50/60 shadow-none">
          <Empty
            description={filters.searchText ? "没有找到符合条件的公开题库" : "暂时还没有公开题库"}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </Card>
      )}

      {total > pageSize ? (
        <div className="flex justify-end">
          <Pagination
            current={current}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            pageSizeOptions={["12", "24", "36"]}
            onChange={(page, size) => {
              void loadData(page, size, filters);
            }}
          />
        </div>
      ) : null}
    </section>
  );
};

export default BanksExplorer;
