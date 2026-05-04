"use client";

import Link from "next/link";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button, Card, Empty, Input, List, Popconfirm, Progress, Skeleton, Tag, Typography, message } from "antd";
import {
  Briefcase,
  BrainCircuit,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Clock3,
  Download,
  Flag,
  Mic,
  MicOff,
  Radar,
  RefreshCw,
  Square,
  Sparkles,
  Volume2,
  VolumeX,
} from "lucide-react";
import {
  downloadMockInterviewReviewUsingGet,
  getMockInterviewByIdUsingGet,
  handleMockInterviewEventUsingPost,
  synthesizeMockInterviewSpeechUsingPost,
  streamMockInterviewEventUsingPost,
  transcribeMockInterviewAudioUsingPost,
} from "@/api/mockInterviewController";
import { getInterviewDepthMeta } from "@/lib/mockInterview";
import "./index.css";

const { Title, Paragraph, Text } = Typography;

interface MessageItem {
  content: string;
  isAI: boolean;
  timestamp: number;
  round?: number;
  stage?: string;
}

interface RoundRecord {
  round?: number;
  question?: string;
  answer?: string;
  shortComment?: string;
  interviewerObservation?: string;
  focus?: string;
  score?: number;
  communicationScore?: number;
  technicalScore?: number;
  problemSolvingScore?: number;
  scoreReasons?: string[];
  questionStyle?: string;
  recommendedAnswerSeconds?: number;
  responseSeconds?: number;
  verdict?: string;
  improvementTags?: string[];
  missingPoints?: string[];
  answerQualitySignals?: string[];
  answerRewriteSuggestion?: string;
  followUpReason?: string;
}

interface InterviewAgendaItem {
  round?: number;
  label?: string;
  focusTopic?: string;
  questionStyle?: string;
  recommendedAnswerSeconds?: number;
}

interface InterviewReport {
  expectedRounds?: number;
  completedRounds?: number;
  overallScore?: number;
  summary?: string;
  communicationScore?: number;
  technicalScore?: number;
  problemSolvingScore?: number;
  currentFocus?: string;
  currentQuestionStyle?: string;
  recommendedAnswerSeconds?: number;
  nextActionHint?: string;
  strengths?: string[];
  improvements?: string[];
  suggestedTopics?: string[];
  practicePlan?: string[];
  hiringRecommendation?: string;
  riskPoints?: string[];
  nextInterviewFocus?: string[];
  roundRecords?: RoundRecord[];
  agenda?: InterviewAgendaItem[];
  answerChecklist?: string[];
  readinessLevel?: string;
  recommendedNextAction?: string;
}

interface MockInterviewDetail extends API.MockInterview {
  parsedMessages?: MessageItem[];
  parsedReport?: InterviewReport | null;
}

interface StreamingReply {
  content: string;
  round?: number;
  stage?: string;
}

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionErrorEventLike {
  error?: string;
}

interface SpeechRecognitionLike extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

type SpeechWindow = Window & {
  SpeechRecognition?: SpeechRecognitionConstructor;
  webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: "待开始", color: "orange" },
  1: { text: "进行中", color: "green" },
  2: { text: "已结束", color: "red" },
  3: { text: "已暂停", color: "gold" },
};

const AUTO_SPEAK_STORAGE_KEY = "mockInterview:autoSpeakEnabled";
const MAX_ANSWER_LENGTH = 4000;

