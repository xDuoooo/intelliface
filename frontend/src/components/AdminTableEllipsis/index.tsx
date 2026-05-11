"use client";

import React from "react";
import { Tooltip } from "antd";
import { cn } from "@/lib/utils";

type TooltipPlacement = React.ComponentProps<typeof Tooltip>["placement"];

interface AdminTableEllipsisProps {
  value?: React.ReactNode;
  children?: React.ReactNode;
  tooltip?: React.ReactNode;
  fallback?: React.ReactNode;
  className?: string;
  maxWidth?: number | string;
  placement?: TooltipPlacement;
}

const isEmptyValue = (value: React.ReactNode) =>
  value === undefined || value === null || value === "";

export default function AdminTableEllipsis({
  value,
  children,
  tooltip,
  fallback = <span className="text-slate-300">-</span>,
  className,
  maxWidth = "100%",
  placement = "topLeft",
}: AdminTableEllipsisProps) {
  const rawContent = children ?? value;
  const isEmpty = isEmptyValue(rawContent);
  const content = isEmpty ? fallback : rawContent;
  const tooltipTitle = isEmpty ? undefined : tooltip ?? rawContent;
  const style: React.CSSProperties = {
    maxWidth: typeof maxWidth === "number" ? `${maxWidth}px` : maxWidth,
  };
  const node = (
    <span className={cn("block min-w-0 max-w-full truncate", className)} style={style}>
      {content}
    </span>
  );

  if (isEmptyValue(tooltipTitle)) {
    return node;
  }

  return (
    <Tooltip title={tooltipTitle} placement={placement} mouseEnterDelay={0.25}>
      {node}
    </Tooltip>
  );
}
