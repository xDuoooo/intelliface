"use client";

import Link from "next/link";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button, Card, Empty, Input, List, Popconfirm, Progress, Skeleton, Tag, Typography, message } from "antd";
import {
  Briefcase,
  BrainCircuit,
  ChevronRight,
  ClipboardCheck,
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
  streamMockInterviewEventUsingPost,
  transcribeMockInterviewAudioUsingPost,
} from "@/api/mockInterviewController";
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

function buildAnswerCoachHints(answer: string) {
  const normalized = answer.trim();
  if (!normalized) {
    return [];
  }
  const hints: string[] = [];
  if (normalized.length < 40) {
    hints.push("这段回答偏短，最好补上业务背景、你的职责和最终结果。");
  }
  if (!/\d/.test(normalized)) {
    hints.push("如果能补一个量化指标，比如 QPS、耗时、成本或收益，会更像真实面试高质量回答。");
  }
  if (!/(因为|所以|权衡|取舍|方案|设计)/.test(normalized)) {
    hints.push("建议补一句技术选型或设计取舍，面试官会更容易判断你的思考深度。");
  }
  if (!/(复盘|优化|改进|如果重来)/.test(normalized)) {
    hints.push("可以加一句复盘或后续优化方向，这会让回答更完整。");
  }
  return hints.slice(0, 3);
}

function buildAnswerRecoveryHint(answer: string) {
  const normalized = answer.trim();
  if (!normalized) {
    return "";
  }
  if (/(不会|不知道|不清楚|不了解|没接触|没做过|不太懂|答不上来)/.test(normalized)) {
    return "可以诚实说明不熟，但别停在“不会”。补一段你的理解、排查思路、可验证假设和后续学习路径。";
  }
  if (/(可能|大概|应该|也许|好像|不确定)/.test(normalized) && normalized.length < 120) {
    return "这段回答不确定性偏高。建议补一个你亲自验证过的事实、指标或项目经历来托住判断。";
  }
  return "";
}

function buildAnswerCoverageItems(answer: string, questionStyle?: string) {
  const normalized = answer.trim();
  const style = questionStyle || "";
  if (/(架构|设计|扩展|数据|性能|稳定性|安全|成本)/.test(style)) {
    return [
      {
        label: "需求约束",
        matched: /(需求|目标|约束|边界|量级|容量|qps|并发|sla)/i.test(normalized),
      },
      {
        label: "模块数据流",
        matched: /(模块|服务|接口|数据流|链路|表|缓存|队列|消息|存储)/.test(normalized),
      },
      {
        label: "关键取舍",
        matched: /(取舍|权衡|因为|所以|一致性|可用性|延迟|吞吐|成本|复杂度)/.test(normalized),
      },
      {
        label: "风险兜底",
        matched: /(风险|异常|降级|限流|熔断|容灾|监控|告警|回滚)/.test(normalized),
      },
      {
        label: "指标验证",
        matched: /(\d|%|\b(qps|rt|ms|sla|p95|p99)\b|秒|分钟|提升|降低|成本|耗时)/i.test(normalized),
      },
    ];
  }
  if (/(行为|压力|协作|冲突|动机|规划)/.test(style)) {
    return [
      { label: "具体事件", matched: /(有一次|当时|背景|项目|场景|情况|situation)/i.test(normalized) },
      { label: "目标任务", matched: /(目标|任务|问题|挑战|task|需要|希望)/i.test(normalized) },
      { label: "我的行动", matched: /(我|本人|负责|推进|协调|沟通|action|做了)/i.test(normalized) },
      { label: "结果影响", matched: /(结果|最终|影响|收益|提升|降低|完成|result|\d|%)/i.test(normalized) },
      { label: "反思复盘", matched: /(反思|复盘|学到|改进|下次|reflection|如果重来)/i.test(normalized) },
    ];
  }
  if (/原理/.test(style)) {
    return [
      { label: "核心概念", matched: /(概念|本质|核心|原理|机制|是什么)/.test(normalized) },
      { label: "适用场景", matched: /(场景|适用|用于|什么时候|业务|项目)/.test(normalized) },
      { label: "关键机制", matched: /(流程|机制|过程|实现|底层|源码|协议|算法)/.test(normalized) },
      { label: "边界坑点", matched: /(边界|缺点|问题|坑|风险|异常|限制|不适合)/.test(normalized) },
      { label: "项目经验", matched: /(我|项目|线上|排查|实践|使用|落地|优化)/.test(normalized) },
    ];
  }
  if (/(结果|复盘|压测)/.test(style)) {
    return [
      { label: "目标指标", matched: /(目标|指标|基线|qps|rt|p95|p99|成本|耗时|错误率)/i.test(normalized) },
      { label: "我的动作", matched: /(我|负责|推进|优化|调整|改造|压测|定位)/.test(normalized) },
      { label: "最终结果", matched: /(结果|最终|提升|降低|减少|增长|稳定|\d|%)/.test(normalized) },
      { label: "问题风险", matched: /(问题|风险|异常|瓶颈|失败|回滚|降级)/.test(normalized) },
      { label: "复盘改进", matched: /(复盘|改进|如果重来|下次|后续|优化)/.test(normalized) },
    ];
  }
  return [
    {
      label: "背景目标",
      matched: /(背景|目标|业务|场景|需求|问题|痛点)/.test(normalized),
    },
    {
      label: "个人职责",
      matched: /(我|本人|负责|主导|参与|推进|落地)/.test(normalized),
    },
    {
      label: "方案取舍",
      matched: /(方案|设计|架构|选型|取舍|权衡|因为|所以|考虑)/.test(normalized),
    },
    {
      label: "量化结果",
      matched: /(\d|%|\b(qps|rt|ms|sla|p95|p99)\b|秒|分钟|提升|降低|减少|增长|成本|耗时)/i.test(normalized),
    },
    {
      label: "复盘风险",
      matched: /(复盘|优化|改进|风险|异常|降级|监控|告警|如果重来)/.test(normalized),
    },
  ];
}

