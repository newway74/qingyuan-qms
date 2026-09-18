import React from 'react';
import { Card, Result } from 'antd';

const LoginLogs: React.FC = () => (
  <Card title="登录日志">
    <Result status="info" title="登录日志查询页将在阶段6完善" subTitle="登录/登出事件已实时写入 sys_login_log。" />
  </Card>
);

export default LoginLogs;
