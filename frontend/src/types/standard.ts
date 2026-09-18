import type { LongId } from './auth';

export interface StandardTemplate {
  id: LongId;
  templateCode: string;
  templateName: string;
  categoryId: LongId;
  packageForm?: string;
  /** HIGH/MID/LOW，仅新品档位标准有值 */
  grade?: string | null;
  npiProjectId?: LongId | null;
  /** 国标/法规依据 */
  regulationBasis?: string | null;
  /** 市面竞品对标 */
  marketBenchmark?: string | null;
  version: number;
  status: string;
  maxAFail: number;
  maxBFail: number;
  maxCFail: number;
  concessionAllowed: number;
  remark?: string;
  publishedBy?: LongId;
  publishedAt?: string;
  lockVersion?: number;
}

/** 标准评审记录（采购会签/老板批准，只增） */
export interface StdReview {
  id: LongId;
  templateId: LongId;
  /** SUBMIT/PROCUREMENT/BOSS */
  node: string;
  /** SUBMIT/PASS/REJECT/COMMENT */
  action: string;
  comment?: string | null;
  operatorId?: LongId | null;
  operatorName?: string | null;
  operatedAt: string;
}

export interface GradeTemplateGenerate {
  projectId: LongId;
  grade: string;
  templateName?: string;
  regulationBasis?: string;
  marketBenchmark?: string;
}

export interface StdReviewRequest {
  /** PROCUREMENT / BOSS */
  node: string;
  pass: boolean;
  comment?: string;
}

export interface StandardItem {
  id?: LongId;
  templateId?: LongId;
  groupCode: string;
  itemName: string;
  inspectMethod?: string;
  resultType: string;
  defectLevel: string;
  vetoFlag: number;
  requiredFlag: number;
  minValue?: number | string;
  maxValue?: number | string;
  nominalValue?: number | string;
  unit?: string;
  toleranceRule?: string;
  judgeConfig?: Record<string, unknown> | null;
  sort: number;
}

export interface TemplateDetail {
  template: StandardTemplate;
  categoryName?: string;
  items: StandardItem[];
}

export interface TemplateUpsert {
  id?: LongId;
  templateCode?: string;
  templateName: string;
  categoryId: LongId;
  packageForm?: string;
  grade?: string | null;
  npiProjectId?: LongId | null;
  regulationBasis?: string;
  marketBenchmark?: string;
  maxAFail?: number;
  maxBFail?: number;
  maxCFail?: number;
  concessionAllowed?: number;
  remark?: string;
}

export interface StandardItemInput {
  groupCode: string;
  itemName: string;
  inspectMethod?: string;
  resultType: string;
  defectLevel: string;
  vetoFlag?: number;
  requiredFlag?: number;
  minValue?: number;
  maxValue?: number;
  nominalValue?: number;
  unit?: string;
  toleranceRule?: string;
  judgeConfig?: Record<string, unknown> | null;
  sort?: number;
}
