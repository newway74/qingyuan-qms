import React, { useState } from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Form, Input, Modal, Select, Space, Table, Tag, Timeline } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import AuthButton from '@/components/AuthButton';
import { caseApi, rectApi } from '@/api/defect';
import {
  DEFECT_CASE_STATUS,
  DEFECT_LEVELS,
  DISPOSITIONS,
  RECT_STATUS,
  REPORT_CONCLUSIONS,
} from '@/constants/qms';
import type { CaseDetail as CaseDetailData, DefectApprovalRow, DefectItem, RectificationRow } from '@/types/defect';
import message from '@/utils/feedback';

const statusMeta = (v: string) => DEFECT_CASE_STATUS.find((s) => s.value === v);
const severityMeta = (v: string) => DEFECT_LEVELS.find((s) => s.value === v);
const dispositionMeta = (v?: string) => DISPOSITIONS.find((d) => d.value === v);
const rectStatusMeta = (v: string) => RECT_STATUS.find((s) => s.value === v);
const conclusionMeta = (v?: string) => REPORT_CONCLUSIONS.find((c) => c.value === v);

const NODE_LABEL: Record<string, string> = {
  REVIEW: '质量评审',
  APPROVAL: '主管审批',
  DISPOSE: '处置执行',
  RECTIFY: '供应商整改',
  RECHECK: '复检',
  CLOSE: '闭环',
};
const ACTION_COLOR: Record<string, string> = {
  CREATE: 'blue',
  SUBMIT: 'blue',
  PASS: 'green',
  EXECUTE: 'blue',
  CLOSE: 'green',
  REJECT: 'red',
  CANCEL: 'red',
};

const CaseDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const caseId = id as string;
  const detailQuery = useQuery({
    queryKey: ['defect-detail', caseId],
    queryFn: () => caseApi.detail(caseId),
  });
  const d: CaseDetailData | undefined = detailQuery.data;

  const [modal, setModal] = useState<
    '' | 'review' | 'approve' | 'execute' | 'concession' | 'cancel' | 'close' | 'rectCreate' | 'rectReply' | 'rectVerify'
  >('');
  const [verifyResult, setVerifyResult] = useState<'PASS' | 'FAIL'>('PASS');
  const [form] = Form.useForm();

  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['defect-detail', caseId] }),
      queryClient.invalidateQueries({ queryKey: ['rect-page'] }),
    ]);

  const reviewMut = useMutation({
    mutationFn: (v: { disposition: string; rootCause: string; comment?: string }) =>
      caseApi.review(caseId, v),
    onSuccess: async () => {
      message.success('评审已提交，等待主管审批');
      await invalidate();
      setModal('');
    },
  });
  const approveMut = useMutation({
    mutationFn: (v: { action: 'PASS' | 'REJECT'; password?: string; comment?: string }) =>
      caseApi.approve(caseId, v),
    onSuccess: async () => {
      message.success('审批完成');
      await invalidate();
      setModal('');
    },
  });
  const executeMut = useMutation({
    mutationFn: (v: { comment: string; attachmentIds?: string }) => caseApi.execute(caseId, v),
    onSuccess: async () => {
      message.success('处置完成已登记，进入复检闭环');
      await invalidate();
      setModal('');
    },
  });
  const concessionMut = useMutation({
    mutationFn: (v: { password: string; comment: string }) => caseApi.concession(caseId, v),
    onSuccess: async () => {
      message.success('让步接收审批通过，不合格单已闭环');
      await invalidate();
      setModal('');
    },
  });
  const cancelMut = useMutation({
    mutationFn: (comment: string) => caseApi.cancel(caseId, comment),
    onSuccess: async () => {
      message.success('不合格单已撤销');
      await invalidate();
      setModal('');
    },
  });
  const closeMut = useMutation({
    mutationFn: (comment?: string) => caseApi.close(caseId, comment),
    onSuccess: async () => {
      message.success('不合格单已闭环');
      await invalidate();
      setModal('');
    },
  });

  const rect: RectificationRow | null | undefined = d?.rectification;
  const rectCreateMut = useMutation({
    mutationFn: (v: { issueDesc: string; planFinishDate?: string }) =>
      rectApi.create({ defectCaseId: caseId, ...v }),
    onSuccess: async () => {
      message.success('整改单已签发');
      await invalidate();
      setModal('');
    },
  });
  const rectReplyMut = useMutation({
    mutationFn: (v: { correctiveAction: string; planFinishDate?: string; actualFinishDate?: string }) =>
      rectApi.reply(rect!.rect.id, v),
    onSuccess: async () => {
      message.success('整改回复已提交');
      await invalidate();
      setModal('');
    },
  });
  const rectVerifyMut = useMutation({
    mutationFn: (v: { result: 'PASS' | 'FAIL'; password: string; comment?: string }) =>
      rectApi.verify(rect!.rect.id, v),
    onSuccess: async (_d, v) => {
      message.success(v.result === 'PASS' ? '验证通过，复检任务已生成' : '验证不通过，不合格单退回处置中');
      await invalidate();
      setModal('');
    },
  });

  const itemColumns: ColumnsType<DefectItem> = [
    {
      title: '等级',
      dataIndex: 'defectLevel',
      width: 90,
      align: 'center',
      render: (v: string) => <Tag color={severityMeta(v)?.color}>{severityMeta(v)?.label || v}</Tag>,
    },
    { title: '不合格项', dataIndex: 'itemName', width: 220 },
    { title: '不符合描述', dataIndex: 'failDesc' },
  ];

  const openModal = (key: typeof modal, initial?: Record<string, unknown>) => {
    form.resetFields();
    if (initial) {
      form.setFieldsValue(initial);
    }
    if (key === 'rectVerify') {
      setVerifyResult('PASS');
    }
    setModal(key);
  };

  const isA = d?.caseObj.maxSeverity === 'A';
  const status = d?.caseObj.status;
  const disposition = d?.caseObj.disposition;

  return (
    <Card
      title={
        <Space>
          <Button icon={<ArrowLeftOutlined />} size="small" onClick={() => navigate('/defect')}>
            返回
          </Button>
          <span>不合格处置单 {d?.caseObj.caseNo}</span>
          {status && <Tag color={statusMeta(status)?.color}>{statusMeta(status)?.label || status}</Tag>}
        </Space>
      }
      extra={
        d && (
          <Space wrap>
            {status === 'PENDING_REVIEW' && (
              <AuthButton perm="defect:review" type="primary" onClick={() => openModal('review')}>
                质量评审
              </AuthButton>
            )}
            {status === 'PENDING_APPROVAL' && (
              <AuthButton perm="defect:approve" type="primary" onClick={() => openModal('approve')}>
                处置审批
              </AuthButton>
            )}
            {status === 'PROCESSING' && disposition === 'RECTIFY' && !rect && (
              <AuthButton perm="rect:save" onClick={() => openModal('rectCreate')}>
                签发整改单
              </AuthButton>
            )}
            {status === 'PROCESSING' && disposition === 'CONCESSION' && (
              <AuthButton perm="defect:concession" type="primary" danger onClick={() => openModal('concession')}>
                让步接收审批
              </AuthButton>
            )}
            {status === 'PROCESSING' && disposition && disposition !== 'CONCESSION' && (
              <AuthButton
                perm="defect:execute"
                type="primary"
                onClick={() => openModal('execute')}
              >
                登记处置完成
              </AuthButton>
            )}
            {status === 'PENDING_RECHECK' && (
              <AuthButton perm="defect:close" type="primary" onClick={() => openModal('close')}>
                复检合格闭环
              </AuthButton>
            )}
            {status === 'PENDING_REVIEW' && (
              <AuthButton perm="defect:close" danger onClick={() => openModal('cancel')}>
                撤销
              </AuthButton>
            )}
          </Space>
        )
      }
      loading={detailQuery.isLoading}
    >
      {d && (
        <>
          {isA && (
            <Alert
              type="error"
              showIcon
              style={{ marginBottom: 16 }}
              message="存在严重（A类）缺陷：禁止让步接收/改判，必须退货、销毁等实质处置并复检合格后方可闭环"
            />
          )}

          <Descriptions column={3} bordered size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="来源报告">{d.reportNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="检验任务">
              {d.caseObj.taskId ? (
                <a onClick={() => navigate(`/inspection/tasks/${d.caseObj.taskId}`)}>{d.taskNo}</a>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="样品">{d.sampleNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="产品" span={2}>
              {d.productName} {d.spec}
            </Descriptions.Item>
            <Descriptions.Item label="批号">{d.batchNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="供应商">{d.supplierName || '-'}</Descriptions.Item>
            <Descriptions.Item label="报告结论">
              {d.conclusion ? (
                <Tag color={conclusionMeta(d.conclusion)?.color}>
                  {conclusionMeta(d.conclusion)?.label || d.conclusion}
                </Tag>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="A/B/C 不合格数">{`${d.aFailCount} / ${d.bFailCount} / ${d.cFailCount}`}</Descriptions.Item>
            <Descriptions.Item label="处置方式">
              {disposition ? dispositionMeta(disposition)?.label || disposition : <span style={{ color: '#999' }}>待评审</span>}
            </Descriptions.Item>
            <Descriptions.Item label="根本原因" span={2}>
              {d.caseObj.rootCause || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="闭环时间">
              {d.caseObj.closedAt?.replace('T', ' ').slice(0, 16) || '-'}
            </Descriptions.Item>
          </Descriptions>

          <Card type="inner" title={`缺陷明细（${d.items.length} 项）`} style={{ marginBottom: 16 }}>
            <Table<DefectItem>
              rowKey={(r) => String(r.id)}
              size="small"
              columns={itemColumns}
              dataSource={d.items}
              pagination={false}
            />
          </Card>

          {rect && (
            <Card
              type="inner"
              title={
                <Space>
                  <span>供应商整改单 {rect.rect.rectifyNo}</span>
                  <Tag color={rectStatusMeta(rect.rect.status)?.color}>
                    {rectStatusMeta(rect.rect.status)?.label || rect.rect.status}
                  </Tag>
                </Space>
              }
              style={{ marginBottom: 16 }}
              extra={
                <Space wrap>
                  {(rect.rect.status === 'ISSUED' || rect.rect.status === 'FAILED') && (
                    <AuthButton perm="rect:save" onClick={() => openModal('rectReply')}>
                      回复整改
                    </AuthButton>
                  )}
                  {rect.rect.status === 'REPLIED' && (
                    <AuthButton perm="rect:verify" type="primary" onClick={() => openModal('rectVerify')}>
                      质量验证
                    </AuthButton>
                  )}
                  {rect.rect.recheckTaskId && (
                    <Button onClick={() => navigate(`/inspection/tasks/${rect.rect.recheckTaskId}`)}>
                      查看复检任务{rect.recheckTaskNo ? `（${rect.recheckTaskNo}）` : ''}
                    </Button>
                  )}
                </Space>
              }
            >
              <Descriptions column={2} bordered size="small">
                <Descriptions.Item label="供应商">{rect.supplierName || '-'}</Descriptions.Item>
                <Descriptions.Item label="关联不合格单">{rect.caseNo || '-'}</Descriptions.Item>
                <Descriptions.Item label="问题描述" span={2}>
                  {rect.rect.issueDesc}
                </Descriptions.Item>
                <Descriptions.Item label="纠正措施" span={2}>
                  {rect.rect.correctiveAction || <span style={{ color: '#999' }}>待供应商回复</span>}
                </Descriptions.Item>
                <Descriptions.Item label="计划完成日期">{rect.rect.planFinishDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="实际完成日期">{rect.rect.actualFinishDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="验证人">{rect.verifierName || '-'}</Descriptions.Item>
                <Descriptions.Item label="验证时间">
                  {rect.rect.verifiedAt?.replace('T', ' ').slice(0, 16) || '-'}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          <Card type="inner" title="审批时间线（全程留痕，只增不改）">
            <Timeline
              items={d.timeline.map((t: DefectApprovalRow) => ({
                color: ACTION_COLOR[t.action] || 'gray',
                children: (
                  <div>
                    <Space size={6} wrap>
                      <Tag>{NODE_LABEL[t.node] || t.node}</Tag>
                      <strong>{t.action}</strong>
                      <span style={{ color: '#999', fontSize: 12 }}>
                        {t.operatedAt?.replace('T', ' ').slice(0, 19)}
                      </span>
                      <span style={{ color: '#666', fontSize: 12 }}>
                        {t.operatorName || '系统'}
                      </span>
                    </Space>
                    {t.comment && <div style={{ marginTop: 4 }}>{t.comment}</div>}
                    {t.attachmentIds && (
                      <div style={{ marginTop: 4, color: '#999', fontSize: 12 }}>
                        附件：{t.attachmentIds}
                      </div>
                    )}
                  </div>
                ),
              }))}
            />
          </Card>
        </>
      )}

      {/* 质量评审 */}
      <Modal
        title="质量评审"
        open={modal === 'review'}
        onCancel={() => setModal('')}
        confirmLoading={reviewMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            reviewMut.mutate({
              disposition: v.disposition,
              rootCause: v.rootCause,
              comment: v.comment?.trim() || undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="disposition"
            label="处置方式"
            rules={[{ required: true, message: '请选择处置方式' }]}
          >
            <Select
              options={DISPOSITIONS.map((x) => ({ value: x.value, label: x.label }))}
              placeholder="选择处置方式"
            />
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {() =>
              form.getFieldValue('disposition') === 'CONCESSION' && isA ? (
                <Alert type="error" showIcon style={{ marginBottom: 12 }} message="A类缺陷禁止让步接收" />
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="rootCause"
            label="根本原因分析"
            rules={[{ required: true, message: '请填写根本原因' }]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="comment" label="评审意见">
            <Input.TextArea rows={2} maxLength= {500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 主管审批 */}
      <Modal
        title="主管审批（密码二次认证）"
        open={modal === 'approve'}
        onCancel={() => setModal('')}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) =>
            approveMut.mutate({
              action: v.action,
              password: v.password || undefined,
              comment: v.comment?.trim() || undefined,
            })
          }
        >
          <Form.Item name="action" label="审批结论" initialValue="PASS" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'PASS', label: '通过（进入处置执行）' },
                { value: 'REJECT', label: '驳回（退回质量评审）' },
              ]}
            />
          </Form.Item>
          <Form.Item shouldUpdate noStyle>
            {() =>
              form.getFieldValue('action') === 'PASS' ? (
                <Form.Item
                  name="password"
                  label="登录密码"
                  rules={[{ required: true, message: '通过审批必须输入密码二次认证' }]}
                >
                  <Input.Password placeholder="请输入登录密码" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (getFieldValue('action') === 'REJECT' && (!value || !value.trim())) {
                    return Promise.reject(new Error('驳回必须填写意见'));
                  }
                  return Promise.resolve();
                },
              }),
            ]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Space style={{ float: 'right' }}>
            <Button onClick={() => setModal('')}>取消</Button>
            <Button type="primary" htmlType="submit" loading={approveMut.isPending}>
              提交审批
            </Button>
          </Space>
        </Form>
      </Modal>

      {/* 登记处置完成 */}
      <Modal
        title="登记处置完成"
        open={modal === 'execute'}
        onCancel={() => setModal('')}
        confirmLoading={executeMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            executeMut.mutate({ comment: v.comment, attachmentIds: v.attachmentIds?.trim() || undefined }),
          )
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="登记后进入复检闭环；供应商整改方式须整改验证通过并复检合格。"
        />
        <Form form={form} layout="vertical">
          <Form.Item name="comment" label="处置情况说明" rules={[{ required: true, message: '请填写处置情况' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="attachmentIds" label="凭证附件ID（多个逗号分隔）">
            <Input placeholder="如：1234567890,1234567891" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 让步接收 */}
      <Modal
        title="让步接收审批"
        open={modal === 'concession'}
        onCancel={() => setModal('')}
        confirmLoading={concessionMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            concessionMut.mutate({ password: v.password, comment: v.comment }),
          )
        }
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="让步接收直接闭环且计入供应商不合格评级；A类缺陷系统强制禁止。"
        />
        <Form form={form} layout="vertical">
          <Form.Item name="password" label="登录密码" rules={[{ required: true, message: '请输入密码二次认证' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="comment" label="让步理由" rules={[{ required: true, message: '请填写让步理由' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {/* 撤销 */}
      <Modal
        title="撤销不合格单"
        open={modal === 'cancel'}
        onCancel={() => setModal('')}
        confirmLoading={cancelMut.isPending}
        onOk={() =>
          form.validateFields().then((v) => cancelMut.mutate(v.comment.trim()))
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="comment" label="撤销原因" rules={[{ required: true, message: '请填写撤销原因' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {/* 闭环确认 */}
      <Modal
        title="闭环确认"
        open={modal === 'close'}
        onCancel={() => setModal('')}
        confirmLoading={closeMut.isPending}
        onOk={() =>
          form.validateFields().then((v) => closeMut.mutate(v.comment?.trim() || undefined))
        }
      >
        <Alert type="success" showIcon style={{ marginBottom: 12 }} message="复检合格后关闭不合格单，并沉淀供应商当期评级。" />
        <Form form={form} layout="vertical">
          <Form.Item name="comment" label="闭环备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 签发整改单 */}
      <Modal
        title="签发供应商整改单"
        open={modal === 'rectCreate'}
        onCancel={() => setModal('')}
        confirmLoading={rectCreateMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            rectCreateMut.mutate({
              issueDesc: v.issueDesc.trim(),
              planFinishDate: v.planFinishDate ? v.planFinishDate.format('YYYY-MM-DD') : undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="issueDesc" label="整改问题描述" rules={[{ required: true, message: '请填写问题描述' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="planFinishDate" label="要求完成日期">
            <DatePicker style={{ width: '100%' }} disabledDate={(d0) => d0.isBefore(dayjs().startOf('day'))} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 供应商回复 */}
      <Modal
        title="整改回复（纠正措施）"
        open={modal === 'rectReply'}
        onCancel={() => setModal('')}
        confirmLoading={rectReplyMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            rectReplyMut.mutate({
              correctiveAction: v.correctiveAction.trim(),
              planFinishDate: v.planFinishDate ? v.planFinishDate.format('YYYY-MM-DD') : undefined,
              actualFinishDate: v.actualFinishDate ? v.actualFinishDate.format('YYYY-MM-DD') : undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="correctiveAction" label="纠正/预防措施" rules={[{ required: true, message: '请填写纠正措施' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <Space size={12}>
            <Form.Item name="planFinishDate" label="计划完成日期">
              <DatePicker disabledDate={(d0) => d0.isBefore(dayjs().startOf('day'))} />
            </Form.Item>
            <Form.Item name="actualFinishDate" label="实际完成日期">
              <DatePicker />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 质量验证 */}
      <Modal
        title="整改验证（密码二次认证，通过即生成复检任务）"
        open={modal === 'rectVerify'}
        onCancel={() => setModal('')}
        confirmLoading={rectVerifyMut.isPending}
        onOk={() =>
          form.validateFields().then((v) =>
            rectVerifyMut.mutate({
              result: verifyResult,
              password: v.password,
              comment: v.comment?.trim() || undefined,
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item label="验证结论" required>
            <Select
              value={verifyResult}
              style={{ width: 260 }}
              onChange={(v) => setVerifyResult(v)}
              options={[
                { value: 'PASS', label: '通过：发起复检任务' },
                { value: 'FAIL', label: '不通过：退回重新处置' },
              ]}
            />
          </Form.Item>
          <Form.Item name="password" label="登录密码" rules={[{ required: true, message: '请输入密码二次认证' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="comment" label="验证意见">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default CaseDetailPage;
