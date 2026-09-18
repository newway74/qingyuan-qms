import { get, post } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type { SamplingUpsert, SamplingVO } from '@/types/inspection';

export interface SamplingQuery {
  pageNo?: number;
  pageSize?: number;
  samplingNo?: string;
  source?: string;
  status?: string;
  skuId?: LongId;
  batchNo?: string;
  keyword?: string;
}

export const samplingApi = {
  page: (params: SamplingQuery) => get<PageResult<SamplingVO>>('/sampling', { params }),
  detail: (id: LongId) => get<SamplingVO>(`/sampling/${id}`),
  create: (data: SamplingUpsert) => post<LongId>('/sampling', data),
  update: (data: SamplingUpsert) => post<void>('/sampling/update', data),
  submit: (id: LongId) => post<void>(`/sampling/${id}/submit`),
  cancel: (id: LongId, reason: string) => post<void>('/sampling/cancel', { id, reason }),
};
