import Image from "next/image";
import { Button, Form, Input, Select, message, Typography, Upload } from "antd";
import { 
  updateMyUserUsingPost, 
  getLoginUserUsingGet
} from "@/api/userController";
import React, { useState, useEffect } from "react";
import { useDispatch } from "react-redux";
import { AppDispatch } from "@/stores";
import { setLoginUser } from "@/stores/loginUser";
import { User, FileText, ArrowRight, Camera, Loader2, MapPin } from "lucide-react";
import { formatIpLocation } from "@/lib/location";
import { validateImageSrc } from "@/lib/utils";
import { buildApiUrl } from "@/libs/request";
import TagSearchSelect from "@/components/TagSearchSelect";

const { Text } = Typography;
const CAREER_DIRECTION_OPTIONS = [
  { label: "Java 后端", value: "Java 后端" },
  { label: "前端开发", value: "前端开发" },
  { label: "大数据 / 数据开发", value: "大数据 / 数据开发" },
  { label: "算法工程师", value: "算法工程师" },
  { label: "测试开发", value: "测试开发" },
  { label: "运维 / 云原生", value: "运维 / 云原生" },
  { label: "产品 / 运营技术", value: "产品 / 运营技术" },
];

interface Props {
  user: API.LoginUserVO;
  onSuccess?: () => void;
}

/**
 * 用户个人信息编辑表单
 */
