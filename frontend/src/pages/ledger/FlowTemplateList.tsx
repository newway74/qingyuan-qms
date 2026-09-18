import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Tooltip } from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CopyOutlined,
  DeleteOutlined,
  PlusOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import { useAuthStore } from '@/store/authStore';
import { ledgerApi } from '@/api/ledger';
import type { LongId } from '@/types/auth';
import type {
  FieldType,
  FlowNodeFieldInput,
  FlowNodeInput,
  FlowTemplate,
  FlowTemplateDetailVO,
  FlowTemplateUpsertRequest,
  MaterialItemUpsertRequest,
  MaterialItemVO,
} from '@/types/ledger';
import message from '@/utils/feedback';

const FIELD_TYPES: Array<{ value: FieldType; label: string }> = [
  { value: 'TEXT', label: '文本' },
  { value: 'TEXTAREA', label: '多行文本' },
  { value: 'DATE', label: '日期' },
  { value: 'SELECT', label: '下拉选项' },
  { value: 'FILE', label: '附件' },
  { value: 'CONCLUSION', label: '结论' },
];

let clientNodeSeq = 0;
const newNode = (): FlowNodeInput => ({
  nodeCode: undefined,
  nodeName: '',
  nodeSort: 0,
  linkSupplier: 0,
  remark: '',
  fields: [],
});
const newField = (): FlowNodeFieldInput => {
  clientNodeSeq += 1;
  return { fieldCode: `TMP_FIELD_${clientNodeSeq}`, fieldName: '', fieldType: 'TEXT',
    options: [], required: 0, sort: 0 };
};

