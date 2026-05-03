import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Empty, List, Segmented, Tag, Typography, message } from "antd";
import Link from "next/link";
import { listMyQuestionHistoryByPageUsingGet } from "@/api/userQuestionHistoryController";
import TagList from "@/components/TagList";
import dayjs from "dayjs";
import { QUESTION_DIFFICULTY_COLOR_MAP } from "@/constants/question";

interface Props {
  limit?: number;
}

const { Text } = Typography;

const STATUS_FILTER_OPTIONS = [
  { label: "全部", value: "all" },
  { label: "浏览", value: 0 },
  { label: "掌握", value: 1 },
  { label: "困难", value: 2 },
] as const;

const STATUS_TEXT_MAP: Record<number, string> = {
  0: "浏览",
  1: "掌握",
  2: "困难",
};

const STATUS_COLOR_MAP: Record<number, string> = {
  0: "default",
  1: "success",
  2: "volcano",
};

type StatusFilterValue = (typeof STATUS_FILTER_OPTIONS)[number]["value"];

/**
 * 我的刷题记录列表
 */
const LearningHistoryList: React.FC<Props> = ({ limit }) => {
  const [dataList, setDataList] = useState<API.UserQuestionHistoryVO[]>([]);
  const [total, setTotal] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>("all");
  const [params, setParams] = useState({ current: 1, pageSize: limit || 12 });

  const requestParams = useMemo(
    () => ({
      ...params,
      status: statusFilter === "all" ? undefined : Number(statusFilter),
    }),
    [params, statusFilter],
  );

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listMyQuestionHistoryByPageUsingGet(requestParams);
      const pageData = res.data as API.PageUserQuestionHistoryVO_ | undefined;
      setDataList(pageData?.records || []);
      setTotal(Number(pageData?.total) || 0);
    } catch (e: any) {
      message.error("获取数据失败，" + e.message);
    } finally {
      setLoading(false);
    }
  }, [requestParams]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  return (
    <div className="space-y-6">
      {!limit ? (
        <div className="rounded-[2rem] border border-slate-100 bg-slate-50/70 px-5 py-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="text-base font-black text-slate-900">刷题轨迹</div>
              <div className="mt-2 text-sm leading-6 text-slate-500">
                可以按浏览、掌握和困难状态筛选你的做题记录，回看起来会更顺手。
              </div>
            </div>
            <Segmented
              options={STATUS_FILTER_OPTIONS as any}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value as StatusFilterValue);
                setParams((current) => ({ ...current, current: 1 }));
              }}
            />
          </div>
        </div>
      ) : null}

      <List
        loading={loading}
        itemLayout="horizontal"
        dataSource={dataList}
        locale={{
          emptyText: <Empty description="还没有刷题记录，去做几道题试试看" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        pagination={limit ? false : {
          onChange: (page) => setParams({ ...params, current: page }),
          current: params.current,
          pageSize: params.pageSize,
          total: total,
        }}
        renderItem={(item) => {
          const question = item.question;
          if (!question) return null;
          return (
            <List.Item
              extra={
                <div style={{ color: "rgba(0, 0, 0, 0.45)" }} className="text-xs text-right">
                  <div>练习时间</div>
                  <div className="mt-1 font-medium text-slate-500">
                    {dayjs(item.updateTime).format("YYYY-MM-DD HH:mm")}
                  </div>
                </div>
              }
            >
              <List.Item.Meta
                title={<Link href={`/question/${question.id}`} className="font-semibold text-slate-700 hover:text-primary">{question.title}</Link>}
                description={
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <Tag color={STATUS_COLOR_MAP[Number(item.status)] || "default"} className="m-0 rounded-full px-3 py-1 font-bold">
                        {STATUS_TEXT_MAP[Number(item.status)] || "浏览"}
                      </Tag>
                      {question.difficulty ? (
                        <Tag
                          color={QUESTION_DIFFICULTY_COLOR_MAP[question.difficulty] || "default"}
                          className="m-0 rounded-full px-3 py-1 font-bold"
                        >
                          难度：{question.difficulty}
                        </Tag>
                      ) : null}
                    </div>
                    <TagList tagList={question.tagList} />
                    {!limit ? (
                      <Text className="text-sm text-slate-500">
                        从这里可以快速回到原题，继续复盘、改状态或者补充笔记。
                      </Text>
                    ) : null}
                  </div>
                }
              />
            </List.Item>
          );
        }}
      />
    </div>
  );
};

export default LearningHistoryList;
