/**
 * 后端雪花主键为 19 位 Long，超出 JS 安全整数范围，
 * 后端统一以字符串下发，前端全程透传，不做数值运算。
 */
export type LongId = string;

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  realName: string;
}

export interface UserInfo {
  userId: LongId;
  username: string;
  realName: string;
  deptId?: LongId;
  deptName?: string;
  phone?: string;
  roles: string[];
  permissions: string[];
  dataScope: string;
}

export interface Category {
  id: LongId;
  parentId: LongId;
  code: string;
  name: string;
  sort: number;
  status: number;
  createdAt?: string;
}

export interface CategoryTree {
  id: LongId;
  parentId: LongId;
  code: string;
  name: string;
  sort: number;
  status: number;
  children?: CategoryTree[];
}

export interface AuditLog {
  id: LongId;
  traceId?: string;
  userId?: LongId;
  username?: string;
  module: string;
  action: string;
  bizType?: string;
  bizId?: LongId;
  beforeValue?: string;
  afterValue?: string;
  ip?: string;
  result: number;
  costMs?: number;
  createdAt: string;
}
