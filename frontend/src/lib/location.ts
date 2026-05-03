export function normalizeIpLocationLabel(location?: string | null) {
  const normalizedLocation = location?.trim();
  if (!normalizedLocation) {
    return "";
  }
  if (normalizedLocation.includes("香港")) {
    return "中国香港";
  }
  if (normalizedLocation.includes("澳门")) {
    return "中国澳门";
  }
  if (normalizedLocation.includes("台湾")) {
    return "中国台湾";
  }
  return normalizedLocation;
}

export function formatIpLocation(location?: string | null, fallback = "IP：暂未识别") {
  const normalizedLocation = normalizeIpLocationLabel(location);
  return normalizedLocation ? `IP：${normalizedLocation}` : fallback;
}
