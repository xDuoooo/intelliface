"use client";

import React, { useRef, useState } from "react";
import dynamic from "next/dynamic";
import { deleteUserUsingPost, listUserByPageUsingPost } from "@/api/userController";
import { Plus, Trash2, Edit3, UserCog } from "lucide-react";
import AdminTableEllipsis from "@/app/admin/components/AdminTableEllipsis";
import ProTable from "@/components/DynamicProTable";
import UserAvatar from "@/components/UserAvatar";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { message, Tag, Popconfirm } from "antd";
import { useSelector } from "react-redux";
import { RootState } from "@/stores";
import { CITY_GROUP_OPTIONS, CITY_VALUE_ENUM } from "@/config/cityOptions";
import { extractSortParams } from "@/lib/utils";

const CreateModal = dynamic(() => import("./components/CreateModal"));
const UpdateModal = dynamic(() => import("./components/UpdateModal"));

/**
 * 用户管理页面
 * @constructor
 */
const UserAdminPage: React.FC = () => {
  const loginUser = useSelector((state: RootState) => state.loginUser);
  const [createModalVisible, setCreateModalVisible] = useState<boolean>(false);
  const [updateModalVisible, setUpdateModalVisible] = useState<boolean>(false);
  const actionRef = useRef<ActionType>();
  const [currentRow, setCurrentRow] = useState<API.User>();

  /**
   * 删除节点
   */
  const handleDelete = async (row: API.User) => {
    const hide = message.loading("正在删除");
    if (!row) return true;
    try {
      await deleteUserUsingPost({ id: row.id as any });
      hide();
      message.success("删除成功");
      actionRef?.current?.reload();
      return true;
    } catch (error: any) {
      hide();
      message.error("删除失败，" + error.message);
      return false;
    }
  };

  /**
   * 表格列配置
   */
  const columns: ProColumns<API.User>[] = [
    {
      title: "ID",
      dataIndex: "id",
      valueType: "text",
      hideInForm: true,
      width: 80,
    },
    {
      title: "账号",
      dataIndex: "userAccount",
      valueType: "text",
      width: 180,
      ellipsis: true,
      render: (text) => <AdminTableEllipsis value={text} className="font-bold text-slate-700" />,
    },
    {
      title: "用户名",
      dataIndex: "userName",
      valueType: "text",
      width: 160,
      ellipsis: true,
      render: (text) => <AdminTableEllipsis value={text} className="text-slate-600" />,
    },
    {
      title: "最近登录城市",
      dataIndex: "city",
      valueType: "select",
      valueEnum: CITY_VALUE_ENUM,
      fieldProps: {
        options: CITY_GROUP_OPTIONS,
        showSearch: true,
        optionFilterProp: "label",
        popupMatchSelectWidth: false,
      },
      width: 160,
      ellipsis: true,
      render: (text) => (
        <AdminTableEllipsis
          value={text}
          fallback={<span className="text-slate-300">暂未识别</span>}
          className="text-slate-600"
        />
      ),
    },
    {
      title: "就业方向",
      dataIndex: "careerDirection",
      valueType: "text",
      width: 180,
      ellipsis: true,
      render: (text) => (
        <AdminTableEllipsis
          value={text}
          fallback={<span className="text-slate-300">未填写</span>}
          className="text-slate-600"
        />
      ),
    },
    {
      title: "头像",
      dataIndex: "userAvatar",
      width: 90,
      hideInSearch: true,
      render: (_, record) => (
        <UserAvatar src={record.userAvatar} name={record.userName} size={48} className="border border-slate-100" />
      ),
    },
    {
      title: "角色",
      dataIndex: "userRole",
      width: 110,
      valueEnum: {
        user: { text: "用户", status: "Default" },
        admin: { text: "管理员", status: "Success" },
      },
      render: (_, record) => {
        const isAdmin = record.userRole === "admin";
        return (
          <Tag color={isAdmin ? "gold" : "blue"} className="whitespace-nowrap rounded-lg border-none px-3 py-1 font-bold">
            {isAdmin ? "管理员" : "用户"}
          </Tag>
        );
      },
    },
    {
      title: "创建时间",
      sorter: true,
      dataIndex: "createTime",
      valueType: "dateTime",
      width: 180,
      hideInSearch: true,
      hideInForm: true,
    },
    {
      title: "操作",
      dataIndex: "option",
      valueType: "option",
      width: 150,
      render: (_, record) => {
        const isCurrentLoginUser = Boolean(loginUser?.id && record.id === loginUser.id);
        return (
        <div className="flex w-[130px] flex-nowrap items-center gap-4 whitespace-nowrap">
          <button
            onClick={() => {
              setCurrentRow(record);
              setUpdateModalVisible(true);
            }}
            className="flex shrink-0 items-center gap-1.5 text-primary hover:text-primary/80 font-bold transition-colors"
          >
            <Edit3 className="h-4 w-4" />
            修改
          </button>
          <Popconfirm
            title="确认删除用户"
            description={isCurrentLoginUser ? "当前登录账号不支持在这里删除。" : "删除后该用户将无法登录，请谨慎操作。"}
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true, disabled: isCurrentLoginUser }}
            disabled={isCurrentLoginUser}
            onConfirm={() => handleDelete(record)}
          >
            <button
              className={`flex shrink-0 items-center gap-1.5 font-bold transition-colors ${
                isCurrentLoginUser ? "cursor-not-allowed text-slate-300" : "text-red-500 hover:text-red-600"
              }`}
            >
              <Trash2 className="h-4 w-4" />
              {isCurrentLoginUser ? "当前账号" : "删除"}
            </button>
          </Popconfirm>
        </div>
      )},
    },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-700">
      {/* Premium Admin Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 bg-white/70 backdrop-blur-xl rounded-[2.5rem] p-8 sm:p-12 border border-white shadow-2xl shadow-slate-200/50 relative overflow-hidden">
        <div className="absolute top-0 right-0 p-8 opacity-5">
           <UserCog className="h-32 w-32 text-slate-900" />
        </div>
        <div className="relative z-10 space-y-3">
           <div className="flex items-center gap-2 text-primary font-black uppercase tracking-widest text-xs">
              <span className="h-2 w-2 rounded-full bg-primary animate-pulse" />
              User Control Center
           </div>
           <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-slate-900">用户权限管理</h1>
           <p className="text-slate-500 font-medium text-lg">高效查阅、编辑和精细化管理所有用户的访问权限。</p>
        </div>
        <div className="relative z-10">
          <button
            onClick={() => setCreateModalVisible(true)}
            className="flex items-center gap-2 bg-primary hover:bg-primary/90 text-primary-foreground h-14 px-8 rounded-2xl font-black text-lg transition-all shadow-xl shadow-primary/25 hover:scale-105 active:scale-95"
          >
            <Plus className="h-6 w-6" />
            新增用户
          </button>
        </div>
      </div>

      {/* Table Container */}
      <div className="bg-white rounded-[2.5rem] border border-slate-100 shadow-2xl shadow-slate-200/50 overflow-hidden p-4 sm:p-6 pb-12 ant-table-premium">
        <ProTable<API.User>
          headerTitle={null}
          actionRef={actionRef}
          rowKey="id"
          search={{
            labelWidth: "auto",
            defaultCollapsed: false,
            className: "admin-search-form",
          }}
          request={async (params, sort, filter) => {
            try {
              const { sortField, sortOrder } = extractSortParams(sort as Record<string, "ascend" | "descend" | null>);
              // @ts-ignore
              const res = await listUserByPageUsingPost({
                ...params,
                sortField,
                sortOrder,
                ...filter,
              } as API.UserQueryRequest) as unknown as API.BaseResponsePageUser_;
              return {
                success: res.code === 0,
                data: res.data?.records || [],
                total: Number(res.data?.total) || 0,
              };
            } catch (error: any) {
              message.error(error?.message || "加载用户管理数据失败");
              return {
                success: false,
                data: [],
                total: 0,
              };
            }
          }}
          columns={columns}
          scroll={{ x: 1180 }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
        />
      </div>

      {createModalVisible && (
        <CreateModal
          visible={createModalVisible}
          columns={columns}
          onSubmit={() => {
            setCreateModalVisible(false);
            actionRef.current?.reload();
          }}
          onCancel={() => setCreateModalVisible(false)}
        />
      )}

      {updateModalVisible && currentRow && (
        <UpdateModal
          visible={updateModalVisible}
          columns={columns}
          oldData={currentRow}
          onSubmit={() => {
            setUpdateModalVisible(false);
            setCurrentRow(undefined);
            actionRef.current?.reload();
          }}
          onCancel={() => setUpdateModalVisible(false)}
        />
      )}
    </div>
  );
};

export default UserAdminPage;
