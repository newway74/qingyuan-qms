import type { Dayjs } from 'dayjs';
import type { LongId } from './auth';

// ---------------- 抽样 ----------------

export interface SamplingUpsert {
  id?: LongId;
  source: string;
  skuId: LongId;
  batchNo: string;
  /** 表单中为 Dayjs，提交前格式化为 string */
  productionDate?: string | Dayjs;
  expiryDate?: string | Dayjs;
  storageCondition?: string;
  sampleQuantity?: number;
  quantityUnit?: string;
  samplingLocation?: string;
  sampledAt?: string | Dayjs;
  remark?: string;
}

export interface SampleBrief {
  id: LongId;
  sampleNo: string;
  sampleType: string;
  status: string;
  batchNo: string;
  taskId?: LongId;
  taskNo?: string;
  taskStatus?: string;
  retainLocation?: string;
  retainUntil?: string;
  packageBatchCheck?: string;
  packageBatchNote?: string;
  disposeType?: string;
}

export interface SamplingVO {
  id: LongId;
  samplingNo: string;
  source: string;
  skuId: LongId;
  skuCode?: string;
  productName?: string;
  productId?: LongId;
  spec?: string;
  packageForm?: string;
  categoryName?: string;
  brand?: string;
  supplierName?: string;
  batchNo: string;
  productionDate?: string;
  expiryDate?: string;
  storageCondition?: string;
  sampleQuantity?: number | string;
  quantityUnit?: string;
  samplingLocation?: string;
  samplerId?: LongId;
  samplerName?: string;
  sampledAt?: string;
  remark?: string;
  cancelReason?: string;
  shelfLifeDaysSnapshot?: number;
  expiryDiffDays?: number;
  status: string;
  processDefId?: LongId;
  lockVersion?: number;
  createdAt?: string;
  samples?: SampleBrief[];
}

// ---------------- 样品 ----------------

export interface SampleReceive {
  samplingId: LongId;
  packageBatchCheck: string;
  packageBatchNote?: string;
  productionDate?: string;
  expiryDate?: string;
  inspectionCount?: number;
  retainFlag?: boolean;
  retainLocation?: string;
  retainUntil?: string;
  backupFlag?: boolean;
}

export interface SampleVO {
  id: LongId;
  sampleNo: string;
  samplingId: LongId;
  samplingNo?: string;
  skuId: LongId;
  skuCode?: string;
  productName?: string;
  spec?: string;
  packageForm?: string;
  sampleType: string;
  batchNo: string;
  productionDate?: string;
  expiryDate?: string;
  packageBatchCheck?: string;
  packageBatchNote?: string;
  status: string;
  receivedAt?: string;
  receiverId?: LongId;
  receiverName?: string;
  retainFlag: number;
  retainLocation?: string;
  retainUntil?: string;
  disposeType?: string;
  disposeRemark?: string;
  disposedAt?: string;
  taskId?: LongId;
  taskNo?: string;
  taskStatus?: string;
  createdAt?: string;
}

// ---------------- 检验任务/结果/报告 ----------------

export interface InspectionResult {
  id: LongId;
  taskId: LongId;
  itemId: LongId;
  groupCode: string;
  itemName: string;
  resultType: string;
  qualitativeValue?: string;
  quantitativeValue?: number | string;
  unit?: string;
  docAttachmentId?: LongId;
  autoJudgement?: string;
  finalJudgement?: string;
  reviewNote?: string;
  remark?: string;
}

export interface TaskListRow {
  id: LongId;
  taskNo: string;
  sampleId: LongId;
  sampleNo?: string;
  skuId?: LongId;
  skuCode?: string;
  productName?: string;
  spec?: string;
  batchNo?: string;
  templateId: LongId;
  templateName?: string;
  templateVersion?: number;
  inspectorId?: LongId;
  inspectorName?: string;
  reviewerId?: LongId;
  reviewerName?: string;
  status: string;
  slaDeadline?: string;
  assignedAt?: string;
  startedAt?: string;
  submittedAt?: string;
  reviewedAt?: string;
  roundNo: number;
  resultCount?: number;
}

export interface JudgeSummary {
  aFailCount: number;
  bFailCount: number;
  cFailCount: number;
  vetoFail: boolean;
  unqualified: boolean;
  concessionPossible: boolean;
  suggestedConclusion: string;
}

export interface InspectionReportEntity {
  id: LongId;
  taskId: LongId;
  reportNo: string;
  conclusion: string;
  aFailCount: number;
  bFailCount: number;
  cFailCount: number;
  inspectorSignHash?: string;
  inspectorSignedAt?: string;
  reviewerSignHash?: string;
  reviewerSignedAt?: string;
  pdfAttachmentId?: LongId;
  status: string;
  issuedAt?: string;
  lockVersion?: number;
}

export interface TaskDetail {
  task: TaskListRow;
  template?: import('@/types/standard').StandardTemplate;
  items: import('@/types/standard').StandardItem[];
  results: InspectionResult[];
  report?: InspectionReportEntity;
  summary?: JudgeSummary;
  allowedActions?: string[];
}

export interface ResultItemInput {
  resultId?: LongId;
  itemId: LongId;
  qualitativeValue?: string;
  quantitativeValue?: number | string;
  docAttachmentId?: LongId;
  remark?: string;
}

export interface ReviewAdjustment {
  resultId: LongId;
  finalJudgement: string;
  reviewNote: string;
}

export interface ReportVO {
  report: InspectionReportEntity;
  taskNo?: string;
  sampleNo?: string;
  skuCode?: string;
  productName?: string;
  spec?: string;
  batchNo?: string;
  inspectorName?: string;
  reviewerName?: string;
  templateName?: string;
  templateVersion?: number;
}

export interface UserOption {
  id: LongId;
  username: string;
  realName: string;
}

export interface AttachmentVO {
  id: LongId;
  bizType: string;
  bizId: LongId;
  fileName: string;
  fileExt?: string;
  fileSize: number;
  contentType?: string;
  createdAt?: string;
}
