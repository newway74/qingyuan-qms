import React from 'react';
import { Card, Result } from 'antd';
import { useLocation } from 'react-router-dom';

/**
 * 阶段1未交付菜单的统一占位页（保持菜单可点、路由可达）
 */
const ComingSoon: React.FC = () => {
  const location = useLocation();
  return (
    <Card variant="borderless">
      <Result
        status="info"
        title="功能建设中"
        subTitle={
          <>
            模块 <code>{location.pathname}</code> 将在后续阶段交付，当前阶段（阶段1）已提供：
            登录鉴权、RBAC 按钮权限、审计日志、品类管理样例。
          </>
        }
      />
    </Card>
  );
};

export default ComingSoon;
