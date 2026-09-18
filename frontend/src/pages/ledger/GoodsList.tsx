import React, { useMemo, useState } from 'react';
import { Button, Card, DatePicker, Drawer, Form, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { Dayjs } from 'dayjs';
import { useNavigate } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import { categoryApi } from '@/api/category';
import { ledgerApi } from '@/api/ledger';
import type { CategoryTree, LongId } from '@/types/auth';
import type {
  CooperateResult,
  FlowTemplate,
  GoodsListQuery,
  GoodsListVO,
  GoodsUpsertRequest,
} from '@/types/ledger';
import GoodsImportModal from './GoodsImportModal';
import message from '@/utils/feedback';

/** 合作结论字典（与后端 LedgerConst 对齐） */
const COOPERATE_RESULTS: Array<{ value: CooperateResult; label: string; color: string }> = [
  { value: 'YES', label: '合作', color: 'green' },
  { value: 'PENDING', label: '待定', color: 'gold' },
  { value: 'NO', label: '不合作', color: 'red' },
];

interface GoodsFormValues extends Omit<GoodsUpsertRequest, 'meetingDate' | 'launchDate'> {
  meetingDate?: Dayjs;
  launchDate?: Dayjs;
}

const GoodsList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<GoodsListQuery>({ pageNo: 1, pageSize: 10 });
  const [filterForm] = Form.useForm();
  const [editForm] = Form.useForm<GoodsFormValues>();
  const [editing, setEditing] = useState<GoodsListVO | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [l1Id, setL1Id] = useState<LongId>();

  const pageQuery = useQuery({
    queryKey: ['ledger-goods', query],
    queryFn: () => ledgerApi.goodsPage(query),
  });

  const categoryTreeQuery = useQuery({
    queryKey: ['category-tree'],
    queryFn: () => categoryApi.tree(),
    staleTime: 5 * 60 * 1000,
  });

  // 当前节点筛选项取预置流程模板的节点（与默认流程线一致）
  const templateEnabledQuery = useQuery({
    queryKey: ['ledger-templates-enabled'],
    queryFn: () => ledgerApi.templateEnabled(),
  });
  const presetTemplate = useMemo(
    () =>
      templateEnabledQuery.data?.find((t) => t.isPreset === 1) ??
      templateEnabledQuery.data?.[0],
    [templateEnabledQuery.data],
  );
  const presetDetailQuery = useQuery({
    queryKey: ['ledger-template-detail', presetTemplate?.id],
    queryFn: () => ledgerApi.templateDetail(presetTemplate!.id),
    enabled: !!presetTemplate,
    staleTime: 5 * 60 * 1000,
  });
  const nodeOptions = useMemo(
    () =>
      (presetDetailQuery.data?.nodes ?? []).map((n) => ({
        value: n.nodeCode,
        label: n.nodeName,
      })),
    [presetDetailQuery.data],
  );

  const l1Options = useMemo(
    () =>
      (categoryTreeQuery.data ?? [])
        .filter((c) => c.status === 1)
        .map((c: CategoryTree) => ({ value: c.id, label: c.name })),
    [categoryTreeQuery.data],
  );
  const l2Options = useMemo(() => {
    const root = (categoryTreeQuery.data ?? []).find((c) => String(c.id) === String(l1Id));
    return (root?.children ?? [])
      .filter((c) => c.status === 1)
      .map((c) => ({ value: c.id, label: c.name }));
  }, [categoryTreeQuery.data, l1Id]);

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['ledger-goods'] });

  const saveMutation = useMutation({
    mutationFn: async (values: GoodsFormValues) => {
      const payload: GoodsUpsertRequest = {
        ...values,
        meetingDate: values.meetingDate?.format('YYYY-MM-DD'),
        launchDate: values.launchDate?.format('YYYY-MM-DD'),
      };
      if (editing) {
        await ledgerApi.goodsUpdate({ ...payload, id: editing.id });
      } else {
        await ledgerApi.goodsCreate(payload);
      }
    },
    onSuccess: () => {
      message.success(editing ? '商品已更新' : '商品已建档，流程节点与资料清单已初始化');
      setDrawerOpen(false);
      invalidate();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: LongId) => ledgerApi.goodsRemove(id),
    onSuccess: () => {
      message.success('商品已删除（流程记录与资料清单一并移除）');
      invalidate();
    },
  });

  const openCreate = () => {
    setEditing(null);
    setL1Id(undefined);
    editForm.resetFields();
    editForm.setFieldsValue({ cooperateResult: 'PENDING' } as never);
    setDrawerOpen(true);
  };

  const openEdit = (record: GoodsListVO) => {
    setEditing(record);
    setL1Id(record.categoryL1Id);
    editForm.setFieldsValue({
      ...record,
      meetingDate: record.meetingDate ? dayjs(record.meetingDate) : undefined,
      launchDate: record.launchDate ? dayjs(record.launchDate) : undefined,
    });
    setDrawerOpen(true);
  };

  const onSearch = () => {
    const v = filterForm.getFieldsValue();
    setQuery((q) => ({
      ...q,
      pageNo: 1,
      keyword: v.keyword?.trim() || undefined,
      categoryL1Id: v.categoryL1Id,
      categoryL2Id: v.categoryL2Id,
      cooperateResult: v.cooperateResult,
      currentNodeCode: v.currentNodeCode,
    }));
  };

  const columns: ColumnsType<GoodsListVO> = [
    {
      title: 'SKU',
      dataIndex: 'sku',
      width: 150,
      fixed: 'left',
      render: (v: string, record) => (
        <Button type="link" size="small" style={{ padding: 0 }}
          onClick={() => navigate(`/ledger/goods/${record.id}`)}>
          {v}
        </Button>
      ),
    },
    { title: '产品通用名', dataIndex: 'commonName', width: 180 },
    { title: '规格', dataIndex: 'spec', width: 120 },
    { title: '品牌', dataIndex: 'brand', width: 110 },
    { title: '生产企业', dataIndex: 'manufacturer', width: 180, ellipsis: true },
    {
      title: '品类',
      key: 'category',
      width: 150,
      render: (_, r) =>
        [r.categoryL1Name, r.categoryL2Name].filter(Boolean).join(' / ') || '-',
    },
    {
      title: '合作结论',
      dataIndex: 'cooperateResult',
      width: 90,
      render: (v: CooperateResult) => {
        const meta = COOPERATE_RESULTS.find((x) => x.value === v);
        return <Tag color={meta?.color}>{meta?.label ?? v}</Tag>;
      },
    },
    { title: '当前节点', dataIndex: 'currentNodeName', width: 130,
      render: (v?: string) => v ?? '-' },
    {
      title: '资料缺口',
      dataIndex: 'gapCount',
      width: 90,
      render: (v: number) =>
        v > 0 ? <Tag color="orange">{v} 项</Tag> : <Tag color="green">齐套</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'dataSource',
      width: 80,
      render: (v: string) =>
        v === 'DEMO' ? <Tag color="purple">演示</Tag> : <Tag>录入</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small"
            onClick={() => navigate(`/ledger/goods/${record.id}`)}>
            详情
          </Button>
          <AuthButton type="link" size="small" perm="ledger:goods:edit"
            onClick={() => openEdit(record)}>
            编辑
          </AuthButton>
          <Popconfirm
            title="确认删除该商品？"
            description="将同时删除其全流程记录与资料清单，删除后不可恢复。"
            okText="确认删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteMutation.mutate(record.id)}>
            <AuthButton type="link" size="small" danger perm="ledger:goods:delete">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Form form={filterForm} layout="inline" onFinish={onSearch}>
          <Form.Item name="keyword" style={{ marginBottom: 8 }}>
            <Input.Search
              placeholder="SKU / 名称 / 品牌 / 生产企业"
              allowClear
              style={{ width: 260 }}
              onSearch={onSearch}
            />
          </Form.Item>
          <Form.Item name="categoryL1Id" style={{ marginBottom: 8 }}>
            <Select
              placeholder="一级品类"
              allowClear
              style={{ width: 150 }}
              options={l1Options}
              onChange={(v) => {
                setL1Id(v);
                filterForm.setFieldValue('categoryL2Id', undefined);
              }}
            />
          </Form.Item>
          <Form.Item name="categoryL2Id" style={{ marginBottom: 8 }}>
            <Select placeholder="二级品类" allowClear style={{ width: 150 }}
              options={l2Options} disabled={!l1Id} />
          </Form.Item>
          <Form.Item name="cooperateResult" style={{ marginBottom: 8 }}>
            <Select placeholder="合作结论" allowClear style={{ width: 120 }}
              options={COOPERATE_RESULTS.map(({ value, label }) => ({ value, label }))} />
          </Form.Item>
          <Form.Item name="currentNodeCode" style={{ marginBottom: 8 }}>
            <Select placeholder="当前节点" allowClear style={{ width: 160 }}
              options={nodeOptions} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Space>
              <Button type="primary" htmlType="submit">查询</Button>
              <Button
                onClick={() => {
                  filterForm.resetFields();
                  setL1Id(undefined);
                  setQuery({ pageNo: 1, pageSize: 10 });
                }}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        size="small"
        title="商品品控台账"
        extra={
          <Space>
            <AuthButton perm="ledger:goods:import" onClick={() => setImportOpen(true)}>
              Excel 导入
            </AuthButton>
            <AuthButton type="primary" perm="ledger:goods:create" onClick={openCreate}>
              商品建档
            </AuthButton>
          </Space>
        }>
        <Table<GoodsListVO>
          rowKey="id"
          loading={pageQuery.isLoading}
          dataSource={pageQuery.data?.records ?? []}
          columns={columns}
          scroll={{ x: 1500 }}
          pagination={{
            current: Number(pageQuery.data?.pageNo ?? query.pageNo),
            pageSize: Number(pageQuery.data?.pageSize ?? query.pageSize),
            total: Number(pageQuery.data?.total ?? 0),
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (pageNo, pageSize) => setQuery((q) => ({ ...q, pageNo, pageSize })),
          }}
        />
      </Card>

      <Drawer
        title={editing ? '编辑商品' : '商品建档'}
        width={640}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        // forceRender 让 Form 在首次打开展示前就完成挂载连接，
        // openCreate/openEdit 每次打开都会 reset/setFieldsValue，等价于关闭即清空
        forceRender
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saveMutation.isPending}
              onClick={() => editForm.submit()}>
              保存
            </Button>
          </Space>
        }>
        <Form<GoodsFormValues>
          form={editForm}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="sku" label="SKU" style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入 SKU' },
                { max: 64, message: '不超过 64 个字符' }]}>
              <Input placeholder="商品唯一编码，租户内不可重复" disabled={!!editing} />
            </Form.Item>
            <Form.Item name="commonName" label="产品通用名" style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入产品通用名' }]}>
              <Input placeholder="如：某类代用茶" />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="spec" label="规格" style={{ flex: 1 }}>
              <Input placeholder="如：150g（5g×30袋）" />
            </Form.Item>
            <Form.Item name="brand" label="品牌" style={{ flex: 1 }}>
              <Input placeholder="品牌名称" />
            </Form.Item>
          </Space>
          <Form.Item name="manufacturer" label="生产企业">
            <Input placeholder="生产企业全称" />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="approvalNo" label="批准文号/备案号" style={{ flex: 1 }}>
              <Input placeholder="批准文号或备案编号" />
            </Form.Item>
            <Form.Item name="upc" label="UPC(69码)" style={{ flex: 1 }}>
              <Input placeholder="69 开头商品条码" />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="categoryL1Id" label="一级品类" style={{ flex: 1 }}>
              <Select
                placeholder="选择一级品类"
                allowClear
                options={l1Options}
                onChange={(v) => {
                  setL1Id(v);
                  editForm.setFieldValue('categoryL2Id', undefined);
                }}
              />
            </Form.Item>
            <Form.Item name="categoryL2Id" label="二级品类" style={{ flex: 1 }}>
              <Select placeholder="选择二级品类" allowClear disabled={!l1Id}
                options={l2Options} />
            </Form.Item>
          </Space>
          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="meetingDate" label="过会时间" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="cooperateResult" label="最终合作结论" style={{ flex: 1 }}>
              <Select options={COOPERATE_RESULTS.map(({ value, label }) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="launchDate" label="上市日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          {!editing && (
            <Form.Item name="templateId" label="流程模板"
              tooltip="不选则使用系统预置的“商品全流程（默认示例）”模板">
              <Select
                placeholder="默认使用预置模板"
                allowClear
                fieldNames={{ label: 'templateName', value: 'id' }}
                options={(templateEnabledQuery.data ?? []) as FlowTemplate[]}
                optionRender={(o) => (
                  <Space>
                    <span>{o.data.templateName}</span>
                    <Tag color={o.data.isPreset === 1 ? 'blue' : 'default'}>
                      v{o.data.version}
                    </Tag>
                  </Space>
                )}
              />
            </Form.Item>
          )}
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="选填" />
          </Form.Item>
        </Form>
      </Drawer>

      <GoodsImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={() => {
          invalidate();
          queryClient.invalidateQueries({ queryKey: ['ledger-supplier-options'] });
        }}
      />
    </div>
  );
};

export default GoodsList;
