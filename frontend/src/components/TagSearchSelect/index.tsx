"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Select } from "antd";
import { listTagSuggestionsUsingGet, type TagSuggestionScene } from "@/api/tagController";

type Props = {
  scene?: TagSuggestionScene;
  value?: string[];
  onChange?: (value: string[]) => void;
  placeholder?: string;
  maxCount?: number;
  tokenSeparators?: string[];
  size?: "small" | "middle" | "large";
  className?: string;
  allowClear?: boolean;
  maxTagCount?: number | "responsive";
  disabled?: boolean;
};

const SUGGESTION_LIMIT = 12;

export default function TagSearchSelect({
  scene = "all",
  value = [],
  onChange,
  placeholder,
  maxCount,
  tokenSeparators = [",", " "],
  size = "large",
  className,
  allowClear = true,
  maxTagCount = "responsive",
  disabled = false,
}: Props) {
  const [options, setOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [fetching, setFetching] = useState(false);

  const fetchSuggestions = useCallback(async (keyword: string) => {
    setFetching(true);
    try {
      const res = await listTagSuggestionsUsingGet({
        keyword: keyword || undefined,
        scene,
        limit: SUGGESTION_LIMIT,
      });
      const tagList = res.data || [];
      setOptions(tagList.map((tag) => ({ label: tag, value: tag })));
    } catch (error) {
      console.error("获取标签建议失败", error);
    } finally {
      setFetching(false);
    }
  }, [scene]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fetchSuggestions(searchKeyword.trim());
    }, searchKeyword ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [fetchSuggestions, searchKeyword]);

  const mergedOptions = useMemo(() => {
    const uniqueTagSet = new Set<string>();
    [...value, ...options.map((option) => option.value)]
      .filter(Boolean)
      .forEach((tag) => uniqueTagSet.add(tag));
    return Array.from(uniqueTagSet).map((tag) => ({
      label: tag,
      value: tag,
    }));
  }, [options, value]);

  return (
    <Select
      mode="tags"
      size={size}
      value={value}
      onChange={onChange}
      options={mergedOptions}
      showSearch
      filterOption={false}
      onSearch={setSearchKeyword}
      onFocus={() => {
        if (!options.length) {
          void fetchSuggestions("");
        }
      }}
      onDropdownVisibleChange={(open) => {
        if (open && !options.length) {
          void fetchSuggestions(searchKeyword.trim());
        }
      }}
      notFoundContent={fetching ? "正在搜索标签..." : searchKeyword ? "没有找到相关标签" : "暂无标签建议"}
      placeholder={placeholder}
      allowClear={allowClear}
      maxCount={maxCount}
      tokenSeparators={tokenSeparators}
      maxTagCount={maxTagCount}
      className={className}
      disabled={disabled}
    />
  );
}
