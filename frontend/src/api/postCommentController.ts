import request from "@/libs/request";

interface UserVO {
  id: number | string;
  userName: string;
  userAvatar: string;
  userProfile?: string;
  userRole: string;
}

interface PostCommentAddRequest {
  postId: number | string;
  parentId?: number | string | null;
  replyToId?: number | string | null;
  content: string;
}

interface PostCommentQueryRequest {
  postId: number | string;
  current?: number;
  pageSize?: number;
  sortField?: "createTime" | "likeNum";
  sortOrder?: "ascend" | "descend";
}

interface PostCommentAdminQueryRequest {
  postId?: number | string;
  userId?: number | string;
  status?: number;
  content?: string;
  current?: number;
  pageSize?: number;
}

interface CommentActivityQueryRequest {
  current?: number;
  pageSize?: number;
  searchText?: string;
  status?: number;
}

interface PostCommentReviewRequest {
  id: number | string;
  status: number;
  reviewMessage?: string;
}

export interface PostCommentVO {
  id: number | string;
  postId: number | string;
  parentId?: number | string | null;
  replyToId?: number | string | null;
  content?: string;
  ipLocation?: string;
  likeNum?: number;
  hasLiked?: boolean;
  status?: number;
  reviewMessage?: string;
  createTime?: string;
  deleted?: boolean;
  user?: UserVO | null;
  replyToUser?: UserVO | null;
  replies: PostCommentVO[];
}

export interface PostCommentSubmitResult {
  id: number | string;
  status: number;
  reviewMessage?: string;
}

export interface PostCommentActivityVO {
  id: number | string;
  postId: number | string;
  postTitle?: string;
  parentId?: number | string | null;
  replyToId?: number | string | null;
  content?: string;
  ipLocation?: string;
  status?: number;
  reviewMessage?: string;
  createTime?: string;
  actionTime?: string;
  deleted?: boolean;
  user?: UserVO | null;
  replyToUser?: UserVO | null;
}

interface Page<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export async function addPostComment(data: PostCommentAddRequest): Promise<PostCommentSubmitResult> {
  const res = (await request.post("/api/post/comment/add", data)) as any;
  return res.data;
}

export async function deletePostComment(id: number | string): Promise<boolean> {
  const res = (await request.post("/api/post/comment/delete", { id })) as any;
  return res.data;
}

export async function likePostComment(commentId: number | string): Promise<{ liked: boolean; likeNum: number }> {
  const res = (await request.post(`/api/post/comment/like?commentId=${commentId}`)) as any;
  return res.data;
}

export async function listPostCommentsByPage(data: PostCommentQueryRequest): Promise<Page<PostCommentVO>> {
  const res = (await request.post("/api/post/comment/list/page/vo", data)) as any;
  return res.data;
}

export async function listAdminPostCommentsByPage(
  data: PostCommentAdminQueryRequest,
): Promise<Page<PostCommentVO>> {
  const res = (await request.post("/api/post/comment/admin/list/page/vo", data)) as any;
  return res.data;
}

export async function reviewPostComment(data: PostCommentReviewRequest): Promise<boolean> {
  const res = (await request.post("/api/post/comment/review", data)) as any;
  return res.data;
}

export async function listMyRepliedPostCommentsByPage(
  data: CommentActivityQueryRequest,
): Promise<Page<PostCommentActivityVO>> {
  const res = (await request.post("/api/post/comment/my/replied/page/vo", data)) as any;
  return res.data;
}
