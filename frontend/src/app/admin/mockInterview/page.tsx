"use client";

import React, { useRef } from "react";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Button, Card, message, Popconfirm, Tag, Typography } from "antd";
import { BrainCircuit, Download, FileSearch, RefreshCw, Trash2 } from "lucide-react";
import ProTable from "@/components/DynamicProTable";
import {
  deleteMockInterviewUsingPost,
  downloadMockInterviewReviewUsingGet,
  listMockInterviewByPageUsingPost,
} from "@/api/mockInterviewController";
import { formatDateTime } from "@/lib/utils";

const { Text } = Typography;

const STATUS_MAP: Record<number, { text: string; color: string }> = {
  0: { text: "待开始", color: "orange" },
  1: { text: "进行中", color: "green" },
  2: { text: "已结束", color: "red" },
  3: { text: "已暂停", color: "gold" },
};

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

export default function AdminMockInterviewPage() {
  const actionRef = useRef<ActionType>();

  const normalizeScalar = (value: unknown) => {
    if (Array.isArray(value)) {
      return value[0];
    }
    return value === "" ? undefined : value;
  };

  const buildMockInterviewQueryRequest = (params: Record<string, any>, sort: Record<string, any>) => {
    const sortField = Object.keys(sort || {})[0];
    const sortOrder = sortField ? sort?.[sortField] : undefined;
    return {
      current: Number(params.current) || 1,
      pageSize: Number(params.pageSize) || 10,
      id: normalizeScalar(params.id),
      userId: normalizeScalar(params.userId),
      jobPosition: normalizeScalar(params.jobPosition),
      interviewType: normalizeScalar(params.interviewType),
      difficulty: normalizeScalar(params.difficulty),
      status: normalizeScalar(params.status),
      sortField,
      sortOrder,
    } as API.MockInterviewQueryRequest;
  };

  const handleDelete = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在删除模拟面试记录...");
    try {
      await deleteMockInterviewUsingPost({ id });
      message.success("删除成功");
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(error?.message || "删除失败");
    } finally {
      hide();
    }
  };

  const handleExport = async (id?: string | number) => {
    if (!id) {
      return;
    }
    try {
      const { blob, fileName } = await downloadMockInterviewReviewUsingGet(id);
      downloadBlob(blob, fileName);
      message.success("复盘报告已导出");
    } catch (error: any) {
      message.error(error?.message || "导出复盘失败");
    }
  };

  const columns: ProColumns<API.MockInterview>[] = [
    {
      title: "面试 ID",
      dataIndex: "id",
      width: 90,
      hideInSearch: true,
    },
    {
      title: "岗位",
      dataIndex: "jobPosition",
      ellipsis: true,
      render: (text) => <span className="block max-w-[240px] break-words font-bold leading-6 text-slate-800">{text || "未命名面试"}</span>,
    },
    {
      title: "用户 ID",
      dataIndex: "userId",
      valueType: "digit",
      width: 110,
    },
    {
      title: "面试类型",
      dataIndex: "interviewType",
      valueType: "select",
      render: (text) => <Tag className="rounded-full px-3 py-1">{text || "技术深挖"}</Tag>,
    },
    {
      title: "技术方向",
      dataIndex: "techStack",
      ellipsis: true,
      hideInSearch: true,
      render: (text) => text ? <span className="block max-w-[220px] break-words leading-6 text-slate-700">{text}</span> : <span className="text-slate-300">-</span>,
    },
    {
      title: "难度",
      dataIndex: "difficulty",
      valueType: "select",
      render: (text) => text || <span className="text-slate-300">-</span>,
    },
    {
      title: "状态",
      dataIndex: "status",
      valueType: "select",
      width: 120,
      valueEnum: {
        0: { text: "待开始" },
        1: { text: "进行中" },
        2: { text: "已结束" },
        3: { text: "已暂停" },
      },
      render: (_, record) => {
        const status = STATUS_MAP[Number(record.status ?? 0)] || STATUS_MAP[0];
        return <Tag color={status.color} className="rounded-full px-3 py-1 font-bold">{status.text}</Tag>;
      },
    },
    {
      title: "轮次进度",
      dataIndex: "progress",
      hideInSearch: true,
      width: 120,
      render: (_, record) => `${record.currentRound || 0}/${record.expectedRounds || 5}`,
    },
    {
      title: "工作年限",
      dataIndex: "workExperience",
      hideInSearch: true,
      render: (text) => text || <span className="text-slate-300">不限</span>,
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      valueType: "dateTime",
      width: 180,
      hideInSearch: true,
      sorter: true,
      render: (_, record) => formatDateTime(record.createTime),
    },
    {
      title: "更新时间",
      dataIndex: "updateTime",
      valueType: "dateTime",
      width: 180,
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.updateTime),
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      width: 280,
      render: (_, record) => (
        <div className="flex flex-wrap gap-3">
          <Button
            type="link"
            className="!px-0 font-bold"
            icon={<FileSearch className="h-4 w-4" />}
            onClick={() => window.open(`/mockInterview/chat/${record.id}`, "_blank")}
          >
            查看会话
          </Button>
          <Button
            type="link"
            className="!px-0 font-bold"
            icon={<Download className="h-4 w-4" />}
            onClick={() => handleExport(record.id)}
          >
            导出复盘
          </Button>
          <Popconfirm
            title="确认删除这条模拟面试记录？"
            description="删除后无法恢复，请谨慎操作。"
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(record.id)}
          >
            <Button danger type="link" className="!px-0 font-bold" icon={<Trash2 className="h-4 w-4" />}>
              删除
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div className="animate-in fade-in slide-in-from-bottom-4 space-y-8 duration-700">
      <Card
        className="relative overflow-hidden rounded-[2.5rem] border border-white bg-white/70 shadow-2xl shadow-slate-200/50 backdrop-blur-xl"
        bodyStyle={{ padding: "3rem" }}
      >
        <div className="absolute right-0 top-0 rotate-12 p-12 opacity-5">
          <BrainCircuit className="h-40 w-40 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 rounded-full bg-violet-50 px-4 py-2 text-xs font-black uppercase tracking-widest text-violet-700">
            <BrainCircuit className="h-3.5 w-3.5" />
            AI Interview Ops
          </div>
          <h1 className="text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">模拟面试管理</h1>
          <p className="max-w-2xl text-lg font-medium text-slate-500">
            在这里统一查看平台所有 AI 模拟面试记录，按岗位、类型、状态筛选，并支持导出复盘或删除异常记录。
          </p>
        </div>
      </Card>

      <div className="overflow-hidden rounded-[2.5rem] border border-slate-100 bg-white p-2 pb-12 shadow-xl shadow-slate-200/50 sm:p-6">
        <ProTable<API.MockInterview>
          headerTitle={
            <div className="flex items-center gap-2 font-black text-slate-800">
              <BrainCircuit className="h-5 w-5 text-violet-500" />
              面试记录列表
            </div>
          }
          actionRef={actionRef}
          rowKey="id"
          search={{
            labelWidth: 88,
            defaultCollapsed: false,
          }}
          toolbar={{
            actions: [
              <Button key="refresh" icon={<RefreshCw className="h-4 w-4" />} onClick={() => actionRef.current?.reload()}>
                刷新
              </Button>,
            ],
          }}
          columns={columns}
          request={async (params, sort) => {
            try {
              const res = await listMockInterviewByPageUsingPost(
                buildMockInterviewQueryRequest(params, sort as Record<string, any>),
              );
              return {
                data: res.data?.records || [],
                total: Number(res.data?.total || 0),
                success: true,
              };
            } catch (error: any) {
              message.error(error?.message || "加载面试管理数据失败");
              return {
                data: [],
                total: 0,
                success: false,
              };
            }
          }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
          scroll={{ x: 1450 }}
          options={false}
        />
      </div>
    </div>
  );
}
