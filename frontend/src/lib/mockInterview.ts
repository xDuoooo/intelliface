export type InterviewDepthPreset = {
  rounds: number;
  label: string;
  durationText: string;
  description: string;
};

export const INTERVIEW_DEPTH_PRESETS: InterviewDepthPreset[] = [
  {
    rounds: 3,
    label: "快速热身",
    durationText: "约 8 - 12 分钟",
    description: "先把项目主线、核心技术点和表达状态热起来。",
  },
  {
    rounds: 5,
    label: "标准模拟",
    durationText: "约 15 - 20 分钟",
    description: "覆盖项目、原理、场景和稳定性，适合日常练习。",
  },
  {
    rounds: 7,
    label: "深度拷打",
    durationText: "约 25 - 35 分钟",
    description: "会延展到系统设计、压测复盘和综合取舍，适合冲刺准备。",
  },
];

export function normalizeInterviewDepthPresetRounds(expectedRounds?: number) {
  const rounds = Number(expectedRounds || 5);
  if (rounds <= 3) {
    return 3;
  }
  if (rounds <= 5) {
    return 5;
  }
  return 7;
}

export function getInterviewDepthMeta(expectedRounds?: number) {
  const normalizedPresetRounds = normalizeInterviewDepthPresetRounds(expectedRounds);
  return INTERVIEW_DEPTH_PRESETS.find((item) => item.rounds === normalizedPresetRounds) || INTERVIEW_DEPTH_PRESETS[1];
}
