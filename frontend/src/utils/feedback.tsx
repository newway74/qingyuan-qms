import { useEffect } from 'react';
import { App as AntdApp, message as staticMessage } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';

/**
 * 全局反馈（message）桥接：
 * antd v5 的 message.success() 等“静态方法”无法消费 ConfigProvider 主题/App 上下文，
 * 控制台会输出 Static function can not consume context 警告。
 * 这里在 <App> 上下文内通过 App.useApp() 拿到动态 message 实例并注册，
 * 非组件模块（axios 拦截器）与未直接使用 useApp 的组件统一经 messageProxy 调用：
 * 桥接挂载前回退静态实例，挂载后自动走上下文实例。
 */
let dynamicMessage: MessageInstance | null = null;

export function bindMessage(api: MessageInstance): void {
  dynamicMessage = api;
}

/** 在 antd <App> 内部挂载一次即可 */
export function FeedbackBridge() {
  const { message } = AntdApp.useApp();
  useEffect(() => {
    dynamicMessage = message;
    return () => {
      dynamicMessage = null;
    };
  }, [message]);
  return null;
}

const messageProxy: MessageInstance = new Proxy({} as MessageInstance, {
  get(_target, property) {
    const api: Record<string | symbol, unknown> = (dynamicMessage ??
      staticMessage) as unknown as Record<string | symbol, unknown>;
    const fn = api[property];
    return typeof fn === 'function' ? (fn as (...args: unknown[]) => unknown).bind(api) : fn;
  },
});

export default messageProxy;
