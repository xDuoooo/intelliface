// @ts-ignore
/* eslint-disable */
import request from '@/libs/request';
import { buildApiUrl } from '@/libs/request';

/** addQuestion POST /api/question/add */
export async function addQuestionUsingPost(
  body: API.QuestionAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLong_>('/api/question/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** aiGenerateQuestions POST /api/question/ai/generate/question */
export async function aiGenerateQuestionsUsingPost(
  body: API.QuestionAIGenerateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/ai/generate/question', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export type QuestionAiGenerateTaskStatus = {
  taskId: string;
  creatorUserId?: number;
  questionType?: string;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  status?: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED" | string;
  message?: string;
  createTime?: number;
  updateTime?: number;
  finishTime?: number;
};

/** startQuestionAiGenerateTask POST /api/question/ai/generate/question/task */
export async function startQuestionAiGenerateTaskUsingPost(
  body: API.QuestionAIGenerateRequest,
  options?: { [key: string]: any },
) {
  return request<{ code: number; data: QuestionAiGenerateTaskStatus; message: string }>(
    "/api/question/ai/generate/question/task",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      data: body,
      ...(options || {}),
    },
  );
}

/** getQuestionAiGenerateTask GET /api/question/ai/generate/question/task */
export async function getQuestionAiGenerateTaskUsingGet(
  params: { taskId: string },
  options?: { [key: string]: any },
) {
  return request<{ code: number; data: QuestionAiGenerateTaskStatus; message: string }>(
    "/api/question/ai/generate/question/task",
    {
      method: "GET",
      params: {
        ...params,
      },
      ...(options || {}),
    },
  );
}

/** evaluateQuestionAnswer POST /api/question/ai/evaluate */
export async function evaluateQuestionAnswerUsingPost(
  body: API.QuestionAnswerEvaluateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseQuestionAnswerEvaluateVO_>('/api/question/ai/evaluate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function evaluateQuestionAnswerByAudioUsingPost(
  questionId: string | number,
  audioFile: Blob,
  fileName = 'question-answer.webm',
) {
  const formData = new FormData();
  formData.append('questionId', String(questionId));
  formData.append('file', audioFile, fileName);

  const response = await fetch(buildApiUrl('/api/question/ai/evaluate/audio'), {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  const json = await response.json();
  if (!response.ok || json?.code !== 0) {
    throw new Error(json?.message || '语音判题失败');
  }
  return json as API.BaseResponseQuestionAnswerEvaluateVO_;
}

/** deleteQuestion POST /api/question/delete */
export async function deleteQuestionUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** batchDeleteQuestions POST /api/question/delete/batch */
export async function batchDeleteQuestionsUsingPost(
  body: API.QuestionBatchDeleteRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/delete/batch', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** editQuestion POST /api/question/edit */
export async function editQuestionUsingPost(
  body: API.QuestionEditRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/edit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** getQuestionVOById GET /api/question/get/vo */
export async function getQuestionVoByIdUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getQuestionVOByIdUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseQuestionVO_>('/api/question/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** listPersonalRecommendQuestionVO GET /api/question/recommend/personal */
export async function listPersonalRecommendQuestionVoUsingGet(
  params: {
    questionId?: string | number;
    size?: number;
  },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseListQuestionVO_>('/api/question/recommend/personal', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** listRelatedQuestionVO GET /api/question/recommend/related */
export async function listRelatedQuestionVoUsingGet(
  params: {
    questionId?: string | number;
    size?: number;
  },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseListQuestionVO_>('/api/question/recommend/related', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** recommendQuestionsByResume POST /api/question/recommend/resume */
export async function recommendQuestionsByResumeUsingPost(
  body: API.QuestionResumeRecommendRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseResumeQuestionRecommendVO_>('/api/question/recommend/resume', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** recommendQuestionsByResumeFile POST /api/question/recommend/resume/file */
export async function recommendQuestionsByResumeFileUsingPost(
  file: File,
  size = 4,
  options?: { [key: string]: any },
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('size', String(size));
  return request<API.BaseResponseResumeQuestionRecommendVO_>('/api/question/recommend/resume/file', {
    method: 'POST',
    data: formData,
    ...(options || {}),
  });
}

/** logRecommendClick POST /api/question/recommend/click */
export async function logRecommendClickUsingPost(
  body: API.QuestionRecommendClickRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/recommend/click', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** reviewQuestion POST /api/question/review */
export async function reviewQuestionUsingPost(
  body: API.QuestionReviewRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/review', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** submitQuestionReview POST /api/question/submit/review */
export async function submitQuestionReviewUsingPost(
  body: API.QuestionSubmitReviewRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/submit/review', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listQuestionByPage POST /api/question/list/page */
export async function listQuestionByPageUsingPost(
  body: API.QuestionQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestion_>('/api/question/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listQuestionVOByPage POST /api/question/list/page/vo */
export async function listQuestionVoByPageUsingPost(
  body: API.QuestionQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestionVO_>('/api/question/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listQuestionVOByPageSentinel POST /api/question/list/page/vo/sentinel */
export async function listQuestionVoByPageSentinelUsingPost(
  body: API.QuestionQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestionVO_>('/api/question/list/page/vo/sentinel', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listMyQuestionVOByPage POST /api/question/my/list/page/vo */
export async function listMyQuestionVoByPageUsingPost(
  body: API.QuestionQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestionVO_>('/api/question/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** searchQuestionVOByPage POST /api/question/search/page/vo */
export async function searchQuestionVoByPageUsingPost(
  body: API.QuestionQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestionVO_>('/api/question/search/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** updateQuestion POST /api/question/update */
export async function updateQuestionUsingPost(
  body: API.QuestionUpdateRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/question/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