function buildFallbackChecklist(questionStyle?: string) {
  const style = questionStyle || "";
  if (/(架构|设计|扩展)/.test(style)) {
    return ["澄清目标、约束和量级", "拆核心模块、数据流和接口", "补瓶颈、降级、监控和成本"];
  }
  if (/(数据|性能|稳定性|安全|成本)/.test(style)) {
    return ["先说目标指标和现状基线", "补定位过程、动作和取舍", "说明兜底、监控和最终效果"];
  }
  if (/(行为|压力|协作|动机|规划)/.test(style)) {
    return ["用具体事件回答", "交代行动、冲突和推进方式", "补结果、反思和下一步"];
  }
  if (/原理/.test(style)) {
    return ["讲核心概念和适用场景", "补关键机制、边界和常见坑", "结合一次真实项目经验"];
  }
  if (/(结果|复盘|压测)/.test(style)) {
    return ["给目标指标、基线和变化", "讲你的动作和关键取舍", "补风险、复盘和后续优化"];
  }
  return ["背景目标和职责边界", "技术方案和关键取舍", "量化结果、复盘和优化"];
}

function buildContextualAnswerTemplate(questionStyle?: string, currentFocus?: string) {
  const style = questionStyle || "";
  const focus = currentFocus || "当前问题";
  if (/(架构|设计|扩展|数据|性能|稳定性|安全|成本)/.test(style)) {
    return `${focus}\n需求和约束：\n核心模块：\n数据流 / 接口：\n容量、性能或一致性：\n风险、降级和取舍：`;
  }
  if (/(行为|压力|协作|冲突|动机|规划)/.test(style)) {
    return `Situation：\nTask：\nAction：\nResult：\nReflection：`;
  }
  if (/原理/.test(style)) {
    return `${focus}\n核心概念：\n适用场景：\n关键机制：\n边界条件 / 常见坑：\n项目里的使用或排障经验：`;
  }
  if (/(结果|复盘|压测)/.test(style)) {
    return `${focus}\n目标指标：\n我的动作：\n最终结果：\n问题和风险：\n如果重来会怎么优化：`;
  }
  return `${focus}\n背景目标：\n我的职责：\n方案和取舍：\n量化结果：\n复盘改进：`;
}

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

