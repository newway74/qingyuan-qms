import { get, post } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type { SampleReceive, SampleVO, SamplingVO } from '@/types/inspection';

export interface SampleQuery {
  pageNo?: number;
  pageSize?: number;
  sampleNo?: string;
  batchNo?: string;
  status?: string;
  sampleType?: string;
  skuId?: LongId;
}

export const sampleApi = {
  page: (params: SampleQuery) => get<PageResult<SampleVO>>('/samples', { params }),
  detail: (id: LongId) => get<SampleVO>(`/samples/${id}`),
  receive: (data: SampleReceive) => post<SamplingVO>('/samples/receive', data),
  barcode: (code: string) => get<SampleVO>(`/samples/barcode/${encodeURIComponent(code)}`),
  barcodeImageUrl: (id: LongId) => `/api/v1/samples/${id}/barcode?width=480&height=120`,
  retain: (id: LongId, retainLocation: string, retainUntil: string) =>
    post<void>('/samples/retain', { id, retainLocation, retainUntil }),
  dispose: (id: LongId, disposeType: string, disposeRemark: string) =>
    post<void>('/samples/dispose', { id, disposeType, disposeRemark }),
};
