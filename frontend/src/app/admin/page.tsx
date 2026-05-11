"use client";

import React, { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { Card, Row, Col, Typography, Button, Tag, List, message, Skeleton, Popconfirm } from "antd";
import {
  Users,
  BookOpen,
  Database,
  ChevronRight,
  Activity,
  ShieldCheck,
  TrendingUp,
  Sparkles,
  PieChart as PieChartIcon,
  BarChart3,
  MessageSquareText,
  BrainCircuit,
  Ban,
  Wand2,
  Search,
  AlertTriangle,
  MousePointerClick,
  Repeat2
} from "lucide-react";
import Link from "next/link";
import dayjs from "dayjs";
import AdminTableEllipsis from "@/app/admin/components/AdminTableEllipsis";
import { getDashboardOverviewUsingGet } from "@/api/adminDashboardController";
import { banUserByAlertUsingPost, ignoreAlertUsingPost } from "@/api/securityAlertController";

const { Title, Text, Paragraph } = Typography;

const ReactECharts = dynamic(() => import("echarts-for-react"), {
  ssr: false,
  loading: () => <Skeleton active paragraph={{ rows: 8 }} />,
});

interface DashboardData {
  overview?: Record<string, any>;
  todayStats?: Record<string, any>;
  trend?: Record<string, any>;
  growthComparison?: Record<string, any>;
  retentionAnalytics?: Record<string, any>;
  searchAnalytics?: Record<string, any>;
  recommendationAnalytics?: Record<string, any>;
  geoDistribution?: Record<string, any>;
  tagDistribution?: any[];
  questionHealth?: any[];
  riskAlerts?: any[];
  recentOperations?: any[];
}

/**
 * 管理员后台首页 - 数据驾驶舱
 */
export default function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState<DashboardData>({});
  const [loading, setLoading] = useState(true);

  const fetchDashboard = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getDashboardOverviewUsingGet();
      setDashboard((res.data || {}) as DashboardData);
    } catch (error: any) {
      message.error("获取驾驶舱数据失败：" + (error?.message || "请稍后重试"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboard();
  }, [fetchDashboard]);

  const overview = dashboard.overview || {};
  const todayStats = dashboard.todayStats || {};
  const trend = dashboard.trend || {};
  const growthComparison = dashboard.growthComparison || {};
  const retentionAnalytics = dashboard.retentionAnalytics || {};
  const searchAnalytics = dashboard.searchAnalytics || {};
  const searchTrend = searchAnalytics.trend || {};
  const recommendationAnalytics = dashboard.recommendationAnalytics || {};
  const recommendationTrend = recommendationAnalytics.trend || {};
  const recommendationSourceBreakdown = recommendationAnalytics.sourceBreakdown || [];
  const geoDistribution = dashboard.geoDistribution || {};
  const geoCityList = geoDistribution.cityList || [];
  const tagDistribution = dashboard.tagDistribution || [];
  const questionHealth = dashboard.questionHealth || [];
  const riskAlerts = dashboard.riskAlerts || [];
  const recentOperations = dashboard.recentOperations || [];

  const handleIgnoreAlert = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在忽略告警...");
    try {
      await ignoreAlertUsingPost({ id });
      message.success("已忽略该告警");
      fetchDashboard();
    } catch (error: any) {
      message.error("操作失败：" + (error?.message || "请稍后重试"));
    } finally {
      hide();
    }
  };

  const handleBanAlertUser = async (id?: string | number) => {
    if (!id) {
      return;
    }
    const hide = message.loading("正在封禁关联用户...");
    try {
      await banUserByAlertUsingPost({ id });
      message.success("已封禁关联用户并处理告警");
      fetchDashboard();
    } catch (error: any) {
      message.error("封禁失败：" + (error?.message || "请稍后重试"));
    } finally {
      hide();
    }
  };

  const searchTrendOption = {
    grid: { top: 20, right: 20, bottom: 40, left: 40 },
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    xAxis: {
      type: "category",
      data: searchTrend.dates || [],
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", fontWeight: "bold" },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { type: "dashed", color: "#f1f5f9" } },
      axisLabel: { color: "#64748b" },
    },
    series: [
      {
        name: "搜索次数",
        data: searchTrend.searchTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#0f766e" },
        itemStyle: { color: "#0f766e" },
      },
      {
        name: "无结果次数",
        data: searchTrend.zeroResultTrend || [],
        type: "bar",
        barWidth: 18,
        itemStyle: { color: "#ef4444", borderRadius: [8, 8, 0, 0] },
      },
    ],
  };

  const geoOption = {
    grid: { top: 20, right: 20, bottom: 40, left: 50 },
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    xAxis: {
      type: "category",
      data: geoCityList.map((item: any) => item.city),
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", rotate: 20, fontWeight: "bold" },
    },
    yAxis: [
      {
        type: "value",
        name: "用户数",
        splitLine: { lineStyle: { type: "dashed", color: "#f1f5f9" } },
        axisLabel: { color: "#64748b" },
      },
      {
        type: "value",
        name: "人均练习",
        axisLabel: { color: "#64748b" },
      },
    ],
    series: [
      {
        name: "用户数",
        data: geoCityList.map((item: any) => item.userCount || 0),
        type: "bar",
        barWidth: 18,
        itemStyle: { color: "#2563eb", borderRadius: [8, 8, 0, 0] },
      },
      {
        name: "刷题热度",
        data: geoCityList.map((item: any) => item.practiceCount || 0),
        type: "bar",
        barWidth: 18,
        itemStyle: { color: "#14b8a6", borderRadius: [8, 8, 0, 0] },
      },
      {
        name: "人均练习",
        data: geoCityList.map((item: any) => item.avgPracticeCount || 0),
        yAxisIndex: 1,
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#f59e0b" },
        itemStyle: { color: "#f59e0b" },
      },
    ],
  };

  const statCards = [
    {
      title: "平台用户量",
      value: overview.userTotal || 0,
      icon: <Users className="h-6 w-6 text-blue-500" />,
      desc: "累计注册用户",
      href: "/admin/user",
    },
    {
      title: "题库总数",
      value: overview.bankTotal || 0,
      icon: <BookOpen className="h-6 w-6 text-emerald-500" />,
      desc: "已建立题库",
      href: "/admin/bank",
    },
    {
      title: "题目总量",
      value: overview.questionTotal || 0,
      icon: <Database className="h-6 w-6 text-orange-500" />,
      desc: "面试题资源池",
      href: "/admin/question",
    },
    {
      title: "评论总量",
      value: overview.commentTotal || 0,
      icon: <MessageSquareText className="h-6 w-6 text-cyan-500" />,
      desc: "社区互动总量",
      href: "/admin/question",
    },
    {
      title: "模拟面试量",
      value: overview.mockInterviewTotal || 0,
      icon: <BrainCircuit className="h-6 w-6 text-violet-500" />,
      desc: "AI 面试发起次数",
      href: "/admin/mockInterview",
    },
    {
      title: "封禁用户数",
      value: overview.bannedUserTotal || 0,
      icon: <Ban className="h-6 w-6 text-rose-500" />,
      desc: "当前封禁账号",
      href: "/admin/user",
    },
    {
      title: "待处理告警",
      value: overview.pendingRiskAlertTotal || 0,
      icon: <AlertTriangle className="h-6 w-6 text-amber-500" />,
      desc: "风控面板待处理",
      href: "/admin/security",
    },
  ];

  const heroHighlights = [
    {
      label: "今日活跃",
      value: todayStats.todayActiveUserCount || 0,
      hint: "活跃用户",
    },
    {
      label: "待处理告警",
      value: overview.pendingRiskAlertTotal || 0,
      hint: "风险事件",
    },
    {
      label: "推荐点击率",
      value: `${recommendationAnalytics.clickThroughRate || 0}%`,
      hint: "综合效果",
    },
    {
      label: "搜索次数",
      value: searchAnalytics.todaySearchCount || 0,
      hint: "今日检索",
    },
  ];

  const trendOption = {
    grid: { top: 20, right: 20, bottom: 40, left: 40 },
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    xAxis: {
      type: "category",
      data: trend.dates || [],
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", fontWeight: "bold" },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { type: "dashed", color: "#f1f5f9" } },
      axisLabel: { color: "#64748b" },
    },
    series: [
      {
        name: "新增用户",
        data: trend.registerTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#2563eb" },
        itemStyle: { color: "#2563eb" },
      },
      {
        name: "刷题活跃",
        data: trend.practiceTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#16a34a" },
        itemStyle: { color: "#16a34a" },
      },
      {
        name: "社区评论",
        data: trend.commentTrend || [],
        type: "bar",
        barWidth: 18,
        itemStyle: { color: "#f59e0b", borderRadius: [8, 8, 0, 0] },
      },
    ],
  };

  const tagOption = {
    tooltip: { trigger: "item" },
    legend: { bottom: "0%", left: "center", icon: "circle" },
    series: [
      {
        name: "标签占比",
        type: "pie",
        radius: ["40%", "70%"],
        itemStyle: { borderRadius: 10, borderColor: "#fff", borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: "bold" } },
        data: tagDistribution,
      },
    ],
  };

  const retentionOption = {
    grid: { top: 20, right: 20, bottom: 40, left: 40 },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      data: retentionAnalytics.dates || [],
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", fontWeight: "bold" },
    },
    yAxis: {
      type: "value",
      axisLabel: {
        color: "#64748b",
        formatter: "{value}%",
      },
      splitLine: { lineStyle: { type: "dashed", color: "#f1f5f9" } },
      max: 100,
    },
    series: [
      {
        name: "次日留存",
        data: retentionAnalytics.nextDayRetentionTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#8b5cf6" },
        itemStyle: { color: "#8b5cf6" },
        areaStyle: { color: "rgba(139, 92, 246, 0.12)" },
      },
    ],
  };

  const recommendationTrendOption = {
    grid: { top: 20, right: 20, bottom: 40, left: 40 },
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    xAxis: {
      type: "category",
      data: recommendationTrend.dates || [],
      axisLine: { lineStyle: { color: "#e2e8f0" } },
      axisLabel: { color: "#64748b", fontWeight: "bold" },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { type: "dashed", color: "#f1f5f9" } },
      axisLabel: { color: "#64748b" },
    },
    series: [
      {
        name: "曝光",
        data: recommendationTrend.exposureTrend || [],
        type: "bar",
        barWidth: 18,
        itemStyle: { color: "#2563eb", borderRadius: [8, 8, 0, 0] },
      },
      {
        name: "点击",
        data: recommendationTrend.clickTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#14b8a6" },
        itemStyle: { color: "#14b8a6" },
      },
      {
        name: "练习",
        data: recommendationTrend.practiceTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#8b5cf6" },
        itemStyle: { color: "#8b5cf6" },
      },
      {
        name: "掌握",
        data: recommendationTrend.masteredTrend || [],
        type: "line",
        smooth: true,
        lineStyle: { width: 3, color: "#f97316" },
        itemStyle: { color: "#f97316" },
      },
    ],
  };

  return (
    <div className="space-y-10 animate-in fade-in slide-in-from-bottom-4 duration-700">
      <section className="bg-white/80 backdrop-blur-xl rounded-[3rem] p-8 sm:p-12 text-slate-900 relative overflow-hidden shadow-2xl shadow-slate-200/50 border border-slate-200/60">
        <div className="absolute top-0 right-0 p-12 opacity-10 rotate-12">
          <Sparkles className="h-32 w-32 text-primary" />
        </div>
        <div className="relative z-10 grid gap-8 xl:grid-cols-[minmax(0,1fr)_380px] xl:items-end">
          <div className="space-y-6 max-w-3xl">
            <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-xs">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              Platform Overview
            </div>
            <div className="space-y-4">
              <Title level={1} className="!text-slate-900 !font-black !m-0 !text-4xl sm:!text-5xl tracking-tight">
                后台数据驾驶舱
              </Title>
              <Paragraph className="!mb-0 text-slate-500 text-base sm:text-lg font-medium leading-8">
                直接查看增长、活跃、推荐、面试和风控信号，把今天最值得处理的重点先拎出来。
              </Paragraph>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-4">
              {heroHighlights.map((item) => (
                <div
                  key={item.label}
                  className="rounded-[1.75rem] border border-slate-200/70 bg-white/80 px-4 py-4 shadow-sm shadow-slate-200/40"
                >
                  <div className="text-[11px] font-black uppercase tracking-[0.18em] text-slate-400">{item.label}</div>
                  <div className="mt-3 text-2xl font-black tracking-tight text-slate-900">{item.value}</div>
                  <div className="mt-1 text-xs font-semibold text-slate-400">{item.hint}</div>
                </div>
              ))}
            </div>

            <div className="flex flex-wrap items-center gap-3 pt-2 text-sm text-slate-400">
              <span className="inline-flex items-center gap-2 rounded-full bg-slate-100/80 px-4 py-2 font-semibold text-slate-500">
                <ShieldCheck className="h-4 w-4 text-slate-400" />
                后台入口已收纳到左侧导航
              </span>
              <button
                type="button"
                onClick={fetchDashboard}
                className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white/80 px-4 py-2 font-semibold text-slate-500 transition hover:border-primary/30 hover:text-primary"
              >
                <Sparkles className="h-4 w-4" />
                刷新数据
              </button>
            </div>
          </div>

          <div className="rounded-[2.25rem] border border-slate-200/70 bg-gradient-to-br from-slate-50 via-white to-sky-50 p-6 text-slate-900 shadow-xl shadow-slate-200/50">
            <div className="flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.22em] text-slate-400">
              <BarChart3 className="h-4 w-4" />
              今日速览
            </div>
            <div className="mt-4 space-y-4">
              <div className="rounded-[1.5rem] border border-slate-200/80 bg-white/85 px-4 py-4 shadow-sm shadow-slate-100">
                <div className="text-sm font-bold text-slate-600">新增注册与活跃</div>
                <div className="mt-2 flex items-end justify-between gap-4">
                  <div>
                    <div className="text-3xl font-black tracking-tight">{todayStats.todayRegisterCount || 0}</div>
                    <div className="text-xs text-slate-400">今日新增用户</div>
                  </div>
                  <div className="text-right">
                    <div className="text-xl font-black">{todayStats.todayActiveUserCount || 0}</div>
                    <div className="text-xs text-slate-400">今日活跃用户</div>
                  </div>
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-[1.4rem] border border-slate-200/80 bg-white/80 px-4 py-4 shadow-sm shadow-slate-100">
                  <div className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">推荐掌握转化</div>
                  <div className="mt-2 text-2xl font-black">{recommendationAnalytics.masteredConversionRate || 0}%</div>
                </div>
                <div className="rounded-[1.4rem] border border-slate-200/80 bg-white/80 px-4 py-4 shadow-sm shadow-slate-100">
                  <div className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">今日搜索量</div>
                  <div className="mt-2 text-2xl font-black">{searchAnalytics.todaySearchCount || 0}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="flex items-center justify-between mb-8 px-2">
          <div className="flex items-center gap-3">
            <TrendingUp className="h-5 w-5 text-primary" />
            <h2 className="text-2xl font-black text-slate-800 tracking-tight">核心资源概览</h2>
          </div>
          <Tag color="blue">真实统计</Tag>
        </div>
        <Row gutter={[24, 24]}>
          {statCards.map((card) => (
            <Col xs={24} md={12} xl={8} key={card.title}>
              <Link href={card.href}>
                <Card
                  className="group hover:border-primary/30 transition-all duration-300 rounded-[2.5rem] border-slate-100 shadow-xl shadow-slate-200/50 overflow-hidden"
                  bodyStyle={{ padding: "2rem" }}
                >
                  {loading ? (
                    <Skeleton active paragraph={{ rows: 2 }} />
                  ) : (
                    <>
                      <div className="flex justify-between items-start mb-6">
                        <div className="p-4 rounded-2xl bg-slate-50 group-hover:bg-primary/10 transition-colors">
                          {card.icon}
                        </div>
                        <ChevronRight className="h-5 w-5 text-slate-300 group-hover:text-primary group-hover:translate-x-1 transition-all" />
                      </div>
                      <div className="text-slate-500 font-bold mb-2">{card.title}</div>
                      <div className="text-4xl font-black tracking-tight text-slate-900">{card.value}</div>
                      <Text type="secondary" className="text-xs font-medium mt-4 block opacity-70">{card.desc}</Text>
                    </>
                  )}
                </Card>
              </Link>
            </Col>
          ))}
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={16}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 p-6 sm:p-10"
              title={<div className="flex items-center gap-2 font-black text-lg"><BarChart3 className="h-5 w-5 text-primary" /> 近 7 日平台趋势</div>}
              extra={<Tag color="purple" bordered={false} className="font-bold">新增 / 活跃 / 评论</Tag>}
            >
              {loading ? <Skeleton active paragraph={{ rows: 8 }} /> : <ReactECharts option={trendOption} style={{ height: "350px" }} />}
            </Card>
          </Col>
          <Col xs={24} lg={8}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 p-6 sm:p-10"
              title={<div className="flex items-center gap-2 font-black text-lg"><PieChartIcon className="h-5 w-5 text-emerald-500" /> 热门标签分布</div>}
              extra={<Tag color="green" bordered={false} className="font-bold">{overview.tagTotal || 0} 个标签</Tag>}
            >
              {loading ? <Skeleton active paragraph={{ rows: 8 }} /> : <ReactECharts option={tagOption} style={{ height: "350px" }} />}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={10}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><TrendingUp className="h-5 w-5 text-emerald-500" /> 增长对比</div>}
              extra={<Tag color="green" bordered={false}>周环比 / 月环比 / 同比</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : (
                <div className="grid grid-cols-1 gap-4">
                  {[
                    {
                      label: "本周新增用户",
                      value: growthComparison.currentWeekRegisterCount || 0,
                      compareText: `上周 ${growthComparison.previousWeekRegisterCount || 0}`,
                      rate: growthComparison.weekOverWeekRegisterRate || 0,
                    },
                    {
                      label: "本月活跃用户",
                      value: growthComparison.currentMonthActiveUserCount || 0,
                      compareText: `上月 ${growthComparison.previousMonthActiveUserCount || 0}`,
                      rate: growthComparison.monthOverMonthActiveRate || 0,
                    },
                    {
                      label: "本月新增用户",
                      value: growthComparison.currentMonthRegisterCount || 0,
                      compareText: `去年同期 ${growthComparison.lastYearMonthRegisterCount || 0}`,
                      rate: growthComparison.yearOverYearRegisterRate || 0,
                    },
                  ].map((item) => (
                    <div key={item.label} className="rounded-2xl border border-slate-100 bg-slate-50/70 px-5 py-4">
                      <div className="text-xs font-black uppercase tracking-wider text-slate-400">{item.label}</div>
                      <div className="mt-3 flex items-end justify-between gap-4">
                        <div className="text-3xl font-black text-slate-900">{item.value}</div>
                        <Tag color={Number(item.rate) >= 0 ? "success" : "error"} className="m-0 rounded-full px-3 py-1">
                          {Number(item.rate) >= 0 ? "+" : ""}{item.rate}%
                        </Tag>
                      </div>
                      <div className="mt-2 text-sm text-slate-500">{item.compareText}</div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><Repeat2 className="h-5 w-5 text-violet-500" /> 用户留存分析</div>}
              extra={<Tag color="purple" bordered={false}>次日 / 3 日 / 7 日</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : (
                <div className="space-y-8">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    {[
                      { label: "次日留存", value: retentionAnalytics.nextDayRetention || 0, color: "bg-violet-50 text-violet-700" },
                      { label: "3 日留存", value: retentionAnalytics.threeDayRetention || 0, color: "bg-blue-50 text-blue-700" },
                      { label: "7 日留存", value: retentionAnalytics.sevenDayRetention || 0, color: "bg-emerald-50 text-emerald-700" },
                    ].map((item) => (
                      <div key={item.label} className={`rounded-2xl border border-slate-100 px-5 py-4 ${item.color}`}>
                        <div className="text-xs font-black uppercase tracking-wider opacity-70">{item.label}</div>
                        <div className="mt-3 text-3xl font-black tracking-tight">{item.value}%</div>
                      </div>
                    ))}
                  </div>
                  <ReactECharts option={retentionOption} style={{ height: "260px" }} />
                </div>
              )}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={15}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 p-6 sm:p-10 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><Users className="h-5 w-5 text-sky-600" /> 地理热度统计</div>}
              extra={<Tag color="blue" bordered={false} className="font-bold">基于用户资料中的城市字段</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : geoCityList.length ? (
                <div className="space-y-8">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    {[
                      { label: "覆盖城市数", value: geoDistribution.cityCount || 0, color: "bg-sky-50 text-sky-700" },
                      { label: "已识别城市用户", value: geoDistribution.filledUserCount || 0, color: "bg-emerald-50 text-emerald-700" },
                      { label: "识别覆盖率", value: `${geoDistribution.coverageRate || 0}%`, color: "bg-amber-50 text-amber-700" },
                    ].map((item) => (
                      <div key={item.label} className={`rounded-2xl border border-slate-100 px-5 py-4 ${item.color}`}>
                        <div className="text-xs font-black uppercase tracking-wider opacity-70">{item.label}</div>
                        <div className="mt-3 text-3xl font-black tracking-tight">{item.value}</div>
                      </div>
                    ))}
                  </div>
                  <ReactECharts option={geoOption} style={{ height: "340px" }} />
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-slate-200 px-6 py-16 text-center text-slate-400">
                  目前还没有足够的城市数据。让用户在个人资料中补充所在城市后，这里会自动形成地区热度统计。
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={9}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><TrendingUp className="h-5 w-5 text-emerald-500" /> 城市活跃榜</div>}
              extra={<Tag color="green" bordered={false}>用户数 / 刷题量 / 人均练习</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : geoCityList.length ? (
                <List
                  dataSource={geoCityList}
                  renderItem={(item: any, index) => (
                    <List.Item>
                      <div className="flex w-full flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div className="min-w-0 flex items-center gap-3">
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-primary/15 bg-primary/10 text-sm font-black text-primary">
                            {index + 1}
                          </div>
                          <div className="min-w-0">
                            <AdminTableEllipsis value={item.city} className="font-semibold text-slate-800" />
                            <div className="text-xs text-slate-500 mt-1">用户 {item.userCount || 0} 人</div>
                          </div>
                        </div>
                        <div className="shrink-0 text-left sm:text-right">
                          <Tag color="blue" className="whitespace-nowrap">{item.practiceCount || 0} 次练习</Tag>
                          <div className="text-xs text-slate-500 mt-1">人均 {item.avgPracticeCount || 0}</div>
                        </div>
                      </div>
                    </List.Item>
                  )}
                />
              ) : (
                <div className="py-16 text-center text-slate-400">等待城市数据沉淀中</div>
              )}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={14}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 p-6 sm:p-10 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><MousePointerClick className="h-5 w-5 text-cyan-600" /> 推荐效果分析</div>}
              extra={<Tag color="cyan" bordered={false}>曝光 / 点击 / 练习 / 掌握</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : (
                <div className="space-y-8">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    {[
                      { label: "累计曝光", value: recommendationAnalytics.totalExposureCount || 0, color: "bg-cyan-50 text-cyan-700" },
                      { label: "累计点击", value: recommendationAnalytics.totalClickCount || 0, color: "bg-emerald-50 text-emerald-700" },
                      { label: "点击率", value: `${recommendationAnalytics.clickThroughRate || 0}%`, color: "bg-blue-50 text-blue-700" },
                      { label: "练习转化", value: `${recommendationAnalytics.practiceConversionRate || 0}%`, color: "bg-violet-50 text-violet-700" },
                      { label: "收藏转化", value: `${recommendationAnalytics.favourConversionRate || 0}%`, color: "bg-amber-50 text-amber-700" },
                      { label: "掌握转化", value: `${recommendationAnalytics.masteredConversionRate || 0}%`, color: "bg-rose-50 text-rose-700" },
                    ].map((item) => (
                      <div key={item.label} className={`rounded-2xl border border-slate-100 px-5 py-4 ${item.color}`}>
                        <div className="text-xs font-black uppercase tracking-wider opacity-70">{item.label}</div>
                        <div className="mt-3 text-3xl font-black tracking-tight">{item.value}</div>
                      </div>
                    ))}
                  </div>
                  <ReactECharts option={recommendationTrendOption} style={{ height: "280px" }} />
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><Sparkles className="h-5 w-5 text-amber-500" /> 推荐来源拆解</div>}
              extra={<Tag color="gold" bordered={false}>混合推荐效果</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 8 }} />
              ) : (
                <List
                  dataSource={recommendationSourceBreakdown}
                  locale={{ emptyText: "当前还没有推荐效果数据" }}
                  renderItem={(item: any) => (
                    <List.Item>
                      <div className="w-full flex items-center justify-between gap-4">
                        <div className="min-w-0">
                          <div className="font-semibold text-slate-800">{item.source || "unknown"}</div>
                          <div className="mt-1 text-xs text-slate-500">
                            曝光 {item.exposureCount || 0} 次 · 点击 {item.clickCount || 0} 次 · 练习 {item.practiceCount || 0} 次
                          </div>
                        </div>
                        <div className="flex flex-col items-end gap-2">
                          <Tag color="processing" className="m-0 whitespace-nowrap">
                            CTR {item.clickThroughRate || 0}%
                          </Tag>
                          <Tag color="purple" className="m-0 whitespace-nowrap">
                            掌握转化 {item.masteredConversionRate || 0}%
                          </Tag>
                        </div>
                      </div>
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={14}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 p-6 sm:p-10 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><Search className="h-5 w-5 text-cyan-600" /> 搜索效果统计</div>}
              extra={<Tag color="cyan" bordered={false} className="font-bold">热搜词 / 无结果率</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 9 }} />
              ) : (
                <div className="space-y-8">
                  <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
                    {[
                      { label: "累计搜索", value: searchAnalytics.totalSearchCount || 0, color: "bg-cyan-50 text-cyan-700" },
                      { label: "今日搜索", value: searchAnalytics.todaySearchCount || 0, color: "bg-blue-50 text-blue-700" },
                      { label: "独立关键词", value: searchAnalytics.distinctKeywordCount || 0, color: "bg-emerald-50 text-emerald-700" },
                      { label: "无结果率", value: `${searchAnalytics.zeroResultRate || 0}%`, color: "bg-rose-50 text-rose-700" },
                    ].map((item) => (
                      <div key={item.label} className={`rounded-2xl border border-slate-100 px-5 py-4 ${item.color}`}>
                        <div className="text-xs font-black uppercase tracking-wider opacity-70">{item.label}</div>
                        <div className="mt-3 text-3xl font-black tracking-tight">{item.value}</div>
                      </div>
                    ))}
                  </div>
                  <ReactECharts option={searchTrendOption} style={{ height: "300px" }} />
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><AlertTriangle className="h-5 w-5 text-amber-500" /> 热搜与空结果关键词</div>}
              extra={<Tag color="orange" bordered={false}>搜索治理优先级</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 10 }} />
              ) : (
                <div className="space-y-8">
                  <div>
                    <div className="mb-4 flex items-center justify-between">
                      <Text className="font-bold text-slate-700">Top 热搜关键词</Text>
                      <Tag color="blue">{(searchAnalytics.topKeywords || []).length} 项</Tag>
                    </div>
                    <div className="space-y-3">
                      {(searchAnalytics.topKeywords || []).slice(0, 8).map((item: any, index: number) => (
                        <div key={`${item.keyword}-${index}`} className="flex flex-col gap-3 rounded-2xl border border-slate-100 bg-slate-50/70 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                          <div className="min-w-0">
                            <AdminTableEllipsis value={item.keyword} className="font-semibold text-slate-800" />
                            <div className="text-xs text-slate-500 mt-1">
                              平均命中 {item.avgResultCount || 0} 条
                            </div>
                          </div>
                          <Tag color="geekblue" className="m-0 whitespace-nowrap self-start sm:self-auto">搜索 {item.count || 0} 次</Tag>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div>
                    <div className="mb-4 flex items-center justify-between">
                      <Text className="font-bold text-slate-700">无结果关键词</Text>
                      <Tag color="red">{(searchAnalytics.zeroResultKeywords || []).length} 项</Tag>
                    </div>
                    {(searchAnalytics.zeroResultKeywords || []).length ? (
                      <div className="flex flex-wrap gap-3">
                        {(searchAnalytics.zeroResultKeywords || []).slice(0, 10).map((item: any, index: number) => (
                          <Tag key={`${item.keyword}-empty-${index}`} color="error" className="m-0 max-w-[180px] truncate whitespace-nowrap rounded-full px-3 py-1">
                            {item.keyword} · {item.count || 0}
                          </Tag>
                        ))}
                      </div>
                    ) : (
                      <Text type="secondary">当前还没有无结果关键词记录</Text>
                    )}
                  </div>
                </div>
              )}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Row gutter={[24, 24]}>
          <Col xs={24} lg={10}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><Activity className="h-5 w-5 text-primary" /> 今日运营快照</div>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 6 }} />
              ) : (
                <div className="grid grid-cols-1 gap-4">
                  {[
                    { label: "今日新增用户", value: todayStats.todayRegisterCount || 0, color: "blue" },
                    { label: "7 日新增用户", value: todayStats.sevenDayRegisterCount || 0, color: "cyan" },
                    { label: "今日刷题次数", value: todayStats.todayPracticeCount || 0, color: "green" },
                    { label: "7 日活跃用户", value: todayStats.sevenDayActiveUserCount || 0, color: "gold" },
                    { label: "今日评论数", value: todayStats.todayCommentCount || 0, color: "purple" },
                    { label: "今日模拟面试", value: todayStats.todayMockInterviewCount || 0, color: "volcano" },
                    { label: "今日风险告警", value: todayStats.todayRiskAlertCount || 0, color: "red" },
                  ].map((item) => (
                    <div key={item.label} className="rounded-2xl border border-slate-100 bg-slate-50/70 px-5 py-4 flex items-center justify-between">
                      <Text className="font-medium text-slate-600">{item.label}</Text>
                      <Tag color={item.color} className="m-0 whitespace-nowrap px-3 py-1 text-base">{item.value}</Tag>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={14}>
            <Card
              className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50 h-full"
              title={<div className="flex items-center gap-2 font-black text-lg"><ShieldCheck className="h-5 w-5 text-amber-500" /> 题目健康度监控</div>}
              extra={<Tag color="orange" bordered={false}>低热度优先治理</Tag>}
            >
              {loading ? (
                <Skeleton active paragraph={{ rows: 7 }} />
              ) : (
                <List
                  dataSource={questionHealth}
                  renderItem={(item: any) => (
                    <List.Item>
                      <div className="flex w-full flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div className="min-w-0">
                          <AdminTableEllipsis value={item.title || "未命名题目"} className="font-semibold text-slate-800" />
                          <div className="text-xs text-slate-400 mt-1">
                            最近更新时间：{item.updateTime ? dayjs(item.updateTime).format("YYYY-MM-DD HH:mm") : "-"}
                          </div>
                        </div>
                        <div className="shrink-0 text-left sm:text-right">
                          <Tag color={item.status === "待激活" ? "red" : item.status === "低热度" ? "gold" : "green"} className="whitespace-nowrap">{item.status}</Tag>
                          <div className="text-xs text-slate-500 mt-1">练习 {item.practiceCount} 次</div>
                        </div>
                      </div>
                    </List.Item>
                  )}
                />
              )}
            </Card>
          </Col>
        </Row>
      </section>

      <section>
        <Card
          className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50"
          title={<div className="flex items-center gap-2 font-black text-lg"><AlertTriangle className="h-5 w-5 text-red-500" /> 异常行为告警面板</div>}
          extra={<Tag color="red" bordered={false}>高频访问预警</Tag>}
        >
          {loading ? (
            <Skeleton active paragraph={{ rows: 6 }} />
          ) : (
            <List
              dataSource={riskAlerts}
              locale={{ emptyText: "当前没有待处理异常告警" }}
              renderItem={(item: any) => (
                <List.Item
                  actions={[
                    item.userId ? (
                      <Popconfirm
                        key="ban"
                        title="确认封禁该用户？"
                        description="封禁后该用户将无法继续访问系统"
                        onConfirm={() => handleBanAlertUser(item.id)}
                      >
                        <Button danger type="link" className="px-0">一键封禁</Button>
                      </Popconfirm>
                    ) : (
                      <Button key="ban-disabled" type="link" disabled className="px-0">无法封禁</Button>
                    ),
                    <Popconfirm
                      key="ignore"
                      title="确认忽略该告警？"
                      onConfirm={() => handleIgnoreAlert(item.id)}
                    >
                      <Button type="link" className="px-0">忽略</Button>
                    </Popconfirm>,
                  ]}
                >
                  <div className="w-full flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Tag color={item.riskLevel === "high" ? "error" : "warning"} className="whitespace-nowrap">
                          {item.riskLevel === "high" ? "高风险" : "中风险"}
                        </Tag>
                        <AdminTableEllipsis value={item.reason || "异常访问告警"} className="max-w-[520px] font-semibold text-slate-800" />
                      </div>
                      <div className="mt-2 text-sm text-slate-500">
                        {(item.userName || "未知用户")}
                        {item.userId ? `（ID: ${item.userId}）` : ""}
                        {item.ip ? ` · IP: ${item.ip}` : ""}
                      </div>
                      {item.detail ? (
                        <AdminTableEllipsis value={item.detail} className="mt-2 text-sm text-slate-500" />
                      ) : null}
                    </div>
                    <Tag color="red" className="shrink-0 whitespace-nowrap">
                      {item.createTime ? dayjs(item.createTime).format("MM-DD HH:mm") : "-"}
                    </Tag>
                  </div>
                </List.Item>
              )}
            />
          )}
        </Card>
      </section>

      <section>
        <Card
          className="rounded-[3rem] border-slate-100 shadow-xl shadow-slate-200/50"
          title={<div className="flex items-center gap-2 font-black text-lg"><Activity className="h-5 w-5 text-slate-700" /> 最近管理操作</div>}
          extra={<Link href="/admin/logs" className="text-primary font-bold">查看全部</Link>}
        >
          {loading ? (
            <Skeleton active paragraph={{ rows: 6 }} />
          ) : (
            <List
              dataSource={recentOperations}
              renderItem={(item: any) => (
                <List.Item>
                  <div className="w-full flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                    <div>
                      <AdminTableEllipsis value={item.operation || item.method} className="font-semibold text-slate-800" />
                      <div className="text-xs text-slate-500 mt-1">
                        {item.userName || "未知管理员"} · {item.ip || "-"} · {item.method || "-"}
                      </div>
                    </div>
                    <Tag color="blue" className="whitespace-nowrap">{item.createTime ? dayjs(item.createTime).format("MM-DD HH:mm") : "-"}</Tag>
                  </div>
                </List.Item>
              )}
            />
          )}
        </Card>
      </section>

      <section className="bg-white rounded-[3rem] border border-slate-100 shadow-2xl shadow-slate-200/50 p-10">
        <div className="flex items-center gap-3 mb-8">
          <Activity className="h-5 w-5 text-primary" />
          <h2 className="text-2xl font-black text-slate-800 tracking-tight">智能管理工具</h2>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: "AI 批量生成", href: "/admin/question/ai", icon: <Wand2 className="h-4 w-4" /> },
            { label: "用户审计", href: "/admin/user", icon: <ShieldCheck className="h-4 w-4" /> },
            { label: "题库维护", href: "/admin/bank", icon: <BookOpen className="h-4 w-4" /> },
            { label: "操作日志", href: "/admin/logs", icon: <Activity className="h-4 w-4" /> },
          ].map((op) => (
            <Link href={op.href} key={op.label}>
              <div className="p-8 rounded-2xl bg-slate-50 border border-slate-100 hover:border-primary/30 hover:bg-white hover:shadow-lg transition-all text-center flex flex-col items-center gap-4">
                <div className="p-3 bg-white rounded-xl shadow-sm">{op.icon}</div>
                <span className="font-bold text-slate-600">{op.label}</span>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
