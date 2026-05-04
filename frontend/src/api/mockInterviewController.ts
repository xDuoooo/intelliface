// @ts-ignore
/* eslint-disable */
import { buildApiUrl } from '@/libs/request';
import request from '@/libs/request';

type StreamEventCallback = (event: string, payload: any) => void | Promise<void>;

function findSseDelimiter(buffer: string) {
  const lfIndex = buffer.indexOf('\n\n');
  const crlfIndex = buffer.indexOf('\r\n\r\n');
  if (lfIndex < 0 && crlfIndex < 0) {
    return null;
  }
  if (lfIndex >= 0 && (crlfIndex < 0 || lfIndex < crlfIndex)) {
    return { index: lfIndex, length: 2 };
  }
  return { index: crlfIndex, length: 4 };
}

async function readResponseError(response: Response, fallbackMessage: string) {
  const contentType = response.headers.get('content-type') || '';
  try {
    if (contentType.includes('application/json')) {
      const json = await response.json();
      return json?.message || fallbackMessage;
    }
    const text = await response.text();
    return text || fallbackMessage;
  } catch {
    return fallbackMessage;
  }
}

function parseSseBlock(block: string) {
  let event = 'message';
  const dataLines: string[] = [];
  block
    .split('\n')
    .map((line) => line.trimEnd())
    .forEach((line) => {
      if (!line || line.startsWith(':')) {
        return;
      }
      if (line.startsWith('event:')) {
        event = line.slice(6).trim();
        return;
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    });
  const dataText = dataLines.join('\n');
  let payload: any = dataText;
  if (dataText) {
    try {
      payload = JSON.parse(dataText);
    } catch {
      payload = dataText;
    }
  }
  return { event, payload };
}

/** addMockInterview POST /api/mockInterview/add */
export async function addMockInterviewUsingPost(
  body: API.MockInterviewAddRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseLong_>('/api/mockInterview/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** deleteMockInterview POST /api/mockInterview/delete */
export async function deleteMockInterviewUsingPost(
  body: API.DeleteRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean_>('/api/mockInterview/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** getMockInterviewById GET /api/mockInterview/get */
export async function getMockInterviewByIdUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getMockInterviewByIdUsingGETParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseMockInterview_>('/api/mockInterview/get', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** handleMockInterviewEvent POST /api/mockInterview/handleEvent */
export async function handleMockInterviewEventUsingPost(
  body: API.MockInterviewEventRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseString_>('/api/mockInterview/handleEvent', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function streamMockInterviewEventUsingPost(
  body: API.MockInterviewEventRequest,
  onEvent: StreamEventCallback,
  signal?: AbortSignal,
) {
  const response = await fetch(buildApiUrl('/api/mockInterview/stream/handleEvent'), {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const contentType = response.headers.get('content-type') || '';
  if (!response.ok) {
    throw new Error(await readResponseError(response, '流式面试连接失败'));
  }
  if (!response.body) {
    throw new Error('流式面试连接失败');
  }
  if (contentType.includes('application/json')) {
    const json = await response.json();
    throw new Error(json?.message || '流式面试处理失败');
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    let delimiter = findSseDelimiter(buffer);
    while (delimiter) {
      const block = buffer.slice(0, delimiter.index).replace(/\r/g, '');
      buffer = buffer.slice(delimiter.index + delimiter.length);
      if (block.trim()) {
        const parsed = parseSseBlock(block);
        await onEvent(parsed.event, parsed.payload);
      }
      delimiter = findSseDelimiter(buffer);
    }
    if (done) {
      break;
    }
  }
  if (buffer.trim()) {
    const parsed = parseSseBlock(buffer.replace(/\r/g, ''));
    await onEvent(parsed.event, parsed.payload);
  }
}

export async function transcribeMockInterviewAudioUsingPost(
  id: string | number,
  audioFile: Blob,
  fileName = 'mock-interview-answer.webm',
) {
  const formData = new FormData();
  formData.append('id', String(id));
  formData.append('file', audioFile, fileName);

  const response = await fetch(buildApiUrl('/api/mockInterview/transcribe'), {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    throw new Error('语音转写失败');
  }
  if (!response.ok || json?.code !== 0) {
    throw new Error(json?.message || '语音转写失败');
  }
  return String(json?.data || '');
}

export async function synthesizeMockInterviewSpeechUsingPost(
  body: { id: string | number; text: string },
  signal?: AbortSignal,
) {
  const response = await fetch(buildApiUrl('/api/mockInterview/speech'), {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Accept': 'audio/mpeg,audio/wav,audio/ogg,audio/pcm,application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const contentType = response.headers.get('content-type') || '';
  if (!response.ok || contentType.includes('application/json')) {
    throw new Error(await readResponseError(response, '语音播报失败'));
  }
  return response.blob();
}

export async function downloadMockInterviewReviewUsingGet(id: string | number) {
  const response = await fetch(buildApiUrl(`/api/mockInterview/export?id=${encodeURIComponent(String(id))}`), {
    method: 'GET',
    credentials: 'include',
  });
  const contentType = response.headers.get('content-type') || '';
  if (!response.ok) {
    throw new Error(await readResponseError(response, '导出复盘失败'));
  }
  if (contentType.includes('application/json')) {
    const json = await response.json();
    throw new Error(json?.message || '导出复盘失败');
  }
  const blob = await response.blob();
  const disposition = response.headers.get('content-disposition') || '';
  const matched = disposition.match(/filename\*=UTF-8''([^;]+)/);
  const fileName = matched?.[1] ? decodeURIComponent(matched[1]) : `mock-interview-${id}-review.md`;
  return { blob, fileName };
}

/** listMockInterviewByPage POST /api/mockInterview/list/page */
export async function listMockInterviewByPageUsingPost(
  body: API.MockInterviewQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageMockInterview_>('/api/mockInterview/list/page', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** listMockInterviewVOByPage POST /api/mockInterview/my/list/page/vo */
export async function listMockInterviewVoByPageUsingPost(
  body: API.MockInterviewQueryRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponsePageMockInterview_>('/api/mockInterview/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
