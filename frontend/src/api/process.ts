import { get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type { ProcessChangeLog, ProcessDef, ProcessDetail, ProcessNodeInput } from '@/types/process';

export interface ProcessQuery {
  pageNo?: number;
  pageSize?: number;
  processName?: string;
  processCode?: string;
  status?: string;
}

export const processApi = {
  page: (params: ProcessQuery) => get<PageResult<ProcessDef>>('/process/defs', { params }),
  detail: (id: LongId) => get<ProcessDetail>(`/process/defs/${id}`),
  versions: (id: LongId) => get<ProcessDef[]>(`/process/defs/${id}/versions`),
  changelog: (id: LongId) => get<ProcessChangeLog[]>(`/process/defs/${id}/changelog`),
  effective: () => get<ProcessDetail>('/process/defs/effective'),
  create: (data: { processCode: string; processName: string; remark?: string }) =>
    post<LongId>('/process/defs', data),
  update: (data: { id: LongId; processName: string; remark?: string }) => put<void>('/process/defs', data),
  revise: (id: LongId) => post<LongId>(`/process/defs/${id}/revise`),
  publish: (id: LongId) => post<void>(`/process/defs/${id}/publish`),
  saveNodes: (id: LongId, nodes: ProcessNodeInput[]) => put<void>(`/process/defs/${id}/nodes`, { nodes }),
};
