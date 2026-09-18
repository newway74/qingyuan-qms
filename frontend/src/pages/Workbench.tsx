import React from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Empty,
  List,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  AlertOutlined,
  AuditOutlined,
  BellOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { workbenchApi, type TodoRow, type WorkbenchCard } from '@/api/workbench';
import { useAuthStore } from '@/store/authStore';

const { Title, Paragraph, Text } = Typography;

const CARD_ROUTE: Record<string, string> = {
  pendingReceive: '/sampling',
  todayReceived: '/samples',
  myPendingInspect: '/inspection/tasks',
  myInspecting: '/inspection/tasks',
  mySlaDueSoon: '/inspection/tasks',
  mySlaOverdue: '/inspection/tasks',
  pendingReview: '/inspection/tasks',
  pendingAssign: '/inspection/tasks',
  defectReview: '/defect',
  defectApproval: '/defect',
  defectProcessing: '/defect',
  rectReplied: '/rectification',
  rectFollowing: '/rectification',
  alertOpen: '/alerts',
};

const TODO_ROUTE: Record<string, string> = {
  qc_sampling: '/sampling',
  qc_sample: '/samples',
  qc_inspection_task: '/inspection/tasks',
  qc_defect_case: '/defect',
  qc_supplier_rectification: '/rectification',
  qc_alert: '/alerts',
};

const TODO_TYPE_META: Record<string, { label: string; color: string }> = {
  RECEIVE: { label: '待收样', color: 'cyan' },
  INSPECT: { label: '待检验', color: 'blue' },
  REVIEW: { label: '待复核', color: 'geekblue' },
  APPROVAL: { label: '待分配', color: 'purple' },
  DEFECT: { label: '不合格处置', color: 'volcano' },
  RECT: { label: '整改验证', color: 'orange' },
  ALERT: { label: '预警', color: 'red' },
};

const toneColor: Record<WorkbenchCard['tone'], string> = {
  normal: '#1677ff',
  warning: '#faad14',
  danger: '#cf1322',
};

