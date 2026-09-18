import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Input, Modal, Row, Space, Statistic, Tag } from 'antd';
import { DeleteOutlined, WarningOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ledgerApi } from '@/api/ledger';
import type { DemoDataStatsVO } from '@/types/ledger';
import message from '@/utils/feedback';

/**
 * 演示数据管理（仅管理员）：展示 DEMO 数据规模 + 一键清除。
 * 清除仅作用于 data_source=DEMO 的数据；账号/品类/模板/资料项定义/USER 业务数据全部保留。
 * 二次确认：必须手动输入“清除”二字，红色警示，防止误操作。
 */
const DemoSetting: React.FC = () => {
  const queryClient = useQueryClient();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmText, setConfirmText] = useState('');
  const statsRef = useRef<DemoDataStatsVO | null>(null);

  const statsQuery = useQuery({
    queryKey: ['ledger-demo-stats'],
    queryFn: () => ledgerApi.demoStats(),
  });
  const stats = statsQuery.data;
  useEffect(() => {
    statsRef.current = stats ?? null;
  }, [stats]);

  const clearMutation = useMutation({
    mutationFn: () => ledgerApi.demoClear(),
    onSuccess: (data) => {
      message.success(
        `已清除演示数据：商品 ${data.goodsCount}、供应商 ${data.supplierCount}、流程记录 ${data.flowNodeCount}、资料清单 ${data.materialCount}`,
      );
      setConfirmOpen(false);
      setConfirmText('');
      queryClient.invalidateQueries({ queryKey: ['ledger-demo-stats'] });
      queryClient.invalidateQueries({ queryKey: ['ledger-goods'] });
      queryClient.invalidateQueries({ queryKey: ['ledger-goods-detail'] });
    },
    onError: () => {
      message.error('清除失败，请稍后重试');
    },
  });

  const hasDemo = (stats?.goodsCount ?? 0) > 0 || (stats?.supplierCount ?? 0) > 0;

  return (
    <div>
      <Card
        size="small"
        title={
          <Space>
            <WarningOutlined style={{ color: '#cf1322' }} />
            <span>演示数据管理</span>
            <Tag color="purple">仅管理员</Tag>
          </Space>
        }
        extra={
          <Button
            danger
            type="primary"
            icon={<DeleteOutlined />}
            disabled={!hasDemo}
            onClick={() => setConfirmOpen(true)}>
            一键清除演示数据
          </Button>
        }>
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="危险操作：一键清除将删除全部演示数据"
          description={
            <div>
              <div>清除范围：演示商品及其全流程记录、演示资料清单、演示供应商（数据来源标记为“演示(DEMO)”的数据）。</div>
              <div>
                保留范围：<b>账号、品类主数据、流程模板与资料项定义、系统配置，以及您自行录入或导入的全部用户数据（USER）</b>。
              </div>
            </div>
          }
        />

        <Row gutter={16}>
          <Col span={4}>
            <Card size="small"><Statistic title="演示商品" value={stats?.goodsCount ?? '-'} /></Card>
          </Col>
          <Col span={4}>
            <Card size="small"><Statistic title="演示供应商" value={stats?.supplierCount ?? '-'} /></Card>
          </Col>
          <Col span={4}>
            <Card size="small"><Statistic title="流程实例" value={stats?.flowCount ?? '-'} /></Card>
          </Col>
          <Col span={4}>
            <Card size="small"><Statistic title="流程节点记录" value={stats?.flowNodeCount ?? '-'} /></Card>
          </Col>
          <Col span={4}>
            <Card size="small"><Statistic title="资料清单" value={stats?.materialCount ?? '-'} /></Card>
          </Col>
          <Col span={4}>
            <Card size="small">
              <Statistic
                title="数据口径"
                value="DEMO"
                valueStyle={{ fontSize: 20, color: '#722ed1' }}
              />
            </Card>
          </Col>
        </Row>

        {!hasDemo && (
          <Alert
            style={{ marginTop: 16 }}
            type="success"
            showIcon
            message="当前没有演示数据，无需清除；您录入或导入的数据不受影响。"
          />
        )}
      </Card>

      <Modal
        title={
          <Space>
            <WarningOutlined style={{ color: '#cf1322' }} />
            <span>确认清除全部演示数据？</span>
          </Space>
        }
        open={confirmOpen}
        onCancel={() => {
          setConfirmOpen(false);
          setConfirmText('');
        }}
        okText="确认清除"
        cancelText="取消"
        okButtonProps={{ danger: true, disabled: confirmText.trim() !== '清除', loading: clearMutation.isPending }}
        onOk={() => clearMutation.mutate()}
        destroyOnHidden>
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="该操作不可恢复（逻辑删除并留痕），请再次确认清除范围。"
        />
        <div style={{ marginBottom: 8 }}>
          本次将清除：演示商品 <b>{statsRef.current?.goodsCount ?? 0}</b> 个、演示供应商{' '}
          <b>{statsRef.current?.supplierCount ?? 0}</b> 家、流程节点记录{' '}
          <b>{statsRef.current?.flowNodeCount ?? 0}</b> 条、资料清单{' '}
          <b>{statsRef.current?.materialCount ?? 0}</b> 条。
        </div>
        <div>请手动输入“清除”二字以继续：</div>
        <Input
          style={{ marginTop: 8 }}
          placeholder="清除"
          value={confirmText}
          onChange={(e) => setConfirmText(e.target.value)}
          onPressEnter={() => confirmText.trim() === '清除' && clearMutation.mutate()}
        />
      </Modal>
    </div>
  );
};

export default DemoSetting;
