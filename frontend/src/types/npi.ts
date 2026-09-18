import type { LongId } from './auth';
import type { StandardTemplate, StdReview } from './standard';

// ---------------- 项目 ----------------

export interface NpiProject {
  id: LongId;
  projectNo: string;
  projectName: string;
  categoryId: LongId;
  brand?: string | null;
  background?: string | null;
  meetingAt?: string | null;
  attendees?: string | null;
  initiatorId?: LongId | null;
  targetListingDate?: string | null;
  /** DRAFT/STD_REVIEW/SOURCING/AUDIT/PRODUCING/EXT_TEST/LISTING_REVIEW/LISTED/TERMINATED */
  status: string;
  targetGrade?: string | null;
  selectedTemplateId?: LongId | null;
  chosenSupplierId?: LongId | null;
  productId?: LongId | null;
  stdSubmittedAt?: string | null;
  stdApprovedAt?: string | null;
  supplierFixedAt?: string | null;
  auditPassedAt?: string | null;
  extTestPassedAt?: string | null;
  listedAt?: string | null;
  terminateReason?: string | null;
  lockVersion?: number;
}

export interface ProjectListRow {
  project: NpiProject;
  categoryName?: string;
  chosenSupplierName?: string;
  productName?: string;
  initiatorName?: string;
}

export interface GateStatus {
  name: string;
  ok: boolean;
  detail: string;
}

export interface NpiTimeline {
  id: LongId;
  projectId: LongId;
  stage: string;
  action: string;
  title: string;
  comment?: string | null;
  operatorId?: LongId | null;
  operatorName?: string | null;
  createdAt: string;
}

// ---------------- 送样评估 ----------------

export interface NpiEvalItem {
  id?: LongId;
  evalId?: LongId;
  /** QUALITY/PACKAGE/PRICE/DELIVERY/SERVICE */
  dimensionCode: string;
  dimensionName: string;
  score?: number | string | null;
  weight?: number | string | null;
  note?: string | null;
  sort?: number;
}

export interface NpiEval {
  id: LongId;
  evalNo: string;
  projectId: LongId;
  supplierId: LongId;
  roundNo: number;
  sampleDesc?: string | null;
  receivedAt?: string | null;
  /** PENDING/QUALIFIED/UNQUALIFIED */
  qualityConclusion?: string | null;
  totalScore?: number | string | null;
  rankNo?: number | null;
  selectedFlag?: number;
  reportAttachmentId?: LongId | null;
  remark?: string | null;
  /** DRAFT/SUBMITTED */
  status: string;
  lockVersion?: number;
}

export interface EvalDetail {
  eval: NpiEval;
  supplierName?: string;
  items: NpiEvalItem[];
}

export interface EvalItemInput {
  dimensionCode: string;
  dimensionName: string;
  score?: number | null;
  weight?: number | null;
  note?: string;
}

export interface EvalUpsert {
  id?: LongId;
  projectId: LongId;
  supplierId: LongId;
  roundNo?: number;
  sampleDesc?: string;
  receivedAt?: string;
  qualityConclusion?: string;
  remark?: string;
  items: EvalItemInput[];
}

// ---------------- 验厂 ----------------

export interface FactoryAudit {
  id: LongId;
  auditNo: string;
  projectId: LongId;
  supplierId: LongId;
  parentAuditId?: LongId | null;
  /** INITIAL/FOLLOW_UP */
  auditType: string;
  plannedAt?: string | null;
  auditedAt?: string | null;
  leaderId?: LongId | null;
  auditors?: string | null;
  totalScore?: number | string | null;
  /** PASS/CONDITIONAL/FAIL */
  conclusion?: string | null;
  rectifyRequirement?: string | null;
  rectifyDeadline?: string | null;
  reportAttachmentId?: LongId | null;
  /** PLANNED/IN_PROGRESS/SUBMITTED/CONFIRMED */
  status: string;
  remark?: string | null;
  lockVersion?: number;
}

export interface FactoryAuditItem {
  id?: LongId;
  auditId?: LongId;
  /** QUALIFICATION/PRODUCTION/WAREHOUSE/COLD_CHAIN/QUALITY_SYSTEM */
  category: string;
  itemName: string;
  requirement?: string | null;
  maxScore?: number | string;
  score?: number | string | null;
  /** COMPLIANT/MINOR/MAJOR/NA */
  result?: string | null;
  note?: string | null;
  sort: number;
}

export interface AuditDetail {
  audit: FactoryAudit;
  supplierName?: string;
  projectName?: string;
  items: FactoryAuditItem[];
}

export interface AuditListRow {
  audit: FactoryAudit;
  supplierName?: string;
  projectName?: string;
}

export interface AuditItemInput {
  category: string;
  itemName: string;
  requirement?: string;
  maxScore?: number;
  score?: number | null;
  result?: string | null;
  note?: string;
  sort?: number;
}

export interface AuditUpsert {
  id?: LongId;
  projectId: LongId;
  supplierId: LongId;
  parentAuditId?: LongId;
  auditType?: string;
  plannedAt?: string;
  auditedAt?: string;
  auditors?: string;
  rectifyRequirement?: string;
  rectifyDeadline?: string;
  remark?: string;
  /** 传入即确认结论：PASS/CONDITIONAL/FAIL */
  confirmConclusion?: string | null;
  items: AuditItemInput[];
}

// ---------------- 外检 ----------------

export interface ExternalTest {
  id: LongId;
  testNo: string;
  projectId: LongId;
  supplierId: LongId;
  productName?: string | null;
  sampleDesc?: string | null;
  labName?: string | null;
  /** CMA/CNAS/NMPA/OTHER */
  labQualification?: string | null;
  testItems?: string | null;
  sentAt?: string | null;
  reportNo?: string | null;
  reportDate?: string | null;
  /** PENDING/PASS/FAIL/PARTIAL */
  conclusion?: string | null;
  reportAttachmentId?: LongId | null;
  /** PLANNED/SENT/REPORTED */
  status: string;
  remark?: string | null;
  lockVersion?: number;
}

export interface ExtTestListRow {
  test: ExternalTest;
  projectName?: string;
}

export interface ExtTestUpsert {
  id?: LongId;
  projectId: LongId;
  supplierId: LongId;
  productName?: string;
  sampleDesc?: string;
  labName?: string;
  labQualification?: string;
  testItems?: string;
  sentAt?: string;
  reportNo?: string;
  reportDate?: string;
  conclusion?: string;
  remark?: string;
}

// ---------------- 详情聚合 ----------------

export interface ProjectDetail {
  project: NpiProject;
  categoryName?: string;
  chosenSupplierName?: string;
  productName?: string;
  initiatorName?: string;
  gradeTemplates: Record<string, StandardTemplate>;
  templateReviews: Record<string, StdReview[]>;
  evals: EvalDetail[];
  audits: AuditDetail[];
  extTests: ExternalTest[];
  timeline: NpiTimeline[];
  allowedActions: string[];
  gates: GateStatus[];
}

export interface ProjectUpsert {
  id?: LongId;
  projectName: string;
  categoryId: LongId;
  brand?: string;
  background?: string;
  meetingAt?: string;
  attendees?: string;
  targetListingDate?: string;
}

export interface ProjectActionRequest {
  comment?: string;
  grade?: string;
  templateId?: LongId;
  supplierId?: LongId;
  evalId?: LongId;
  productId?: LongId;
}
