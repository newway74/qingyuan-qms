import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Descriptions, Empty, Form, Input, InputNumber, Modal, Radio, Space, Spin, Switch, Table, Tag } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import AttachmentUpload from '@/components/AttachmentUpload';
import { taskApi, reportApi } from '@/api/inspection';
import request from '@/api/request';
import {
  DEFECT_LEVELS,
  ITEM_GROUPS,
  JUDGEMENTS,
  REPORT_CONCLUSIONS,
  REPORT_STATUS,
  TASK_STATUS,
} from '@/constants/qms';
import type {
  InspectionResult,
  ResultItemInput,
  ReviewAdjustment,
  TaskDetail,
} from '@/types/inspection';
import type { StandardItem } from '@/types/standard';
import type { LongId } from '@/types/auth';
import message from '@/utils/feedback';

const RESULT_BIZ_TYPE = 'qc_inspection_result';
const groupLabel = (code: string) => ITEM_GROUPS.find((g) => g.value === code)?.label || code;
const levelMeta = (v: string) => DEFECT_LEVELS.find((d) => d.value === v);
const judgeMeta = (v?: string) => JUDGEMENTS.find((j) => j.value === v);
const taskStatusMeta = (v: string) => TASK_STATUS.find((s) => s.value === v);

interface RowDraft {
  qualitativeValue?: string;
  quantitativeValue?: number | null;
  docAttachmentId?: LongId;
  remark?: string;
}

function standardText(item: StandardItem): string {
  const parts: string[] = [];
  if (item.toleranceRule) parts.push(`规则：${item.toleranceRule}`);
  if (item.nominalValue !== undefined && item.nominalValue !== null)
    parts.push(`标称值 ${item.nominalValue}${item.unit || ''}`);
  if (item.minValue !== undefined && item.minValue !== null)
    parts.push(`下限 ${item.minValue}${item.unit || ''}`);
  if (item.maxValue !== undefined && item.maxValue !== null)
    parts.push(`上限 ${item.maxValue}${item.unit || ''}`);
  if (item.inspectMethod) parts.push(`方法：${item.inspectMethod}`);
  return parts.join('；');
}

