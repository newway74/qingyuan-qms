import { get, post } from './request';
import type { PageResult } from '@/types/api';

export interface WorkbenchCard {
  key: string;
  label: string;
  value: number;
  tone: 'normal' | 'warning' | 'danger';
}

export interface WorkbenchSummary {
  todoTotal: number;
  unreadMessages: number;
  todoByType: Record<string, number>;
  cards: WorkbenchCard[];
}

export interface TodoRow {
  id: number;
  userId: number | null;
  roleCode: string | null;
  todoType: string;
  bizType: string;
  bizId: number;
  title: string;
  bizNo: string | null;
  priority: number;
  status: number;
  deadline: string | null;
  handledAt: string | null;
  createdAt: string;
}

export interface MessageRow {
  id: number;
  receiverId: number | null;
  roleCode: string | null;
  msgType: string;
  title: string;
  content: string | null;
  bizType: string | null;
  bizId: number | null;
  isRead: number;
  readAt: string | null;
  createdAt: string;
}

export const workbenchApi = {
  summary: () => get<WorkbenchSummary>('/workbench/summary'),
  todos: (params: { pageNo?: number; pageSize?: number; status?: number; todoType?: string }) =>
    get<PageResult<TodoRow>>('/workbench/todos', { params }),
  messages: (params: { pageNo?: number; pageSize?: number; isRead?: number; msgType?: string }) =>
    get<PageResult<MessageRow>>('/workbench/messages', { params }),
  markRead: (id: number) => post<void>(`/workbench/messages/${id}/read`),
  readAll: () => post<{ updated: number }>('/workbench/messages/read-all'),
};
