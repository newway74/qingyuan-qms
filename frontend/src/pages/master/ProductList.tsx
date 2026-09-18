import React, { useMemo, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tabs, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import AuthButton from '@/components/AuthButton';
import CategoryTreeSelect from '@/components/CategoryTreeSelect';
import { supplierApi } from '@/api/supplier';
import { productApi, ProductQuery } from '@/api/product';
import { PACKAGE_FORMS, QUALITY_STATUS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type {
  ProductListRow,
  ProductLicense,
  ProductLicenseUpsert,
  ProductSku,
  ProductSkuUpsert,
  ProductUpsert,
} from '@/types/master';
import message from '@/utils/feedback';

const qMeta = (v: string) => QUALITY_STATUS.find((s) => s.value === v);

const ProductList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<ProductQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const [editing, setEditing] = useState<ProductListRow | null>(null);
  const [form] = Form.useForm<ProductUpsert>();
  const [modalOpen, setModalOpen] = useState(false);
  const [drawerId, setDrawerId] = useState<LongId | null>(null);

  const pageQuery = useQuery({ queryKey: ['products', query], queryFn: () => productApi.page(query) });
  const supplierOptions = useQuery({
    queryKey: ['supplier-options'],
    queryFn: () => supplierApi.page({ pageNo: 1, pageSize: 200 }),
  });

  const suppliers = useMemo(
    () => (supplierOptions.data?.records || []).map((s) => ({ value: s.id, label: s.supplierName })),
    [supplierOptions.data],
  );

  const invalidateAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['products'] });
    void queryClient.invalidateQueries({ queryKey: ['product-detail'] });
  };

  const saveMutation = useMutation({
    mutationFn: async (values: ProductUpsert) => {
      if (editing) await productApi.update({ ...values, id: editing.id });
      else await productApi.create(values);
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
    form.setFieldsValue({ extInspectionRequired: 0 });
    setModalOpen(true);
  };

  const openEdit = (r: ProductListRow) => {
    setEditing(r);
    form.setFieldsValue({
      id: r.id,
      spuCode: r.spuCode,
      productName: r.productName,
      categoryId: r.categoryId,
      brand: r.brand,
      supplierId: r.supplierId,
      executionStandard: r.executionStandard,
      storageCondition: r.storageCondition,
      shelfLifeDays: r.shelfLifeDays,
      extInspectionRequired: r.extInspectionRequired,
    });
    setModalOpen(true);
  };

  const columns: ColumnsType<ProductListRow> = [
    { title: 'SPU编码', dataIndex: 'spuCode', width: 130 },
    { title: '品名', dataIndex: 'productName' },
    { title: '品类', dataIndex: 'categoryName', width: 110 },
    { title: '品牌', dataIndex: 'brand', width: 110 },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    { title: '规格形态', width: 130, render: (_, r) => `${r.firstNetContent || '-'}${r.firstNetContentUnit || ''} ${r.firstPackageForm || ''}` },
    { title: 'SKU数', dataIndex: 'skuCount', width: 70 },
    {
      title: '需外检',
      dataIndex: 'extInspectionRequired',
      width: 80,
      render: (v: number) => (v ? <Tag color="purple">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '质量状态',
      dataIndex: 'qualityStatus',
      width: 100,
      render: (v: string) => <Tag color={qMeta(v)?.color}>{qMeta(v)?.label || v}</Tag>,
    },
    {
      title: '操作',
      key: 'ops',
      width: 210,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => setDrawerId(record.id)}>
            SKU/资质
          </Button>
          <AuthButton type="link" size="small" perm="master:product:edit" onClick={() => openEdit(record)}>
            编辑
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="产品档案"
      extra={
        <AuthButton type="primary" perm="master:product:create" onClick={openCreate}>
          新增产品
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16, rowGap: 8 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="productName" label="品名">
          <Input placeholder="品名" allowClear />
        </Form.Item>
        <Form.Item name="spuCode" label="SPU编码">
          <Input placeholder="SPU编码" allowClear />
        </Form.Item>
        <Form.Item name="categoryId" label="品类">
          <CategoryTreeSelect
            allowClear
            placeholder="全部品类"
            style={{ width: 160 }}
          />
        </Form.Item>
        <Form.Item name="supplierId" label="供应商">
          <Select allowClear showSearch optionFilterProp="label" placeholder="全部" style={{ width: 180 }} options={suppliers} />
        </Form.Item>
        <Form.Item name="qualityStatus" label="质量状态">
          <Select allowClear placeholder="全部" style={{ width: 120 }} options={QUALITY_STATUS} />
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

      <Table<ProductListRow>
        rowKey="id"
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        scroll={{ x: 1300 }}
        locale={{ emptyText: pageQuery.isError ? '加载失败，请稍后重试' : '暂无产品' }}
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
        title={editing ? '编辑产品 SPU' : '新增产品 SPU'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        // forceRender：表单弹窗预挂载，打开处理器中的 resetFields/setFieldsValue 始终命中已连接实例
        forceRender
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)} initialValues={{ extInspectionRequired: 0 }}>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="spuCode"
              label="SPU编码"
              style={{ flex: 1 }}
              rules={[
                { required: true, message: '请输入SPU编码' },
                { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线、短横线' },
              ]}
            >
              <Input maxLength={50} disabled={!!editing} placeholder="一经使用不可复用" />
            </Form.Item>
            <Form.Item name="productName" label="品名" style={{ flex: 1 }} rules={[{ required: true, message: '请输入品名' }]}>
              <Input maxLength={150} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="categoryId" label="品类" style={{ flex: 1 }} rules={[{ required: true, message: '请选择品类' }]}>
              <CategoryTreeSelect placeholder="选择品类（没有合适品类可直接在下拉底部新建）" />
            </Form.Item>
            <Form.Item name="brand" label="品牌" style={{ flex: 1 }}>
              <Input maxLength={100} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="supplierId" label="主供应商" style={{ flex: 1 }}>
              <Select showSearch optionFilterProp="label" allowClear options={suppliers} placeholder="选择供应商" />
            </Form.Item>
            <Form.Item name="executionStandard" label="执行标准" style={{ flex: 1 }}>
              <Input placeholder="如《中国药典》2020版一部 / GB/T 5835" maxLength={100} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="storageCondition" label="储存条件" style={{ flex: 1 }}>
              <Input placeholder="置阴凉干燥处，密封保存" maxLength={100} />
            </Form.Item>
            <Form.Item name="shelfLifeDays" label="保质期(天)" style={{ flex: 1 }}>
              <InputNumber min={1} max={99999} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="extInspectionRequired" label="是否需第三方外检" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 0, label: '否' },
                { value: 1, label: '是' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <ProductDrawer
        open={drawerId !== null}
        drawerId={drawerId}
        onClose={() => setDrawerId(null)}
        onChanged={invalidateAll}
      />
    </Card>
  );
};

const ProductDrawer: React.FC<{
  open: boolean;
  drawerId: LongId | null;
  onClose: () => void;
  onChanged: () => void;
}> = ({ open, drawerId, onClose, onChanged }) => {
  const detail = useQuery({
    queryKey: ['product-detail', drawerId],
    queryFn: () => productApi.detail(drawerId as LongId),
    enabled: drawerId !== null,
  });

  const [statusModal, setStatusModal] = useState(false);
  const [statusForm] = Form.useForm<{ qualityStatus: string; reason: string }>();
  const statusMutation = useMutation({
    mutationFn: async (v: { qualityStatus: string; reason: string }) => {
      if (drawerId) {
        await productApi.changeQualityStatus(drawerId, v.qualityStatus, v.reason, detail.data?.product.lockVersion);
      }
    },
    onSuccess: () => {
      message.success('质量状态已变更（原因留痕，审计可查）');
      setStatusModal(false);
      onChanged();
    },
  });

  const openStatus = () => {
    statusForm.resetFields();
    setStatusModal(true);
  };

  return (
    <Drawer
      title={detail.data ? `${detail.data.product.productName}（${detail.data.product.spuCode}）` : '产品详情'}
      width={960}
      open={open}
      onClose={onClose}
      loading={detail.isLoading}
      destroyOnHidden
      extra={
        <AuthButton type="primary" size="small" perm="master:product:status" onClick={openStatus}>
          变更质量状态
        </AuthButton>
      }
    >
      {detail.data && (
        <Tabs
          items={[
            {
              key: 'sku',
              label: `SKU 规格（${detail.data.skus.length}）`,
              children: <SkuPanel productId={detail.data.product.id} rows={detail.data.skus} onChanged={onChanged} />,
            },
            {
              key: 'license',
              label: `产品资质（${detail.data.licenses.length}）`,
              children: (
                <LicensePanel productId={detail.data.product.id} rows={detail.data.licenses} onChanged={onChanged} />
              ),
            },
            {
              key: 'base',
              label: '基础信息',
              children: (
                <Card size="small">
                  <p>
                    品类：{detail.data.categoryName || '-'}　品牌：{detail.data.product.brand || '-'}
                    供应商：{detail.data.supplierName || '-'}
                  </p>
                  <p>执行标准：{detail.data.product.executionStandard || '-'}</p>
                  <p>储存条件：{detail.data.product.storageCondition || '-'}　保质期：{detail.data.product.shelfLifeDays || '-'} 天</p>
                  <p>
                    当前质量状态：
                    <Tag color={qMeta(detail.data.product.qualityStatus)?.color}>
                      {qMeta(detail.data.product.qualityStatus)?.label}
                    </Tag>
                    （乐观锁版本 v{detail.data.product.lockVersion ?? 0}）
                  </p>
                </Card>
              ),
            },
          ]}
        />
      )}

      <Modal
        title="变更质量状态（强制填写原因）"
        open={statusModal}
        onCancel={() => setStatusModal(false)}
        onOk={() => statusForm.submit()}
        confirmLoading={statusMutation.isPending}
        // forceRender：表单弹窗预挂载，打开处理器中的 resetFields/setFieldsValue 始终命中已连接实例
        forceRender
      >
        <Form form={statusForm} layout="vertical" onFinish={(v) => statusMutation.mutate(v)}>
          <Form.Item name="qualityStatus" label="新状态" rules={[{ required: true, message: '请选择状态' }]}>
            <Select
              options={QUALITY_STATUS.filter((s) => s.value !== detail.data?.product.qualityStatus)}
              placeholder="选择目标状态（同状态重复提交会被拒绝）"
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="变更原因"
            rules={[
              { required: true, message: '质量状态变更必须填写原因（合规留痕）' },
              { min: 4, message: '原因不少于 4 个字' },
            ]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount placeholder="如：连续两批二氧化硫超标，暂停采购，启动供应商整改" />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
};

const SkuPanel: React.FC<{ productId: LongId; rows: ProductSku[]; onChanged: () => void }> = ({
  productId,
  rows,
  onChanged,
}) => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProductSku | null>(null);
  const [form] = Form.useForm<ProductSkuUpsert>();

  const mutation = useMutation({
    mutationFn: async (v: ProductSkuUpsert) => {
      if (editing) await productApi.updateSku(productId, editing.id, v);
      else await productApi.addSku(productId, v);
    },
    onSuccess: () => {
      message.success('SKU已保存');
      setModalOpen(false);
      onChanged();
    },
  });

  const removeMutation = useMutation({
    mutationFn: (skuId: LongId) => productApi.removeSku(productId, skuId),
    onSuccess: () => {
      message.success('SKU已停用删除（逻辑删除，留痕）');
      onChanged();
    },
  });

  const open = (r?: ProductSku) => {
    setEditing(r || null);
    form.resetFields();
    form.setFieldsValue(
      r
        ? { ...r, netContent: r.netContent !== undefined && r.netContent !== null ? Number(r.netContent) : undefined }
        : { status: 1 },
    );
    setModalOpen(true);
  };

  const columns: ColumnsType<ProductSku> = [
    { title: 'SKU编码', dataIndex: 'skuCode', width: 140 },
    { title: '规格描述', dataIndex: 'spec' },
    { title: '包装形态', dataIndex: 'packageForm', width: 100 },
    { title: '净含量', width: 120, render: (_, r) => `${r.netContent ?? '-'}${r.netContentUnit || ''}` },
    { title: '条码', dataIndex: 'barcode', width: 150 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: number) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, r) => (
        <Space>
          <AuthButton type="link" size="small" perm="master:product:sku" onClick={() => open(r)}>
            编辑
          </AuthButton>
          <Popconfirm title="删除该SKU？" onConfirm={() => removeMutation.mutate(r.id)}>
            <AuthButton type="link" size="small" danger perm="master:product:sku">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 8, textAlign: 'right' }}>
        <AuthButton type="primary" size="small" perm="master:product:sku" onClick={() => open()}>
          新增SKU
        </AuthButton>
      </div>
      <Table<ProductSku> rowKey="id" size="small" pagination={false} columns={columns} dataSource={rows} />
      <Modal
        title={editing ? '编辑SKU' : '新增SKU'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={mutation.isPending}
        // forceRender：表单弹窗预挂载，打开处理器中的 resetFields/setFieldsValue 始终命中已连接实例
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(v) => mutation.mutate(v)} initialValues={{ status: 1 }}>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="skuCode"
              label="SKU编码"
              style={{ flex: 1 }}
              rules={[
                { required: true, message: '请输入SKU编码' },
                { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线、短横线' },
              ]}
            >
              <Input maxLength={50} disabled={!!editing} />
            </Form.Item>
            <Form.Item name="spec" label="规格描述" style={{ flex: 1 }}>
              <Input placeholder="如 250g/袋" maxLength={100} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="packageForm" label="包装形态" style={{ flex: 1 }}>
              <Select allowClear options={PACKAGE_FORMS.map((p) => ({ value: p, label: p }))} />
            </Form.Item>
            <Form.Item name="netContent" label="净含量数值" style={{ flex: 1 }}>
              <InputNumber min={0} step="0.01" style={{ width: '100%' }} placeholder="250" />
            </Form.Item>
            <Form.Item name="netContentUnit" label="单位" style={{ flex: 1 }}>
              <Input placeholder="g / ml / 片" maxLength={10} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="barcode" label="商品条码" style={{ flex: 1 }}>
              <Input maxLength={50} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }} rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '停用' },
                ]}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </>
  );
};

