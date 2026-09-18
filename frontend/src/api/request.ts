import axios, { AxiosError, AxiosRequestConfig } from 'axios';

import type { R } from '@/types/api';
import { useAuthStore } from '@/store/authStore';
import message from '@/utils/feedback';

/**
 * 自定义请求配置：silent=true 时不弹全局错误提示，
 * 由调用方在自己的交互容器内联展示错误（如“新建品类”弹窗）。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean;
  }
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 20000,
});

function uuidKey() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

request.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // 写操作自动携带幂等键（业务也可显式传入覆盖）
  if (['post', 'put', 'patch'].includes((config.method || '').toLowerCase()) && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = uuidKey();
  }
  return config;
});

let redirecting = false;

request.interceptors.response.use(
  (response) => {
    const body = response.data as R<unknown>;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === '0') {
        return body.data as never;
      }
      if (!response.config.silent) {
        message.error(body.message || '请求失败');
      }
      return Promise.reject(new BizError(body.code, body.message));
    }
    return response.data;
  },
  (error: AxiosError<R<unknown>>) => {
    const status = error.response?.status;
    const body = error.response?.data;
    const silent = error.config?.silent;
    if (status === 401) {
      if (!redirecting) {
        redirecting = true;
        message.warning('登录已过期，请重新登录');
        useAuthStore.getState().clearAuth();
        const redirect = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login?redirect=${redirect}`;
        setTimeout(() => (redirecting = false), 1500);
      }
    } else if (status === 403) {
      if (!silent) message.error(body?.message || '没有操作权限');
    } else if (body?.message) {
      if (!silent) message.error(body.message);
    } else if (!silent) {
      message.error('网络异常，请稍后重试');
    }
    return Promise.reject(error);
  },
);

export class BizError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.get(url, config) as unknown as Promise<T>;
}
export function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.post(url, data, config) as unknown as Promise<T>;
}
export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  return request.put(url, data, config) as unknown as Promise<T>;
}
export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return request.delete(url, config) as unknown as Promise<T>;
}

export default request;
