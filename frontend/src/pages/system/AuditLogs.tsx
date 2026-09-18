import React, { useState } from 'react';
import { Button, Card, Form, Input, Space, Table, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { auditApi } from '@/api/audit';
import type { AuditLog } from '@/types/auth';

const AuditLogs: React.FC = () => {
  const [params, setParams] = useState<Record<string, unknown>>({ pageNo: 1, pageSize: 10 });
  const [form] = Form.useForm();

  const query = useQuery({ queryKey: ['audit-logs', params], queryFn: () => auditApi.logs(params) });

  const columns: ColumnsType<AuditLog> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '模块', dataIndex: 'module', width: 120 },
    { title: '动作', dataIndex: 'action', width: 120 },
    { title: '业务类型', dataIndex: 'bizType', width: 140 },
    { title: '业务ID', dataIndex: 'bizId', width: 100 },
    { title: '操作人', dataIndex: 'username', width: 110 },
    { title: 'IP', dataIndex: 'ip', width: 130 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: number) => (v === 1 ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
    },
    { title: '耗时(ms)', dataIndex: 'costMs', width: 90 },
    { title: '时间', dataIndex: 'createdAt', width: 180 },
    {
      title: '变更内容',
      key: 'change',
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          {r.beforeValue ? <span style={{ color: '#cf1322', fontSize: 12 }}>前: {r.beforeValue}</span> : null}
          {r.afterValue ? <span style={{ color: '#389e0d', fontSize: 12 }}>后: {r.afterValue}</span> : null}
        </Space>
      ),
    },
  ];

  return (
    <Card title="操作审计日志（只增不删）">
      <Form
        form={form}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setParams({ ...values, pageNo: 1, pageSize: 10 })}
      >
        <Form.Item name="module" label="模块">
          <Input allowClear placeholder="如 MASTER" />
        </Form.Item>
        <Form.Item name="username" label="操作人">
          <Input allowClear />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit">
            查询
          </Button>
        </Form.Item>
      </Form>
      <Table<AuditLog>
        rowKey="id"
        size="small"
        loading={query.isLoading}
        columns={columns}
        dataSource={query.data?.records || []}
        scroll={{ x: 1200 }}
        locale={{ emptyText: query.isError ? '加载失败，请稍后重试' : '暂无审计记录' }}
        pagination={{
          current: params.pageNo as number,
          pageSize: params.pageSize as number,
          total: Number(query.data?.total) || 0,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (pageNo, pageSize) => setParams({ ...form.getFieldsValue(), pageNo, pageSize }),
        }}
      />
    </Card>
  );
};

export default AuditLogs;
