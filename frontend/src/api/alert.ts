import { get, post } from './request';
import type { PageResult } from '@/types/api';

export interface AlertRow {
  id: number;
  alertType: string;
  bizType: string;
  bizId: number;
  targetRole: string | null;
  targetUserId: number | null;
  message: string;
  level: number;
  status: number;
  dedupKey: string;
  triggeredAt: string;
  handledAt: string | null;
  handlerId: number | null;
}

export interface ScanStat {
  raised: number;
  resolved: number;
}

export const alertApi = {
  page: (params: { pageNo?: number; pageSize?: number; alertType?: string; status?: number }) =>
    get<PageResult<AlertRow>>('/alerts', { params }),
  handle: (id: number) => post<void>(`/alerts/${id}/handle`),
  scan: (types?: string) =>
    post<Record<string, ScanStat>>('/alerts/scan', types ? { types } : {}),
};
