"use client";

import { Tooltip } from "antd";

type PublicHistoryRecord = {
  date?: string;
  count?: number | string;
};

function toNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : fallback;
}

function getDateKey(date: Date) {
  return date.toISOString().slice(0, 10);
}

function getIntensity(count: number) {
  if (count >= 7) {
    return "bg-emerald-600";
  }
  if (count >= 4) {
    return "bg-emerald-500";
  }
  if (count >= 2) {
    return "bg-emerald-300";
  }
  if (count >= 1) {
    return "bg-emerald-100";
  }
  return "bg-slate-100";
}

export default function PublicLearningHeatmap({
  recordList = [],
  year = new Date().getFullYear(),
}: {
  recordList?: PublicHistoryRecord[];
  year?: number;
}) {
  const countByDate = new Map<string, number>();
  recordList.forEach((item) => {
    if (!item.date) {
      return;
    }
    const dateKey = String(item.date).slice(0, 10);
    countByDate.set(dateKey, toNumber(item.count));
  });

  const days: Array<{ date?: string; count?: number }> = [];
  const firstDay = new Date(Date.UTC(year, 0, 1));
  const firstWeekday = (firstDay.getUTCDay() + 6) % 7;
  for (let i = 0; i < firstWeekday; i += 1) {
    days.push({});
  }
  for (let date = new Date(firstDay); date.getUTCFullYear() === year; date.setUTCDate(date.getUTCDate() + 1)) {
    const dateKey = getDateKey(date);
    days.push({
      date: dateKey,
      count: countByDate.get(dateKey) || 0,
    });
  }

  const activeDays = recordList.filter((item) => toNumber(item.count) > 0).length;
  const totalCount = recordList.reduce((sum, item) => sum + toNumber(item.count), 0);

  return (
    <div className="rounded-[1.75rem] border border-slate-100 bg-slate-50/70 p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-[0.18em] text-primary">
            Heatmap
          </div>
          <h3 className="mt-2 text-lg font-black text-slate-900">刷题热力图</h3>
        </div>
        <div className="flex flex-wrap gap-2 text-xs font-black text-slate-500 sm:justify-end">
          <span className="rounded-full bg-white px-3 py-1">{activeDays} 天活跃</span>
          <span className="rounded-full bg-white px-3 py-1">{totalCount} 道题</span>
        </div>
      </div>

      <div className="-mx-1 mt-5 overflow-x-auto px-1 pb-2 touch-pan-x">
        <div
          className="grid min-w-max auto-cols-[14px] grid-flow-col gap-1 pr-1"
          style={{ gridTemplateRows: "repeat(7, 14px)" }}
        >
          {days.map((day, index) => {
            if (!day.date) {
              return <div key={`empty-${index}`} className="h-3.5 w-3.5 rounded-[4px] bg-transparent" />;
            }

            const count = day.count || 0;
            return (
              <Tooltip
                key={day.date}
                mouseEnterDelay={0.05}
                title={
                  <div className="space-y-1">
                    <div>{day.date}</div>
                    <div>做题 {count} 道</div>
                  </div>
                }
              >
                <button
                  type="button"
                  aria-label={`${day.date} 做题 ${count} 道`}
                  className={`h-3.5 w-3.5 rounded-[4px] transition-transform hover:scale-110 ${getIntensity(count)}`}
                />
              </Tooltip>
            );
          })}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-start gap-2 text-[11px] font-bold text-slate-400 sm:justify-end">
        <span>少</span>
        <span className="h-3 w-3 rounded-[4px] bg-slate-100" />
        <span className="h-3 w-3 rounded-[4px] bg-emerald-100" />
        <span className="h-3 w-3 rounded-[4px] bg-emerald-300" />
        <span className="h-3 w-3 rounded-[4px] bg-emerald-500" />
        <span className="h-3 w-3 rounded-[4px] bg-emerald-600" />
        <span>多</span>
      </div>
    </div>
  );
}
