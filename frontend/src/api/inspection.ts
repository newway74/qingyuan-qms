import { get, post } from './request';
import type { PageResult } from '@/types/api';
import type { LongId } from '@/types/auth';
import type {
  ReportVO,
  ResultItemInput,
  ReviewAdjustment,
  TaskDetail,
  TaskListRow,
  UserOption,
} from '@/types/inspection';

export interface TaskQuery {
  pageNo?: number;
  pageSize?: number;
  view?: 'PENDING_ASSIGN' | 'MINE' | 'ALL';
  status?: string;
  taskNo?: string;
}

export const taskApi = {
  page: (params: TaskQuery) => get<PageResult<TaskListRow>>('/inspection/tasks', { params }),
  detail: (id: LongId) => get<TaskDetail>(`/inspection/tasks/${id}`),
  usersByRole: (roleCode: string) => get<UserOption[]>('/inspection/tasks/users', { params: { roleCode } }),
  assign: (taskId: LongId, inspectorId: LongId, reviewerId: LongId) =>
    post<void>('/inspection/tasks/assign', { taskId, inspectorId, reviewerId }),
  start: (id: LongId) => post<void>(`/inspection/tasks/${id}/start`),
  saveResults: (id: LongId, results: ResultItemInput[]) =>
    post<void>(`/inspection/tasks/${id}/results`, { results }),
  submit: (id: LongId, password: string) =>
    post<void>(`/inspection/tasks/${id}/submit`, { password }),
  review: (
    id: LongId,
    payload: {
      action: 'PASS' | 'REJECT';
      password?: string;
      rejectReason?: string;
      conclusion?: string;
      adjustments?: ReviewAdjustment[];
    },
  ) => post<void>(`/inspection/tasks/${id}/review`, payload),
  close: (id: LongId) => post<void>(`/inspection/tasks/${id}/close`),
  recheck: (id: LongId, reason: string) =>
    post<LongId>(`/inspection/tasks/${id}/recheck`, { reason }),
};

export interface ReportQuery {
  pageNo?: number;
  pageSize?: number;
  reportNo?: string;
  conclusion?: string;
  status?: string;
}

export const reportApi = {
  page: (params: ReportQuery) => get<PageResult<ReportVO>>('/inspection/reports', { params }),
  detail: (id: LongId) => get<ReportVO>(`/inspection/reports/${id}`),
  byTask: (taskId: LongId) => get<ReportVO>(`/inspection/tasks/${taskId}/report`),
  issue: (id: LongId) => post<void>(`/inspection/reports/${id}/issue`),
  pdfUrl: (id: LongId) => `/api/v1/inspection/reports/${id}/pdf`,
};
