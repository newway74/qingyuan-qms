import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Card, Col, DatePicker, Empty, Row, Select, Space, Tabs } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import * as echarts from 'echarts';
import dayjs, { Dayjs } from 'dayjs';
import { dashboardApi, type ChartsData, type RankDim, type RankRow } from '@/api/dashboard';
import { categoryApi } from '@/api/category';
import { supplierApi } from '@/api/supplier';
import request from '@/api/request';
import AuthButton from '@/components/AuthButton';
import { BATCH_STATUS, SAMPLING_SOURCES } from '@/constants/qms';
import type { Category } from '@/types/auth';
import type { Supplier } from '@/types/master';
import message from '@/utils/feedback';

const { RangePicker } = DatePicker;

// 直接基于 echarts 封装：echarts-for-react@3.0.2 + echarts@5.5 的 finished 事件死锁会致空白。
function EChart({ option, height = 300 }: { option: echarts.EChartsCoreOption; height?: number }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, true);
  }, [option]);

  return <div ref={containerRef} style={{ width: '100%', height }} />;
}

const fmtPeriod = (p: string) => (p && p.length === 6 ? `${p.slice(0, 4)}-${p.slice(4)}` : p);

const sourceLabel = (code: string) => SAMPLING_SOURCES.find((s) => s.value === code)?.label ?? code;
const batchLabel = (code: string) => BATCH_STATUS.find((s) => s.value === code)?.label ?? code;

const ChartCard: React.FC<{ title: string; children: React.ReactNode; span?: number }> = ({
  title,
  children,
  span = 12,
}) => (
  <Col xs={24} lg={span}>
    <Card title={title} size="small">
      {children}
    </Card>
  </Col>
);

