import request, { del, get, post, put } from './request';
import type { LongId } from '@/types/auth';
import type { PageResult } from '@/types/api';
import type {
  DemoDataStatsVO,
  FlowNodeBatchSaveRequest,
  FlowTemplate,
  FlowTemplateDetailVO,
  FlowTemplateUpsertRequest,
  GoodsDetailVO,
  GoodsImportResultVO,
  GoodsImportStrategy,
  GoodsListQuery,
  GoodsMaterialUpsertRequest,
  GoodsPage,
  GoodsUpsertRequest,
  LedgerDashboardVO,
  MaterialGapVO,
  MaterialItemUpsertRequest,
  MaterialItemVO,
  NodeRecordUpsertRequest,
  SupplierOption,
} from '@/types/ledger';

/** 商品全流程品控台账接口 */
export const ledgerApi = {
  // 商品台账
  goodsPage: (params: GoodsListQuery) => get<GoodsPage>('/ledger/goods', { params }),
  goodsDetail: (id: LongId) => get<GoodsDetailVO>(`/ledger/goods/${id}`),
  goodsCreate: (data: GoodsUpsertRequest) => post<LongId>('/ledger/goods', data),
  goodsUpdate: (data: GoodsUpsertRequest) => put<void>('/ledger/goods', data),
  goodsRemove: (id: LongId) => del<void>(`/ledger/goods/${id}`),

  // Excel 批量导入
  goodsImport: async (file: File, strategy: GoodsImportStrategy): Promise<GoodsImportResultVO> => {
    const form = new FormData();
    form.append('file', file);
    form.append('strategy', strategy);
    return request.post('/ledger/goods/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    }) as unknown as Promise<GoodsImportResultVO>;
  },
  goodsImportTemplateUrl: () => `/api/v1/ledger/goods/import-template`,

  // 演示数据（一键清除）
  demoStats: () => get<DemoDataStatsVO>('/ledger/demo/stats'),
  demoClear: () => request.delete('/ledger/demo') as unknown as Promise<DemoDataStatsVO>,

  // 品控工作台
  dashboardSummary: () => get<LedgerDashboardVO>('/ledger/dashboard/summary'),
  dashboardGaps: (params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<MaterialGapVO>>('/ledger/dashboard/gaps', { params }),
  exportGoodsUrl: '/ledger/dashboard/export/goods',
  exportFlowsUrl: '/ledger/dashboard/export/flows',
  exportGapsUrl: '/ledger/dashboard/export/gaps',

  // 流程办理（节点记录 + 资料状态）
  nodeUpdate: (goodsId: LongId, nodeCode: string, data: NodeRecordUpsertRequest) =>
    put<void>(`/ledger/goods/${goodsId}/nodes/${nodeCode}`, data),
  materialUpdate: (goodsId: LongId, materialId: LongId, data: GoodsMaterialUpsertRequest) =>
    put<void>(`/ledger/goods/${goodsId}/materials/${materialId}`, data),
  supplierOptions: () => get<SupplierOption[]>('/ledger/supplier-options'),

  // 资料项定义（管理员）
  materialItemList: () => get<MaterialItemVO[]>('/ledger/material-items'),
  materialItemCreate: (data: MaterialItemUpsertRequest) =>
    post<LongId>('/ledger/material-items', data),
  materialItemUpdate: (data: MaterialItemUpsertRequest) =>
    put<void>('/ledger/material-items', data),
  materialItemRemove: (id: LongId) => del<void>(`/ledger/material-items/${id}`),

  // 流程模板
  templateList: () => get<FlowTemplate[]>('/ledger/flow-templates'),
  templateEnabled: () => get<FlowTemplate[]>('/ledger/flow-templates/enabled'),
  templateDetail: (id: LongId) => get<FlowTemplateDetailVO>(`/ledger/flow-templates/${id}`),
  templateCreate: (data: FlowTemplateUpsertRequest) =>
    post<LongId>('/ledger/flow-templates', data),
  templateUpdate: (data: FlowTemplateUpsertRequest) =>
    put<void>('/ledger/flow-templates', data),
  templateSaveNodes: (id: LongId, data: FlowNodeBatchSaveRequest) =>
    put<LongId>(`/ledger/flow-templates/${id}/nodes`, data),
  templateCopy: (id: LongId, newName?: string) =>
    post<LongId>(`/ledger/flow-templates/${id}/copy`, undefined, {
      params: newName ? { newName } : undefined,
    }),
  templateRemove: (id: LongId) => del<void>(`/ledger/flow-templates/${id}`),
};
