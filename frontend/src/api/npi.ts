import { get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type {
  AuditDetail,
  AuditListRow,
  AuditUpsert,
  EvalDetail,
  EvalUpsert,
  ExtTestListRow,
  ExtTestUpsert,
  ExternalTest,
  FactoryAuditItem,
  ProjectActionRequest,
  ProjectDetail,
  ProjectListRow,
  ProjectUpsert,
} from '@/types/npi';

export interface ProjectQuery {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  projectName?: string;
}

export interface AuditQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: LongId;
  status?: string;
}

export interface ExtTestQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: LongId;
  status?: string;
}

export const npiApi = {
  page: (params: ProjectQuery) => get<PageResult<ProjectListRow>>('/npi/projects', { params }),
  detail: (id: LongId) => get<ProjectDetail>(`/npi/projects/${id}`),
  create: (data: ProjectUpsert) => post<LongId>('/npi/projects', data),
  update: (data: ProjectUpsert) => put<void>('/npi/projects', data),
  act: (id: LongId, action: string, data?: ProjectActionRequest) =>
    post<void>(`/npi/projects/${id}/actions/${action}`, data || {}),
};

export const evalApi = {
  detail: (id: LongId) => get<EvalDetail>(`/npi/evals/${id}`),
  save: (data: EvalUpsert) => post<LongId>('/npi/evals', data),
  submit: (id: LongId) => post<void>(`/npi/evals/${id}/submit`),
};

export const auditApi = {
  page: (params: AuditQuery) => get<PageResult<AuditListRow>>('/npi/audits', { params }),
  detail: (id: LongId) => get<AuditDetail>(`/npi/audits/${id}`),
  defaultItems: () => get<FactoryAuditItem[]>('/npi/audits/default-items'),
  save: (data: AuditUpsert) => post<LongId>('/npi/audits', data),
};

export const extTestApi = {
  page: (params: ExtTestQuery) => get<PageResult<ExtTestListRow>>('/npi/ext-tests', { params }),
  detail: (id: LongId) => get<ExternalTest>(`/npi/ext-tests/${id}`),
  save: (data: ExtTestUpsert) => post<LongId>('/npi/ext-tests', data),
};
