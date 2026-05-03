"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Button, message, Skeleton, Switch } from "antd";
import { ArrowLeft, Eye, EyeOff, ExternalLink, Lock, Save, Settings, Users } from "lucide-react";
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "@/stores";
import { getLoginUserUsingGet, updateMyUserUsingPost } from "@/api/userController";
import { setLoginUser } from "@/stores/loginUser";

const DEFAULT_PROFILE_VISIBLE_FIELDS = [
  "profile",
  "city",
  "career",
  "tags",
  "joinTime",
  "stats",
  "activity",
  "content",
  "relation",
  "relationList",
];

const VISIBILITY_GROUPS = [
  {
    key: "base",
    title: "基础资料",
    items: [
      { label: "个人简介", value: "profile" },
      { label: "最近登录城市", value: "city" },
      { label: "就业方向", value: "career" },
      { label: "兴趣标签", value: "tags" },
      { label: "加入时间", value: "joinTime" },
    ],
  },
  {
    key: "learning",
    title: "学习内容",
    items: [
      { label: "学习数据", value: "stats" },
      { label: "学习动态", value: "activity" },
      { label: "公开题目/题库", value: "content" },
    ],
  },
  {
    key: "relation",
    title: "关注关系",
    items: [
      { label: "展示粉丝/关注数量", value: "relation" },
      { label: "允许查看粉丝/关注列表", value: "relationList" },
    ],
  },
];

export default function PublicProfileSettingsPage() {
  const dispatch = useDispatch<AppDispatch>();
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectedFields, setSelectedFields] = useState<string[]>(DEFAULT_PROFILE_VISIBLE_FIELDS);

  const selectedFieldSet = useMemo(() => new Set(selectedFields), [selectedFields]);

  useEffect(() => {
    const loadLoginUser = async () => {
      setLoading(true);
      try {
        const res = await getLoginUserUsingGet();
        if (res.data) {
          dispatch(setLoginUser(res.data as any));
          setSelectedFields(
            Array.isArray(res.data.profileVisibleFieldList)
              ? res.data.profileVisibleFieldList
              : DEFAULT_PROFILE_VISIBLE_FIELDS,
          );
        }
      } catch (error: any) {
        message.error(error?.message || "获取公开主页设置失败");
      } finally {
        setLoading(false);
      }
    };
    void loadLoginUser();
  }, [dispatch]);

  const toggleField = (field: string, checked: boolean) => {
    setSelectedFields((current) => {
      const nextFieldSet = new Set(current);
      if (checked) {
        nextFieldSet.add(field);
      } else {
        nextFieldSet.delete(field);
      }
      return DEFAULT_PROFILE_VISIBLE_FIELDS.filter((item) => nextFieldSet.has(item));
    });
  };

  const saveSettings = async () => {
    setSaving(true);
    const hide = message.loading("正在保存公开主页设置...");
    try {
      await updateMyUserUsingPost({
        profileVisibleFields: selectedFields,
      });
      const res = await getLoginUserUsingGet();
      if (res.data) {
        dispatch(setLoginUser(res.data as any));
        setSelectedFields(
          Array.isArray(res.data.profileVisibleFieldList)
            ? res.data.profileVisibleFieldList
            : DEFAULT_PROFILE_VISIBLE_FIELDS,
        );
      }
      message.success("公开主页设置已保存");
    } catch (error: any) {
      message.error(error?.message || "保存失败");
    } finally {
      hide();
      setSaving(false);
    }
  };

  const setAllVisible = () => setSelectedFields(DEFAULT_PROFILE_VISIBLE_FIELDS);
  const setAllHidden = () => setSelectedFields([]);
  const secondaryActionClassName =
    "inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 text-sm font-bold text-slate-600 transition-all hover:border-primary/20 hover:text-primary active:scale-95";

  return (
    <div className="mx-auto max-w-5xl space-y-6 pb-20">
      <Link
        href={loginUser?.id ? `/user/${loginUser.id}` : "/user/center"}
        className="inline-flex items-center gap-2 px-2 text-sm font-bold text-slate-400 transition-colors hover:text-primary"
      >
        <ArrowLeft className="h-4 w-4" />
        返回公开主页
      </Link>

      <section className="rounded-[2.5rem] border border-slate-100 bg-white p-8 shadow-2xl shadow-slate-200/40 sm:p-10">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-4 py-2 text-xs font-black uppercase tracking-[0.18em] text-primary">
              <Settings className="h-4 w-4" />
              Public Profile
            </div>
            <h1 className="mt-5 text-3xl font-black tracking-tight text-slate-900">
              公开主页设置
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-500">
              管理别人进入你的公开主页和用户名片时可以看到的资料范围。
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <button type="button" className={secondaryActionClassName} onClick={setAllVisible}>
              <Eye className="h-4 w-4" />
              全部公开
            </button>
            <button type="button" className={secondaryActionClassName} onClick={setAllHidden}>
              <EyeOff className="h-4 w-4" />
              全部隐藏
            </button>
            {loginUser?.id ? (
              <Link
                href={`/user/${loginUser.id}`}
                className={secondaryActionClassName}
              >
                <ExternalLink className="h-4 w-4" />
                查看主页
              </Link>
            ) : null}
          </div>
        </div>
      </section>

      {loading ? (
        <section className="rounded-[2.5rem] border border-slate-100 bg-white p-8 shadow-xl shadow-slate-200/30">
          <Skeleton active paragraph={{ rows: 8 }} />
        </section>
      ) : (
        <section className="grid gap-5 lg:grid-cols-3">
          {VISIBILITY_GROUPS.map((group) => (
            <div key={group.key} className="rounded-[2rem] border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/30">
              <div className="mb-5 flex items-center gap-2 text-lg font-black text-slate-900">
                {group.key === "relation" ? <Users className="h-5 w-5 text-primary" /> : <Lock className="h-5 w-5 text-primary" />}
                {group.title}
              </div>
              <div className="space-y-3">
                {group.items.map((item) => (
                  <div
                    key={item.value}
                    className="flex items-center justify-between gap-4 rounded-2xl border border-slate-100 bg-slate-50/70 px-4 py-4"
                  >
                    <span className="text-sm font-bold text-slate-700">{item.label}</span>
                    <Switch
                      checked={selectedFieldSet.has(item.value)}
                      onChange={(checked) => toggleField(item.value, checked)}
                    />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </section>
      )}

      <div className="sticky bottom-4 z-10 flex justify-end">
        <Button
          type="primary"
          size="large"
          loading={saving}
          onClick={saveSettings}
          icon={<Save className="h-4 w-4" />}
          className="h-12 rounded-2xl px-8 font-black shadow-xl shadow-primary/20"
        >
          保存设置
        </Button>
      </div>
    </div>
  );
}
