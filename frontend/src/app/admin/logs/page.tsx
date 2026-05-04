"use client";

import React, { useRef } from "react";
import ProTable from "@/components/DynamicProTable";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Button, Card, Space, Tag, Tooltip, Typography, message } from "antd";
import {
  Activity,
  Clock,
  Download,
  FileSearch,
  Globe,
  ShieldAlert,
  Terminal,
  User as UserIcon,
} from "lucide-react";
import request, { buildApiUrl } from "@/libs/request";
import { extractSortParams, formatDateTime } from "@/lib/utils";

const { Text } = Typography;

type AdminLogRecord = {
  id?: number;
  userId?: number;
  userName?: string;
  operation?: string;
  method?: string;
  params?: string;
  ip?: string;
  createTime?: string;
};

type AdminLogQueryParams = {
  current?: number;
  pageSize?: number;
  userName?: string;
  operation?: string;
  method?: string;
  ip?: string;
  startTime?: string;
  endTime?: string;
  sortField?: string;
  sortOrder?: "ascend" | "descend";
};

/**
 * 管理员操作日志页面
 */
export default function AdminLogsPage() {
  const actionRef = useRef<ActionType>();
  const latestQueryRef = useRef<Partial<AdminLogQueryParams>>({});

  const columns: ProColumns<AdminLogRecord>[] = [
    {
      title: "操作者",
      dataIndex: "userName",
      width: 180,
      hideInSearch: true,
      render: (_, record) => (
        <Space>
          <div className="rounded-lg bg-slate-100 p-1.5">
            <UserIcon className="h-4 w-4 text-slate-500" />
          </div>
          <div className="flex flex-col">
            <Text className="font-bold text-slate-700">{record.userName || "未知用户"}</Text>
            <Text type="secondary" className="text-[10px]">
              ID: {record.userId}
            </Text>
          </div>
        </Space>
      ),
    },
    {
      title: "操作描述",
      dataIndex: "operation",
      width: 220,
      ellipsis: true,
      copyable: true,
      hideInSearch: true,
      render: (text) => <span className="font-semibold text-slate-700">{text || "未记录操作描述"}</span>,
    },
    {
      title: "操作方法",
      dataIndex: "method",
      width: 220,
      ellipsis: true,
      hideInSearch: true,
      render: (text) => (
        <Tooltip title={text}>
          <Tag className="max-w-[220px] truncate rounded-lg border-slate-200 bg-slate-50 font-mono text-xs text-slate-500">
            {String(text || "-").split(".").pop()}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: "IP 地址",
      dataIndex: "ip",
      width: 160,
      hideInSearch: true,
      render: (text) => (
        <Space className="font-medium text-slate-500">
          <Globe className="h-3.5 w-3.5 opacity-40" />
          {text || "-"}
        </Space>
      ),
    },
    {
      title: "请求参数",
      dataIndex: "params",
      width: 280,
      ellipsis: true,
      copyable: true,
      hideInSearch: true,
      render: (text) => (
        <Tooltip title={text || "-"}>
          <span className="block max-w-[280px] truncate font-mono text-xs text-slate-500">
            {text || "-"}
          </span>
        </Tooltip>
      ),
    },
    {
      title: "操作时间",
      dataIndex: "createTime",
      valueType: "dateTime",
      sorter: true,
      width: 180,
      hideInSearch: true,
      render: (_, record) => (
        <Space className="text-slate-500">
          <Clock className="h-3.5 w-3.5 opacity-40" />
          {formatDateTime(record.createTime)}
        </Space>
      ),
    },
    {
      title: "操作者",
      dataIndex: "userName",
      hideInTable: true,
    },
    {
      title: "操作描述",
      dataIndex: "operation",
      hideInTable: true,
    },
    {
      title: "方法名",
      dataIndex: "method",
      hideInTable: true,
    },
    {
      title: "IP 地址",
      dataIndex: "ip",
      hideInTable: true,
    },
    {
      title: "操作时间",
      dataIndex: "dateRange",
      valueType: "dateTimeRange",
      hideInTable: true,
      search: {
        transform: (value: [string, string]) => ({
          startTime: value?.[0],
          endTime: value?.[1],
        }),
      },
    },
  ];

  const handleExport = async () => {
    try {
      const response = await fetch(buildApiUrl("/api/admin/log/export"), {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(latestQueryRef.current),
      });
      if (!response.ok) {
        throw new Error("导出失败");
      }
      const blob = await response.blob();
      const contentDisposition = response.headers.get("content-disposition") || "";
      const matched = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
      const fileName = matched?.[1] ? decodeURIComponent(matched[1]) : "admin-operation-log.csv";
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = fileName;
      link.click();
      URL.revokeObjectURL(link.href);
      message.success("日志已导出");
    } catch (error: any) {
      message.error(error?.message || "日志导出失败");
    }
  };

  return (
    <div className="animate-in fade-in slide-in-from-bottom-4 space-y-8 duration-700">
      <Card
        className="relative overflow-hidden rounded-[2.5rem] border border-white bg-white/70 shadow-2xl shadow-slate-200/50 backdrop-blur-xl"
        bodyStyle={{ padding: "3rem" }}
      >
        <div className="absolute right-0 top-0 rotate-12 p-12 opacity-5">
          <Terminal className="h-40 w-40 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-4">
          <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-xs font-black uppercase tracking-widest text-primary">
            <ShieldAlert className="h-3 w-3" />
            Security Audits
          </div>
          <h1 className="text-3xl font-black tracking-tight text-slate-900 sm:text-4xl">操作审计日志</h1>
          <p className="max-w-xl text-lg font-medium text-slate-500">
            实时、全量记录管理员的关键写操作，支持按操作者、操作类型、时间区间快速定位问题，并导出留档。
          </p>
        </div>
      </Card>

      <div className="overflow-hidden rounded-[2.5rem] border border-slate-100 bg-white p-2 pb-12 shadow-xl shadow-slate-200/50 sm:p-6">
        <ProTable<AdminLogRecord>
          headerTitle={
            <div className="flex items-center gap-2 font-black text-slate-800">
              <Activity className="h-5 w-5 text-primary" />
              审计流水
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
              <Button key="export" icon={<Download className="h-4 w-4" />} onClick={handleExport}>
                导出 CSV
              </Button>,
            ],
          }}
          request={async (params, sort) => {
            try {
              const { sortField, sortOrder } = extractSortParams(sort as Record<string, "ascend" | "descend" | null>);
              latestQueryRef.current = {
                userName: params.userName,
                operation: params.operation,
                method: params.method,
                ip: params.ip,
                startTime: params.startTime,
                endTime: params.endTime,
                sortField,
                sortOrder,
              };
              const res: any = await request<any>("/api/admin/log/list/page", {
                method: "POST",
                data: {
                  current: params.current,
                  pageSize: params.pageSize,
                  userName: params.userName,
                  operation: params.operation,
                  method: params.method,
                  ip: params.ip,
                  startTime: params.startTime,
                  endTime: params.endTime,
                  sortField,
                  sortOrder,
                },
              });
              return {
                success: res.code === 0,
                data: res.data?.records || [],
                total: res.data?.total || 0,
              };
            } catch (error: any) {
              message.error(error?.message || "加载操作日志失败");
              return {
                success: false,
                data: [],
                total: 0,
              };
            }
          }}
          columns={columns}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
          scroll={{ x: 1280 }}
          options={{
            density: true,
            fullScreen: true,
            reload: true,
          }}
          cardBordered={false}
        />

        <div className="mt-4 flex items-start gap-2 rounded-2xl bg-slate-50 px-4 py-3 text-xs text-slate-500">
          <FileSearch className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span>导出会沿用当前筛选条件，单次最多导出 1000 条日志，便于排查问题和归档审计记录。</span>
        </div>
      </div>
    </div>
  );
}
