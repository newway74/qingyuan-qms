import React, { useEffect } from 'react';
import { Button, DatePicker, Drawer, Form, Input, Select, Space, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { extTestApi } from '@/api/npi';
import { supplierApi } from '@/api/supplier';
import { EXT_CONCLUSION, EXT_STATUS, LAB_QUALIFICATIONS } from '@/constants/qms';
import type { LongId } from '@/types/auth';
import type { ExtTestUpsert } from '@/types/npi';
import message from '@/utils/feedback';

interface FormValues {
  supplierId?: LongId;
  productName?: string;
  sampleDesc?: string;
  labName?: string;
  labQualification?: string;
  testItems?: string;
  sentAt?: dayjs.Dayjs;
  reportNo?: string;
  reportDate?: dayjs.Dayjs;
  conclusion?: string;
  remark?: string;
}

/** 外检送检登记/寄出/报告回填抽屉（状态由后端按字段自动推导）。 */
const ExtTestDrawer: React.FC<{
  id: LongId | null;
  projectId: LongId;
  defaultSupplierId?: LongId | null;
  onClose: () => void;
}> = ({ id, projectId, defaultSupplierId, onClose }) => {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();

  const suppliers = useQuery({
    queryKey: ['supplier-options'],
    queryFn: () => supplierApi.page({ pageNo: 1, pageSize: 100 }),
  });
  const detail = useQuery({
    queryKey: ['npi-exttest', id],
    queryFn: () => extTestApi.detail(id as LongId),
    enabled: id !== null,
  });

  useEffect(() => {
    if (id === null) {
      form.resetFields();
      form.setFieldsValue({
        supplierId: defaultSupplierId ?? undefined,
        labQualification: 'CMA',
        conclusion: 'PENDING',
        sentAt: dayjs(),
      });
      return;
    }
    if (detail.data) {
      const t = detail.data;
      form.setFieldsValue({
        supplierId: t.supplierId,
        productName: t.productName || undefined,
        sampleDesc: t.sampleDesc || undefined,
        labName: t.labName || undefined,
        labQualification: t.labQualification || undefined,
        testItems: t.testItems || undefined,
        sentAt: t.sentAt ? dayjs(t.sentAt) : undefined,
        reportNo: t.reportNo || undefined,
        reportDate: t.reportDate ? dayjs(t.reportDate) : undefined,
        conclusion: t.conclusion || 'PENDING',
        remark: t.remark || undefined,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, detail.data]);

  const save = useMutation({
    mutationFn: async () => {
      const v = await form.validateFields();
      const payload: ExtTestUpsert = {
        id: id ?? undefined,
        projectId,
        supplierId: v.supplierId!,
        productName: v.productName,
        sampleDesc: v.sampleDesc,
        labName: v.labName,
        labQualification: v.labQualification,
        testItems: v.testItems,
        sentAt: v.sentAt ? v.sentAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        reportNo: v.reportNo,
        reportDate: v.reportDate ? v.reportDate.format('YYYY-MM-DD') : undefined,
        conclusion: v.conclusion || 'PENDING',
        remark: v.remark,
      };
      return extTestApi.save(payload);
    },
    onSuccess: () => {
      message.success('外检单已保存');
      void queryClient.invalidateQueries({ queryKey: ['npi'] });
      onClose();
    },
  });

  const test = detail.data;

  return (
    <Drawer
      width={760}
      open
      onClose={onClose}
      destroyOnHidden
      title={test ? `外检单 ${test.testNo}` : '登记外检送检'}
      extra={
        test ? (
          <Tag color={EXT_STATUS.find((s) => s.value === test.status)?.color}>
            {EXT_STATUS.find((s) => s.value === test.status)?.label}
          </Tag>
        ) : undefined
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={() => save.mutate()}
        initialValues={{ labQualification: 'CMA', conclusion: 'PENDING' }}
      >
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="supplierId" label="送检供应商" style={{ flex: 1 }} rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              disabled={id !== null}
              placeholder="选择供应商"
              options={(suppliers.data?.records || []).map((s) => ({ value: s.id, label: s.supplierName }))}
            />
          </Form.Item>
          <Form.Item name="productName" label="送检样品名称（规格）" style={{ flex: 1 }}>
            <Input maxLength={200} />
          </Form.Item>
        </Space>
        <Form.Item name="sampleDesc" label="样品描述（批号/份数）">
          <Input maxLength={500} />
        </Form.Item>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="labName" label="检测机构" style={{ flex: 2 }}>
            <Input maxLength={200} placeholder="如 浙江省检验检疫科学技术研究院" />
          </Form.Item>
          <Form.Item name="labQualification" label="机构资质" style={{ flex: 1 }}>
            <Select options={LAB_QUALIFICATIONS} />
          </Form.Item>
        </Space>
        <Form.Item name="testItems" label="送检项目清单">
          <Input.TextArea rows={2} maxLength={1000} placeholder="如 二氧化硫、铅、农残18项、菌落总数、致病菌" />
        </Form.Item>
        <Space style={{ display: 'flex' }} align="start">
          <Form.Item name="sentAt" label="寄样时间" style={{ flex: 1 }} tooltip="填写寄样时间后单据自动进入“已送检”">
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reportNo" label="报告编号" style={{ flex: 1 }} tooltip="填写报告号且结论非检测中，自动进入“已出报告”">
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="reportDate" label="报告日期" style={{ flex: 1 }}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Space>
        <Form.Item name="conclusion" label="检测结论">
          <Select options={EXT_CONCLUSION} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={500} />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={save.isPending}>保存</Button>
      </Form>
    </Drawer>
  );
};

export default ExtTestDrawer;
