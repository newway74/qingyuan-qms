import { get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type {
  BatchRow,
  CaseDetail,
  CaseListRow,
  DefectApprovalRow,
  RectificationRow,
  SupplierQualityRow,
} from '@/types/defect';

export interface CaseQuery {
  pageNo?: number;
  pageSize?: number;
  caseNo?: string;
  status?: string;
  maxSeverity?: string;
}

export const caseApi = {
  page: (params: CaseQuery) => get<PageResult<CaseListRow>>('/defect/cases', { params }),
  detail: (id: LongId) => get<CaseDetail>(`/defect/cases/${id}`),
  timeline: (id: LongId) => get<DefectApprovalRow[]>(`/defect/cases/${id}/timeline`),
  review: (id: LongId, payload: { disposition: string; rootCause: string; comment?: string }) =>
    post<void>(`/defect/cases/${id}/review`, payload),
  approve: (
    id: LongId,
    payload: { action: 'PASS' | 'REJECT'; password?: string; comment?: string },
  ) => post<void>(`/defect/cases/${id}/approve`, payload),
  execute: (id: LongId, payload: { comment: string; attachmentIds?: string }) =>
    post<void>(`/defect/cases/${id}/execute`, payload),
  concession: (id: LongId, payload: { password: string; comment: string }) =>
    post<void>(`/defect/cases/${id}/concession`, payload),
  close: (id: LongId, comment?: string) =>
    post<void>(`/defect/cases/${id}/close`, comment ? { comment } : {}),
  cancel: (id: LongId, comment: string) =>
    post<void>(`/defect/cases/${id}/cancel`, { comment }),
};

export interface RectQuery {
  pageNo?: number;
  pageSize?: number;
  rectifyNo?: string;
  status?: string;
}

export const rectApi = {
  page: (params: RectQuery) => get<PageResult<RectificationRow>>('/rectifications', { params }),
  detail: (id: LongId) => get<RectificationRow>(`/rectifications/${id}`),
  create: (payload: { defectCaseId: LongId; issueDesc: string; planFinishDate?: string }) =>
    post<LongId>('/rectifications', payload),
  reply: (
    id: LongId,
    payload: { correctiveAction: string; planFinishDate?: string; actualFinishDate?: string },
  ) => put<void>(`/rectifications/${id}`, payload),
  verify: (id: LongId, payload: { result: 'PASS' | 'FAIL'; password: string; comment?: string }) =>
    post<void>(`/rectifications/${id}/verify`, payload),
};

export interface BatchQuery {
  pageNo?: number;
  pageSize?: number;
  batchNo?: string;
  status?: string;
  skuId?: LongId;
}

export const batchApi = {
  page: (params: BatchQuery) => get<PageResult<BatchRow>>('/batches', { params }),
  summary: () => get<Record<string, number>>('/batches/summary'),
  recompute: () => post<{ scanned: number; changed: number }>('/batches/recompute'),
};

export const supplierQualityApi = {
  trend: (supplierId: LongId) =>
    get<SupplierQualityRow[]>(`/master/suppliers/${supplierId}/quality-trend`),
};
