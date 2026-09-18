export interface R<T> {
  code: string;
  message: string;
  data: T;
  traceId?: string;
}

export interface PageResult<T> {
  /** 后端 long 以字符串下发，使用处需 Number() 转换 */
  total: number | string;
  pageNo: number | string;
  pageSize: number | string;
  records: T[];
}

export interface PageQuery {
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}
