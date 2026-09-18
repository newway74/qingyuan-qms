import React, { useState } from 'react';
import { Button, Card, Form, Input, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import { caseApi, CaseQuery } from '@/api/defect';
import {
  DEFECT_CASE_STATUS,
  DEFECT_LEVELS,
  DISPOSITIONS,
  REPORT_CONCLUSIONS,
} from '@/constants/qms';
import type { CaseListRow } from '@/types/defect';

const statusMeta = (v: string) => DEFECT_CASE_STATUS.find((s) => s.value === v);
const severityMeta = (v: string) => DEFECT_LEVELS.find((s) => s.value === v);
const dispositionMeta = (v?: string) => DISPOSITIONS.find((d) => d.value === v);
const conclusionMeta = (v?: string) => REPORT_CONCLUSIONS.find((c) => c.value === v);

const CaseList: React.FC = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState<CaseQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['defect-page', query], queryFn: () => caseApi.page(query) });

  const columns: ColumnsType<CaseListRow> = [
    {
      title: '不合格单号',
      dataIndex: ['caseObj', 'caseNo'],
      width: 170,
      fixed: 'left',
      render: (v: string, r) => <a onClick={() => navigate(`/defect/${r.caseObj.id}`)}>{v}</a>,
    },
    { title: '来源报告', dataIndex: 'reportNo', width: 160 },
    { title: '产品', dataIndex: 'productName', width: 200, render: (v: string, r) => `${v || ''} ${r.spec || ''}` },
    { title: '批号', dataIndex: 'batchNo', width: 130 },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    {
      title: '最高等级',
      dataIndex: ['caseObj', 'maxSeverity'],
      width: 90,
      align: 'center',
      render: (v: string) => <Tag color={severityMeta(v)?.color}>{severityMeta(v)?.label || v}</Tag>,
    },
    { title: 'A/B/C', width: 90, align: 'center', render: (_, r) => `${r.aFailCount}/${r.bFailCount}/${r.cFailCount}` },
    {
      title: '报告结论',
      dataIndex: 'conclusion',
      width: 100,
      render: (v?: string) => (v ? <Tag color={conclusionMeta(v)?.color}>{conclusionMeta(v)?.label || v}</Tag> : '-'),
    },
    {
      title: '处置方式',
      dataIndex: ['caseObj', 'disposition'],
      width: 110,
      render: (v?: string) => (v ? dispositionMeta(v)?.label || v : <span style={{ color: '#999' }}>待评审</span>),
    },
    {
      title: '状态',
      dataIndex: ['caseObj', 'status'],
      width: 110,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    { title: '建档时间', dataIndex: ['caseObj', 'createdAt'], width: 160, render: (v?: string) => v?.replace('T', ' ').slice(0, 16) || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <Button type="link" size="small" onClick={() => navigate(`/defect/${r.caseObj.id}`)}>
          处置
        </Button>
      ),
    },
  ];

  return (
    <Card title="不合格处置闭环">
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="caseNo" label="不合格单号">
          <Input allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="maxSeverity" label="缺陷等级">
          <Select
            allowClear
            style={{ width: 110 }}
            placeholder="全部"
            options={DEFECT_LEVELS.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="全部"
            options={DEFECT_CASE_STATUS.map((s) => ({ value: s.value, label: s.label }))}
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

      <Table<CaseListRow>
        rowKey={(r) => String(r.caseObj.id)}
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
    </Card>
  );
};

export default CaseList;