const FlowTemplateList: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [basicForm] = Form.useForm<FlowTemplateUpsertRequest>();
  const [basicOpen, setBasicOpen] = useState(false);
  const [editingBasic, setEditingBasic] = useState<FlowTemplate | null>(null);
  const [editorId, setEditorId] = useState<LongId | null>(null);
  const [nodes, setNodes] = useState<FlowNodeInput[]>([]);

  const listQuery = useQuery({
    queryKey: ['ledger-flow-templates'],
    queryFn: () => ledgerApi.templateList(),
  });

  const detailQuery = useQuery({
    queryKey: ['ledger-template-detail', editorId],
    queryFn: () => ledgerApi.templateDetail(editorId as LongId),
    enabled: editorId !== null,
  });

  useEffect(() => {
    if (detailQuery.data) {
      const d: FlowTemplateDetailVO = detailQuery.data;
      setNodes(
        d.nodes.map((n) => ({
          id: n.id,
          nodeCode: n.nodeCode,
          nodeName: n.nodeName,
          nodeSort: n.nodeSort,
          linkSupplier: n.linkSupplier,
          remark: n.remark ?? '',
          fields: n.fields.map((f) => ({
            id: f.id,
            fieldCode: f.fieldCode,
            fieldName: f.fieldName,
            fieldType: f.fieldType,
            options: f.options ?? [],
            required: f.required,
            sort: f.sort,
          })),
        })),
      );
    }
  }, [detailQuery.data]);

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['ledger-flow-templates'] });

  const createMutation = useMutation({
    mutationFn: (data: FlowTemplateUpsertRequest) => ledgerApi.templateCreate(data),
    onSuccess: (newId) => {
      message.success('模板已创建，请继续编排流程节点');
      setBasicOpen(false);
      invalidate();
      setEditorId(newId);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: FlowTemplateUpsertRequest) => ledgerApi.templateUpdate(data),
    onSuccess: () => {
      message.success('模板信息已保存');
      setBasicOpen(false);
      invalidate();
    },
  });

  const copyMutation = useMutation({
    mutationFn: ({ id, name }: { id: LongId; name: string }) =>
      ledgerApi.templateCopy(id, name),
    onSuccess: () => {
      message.success('模板已复制，可在副本上自由修改');
      invalidate();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: LongId) => ledgerApi.templateRemove(id),
    onSuccess: () => {
      message.success('模板已删除');
      invalidate();
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: (record: FlowTemplate) =>
      ledgerApi.templateUpdate({
        id: record.id,
        templateName: record.templateName,
        status: record.status === 1 ? 0 : 1,
        remark: record.remark,
      }),
    onSuccess: () => {
      message.success('状态已更新');
      invalidate();
    },
  });

  const saveNodesMutation = useMutation({
    mutationFn: async () => {
      if (editorId === null) return;
      const cleaned = nodes
        .map((n, i) => ({
          ...n,
          nodeSort: (i + 1) * 10,
          fields: (n.fields ?? []).map((f, fi) => ({
            ...f,
            sort: (fi + 1) * 10,
            options: f.fieldType === 'SELECT' ? f.options ?? [] : [],
          })),
        }))
        .filter((n) => n.nodeName.trim());
      const effectiveId = await ledgerApi.templateSaveNodes(editorId, { nodes: cleaned });
      if (String(effectiveId) !== String(editorId)) {
        // 节点变更生成了新版本行，跳转到新版本继续编辑
        setEditorId(effectiveId);
      }
    },
    onSuccess: () => {
      message.success('节点编排已保存（如有变更将自动生成新版本，旧版本保留给已建商品）');
      invalidate();
      queryClient.invalidateQueries({ queryKey: ['ledger-template-detail', editorId] });
    },
  });

  const openBasicCreate = () => {
    setEditingBasic(null);
    basicForm.resetFields();
    basicForm.setFieldsValue({ status: 1 });
    setBasicOpen(true);
  };

  const openBasicEdit = (record: FlowTemplate) => {
    setEditingBasic(record);
    basicForm.setFieldsValue(record);
    setBasicOpen(true);
  };

  // 节点编辑操作（纯本地状态，保存时整体提交）
  const updateNode = (index: number, patch: Partial<FlowNodeInput>) =>
    setNodes((prev) => prev.map((n, i) => (i === index ? { ...n, ...patch } : n)));
  const moveNode = (index: number, delta: -1 | 1) =>
    setNodes((prev) => {
      const target = index + delta;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  const removeNode = (index: number) =>
    setNodes((prev) => prev.filter((_, i) => i !== index));
  const updateField = (ni: number, fi: number, patch: Partial<FlowNodeFieldInput>) =>
    setNodes((prev) =>
      prev.map((n, i) =>
        i === ni
          ? {
              ...n,
              fields: (n.fields ?? []).map((f, j) => (j === fi ? { ...f, ...patch } : f)),
            }
          : n,
      ),
    );
  const removeField = (ni: number, fi: number) =>
    setNodes((prev) =>
      prev.map((n, i) =>
        i === ni ? { ...n, fields: (n.fields ?? []).filter((_, j) => j !== fi) } : n,
      ),
    );

  const validateBeforeSave = (): string | null => {
    if (nodes.length === 0) return '至少需要一个节点';
    const names = new Set<string>();
    for (const n of nodes) {
      const name = n.nodeName.trim();
      if (!name) return '存在未命名节点，请补全或删除';
      if (names.has(name)) return `节点名称重复：${name}`;
      names.add(name);
      for (const f of n.fields ?? []) {
        if (!f.fieldName.trim()) return `节点【${name}】存在未命名字段`;
        if (f.fieldType === 'SELECT' && (f.options ?? []).length === 0) {
          return `节点【${name}】的下拉字段【${f.fieldName}】至少配置一个选项`;
        }
      }
    }
    return null;
  };

  const columns: ColumnsType<FlowTemplate> = [
    { title: '模板名称', dataIndex: 'templateName',
      render: (v: string, r) => (
        <Space>
          <a onClick={() => setEditorId(r.id)}>{v}</a>
          {r.isPreset === 1 && <Tag color="blue">系统预置</Tag>}
        </Space>
      ) },
    { title: '编码', dataIndex: 'templateCode', width: 180 },
    { title: '版本', dataIndex: 'version', width: 80,
      render: (v: number) => <Tag>v{v}</Tag> },
    { title: '状态', dataIndex: 'status', width: 90,
      render: (v: number) => (v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>) },
    {
      title: '操作',
      key: 'actions',
      width: 340,
      render: (_, record) => (
        <Space size={4} wrap>
          <AuthButton type="link" size="small" perm="ledger:template:list"
            icon={<SettingOutlined />} onClick={() => setEditorId(record.id)}>
            配置节点
          </AuthButton>
          <AuthButton type="link" size="small" perm="ledger:template:edit"
            icon={<CopyOutlined />}
            onClick={() => {
              let name = '';
              Modal.confirm({
                title: '复制流程模板',
                content: (
                  <Input placeholder="新模板名称"
                    onChange={(e) => { name = e.target.value; }} />
                ),
                onOk: () =>
                  copyMutation.mutateAsync({
                    id: record.id,
                    name: name.trim() || `${record.templateName}-副本`,
                  }),
              });
            }}>
            复制
          </AuthButton>
          <AuthButton type="link" size="small" perm="ledger:template:edit"
            onClick={() => openBasicEdit(record)}>
            重命名
          </AuthButton>
          <AuthButton type="link" size="small" perm="ledger:template:edit"
            onClick={() => toggleStatusMutation.mutate(record)}>
            {record.status === 1 ? '停用' : '启用'}
          </AuthButton>
          <Popconfirm
            title="确认删除该模板？"
            description="系统预置模板与已绑定商品的模板不可删除。"
            okText="删除" okButtonProps={{ danger: true }} cancelText="取消"
            onConfirm={() => deleteMutation.mutate(record.id)}>
            <AuthButton type="link" size="small" danger icon={<DeleteOutlined />}
              perm="ledger:template:edit">
              删除
            </AuthButton>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card
        size="small"
        title="商品全流程模板配置"
        extra={
          <Space>
            <Button onClick={() => navigate('/ledger/goods')}>返回商品台账</Button>
            <AuthButton type="primary" icon={<PlusOutlined />}
              perm="ledger:template:edit" onClick={openBasicCreate}>
              新建模板
            </AuthButton>
          </Space>
        }>
        <Table<FlowTemplate>
          rowKey="id"
          loading={listQuery.isLoading}
          dataSource={listQuery.data ?? []}
          columns={columns}
          pagination={false}
        />
      </Card>

      <div style={{ height: 12 }} />
      <MaterialItemPanel />

      {/* 新建 / 重命名 */}
      <Modal
        title={editingBasic ? '编辑模板信息' : '新建流程模板'}
        open={basicOpen}
        onCancel={() => setBasicOpen(false)}
        onOk={() => basicForm.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText="保存" cancelText="取消"
        // forceRender：Form 预先挂载，openBasicCreate/openEdit 打开时即可安全
        // resetFields/setFieldsValue（open 处理器每次都会重设值，无需销毁重建）
        forceRender>
        <Form
          form={basicForm}
          layout="vertical"
          style={{ marginTop: 16 }}
          onFinish={(values) => {
            if (editingBasic) {
              updateMutation.mutate({ ...values, id: editingBasic.id });
            } else {
              createMutation.mutate(values);
            }
          }}>
          <Form.Item name="templateName" label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' },
              { max: 50, message: '不超过 50 个字符' }]}>
            <Input placeholder="如：新品引入全流程（定制）" />
          </Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked"
            getValueFromEvent={(v: boolean) => (v ? 1 : 0)}
            getValueProps={(v) => ({ checked: v === undefined ? true : v === 1 })}>
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 节点编排 */}
      <Drawer
        title={`配置节点 - ${detailQuery.data?.templateName ?? ''}（v${detailQuery.data?.version ?? '-'}）`}
        width={960}
        open={editorId !== null}
        onClose={() => setEditorId(null)}
        destroyOnHidden
        extra={
          <Space>
            <Tooltip title="新增节点将追加到流程线末尾，可用上下箭头调整顺序">
              <Button icon={<PlusOutlined />} onClick={() => setNodes((p) => [...p, newNode()])}>
                新增节点
              </Button>
            </Tooltip>
            <Button type="primary" loading={saveNodesMutation.isPending}
              onClick={() => {
                const err = validateBeforeSave();
                if (err) {
                  message.warning(err);
                  return;
                }
                saveNodesMutation.mutate();
              }}>
              保存编排
            </Button>
          </Space>
        }>
        {detailQuery.data?.boundGoodsCount ? (
          <Alert
            type="warning" showIcon style={{ marginBottom: 12 }}
            message={`已有 ${detailQuery.data.boundGoodsCount} 个商品使用该模板。保存节点编排时会生成新版本，已有商品继续沿用旧版本，不强制追溯。`}
          />
        ) : null}
        {!detailQuery.isLoading && nodes.length === 0 && (
          <Empty description="暂无节点，点击右上角“新增节点”开始编排">
            <Button type="primary" icon={<PlusOutlined />}
              onClick={() => setNodes([newNode()])}>
              新增第一个节点
            </Button>
          </Empty>
        )}
        <Space direction="vertical" size={12} style={{ display: 'flex' }}>
          {nodes.map((node, ni) => (
            <Card
              key={ni}
              size="small"
              type="inner"
              title={
                <Space>
                  <Tag color="blue">{ni + 1}</Tag>
                  <Input
                    value={node.nodeName}
                    placeholder="节点名称（如：过会）"
                    style={{ width: 220 }}
                    onChange={(e) => updateNode(ni, { nodeName: e.target.value })}
                  />
                  <span style={{ fontSize: 12, color: '#888' }}>
                    编码：{node.nodeCode || `NODE_${ni + 1}（保存后生效）`}
                  </span>
                </Space>
              }
              extra={
                <Space size={2}>
                  <Tooltip title="上移">
                    <Button size="small" type="text" icon={<ArrowUpOutlined />}
                      disabled={ni === 0} onClick={() => moveNode(ni, -1)} />
                  </Tooltip>
                  <Tooltip title="下移">
                    <Button size="small" type="text" icon={<ArrowDownOutlined />}
                      disabled={ni === nodes.length - 1}
                      onClick={() => moveNode(ni, 1)} />
                  </Tooltip>
                  <Popconfirm title="删除该节点？" okText="删除"
                    okButtonProps={{ danger: true }} cancelText="取消"
                    onConfirm={() => removeNode(ni)}>
                    <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              }>
              <Space style={{ marginBottom: 8 }}>
                <span>关联供应商：</span>
                <Switch
                  checked={node.linkSupplier === 1}
                  checkedChildren="是" unCheckedChildren="否"
                  onChange={(v) => updateNode(ni, { linkSupplier: v ? 1 : 0 })}
                />
                <Input
                  value={node.remark}
                  placeholder="节点说明（选填）"
                  style={{ width: 320 }}
                  onChange={(e) => updateNode(ni, { remark: e.target.value })}
                />
              </Space>
              <Table
                rowKey={(f) => f.fieldCode ?? `${ni}-${(f as FlowNodeFieldInput & {_k?:number})._k ?? ''}`}
                size="small"
                pagination={false}
                dataSource={node.fields ?? []}
                locale={{ emptyText: '该节点暂未配置填写字段' }}
                columns={[
                  {
                    title: '字段名', dataIndex: 'fieldName', width: 200,
                    render: (_: unknown, f: FlowNodeFieldInput) => (
                      <Input value={f.fieldName} placeholder="如：审核结论"
                        onChange={(e) => updateField(ni, (node.fields ?? []).indexOf(f),
                          { fieldName: e.target.value })} />
                    ),
                  },
                  {
                    title: '类型', dataIndex: 'fieldType', width: 150,
                    render: (_: unknown, f: FlowNodeFieldInput) => (
                      <Select
                        style={{ width: 130 }}
                        value={f.fieldType}
                        options={FIELD_TYPES}
                        onChange={(v) => updateField(ni, (node.fields ?? []).indexOf(f),
                          { fieldType: v, options: v === 'SELECT' ? f.options ?? [] : [] })}
                      />
                    ),
                  },
                  {
                    title: '下拉选项', dataIndex: 'options',
                    render: (_: unknown, f: FlowNodeFieldInput) =>
                      f.fieldType === 'SELECT' ? (
                        <Select
                          mode="tags"
                          style={{ width: '100%' }}
                          placeholder="输入选项后回车，可添加多个"
                          value={f.options ?? []}
                          tokenSeparators={[',', '，']}
                          onChange={(vals) => updateField(ni, (node.fields ?? []).indexOf(f),
                            { options: vals })}
                        />
                      ) : (
                        <span style={{ color: '#999' }}>—</span>
                      ),
                  },
                  {
                    title: '必填', dataIndex: 'required', width: 70,
                    render: (_: unknown, f: FlowNodeFieldInput) => (
                      <Switch
                        size="small"
                        checked={f.required === 1}
                        onChange={(v) => updateField(ni, (node.fields ?? []).indexOf(f),
                          { required: v ? 1 : 0 })}
                      />
                    ),
                  },
                  {
                    title: '', width: 48,
                    render: (_: unknown, f: FlowNodeFieldInput) => (
                      <Button size="small" type="text" danger icon={<DeleteOutlined />}
                        onClick={() => removeField(ni, (node.fields ?? []).indexOf(f))} />
                    ),
                  },
                ]}
              />
              <Button size="small" type="dashed" icon={<PlusOutlined />}
                style={{ marginTop: 8 }}
                onClick={() =>
                  updateNode(ni, { fields: [...(node.fields ?? []), newField()] })}>
                添加字段
              </Button>
            </Card>
          ))}
        </Space>
      </Drawer>
    </div>
  );
};

/**
 * 资料项维护面板（管理员）：资料项是商品资料清单与“重点缺口”统计的口径来源。
 * 新建/重新启用的资料项会自动补挂到全部既有商品（不覆盖已维护状态）；预置项只可停用/改名。
 */
const MaterialItemPanel: React.FC = () => {
  const queryClient = useQueryClient();
  const canEditMaterial = useAuthStore((s) => s.hasPerm('ledger:material:edit'));
  const listQuery = useQuery({
    queryKey: ['ledger-material-items'],
    queryFn: () => ledgerApi.materialItemList(),
  });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<MaterialItemVO | null>(null);
  const [form] = Form.useForm<MaterialItemUpsertRequest>();

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['ledger-material-items'] });
    // 资料项变化会影响所有商品的缺口数与详情清单
    queryClient.invalidateQueries({ queryKey: ['ledger-goods'] });
    queryClient.invalidateQueries({ queryKey: ['ledger-goods-detail'] });
  };

  const createMutation = useMutation({
    mutationFn: (data: MaterialItemUpsertRequest) => ledgerApi.materialItemCreate(data),
    onSuccess: () => {
      message.success('资料项已新建，并已补挂到全部商品');
      invalidateAll();
      setFormOpen(false);
    },
  });
  const updateMutation = useMutation({
    mutationFn: (data: MaterialItemUpsertRequest) => ledgerApi.materialItemUpdate(data),
    onSuccess: (_d, variables) => {
      message.success(variables.status === 0 ? '资料项已停用，缺口统计不再计入' : '资料项已更新');
      invalidateAll();
      setFormOpen(false);
    },
  });
  const deleteMutation = useMutation({
    mutationFn: (id: LongId) => ledgerApi.materialItemRemove(id),
    onSuccess: () => {
      message.success('资料项已删除（商品历史清单保留展示）');
      invalidateAll();
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setFormOpen(true);
  };
  const openEdit = (record: MaterialItemVO) => {
    setEditing(record);
    form.setFieldsValue({
      id: record.id,
      itemName: record.itemName,
      sort: record.sort,
      status: record.status,
    });
    setFormOpen(true);
  };

  const columns: ColumnsType<MaterialItemVO> = [
    { title: '资料项', dataIndex: 'itemName',
      render: (v: string, r) => (
        <Space>
          {v}
          {r.isPreset === 1 && <Tag color="blue">系统预置</Tag>}
          {r.status === 0 && <Tag>已停用</Tag>}
        </Space>
      ) },
    { title: '编码', dataIndex: 'itemCode', width: 200 },
    { title: '排序', dataIndex: 'sort', width: 80 },
    {
      title: '启用',
      dataIndex: 'status',
      width: 80,
      render: (v: number, record) =>
        canEditMaterial ? (
          <Switch
            size="small"
            checked={v === 1}
            checkedChildren="启用"
            unCheckedChildren="停用"
            onChange={(checked) => updateMutation.mutate({
              id: record.id,
              itemName: record.itemName,
              sort: record.sort,
              status: checked ? 1 : 0,
            })}
          />
        ) : (
          <Tag>{v === 1 ? '启用' : '停用'}</Tag>
        ),
    },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <AuthButton type="link" size="small" perm="ledger:material:edit"
            onClick={() => openEdit(record)}>
            编辑
          </AuthButton>
          {record.isPreset !== 1 && (
            <Popconfirm
              title="确认删除该资料项？"
              description="删除后商品历史清单仍保留展示，但不再计入重点缺口。"
              okText="删除" okButtonProps={{ danger: true }} cancelText="取消"
              onConfirm={() => deleteMutation.mutate(record.id)}>
              <AuthButton type="link" size="small" danger perm="ledger:material:edit">
                删除
              </AuthButton>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      size="small"
      title="资料项维护"
      extra={
        <AuthButton type="primary" ghost icon={<PlusOutlined />}
          perm="ledger:material:edit" onClick={openCreate}>
          新建资料项
        </AuthButton>
      }>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="资料项（如产前样品、型式检验报告）是商品“资料清单”与重点缺口统计的口径来源；新建或重新启用后会自动补挂到全部既有商品，不影响已维护的状态。"
      />
      <Table<MaterialItemVO>
        rowKey="id"
        size="small"
        loading={listQuery.isLoading}
        dataSource={listQuery.data ?? []}
        columns={columns}
        pagination={false}
      />
      <Modal
        title={editing ? '编辑资料项' : '新建资料项'}
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText="保存"
        cancelText="取消"
        // forceRender：资料项 Form 打开前已连接，openCreate/openEdit 中
        // resetFields/setFieldsValue 不再触发“useForm 未连接”告警
        forceRender>
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{ status: 1 }}
          onFinish={(values) => {
            if (editing) {
              updateMutation.mutate({ ...values, id: editing.id });
            } else {
              createMutation.mutate(values);
            }
          }}>
          <Form.Item name="itemName" label="资料项名称"
            rules={[{ required: true, message: '请输入资料项名称' },
              { max: 50, message: '不超过 50 个字符' }]}>
            <Input placeholder="如：随货同行单" />
          </Form.Item>
          <Form.Item name="sort" label="排序（从小到大）">
            <InputNumber min={1} max={9999} style={{ width: '100%' }} placeholder="留空自动排末尾" />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label="状态" valuePropName="checked"
              getValueFromEvent={(v: boolean) => (v ? 1 : 0)}
              getValueProps={(v) => ({ checked: v === undefined ? true : v === 1 })}>
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
};

export default FlowTemplateList;
