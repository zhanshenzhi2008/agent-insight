# Agent Insight 技术设计说明书（HLD）

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v0.1 | 2026-06-25 | - | 初稿 |

---

## 1. 系统架构

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（React + Ant Design）                │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐   │
│  │  请求检索    │ │ 轨迹可视化   │ │   源码对照视图        │   │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘   │
└─────────┼─────────────────┼─────────────────────┼───────────────┘
          │                 │                     │
          └─────────────────┼─────────────────────┘
                            │ HTTP / WebSocket
┌───────────────────────────┼─────────────────────────────────────┐
│                    Spring Boot 3.5 应用                          │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │                  Controller 层                             │    │
│  │   RequestController  TraceController  SourceController     │    │
│  └────────────────────────┬────────────────────────────────┘    │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │                  Service 层                               │    │
│  │  RequestSearchService  TraceAnalysisService  SourceService │    │
│  │  LlmCallAnalysisService  RootCauseService (future)        │    │
│  └────────────────────────┬────────────────────────────────┘    │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │                  Repository / 数据接入层                  │    │
│  │  JPA Repository (MySQL) │ FileReader │ ScriptRepository  │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
    ┌───────────┐    ┌───────────┐    ┌──────────────┐
    │  MySQL    │    │ FileSystem│    │ Script Repo  │
    │  (日志表)  │    │ (.log)    │    │  (源码)      │
    └───────────┘    └───────────┘    └──────────────┘
```

### 1.2 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| 后端框架 | Spring Boot 3.5 + Java 21 | 与 llm-agent 现有技术栈一致 |
| ORM | Spring Data JPA + Hibernate | 访问 MySQL 日志表 |
| 前端 | React 18 + Ant Design 5 | 企业级 UI 组件库 |
| 可视化 | React Flow / D3.js | 执行轨迹图 / 时间线图 |
| 代码展示 | CodeMirror 6 / Monaco Editor | 源码查看与高亮 |
| 构建工具 | Maven + Vite | 后端 Maven / 前端 Vite |
| 部署 | Docker | 容器化部署 |

### 1.3 模块划分

```
agent-insight
├── agent-insight-server        # 后端服务
│   ├── controller/             # REST API 控制器
│   ├── service/                # 业务逻辑
│   ├── repository/             # 数据访问
│   ├── client/                 # 外部系统调用（MySQL / 文件系统 / 源码库）
│   ├── dto/                    # 数据传输对象
│   └── config/                # 配置类
│
├── agent-insight-web          # 前端应用
│   ├── pages/
│   │   ├── RequestSearch/     # 请求检索
│   │   ├── TraceAnalysis/     # 轨迹分析
│   │   ├── LogViewer/         # 日志查看
│   │   ├── LlmAnalysis/       # LLM 调用分析
│   │   └── SourceViewer/      # 源码对照
│   ├── components/            # 通用组件
│   └── services/              # API 调用
│
└── docs/                      # 文档（另放 docs/ 目录）
```

---

## 2. 核心服务设计

### 2.1 RequestSearchService（请求检索）

**职责**：检索满足条件的 requestId 列表，提供概览统计。

```java
// 核心接口
public interface RequestSearchService {

    // 按条件检索请求列表
    Page<RequestSummaryDTO> searchRequests(RequestSearchQuery query);

    // 请求概览
    RequestOverviewDTO getOverview(String requestId);

    // Agent 实例列表（入口 + 子 Agent）
    List<AgentInstanceDTO> listAgentInstances(String requestId);
}
```

**查询逻辑**：
- 主查询走 `log_llm_agent_main` 表，按 `requestId` 分组
- 入口 Agent：`entranceAgent = true`，取 `id` 最大的那条
- 统计：成功/失败数从 `log_llm_task_detail.success` 聚合
- 排序：默认按 `create_time` 降序

### 2.2 TraceAnalysisService（执行轨迹分析）

**职责**：加载并分析 `log_llm_task_detail` + `log_llm_task_step`，提供源码级映射。

```java
public interface TraceAnalysisService {

