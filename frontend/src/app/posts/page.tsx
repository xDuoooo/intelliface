import Link from "next/link";
import {
  listMyPostVoByPageUsingPost,
  listPostVoByPageUsingPost,
  searchPostVoByPageUsingPost,
} from "@/api/postController";
import { getLoginUserUsingGet } from "@/api/userController";
import PostList from "@/components/PostList";
import PostSearchPanel from "@/components/PostSearchPanel";
import { FilePlus2 } from "lucide-react";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

const ALLOWED_SORT_FIELDS = new Set(["createTime", "thumbNum", "favourNum"]);

const SORT_LABEL_MAP: Record<string, string> = {
  createTime: "最新发布",
  thumbNum: "点赞最多",
  favourNum: "收藏最多",
};

function getSingleParam(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value[0] || "";
  }
  return value || "";
}

export default async function PostsPage({
  searchParams,
}: {
  searchParams: {
    q?: string | string[];
    tag?: string | string[];
    featured?: string | string[];
    sortField?: string | string[];
    page?: string | string[];
  };
}) {
  const requestOptions = buildServerRequestOptions();
  let postList: API.PostVO[] = [];
  let myPostList: API.PostVO[] = [];
  let total = 0;
  let isLoggedIn = false;

  const keyword = getSingleParam(searchParams.q).trim();
  const activeTag = getSingleParam(searchParams.tag).trim();
  const activeFeatured = getSingleParam(searchParams.featured) === "1";
  const requestedSortField = getSingleParam(searchParams.sortField).trim();
  const current = Math.max(1, Number(getSingleParam(searchParams.page)) || 1);
  const pageSize = 12;
  const activeSortField = ALLOWED_SORT_FIELDS.has(requestedSortField) ? requestedSortField : "createTime";
  const activeSortLabel = SORT_LABEL_MAP[activeSortField] || SORT_LABEL_MAP.createTime;

  const postQueryRequest: API.PostQueryRequest = {
    current,
    pageSize,
    sortField: activeSortField,
    sortOrder: "descend",
    searchText: keyword || undefined,
    tags: activeTag ? [activeTag] : undefined,
    isFeatured: activeFeatured ? 1 : undefined,
  };

  const [postListResult, loginUserResult] = await Promise.allSettled([
    keyword || activeTag || activeFeatured
      ? searchPostVoByPageUsingPost(postQueryRequest, requestOptions)
      : listPostVoByPageUsingPost(postQueryRequest, requestOptions),
    getLoginUserUsingGet(requestOptions),
  ]);

  if (postListResult.status === "fulfilled") {
    postList = postListResult.value.data?.records || [];
    total = Number(postListResult.value.data?.total || 0);
  } else {
    console.error("获取帖子列表失败", postListResult.reason);
  }

  if (loginUserResult.status === "fulfilled" && loginUserResult.value?.data?.id) {
    isLoggedIn = true;
    const myPostListResult = await listMyPostVoByPageUsingPost(
      {
        current: 1,
        pageSize: 6,
        sortField: "createTime",
        sortOrder: "descend",
      },
      requestOptions,
    ).catch((error) => {
      console.error("获取我的帖子失败", error);
      return undefined;
    });
    myPostList = myPostListResult?.data?.records || [];
  }

  const myPendingPostList = myPostList.filter((item) => Number(item.reviewStatus ?? 1) !== 1);
  const suggestedTagList = Array.from(
    new Set(
      postList.flatMap((item) => item.tagList || []).filter(Boolean),
    ),
  ).slice(0, 8);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const hasSearch = Boolean(keyword || activeTag || activeFeatured);

  const buildPageHref = (page: number) => {
    const params = new URLSearchParams();
    if (keyword) {
      params.set("q", keyword);
    }
    if (activeTag) {
      params.set("tag", activeTag);
    }
    if (activeFeatured) {
      params.set("featured", "1");
    }
    if (activeSortField !== "createTime") {
      params.set("sortField", activeSortField);
    }
    if (page > 1) {
      params.set("page", String(page));
    }
    const queryString = params.toString();
    return queryString ? `/posts?${queryString}` : "/posts";
  };

  return (
    <div className="max-width-content space-y-10">
      <section className="rounded-[3rem] border border-slate-100 bg-white px-8 py-12 shadow-2xl shadow-slate-200/40">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div className="space-y-4">
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">Community</div>
            <h1 className="text-4xl font-black tracking-tight text-slate-900">经验交流社区</h1>
            <p className="max-w-3xl text-base leading-8 text-slate-500">
              这里聚合了面试经验、系统设计思路、项目复盘和学习心得。你可以把它当成题库之外的补充认知层。
            </p>
          </div>
          <Link
            href="/posts/create"
            className="inline-flex items-center gap-2 rounded-2xl bg-primary px-6 py-3 text-sm font-black text-primary-foreground shadow-xl shadow-primary/20 transition-all hover:bg-primary/90"
          >
            <FilePlus2 className="h-4 w-4" />
            发布经验帖
          </Link>
        </div>
      </section>

      <PostSearchPanel suggestedTags={suggestedTagList} />

      {isLoggedIn && myPendingPostList.length ? (
        <section className="space-y-6">
          <div className="flex items-end justify-between">
            <div>
              <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">My Community</div>
              <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-900">我的待处理帖子</h2>
              <p className="mt-2 text-sm leading-7 text-slate-500">
                这里会展示你最近发布但尚未公开的帖子，方便你快速查看审核状态并回到个人中心继续修改。
              </p>
            </div>
            <Link href="/user/center?tab=posts" className="text-sm font-black text-slate-400 transition-colors hover:text-primary">
              去个人中心查看全部
            </Link>
          </div>
          <PostList postList={myPendingPostList} itemHref="/user/center?tab=posts" />
        </section>
      ) : null}

      <section className="space-y-6">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-primary">
              {hasSearch ? "Search Results" : "Community Feed"}
            </div>
            <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-900">
              {hasSearch ? "社区搜索结果" : "最新社区帖子"}
            </h2>
            <p className="mt-2 text-sm leading-7 text-slate-500">
              {hasSearch
                ? `共找到 ${total} 条相关帖子${keyword ? `，关键词“${keyword}”` : ""}${activeTag ? `，标签 #${activeTag}` : ""}${activeFeatured ? "，已筛选精选内容" : ""}，当前按“${activeSortLabel}”排序。`
                : `当前按“${activeSortLabel}”排序社区里公开的经验帖与讨论内容。`}
            </p>
          </div>
          {total > 0 ? (
            <div className="text-sm font-bold text-slate-400">
              第 {current} / {totalPages} 页
            </div>
          ) : null}
        </div>

        {postList.length ? (
          <PostList postList={postList} />
        ) : (
          <div className="rounded-[2rem] border border-dashed border-slate-200 bg-white px-8 py-16 text-center">
            <div className="text-xl font-black text-slate-900">暂时没有找到匹配的帖子</div>
            <p className="mt-3 text-sm leading-7 text-slate-500">
              可以换个关键词试试，或者清空筛选后看看最新经验帖。
            </p>
          </div>
        )}

        {totalPages > 1 ? (
          <div className="flex items-center justify-center gap-3 pt-2">
            {current > 1 ? (
              <Link
                href={buildPageHref(current - 1)}
                className="rounded-2xl border border-slate-200 bg-white px-5 py-2 text-sm font-bold text-slate-500 transition-colors hover:border-primary hover:text-primary"
              >
                上一页
              </Link>
            ) : null}
            {current < totalPages ? (
              <Link
                href={buildPageHref(current + 1)}
                className="rounded-2xl border border-slate-200 bg-white px-5 py-2 text-sm font-bold text-slate-500 transition-colors hover:border-primary hover:text-primary"
              >
                下一页
              </Link>
            ) : null}
          </div>
        ) : null}
      </section>
    </div>
  );
}
