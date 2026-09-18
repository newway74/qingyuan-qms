import React from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, Modal, Radio, Select, Space, Steps, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { ledgerApi } from '@/api/ledger';
import AttachmentUpload from '@/components/AttachmentUpload';
import AuthButton from '@/components/AuthButton';
import { useAuthStore } from '@/store/authStore';
import type {
  CooperateResult,
  FlowNodeFieldDefVO,
  FlowNodeStatus,
  FlowNodeVO,
  GoodsDetailVO,
  MaterialStatus,
  MaterialVO,
  NodeRecordUpsertRequest,
  SupplierOption,
} from '@/types/ledger';
import type { LongId } from '@/types/auth';
import message from '@/utils/feedback';

const COOPERATE_META: Record<CooperateResult, { label: string; color: string }> = {
  YES: { label: '合作', color: 'green' },
  PENDING: { label: '待定', color: 'gold' },
  NO: { label: '不合作', color: 'red' },
};

const NODE_STATUS_META: Record<FlowNodeStatus, { label: string; status: 'wait' | 'process' | 'finish' | 'error' }> = {
  NOT_STARTED: { label: '未开始', status: 'wait' },
  IN_PROGRESS: { label: '进行中', status: 'process' },
  DONE: { label: '已完成', status: 'finish' },
  REJECTED: { label: '不通过', status: 'error' },
};

