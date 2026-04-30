import { Award, CheckCircle2 } from "lucide-react";

type PublicAchievementItem = {
  key?: string;
  title?: string;
  description?: string;
  unit?: string;
  current?: number | string;
  target?: number | string;
  nextTarget?: number | string;
  percent?: number | string;
  currentLevel?: number | string;
  totalLevels?: number | string;
  currentStageTitle?: string;
  statusText?: string;
  maxLevel?: boolean;
  milestones?: Array<{
    level?: number | string;
    target?: number | string;
    achieved?: boolean;
    current?: boolean;
  }>;
};

function toNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : fallback;
}

export default function PublicAchievementStrip({
  achievementList = [],
}: {
  achievementList?: PublicAchievementItem[];
}) {
  if (!achievementList.length) {
    return (
      <div className="flex min-h-[280px] min-w-0 items-center justify-center rounded-[1.75rem] border border-dashed border-slate-200 bg-slate-50/70 px-6 py-10 text-center text-sm text-slate-400">
        暂无公开成就进度。
      </div>
    );
  }

  return (
    <div className="max-h-[430px] min-w-0 overflow-hidden overflow-y-auto pr-0 sm:pr-1">
      <div className="space-y-3">
        {achievementList.map((item, index) => {
          const percent = Math.max(0, Math.min(100, toNumber(item.percent)));
          const current = toNumber(item.current);
          const nextTarget = item.nextTarget ?? item.target ?? 0;
          const itemKey = item.key || `${item.title}-${index}`;
          return (
            <div
              key={itemKey}
              className={`min-w-0 overflow-hidden rounded-[1.5rem] border px-4 py-4 ${
                item.maxLevel
                  ? "border-emerald-200 bg-emerald-50/80"
                  : percent > 0
                    ? "border-blue-100 bg-blue-50/60"
                    : "border-slate-100 bg-slate-50/80"
              }`}
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-white text-primary shadow-sm shadow-slate-200/70">
                      {item.maxLevel ? <CheckCircle2 className="h-4 w-4" /> : <Award className="h-4 w-4" />}
                    </span>
                    <div className="font-black text-slate-900">{item.title || "成就"}</div>
                  </div>
                  <div className="mt-2 line-clamp-2 text-sm leading-6 text-slate-500">
                    {item.statusText || item.description}
                  </div>
                </div>
                <div className="self-start rounded-full bg-white px-3 py-1 text-xs font-black text-slate-600 shadow-sm shadow-slate-200/70 sm:shrink-0">
                  {item.maxLevel ? "满级" : `Lv.${item.currentLevel || 0}/${item.totalLevels || 0}`}
                </div>
              </div>

              <div className="mt-4 h-2 overflow-hidden rounded-full bg-white">
                <div
                  className={item.maxLevel ? "h-full rounded-full bg-emerald-500" : "h-full rounded-full bg-primary"}
                  style={{ width: `${percent}%` }}
                />
              </div>

              <div className="mt-3 flex flex-col items-start gap-1 text-xs font-bold text-slate-500 sm:flex-row sm:items-center sm:justify-between">
                <span className="break-words">{item.currentStageTitle || "尚未解锁"}</span>
                <span className="break-words">
                  {current}/{nextTarget}
                  {item.unit || ""}
                </span>
              </div>

              {item.milestones?.length ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {item.milestones.map((milestone) => (
                    <span
                      key={`${itemKey}-${milestone.level}`}
                      className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${
                        milestone.achieved
                          ? "bg-emerald-100 text-emerald-700"
                          : milestone.current
                            ? "bg-blue-100 text-blue-700"
                            : "bg-white text-slate-400"
                      }`}
                    >
                      {milestone.target}
                      {item.unit || ""}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}
