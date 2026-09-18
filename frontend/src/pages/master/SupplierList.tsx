import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, DatePicker, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import * as echarts from 'echarts';
import dayjs, { Dayjs } from 'dayjs';
import AuthButton from '@/components/AuthButton';
import { supplierApi, SupplierQuery } from '@/api/supplier';
import { supplierQualityApi } from '@/api/defect';
import { SUPPLIER_GRADES, SUPPLIER_STATUS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { Supplier, SupplierLicense, SupplierLicenseUpsert } from '@/types/master';
import type { SupplierQualityRow } from '@/types/defect';
import message from '@/utils/feedback';

const statusMeta = (v: string) => SUPPLIER_STATUS.find((s) => s.value === v);

// 直接基于 echarts 封装：echarts-for-react@3.0.2 在 echarts@5.5 下临时实例等待
// finished 事件后才 setOption，而空 option 不再触发该事件，导致图表永不渲染。
function TrendChart({ option }: { option: echarts.EChartsCoreOption }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, true);
  }, [option]);

  return <div ref={containerRef} style={{ width: '100%', height: 260 }} />;
}

function expiryTag(validTo?: string) {
  if (!validTo) return <Tag>长期</Tag>;
  const days = dayjs(validTo).diff(dayjs().startOf('day'), 'day');
  if (days < 0) return <Tag color="red">已过期 {Math.abs(days)} 天</Tag>;
  if (days <= 60) return <Tag color="orange">{days} 天后到期</Tag>;
  return <Tag color="green">{days} 天后到期</Tag>;
}

interface LicenseFormValues extends Omit<SupplierLicenseUpsert, 'validFrom' | 'validTo'> {
  range?: [Dayjs, Dayjs];
  validTo?: Dayjs;
}

const SupplierList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<SupplierQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [form] = Form.useForm<Supplier>();
  const [modalOpen, setModalOpen] = useState(false);
  const [drawerId, setDrawerId] = useState<LongId | null>(null);

  const pageQuery = useQuery({
    queryKey: ['suppliers', query],
    queryFn: () => supplierApi.page(query),
  });

  const detailQuery = useQuery({
    queryKey: ['supplier-detail', drawerId],
    queryFn: () => supplierApi.detail(drawerId as LongId),
    enabled: drawerId !== null,
  });

  const invalidateAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    void queryClient.invalidateQueries({ queryKey: ['supplier-detail'] });
  };

  const saveMutation = useMutation({
    mutationFn: async (values: Supplier) => {
      if (editing) {
        await supplierApi.update({ ...values, id: editing.id });
      } else {
        await supplierApi.create(values);
      }
    },
    onSuccess: () => {
      message.success(editing ? '更新成功' : '新增成功');
      setModalOpen(false);
      invalidateAll();
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'QUALIFIED' } as never);
    setModalOpen(true);
  };

  const openEdit = (record: Supplier) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const columns: ColumnsType<Supplier> = [
    { title: '供应商编码', dataIndex: 'supplierCode', width: 140 },
    { title: '供应商名称', dataIndex: 'supplierName' },
    { title: '联系人', dataIndex: 'contact', width: 100 },
    { title: '电话', dataIndex: 'phone', width: 150 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const meta = statusMeta(v);
        return <Tag color={meta?.color}>{meta?.label || v}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => setDrawerId(record.id)}>
            证照/详情
          </Button>
          <AuthButton type="link" size="small" perm="master:supplier:edit" onClick={() => openEdit(record)}>
            编辑
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="供应商管理"
      extra={
        <AuthButton type="primary" perm="master:supplier:create" onClick={openCreate}>
          新增供应商
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="supplierName" label="名称">
          <Input placeholder="供应商名称" allowClear />
        </Form.Item>
        <Form.Item name="supplierCode" label="编码">
          <Input placeholder="供应商编码" allowClear />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select placeholder="全部" allowClear style={{ width: 120 }} options={SUPPLIER_STATUS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<Supplier>
        rowKey="id"
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        locale={{ emptyText: pageQuery.isError ? '加载失败，请稍后重试' : '暂无供应商' }}
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
        title={editing ? '编辑供应商' : '新增供应商'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        // forceRender：打开处理器（openCreate/openLicense）会先操作 form 实例，需预挂载保持连接
        forceRender
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
          initialValues={{ status: 'QUALIFIED' }}
        >
          <Form.Item
            name="supplierCode"
            label="供应商编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线、短横线' },
            ]}
          >
            <Input maxLength={50} disabled={!!editing} placeholder="如 SUP001（一经使用不可复用）" />
          </Form.Item>
          <Form.Item name="supplierName" label="供应商名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={150} />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="contact" label="联系人" style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="phone" label="电话" style={{ flex: 1 }}>
              <Input maxLength={32} />
            </Form.Item>
          </Space>
          <Form.Item name="address" label="地址">
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select options={SUPPLIER_STATUS} />
          </Form.Item>
        </Form>
      </Modal>

      <SupplierDrawer
        open={drawerId !== null}
        loading={detailQuery.isLoading}
        data={detailQuery.data}
        onClose={() => setDrawerId(null)}
        onChanged={invalidateAll}
      />
    </Card>
  );
};