const NODE_STATUS_OPTIONS: Array<{ value: FlowNodeStatus; label: string }> = [
  { value: 'NOT_STARTED', label: '未开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'DONE', label: '已完成' },
  { value: 'REJECTED', label: '不通过' },
];

/** 结论型自定义字段的系统固定选项（模板中不可自定义） */
const CONCLUSION_OPTIONS = ['通过', '不通过', '待整改'];

const MATERIAL_META: Record<MaterialStatus, { label: string; color: string }> = {
  READY: { label: '齐套', color: 'green' },
  MISSING: { label: '缺失', color: 'red' },
  PENDING: { label: '待确认', color: 'gold' },
};

const MATERIAL_STATUS_OPTIONS: Array<{ value: MaterialStatus; label: string }> = [
  { value: 'READY', label: '齐套' },
  { value: 'MISSING', label: '缺失' },
  { value: 'PENDING', label: '待确认' },
];

/** 节点办理录入抽屉：按节点字段定义动态渲染表单项 */
const NodeRecordDrawer: React.FC<{
  goodsId: LongId;
  node: FlowNodeVO | null;
  readOnly: boolean;
  supplierOptions: SupplierOption[];
  onClose: () => void;
  onSaved: () => void;
}> = ({ goodsId, node, readOnly, supplierOptions, onClose, onSaved }) => {
  const [form] = Form.useForm();
  const statusValue = Form.useWatch('status', form) as FlowNodeStatus | undefined;
  const finished = statusValue === 'DONE' || statusValue === 'REJECTED';

  // Drawer(destroyOnHidden) 内容懒挂载：若用 useEffect + setFieldsValue，
  // 会在表单项注册前写入而全部丢失，故改为 key 重建 Form + initialValues 初始化。
  const initialValues = React.useMemo(() => {
    if (!node) {
      return undefined;
    }
    const values: Record<string, unknown> = {
      status: node.status,
      finishDate: node.finishDate ? dayjs(node.finishDate) : undefined,
      ownerName: node.ownerName,
      conclusion: node.conclusion,
      supplierId: node.supplierId,
      remark: node.remark,
    };
    (node.fieldDefs ?? []).forEach((f) => {
      const raw = node.fieldValues?.[f.fieldCode];
      values[`field_${f.fieldCode}`] =
        raw === undefined || raw === null ? undefined : String(raw);
    });
    return values;
  }, [node]);

  // 切到“已完成/不通过”时补默认完成日期，减轻录入负担（仍可改）
  const handleStatusChange = (status: FlowNodeStatus) => {
    if ((status === 'DONE' || status === 'REJECTED') && !form.getFieldValue('finishDate')) {
      form.setFieldValue('finishDate', dayjs());
    }
  };

  const renderFieldInput = (f: FlowNodeFieldDefVO) => {
    const name = `field_${f.fieldCode}`;
    const rules = f.required === 1 && finished && !readOnly
      ? [{ required: true, message: `请填写${f.fieldName}` }]
      : [];
    const label = (
      <span>
        {f.fieldName}
        <span style={{ color: '#999', fontWeight: 400, fontSize: 12 }}>（自定义）</span>
      </span>
    );
    switch (f.fieldType) {
      case 'TEXTAREA':
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <Input.TextArea rows={2} disabled={readOnly} placeholder={`请填写${f.fieldName}`} />
          </Form.Item>
        );
      case 'DATE':
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <DatePicker style={{ width: '100%' }} disabled={readOnly} />
          </Form.Item>
        );
      case 'SELECT':
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <Select
              allowClear
              disabled={readOnly}
              placeholder={`请选择${f.fieldName}`}
              options={(f.options ?? []).map((o) => ({ value: o, label: o }))}
            />
          </Form.Item>
        );
      case 'CONCLUSION':
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <Select
              allowClear
              disabled={readOnly}
              placeholder="请选择结论"
              options={CONCLUSION_OPTIONS.map((o) => ({ value: o, label: o }))}
            />
          </Form.Item>
        );
      case 'FILE':
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <AttachmentUpload
              bizType="LEDGER_NODE"
              bizId={node?.id}
              readOnly={readOnly}
              accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
            />
          </Form.Item>
        );
      case 'TEXT':
      default:
        return (
          <Form.Item key={f.fieldCode} name={name} label={label} rules={rules}>
            <Input disabled={readOnly} placeholder={`请填写${f.fieldName}`} />
          </Form.Item>
        );
    }
  };

  const saveMutation = useMutation({
    mutationFn: async (values: Record<string, unknown>) => {
      if (!node) {
        return;
      }
      const fieldValues: Record<string, string> = {};
      (node.fieldDefs ?? []).forEach((f) => {
        const v = values[`field_${f.fieldCode}`];
        // DatePicker 给出 dayjs 对象，统一转字符串
        const normalized = v && typeof v === 'object' && 'format' in (v as object)
          ? (v as { format: (p: string) => string }).format('YYYY-MM-DD')
          : v === undefined || v === null ? '' : String(v);
        if (normalized !== '') {
          fieldValues[f.fieldCode] = normalized;
        }
      });
      const payload: NodeRecordUpsertRequest = {
        status: values.status as FlowNodeStatus,
        finishDate: values.finishDate
          ? (values.finishDate as { format: (p: string) => string }).format('YYYY-MM-DD')
          : undefined,
        ownerName: values.ownerName as string | undefined,
        conclusion: values.conclusion as string | undefined,
        supplierId: values.supplierId as LongId | undefined,
        remark: values.remark as string | undefined,
        fieldValues,
      };
      await ledgerApi.nodeUpdate(goodsId, node.nodeCode, payload);
    },
    onSuccess: () => {
      message.success('节点记录已保存');
      onSaved();
      onClose();
    },
  });

  const handleOk = async () => {
    const values = await form.validateFields();
    saveMutation.mutate(values);
  };

  return (
    <Drawer
      title={node ? `节点办理 · ${node.nodeName}` : ''}
      width={520}
      open={!!node}
      onClose={onClose}
      destroyOnHidden
      extra={
        readOnly ? undefined : (
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={saveMutation.isPending} onClick={handleOk}>
              保存记录
            </Button>
          </Space>
        )
      }
    >
      {node && (
        <Form
          key={node.id}
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={initialValues}
        >
          {readOnly && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="当前账号只有查看权限，节点信息不可编辑"
            />
          )}
          <Form.Item name="status" label="节点状态" rules={[{ required: true }]}>
            <Select
              options={NODE_STATUS_OPTIONS}
              disabled={readOnly}
              onChange={handleStatusChange}
            />
          </Form.Item>
          <Form.Item
            name="finishDate"
            label="完成日期"
            rules={[{ required: finished && !readOnly, message: '请选择完成日期' }]}
            extra="状态为已完成/不通过时必填"
          >
            <DatePicker style={{ width: '100%' }} disabled={readOnly} />
          </Form.Item>
          <Form.Item name="ownerName" label="负责人">
            <Input disabled={readOnly} placeholder="请输入负责人姓名" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="conclusion"
            label="节点结论"
            rules={[{ required: finished && !readOnly, message: '请填写节点结论' }]}
            extra="状态为已完成/不通过时必填，如：通过、限期整改后通过"
          >
            <Input disabled={readOnly} maxLength={255} placeholder="请填写结论" />
          </Form.Item>
          {node.linkSupplier === 1 && (
            <Form.Item name="supplierId" label="关联供应商">
              <Select
                allowClear
                showSearch
                disabled={readOnly}
                optionFilterProp="label"
                placeholder="选择准入/验厂对应的供应商"
                options={supplierOptions.map((s) => ({
                  value: s.id,
                  label: `${s.supplierName}（${s.supplierCode}）`,
                }))}
              />
            </Form.Item>
          )}
          {node.fieldDefs?.map(renderFieldInput)}
          <Form.Item label="节点附件">
            <AttachmentUpload
              bizType="LEDGER_NODE"
              bizId={node.id}
              readOnly={readOnly}
              accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} disabled={readOnly} maxLength={1000} placeholder="选填" />
          </Form.Item>
        </Form>
      )}
    </Drawer>
  );
};

