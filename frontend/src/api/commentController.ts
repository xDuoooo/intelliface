/**
 * 题目评论 API 封装
 */
import request from "@/libs/request";

interface CommentAddRequest {
  questionId: string | number;
  parentId?: string | number | null;
  replyToId?: string | number | null;
  content: string;
}

interface CommentAdminQueryRequest {
  questionId?: string | number;
  userId?: string | number;
  content?: string;
  status?: number;
  current?: number;
  pageSize?: number;
  sortField?: string;
  sortOrder?: "ascend" | "descend";
}

interface CommentReviewRequest {
  id: string | number;
  status: number;
  reviewMessage?: string;
}

interface CommentQueryRequest {
  questionId: string | number;
  current?: number;
  pageSize?: number;
  sortField?: "createTime" | "likeNum";
  sortOrder?: "ascend" | "descend";
}

interface CommentReportRequest {
  commentId: string | number;
  reason: string;
}

interface CommentActivityQueryRequest {
  current?: number;
  pageSize?: number;
  searchText?: string;
  status?: number;
}

interface UserVO {
  id: string | number;
  userName: string;
  userAvatar: string;
  userProfile?: string;
  userRole: string;
}

export interface CommentVO {
  id: string | number;
  questionId: string | number;
  parentId?: string | number | null;
  replyToId?: string | number | null;
  content: string;
  ipLocation?: string;
  likeNum: number;
  isPinned: number;
  isOfficial: number;
  status: number;
  reviewMessage?: string;
  createTime: string;
  deleted: boolean;
  user: UserVO | null;
  replyToUser?: UserVO | null;
  hasLiked: boolean;
  replies: CommentVO[];
}

export interface UserCommentActivityVO {
  id: number | string;
  questionId: number | string;
  questionTitle?: string;
  parentId?: number | string | null;
  replyToId?: number | string | null;
  content?: string;
  ipLocation?: string;
  likeNum?: number;
  status?: number;
  reviewMessage?: string;
  createTime?: string;
  actionTime?: string;
  deleted?: boolean;
  hasLiked?: boolean;
  user?: UserVO | null;
  replyToUser?: UserVO | null;
}

export interface CommentSubmitResult {
  id: string | number;
  status: number;
  reviewMessage?: string;
}

interface Page<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/**
 * 注意：request (myAxios) 的响应拦截器已经返回了 data (BaseResponse)
 * 我们进一步提取出 BaseResponse 中的数据部分返回给组件
 */

export async function addComment(data: CommentAddRequest): Promise<CommentSubmitResult> {
  const res = (await request.post("/api/question/comment/add", data)) as any;
  return res.data;
}

export async function deleteComment(id: string | number): Promise<boolean> {
  const res = (await request.post("/api/question/comment/delete", { id })) as any;
  return res.data;
}

export async function listCommentsByPage(data: CommentQueryRequest): Promise<Page<CommentVO>> {
  const res = (await request.post("/api/question/comment/list/page/vo", data)) as any;
  return res.data;
}

export async function likeComment(commentId: string | number): Promise<{ liked: boolean; likeNum: number }> {
  const res = (await request.post(`/api/question/comment/like?commentId=${commentId}`)) as any;
  return res.data;
}

export async function reportComment(data: CommentReportRequest): Promise<boolean> {
  const res = (await request.post("/api/question/comment/report", data)) as any;
  return res.data;
}

export async function pinComment(commentId: string | number, pinned: boolean): Promise<boolean> {
  const res = (await request.post(`/api/question/comment/pin?commentId=${commentId}&pinned=${pinned}`)) as any;
  return res.data;
}

export async function setOfficialAnswer(commentId: string | number, official: boolean): Promise<boolean> {
  const res = (await request.post(`/api/question/comment/official?commentId=${commentId}&official=${official}`)) as any;
  return res.data;
}

export async function listAdminCommentsByPage(data: CommentAdminQueryRequest): Promise<Page<CommentVO>> {
  const res = (await request.post("/api/question/comment/admin/list/page/vo", data)) as any;
  return res.data;
}

export async function reviewComment(data: CommentReviewRequest): Promise<boolean> {
  const res = (await request.post("/api/question/comment/review", data)) as any;
  return res.data;
}

export async function listMyLikedCommentsByPage(
  data: CommentActivityQueryRequest,
): Promise<Page<UserCommentActivityVO>> {
  const res = (await request.post("/api/question/comment/my/liked/page/vo", data)) as any;
  return res.data;
}

export async function listMyReplyCommentsByPage(
  data: CommentActivityQueryRequest,
): Promise<Page<UserCommentActivityVO>> {
  const res = (await request.post("/api/question/comment/my/replied/page/vo", data)) as any;
  return res.data;
}