function safeParseJson<T>(value?: string | null): T | null {
  if (!value) {
    return null;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

function formatTime(timestamp?: number) {
  if (!timestamp) {
    return "";
  }
  return new Date(timestamp).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDuration(seconds?: number) {
  const safeSeconds = Math.max(0, Math.floor(seconds || 0));
  return `${Math.floor(safeSeconds / 60)}:${String(safeSeconds % 60).padStart(2, "0")}`;
}

function splitSpeechText(content: string, maxBytes = 900) {
  const normalized = content
    .replace(/\r/g, "\n")
    .replace(/\n{2,}/g, "\n")
    .replace(/[ \t\f\v]+/g, " ")
    .trim();
  if (!normalized) {
    return [];
  }
  const encoder = new TextEncoder();
  const punctuation = new Set(["。", "！", "？", "；", "，", ".", "!", "?", ";", ",", "\n", "：", ":"]);
  const segments: string[] = [];
  let current = "";
  let lastBreakIndex = -1;

  const flushCurrent = (value: string) => {
    const trimmed = value.trim();
    if (trimmed) {
      segments.push(trimmed);
    }
  };

  for (const char of normalized) {
    const candidate = current + char;
    if (encoder.encode(candidate).length <= maxBytes) {
      current = candidate;
      if (punctuation.has(char)) {
        lastBreakIndex = current.length;
      }
      continue;
    }
    if (!current) {
      flushCurrent(char);
      lastBreakIndex = -1;
      continue;
    }
    if (lastBreakIndex > 0) {
      const head = current.slice(0, lastBreakIndex);
      const tail = current.slice(lastBreakIndex);
      flushCurrent(head);
      current = `${tail}${char}`.trimStart();
    } else {
      flushCurrent(current);
      current = char;
    }
    lastBreakIndex = -1;
    for (let i = 0; i < current.length; i += 1) {
      if (punctuation.has(current[i])) {
        lastBreakIndex = i + 1;
      }
    }
  }
  flushCurrent(current);
  return segments;
}

function buildRoundScoreItems(record?: RoundRecord | null) {
  return [
    { label: "表达", value: record?.communicationScore || 0 },
    { label: "技术", value: record?.technicalScore || 0 },
    { label: "分析", value: record?.problemSolvingScore || 0 },
  ];
}

function formatMessageStage(stage?: string) {
  switch (stage) {
    case "opening":
      return "开场";
    case "question":
      return "提问";
    case "probe":
      return "追问";
    case "answer":
      return "回答";
    case "pause":
      return "暂停";
    case "resume":
      return "继续";
    case "summary":
      return "总结";
    default:
      return "";
  }
}

function hydrateInterviewDetail(rawData?: API.MockInterview): MockInterviewDetail | undefined {
  if (!rawData) {
    return undefined;
  }
  const data = { ...(rawData as MockInterviewDetail) };
  data.parsedMessages = safeParseJson<MessageItem[]>(data.messages) || [];
  data.parsedReport = safeParseJson<InterviewReport>(data.report) || null;
  return data;
}

function getSpeechRecognitionConstructor() {
  if (typeof window === "undefined") {
    return null;
  }
  const speechWindow = window as SpeechWindow;
  return speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition || null;
}

function getPreferredAudioMimeType() {
  if (typeof window === "undefined" || typeof MediaRecorder === "undefined") {
    return "";
  }
  const candidates = [
    "audio/ogg;codecs=opus",
    "audio/ogg",
    "audio/mpeg",
    "audio/mp4",
    "audio/webm;codecs=opus",
    "audio/webm",
  ];
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) || "";
}

function getAudioFileExtension(mimeType?: string) {
  if (!mimeType) {
    return "webm";
  }
  if (mimeType.includes("mp4")) {
    return "m4a";
  }
  if (mimeType.includes("mpeg")) {
    return "mp3";
  }
  if (mimeType.includes("ogg")) {
    return "ogg";
  }
  if (mimeType.includes("wav")) {
    return "wav";
  }
  return "webm";
}

export default function InterviewRoomPage({ params }: { params: { mockInterviewId: string } }) {
  const { mockInterviewId } = params;
  const interviewId = mockInterviewId;
  const draftStorageKey = useMemo(() => `mockInterview:draft:${interviewId}`, [interviewId]);
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [inputMessage, setInputMessage] = useState("");
  const [interview, setInterview] = useState<MockInterviewDetail>();
  const [messages, setMessages] = useState<MessageItem[]>([]);
  const [questionElapsedSeconds, setQuestionElapsedSeconds] = useState(0);
  const [streamStatus, setStreamStatus] = useState("");
  const [streamingReply, setStreamingReply] = useState<StreamingReply | null>(null);
  const [autoSpeakEnabled, setAutoSpeakEnabled] = useState(true);
  const [speechRecognitionSupported, setSpeechRecognitionSupported] = useState(false);
  const [audioPlaybackSupported, setAudioPlaybackSupported] = useState(false);
  const [audioRecordingSupported, setAudioRecordingSupported] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [voiceStatus, setVoiceStatus] = useState("");
  const [expandedRoundKeys, setExpandedRoundKeys] = useState<string[]>([]);
  const [expandedRoundTextKeys, setExpandedRoundTextKeys] = useState<string[]>([]);

  const speechRecognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const streamAbortControllerRef = useRef<AbortController | null>(null);
  const refreshAfterAbortTimerRef = useRef<number | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);
  const voiceBaseInputRef = useRef("");
  const lastSpokenKeyRef = useRef("");
  const draftHydratedKeyRef = useRef<string>();
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const recordedChunksRef = useRef<BlobPart[]>([]);
  const recordMimeTypeRef = useRef("audio/webm");
  const activeAudioRef = useRef<HTMLAudioElement | null>(null);
  const speechAbortControllerRef = useRef<AbortController | null>(null);
  const speakingTaskIdRef = useRef(0);

  const applyInterviewData = useCallback((rawData?: API.MockInterview) => {
    const nextInterview = hydrateInterviewDetail(rawData);
    setInterview(nextInterview);
    setMessages(nextInterview?.parsedMessages || []);
  }, []);

  const loadInterview = useCallback(async (silent = false) => {
    if (!interviewId) {
      message.error("面试记录不存在");
      return;
    }
    if (!silent) {
      setLoading(true);
    }
    try {
      const res = await getMockInterviewByIdUsingGet({ id: interviewId });
      applyInterviewData(res.data);
    } catch (error: any) {
      message.error(error?.message || "加载面试数据失败");
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, [applyInterviewData, interviewId]);

  useEffect(() => {
    void loadInterview();
  }, [loadInterview]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    setSpeechRecognitionSupported(Boolean(getSpeechRecognitionConstructor()));
    setAudioPlaybackSupported(typeof window.Audio !== "undefined");
    setAudioRecordingSupported(Boolean(window.MediaRecorder) && Boolean(navigator.mediaDevices?.getUserMedia));
    const savedAutoSpeak = window.localStorage.getItem(AUTO_SPEAK_STORAGE_KEY);
    if (savedAutoSpeak === "0") {
      setAutoSpeakEnabled(false);
    }
    return () => {
      speechRecognitionRef.current?.abort();
      speechRecognitionRef.current = null;
      const recorder = mediaRecorderRef.current;
      if (recorder) {
        recorder.ondataavailable = null;
        recorder.onstop = null;
        recorder.onerror = null;
        if (recorder.state !== "inactive") {
          recorder.stop();
        }
      }
      mediaRecorderRef.current = null;
      mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
      recordedChunksRef.current = [];
      streamAbortControllerRef.current?.abort();
      streamAbortControllerRef.current = null;
      if (refreshAfterAbortTimerRef.current) {
        window.clearTimeout(refreshAfterAbortTimerRef.current);
      }
      speechAbortControllerRef.current?.abort();
      speechAbortControllerRef.current = null;
      const activeAudio = activeAudioRef.current;
      if (activeAudio) {
        activeAudio.pause();
        activeAudio.src = "";
      }
      activeAudioRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (typeof window === "undefined" || draftHydratedKeyRef.current === draftStorageKey) {
      return;
    }
    const savedDraft = window.localStorage.getItem(draftStorageKey);
    draftHydratedKeyRef.current = draftStorageKey;
    if (savedDraft) {
      setInputMessage(savedDraft);
      setVoiceStatus("已恢复上次未发送的回答草稿");
    }
  }, [draftStorageKey]);

  useEffect(() => {
    if (typeof window === "undefined" || draftHydratedKeyRef.current !== draftStorageKey) {
      return;
    }
    const normalizedDraft = inputMessage.trim();
    if (normalizedDraft) {
      window.localStorage.setItem(draftStorageKey, inputMessage);
    } else {
      window.localStorage.removeItem(draftStorageKey);
    }
  }, [draftStorageKey, inputMessage]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    window.localStorage.setItem(AUTO_SPEAK_STORAGE_KEY, autoSpeakEnabled ? "1" : "0");
  }, [autoSpeakEnabled]);

  const status = statusMap[interview?.status ?? 0] || statusMap[0];
  const isStarted = interview?.status === 1;
  const isEnded = interview?.status === 2;
  const isPaused = interview?.status === 3;
  const report = interview?.parsedReport;
  const expectedRounds = interview?.expectedRounds || report?.expectedRounds || 5;
  const currentRound = interview?.currentRound || report?.completedRounds || 0;
  const depthMeta = getInterviewDepthMeta(expectedRounds);
  const progressPercent = Math.min(100, Math.round((currentRound / Math.max(1, expectedRounds)) * 100));

  const latestQuestionMessage = useMemo(() => {
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      const item = messages[i];
      if (item.isAI && (item.stage === "question" || item.stage === "probe" || item.stage === "resume")) {
        return item;
      }
    }
    return null;
  }, [messages]);

  const latestRoundRecord = useMemo(() => {
    const roundRecords = report?.roundRecords || [];
    return roundRecords.length ? roundRecords[roundRecords.length - 1] : null;
  }, [report?.roundRecords]);

  const activeAgendaRound = useMemo(() => {
    if (isEnded) {
      return 0;
    }
    if (latestQuestionMessage?.round && latestQuestionMessage.round > 0) {
      return Math.min(expectedRounds, latestQuestionMessage.round);
    }
    return Math.min(expectedRounds, Math.max(1, currentRound + 1));
  }, [currentRound, expectedRounds, isEnded, latestQuestionMessage?.round]);

  const latestSpeakableMessage = useMemo(() => {
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      const item = messages[i];
      if (item.isAI && ["question", "probe", "resume", "summary"].includes(item.stage || "")) {
        return item;
      }
    }
    return null;
  }, [messages]);

  const shouldPauseQuestionTimer = submitting && isStarted && !isEnded;

  useEffect(() => {
    if (!latestQuestionMessage?.timestamp || isEnded || !isStarted) {
      setQuestionElapsedSeconds(0);
      return;
    }
    if (shouldPauseQuestionTimer) {
      return;
    }
    const updateElapsed = () => {
      setQuestionElapsedSeconds(Math.max(0, Math.floor((Date.now() - latestQuestionMessage.timestamp) / 1000)));
    };
    updateElapsed();
    const timer = window.setInterval(updateElapsed, 1000);
    return () => window.clearInterval(timer);
  }, [isEnded, isStarted, latestQuestionMessage?.timestamp, shouldPauseQuestionTimer]);

  const stopSpeaking = useCallback(() => {
    speakingTaskIdRef.current += 1;
    speechAbortControllerRef.current?.abort();
    speechAbortControllerRef.current = null;
    const activeAudio = activeAudioRef.current;
    if (activeAudio) {
      activeAudio.pause();
      activeAudio.src = "";
      activeAudioRef.current = null;
    }
    setVoiceStatus((current) => (current.startsWith("豆包语音") || current.includes("自动播放") ? "" : current));
  }, []);

  const releaseAudioRecorderResources = useCallback(() => {
    const recorder = mediaRecorderRef.current;
    if (recorder) {
      recorder.ondataavailable = null;
      recorder.onstop = null;
      recorder.onerror = null;
      mediaRecorderRef.current = null;
    }
    mediaStreamRef.current?.getTracks().forEach((track) => track.stop());
    mediaStreamRef.current = null;
    recordedChunksRef.current = [];
  }, []);

  const speakText = useCallback(async (content?: string, manual = false) => {
    if (!content || typeof window === "undefined" || typeof window.Audio === "undefined" || !interview?.id) {
      return;
    }
    stopSpeaking();
    const taskId = speakingTaskIdRef.current;
    const segments = splitSpeechText(content);
    if (!segments.length) {
      return;
    }
    setVoiceStatus(segments.length > 1 ? "豆包语音正在分段播报..." : "豆包语音正在播报...");
    try {
      for (let index = 0; index < segments.length; index += 1) {
        if (speakingTaskIdRef.current !== taskId) {
          return;
        }
        const abortController = new AbortController();
        speechAbortControllerRef.current = abortController;
        const audioBlob = await synthesizeMockInterviewSpeechUsingPost(
          {
            id: interview.id,
            text: segments[index],
          },
          abortController.signal,
        );
        if (speakingTaskIdRef.current !== taskId) {
          return;
        }
        const audioUrl = URL.createObjectURL(audioBlob);
        try {
          await new Promise<void>((resolve, reject) => {
            const audio = new Audio(audioUrl);
            activeAudioRef.current = audio;
            audio.onended = () => resolve();
            audio.onpause = () => {
              if (speakingTaskIdRef.current !== taskId || audio.ended) {
                resolve();
              }
            };
            audio.onerror = () => reject(new Error("音频播放失败"));
            audio.play().then(() => {
              if (segments.length > 1) {
                setVoiceStatus(`豆包语音播报中（${index + 1}/${segments.length}）...`);
              }
            }).catch(reject);
          });
        } finally {
          URL.revokeObjectURL(audioUrl);
          if (activeAudioRef.current) {
            activeAudioRef.current.onended = null;
            activeAudioRef.current.onpause = null;
            activeAudioRef.current.onerror = null;
            activeAudioRef.current = null;
          }
        }
      }
      setVoiceStatus("");
    } catch (error: any) {
      if (error?.name === "AbortError" || speakingTaskIdRef.current !== taskId) {
        return;
      }
      const blocked = error?.name === "NotAllowedError";
      const errorText = blocked && !manual
        ? "浏览器拦截了自动播放，请点击“播报当前题目”手动收听"
        : error?.message || "豆包语音播报失败";
      setVoiceStatus(errorText);
      if (manual || !blocked) {
        message.error(errorText);
      }
    } finally {
      if (speechAbortControllerRef.current?.signal.aborted) {
        speechAbortControllerRef.current = null;
      }
      if (speakingTaskIdRef.current === taskId) {
        speechAbortControllerRef.current = null;
      }
    }
  }, [interview?.id, stopSpeaking]);

  useEffect(() => {
    if (!autoSpeakEnabled || !audioPlaybackSupported || !latestSpeakableMessage?.content) {
      return;
    }
    const speakKey = `${latestSpeakableMessage.stage || "ai"}-${latestSpeakableMessage.timestamp}-${latestSpeakableMessage.content}`;
    if (lastSpokenKeyRef.current === speakKey) {
      return;
    }
    lastSpokenKeyRef.current = speakKey;
    void speakText(latestSpeakableMessage.content);
  }, [audioPlaybackSupported, autoSpeakEnabled, latestSpeakableMessage, speakText]);

  useEffect(() => {
    if (!autoSpeakEnabled) {
      stopSpeaking();
    }
  }, [autoSpeakEnabled, stopSpeaking]);

  useEffect(() => {
    if (!messageListRef.current) {
      return;
    }
    messageListRef.current.scrollTo({
      top: messageListRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages.length, streamingReply?.content]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      const hasPendingInterviewActivity = Boolean(inputMessage.trim())
        || isListening
        || isRecording
        || (submitting && Boolean(streamAbortControllerRef.current));
      if (!hasPendingInterviewActivity) {
        return;
      }
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [inputMessage, isListening, isRecording, submitting]);

  const cancelStreaming = useCallback((reason = "已停止当前实时输出，稍后会刷新面试结果。") => {
    if (streamAbortControllerRef.current) {
      streamAbortControllerRef.current.abort();
      streamAbortControllerRef.current = null;
    }
    setStreamingReply(null);
    setSubmitting(false);
    setStreamStatus(reason);
    if (typeof window !== "undefined") {
      if (refreshAfterAbortTimerRef.current) {
        window.clearTimeout(refreshAfterAbortTimerRef.current);
      }
      refreshAfterAbortTimerRef.current = window.setTimeout(() => {
        void loadInterview(true);
        setStreamStatus("");
        refreshAfterAbortTimerRef.current = null;
      }, 1200);
    }
  }, [loadInterview]);

  const stopVoiceInput = useCallback(() => {
    if (!speechRecognitionRef.current) {
      setIsListening(false);
      return;
    }
    speechRecognitionRef.current.stop();
    speechRecognitionRef.current = null;
    setIsListening(false);
    setVoiceStatus("已停止语音输入");
  }, []);

  const transcribeRecordedAudio = useCallback(
    async (audioBlob: Blob, mimeType?: string) => {
      if (!interview?.id) {
        return;
      }
      setIsTranscribing(true);
      setVoiceStatus("正在上传录音并转写...");
      try {
        const extension = getAudioFileExtension(mimeType);
        const transcript = await transcribeMockInterviewAudioUsingPost(
          interview.id,
          audioBlob,
          `mock-interview-answer.${extension}`,
        );
        const cleanTranscript = transcript.trim();
        if (!cleanTranscript) {
          throw new Error("没有识别到有效语音内容");
        }
        stopSpeaking();
        setInputMessage((prev) => (prev.trim() ? `${prev.trim()}\n${cleanTranscript}` : cleanTranscript));
        setVoiceStatus("录音转写完成，已回填到输入框");
        message.success("录音转写完成");
      } catch (error: any) {
        const errorText = error?.message || "录音转写失败";
        setVoiceStatus(errorText);
        message.error(errorText);
      } finally {
        setIsTranscribing(false);
      }
    },
    [interview?.id, stopSpeaking],
  );

  const stopAudioRecording = useCallback(() => {
    const recorder = mediaRecorderRef.current;
    if (!recorder) {
      setIsRecording(false);
      return;
    }
    if (recorder.state !== "inactive") {
      setVoiceStatus("录音已结束，正在转写...");
      recorder.stop();
    }
    setIsRecording(false);
  }, []);

  const startVoiceInput = useCallback(() => {
    const SpeechRecognition = getSpeechRecognitionConstructor();
    if (!SpeechRecognition) {
      message.warning("当前浏览器不支持语音输入");
      return;
    }
    stopSpeaking();
    if (submitting && streamAbortControllerRef.current) {
      cancelStreaming("检测到你开始作答，已停止面试官实时输出。");
    }
    speechRecognitionRef.current?.abort();
    const recognition = new SpeechRecognition();
    voiceBaseInputRef.current = inputMessage.trim();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.onresult = (event) => {
      let finalTranscript = "";
      let interimTranscript = "";
      for (let i = 0; i < event.results.length; i += 1) {
        const result = event.results[i];
        const transcript = result[0]?.transcript?.trim() || "";
        if (!transcript) {
          continue;
        }
        if (result.isFinal) {
          finalTranscript += `${transcript} `;
        } else {
          interimTranscript += `${transcript} `;
        }
      }
      const parts = [
        voiceBaseInputRef.current,
        finalTranscript.trim(),
        interimTranscript.trim(),
      ].filter(Boolean);
      setInputMessage(parts.join(" "));
    };
    recognition.onerror = (event) => {
      const errorText = event?.error === "not-allowed" ? "麦克风权限被拒绝" : "语音输入不可用";
      setVoiceStatus(errorText);
      setIsListening(false);
      speechRecognitionRef.current = null;
    };
    recognition.onend = () => {
      setIsListening(false);
      if (speechRecognitionRef.current === recognition) {
        speechRecognitionRef.current = null;
      }
      setVoiceStatus((current) => (current.includes("拒绝") || current.includes("不可用") ? current : "已结束语音输入"));
    };
    recognition.start();
    speechRecognitionRef.current = recognition;
    setIsListening(true);
    setVoiceStatus("正在听你说话...");
  }, [cancelStreaming, inputMessage, stopSpeaking, submitting]);

  const startAudioRecording = useCallback(async () => {
    if (typeof window === "undefined" || typeof MediaRecorder === "undefined" || !navigator.mediaDevices?.getUserMedia) {
      message.warning("当前浏览器不支持录音转写");
      return;
    }
    stopSpeaking();
    if (isListening) {
      stopVoiceInput();
    }
    if (submitting && streamAbortControllerRef.current) {
      cancelStreaming("检测到你开始录音作答，已停止面试官实时输出。");
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = getPreferredAudioMimeType();
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
      recordMimeTypeRef.current = recorder.mimeType || mimeType || "audio/webm";
      recordedChunksRef.current = [];
      mediaStreamRef.current = stream;
      recorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          recordedChunksRef.current.push(event.data);
        }
      };
      recorder.onerror = () => {
        releaseAudioRecorderResources();
        setIsRecording(false);
        setVoiceStatus("录音失败，请检查麦克风权限后重试");
      };
      recorder.onstop = () => {
        const audioBlob = new Blob(recordedChunksRef.current, {
          type: recordMimeTypeRef.current || "audio/webm",
        });
        releaseAudioRecorderResources();
        if (!audioBlob.size) {
          setVoiceStatus("没有录到有效音频内容");
          return;
        }
        void transcribeRecordedAudio(audioBlob, audioBlob.type);
      };
      recorder.start(250);
      mediaRecorderRef.current = recorder;
      setIsRecording(true);
      setVoiceStatus("正在录音，结束后会自动转写");
    } catch (error: any) {
      releaseAudioRecorderResources();
      const errorText = error?.name === "NotAllowedError" ? "麦克风权限被拒绝" : "无法开始录音";
      setVoiceStatus(errorText);
      message.error(errorText);
    }
  }, [cancelStreaming, isListening, releaseAudioRecorderResources, stopSpeaking, stopVoiceInput, submitting, transcribeRecordedAudio]);

  const handleEvent = useCallback(
    async (eventType: "start" | "chat" | "pause" | "resume" | "end", msg?: string) => {
      if (!interview?.id) {
        return false;
      }
      if (refreshAfterAbortTimerRef.current && typeof window !== "undefined") {
        window.clearTimeout(refreshAfterAbortTimerRef.current);
        refreshAfterAbortTimerRef.current = null;
      }
      setSubmitting(true);
      setStreamStatus(eventType === "chat" ? "面试官正在组织下一轮问题..." : "正在更新面试状态...");
      setStreamingReply(null);
      stopSpeaking();
      const abortController = new AbortController();
      streamAbortControllerRef.current = abortController;

      let hasStreamed = false;
      try {
        await streamMockInterviewEventUsingPost(
          {
            event: eventType,
            id: interview.id,
            message: msg,
          },
          async (event, payload) => {
            hasStreamed = true;
            if (event === "status") {
              setStreamStatus(payload?.message || "");
              return;
            }
            if (event === "delta") {
              setStreamStatus("面试官正在输出回复...");
              setStreamingReply({
                content: payload?.accumulated || "",
                round: payload?.round,
                stage: payload?.stage,
              });
              return;
            }
            if (event === "done") {
              if (payload?.interview) {
                applyInterviewData(payload.interview as API.MockInterview);
              } else {
                await loadInterview(true);
              }
              streamAbortControllerRef.current = null;
              setStreamingReply(null);
              setStreamStatus("");
              return;
            }
            if (event === "error") {
              throw new Error(payload?.message || "流式面试处理失败");
            }
          },
          abortController.signal,
        );

        if (eventType === "start") {
          message.success("面试已开始");
        }
        if (eventType === "pause") {
          message.success("已暂停当前面试");
        }
        if (eventType === "resume") {
          message.success("已继续面试");
        }
        if (eventType === "end") {
          message.success("已生成最终面试报告");
        }
        return true;
      } catch (error: any) {
        if (abortController.signal.aborted) {
          return false;
        }
        setStreamingReply(null);
        setStreamStatus("");
        if (hasStreamed) {
          await loadInterview(true);
          message.warning(error?.message || "流式输出中断，已为你刷新最新面试结果");
          return false;
        }
        try {
          await handleMockInterviewEventUsingPost({
            event: eventType,
            id: interview.id,
            message: msg,
          });
          await loadInterview(true);
          if (eventType === "start") {
            message.success("面试已开始");
          }
          if (eventType === "pause") {
            message.success("已暂停当前面试");
          }
          if (eventType === "resume") {
            message.success("已继续面试");
          }
          if (eventType === "end") {
            message.success("已生成最终面试报告");
          }
          return true;
        } catch (fallbackError: any) {
          message.error(fallbackError?.message || "操作失败");
          return false;
        }
      } finally {
        if (streamAbortControllerRef.current === abortController) {
          streamAbortControllerRef.current = null;
        }
        setSubmitting(false);
      }
    },
    [applyInterviewData, interview?.id, loadInterview, stopSpeaking],
  );

  const sendMessage = async () => {
    if (!inputMessage.trim() || submitting || isTranscribing) {
      return;
    }
    const nextAnswer = inputMessage.trim();
    if (nextAnswer.length > MAX_ANSWER_LENGTH) {
      message.error(`回答内容过长，请控制在 ${MAX_ANSWER_LENGTH} 字以内`);
      return;
    }
    if (isListening) {
      stopVoiceInput();
    }
    if (isRecording) {
      stopAudioRecording();
      return;
    }
    const success = await handleEvent("chat", nextAnswer);
    if (success) {
      setInputMessage("");
      setVoiceStatus("");
    }
  };

  const handleExportReview = async () => {
    if (!interview?.id) {
      return;
    }
    setExporting(true);
    try {
      const { blob, fileName } = await downloadMockInterviewReviewUsingGet(interview.id);
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(objectUrl);
      message.success("逐题复盘已导出");
    } catch (error: any) {
      message.error(error?.message || "导出复盘失败");
    } finally {
      setExporting(false);
    }
  };

  const infoItems = [
    {
      icon: <Briefcase size={14} />,
      label: interview?.jobPosition || "未命名岗位",
    },
    {
      icon: <Flag size={14} />,
      label: interview?.interviewType || "技术深挖",
    },
    {
      icon: <Clock3 size={14} />,
      label: `${interview?.workExperience || "经验不限"} / ${interview?.difficulty || "中等"}`,
    },
    {
      icon: <Radar size={14} />,
      label: interview?.techStack || "通用后端",
    },
  ];

  const recommendedAnswerSeconds = report?.recommendedAnswerSeconds || latestRoundRecord?.recommendedAnswerSeconds || 120;
  const currentFocus = report?.currentFocus || latestRoundRecord?.focus || "开始面试后这里会显示当前考察重点";
  const currentQuestionStyle = report?.currentQuestionStyle || latestRoundRecord?.questionStyle || "真实面试追问";
  const currentActionHint = report?.nextActionHint || "建议用背景、方案、结果和复盘的结构组织回答。";
  const hiringRecommendation = report?.hiringRecommendation || "";
  const riskPoints = report?.riskPoints || [];
  const nextInterviewFocus = report?.nextInterviewFocus || [];
  const agendaItems = useMemo(() => report?.agenda || [], [report?.agenda]);
  const roundRecords = useMemo(() => report?.roundRecords || [], [report?.roundRecords]);
  const latestQuestionText = latestQuestionMessage?.content || "面试官会在这里给出当前问题。";
  const answerTimePercent = Math.min(
    100,
    Math.round((questionElapsedSeconds / Math.max(1, recommendedAnswerSeconds)) * 100),
  );
  const answerOvertime = questionElapsedSeconds > recommendedAnswerSeconds;
  const canAnswer = isStarted && !isEnded;
  const isStreaming = submitting && Boolean(streamAbortControllerRef.current);
  const hasSideRail = !isEnded && agendaItems.length > 0;
  const canSendAnswer = canAnswer
    && Boolean(inputMessage.trim())
    && !submitting
    && !isRecording
    && !isTranscribing;

  useEffect(() => {
    if (!roundRecords.length) {
      setExpandedRoundKeys([]);
      setExpandedRoundTextKeys([]);
      return;
    }
    const latestIndex = roundRecords.length - 1;
    const latestRecord = roundRecords[latestIndex];
    setExpandedRoundKeys([`${latestRecord.round || "round"}-${latestIndex}`]);
    setExpandedRoundTextKeys([]);
  }, [roundRecords]);

  const toggleRoundRecord = useCallback((key: string) => {
    setExpandedRoundKeys((prev) => (
      prev.includes(key) ? prev.filter((item) => item !== key) : [...prev, key]
    ));
  }, []);

  const expandAllRoundRecords = useCallback(() => {
    setExpandedRoundKeys(roundRecords.map((item, index) => `${item.round || "round"}-${index}`));
  }, [roundRecords]);

  const collapseAllRoundRecords = useCallback(() => {
    setExpandedRoundKeys([]);
  }, []);

  const toggleRoundText = useCallback((key: string) => {
    setExpandedRoundTextKeys((prev) => (
      prev.includes(key) ? prev.filter((item) => item !== key) : [...prev, key]
    ));
  }, []);

  const buildCollapsedPreview = useCallback((text?: string | null, limit = 220) => {
    const normalized = (text || "").replace(/\s+/g, " ").trim();
    if (!normalized) {
      return "";
    }
    if (normalized.length <= limit) {
      return normalized;
    }
    return `${normalized.slice(0, limit).trimEnd()}...`;
  }, []);

  const renderExpandableRoundBlock = useCallback((
    roundKey: string,
    sectionKey: string,
    label: string,
    content: string | undefined,
    className: string,
    emptyText: string,
    limit: number,
  ) => {
    const normalized = (content || "").trim();
    const textKey = `${roundKey}:${sectionKey}`;
    const shouldCollapse = normalized.length > limit;
    const isExpandedText = expandedRoundTextKeys.includes(textKey);
    const displayText = shouldCollapse && !isExpandedText
      ? buildCollapsedPreview(normalized, limit)
      : (normalized || emptyText);
    return (
      <div className="review-round-block">
        <div className="focus-label">{label}</div>
        <div className={className}>{displayText}</div>
        {shouldCollapse ? (
          <Button
            type="text"
            className="record-text-toggle"
            onClick={() => toggleRoundText(textKey)}
          >
            {isExpandedText ? "收起全文" : "展开全文"}
          </Button>
        ) : null}
      </div>
    );
  }, [buildCollapsedPreview, expandedRoundTextKeys, toggleRoundText]);

  if (loading) {
    return (
      <div id="interviewRoomPage" className="max-width-content">
        <div className="space-y-4">
          <Skeleton active paragraph={{ rows: 4 }} />
          <Skeleton active paragraph={{ rows: 8 }} />
        </div>
      </div>
    );
  }

  if (!interview) {
    return (
      <div id="interviewRoomPage" className="max-width-content">
        <div className="interview-empty-state">
          <div className="interview-empty-illustration">
            <BrainCircuit size={28} />
          </div>
          <Title level={3} className="!mb-2 !mt-0">
            这场模拟面试不存在或暂时无法访问
          </Title>
          <Paragraph className="interview-empty-text">
            可能是记录已被删除、当前账号没有权限查看，或者链接已经失效。你可以回到模拟面试列表重新进入，或者直接发起一场新的面试。
          </Paragraph>
          <div className="interview-empty-actions">
            <Link href="/mockInterview">
              <Button className="action-button secondary">返回模拟面试</Button>
            </Link>
            <Link href="/mockInterview/add">
              <Button type="primary" className="action-button">发起新面试</Button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div id="interviewRoomPage" className="max-width-content">
      <div className={`interview-shell ${hasSideRail ? "with-side" : "without-side"}`}>
        <section className="interview-main">
          <Card className="hero-card">
            <div className="hero-header">
              <div>
                <div className="hero-eyebrow">
                  <BrainCircuit size={14} />
                  AI Mock Interview
                </div>
                <Title level={2} className="!mb-2 !mt-4 !text-slate-900">
                  模拟面试 #{interview.id}
                </Title>
                <Paragraph className="!mb-0 text-slate-500">
                  这一轮会尽量按真实面试节奏追问。你可以直接说话作答，也可以开启自动播报来模拟面试官口头发问。
                </Paragraph>
              </div>
              <Tag color={status.color} className="status-tag">
                {status.text}
              </Tag>
            </div>

            <div className="hero-meta">
              {infoItems.map((item) => (
                <span className="meta-pill" key={item.label}>
                  {item.icon}
                  {item.label}
                </span>
              ))}
            </div>

            <div className="hero-actions">
              <Link href={`/mockInterview/add?from=${interview.id}`}>
                <Button className="action-button secondary">
                  <RefreshCw size={16} />
                  再来一场
                </Button>
              </Link>
              <Button
                type="primary"
                onClick={() => void handleEvent("start")}
                disabled={isStarted || isPaused || isEnded}
                loading={submitting}
                className="action-button"
              >
                开始面试
              </Button>
              <Button
                onClick={() => void handleEvent("pause")}
                disabled={!isStarted || isPaused || isEnded}
                loading={submitting}
                className="action-button secondary"
              >
                暂停面试
              </Button>
              <Button
                type="primary"
                onClick={() => void handleEvent("resume")}
                disabled={!isPaused || isEnded}
                loading={submitting}
                className="action-button"
              >
                继续面试
              </Button>
              <Popconfirm
                title={currentRound < expectedRounds ? "还没走完主要考察主题，确定结束吗？" : "确定结束并生成报告？"}
                description={
                  currentRound < expectedRounds
                    ? `当前只完成 ${currentRound}/${expectedRounds} 个主要考察主题，报告可信度会受影响。`
                    : "结束后会生成最终复盘，当前会话将不能继续作答。"
                }
                okText="确认结束"
                cancelText="继续面试"
                okButtonProps={{ danger: true }}
                disabled={(!isStarted && !isPaused) || isEnded || submitting}
                onConfirm={() => void handleEvent("end")}
              >
                <Button
                  danger
                  disabled={(!isStarted && !isPaused) || isEnded}
                  loading={submitting}
                  className="action-button"
                >
                  结束并生成报告
                </Button>
              </Popconfirm>
              <Button
                onClick={handleExportReview}
                disabled={!isEnded}
                loading={exporting}
                className="action-button secondary"
              >
                <Download size={16} />
                导出逐题复盘
              </Button>
            </div>
          </Card>

          <div className="context-grid">
            <Card className="side-card context-card">
              <div className="section-heading compact">
                <div>
                  <div className="section-eyebrow">Progress</div>
                  <Title level={5} className="!mb-0 !mt-2">
                    模拟进度
                  </Title>
                </div>
                <span className="score-pill">{progressPercent}%</span>
              </div>
              <Progress percent={progressPercent} showInfo={false} strokeColor="#1677ff" />
              <div className="metric-grid">
                <div className="metric-card">
                  <span>面试深度</span>
                  <strong>{depthMeta.label}</strong>
                </div>
                <div className="metric-card">
                  <span>预计时长</span>
                  <strong>{depthMeta.durationText}</strong>
                </div>
                <div className="metric-card">
                  <span>已完成主题</span>
                  <strong>{currentRound}/{expectedRounds}</strong>
                </div>
                <div className="metric-card">
                  <span>当前状态</span>
                  <strong>{status.text}</strong>
                </div>
              </div>
            </Card>

            <Card className="side-card context-card">
              <div className="section-heading compact">
                <div>
                  <div className="section-eyebrow">Live Cue</div>
                  <Title level={5} className="!mb-0 !mt-2">
                    当前考察点
                  </Title>
                </div>
                <span className="score-pill subtle">
                  {formatDuration(questionElapsedSeconds)}
                </span>
              </div>
              <div className="live-cue-panel">
                <div className="cue-tag">{currentQuestionStyle}</div>
                <div className="cue-focus">{currentFocus}</div>
                <div className="cue-hint">{currentActionHint}</div>
                {isPaused ? (
                  <div className="cue-paused-banner">面试已暂停，继续后会从当前考察点接着追问。</div>
                ) : null}
              </div>
            </Card>
          </div>

          <Card className="message-card">
            <div className="section-heading">
              <div>
                <div className="section-eyebrow">Interview Flow</div>
                <Title level={4} className="!mb-0 !mt-2">
                  面试过程
                </Title>
              </div>
              <Text className="text-slate-400">
                当前已完成 {currentRound} / {expectedRounds} 个主要考察主题
              </Text>
            </div>

            <div className="message-list" ref={messageListRef}>
              {messages.length ? (
                <>
                  <List
                    dataSource={messages}
                    split={false}
                    renderItem={(item) => (
                      <List.Item
                        className={item.isAI ? "message-row ai" : "message-row user"}
                      >
                        <div className={`message-shell ${item.isAI ? "ai" : "user"}`}>
                          <div className={`message-avatar ${item.isAI ? "ai" : "user"}`}>
                            {item.isAI ? <BrainCircuit size={16} /> : "你"}
                          </div>
                          <div className={`message-stack ${item.isAI ? "ai" : "user"}`}>
                            <div className="message-meta">
                              <span className="speaker">{item.isAI ? "面试官" : "候选人"}</span>
                              {formatMessageStage(item.stage) ? (
                                <span className={`stage-tag ${item.stage || ""}`}>{formatMessageStage(item.stage)}</span>
                              ) : null}
                              {item.round ? <span className="round-tag">第 {item.round} 轮</span> : null}
                              <span className="message-time">{formatTime(item.timestamp)}</span>
                            </div>
                            <div className={`message-bubble ${item.isAI ? "ai" : "user"}`}>
                              <div className="message-content">{item.content}</div>
                            </div>
                          </div>
                        </div>
                      </List.Item>
                    )}
                  />
                  {streamingReply?.content ? (
                    <div className="message-row ai">
                      <div className="message-shell ai">
                        <div className="message-avatar ai">
                          <BrainCircuit size={16} />
                        </div>
                        <div className="message-stack ai">
                          <div className="message-meta">
                            <span className="speaker">面试官输入中</span>
                            {formatMessageStage(streamingReply.stage) ? (
                              <span className={`stage-tag ${streamingReply.stage || ""}`}>{formatMessageStage(streamingReply.stage)}</span>
                            ) : null}
                            {streamingReply.round ? (
                              <span className="round-tag">第 {streamingReply.round} 轮</span>
                            ) : null}
                          </div>
                          <div className="message-bubble ai streaming">
                            <div className="message-content">{streamingReply.content}</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : null}
                  {submitting && isStarted && !isEnded && !streamingReply?.content ? (
                    <div className="thinking-card">
                      <div className="thinking-dot" />
                      <div className="thinking-dot" />
                      <div className="thinking-dot" />
                      <span>{streamStatus || "面试官正在整理下一轮追问..."}</span>
                    </div>
                  ) : null}
                </>
              ) : (
                <Empty description="还没有开始这场面试" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </div>

            <div className="input-area">
              {canAnswer && latestQuestionMessage ? (
                <div className="active-question-panel">
                  <div className="active-question-head">
                    <div>
                      <span className="active-question-kicker">Current Question</span>
                      <strong>第 {activeAgendaRound} 轮 · {currentQuestionStyle}</strong>
                    </div>
                    <span className={`active-question-timer ${answerOvertime ? "over" : ""}`}>
                      <Clock3 size={14} />
                      {formatDuration(questionElapsedSeconds)} / {formatDuration(recommendedAnswerSeconds)}
                    </span>
                  </div>
                  <div className="active-question-text">{latestQuestionText}</div>
                  <Progress
                    percent={answerTimePercent}
                    showInfo={false}
                    strokeColor={answerOvertime ? "#f59e0b" : "#1677ff"}
                    trailColor="rgba(203, 213, 225, 0.8)"
                  />
                </div>
              ) : null}
              <Input.TextArea
                value={inputMessage}
                onChange={(e) => {
                  stopSpeaking();
                  setInputMessage(e.target.value);
                }}
                placeholder="输入你的回答。建议尽量具体，给出业务背景、技术方案、结果指标和复盘。"
                disabled={!canAnswer}
                rows={4}
                maxLength={MAX_ANSWER_LENGTH}
                showCount
                onFocus={() => {
                  stopSpeaking();
                }}
                onPressEnter={(e) => {
                  if ((e.nativeEvent as KeyboardEvent).isComposing) {
                    return;
                  }
                  if (submitting || isTranscribing) {
                    e.preventDefault();
                    return;
                  }
                  if (!e.shiftKey) {
                    e.preventDefault();
                    void sendMessage();
                  }
                }}
              />
              <div className="input-toolbar">
                <div className="tool-group">
                  <Button
                    onClick={isListening ? stopVoiceInput : startVoiceInput}
                    disabled={!speechRecognitionSupported || !canAnswer || isRecording || isTranscribing}
                    className="tool-button"
                  >
                    {isListening ? <MicOff size={16} /> : <Mic size={16} />}
                    {isListening ? "停止收音" : "语音输入"}
                  </Button>
                  <Button
                    disabled={!speechRecognitionSupported || !canAnswer || isRecording || isTranscribing}
                    className={`tool-button ${isListening ? "active" : ""}`}
                    onMouseDown={(e) => {
                      e.preventDefault();
                      if (!isListening) {
                        startVoiceInput();
                      }
                    }}
                    onMouseUp={stopVoiceInput}
                    onMouseLeave={() => {
                      if (isListening) {
                        stopVoiceInput();
                      }
                    }}
                    onTouchStart={(e) => {
                      e.preventDefault();
                      if (!isListening) {
                        startVoiceInput();
                      }
                    }}
                    onTouchEnd={(e) => {
                      e.preventDefault();
                      stopVoiceInput();
                    }}
                  >
                    <Mic size={16} />
                    按住说话
                  </Button>
                  <Button
                    onClick={isRecording ? stopAudioRecording : () => void startAudioRecording()}
                    disabled={!audioRecordingSupported || !canAnswer || isListening || isTranscribing}
                    className={`tool-button ${isRecording ? "active" : ""}`}
                  >
                    {isRecording ? <Square size={16} /> : <Mic size={16} />}
                    {isTranscribing ? "转写中" : isRecording ? "停止录音" : "录音转写"}
                  </Button>
                  <Button
                    onClick={() => {
                      setAutoSpeakEnabled((prev) => !prev);
                    }}
                    disabled={!audioPlaybackSupported}
                    className="tool-button"
                  >
                    {autoSpeakEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
                    {autoSpeakEnabled ? "关闭自动播报" : "开启自动播报"}
                  </Button>
                  <Button
                    onClick={() => {
                      void speakText(latestSpeakableMessage?.content, true);
                    }}
                    disabled={!audioPlaybackSupported || !latestSpeakableMessage?.content}
                    className="tool-button"
                  >
                    <Volume2 size={16} />
                    播报当前题目
                  </Button>
                  {isStreaming ? (
                    <Button
                      danger
                      onClick={() => {
                        cancelStreaming();
                      }}
                      className="tool-button"
                    >
                      <Square size={16} />
                      停止实时输出
                    </Button>
                  ) : null}
                </div>
                <Button
                  type="primary"
                  onClick={() => void sendMessage()}
                  loading={submitting}
                  disabled={!canSendAnswer}
                  className="send-button"
                >
                  发送回答
                </Button>
              </div>
              {voiceStatus ? <div className="voice-status">{voiceStatus}</div> : null}
              {streamStatus && submitting ? <div className="stream-status">{streamStatus}</div> : null}
              {isPaused ? <div className="pause-tip">当前面试已暂停，点击“继续面试”后才能继续回答。</div> : null}
            </div>
          </Card>
        </section>

        {hasSideRail ? (
          <aside className="interview-side">
            {agendaItems.length ? (
              <Card className="side-card">
                <div className="section-heading compact">
                  <div>
                    <div className="section-eyebrow">Interview Agenda</div>
                    <Title level={5} className="!mb-0 !mt-2">
                      面试议程
                    </Title>
                  </div>
                </div>
                <div className="agenda-list">
                  {agendaItems.map((item) => {
                    const isActive = !isEnded && item.round === activeAgendaRound;
                    const isCompleted = (item.round || 0) <= currentRound;
                    return (
                      <div
                        key={`${item.round}-${item.label}`}
                        className={`agenda-item ${isActive ? "active" : ""} ${isCompleted ? "done" : ""}`}
                      >
                        <div className="agenda-index">{item.round}</div>
                        <div className="agenda-content">
                          <div className="agenda-title">{item.label}</div>
                          <div className="agenda-desc">{item.focusTopic}</div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>
            ) : null}
          </aside>
        ) : null}
      </div>

      <section className="review-board-section">
        <Card className="review-board-card">
          <div className="section-heading">
            <div>
              <div className="section-eyebrow">Interview Review</div>
              <Title level={4} className="!mb-0 !mt-2">
                最终报告与逐轮记录
              </Title>
            </div>
            <Sparkles size={18} className="text-amber-500" />
          </div>

          {isEnded && report ? (
            <div className="review-board-content">
              <div className="review-hero">
                <div className="review-score-stack">
                  <div className="overall-score">{report.overallScore || 0}</div>
                  <div className="report-label">综合评分 / 100</div>
                </div>
                <div className="review-summary-panel">
                  <Paragraph className="!mb-0 text-slate-600">
                    {report.summary || "面试总结已生成。"}
                  </Paragraph>
                  <div className="review-summary-meta">
                    {report.readinessLevel ? (
                      <div className="readiness-pill">{report.readinessLevel}</div>
                    ) : null}
                    <div className="review-next-action">
                      {report.recommendedNextAction || "继续围绕项目细节、技术取舍和量化结果做口头表达训练。"}
                    </div>
                  </div>
                  <div className="review-stat-grid">
                    <div className="review-stat-card">
                      <span>已完成主题</span>
                      <strong>{currentRound}/{expectedRounds}</strong>
                    </div>
                    <div className="review-stat-card">
                      <span>面试深度</span>
                      <strong>{depthMeta.label}</strong>
                    </div>
                    <div className="review-stat-card">
                      <span>最终状态</span>
                      <strong>{status.text}</strong>
                    </div>
                  </div>
                </div>
              </div>

              <div className="review-dimension-grid">
                {[
                  { label: "表达能力", value: report.communicationScore || 0 },
                  { label: "技术深度", value: report.technicalScore || 0 },
                  { label: "问题分析", value: report.problemSolvingScore || 0 },
                ].map((item) => (
                  <div className="dimension-item" key={item.label}>
                    <div className="dimension-head">
                      <span>{item.label}</span>
                      <strong>{item.value}</strong>
                    </div>
                    <Progress percent={item.value} showInfo={false} strokeColor="#1677ff" />
                  </div>
                ))}
              </div>

              {hiringRecommendation ? (
                <div className="review-decision-panel">
                  <div className="review-decision-title">面试官结论</div>
                  <div className="review-decision-text">{hiringRecommendation}</div>
                </div>
              ) : null}

              {agendaItems.length ? (
                <div className="review-agenda-panel">
                  <div className="block-title">本场议程</div>
                  <div className="review-agenda-grid">
                    {agendaItems.map((item) => (
                      <div key={`review-agenda-${item.round}-${item.label}`} className="agenda-item done">
                        <div className="agenda-index">{item.round}</div>
                        <div className="agenda-content">
                          <div className="agenda-title">{item.label}</div>
                          {item.focusTopic ? <div className="agenda-desc">{item.focusTopic}</div> : null}
                          {item.questionStyle || item.recommendedAnswerSeconds ? (
                            <div className="agenda-meta">
                              {item.questionStyle || "真实面试追问"}
                              {item.recommendedAnswerSeconds ? ` · 建议 ${item.recommendedAnswerSeconds}s` : ""}
                            </div>
                          ) : null}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              {riskPoints.length || nextInterviewFocus.length ? (
                <div className="review-extra-grid">
                  {riskPoints.length ? (
                    <div className="review-extra-panel risk">
                      <div className="block-title">主要风险点</div>
                      <ul>
                        {riskPoints.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  {nextInterviewFocus.length ? (
                    <div className="review-extra-panel focus">
                      <div className="block-title">下一场面试重点</div>
                      <div className="topic-list">
                        {nextInterviewFocus.map((item) => (
                          <span className="topic-tag strong" key={item}>
                            {item}
                          </span>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : null}

              <div className="review-detail-grid">
                <div className="review-detail-panel">
                  <div className="block-title">亮点</div>
                  <ul>
                    {(report.strengths || []).map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
                <div className="review-detail-panel">
                  <div className="block-title">改进建议</div>
                  <ul>
                    {(report.improvements || []).map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </div>
              </div>
              {(report.suggestedTopics || []).length ? (
                <div className="review-topic-panel">
                  <div className="block-title">建议继续准备</div>
                  <div className="topic-list">
                    {(report.suggestedTopics || []).map((item) => (
                      <span className="topic-tag" key={item}>
                        {item}
                      </span>
                    ))}
                  </div>
                </div>
              ) : null}
              {(report.practicePlan || []).length ? (
                <div className="practice-plan-panel">
                  <div className="block-title">下一步训练计划</div>
                  <div className="practice-plan-list">
                    {(report.practicePlan || []).map((item, index) => (
                      <div className="practice-plan-item" key={`${index}-${item}`}>
                        <span>{index + 1}</span>
                        <div>{item}</div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              {roundRecords.length ? (
                <div className="review-round-section">
                  <div className="review-round-header">
                    <div className="block-title review-round-title">逐轮记录</div>
                    <div className="review-round-actions">
                      <Button size="small" className="round-action-button" onClick={expandAllRoundRecords}>
                        全部展开
                      </Button>
                      <Button size="small" className="round-action-button" onClick={collapseAllRoundRecords}>
                        全部收起
                      </Button>
                    </div>
                  </div>
                  <div className="review-round-grid">
                    {roundRecords.map((item, index) => {
                      const roundKey = `${item.round || "round"}-${index}`;
                      const isExpanded = expandedRoundKeys.includes(roundKey);
                      return (
                        <div className={`review-round-card ${isExpanded ? "expanded" : ""}`} key={roundKey}>
                          <div className="review-round-card-head">
                            <div className="record-head">
                              <strong>第 {item.round} 轮</strong>
                              <span>{item.score || 0} / 100</span>
                            </div>
                            <Button
                              type="text"
                              className="round-toggle-button"
                              onClick={() => toggleRoundRecord(roundKey)}
                            >
                              {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                              {isExpanded ? "收起详情" : "展开详情"}
                            </Button>
                          </div>
                          <div className="review-round-summary">
                            {item.questionStyle ? (
                              <div className="record-style">
                                {item.questionStyle} / 建议 {item.recommendedAnswerSeconds || 120}s
                              </div>
                            ) : null}
                            {item.responseSeconds ? (
                              <div className="record-meta">实际作答 {item.responseSeconds}s</div>
                            ) : null}
                          </div>
                          <div className="round-score-mini-grid compact">
                            {buildRoundScoreItems(item).map((scoreItem) => (
                              <div className="round-score-mini" key={`${item.round}-${scoreItem.label}`}>
                                <div className="round-score-mini-head">
                                  <span>{scoreItem.label}</span>
                                  <strong>{scoreItem.value}</strong>
                                </div>
                                <Progress percent={scoreItem.value} showInfo={false} strokeColor="#1677ff" />
                              </div>
                            ))}
                          </div>
                          {item.verdict ? <div className="record-verdict compact">{item.verdict}</div> : null}
                          <div className="record-summary">
                            {item.shortComment || item.interviewerObservation || "这一轮的面试摘要会显示在这里。"}
                          </div>
                          {(item.improvementTags || []).length ? (
                            <div className="record-tags compact">
                              {(item.improvementTags || []).slice(0, 3).map((tag) => (
                                <span className="feedback-tag" key={`${item.round}-${tag}`}>{tag}</span>
                              ))}
                            </div>
                          ) : null}
                          {isExpanded ? (
                            <div className="review-round-detail">
                              {(item.scoreReasons || []).length ? (
                                <div className="score-reason-list compact">
                                  {(item.scoreReasons || []).map((reason) => (
                                    <div className="score-reason" key={`${item.round}-${reason}`}>{reason}</div>
                                  ))}
                                </div>
                              ) : null}
                              {renderExpandableRoundBlock(
                                roundKey,
                                "question",
                                "面试问题",
                                item.question,
                                "record-question",
                                "暂无问题记录",
                                160,
                              )}
                              {renderExpandableRoundBlock(
                                roundKey,
                                "answer",
                                "你的回答",
                                item.answer,
                                "record-answer",
                                "暂无回答记录",
                                260,
                              )}
                              <div className="review-round-block">
                                <div className="focus-label">面试官评语</div>
                                <div className="record-comment">
                                  <span>{item.shortComment || "暂无评语"}</span>
                                  <ChevronRight size={14} />
                                </div>
                              </div>
                              {item.interviewerObservation ? (
                                <div className="review-round-block">
                                  <div className="focus-label">面试官观察</div>
                                  <div className="focus-text">{item.interviewerObservation}</div>
                                </div>
                              ) : null}
                              {(item.improvementTags || []).length ? (
                                <div className="record-tags">
                                  {(item.improvementTags || []).map((tag) => (
                                    <span className="feedback-tag" key={`${item.round}-${tag}`}>{tag}</span>
                                  ))}
                                </div>
                              ) : null}
                              {(item.missingPoints || []).length ? (
                                <div className="record-tags">
                                  {(item.missingPoints || []).map((point) => (
                                    <span className="missing-point" key={`${item.round}-${point}`}>{point}</span>
                                  ))}
                                </div>
                              ) : null}
                              {(item.answerQualitySignals || []).length ? (
                                <div className="review-round-block">
                                  <div className="focus-label">诊断信号</div>
                                  <div className="diagnostic-signal-list">
                                    {(item.answerQualitySignals || []).map((signal) => (
                                      <span className="diagnostic-signal" key={`${item.round}-${signal}`}>{signal}</span>
                                    ))}
                                  </div>
                                </div>
                              ) : null}
                              {item.followUpReason ? (
                                <div className="review-round-block">
                                  <div className="focus-label">追问理由</div>
                                  <div className="focus-text">{item.followUpReason}</div>
                                </div>
                              ) : null}
                              {item.answerRewriteSuggestion ? (
                                renderExpandableRoundBlock(
                                  roundKey,
                                  "rewrite",
                                  "改进版回答骨架",
                                  item.answerRewriteSuggestion,
                                  "record-answer",
                                  "暂无改进版回答骨架",
                                  240,
                                )
                              ) : null}
                            </div>
                          ) : null}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="report-placeholder wide">
              <Paragraph className="!mb-0 text-slate-500">
                面试结束后，这里会展示完整的结构化复盘和逐轮记录。现在右侧会先聚焦当前进度和考察点，避免你在会话进行中被大段反馈打断。
              </Paragraph>
            </div>
          )}
        </Card>
      </section>
    </div>
  );
}
