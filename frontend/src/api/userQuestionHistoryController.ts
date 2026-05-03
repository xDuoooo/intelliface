// @ts-ignore
/* eslint-disable */
import request from '@/libs/request';

/** addQuestionHistory POST /api/user_question_history/add */
export async function addQuestionHistoryUsingPost(
  body: API.UserQuestionHistoryAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/user_question_history/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** reportStudySession POST /api/user_question_history/session/report */
export async function reportStudySessionUsingPost(
  body: API.UserQuestionStudySessionReportRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/user_question_history/session/report', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listMyFavourQuestionByPage GET /api/user_question_history/my/favour/list */
export async function listMyFavourQuestionByPageUsingGet(
  params: {
    current?: number;
    pageSize?: number;
  },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageQuestionVO_>('/api/user_question_history/my/favour/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** listMyQuestionHistoryByPage GET /api/user_question_history/my/history/list */
export async function listMyQuestionHistoryByPageUsingGet(
  params: {
    current?: number;
    pageSize?: number;
    status?: number;
  },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageUserQuestionHistoryVO_>(
    '/api/user_question_history/my/history/list',
    {
      method: 'GET',
      params: {
        ...params,
      },
      ...(options || {}),
    },
  );
}

/** getMyQuestionHistoryRecord GET /api/user_question_history/my/history/record */
export async function getMyQuestionHistoryRecordUsingGet(
  params: {
    year?: number;
  },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseListMapStringObject_>(
    '/api/user_question_history/my/history/record',
    {
      method: 'GET',
      params: {
        ...params,
      },
      ...(options || {}),
    },
  );
}

/** getMyQuestionStats GET /api/user_question_history/my/stats */
export async function getMyQuestionStatsUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringObject_>(
    '/api/user_question_history/my/stats',
    {
      method: 'GET',
      ...(options || {}),
    },
  );
}

/** getMyLearningGoal GET /api/user_question_history/my/goal */
export async function getMyLearningGoalUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseLearningGoalData_>(
    '/api/user_question_history/my/goal',
    {
      method: 'GET',
      ...(options || {}),
    },
  );
}

/** updateMyLearningGoal POST /api/user_question_history/my/goal/update */
export async function updateMyLearningGoalUsingPost(
  body: { dailyTarget?: number; reminderEnabled?: boolean },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>(
    '/api/user_question_history/my/goal/update',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      data: body,
      ...(options || {}),
    },
  );
}
