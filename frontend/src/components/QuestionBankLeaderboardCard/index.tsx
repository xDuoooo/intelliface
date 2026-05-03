import React from "react";
import { BookOpenText, Crown, Medal, Users } from "lucide-react";
import UserAvatar from "@/components/UserAvatar";
import UserProfileHoverCard from "@/components/UserProfileHoverCard";

interface Props {
  leaderboard?: API.QuestionBankLeaderboardVO;
}

function getRankBadgeClass(rank?: number) {
  if (rank === 1) {
    return "border-amber-200 bg-amber-50 text-amber-700";
  }
  if (rank === 2) {
    return "border-slate-200 bg-slate-100 text-slate-700";
  }
  if (rank === 3) {
    return "border-orange-200 bg-orange-50 text-orange-700";
  }
  return "border-slate-200 bg-white text-slate-500";
}

function getRankIcon(rank?: number) {
  if (rank === 1) {
    return <Crown className="h-3.5 w-3.5" />;
  }
  if (rank && rank <= 3) {
    return <Medal className="h-3.5 w-3.5" />;
  }
  return null;
}

export default function QuestionBankLeaderboardCard({ leaderboard }: Props) {
  if (!leaderboard) {
    return null;
  }

  const rankingList = leaderboard.rankingList || [];
  const topList = rankingList.slice(0, 6);
  const champion = topList[0];

  return (
    <section className="rounded-[2rem] border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1 text-[11px] font-black uppercase tracking-[0.14em] text-slate-500">
            <BookOpenText className="h-3.5 w-3.5" />
            排行榜
          </div>
          <div>
            <h3 className="text-xl font-black text-slate-900">刷题排行榜</h3>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              {leaderboard.description || "按照题库内完成题数排序，看看最近谁在这套题库里最投入。"}
            </p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:min-w-[280px]">
          <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
            <div className="flex items-center gap-2 text-[11px] font-black uppercase tracking-[0.14em] text-slate-400">
              <Users className="h-3.5 w-3.5" />
              榜单维度
            </div>
            <div className="mt-2 text-base font-black text-slate-900">
              {leaderboard.metricLabel || "完成题数"}
            </div>
          </div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
            <div className="text-[11px] font-black uppercase tracking-[0.14em] text-slate-400">
              榜首成绩
            </div>
            <div className="mt-2 text-base font-black text-slate-900">
              {champion?.metricValue || 0}
            </div>
          </div>
        </div>
      </div>

      {topList.length ? (
        <div className="mt-6 overflow-hidden rounded-2xl border border-slate-100">
          {topList.map((item, index) => (
            <div
              key={`bank-rank-${item.userId}`}
              className={`flex flex-col gap-3 bg-white px-4 py-4 sm:flex-row sm:items-center sm:justify-between ${index !== topList.length - 1 ? "border-b border-slate-100" : ""}`}
            >
              <UserProfileHoverCard
                user={{
                  id: item.userId,
                  userName: item.userName,
                  userAvatar: item.userAvatar,
                  userRole: item.userRole,
                } as any}
                placement="topLeft"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div
                    className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border text-sm font-black ${getRankBadgeClass(item.rank)}`}
                  >
                    {getRankIcon(item.rank) || item.rank}
                  </div>
                  <UserAvatar src={item.userAvatar} name={item.userName} size={40} />
                  <div className="min-w-0">
                    <div className="truncate font-black text-slate-900">
                      {item.userName || "匿名用户"}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      第 {item.rank || "-"} 名
                    </div>
                  </div>
                </div>
              </UserProfileHoverCard>

              <div className="flex items-center gap-3 self-end sm:self-auto">
                <div className="text-xs font-bold text-slate-400">
                  {item.metricText || leaderboard.metricLabel || "完成题数"}
                </div>
                <div className="min-w-[88px] rounded-xl bg-slate-50 px-3 py-2 text-center text-lg font-black text-slate-900">
                  {item.metricValue || 0}
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-6 rounded-2xl border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400">
          当前题库还没有形成榜单，先去刷几道题试试看。
        </div>
      )}

      {leaderboard.currentUserItem ? (
        <div className="mt-4 rounded-2xl border border-primary/15 bg-primary/[0.04] px-4 py-4">
          <div className="mb-3 text-xs font-black uppercase tracking-[0.16em] text-primary">
            我的当前位置
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <UserProfileHoverCard
              user={{
                id: leaderboard.currentUserItem.userId,
                userName: leaderboard.currentUserItem.userName,
                userAvatar: leaderboard.currentUserItem.userAvatar,
                userRole: leaderboard.currentUserItem.userRole,
              } as any}
              placement="topLeft"
            >
              <div className="flex min-w-0 items-center gap-3">
                <div
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border text-sm font-black ${getRankBadgeClass(leaderboard.currentUserItem.rank)}`}
                >
                  {leaderboard.currentUserItem.rank || "-"}
                </div>
                <UserAvatar
                  src={leaderboard.currentUserItem.userAvatar}
                  name={leaderboard.currentUserItem.userName}
                  size={40}
                />
                <div className="min-w-0">
                  <div className="truncate font-black text-slate-900">
                    {leaderboard.currentUserItem.userName || "当前用户"}
                  </div>
                  <div className="mt-1 text-xs text-slate-500">
                    当前排名第 {leaderboard.currentUserItem.rank || "-"} 名
                  </div>
                </div>
              </div>
            </UserProfileHoverCard>

            <div className="flex items-center gap-3 self-end sm:self-auto">
              <div className="text-xs font-bold text-slate-400">
                {leaderboard.currentUserItem.metricText || leaderboard.metricLabel || "完成题数"}
              </div>
              <div className="min-w-[88px] rounded-xl bg-white px-3 py-2 text-center text-lg font-black text-slate-900">
                {leaderboard.currentUserItem.metricValue || 0}
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
