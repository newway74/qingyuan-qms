import React, { useState } from 'react';
import { Alert, Button, Empty, Form, Modal, Radio, Space, Statistic, Table, Tag, Upload } from 'antd';
import { InboxOutlined, DownloadOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import type { UploadFile } from 'antd/es/upload/interface';
import request from '@/api/request';
import { ledgerApi } from '@/api/ledger';
import type {
  GoodsImportResultVO,
  GoodsImportRowResult,
  GoodsImportStrategy,
} from '@/types/ledger';
import message from '@/utils/feedback';

interface Props {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

/**
 * 商品 Excel 批量导入弹窗：下载标准模板 → 选择文件 → 选策略 → 导入 → 结果页。
 * 结果页展示新建/更新/跳过/失败计数与逐行失败原因；失败行可回溯 Excel 行号。
 */
const GoodsImportModal: React.FC<Props> = ({ open, onClose, onImported }) => {
  const [strategy, setStrategy] = useState<GoodsImportStrategy>('UPDATE');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [result, setResult] = useState<GoodsImportResultVO | null>(null);
  const [downloading, setDownloading] = useState(false);

  const importMutation = useMutation({
    mutationFn: () => {
      const file = fileList[0]?.originFileObj;
      if (!file) {
        return Promise.reject(new Error('nofile'));
      }
      return ledgerApi.goodsImport(file, strategy);
    },
    onSuccess: (data) => {
      setResult(data);
      if (data.failedCount === 0) {
        message.success(`导入完成：新建 ${data.createdCount}，更新 ${data.updatedCount}，跳过 ${data.skippedCount}`);
      } else {
        message.warning(`导入完成：${data.failedCount} 行失败，请查看失败明细`);
      }
      onImported();
    },
    onError: (e: unknown) => {
      if (e instanceof Error && e.message === 'nofile') {
        message.warning('请先选择要导入的 .xlsx 文件');
      } else {
        message.error('导入失败，请检查文件格式后重试');
      }
    },
  });

  const downloadTemplate = async () => {
    setDownloading(true);
    try {
      const resp = await request.get('/ledger/goods/import-template', { responseType: 'blob' });
      const url = window.URL.createObjectURL(
        new Blob([resp as unknown as BlobPart], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        }),
      );
      const a = document.createElement('a');
      a.href = url;
      a.download = '商品批量导入模板.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      message.error('模板下载失败，请重试');
    } finally {
      setDownloading(false);
    }
  };

  const close = () => {
    setResult(null);
    setFileList([]);
    onClose();
  };

  const columns = [
    { title: 'Excel 行号', dataIndex: 'rowNum', width: 90 },
    { title: 'SKU', dataIndex: 'sku', width: 160, render: (v?: string) => v ?? '-' },
    {
      title: '结果',
      dataIndex: 'action',
      width: 90,
      render: (v: GoodsImportRowResult['action']) => {
        const map: Record<string, { color: string; text: string }> = {
          CREATED: { color: 'green', text: '新建' },
          UPDATED: { color: 'blue', text: '更新' },
          SKIPPED: { color: 'default', text: '跳过' },
          FAILED: { color: 'red', text: '失败' },
        };
        const meta = map[v] ?? { color: 'default', text: v };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '说明', dataIndex: 'reason', render: (v?: string) => v ?? '-' },
  ];

  return (
    <Modal
      title="Excel 批量导入商品"
      open={open}
      onCancel={close}
      width={820}
      footer={
        <Space>
          <Button onClick={close}>关闭</Button>
          {!result && (
            <Button
              type="primary"
              loading={importMutation.isPending}
              disabled={fileList.length === 0}
              onClick={() => importMutation.mutate()}>
              开始导入
            </Button>
          )}
        </Space>
      }
      destroyOnHidden
      maskClosable={false}>
      {!result ? (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="按 SKU 匹配：不存在则新建，已存在按所选策略处理；供应商名称匹配不到时会自动新建为合格供应商。"
          />
          <Space>
            <Button icon={<DownloadOutlined />} loading={downloading} onClick={downloadTemplate}>
              下载标准导入模板
            </Button>
            <span style={{ color: '#999' }}>模板含“填写说明”工作表，首次导入请先阅读</span>
          </Space>
          <Form layout="inline">
            <Form.Item label="已存在 SKU">
              <Radio.Group
                value={strategy}
                onChange={(e) => setStrategy(e.target.value as GoodsImportStrategy)}
                optionType="button"
                buttonStyle="solid">
                <Radio.Button value="UPDATE">更新已有（默认）</Radio.Button>
                <Radio.Button value="SKIP">跳过已有</Radio.Button>
              </Radio.Group>
            </Form.Item>
          </Form>
          <Upload.Dragger
            accept=".xlsx"
            maxCount={1}
            fileList={fileList}
            beforeUpload={(file) => {
              setFileList([
                {
                  uid: String(Date.now()),
                  name: file.name,
                  size: file.size,
                  type: file.type,
                  originFileObj: file,
                } as UploadFile,
              ]);
              setResult(null);
              return false;
            }}
            onRemove={() => {
              setFileList([]);
            }}>
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽 .xlsx 文件到此处</p>
            <p className="ant-upload-hint">采用流式解析，单文件建议不超过 1 万行</p>
          </Upload.Dragger>
        </Space>
      ) : (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space size={24} wrap>
            <Statistic title="总行数" value={result.total} />
            <Statistic title="新建" value={result.createdCount} valueStyle={{ color: '#3f8600' }} />
            <Statistic title="更新" value={result.updatedCount} valueStyle={{ color: '#1677ff' }} />
            <Statistic title="跳过" value={result.skippedCount} />
            <Statistic title="失败" value={result.failedCount}
              valueStyle={{ color: result.failedCount > 0 ? '#cf1322' : undefined }} />
          </Space>
          {result.failedCount > 0 ? (
            <Alert
              type="error"
              showIcon
              message={`${result.failedCount} 行未导入，请修正后重新上传（成功行已落库，无需重复处理）`}
            />
          ) : (
            <Alert type="success" showIcon message="全部行导入成功" />
          )}
          {result.rows.length === 0 ? (
            <Empty description="无有效数据行" />
          ) : (
            <Table<GoodsImportRowResult>
              size="small"
              rowKey="rowNum"
              columns={columns}
              dataSource={result.rows}
              pagination={result.rows.length > 10 ? { pageSize: 10, showSizeChanger: false } : false}
              scroll={{ y: 320 }}
            />
          )}
        </Space>
      )}
    </Modal>
  );
};

export default GoodsImportModal;