/** 资料齐套状态维护弹窗：状态 + 备注 + 佐证附件 */
const MaterialEditModal: React.FC<{
  goodsId: LongId;
  material: MaterialVO | null;
  onClose: () => void;
  onSaved: () => void;
}> = ({ goodsId, material, onClose, onSaved }) => {
  const [status, setStatus] = React.useState<MaterialStatus>('MISSING');
  const [remark, setRemark] = React.useState('');

  React.useEffect(() => {
    if (material) {
      setStatus(material.status);
      setRemark(material.remark ?? '');
    }
  }, [material]);

  const saveMutation = useMutation({
    mutationFn: () =>
      ledgerApi.materialUpdate(goodsId, material!.id, { status, remark }),
    onSuccess: () => {
      message.success('资料状态已更新');
      onSaved();
      onClose();
    },
  });

  return (
    <Modal
      title={material ? `资料维护 · ${material.itemName}` : ''}
      open={!!material}
      onCancel={onClose}
      onOk={() => saveMutation.mutate()}
      confirmLoading={saveMutation.isPending}
      okText="保存"
      destroyOnHidden
    >
      {material && (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Radio.Group
            value={status}
            onChange={(e) => setStatus(e.target.value as MaterialStatus)}
            optionType="button"
            buttonStyle="solid"
            options={MATERIAL_STATUS_OPTIONS}
          />
          <Input.TextArea
            rows={2}
            value={remark}
            maxLength={500}
            onChange={(e) => setRemark(e.target.value)}
            placeholder="备注（选填，如缺件原因、预计补齐时间）"
          />
          <div>
            <div style={{ marginBottom: 4, color: '#666' }}>佐证附件</div>
            <AttachmentUpload
              bizType="LEDGER_MATERIAL"
              bizId={material.id}
              accept="image/*,.pdf,.doc,.docx,.xls,.xlsx"
            />
          </div>
        </Space>
      )}
    </Modal>
  );
};

const GoodsDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const goodsId = id as LongId;
  const queryClient = useQueryClient();
  const canEdit = useAuthStore((s) => s.hasPerm('ledger:goods:edit'));

  const detailQuery = useQuery({
    queryKey: ['ledger-goods-detail', goodsId],
    queryFn: () => ledgerApi.goodsDetail(goodsId),
  });
  const supplierOptionsQuery = useQuery({
    queryKey: ['ledger-supplier-options'],
    queryFn: () => ledgerApi.supplierOptions(),
    enabled: canEdit,
  });
  const goods = detailQuery.data;
  const cooperateMeta = goods
    ? COOPERATE_META[goods.cooperateResult ?? 'PENDING']
    : undefined;

  const [activeNode, setActiveNode] = React.useState<FlowNodeVO | null>(null);
  const [activeMaterial, setActiveMaterial] = React.useState<MaterialVO | null>(null);

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['ledger-goods-detail', goodsId] });

  const materialColumns: ColumnsType<GoodsDetailVO['materials'][number]> = [
    { title: '资料项', dataIndex: 'itemName',
      render: (v: string, row) => (
        <Space size={4}>
          {v}
          {row.enabled === false && <Tag>已停用</Tag>}
        </Space>
      ) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: MaterialStatus) => {
        const meta = MATERIAL_META[v];
        return <Tag color={meta?.color}>{meta?.label ?? v}</Tag>;
      },
    },
    { title: '备注', dataIndex: 'remark', render: (v?: string) => v ?? '-' },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <AuthButton
          type="link"
          size="small"
          perm="ledger:goods:edit"
          onClick={() => setActiveMaterial(row)}
        >
          维护
        </AuthButton>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button onClick={() => navigate('/ledger/goods')}>返回台账</Button>
        {goods && (
          <Space size={8}>
            <span style={{ fontSize: 16, fontWeight: 600 }}>
              {goods.sku} · {goods.commonName}
            </span>
            <Tag color={cooperateMeta?.color}>
              {cooperateMeta?.label}
            </Tag>
            {goods.dataSource === 'DEMO' && <Tag color="purple">演示数据</Tag>}
          </Space>
        )}
      </Space>

      {goods?.templateOutdated && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={`该商品绑定的流程模板已更新（当前 v${goods.templateVersion}，最新 v${goods.latestTemplateVersion}）。模板改动不强制追溯，本商品仍按建档时的节点版本展示与录入。`}
        />
      )}

      <Card size="small" title="基础信息" loading={detailQuery.isLoading}
        style={{ marginBottom: 12 }}>
        {goods && (
          <Descriptions column={3} size="small" bordered>
            <Descriptions.Item label="SKU">{goods.sku}</Descriptions.Item>
            <Descriptions.Item label="产品通用名">{goods.commonName}</Descriptions.Item>
            <Descriptions.Item label="规格">{goods.spec ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="品牌">{goods.brand ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="生产企业" span={2}>{goods.manufacturer ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="批准文号/备案号">{goods.approvalNo ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="UPC(69码)">{goods.upc ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="品类">
              {[goods.categoryL1Name, goods.categoryL2Name].filter(Boolean).join(' / ') || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="过会时间">{goods.meetingDate ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="最终合作结论">
              <Tag color={cooperateMeta?.color}>{cooperateMeta?.label}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="上市日期">{goods.launchDate ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="流程模板" span={3}>
              {goods.templateName ?? '-'}（v{goods.templateVersion}）
            </Descriptions.Item>
            <Descriptions.Item label="备注" span={3}>{goods.remark ?? '-'}</Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Card size="small"
        title={
          <Space>
            <span>全流程线</span>
            {goods?.currentNodeName
              ? <Tag color="blue">当前节点：{goods.currentNodeName}</Tag>
              : goods && goods.nodes?.length > 0
                ? <Tag color="green">流程已完结</Tag>
                : null}
            <span style={{ fontSize: 12, color: '#999', fontWeight: 400 }}>
              点击节点可{canEdit ? '查看/录入' : '查看'}办理信息
            </span>
          </Space>
        }
        style={{ marginBottom: 12 }}>
        <Steps
          size="small"
          current={Math.max(0, goods?.nodes.findIndex(
            (n) => n.nodeCode === goods.currentNodeCode,
          ) ?? 0)}
          items={(goods?.nodes ?? []).map((n) => {
            const meta = NODE_STATUS_META[n.status];
            // antd 中若给每项显式 status 会覆盖 current 进度定位：
            // 当前节点尚未开始时，按“进行中”高亮，其余未开始节点置灰。
            const isCurrent = n.nodeCode === goods?.currentNodeCode;
            const stepStatus =
              isCurrent && n.status === 'NOT_STARTED' ? 'process' : meta.status;
            // 用真实元素承载点击，避免依赖 Steps item 的非公开事件透传
            const open = () => setActiveNode(n);
            return {
              title: (
                <span style={{ cursor: 'pointer' }} onClick={open}>{n.nodeName}</span>
              ),
              status: stepStatus,
              description: (
                <Space direction="vertical" size={0} style={{ cursor: 'pointer' }} onClick={open}>
                  <span>
                    {isCurrent && n.status === 'NOT_STARTED' ? '进行中（当前）' : meta.label}
                  </span>
                  {n.ownerName && <span style={{ fontSize: 12 }}>负责人：{n.ownerName}</span>}
                  {n.finishDate && <span style={{ fontSize: 12 }}>{n.finishDate}</span>}
                </Space>
              ),
            };
          })}
        />
      </Card>

      <Card size="small"
        title={
          <Space>
            <span>资料清单</span>
            {goods && goods.gapCount > 0
              ? <Tag color="orange">{goods.gapCount} 项重点缺口</Tag>
              : <Tag color="green">齐套</Tag>}
          </Space>
        }>
        <Table
          rowKey="id"
          size="small"
          loading={detailQuery.isLoading}
          columns={materialColumns}
          dataSource={goods?.materials ?? []}
          pagination={false}
        />
      </Card>

      <NodeRecordDrawer
        goodsId={goodsId}
        node={activeNode}
        readOnly={!canEdit}
        supplierOptions={supplierOptionsQuery.data ?? []}
        onClose={() => setActiveNode(null)}
        onSaved={invalidate}
      />
      <MaterialEditModal
        goodsId={goodsId}
        material={activeMaterial}
        onClose={() => setActiveMaterial(null)}
        onSaved={invalidate}
      />
    </div>
  );
};

export default GoodsDetail;
