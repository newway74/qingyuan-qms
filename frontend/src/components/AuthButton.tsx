import React from 'react';
import { Button } from 'antd';
import { useAuthStore } from '@/store/authStore';

interface AuthButtonProps {
  perm: string;
  children: React.ReactNode;
  onClick?: () => void;
  type?: 'link' | 'text' | 'default' | 'primary' | 'dashed';
  danger?: boolean;
  ghost?: boolean;
  icon?: React.ReactNode;
  size?: 'small' | 'middle' | 'large';
  disabled?: boolean;
  loading?: boolean;
}

/**
 * 按钮级权限：无权限码时不渲染（后端同时有 @PreAuthorize 双重校验）。
 * 使用 forwardRef：当外层是 Tooltip/Popconfirm 等依赖触发器 ref 的组件时，
 * 避免其回退到 findDOMNode（React.StrictMode 下会告警）。
 */
const AuthButton = React.forwardRef<HTMLButtonElement, AuthButtonProps>(
  ({ perm, children, ...rest }, ref) => {
    const hasPerm = useAuthStore((s) => s.hasPerm(perm));
    if (!hasPerm) return null;
    return (
      <Button ref={ref} {...rest}>
        {children}
      </Button>
    );
  },
);
AuthButton.displayName = 'AuthButton';

export default AuthButton;