const LicensePanel: React.FC<{ productId: LongId; rows: ProductLicense[]; onChanged: () => void }> = ({
  productId,
  rows,
  onChanged,
}) => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProductLicense | null>(null);
  const [form] = Form.useForm<ProductLicenseUpsert & { validToDate?: dayjs.Dayjs }>();

  const mutation = useMutation({
    mutationFn: async (v: ProductLicenseUpsert & { validToDate?: dayjs.Dayjs }) => {
      const payload: ProductLicenseUpsert = {
        id: editing?.id,
        licenseType: v.licenseType,
        certNo: v.certNo,
        validTo: v.validToDate?.format('YYYY-MM-DD'),
      };
      if (editing) await productApi.updateLicense(productId, editing.id, payload);
      else await productApi.addLicense(productId, payload);
    },
    onSuccess: () => {
      message.success('资质已保存');
      setModalOpen(false);
      onChanged();
    },
  });

  const removeMutation = useMutation({
    mutationFn: (licenseId: LongId) => productApi.removeLicense(productId, licenseId),
    onSuccess: () => {
      message.success('资质已删除（逻辑删除，留痕）');
      onChanged();
    },
  });

  const open = (r?: ProductLicense) => {
    setEditing(r || null);
    form.resetFields();
    form.setFieldsValue(r ? { ...r, validToDate: r.validTo ? dayjs(r.validTo) : undefined } : {});
    setModalOpen(true);
  };

  const columns: ColumnsType<ProductLicense> = [
    { title: '资质类型', dataIndex: 'licenseType', width: 200 },
    { title: '证号/备案号', dataIndex: 'certNo' },
    { title: '有效期至', dataIndex: 'validTo', width: 140 },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, r) => (
        <Space>
          <AuthButton type="link" size="small" perm="master:product:edit" onClick={() => open(r)}>
            编辑
          </AuthButton>
          <Popconfirm title="删除该资质？" onConfirm={() => removeMutation.mutate(r.id)}>
            <AuthButton type="link" size="small" danger perm="master:product:edit">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div style={{ marginBottom: 8, textAlign: 'right' }}>
        <AuthButton type="primary" size="small" perm="master:product:edit" onClick={() => open()}>
          新增资质
        </AuthButton>
      </div>
      <Table<ProductLicense> rowKey="id" size="small" pagination={false} columns={columns} dataSource={rows} />
      <Modal
        title={editing ? '编辑资质' : '新增资质'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={mutation.isPending}
        // forceRender：表单弹窗预挂载，打开处理器中的 resetFields/setFieldsValue 始终命中已连接实例
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(v) => mutation.mutate(v)}>
          <Form.Item name="licenseType" label="资质类型" rules={[{ required: true, message: '请输入资质类型' }]}>
            <Input placeholder="生产许可证 / 注册备案凭证 / 外检报告" maxLength={50} />
          </Form.Item>
          <Form.Item name="certNo" label="证号/备案号">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="validToDate" label="有效期至">
            <Input type="date" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default ProductList;
