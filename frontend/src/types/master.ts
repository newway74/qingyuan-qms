import type { LongId } from './auth';

export interface Supplier {
  id: LongId;
  supplierCode: string;
  supplierName: string;
  contact?: string;
  phone?: string;
  address?: string;
  status: string;
  lockVersion?: number;
}

export interface SupplierLicense {
  id: LongId;
  supplierId: LongId;
  licenseType: string;
  certNo?: string;
  validFrom?: string;
  validTo?: string;
  fileAttachmentId?: LongId;
  status: number;
}

export interface SupplierQuality {
  id: LongId;
  supplierId: LongId;
  period: string;
  batchCount: number;
  passRate?: number | string;
  defectCount: number;
  score?: number | string;
  grade?: string;
}

export interface SupplierDetail {
  supplier: Supplier;
  licenses: SupplierLicense[];
  qualityRatings: SupplierQuality[];
}

export interface Product {
  id: LongId;
  spuCode: string;
  productName: string;
  categoryId: LongId;
  brand?: string;
  supplierId?: LongId;
  executionStandard?: string;
  storageCondition?: string;
  shelfLifeDays?: number;
  extInspectionRequired: number;
  qualityStatus: string;
  lockVersion?: number;
}

export interface ProductSku {
  id: LongId;
  productId: LongId;
  skuCode: string;
  spec?: string;
  packageForm?: string;
  netContent?: number | string;
  netContentUnit?: string;
  barcode?: string;
  status: number;
}

export interface ProductLicense {
  id: LongId;
  productId: LongId;
  licenseType: string;
  certNo?: string;
  validTo?: string;
  fileAttachmentId?: LongId;
}

export interface ProductListRow {
  id: LongId;
  spuCode: string;
  productName: string;
  categoryId: LongId;
  categoryName?: string;
  brand?: string;
  supplierId?: LongId;
  supplierName?: string;
  executionStandard?: string;
  storageCondition?: string;
  shelfLifeDays?: number;
  extInspectionRequired: number;
  qualityStatus: string;
  lockVersion?: number;
  skuCount: number | string;
  firstNetContent?: number | string;
  firstNetContentUnit?: string;
  firstPackageForm?: string;
}

export interface ProductDetail {
  product: Product;
  categoryName?: string;
  supplierName?: string;
  skus: ProductSku[];
  licenses: ProductLicense[];
}

export interface SupplierUpsert {
  id?: LongId;
  supplierCode: string;
  supplierName: string;
  contact?: string;
  phone?: string;
  address?: string;
  status?: string;
}

export interface SupplierLicenseUpsert {
  id?: LongId;
  licenseType: string;
  certNo?: string;
  validFrom?: string;
  validTo?: string;
  fileAttachmentId?: LongId;
  status?: number;
}

export interface ProductUpsert {
  id?: LongId;
  spuCode: string;
  productName: string;
  categoryId: LongId;
  brand?: string;
  supplierId?: LongId;
  executionStandard?: string;
  storageCondition?: string;
  shelfLifeDays?: number;
  extInspectionRequired?: number;
  qualityStatus?: string;
}

export interface ProductSkuUpsert {
  id?: LongId;
  skuCode: string;
  spec?: string;
  packageForm?: string;
  netContent?: number;
  netContentUnit?: string;
  barcode?: string;
  status?: number;
}

export interface ProductLicenseUpsert {
  id?: LongId;
  licenseType: string;
  certNo?: string;
  validTo?: string;
  fileAttachmentId?: LongId;
}
