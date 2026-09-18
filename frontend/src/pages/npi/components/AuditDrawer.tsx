import React, { useEffect, useMemo, useState } from 'react';
import { Button, DatePicker, Drawer, Dropdown, Form, Input, InputNumber, Select, Space, Table, Tag } from 'antd';
import { CheckCircleOutlined, DownOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { auditApi } from '@/api/npi';
import { supplierApi } from '@/api/supplier';
import {
  AUDIT_CATEGORIES,
  AUDIT_CONCLUSION,
  AUDIT_ITEM_RESULTS,
  AUDIT_STATUS,
} from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { AuditItemInput, AuditUpsert, FactoryAuditItem } from '@/types/npi';
import message from '@/utils/feedback';

interface FormValues {
  supplierId?: LongId;
  auditType?: string;
  plannedAt?: dayjs.Dayjs;
  auditedAt?: dayjs.Dayjs;
  auditors?: string;
  rectifyRequirement?: string;
  rectifyDeadline?: dayjs.Dayjs;
  remark?: string;
}

const catLabel = (c: string) => AUDIT_CATEGORIES.find((x) => x.value === c)?.label || c;
const statusMeta = (s: string) => AUDIT_STATUS.find((x) => x.value === s);

/** 实地验厂登记/评分/结论确认抽屉。 */
const AuditDrawer: React.FC<{
  id: LongId | null;
  projectId: LongId;
  defaultSupplierId?: LongId | null;
  onClose: () => void;
}> = ({ id, projectId, defaultSupplierId, onClose }) => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [rows, setRows] = useState<AuditItemInput[]>([]);

  const suppliers = useQuery({
    queryKey: ['supplier-options'],
    queryFn: () => supplierApi.page({ pageNo: 1, pageSize: 100 }),
  });
  const defaults = useQuery({
    queryKey: ['audit-default-items'],
    queryFn: () => auditApi.defaultItems(),
    enabled: id === null,
  });
  const detail = useQuery({
    queryKey: ['npi-audit', id],
    queryFn: () => auditApi.detail(id as LongId),
    enabled: id !== null,
  });

  useEffect(() => {
    if (id === null) {
      form.resetFields();
      form.setFieldsValue({
        auditType: 'INITIAL',
        supplierId: defaultSupplierId ?? undefined,
        auditedAt: dayjs(),
      });
      setRows(
        (defaults.data || []).map((it: FactoryAuditItem) => ({
          category: it.category,
          itemName: it.itemName,
          requirement: it.requirement || undefined,
          maxScore: Number(it.maxScore ?? 100),
          score: null,
          result: null,
          sort: it.sort,
        })),
      );
      return;
    }
    if (detail.data) {
      const a = detail.data.audit;
      form.setFieldsValue({
        supplierId: a.supplierId,
        auditType: a.auditType,
        plannedAt: a.plannedAt ? dayjs(a.plannedAt) : undefined,
        auditedAt: a.auditedAt ? dayjs(a.auditedAt) : dayjs(),
        auditors: a.auditors || undefined,
        rectifyRequirement: a.rectifyRequirement || undefined,
        rectifyDeadline: a.rectifyDeadline ? dayjs(a.rectifyDeadline) : undefined,
        remark: a.remark || undefined,
      });
      setRows(
        detail.data.items.map((it) => ({
          category: it.category,
          itemName: it.itemName,
          requirement: it.requirement || undefined,
          maxScore: Number(it.maxScore ?? 100),
          score: it.score !== null && it.score !== undefined ? Number(it.score) : null,
          result: it.result || null,
          note: it.note || undefined,
          sort: it.sort,
        })),
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, detail.data, defaults.data]);

  const readOnly = detail.data?.audit.status === 'CONFIRMED';

  const scoredRows = useMemo(
    () => rows.filter((r) => r.score !== null && r.result !== 'NA'),
    [rows],
  );
  const previewTotal = useMemo(() => {
    if (scoredRows.length === 0) return null;
    const denom = scoredRows.reduce((s, r) => s + (r.maxScore || 100), 0);
    const numer = scoredRows.reduce((s, r) => s + (r.score || 0) * (r.maxScore || 100), 0);
    return denom > 0 ? (numer / denom).toFixed(2) : null;
  }, [scoredRows]);

  const save = useMutation({
    mutationFn: async (confirmConclusion?: string) => {
      const v = await form.validateFields();
      if (confirmConclusion === 'CONDITIONAL' && !v.rectifyRequirement) {
        throw new Error('限期整改必须填写整改要求');
      }
      const payload: AuditUpsert = {
        id: id ?? undefined,
        projectId,
        supplierId: v.supplierId!,
        auditType: v.auditType || 'INITIAL',
        plannedAt: v.plannedAt ? v.plannedAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        auditedAt: v.auditedAt ? v.auditedAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        auditors: v.auditors,
        rectifyRequirement: v.rectifyRequirement,
        rectifyDeadline: v.rectifyDeadline ? v.rectifyDeadline.format('YYYY-MM-DD') : undefined,
        remark: v.remark,
        confirmConclusion: confirmConclusion ?? null,
        items: rows.map((r, i) => ({ ...r, sort: i })),
      };
      return auditApi.save(payload);
    },
    onSuccess: (_newId, conclusion) => {
      message.success(conclusion ? `验厂结论已确认：${AUDIT_CONCLUSION.find((c) => c.value === conclusion)?.label}` : '验厂单已保存');
      void queryClient.invalidateQueries({ queryKey: ['npi'] });
      onClose();
    },
    onError: (e: Error) => message.error(e.message),
  });

  const updateRow = (idx: number, patch: Partial<AuditItemInput>) => {
    setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
  };

  const audit = detail.data?.audit;

  return (
    <Drawer
      width={1080}
      open
      onClose={onClose}
      destroyOnHidden
      title={
        audit
          ? `验厂单 ${audit.auditNo}（${audit.auditType === 'FOLLOW_UP' ? '复审' : '初审'}）`
          : '新建实地验厂单'
      }
      extra={
        readOnly ? (
          <Space>
            {audit?.conclusion && (
              <Tag color={AUDIT_CONCLUSION.find((c) => c.value === audit.conclusion)?.color}>
                结论：{AUDIT_CONCLUSION.find((c) => c.value === audit.conclusion)?.label}
              </Tag>
            )}
            <Tag color="green">已确认（只读）</Tag>
          </Space>
        ) : (
          <Space>
            {previewTotal && <Tag color="blue">加权总分预览：{previewTotal}</Tag>}
            <Button loading={save.isPending} onClick={() => save.mutate(undefined)}>保存评分</Button>
            <Dropdown
              menu={{
                items: AUDIT_CONCLUSION.map((c) => ({
                  key: c.value,
                  label: (
                    <Space>
                      <Tag color={c.color} style={{ margin: 0 }}>{c.label}</Tag>
                      确认结论
                    </Space>
                  ),
                })),
                onClick: ({ key }) => save.mutate(key),
              }}
            >
              <Button type="primary" loading={save.isPending}>
                <CheckCircleOutlined /> 确认验厂结论 <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        )
      }
    >
      <Form form={form} layout="vertical" disabled={readOnly}>
        <Space style={{ display: 'flex' }} align="start" wrap>
          <Form.Item name="supplierId" label="受验供应商" style={{ minWidth: 260 }} rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={id !== null}
              placeholder="选择供应商"
              options={(suppliers.data?.records || []).map((s) => ({ value: s.id, label: s.supplierName }))}
            />
          </Form.Item>
          <Form.Item name="auditType" label="验厂类型" style={{ width: 130 }}>
            <Select
              options={[
                { value: 'INITIAL', label: '初审' },
                { value: 'FOLLOW_UP', label: '整改复审' },
              ]}
            />
          </Form.Item>
          <Form.Item name="plannedAt" label="计划时间" style={{ width: 200 }}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="auditedAt" label="实审时间" style={{ width: 200 }}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="auditors" label="验厂组成员" style={{ minWidth: 220, flex: 1 }}>
            <Input placeholder="如 赵主管、李检验" maxLength={500} />
          </Form.Item>
        </Space>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="rectifyRequirement" label="整改要求（限期整改时必填）" style={{ flex: 2 }}>
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
          <Form.Item name="rectifyDeadline" label="整改截止日期" style={{ flex: 1 }}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input maxLength={500} />
        </Form.Item>
      </Form>

      <Table<AuditItemInput & { __i: number }>
        size="small"
        rowKey={(_, i) => String(i)}
        pagination={false}
        scroll={{ x: 980 }}
        dataSource={rows.map((r, i) => ({ ...r, __i: i }))}
        title={() => (
          <Space>
            <span>检查表（资质 / 生产 / 仓储 / 冷链 / 质量体系）</span>
            {audit && <Tag color={statusMeta(audit.status)?.color}>{statusMeta(audit.status)?.label}</Tag>}
          </Space>
        )}
        columns={[
          { title: '类别', dataIndex: 'category', width: 90, render: (v: string) => <Tag>{catLabel(v)}</Tag> },
          { title: '检查项', dataIndex: 'itemName', width: 200 },
          { title: '合规要求', dataIndex: 'requirement', width: 240 },
          {
            title: '得分',
            dataIndex: 'score',
            width: 100,
            render: (v: number | null, r) =>
              readOnly ? (
                v ?? '-'
              ) : (
                <InputNumber
                  min={0}
                  max={r.maxScore || 100}
                  style={{ width: 86 }}
                  value={v}
                  onChange={(val) => updateRow(r.__i, { score: val })}
                />
              ),
          },
          {
            title: '结果',
            dataIndex: 'result',
            width: 120,
            render: (v: string | null, r) =>
              readOnly ? (
                <Tag color={AUDIT_ITEM_RESULTS.find((x) => x.value === v)?.color}>
                  {AUDIT_ITEM_RESULTS.find((x) => x.value === v)?.label || '-'}
                </Tag>
              ) : (
                <Select
                  size="small"
                  allowClear
                  style={{ width: 108 }}
                  value={v}
                  placeholder="选择"
                  options={AUDIT_ITEM_RESULTS}
                  onChange={(val) => updateRow(r.__i, { result: val })}
                />
              ),
          },
          {
            title: '问题记录',
            dataIndex: 'note',
            render: (v: string, r) =>
              readOnly ? (
                v || '-'
              ) : (
                <Input size="small" value={v} onChange={(e) => updateRow(r.__i, { note: e.target.value })} />
              ),
          },
        ]}
      />
    </Drawer>
  );
};

export default AuditDrawer;
