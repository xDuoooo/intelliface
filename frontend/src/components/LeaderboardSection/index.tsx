import React from "react";
import {
  Activity,
  Flame,
  Medal,
  ShieldCheck,
  Sparkles,
  Trophy,
} from "lucide-react";
import UserAvatar from "@/components/UserAvatar";
import UserProfileHoverCard from "@/components/UserProfileHoverCard";

interface Props {
  leaderboard?: API.GlobalLeaderboardVO;
}

const boardThemes: Record<
  string,
  {
    icon: React.ReactNode;
    accent: string;
    accentSoft: string;
    accentText: string;
    glow: string;
    panel: string;
    championBorder: string;
    championBg: string;
    rankBadge: string;
  }
> = {
  overall: {
    icon: <Trophy className="h-5 w-5" />,
    accent: "from-amber-400 to-orange-500",
    accentSoft: "from-amber-50/60 to-orange-50/60",
    accentText: "text-amber-600",
    glow: "bg-amber-300/30",
    panel: "border-amber-100/80 hover:border-amber-300/80 hover:shadow-amber-100/50",
    championBorder: "from-amber-300 to-orange-400 shadow-amber-200/50",
    championBg: "bg-amber-50/40",
    rankBadge: "text-amber-500",
  },
  active: {
    icon: <Activity className="h-5 w-5" />,
    accent: "from-emerald-400 to-teal-500",
    accentSoft: "from-emerald-50/60 to-teal-50/60",
    accentText: "text-emerald-600",
    glow: "bg-emerald-300/30",
    panel: "border-emerald-100/80 hover:border-emerald-300/80 hover:shadow-emerald-100/50",
    championBorder: "from-emerald-300 to-teal-400 shadow-emerald-200/50",
    championBg: "bg-emerald-50/40",
    rankBadge: "text-emerald-500",
  },
  streak: {
    icon: <Flame className="h-5 w-5" />,
    accent: "from-rose-400 to-orange-500",
    accentSoft: "from-rose-50/60 to-orange-50/60",
    accentText: "text-rose-600",
    glow: "bg-rose-300/30",
    panel: "border-rose-100/80 hover:border-rose-300/80 hover:shadow-rose-100/50",
    championBorder: "from-rose-300 to-orange-400 shadow-rose-200/50",
    championBg: "bg-rose-50/40",
    rankBadge: "text-rose-500",
  },
};

function getBoardTheme(key?: string) {
  return boardThemes[key || "overall"] || boardThemes.overall;
}

function getRankBadgeBg(rank: number) {
  if (rank === 1) return "bg-gradient-to-br from-amber-400 to-orange-500 text-white shadow-sm shadow-orange-200";
  if (rank === 2) return "bg-slate-100 text-slate-500";
  if (rank === 3) return "bg-orange-50 text-orange-600";
  return "bg-slate-50 text-slate-400";
}

function getMetricText(boardKey: string | undefined, item: API.LeaderboardUserVO) {
  if (item.metricText) {
    return item.metricText;
  }
  const value = item.metricValue || 0;
  if (boardKey === "overall") {
    return `${value} 分`;
  }
  if (boardKey === "active" || boardKey === "streak") {
    return `${value} 天`;
  }
  return String(value);
}

/**
 * 全站榜单展示区
 */