const Workbench: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const realName = user?.realName || user?.username || '用户';
  const roles = user?.roles || [];

  const summaryQuery = useQuery({ queryKey: ['workbench-summary'], queryFn: workbenchApi.summary });
  const todosQuery = useQuery({
    queryKey: ['workbench-todos-open'],
    queryFn: () => workbenchApi.todos({ pageNo: 1, pageSize: 10, status: 0 }),
  });
  const messagesQuery = useQuery({
    queryKey: ['workbench-messages'],
    queryFn: () => workbenchApi.messages({ pageNo: 1, pageSize: 10 }),
  });

  const markRead = useMutation({
    mutationFn: (id: number) => workbenchApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workbench-messages'] });
      queryClient.invalidateQueries({ queryKey: ['workbench-summary'] });
    },
  });
  const readAll = useMutation({
    mutationFn: workbenchApi.readAll,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workbench-messages'] });
      queryClient.invalidateQueries({ queryKey: ['workbench-summary'] });
    },
  });

  const summary = summaryQuery.data;

  const quickLinks: { to: string; label: string; icon: React.ReactNode; show: () => boolean }[] = [
    { to: '/sampling', label: '抽样登记', icon: <ProfileOutlined />, show: () => roles.includes('SAMPLER') || roles.includes('QA_MANAGER') || roles.includes('ADMIN') },
    { to: '/samples', label: '收样扫码', icon: <FileSearchOutlined />, show: () => roles.includes('SAMPLER') || roles.includes('QA_MANAGER') || roles.includes('ADMIN') },
    { to: '/inspection/tasks', label: '检验任务', icon: <ExperimentOutlined />, show: () => true },
    { to: '/defect', label: '不合格处置', icon: <SafetyCertificateOutlined />, show: () => roles.includes('REVIEWER') || roles.includes('QA_MANAGER') || roles.includes('ADMIN') },
    { to: '/rectification', label: '供应商整改', icon: <AuditOutlined />, show: () => roles.includes('QA_MANAGER') || roles.includes('ADMIN') },
    { to: '/batches', label: '批次效期', icon: <AlertOutlined />, show: () => true },
  ];

  return (
    <Spin spinning={summaryQuery.isLoading}>
      <Card style={{ marginBottom: 16 }}>
        <Row align="middle" justify="space-between">
          <Col>
            <Title level={4} style={{ marginBottom: 8 }}>
              你好，{realName}
            </Title>
            <Space>
              {(roles).map((r) => (
                <Tag key={r} color="blue">
                  {r}
                </Tag>
              ))}
            </Space>
          </Col>
          <Col>
            <Space size={32}>
              <Statistic title="我的待办" value={summary?.todoTotal ?? 0}
                valueStyle={{ color: (summary?.todoTotal ?? 0) > 0 ? '#cf1322' : undefined }} />
              <Statistic title="未读消息" value={summary?.unreadMessages ?? 0}
                valueStyle={{ color: (summary?.unreadMessages ?? 0) > 0 ? '#faad14' : undefined }} />
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        {(summary?.cards || []).map((card) => (
          <Col key={card.key} xs={12} sm={8} md={6}>
            <Card
              size="small"
              hoverable
              onClick={() => {
                const to = CARD_ROUTE[card.key];
                if (to) navigate(to);
              }}
            >
              <Statistic
                title={card.label}
                value={card.value}
                valueStyle={{ color: card.value > 0 ? toneColor[card.tone] : undefined, fontSize: 26 }}
              />
            </Card>
          </Col>
        ))}
        {summary && summary.cards.length === 0 && (
          <Col span={24}>
            <Alert type="info" showIcon message="当前角色暂无待办卡片" />
          </Col>
        )}
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col xs={24} lg={15}>
          <Card
            size="small"
            title="我的待办"
            extra={<Button type="link" size="small" onClick={() => navigate('/inspection/tasks')}>进入工作台</Button>}
          >
            <Table<TodoRow>
              rowKey="id"
              size="small"
              loading={todosQuery.isLoading}
              dataSource={todosQuery.data?.records || []}
              pagination={false}
              locale={{ emptyText: <Empty description="暂无未处理待办" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              onRow={(row) => ({
                onClick: () => {
                  const to = TODO_ROUTE[row.bizType];
                  if (to) navigate(to);
                },
                style: { cursor: 'pointer' },
              })}
              columns={[
                {
                  title: '类型',
                  dataIndex: 'todoType',
                  width: 100,
                  render: (v: string) => {
                    const meta = TODO_TYPE_META[v];
                    return <Tag color={meta?.color}>{meta?.label || v}</Tag>;
                  },
                },
                { title: '事项', dataIndex: 'title', ellipsis: true },
                {
                  title: '截止',
                  dataIndex: 'deadline',
                  width: 150,
                  render: (v: string | null) =>
                    v ? <Text type={dayjs(v).isBefore(dayjs()) ? 'danger' : undefined}>{dayjs(v).format('MM-DD HH:mm')}</Text> : '-',
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={9}>
          <Card
            size="small"
            title={<Space><BellOutlined />消息中心</Space>}
            extra={
              <Button
                type="link"
                size="small"
                disabled={!summary?.unreadMessages}
                loading={readAll.isPending}
                onClick={() => readAll.mutate()}
              >
                全部已读
              </Button>
            }
          >
            <List
              size="small"
              loading={messagesQuery.isLoading}
              dataSource={messagesQuery.data?.records || []}
              locale={{ emptyText: <Empty description="暂无消息" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              renderItem={(m) => (
                <List.Item
                  style={{ cursor: 'pointer', background: m.isRead === 0 ? '#f6ffed' : undefined, paddingLeft: 8 }}
                  onClick={() => {
                    if (m.isRead === 0) markRead.mutate(m.id);
                    const to = m.bizType ? TODO_ROUTE[m.bizType] : undefined;
                    if (to) navigate(to);
                  }}
                >
                  <List.Item.Meta
                    title={
                      <Space size={6}>
                        {m.isRead === 0 && <Badge status="error" />}
                        <Text strong={m.isRead === 0} style={{ fontSize: 13 }}>{m.title}</Text>
                        {m.msgType === 'ALERT' && <Tag color="red">预警</Tag>}
                      </Space>
                    }
                    description={
                      <Space size={8}>
                        {m.content && <Text type="secondary" style={{ fontSize: 12 }}>{m.content}</Text>}
                        <Text type="secondary" style={{ fontSize: 12 }}>{dayjs(m.createdAt).format('MM-DD HH:mm')}</Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
          <Card size="small" title="快捷入口" style={{ marginTop: 16 }}>
            <Space wrap>
              {quickLinks.filter((q) => q.show()).map((q) => (
                <Button key={q.to} icon={q.icon} onClick={() => navigate(q.to)}>
                  {q.label}
                </Button>
              ))}
            </Space>
          </Card>
        </Col>
      </Row>

      <Card style={{ marginTop: 16 }} size="small">
        <Paragraph type="secondary" style={{ margin: 0 }}>
          卡片数量取业务表实时统计，待办与业务状态一致；关键操作全程审计留痕，质量记录不可物理删除。
        </Paragraph>
      </Card>
    </Spin>
  );
};

export default Workbench;
