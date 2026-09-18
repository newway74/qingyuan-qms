import { del, get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type {
  ProductDetail,
  ProductLicenseUpsert,
  ProductListRow,
  ProductSkuUpsert,
  ProductUpsert,
} from '@/types/master';

export interface ProductQuery {
  pageNo?: number;
  pageSize?: number;
  productName?: string;
  spuCode?: string;
  categoryId?: LongId;
  supplierId?: LongId;
  qualityStatus?: string;
}

export const productApi = {
  page: (params: ProductQuery) => get<PageResult<ProductListRow>>('/master/products', { params }),
  detail: (id: LongId) => get<ProductDetail>(`/master/products/${id}`),
  create: (data: ProductUpsert) => post<LongId>('/master/products', data),
  update: (data: ProductUpsert) => put<void>('/master/products', data),
  changeQualityStatus: (id: LongId, qualityStatus: string, reason: string, lockVersion?: number) =>
    put<void>(`/master/products/${id}/quality-status`, { qualityStatus, reason, lockVersion }),
  addSku: (id: LongId, data: ProductSkuUpsert) => post<LongId>(`/master/products/${id}/skus`, data),
  updateSku: (id: LongId, skuId: LongId, data: ProductSkuUpsert) =>
    put<void>(`/master/products/${id}/skus/${skuId}`, data),
  removeSku: (id: LongId, skuId: LongId) => del<void>(`/master/products/${id}/skus/${skuId}`),
  addLicense: (id: LongId, data: ProductLicenseUpsert) =>
    post<LongId>(`/master/products/${id}/licenses`, data),
  updateLicense: (id: LongId, licenseId: LongId, data: ProductLicenseUpsert) =>
    put<void>(`/master/products/${id}/licenses/${licenseId}`, data),
  removeLicense: (id: LongId, licenseId: LongId) =>
    del<void>(`/master/products/${id}/licenses/${licenseId}`),
};
