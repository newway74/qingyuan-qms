import { del, get, post, put } from './request';
import type { AxiosRequestConfig } from 'axios';
import type { PageResult } from '@/types/api';
import type { Category, CategoryTree, LongId } from '@/types/auth';

export interface CategoryQuery {
  pageNo?: number;
  pageSize?: number;
  name?: string;
  code?: string;
  status?: number;
}

export interface CategoryUpsert {
  id?: LongId;
  /** 0=顶级品类；后端 Long，字符串/数字均可被反序列化 */
  parentId: LongId | number;
  code: string;
  name: string;
  sort?: number;
  status: number;
}

export const categoryApi = {
  page: (params: CategoryQuery) => get<PageResult<Category>>('/master/categories', { params }),
  tree: () => get<CategoryTree[]>('/master/categories/tree'),
  create: (data: CategoryUpsert, config?: AxiosRequestConfig) =>
    post<LongId>('/master/categories', data, config),
  update: (data: CategoryUpsert) => put<void>('/master/categories', data),
  remove: (id: LongId | number) => del<void>(`/master/categories/${id}`),
};
