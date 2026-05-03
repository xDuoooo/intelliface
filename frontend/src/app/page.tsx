import Link from "next/link";
import { listQuestionBankVoByPageUsingPost } from "@/api/questionBankController";
import { getGlobalLeaderboardUsingGet } from "@/api/leaderboardController";
import { listQuestionVoByPageUsingPost } from "@/api/questionController";
import { listFeaturedPostUsingGet, listHotPostUsingGet } from "@/api/postController";
import QuestionBankList from "@/components/QuestionBankList";
import QuestionList from "@/components/QuestionList";
import LeaderboardSection from "@/components/LeaderboardSection";
import PostList from "@/components/PostList";
import Image from "next/image";
import { Trophy, Zap, ArrowRight } from "lucide-react";
import { APP_CONFIG } from "@/config/appConfig";
import { QUESTION_REVIEW_STATUS_ENUM } from "@/constants/question";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

// 本页面使用服务端渲染，禁用静态生成
export const dynamic = 'force-dynamic';

/**
 * 主页
 * @constructor
 */
export default async function HomePage() {
  let questionBankList: API.QuestionBankVO[] = [];
  let questionList: API.QuestionVO[] = [];
  let leaderboard: API.GlobalLeaderboardVO | undefined = undefined;
  let featuredPostList: API.PostVO[] = [];
  let hotPostList: API.PostVO[] = [];
  const requestOptions = buildServerRequestOptions();

  const [questionBankResult, latestQuestionResult, leaderboardResult, featuredPostResult, hotPostResult] = await Promise.allSettled([
    listQuestionBankVoByPageUsingPost(
      {
        pageSize: 4,
        reviewStatus: QUESTION_REVIEW_STATUS_ENUM.APPROVED,
        sortField: "createTime",
        sortOrder: "descend",
      },
      requestOptions,
    ),
    listQuestionVoByPageUsingPost(
      {
        pageSize: 4,
        sortField: "createTime",
        sortOrder: "descend",
      },
      requestOptions,
    ),
    getGlobalLeaderboardUsingGet(requestOptions),
    listFeaturedPostUsingGet(requestOptions),
    listHotPostUsingGet(requestOptions),
  ]);

  if (questionBankResult.status === "fulfilled") {
    const questionBankRes = questionBankResult.value as unknown as API.BaseResponsePageQuestionBankVO_;
    questionBankList = questionBankRes.data?.records ?? [];
  } else {
    console.error("获取题库列表失败", questionBankResult.reason);
  }

  if (latestQuestionResult.status === "fulfilled") {
    const latestQuestionListRes = latestQuestionResult.value as unknown as API.BaseResponsePageQuestionVO_;
    questionList = latestQuestionListRes.data?.records ?? [];
  } else {
    console.error("获取题目列表失败", latestQuestionResult.reason);
  }

  if (leaderboardResult.status === "fulfilled") {
    const leaderboardRes = leaderboardResult.value as unknown as API.BaseResponseGlobalLeaderboardVO_;
    leaderboard = leaderboardRes.data;
  } else {
    console.error("获取全站榜单失败", leaderboardResult.reason);
  }

  if (featuredPostResult.status === "fulfilled") {
    const featuredPostRes = featuredPostResult.value as unknown as API.BaseResponseListPostVO_;
    featuredPostList = featuredPostRes.data ?? [];
  } else {
    console.error("获取精选帖子失败", featuredPostResult.reason);
  }

  if (hotPostResult.status === "fulfilled") {
    const hotPostRes = hotPostResult.value as unknown as API.BaseResponseListPostVO_;
    hotPostList = hotPostRes.data ?? [];
  } else {
    console.error("获取热门帖子失败", hotPostResult.reason);
  }

  return (
    <div id="homePage" className="relative pb-32 overflow-hidden bg-[#fbfcfd] space-y-20">
      {/* Background Decorative Elements */}
      <div className="absolute top-0 left-0 w-full h-full pointer-events-none -z-10">
        <div className="absolute top-[20%] right-[-10%] w-[500px] h-[500px] bg-primary/5 rounded-full blur-[120px]" />
        <div className="absolute bottom-[10%] left-[-5%] w-[400px] h-[400px] bg-blue-400/5 rounded-full blur-[100px]" />
      </div>

      {/* Hero Section - Redesigned to Match Bank/Question Style */}
      <section className="relative overflow-hidden rounded-[3rem] bg-white border border-slate-100 shadow-2xl shadow-slate-200/50 p-8 sm:p-20 group">
        <div className="absolute top-0 right-0 p-12 opacity-[0.03] group-hover:opacity-[0.05] transition-opacity duration-1000">
           <Trophy className="h-64 w-64 text-primary" />
        </div>
        
        <div className="flex flex-col md:flex-row gap-12 items-center relative z-10">
          <div className="relative h-40 w-40 sm:h-56 sm:w-56 rounded-[3rem] overflow-hidden shadow-2xl ring-8 ring-slate-50 shrink-0 transform group-hover:scale-105 transition-transform duration-1000">
             <div className="absolute inset-0 bg-white flex items-center justify-center p-8">
                <Image
                  src="/assets/logo.png"
                  width={160}
                  height={160}
                  alt="IntelliFace Logo"
                  className="object-contain transition-transform duration-700 group-hover:scale-110"
                />
             </div>
          </div>

          <div className="flex-1 space-y-8 text-center md:text-left">
            <div className="space-y-4">
              <div className="inline-flex items-center gap-3 px-5 py-2.5 rounded-2xl bg-primary/5 border border-primary/10 text-xs sm:text-sm font-black tracking-widest uppercase text-primary animate-in fade-in slide-in-from-left-4 duration-1000">
                <Zap className="h-4 w-4 animate-pulse" />
                <span>{APP_CONFIG.home.heroBadge}</span>
              </div>
              
              <h1 className="text-5xl sm:text-7xl font-black tracking-tight leading-[1.1] text-slate-900 animate-in fade-in slide-in-from-left-6 duration-1000 delay-100">
                 {APP_CONFIG.brand.name} <br />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-primary to-blue-600 drop-shadow-sm">
                  {APP_CONFIG.brand.englishName}
                </span>
              </h1>
              <p className="text-lg sm:text-xl text-slate-500 font-medium leading-relaxed max-w-2xl animate-in fade-in slide-in-from-left-8 duration-1000 delay-200 mx-auto md:mx-0">
                {APP_CONFIG.home.heroDescription}
              </p>
            </div>

            <div className="flex flex-wrap justify-center md:justify-start gap-5 pt-2 animate-in fade-in slide-in-from-bottom-6 duration-1000 delay-300">
              <Link 
                href="/questions" 
                className="h-16 px-10 rounded-2xl bg-primary text-white font-black text-xl flex items-center gap-3 hover:scale-[1.03] transition-all shadow-xl shadow-primary/20 active:scale-95 group/btn"
              >
                立即刷题 
                <ArrowRight className="h-6 w-6 group-hover/btn:translate-x-1 transition-transform" />
              </Link>
              <Link 
                href="/banks" 
                className="h-16 px-10 rounded-2xl bg-slate-50 border border-slate-200 text-slate-600 font-black text-xl flex items-center gap-2 hover:bg-slate-100 transition-all active:scale-95"
              >
                浏览题库
              </Link>
            </div>
          </div>
        </div>
      </section>

      <LeaderboardSection leaderboard={leaderboard} />

      {/* Featured Banks Section */}
      <section className="relative space-y-10">
        <div className="flex items-end justify-between px-4">
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-primary font-black uppercase tracking-[0.2em] text-xs">
              <span className="h-1.5 w-1.5 rounded-full bg-primary animate-ping" />
              <span>Fresh Collections</span>
            </div>
            <h2 className="text-3xl font-black tracking-tighter text-slate-900 sm:text-5xl">最新公开题库</h2>
          </div>
          <Link href="/banks" className="group flex items-center gap-2 text-sm font-black text-slate-400 hover:text-primary transition-all">
            EXPLORE ALL <ArrowRight className="h-5 w-5 bg-white p-1 rounded-full group-hover:bg-primary group-hover:text-white transition-all shadow-md" />
          </Link>
        </div>
        
        <QuestionBankList questionBankList={questionBankList} />
      </section>

      {/* Latest Questions Section */}
      <section className="relative space-y-10">
        <div className="flex items-end justify-between px-4">
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-orange-500 font-black uppercase tracking-[0.2em] text-xs">
              <span className="h-1.5 w-1.5 rounded-full bg-orange-500" />
              <span>Real-time Updates</span>
            </div>
            <h2 className="text-3xl font-black tracking-tighter text-slate-900 sm:text-5xl">最新题目</h2>
          </div>
          <Link href="/questions" className="group flex items-center gap-2 text-sm font-black text-slate-400 hover:text-primary transition-all">
            VIEW ALL <ArrowRight className="h-5 w-5 bg-white p-1 rounded-full group-hover:bg-primary group-hover:text-white transition-all shadow-md" />
          </Link>
        </div>

        <QuestionList questionList={questionList} />
      </section>

      {featuredPostList.length ? (
        <section className="relative space-y-10">
          <div className="flex items-end justify-between px-4">
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-violet-500 font-black uppercase tracking-[0.2em] text-xs">
                <span className="h-1.5 w-1.5 rounded-full bg-violet-500" />
                <span>Editors Choice</span>
              </div>
              <h2 className="text-3xl font-black tracking-tighter text-slate-900 sm:text-5xl">精选经验帖</h2>
            </div>
            <Link href="/posts" className="group flex items-center gap-2 text-sm font-black text-slate-400 hover:text-primary transition-all">
              VIEW ALL <ArrowRight className="h-5 w-5 bg-white p-1 rounded-full group-hover:bg-primary group-hover:text-white transition-all shadow-md" />
            </Link>
          </div>

          <PostList postList={featuredPostList} />
        </section>
      ) : null}

      <section className="relative space-y-10">
        <div className="flex items-end justify-between px-4">
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-emerald-500 font-black uppercase tracking-[0.2em] text-xs">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              <span>Community Highlights</span>
            </div>
            <h2 className="text-3xl font-black tracking-tighter text-slate-900 sm:text-5xl">热门经验帖</h2>
          </div>
          <Link href="/posts" className="group flex items-center gap-2 text-sm font-black text-slate-400 hover:text-primary transition-all">
            VIEW ALL <ArrowRight className="h-5 w-5 bg-white p-1 rounded-full group-hover:bg-primary group-hover:text-white transition-all shadow-md" />
          </Link>
        </div>

        <PostList postList={hotPostList} />
      </section>
    </div>
  );
}
