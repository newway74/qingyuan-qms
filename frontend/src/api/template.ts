import { get, post, put } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type {
  GradeTemplateGenerate,
  StandardItem,
  StandardItemInput,
  StdReview,
  StdReviewRequest,
  TemplateDetail,
  StandardTemplate,
  TemplateUpsert,
} from '@/types/standard';

export interface TemplateQuery {
  pageNo?: number;
  pageSize?: number;
  templateName?: string;
  templateCode?: string;
  categoryId?: LongId;
  status?: string;
  grade?: string;
  npiProjectId?: LongId;
}

export const templateApi = {
  page: (params: TemplateQuery) => get<PageResult<StandardTemplate>>('/standard/templates', { params }),
  detail: (id: LongId) => get<TemplateDetail>(`/standard/templates/${id}`),
  versions: (id: LongId) => get<StandardTemplate[]>(`/standard/templates/${id}/versions`),
  effective: (categoryId: LongId, packageForm?: string) =>
    get<TemplateDetail>('/standard/templates/effective', { params: { categoryId, packageForm } }),
  create: (data: TemplateUpsert) => post<LongId>('/standard/templates', data),
  update: (data: TemplateUpsert) => put<void>('/standard/templates', data),
  revise: (id: LongId) => post<LongId>(`/standard/templates/${id}/revise`),
  copy: (id: LongId, data: { templateCode: string; templateName?: string }) =>
    post<LongId>(`/standard/templates/${id}/copy`, data),
  publish: (id: LongId) => post<void>(`/standard/templates/${id}/publish`),
  saveItems: (id: LongId, items: StandardItemInput[]) =>
    put<void>(`/standard/templates/${id}/items`, { items }),
  items: (id: LongId) => get<StandardItem[]>(`/standard/templates/${id}/items`),
  byProject: (projectId: LongId) => get<StandardTemplate[]>(`/standard/templates/by-project/${projectId}`),
  reviews: (id: LongId) => get<StdReview[]>(`/standard/templates/${id}/reviews`),
  generateGrade: (data: GradeTemplateGenerate) => post<LongId>('/standard/templates/grade/generate', data),
  submitReview: (id: LongId) => post<void>(`/standard/templates/${id}/review/submit`),
  review: (id: LongId, data: StdReviewRequest) => post<void>(`/standard/templates/${id}/review`, data),
};