export default function Dashboard() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(6, 'month').startOf('day'), dayjs().endOf('day')]);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [supplierId, setSupplierId] = useState<number | undefined>();
  const [dim, setDim] = useState<RankDim>('CATEGORY');
  const [reportMonth, setReportMonth] = useState<Dayjs>(dayjs());
  const [exporting, setExporting] = useState(false);

  const exportMonthly = async () => {
    setExporting(true);
    try {
      const month = reportMonth.format('YYYY-MM');
      const resp = await request.get('/dashboard/monthly-report', {
        params: { month },
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(
        new Blob([resp as unknown as BlobPart], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        }),
      );
      const a = document.createElement('a');
      a.href = url;
      a.download = `质量月报-${month}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      message.success('月报已导出');
    } catch {
      message.error('导出失败，请重试');
    } finally {
      setExporting(false);
    }
  };

  const query = useMemo(
    () => ({
      from: range[0].format('YYYY-MM-DD'),
      to: range[1].format('YYYY-MM-DD'),
      categoryId,
      supplierId,
    }),
    [range, categoryId, supplierId],
  );

  const chartsQ = useQuery({
    queryKey: ['dashboard-charts', query],
    queryFn: () => dashboardApi.charts(query),
  });
  const rankQ = useQuery({
    queryKey: ['dashboard-ranking', dim, query],
    queryFn: () => dashboardApi.ranking(dim, query),
  });
  const categoryQ = useQuery({ queryKey: ['category-options'], queryFn: () => categoryApi.page({ pageSize: 200, status: 1 }) });
  const supplierQ = useQuery({ queryKey: ['supplier-options'], queryFn: () => supplierApi.page({ pageSize: 200 }) });

  const data: ChartsData | undefined = chartsQ.data;
  const ranks: RankRow[] = rankQ.data ?? [];

  // 1. 合格率趋势：柱（总数/合格/不合格）+ 折线（合格率）
  const trendOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.passRateTrend ?? [];
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['合格批次', '不合格批次', '合格率'], top: 0 },
      grid: { left: 48, right: 56, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: rows.map((r) => fmtPeriod(r.period)) },
      yAxis: [
        { type: 'value', name: '批次数', minInterval: 1 },
        { type: 'value', name: '合格率', axisLabel: { formatter: '{value}%' }, min: 0, max: 100 },
      ],
      series: [
        { name: '合格批次', type: 'bar', stack: 'total', color: '#52c41a', data: rows.map((r) => r.qualified) },
        { name: '不合格批次', type: 'bar', stack: 'total', color: '#ff4d4f', data: rows.map((r) => r.unqualified) },
        {
          name: '合格率',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          color: '#1677ff',
          data: rows.map((r) => r.passRate),
          label: { show: true, formatter: '{c}%' },
        },
      ],
    };
  }, [data]);

  // 2. 缺陷帕累托：柱 + 累计占比折线
  const paretoOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.defectPareto ?? [];
    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 90, right: 48, top: 30, bottom: 70 },
      xAxis: {
        type: 'category',
        data: rows.map((r) => r.name),
        axisLabel: { interval: 0, rotate: 28, width: 90, overflow: 'truncate' },
      },
      yAxis: [
        { type: 'value', name: '次数', minInterval: 1 },
        { type: 'value', name: '累计', axisLabel: { formatter: '{value}%' }, min: 0, max: 100 },
      ],
      series: [
        { type: 'bar', color: '#fa8c16', data: rows.map((r) => r.count) },
        {
          type: 'line',
          yAxisIndex: 1,
          color: '#1677ff',
          data: rows.map((r) => r.cumRate),
          label: { show: true, formatter: '{c}%' },
        },
      ],
    };
  }, [data]);

  // 4. 抽检来源饼
  const sourceOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.sourcePie ?? [];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '46%'],
          data: rows.map((r) => ({ name: sourceLabel(r.name), value: r.value })),
          label: { formatter: '{b}\n{c} 批' },
        },
      ],
    };
  }, [data]);

  // 3. 合格率排行（横向条形）
  const rankOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = [...ranks].sort((a, b) => a.passRate - b.passRate);
    return {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (ps: { name: string; value: number }[]) => {
          const row = ranks.find((r) => r.name === ps[0]?.name);
          if (!row) return '';
          return `${row.name}<br/>总判定：${row.total}（合格 ${row.qualified} / 不合格 ${row.unqualified}）<br/>合格率：${row.passRate}%`;
        },
      },
      grid: { left: 140, right: 60, top: 16, bottom: 30 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', data: rows.map((r) => r.name) },
      series: [
        {
          type: 'bar',
          data: rows.map((r) => ({
            value: r.passRate,
            itemStyle: { color: r.passRate >= 95 ? '#52c41a' : r.passRate >= 85 ? '#faad14' : '#ff4d4f' },
          })),
          label: { show: true, position: 'right', formatter: '{c}%' },
        },
      ],
    };
  }, [ranks]);

  // 5. 处理时效
  const efficiencyOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.efficiency ?? [];
    return {
      tooltip: { trigger: 'axis', formatter: (ps: { name: string }[]) => `${ps[0]?.name}<br/>平均处理时效见柱值` },
      grid: { left: 48, right: 24, top: 24, bottom: 30 },
      xAxis: { type: 'category', data: rows.map((r) => fmtPeriod(r.period)) },
      yAxis: { type: 'value', name: '小时' },
      series: [
        {
          type: 'bar',
          color: '#1677ff',
          data: rows.map((r) => ({ value: r.avgHours, finishedCount: r.finished })),
          label: { show: true, formatter: (p: { value: number }) => `${p.value}h` },
        },
      ],
    };
  }, [data]);

  // 6. 留样 / 批次效期
  const retainOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.retainBuckets ?? [];
    const colors = ['#ff4d4f', '#fa8c16', '#fadb14', '#52c41a'];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 份' },
      series: [
        {
          type: 'pie',
          radius: '66%',
          data: rows.map((r, i) => ({ name: r.name, value: r.value, itemStyle: { color: colors[i] } })),
          label: { formatter: '{b}\n{c}' },
        },
      ],
    };
  }, [data]);

  const batchOption: echarts.EChartsCoreOption = useMemo(() => {
    const rows = data?.batchBuckets ?? [];
    const colors: Record<string, string> = { NORMAL: '#52c41a', NEAR_EXPIRY: '#fa8c16', EXPIRED: '#ff4d4f' };
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 批' },
      series: [
        {
          type: 'pie',
          radius: '66%',
          data: rows.map((r) => ({ name: batchLabel(r.name), value: r.value, itemStyle: { color: colors[r.name] } })),
          label: { formatter: '{b}\n{c}' },
        },
      ],
    };
  }, [data]);

  const empty = !data || (data.passRateTrend.length === 0 && data.defectPareto.length === 0 && data.sourcePie.length === 0);

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Space wrap>
          <RangePicker
            value={range}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
            allowClear={false}
          />
          <Select
            allowClear
            placeholder="品类"
            style={{ width: 180 }}
            value={categoryId}
            onChange={(v) => setCategoryId(v)}
            options={(categoryQ.data?.records ?? []).map((c: Category) => ({ value: Number(c.id), label: c.name }))}
            showSearch
            optionFilterProp="label"
          />
          <Select
            allowClear
            placeholder="供应商"
            style={{ width: 220 }}
            value={supplierId}
            onChange={(v) => setSupplierId(v)}
            options={(supplierQ.data?.records ?? []).map((s: Supplier) => ({
              value: Number(s.id),
              label: s.supplierName,
            }))}
            showSearch
            optionFilterProp="label"
          />
          <span style={{ marginLeft: 16 }}>
            <DatePicker
              picker="month"
              value={reportMonth}
              onChange={(v) => v && setReportMonth(v)}
              allowClear={false}
              style={{ width: 130, marginRight: 8 }}
            />
            <AuthButton
              type="primary"
              icon={<DownloadOutlined />}
              perm="report:export"
              loading={exporting}
              onClick={exportMonthly}
            >
              导出月报
            </AuthButton>
          </span>
        </Space>
      </Card>

      {empty ? (
        <Card>
          <Empty description="所选范围内暂无判定数据" />
        </Card>
      ) : (
        <Row gutter={[12, 12]}>
          <ChartCard title="合格率趋势（月度，口径：双签报告）" span={24}>
            <EChart option={trendOption} height={300} />
          </ChartCard>
          <ChartCard title="缺陷项帕累托（Top10）" span={14}>
            <EChart option={paretoOption} height={320} />
          </ChartCard>
          <ChartCard title="抽检来源分布" span={10}>
            <EChart option={sourceOption} height={320} />
          </ChartCard>
          <ChartCard title="合格率排行" span={24}>
            <Tabs
              activeKey={dim}
              onChange={(k) => setDim(k as RankDim)}
              items={[
                { key: 'CATEGORY', label: '按品类' },
                { key: 'BRAND', label: '按品牌' },
                { key: 'SUPPLIER', label: '按供应商' },
              ]}
            />
            {ranks.length === 0 ? <Empty /> : <EChart option={rankOption} height={Math.max(220, ranks.length * 42)} />}
          </ChartCard>
          <ChartCard title="任务处理时效（分配→复核，月度均值）" span={12}>
            <EChart option={efficiencyOption} height={280} />
          </ChartCard>
          <ChartCard title="在库留样效期" span={6}>
            <EChart option={retainOption} height={280} />
          </ChartCard>
          <ChartCard title="批次效期分布" span={6}>
            <EChart option={batchOption} height={280} />
          </ChartCard>
        </Row>
      )}
    </div>
  );
}
