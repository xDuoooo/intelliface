export type InterviewDepthPreset = {
  rounds: number;
  label: string;
  durationText: string;
};

export const INTERVIEW_DEPTH_PRESETS: InterviewDepthPreset[] = [
  {
    rounds: 3,
    label: "快速热身",
    durationText: "约 8 - 12 分钟",
  },
  {
    rounds: 5,
    label: "标准模拟",
    durationText: "约 15 - 20 分钟",
  },
  {
    rounds: 7,
    label: "深度拷打",
    durationText: "约 25 - 35 分钟",
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
