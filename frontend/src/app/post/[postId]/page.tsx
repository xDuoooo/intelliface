import { getMyPostVoByIdUsingGet, getPostVoByIdUsingGet, listRelatedPostUsingGet } from "@/api/postController";
import PostDetailClient from "./PostDetailClient";
import { buildServerRequestOptions } from "@/libs/serverRequestOptions";

export const dynamic = "force-dynamic";

export default async function PostDetailPage({ params }: { params: { postId: string } }) {
  const requestOptions = buildServerRequestOptions();
  const postId = params.postId;
  let post: API.PostVO | undefined;
  let relatedPostList: API.PostVO[] = [];

  const [postResult, relatedResult] = await Promise.allSettled([
    getPostVoByIdUsingGet(
      { id: postId },
      requestOptions,
    ),
    listRelatedPostUsingGet(
      { postId, size: 4 },
      requestOptions,
    ),
  ]);

  if (postResult.status === "fulfilled") {
    post = postResult.value.data;
  } else {
    console.error("获取帖子详情失败", postResult.reason);
    if (requestOptions.headers.cookie) {
      try {
        const myPostResult = await getMyPostVoByIdUsingGet(
          { id: postId },
          requestOptions,
        );
        post = myPostResult.data;
      } catch (error) {
        console.error("获取我的帖子详情失败", error);
      }
    }
  }

  if (relatedResult.status === "fulfilled") {
    relatedPostList = relatedResult.value.data || [];
  } else {
    console.error("获取相关帖子失败", relatedResult.reason);
  }

  return <PostDetailClient postId={postId} initialPost={post} initialRelatedPostList={relatedPostList} />;
}
