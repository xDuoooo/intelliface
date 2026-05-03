"use client";

import React, { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSelector } from "react-redux";
import { Input, Modal, message } from "antd";
import { AlertTriangle, Heart, ThumbsUp } from "lucide-react";
import { RootState } from "@/stores";
import { doThumbUsingPost } from "@/api/postThumbController";
import { doPostFavourUsingPost } from "@/api/postFavourController";
import { reportPostUsingPost } from "@/api/postController";

interface Props {
  postId: string | number;
  initialThumbNum?: number;
  initialFavourNum?: number;
  initialHasThumb?: boolean;
  initialHasFavour?: boolean;
  variant?: "default" | "meta";
  showThumbAndFavour?: boolean;
  showReport?: boolean;
}

export default function PostActionBar({
  postId,
  initialThumbNum = 0,
  initialFavourNum = 0,
  initialHasThumb = false,
  initialHasFavour = false,
  variant = "default",
  showThumbAndFavour = true,
  showReport = true,
}: Props) {
  const router = useRouter();
  const pathname = usePathname() || "/";
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [thumbNum, setThumbNum] = useState(initialThumbNum);
  const [favourNum, setFavourNum] = useState(initialFavourNum);
  const [hasThumb, setHasThumb] = useState(initialHasThumb);
  const [hasFavour, setHasFavour] = useState(initialHasFavour);
  const [thumbLoading, setThumbLoading] = useState(false);
  const [favourLoading, setFavourLoading] = useState(false);
  const [reportVisible, setReportVisible] = useState(false);
  const [reportReason, setReportReason] = useState("");
  const [reportLoading, setReportLoading] = useState(false);

  useEffect(() => {
    setThumbNum(initialThumbNum);
    setFavourNum(initialFavourNum);
    setHasThumb(initialHasThumb);
    setHasFavour(initialHasFavour);
    setReportVisible(false);
    setReportReason("");
  }, [initialFavourNum, initialHasFavour, initialHasThumb, initialThumbNum, postId]);

  const ensureLogin = () => {
    if (!loginUser?.id) {
      message.info("登录后才可以进行点赞、收藏或举报");
      const search = typeof window !== "undefined" ? window.location.search : "";
      router.push(`/user/login?redirect=${encodeURIComponent(`${pathname}${search}`)}`);
      return false;
    }
    return true;
  };

  const handleThumb = async () => {
    if (!ensureLogin() || thumbLoading) {
      return;
    }
    setThumbLoading(true);
    try {
      const res = await doThumbUsingPost({ postId });
      const delta = Number(res.data || 0);
      if (delta !== 0) {
        setThumbNum((prev) => Math.max(0, prev + delta));
        setHasThumb(delta > 0);
      }
    } catch (error: any) {
      message.error(error?.message || "点赞失败");
    } finally {
      setThumbLoading(false);
    }
  };

  const handleFavour = async () => {
    if (!ensureLogin() || favourLoading) {
      return;
    }
    setFavourLoading(true);
    try {
      const res = await doPostFavourUsingPost({ postId });
      const delta = Number(res.data || 0);
      if (delta !== 0) {
        setFavourNum((prev) => Math.max(0, prev + delta));
        setHasFavour(delta > 0);
      }
    } catch (error: any) {
      message.error(error?.message || "收藏失败");
    } finally {
      setFavourLoading(false);
    }
  };

  const handleOpenReport = () => {
    if (!ensureLogin()) {
      return;
    }
    setReportVisible(true);
  };

  const handleReport = async () => {
    const reason = reportReason.trim();
    if (!reason) {
      message.warning("请填写举报原因");
      return;
    }
    setReportLoading(true);
    try {
      await reportPostUsingPost({
        postId,
        reason,
      });
      message.success("举报已提交，管理员会尽快复核");
      setReportVisible(false);
      setReportReason("");
    } catch (error: any) {
      message.error(error?.message || "举报失败");
    } finally {
      setReportLoading(false);
    }
  };

  return (
    <>
      <div className={`flex flex-wrap items-center ${variant === "meta" ? "gap-4 text-sm" : "gap-3"}`}>
        {showThumbAndFavour ? (
          <>
            <button
              onClick={handleThumb}
              disabled={thumbLoading}
              className={`inline-flex items-center gap-1.5 transition-all ${
                variant === "meta"
                  ? hasThumb
                    ? "font-semibold text-blue-600"
                    : "text-slate-400 hover:text-blue-600"
                  : hasThumb
                    ? "rounded-2xl border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-black text-blue-600"
                    : "rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-500 hover:border-blue-200 hover:text-blue-600"
              }`}
            >
              <ThumbsUp className="h-4 w-4" />
              <span>{variant === "meta" ? thumbNum : `点赞 ${thumbNum}`}</span>
            </button>
            <button
              onClick={handleFavour}
              disabled={favourLoading}
              className={`inline-flex items-center gap-1.5 transition-all ${
                variant === "meta"
                  ? hasFavour
                    ? "font-semibold text-rose-600"
                    : "text-slate-400 hover:text-rose-600"
                  : hasFavour
                    ? "rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-black text-rose-600"
                    : "rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-500 hover:border-rose-200 hover:text-rose-600"
              }`}
            >
              <Heart className="h-4 w-4" />
              <span>{variant === "meta" ? favourNum : `收藏 ${favourNum}`}</span>
            </button>
          </>
        ) : null}
        {showReport ? (
          <button
            onClick={handleOpenReport}
            className={`inline-flex items-center gap-2 transition-all ${
              variant === "meta"
                ? "font-semibold text-amber-700 hover:text-amber-800"
                : "rounded-2xl border border-amber-200 bg-amber-50 px-4 py-2 text-sm font-black text-amber-700 hover:border-amber-300 hover:bg-amber-100"
            }`}
          >
            <AlertTriangle className="h-4 w-4" />
            <span>{variant === "meta" ? "举报" : "举报帖子"}</span>
          </button>
        ) : null}
      </div>

      <Modal
        title={null}
        open={reportVisible}
        onCancel={() => {
          setReportVisible(false);
          setReportReason("");
        }}
        onOk={handleReport}
        confirmLoading={reportLoading}
        okText="提交举报"
        cancelText="取消"
      >
        <div className="space-y-5 pt-4">
          <div>
            <div className="text-xs font-black uppercase tracking-[0.2em] text-amber-600">Report Post</div>
            <h3 className="mt-2 text-2xl font-black text-slate-900">举报帖子</h3>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              如果你认为这篇帖子存在广告导流、违规内容或明显不实信息，可以填写原因提交给管理员复核。
            </p>
          </div>
          <Input.TextArea
            rows={5}
            value={reportReason}
            onChange={(event) => setReportReason(event.target.value)}
            placeholder="请简要描述举报原因，例如：广告引流、内容抄袭、违规联系方式等"
            maxLength={200}
            showCount
          />
        </div>
      </Modal>
    </>
  );
}