const SupplierDrawer: React.FC<{
  open: boolean;
  loading: boolean;
  data?: Awaited<ReturnType<typeof supplierApi.detail>>;
  onClose: () => void;
  onChanged: () => void;
}> = ({ open, loading, data, onClose, onChanged }) => {
  const [licenseEditing, setLicenseEditing] = useState<SupplierLicense | null>(null);
  const [licenseModalOpen, setLicenseModalOpen] = useState(false);
  const [licenseForm] = Form.useForm<LicenseFormValues>();
  const supplierId = data?.supplier.id;

  const saveLicense = useMutation({
    mutationFn: async (values: LicenseFormValues) => {
      if (!supplierId) return;
      const payload: SupplierLicenseUpsert = {
        id: licenseEditing?.id,
        licenseType: values.licenseType,
        certNo: values.certNo,
        validFrom: values.range?.[0]?.format('YYYY-MM-DD'),
        validTo: (values.range?.[1] || values.validTo)?.format('YYYY-MM-DD'),
        status: values.status ?? 1,
      };
      if (licenseEditing) {
        await supplierApi.updateLicense(supplierId, licenseEditing.id, payload);
      } else {
        await supplierApi.addLicense(supplierId, payload);
      }
    },
    onSuccess: () => {
      message.success('证照已保存');
      setLicenseModalOpen(false);
      onChanged();
    },
  });

  const deleteLicense = useMutation({
    mutationFn: async (licenseId: LongId) => {
      if (supplierId) await supplierApi.removeLicense(supplierId, licenseId);
    },
    onSuccess: () => {
      message.success('证照已删除（逻辑删除，留痕）');
      onChanged();
    },
  });

  const openLicense = (record?: SupplierLicense) => {
    setLicenseEditing(record || null);
    licenseForm.resetFields();
    if (record) {
      licenseForm.setFieldsValue({
        licenseType: record.licenseType,
        certNo: record.certNo,
        range:
          record.validFrom && record.validTo
            ? [dayjs(record.validFrom), dayjs(record.validTo)]
            : undefined,
        validTo: record.validTo ? dayjs(record.validTo) : undefined,
        status: record.status,
      });
    } else {
      licenseForm.setFieldsValue({ status: 1 });
    }
    setLicenseModalOpen(true);
  };

  const licenseColumns: ColumnsType<SupplierLicense> = [
    { title: '证照类型', dataIndex: 'licenseType', width: 150 },
    { title: '证号', dataIndex: 'certNo' },
    { title: '生效日', dataIndex: 'validFrom', width: 110 },
    {
      title: '到期日/预警',
      dataIndex: 'validTo',
      width: 160,
      render: (v: string) => (
        <Space direction="vertical" size={0}>
          <span>{v || '长期'}</span>
          {expiryTag(v)}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, record) => (
        <Space>
          <AuthButton type="link" size="small" perm="master:supplier:license" onClick={() => openLicense(record)}>
            编辑
          </AuthButton>
          <Popconfirm title="删除该证照？" description="逻辑删除，审计留痕。" onConfirm={() => deleteLicense.mutate(record.id)}>
            <AuthButton type="link" size="small" danger perm="master:supplier:license">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const qualityColumns: ColumnsType<Awaited<ReturnType<typeof supplierQualityApi.trend>>[number]> = [
    { title: '周期(yyyyMM)', dataIndex: 'period', width: 120 },
    { title: '批次数', dataIndex: 'batchCount', width: 90 },
    { title: '合格率%', dataIndex: 'passRate', width: 100 },
    { title: '缺陷数', dataIndex: 'defectCount', width: 90 },
    { title: '评分', dataIndex: 'score', width: 90 },
    {
      title: '等级',
      dataIndex: 'grade',
      width: 80,
      render: (v: string) => <Tag color={SUPPLIER_GRADES[v]?.color}>{SUPPLIER_GRADES[v]?.label || v}</Tag>,
    },
  ];

  const trendQuery = useQuery({
    queryKey: ['supplier-quality-trend', supplierId],
    queryFn: () => supplierQualityApi.trend(supplierId as LongId),
    enabled: !!supplierId,
  });
  const trendRows = trendQuery.data || [];
  const trendOption = {
    tooltip: { trigger: 'axis' as const },
    legend: { data: ['合格率%', '质量评分'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category' as const, data: trendRows.map((r) => r.period) },
    yAxis: { type: 'value' as const, min: 0, max: 100 },
    series: [
      { name: '合格率%', type: 'line' as const, smooth: true, data: trendRows.map((r) => r.passRate) },
      { name: '质量评分', type: 'line' as const, smooth: true, data: trendRows.map((r) => r.score) },
    ],
  };

  return (
    <Drawer
      title={data ? `${data.supplier.supplierName}（${data.supplier.supplierCode}）` : '供应商详情'}
      width={860}
      open={open}
      onClose={onClose}
      loading={loading}
      destroyOnHidden
    >
      {data && (
        <>
          <Card size="small" style={{ marginBottom: 16 }}>
            <p>联系人：{data.supplier.contact || '-'}　电话：{data.supplier.phone || '-'}</p>
            <p>地址：{data.supplier.address || '-'}</p>
          </Card>
          <Card
            size="small"
            title="证照资质"
            style={{ marginBottom: 16 }}
            extra={
              <AuthButton type="primary" size="small" perm="master:supplier:license" onClick={() => openLicense()}>
                新增证照
              </AuthButton>
            }
          >
            <Table<SupplierLicense>
              rowKey="id"
              size="small"
              pagination={false}
              columns={licenseColumns}
              dataSource={data.licenses}
              locale={{ emptyText: '暂无证照' }}
            />
          </Card>
          <Card size="small" title="质量评级趋势（合格率 / 评分，阶段4闭环后自动沉淀）">
            {trendRows.length > 0 ? (
              <>
                <TrendChart option={trendOption} />
                <Table size="small" rowKey="id" pagination={false} columns={qualityColumns} dataSource={trendRows} />
              </>
            ) : (
              <Table
                size="small"
                rowKey="id"
                pagination={false}
                columns={qualityColumns}
                dataSource={data.qualityRatings as unknown as SupplierQualityRow[]}
                locale={{ emptyText: '暂无评级数据（检验任务闭环后自动沉淀）' }}
              />
            )}
          </Card>
        </>
      )}

      <Modal
        title={licenseEditing ? '编辑证照' : '新增证照'}
        open={licenseModalOpen}
        onCancel={() => setLicenseModalOpen(false)}
        onOk={() => licenseForm.submit()}
        confirmLoading={saveLicense.isPending}
        // forceRender：打开处理器（openCreate/openLicense）会先操作 form 实例，需预挂载保持连接
        forceRender
      >
        <Form
          form={licenseForm}
          layout="vertical"
          onFinish={(values) => saveLicense.mutate(values)}
          initialValues={{ status: 1 }}
        >
          <Form.Item name="licenseType" label="证照类型" rules={[{ required: true, message: '请输入证照类型' }]}>
            <Input placeholder="营业执照 / 药品生产许可证 / 食品生产许可证 / 食品经营许可证" maxLength={50} />
          </Form.Item>
          <Form.Item name="certNo" label="证号">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="range" label="有效期（长期有效则不填）">
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 1, label: '有效' },
                { value: 0, label: '失效' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
};

export default SupplierList;
