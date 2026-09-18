import type { LongId } from './auth';

export interface ProcessDef {
  id: LongId;
  processCode: string;
  processName: string;
  version: number;
  status: string;
  remark?: string;
  publishedBy?: LongId;
  publishedAt?: string;
  lockVersion?: number;
}

export interface ProcessNode {
  id?: LongId;
  processDefId?: LongId;
  nodeCode: string;
  nodeName: string;
  responsibleRole: string;
  slaHours: number;
  calendarType: string;
  requiredFields?: string[];
  transitionRules?: Record<string, unknown> | null;
  sort: number;
}

export interface ProcessChangeLog {
  id: LongId;
  processDefId: LongId;
  processCode: string;
  fromVersion?: number;
  toVersion: number;
  changeDiff?: {
    added?: string[];
    removed?: string[];
    changed?: Array<{
      nodeCode: string;
      before: Record<string, unknown>;
      after: Record<string, unknown>;
    }>;
  } | null;
  changedBy?: LongId;
  changedAt?: string;
}

export interface ProcessDetail {
  processDef: ProcessDef;
  nodes: ProcessNode[];
}

export interface ProcessNodeInput {
  nodeCode: string;
  nodeName: string;
  responsibleRole: string;
  slaHours: number;
  calendarType?: string;
  requiredFields?: string[];
  transitionRules?: Record<string, unknown> | null;
  sort?: number;
}