function buildRoundScoreItems(record?: RoundRecord | null) {
  return [
    { label: "表达", value: record?.communicationScore || 0 },
    { label: "技术", value: record?.technicalScore || 0 },
    { label: "分析", value: record?.problemSolvingScore || 0 },
  ];
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
  const [speechSynthesisSupported, setSpeechSynthesisSupported] = useState(false);
  const [audioRecordingSupported, setAudioRecordingSupported] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [voiceStatus, setVoiceStatus] = useState("");

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
    setSpeechSynthesisSupported("speechSynthesis" in window);
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
      if ("speechSynthesis" in window) {
        window.speechSynthesis.cancel();
      }
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

  useEffect(() => {
    if (!latestQuestionMessage?.timestamp || isEnded || !isStarted) {
      setQuestionElapsedSeconds(0);
      return;
    }
    const updateElapsed = () => {
      setQuestionElapsedSeconds(Math.max(0, Math.floor((Date.now() - latestQuestionMessage.timestamp) / 1000)));
    };
    updateElapsed();
    const timer = window.setInterval(updateElapsed, 1000);
    return () => window.clearInterval(timer);
  }, [isEnded, isStarted, latestQuestionMessage?.timestamp]);

  const stopSpeaking = useCallback(() => {
    if (typeof window !== "undefined" && "speechSynthesis" in window) {
      window.speechSynthesis.cancel();
    }
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

  const speakText = useCallback((content?: string) => {
    if (!content || typeof window === "undefined" || !("speechSynthesis" in window)) {
      return;
    }
    const utterance = new SpeechSynthesisUtterance(content);
    utterance.lang = "zh-CN";
    utterance.rate = 1;
    utterance.pitch = 1;
    stopSpeaking();
    window.speechSynthesis.speak(utterance);
  }, [stopSpeaking]);

  useEffect(() => {
    if (!autoSpeakEnabled || !speechSynthesisSupported || !latestSpeakableMessage?.content) {
      return;
    }
    const speakKey = `${latestSpeakableMessage.stage || "ai"}-${latestSpeakableMessage.timestamp}-${latestSpeakableMessage.content}`;
    if (lastSpokenKeyRef.current === speakKey) {
      return;
    }
    lastSpokenKeyRef.current = speakKey;
    speakText(latestSpeakableMessage.content);
  }, [autoSpeakEnabled, latestSpeakableMessage, speakText, speechSynthesisSupported]);

  useEffect(() => {
    if (!autoSpeakEnabled && typeof window !== "undefined" && "speechSynthesis" in window) {
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
  const liveAnswerChecklist = (report?.answerChecklist || []).length
    ? report?.answerChecklist || []
    : buildFallbackChecklist(currentQuestionStyle);
  const latestQuestionText = latestQuestionMessage?.content || "面试官会在这里给出当前问题。";
  const answerTimePercent = Math.min(
    100,
    Math.round((questionElapsedSeconds / Math.max(1, recommendedAnswerSeconds)) * 100),
  );
  const answerOvertime = questionElapsedSeconds > recommendedAnswerSeconds;
  const canAnswer = isStarted && !isEnded;
  const isStreaming = submitting && Boolean(streamAbortControllerRef.current);
  const answerCoachHints = useMemo(() => buildAnswerCoachHints(inputMessage), [inputMessage]);
  const answerRecoveryHint = useMemo(() => buildAnswerRecoveryHint(inputMessage), [inputMessage]);
  const answerCoverageItems = useMemo(
    () => buildAnswerCoverageItems(inputMessage, currentQuestionStyle),
    [currentQuestionStyle, inputMessage],
  );
  const answerCoverageCount = answerCoverageItems.filter((item) => item.matched).length;
  const contextualAnswerTemplate = useMemo(
    () => buildContextualAnswerTemplate(currentQuestionStyle, currentFocus),
    [currentFocus, currentQuestionStyle],
  );
  const latestRoundScoreItems = buildRoundScoreItems(latestRoundRecord);
  const canSendAnswer = canAnswer
    && Boolean(inputMessage.trim())
    && !submitting
    && !isRecording
    && !isTranscribing;

  const appendAnswerTemplate = (template: string) => {
    stopSpeaking();
    setInputMessage((prev) => {
      if (!prev.trim()) {
        return template;
      }
      return `${prev.trim()}\n${template}`;
    });
  };

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
        <Empty description="这场模拟面试不存在或无法访问" />
      </div>
    );
  }

  return (
    <div id="interviewRoomPage" className="max-width-content">
      <div className="interview-shell">
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
                title={currentRound < expectedRounds ? "还没完成计划轮次，确定结束吗？" : "确定结束并生成报告？"}
                description={
                  currentRound < expectedRounds
                    ? `当前只完成 ${currentRound}/${expectedRounds} 轮，报告可信度会受影响。`
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

          <Card className="message-card">
            <div className="section-heading">
              <div>
                <div className="section-eyebrow">Interview Flow</div>
                <Title level={4} className="!mb-0 !mt-2">
                  面试过程
                </Title>
              </div>
              <Text className="text-slate-400">
                当前已完成 {currentRound} / {expectedRounds} 轮
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
                        <div className={`message-bubble ${item.isAI ? "ai" : "user"}`}>
                          <div className="message-head">
                            <span className="speaker">{item.isAI ? "面试官" : "候选人"}</span>
                            {item.round ? <span className="round-tag">第 {item.round} 轮</span> : null}
                          </div>
                          <div className="message-content">{item.content}</div>
                          <div className="message-time">{formatTime(item.timestamp)}</div>
                        </div>
                      </List.Item>
                    )}
                  />
                  {streamingReply?.content ? (
                    <div className="message-row ai">
                      <div className="message-bubble ai streaming">
                        <div className="message-head">
                          <span className="speaker">面试官输入中</span>
                          {streamingReply.round ? (
                            <span className="round-tag">第 {streamingReply.round} 轮</span>
                          ) : null}
                        </div>
                        <div className="message-content">{streamingReply.content}</div>
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
                  <div className="active-question-footer">
                    {liveAnswerChecklist.map((item) => (
                      <span key={item}>{item}</span>
                    ))}
                  </div>
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
              <div className="template-row">
                <Button
                  className="template-button"
                  onClick={() => appendAnswerTemplate(contextualAnswerTemplate)}
                  disabled={!canAnswer}
                >
                  插入本轮回答骨架
                </Button>
                <Button
                  className="template-button"
                  onClick={() => appendAnswerTemplate("背景：\n职责：\n方案：\n结果：\n复盘：")}
                  disabled={!canAnswer}
                >
                  插入项目回答模板
                </Button>
                <Button
                  className="template-button"
                  onClick={() => appendAnswerTemplate("Situation：\nTask：\nAction：\nResult：")}
                  disabled={!canAnswer}
                >
                  插入 STAR 模板
                </Button>
              </div>
              {answerRecoveryHint ? (
                <div className="answer-risk-card">
                  <div>
                    <div className="answer-risk-title">回答风险提示</div>
                    <div className="answer-risk-text">{answerRecoveryHint}</div>
                  </div>
                  <Button
                    className="template-button compact"
                    onClick={() => appendAnswerTemplate("我对这个点不是最熟，但我的理解是：\n我会先验证：\n如果落到项目里，我会关注：\n后续我会补齐：")}
                    disabled={!canAnswer}
                  >
                    插入补救结构
                  </Button>
                </div>
              ) : null}
              {canAnswer ? (
                <div className="answer-coverage-card">
                  <div className="answer-coverage-head">
                    <span>作答覆盖度</span>
                    <strong>{answerCoverageCount}/{answerCoverageItems.length}</strong>
                  </div>
                  <div className="coverage-list">
                    {answerCoverageItems.map((item) => (
                      <span className={`coverage-item ${item.matched ? "done" : ""}`} key={item.label}>
                        {item.label}
                      </span>
                    ))}
                  </div>
                  {inputMessage.trim() && answerCoverageCount < 3 ? (
                    <div className="coverage-tip">建议至少补齐 3 项后再发送，回答会更像真实面试里的有效信息。</div>
                  ) : null}
                </div>
              ) : null}
              {answerCoachHints.length ? (
                <div className="answer-coach-card">
                  <div className="answer-coach-title">发出前再补一口</div>
                  <ul>
                    {answerCoachHints.map((hint) => (
                      <li key={hint}>{hint}</li>
                    ))}
                  </ul>
                </div>
              ) : null}
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
                    disabled={!speechSynthesisSupported}
                    className="tool-button"
                  >
                    {autoSpeakEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
                    {autoSpeakEnabled ? "关闭自动播报" : "开启自动播报"}
                  </Button>
                  <Button
                    onClick={() => speakText(latestSpeakableMessage?.content)}
                    disabled={!speechSynthesisSupported || !latestSpeakableMessage?.content}
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
                  className={`send-button ${answerCoverageCount < 3 && inputMessage.trim() ? "needs-more" : ""}`}
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

        <aside className="interview-side">
          <Card className="side-card">
            <div className="section-heading compact">
              <div>
                <div className="section-eyebrow">Progress</div>
                <Title level={5} className="!mb-0 !mt-2">
                  轮次进度
                </Title>
              </div>
              <span className="score-pill">{progressPercent}%</span>
            </div>
            <Progress percent={progressPercent} showInfo={false} strokeColor="#1677ff" />
            <div className="metric-grid">
              <div className="metric-card">
                <span>计划轮次</span>
                <strong>{expectedRounds}</strong>
              </div>
              <div className="metric-card">
                <span>已完成</span>
                <strong>{currentRound}</strong>
              </div>
              <div className="metric-card">
                <span>当前状态</span>
                <strong>{status.text}</strong>
              </div>
              <div className="metric-card">
                <span>建议作答时长</span>
                <strong>{recommendedAnswerSeconds}s</strong>
              </div>
            </div>
          </Card>

          <Card className="side-card">
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
              <div className="cue-checklist">
                <div className="cue-checklist-title">本轮回答抓手</div>
                {liveAnswerChecklist.map((item) => (
                  <div className="cue-checklist-item" key={item}>{item}</div>
                ))}
              </div>
              {isPaused ? (
                <div className="cue-paused-banner">面试已暂停，继续后会从当前考察点接着追问。</div>
              ) : null}
            </div>
          </Card>

          <Card className="side-card">
            <div className="section-heading compact">
              <div>
                <div className="section-eyebrow">Round Insight</div>
                <Title level={5} className="!mb-0 !mt-2">
                  最近一轮反馈
                </Title>
              </div>
              <ClipboardCheck size={18} className="text-primary" />
            </div>
            {latestRoundRecord ? (
              <div className="round-feedback">
                {isEnded ? (
                  <div className="feedback-score">
                    <span>本轮评分</span>
                    <strong>{latestRoundRecord.score || 0}</strong>
                  </div>
                ) : null}
                {latestRoundRecord.verdict ? (
                  <div className="feedback-verdict">{latestRoundRecord.verdict}</div>
                ) : null}
                <Paragraph className="!mb-3 text-slate-600">
                  {latestRoundRecord.shortComment || "这一轮反馈将在你完成回答后显示。"}
                </Paragraph>
                {latestRoundRecord.responseSeconds ? (
                  <div className="feedback-meta">本轮作答用时 {latestRoundRecord.responseSeconds}s</div>
                ) : null}
                {latestRoundScoreItems.some((item) => item.value > 0) ? (
                  <div className="round-score-mini-grid">
                    {latestRoundScoreItems.map((item) => (
                      <div className="round-score-mini" key={item.label}>
                        <div className="round-score-mini-head">
                          <span>{item.label}</span>
                          <strong>{item.value}</strong>
                        </div>
                        <Progress percent={item.value} showInfo={false} strokeColor="#1677ff" />
                      </div>
                    ))}
                  </div>
                ) : null}
                {(latestRoundRecord.scoreReasons || []).length ? (
                  <div className="score-reason-list">
                    {(latestRoundRecord.scoreReasons || []).map((item) => (
                      <div className="score-reason" key={item}>{item}</div>
                    ))}
                  </div>
                ) : null}
                {(latestRoundRecord.improvementTags || []).length ? (
                  <div className="feedback-tags">
                    {(latestRoundRecord.improvementTags || []).map((tag) => (
                      <span className="feedback-tag" key={tag}>{tag}</span>
                    ))}
                  </div>
                ) : null}
                {(latestRoundRecord.missingPoints || []).length ? (
                  <div className="missing-point-list">
                    {(latestRoundRecord.missingPoints || []).map((item) => (
                      <span className="missing-point" key={item}>{item}</span>
                    ))}
                  </div>
                ) : null}
                {(latestRoundRecord.answerQualitySignals || []).length ? (
                  <div className="diagnostic-signal-panel">
                    <div className="focus-label">诊断信号</div>
                    <div className="diagnostic-signal-list">
                      {(latestRoundRecord.answerQualitySignals || []).map((item) => (
                        <span className="diagnostic-signal" key={item}>{item}</span>
                      ))}
                    </div>
                  </div>
                ) : null}
                <div className="feedback-focus">
                  <div className="focus-label">{isEnded ? "这一轮主要问题：" : "面试官观察重点："}</div>
                  <div className="focus-text">{latestRoundRecord.focus || "继续补充项目细节和设计取舍。"}</div>
                </div>
                {latestRoundRecord.followUpReason ? (
                  <div className="feedback-focus neutral">
                    <div className="focus-label">追问理由</div>
                    <div className="focus-text">{latestRoundRecord.followUpReason}</div>
                  </div>
                ) : null}
                {latestRoundRecord.answerRewriteSuggestion ? (
                  <div className="rewrite-suggestion">
                    <div className="focus-label">改进版回答骨架</div>
                    <div>{latestRoundRecord.answerRewriteSuggestion}</div>
                  </div>
                ) : null}
              </div>
            ) : (
              <Empty description="回答第一轮后，这里会显示本轮反馈" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>

          {(report?.agenda || []).length ? (
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
                {(report?.agenda || []).map((item) => {
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
                  <div className="report-label">综合评分</div>
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
                    <Progress percent={item.value} showInfo={false} strokeColor="#0f172a" />
                  </div>
                ))}
              </div>

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
                <div className="review-detail-panel">
                  <div className="block-title">建议继续准备</div>
                  <div className="topic-list">
                    {(report.suggestedTopics || []).map((item) => (
                      <span className="topic-tag" key={item}>
                        {item}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
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

              {(report?.roundRecords || []).length ? (
                <div className="review-round-section">
                  <div className="block-title review-round-title">逐轮记录</div>
                  <div className="review-round-grid">
                    {(report?.roundRecords || []).map((item, index) => (
                      <div className="review-round-card" key={`${item.round || "round"}-${index}`}>
                        <div className="record-head">
                          <strong>第 {item.round} 轮</strong>
                          <span>{item.score || 0} 分</span>
                        </div>
                        {item.questionStyle ? (
                          <div className="record-style">
                            {item.questionStyle} / 建议 {item.recommendedAnswerSeconds || 120}s
                          </div>
                        ) : null}
                        {item.responseSeconds ? (
                          <div className="record-meta">实际作答 {item.responseSeconds}s</div>
                        ) : null}
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
                        {(item.scoreReasons || []).length ? (
                          <div className="score-reason-list compact">
                            {(item.scoreReasons || []).map((reason) => (
                              <div className="score-reason" key={`${item.round}-${reason}`}>{reason}</div>
                            ))}
                          </div>
                        ) : null}
                        {item.verdict ? <div className="record-verdict">{item.verdict}</div> : null}
                        <div className="review-round-block">
                          <div className="focus-label">面试问题</div>
                          <div className="record-question">{item.question || "暂无问题记录"}</div>
                        </div>
                        <div className="review-round-block">
                          <div className="focus-label">你的回答</div>
                          <div className="record-answer">{item.answer || "暂无回答记录"}</div>
                        </div>
                        <div className="review-round-block">
                          <div className="focus-label">面试官评语</div>
                          <div className="record-comment">
                            <span>{item.shortComment || "暂无评语"}</span>
                            <ChevronRight size={14} />
                          </div>
                        </div>
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
                          <div className="review-round-block">
                            <div className="focus-label">改进版回答骨架</div>
                            <div className="record-answer">{item.answerRewriteSuggestion}</div>
                          </div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="report-placeholder wide">
              <Paragraph className="!mb-0 text-slate-500">
                面试结束后，这里会展示完整的结构化复盘和逐轮记录。现在右侧会先聚焦当前进度、考察点和最近一轮反馈，避免你在会话进行中被大段报告打断。
              </Paragraph>
            </div>
          )}
        </Card>
      </section>
    </div>
  );
}
