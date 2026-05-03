import { MenuDataItem } from "@ant-design/pro-layout";
import { CrownOutlined } from "@ant-design/icons";
import ACCESS_ENUM from "@/access/accessEnum";

// 菜单列表
export const menus = [
  {
    path: "/",
    name: "主页",
  },
  {
    path: "/banks",
    name: "题库",
  },
  {
    path: "/questions",
    name: "题目",
  },
  {
    path: "/posts",
    name: "社区",
  },
  {
    path: "/posts/create",
    name: "发布帖子",
    access: ACCESS_ENUM.USER,
    hideInMenu: true,
  },
  {
    path: "/mockInterview/add",
    name: "AI 模拟面试",
    access: ACCESS_ENUM.USER,
  },
  {
    path: "/mockInterview",
    name: "模拟面试记录",
    access: ACCESS_ENUM.USER,
  },
  {
    path: "/mockInterview/chat/[mockInterviewId]",
    name: "模拟面试会话",
    access: ACCESS_ENUM.USER,
    hideInMenu: true,
  },
  {
    path: "/user/center",
    name: "个人中心",
    access: ACCESS_ENUM.USER,
  },
  {
    path: "/user/public-profile/settings",
    name: "公开主页设置",
    access: ACCESS_ENUM.USER,
    hideInMenu: true,
  },
  {
    path: "/user/notifications",
    name: "通知中心",
    access: ACCESS_ENUM.USER,
  },
  {
    path: "/admin",
    name: "管理",
    icon: <CrownOutlined />,
    access: ACCESS_ENUM.ADMIN,
    children: [
      {
        path: "/admin/user",
        name: "用户管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/bank",
        name: "题库管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/question",
        name: "题目管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/post",
        name: "社区管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/notification",
        name: "通知管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/security",
        name: "风控面板",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/comment",
        name: "评论审核",
        access: ACCESS_ENUM.ADMIN,
        hideInMenu: true,
      },
      {
        path: "/admin/post/comment",
        name: "社区回复审核",
        access: ACCESS_ENUM.ADMIN,
        hideInMenu: true,
      },
      {
        path: "/admin/question/ai",
        name: "AI 出题",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/mockInterview",
        name: "面试管理",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/logs",
        name: "日志中心",
        access: ACCESS_ENUM.ADMIN,
      },
      {
        path: "/admin/settings",
        name: "系统设置",
        access: ACCESS_ENUM.ADMIN,
      },
    ],
  },
] as MenuDataItem[];

// 根据全部路径查找菜单
export const findAllMenuItemByPath = (path: string): MenuDataItem | null => {
  return findMenuItemByPath(menus, path);
};

// 根据路径查找菜单（递归）
export const findMenuItemByPath = (
  menus: MenuDataItem[],
  path: string,
): MenuDataItem | null => {
  for (const menu of menus) {
    if (isMenuPathMatch(menu.path, path)) {
      return menu;
    }
    if (menu.children) {
      const matchedMenuItem = findMenuItemByPath(menu.children, path);
      if (matchedMenuItem) {
        return matchedMenuItem;
      }
    }
  }
  return null;
};

const isMenuPathMatch = (menuPath?: string, currentPath?: string) => {
  if (!menuPath || !currentPath) {
    return false;
  }
  const menuSegments = menuPath.split("/").filter(Boolean);
  const currentSegments = currentPath.split("/").filter(Boolean);
  if (menuSegments.length !== currentSegments.length) {
    return false;
  }
  return menuSegments.every((segment, index) => {
    if (segment.startsWith("[") && segment.endsWith("]")) {
      return true;
    }
    return segment === currentSegments[index];
  });
};
