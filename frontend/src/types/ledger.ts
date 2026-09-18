/** 商品品控台账模块类型（与后端 com.qms.modules.ledger 对齐） */
import type { LongId } from '@/types/auth';
import type { PageResult } from '@/types/api';

/** 最终合作结论 */
export type CooperateResult = 'YES' | 'PENDING' | 'NO';
/** 流程节点状态 */
export type FlowNodeStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE' | 'REJECTED';
/** 模板节点字段类型 */
export type FieldType = 'TEXT' | 'DATE' | 'SELECT' | 'FILE' | 'CONCLUSION' | 'TEXTAREA';
/** 资料项状态 */
export type MaterialStatus = 'READY' | 'MISSING' | 'PENDING';
/** 数据来源 */
export type DataSource = 'USER' | 'DEMO';

export interface GoodsListQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  categoryL1Id?: LongId;
  categoryL2Id?: LongId;
  cooperateResult?: CooperateResult;
  currentNodeCode?: string;
}

export interface GoodsListVO {
  id: LongId;
  sku: string;
  commonName: string;
  spec?: string;
  manufacturer?: string;
  approvalNo?: string;
  upc?: string;
  brand?: string;
  categoryL1Id?: LongId;
  categoryL2Id?: LongId;
  categoryL1Name?: string;
  categoryL2Name?: string;
  meetingDate?: string;
  cooperateResult: CooperateResult;
  launchDate?: string;
  currentNodeCode?: string;
  currentNodeName?: string;
  templateId?: LongId;
  templateVersion?: number;
  npiProjectId?: LongId;
  dataSource: DataSource;
  remark?: string;
  gapCount: number;
  createdAt: string;
  updatedAt: string;
}

export type GoodsPage = PageResult<GoodsListVO>;

export interface GoodsUpsertRequest {
  id?: LongId;
  sku: string;
  commonName: string;
  spec?: string;
  manufacturer?: string;
  approvalNo?: string;
  upc?: string;
  brand?: string;
  categoryL1Id?: LongId;
  categoryL2Id?: LongId;
  meetingDate?: string;
  cooperateResult?: CooperateResult;
  launchDate?: string;
  templateId?: LongId;
  npiProjectId?: LongId;
  remark?: string;
}

export interface FlowNodeFieldDefVO {
  id: LongId;
  fieldCode: string;
  fieldName: string;
  fieldType: FieldType;
  options?: string[];
  required: number;
  sort?: number;
}

export interface FlowNodeVO {
  id: LongId;
  nodeCode: string;
  nodeName: string;
  nodeSort: number;
  linkSupplier: number;
  supplierId?: LongId;
  supplierName?: string;
  status: FlowNodeStatus;
  finishDate?: string;
  ownerName?: string;
  conclusion?: string;
  remark?: string;
  fieldValues: Record<string, unknown>;
  fieldDefs: FlowNodeFieldDefVO[];
}

export interface MaterialVO {
  id: LongId;
  itemId: LongId;
  itemCode?: string;
  itemName: string;
  status: MaterialStatus;
  remark?: string;
  /** 资料项定义是否仍启用（停用/删除项的历史快照不计入缺口） */
  enabled?: boolean;
}

/** 节点办理记录更新请求 */
export interface NodeRecordUpsertRequest {
  status: FlowNodeStatus;
  finishDate?: string;
  ownerName?: string;
  conclusion?: string;
  supplierId?: LongId;
  remark?: string;
  fieldValues?: Record<string, unknown>;
}

/** 商品资料状态更新请求 */
export interface GoodsMaterialUpsertRequest {
  status: MaterialStatus;
  remark?: string;
}

/** 资料项维护请求（仅管理员） */
export interface MaterialItemUpsertRequest {
  id?: LongId;
  itemName: string;
  sort?: number;
  status?: number;
}

/** 供应商简要选项（节点关联供应商） */
export interface SupplierOption {
  id: LongId;
  supplierCode: string;
  supplierName: string;
  status: string;
}

export interface GoodsDetailVO extends Omit<GoodsUpsertRequest, 'id'> {
  id: LongId;
  categoryL1Name?: string;
  categoryL2Name?: string;
  templateName?: string;
  templateVersion?: number;
  latestTemplateVersion?: number;
  templateOutdated?: boolean;
  currentNodeCode?: string;
  currentNodeName?: string;
  dataSource: DataSource;
  gapCount: number;
  nodes: FlowNodeVO[];
  materials: MaterialVO[];
}

/** ---------- 流程模板 ---------- */

export interface FlowTemplate {
  id: LongId;
  templateCode: string;
  templateName: string;
  version: number;
  isPreset: number;
  status: number;
  remark?: string;
}

export interface FlowNodeFieldInput {
  id?: LongId;
  fieldCode?: string;
  fieldName: string;
  fieldType: FieldType;
  options?: string[];
  required?: number;
  sort?: number;
}

export interface FlowNodeInput {
  id?: LongId;
  nodeCode?: string;
  nodeName: string;
  nodeSort?: number;
  linkSupplier?: number;
  remark?: string;
  fields?: FlowNodeFieldInput[];
}

export interface FlowNodeBatchSaveRequest {
  nodes: FlowNodeInput[];
}

export interface FlowTemplateUpsertRequest {
  id?: LongId;
  templateName: string;
  status?: number;
  remark?: string;
}

export interface FlowTemplateDetailVO extends FlowTemplate {
  boundGoodsCount: number;
  nodes: Array<{
    id: LongId;
    nodeCode: string;
    nodeName: string;
    nodeSort: number;
    linkSupplier: number;
    remark?: string;
    fields: FlowNodeFieldDefVO[];
  }>;
}

/** ---------- 资料项 ---------- */

export interface MaterialItemVO {
  id: LongId;
  itemCode: string;
  itemName: string;
  sort: number;
  isPreset: number;
  status: number;
}

/** ---------- Excel 批量导入 ---------- */

/** 导入时对已存在 SKU 的处理策略 */
export type GoodsImportStrategy = 'UPDATE' | 'SKIP';

export interface GoodsImportRowResult {
  /** Excel 物理行号（表头为第 1 行） */
  rowNum: number;
  sku?: string;
  /** CREATED 新建 / UPDATED 更新 / SKIPPED 跳过 / FAILED 失败 */
  action: 'CREATED' | 'UPDATED' | 'SKIPPED' | 'FAILED';
  success: boolean;
  reason?: string;
}

export interface GoodsImportResultVO {
  total: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  failedCount: number;
  rows: GoodsImportRowResult[];
}

/** ---------- 演示数据 ---------- */

export interface DemoDataStatsVO {
  goodsCount: number;
  supplierCount: number;
  flowCount: number;
  flowNodeCount: number;
  materialCount: number;
  cleared?: boolean;
}

/** ---------- 品控工作台 ---------- */

export interface DashboardCards {
  goodsTotal: number;
  supplierTotal: number;
  flowRecordTotal: number;
  gapItemTotal: number;
}

export interface NameValue {
  name: string;
  value: number;
}

export interface LedgerDashboardVO {
  cards: DashboardCards;
  cooperateResultDist: NameValue[];
  currentNodeDist: NameValue[];
  categoryDist: NameValue[];
}

export interface MaterialGapItem {
  itemName: string;
  /** MISSING 缺失 / PENDING 待确认 */
  status: MaterialStatus;
}

export interface MaterialGapVO {
  goodsId: LongId;
  sku: string;
  commonName: string;
  brand?: string;
  categoryL1Name?: string;
  currentNodeName?: string;
  gapCount: number;
  gapItems: MaterialGapItem[];
}
