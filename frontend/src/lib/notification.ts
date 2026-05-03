export const NOTIFICATION_TYPE_OPTIONS = [
  { label: "题目审核", value: "question_review" },
  { label: "题库审核", value: "question_bank_review" },
  { label: "评论审核", value: "comment_review" },
  { label: "评论回复", value: "reply" },
  { label: "点赞提醒", value: "like" },
  { label: "题目评论", value: "question_comment" },
  { label: "题目收藏", value: "question_favour" },
  { label: "关注提醒", value: "user_follow" },
  { label: "帖子审核", value: "post_review" },
  { label: "帖子回复", value: "post_reply" },
  { label: "帖子点赞", value: "post_thumb" },
  { label: "帖子收藏", value: "post_favour" },
  { label: "社区回复点赞", value: "post_comment_like" },
  { label: "社区回复审核", value: "post_comment_review" },
  { label: "学习提醒", value: "learning_goal_reminder" },
  { label: "系统公告", value: "system_announcement" },
  { label: "运营通知", value: "operation_notice" },
  { label: "活动提醒", value: "activity_notice" },
  { label: "学习鼓励", value: "learning_notice" },
  { label: "自定义通知", value: "custom_notice" },
] as const;

export const NOTIFICATION_TYPE_LABEL_MAP: Record<string, string> = Object.fromEntries(
  NOTIFICATION_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);

export const NOTIFICATION_TYPE_COLOR_MAP: Record<string, string> = {
  question_review: "blue",
  question_bank_review: "cyan",
  comment_review: "purple",
  reply: "cyan",
  like: "magenta",
  question_comment: "cyan",
  question_favour: "gold",
  user_follow: "gold",
  post_review: "geekblue",
  post_reply: "lime",
  post_thumb: "magenta",
  post_favour: "gold",
  post_comment_like: "magenta",
  post_comment_review: "purple",
  learning_goal_reminder: "green",
  system_announcement: "processing",
  operation_notice: "orange",
  activity_notice: "volcano",
  learning_notice: "green",
  custom_notice: "default",
};

export function getNotificationTypeLabel(type?: string) {
  if (!type) {
    return "系统通知";
  }
  return NOTIFICATION_TYPE_LABEL_MAP[type] || type;
}

export function getNotificationTargetUrl(item?: {
  targetUrl?: string;
  type?: string;
  title?: string;
  content?: string;
  targetId?: string | number;
}) {
  if (!item) {
    return "/user/notifications";
  }
  if (item.targetUrl) {
    return item.targetUrl;
  }
  const type = item.type || "";
  const title = item.title || "";
  const content = item.content || "";
  const targetId = String(item.targetId ?? "").trim();
  const hasTargetId = /^[1-9]\d*$/.test(targetId);

  switch (type) {
    case "question_bank_review":
      if (title.includes("未通过") || content.includes("未通过")) {
        return "/user/center?tab=banks";
      }
      return hasTargetId ? `/bank/${targetId}` : "/user/center?tab=banks";
    case "post_review":
      if (title.includes("未通过") || content.includes("未通过")) {
        return "/user/center?tab=posts";
      }
      return hasTargetId ? `/post/${targetId}` : "/user/center?tab=posts";
    case "post_reply":
    case "post_comment_like":
    case "post_comment_review":
      return hasTargetId ? `/post/${targetId}#post-comment-section` : "/user/notifications";
    case "post_thumb":
    case "post_favour":
      return hasTargetId ? `/post/${targetId}` : "/user/notifications";
    case "question_review":
      if (title.includes("未通过") || content.includes("未通过")) {
        return "/user/center?tab=submission";
      }
      return hasTargetId ? `/question/${targetId}` : "/user/center?tab=submission";
    case "reply":
    case "like":
    case "question_comment":
    case "comment_review":
      return hasTargetId ? `/question/${targetId}#comment-section` : "/user/notifications";
    case "question_favour":
      return hasTargetId ? `/question/${targetId}` : "/user/notifications";
    case "user_follow":
      return hasTargetId ? `/user/${targetId}` : "/user/notifications";
    case "learning_goal_reminder":
      return "/user/center?tab=record";
    default:
      if (hasTargetId) {
        if (type.startsWith("post")) {
          return `/post/${targetId}`;
        }
        if (type.startsWith("question")) {
          return `/question/${targetId}`;
        }
      }
      return "/user/notifications";
  }
}
