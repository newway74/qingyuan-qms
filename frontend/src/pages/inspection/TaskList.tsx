import React, { useState } from 'react';
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tabs, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { taskApi, TaskQuery } from '@/api/inspection';
import { TASK_STATUS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { TaskListRow, UserOption } from '@/types/inspection';
import message from '@/utils/feedback';

const statusMeta = (v: string) => TASK_STATUS.find((s) => s.value === v);

const TaskList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'PENDING_ASSIGN' | 'MINE' | 'ALL'>('PENDING_ASSIGN');
  const [query, setQuery] = useState<TaskQuery>({ pageNo: 1, pageSize: 10, view: 'PENDING_ASSIGN' });
  const [searchNo, setSearchNo] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>();
  const pageQuery = useQuery({ queryKey: ['task-page', query], queryFn: () => taskApi.page(query) });

  const [assignOf, setAssignOf] = useState<TaskListRow | null>(null);
  const [assignForm] = Form.useForm<{ inspectorId: LongId; reviewerId: LongId }>();
  const inspectors = useQuery({
    queryKey: ['task-users', 'INSPECTOR'],
    queryFn: () => taskApi.usersByRole('INSPECTOR'),
    enabled: !!assignOf,
  });
  const reviewers = useQuery({
    queryKey: ['task-users', 'REVIEWER'],
    queryFn: () => taskApi.usersByRole('REVIEWER'),
    enabled: !!assignOf,
  });

  const assignMutation = useMutation({
    mutationFn: async () => {
      const v = await assignForm.validateFields();
      if (assignOf) await taskApi.assign(assignOf.id, v.inspectorId, v.reviewerId);
    },
    onSuccess: () => {
      message.success('任务已分配，按流程 SLA 开始计时');
      setAssignOf(null);
      invalidate();
    },
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['task-page'] });
  };

  const switchTab = (key: string) => {
    const view = key as TaskQuery['view'];
    setTab(view || 'PENDING_ASSIGN');
    setQuery({ pageNo: 1, pageSize: 10, view, ...(view === 'ALL' && statusFilter ? { status: statusFilter } : {}) });
  };

  const columns: ColumnsType<TaskListRow> = [
    { title: '任务单号', dataIndex: 'taskNo', width: 160, fixed: 'left' },
    { title: '样品编号', dataIndex: 'sampleNo', width: 160 },
    { title: '产品', dataIndex: 'productName', width: 180, render: (v: string, r) => `${v || ''} ${r.spec || ''}` },
    { title: '批号', dataIndex: 'batchNo', width: 140 },
    { title: '标准模板', dataIndex: 'templateName', width: 170, render: (v: string, r) => (v ? `${v} v${r.templateVersion}` : '-') },
    { title: '检验员', dataIndex: 'inspectorName', width: 90, render: (v: string) => v || '-' },
    { title: '复核人', dataIndex: 'reviewerName', width: 90, render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    {
      title: 'SLA截止',
      dataIndex: 'slaDeadline',
      width: 165,
      render: (v?: string) => {
        if (!v) return '-';
        const overdue = new Date(v).getTime() < Date.now();
        return <Tag color={overdue ? 'red' : 'default'}>{v.replace('T', ' ').slice(0, 16)}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => navigate(`/inspection/tasks/${r.id}`)}>
            进入工作台
          </Button>
          {r.status === 'PENDING_ASSIGN' && (
            <AuthButton perm="inspection:task:assign" type="link" size="small" onClick={() => setAssignOf(r)}>
              分配
            </AuthButton>
          )}
        </Space>
      ),
    },
  ];

  const userOptions = (list?: UserOption[]) =>
    (list || []).map((u) => ({ value: u.id, label: `${u.realName}（${u.username}）` }));

  return (
    <Card title="检验任务">
      <Tabs
        activeKey={tab}
        onChange={switchTab}
        items={[
          { key: 'PENDING_ASSIGN', label: '待分配' },
          { key: 'MINE', label: '我的待办' },
          {
            key: 'ALL',
            label: '全部任务',
            forceRender: true,
          },
        ]}
        tabBarExtraContent={
          <Space>
            {tab === 'ALL' && (
              <Select
                allowClear
                placeholder="按状态筛选"
                style={{ width: 140 }}
                value={statusFilter}
                onChange={(v) => {
                  setStatusFilter(v);
                  setQuery((q) => ({ ...q, status: v, pageNo: 1 }));
                }}
                options={TASK_STATUS.map((s) => ({ value: s.value, label: s.label }))}
              />
            )}
            <Input.Search
              placeholder="任务单号"
              style={{ width: 170 }}
              value={searchNo}
              onChange={(e) => setSearchNo(e.target.value)}
              onSearch={(v) => setQuery((q) => ({ ...q, taskNo: v || undefined, pageNo: 1 }))}
              allowClear
            />
          </Space>
        }
      />
      <Table<TaskListRow>
        rowKey={(r) => String(r.id)}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        loading={pageQuery.isLoading}
        scroll={{ x: 1500 }}
        pagination={{
          current: Number(pageQuery.data?.pageNo || 1),
          pageSize: Number(pageQuery.data?.pageSize || 10),
          total: Number(pageQuery.data?.total || 0),
          showSizeChanger: true,
          onChange: (pageNo, pageSize) => setQuery((q) => ({ ...q, pageNo, pageSize })),
        }}
      />

      <Modal
        title={assignOf ? `分配任务 ${assignOf.taskNo}` : ''}
        open={!!assignOf}
        onCancel={() => setAssignOf(null)}
        onOk={() => assignMutation.mutate()}
        confirmLoading={assignMutation.isPending}
        destroyOnHidden
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            name="inspectorId"
            label="检验员"
            rules={[{ required: true, message: '请选择检验员' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择检验员"
              loading={inspectors.isLoading}
              options={userOptions(inspectors.data)}
            />
          </Form.Item>
          <Form.Item
            name="reviewerId"
            label="复核人"
            rules={[{ required: true, message: '请选择复核人' }]}
            extra="检验员与复核人不能为同一人"
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择复核人"
              loading={reviewers.isLoading}
              options={userOptions(reviewers.data)}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default TaskList;
