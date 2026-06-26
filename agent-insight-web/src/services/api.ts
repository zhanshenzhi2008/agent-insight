import axios from 'axios';
import type {
  ApiResponse, PageResult, RequestSummary, RequestOverview,
  AgentInstance, TaskDetail, TaskStep, LogFile, LogSearchResult,
  TaskTree, LlmCall, LlmCallDetail, TokenUsage,
  ScriptFile, SourceLineMapping,
} from '../types';

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
});

// 请求检索
export const requestApi = {
  search: (params: {
    requestId?: string;
    agentName?: string;
    startTime?: string;
    endTime?: string;
    status?: string;
    page?: number;
    size?: number;
  }) => api.get<ApiResponse<PageResult<RequestSummary>>>('/requests', { params }),

  overview: (requestId: string) =>
    api.get<ApiResponse<RequestOverview>>(`/requests/${requestId}/overview`),

  instances: (requestId: string) =>
    api.get<ApiResponse<AgentInstance[]>>(`/requests/${requestId}/instances`),
};

// 执行轨迹
export const traceApi = {
  getTrace: (requestId: string, agentName?: string) =>
    api.get<ApiResponse<TaskDetail[]>>(`/requests/${requestId}/trace`, {
      params: { agentName },
    }),

  getTree: (requestId: string, agentName?: string) =>
    api.get<ApiResponse<TaskTree>>(`/requests/${requestId}/trace/tree`, {
      params: { agentName },
    }),

  getSteps: (taskDetailId: number) =>
    api.get<ApiResponse<TaskStep[]>>(`/trace/${taskDetailId}/steps`),

  getFailed: (requestId: string) =>
    api.get<ApiResponse<TaskDetail[]>>(`/requests/${requestId}/trace/failed`),
};

// 日志查看
export const logApi = {
  getFile: (requestId: string, username: string, page = 0, pageSize = 5000) =>
    api.get<ApiResponse<LogFile>>(`/requests/${requestId}/log`, {
      params: { username, page, pageSize },
    }),

  search: (requestId: string, username: string, keyword: string, regex = false) =>
    api.get<ApiResponse<LogSearchResult[]>>(`/requests/${requestId}/log/search`, {
      params: { username, keyword, regex },
    }),

  download: (requestId: string, username: string) =>
    `${api.defaults.baseURL}/requests/${requestId}/log/download?username=${username}`,
};

// LLM 调用
export const llmApi = {
  list: (requestId: string) =>
    api.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls`),

  slowCalls: (requestId: string, topN = 10) =>
    api.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls/slow`, {
      params: { topN },
    }),

  tokenUsage: (requestId: string) =>
    api.get<ApiResponse<TokenUsage>>(`/requests/${requestId}/llm-calls/usage`),

  failedCalls: (requestId: string) =>
    api.get<ApiResponse<LlmCall[]>>(`/requests/${requestId}/llm-calls/failed`),

  detail: (callId: number) =>
    api.get<ApiResponse<LlmCallDetail>>(`/llm-calls/${callId}/detail`),
};

// 源码对照
export const sourceApi = {
  listScripts: (agentName: string) =>
    api.get<ApiResponse<ScriptFile[]>>(`/agents/${agentName}/scripts`),

  content: (path: string, startLine?: number, endLine?: number) =>
    api.get<ApiResponse<string>>('/scripts/content', {
      params: { path, startLine, endLine },
    }),

  mapping: (agentName: string, taskUniqueName: string) =>
    api.get<ApiResponse<SourceLineMapping>>(`/agents/${agentName}/scripts/mapping`, {
      params: { taskUniqueName },
    }),
};
