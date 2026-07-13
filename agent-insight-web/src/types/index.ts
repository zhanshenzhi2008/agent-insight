export const API_BASE = '/api/v1';

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  hasNext: boolean;
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface RequestSummary {
  requestId: string;
  topAgentName: string;
  agentId: number;
  taskStatus: number;
  success: boolean;
  totalTaskCount: number;
  failedTaskCount: number;
  totalDuration: number;
  createTime: string;
  subAgentNames: string[];
}

export interface RequestOverview {
  requestId: string;
  topAgentName: string;
  success: boolean;
  totalTaskCount: number;
  failedTaskCount: number;
  totalDuration: number;
  llmCallCount: number;
  totalPromptTokens: number;
  totalCompletionTokens: number;
  createTime: string;
  agentInstances: AgentInstance[];
}

export interface AgentInstance {
  agentId: number;
  agentName: string;
  entrance: boolean;
  taskCount: number;
  success: boolean;
  duration: number;
}

export interface TaskDetail {
  id: number;
  requestId: string;
  agentName: string;
  taskName: string;
  taskUniqueName: string;
  taskType: string;
  taskIndex: number;
  fullPath: string;
  success: boolean;
  result: string;
  resultType: number;
  errorMessage: string;
  duration: number;
  agentTryCount: number;
  taskTryCount: number;
  finalResult: boolean;
  createTime: string;
  taskEndTime: string;
  steps: TaskStep[];
  sourceFile: string;
  sourceStartLine: number;
  sourceEndLine: number;
}

export interface TaskStep {
  id: number;
  step: number;
  stepLabel: string;
  template: string;
  input: string;
  output: string;
  resultType: number;
  success: boolean;
  duration: number;
  endTime: string;
}

export interface LogFile {
  fileName: string;
  fileSize: number;
  requestId: string;
  lines: string[];
  startLine: number;
  endLine: number;
  hasMore: boolean;
  status: string;
  message: string;
}

export interface LogSearchResult {
  lineNumber: number;
  lineContent: string;
}

export interface TaskTree {
  requestId: string;
  agentName: string;
  roots: TaskTreeNode[];
}

export interface TaskTreeNode {
  id: number;
  name: string;
  type: string;
  success: boolean;
  duration: number;
  children: TaskTreeNode[];
}

export interface LlmCall {
  id: number;
  requestId: string;
  agent: string;
  templateName: string;
  planUniqueName: string;
  modelType: string;
  spendTime: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  successExpression: boolean;
  createTime: string;
  requestBodyPreview: string;
  responseBodyPreview: string;
}

export interface LlmCallDetail {
  id: number;
  agent: string;
  templateName: string;
  modelType: string;
  spendTime: number;
  promptTokens: number;
  completionTokens: number;
  successExpression: boolean;
  requestBody: string;
  requestUrl: string;
  responseBody: string;
  createTime: string;
}

export interface TokenUsage {
  totalPromptTokens: number;
  totalCompletionTokens: number;
  totalTokens: number;
  callCount: number;
}

export interface ScriptFile {
  fileName: string;
  fullPath: string;
  extension: string;
  fileSize: number;
  lastModified: number;
}

export interface SourceLineMapping {
  agentName: string;
  taskUniqueName: string;
  filePath: string;
  startLine: number;
  endLine: number;
}
