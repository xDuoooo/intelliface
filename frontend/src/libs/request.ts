import axios, { type AxiosRequestConfig } from "axios";

const isServer = typeof window === "undefined";
const serverApiBaseUrl =
  process.env.API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  "http://localhost:8101";

const browserApiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  (process.env.NODE_ENV === "development" ? "http://localhost:8101" : "");

export const API_BASE_URL = isServer ? serverApiBaseUrl : browserApiBaseUrl;

export function buildApiUrl(path: string) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  if (!API_BASE_URL) {
    return path;
  }
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

const myAxios = axios.create({
  baseURL: API_BASE_URL || undefined,
  timeout: 60000,
  withCredentials: true,
});

// 创建请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // 请求执行前执行
    return config;
  },
  function (error) {
    // 处理请求错误
    return Promise.reject(error);
  },
);

// 创建响应拦截器
myAxios.interceptors.response.use(
  // 2xx 响应触发
  function (response) {
    // 处理响应数据
    const { data } = response;
    const requestUrl = response.config.url || "";
    // 未登录
    if (data.code === 40100) {
      const isLoginStatusRequest = requestUrl.includes("user/get/login");
      // 获取登录状态的请求静默处理，不抛异常
      if (!isLoginStatusRequest) {
        throw new Error(data.message ?? "请先登录");
      }
    } else if (data.code !== 0) {
      // 其他错误
      throw new Error(data.message ?? "服务器错误");
    }
    return data;
  },
  // 非 2xx 响应触发
  function (error) {
    // 处理响应错误
    return Promise.reject(error);
  },
);

type RequestInstance = {
  <T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
};

const request = ((url: string, config?: AxiosRequestConfig) =>
  myAxios.request<any, any>({
    url,
    ...(config || {}),
  })) as RequestInstance;

request.get = <T = any>(url: string, config?: AxiosRequestConfig) =>
  myAxios.get<any, T>(url, config);

request.post = <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
  myAxios.post<any, T>(url, data, config);

request.put = <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
  myAxios.put<any, T>(url, data, config);

request.delete = <T = any>(url: string, config?: AxiosRequestConfig) =>
  myAxios.delete<any, T>(url, config);

request.patch = <T = any>(url: string, data?: any, config?: AxiosRequestConfig) =>
  myAxios.patch<any, T>(url, data, config);

export default request;