const UserInfoEditForm = (props: Props) => {
  const dispatch = useDispatch<AppDispatch>();
  const [loading, setLoading] = useState(false);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [form] = Form.useForm();
  const { user, onSuccess } = props;

  // 这里的 userAvatar 状态用于实时预览
  const [avatarUrl, setAvatarUrl] = useState(user.userAvatar || "");

  useEffect(() => {
    form.setFieldsValue({
      ...user,
      interestTags: user.interestTagList,
    });
    setAvatarUrl(user.userAvatar || "");
  }, [user, form]);

  /**
   * 提交基础信息修改
   */
  const doSubmit = async (values: API.UserUpdateMyRequest) => {
    setLoading(true);
    const hide = message.loading("正在保存更改...");
    try {
      await updateMyUserUsingPost({
        ...values,
        userAvatar: avatarUrl, // 使用上传后的最新 URL
      });
      message.success("资料更新成功");
      
      const res = await getLoginUserUsingGet();
      if (res.data) {
        dispatch(setLoginUser(res.data as any));
        onSuccess?.();
      }
    } catch (error: any) {
      message.error("更新失败：" + error.message);
    } finally {
      hide();
      setLoading(false);
    }
  };

  /**
   * 上传前校验
   */
  const beforeUpload = (file: File) => {
    const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp';
    if (!isJpgOrPng) {
      message.error('仅支持 JPG/PNG/WebP 格式图片!');
    }
    const isLt1M = file.size / 1024 / 1024 < 1;
    if (!isLt1M) {
      message.error('图片大小不能超过 1MB!');
    }
    return isJpgOrPng && isLt1M;
  };

  /**
   * 上传状态变更
   */
  const handleUploadChange = (info: any) => {
    if (info.file.status === 'uploading') {
      setUploadLoading(true);
      return;
    }
    if (info.file.status === 'done') {
      const { code, data, message: msg } = info.file.response;
      if (code === 0) {
        setAvatarUrl(data);
        message.success("头像上传成功");
      } else {
        message.error(msg || "上传失败");
      }
      setUploadLoading(false);
    } else if (info.file.status === 'error') {
      message.error("服务器响应错误，上传失败");
      setUploadLoading(false);
    }
  };

  return (
    <div className="user-info-edit-container max-w-2xl mx-auto px-4 py-2">
      <Form
        form={form}
        layout="vertical"
        onFinish={doSubmit}
        className="space-y-6"
      >
        <div className="flex flex-col items-center mb-8">
          <Upload
            name="file"
            listType="picture-circle"
            className="avatar-uploader"
            showUploadList={false}
            action={buildApiUrl("/api/file/upload?biz=user_avatar")}
            beforeUpload={beforeUpload}
            onChange={handleUploadChange}
            withCredentials={true}
          >
            {avatarUrl ? (
              <div className="relative group w-full h-full rounded-full overflow-hidden border-2 border-slate-100 p-1">
                <Image
                  src={validateImageSrc(avatarUrl)}
                  alt="avatar"
                  fill
                  className="w-full h-full object-cover rounded-full"
                />
                <div className="absolute inset-0 bg-black/40 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  {uploadLoading ? <Loader2 size={24} className="text-white animate-spin" /> : <Camera size={24} className="text-white" />}
                  <span className="text-[10px] text-white font-bold mt-1">更换头像</span>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center space-y-2">
                {uploadLoading ? <Loader2 size={24} className="text-primary animate-spin" /> : <Camera size={24} className="text-slate-400" />}
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">上传头像</div>
              </div>
            )}
          </Upload>
          <Text type="secondary" className="text-[11px] mt-3 font-bold uppercase tracking-widest text-slate-400">
            支持 JPG, PNG, WebP (最大 1MB)
          </Text>
        </div>

        <div className="grid grid-cols-1 gap-6">
          <Form.Item
            label={
              <span className="font-bold text-slate-700 flex items-center gap-2 text-sm">
                <User size={16} className="text-primary"/> 昵称
              </span>
            }
            name="userName"
            rules={[
              { required: true, message: '请输入昵称' },
              { max: 20, message: '昵称最多 20 个字符' }
            ]}
          >
            <Input 
              placeholder="您的公开显示名称" 
              showCount
              maxLength={20}
              className="h-12 rounded-2xl bg-slate-50 border-slate-100 hover:border-primary focus:border-primary transition-all shadow-sm"
            />
          </Form.Item>

          <Form.Item
            label={
              <span className="font-bold text-slate-700 flex items-center gap-2 text-sm">
                <User size={16} className="text-primary"/> 就业方向
              </span>
            }
            name="careerDirection"
            rules={[{ max: 30, message: "就业方向最多 30 个字符" }]}
          >
            <Select
              placeholder="请选择或输入你的目标方向"
              allowClear
              showSearch
              optionFilterProp="label"
              options={CAREER_DIRECTION_OPTIONS}
              size="large"
            />
          </Form.Item>

          <Form.Item
            label={
              <span className="font-bold text-slate-700 flex items-center gap-2 text-sm">
                <FileText size={16} className="text-primary"/> 兴趣标签
              </span>
            }
            name="interestTags"
            extra="最多 8 个标签，用于个性化推荐和公开主页展示。"
          >
            <TagSearchSelect
              scene="interest"
              placeholder="输入你关注的技术方向，例如：MySQL、Redis、并发"
              tokenSeparators={[",", " "]}
              maxCount={8}
              maxTagCount="responsive"
            />
          </Form.Item>

          <div className="rounded-[1.75rem] border border-emerald-100 bg-emerald-50/70 px-5 py-4">
            <div className="font-bold text-slate-700 flex items-center gap-2 text-sm">
              <MapPin size={16} className="text-emerald-600"/> IP 归属地
            </div>
            <div className="mt-2 text-sm text-slate-600">
              {formatIpLocation(user.city)}
            </div>
            <div className="mt-2 text-xs text-slate-400 leading-6">
              归属地由系统根据最近登录 IP 自动识别，用于地理热度统计与公开资料展示，普通用户不支持手动修改。
            </div>
          </div>

          <Form.Item
            label={
              <span className="font-bold text-slate-700 flex items-center gap-2 text-sm">
                <FileText size={16} className="text-primary"/> 个人简介
              </span>
            }
            name="userProfile"
            rules={[
              { max: 200, message: '简介最多 200 个字符' }
            ]}
          >
            <Input.TextArea 
              placeholder="介绍一下你自己..." 
              rows={4}
              showCount
              maxLength={200}
              className="rounded-2xl bg-slate-50 border-slate-100 hover:border-primary focus:border-primary transition-all shadow-sm py-4"
            />
          </Form.Item>

        </div>

        <div className="pt-8 flex justify-center border-t border-slate-50 mt-10">
          <Button 
            type="primary" 
            htmlType="submit" 
            loading={loading || uploadLoading}
            className="h-14 px-16 rounded-2xl bg-slate-900 hover:bg-black text-white border-none font-black text-base flex items-center gap-3 shadow-xl shadow-slate-200 transition-all hover:-translate-y-0.5 active:translate-y-0"
          >
            {loading ? "更新中..." : "保存更改"}
            {!loading && <ArrowRight size={20} />}
          </Button>
        </div>
      </Form>
    </div>
  );
};

export default UserInfoEditForm;
