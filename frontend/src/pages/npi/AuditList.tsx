import React, { useState } from 'react';
import {
  Button,
  Card,
  Form,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { auditApi, npiApi } from '@/api/npi';
import type { AuditQuery } from '@/api/npi';
import { AUDIT_CONCLUSION, AUDIT_STATUS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { AuditListRow } from '@/types/npi';
import AuditDrawer from './components/AuditDrawer';

const AuditList: React.FC = () => {
  const [query, setQuery] = useState<AuditQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['npi', 'audits', query], queryFn: () => auditApi.page(query) });
  const projects = useQuery({ queryKey: ['npi', 'project-options'], queryFn: () => npiApi.page({ pageNo: 1, pageSize: 200 }) });

  const [drawer, setDrawer] = useState<{ id: LongId | null; projectId?: LongId } | null>(null);
  const [pickOpen, setPickOpen] = useState(false);
  const [pickForm] = Form.useForm<{ projectId: LongId }>();

  const columns: ColumnsType<AuditListRow> = [
    {
      title: '验厂单号',
      dataIndex: ['audit', 'auditNo'],
      width: 150,
      render: (v: string) => <span style={{ fontFamily: 'monospace' }}>{v}</span>,
    },
    { title: '所属项目', dataIndex: 'projectName', width: 180 },
    {
      title: '类型',
      dataIndex: ['audit', 'auditType'],
      width: 80,
      render: (v: string) => (v === 'FOLLOW_UP' ? '复审' : '初审'),
    },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    { title: '计划时间', dataIndex: ['audit', 'plannedAt'], width: 160, render: (v?: string) => v || '-' },
    { title: '实审时间', dataIndex: ['audit', 'auditedAt'], width: 160, render: (v?: string) => v || '-' },
    { title: '验厂组', dataIndex: ['audit', 'auditors'], ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '总分',
      dataIndex: ['audit', 'totalScore'],
      width: 80,
      render: (v?: number | string | null) => (v === null || v === undefined ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '结论',
      dataIndex: ['audit', 'conclusion'],
      width: 100,
      render: (v?: string | null) =>
        v ? <Tag color={AUDIT_CONCLUSION.find((c) => c.value === v)?.color}>{AUDIT_CONCLUSION.find((c) => c.value === v)?.label}</Tag> : '-',
    },
    {
      title: '状态',
      dataIndex: ['audit', 'status'],
      width: 90,
      render: (v: string) => <Tag color={AUDIT_STATUS.find((s) => s.value === v)?.color}>{AUDIT_STATUS.find((s) => s.value === v)?.label}</Tag>,
    },
    {
      title: '操作',
      key: 'ops',
      width: 90,
      render: (_, r) => (
        <Button type="link" size="small" onClick={() => setDrawer({ id: r.audit.id, projectId: r.audit.projectId })}>
          {r.audit.status === 'CONFIRMED' ? '查看' : '处理'}
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="实地验厂单"
      extra={
        <AuthButton type="primary" perm="npi:audit:edit" onClick={() => { pickForm.resetFields(); setPickOpen(true); }}>
          新建验厂单
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(v) => setQuery({ ...query, ...v, pageNo: 1 })}
      >
        <Form.Item name="projectId" label="所属项目">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 260 }}
            placeholder="全部项目"
            options={(projects.data?.records || []).map((r) => ({ value: r.project.id, label: `${r.project.projectNo} ${r.project.projectName}` }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select allowClear style={{ width: 140 }} placeholder="全部状态" options={AUDIT_STATUS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<AuditListRow>
        rowKey={(r) => r.audit.id as string}
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        scroll={{ x: 1300 }}
        pagination={{
          current: query.pageNo,
          pageSize: query.pageSize,
          total: Number(pageQuery.data?.total) || 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (pageNo, pageSize) => setQuery({ ...query, pageNo, pageSize }),
        }}
      />

      <Modal
        title="选择验厂所属项目"
        open={pickOpen}
        onCancel={() => setPickOpen(false)}
        onOk={async () => {
          const v = await pickForm.validateFields();
          setPickOpen(false);
          setDrawer({ id: null, projectId: v.projectId });
        }}
        // forceRender：按钮 onClick 中先 pickForm.resetFields 再打开
        forceRender
      >
        <Form form={pickForm} layout="vertical">
          <Form.Item name="projectId" label="新品项目" rules={[{ required: true, message: '请选择项目' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择新品项目"
              options={(projects.data?.records || [])
                .filter((r) => ['SOURCING', 'AUDIT', 'PRODUCING'].includes(r.project.status))
                .map((r) => ({ value: r.project.id, label: `${r.project.projectNo} ${r.project.projectName}` }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {drawer?.projectId && (
        <AuditDrawer
          id={drawer.id}
          projectId={drawer.projectId}
          onClose={() => setDrawer(null)}
        />
      )}
    </Card>
  );
};

export default AuditList;
