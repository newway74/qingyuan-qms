import { get } from './request';
import type { PageResult } from '@/types/api';
import type { AuditLog } from '@/types/auth';

export const auditApi = {
  logs: (params: Record<string, unknown>) =>
    get<PageResult<AuditLog>>('/audit/logs', { params }),
};
