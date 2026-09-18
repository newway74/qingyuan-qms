import React, { useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Form, Input, Modal, Popconfirm, Radio, Row, Select, Space, Steps, Table, Tabs, Tag, Timeline, Tooltip } from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import { evalApi, extTestApi, npiApi } from '@/api/npi';
import { productApi } from '@/api/product';
import { templateApi } from '@/api/template';
import {
  AUDIT_CONCLUSION,
  AUDIT_STATUS,
  EVAL_DIMENSIONS,
  EVAL_QUALITY,
  EXT_CONCLUSION,
  EXT_STATUS,
  LAB_QUALIFICATIONS,
  NPI_STATUS,
  STD_GRADES,
  STD_GRADE_FULL,
} from '@/constants/qms';
import type { ColumnsType } from 'antd/es/table';
import type { LongId } from '@/types/auth';
import type { AuditDetail, EvalDetail, ExternalTest, GateStatus, ProjectDetail as Detail } from '@/types/npi';
import type { StdReview, StandardTemplate } from '@/types/standard';
import { TemplateDrawer } from '@/pages/standard/TemplateList';
import EvalDrawer from './components/EvalDrawer';
import AuditDrawer from './components/AuditDrawer';
import ExtTestDrawer from './components/ExtTestDrawer';
import message from '@/utils/feedback';

const STEP_ITEMS = [
  { title: '新品会立项' },
  { title: '标准评审' },
  { title: '寻源送样' },
  { title: '实地验厂' },
  { title: '量产' },
  { title: '国家检测' },
  { title: '上市放行' },
  { title: '已上市' },
];
const STEP_INDEX: Record<string, number> = {
  DRAFT: 0,
  STD_REVIEW: 1,
  SOURCING: 2,
  AUDIT: 3,
  PRODUCING: 4,
  EXT_TEST: 5,
  LISTING_REVIEW: 6,
  LISTED: 7,
};
const NODE_META: Record<string, { label: string; color: string }> = {
  SUBMIT: { label: '提交评审', color: 'default' },
  PROCUREMENT: { label: '采购会签', color: 'blue' },
  BOSS: { label: '老板批准', color: 'purple' },
};
const REVIEW_ACTION_META: Record<string, { label: string; color: string }> = {
  SUBMIT: { label: '提交', color: 'default' },
  PASS: { label: '通过', color: 'green' },
  REJECT: { label: '驳回', color: 'red' },
  COMMENT: { label: '意见', color: 'orange' },
};

const npiMeta = (v: string) => NPI_STATUS.find((s) => s.value === v);
const tplStatusColor: Record<string, string> = { DRAFT: 'default', REVIEWING: 'gold', PUBLISHED: 'green', ARCHIVED: 'default' };
const tplStatusLabel: Record<string, string> = { DRAFT: '草稿', REVIEWING: '评审中', PUBLISHED: '已发布', ARCHIVED: '已归档' };

const GateCard: React.FC<{ gates: GateStatus[] }> = ({ gates }) => (
  <Card size="small" title="放行闸门（全部通过才可上市放行）" style={{ marginBottom: 16 }}>
    <Row gutter={[12, 12]}>
      {gates.map((g) => (
        <Col key={g.name} span={8}>
          <Card
            size="small"
            style={{
              borderColor: g.ok ? '#b7eb8f' : '#ffccc7',
              background: g.ok ? '#f6ffed' : '#fff2f0',
            }}
          >
            <Space align="start">
              {g.ok ? (
                <CheckCircleFilled style={{ color: '#52c41a', fontSize: 18, marginTop: 2 }} />
              ) : (
                <CloseCircleFilled style={{ color: '#ff4d4f', fontSize: 18, marginTop: 2 }} />
              )}
              <div>
                <div style={{ fontWeight: 600 }}>{g.name}</div>
                <div style={{ color: '#666', fontSize: 12 }}>{g.detail}</div>
              </div>
            </Space>
          </Card>
        </Col>
      ))}
    </Row>
  </Card>
);

const ProjectDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const projectId = id as LongId;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const detailQuery = useQuery({
    queryKey: ['npi', 'detail', projectId],
    queryFn: () => npiApi.detail(projectId),
    enabled: !!id,
  });
  const d = detailQuery.data;

  const [templateDrawerId, setTemplateDrawerId] = useState<LongId | null>(null);
  const [genGrade, setGenGrade] = useState<string | null>(null);
  const [reviewCtx, setReviewCtx] = useState<{ templateId: LongId; node: 'PROCUREMENT' | 'BOSS' } | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [fixOpen, setFixOpen] = useState(false);
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [rejectStdOpen, setRejectStdOpen] = useState(false);
  const [terminateOpen, setTerminateOpen] = useState(false);
  const [evalDrawer, setEvalDrawer] = useState<{ id: LongId | null } | null>(null);
  const [auditDrawer, setAuditDrawer] = useState<{ id: LongId | null } | null>(null);
  const [extDrawer, setExtDrawer] = useState<{ id: LongId | null } | null>(null);
  const [genForm] = Form.useForm();
  const [reviewForm] = Form.useForm();
  const [approveForm] = Form.useForm();
  const [fixForm] = Form.useForm();
  const [releaseForm] = Form.useForm();
  const [rejectForm] = Form.useForm();
  const [terminateForm] = Form.useForm();

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['npi'] });

  const act = useMutation({
    mutationFn: ({ action, body }: { action: string; body?: Record<string, unknown> }) =>
      npiApi.act(projectId, action, body),
    onSuccess: () => {
      message.success('操作成功');
      invalidate();
    },
  });

  const generateMutation = useMutation({
    mutationFn: async () => {
      const v = await genForm.validateFields();
      return templateApi.generateGrade({
        projectId,
        grade: genGrade!,
        templateName: v.templateName,
        regulationBasis: v.regulationBasis,
        marketBenchmark: v.marketBenchmark,
      });
    },
    onSuccess: () => {
      message.success('档位标准草稿已生成（复制品类现行通用模板检验项），请细化指标后提交评审');
      setGenGrade(null);
      genForm.resetFields();
      invalidate();
    },
  });

  const reviewMutation = useMutation({
    mutationFn: async () => {
      const v = await reviewForm.validateFields();
      await templateApi.review(reviewCtx!.templateId, {
        node: reviewCtx!.node,
        pass: v.pass,
        comment: v.comment,
      });
    },
    onSuccess: () => {
      message.success('评审意见已记录');
      setReviewCtx(null);
      reviewForm.resetFields();
      invalidate();
    },
  });

  const productsQuery = useQuery({
    queryKey: ['product-options'],
    queryFn: () => productApi.page({ pageNo: 1, pageSize: 100 }),
    enabled: releaseOpen,
  });

  const currentStep = d ? STEP_INDEX[d.project.status] ?? 0 : 0;
  const terminated = d?.project.status === 'TERMINATED';
  const reviewPass = Form.useWatch('pass', reviewForm);

  const actionBar = useMemo(() => {
    if (!d) return null;
    const actions = d.allowedActions || [];
    const btn = (action: string, label: string, danger = false, type: 'primary' | 'default' = 'primary') => {
      const clickMap: Record<string, () => void> = {
        SUBMIT_STD: () => act.mutate({ action, body: {} }),
        APPROVE_STD: () => { approveForm.resetFields(); setApproveOpen(true); },
        REJECT_STD: () => { rejectForm.resetFields(); setRejectStdOpen(true); },
        FIX_SUPPLIER: () => { fixForm.resetFields(); setFixOpen(true); },
        AUDIT_PASS: () => act.mutate({ action, body: {} }),
        AUDIT_FAIL: () => act.mutate({ action, body: {} }),
        SEND_EXT: () => act.mutate({ action, body: {} }),
        TEST_PASS: () => act.mutate({ action, body: {} }),
        TEST_FAIL: () => act.mutate({ action, body: {} }),
        RELEASE: () => { releaseForm.resetFields(); setReleaseOpen(true); },
        TERMINATE: () => { terminateForm.resetFields(); setTerminateOpen(true); },
      };
      const direct = new Set(['SUBMIT_STD', 'AUDIT_PASS', 'AUDIT_FAIL', 'SEND_EXT', 'TEST_PASS', 'TEST_FAIL']);
      const confirmText: Record<string, string> = {
        SUBMIT_STD: '确认三档标准（高/中/低）已起草齐全，提交评审？',
        AUDIT_PASS: '确认验厂通过并进入量产阶段？',
        AUDIT_FAIL: '验厂不通过，项目退回寻源重新选点？',
        SEND_EXT: '确认已开始量产并寄样到国家认可检测机构？',
        TEST_PASS: '外检合格，进入上市放行审核？',
        TEST_FAIL: '外检不合格，退回量产整改后重新送检？',
      };
      if (direct.has(action)) {
        return (
          <Popconfirm key={action} title={confirmText[action]} onConfirm={() => act.mutate({ action, body: {} })}>
            <Button type={type} danger={danger} loading={act.isPending}>{label}</Button>
          </Popconfirm>
        );
      }
      return (
        <Button key={action} type={type} danger={danger} loading={act.isPending} onClick={clickMap[action]}>
          {label}
        </Button>
      );
    };
    const labelMap: Record<string, { label: string; danger?: boolean; type?: 'default' }> = {
      SUBMIT_STD: { label: '提交三档标准评审' },
      REJECT_STD: { label: '驳回标准（退回起草）', danger: true, type: 'default' },
      APPROVE_STD: { label: '评审通过并选定寻源档位' },
      FIX_SUPPLIER: { label: '定点供应商' },
      AUDIT_FAIL: { label: '验厂不通过（退回寻源）', danger: true, type: 'default' },
      AUDIT_PASS: { label: '验厂通过，进入量产' },
      SEND_EXT: { label: '量产完成，送外检' },
      TEST_FAIL: { label: '外检不合格（退回量产）', danger: true, type: 'default' },
      TEST_PASS: { label: '外检合格，送上市放行' },
      RELEASE: { label: '上市放行' },
      TERMINATE: { label: '终止项目', danger: true, type: 'default' },
    };
    return actions
      .filter((a) => labelMap[a])
      .map((a) => btn(a, labelMap[a].label!, labelMap[a].danger, labelMap[a].type as 'primary' | 'default'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [d, act.isPending]);

  if (detailQuery.isLoading) return <Card loading />;
  if (!d) return <Alert type="error" showIcon message="项目不存在或无权限查看" />;
  const p = d.project;

  const publishedGrades = STD_GRADES.filter((g) => d.gradeTemplates[g.value]?.status === 'PUBLISHED');

  // ---------- 三档标准 ----------
  const GradeCard = ({ grade }: { grade: { value: string; label: string; color: string } }) => {
    const t: StandardTemplate | undefined = d.gradeTemplates[grade.value];
    const reviews: StdReview[] = t ? d.templateReviews[String(t.id)] || [] : [];
    const procurementPass = reviews.some((r) => r.node === 'PROCUREMENT' && r.action === 'PASS');
    return (
      <Card
        size="small"
        title={<Space><Tag color={grade.color}>{grade.label}</Tag>{STD_GRADE_FULL[grade.value].label}</Space>}
        extra={
          p.targetGrade === grade.value && t?.id === p.selectedTemplateId ? (
            <Tag color="success">本项目选定档</Tag>
          ) : null
        }
        style={{ marginBottom: 12 }}
      >
        {!t ? (
          <Space direction="vertical">
            <span style={{ color: '#999' }}>尚未起草该档位标准</span>
            <AuthButton
              type="primary"
              ghost
              size="small"
              perm="std:template:create"
              onClick={() => { setGenGrade(grade.value); genForm.resetFields(); }}
            >
              生成档位标准草稿
            </AuthButton>
          </Space>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <Space wrap>
              <strong>{t.templateName}</strong>
              <span style={{ fontFamily: 'monospace', color: '#666' }}>{t.templateCode} v{t.version}</span>
              <Tag color={tplStatusColor[t.status]}>{tplStatusLabel[t.status] || t.status}</Tag>
            </Space>
            {t.regulationBasis && <div style={{ fontSize: 13 }}><b>国标/法规依据：</b>{t.regulationBasis}</div>}
            {t.marketBenchmark && <div style={{ fontSize: 13 }}><b>市面竞品对标：</b>{t.marketBenchmark}</div>}
            <Space wrap>
              <Button size="small" onClick={() => setTemplateDrawerId(t.id)}>
                {t.status === 'DRAFT' ? '编排检验项' : '查看检验项'}
              </Button>
              {t.status === 'REVIEWING' && (
                <>
                  <AuthButton
                    size="small"
                    perm="std:template:review"
                    disabled={procurementPass}
                    onClick={() => { reviewForm.setFieldsValue({ node: 'PROCUREMENT', pass: true }); setReviewCtx({ templateId: t.id, node: 'PROCUREMENT' }); }}
                  >
                    {procurementPass ? '采购已会签' : '采购会签通过'}
                  </AuthButton>
                  <Tooltip title={!procurementPass ? '需先完成采购会签' : undefined}>
                    <AuthButton
                      size="small"
                      type="primary"
                      perm="std:template:review"
                      disabled={!procurementPass}
                      onClick={() => { reviewForm.setFieldsValue({ node: 'BOSS', pass: true }); setReviewCtx({ templateId: t.id, node: 'BOSS' }); }}
                    >
                      老板批准发布
                    </AuthButton>
                  </Tooltip>
                  <AuthButton
                    size="small"
                    danger
                    perm="std:template:review"
                    onClick={() => { reviewForm.setFieldsValue({ node: 'BOSS', pass: false }); setReviewCtx({ templateId: t.id, node: 'BOSS' }); }}
                  >
                    老板驳回
                  </AuthButton>
                </>
              )}
            </Space>
            {reviews.length > 0 && (
              <div style={{ background: '#fafafa', padding: '6px 10px', borderRadius: 4 }}>
                {reviews.map((r) => (
                  <div key={r.id as string} style={{ fontSize: 12, color: '#555', lineHeight: '22px' }}>
                    <Tag color={NODE_META[r.node]?.color} style={{ marginRight: 4 }}>{NODE_META[r.node]?.label || r.node}</Tag>
                    <Tag color={REVIEW_ACTION_META[r.action]?.color} style={{ marginRight: 4 }}>
                      {REVIEW_ACTION_META[r.action]?.label || r.action}
                    </Tag>
                    {r.comment ? `意见：${r.comment}　` : ''}
                    <span style={{ color: '#999' }}>{r.operatorName || '-'} · {r.operatedAt}</span>
                  </div>
                ))}
              </div>
            )}
          </Space>
        )}
      </Card>
    );
  };

  // ---------- 送样评估 ----------
  const submittedEvalOptions = d.evals
    .filter((e) => e.eval.status === 'SUBMITTED')
    .map((e) => ({
      value: e.eval.supplierId,
      label: `${e.supplierName || e.eval.supplierId}（${e.eval.roundNo}轮 / ${Number(e.eval.totalScore ?? 0).toFixed(2)}分 / 排名${e.eval.rankNo ?? '-'}）`,
    }));

  const evalColumns: ColumnsType<EvalDetail> = [
    { title: '送样供应商', dataIndex: 'supplierName', width: 180, render: (v?: string, r?) => v || r?.eval.supplierId },
    { title: '轮次', dataIndex: ['eval', 'roundNo'], width: 60 },
    { title: '样品描述', dataIndex: ['eval', 'sampleDesc'], ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '质量结论',
      dataIndex: ['eval', 'qualityConclusion'],
      width: 100,
      render: (v?: string) => <Tag color={EVAL_QUALITY.find((q) => q.value === v)?.color}>{EVAL_QUALITY.find((q) => q.value === v)?.label || '待判定'}</Tag>,
    },
    {
      title: '加权总分',
      dataIndex: ['eval', 'totalScore'],
      width: 90,
      render: (v?: number | string | null) => (v === null || v === undefined ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '排名',
      dataIndex: ['eval', 'rankNo'],
      width: 60,
      render: (v?: number | null) => v ?? '-',
    },
    {
      title: '定点',
      dataIndex: ['eval', 'selectedFlag'],
      width: 70,
      render: (v?: number) => (v === 1 ? <Tag color="success">选定</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: ['eval', 'status'],
      width: 80,
      render: (v: string) => <Tag color={v === 'SUBMITTED' ? 'green' : 'default'}>{v === 'SUBMITTED' ? '已提交' : '草稿'}</Tag>,
    },
    {
      title: '操作',
      key: 'ops',
      width: 180,
      render: (_, r) => (
        <Space>
          <Button type="link" size="small" onClick={() => setEvalDrawer({ id: r.eval.id })}>
            {r.eval.status === 'DRAFT' ? '继续评分' : '查看'}
          </Button>
          {r.eval.status === 'DRAFT' && (
            <Popconfirm title="提交后系统按加权总分重新排名，确定提交？"
              onConfirm={() => evalApi.submit(r.eval.id).then(() => { message.success('评估已提交'); invalidate(); })}>
              <AuthButton type="link" size="small" perm="npi:project:edit">提交评估</AuthButton>
            </Popconfirm>
          )}
          {d.allowedActions.includes('FIX_SUPPLIER') && r.eval.status === 'SUBMITTED' && r.eval.selectedFlag !== 1 && (
            <Button
              type="link"
              size="small"
              onClick={() => { fixForm.setFieldsValue({ supplierId: r.eval.supplierId }); setFixOpen(true); }}
            >
              定点
            </Button>
          )}
        </Space>
      ),
    },
  ];

  // ---------- 验厂 ----------
  const auditColumns: ColumnsType<AuditDetail> = [
    { title: '验厂单号', dataIndex: ['audit', 'auditNo'], width: 150, render: (v: string) => <span style={{ fontFamily: 'monospace' }}>{v}</span> },
    {
      title: '类型',
      dataIndex: ['audit', 'auditType'],
      width: 80,
      render: (v: string) => (v === 'FOLLOW_UP' ? '复审' : '初审'),
    },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    { title: '计划时间', dataIndex: ['audit', 'plannedAt'], width: 160, render: (v?: string) => v || '-' },
    { title: '实审时间', dataIndex: ['audit', 'auditedAt'], width: 160, render: (v?: string) => v || '-' },
    { title: '验厂组', dataIndex: ['audit', 'auditors'], ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '总分',
      dataIndex: ['audit', 'totalScore'],
      width: 80,
      render: (v?: number | string | null) => (v === null || v === undefined ? '-' : Number(v).toFixed(2)),
    },
    {
      title: '结论',
      dataIndex: ['audit', 'conclusion'],
      width: 100,
      render: (v?: string | null) =>
        v ? <Tag color={AUDIT_CONCLUSION.find((c) => c.value === v)?.color}>{AUDIT_CONCLUSION.find((c) => c.value === v)?.label}</Tag> : '-',
    },
    {
      title: '状态',
      dataIndex: ['audit', 'status'],
      width: 90,
      render: (v: string) => <Tag color={AUDIT_STATUS.find((s) => s.value === v)?.color}>{AUDIT_STATUS.find((s) => s.value === v)?.label}</Tag>,
    },
    {
      title: '整改截止',
      dataIndex: ['audit', 'rectifyDeadline'],
      width: 110,
      render: (v?: string | null) => v || '-',
    },
    {
      title: '操作',
      key: 'ops',
      width: 80,
      render: (_, r) => (
        <Button type="link" size="small" onClick={() => setAuditDrawer({ id: r.audit.id })}>
          {r.audit.status === 'CONFIRMED' ? '查看' : '评分/确认'}
        </Button>
      ),
    },
  ];

  // ---------- 外检 ----------
  const extColumns: ColumnsType<ExternalTest> = [
    { title: '外检单号', dataIndex: 'testNo', width: 150, render: (v: string) => <span style={{ fontFamily: 'monospace' }}>{v}</span> },
    { title: '机构', dataIndex: 'labName', width: 200, render: (v?: string) => v || '-' },
    {
      title: '资质',
      dataIndex: 'labQualification',
      width: 90,
      render: (v?: string) => <Tag>{LAB_QUALIFICATIONS.find((q) => q.value === v)?.label || v || '-'}</Tag>,
    },
    { title: '送检项目', dataIndex: 'testItems', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '寄样时间', dataIndex: 'sentAt', width: 160, render: (v?: string) => v || '-' },
    { title: '报告编号', dataIndex: 'reportNo', width: 150, render: (v?: string) => v || '-' },
    { title: '报告日期', dataIndex: 'reportDate', width: 110, render: (v?: string) => v || '-' },
    {
      title: '结论',
      dataIndex: 'conclusion',
      width: 100,
      render: (v?: string) => <Tag color={EXT_CONCLUSION.find((c) => c.value === v)?.color}>{EXT_CONCLUSION.find((c) => c.value === v)?.label || '检测中'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag color={EXT_STATUS.find((s) => s.value === v)?.color}>{EXT_STATUS.find((s) => s.value === v)?.label}</Tag>,
    },
    {
      title: '操作',
      key: 'ops',
      width: 80,
      render: (_, r) => (
        <Button type="link" size="small" onClick={() => setExtDrawer({ id: r.id })}>
          {r.status === 'REPORTED' ? '查看' : '回填/编辑'}
        </Button>
      ),
    },
  ];

  const canCreateEval = ['SOURCING', 'AUDIT'].includes(p.status);
  const canCreateAudit = ['SOURCING', 'AUDIT', 'PRODUCING'].includes(p.status);
  const canCreateExt = ['PRODUCING', 'EXT_TEST', 'LISTING_REVIEW'].includes(p.status);

  return (
    <div>
      <Card
        style={{ marginBottom: 16 }}
        title={
          <Space>
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/npi/projects')} />
            <strong style={{ fontSize: 16 }}>{p.projectName}</strong>
            <span style={{ fontFamily: 'monospace', color: '#888' }}>{p.projectNo}</span>
            <Tag color={npiMeta(p.status)?.color}>{npiMeta(p.status)?.label}</Tag>
            {p.targetGrade && (
              <Tag color={STD_GRADE_FULL[p.targetGrade]?.color}>
                寻源档位：{STD_GRADES.find((g) => g.value === p.targetGrade)?.label}
              </Tag>
            )}
          </Space>
        }
        extra={<Space>{actionBar}</Space>}
      >
        {terminated && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 12 }}
            message={`项目已终止：${p.terminateReason || '（未填写原因）'}`}
          />
        )}
        <Descriptions size="small" column={3} bordered>
          <Descriptions.Item label="拟引入品类">{d.categoryName || p.categoryId}</Descriptions.Item>
          <Descriptions.Item label="品牌">{p.brand || '-'}</Descriptions.Item>
          <Descriptions.Item label="发起人">{d.initiatorName || '-'}</Descriptions.Item>
          <Descriptions.Item label="定点供应商">{d.chosenSupplierName || '-'}</Descriptions.Item>
          <Descriptions.Item label="关联产品">{d.productName || '-'}</Descriptions.Item>
          <Descriptions.Item label="计划上市">{p.targetListingDate || '-'}</Descriptions.Item>
          <Descriptions.Item label="新品会时间">{p.meetingAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="参会人" span={2}>{p.attendees || '-'}</Descriptions.Item>
          <Descriptions.Item label="立项背景" span={3}>{p.background || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Steps
          size="small"
          current={currentStep}
          status={terminated ? 'error' : p.status === 'LISTED' ? 'finish' : 'process'}
          items={STEP_ITEMS}
        />
      </Card>

      <GateCard gates={d.gates || []} />

      <Card>
        <Tabs
          items={[
            {
              key: 'grades',
              label: `三档质量标准（${STD_GRADES.filter((g) => d.gradeTemplates[g.value]).length}/3）`,
              children: (
                <div>
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                    message="依据国家标准/法规并对标市面竞品，按高/中/低三档分别起草；三档齐全后提交采购会签、老板批准，并选定其中一档用于寻源。"
                  />
                  {STD_GRADES.map((g) => <GradeCard key={g.value} grade={g} />)}
                </div>
              ),
            },
            {
              key: 'evals',
              label: `送样评估（${d.evals.length}）`,
              children: (
                <div>
                  {canCreateEval && (
                    <div style={{ marginBottom: 12 }}>
                      <AuthButton type="primary" perm="npi:project:edit" onClick={() => setEvalDrawer({ id: null })}>
                        登记送样评估
                      </AuthButton>
                    </div>
                  )}
                  <Table
                    size="small"
                    rowKey={(r) => r.eval.id as string}
                    columns={evalColumns}
                    dataSource={d.evals}
                    pagination={false}
                    expandable={{
                      expandedRowRender: (r) => (
                        <Table
                          size="small"
                          rowKey={(it) => it.dim.code}
                          pagination={false}
                          dataSource={EVAL_DIMENSIONS.map((dim) => ({
                            dim,
                            item: r.items.find((it) => it.dimensionCode === dim.code),
                          }))}
                          columns={[
                            { title: '维度', dataIndex: ['dim', 'name'], width: 130 },
                            {
                              title: '得分',
                              width: 90,
                              render: (_, row) =>
                                row.item?.score !== null && row.item?.score !== undefined ? Number(row.item.score).toFixed(1) : '-',
                            },
                            { title: '权重', width: 80, render: (_, row) => row.item?.weight ?? row.dim.defaultWeight },
                            { title: '评价说明', render: (_, row) => row.item?.note || '-' },
                          ]}
                        />
                      ),
                    }}
                  />
                </div>
              ),
            },
            {
              key: 'audits',
              label: `实地验厂（${d.audits.length}）`,
              children: (
                <div>
                  {canCreateAudit && (
                    <div style={{ marginBottom: 12 }}>
                      <AuthButton type="primary" perm="npi:audit:edit" onClick={() => setAuditDrawer({ id: null })}>
                        新建验厂单（资质/生产/仓储/冷链/质量体系）
                      </AuthButton>
                    </div>
                  )}
                  <Table size="small" rowKey={(r) => r.audit.id as string} columns={auditColumns} dataSource={d.audits} pagination={false} />
                </div>
              ),
            },
            {
              key: 'ext',
              label: `外检送检（${d.extTests.length}）`,
              children: (
                <div>
                  {canCreateExt && (
                    <div style={{ marginBottom: 12 }}>
                      <AuthButton type="primary" perm="npi:exttest:edit" onClick={() => setExtDrawer({ id: null })}>
                        登记外检送检
                      </AuthButton>
                    </div>
                  )}
                  <Table size="small" rowKey={(r) => r.id as string} columns={extColumns} dataSource={d.extTests} pagination={false} />
                </div>
              ),
            },
            {
              key: 'timeline',
              label: `项目动态（${d.timeline.length}）`,
              children: (
                <Timeline
                  items={[...d.timeline]
                    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
                    .map((t) => ({
                      children: (
                        <div>
                          <Space size={6}>
                            <b>{t.title}</b>
                            {t.comment ? <span style={{ color: '#666' }}>— {t.comment}</span> : null}
                          </Space>
                          <div style={{ color: '#999', fontSize: 12 }}>
                            {t.operatorName || '系统'} · {t.createdAt}
                          </div>
                        </div>
                      ),
                    }))}
                />
              ),
            },
          ]}
        />
      </Card>

      {/* 生成档位标准 */}
      <Modal
        title={`生成${STD_GRADES.find((g) => g.value === genGrade)?.label}标准草稿`}
        open={genGrade !== null}
        onCancel={() => setGenGrade(null)}
        onOk={() => generateMutation.mutate()}
        confirmLoading={generateMutation.isPending}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="系统将复制该品类现行通用模板的全部检验项作为底稿，请在此基础上按本档定位细化加严/放宽指标。"
        />
        <Form form={genForm} layout="vertical">
          <Form.Item name="templateName" label="标准名称">
            <Input maxLength={200} placeholder="留空使用系统默认名称" />
          </Form.Item>
          <Form.Item name="regulationBasis" label="国家标准 / 法规依据" rules={[{ required: true, message: '请填写国标/法规依据' }]}>
            <Input.TextArea rows={3} placeholder="如 GB/T 19692-2008、GH/T 1091、《食品安全法》相关要求……" />
          </Form.Item>
          <Form.Item name="marketBenchmark" label="市面竞品对标" rules={[{ required: true, message: '请填写竞品对标情况' }]}>
            <Input.TextArea rows={3} placeholder="如 对标XX品牌同类品：含水量≤X%、二氧化硫≤X、包装克重/材质……" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 评审意见 */}
      <Modal
        title={reviewCtx?.node === 'PROCUREMENT' ? '采购会签' : '老板批准'}
        open={reviewCtx !== null}
        onCancel={() => setReviewCtx(null)}
        onOk={() => reviewMutation.mutate()}
        confirmLoading={reviewMutation.isPending}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
        okText="提交意见"
        okButtonProps={{ danger: reviewPass === false }}
      >
        <Form form={reviewForm} layout="vertical" initialValues={{ pass: true }}>
          <Form.Item name="pass" label="评审结论" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio value={true}>
                {reviewCtx?.node === 'PROCUREMENT' ? '会签通过' : '批准发布'}
              </Radio>
              {reviewCtx?.node === 'BOSS' && (
                <Radio value={false}>驳回（退回起草修改）</Radio>
              )}
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="comment"
            label="评审意见"
            rules={[{ required: reviewPass === false, message: '驳回必须填写意见' }]}
          >
            <Input.TextArea rows={3} maxLength={500} placeholder="通过可填共识要点；驳回须写明修改要求" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 评审通过选档 */}
      <Modal
        title="评审通过并选定寻源档位"
        open={approveOpen}
        onCancel={() => setApproveOpen(false)}
        onOk={async () => {
          const v = await approveForm.validateFields();
          const tpl = d.gradeTemplates[v.grade];
          await act.mutateAsync({ action: 'APPROVE_STD', body: { grade: v.grade, templateId: tpl.id, comment: v.comment } });
          setApproveOpen(false);
        }}
        confirmLoading={act.isPending}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        {publishedGrades.length === 0 && <Alert type="error" showIcon message="三档标准均未完成发布，无法通过评审" style={{ marginBottom: 12 }} />}
        <Form form={approveForm} layout="vertical" initialValues={{ grade: publishedGrades[0]?.value }}>
          <Form.Item name="grade" label="选定采购寻源档位" rules={[{ required: true }]}>
            <Radio.Group>
              <Space direction="vertical">
                {publishedGrades.map((g) => {
                  const tpl = d.gradeTemplates[g.value];
                  return (
                    <Radio key={g.value} value={g.value}>
                      <Tag color={g.color}>{g.label}</Tag>
                      {tpl.templateName}（{tpl.templateCode} v{tpl.version}）
                    </Radio>
                  );
                })}
              </Space>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="comment" label="评审共识备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 定点供应商 */}
      <Modal
        title="定点供应商"
        open={fixOpen}
        onCancel={() => setFixOpen(false)}
        onOk={async () => {
          const v = await fixForm.validateFields();
          await act.mutateAsync({ action: 'FIX_SUPPLIER', body: { supplierId: v.supplierId } });
          setFixOpen(false);
        }}
        confirmLoading={act.isPending}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        {submittedEvalOptions.length === 0 && (
          <Alert type="warning" showIcon message="还没有已提交的送样评估，无法定点" style={{ marginBottom: 12 }} />
        )}
        <Form form={fixForm} layout="vertical">
          <Form.Item name="supplierId" label="选择定点供应商" rules={[{ required: true, message: '请选择供应商' }]}>
            <Select options={submittedEvalOptions} placeholder="仅可选择已提交评估的供应商" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 上市放行 */}
      <Modal
        title="上市放行"
        open={releaseOpen}
        onCancel={() => setReleaseOpen(false)}
        onOk={async () => {
          const v = await releaseForm.validateFields();
          await act.mutateAsync({ action: 'RELEASE', body: { productId: v.productId, comment: v.comment } });
          setReleaseOpen(false);
        }}
        confirmLoading={act.isPending}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        <Alert type="info" showIcon style={{ marginBottom: 12 }}
          message="系统将再次校验：三档标准评审、选定档位、定点供应商、验厂合格、外检合格、项目未终止，全部满足才放行。" />
        <Form form={releaseForm} layout="vertical">
          <Form.Item name="productId" label="关联在售产品档案（可选）">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择已建档产品"
              options={(productsQuery.data?.records || []).map((pr) => ({
                value: pr.id,
                label: `${pr.productName}（${pr.spuCode || pr.id}）`,
              }))}
            />
          </Form.Item>
          <Form.Item name="comment" label="放行意见">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 驳回标准 */}
      <Modal
        title="驳回三档标准（退回起草）"
        open={rejectStdOpen}
        onCancel={() => setRejectStdOpen(false)}
        onOk={async () => {
          const v = await rejectForm.validateFields();
          await act.mutateAsync({ action: 'REJECT_STD', body: { comment: v.comment } });
          setRejectStdOpen(false);
        }}
        confirmLoading={act.isPending}
        okButtonProps={{ danger: true }}
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        <Form form={rejectForm} layout="vertical">
          <Form.Item name="comment" label="驳回意见" rules={[{ required: true, message: '请填写驳回意见' }]}>
            <Input.TextArea rows={3} maxLength={500} placeholder="说明标准需要调整的内容" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 终止项目 */}
      <Modal
        title="终止项目"
        open={terminateOpen}
        onCancel={() => setTerminateOpen(false)}
        onOk={async () => {
          const v = await terminateForm.validateFields();
          await act.mutateAsync({ action: 'TERMINATE', body: { comment: v.comment } });
          setTerminateOpen(false);
        }}
        confirmLoading={act.isPending}
        okButtonProps={{ danger: true }}
        okText="确认终止"
        // forceRender：7 个动作弹窗均在按钮 onClick 中先 reset/setFieldsValue 再 open，必须预挂载
        forceRender
      >
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="终止后项目不可再推进，请谨慎操作。" />
        <Form form={terminateForm} layout="vertical">
          <Form.Item name="comment" label="终止原因" rules={[{ required: true, message: '请填写终止原因' }]}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 抽屉：检验项编排 / 评估 / 验厂 / 外检 */}
      {templateDrawerId !== null && (
        <TemplateDrawer
          id={templateDrawerId}
          gradeMode
          onClose={() => setTemplateDrawerId(null)}
          onChanged={invalidate}
        />
      )}
      {evalDrawer && (
        <EvalDrawer
          id={evalDrawer.id}
          projectId={projectId}
          onClose={() => setEvalDrawer(null)}
        />
      )}
      {auditDrawer && (
        <AuditDrawer
          id={auditDrawer.id}
          projectId={projectId}
          defaultSupplierId={p.chosenSupplierId}
          onClose={() => setAuditDrawer(null)}
        />
      )}
      {extDrawer && (
        <ExtTestDrawer
          id={extDrawer.id}
          projectId={projectId}
          defaultSupplierId={p.chosenSupplierId}
          onClose={() => setExtDrawer(null)}
        />
      )}
    </div>
  );
};

export default ProjectDetailPage;
