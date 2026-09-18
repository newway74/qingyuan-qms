import React, { useEffect, useState } from 'react';
import { Button, DatePicker, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { evalApi } from '@/api/npi';
import { supplierApi } from '@/api/supplier';
import { EVAL_DIMENSIONS, EVAL_QUALITY } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { EvalItemInput, EvalUpsert } from '@/types/npi';
import message from '@/utils/feedback';

interface FormValues {
  supplierId?: LongId;
  roundNo?: number;
  sampleDesc?: string;
  qualityConclusion?: string;
  receivedAt?: dayjs.Dayjs;
  remark?: string;
  items: EvalItemInput[];
}

/** 送样评估登记/评分抽屉（5 维度加权评分）。 */
const EvalDrawer: React.FC<{
  id: LongId | null;
  projectId: LongId;
  onClose: () => void;
}> = ({ id, projectId, onClose }) => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [scores, setScores] = useState<Record<string, number | null>>({});
  const suppliers = useQuery({
    queryKey: ['supplier-options'],
    queryFn: () => supplierApi.page({ pageNo: 1, pageSize: 100 }),
  });
  const detail = useQuery({
    queryKey: ['npi-eval', id],
    queryFn: () => evalApi.detail(id as LongId),
    enabled: id !== null,
  });

  useEffect(() => {
    if (id === null) {
      form.setFieldsValue({
        roundNo: 1,
        qualityConclusion: 'PENDING',
        items: EVAL_DIMENSIONS.map((d) => ({
          dimensionCode: d.code,
          dimensionName: d.name,
          weight: d.defaultWeight,
        })),
      });
      setScores({});
      return;
    }
    if (detail.data) {
      const e = detail.data.eval;
      const scoreMap: Record<string, number | null> = {};
      form.setFieldsValue({
        supplierId: e.supplierId,
        roundNo: e.roundNo,
        sampleDesc: e.sampleDesc || undefined,
        qualityConclusion: e.qualityConclusion || 'PENDING',
        receivedAt: e.receivedAt ? dayjs(e.receivedAt) : undefined,
        remark: e.remark || undefined,
        items: EVAL_DIMENSIONS.map((d) => {
          const found = detail.data!.items.find((it) => it.dimensionCode === d.code);
          scoreMap[d.code] = found?.score !== undefined && found.score !== null ? Number(found.score) : null;
          return {
            dimensionCode: d.code,
            dimensionName: d.name,
            weight: found?.weight !== undefined && found.weight !== null ? Number(found.weight) : d.defaultWeight,
            note: found?.note || undefined,
          };
        }),
      });
      setScores(scoreMap);
    }
  }, [id, detail.data, form]);

  const readOnly = detail.data?.eval.status === 'SUBMITTED';

  const save = useMutation({
    mutationFn: async (submit: boolean) => {
      const v = await form.validateFields();
      const payload: EvalUpsert = {
        id: id ?? undefined,
        projectId,
        supplierId: v.supplierId!,
        roundNo: v.roundNo ?? 1,
        sampleDesc: v.sampleDesc,
        qualityConclusion: v.qualityConclusion || 'PENDING',
        receivedAt: v.receivedAt ? v.receivedAt.format('YYYY-MM-DDTHH:mm:ss') : dayjs().format('YYYY-MM-DDTHH:mm:ss'),
        remark: v.remark,
        items: v.items.map((it, i) => ({
          dimensionCode: EVAL_DIMENSIONS[i].code,
          dimensionName: EVAL_DIMENSIONS[i].name,
          score: scores[EVAL_DIMENSIONS[i].code] ?? null,
          weight: it.weight ?? EVAL_DIMENSIONS[i].defaultWeight,
          note: it.note,
        })),
      };
      const newId = await evalApi.save(payload);
      if (submit) await evalApi.submit(id ?? newId);
    },
    onSuccess: (_d, submit) => {
      message.success(submit ? '评估已提交，总分与排名已更新' : '评估草稿已保存');
      void queryClient.invalidateQueries({ queryKey: ['npi'] });
      onClose();
    },
  });

  const watchedItems = Form.useWatch('items', form) as EvalItemInput[] | undefined;
  const weightSum = EVAL_DIMENSIONS.reduce((sum, d) => {
    const w = watchedItems?.[EVAL_DIMENSIONS.findIndex((x) => x.code === d.code)]?.weight;
    return sum + (Number(w) || 0);
  }, 0);
  const scoreSum = EVAL_DIMENSIONS.reduce((sum, d) => {
    const w = watchedItems?.[EVAL_DIMENSIONS.findIndex((x) => x.code === d.code)]?.weight;
    return sum + (scores[d.code] ?? 0) * (Number(w) || 0);
  }, 0);
  const previewTotal = weightSum > 0 ? (scoreSum / weightSum).toFixed(2) : '-';

  return (
    <Drawer
      title={id ? `送样评估 ${detail.data?.eval.evalNo || ''}` : '登记送样评估'}
      width={860}
      open
      onClose={onClose}
      destroyOnHidden
      extra={
        readOnly ? (
          <Tag color="green">已提交（只读）</Tag>
        ) : (
          <Space>
            <Tag color="blue">加权总分预览：{previewTotal}</Tag>
            <Button loading={save.isPending} onClick={() => save.mutate(false)}>保存草稿</Button>
            <Button type="primary" loading={save.isPending} onClick={() => save.mutate(true)}>
              保存并提交
            </Button>
          </Space>
        )
      }
    >
      <Form form={form} layout="vertical" disabled={readOnly}>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="supplierId" label="送样供应商" style={{ flex: 1 }} rules={[{ required: true, message: '请选择供应商' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择送样供应商"
              disabled={id !== null}
              options={(suppliers.data?.records || []).map((s) => ({
                value: s.id,
                label: `${s.supplierName}（${s.status === 'QUALIFIED' ? '合格' : s.status === 'CONTROLLED' ? '受控' : '禁用'}）`,
              }))}
            />
          </Form.Item>
          <Form.Item name="roundNo" label="送样轮次" style={{ width: 110 }}>
            <InputNumber min={1} max={9} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="receivedAt" label="收样时间" style={{ flex: 1 }}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="sampleDesc" label="样品描述（规格/批号/数量）">
          <Input maxLength={500} />
        </Form.Item>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="qualityConclusion" label="对照标准检验结论" style={{ flex: 1 }}>
            <Select options={EVAL_QUALITY} />
          </Form.Item>
          <Form.Item name="remark" label="备注" style={{ flex: 2 }}>
            <Input maxLength={500} />
          </Form.Item>
        </Space>

        <Table
          size="small"
          rowKey="code"
          pagination={false}
          dataSource={EVAL_DIMENSIONS}
          columns={[
            { title: '评估维度', dataIndex: 'name', width: 130 },
            {
              title: '得分(0-100)',
              width: 140,
              render: (_, d) => (
                <InputNumber
                  min={0}
                  max={100}
                  style={{ width: 120 }}
                  value={scores[d.code]}
                  onChange={(v) => setScores((prev) => ({ ...prev, [d.code]: v }))}
                />
              ),
            },
            {
              title: '权重',
              width: 110,
              render: (_, d, idx) => (
                <Form.Item name={['items', idx, 'weight']} noStyle>
                  <InputNumber min={0} max={100} style={{ width: 90 }} />
                </Form.Item>
              ),
            },
            {
              title: '评价说明',
              render: (_, d, idx) => (
                <Form.Item name={['items', idx, 'note']} noStyle>
                  <Input placeholder={`${d.name}评价`} />
                </Form.Item>
              ),
            },
          ]}
        />
      </Form>
    </Drawer>
  );
};

export default EvalDrawer;