    // 按 taskIndex 排序的完整执行轨迹
    List<TaskDetailDTO> getExecutionTrace(String requestId);

    // 任务树结构（用于可视化）
    TaskTreeDTO buildTaskTree(String requestId, String agentName);

    // 任务步骤明细
    List<TaskStepDTO> getTaskSteps(Long taskDetailId);

    // 定位失败节点
    List<TaskDetailDTO> getFailedTasks(String requestId);

    // 源码行号映射
    SourceCodeMappingDTO mapTaskToSource(String agentName, String taskUniqueName);
}
```

**数据加载顺序**：
```
log_llm_agent_main (确定有哪些 Agent 实例)
  └─ log_llm_task_detail (按 taskIndex 排序)
       └─ log_llm_task_step (每个 detail 的步骤)
```

### 2.3 LogViewerService（日志查看）

**职责**：读取 per-request 日志文件，提供全文查看与搜索。

```java
public interface LogViewerService {

    // 读取日志文件（支持分页）
    LogFileDTO readLogFile(String requestId, String username, int page, int pageSize);

    // 日志时间线
    List<LogTimelineEntryDTO> buildTimeline(String requestId, String username);

    // 日志全文搜索
    List<LogSearchResultDTO> searchInLog(String requestId, String keyword, boolean regex);

    // 确认日志文件路径
    String resolveLogFilePath(String requestId, String username);
}
```

**文件路径规则**：
```
{dataRoot}/user/{username}/{yyyyMM}/{requestId}.log
```

路径解析流程：
1. 从 `requestId` 推断年月（存储在日志文件的元数据中，或从数据库 `create_time` 反推）
2. 尝试按 yyyyMM 格式遍历最近 3 个月目录（减少猜测次数）
3. 文件名即 `requestId.log`

**大文件处理**：
- 文件 < 5MB：一次性读取
- 文件 5~100MB：按固定行数分页，每页 5000 行
- 文件 > 100MB：返回文件过大提示，建议分段下载

### 2.4 LlmCallAnalysisService（LLM 调用分析）

**职责**：分析 `log_llm_http_request`，识别慢调用、高 Token 消耗、失败调用。

```java
public interface LlmCallAnalysisService {

    // LLM 调用列表
    List<LlmCallDTO> listLlmCalls(String requestId);

    // 慢调用排行（Top N）
    List<LlmCallDTO> getSlowCalls(String requestId, int topN);

    // Token 消耗统计
    TokenUsageDTO getTokenUsage(String requestId);

    // LLM 调用失败列表
    List<LlmCallDTO> getFailedCalls(String requestId);

    // 调用详情（requestBody / responseBody）
    LlmCallDetailDTO getCallDetail(Long callId);
}
```

### 2.5 SourceViewerService（源码对照）

**职责**：加载 Agent 脚本源码，提供行号级对照能力。

```java
public interface SourceViewerService {

    // Agent 脚本列表
    List<ScriptFileDTO> listScripts(String agentName);

    // 源码内容
    String getScriptContent(String scriptPath);

    // taskUniqueName → 源码行号映射
    SourceLineMappingDTO mapTaskToLine(String agentName, String taskUniqueName);

    // 高亮代码片段（给定行号范围）
    List<CodeHighlightDTO> getHighlightedRange(String scriptPath, int startLine, int endLine);
}
```

**Agent 脚本路径约定**：
```
{systemRoot}/agt/{agentName}/
  ├── flow.java          # 主流程脚本
  ├── plans/
  │   └── *.java        # Plan 定义
  └── tools/
      └── *.java        # 工具定义
```

### 2.6 RootCauseService（根因分析，待迭代）

```java
public interface RootCauseService {

    // 生成根因分析报告
    RootCauseReportDTO analyze(String requestId);

