import React, { useEffect, useMemo, useState } from 'react';
import { Avatar, Dropdown, Layout, Menu, Tag, Typography } from 'antd';
import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { menuConfig, MenuItemConfig } from './menuConfig';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '@/api/auth';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const ROLE_LABEL: Record<string, string> = {
  ADMIN: '系统管理员',
  SAMPLER: '抽样收样员',
  INSPECTOR: '检验员',
  REVIEWER: '复核判定人',
  QA_MANAGER: '质量主管',
};

function filterMenus(items: MenuItemConfig[], hasPerm: (p: string) => boolean): MenuItemConfig[] {
  return items
    .filter((item) => !item.perm || hasPerm(item.perm))
    .map((item) => ({
      ...item,
      children: item.children ? filterMenus(item.children, hasPerm) : undefined,
    }))
    .filter((item) => !item.children || item.children.length > 0);
}

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, hasPerm, clearAuth } = useAuthStore();

  // 进入主框架后刷新一次权限信息（角色/权限可能被管理员调整）
  const userInfoQuery = useQuery({ queryKey: ['userinfo'], queryFn: authApi.userInfo });
  useEffect(() => {
    if (userInfoQuery.data) {
      useAuthStore.getState().setUser(userInfoQuery.data);
    }
  }, [userInfoQuery.data]);

  const menus = useMemo(() => filterMenus(menuConfig, hasPerm), [hasPerm]);

  const selectedKey = location.pathname;
  const openKey = menus.find((m) => m.children?.some((c) => c.key === selectedKey))?.key;

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } finally {
      clearAuth();
      navigate('/login', { replace: true });
    }
  };

  const menuItems = menus.map((item) => {
    if (item.children) {
      return {
        key: item.key,
        icon: item.icon,
        label: item.label,
        children: item.children.map((child) => ({ key: child.key, icon: child.icon, label: child.label })),
      };
    }
    return { key: item.key, icon: item.icon, label: item.label };
  });

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} theme="dark" width={220}>
        <div
          style={{
            height: 56,
            margin: 8,
            borderRadius: 6,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 600,
            fontSize: collapsed ? 14 : 16,
            background: 'rgba(255,255,255,0.08)',
          }}
        >
          {collapsed ? '品控' : '清源 QMS 品控工作台'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={openKey ? [openKey] : []}
          items={menuItems}
          onClick={({ key }) => {
            if (key.startsWith('/')) navigate(key);
          }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,21,41,0.08)',
          }}
        >
          <div onClick={() => setCollapsed(!collapsed)} style={{ fontSize: 18, cursor: 'pointer' }}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>
          <Dropdown
            menu={{
              items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout }],
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <Text>{user?.realName || user?.username}</Text>
              {(user?.roles || []).slice(0, 1).map((role) => (
                <Tag key={role} color="blue">
                  {ROLE_LABEL[role] || role}
                </Tag>
              ))}
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
