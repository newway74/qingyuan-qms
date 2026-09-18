import { get } from './request';

export interface NameValue {
  name: string;
  value: number;
}

export interface TrendPoint {
  period: string;
  total: number;
  qualified: number;
  unqualified: number;
  passRate: number;
}

export interface ParetoItem {
  name: string;
  count: number;
  cumRate: number;
}

export interface RankRow {
  name: string;
  total: number;
  qualified: number;
  unqualified: number;
  passRate: number;
}

export interface EfficiencyPoint {
  period: string;
  finished: number;
  avgHours: number;
}

export interface ChartsData {
  passRateTrend: TrendPoint[];
  defectPareto: ParetoItem[];
  sourcePie: NameValue[];
  efficiency: EfficiencyPoint[];
  retainBuckets: NameValue[];
  batchBuckets: NameValue[];
}

export interface DashboardQuery {
  from?: string;
  to?: string;
  categoryId?: string | number;
  supplierId?: string | number;
}

export type RankDim = 'CATEGORY' | 'BRAND' | 'SUPPLIER';

export const dashboardApi = {
  charts: (params: DashboardQuery) => get<ChartsData>('/dashboard/charts', { params }),
  ranking: (dim: RankDim, params: DashboardQuery) =>
    get<RankRow[]>('/dashboard/ranking', { params: { dim, ...params } }),
};
