import React, { useEffect, useState } from 'react';
import { DatePicker, Form, Input, InputNumber, Modal, Radio, Switch } from 'antd';
import dayjs from 'dayjs';
import { sampleApi } from '@/api/sample';
import type { SampleReceive, SamplingVO } from '@/types/inspection';
import type { LongId } from '@/types/auth';

interface Props {
  sampling: SamplingVO | null;
  onClose: () => void;
  onSuccess: () => void;
}

const ReceiveModal: React.FC<Props> = ({ sampling, onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [mismatch, setMismatch] = useState(false);
  const [retain, setRetain] = useState(true);

  useEffect(() => {
    if (sampling) {
      form.setFieldsValue({
        packageBatchCheck: 'MATCH',
        productionDate: sampling.productionDate ? dayjs(sampling.productionDate) : undefined,
        expiryDate: sampling.expiryDate ? dayjs(sampling.expiryDate) : undefined,
        inspectionCount: 1,
        retainFlag: true,
        backupFlag: false,
        retainUntil: sampling.expiryDate ? dayjs(sampling.expiryDate) : undefined,
      });
      setMismatch(false);
      setRetain(true);
    }
  }, [sampling, form]);

  const handleOk = async () => {
    // 校验失败时由表单单行错误提示；reject 若上抛会让 Modal 确认按钮 Promise reject，
    // antd 会保持 confirmLoading 并将 modal-body 置为 pointer-events:none，弹窗永久锁死
    let v;
    try {
      v = await form.validateFields();
    } catch {
      return;
    }
    if (!sampling) return;
    const payload: SampleReceive = {
      samplingId: sampling.id as LongId,
      packageBatchCheck: v.packageBatchCheck,
      packageBatchNote: v.packageBatchNote,
      productionDate: v.productionDate ? v.productionDate.format('YYYY-MM-DD') : undefined,
      expiryDate: v.expiryDate ? v.expiryDate.format('YYYY-MM-DD') : undefined,
      inspectionCount: v.inspectionCount,
      retainFlag: v.retainFlag,
      retainLocation: v.retainFlag ? v.retainLocation : undefined,
      retainUntil: v.retainFlag && v.retainUntil ? v.retainUntil.format('YYYY-MM-DD') : undefined,
      backupFlag: v.backupFlag,
    };
    setSaving(true);
    try {
      await sampleApi.receive(payload);
      onSuccess();
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={sampling ? `收样登记 - ${sampling.samplingNo}` : ''}
      open={!!sampling}
      onOk={handleOk}
      confirmLoading={saving}
      onCancel={onClose}
      width={560}
      destroyOnHidden
    >
      {sampling && (
        <Form form={form} layout="vertical">
          <Form.Item label="喷码批号/生产日期核对">
            <Form.Item
              name="packageBatchCheck"
              noStyle
              rules={[{ required: true, message: '请选择核对结果' }]}
            >
              <Radio.Group
                optionType="button"
                buttonStyle="solid"
                options={[
                  { value: 'MATCH', label: '一致' },
                  { value: 'MISMATCH', label: '不一致（需注明）' },
                ]}
                onChange={(e) => setMismatch(e.target.value === 'MISMATCH')}
              />
            </Form.Item>
          </Form.Item>
          <Form.Item
            name="packageBatchNote"
            label="核对备注/差异说明"
            rules={mismatch ? [{ required: true, message: '喷码信息不一致必须填写差异说明' }] : []}
          >
            <Input.TextArea rows={2} placeholder="不一致时必须填写差异说明" />
          </Form.Item>
          <Form.Item
            name="productionDate"
            label="生产日期"
            rules={[{ required: true, message: '请选择生产日期' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="expiryDate"
            label="保质期至"
            rules={[{ required: true, message: '请选择保质期至' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="inspectionCount"
            label="检验样数量"
            rules={[{ required: true, message: '至少1个检验样' }]}
            extra="每个检验样自动生成检验任务"
          >
            <InputNumber min={1} max={20} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="retainFlag" label="同时登记留样" valuePropName="checked">
            <Switch onChange={setRetain} />
          </Form.Item>
          {retain && (
            <>
              <Form.Item
                name="retainLocation"
                label="留样位置"
                rules={[{ required: true, message: '请填写留样位置' }]}
              >
                <Input placeholder="如：留样柜A-02" />
              </Form.Item>
              <Form.Item name="retainUntil" label="留样到期日">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
          <Form.Item name="backupFlag" label="同时登记备样" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
};

export default ReceiveModal;
