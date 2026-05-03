import { Bookmark, Brain, Clock3, Gauge, History, Target, TimerReset } from "lucide-react";

function formatDuration(seconds?: number) {
  const totalSeconds = Math.max(0, Number(seconds || 0));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`;
  }
  return `${minutes}分钟`;
}

export default function PublicLearningInsights({
  profile,
}: {
  profile: API.UserProfileVO;
}) {
  const todayCount = Number(profile.todayCount || 0);
  const dailyTarget = Number(profile.dailyTarget || 0);
  const goalPercent = dailyTarget > 0 ? Math.min(100, Math.round((todayCount / dailyTarget) * 100)) : 0;
  const insights = [
    {
      key: "favour",
      label: "收藏沉淀",
      value: `${profile.favourCount || 0} 道`,
      icon: <Bookmark className="h-4 w-4 text-rose-500" />,
    },
    {
      key: "totalStudy",
      label: "累计学习",
      value: formatDuration(profile.totalStudyDurationSeconds),
      icon: <Clock3 className="h-4 w-4 text-sky-500" />,
    },
    {
      key: "todayStudy",
      label: "今日学习",
      value: formatDuration(profile.todayStudyDurationSeconds),
      icon: <History className="h-4 w-4 text-cyan-500" />,
    },
    {
      key: "sessions",
      label: "学习会话",
      value: `${profile.studySessionCount || 0} 次`,
      icon: <TimerReset className="h-4 w-4 text-emerald-500" />,
    },
    {
      key: "average",
      label: "平均专注",
      value: formatDuration(profile.averageStudyDurationSeconds),
      icon: <Gauge className="h-4 w-4 text-amber-500" />,
    },
    {
      key: "difficulty",
      label: "推荐难度",
      value: profile.recommendedDifficulty || "暂无",
      icon: <Brain className="h-4 w-4 text-violet-500" />,
    },
  ];

  return (
    <div className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 p-5">
      <div className="grid gap-3 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,2fr)]">
        <div className="min-w-0 rounded-[1.4rem] border border-primary/10 bg-white p-4">
          <div className="flex items-center gap-2 text-sm font-black text-slate-500">
            <Target className="h-4 w-4 text-primary" />
            今日目标
          </div>
          <div className="mt-3 flex items-end gap-2">
            <span className="text-3xl font-black tracking-tight text-slate-900">
              {todayCount}
            </span>
            <span className="pb-1 text-sm font-bold text-slate-400">
              / {dailyTarget || 0} 道
            </span>
          </div>
          <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
            <div
              className={profile.goalCompletedToday ? "h-full rounded-full bg-emerald-500" : "h-full rounded-full bg-primary"}
              style={{ width: `${goalPercent}%` }}
            />
          </div>
          <div className="mt-3 text-xs font-bold text-slate-400">
            {profile.goalCompletedToday ? "已完成" : `${goalPercent}%`}
          </div>
        </div>

        <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-6">
          {insights.map((item) => (
            <div key={item.key} className="min-w-0 overflow-hidden rounded-[1.4rem] border border-slate-100 bg-white p-4">
              <div className="flex min-w-0 items-center gap-2 text-xs font-black text-slate-400">
                {item.icon}
                <span className="truncate">{item.label}</span>
              </div>
              <div className="mt-3 break-words text-lg font-black leading-7 text-slate-900 sm:truncate">
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
