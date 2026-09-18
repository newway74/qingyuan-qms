import React from 'react';
import {
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BarcodeOutlined,
  BookOutlined,
  BranchesOutlined,
  ContainerOutlined,
  DashboardOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  GoldOutlined,
  LoginOutlined,
  ProfileOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SafetyOutlined,
  ShopOutlined,
  SolutionOutlined,
  TeamOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';

export interface MenuItemConfig {
  key: string;
  perm?: string;
  icon?: React.ReactNode;
  label: string;
  children?: MenuItemConfig[];
}

/**
 * 菜单定义（静态路由 + 权限码过滤，与后端 sys_permission 一致）。
 * 后续可切换为后端下发，结构保持兼容。
 */
export const menuConfig: MenuItemConfig[] = [
  { key: '/workbench', perm: 'workbench:view', icon: <DashboardOutlined />, label: '品控工作台' },
  {
    key: 'sampling-group',
    icon: <AuditOutlined />,
    label: '抽样与样品',
    children: [
      { key: '/sampling', perm: 'sampling:list', icon: <ProfileOutlined />, label: '抽样管理' },
      { key: '/samples', perm: 'sample:list', icon: <BarcodeOutlined />, label: '样品管理' },
    ],
  },
  {
    key: 'inspection-group',
    icon: <ExperimentOutlined />,
    label: '检验管理',
    children: [
      { key: '/inspection/tasks', perm: 'inspection:task:list', label: '检验任务' },
      { key: '/inspection/reports', perm: 'inspection:report:view', label: '质检报告' },
    ],
  },
  {
    key: 'defect-group',
    icon: <WarningOutlined />,
    label: '不合格闭环',
    children: [
      { key: '/defect', perm: 'defect:list', label: '不合格处置' },
      { key: '/rectification', perm: 'rect:list', label: '供应商整改' },
      { key: '/batches', perm: 'batch:list', label: '批次效期台账' },
      { key: '/alerts', perm: 'alert:list', label: '质量预警' },
    ],
  },
  {
    key: 'npi-group',
    icon: <RocketOutlined />,
    label: '新品引入',
    children: [
      { key: '/npi/projects', perm: 'npi:project:list', icon: <RocketOutlined />, label: '新品项目' },
      { key: '/npi/audits', perm: 'npi:audit:list', icon: <AuditOutlined />, label: '实地验厂' },
      { key: '/npi/ext-tests', perm: 'npi:exttest:list', icon: <SafetyCertificateOutlined />, label: '外检送检' },
    ],
  },
  {
    key: 'ledger-group',
    icon: <GoldOutlined />,
    label: '商品品控台账',
    children: [
      { key: '/ledger/dashboard', perm: 'ledger:dashboard:view', icon: <BarChartOutlined />, label: '品控工作台' },
      { key: '/ledger/goods', perm: 'ledger:goods:list', icon: <ContainerOutlined />, label: '商品台账' },
      { key: '/ledger/templates', perm: 'ledger:template:list', icon: <BranchesOutlined />, label: '流程模板配置' },
      { key: '/ledger/demo-setting', perm: 'ledger:demo:view', icon: <DeleteOutlined />, label: '演示数据管理' },
    ],
  },
  {
    key: 'master-group',
    icon: <ShopOutlined />,
    label: '主数据',
    children: [
      { key: '/master/products', perm: 'master:product:list', icon: <ShopOutlined />, label: '产品档案' },
      { key: '/master/suppliers', perm: 'master:supplier:list', icon: <TeamOutlined />, label: '供应商' },
      { key: '/master/categories', perm: 'master:category:list', icon: <AppstoreOutlined />, label: '品类' },
    ],
  },
  {
    key: 'config-group',
    icon: <BranchesOutlined />,
    label: '质量标准与流程',
    children: [
      { key: '/standard/templates', perm: 'std:template:list', icon: <SolutionOutlined />, label: '检验标准库' },
      { key: '/process', perm: 'process:def:list', icon: <BranchesOutlined />, label: '品控流程配置' },
    ],
  },
  { key: '/dashboard', perm: 'dashboard:view', icon: <BarChartOutlined />, label: '质量看板' },
  {
    key: 'system-group',
    icon: <SafetyOutlined />,
    label: '系统管理',
    children: [
      { key: '/system/users', perm: 'system:user:list', icon: <UserOutlined />, label: '用户管理' },
      { key: '/system/roles', perm: 'system:role:list', icon: <SafetyOutlined />, label: '角色权限' },
      { key: '/system/dicts', perm: 'system:dict:list', icon: <BookOutlined />, label: '数据字典' },
      { key: '/system/audit-logs', perm: 'audit:list', icon: <FileSearchOutlined />, label: '操作审计日志' },
      { key: '/system/login-logs', perm: 'audit:login', icon: <LoginOutlined />, label: '登录日志' },
    ],
  },
];