export default function LeaderboardSection({ leaderboard }: Props) {
  const boardList = leaderboard?.boardList || [];

  if (!boardList.length) {
    return null;
  }

  return (
    <section className="space-y-6">
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        {boardList.map((board) => {
          const theme = getBoardTheme(board.key);
          const rankingList = board.rankingList || [];

          return (
            <article
              key={board.key}
              className={`group relative flex h-full flex-col overflow-hidden rounded-[2rem] border bg-white shadow-xl shadow-slate-200/20 transition-all duration-300 hover:-translate-y-0.5 ${theme.panel}`}
            >
              {/* Soft Gradient Background */}
              <div className={`absolute inset-0 bg-gradient-to-br opacity-50 ${theme.accentSoft}`} />
              <div className={`absolute -right-20 -top-20 h-48 w-48 rounded-full blur-[80px] opacity-40 transition-opacity duration-500 group-hover:opacity-70 ${theme.glow}`} />

              <div className="relative z-10 flex-1 flex flex-col p-6">
                {/* Header */}
                <div className="flex items-center gap-4 mb-6">
                  <div className={`inline-flex h-12 w-12 items-center justify-center rounded-[1.2rem] bg-gradient-to-br ${theme.accent} text-white shadow-md`}>
                    {theme.icon}
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-slate-800">{board.title}</h3>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      <div className="text-[11px] font-bold uppercase tracking-widest text-slate-400">
                        {board.metricLabel}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex-1 flex flex-col gap-2">
                  {/* Rankings List (Top 1-5) */}
                  {rankingList.slice(0, 5).map((item: API.LeaderboardUserVO, index: number) => {
                    const rank = item.rank || index + 1;
                    const isChampion = rank === 1;
                    
                    return (
                      <UserProfileHoverCard
                        key={`${board.key}-${item.userId}`}
                        user={{
                          id: item.userId,
                          userName: item.userName,
                          userAvatar: item.userAvatar,
                          userRole: item.userRole,
                        } as any}
                      >
                        <div className={`group/item flex items-center justify-between rounded-xl p-2.5 transition-all duration-200 hover:bg-white/80 hover:shadow-sm cursor-pointer border hover:border-slate-100 ${isChampion ? 'bg-amber-50/10 border-transparent shadow-sm' : 'border-transparent'}`}>
                          <div className="flex items-center gap-3 min-w-0">
                            <div className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${getRankBadgeBg(rank)}`}>
                              {isChampion ? <Medal className="h-3 w-3" /> : rank}
                            </div>
                            <UserAvatar src={item.userAvatar} name={item.userName} size={36} />
                            <div className="flex items-center gap-1.5 min-w-0">
                                <span className={`truncate text-sm font-bold transition-colors ${isChampion ? 'text-slate-800 group-hover/item:text-black' : 'text-slate-700 group-hover/item:text-slate-900'}`}>
                                  {item.userName || "匿名用户"}
                                </span>
                                {item.userRole === "admin" && (
                                  <ShieldCheck className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                                )}
                            </div>
                          </div>
                          <div className={`shrink-0 font-bold pl-3 ${isChampion ? theme.accentText : 'text-slate-700'}`}>
                            {getMetricText(board.key, item)}
                          </div>
                        </div>
                      </UserProfileHoverCard>
                    )
                  })}
                  {!rankingList.length ? (
                    <div className="flex h-[92px] items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/50">
                      <span className="text-xs font-semibold text-slate-400">目前无人登顶，虚位以待</span>
                    </div>
                  ) : null}

                  {/* My Position Box */}
                  <div className="mt-2 pt-4 border-t border-slate-200/60">
                    {board.currentUserItem ? (
                      <UserProfileHoverCard
                        user={{
                          id: board.currentUserItem.userId,
                          userName: board.currentUserItem.userName,
                          userAvatar: board.currentUserItem.userAvatar,
                          userRole: board.currentUserItem.userRole,
                        } as any}
                      >
                        <div className="group/my flex items-center justify-between rounded-xl p-2.5 transition-all duration-200 hover:bg-white/80 hover:shadow-sm cursor-pointer border border-slate-200 bg-white shadow-sm hover:border-slate-300">
                          <div className="flex items-center gap-3 min-w-0">
                            <div className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${getRankBadgeBg(board.currentUserItem.rank || 0)}`}>
                              {board.currentUserItem.rank === 1 ? <Medal className="h-3 w-3" /> : (board.currentUserItem.rank || "-")}
                            </div>
                            <UserAvatar src={board.currentUserItem.userAvatar} name={board.currentUserItem.userName} size={36} />
                            <div className="flex items-center gap-1.5 min-w-0">
                                <span className="truncate text-sm font-bold text-slate-700 group-hover/my:text-slate-900">
                                  {board.currentUserItem.userName || "当前用户"}
                                </span>
                                {board.currentUserItem.userRole === "admin" && (
                                  <ShieldCheck className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                                )}
                            </div>
                          </div>
                          <div className={`shrink-0 font-bold pl-3 ${board.currentUserItem.rank === 1 ? theme.accentText : 'text-slate-700'}`}>
                            {getMetricText(board.key, board.currentUserItem)}
                          </div>
                        </div>
                      </UserProfileHoverCard>
                    ) : (
                      <div className="flex items-center justify-center h-[56px] rounded-xl border border-dashed border-slate-200 bg-slate-50/50 text-[11px] font-semibold text-slate-400">
                        登录并参与刷题即可上榜
                      </div>
                    )}
                  </div>

                </div>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
