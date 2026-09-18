import React, { useState } from 'react';
import { Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import message from '@/utils/feedback';

const { Title, Text } = Typography;

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const setTokens = useAuthStore((s) => s.setTokens);
  const setUser = useAuthStore((s) => s.setUser);

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const loginResp = await authApi.login(values);
      setTokens(loginResp.accessToken, loginResp.refreshToken);
      const userInfo = await authApi.userInfo();
      setUser(userInfo);
      message.success(`欢迎，${userInfo.realName || userInfo.username}`);
      const params = new URLSearchParams(location.search);
      const redirect = params.get('redirect');
      // 只接受站内路径，且排除登录页自身（登出瞬间守卫可能写入 redirect=/login，跳回会形成假死）
      const safeRedirect =
        redirect && redirect.startsWith('/') && !redirect.startsWith('/login') ? redirect : '/workbench';
      navigate(safeRedirect, { replace: true });
    } catch {
      // 错误提示已由 axios 拦截器统一处理
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1e3a5f 0%, #2c5f8a 100%)',
      }}
    >
      <Card style={{ width: 400, borderRadius: 8 }} variant="borderless">
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            清源QMS
          </Title>
          <Text type="secondary">产品品控全流程信息管理系统</Text>
        </div>
        <Form onFinish={onFinish} size="large" initialValues={{ username: 'admin', password: 'Qms@Demo2026' }}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>
        <Text type="secondary" style={{ fontSize: 12 }}>
          演示账号：admin / Qms@Demo2026（sampler、inspector、reviewer、qamanager 同密码）
        </Text>
        <div style={{ fontSize: 12, color: '#fa8c16', marginTop: 4 }}>
          演示环境账号，首次登录后请尽快在「个人中心」修改密码
        </div>
      </Card>
    </div>
  );
};

export default Login;
