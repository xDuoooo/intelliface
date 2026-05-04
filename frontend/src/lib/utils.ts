import { clsx, type ClassValue } from "clsx";
import { formatDistanceToNow } from "date-fns";
import { zhCN } from "date-fns/locale";
import { twMerge } from "tailwind-merge";

const SHANGHAI_TIME_ZONE = "Asia/Shanghai";
const SHANGHAI_UTC_OFFSET_HOURS = 8;
const DATE_WITHOUT_TIME_ZONE_REGEX =
  /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?)?$/;
const TIME_ZONE_SUFFIX_REGEX = /(Z|[+-]\d{2}:\d{2}|[+-]\d{4})$/i;

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * 校验图片路径是否合法 (Next.js Image 要求)
 * @param src
 * @param fallback
 */
export function validateImageSrc(src?: string, fallback: string = "/assets/logo.png") {
  if (!src) return fallback;
  // Next.js Image 要求相对路径必须以 / 开头，或为绝对路径
  if (src.startsWith("/") || src.startsWith("http://") || src.startsWith("https://")) {
    return src;
  }
  return fallback;
}

function parseShanghaiDateString(value: string) {
  const matched = value.match(DATE_WITHOUT_TIME_ZONE_REGEX);
  if (!matched) {
    return null;
  }
  const [, yearText, monthText, dayText, hourText = "00", minuteText = "00", secondText = "00", millisecondText = "0"] = matched;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const millisecond = Number(millisecondText.padEnd(3, "0"));
  const utcTimestamp = Date.UTC(
    year,
    month - 1,
    day,
    hour - SHANGHAI_UTC_OFFSET_HOURS,
    minute,
    second,
    millisecond,
  );
  const date = new Date(utcTimestamp);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function parseDateValue(value?: string | number | Date | null) {
  if (!value) {
    return null;
  }
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === "string") {
    const normalizedValue = value.trim();
    if (!normalizedValue) {
      return null;
    }
    if (DATE_WITHOUT_TIME_ZONE_REGEX.test(normalizedValue) && !TIME_ZONE_SUFFIX_REGEX.test(normalizedValue)) {
      return parseShanghaiDateString(normalizedValue);
    }
    const numericValue = Number(normalizedValue);
    if (Number.isFinite(numericValue) && /^\d{10,13}$/.test(normalizedValue)) {
      const timestamp = normalizedValue.length === 10 ? numericValue * 1000 : numericValue;
      const date = new Date(timestamp);
      return Number.isNaN(date.getTime()) ? null : date;
    }
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * 统一格式化日期时间
 */
export function formatDateTime(value?: string | number | Date | null, fallback = "-") {
  const date = parseDateValue(value);
  if (!date) {
    return fallback;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: SHANGHAI_TIME_ZONE,
  })
    .format(date)
    .replace(/\//g, "-");
}

/**
 * 统一格式化日期
 */
export function formatDate(value?: string | number | Date | null, fallback = "-") {
  const date = parseDateValue(value);
  if (!date) {
    return fallback;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone: SHANGHAI_TIME_ZONE,
  })
    .format(date)
    .replace(/\//g, "-");
}

/**
 * 统一格式化相对时间
 */
export function formatRelativeTime(value?: string | number | Date | null, fallback = "-") {
  const date = parseDateValue(value);
  if (!date) {
    return fallback;
  }
  return formatDistanceToNow(date, {
    addSuffix: true,
    locale: zhCN,
  });
}

/**
 * 从 ProTable / Table 的 sort 对象中安全提取排序参数
 */
export function extractSortParams(
  sort?: Record<string, "ascend" | "descend" | null | undefined>,
): {
  sortField?: string;
  sortOrder?: "ascend" | "descend";
} {
  const rawSortField = Object.keys(sort || {}).find((key) => {
    const normalizedKey = String(key || "").trim();
    return Boolean(normalizedKey)
      && normalizedKey !== "undefined"
      && normalizedKey !== "null"
      && /^[A-Za-z0-9_]+$/.test(normalizedKey);
  });
  if (!rawSortField) {
    return {};
  }
  const sortOrder = sort?.[rawSortField];
  if (sortOrder !== "ascend" && sortOrder !== "descend") {
    return {};
  }
  return {
    sortField: rawSortField,
    sortOrder,
  };
}
