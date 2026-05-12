"use client";

import React from "react";
import { cn } from "@/lib/utils";

interface AdminTagListProps {
  tagList?: string[];
  maxVisible?: number;
  maxTagWidth?: number | string;
  className?: string;
  tagClassName?: string;
  showRestCount?: boolean;
}

export default function AdminTagList({
  tagList = [],
  maxVisible,
  maxTagWidth = 120,
  className,
  tagClassName,
  showRestCount = true,
}: AdminTagListProps) {
  const visibleTags = typeof maxVisible === "number" ? tagList.slice(0, maxVisible) : tagList;
  const restCount = Math.max(tagList.length - visibleTags.length, 0);
  const tagStyle: React.CSSProperties = {
    maxWidth: typeof maxTagWidth === "number" ? `${maxTagWidth}px` : maxTagWidth,
  };

  return (
    <div className={cn("flex min-w-0 max-w-full flex-wrap gap-2", className)}>
      {visibleTags.map((tag) => (
        <span
          key={tag}
          aria-label={tag}
          className={cn(
            "inline-block min-w-0 truncate whitespace-nowrap rounded-lg border border-slate-200/50 bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-600 transition-all hover:border-primary/20 hover:bg-primary/5 hover:text-primary",
            tagClassName,
          )}
          style={tagStyle}
        >
          {tag}
        </span>
      ))}
      {showRestCount && restCount > 0 ? (
        <span
          aria-label={tagList.slice(visibleTags.length).join("、")}
          className="inline-block whitespace-nowrap rounded-lg border border-slate-200/50 bg-white px-2.5 py-1 text-xs font-bold text-slate-500"
        >
          +{restCount}
        </span>
      ) : null}
    </div>
  );
}
