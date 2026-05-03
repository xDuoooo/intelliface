import React, { useCallback, useEffect, useState } from "react";
import { Button, Card, Col, Empty, InputNumber, Progress, Row, Space, Statistic, Switch, Tag, Typography, message } from "antd";
import { BookOutlined, CheckCircleOutlined, HeartOutlined } from "@ant-design/icons";
import { FireOutlined, CalendarOutlined, TrophyOutlined } from "@ant-design/icons";
import {
  getMyLearningGoalUsingGet,
  updateMyLearningGoalUsingPost
} from "@/api/userQuestionHistoryController";

interface Props {
  stats?: Record<string, any>;
  statsLoading?: boolean;
  onRefreshStats?: (force?: boolean) => Promise<Record<string, any>>;
}

function formatDuration(seconds?: number) {
  const totalSeconds = Math.max(0, Number(seconds || 0));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`;
  }
  return `${minutes}分钟`;
}

/**
 * 学习数据看板
 * @constructor
 */
const LearningDataDashboard: React.FC<Props> = ({ stats = {}, statsLoading = false, onRefreshStats }) => {
  const { Text, Title, Paragraph } = Typography;
  const [dailyTarget, setDailyTarget] = useState(3);
  const [reminderEnabled, setReminderEnabled] = useState(true);
  const [goalLoading, setGoalLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const fetchGoal = useCallback(async () => {
    setGoalLoading(true);
    try {
      const res = await getMyLearningGoalUsingGet();
      const data: API.LearningGoalData = res.data ?? {};
      setDailyTarget(Number(data.dailyTarget || 3));
      setReminderEnabled(Boolean(data.reminderEnabled));
    } catch (error) {
      console.error("获取学习目标失败", error);
    } finally {
      setGoalLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchGoal();
  }, [fetchGoal]);

  const saveGoal = async () => {
    setSaving(true);
    try {
      await updateMyLearningGoalUsingPost({
        dailyTarget,
        reminderEnabled,
      });
      message.success("学习目标已更新");
      await Promise.allSettled([fetchGoal(), onRefreshStats ? onRefreshStats(true) : Promise.resolve({})]);
    } catch (error: any) {
      message.error("更新失败：" + (error?.message || "请稍后重试"));
    } finally {
      setSaving(false);
    }
  };

  const loading = goalLoading || statsLoading;
  const achievementList = stats.achievementList || [];
  const todayCount = Number(stats.todayCount || 0);
  const target = Number(stats.dailyTarget || dailyTarget || 3);
  const goalPercent = target > 0 ? Math.min(100, Math.round((todayCount / target) * 100)) : 0;

  return (
    <div style={{ marginBottom: 24 }} className="space-y-6">
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="累计刷题题数"
              value={stats.totalCount || 0}
              prefix={<BookOutlined style={{ color: "#1890ff" }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="已掌握"
              value={stats.masteredCount || 0}
              valueStyle={{ color: "#3f8600" }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="收藏题目"
              value={stats.favourCount || 0}
              valueStyle={{ color: "#cf1322" }}
              prefix={<HeartOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="当前连续天数"
              value={stats.currentStreak || 0}
              valueStyle={{ color: "#fa8c16" }}
              prefix={<FireOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="累计活跃天数"
              value={stats.activeDays || 0}
              valueStyle={{ color: "#13c2c2" }}
              prefix={<CalendarOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="今日已完成"
              value={todayCount}
              suffix={`/ ${target}`}
              valueStyle={{ color: stats.goalCompletedToday ? "#3f8600" : "#722ed1" }}
              prefix={<TrophyOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="累计学习时长"
              value={formatDuration(stats.totalStudyDurationSeconds)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="今日专注时长"
              value={formatDuration(stats.todayStudyDurationSeconds)}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card bordered={false} loading={loading} className="stats-card">
            <Statistic
              title="平均单次专注"
              value={formatDuration(stats.averageStudyDurationSeconds)}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} align="stretch">
        <Col xs={24} lg={10}>
          <Card
            loading={loading}
            bordered={false}
            className="stats-card h-full lg:h-[520px]"
            bodyStyle={{ height: "100%", display: "flex", flexDirection: "column" }}
          >
            <div className="flex h-full flex-col gap-5">
              <div>
                <Title level={5} style={{ marginBottom: 8 }}>每日学习目标</Title>
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  设定每天的刷题目标，系统会在晚上 8 点对未达标用户触发站内提醒，并在已配置邮箱时同步发送邮件提醒。
                </Paragraph>
              </div>
              <Progress percent={goalPercent} strokeColor={stats.goalCompletedToday ? "#52c41a" : "#1677ff"} />
              <div className="rounded-2xl border border-slate-100 bg-slate-50/80 px-4 py-3 text-sm text-slate-500">
                本周你已经累计了 <span className="font-semibold text-slate-800">{stats.studySessionCount || 0}</span> 次专注学习，
                当前平均单次投入 <span className="font-semibold text-slate-800">{formatDuration(stats.averageStudyDurationSeconds)}</span>。
              </div>
              <Space direction="vertical" size="middle" style={{ width: "100%", marginTop: "auto" }}>
                <div className="flex items-center justify-between">
                  <Text strong>每日目标</Text>
                  <InputNumber
                    min={1}
                    max={200}
                    value={dailyTarget}
                    onChange={(value) => setDailyTarget(Number(value || 1))}
                  />
                </div>
                <div className="flex items-center justify-between">
                  <Text strong>晚上 8 点提醒</Text>
                  <Switch checked={reminderEnabled} onChange={setReminderEnabled} />
                </div>
                <Button type="primary" loading={saving} onClick={saveGoal}>
                  保存目标
                </Button>
              </Space>
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card
            loading={loading}
            bordered={false}
            className="stats-card h-full lg:h-[520px]"
            bodyStyle={{ height: "100%", display: "flex", flexDirection: "column", overflow: "hidden" }}
          >
            <div className="flex h-full flex-col gap-4">
              <div className="flex items-center justify-between">
                <Title level={5} style={{ marginBottom: 0 }}>成就进度</Title>
                <Space>
                  {stats.recommendedDifficulty ? <Tag color="processing">建议难度：{stats.recommendedDifficulty}</Tag> : null}
                  <Tag color="blue">数据驱动激励</Tag>
                </Space>
              </div>
              {achievementList.length === 0 ? (
                <Empty description="还没有成就数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <div className="achievement-scroll -mx-1 max-h-none overflow-x-auto overflow-y-hidden pb-2 touch-pan-x lg:min-h-0 lg:flex-1 lg:overflow-x-hidden lg:overflow-y-auto lg:pr-2 lg:touch-pan-y">
                  <div className="flex min-w-max snap-x snap-mandatory gap-3 px-1 lg:min-w-0 lg:flex-col lg:snap-none">
                  {achievementList.map((item: any) => (
                    <div
                      key={item.key}
                      className={`flex w-[280px] shrink-0 snap-start flex-col rounded-2xl border p-4 transition-all sm:w-[320px] lg:w-full lg:snap-start ${
                        item.maxLevel
                          ? "border-emerald-200 bg-emerald-50/70"
                          : item.currentLevel > 0
                            ? "border-blue-200 bg-blue-50/60"
                            : "border-slate-200 bg-slate-50/70"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="font-semibold text-slate-800">{item.title}</div>
                          <div className="mt-1 text-sm text-slate-500">{item.description}</div>
                          <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                            <Tag color={item.maxLevel ? "gold" : "blue"}>
                              {item.maxLevel ? `Lv.${item.totalLevels} 已满级` : `Lv.${item.currentLevel}/${item.totalLevels}`}
                            </Tag>
                            <Tag bordered={false} color="default">
                              当前阶段：{item.currentStageTitle || "尚未解锁"}
                            </Tag>
                            {!item.maxLevel ? (
                              <Tag bordered={false} color="processing">
                                下一档：{item.nextTarget}{item.unit || ""}
                              </Tag>
                            ) : null}
                          </div>
                        </div>
                        <Tag color={item.maxLevel ? "success" : item.achieved ? "processing" : "default"}>
                          {item.maxLevel ? "已满级" : `${item.current}/${item.nextTarget || item.target}`}
                        </Tag>
                      </div>
                      <div className="mt-3 min-h-[44px] text-sm font-medium text-slate-600">{item.statusText}</div>
                      <Progress
                        percent={Number(item.percent || 0)}
                        showInfo={false}
                        strokeColor={item.maxLevel ? "#52c41a" : "#1677ff"}
                        style={{ marginTop: 12, marginBottom: 0 }}
                      />
                      {(item.milestones || []).length ? (
                        <div className="mt-3 flex flex-wrap gap-2">
                          {(item.milestones || []).map((milestone: any) => (
                            <span
                              key={`${item.key}-${milestone.level}`}
                              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                                milestone.achieved
                                  ? "bg-emerald-100 text-emerald-700"
                                  : milestone.current
                                    ? "bg-blue-100 text-blue-700"
                                    : "bg-white text-slate-500 border border-slate-200"
                              }`}
                            >
                              {milestone.target}
                              {item.unit || ""}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  ))}
                  </div>
                </div>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default LearningDataDashboard;
