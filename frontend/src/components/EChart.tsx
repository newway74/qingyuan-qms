import React, { useEffect, useRef } from 'react';
import * as echarts from 'echarts';

interface Props {
  option: echarts.EChartsCoreOption;
  height?: number;
}

/**
 * ECharts 轻量封装：init + ResizeObserver 自适应 + notMerge 全量刷新。
 * 不使用 echarts-for-react（其在 echarts@5.5 下等待 finished 事件，空 option 会导致图表永不渲染）。
 */
const EChart: React.FC<Props> = ({ option, height = 280 }) => {
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
    // 第二参 true：notMerge，节点/品类数量变化时彻底替换旧 option，避免残留系列
    chartRef.current?.setOption(option, true);
  }, [option]);

  return <div ref={containerRef} style={{ width: '100%', height }} />;
};

export default EChart;
