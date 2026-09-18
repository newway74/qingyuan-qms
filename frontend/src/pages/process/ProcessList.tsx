import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Timeline } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import { processApi, ProcessQuery } from '@/api/process';
import { CALENDAR_TYPES, CONFIG_STATUS, PROCESS_NODES, PROCESS_ROLES } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { ProcessChangeLog, ProcessDef, ProcessNode, ProcessNodeInput } from '@/types/process';
import message from '@/utils/feedback';

const cfgMeta = (v: string) => CONFIG_STATUS.find((s) => s.value === v);
const NODE_LABEL: Record<string, string> = Object.fromEntries(PROCESS_NODES.map((n) => [n.value, n.label]));
const ROLE_LABEL: Record<string, string> = Object.fromEntries(PROCESS_ROLES.map((r) => [r.value, r.label]));

const ProcessList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<ProcessQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['process-defs', query], queryFn: () => processApi.page(query) });

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProcessDef | null>(null);
  const [form] = Form.useForm<{ processCode?: string; processName: string; remark?: string }>();
  const [drawerId, setDrawerId] = useState<LongId | null>(null);
  const [changelogOf, setChangelogOf] = useState<ProcessDef | null>(null);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['process-defs'] });
    void queryClient.invalidateQueries({ queryKey: ['process-detail'] });
  };

  const saveMutation = useMutation({
    mutationFn: async (v: { processCode?: string; processName: string; remark?: string }) => {
      if (editing) await processApi.update({ id: editing.id, processName: v.processName, remark: v.remark });
      else return processApi.create({ processCode: v.processCode!, processName: v.processName, remark: v.remark });
    },
    onSuccess: (newId) => {
      message.success(editing ? '已更新' : '草稿流程已创建，请编排九节点');
      setModalOpen(false);
      invalidate();
      if (newId) setDrawerId(newId);
    },
  });

  const publishMutation = useMutation({
    mutationFn: (id: LongId) => processApi.publish(id),
    onSuccess: () => {
      message.success('发布成功：九节点校验通过，旧发布版已归档并生成变更记录');
      invalidate();
    },
  });

  const reviseMutation = useMutation({
    mutationFn: (id: LongId) => processApi.revise(id),
    onSuccess: (newId) => {
      message.success('已创建修订草稿（节点已复制，版本号 +1）');
      invalidate();
      setDrawerId(newId);
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const columns: ColumnsType<ProcessDef> = [
    { title: '流程编码', dataIndex: 'processCode', width: 160 },
    { title: '流程名称', dataIndex: 'processName' },
    { title: '版本', dataIndex: 'version', width: 80, render: (v: number) => `v${v}` },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={cfgMeta(v)?.color}>{cfgMeta(v)?.label || v}</Tag>,
    },
    { title: '发布时间', dataIndex: 'publishedAt', width: 180 },
    {
      title: '操作',
      key: 'ops',
      width: 300,
      render: (_, r) => (
        <Space wrap>
          <Button type="link" size="small" onClick={() => setDrawerId(r.id)}>
            {r.status === 'DRAFT' ? '编排节点' : '查看节点'}
          </Button>
          {r.status === 'DRAFT' && (
            <>
              <AuthButton type="link" size="small" perm="process:def:edit" onClick={() => { setEditing(r); form.setFieldsValue({ processName: r.processName, remark: r.remark }); setModalOpen(true); }}>
                编辑
              </AuthButton>
              <Popconfirm
                title="发布该流程？"
                description="九节点必须齐全不重复，发布后不可再修改。"
                onConfirm={() => publishMutation.mutate(r.id)}
              >
                <AuthButton type="link" size="small" perm="process:def:publish">发布</AuthButton>
              </Popconfirm>
            </>
          )}
          {r.status === 'PUBLISHED' && (
            <AuthButton type="link" size="small" perm="process:def:publish" onClick={() => reviseMutation.mutate(r.id)}>
              修订
            </AuthButton>
          )}
          <Button type="link" size="small" onClick={() => setChangelogOf(r)}>
            变更记录
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="品控流程配置"
      extra={
        <AuthButton type="primary" perm="process:def:edit" onClick={openCreate}>
          新增流程草稿
        </AuthButton>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="流程固定九个环节：抽样→收样→分配→检验→复核→判定→不合格处置→复检→归档。草稿可编排责任角色、SLA 时效、必填项；发布生成新版本，差异自动写入只增变更记录。"
      />
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="processName" label="名称">
          <Input allowClear placeholder="流程名称" />
        </Form.Item>
        <Form.Item name="processCode" label="编码">
          <Input allowClear placeholder="流程编码" />
        </Form.Item>
        <Form.Item name="status" label="状态">
          <Select allowClear style={{ width: 110 }} placeholder="全部" options={CONFIG_STATUS} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { searchForm.resetFields(); setQuery({ pageNo: 1, pageSize: 10 }); }}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table<ProcessDef>
        rowKey="id"
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
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
        title={editing ? '编辑流程草稿' : '新增流程草稿'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        // forceRender：编辑按钮内联 setFieldsValue 后才打开弹窗
        forceRender
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => saveMutation.mutate(v)}
        >
          {!editing && (
            <Form.Item
              name="processCode"
              label="流程编码（同编码多版本，编码永久不可复用）"
              rules={[{ required: true }, { pattern: /^[A-Za-z0-9_-]+$/, message: '仅字母数字下划线短横线' }]}
            >
              <Input maxLength={50} placeholder="如 QC_DEFAULT" />
            </Form.Item>
          )}
          <Form.Item name="processName" label="流程名称" rules={[{ required: true }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <ProcessDrawer
        id={drawerId}
        onClose={() => setDrawerId(null)}
        onChanged={invalidate}
        onPublish={(id) => publishMutation.mutate(id)}
      />

      <ChangelogModal def={changelogOf} onClose={() => setChangelogOf(null)} />
    </Card>
  );
};

const ProcessDrawer: React.FC<{
  id: LongId | null;
  onClose: () => void;
  onChanged: () => void;
  onPublish: (id: LongId) => void;
}> = ({ id, onClose, onChanged, onPublish }) => {
  const detail = useQuery({
    queryKey: ['process-detail', id],
    queryFn: () => processApi.detail(id as LongId),
    enabled: id !== null,
  });
  const effective = useQuery({
    queryKey: ['process-effective'],
    queryFn: () => processApi.effective(),
    // 仅新草稿（无节点）需要从生效版初始化
    enabled: false,
  });

  const editable = detail.data?.processDef.status === 'DRAFT';
  const [draft, setDraft] = useState<ProcessNodeInput[]>([]);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      if (!detail.data) return;
      if (detail.data.nodes.length > 0) {
        if (!cancelled) {
          setDraft(toInputs(detail.data.nodes));
          setDirty(false);
        }
        return;
      }
      // 新草稿：自动从当前生效流程复制九节点（含流转规则）
      const eff = await effective.refetch();
      if (!cancelled && eff.data) {
        setDraft(toInputs(eff.data.nodes));
        setDirty(true);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail.data]);

  const updateRow = (idx: number, patch: Partial<ProcessNodeInput>) => {
    setDraft((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
    setDirty(true);
  };

  const saveNodes = useMutation({
    mutationFn: () => processApi.saveNodes(id as LongId, draft),
    onSuccess: () => {
      message.success('节点编排已保存');
      setDirty(false);
      onChanged();
    },
  });

  const columns: ColumnsType<ProcessNodeInput> = [
    {
      title: '序号',
      width: 56,
      render: (_, __, idx) => idx + 1,
    },
    {
      title: '节点编码（固定）',
      dataIndex: 'nodeCode',
      width: 120,
      render: (v: string) => <Tag>{NODE_LABEL[v] || v}</Tag>,
    },
    {
      title: '节点名称',
      dataIndex: 'nodeName',
      width: 200,
      render: (v: string, __, idx) =>
        editable ? (
          <Input size="small" value={v} onChange={(e) => updateRow(idx, { nodeName: e.target.value })} />
        ) : (
          v
        ),
    },
    {
      title: '责任角色',
      dataIndex: 'responsibleRole',
      width: 220,
      render: (v: string, __, idx) =>
        editable ? (
          <Select
            size="small"
            style={{ width: 210 }}
            value={v}
            options={PROCESS_ROLES}
            onChange={(role) => updateRow(idx, { responsibleRole: role })}
          />
        ) : (
          ROLE_LABEL[v] || v
        ),
    },
    {
      title: 'SLA(小时)',
      dataIndex: 'slaHours',
      width: 110,
      render: (v: number, __, idx) =>
        editable ? (
          <InputNumber
            size="small"
            min={1}
            max={24 * 365}
            style={{ width: 96 }}
            value={v}
            onChange={(h) => updateRow(idx, { slaHours: h ?? 24 })}
          />
        ) : (
          v
        ),
    },
    {
      title: '日历',
      dataIndex: 'calendarType',
      width: 110,
      render: (v: string, __, idx) =>
        editable ? (
          <Select
            size="small"
            style={{ width: 100 }}
            value={v || 'NATURAL'}
            options={CALENDAR_TYPES}
            onChange={(c) => updateRow(idx, { calendarType: c })}
          />
        ) : (
          CALENDAR_TYPES.find((c) => c.value === (v || 'NATURAL'))?.label
        ),
    },
    {
      title: '必填字段（流转校验）',
      dataIndex: 'requiredFields',
      render: (v: string[] | undefined, __, idx) =>
        editable ? (
          <Select
            mode="tags"
            size="small"
            style={{ minWidth: 220 }}
            value={v || []}
            placeholder="输入字段名后回车"
            tokenSeparators={[',']}
            onChange={(fields) => updateRow(idx, { requiredFields: fields })}
          />
        ) : (
          <Space size={4} wrap>
            {(v || []).map((f) => (
              <Tag key={f}>{f}</Tag>
            ))}
          </Space>
        ),
    },
  ];

  const d = detail.data?.processDef;

  return (
    <Drawer
      title={d ? `${d.processName} v${d.version}` : '流程编排'}
      width={1180}
      open={id !== null}
      onClose={onClose}
      loading={detail.isLoading}
      destroyOnHidden
      extra={
        editable ? (
          <Space>
            {dirty && <Tag color="orange">有未保存修改</Tag>}
            <AuthButton
              type="primary"
              size="small"
              perm="process:node:edit"
              loading={saveNodes.isPending}
              onClick={() => saveNodes.mutate()}
            >
              保存节点编排
            </AuthButton>
            <Popconfirm
              title="发布该流程？"
              description="九节点必须齐全不重复；发布后此版本冻结，并生成与上一版本的差异记录。"
              onConfirm={() => onPublish(d!.id)}
            >
              <AuthButton size="small" perm="process:def:publish">发布版本</AuthButton>
            </Popconfirm>
          </Space>
        ) : (
          <Tag color={d ? cfgMeta(d.status)?.color : undefined}>
            {d ? cfgMeta(d.status)?.label : ''}（只读）
          </Tag>
        )
      }
    >
      {d && (
        <>
          {editable && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="草稿已从当前生效流程预填九节点。节点编码不可增删改；请配置节点名称、责任角色、SLA 与必填字段，保存后再发布。"
            />
          )}
          <Table<ProcessNodeInput>
            size="small"
            rowKey={(r) => r.nodeCode}
            pagination={false}
            columns={columns}
            dataSource={editable ? draft : toInputs(detail.data!.nodes)}
            scroll={{ x: 1050 }}
          />
        </>
      )}
    </Drawer>
  );
};

function toInputs(nodes: ProcessNode[]): ProcessNodeInput[] {
  return [...nodes]
    .sort((a, b) => a.sort - b.sort)
    .map((n) => ({
      nodeCode: n.nodeCode,
      nodeName: n.nodeName,
      responsibleRole: n.responsibleRole,
      slaHours: n.slaHours,
      calendarType: n.calendarType,
      requiredFields: n.requiredFields || [],
      transitionRules: n.transitionRules || undefined,
      sort: n.sort,
    }));
}

const ChangelogModal: React.FC<{ def: ProcessDef | null; onClose: () => void }> = ({ def, onClose }) => {
  const logs = useQuery({
    queryKey: ['process-changelog', def?.id],
    queryFn: () => processApi.changelog(def!.id),
    enabled: def !== null,
  });

  return (
    <Modal title={`变更记录：${def?.processCode || ''}`} open={def !== null} onCancel={onClose} footer={null} width={780}>
      <Timeline
        items={(logs.data || []).map((log: ProcessChangeLog) => ({
          color: log.fromVersion ? 'blue' : 'green',
          children: (
            <div>
              <Space>
                <Tag color={log.fromVersion ? 'blue' : 'green'}>
                  {log.fromVersion ? `v${log.fromVersion} → v${log.toVersion}` : `首版发布 v${log.toVersion}`}
                </Tag>
                <span style={{ color: '#888' }}>{log.changedAt}</span>
              </Space>
              <div style={{ marginTop: 6 }}>
                {log.changeDiff?.added?.length ? (
                  <p>
                    <Tag color="green">新增节点</Tag>
                    {log.changeDiff.added.map((c) => NODE_LABEL[c] || c).join('、')}
                  </p>
                ) : null}
                {log.changeDiff?.removed?.length ? (
                  <p>
                    <Tag color="red">删除节点</Tag>
                    {log.changeDiff.removed.map((c) => NODE_LABEL[c] || c).join('、')}
                  </p>
                ) : null}
                {log.changeDiff?.changed?.length ? (
                  <div>
                    <Tag color="orange">节点变更</Tag>
                    <ul style={{ marginTop: 4, paddingLeft: 20 }}>
                      {log.changeDiff.changed.map((c, i) => (
                        <li key={i}>
                          <b>{NODE_LABEL[c.nodeCode] || c.nodeCode}</b>
                          <span style={{ color: '#888' }}>
                            {' '}
                            角色 {fmt(c.before?.responsibleRole)} → {fmt(c.after?.responsibleRole)}；
                            SLA {fmt(c.before?.slaHours)} → {fmt(c.after?.slaHours)}h；
                            必填 {fmtList(c.before?.requiredFields)} → {fmtList(c.after?.requiredFields)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                {!log.changeDiff?.added?.length &&
                  !log.changeDiff?.removed?.length &&
                  !log.changeDiff?.changed?.length && <span style={{ color: '#888' }}>无字段差异（首版）</span>}
              </div>
            </div>
          ),
        }))}
      />
      {logs.isLoading && <p style={{ color: '#888' }}>加载中…</p>}
    </Modal>
  );
};

function fmt(v: unknown): string {
  if (v === undefined || v === null) return '-';
  return String(v);
}

function fmtList(v: unknown): string {
  if (!Array.isArray(v) || v.length === 0) return '无';
  return v.map(String).join('/');
}

export default ProcessList;
