import React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import MainLayout from '@/layouts/MainLayout';
import Login from '@/pages/Login';
import Workbench from '@/pages/Workbench';
import ComingSoon from '@/pages/ComingSoon';
import NotFound from '@/pages/NotFound';
import Forbidden from '@/pages/Forbidden';
import CategoryList from '@/pages/master/CategoryList';
import SupplierList from '@/pages/master/SupplierList';
import ProductList from '@/pages/master/ProductList';
import TemplateList from '@/pages/standard/TemplateList';
import ProcessList from '@/pages/process/ProcessList';
import SamplingList from '@/pages/sampling/SamplingList';
import SampleList from '@/pages/sample/SampleList';
import TaskList from '@/pages/inspection/TaskList';
import TaskWorkbench from '@/pages/inspection/TaskWorkbench';
import ReportList from '@/pages/inspection/ReportList';
import CaseList from '@/pages/defect/CaseList';
import CaseDetailPage from '@/pages/defect/CaseDetail';
import RectificationList from '@/pages/defect/RectificationList';
import BatchList from '@/pages/batch/BatchList';
import AlertList from '@/pages/alert/AlertList';
import ProjectListNpi from '@/pages/npi/ProjectList';
import ProjectDetailNpi from '@/pages/npi/ProjectDetail';
import AuditListNpi from '@/pages/npi/AuditList';
import ExtTestListNpi from '@/pages/npi/ExtTestList';
import Dashboard from '@/pages/dashboard/Dashboard';
import LedgerGoodsList from '@/pages/ledger/GoodsList';
import LedgerGoodsDetail from '@/pages/ledger/GoodsDetail';
import LedgerTemplateList from '@/pages/ledger/FlowTemplateList';
import LedgerDemoSetting from '@/pages/ledger/DemoSetting';
import LedgerDashboard from '@/pages/ledger/LedgerDashboard';
import AuditLogs from '@/pages/system/AuditLogs';
import LoginLogs from '@/pages/system/LoginLogs';
import { useAuthStore } from '@/store/authStore';

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = useAuthStore((s) => s.accessToken);
  const location = useLocation();
  if (!token) {
    // 必须用路由上下文里的 location（登出瞬间全局 location 可能已切到 /login）
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  return <>{children}</>;
};

const RequirePerm: React.FC<{ perm?: string; children: React.ReactNode }> = ({ perm, children }) => {
  const hasPerm = useAuthStore((s) => s.hasPerm);
  const user = useAuthStore((s) => s.user);
  if (!user) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }
  if (perm && !hasPerm(perm)) {
    return <Forbidden />;
  }
  return <>{children}</>;
};

/** 后续阶段菜单的占位路由 */
const soon = (key: string, perm: string) => (
  <Route key={key} path={key} element={<RequirePerm perm={perm}><ComingSoon /></RequirePerm>} />
);

const AppRouter: React.FC = () => (
  <Routes>
    <Route path="/login" element={<Login />} />
    <Route
      path="/"
      element={
        <RequireAuth>
          <MainLayout />
        </RequireAuth>
      }
    >
      <Route index element={<Navigate to="/workbench" replace />} />
      <Route path="workbench" element={<RequirePerm perm="workbench:view"><Workbench /></RequirePerm>} />

      <Route path="sampling" element={<RequirePerm perm="sampling:list"><SamplingList /></RequirePerm>} />
      <Route path="samples" element={<RequirePerm perm="sample:list"><SampleList /></RequirePerm>} />
      <Route path="inspection/tasks" element={<RequirePerm perm="inspection:task:list"><TaskList /></RequirePerm>} />
      <Route
        path="inspection/tasks/:id"
        element={<RequirePerm perm="inspection:task:list"><TaskWorkbench /></RequirePerm>}
      />
      <Route path="inspection/reports" element={<RequirePerm perm="inspection:report:view"><ReportList /></RequirePerm>} />
      <Route path="defect" element={<RequirePerm perm="defect:list"><CaseList /></RequirePerm>} />
      <Route
        path="defect/:id"
        element={<RequirePerm perm="defect:view"><CaseDetailPage /></RequirePerm>}
      />
      <Route path="rectification" element={<RequirePerm perm="rect:list"><RectificationList /></RequirePerm>} />
      <Route path="batches" element={<RequirePerm perm="batch:list"><BatchList /></RequirePerm>} />
      <Route path="alerts" element={<RequirePerm perm="alert:list"><AlertList /></RequirePerm>} />

      <Route path="npi/projects" element={<RequirePerm perm="npi:project:list"><ProjectListNpi /></RequirePerm>} />
      <Route
        path="npi/projects/:id"
        element={<RequirePerm perm="npi:project:view"><ProjectDetailNpi /></RequirePerm>}
      />
      <Route path="npi/audits" element={<RequirePerm perm="npi:audit:list"><AuditListNpi /></RequirePerm>} />
      <Route path="npi/ext-tests" element={<RequirePerm perm="npi:exttest:list"><ExtTestListNpi /></RequirePerm>} />

      <Route path="master/products" element={<RequirePerm perm="master:product:list"><ProductList /></RequirePerm>} />
      <Route path="master/suppliers" element={<RequirePerm perm="master:supplier:list"><SupplierList /></RequirePerm>} />
      <Route
        path="master/categories"
        element={
          <RequirePerm perm="master:category:list">
            <CategoryList />
          </RequirePerm>
        }
      />

      <Route path="standard/templates" element={<RequirePerm perm="std:template:list"><TemplateList /></RequirePerm>} />
      <Route path="process" element={<RequirePerm perm="process:def:list"><ProcessList /></RequirePerm>} />
      <Route path="dashboard" element={<RequirePerm perm="dashboard:view"><Dashboard /></RequirePerm>} />

      {/* 商品全流程品控台账（v1.2.0 新增） */}
      <Route
        path="ledger/dashboard"
        element={<RequirePerm perm="ledger:dashboard:view"><LedgerDashboard /></RequirePerm>}
      />
      <Route path="ledger/goods" element={<RequirePerm perm="ledger:goods:list"><LedgerGoodsList /></RequirePerm>} />
      <Route
        path="ledger/goods/:id"
        element={<RequirePerm perm="ledger:goods:view"><LedgerGoodsDetail /></RequirePerm>}
      />
      <Route
        path="ledger/templates"
        element={<RequirePerm perm="ledger:template:list"><LedgerTemplateList /></RequirePerm>}
      />
      <Route
        path="ledger/demo-setting"
        element={<RequirePerm perm="ledger:demo:view"><LedgerDemoSetting /></RequirePerm>}
      />

      <Route path="system/users" element={<RequirePerm perm="system:user:list"><ComingSoon /></RequirePerm>} />
      <Route path="system/roles" element={<RequirePerm perm="system:role:list"><ComingSoon /></RequirePerm>} />
      <Route path="system/depts" element={<RequirePerm perm="system:dept:list"><ComingSoon /></RequirePerm>} />
      <Route path="system/dicts" element={<RequirePerm perm="system:dict:list"><ComingSoon /></RequirePerm>} />
      <Route
        path="system/audit-logs"
        element={
          <RequirePerm perm="audit:list">
            <AuditLogs />
          </RequirePerm>
        }
      />
      <Route
        path="system/login-logs"
        element={
          <RequirePerm perm="audit:login">
            <LoginLogs />
          </RequirePerm>
        }
      />
    </Route>
    <Route path="*" element={<NotFound />} />
  </Routes>
);

export default AppRouter;
