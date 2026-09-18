import React, { useMemo, useState } from 'react';
import { Card, Col, Empty, Input, Row, Space, Statistic, Table, Tag } from 'antd';
import {
  BranchesOutlined,
  ContainerOutlined,
  DownloadOutlined,
  FileSearchOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnsType } from 'antd/es/table';
import * as echarts from 'echarts';
import { useNavigate } from 'react-router-dom';
import AuthButton from '@/components/AuthButton';
import EChart from '@/components/EChart';
import { ledgerApi } from '@/api/ledger';
import request from '@/api/request';
import type { MaterialGapVO, NameValue } from '@/types/ledger';
import message from '@/utils/feedback';

/** 下载带查询参数的 Excel 报表（blob） */
async function downloadReport(url: string, params: Record<string, string | undefined>, fileName: string) {
  const resp = await request.get(url, { params, responseType: 'blob' });
  const blobUrl = window.URL.createObjectURL(
    new Blob([resp as unknown as BlobPart], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }),
  );
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(blobUrl);
}

/**
 * 商品品控工作台（台账统计主页）：四张卡片 + 过会状态/当前节点/品类三图 + 资料重点缺口明细。
 */
const LedgerDashboard: React.FC = () => {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [exporting, setExporting] = useState('');

  const summaryQ = useQuery({
    queryKey: ['ledger-dashboard-summary'],
    queryFn: () => ledgerApi.dashboardSummary(),
  });
  const gapsQ = useQuery({
    queryKey: ['ledger-dashboard-gaps', pageNo, keyword],
    queryFn: () => ledgerApi.dashboardGaps({ pageNo, pageSize: 10, keyword: keyword || undefined }),
  });

  const summary = summaryQ.data;

  // 图1：过会状态分布（已过会/待定/不合作）
  const cooperateOption = useMemo<echarts.EChartsCoreOption>(() => {
    const rows = summary?.cooperateResultDist ?? [];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      color: ['#52c41a', '#faad14', '#ff4d4f'],
      series: [
        {
          name: '过会状态',
          type: 'pie',
          radius: ['38%', '62%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}\n{c}' },
          data: rows.map((r: NameValue) => ({ name: r.name, value: r.value })),
        },
      ],
    };
  }, [summary]);

  // 图2：当前节点分布（横向柱状，名称长适合纵向排列）
  const nodeOption = useMemo<echarts.EChartsCoreOption>(() => {
    const rows = [...(summary?.currentNodeDist ?? [])].reverse();
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 110, right: 40, top: 16, bottom: 24 },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: rows.map((r) => r.name) },
      series: [
        {
          name: '商品数',
          type: 'bar',
          barMaxWidth: 18,
          itemStyle: { color: '#1677ff', borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: 'right' },
          data: rows.map((r) => r.value),
        },
      ],
    };
  }, [summary]);

  // 图3：一级品类分布（环形）
  const categoryOption = useMemo<echarts.EChartsCoreOption>(() => {
    const rows = summary?.categoryDist ?? [];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      color: ['#1677ff', '#52c41a', '#faad14', '#722ed1', '#13c2c2', '#eb2f96', '#fa8c16'],
      series: [
        {
          name: '一级品类',
          type: 'pie',
          radius: ['38%', '62%'],
          center: ['50%', '44%'],
          label: { formatter: '{b}\n{c}' },
          data: rows.map((r) => ({ name: r.name, value: r.value })),
        },
      ],
    };
  }, [summary]);

  const onExport = async (kind: 'goods' | 'flows' | 'gaps') => {
    setExporting(kind);
    try {
      const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      if (kind === 'goods') {
        await downloadReport(
          ledgerApi.exportGoodsUrl,
          { keyword: keyword || undefined },
          `商品品控台账_${stamp}.xlsx`,
        );
      } else if (kind === 'flows') {
        await downloadReport(ledgerApi.exportFlowsUrl, {}, `商品全流程记录_${stamp}.xlsx`);
      } else {
        await downloadReport(
          ledgerApi.exportGapsUrl,
          {},
          `资料重点缺口清单_${stamp}.xlsx`,
        );
      }
      message.success('报表已导出');
    } catch {
      message.error('导出失败，请重试');
    } finally {
      setExporting('');
    }
  };

  const columns: ColumnsType<MaterialGapVO> = [
    { title: 'SKU', dataIndex: 'sku', width: 150 },
    { title: '产品通用名', dataIndex: 'commonName', width: 180 },
    { title: '品牌', dataIndex: 'brand', width: 120, render: (v?: string) => v ?? '-' },
    { title: '一级品类', dataIndex: 'categoryL1Name', width: 120, render: (v?: string) => v ?? '-' },
    { title: '当前节点', dataIndex: 'currentNodeName', width: 140, render: (v?: string) => v ?? '-' },
    {
      title: '缺口数',
      dataIndex: 'gapCount',
      width: 80,
      sorter: (a, b) => a.gapCount - b.gapCount,
      render: (v: number) => <Tag color="red">{v}</Tag>,
    },
    {
      title: '缺失 / 待确认资料项',
      dataIndex: 'gapItems',
      render: (items: MaterialGapVO['gapItems']) => (
        <Space size={[4, 4]} wrap>
          {items.map((it) => (
            <Tag key={it.itemName} color={it.status === 'PENDING' ? 'orange' : 'red'}>
              {it.status === 'PENDING' ? '待确认' : '缺失'}·{it.itemName}
            </Tag>
          ))}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Row gutter={12}>
        <Col span={6}>
          <Card>
            <Statistic
              title="商品总数"
              value={summary?.cards.goodsTotal ?? '-'}
              prefix={<ContainerOutlined style={{ color: '#1677ff' }} />}
              loading={summaryQ.isLoading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="供应商总数"
              value={summary?.cards.supplierTotal ?? '-'}
              prefix={<TeamOutlined style={{ color: '#52c41a' }} />}
              loading={summaryQ.isLoading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="全流程记录数"
              value={summary?.cards.flowRecordTotal ?? '-'}
              prefix={<BranchesOutlined style={{ color: '#722ed1' }} />}
              loading={summaryQ.isLoading}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="资料重点缺口数"
              value={summary?.cards.gapItemTotal ?? '-'}
              prefix={<FileSearchOutlined style={{ color: '#fa541c' }} />}
              valueStyle={{ color: '#cf1322' }}
              loading={summaryQ.isLoading}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={12}>
        <Col span={8}>
          <Card size="small" title="过会状态分布">
            <EChart option={cooperateOption} height={300} />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="当前流程节点分布">
            <EChart option={nodeOption} height={300} />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" title="一级品类分布">
            <EChart option={categoryOption} height={300} />
          </Card>
        </Col>
      </Row>

      <Card
        size="small"
        title="资料重点缺口明细（点击商品行可进入详情补齐资料）"
        extra={
          <Space>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="SKU / 名称 / 品牌 / 企业"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPageNo(1);
              }}
              style={{ width: 220 }}
            />
            <AuthButton
              perm="ledger:goods:export"
              icon={<DownloadOutlined />}
              loading={exporting === 'goods'}
              onClick={() => onExport('goods')}>
              导出台账
            </AuthButton>
            <AuthButton
              perm="ledger:goods:export"
              loading={exporting === 'flows'}
              onClick={() => onExport('flows')}>
              导出流程记录
            </AuthButton>
            <AuthButton
              perm="ledger:goods:export"
              loading={exporting === 'gaps'}
              onClick={() => onExport('gaps')}>
              导出缺口清单
            </AuthButton>
          </Space>
        }>
        <Table<MaterialGapVO>
          rowKey="goodsId"
          size="small"
          loading={gapsQ.isLoading}
          columns={columns}
          dataSource={gapsQ.data?.records ?? []}
          onRow={(row) => ({
            onClick: () => navigate(`/ledger/goods/${row.goodsId}`),
            style: { cursor: 'pointer' },
          })}
          locale={{ emptyText: <Empty description="暂无资料缺口，全部齐套" /> }}
          pagination={{
            current: pageNo,
            pageSize: 10,
            total: Number(gapsQ.data?.total ?? 0),
            showTotal: (t) => `共 ${t} 个商品存在缺口`,
            onChange: (p) => setPageNo(p),
          }}
        />
      </Card>
    </Space>
  );
};

export default LedgerDashboard;
