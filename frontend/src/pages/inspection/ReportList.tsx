import React, { useState } from 'react';
import { Button, Card, Descriptions, Drawer, Form, Input, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { reportApi, ReportQuery } from '@/api/inspection';
import request from '@/api/request';
import { REPORT_CONCLUSIONS, REPORT_STATUS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { ReportVO } from '@/types/inspection';
import message from '@/utils/feedback';

const conclusionMeta = (v: string) => REPORT_CONCLUSIONS.find((c) => c.value === v);
const statusMeta = (v: string) => REPORT_STATUS.find((s) => s.value === v);

async function openPdf(id: LongId) {
  const resp = await request.get(`/inspection/reports/${id}/pdf`, { responseType: 'blob' });
  const url = window.URL.createObjectURL(
    new Blob([resp as unknown as BlobPart], { type: 'application/pdf' }),
  );
  window.open(url, '_blank');
  setTimeout(() => window.URL.revokeObjectURL(url), 60_000);
}

const ReportList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<ReportQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['report-page', query], queryFn: () => reportApi.page(query) });
  const [detail, setDetail] = useState<ReportVO | null>(null);

  const issueMutation = useMutation({
    mutationFn: (id: LongId) => reportApi.issue(id),
    onSuccess: () => {
      message.success('报告已签发，PDF 已固化归档');
      void queryClient.invalidateQueries({ queryKey: ['report-page'] });
      setDetail(null);
    },
  });

  const columns: ColumnsType<ReportVO> = [
    {
      title: '报告编号',
      dataIndex: ['report', 'reportNo'],
      width: 170,
      fixed: 'left',
    },
    { title: '任务单号', dataIndex: 'taskNo', width: 160 },
    {
      title: '产品',
      dataIndex: 'productName',
      width: 200,
      render: (v: string, r) => `${v || ''} ${r.spec || ''}`,
    },
    { title: '批号', dataIndex: 'batchNo', width: 140 },
    { title: '检验员', dataIndex: 'inspectorName', width: 90, render: (v: string) => v || '-' },
    { title: '复核人', dataIndex: 'reviewerName', width: 90, render: (v: string) => v || '-' },
    {
      title: '结论',
      dataIndex: ['report', 'conclusion'],
      width: 100,
      render: (v: string) => <Tag color={conclusionMeta(v)?.color}>{conclusionMeta(v)?.label || v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: ['report', 'status'],
      width: 90,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    { title: '签发时间', dataIndex: ['report', 'issuedAt'], width: 160, render: (v?: string) => v?.replace('T', ' ').slice(0, 16) || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 210,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => setDetail(r)}>预览</Button>
          <AuthButton
            perm="inspection:report:export"
            type="link"
            size="small"
            onClick={() => openPdf(r.report.id)}
          >
            导出PDF
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card title="质检报告">
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="reportNo" label="报告编号">
          <Input allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="conclusion" label="结论">
          <Select
            allowClear
            style={{ width: 130 }}
            placeholder="全部"
            options={REPORT_CONCLUSIONS.map((c) => ({ value: c.value, label: c.label }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            style={{ width: 120 }}
            placeholder="全部"
            options={REPORT_STATUS.map((s) => ({ value: s.value, label: s.label }))}
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

      <Table<ReportVO>
        rowKey={(r) => String(r.report.id)}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        loading={pageQuery.isLoading}
        scroll={{ x: 1400 }}
        pagination={{
          current: Number(pageQuery.data?.pageNo || 1),
          pageSize: Number(pageQuery.data?.pageSize || 10),
          total: Number(pageQuery.data?.total || 0),
          showSizeChanger: true,
          onChange: (pageNo, pageSize) => setQuery((q) => ({ ...q, pageNo, pageSize })),
        }}
      />

      <Drawer
        title={detail ? `报告 ${detail.report.reportNo}` : ''}
        open={!!detail}
        width={620}
        onClose={() => setDetail(null)}
        extra={
          detail && (
            <Space>
              <AuthButton perm="inspection:report:export" onClick={() => openPdf(detail.report.id)}>
                导出PDF
              </AuthButton>
              {detail.report.status === 'SIGNED' && (
                <AuthButton
                  perm="inspection:report:export"
                  type="primary"
                  loading={issueMutation.isPending}
                  onClick={() => issueMutation.mutate(detail.report.id)}
                >
                  签发（固化PDF）
                </AuthButton>
              )}
              <Button onClick={() => navigate(`/inspection/tasks/${detail.report.taskId}`)}>
                查看任务
              </Button>
            </Space>
          )
        }
      >
        {detail && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="结论" span={2}>
              <Tag color={conclusionMeta(detail.report.conclusion)?.color}>
                {conclusionMeta(detail.report.conclusion)?.label}
              </Tag>
              <Tag color={statusMeta(detail.report.status)?.color}>
                {statusMeta(detail.report.status)?.label}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="样品">{detail.sampleNo}</Descriptions.Item>
            <Descriptions.Item label="任务单">{detail.taskNo}</Descriptions.Item>
            <Descriptions.Item label="产品" span={2}>
              {detail.productName} {detail.spec}
            </Descriptions.Item>
            <Descriptions.Item label="批号">{detail.batchNo}</Descriptions.Item>
            <Descriptions.Item label="标准模板">
              {detail.templateName} v{detail.templateVersion}
            </Descriptions.Item>
            <Descriptions.Item label="检验员">{detail.inspectorName}</Descriptions.Item>
            <Descriptions.Item label="复核人">{detail.reviewerName}</Descriptions.Item>
            <Descriptions.Item label="A类不合格">{detail.report.aFailCount}</Descriptions.Item>
            <Descriptions.Item label="B类不合格">{detail.report.bFailCount}</Descriptions.Item>
            <Descriptions.Item label="C类不合格" span={2}>
              {detail.report.cFailCount}
            </Descriptions.Item>
            <Descriptions.Item label="检验签名" span={2}>
              <code style={{ fontSize: 12 }}>{detail.report.inspectorSignHash}</code>
              <div>{detail.report.inspectorSignedAt?.replace('T', ' ').slice(0, 19)}</div>
            </Descriptions.Item>
            <Descriptions.Item label="复核签名" span={2}>
              <code style={{ fontSize: 12 }}>{detail.report.reviewerSignHash}</code>
              <div>{detail.report.reviewerSignedAt?.replace('T', ' ').slice(0, 19)}</div>
            </Descriptions.Item>
            {detail.report.pdfAttachmentId && (
              <Descriptions.Item label="PDF附件ID" span={2}>
                {String(detail.report.pdfAttachmentId)}
              </Descriptions.Item>
            )}
            <Descriptions.Item label="签发时间" span={2}>
              {detail.report.issuedAt?.replace('T', ' ').slice(0, 19) || '未签发'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </Card>
  );
};

export default ReportList;
