"use client";

import React, { useRef } from "react";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Button, Card, message, Popconfirm, Tag, Typography } from "antd";
import { AlertTriangle, Ban, RefreshCw, ShieldAlert, ShieldCheck } from "lucide-react";
import AdminTableEllipsis from "@/app/admin/components/AdminTableEllipsis";
import ProTable from "@/components/DynamicProTable";
import {
  banUserByAlertUsingPost,
  ignoreAlertUsingPost,
  listAlertByPageUsingPost,
} from "@/api/securityAlertController";
import { extractSortParams, formatDateTime } from "@/lib/utils";

const { Text } = Typography;

const RISK_LEVEL_COLOR_MAP: Record<string, string> = {
  high: "red",
  medium: "orange",
  low: "blue",
};

const RISK_LEVEL_TEXT_MAP: Record<string, string> = {
  high: "高风险",
  medium: "中风险",
  low: "低风险",
};

const STATUS_COLOR_MAP: Record<number, string> = {
  0: "processing",
  1: "error",
  2: "default",
};

const STATUS_TEXT_MAP: Record<number, string> = {
  0: "待处理",
  1: "已封禁",
  2: "已忽略",
};

export default function AdminSecurityPage() {
  const actionRef = useRef<ActionType>();

  const handleIgnore = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在忽略告警...");
    try {
      await ignoreAlertUsingPost({ id });
      message.success("已忽略该告警");
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(error?.message || "忽略告警失败");
    } finally {
      hide();
    }
  };

  const handleBan = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在封禁关联用户...");
    try {
      await banUserByAlertUsingPost({ id });
      message.success("已封禁关联用户");
      actionRef.current?.reload();
    } catch (error: any) {
      message.error(error?.message || "封禁关联用户失败");
    } finally {
      hide();
    }
  };

  const columns: ProColumns<API.SecurityAlert>[] = [
    {
      title: "告警 ID",
      dataIndex: "id",
      width: 90,
      hideInSearch: true,
    },
    {
      title: "用户",
      dataIndex: "userName",
      width: 180,
      render: (_, record) => (
        <div className="flex flex-col">
          <span className="font-bold text-slate-700">{record.userName || "未知用户"}</span>
          <span className="text-xs text-slate-400">用户 ID：{record.userId || "-"}</span>
        </div>
      ),
    },
    {
      title: "关键词",
      dataIndex: "searchText",
      hideInTable: true,
    },
    {
      title: "告警类型",
      dataIndex: "alertType",
      width: 150,
      valueType: "select",
      valueEnum: {
        ACCESS_LIMIT: { text: "访问限流" },
        SEARCH_ABUSE: { text: "搜索滥用" },
        UNKNOWN: { text: "其他" },
      },
      render: (_, record) => (
        <AdminTableEllipsis
          value={record.alertType || "UNKNOWN"}
          className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-600"
        />
      ),
    },
    {
      title: "风险等级",
      dataIndex: "riskLevel",
      width: 120,
      valueType: "select",
      valueEnum: {
        high: { text: "高风险" },
        medium: { text: "中风险" },
        low: { text: "低风险" },
      },
      render: (_, record) => (
        <Tag color={RISK_LEVEL_COLOR_MAP[String(record.riskLevel || "medium")] || "default"} className="whitespace-nowrap rounded-full px-3 py-1 font-bold">
          {RISK_LEVEL_TEXT_MAP[String(record.riskLevel || "medium")] || "未知"}
        </Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 120,
      valueType: "select",
      valueEnum: {
        0: { text: "待处理" },
        1: { text: "已封禁" },
        2: { text: "已忽略" },
      },
      render: (_, record) => (
        <Tag color={STATUS_COLOR_MAP[Number(record.status ?? 0)] || "default"} className="whitespace-nowrap rounded-full px-3 py-1 font-bold">
          {STATUS_TEXT_MAP[Number(record.status ?? 0)] || "未知"}
        </Tag>
      ),
    },
    {
      title: "告警原因",
      dataIndex: "reason",
      width: 260,
      hideInSearch: true,
      ellipsis: true,
      render: (text) => (
        <AdminTableEllipsis value={text} className="font-medium text-slate-700" />
      ),
    },
    {
      title: "详情",
      dataIndex: "detail",
      width: 260,
      hideInSearch: true,
      ellipsis: true,
      render: (text) => (
        <AdminTableEllipsis value={text} className="text-slate-500" />
      ),
    },
    {
      title: "IP",
      dataIndex: "ip",
      width: 140,
      hideInSearch: true,
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      valueType: "dateTime",
      width: 180,
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.createTime),
    },
    {
      title: "处理时间",
      dataIndex: "handleTime",
      valueType: "dateTime",
      width: 180,
      hideInSearch: true,
      render: (_, record) => formatDateTime(record.handleTime),
    },
    {
      title: "处理动作",
      dataIndex: "handleAction",
      width: 160,
      hideInSearch: true,
      ellipsis: true,
      render: (text) => <AdminTableEllipsis value={text} className="text-slate-600" />,
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      width: 210,
      render: (_, record) => {
        const isPending = Number(record.status ?? 0) === 0;
        if (!isPending) {
          return <Text type="secondary">已处理</Text>;
        }
        return (
          <div className="flex flex-wrap gap-2">
            <Button size="small" onClick={() => handleIgnore(record.id)}>
              忽略
            </Button>
            <Popconfirm
              title="确认封禁关联用户？"
              description="这会把该用户角色设为 ban。"
              okText="确认封禁"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => handleBan(record.id)}
            >
              <Button danger size="small" icon={<Ban className="h-3.5 w-3.5" />}>
                封禁用户
              </Button>
            </Popconfirm>
          </div>
        );
      },
    },
  ];

  return (
    <div className="animate-in fade-in slide-in-from-bottom-4 space-y-8 duration-700">
      <Card
        className="relative overflow-hidden rounded-[2.5rem] border border-white bg-white/70 shadow-2xl shadow-slate-200/50 backdrop-blur-xl"
        bodyStyle={{ padding: "3rem" }}
      >
        <div className="absolute right-0 top-0 rotate-12 p-12 opacity-5">
          <ShieldAlert className="h-40 w-40 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-4 py-2 text-xs font-black uppercase tracking-widest text-amber-700">
            <AlertTriangle className="h-3.5 w-3.5" />
            Risk Control
          </div>
          <h1 className="text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">风控面板</h1>
          <p className="max-w-2xl text-lg font-medium text-slate-500">
            这里集中处理异常访问、可疑搜索和触发封控策略的安全告警。你可以直接忽略告警，或一键封禁关联用户。
          </p>
        </div>
      </Card>

      <div className="overflow-hidden rounded-[2.5rem] border border-slate-100 bg-white p-2 pb-12 shadow-xl shadow-slate-200/50 sm:p-6">
        <ProTable<API.SecurityAlert>
          headerTitle={
            <div className="flex items-center gap-2 font-black text-slate-800">
              <ShieldCheck className="h-5 w-5 text-amber-500" />
              风控告警列表
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
              const { sortField, sortOrder } = extractSortParams(sort as Record<string, "ascend" | "descend" | null>);
              const res = await listAlertByPageUsingPost({
                current: params.current,
                pageSize: params.pageSize,
                searchText: params.searchText,
                userName: params.userName,
                alertType: params.alertType,
                riskLevel: params.riskLevel,
                status: params.status,
                sortField,
                sortOrder,
              });
              return {
                data: res.data?.records || [],
                total: Number(res.data?.total || 0),
                success: true,
              };
            } catch (error: any) {
              message.error(error?.message || "加载风控面板数据失败");
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
          scroll={{ x: 1500 }}
          options={false}
        />
      </div>
    </div>
  );
}
