import React, { useState } from 'react';
import { Button, Card, DatePicker, Descriptions, Drawer, Form, Input, Modal, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import AuthButton from '@/components/AuthButton';
import { rectApi, RectQuery } from '@/api/defect';
import { RECT_STATUS } from '@/constants/qms';
import type { RectificationRow } from '@/types/defect';
import message from '@/utils/feedback';

const statusMeta = (v: string) => RECT_STATUS.find((s) => s.value === v);

const RectificationList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<RectQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const [detail, setDetail] = useState<RectificationRow | null>(null);
  const [modal, setModal] = useState<'' | 'reply' | 'verify'>('');
  const [verifyResult, setVerifyResult] = useState<'PASS' | 'FAIL'>('PASS');
  const [form] = Form.useForm();

  const pageQuery = useQuery({ queryKey: ['rect-page', query], queryFn: () => rectApi.page(query) });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['rect-page'] });

  const replyMut = useMutation({
    mutationFn: (v: { correctiveAction: string; planFinishDate?: string; actualFinishDate?: string }) =>
      rectApi.reply(detail!.rect.id, v),
    onSuccess: async () => {
      message.success('整改回复已提交');
      await invalidate();
      setModal('');
      setDetail(null);
    },
  });
  const verifyMut = useMutation({
    mutationFn: (v: { result: 'PASS' | 'FAIL'; password: string; comment?: string }) =>
      rectApi.verify(detail!.rect.id, v),
    onSuccess: async (_d, v) => {
      message.success(v.result === 'PASS' ? '验证通过，复检任务已生成' : '验证不通过，已退回');
      await invalidate();
      setModal('');
      setDetail(null);
    },
  });

  const openModal = (key: typeof modal) => {
    form.resetFields();
    if (key === 'verify') {
      setVerifyResult('PASS');
    }
    setModal(key);
  };

  const columns: ColumnsType<RectificationRow> = [
    {
      title: '整改单号',
      dataIndex: ['rect', 'rectifyNo'],
      width: 170,
      fixed: 'left',
      render: (v: string) => v,
    },
    { title: '供应商', dataIndex: 'supplierName', width: 180 },
    { title: '关联不合格单', dataIndex: 'caseNo', width: 170 },
    { title: '问题描述', dataIndex: ['rect', 'issueDesc'], ellipsis: true },
    { title: '计划完成', dataIndex: ['rect', 'planFinishDate'], width: 110 },
    { title: '实际完成', dataIndex: ['rect', 'actualFinishDate'], width: 110 },
    { title: '复检任务', dataIndex: 'recheckTaskNo', width: 160, render: (v?: string) => v || '-' },
    { title: '验证人', dataIndex: 'verifierName', width: 90, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: ['rect', 'status'],
      width: 120,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setDetail(r)}>
            详情
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/defect/${r.rect.defectCaseId}`)}
          >
            不合格单
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card title="供应商整改">
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="rectifyNo" label="整改单号">
          <Input allowClear style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            style={{ width: 140 }}
            placeholder="全部"
            options={RECT_STATUS.map((s) => ({ value: s.value, label: s.label }))}
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

      <Table<RectificationRow>
        rowKey={(r) => String(r.rect.id)}
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

      <Drawer
        title={detail ? `整改单 ${detail.rect.rectifyNo}` : ''}
        open={!!detail}
        width={560}
        onClose={() => setDetail(null)}
        extra={
          detail && (
            <Space wrap>
              {(detail.rect.status === 'ISSUED' || detail.rect.status === 'FAILED') && (
                <AuthButton perm="rect:save" onClick={() => openModal('reply')}>
                  回复整改
                </AuthButton>
              )}
              {detail.rect.status === 'REPLIED' && (
                <AuthButton perm="rect:verify" type="primary" onClick={() => openModal('verify')}>
                  质量验证
                </AuthButton>
              )}
              {detail.rect.recheckTaskId && (
                <Button onClick={() => navigate(`/inspection/tasks/${detail.rect.recheckTaskId}`)}>
                  查看复检任务
                </Button>
              )}
            </Space>
          )
        }
      >
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="状态">
              <Tag color={statusMeta(detail.rect.status)?.color}>
                {statusMeta(detail.rect.status)?.label || detail.rect.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="供应商">{detail.supplierName || '-'}</Descriptions.Item>
            <Descriptions.Item label="关联不合格单">{detail.caseNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="问题描述">{detail.rect.issueDesc}</Descriptions.Item>
            <Descriptions.Item label="纠正措施">
              {detail.rect.correctiveAction || <span style={{ color: '#999' }}>待回复</span>}
            </Descriptions.Item>
            <Descriptions.Item label="计划完成日期">{detail.rect.planFinishDate || '-'}</Descriptions.Item>
            <Descriptions.Item label="实际完成日期">{detail.rect.actualFinishDate || '-'}</Descriptions.Item>
            <Descriptions.Item label="验证人">{detail.verifierName || '-'}</Descriptions.Item>
            <Descriptions.Item label="验证时间">
              {detail.rect.verifiedAt?.replace('T', ' ').slice(0, 16) || '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>

      <Modal
        title="整改回复"
        open={modal === 'reply'}
        onCancel={() => setModal('')}
        confirmLoading={replyMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            replyMut.mutate({
              correctiveAction: v.correctiveAction.trim(),
              planFinishDate: v.planFinishDate ? v.planFinishDate.format('YYYY-MM-DD') : undefined,
              actualFinishDate: v.actualFinishDate ? v.actualFinishDate.format('YYYY-MM-DD') : undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="correctiveAction" label="纠正/预防措施" rules={[{ required: true, message: '请填写纠正措施' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Space size={12}>
            <Form.Item name="planFinishDate" label="计划完成日期">
              <DatePicker disabledDate={(d0) => d0.isBefore(dayjs().startOf('day'))} />
            </Form.Item>
            <Form.Item name="actualFinishDate" label="实际完成日期">
              <DatePicker />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="整改验证（通过即生成复检任务）"
        open={modal === 'verify'}
        onCancel={() => setModal('')}
        confirmLoading={verifyMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            verifyMut.mutate({
              result: verifyResult,
              password: v.password,
              comment: v.comment?.trim() || undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item label="验证结论" required>
            <Select
              value={verifyResult}
              style={{ width: 260 }}
              onChange={(v) => setVerifyResult(v)}
              options={[
                { value: 'PASS', label: '通过：发起复检' },
                { value: 'FAIL', label: '不通过：退回处置' },
              ]}
            />
          </Form.Item>
          <Form.Item name="password" label="登录密码" rules={[{ required: true, message: '请输入密码二次认证' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="comment" label="验证意见">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default RectificationList;
