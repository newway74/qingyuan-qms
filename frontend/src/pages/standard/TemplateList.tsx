import React, { useMemo, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import AuthButton from '@/components/AuthButton';
import CategoryTreeSelect from '@/components/CategoryTreeSelect';
import { templateApi, TemplateQuery } from '@/api/template';
import { CONFIG_STATUS, DEFECT_LEVELS, ITEM_GROUPS, PACKAGE_FORMS, RESULT_TYPES } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { StandardItem, StandardItemInput, StandardTemplate, TemplateDetail, TemplateUpsert } from '@/types/standard';
import message from '@/utils/feedback';

const cfgMeta = (v: string) => CONFIG_STATUS.find((s) => s.value === v);
const groupLabel = (v: string) => ITEM_GROUPS.find((g) => g.value === v)?.label || v;
const levelMeta = (v: string) => DEFECT_LEVELS.find((d) => d.value === v);

const TemplateList: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<TemplateQuery>({ pageNo: 1, pageSize: 10 });
  const [searchForm] = Form.useForm();
  const pageQuery = useQuery({ queryKey: ['templates', query], queryFn: () => templateApi.page(query) });

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<StandardTemplate | null>(null);
  const [form] = Form.useForm<TemplateUpsert>();
  const [drawerId, setDrawerId] = useState<LongId | null>(null);
  const [versionOf, setVersionOf] = useState<StandardTemplate | null>(null);
  const [copyOf, setCopyOf] = useState<StandardTemplate | null>(null);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['templates'] });
    void queryClient.invalidateQueries({ queryKey: ['template-detail'] });
  };

  const saveMutation = useMutation({
    mutationFn: async (v: TemplateUpsert) => {
      if (editing) await templateApi.update({ ...v, id: editing.id });
      else await templateApi.create(v);
    },
    onSuccess: () => {
      message.success('草稿已保存');
      setModalOpen(false);
      invalidate();
    },
  });

  const publishMutation = useMutation({
    mutationFn: (id: LongId) => templateApi.publish(id),
    onSuccess: () => {
      message.success('发布成功：旧发布版已自动归档');
      invalidate();
    },
  });

  const reviseMutation = useMutation({
    mutationFn: (id: LongId) => templateApi.revise(id),
    onSuccess: (newId) => {
      message.success('已基于最新发布版创建修订草稿（版本号 +1，检验项已复制）');
      invalidate();
      setDrawerId(newId);
    },
  });

  const copyMutation = useMutation({
    mutationFn: async (v: { code: string; name: string }) => {
      if (copyOf) return templateApi.copy(copyOf.id, { templateCode: v.code, templateName: v.name });
    },
    onSuccess: (newId) => {
      message.success('已复制为新编码草稿');
      setCopyOf(null);
      invalidate();
      if (newId) setDrawerId(newId);
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ maxAFail: 0, maxBFail: 2, maxCFail: 3, concessionAllowed: 0 });
    setModalOpen(true);
  };

  const openEdit = (r: StandardTemplate) => {
    setEditing(r);
    form.setFieldsValue({
      id: r.id,
      templateCode: r.templateCode,
      templateName: r.templateName,
      categoryId: r.categoryId,
      packageForm: r.packageForm,
      maxAFail: r.maxAFail,
      maxBFail: r.maxBFail,
      maxCFail: r.maxCFail,
      concessionAllowed: r.concessionAllowed,
      remark: r.remark,
    });
    setModalOpen(true);
  };

  const columns = [
    { title: '模板编码', dataIndex: 'templateCode', width: 130 },
    { title: '模板名称', dataIndex: 'templateName' },
    { title: '适用包装', dataIndex: 'packageForm', width: 90, render: (v: string) => v || <Tag>通用</Tag> },
    { title: '版本', dataIndex: 'version', width: 70, render: (v: number) => `v${v}` },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag color={cfgMeta(v)?.color}>{cfgMeta(v)?.label || v}</Tag>,
    },
    { title: 'A阈值', dataIndex: 'maxAFail', width: 70 },
    { title: 'B阈值', dataIndex: 'maxBFail', width: 70 },
    { title: 'C阈值', dataIndex: 'maxCFail', width: 70 },
    { title: '让步', dataIndex: 'concessionAllowed', width: 70, render: (v: number) => (v ? '允许' : '否') },
    {
      title: '操作',
      key: 'ops',
      width: 300,
      render: (_: unknown, r: StandardTemplate) => (
        <Space wrap>
          <Button type="link" size="small" onClick={() => setDrawerId(r.id)}>
            {r.status === 'DRAFT' ? '编排检验项' : '查看'}
          </Button>
          {r.status === 'DRAFT' && (
            <>
              <AuthButton type="link" size="small" perm="std:template:edit" onClick={() => openEdit(r)}>
                编辑
              </AuthButton>
              <Popconfirm
                title="发布该草稿？"
                description="发布后此版本不可再修改，同编码旧发布版自动归档。"
                onConfirm={() => publishMutation.mutate(r.id)}
              >
                <AuthButton type="link" size="small" perm="std:template:publish">
                  发布
                </AuthButton>
              </Popconfirm>
            </>
          )}
          {r.status === 'PUBLISHED' && (
            <>
              <AuthButton type="link" size="small" perm="std:template:publish" onClick={() => reviseMutation.mutate(r.id)}>
                修订
              </AuthButton>
              <AuthButton type="link" size="small" perm="std:template:edit" onClick={() => setCopyOf(r)}>
                复制
              </AuthButton>
            </>
          )}
          <Button type="link" size="small" onClick={() => setVersionOf(r)}>
            版本
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="检验标准模板库"
      extra={
        <AuthButton type="primary" perm="std:template:edit" onClick={openCreate}>
          新增草稿模板
        </AuthButton>
      }
    >
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={(values) => setQuery({ ...query, ...values, pageNo: 1 })}
      >
        <Form.Item name="templateName" label="名称">
          <Input allowClear placeholder="模板名称" />
        </Form.Item>
        <Form.Item name="templateCode" label="编码">
          <Input allowClear placeholder="模板编码" />
        </Form.Item>
        <Form.Item name="categoryId" label="品类">
          <CategoryTreeSelect allowClear style={{ width: 160 }} placeholder="全部品类" />
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

      <Table<StandardTemplate>
        rowKey="id"
        loading={pageQuery.isLoading}
        columns={columns}
        dataSource={pageQuery.data?.records || []}
        scroll={{ x: 1150 }}
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
        title={editing ? '编辑草稿模板' : '新增草稿模板'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        forceRender
        width={640}
      >
        <Form form={form} layout="vertical" onFinish={(v) => saveMutation.mutate(v)}
          initialValues={{ maxAFail: 0, maxBFail: 2, maxCFail: 3, concessionAllowed: 0 }}
        >
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="templateCode" label="模板编码（同编码多版本，编码永久不可复用）" style={{ flex: 1 }}
              rules={[{ required: true }, { pattern: /^[A-Za-z0-9_-]+$/, message: '仅字母数字下划线短横线' }]}
            >
              <Input maxLength={50} disabled={!!editing} placeholder="如 TPL_TCM" />
            </Form.Item>
            <Form.Item name="templateName" label="模板名称" style={{ flex: 1 }} rules={[{ required: true }]}>
              <Input maxLength={100} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="categoryId" label="适用品类" style={{ flex: 1 }} rules={[{ required: true }]}>
              <CategoryTreeSelect placeholder="选择品类（没有合适品类可直接在下拉底部新建）" />
            </Form.Item>
            <Form.Item name="packageForm" label="包装形态（空=通用，精确形态优先匹配）" style={{ flex: 1 }}>
              <Select allowClear options={PACKAGE_FORMS.map((p) => ({ value: p, label: p }))} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="maxAFail" label="A类允许不合格数" style={{ flex: 1 }} tooltip="严重(A)默认0：出现即一票否决">
              <InputNumber min={0} max={99} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="maxBFail" label="B类允许数" style={{ flex: 1 }}>
              <InputNumber min={0} max={99} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="maxCFail" label="C类允许数" style={{ flex: 1 }}>
              <InputNumber min={0} max={99} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="concessionAllowed" label="是否允许让步接收" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 0, label: '不允许' },
                { value: 1, label: '允许（仍需质量主管审批）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <TemplateDrawer id={drawerId} onClose={() => setDrawerId(null)} onChanged={invalidate}
        onPublish={(id) => publishMutation.mutate(id)}
      />

      <VersionModal template={versionOf} onClose={() => setVersionOf(null)} onOpen={(id) => { setVersionOf(null); setDrawerId(id); }} />

      <Modal
        title={`复制模板：${copyOf?.templateName || ''}`}
        open={copyOf !== null}
        onCancel={() => setCopyOf(null)}
        destroyOnHidden
        footer={null}
      >
        <Form
          layout="vertical"
          onFinish={(v) => copyMutation.mutate(v)}
          initialValues={{ code: '', name: `${copyOf?.templateName || ''}-副本` }}
        >
          <Form.Item name="code" label="新模板编码" rules={[{ required: true }, { pattern: /^[A-Za-z0-9_-]+$/, message: '仅字母数字下划线短横线' }]}>
            <Input maxLength={50} placeholder="复制产生新编码的草稿，版本从 v1 开始" />
          </Form.Item>
          <Form.Item name="name" label="新模板名称" rules={[{ required: true }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={copyMutation.isPending}>确认复制</Button>
            <Button onClick={() => setCopyOf(null)}>取消</Button>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
};

type DraftRow = StandardItemInput & { __i: number };

export const TemplateDrawer: React.FC<{
  id: LongId | null;
  onClose: () => void;
  onChanged: () => void;
  onPublish?: (id: LongId) => void;
  /** 新品档位标准模式：隐藏直接发布，改走“提交评审” */
  gradeMode?: boolean;
}> = ({ id, onClose, onChanged, onPublish, gradeMode }) => {
  const detail = useQuery({
    queryKey: ['template-detail', id],
    queryFn: () => templateApi.detail(id as LongId),
    enabled: id !== null,
  });
  const [draft, setDraft] = useState<StandardItemInput[]>([]);
  const [dirty, setDirty] = useState(false);
  const editable = detail.data?.template.status === 'DRAFT';

  React.useEffect(() => {
    if (detail.data) {
      setDraft(
        detail.data.items.map((it) => ({
          groupCode: it.groupCode,
          itemName: it.itemName,
          inspectMethod: it.inspectMethod,
          resultType: it.resultType,
          defectLevel: it.defectLevel,
          vetoFlag: it.vetoFlag,
          requiredFlag: it.requiredFlag,
          minValue: it.minValue !== null && it.minValue !== undefined ? Number(it.minValue) : undefined,
          maxValue: it.maxValue !== null && it.maxValue !== undefined ? Number(it.maxValue) : undefined,
          nominalValue: it.nominalValue !== null && it.nominalValue !== undefined ? Number(it.nominalValue) : undefined,
          unit: it.unit,
          toleranceRule: it.toleranceRule,
          judgeConfig: it.judgeConfig || undefined,
          sort: it.sort,
        })),
      );
      setDirty(false);
    }
  }, [detail.data]);

  const saveItems = useMutation({
    mutationFn: () => templateApi.saveItems(id as LongId, draft),
    onSuccess: () => {
      message.success('检验项已整组替换保存（旧版本逻辑删除并审计留痕）');
      setDirty(false);
      onChanged();
    },
  });

  const submitReview = useMutation({
    mutationFn: () => templateApi.submitReview(id as LongId),
    onSuccess: () => {
      message.success('已提交评审：采购会签 → 老板批准');
      onChanged();
    },
  });

  const updateRow = (idx: number, patch: Partial<StandardItemInput>) => {
    setDraft((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
    setDirty(true);
  };

  const addRow = (groupCode: string) => {
    setDraft((prev) => [
      ...prev,
      {
        groupCode,
        itemName: '',
        resultType: groupCode === 'CERT_DOC' ? 'DOCUMENT' : groupCode === 'NET_CONTENT' ? 'QUANTITATIVE' : 'QUALITATIVE',
        defectLevel: groupCode === 'SENSORY' ? 'B' : 'C',
        vetoFlag: 0,
        requiredFlag: 1,
        toleranceRule: groupCode === 'NET_CONTENT' ? 'JJF1070' : undefined,
        sort: prev.length + 1,
      },
    ]);
    setDirty(true);
  };

  const removeRow = (idx: number) => {
    setDraft((prev) => prev.filter((_, i) => i !== idx));
    setDirty(true);
  };

  const editableColumns = (groupCode: string): ColumnsType<DraftRow> => [
    {
      title: '检验项名称',
      dataIndex: 'itemName',
      width: 220,
      render: (_, row) => (
        <Input size="small" value={draft[row.__i].itemName} placeholder="如 色泽 / 二氧化硫残留量"
          onChange={(e) => updateRow(row.__i, { itemName: e.target.value })} />
      ),
    },
    {
      title: '检验方法',
      dataIndex: 'inspectMethod',
      width: 170,
      render: (_, row) => (
        <Input size="small" value={draft[row.__i].inspectMethod} placeholder="GB标准/药典法/目测"
          onChange={(e) => updateRow(row.__i, { inspectMethod: e.target.value })} />
      ),
    },
    {
      title: '结果类型',
      dataIndex: 'resultType',
      width: 100,
      render: (_, row) => (
        <Select size="small" style={{ width: 92 }} value={draft[row.__i].resultType}
          options={RESULT_TYPES}
          onChange={(v) => updateRow(row.__i, { resultType: v, minValue: undefined, maxValue: undefined })} />
      ),
    },
    {
      title: '缺陷',
      dataIndex: 'defectLevel',
      width: 86,
      render: (_, row) => (
        <Select size="small" style={{ width: 78 }} value={draft[row.__i].defectLevel}
          options={DEFECT_LEVELS.map((d) => ({ value: d.value, label: d.value }))}
          onChange={(v) => updateRow(row.__i, { defectLevel: v })} />
      ),
    },
    {
      title: '一票否决',
      dataIndex: 'vetoFlag',
      width: 70,
      render: (_, row) => (
        <Switch size="small" checked={draft[row.__i].vetoFlag === 1}
          onChange={(c) => updateRow(row.__i, { vetoFlag: c ? 1 : 0 })} />
      ),
    },
    {
      title: '必检',
      dataIndex: 'requiredFlag',
      width: 60,
      render: (_, row) => (
        <Switch size="small" checked={draft[row.__i].requiredFlag === 1}
          onChange={(c) => updateRow(row.__i, { requiredFlag: c ? 1 : 0 })} />
      ),
    },
    {
      title: '下限/标准值',
      width: 110,
      render: (_, row) =>
        draft[row.__i].resultType === 'QUANTITATIVE' ? (
          <InputNumber size="small" style={{ width: 100 }} value={draft[row.__i].minValue as number}
            placeholder={groupCode === 'NET_CONTENT' ? '标示量' : 'min'}
            onChange={(v) => updateRow(row.__i, { minValue: v ?? undefined })} />
        ) : null,
    },
    {
      title: '上限',
      width: 90,
      render: (_, row) =>
        draft[row.__i].resultType === 'QUANTITATIVE' ? (
          <InputNumber size="small" style={{ width: 80 }} value={draft[row.__i].maxValue as number}
            onChange={(v) => updateRow(row.__i, { maxValue: v ?? undefined })} />
        ) : null,
    },
    {
      title: '单位/规则',
      width: 150,
      render: (_, row) => {
        const cur = draft[row.__i];
        if (cur.resultType !== 'QUANTITATIVE') {
          if (cur.resultType === 'QUALITATIVE') {
            const notDetect = cur.judgeConfig?.operator === 'NOT_DETECT';
            return (
              <Switch size="small" checked={!!notDetect} checkedChildren="不得检出" unCheckedChildren="定性"
                onChange={(c) => updateRow(row.__i, { judgeConfig: c ? { operator: 'NOT_DETECT' } : undefined })} />
            );
          }
          return <Tag>资料上传</Tag>;
        }
        return (
          <Space size={4}>
            <Input size="small" style={{ width: 56 }} value={cur.unit} placeholder="单位"
              onChange={(e) => updateRow(row.__i, { unit: e.target.value })} />
            {groupCode === 'NET_CONTENT' ? (
              <Tag color="blue">JJF1070</Tag>
            ) : (
              <Select size="small" style={{ width: 84 }} allowClear value={cur.toleranceRule}
                placeholder="无规则"
                onChange={(v) => updateRow(row.__i, { toleranceRule: v })}
                options={[{ value: 'JJF1070', label: 'JJF1070' }]} />
            )}
          </Space>
        );
      },
    },
    {
      title: '',
      width: 50,
      render: (_, row) => (
        <Button size="small" type="link" danger onClick={() => removeRow(row.__i)}>删</Button>
      ),
    },
  ];

  const readonlyColumns = [
    { title: '检验项', dataIndex: 'itemName' },
    { title: '方法', dataIndex: 'inspectMethod', width: 180 },
    { title: '类型', dataIndex: 'resultType', width: 80, render: (v: string) => RESULT_TYPES.find((r) => r.value === v)?.label },
    {
      title: '缺陷', dataIndex: 'defectLevel', width: 80,
      render: (v: string) => <Tag color={levelMeta(v)?.color}>{v}</Tag>,
    },
    { title: '否决', dataIndex: 'vetoFlag', width: 60, render: (v: number) => (v ? '是' : '') },
    { title: '必检', dataIndex: 'requiredFlag', width: 60, render: (v: number) => (v ? '是' : '') },
    {
      title: '限值/规则', width: 200,
      render: (_: unknown, r: StandardItem) =>
        r.resultType === 'QUANTITATIVE'
          ? `${r.minValue ?? '-'} ~ ${r.maxValue ?? '-'}${r.unit || ''}${r.toleranceRule ? ' / ' + r.toleranceRule : ''}`
          : r.judgeConfig?.operator === 'NOT_DETECT'
            ? '不得检出'
            : r.resultType === 'DOCUMENT' ? '资料上传' : '定性判定',
    },
  ];

  const t = detail.data?.template;

  return (
    <Drawer
      title={t ? `${t.templateName} v${t.version}` : '模板详情'}
      width={1200}
      open={id !== null}
      onClose={onClose}
      loading={detail.isLoading}
      destroyOnHidden
      extra={
        t?.status === 'DRAFT' ? (
          <Space>
            {dirty && <Tag color="orange">有未保存修改</Tag>}
            <AuthButton type="primary" size="small" perm="std:template:edit"
              onClick={() => saveItems.mutate()} loading={saveItems.isPending}>
              保存全部检验项
            </AuthButton>
            {gradeMode ? (
              <Popconfirm
                title="提交该档位标准评审？"
                description="提交后进入“采购会签 → 老板批准”流程，期间不可修改检验项。"
                onConfirm={() => submitReview.mutate()}
              >
                <AuthButton size="small" type="default" perm="std:template:review" loading={submitReview.isPending}>
                  提交评审
                </AuthButton>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="发布该草稿？"
                description="发布后该版本不可再修改；同编码旧发布版自动归档。至少1个检验项且限值合法才可发布。"
                onConfirm={() => onPublish?.(t.id)}
              >
                <AuthButton size="small" type="default" perm="std:template:publish">发布版本</AuthButton>
              </Popconfirm>
            )}
          </Space>
        ) : (
          <Tag color={t ? cfgMeta(t.status)?.color : undefined}>{t ? cfgMeta(t.status)?.label : ''}（只读）</Tag>
        )
      }
    >
      {t && (
        <>
          <Card size="small" style={{ marginBottom: 12 }}>
            <Space size="large" wrap>
              <span>编码：{t.templateCode}</span>
              <span>品类：{detail.data?.categoryName || t.categoryId}</span>
              <span>包装：{t.packageForm || '通用'}</span>
              <span>A≤{t.maxAFail} / B≤{t.maxBFail} / C≤{t.maxCFail}</span>
              <span>让步接收：{t.concessionAllowed ? '允许(需审批)' : '不允许'}</span>
              {t.publishedAt && <span>发布时间：{t.publishedAt}</span>}
            </Space>
            {gradeMode && (t.regulationBasis || t.marketBenchmark) && (
              <div style={{ marginTop: 8, color: '#555' }}>
                {t.regulationBasis && <div>国标/法规依据：{t.regulationBasis}</div>}
                {t.marketBenchmark && <div>竞品对标：{t.marketBenchmark}</div>}
              </div>
            )}
          </Card>
          <Tabs
            items={ITEM_GROUPS.map((g) => {
              const draftRows: DraftRow[] = draft
                .map((r, i) => ({ ...r, __i: i }))
                .filter((r) => r.groupCode === g.value);
              const readonlyRows: StandardItem[] = (detail.data?.items || []).filter(
                (r) => r.groupCode === g.value,
              );
              return {
                key: g.value,
                label: `${g.label}（${editable ? draftRows.length : readonlyRows.length}）`,
                children: (
                  <>
                    {editable && (
                      <div style={{ marginBottom: 8 }}>
                        <Button size="small" onClick={() => addRow(g.value)}>＋ 添加{g.label}检验项</Button>
                      </div>
                    )}
                    {editable ? (
                      <Table<DraftRow>
                        size="small"
                        rowKey={(r) => String(r.__i)}
                        pagination={false}
                        columns={editableColumns(g.value)}
                        dataSource={draftRows}
                        scroll={{ x: 1100 }}
                      />
                    ) : (
                      <Table<StandardItem>
                        size="small" rowKey={(r) => r.id as string} pagination={false}
                        columns={readonlyColumns} dataSource={readonlyRows}
                      />
                    )}
                  </>
                ),
              };
            })}
          />
        </>
      )}
    </Drawer>
  );
};

const VersionModal: React.FC<{
  template: StandardTemplate | null;
  onClose: () => void;
  onOpen: (id: LongId) => void;
}> = ({ template, onClose, onOpen }) => {
  const versions = useQuery({
    queryKey: ['template-versions', template?.id],
    queryFn: () => templateApi.versions(template!.id),
    enabled: template !== null,
  });
  return (
    <Modal title={`版本历史：${template?.templateCode || ''}`} open={template !== null} onCancel={onClose} footer={null} width={720}>
      <Table<StandardTemplate>
        rowKey="id"
        size="small"
        loading={versions.isLoading}
        pagination={false}
        dataSource={versions.data || []}
        columns={[
          { title: '版本', dataIndex: 'version', width: 80, render: (v: number) => `v${v}` },
          {
            title: '状态', dataIndex: 'status', width: 100,
            render: (v: string) => <Tag color={cfgMeta(v)?.color}>{cfgMeta(v)?.label}</Tag>,
          },
          { title: '包装', dataIndex: 'packageForm', width: 90, render: (v: string) => v || '通用' },
          { title: '发布人', dataIndex: 'publishedBy', width: 140 },
          { title: '发布时间', dataIndex: 'publishedAt' },
          {
            title: '操作', width: 90,
            render: (_: unknown, r: StandardTemplate) => (
              <Button type="link" size="small" onClick={() => onOpen(r.id)}>查看</Button>
            ),
          },
        ]}
      />
    </Modal>
  );
};

export default TemplateList;
