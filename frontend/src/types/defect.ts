import type { LongId } from './auth';

/** 不合格处置单 BH */
export interface DefectCaseObj {
  id: LongId;
  caseNo: string;
  reportId?: LongId;
  taskId?: LongId;
  sampleId?: LongId;
  /** A/B/C */
  maxSeverity: string;
  /** RETURN/OFF_SHELF/DESTROY/EXCHANGE/RECTIFY/CONCESSION */
  disposition?: string;
  rootCause?: string;
  /** PENDING_REVIEW/PENDING_APPROVAL/PROCESSING/PENDING_RECHECK/CLOSED/CANCELLED */
  status: string;
  ownerId?: LongId;
  closedAt?: string;
  lockVersion?: number;
  createdAt?: string;
}

export interface DefectItem {
  id: LongId;
  caseId: LongId;
  resultId?: LongId;
  defectLevel: string;
  itemName: string;
  failDesc: string;
}

export interface DefectApprovalRow {
  id: LongId;
  caseId: LongId;
  /** REVIEW/APPROVAL/DISPOSE/RECTIFY/RECHECK/CLOSE */
  node: string;
  /** SUBMIT/PASS/REJECT/CLOSE/EXECUTE/CANCEL/CREATE */
  action: string;
  comment?: string;
  beforeSnapshot?: Record<string, unknown> | null;
  afterSnapshot?: Record<string, unknown> | null;
  attachmentIds?: string;
  operatorId?: LongId;
  operatorName?: string;
  operatedAt: string;
}

export interface CaseListRow {
  caseObj: DefectCaseObj;
  reportNo?: string;
  taskNo?: string;
  sampleNo?: string;
  productName?: string;
  spec?: string;
  batchNo?: string;
  supplierName?: string;
  conclusion?: string;
  aFailCount: number;
  bFailCount: number;
  cFailCount: number;
  itemCount: number;
  issuedAt?: string;
}

export interface CaseDetail extends CaseListRow {
  supplierId?: LongId;
  items: DefectItem[];
  timeline: DefectApprovalRow[];
  rectification?: RectificationRow | null;
  allowedActions: string[];
}

/** 供应商整改单 ZG */
export interface RectObj {
  id: LongId;
  rectifyNo: string;
  defectCaseId: LongId;
  supplierId?: LongId;
  issueDesc: string;
  correctiveAction?: string;
  planFinishDate?: string;
  actualFinishDate?: string;
  /** ISSUED/REPLIED/VERIFYING/PASSED/FAILED */
  status: string;
  recheckTaskId?: LongId;
  verifierId?: LongId;
  verifiedAt?: string;
  createdAt?: string;
}

export interface RectificationRow {
  rect: RectObj;
  supplierName?: string;
  caseNo?: string;
  recheckTaskNo?: string;
  verifierName?: string;
}

/** 批次效期台账 */
export interface BatchRow {
  batch: {
    id: LongId;
    skuId: LongId;
    batchNo: string;
    productionDate?: string;
    expiryDate?: string;
    storageCondition?: string;
    status: string;
    lastCheckAt?: string;
  };
  skuCode?: string;
  spec?: string;
  productName?: string;
  daysToExpiry?: number;
  latestReceiveDate?: string;
  latestCheckAt?: string;
}

/** 供应商质量评级 */
export interface SupplierQualityRow {
  id: LongId;
  supplierId: LongId;
  /** yyyyMM */
  period: string;
  batchCount: number;
  passRate: number;
  defectCount: number;
  score: number;
  grade: string;
}
