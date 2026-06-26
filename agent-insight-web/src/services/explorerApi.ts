  // 智能分析（基于采样统计）
  analyzeColumns: (datasourceKey: string, tableName: string) =>
    api.post('/ai/analyze', null, { params: { datasourceKey, tableName } }),

  // AI 功能状态
  getAiStatus: () => api.get('/ai/status'),

  // 更新 AI 配置
  updateAiConfig: (data: any) => api.put('/ai/config', data),

  // AI 增强单列分析
  analyzeColumnWithAi: (columnName: string, displayName: string,
    dataType: string, renderType: string, analysisData?: any) =>
    api.post('/ai/analyze/column', analysisData, {
      params: { columnName, displayName, dataType, renderType },
    }),

  // AI 批量增强分析
  batchAnalyzeWithAi: (columns: any[]) =>
    api.post('/ai/analyze/batch', columns),

  // 自然语言转查询
  nlQuery: (query: string, columns: any[]) =>
    api.post('/ai/nl-query', columns, { params: { query } }),

  // AI 摘要结果
  summarizeResults: (rows: any[], columns: any[], tableName: string, userQuestion: string) =>
    api.post('/ai/summarize', { rows, columns, tableName, userQuestion }),

  // 通用对话（测试模型）
  aiChat: (system: string, message: string) =>
    api.post('/ai/chat', { system, message }),
