import React, { useMemo, useState } from 'react';
import { Button, Card, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import AuthButton from '@/components/AuthButton';
import { productApi } from '@/api/product';
import { samplingApi, SamplingQuery } from '@/api/sampling';
import {
  SAMPLING_SOURCES,
  SAMPLING_STATUS,
  SAMPLE_STATUS,
  SAMPLE_TYPES,
} from '@/constants/qms';
import type { SamplingUpsert, SamplingVO, SampleBrief } from '@/types/inspection';
import type { ProductDetail } from '@/types/master';
import type { LongId } from '@/types/auth';
import ReceiveModal from '@/pages/sample/ReceiveModal';
import message from '@/utils/feedback';

const sourceMeta = (v: string) => SAMPLING_SOURCES.find((s) => s.value === v);
const statusMeta = (v: string) => SAMPLING_STATUS.find((s) => s.value === v);
const sampleTypeMeta = (v: string) => SAMPLE_TYPES.find((s) => s.value === v);
const sampleStatusMeta = (v: string) => SAMPLE_STATUS.find((s) => s.value === v);

export interface SkuOption {
  value: LongId;
  label: string;
  detail: ProductDetail;
  sku: ProductDetail['skus'][number];
}

/** 拉全部产品+SKU作为下拉项（演示量级可接受；后续可换 SKU 搜索接口） */
export function useSkuOptions() {
  return useQuery<SkuOption[]>({
    queryKey: ['sku-options'],
    staleTime: 60_000,
    queryFn: async () => {
      const page = await productApi.page({ pageNo: 1, pageSize: 100 });
      const details = await Promise.all(page.records.map((r) => productApi.detail(r.id)));
      return details.flatMap((d) =>
        d.skus
          .filter((s) => s.status === 1)
          .map((sku) => ({
            value: sku.id,
            label: `${d.product.productName} ${sku.spec || ''} ${sku.packageForm || ''}（${sku.skuCode}）`,
            detail: d,
            sku,
          })),
      );
    },
  });
}

const SamplingList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<SamplingQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['sampling-page', query], queryFn: () => samplingApi.page(query) });
  const skuOptions = useSkuOptions();
  const skuMap = useMemo(
    () => new Map((skuOptions.data || []).map((o) => [String(o.value), o])),
    [skuOptions.data],
  );

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SamplingVO | null>(null);
  const [form] = Form.useForm<SamplingUpsert>();
  const [detail, setDetail] = useState<SamplingVO | null>(null);
  const [receiveOf, setReceiveOf] = useState<SamplingVO | null>(null);
  const [cancelOf, setCancelOf] = useState<SamplingVO | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['sampling-page'] });
    if (detail) void queryClient.invalidateQueries({ queryKey: ['sampling-detail', String(detail.id)] });
  };

  const saveMutation = useMutation({
    mutationFn: async (v: SamplingUpsert) => {
      const values: SamplingUpsert = {
        ...v,
        productionDate: v.productionDate ? dayjs(v.productionDate).format('YYYY-MM-DD') : undefined,
        expiryDate: v.expiryDate ? dayjs(v.expiryDate).format('YYYY-MM-DD') : undefined,
        sampledAt: v.sampledAt ? dayjs(v.sampledAt).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      };
      if (editing) await samplingApi.update({ ...values, id: editing.id });
      else await samplingApi.create(values);
    },
    onSuccess: () => {
      message.success('抽样单已保存为草稿');
      setModalOpen(false);
      invalidate();
    },
  });

  const submitMutation = useMutation({
    mutationFn: (id: LongId) => samplingApi.submit(id),
    onSuccess: () => {
      message.success('已提交，等待收样登记');
      invalidate();
    },
  });

  const cancelMutation = useMutation({
    mutationFn: ({ id, reason }: { id: LongId; reason: string }) => samplingApi.cancel(id, reason),
    onSuccess: () => {
      message.success('抽样单已撤销');
      setCancelOf(null);
      setCancelReason('');
      invalidate();
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      source: 'INCOMING_BATCH',
      quantityUnit: 'g',
      sampledAt: dayjs(),
    });
    setModalOpen(true);
  };

  const openEdit = (row: SamplingVO) => {
    setEditing(row);
    form.setFieldsValue({
      source: row.source,
      skuId: row.skuId,
      batchNo: row.batchNo,
      productionDate: row.productionDate ? dayjs(row.productionDate) : undefined,
      expiryDate: row.expiryDate ? dayjs(row.expiryDate) : undefined,
      storageCondition: row.storageCondition,
      sampleQuantity: row.sampleQuantity !== undefined ? Number(row.sampleQuantity) : undefined,
      quantityUnit: row.quantityUnit,
      samplingLocation: row.samplingLocation,
      sampledAt: row.sampledAt ? dayjs(row.sampledAt) : dayjs(),
      remark: row.remark,
    });
    setModalOpen(true);
  };

  const openDetail = async (row: SamplingVO) => {
    const full = await samplingApi.detail(row.id);
    setDetail(full);
  };

  const onSkuChange = (skuId: LongId) => {
    const opt = skuMap.get(String(skuId));
    if (opt) {
      form.setFieldValue('storageCondition', opt.detail.product.storageCondition || '');
      form.setFieldValue(
        'sampleQuantity',
        opt.sku.netContent ? Number(opt.sku.netContent) : undefined,
      );
      form.setFieldValue('quantityUnit', opt.sku.netContentUnit || 'g');
    }
  };

  const sampleColumns: ColumnsType<SampleBrief> = [
    { title: '样品编号', dataIndex: 'sampleNo', width: 170 },
    {
      title: '类型',
      dataIndex: 'sampleType',
      width: 90,
      render: (v: string) => {
        const m = sampleTypeMeta(v);
        return <Tag color={m?.color}>{m?.label || v}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const m = sampleStatusMeta(v);
        return <Tag color={m?.color}>{m?.label || v}</Tag>;
      },
    },
    { title: '关联任务', dataIndex: 'taskNo', width: 160, render: (v: string) => v || '-' },
    { title: '留样位置', dataIndex: 'retainLocation', render: (v: string) => v || '-' },
    { title: '留样到期', dataIndex: 'retainUntil', width: 120, render: (v: string) => v || '-' },
  ];

  const columns: ColumnsType<SamplingVO> = [
    { title: '抽样单号', dataIndex: 'samplingNo', width: 160, fixed: 'left' },
    {
      title: '来源',
      dataIndex: 'source',
      width: 110,
      render: (v: string) => {
        const m = sourceMeta(v);
        return <Tag>{m?.label || v}</Tag>;
      },
    },
    { title: '产品', dataIndex: 'productName', width: 200, render: (v: string, r) => `${v || ''} ${r.spec || ''}` },
    { title: '批号', dataIndex: 'batchNo', width: 150 },
    { title: '生产日期', dataIndex: 'productionDate', width: 110 },
    { title: '保质期至', dataIndex: 'expiryDate', width: 110 },
    {
      title: '抽样数量',
      dataIndex: 'sampleQuantity',
      width: 100,
      render: (v: number | string, r) => (v !== undefined && v !== null ? `${v}${r.quantityUnit || ''}` : '-'),
    },
    { title: '抽样人', dataIndex: 'samplerName', width: 100, render: (v: string) => v || '-' },
    { title: '抽样时间', dataIndex: 'sampledAt', width: 160 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      fixed: 'right',
      render: (v: string) => {
        const m = statusMeta(v);
        return <Tag color={m?.color}>{m?.label || v}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openDetail(r)}>详情</Button>
          {r.status === 'DRAFT' && (
            <>
              <AuthButton perm="sampling:update" type="link" size="small" onClick={() => openEdit(r)}>
                编辑
              </AuthButton>
              <AuthButton
                perm="sampling:submit"
                type="link"
                size="small"
                loading={submitMutation.isPending}
                onClick={() => submitMutation.mutate(r.id)}
              >
                提交
              </AuthButton>
            </>
          )}
          {r.status === 'PENDING_RECEIVE' && (
            <AuthButton perm="sample:receive" type="link" size="small" onClick={() => setReceiveOf(r)}>
              收样登记
            </AuthButton>
          )}
          {['DRAFT', 'PENDING_RECEIVE'].includes(r.status) && (
            <Button type="link" size="small" danger onClick={() => setCancelOf(r)}>
              撤销
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="抽样管理"
      extra={
        <AuthButton perm="sampling:create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建抽样单
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(v) => setQuery({ pageNo: 1, pageSize: query.pageSize, ...v })}
      >
        <Form.Item name="samplingNo" label="抽样单号">
          <Input allowClear placeholder="CY单号" style={{ width: 150 }} />
        </Form.Item>
        <Form.Item name="source" label="来源">
          <Select
            allowClear
            placeholder="全部"
            style={{ width: 140 }}
            options={SAMPLING_SOURCES.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select
            allowClear
            placeholder="全部"
            style={{ width: 130 }}
            options={SAMPLING_STATUS.map((s) => ({ value: s.value, label: s.label }))}
          />
        </Form.Item>
        <Form.Item name="batchNo" label="批号">
          <Input allowClear style={{ width: 140 }} />
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

      <Table<SamplingVO>
        rowKey={(r) => String(r.id)}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        loading={pageQuery.isLoading}
        scroll={{ x: 1600 }}
        pagination={{
          current: Number(pageQuery.data?.pageNo || 1),
          pageSize: Number(pageQuery.data?.pageSize || 10),
          total: Number(pageQuery.data?.total || 0),
          showSizeChanger: true,
          onChange: (pageNo, pageSize) => setQuery((q) => ({ ...q, pageNo, pageSize })),
        }}
      />

      {/* 新建/编辑 */}
      <Modal
        title={editing ? `编辑抽样单 ${editing.samplingNo}` : '新建抽样单'}
        open={modalOpen}
        width={640}
        // forceRender：openCreate 中先 reset/setFieldsValue，选品回调也会写入 form
        forceRender
        onCancel={() => setModalOpen(false)}
        onOk={() =>
          form
            .validateFields()
            .then((v) => saveMutation.mutate(v))
            .catch(() => {})
        }
        confirmLoading={saveMutation.isPending}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="source" label="抽样来源" rules={[{ required: true }]}>
            <Select options={SAMPLING_SOURCES.map((s) => ({ value: s.value, label: s.label }))} />
          </Form.Item>
          <Form.Item
            name="skuId"
            label="产品 SKU"
            rules={[{ required: true, message: '请选择产品 SKU' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择产品/SKU"
              loading={skuOptions.isLoading}
              options={skuOptions.data || []}
              onChange={onSkuChange}
            />
          </Form.Item>
          <Form.Item name="batchNo" label="批号（包装喷码）" rules={[{ required: true, message: '请填写批号' }]}>
            <Input placeholder="如 B2026090101" />
          </Form.Item>
          <Space style={{ display: 'flex' }}>
            <Form.Item name="productionDate" label="生产日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="expiryDate" label="保质期至" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }}>
            <Form.Item name="sampleQuantity" label="抽样数量" style={{ flex: 1 }}>
              <InputNumber min={0} precision={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="quantityUnit" label="单位" style={{ width: 100 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="samplingLocation" label="抽样地点">
            <Input placeholder="如：成品仓3号库位" />
          </Form.Item>
          <Form.Item name="storageCondition" label="储存条件">
            <Input placeholder="选择SKU后自动带出，可修改" />
          </Form.Item>
          <Form.Item name="sampledAt" label="抽样时间">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 撤销 */}
      <Modal
        title="撤销抽样单"
        open={!!cancelOf}
        onCancel={() => setCancelOf(null)}
        onOk={() => cancelOf && cancelReason.trim() && cancelMutation.mutate({ id: cancelOf.id, reason: cancelReason.trim() })}
        confirmLoading={cancelMutation.isPending}
        okButtonProps={{ danger: true, disabled: !cancelReason.trim() }}
      >
        <p>确认撤销 <b>{cancelOf?.samplingNo}</b>？撤销后不可恢复。</p>
        <Input.TextArea
          rows={3}
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          placeholder="必须填写撤销原因（留痕）"
        />
      </Modal>

      {/* 收样登记 */}
      <ReceiveModal
        sampling={receiveOf}
        onClose={() => setReceiveOf(null)}
        onSuccess={() => {
          invalidate();
          message.success('收样完成，样品编号与检验任务已生成');
        }}
      />

      {/* 详情 */}
      <Drawer
        title={detail ? `抽样单详情 ${detail.samplingNo}` : ''}
        open={!!detail}
        width={780}
        onClose={() => setDetail(null)}
        extra={
          detail?.status === 'PENDING_RECEIVE' ? (
            <AuthButton perm="sample:receive" type="primary" onClick={() => setReceiveOf(detail)}>
              收样登记
            </AuthButton>
          ) : undefined
        }
      >
        {detail && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="状态">
                <Tag color={statusMeta(detail.status)?.color}>{statusMeta(detail.status)?.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源">{sourceMeta(detail.source)?.label}</Descriptions.Item>
              <Descriptions.Item label="产品" span={2}>
                {detail.productName} {detail.spec}（{detail.brand || '-'}）
              </Descriptions.Item>
              <Descriptions.Item label="批号">{detail.batchNo}</Descriptions.Item>
              <Descriptions.Item label="SKU">{detail.skuCode}</Descriptions.Item>
              <Descriptions.Item label="生产日期">{detail.productionDate}</Descriptions.Item>
              <Descriptions.Item label="保质期至">{detail.expiryDate}</Descriptions.Item>
              <Descriptions.Item label="抽样数量">
                {detail.sampleQuantity}
                {detail.quantityUnit}
              </Descriptions.Item>
              <Descriptions.Item label="效期差(天)">{detail.expiryDiffDays ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="抽样地点" span={2}>
                {detail.samplingLocation || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="储存条件" span={2}>
                {detail.storageCondition || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="抽样人">{detail.samplerName || '-'}</Descriptions.Item>
              <Descriptions.Item label="抽样时间">{detail.sampledAt}</Descriptions.Item>
              {detail.cancelReason && (
                <Descriptions.Item label="撤销原因" span={2}>
                  {detail.cancelReason}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="备注" span={2}>
                {detail.remark || '-'}
              </Descriptions.Item>
            </Descriptions>
            <h4 style={{ marginTop: 16 }}>收样生成的样品（{detail.samples?.length || 0}）</h4>
            <Table<SampleBrief>
              rowKey={(s) => String(s.id)}
              size="small"
              columns={sampleColumns}
              dataSource={detail.samples || []}
              pagination={false}
            />
          </>
        )}
      </Drawer>
    </Card>
  );
};

export default SamplingList;