async function downloadPdf(id: LongId) {
  const resp = await request.get(`/inspection/reports/${id}/pdf`, { responseType: 'blob' });
  const url = window.URL.createObjectURL(new Blob([resp as unknown as BlobPart], { type: 'application/pdf' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `质检报告-${id}.pdf`;
  a.target = '_blank';
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

const TaskWorkbench: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const taskId = id as LongId;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const detailQuery = useQuery({ queryKey: ['task-detail', String(taskId)], queryFn: () => taskApi.detail(taskId) });
  const detail = detailQuery.data;

  const [drafts, setDrafts] = useState<Record<string, RowDraft>>({});
  const [submitOpen, setSubmitOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [submitForm] = Form.useForm<{ password: string }>();
  const [reviewForm] = Form.useForm();

  useEffect(() => {
    if (detail?.results) {
      const init: Record<string, RowDraft> = {};
      detail.results.forEach((r) => {
        init[String(r.id)] = {
          qualitativeValue: r.qualitativeValue,
          quantitativeValue: r.quantitativeValue !== undefined && r.quantitativeValue !== null ? Number(r.quantitativeValue) : undefined,
          docAttachmentId: r.docAttachmentId,
          remark: r.remark,
        };
      });
      setDrafts(init);
    }
  }, [detail?.results]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['task-detail', String(taskId)] });

  const patchDraft = (rid: LongId, patch: Partial<RowDraft>) =>
    setDrafts((m) => ({ ...m, [String(rid)]: { ...m[String(rid)], ...patch } }));

  const buildPayload = (): ResultItemInput[] =>
    (detail?.results || []).map((r) => {
      const d = drafts[String(r.id)] || {};
      return {
        resultId: r.id,
        itemId: r.itemId,
        qualitativeValue: d.qualitativeValue,
        quantitativeValue: d.quantitativeValue ?? undefined,
        docAttachmentId: d.docAttachmentId,
        remark: d.remark,
      };
    });

  const saveMutation = useMutation({
    mutationFn: () => taskApi.saveResults(taskId, buildPayload()),
    onSuccess: () => {
      message.success('结果已保存，自动判定已刷新');
      invalidate();
    },
  });

  const startMutation = useMutation({
    mutationFn: () => taskApi.start(taskId),
    onSuccess: () => {
      message.success('检验已开始，结果行已按标准模板初始化');
      invalidate();
    },
  });

  const submitMutation = useMutation({
    mutationFn: async () => {
      const v = await submitForm.validateFields();
      await taskApi.submit(taskId, v.password);
    },
    onSuccess: () => {
      message.success('已提交复核，检验签名已固化');
      setSubmitOpen(false);
      submitForm.resetFields();
      invalidate();
    },
  });

  const reviewMutation = useMutation({
    mutationFn: async (payload: Parameters<typeof taskApi.review>[1]) => taskApi.review(taskId, payload),
    onSuccess: () => {
      message.success('复核完成，报告已双签生成');
      setReviewOpen(false);
      invalidate();
    },
  });

  const closeMutation = useMutation({
    mutationFn: () => taskApi.close(taskId),
    onSuccess: () => {
      message.success('任务已闭环归档');
      invalidate();
    },
  });

  const [recheckOpen, setRecheckOpen] = useState(false);
  const [recheckForm] = Form.useForm<{ reason: string }>();
  const recheckMutation = useMutation({
    mutationFn: (reason: string) => taskApi.recheck(taskId, reason),
    onSuccess: (childId) => {
      message.success('复检子任务已创建，原任务/报告保持不变');
      setRecheckOpen(false);
      invalidate();
      navigate(`/inspection/tasks/${childId}`);
    },
  });

  const grouped = useMemo(() => {
    const map = new Map<string, { item: StandardItem; result?: InspectionResult }[]>();
    (detail?.items || []).forEach((item) => {
      const result = detail?.results?.find((r) => String(r.itemId) === String(item.id));
      const list = map.get(item.groupCode) || [];
      list.push({ item, result });
      map.set(item.groupCode, list);
    });
    return map;
  }, [detail]);

  if (detailQuery.isLoading) return <Spin style={{ display: 'block', marginTop: 120 }} size="large" />;
  if (!detail) return <Empty description="任务不存在或无权查看" />;

  const { task, report, summary, allowedActions = [] } = detail;
  const can = (a: string) => allowedActions.includes(a);
  const editable = task.status === 'INSPECTING';
  const failedResults = (detail.results || []).filter((r) => r.finalJudgement === 'FAIL' || r.autoJudgement === 'FAIL');

  const renderItemTable = (groupCode: string) => {
    const rows = grouped.get(groupCode) || [];
    return (
      <Table<{ item: StandardItem; result?: InspectionResult }>
        key={groupCode}
        rowKey={(r) => String(r.item.id)}
        size="small"
        pagination={false}
        dataSource={rows}
        columns={[
          {
            title: '检验项',
            dataIndex: ['item', 'itemName'],
            width: 200,
            render: (v: string, r) => (
              <Space size={4} wrap>
                <b>{v}</b>
                <Tag color={levelMeta(r.item.defectLevel)?.color}>{levelMeta(r.item.defectLevel)?.label}</Tag>
                {r.item.requiredFlag === 1 && <Tag color="red">必检</Tag>}
                {r.item.vetoFlag === 1 && <Tag color="volcano">一票否决</Tag>}
              </Space>
            ),
          },
          {
            title: '标准/方法',
            width: 260,
            render: (_, r) => standardText(r.item) || '-',
          },
          {
            title: '实测/判定录入',
            width: 280,
            render: (_, r) => {
              if (!r.result) return <Tag>任务开始后初始化</Tag>;
              const rid = r.result.id;
              const d = drafts[String(rid)] || {};
              if (r.item.resultType === 'QUALITATIVE') {
                return (
                  <Radio.Group
                    disabled={!editable}
                    optionType="button"
                    buttonStyle="solid"
                    value={d.qualitativeValue}
                    options={[
                      { value: 'PASS', label: '符合' },
                      { value: 'FAIL', label: '不符合' },
                      { value: 'NA', label: '未检出/不适用' },
                    ]}
                    onChange={(e) => patchDraft(rid, { qualitativeValue: e.target.value })}
                  />
                );
              }
              if (r.item.resultType === 'QUANTITATIVE') {
                return (
                  <Space>
                    <InputNumber
                      disabled={!editable}
                      style={{ width: 150 }}
                      placeholder="实测值"
                      value={d.quantitativeValue ?? undefined}
                      onChange={(v) => patchDraft(rid, { quantitativeValue: v })}
                    />
                    <span>{r.item.unit}</span>
                  </Space>
                );
              }
              return (
                <AttachmentUpload
                  bizType={RESULT_BIZ_TYPE}
                  bizId={rid}
                  value={d.docAttachmentId}
                  readOnly={!editable}
                  onChange={(attId) => patchDraft(rid, { docAttachmentId: attId ?? undefined })}
                />
              );
            },
          },
          {
            title: '自动判定',
            width: 110,
            render: (_, r) => {
              const j = r.result?.autoJudgement;
              if (!j || j === 'NONE') return <Tag>待判定</Tag>;
              const m = judgeMeta(j);
              return <Tag color={m?.color}>{m?.label}</Tag>;
            },
          },
          {
            title: '复核判定/备注',
            render: (_, r) => {
              if (!r.result) return '-';
              const rid = r.result.id;
              const d = drafts[String(rid)] || {};
              return (
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  {r.result.finalJudgement && r.result.finalJudgement !== r.result.autoJudgement && (
                    <Tag color="gold">
                      复核改判：{judgeMeta(r.result.finalJudgement)?.label}
                      {r.result.reviewNote ? `（${r.result.reviewNote}）` : ''}
                    </Tag>
                  )}
                  <Input.TextArea
                    disabled={!editable}
                    rows={1}
                    placeholder="备注"
                    value={d.remark}
                    onChange={(e) => patchDraft(rid, { remark: e.target.value })}
                  />
                </Space>
              );
            },
          },
        ]}
      />
    );
  };

  return (
    <Card
      title={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/inspection/tasks')}>
            返回
          </Button>
          <span>检验工作台 {task.taskNo}</span>
          <Tag color={taskStatusMeta(task.status)?.color}>{taskStatusMeta(task.status)?.label}</Tag>
          {task.roundNo > 1 && <Tag color="orange">第 {task.roundNo} 轮（复检）</Tag>}
        </Space>
      }
      extra={
        <Space wrap>
          {can('START') && (
            <AuthButton perm="inspection:task:handle" type="primary" loading={startMutation.isPending} onClick={() => startMutation.mutate()}>
              开始检验
            </AuthButton>
          )}
          {editable && (
            <>
              <AuthButton
                perm="inspection:result:save"
                type="primary"
                ghost
                loading={saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                保存结果/刷新判定
              </AuthButton>
              <AuthButton perm="inspection:task:submit" type="primary" onClick={() => setSubmitOpen(true)}>
                提交复核（签名）
              </AuthButton>
            </>
          )}
          {(can('PASS') || can('REJECT')) && (
            <AuthButton perm="inspection:task:review" type="primary" danger={can('REJECT')} onClick={() => setReviewOpen(true)}>
              复核判定（双签）
            </AuthButton>
          )}
          {can('RECHECK') && (
            <AuthButton
              perm="inspection:task:recheck"
              loading={recheckMutation.isPending}
              onClick={() => {
                recheckForm.resetFields();
                setRecheckOpen(true);
              }}
            >
              发起复检
            </AuthButton>
          )}
          {can('CLOSE') && (
            <AuthButton perm="inspection:task:assign" loading={closeMutation.isPending} onClick={() => closeMutation.mutate()}>
              闭环归档
            </AuthButton>
          )}
        </Space>
      }
    >
      <Descriptions column={3} bordered size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="样品编号">{task.sampleNo}</Descriptions.Item>
        <Descriptions.Item label="产品" span={1}>
          {task.productName} {task.spec}
        </Descriptions.Item>
        <Descriptions.Item label="批号">{task.batchNo}</Descriptions.Item>
        <Descriptions.Item label="标准模板">
          {task.templateName} v{task.templateVersion}
        </Descriptions.Item>
        <Descriptions.Item label="检验员">{task.inspectorName || '-'}</Descriptions.Item>
        <Descriptions.Item label="复核人">{task.reviewerName || '-'}</Descriptions.Item>
        <Descriptions.Item label="SLA截止">{task.slaDeadline?.replace('T', ' ').slice(0, 16) || '-'}</Descriptions.Item>
        <Descriptions.Item label="开始时间">{task.startedAt?.replace('T', ' ').slice(0, 16) || '-'}</Descriptions.Item>
        <Descriptions.Item label="提交时间">{task.submittedAt?.replace('T', ' ').slice(0, 16) || '-'}</Descriptions.Item>
      </Descriptions>

      {summary && (
        <Alert
          style={{ marginBottom: 16 }}
          type={summary.unqualified ? 'error' : 'success'}
          showIcon
          message={
            <Space wrap>
              <span>
                综合判定：
                <b>{REPORT_CONCLUSIONS.find((c) => c.value === summary.suggestedConclusion)?.label}</b>
              </span>
              <Tag color="red">A类不合格 {summary.aFailCount}</Tag>
              <Tag color="orange">B类不合格 {summary.bFailCount}</Tag>
              <Tag color="blue">C类不合格 {summary.cFailCount}</Tag>
              {summary.vetoFail && <Tag color="volcano">触发关键项一票否决</Tag>}
              {summary.concessionPossible && <Tag color="gold">符合让步接收条件</Tag>}
            </Space>
          }
        />
      )}

      {editable && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="逐项录入后请先「保存结果/刷新判定」；定量项按标准阈值/JJF1070净含量允差自动判定，全部必检项完成后方可提交复核。"
        />
      )}

      {Array.from(grouped.entries()).map(([groupCode]) => (
        <Card key={groupCode} size="small" type="inner" title={groupLabel(groupCode)} style={{ marginBottom: 12 }}>
          {renderItemTable(groupCode)}
        </Card>
      ))}

      {report && (
        <Card
          size="small"
          type="inner"
          title={
            <Space>
              质检报告 {report.reportNo}
              <Tag color={REPORT_CONCLUSIONS.find((c) => c.value === report.conclusion)?.color}>
                {REPORT_CONCLUSIONS.find((c) => c.value === report.conclusion)?.label}
              </Tag>
              <Tag color={REPORT_STATUS.find((s) => s.value === report.status)?.color}>
                {REPORT_STATUS.find((s) => s.value === report.status)?.label}
              </Tag>
            </Space>
          }
          style={{ marginTop: 16 }}
          extra={
            <Space>
              {report.pdfAttachmentId && (
                <Button onClick={() => downloadPdf(report.id)}>下载PDF报告</Button>
              )}
              <Button onClick={() => navigate('/inspection/reports')}>前往报告列表</Button>
            </Space>
          }
        >
          <Descriptions column={2} size="small">
            <Descriptions.Item label="检验员签名">
              {report.inspectorSignHash ? (
                <Space direction="vertical" size={0}>
                  <code style={{ fontSize: 12 }}>{report.inspectorSignHash.slice(0, 24)}…</code>
                  <span>{report.inspectorSignedAt?.replace('T', ' ').slice(0, 16)}</span>
                </Space>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="复核人签名">
              {report.reviewerSignHash ? (
                <Space direction="vertical" size={0}>
                  <code style={{ fontSize: 12 }}>{report.reviewerSignHash.slice(0, 24)}…</code>
                  <span>{report.reviewerSignedAt?.replace('T', ' ').slice(0, 16)}</span>
                </Space>
              ) : (
                '-'
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* 提交复核：密码二次认证 */}
      <Modal
        title="提交复核（检验员电子签名）"
        open={submitOpen}
        onCancel={() => setSubmitOpen(false)}
        onOk={() => submitMutation.mutate()}
        confirmLoading={submitMutation.isPending}
        destroyOnHidden
      >
        <Alert
          style={{ marginBottom: 12 }}
          type="warning"
          showIcon
          message="提交后结果将固化并计算签名哈希，不可再修改实测值。"
        />
        <Form form={submitForm} layout="vertical">
          <Form.Item name="password" label="登录密码（二次认证）" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="off" placeholder="请输入登录密码" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 复核判定 */}
      <ReviewModal
        open={reviewOpen}
        detail={detail}
        failedResults={failedResults}
        form={reviewForm}
        onClose={() => setReviewOpen(false)}
        onSubmit={(payload) => reviewMutation.mutate(payload)}
        loading={reviewMutation.isPending}
        canReject={can('REJECT')}
        canPass={can('PASS')}
      />

      {/* 发起复检：原任务转复检中，新建子任务，原任务/报告不可变 */}
      <Modal
        title="发起复检"
        open={recheckOpen}
        onCancel={() => setRecheckOpen(false)}
        confirmLoading={recheckMutation.isPending}
        onOk={async () => {
          try {
            const v = await recheckForm.validateFields();
            recheckMutation.mutate(v.reason.trim());
          } catch {
            // 校验失败由表单提示
          }
        }}
        // forceRender：按钮 onClick 中先 recheckForm.resetFields 再打开（该弹窗本就不销毁，仅需预挂载）
        forceRender
      >
        <Alert
          style={{ marginBottom: 12 }}
          type="info"
          showIcon
          message="将新建复检子任务（轮次+1），复制模板快照、分配与原结果；原任务与原报告保持不变。"
        />
        <Form form={recheckForm} layout="vertical">
          <Form.Item name="reason" label="复检原因" rules={[{ required: true, message: '请填写复检原因' }]}>
            <Input.TextArea rows={3} maxLength={300} showCount placeholder="如：供应商整改完成，按不合格处置要求复检" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

interface ReviewModalProps {
  open: boolean;
  detail: TaskDetail;
  failedResults: InspectionResult[];
  form: ReturnType<typeof Form.useForm>[0];
  onClose: () => void;
  onSubmit: (payload: Parameters<typeof taskApi.review>[1]) => void;
  loading: boolean;
  canReject: boolean;
  canPass: boolean;
}

const ReviewModal: React.FC<ReviewModalProps> = ({
  open,
  detail,
  failedResults,
  form,
  onClose,
  onSubmit,
  loading,
  canReject,
  canPass,
}) => {
  const [action, setAction] = useState<'PASS' | 'REJECT'>('PASS');
  const [adjustments, setAdjustments] = useState<Record<string, { judgement: string; note: string }>>({});
  const [useConcession, setUseConcession] = useState(false);

  useEffect(() => {
    if (open) {
      setAction('PASS');
      setAdjustments({});
      setUseConcession(false);
      form.resetFields();
    }
  }, [open, form]);

  const { summary } = detail;
  const itemMap = useMemo(
    () => new Map((detail.items || []).map((i) => [String(i.id), i])),
    [detail.items],
  );

  const handleOk = async () => {
    if (action === 'REJECT') {
      let v: { rejectReason: string };
      try {
        v = (await form.validateFields(['rejectReason'])) as { rejectReason: string };
      } catch {
        return; // 校验失败由表单提示，避免 reject 上抛导致 Modal 锁死
      }
      onSubmit({ action: 'REJECT', rejectReason: v.rejectReason });
      return;
    }
    let v: {
      password: string;
      conclusion: string;
    };
    try {
      v = (await form.validateFields(['password', 'conclusion'])) as {
        password: string;
        conclusion: string;
      };
    } catch {
      return;
    }
    const adjList: ReviewAdjustment[] = [];
    Object.entries(adjustments).forEach(([rid, a]) => {
      if (a.judgement && a.note?.trim()) {
        adjList.push({ resultId: rid as LongId, finalJudgement: a.judgement, reviewNote: a.note.trim() });
      }
    });
    onSubmit({
      action: 'PASS',
      password: v.password,
      conclusion: v.conclusion,
      adjustments: adjList,
    });
  };

  const conclusionOptions = REPORT_CONCLUSIONS.filter((c) => {
    if (!summary) return true;
    if (summary.unqualified) return c.value === 'UNQUALIFIED';
    if (c.value === 'UNQUALIFIED') return false;
    if (c.value === 'CONCESSION') return !!summary.concessionPossible && useConcession;
    if (c.value === 'QUALIFIED') return !useConcession;
    return true;
  });

  return (
    <Modal
      title="复核判定（复核人电子签名）"
      open={open}
      width={720}
      onCancel={onClose}
      onOk={handleOk}
      confirmLoading={loading}
      destroyOnHidden
      okText={action === 'PASS' ? '复核通过并双签' : '驳回退检'}
      okButtonProps={{ danger: action === 'REJECT' }}
    >
      <Radio.Group
        value={action}
        optionType="button"
        buttonStyle="solid"
        style={{ marginBottom: 12 }}
        disabled={!canPass || !canReject}
        onChange={(e) => setAction(e.target.value)}
        options={[
          { value: 'PASS', label: '复核通过', disabled: !canPass },
          { value: 'REJECT', label: '驳回（退检验员）', disabled: !canReject },
        ]}
      />

      {action === 'REJECT' ? (
        <Form form={form} layout="vertical">
          <Form.Item
            name="rejectReason"
            label="驳回原因"
            rules={[{ required: true, message: '驳回必须填写原因（留痕）' }]}
          >
            <Input.TextArea rows={4} placeholder="请说明驳回原因，任务将退回检验员" />
          </Form.Item>
        </Form>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ conclusion: summary?.suggestedConclusion }}>
          {summary && (
            <Alert
              style={{ marginBottom: 12 }}
              type={summary.unqualified ? 'error' : 'success'}
              showIcon
              message={
                <Space wrap>
                  <Tag color="red">A类 {summary.aFailCount}</Tag>
                  <Tag color="orange">B类 {summary.bFailCount}</Tag>
                  <Tag color="blue">C类 {summary.cFailCount}</Tag>
                  {summary.vetoFail && <Tag color="volcano">一票否决已触发</Tag>}
                </Space>
              }
            />
          )}

          {failedResults.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <b>不合格项复核（仅B/C类且非否决项可改判，必须填写复核理由）：</b>
              <Table
                rowKey={(r) => String(r.id)}
                size="small"
                style={{ marginTop: 8 }}
                pagination={false}
                dataSource={failedResults}
                columns={[
                  {
                    title: '检验项',
                    width: 180,
                    render: (_, r) => {
                      const item = itemMap.get(String(r.itemId));
                      return (
                        <Space size={4}>
                          <span>{r.itemName}</span>
                          <Tag color={levelMeta(item?.defectLevel || '')?.color}>{item?.defectLevel}</Tag>
                          {item?.vetoFlag === 1 && <Tag color="volcano">否决项</Tag>}
                        </Space>
                      );
                    },
                  },
                  {
                    title: '自动判定',
                    width: 90,
                    render: (_, r) => <Tag color="red">{judgeMeta(r.autoJudgement)?.label}</Tag>,
                  },
                  {
                    title: '复核改判',
                    width: 150,
                    render: (_, r) => {
                      const item = itemMap.get(String(r.itemId));
                      const locked = item?.defectLevel === 'A' || item?.vetoFlag === 1;
                      if (locked) return <Tag color="red">A类/否决项禁止改判</Tag>;
                      return (
                        <Radio.Group
                          size="small"
                          value={adjustments[String(r.id)]?.judgement}
                          options={[
                            { value: 'FAIL', label: '维持不合格' },
                            { value: 'PASS', label: '改判合格' },
                          ]}
                          onChange={(e) =>
                            setAdjustments((m) => ({
                              ...m,
                              [String(r.id)]: { ...m[String(r.id)], judgement: e.target.value },
                            }))
                          }
                        />
                      );
                    },
                  },
                  {
                    title: '复核理由',
                    render: (_, r) => {
                      const item = itemMap.get(String(r.itemId));
                      const locked = item?.defectLevel === 'A' || item?.vetoFlag === 1;
                      const changed = adjustments[String(r.id)]?.judgement === 'PASS';
                      return (
                        <Input
                          size="small"
                          disabled={locked}
                          status={changed && !adjustments[String(r.id)]?.note?.trim() ? 'error' : undefined}
                          placeholder={changed ? '改判必须填写理由' : '复核意见（选填）'}
                          value={adjustments[String(r.id)]?.note}
                          onChange={(e) =>
                            setAdjustments((m) => ({
                              ...m,
                              [String(r.id)]: { ...m[String(r.id)], note: e.target.value },
                            }))
                          }
                        />
                      );
                    },
                  },
                ]}
              />
            </div>
          )}

          {summary?.concessionPossible && !summary.unqualified && (
            <Form.Item label="让步接收">
              <Switch
                checked={useConcession}
                onChange={setUseConcession}
                checkedChildren="判定让步接收"
                unCheckedChildren="正常合格"
              />
            </Form.Item>
          )}

          <Form.Item name="conclusion" label="最终结论" rules={[{ required: true, message: '请选择结论' }]}>
            <Radio.Group options={conclusionOptions.map((c) => ({ value: c.value, label: c.label }))} />
          </Form.Item>
          <Form.Item name="password" label="登录密码（二次认证）" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="off" placeholder="请输入登录密码完成双签" />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
};

export default TaskWorkbench;
