"use client";
import React, { useCallback, useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { Card, Col, Row, Tag, Button, Typography, Modal, message, Progress } from "antd";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import { useRouter } from "next/navigation";
import dayjs from "dayjs";
import {
  Edit,
  LayoutDashboard,
  History,
  Heart,
  ShieldCheck,
  Calendar,
  BookOpen,
  Flame,
  FilePenLine,
  User as UserIcon,
  NotebookPen,
  BriefcaseBusiness,
  MessageSquareText,
  FileSearch,
} from "lucide-react";
import UserAvatar from "@/components/UserAvatar";
import { formatIpLocation } from "@/lib/location";

import { getMyQuestionStatsUsingGet } from "@/api/userQuestionHistoryController";
import { USER_ROLE_ENUM, USER_ROLE_TEXT_MAP } from "@/constants/user";
import "./index.css";

const { Title, Paragraph, Text } = Typography;

const UserInfoEditForm = dynamic(() => import("@/app/user/center/components/UserInfoEditForm"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载资料表单...</div>,
});

const AccountSecurityCenter = dynamic(() => import("@/app/user/center/components/AccountSecurityCenter"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载账号安全...</div>,
});

const LearningDataDashboard = dynamic(() => import("@/app/user/center/components/LearningDataDashboard"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载学习数据...</div>,
});

const MyFavourList = dynamic(() => import("@/app/user/center/components/MyFavourList"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载收藏列表...</div>,
});

const LearningHistoryList = dynamic(() => import("@/app/user/center/components/LearningHistoryList"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载刷题轨迹...</div>,
});

const ResumeRecommendPanel = dynamic(() => import("@/app/user/center/components/ResumeRecommendPanel"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载简历推荐...</div>,
});

const MyQuestionSubmissionPanel = dynamic(() => import("@/app/user/center/components/MyQuestionSubmissionPanel"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载题目记录...</div>,
});

const MyQuestionBankPanel = dynamic(() => import("@/app/user/center/components/MyQuestionBankPanel"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载我的题库...</div>,
});

const MyQuestionNoteList = dynamic(() => import("@/app/user/center/components/MyQuestionNoteList"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载我的笔记...</div>,
});

const MyCommentActivityPanel = dynamic(() => import("@/app/user/center/components/MyCommentActivityPanel"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载评论足迹...</div>,
});

const MyCommunityPostPanel = dynamic(() => import("@/app/user/center/components/MyCommunityPostPanel"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载社区足迹...</div>,
});

const CalendarChart = dynamic(() => import("@/app/user/center/components/CalendarChart"), {
  loading: () => <div className="py-8 text-center text-slate-400">正在加载热力图...</div>,
});

/**
 * 用户中心页面
 */
function UserCenterContent() {
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const user = loginUser;
  const [activeTabKey, setActiveTabKey] = useState<string>("overview");
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [stats, setStats] = useState<any>({});
  const [statsLoading, setStatsLoading] = useState(false);
  const [showRecordExtras, setShowRecordExtras] = useState(false);

  const router = useRouter();
  const hasShownMessage = useRef(false);
  const statsRef = useRef<any>({});
  const tabScrollRef = useRef<HTMLDivElement | null>(null);
  const statsLoadedRef = useRef(false);
  const statsRequestRef = useRef<Promise<Record<string, any>> | null>(null);
  const validTabKeySet = useRef(
    new Set([
      "overview",
      "record",
      "favourite",
      "banks",
      "submission",
      "submissions",
      "security",
      "notes",
      "comments",
      "posts",
      "history",
      "resume",
      "achievement",
    ]),
  );

  // 获取统计数据用于侧边栏快查
  const fetchStats = useCallback(async (force = false) => {
    if (!user?.id) {
      return {};
    }
    if (!force && statsLoadedRef.current) {
      return statsRef.current;
    }
    if (!force && statsRequestRef.current) {
      return statsRequestRef.current;
    }
    const request = (async () => {
      setStatsLoading(true);
      try {
        const res = await getMyQuestionStatsUsingGet();
        const nextStats = res.data || {};
        statsRef.current = nextStats;
        setStats(nextStats);
        statsLoadedRef.current = true;
        return nextStats;
      } catch (error) {
        return {};
      } finally {
        setStatsLoading(false);
        statsRequestRef.current = null;
      }
    })();
    statsRequestRef.current = request;
    try {
      return await request;
    } catch (error) {
      return {};
    }
  }, [user?.id]);

  useEffect(() => {
    statsRef.current = {};
    statsLoadedRef.current = false;
    statsRequestRef.current = null;
    setStats({});
  }, [user?.id]);

  useEffect(() => {
    if (activeTabKey === "overview" || activeTabKey === "record") {
      void fetchStats();
    }
  }, [activeTabKey, fetchStats]);

  useEffect(() => {
    if (activeTabKey !== "record") {
      setShowRecordExtras(false);
      return;
    }
    if (typeof window === "undefined") {
      setShowRecordExtras(true);
      return;
    }
    let timeoutId: number | undefined;
    const idleWindow = window as Window & {
      requestIdleCallback?: (callback: (...args: any[]) => void, options?: { timeout: number }) => number;
      cancelIdleCallback?: (handle: number) => void;
    };
    let idleId: number | undefined;
    const revealExtras = () => {
      setShowRecordExtras(true);
    };
    if (typeof idleWindow.requestIdleCallback === "function") {
      idleId = idleWindow.requestIdleCallback(revealExtras, { timeout: 300 });
      return () => {
        if (idleId !== undefined && typeof idleWindow.cancelIdleCallback === "function") {
          idleWindow.cancelIdleCallback(idleId);
        }
      };
    }
    timeoutId = window.setTimeout(revealExtras, 120);
    return () => {
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId);
      }
    };
  }, [activeTabKey]);

  // 处理来自三方跳转的提示消息
  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const currentSearchParams = new URLSearchParams(window.location.search);
    const tab = currentSearchParams.get("tab");
    const error = currentSearchParams.get("error");
    const msg = currentSearchParams.get("msg");
    const editProfile = currentSearchParams.get("editProfile");
    let shouldReplaceQuery = false;
    const normalizedTab =
      tab === "submissions"
        ? "submission"
        : tab === "achievement"
          ? "record"
          : tab === "status"
            ? "history"
            : tab;
    if (normalizedTab && validTabKeySet.current.has(normalizedTab)) {
      setActiveTabKey(normalizedTab);
    }
    if (editProfile === "1") {
      setIsEditModalVisible(true);
      shouldReplaceQuery = true;
    }
    if ((error || msg) && !hasShownMessage.current) {
      if (error) message.error(error);
      if (msg) message.success(msg);
      hasShownMessage.current = true;
      if (!tab) {
        setActiveTabKey("security");
      }
      shouldReplaceQuery = true;
    }
    if (shouldReplaceQuery) {
      const nextSearchParams = new URLSearchParams(window.location.search);
      nextSearchParams.delete("error");
      nextSearchParams.delete("msg");
      nextSearchParams.delete("editProfile");
      const nextQuery = nextSearchParams.toString();
      router.replace(nextQuery ? `${window.location.pathname}?${nextQuery}` : window.location.pathname);
    }
  }, [router]);

  const onTabChange = (key: string) => {
    setActiveTabKey(key);
  };

  const handleTabWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    const container = tabScrollRef.current;
    if (!container) {
      return;
    }
    if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) {
      return;
    }
    container.scrollLeft += event.deltaY;
    event.preventDefault();
  };

  const centerTabList = [
    { key: "overview", label: <span className="flex items-center gap-2 whitespace-nowrap"><LayoutDashboard size={16} />个人概览</span> },
    { key: "record", label: <span className="flex items-center gap-2 whitespace-nowrap"><Calendar size={16} />成就看板</span> },
    { key: "banks", label: <span className="flex items-center gap-2 whitespace-nowrap"><BookOpen size={16} />我的题库</span> },
    { key: "submission", label: <span className="flex items-center gap-2 whitespace-nowrap"><FilePenLine size={16} />我的题目</span> },
    { key: "notes", label: <span className="flex items-center gap-2 whitespace-nowrap"><NotebookPen size={16} />我的笔记</span> },
    { key: "comments", label: <span className="flex items-center gap-2 whitespace-nowrap"><MessageSquareText size={16} />评论足迹</span> },
    { key: "posts", label: <span className="flex items-center gap-2 whitespace-nowrap"><MessageSquareText size={16} />社区足迹</span> },
    { key: "resume", label: <span className="flex items-center gap-2 whitespace-nowrap"><FileSearch size={16} />简历推荐</span> },
    { key: "security", label: <span className="flex items-center gap-2 whitespace-nowrap"><ShieldCheck size={16} />账号安全</span> },
    { key: "favour", label: <span className="flex items-center gap-2 whitespace-nowrap"><Heart size={16} />收藏题目</span> },
    { key: "history", label: <span className="flex items-center gap-2 whitespace-nowrap"><History size={16} />刷题轨迹</span> },
  ];

  return (
    <div id="userCenterPage" className="max-width-content">
      <Row gutter={[24, 24]}>
        {/* 左侧华丽版侧边栏 */}
        <Col xs={24} md={7}>
          <Card
            className="user-profile-card overflow-hidden"
          >
            {/* 用户核心信息 */}
            <div className="px-6 py-8 relative">
              <div className="relative mb-6 inline-block">
                <UserAvatar
                  src={user.userAvatar}
                  size={96}
                  className="shadow-xl ring-1 ring-slate-100"
                  name={user.userName}
                />
                {user.userRole === USER_ROLE_ENUM.ADMIN && (
                  <div className="absolute -bottom-1 -right-1 bg-amber-400 p-1.5 rounded-full border-2 border-white shadow-sm">
                    <ShieldCheck size={14} className="text-white" />
                  </div>
                )}
              </div>

              <div className="flex items-center justify-between mb-2">
                <Title level={4} style={{ margin: 0 }}>{user.userName}</Title>
                <Button
                  icon={<Edit size={14} />}
                  size="small"
                  className="rounded-lg border-slate-200 text-slate-500 hover:text-primary"
                  onClick={() => setIsEditModalVisible(true)}
                >
                  编辑资料
                </Button>
              </div>

              <Paragraph type="secondary" className="text-sm line-clamp-2 min-h-[40px] mb-4">
                {user.userProfile || "这个用户很懒，什么都没有写~"}
              </Paragraph>

              <div className="flex flex-wrap gap-2 mb-6">
                <Tag color={user.userRole === USER_ROLE_ENUM.ADMIN ? "gold" : "blue"} className="rounded-full px-3 m-0">
                  {user.userRole ? (USER_ROLE_TEXT_MAP as Record<string, string>)[user.userRole] : "未知角色"}
                </Tag>
                <Tag className="rounded-full px-3 m-0 bg-slate-50 border-slate-100">
                  ID: {user.id}
                </Tag>
                {user.city ? (
                  <Tag className="rounded-full px-3 m-0 bg-emerald-50 border-emerald-100 text-emerald-700">
                    {formatIpLocation(user.city)}
                  </Tag>
                ) : null}
                {user.careerDirection ? (
                  <Tag className="rounded-full px-3 m-0 bg-violet-50 border-violet-100 text-violet-700">
                    方向: {user.careerDirection}
                  </Tag>
                ) : null}
              </div>

              {user.interestTagList?.length ? (
                <div className="mb-6 flex flex-wrap gap-2">
                  {user.interestTagList.slice(0, 6).map((tag) => (
                    <Tag key={tag} className="rounded-full px-3 py-1 m-0 bg-slate-50 border-slate-200 text-slate-600">
                      {tag}
                    </Tag>
                  ))}
                </div>
              ) : null}

              {/* 侧边栏快查数据 */}
              <div className="grid grid-cols-2 gap-3 pt-6 border-t border-slate-100">
                <div className="p-3 rounded-2xl bg-blue-50/50 border border-blue-100/30">
                  <div className="text-[10px] uppercase font-black text-blue-400 flex items-center gap-1.5 mb-1">
                    <BookOpen size={12} /> 累计刷题
                  </div>
                  <div className="text-xl font-black text-blue-700">{stats.totalCount || 0}</div>
                </div>
                <div className="p-3 rounded-2xl bg-orange-50/50 border border-orange-100/30">
                  <div className="text-[10px] uppercase font-black text-orange-400 flex items-center gap-1.5 mb-1">
                    <Flame size={12} /> 已掌握
                  </div>
                  <div className="text-xl font-black text-orange-700">{stats.masteredCount || 0}</div>
                </div>
                <div className="col-span-2 p-3 rounded-2xl bg-violet-50/60 border border-violet-100/40">
                  <div className="text-[10px] uppercase font-black text-violet-400 flex items-center gap-1.5 mb-1">
                    <BriefcaseBusiness size={12} /> 当前建议训练难度
                  </div>
                  <div className="text-xl font-black text-violet-700">{stats.recommendedDifficulty || "中等"}</div>
                </div>
                <div className="col-span-2 p-3 rounded-2xl bg-emerald-50/60 border border-emerald-100/40">
                  <div className="text-[10px] uppercase font-black text-emerald-400 flex items-center gap-1.5 mb-1">
                    <Calendar size={12} /> 累计学习时长
                  </div>
                  <div className="text-xl font-black text-emerald-700">
                    {(() => {
                      const totalSeconds = Number(stats.totalStudyDurationSeconds || 0);
                      const hours = Math.floor(totalSeconds / 3600);
                      const minutes = Math.floor((totalSeconds % 3600) / 60);
                      return hours > 0 ? `${hours}小时${minutes}分` : `${minutes}分钟`;
                    })()}
                  </div>
                </div>
              </div>

              <Paragraph type="secondary" className="text-[11px] mt-6 text-center opacity-40">
                加入时间 {user.createTime ? dayjs(user.createTime).format("YYYY-MM-DD") : "今日"}
              </Paragraph>
            </div>
          </Card>
        </Col>

        {/* 右侧核心内容区 */}
        <Col xs={24} md={17}>
          <div className="user-tabs-shell">
            <div
              ref={tabScrollRef}
              className="user-tabs-scroll"
              role="tablist"
              aria-label="个人中心栏目"
              onWheel={handleTabWheel}
            >
              {centerTabList.map((tab) => {
                const isActive = activeTabKey === tab.key;
                return (
                  <button
                    key={tab.key}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    className={`user-tab-trigger ${isActive ? "user-tab-trigger-active" : ""}`}
                    onClick={() => onTabChange(tab.key)}
                  >
                    {tab.label}
                  </button>
                );
              })}
            </div>

            <Card className="user-tabs-card min-h-[600px]">
            {activeTabKey === "overview" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <div className="space-y-8">
                  <Row gutter={[16, 16]}>
                    <Col xs={24} sm={12} xl={6}>
                      <Card bordered={false} className="stats-card">
                        <div className="text-[11px] uppercase font-black tracking-wider text-blue-400">累计刷题</div>
                        <div className="mt-2 text-3xl font-black text-slate-900">{stats.totalCount || 0}</div>
                        <div className="mt-2 text-sm text-slate-500">到目前为止完成的题目总数</div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card bordered={false} className="stats-card">
                        <div className="text-[11px] uppercase font-black tracking-wider text-emerald-500">已掌握</div>
                        <div className="mt-2 text-3xl font-black text-slate-900">{stats.masteredCount || 0}</div>
                        <div className="mt-2 text-sm text-slate-500">已经标记为掌握的题目数量</div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card bordered={false} className="stats-card">
                        <div className="text-[11px] uppercase font-black tracking-wider text-orange-500">连续学习</div>
                        <div className="mt-2 text-3xl font-black text-slate-900">{stats.currentStreak || 0}</div>
                        <div className="mt-2 text-sm text-slate-500">当前保持连续学习的天数</div>
                      </Card>
                    </Col>
                    <Col xs={24} sm={12} xl={6}>
                      <Card bordered={false} className="stats-card">
                        <div className="text-[11px] uppercase font-black tracking-wider text-violet-500">今日进度</div>
                        <div className="mt-2 text-3xl font-black text-slate-900">
                          {stats.todayCount || 0} / {stats.dailyTarget || 3}
                        </div>
                        <div className="mt-3">
                          <Progress
                            percent={Math.min(
                              100,
                              Math.round(
                                ((Number(stats.todayCount || 0) || 0) /
                                  Math.max(Number(stats.dailyTarget || 3), 1)) * 100,
                              ),
                            )}
                            showInfo={false}
                            strokeColor={stats.goalCompletedToday ? "#52c41a" : "#1677ff"}
                          />
                        </div>
                      </Card>
                    </Col>
                  </Row>

                  <Row gutter={[16, 16]}>
                    <Col xs={24} lg={10}>
                      <Card bordered={false} className="stats-card h-full">
                        <div className="text-[11px] uppercase font-black tracking-wider text-violet-500">学习画像</div>
                        <div className="mt-4 space-y-4">
                          <div>
                            <div className="text-sm font-bold text-slate-700">目标方向</div>
                            <div className="mt-2 text-base font-semibold text-slate-900">
                              {user.careerDirection || "还没有填写就业方向"}
                            </div>
                          </div>
                          <div>
                            <div className="text-sm font-bold text-slate-700">兴趣标签</div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              {user.interestTagList?.length ? (
                                user.interestTagList.map((tag) => (
                                  <Tag key={tag} className="m-0 rounded-full border-slate-200 bg-slate-50 px-3 py-1">
                                    {tag}
                                  </Tag>
                                ))
                              ) : (
                                <Text type="secondary">添加兴趣标签后，推荐会更贴近你的学习方向。</Text>
                              )}
                            </div>
                          </div>
                        </div>
                      </Card>
                    </Col>
                    <Col xs={24} lg={14}>
                      <Card bordered={false} className="stats-card h-full">
                        <div className="text-[11px] uppercase font-black tracking-wider text-blue-500">动态难度建议</div>
                        <div className="mt-4 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                          <div>
                            <div className="text-3xl font-black text-slate-900">{stats.recommendedDifficulty || "中等"}</div>
                            <div className="mt-2 text-sm text-slate-500">
                              系统会结合你的掌握情况、困难题比例和刷题轨迹，为你推荐更适合继续训练的题目难度。
                            </div>
                          </div>
                          <Tag color="processing" className="m-0 rounded-full px-4 py-2 text-sm">
                            个性化推荐已联动
                          </Tag>
                        </div>
                      </Card>
                    </Col>
                  </Row>
                </div>
              </div>
            )}
            {activeTabKey === "security" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <AccountSecurityCenter user={user} />
              </div>
            )}
            {activeTabKey === "submission" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <MyQuestionSubmissionPanel />
              </div>
            )}
            {activeTabKey === "banks" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <MyQuestionBankPanel />
              </div>
            )}
            {activeTabKey === "notes" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <MyQuestionNoteList />
              </div>
            )}
            {activeTabKey === "comments" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <MyCommentActivityPanel />
              </div>
            )}
            {activeTabKey === "posts" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <MyCommunityPostPanel />
              </div>
            )}
            {activeTabKey === "resume" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <ResumeRecommendPanel />
              </div>
            )}
            {activeTabKey === "record" && (
              <div className="fade-in animate-in slide-in-from-bottom-2 duration-500">
                <LearningDataDashboard stats={stats} statsLoading={statsLoading} onRefreshStats={fetchStats} />
                {showRecordExtras ? (
                  <>
                    <Title level={5} className="flex items-center gap-2 mb-6">
                      <Calendar size={18} className="text-primary" /> 刷题热力图
                    </Title>
                    <div className="bg-slate-50/50 p-6 rounded-3xl border border-dashed border-slate-200 mb-8">
                      <CalendarChart />
                    </div>
                    <Title level={5} className="flex items-center gap-2 mb-6">
                      <History size={18} className="text-primary" /> 最近刷题
                    </Title>
                    <LearningHistoryList limit={5} />
                  </>
                ) : (
                  <div className="mb-8 rounded-3xl border border-dashed border-slate-200 bg-slate-50/60 px-6 py-10 text-center text-sm text-slate-400">
                    正在准备热力图和最近刷题...
                  </div>
                )}
              </div>
            )}
            {activeTabKey === "favour" && <MyFavourList />}
            {activeTabKey === "history" && <LearningHistoryList />}
            </Card>
          </div>
        </Col>
      </Row>

      {/* 编辑个人资料弹窗 */}
      <Modal
        title={null}
        open={isEditModalVisible}
        onCancel={() => setIsEditModalVisible(false)}
        footer={null}
        width={720}
        destroyOnClose
        centered
        className="profile-edit-modal"
      >
        <div className="pt-4">
          <div className="flex items-center gap-3 mb-8 px-2 border-b border-slate-100 pb-4">
            <div className="p-3 bg-blue-50 rounded-2xl">
              <UserIcon size={28} className="text-primary" />
            </div>
            <div>
              <Typography.Title level={4} style={{ margin: 0 }}>完善您的资料</Typography.Title>
              <Text type="secondary" className="text-sm">打造一个个性化的个人主页</Text>
            </div>
          </div>
          <UserInfoEditForm user={user} onSuccess={() => setIsEditModalVisible(false)} />
        </div>
      </Modal>
    </div>
  );
}

export default function UserCenterPage() {
  return <UserCenterContent />;
}
