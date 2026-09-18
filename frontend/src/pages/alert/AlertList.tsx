import React, { useState } from 'react';
import { Button, Card, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { alertApi, type AlertRow, type ScanStat } from '@/api/alert';
import AuthButton from '@/components/AuthButton';
import message from '@/utils/feedback';

const TYPE_OPTIONS = [
  { value: 'SLA_WARNING', label: 'SLA临期' },
  { value: 'SLA_OVERDUE', label: 'SLA超期' },
  { value: 'RETAIN_EXPIRE', label: '留样到期' },
  { value: 'LICENSE_EXPIRE', label: '证照到期' },
  { value: 'NEAR_EXPIRY', label: '批次近效期' },
  { value: 'BATCH_EXPIRED', label: '批次过期' },
];

const TYPE_META: Record<string, { label: string; color: string }> = Object.fromEntries(
  TYPE_OPTIONS.map((t) => [t.value, { label: t.label, color: t.value.includes('OVERDUE') || t.value.includes('EXPIRED') ? 'red' : 'orange' }]),
);

const AlertList: React.FC = () => {
  const queryClient = useQueryClient();
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [alertType, setAlertType] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>(0);

  const query = useQuery({
    queryKey: ['alerts', pageNo, pageSize, alertType, status],
    queryFn: () => alertApi.page({ pageNo, pageSize, alertType, status }),
  });

  const handleMut = useMutation({
    mutationFn: (id: number) => alertApi.handle(id),
    onSuccess: () => {
      message.success('预警已处理');
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['workbench-summary'] });
    },
  });

  const scanMut = useMutation({
    mutationFn: () => alertApi.scan(),
    onSuccess: (data) => {
      const lines = Object.entries(data as Record<string, ScanStat>)
        .map(([t, s]) => `${t}: 新增${s.raised}/解除${s.resolved}`)
        .join('；');
      message.success(`扫描完成：${lines}`);
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['workbench-summary'] });
    },
  });

  const columns: ColumnsType<AlertRow> = [
    {
      title: '类型',
      dataIndex: 'alertType',
      width: 120,
      render: (v: string) => <Tag color={TYPE_META[v]?.color}>{TYPE_META[v]?.label || v}</Tag>,
    },
    { title: '预警内容', dataIndex: 'message' },
    {
      title: '级别',
      dataIndex: 'level',
      width: 90,
      render: (v: number) =>
        v === 3 ? <Tag color="red">严重</Tag> : v === 2 ? <Tag color="orange">警告</Tag> : <Tag>提示</Tag>,
    },
    { title: '责任角色', dataIndex: 'targetRole', width: 120, render: (v: string) => v || '-' },
    {
      title: '触发时间',
      dataIndex: 'triggeredAt',
      width: 160,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: number, row) =>
        v === 0 ? <Tag color="processing">未处理</Tag> : (
          <span>
            <Tag color="default">已处理</Tag>
            {row.handledAt && <span style={{ fontSize: 12, color: '#999' }}>{dayjs(row.handledAt).format('MM-DD HH:mm')}</span>}
          </span>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, row) =>
        row.status === 0 ? (
          <AuthButton
            perm="alert:handle"
            type="link"
            size="small"
            danger
            loading={handleMut.isPending}
            onClick={() => handleMut.mutate(row.id)}
          >
            处理
          </AuthButton>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Card
      title="质量预警"
      extra={
        <Space wrap>
          <Select
            allowClear
            placeholder="预警类型"
            style={{ width: 140 }}
            options={TYPE_OPTIONS}
            value={alertType}
            onChange={(v) => { setAlertType(v); setPageNo(1); }}
          />
          <Select
            placeholder="状态"
            style={{ width: 110 }}
            options={[
              { value: 0, label: '未处理' },
              { value: 1, label: '已处理' },
            ]}
            allowClear
            value={status}
            onChange={(v) => { setStatus(v); setPageNo(1); }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => query.refetch()}>
            刷新
          </Button>
          <Popconfirm
            title="立即执行四类预警扫描？"
            onConfirm={() => scanMut.mutate()}
          >
            <AuthButton type="primary" icon={<ThunderboltOutlined />} perm="alert:scan" loading={scanMut.isPending}>
              手动扫描
            </AuthButton>
          </Popconfirm>
        </Space>
      }
    >
      <Table<AlertRow>
        rowKey="id"
        size="small"
        loading={query.isLoading}
        columns={columns}
        dataSource={query.data?.records || []}
        onChange={(p) => {
          setPageNo(p.current || 1);
          setPageSize(p.pageSize || 10);
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total: Number(query.data?.total || 0),
          showSizeChanger: true,
        }}
      />
    </Card>
  );
};

export default AlertList;
