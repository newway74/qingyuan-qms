import React, { useState } from 'react';
import { Button, Card, Form, Input, Select, Space, Statistic, Table, Tag } from 'antd';
import { ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { batchApi, BatchQuery } from '@/api/defect';
import { BATCH_STATUS } from '@/constants/qms';
import type { BatchRow } from '@/types/defect';
import message from '@/utils/feedback';

const statusMeta = (v: string) => BATCH_STATUS.find((s) => s.value === v);

const BatchList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<BatchQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['batch-page', query], queryFn: () => batchApi.page(query) });
  const summaryQuery = useQuery({ queryKey: ['batch-summary'], queryFn: () => batchApi.summary() });

  const recomputeMut = useMutation({
    mutationFn: () => batchApi.recompute(),
    onSuccess: (r) => {
      message.success(`效期重算完成：扫描 ${r.scanned} 批，状态更新 ${r.changed} 批`);
      void queryClient.invalidateQueries({ queryKey: ['batch-page'] });
      void queryClient.invalidateQueries({ queryKey: ['batch-summary'] });
    },
  });

  const columns: ColumnsType<BatchRow> = [
    { title: '批号', dataIndex: ['batch', 'batchNo'], width: 160, fixed: 'left' },
    { title: 'SKU', dataIndex: 'skuCode', width: 150 },
    { title: '产品', dataIndex: 'productName', width: 200, render: (v: string, r) => `${v || ''} ${r.spec || ''}` },
    { title: '生产日期', dataIndex: ['batch', 'productionDate'], width: 110 },
    { title: '效期至', dataIndex: ['batch', 'expiryDate'], width: 110 },
    {
      title: '剩余天数',
      dataIndex: 'daysToExpiry',
      width: 100,
      align: 'center',
      render: (v?: number) => {
        if (v === undefined || v === null) {
          return '-';
        }
        if (v < 0) {
          return <Tag color="red">已过期 {-v} 天</Tag>;
        }
        if (v <= 30) {
          return <Tag color="orange">{v} 天</Tag>;
        }
        return <span>{v} 天</span>;
      },
    },
    {
      title: '状态',
      dataIndex: ['batch', 'status'],
      width: 100,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    { title: '贮存条件', dataIndex: ['batch', 'storageCondition'], width: 120 },
    { title: '最近收样', dataIndex: 'latestReceiveDate', width: 110 },
    { title: '最近盘点', dataIndex: 'latestCheckAt', width: 160, render: (v?: string) => v?.replace('T', ' ').slice(0, 16) || '-' },
  ];

  return (
    <Card
      title="批次效期台账"
      extra={
        <AuthButton perm="batch:recompute" icon={<SyncOutlined />} loading={recomputeMut.isPending} onClick={() => recomputeMut.mutate()}>
          重算效期
        </AuthButton>
      }
    >
      <Space size={32} style={{ marginBottom: 16 }}>
        <Statistic title="正常批次" value={Number(summaryQuery.data?.NORMAL ?? 0)} valueStyle={{ color: '#52c41a' }} />
        <Statistic title="近效期批次（≤30天）" value={Number(summaryQuery.data?.NEAR_EXPIRY ?? 0)} valueStyle={{ color: '#fa8c16' }} />
        <Statistic title="已过期批次" value={Number(summaryQuery.data?.EXPIRED ?? 0)} valueStyle={{ color: '#f5222d' }} />
      </Space>

      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="batchNo" label="批号">
          <Input allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            style={{ width: 130 }}
            placeholder="全部"
            options={BATCH_STATUS.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                searchForm.resetFields();
                setQuery({ pageNo: 1, pageSize: 10 });
              }}
            >
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<BatchRow>
        rowKey={(r) => String(r.batch.id)}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        loading={pageQuery.isLoading}
        scroll={{ x: 1300 }}
        pagination={{
          current: Number(pageQuery.data?.pageNo || 1),
          pageSize: Number(pageQuery.data?.pageSize || 10),
          total: Number(pageQuery.data?.total || 0),
          showSizeChanger: true,
          onChange: (pageNo, pageSize) => setQuery((q) => ({ ...q, pageNo, pageSize })),
        }}
      />
    </Card>
  );
};

export default BatchList;