    // 历史问题模式匹配
    List<PatternMatchDTO> matchHistoricalPatterns(String requestId);
}
```

---

## 3. 数据模型设计

### 3.1 DTO 层

#### RequestSummaryDTO（请求摘要）

```java
@Data
public class RequestSummaryDTO {
    private String requestId;
    private String topAgentName;
    private Integer taskStatus;          // 任务状态
    private Boolean success;              // 是否成功
    private Integer totalTaskCount;       // 总任务数
    private Integer failedTaskCount;      // 失败任务数
    private Long totalDuration;           // 总耗时(ms)
    private LocalDateTime createTime;
    private String entranceAgentName;
    private List<String> subAgentNames;   // 子 Agent 列表
}
```

#### TaskDetailDTO（任务明细）

```java
@Data
public class TaskDetailDTO {
    private Long id;
    private String taskName;
    private String taskUniqueName;
    private String taskType;              // expression / foreach / when
    private Integer taskIndex;
    private String fullPath;             // 在任务树中的完整路径
    private Boolean success;
    private String result;                // 任务结果（截断显示）
    private Integer resultType;
    private String errorMessage;          // 失败时的错误信息
    private Long duration;                // 耗时(ms)
    private Integer agentTryCount;
    private Integer taskTryCount;
    private LocalDateTime createTime;
    private LocalDateTime taskEndTime;
    private List<TaskStepDTO> steps;      // 步骤明细

