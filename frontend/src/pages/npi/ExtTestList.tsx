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
import { extTestApi, npiApi } from '@/api/npi';
import type { ExtTestQuery } from '@/api/npi';
import { EXT_CONCLUSION, EXT_STATUS, LAB_QUALIFICATIONS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { ExtTestListRow } from '@/types/npi';
import ExtTestDrawer from './components/ExtTestDrawer';

const ExtTestList: React.FC = () => {
  const [query, setQuery] = useState<ExtTestQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['npi', 'exttests', query], queryFn: () => extTestApi.page(query) });
  const projects = useQuery({ queryKey: ['npi', 'project-options'], queryFn: () => npiApi.page({ pageNo: 1, pageSize: 200 }) });

  const [drawer, setDrawer] = useState<{ id: LongId | null; projectId?: LongId } | null>(null);
  const [pickOpen, setPickOpen] = useState(false);
  const [pickForm] = Form.useForm<{ projectId: LongId }>();

  const columns: ColumnsType<ExtTestListRow> = [
    {
      title: '外检单号',
      dataIndex: ['test', 'testNo'],
      width: 150,
      render: (v: string) => <span style={{ fontFamily: 'monospace' }}>{v}</span>,
    },
    { title: '所属项目', dataIndex: 'projectName', width: 180 },
    { title: '机构', dataIndex: ['test', 'labName'], width: 200, render: (v?: string) => v || '-' },
    {
      title: '资质',
      dataIndex: ['test', 'labQualification'],
      width: 110,
      render: (v?: string) => <Tag>{LAB_QUALIFICATIONS.find((q) => q.value === v)?.label || v || '-'}</Tag>,
    },
    { title: '送检项目', dataIndex: ['test', 'testItems'], ellipsis: true, render: (v?: string) => v || '-' },
    { title: '报告编号', dataIndex: ['test', 'reportNo'], width: 160, render: (v?: string) => v || '-' },
    { title: '报告日期', dataIndex: ['test', 'reportDate'], width: 110, render: (v?: string) => v || '-' },
    {
      title: '结论',
      dataIndex: ['test', 'conclusion'],
      width: 100,
      render: (v?: string) => <Tag color={EXT_CONCLUSION.find((c) => c.value === v)?.color}>{EXT_CONCLUSION.find((c) => c.value === v)?.label || '检测中'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: ['test', 'status'],
      width: 90,
      render: (v: string) => <Tag color={EXT_STATUS.find((s) => s.value === v)?.color}>{EXT_STATUS.find((s) => s.value === v)?.label}</Tag>,
    },
    {
      title: '操作',
      key: 'ops',
      width: 90,
      render: (_, r) => (
        <Button type="link" size="small" onClick={() => setDrawer({ id: r.test.id, projectId: r.test.projectId })}>
          {r.test.status === 'REPORTED' ? '查看' : '处理'}
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="外检送检单"
      extra={
        <AuthButton type="primary" perm="npi:exttest:edit" onClick={() => { pickForm.resetFields(); setPickOpen(true); }}>
          登记送检
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
          <Select allowClear style={{ width: 140 }} placeholder="全部状态" options={EXT_STATUS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<ExtTestListRow>
        rowKey={(r) => r.test.id as string}
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
        title="选择送检所属项目"
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
                .filter((r) => ['PRODUCING', 'EXT_TEST', 'LISTING_REVIEW'].includes(r.project.status))
                .map((r) => ({ value: r.project.id, label: `${r.project.projectNo} ${r.project.projectName}` }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {drawer?.projectId && (
        <ExtTestDrawer
          id={drawer.id}
          projectId={drawer.projectId}
          onClose={() => setDrawer(null)}
        />
      )}
    </Card>
  );
};

export default ExtTestList;
