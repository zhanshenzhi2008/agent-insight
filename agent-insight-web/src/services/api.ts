import http from './http';
import type {
  ApiResponse, PageResult, RequestSummary, RequestOverview,
  AgentInstance, TaskDetail, TaskStep, LogFile, LogSearchResult,
  TaskTree, LlmCall, LlmCallDetail, TokenUsage,
  ScriptFile, SourceLineMapping,
} from '../types';

// Request retrieval
export const requestApi = {
  search: (params: {
    requestId?: string;
    agentName?: string;
    startTime?: string;
    endTime?: string;
    status?: string;
    page?: number;
    size?: number;
  }) => http.get<ApiResponse<PageResult<RequestSummary>>>('/requests', { params }),

  overview: (requestId: string) =>
    http.get<ApiResponse<RequestOverview>>(`/requests/${requestId}/overview`),

  instances: (requestId: string) =>
    http.get<ApiResponse<AgentInstance[]>>(`/requests/${requestId}/instances`),
};

// 执行轨迹
export const traceApi = {
  getTrace: (requestId: string, agentName?: string) =>
    http.get<ApiResponse<TaskDetail[]>>(`/requests/${requestId}/trace`, {
      params: { agentName },
    }),

  getTree: (requestId: string, agentName?: string) =>
    http.get<ApiResponse<TaskTree>>(`/requests/${requestId}/trace/tree`, {
      params: { agentName },
    }),

  getSteps: (taskDetailId: number) =>
    http.get<ApiResponse<TaskStep[]>>(`/trace/${taskDetailId}/steps`),

  getFailed: (requestId: string) =>
    http.get<ApiResponse<TaskDetail[]>>(`/requests/${requestId}/trace/failed`),
};

// 日志查看
export const logApi = {
  getFile: (requestId: string, username: string, page = 0, pageSize = 5000) =>
    http.get<ApiResponse<LogFile>>(`/requests/${requestId}/log`, {
      params: { username, page, pageSize },
    }),

  search: (requestId: string, username: string, keyword: string, regex = false) =>
    http.get<ApiResponse<LogSearchResult[]>>(`/requests/${requestId}/log/search`, {
      params: { username, keyword, regex },
    }),

  download: (requestId: string, username: string) =>
    `${http.defaults.baseURL}/requests/${requestId}/log/download?username=${username}`,
};

// LLM 调用
export const llmApi = {
  list: (requestId: string) =>
    http.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls`),

  slowCalls: (requestId: string, topN = 10) =>
    http.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls/slow`, {
      params: { topN },
    }),

  tokenUsage: (requestId: string) =>
    http.get<ApiResponse<TokenUsage>>(`/requests/${requestId}/llm-calls/usage`),

  failedCalls: (requestId: string) =>
    http.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls/failed`),

  detail: (callId: number) =>
    http.get<ApiResponse<LlmCallDetail>>(`/llm-calls/${callId}/detail`),
};

// 源码对照
export const sourceApi = {
  listScripts: (agentName: string) =>
    http.get<ApiResponse<ScriptFile[]>>(`/agents/${agentName}/scripts`),

  content: (path: string, startLine?: number, endLine?: number) =>
    http.get<ApiResponse<string>>('/scripts/content', {
      params: { path, startLine, endLine },
    }),

  mapping: (agentName: string, taskUniqueName: string) =>
    http.get<ApiResponse<SourceLineMapping>>(`/agents/${agentName}/scripts/mapping`, {
      params: { taskUniqueName },
    }),
};
