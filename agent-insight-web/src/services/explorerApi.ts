import http from './http';

// Explorer API with /explorerHttp/v1/explorer base URL
const explorerHttp = http.create({
  baseURL: '/explorerHttp/v1/explorer',
  timeout: 60000,
});

export interface QueryFilter {
  column: string;
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE' | 'IN' | 'NOT_IN' | 'IS_NULL' | 'IS_NOT_NULL' | 'BETWEEN';
  value?: any;
  value2?: any;
  combine?: 'AND' | 'OR';
}

export interface QueryOrder {
  field: string;
  direction: 'ASC' | 'DESC';
}

export interface ExecuteRequest {
  datasourceKey: string;
  tableName?: string;
  savedQueryId?: string;
  freeSql?: string;
  filters?: QueryFilter[];
  orders?: QueryOrder[];
  selectFields?: string[];
  variables?: Record<string, any>;
  page?: number;
  pageSize?: number;
  countOnly?: boolean;
}

export const explorerApi = {
  // ===== 数据源 =====
  listDatasources: () =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>('/datasources'),

  getDatasource: (id: string) =>
    explorerHttp.get<any, { data: { code: number; data: any } }>(`/datasources/${id}`),

  createDatasource: (data: any) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>('/datasources', data),

  updateDatasource: (id: string, data: any) =>
    explorerHttp.put<any, { data: { code: number; data: any } }>(`/datasources/${id}`, data),

  deleteDatasource: (id: string) =>
    explorerHttp.delete<any, { data: { code: number; data: any } }>(`/datasources/${id}`),

  testDatasourceConnection: (id: string) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>(`/datasources/${id}/test`),

  listExternalTables: (id: string) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>(`/datasources/${id}/tables`),

  // ===== 表配置 =====
  listTables: (datasourceKey: string) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>(`/tables`, {
      params: { datasourceKey },
    }),

  createTable: (data: any) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>('/tables', data),

  updateTable: (id: string, data: any) =>
    explorerHttp.put<any, { data: { code: number; data: any } }>(`/tables/${id}`, data),

  deleteTable: (id: string) =>
    explorerHttp.delete<any, { data: { code: number; data: any } }>(`/tables/${id}`),

  discoverTables: (datasourceKey: string) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>(`/tables/discover`, {
      params: { datasourceKey },
    }),

  // ===== 列配置 =====
  listColumns: (datasourceKey: string, tableName: string) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>(`/columns`, {
      params: { datasourceKey, tableName },
    }),

  saveColumns: (data: any[]) =>
    explorerHttp.post<any, { data: { code: number } }>('/columns', data),

  updateColumn: (id: string, data: any) =>
    explorerHttp.put<any, { data: { code: number; data: any } }>(`/columns/${id}`, data),

  deleteColumn: (id: string) =>
    explorerHttp.delete<any, { data: { code: number } }>(`/columns/${id}`),

  // ===== 查询模板（已保存查询） =====
  listQueryTemplates: (datasourceKey?: string) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>('/queries', {
      params: { datasourceKey },
    }),

  getQueryTemplate: (id: string) =>
    explorerHttp.get<any, { data: { code: number; data: any } }>(`/queries/${id}`),

  createQueryTemplate: (data: any) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>('/queries', data),

  updateQueryTemplate: (id: string, data: any) =>
    explorerHttp.put<any, { data: { code: number; data: any } }>(`/queries/${id}`, data),

  deleteQueryTemplate: (id: string) =>
    explorerHttp.delete<any, { data: { code: number } }>(`/queries/${id}`),

  // ===== 查询执行 =====
  execute: (req: ExecuteRequest) =>
    explorerHttp.post<any, { data: { code: number; data: any; message?: string } }>('/query/execute', req),

  getQueryHistory: (params?: { datasourceKey?: string; tableName?: string; page?: number; size?: number }) =>
    explorerHttp.get<any, { data: { code: number; data: any[] } }>('/query/history', { params }),

  // ===== 智能分析（基于采样统计） =====
  analyzeColumns: (datasourceKey: string, tableName: string) =>
    explorerHttp.post<any, { data: { code: number; data: any[] } }>('/ai/analyze', null, {
      params: { datasourceKey, tableName },
    }),

  // AI 功能状态
  getAiStatus: () =>
    explorerHttp.get<any, { data: { code: number; data: any } }>('/ai/status'),

  // 更新 AI 配置
  updateAiConfig: (data: any) =>
    explorerHttp.put<any, { data: { code: number; data: any } }>('/ai/config', data),

  // AI 增强单列分析
  analyzeColumnWithAi: (
    columnName: string,
    displayName: string,
    dataType: string,
    renderType: string,
    analysisData?: any,
  ) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>(
      '/ai/analyze/column',
      analysisData,
      { params: { columnName, displayName, dataType, renderType } },
    ),

  // AI 批量增强分析
  batchAnalyzeWithAi: (columns: any[]) =>
    explorerHttp.post<any, { data: { code: number; data: any[] } }>('/ai/analyze/batch', columns),

  // 自然语言转查询
  nlQuery: (query: string, columns: any[]) =>
    explorerHttp.post<any, { data: { code: number; data: any } }>('/ai/nl-query', columns, {
      params: { query },
    }),

  // AI 摘要结果
  summarizeResults: (
    rows: any[],
    columns: any[],
    tableName: string,
    userQuestion: string,
  ) =>
    explorerHttp.post<any, { data: { code: number; data: string } }>('/ai/summarize', {
      rows,
      columns,
      tableName,
      userQuestion,
    }),

  // 通用对话（测试模型）
  aiChat: (system: string, message: string) =>
    explorerHttp.post<any, { data: { code: number; data: string } }>('/ai/chat', { system, message }),
};

export default explorerApi;