    // 源码映射
    private String sourceFile;
    private Integer sourceStartLine;
    private Integer sourceEndLine;
}
```

#### TaskStepDTO（任务步骤）

```java
@Data
public class TaskStepDTO {
    private Long id;
    private Integer step;                 // 0=template,1=rag,2=parser,3=verifier,4=action,5=result
    private String template;              // 模板名称
    private String input;                 // 输入
    private String output;                // 输出
    private Boolean success;
    private Long duration;                // 耗时(ms)
    private LocalDateTime endTime;
}
```

#### LlmCallDTO（LLM 调用）

```java
@Data
public class LlmCallDTO {
    private Long id;
    private String templateName;
    private String planUniqueName;
    private String modelType;
    private Long spendTime;               // 耗时(ms)
    private Integer promptTokens;
    private Integer completionTokens;
    private Boolean successExpression;
    private LocalDateTime createTime;
    // 摘要信息（完整 body 在 detail 接口）
    private String requestBodyPreview;
    private String responseBodyPreview;
}
```

### 3.2 数据库表（新增）

Agent Insight 无需新建日志表，直接查询 `log_llm_*` 系列表。

如需存储分析结果或缓存，可新增：

#### insight_analysis_cache（分析缓存）

```sql
CREATE TABLE insight_analysis_cache (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id    VARCHAR(64) NOT NULL,
    agent_name    VARCHAR(100),
    cache_key     VARCHAR(128) NOT NULL,
    cache_data    MEDIUMTEXT,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at    DATETIME,
    INDEX idx_request_id (request_id),
    INDEX idx_cache_key (cache_key, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. API 设计

### 4.1 请求检索 API

#### GET /api/v1/requests

**描述**：分页检索请求列表

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| requestId | string | 否 | requestId 模糊搜索 |
| agentName | string | 否 | Agent 名称过滤 |
| startTime | datetime | 否 | 开始时间 |
| endTime | datetime | 否 | 结束时间 |
| status | string | 否 | success / failed / all |
| page | int | 否 | 页码，默认 0 |
| size | int | 否 | 每页大小，默认 20 |

**响应**：

```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "requestId": "req_abc123",
        "topAgentName": "DataAnalysisAgent",
        "success": false,
        "totalTaskCount": 12,
        "failedTaskCount": 2,
        "createTime": "2026-06-25T10:00:00"
      }
    ],
    "totalElements": 150,
    "totalPages": 8
  }
}
```

#### GET /api/v1/requests/{requestId}/overview

**描述**：获取请求概览

### 4.2 轨迹分析 API

#### GET /api/v1/requests/{requestId}/trace

**描述**：获取完整执行轨迹（按 taskIndex 排序）

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentName | string | 否 | 指定 Agent 名称，不填则返回入口 Agent |

#### GET /api/v1/trace/{taskDetailId}/steps

**描述**：获取任务步骤明细

#### GET /api/v1/requests/{requestId}/trace/tree

**描述**：获取任务树结构（用于可视化）

### 4.3 日志查看 API

#### GET /api/v1/requests/{requestId}/log

**描述**：获取 per-request 日志文件内容

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码（每页 5000 行），默认 0 |
| keyword | string | 否 | 搜索关键词 |

#### GET /api/v1/requests/{requestId}/log/download

**描述**：下载原始日志文件

### 4.4 LLM 调用 API

#### GET /api/v1/requests/{requestId}/llm-calls

**描述**：获取 LLM 调用列表

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sortBy | string | 否 | spendTime / tokenCount，默认 spendTime |
| topN | int | 否 | 取 TopN，默认返回全部 |

#### GET /api/v1/llm-calls/{callId}/detail

**描述**：获取 LLM 调用详情（包含完整 requestBody / responseBody）

### 4.5 源码 API

#### GET /api/v1/agents/{agentName}/scripts

**描述**：获取 Agent 的脚本文件列表

#### GET /api/v1/scripts/content

**描述**：获取脚本源码内容

**参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| path | string | 是 | 脚本文件路径 |
| startLine | int | 否 | 起始行号 |
| endLine | int | 否 | 结束行号 |

---

## 5. 源码对照实现方案

### 5.1 核心思路

Agent 脚本中，每个 `taskUniqueName` 对应源码中一个 Plan 节点。通过以下方式建立映射：

1. **静态分析**：解析 Agent 脚本源码，建立 `taskUniqueName → 代码行号范围` 的索引
2. **动态关联**：从 `log_llm_task_detail.fullPath` 字段获取任务在 AgentFlow 中的路径
3. **叠加展示**：前端左侧显示源码（CodeMirror），右侧显示执行结果，中间以 `taskIndex` 为纽带对齐

### 5.2 索引构建

```java
public class ScriptIndexer {

    // 从 Agent 脚本中提取 taskUniqueName → 行号范围
    public Map<String, SourceLineRange> indexScript(File scriptFile) {
        // 1. 解析 @Plan 或 @Task 注解
        // 2. 提取 uniqueName 属性
        // 3. 记录起始行号和结束行号
        // 4. 返回映射
    }

    // 批量构建索引（启动时或定时）
    public void buildIndex(String agentName);
}
```

### 5.3 前端对照视图

```
┌─────────────────────────────┬──────────────────────────────────┐
│  源码区 (Monaco Editor)      │   执行结果区                      │
│                             │                                  │
│  1│ plan("analyze_data") {  │  [Task 1] analyze_data          │
│  2│   task("step1") {      │   ✓ 成功 · 耗时: 1200ms          │
│  3│     expression {       │   Input: {...}                  │
│  4│       ...              │   Output: {...}                │
│  5│     }                   │                                  │
│  6│   }                    │  [Task 2] process_result       │
│  7│                          │   ✗ 失败 · 耗时: 350ms          │
│  8│   task("step2") {      │   Error: NullPointerException   │
│  9│     ...                │   ├─ Step 1 (template) ✓       │
│ 10│   }                    │   ├─ Step 2 (rag) ✓            │
│ 11│ }                      │   └─ Step 3 (action) ✗          │
└─────────────────────────────┴──────────────────────────────────┘
```

- **高亮规则**：当前选中的 taskIndex 对应的源码行以黄色背景高亮
- **行号点击**：点击源码行号，自动滚动到右侧对应的执行结果
- **失败节点**：红色边框标识失败 task，双击可跳转到相关日志行

---

## 6. 配置说明

### 6.1 application.yml

```yaml
agent-insight:
  # 数据根目录（per-request 日志文件所在）
  log:
    data-root: /app/project/data
    # 日志文件后缀
    file-suffix: .log
    # 大文件阈值（MB），超过则分页
    large-file-threshold: 5
    # 分页大小（行数）
    page-size: 5000

  # Agent 脚本仓库
  script:
    root: /app/project/data/system/agt
    # 脚本类型（java / py / md）
    extensions: java, py, md

  # 缓存配置
  cache:
    # 分析结果缓存时间（分钟）
    analysis-ttl: 30
    # 源码索引缓存时间（小时）
    index-ttl: 24

  # 数据库（复用 llm-agent 的数据源）
  datasource:
    jdbc-url: ${MYSQL_URL:jdbc:mysql://172.18.2.14:3306/llm_agent}
    username: ${MYSQL_USR:root}
    password: ${MYSQL_PWD:}
```

---

## 7. 关键设计决策

### 7.1 为什么不用新建日志表

直接复用 `log_llm_task_detail`、`log_llm_task_step`、`log_llm_http_request` 等现有表。原因：
- 无需修改 llm-agent 主工程的数据写入逻辑
- Agent Insight 作为只读分析系统，不产生写入压力
- 避免数据重复和同步一致性问题

### 7.2 文件系统访问策略

per-request 日志文件通过 `FileInputStream` 直接读取，不通过数据库中转。原因：
- 文件是完整链路信息的权威来源（包含所有线程、traceId 等）
- 避免大文件内容撑爆数据库字段
- 可直接对接后续 ELK / 对象存储等文件系统

### 7.3 缓存策略

| 数据类型 | 缓存方式 | TTL |
|----------|----------|------|
| 请求概览 | Redis | 5 分钟 |
| 执行轨迹 | Redis | 30 分钟 |
| 源码内容 | 本地内存（LruCache） | 24 小时 |
| 源码索引 | 本地内存 | 启动时构建 + 手动刷新 |

### 7.4 权限控制

- 读取日志属于敏感操作，需对接 llm-agent 现有权限体系
- 通过 `ChatDoc` 模块的权限注解 `@PrivilegePath` 扩展实现
- 或独立实现基于 `chatMessageId` / `createById` 的行级权限

---

## 8. 部署架构

```
┌──────────────────────────────────────────────────────────────┐
│                      Nginx / 网关                            │
│                  (路由 /agent-insight/*)                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   ┌───────────┐ ┌───────────┐ ┌───────────┐
   │  Node-1   │ │  Node-2   │ │  Node-3   │
   │ Insight-1 │ │ Insight-2 │ │ Insight-3 │
   │ (Docker)  │ │ (Docker)  │ │ (Docker)  │
   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘
         │             │             │
         └─────────────┼─────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   ┌───────────┐ ┌───────────┐ ┌───────────┐
   │   MySQL   │ │FileSystem │ │    NFS    │
   │ (llm_     │ │ (日志文件) │ │ (脚本仓库) │
   │  agent)   │ │           │ │           │
   └───────────┘ └───────────┘ └───────────┘
```

---

## 9. 风险与应对

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| 日志文件跨月存储 | requestId 无法直接定位月份目录 | 从 `log_llm_agent_main.create_time` 反推年月 |
| 日志文件已清理 | 无法加载原始链路 | 仅展示数据库部分，UI 提示"日志文件不可用" |
| Agent 脚本更新/删除 | 源码对照失效 | 启动时构建索引 + 脚本版本控制 |
| 超大日志文件 | 内存溢出 | 分页流式读取 + 大文件检测告警 |
| 跨服务调用 | 依赖方不可用 | 降级策略：只读模式（数据库），部分功能提示不可用 |
