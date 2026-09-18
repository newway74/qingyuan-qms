import React, { useState } from 'react';
import { Button, Card, DatePicker, Descriptions, Drawer, Form, Input, Modal, Select, Space, Table, Tag } from 'antd';
import { PrinterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import AuthButton from '@/components/AuthButton';
import AuthImage from '@/components/AuthImage';
import { sampleApi, SampleQuery } from '@/api/sample';
import { DISPOSE_TYPES, SAMPLE_STATUS, SAMPLE_TYPES, TASK_STATUS } from '@/constants/qms';
import type { SampleVO } from '@/types/inspection';
import type { LongId } from '@/types/auth';
import message from '@/utils/feedback';

const typeMeta = (v: string) => SAMPLE_TYPES.find((s) => s.value === v);
const statusMeta = (v: string) => SAMPLE_STATUS.find((s) => s.value === v);
const taskStatusMeta = (v?: string) => TASK_STATUS.find((s) => s.value === v);

const SampleList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<SampleQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const [scanCode, setScanCode] = useState('');
  const pageQuery = useQuery({ queryKey: ['sample-page', query], queryFn: () => sampleApi.page(query) });

  const [detail, setDetail] = useState<SampleVO | null>(null);
  const [retainOf, setRetainOf] = useState<SampleVO | null>(null);
  const [disposeOf, setDisposeOf] = useState<SampleVO | null>(null);
  const [retainForm] = Form.useForm();
  const [disposeForm] = Form.useForm();

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['sample-page'] });
  };

  const doScan = async () => {
    const code = scanCode.trim();
    if (!code) return;
    try {
      const s = await sampleApi.barcode(code);
      setDetail(s);
      message.success(`已定位样品：${s.sampleNo}`);
    } catch {
      /* 错误已由拦截器提示 */
    }
  };

  const openDetail = async (id: LongId) => setDetail(await sampleApi.detail(id));

  const retainMutation = useMutation({
    mutationFn: async () => {
      const v = await retainForm.validateFields();
      if (retainOf) {
        await sampleApi.retain(
          retainOf.id,
          v.retainLocation,
          v.retainUntil.format('YYYY-MM-DD'),
        );
      }
    },
    onSuccess: () => {
      message.success('留样登记成功');
      setRetainOf(null);
      invalidate();
      if (detail) setDetail(null);
    },
  });

  const disposeMutation = useMutation({
    mutationFn: async () => {
      const v = await disposeForm.validateFields();
      if (disposeOf) await sampleApi.dispose(disposeOf.id, v.disposeType, v.disposeRemark);
    },
    onSuccess: () => {
      message.success('已处置并留痕');
      setDisposeOf(null);
      invalidate();
      setDetail(null);
    },
  });

  const columns: ColumnsType<SampleVO> = [
    { title: '样品编号', dataIndex: 'sampleNo', width: 170, fixed: 'left' },
    {
      title: '类型',
      dataIndex: 'sampleType',
      width: 90,
      render: (v: string) => <Tag color={typeMeta(v)?.color}>{typeMeta(v)?.label || v}</Tag>,
    },
    { title: '产品', dataIndex: 'productName', width: 200, render: (v: string, r) => `${v || ''} ${r.spec || ''}` },
    { title: '批号', dataIndex: 'batchNo', width: 150 },
    { title: '生产日期', dataIndex: 'productionDate', width: 110 },
    { title: '保质期至', dataIndex: 'expiryDate', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={statusMeta(v)?.color}>{statusMeta(v)?.label || v}</Tag>,
    },
    {
      title: '检验任务',
      dataIndex: 'taskNo',
      width: 160,
      render: (v: string, r) =>
        v ? (
          <Space size={4}>
            <span>{v}</span>
            {r.taskStatus && <Tag color={taskStatusMeta(r.taskStatus)?.color}>{taskStatusMeta(r.taskStatus)?.label}</Tag>}
          </Space>
        ) : (
          '-'
        ),
    },
    { title: '留样位置', dataIndex: 'retainLocation', render: (v: string) => v || '-' },
    { title: '留样到期', dataIndex: 'retainUntil', width: 110, render: (v: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openDetail(r.id)}>详情</Button>
          {['PENDING_RECEIVE', 'IN_INSPECTION'].includes(r.status) && (
            <AuthButton perm="sample:retain" type="link" size="small" onClick={() => setRetainOf(r)}>
              留样
            </AuthButton>
          )}
          {['PENDING_RECEIVE', 'IN_INSPECTION', 'RETAINING'].includes(r.status) && (
            <AuthButton perm="sample:dispose" type="link" size="small" danger onClick={() => setDisposeOf(r)}>
              处置
            </AuthButton>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card title="样品管理">
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          prefix={<SearchOutlined />}
          placeholder="扫描/输入样品编号条码"
          value={scanCode}
          style={{ width: 280 }}
          onChange={(e) => setScanCode(e.target.value)}
          onPressEnter={doScan}
          allowClear
        />
        <Button type="primary" onClick={doScan}>扫码定位</Button>
      </Space>
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="sampleNo" label="样品编号">
          <Input allowClear style={{ width: 150 }} />
        </Form.Item>
        <Form.Item name="sampleType" label="类型">
          <Select
            allowClear
            style={{ width: 120 }}
            placeholder="全部"
            options={SAMPLE_TYPES.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            style={{ width: 120 }}
            placeholder="全部"
            options={SAMPLE_STATUS.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item name="batchNo" label="批号">
          <Input allowClear style={{ width: 130 }} />
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

      <Table<SampleVO>
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

      <Drawer
        title={detail ? `样品详情 ${detail.sampleNo}` : ''}
        open={!!detail}
        width={640}
        onClose={() => setDetail(null)}
        extra={
          detail ? (
            <Space>
              {['PENDING_RECEIVE', 'IN_INSPECTION'].includes(detail.status) && (
                <AuthButton perm="sample:retain" onClick={() => setRetainOf(detail)}>留样登记</AuthButton>
              )}
              {['PENDING_RECEIVE', 'IN_INSPECTION', 'RETAINING'].includes(detail.status) && (
                <AuthButton perm="sample:dispose" danger onClick={() => setDisposeOf(detail)}>处置</AuthButton>
              )}
            </Space>
          ) : null
        }
      >
        {detail && (
          <>
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              <AuthImage src={sampleApi.barcodeImageUrl(detail.id)} style={{ maxWidth: '100%' }} />
              <div style={{ marginTop: 8 }}>
                <Button icon={<PrinterOutlined />} onClick={() => window.print()}>
                  打印条码
                </Button>
              </div>
            </div>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="类型">
                <Tag color={typeMeta(detail.sampleType)?.color}>{typeMeta(detail.sampleType)?.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusMeta(detail.status)?.color}>{statusMeta(detail.status)?.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="抽样单" span={2}>
                {detail.samplingNo}
              </Descriptions.Item>
              <Descriptions.Item label="产品" span={2}>
                {detail.productName} {detail.spec}
              </Descriptions.Item>
              <Descriptions.Item label="批号">{detail.batchNo}</Descriptions.Item>
              <Descriptions.Item label="SKU">{detail.skuCode}</Descriptions.Item>
              <Descriptions.Item label="生产日期">{detail.productionDate}</Descriptions.Item>
              <Descriptions.Item label="保质期至">{detail.expiryDate}</Descriptions.Item>
              <Descriptions.Item label="喷码核对" span={2}>
                {detail.packageBatchCheck === 'MISMATCH' ? (
                  <Tag color="red">不一致：{detail.packageBatchNote}</Tag>
                ) : (
                  <Tag color="green">一致</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="收样人">{detail.receiverName || '-'}</Descriptions.Item>
              <Descriptions.Item label="收样时间">{detail.receivedAt}</Descriptions.Item>
              <Descriptions.Item label="留样位置">{detail.retainLocation || '-'}</Descriptions.Item>
              <Descriptions.Item label="留样到期">{detail.retainUntil || '-'}</Descriptions.Item>
              {detail.disposeType && (
                <>
                  <Descriptions.Item label="处置方式">
                    {DISPOSE_TYPES.find((d) => d.value === detail.disposeType)?.label}
                  </Descriptions.Item>
                  <Descriptions.Item label="处置时间">{detail.disposedAt}</Descriptions.Item>
                  <Descriptions.Item label="处置备注" span={2}>
                    {detail.disposeRemark}
                  </Descriptions.Item>
                </>
              )}
            </Descriptions>
          </>
        )}
      </Drawer>

      <Modal
        title="留样登记"
        open={!!retainOf}
        onCancel={() => setRetainOf(null)}
        onOk={() => retainMutation.mutate()}
        confirmLoading={retainMutation.isPending}
        destroyOnHidden
      >
        <Form form={retainForm} layout="vertical">
          <Form.Item
            name="retainLocation"
            label="留样位置"
            rules={[{ required: true, message: '请填写留样位置' }]}
          >
            <Input placeholder="如：留样柜A-02" />
          </Form.Item>
          <Form.Item
            name="retainUntil"
            label="留样到期日"
            rules={[{ required: true, message: '请选择留样到期日' }]}
            initialValue={retainOf?.retainUntil ? dayjs(retainOf.retainUntil) : undefined}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="样品处置"
        open={!!disposeOf}
        onCancel={() => setDisposeOf(null)}
        onOk={() => disposeMutation.mutate()}
        confirmLoading={disposeMutation.isPending}
        destroyOnHidden
        okButtonProps={{ danger: true }}
      >
        <Form form={disposeForm} layout="vertical">
          <Form.Item name="disposeType" label="处置方式" rules={[{ required: true }]}>
            <Select options={DISPOSE_TYPES.map((d) => ({ value: d.value, label: d.label }))} />
          </Form.Item>
          <Form.Item
            name="disposeRemark"
            label="处置说明"
            rules={[{ required: true, message: '处置必须填写说明（留痕）' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default SampleList;
