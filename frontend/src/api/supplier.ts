import { del, get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type { Supplier, SupplierDetail, SupplierLicenseUpsert, SupplierQuality, SupplierUpsert } from '@/types/master';

export interface SupplierQuery {
  pageNo?: number;
  pageSize?: number;
  supplierName?: string;
  supplierCode?: string;
  status?: string;
}

export const supplierApi = {
  page: (params: SupplierQuery) => get<PageResult<Supplier>>('/master/suppliers', { params }),
  detail: (id: LongId) => get<SupplierDetail>(`/master/suppliers/${id}`),
  quality: (id: LongId) => get<SupplierQuality[]>(`/master/suppliers/${id}/quality`),
  create: (data: SupplierUpsert) => post<LongId>('/master/suppliers', data),
  update: (data: SupplierUpsert) => put<void>('/master/suppliers', data),
  addLicense: (id: LongId, data: SupplierLicenseUpsert) =>
    post<LongId>(`/master/suppliers/${id}/licenses`, data),
  updateLicense: (id: LongId, licenseId: LongId, data: SupplierLicenseUpsert) =>
    put<void>(`/master/suppliers/${id}/licenses/${licenseId}`, data),
  removeLicense: (id: LongId, licenseId: LongId) =>
    del<void>(`/master/suppliers/${id}/licenses/${licenseId}`),
};
