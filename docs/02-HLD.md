# Agent Insight 技术设计说明书（HLD）

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v0.2 | 2026-06-26 | - | 更新技术栈：Spring Boot 4.1 / Spring AI 2.0 / 多数据源 / AI 增强分析 |

---

## 1. 系统架构

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端（React 18 + Ant Design 5）            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐   │
│  │  请求检索    │ │ 轨迹可视化   │ │   源码对照视图        │   │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘   │
└─────────┼─────────────────┼─────────────────────┼───────────────┘
          │                 │                     │
          └─────────────────┼─────────────────────┘
                            │ HTTP / WebSocket
┌───────────────────────────┼─────────────────────────────────────┐
│                   Spring Boot 4.1.0 应用                          │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │  Controller 层（explorer + llm）                         │    │
│  │   AnalyzerController  DatasourceController  QueryController │
│  │   RequestController  TraceController  SourceController     │
│  └────────────────────────┬────────────────────────────────┘    │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │  Service 层                                            │    │
│  │  QueryService  ConfigService  LlmCallAnalysisService   │    │
│  │  TraceAnalysisService  SourceViewerService              │    │
│  └────────────────────────┬────────────────────────────────┘    │
│                           │                                       │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │  数据接入层                                            │    │
│  │  JPA Repository (MySQL) │ MongoTemplate │ FileReader │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          ▼                 ▼                 ▼
    ┌───────────┐    ┌───────────┐    ┌──────────────┐
    │  MySQL    │    │ MongoDB   │    │ FileSystem   │
    │ (llm_*   │    │(元数据存储 │    │ (.log)       │
    │  日志表)  │    │ + DataExp)│    │              │
    └───────────┘    └───────────┘    └──────────────┘
          │
          ├─────────────────────────────┐
          ▼                             ▼
    ┌───────────┐              ┌───────────────┐
    │ PostgreSQL│              │  Script Repo  │
    │ (外部数据)│              │   (源码)      │
    └───────────┘              └───────────────┘
```

### 1.2 技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| 后端框架 | Spring Boot | **4.1.0** | Jakarta EE 10, Java 21 |
| AI 集成 | Spring AI | **2.0.0** | OpenAI / DeepSeek / Anthropic / Google GenAI / Ollama |
| ORM | Spring Data JPA + Hibernate | 6.x | 访问 MySQL 日志表 |
| SQL 方言兼容 | Hibernate 6.x | - | 修复 JPQL LIMIT / PostgreSQL $1 占位符 |
| 元数据存储 | MongoDB | 7.x | insight_* 元数据集合 |
| 外部数据源 | HikariCP + JdbcTemplate | - | MySQL / PostgreSQL / MongoDB |
| 缓存 | Spring Data Redis | - | 查询结果缓存 |
| 前端 | React 18 | 18.x | 企业级 UI 组件库 |
| 构建工具 | Maven（后端）+ Vite（前端） | - | - |
| API 文档 | Knife4j | 4.x (jakarta) | 兼容 Spring Boot 4 |
| 部署 | Docker | - | 容器化部署 |

### 1.3 模块划分

```
agent-insight
├── agent-insight-server        # Spring Boot 后端（端口 9280）
│   ├── controller/             # REST API 控制器
│   │   ├── explorer/           # Data Explorer 模块
│   │   │   ├── AnalyzerController.java    # AI 列分析与 NL 查询
│   │   │   ├── DatasourceController.java  # 数据源管理
│   │   │   ├── QueryController.java       # 动态查询
│   │   │   ├── TableConfigController.java # 表配置
│   │   │   └── ColumnConfigController.java # 列配置
│   │   ├── RequestController.java         # 请求检索
│   │   ├── TraceController.java          # 执行轨迹
│   │   ├── LogController.java             # 日志查看
│   │   ├── LlmCallController.java        # LLM 调用分析
│   │   └── SourceController.java          # 源码对照
│   ├── service/                # 业务逻辑
│   ├── repository/             # 数据访问（JPA Entity）
│   ├── explorer/                # Data Explorer 核心
│   │   ├── engine/             # 动态查询引擎
│   │   │   ├── DynamicDatasourceManager.java  # 多数据源连接池
│   │   │   ├── QueryRouter.java              # 查询路由
│   │   │   ├── QueryExecutor.java            # 执行器接口
│   │   │   ├── SqlQueryExecutor.java         # MySQL/PG 执行
│   │   │   ├── MongoQueryExecutor.java      # MongoDB 执行
│   │   │   └── ColumnAnalyzerService.java    # AI 列分析
│   │   ├── document/           # MongoDB 元数据文档
│   │   │   ├── InsightDatasource.java
│   │   │   ├── InsightTableConfig.java
│   │   │   ├── InsightColumnConfig.java
│   │   │   ├── InsightQueryConfig.java
│   │   │   └── InsightQueryHistory.java
│   │   ├── dto/                # Data Explorer DTO
│   │   └── controller/        # Explorer API
│   ├── dto/                    # 通用 DTO
│   ├── config/                 # 配置类
│   └── AgentInsightApplication.java
│
├── agent-insight-web           # 前端应用
│   ├── pages/
│   │   ├── Explorer/          # Data Explorer 页面
│   │   │   ├── Datasource/   # 数据源管理
│   │   │   ├── TableConfig/   # 表配置
│   │   │   ├── ColumnConfig/ # 列配置
│   │   │   └── TableExplorer/ # 动态数据表
│   │   ├── LlmAnalysis/      # LLM 调用分析
│   │   ├── LogViewer/        # 日志查看
│   │   ├── RequestSearch/    # 请求检索
│   │   ├── RequestOverview/  # 请求概览
│   │   ├── SourceViewer/     # 源码对照
│   │   └── TraceAnalysis/    # 轨迹分析
│   ├── components/
│   │   └── DynamicTable/     # 动态数据表组件
│   ├── services/
│   │   ├── api.ts            # 通用 API
│   │   └── explorerApi.ts    # Data Explorer API
│   └── App.tsx
│
└── docs/                      # 文档
```

### 1.4 双模块定位

本系统包含两大核心功能模块：

| 模块 | 定位 | 核心能力 |
|------|------|----------|
| **Agent Insight**（原设计） | LLM Agent 执行链路分析 | 日志检索、轨迹分析、LLM 调用分析、源码对照 |
| **Data Explorer**（新增） | 多数据源动态查询 | MySQL / PostgreSQL / MongoDB 元数据驱动查询、AI 增强列分析 |

两模块共用同一 Spring Boot 应用，共享基础设施（Redis 缓存、Knife4j 文档等），前端独立部署。

---

## 2. Data Explorer：动态数据源查询引擎

### 2.1 核心设计原则

```
┌─────────────────────────────────────────────────────────────────┐
│                    Design Principles                              │
├─────────────────────────────────────────────────────────────────┤
│ 1. Metadata-First: 所有 schema 由配置驱动，非硬编码 Entity      │
│ 2. Zero Entity: 外部数据表无硬编码 POJO，完全动态化              │
│ 3. Multi-Datasource: 统一查询 MySQL / PostgreSQL / MongoDB      │
│ 4. Dynamic UI: 列由 API 返回，前端无需重新编译                   │
│ 5. AI-Augmented: 列分析与自然语言查询由 LLM 增强                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 元数据模型（MongoDB 集合）

| 集合 | 用途 |
|------|------|
| `insight_datasource` | 外部数据库连接配置（MySQL / PostgreSQL / MongoDB） |
| `insight_table_config` | 表/集合的暴露与查询行为配置 |
| `insight_column_config` | 列的定义、展示规则与渲染方式 |
| `insight_query_config` | 保存的查询模板 |
| `insight_query_history` | 查询执行审计日志 |

### 2.3 动态查询引擎架构

```
┌────────────────────────────────────────────────────────────┐
│                   QueryRouter                               │
│   根据 tableKey → datasourceKey 路由到对应 Executor         │
└──────────────────────────┬─────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│SqlQueryExec- │  │SqlQueryExec- │  │MongoQueryExecutor │
│tor (MySQL)   │  │tor (PG)      │  │                   │
│JdbcTemplate  │  │JdbcTemplate  │  │ MongoTemplate     │
└──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
       │                  │                   │
       └──────────────────┼───────────────────┘
                          ▼
┌────────────────────────────────────────────────────────────┐
│               DynamicDatasourceManager                      │
│   懒加载连接池：MySQL / PostgreSQL / MongoDB               │
│   默认端口支持环境变量覆盖：                                 │
│   POSTGRES_PORT / MYSQL_PORT / MONGODB_PORT              │
└────────────────────────────────────────────────────────────┘
```

### 2.4 核心接口

```java
// 动态查询请求
public record QueryRequest(
    String tableKey,
    List<String> columns,          // 空 = 返回所有列
    List<FilterCondition> filters,
    List<SortField> sorts,
    int page,
    int pageSize
) {}

// 过滤条件
public record FilterCondition(
    String column,
    FilterOperator operator,  // EQ / NE / GT / GTE / LT / LTE / LIKE / IN / IS_NULL
    Object value,
    LogicType logicType      // AND / OR
) {}

// 查询结果
public record QueryResponse(
    List<Map<String, Object>> data,    // 动态行数据
    List<ColumnMetadata> columns,       // 列元数据
    long totalCount,
    int page,
    int pageSize,
    int totalPages,
    long executionTimeMs
) {}

// 列元数据
public record ColumnMetadata(
    String columnKey,
    String columnName,
    String displayName,
    String dataType,       // VARCHAR / INT / DECIMAL / DATE / DATETIME / BOOLEAN / JSON / MONEY / PERCENT / ENUM / HTML
    String columnType,    // DIMENSION / MEASURE / PRIMARY_KEY / FOREIGN_KEY / SYSTEM
    String renderType,    // TEXT / LINK / TAG / MONEY / DATE / BOOLEAN / IMAGE / JSON / STATUS / CUSTOM
    int width,
    boolean sortable,
    boolean filterable,
    Map<String, Object> formatConfig,
    Map<String, Object> renderConfig
) {}
```

### 2.5 跨数据库兼容

| 数据库 | 分页语法 | 占位符 | 特殊处理 |
|--------|----------|--------|----------|
| MySQL | `LIMIT ? OFFSET ?` | `?` | - |
| PostgreSQL | `LIMIT ? OFFSET ?` | `$1`, `$2`... | Hibernate 自动转换 |
| MongoDB | `skip().limit()` | - | Criteria API |

**已知适配问题及修复**：

1. **JPQL LIMIT 语法**：Hibernate 6.x 中 JPQL 不支持 `LIMIT`，改为原生 SQL 或使用 ` Pageable` 接口。
2. **PostgreSQL `$1` 占位符**：Spring Data JPA 原生查询需使用 `$1` 而非 `?`，或通过 `JdbcTemplate` + `NamedParameterJdbcTemplate` 统一处理。
3. **Hibernate 方言检测**：配置 `spring.jpa.database-platform` 为 `org.hibernate.dialect.MySQL8Dialect`（主库为 MySQL 时）。

---

## 3. AI 增强分析模块

### 3.1 AI 配置

支持多 Provider 切换，环境变量优先级最高：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o}
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      chat:
        options:
          model: ${DEEPSEEK_MODEL:deepseek-chat}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    anthropic:
      # Anthropic 支持 via base-url 配置
    google-genai:
      # Google Gemini 支持

agent-insight:
  ai:
    enabled: ${AI_ENABLED:false}           # 默认关闭
    provider: ${AI_PROVIDER:openai}         # openai / deepseek / ollama / anthropic / google
    default-model: ${AI_MODEL:gpt-4o}
    temperature: 0.3
    timeout-seconds: 30
    column-analysis-enabled: true            # AI 列分析
    nl-query-enabled: true                  # 自然语言查询
    summarization-enabled: true             # 结果摘要
```

### 3.2 AI 增强能力

| 能力 | 说明 | Prompt 概要 |
|------|------|-------------|
| **列分析** | 分析列数据特征、生成描述、建议渲染类型 | 分析 `{columnName}` 数据，类型：`{sampleValues}`，建议展示方式 |
| **NL 查询** | 自然语言转换为 SQL/MongoDB 查询 | "Give me all orders from June where amount > 1000" → SQL |
| **结果摘要** | 对查询结果生成自然语言摘要 | 总结 `{resultSet}` 的关键发现 |

---

## 4. 核心服务设计（Agent Insight 模块）

### 4.1 RequestSearchService（请求检索）

**职责**：检索满足条件的 requestId 列表，提供概览统计。

```java
public interface RequestSearchService {
    Page<RequestSummaryDTO> searchRequests(RequestSearchQuery query);
    RequestOverviewDTO getOverview(String requestId);
    List<AgentInstanceDTO> listAgentInstances(String requestId);
}
```

### 4.2 TraceAnalysisService（执行轨迹分析）

```java
public interface TraceAnalysisService {
    List<TaskDetailDTO> getExecutionTrace(String requestId, String agentName);
    TaskTreeDTO buildTaskTree(String requestId, String agentName);
    List<TaskStepDTO> getTaskSteps(Long taskDetailId);
    List<TaskDetailDTO> getFailedTasks(String requestId);
}
```

### 4.3 LogViewerService（日志查看）

```java
public interface LogViewerService {
    LogFileDTO readLogFile(String requestId, String username, int page, int pageSize);
    List<LogSearchResultDTO> searchInLog(String requestId, String keyword, boolean regex);
    String resolveLogFilePath(String requestId, String username);
}
```

### 4.4 LlmCallAnalysisService（LLM 调用分析）

```java
public interface LlmCallAnalysisService {
    List<LlmCallDTO> listLlmCalls(String requestId);
    List<LlmCallDTO> getSlowCalls(String requestId, int topN);
    TokenUsageDTO getTokenUsage(String requestId);
    List<LlmCallDTO> getFailedCalls(String requestId);
    LlmCallDetailDTO getCallDetail(Long callId);
}
```

### 4.5 SourceViewerService（源码对照）

```java
public interface SourceViewerService {
    List<ScriptFileDTO> listScripts(String agentName);
    String getScriptContent(String scriptPath);
    SourceLineMappingDTO mapTaskToLine(String agentName, String taskUniqueName);
}
```

---

## 5. API 设计

### 5.1 Data Explorer API

```
/api/v1/explorer/
├── /datasources
│   ├── GET    /              # 列出所有数据源
│   ├── POST   /              # 创建数据源
│   ├── GET    /{key}         # 获取数据源详情
│   ├── PUT    /{key}         # 更新数据源
│   ├── DELETE /{key}         # 删除数据源
│   ├── POST   /{key}/test    # 测试连接
│   └── GET    /{key}/tables  # 列出数据源中的表
│
├── /tables
│   ├── GET    /              # 列出所有已配置表
│   ├── POST   /              # 创建表配置
│   ├── GET    /{tableKey}    # 获取表详情
│   ├── PUT    /{tableKey}    # 更新表配置
│   ├── DELETE /{tableKey}    # 删除表配置
│   ├── GET    /{tableKey}/columns   # 获取列定义
│   └── POST   /{tableKey}/columns  # 添加/更新列
│
├── /columns
│   ├── GET    /{columnKey}   # 获取列详情
│   ├── PUT    /{columnKey}   # 更新列
│   └── DELETE /{columnKey}   # 删除列
│
├── /query
│   ├── POST   /execute       # 执行动态查询
│   ├── POST   /preview       # 预览查询（不分页）
│   ├── GET    /{tableKey}    # 简单条件查询
│   └── POST   /export        # 导出结果
│
├── /ai
│   ├── POST   /analyze-columns # AI 列分析
│   └── POST   /nl-query       # 自然语言查询
│
├── /saved-queries
│   ├── GET    /              # 列出已保存查询
│   ├── POST   /              # 创建保存查询
│   ├── GET    /{queryKey}   # 获取查询
│   ├── PUT    /{queryKey}   # 更新查询
│   └── DELETE /{queryKey}   # 删除查询
│
└── /query-history
    ├── GET    /              # 查询历史（分页）
    └── GET    /{executionId} # 获取执行详情
```

### 5.2 Agent Insight API

```
/api/v1/requests
├── GET  /                    # 分页检索请求列表
├── GET  /{requestId}/overview      # 请求概览
├── GET  /{requestId}/instances     # Agent 实例列表
├── GET  /{requestId}/log           # 日志文件（分页）
├── GET  /{requestId}/log/search    # 日志搜索
├── GET  /{requestId}/log/download   # 日志下载
├── GET  /{requestId}/trace         # 执行轨迹
├── GET  /{requestId}/trace/tree    # 任务树结构
├── GET  /{requestId}/trace/failed  # 失败任务
├── GET  /{requestId}/llm-calls     # LLM 调用列表
└── GET  /{requestId}/llm-calls/slow # 慢调用 TopN

/api/v1/
├── trace/{taskDetailId}/steps        # 任务步骤明细
├── llm-calls/{callId}/detail        # LLM 调用详情
├── agents/{agentName}/scripts       # Agent 脚本列表
└── scripts/content                  # 脚本源码
```

---

## 6. 配置说明

### 6.1 环境变量（优先于 application.yml 默认值）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_PORT` | 3306 | MySQL 默认端口 |
| `POSTGRES_PORT` | 5433 | PostgreSQL 默认端口（cogniforge 惯例） |
| `MONGODB_PORT` | 27017 | MongoDB 默认端口 |
| `POSTGRES_HOST` | - | PostgreSQL 主机 |
| `POSTGRES_USER` | - | PostgreSQL 用户名 |
| `POSTGRES_PASSWORD` | - | PostgreSQL 密码 |
| `POSTGRES_DB` | - | PostgreSQL 数据库名 |
| `MONGODB_URI` | `mongodb://172.18.2.14:27017/agent_insight` | MongoDB URI |
| `REDIS_HOST` | 172.18.2.14 | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `AI_ENABLED` | false | 启用 AI 功能 |
| `AI_PROVIDER` | openai | AI Provider |
| `OPENAI_API_KEY` | - | OpenAI API Key |
| `OPENAI_BASE_URL` | - | OpenAI Base URL |
| `OPENAI_MODEL` | gpt-4o | OpenAI 默认模型 |
| `DEEPSEEK_API_KEY` | - | DeepSeek API Key |
| `DEEPSEEK_MODEL` | deepseek-chat | DeepSeek 默认模型 |
| `OLLAMA_BASE_URL` | http://localhost:11434 | Ollama Base URL |
| `OLLAMA_MODEL` | qwen2.5 | Ollama 默认模型 |

### 6.2 application.yml 关键配置

```yaml
server:
  port: 9280

spring:
  application:
    name: agent-insight

  # MongoDB（元数据存储）
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://172.18.2.14:${MONGODB_PORT:-27017}/agent_insight}
      auto-index-creation: true

    redis:
      host: ${REDIS_HOST:172.18.2.14}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PWD:}
      database: 1

  # HikariCP 默认连接池
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1

  # Spring AI 2.0.0（多 Provider）
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:EMPTY}
      base-url: ${OPENAI_BASE_URL:}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o}
          temperature: 0.3
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:qwen2.5}
          temperature: 0.3
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:EMPTY}
      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
      chat:
        options:
          model: ${DEEPSEEK_MODEL:deepseek-chat}
          temperature: 0.3

agent-insight:
  log:
    data-root: ${INSIGHT_LOG_ROOT:/app/project/data}
    file-suffix: .log
    large-file-threshold-mb: 5
    page-size: 5000
    search-max-results: 500
  script:
    root: ${INSIGHT_SCRIPT_ROOT:/app/project/data/system/agt}
    extensions: java,py,md
  cache:
    analysis-ttl-minutes: 30
    index-ttl-hours: 24
  ai:
    enabled: ${AI_ENABLED:false}
    provider: ${AI_PROVIDER:openai}
    default-model: ${AI_MODEL:gpt-4o}
    temperature: 0.3
    timeout-seconds: 30
    column-analysis-enabled: true
    nl-query-enabled: true
    summarization-enabled: true
```

---

## 7. 数据库表

### 7.1 Agent Insight（MySQL — 复用 llm-agent 日志表）

无需新建日志表，直接查询 `log_llm_task_detail`、`log_llm_task_step`、`log_llm_http_request`、`log_llm_agent_main`。

### 7.2 Data Explorer（MongoDB 元数据）

#### insight_datasource

```json
{
  "_id": "...",
  "datasourceKey": "prod_mysql_orders",
  "datasourceName": "Production Orders DB",
  "datasourceType": "MYSQL",
  "status": "ACTIVE",
  "connectionConfig": {
    "host": "172.18.2.10",
    "port": 3306,
    "database": "orders_db",
    "username": "readonly_user",
    "password": "encrypted_password",
    "connectionPoolSize": 5,
    "connectionTimeout": 30000,
    "extraParams": { "useSSL": false, "serverTimezone": "Asia/Shanghai" }
  },
  "description": "Primary orders database - read only",
  "tags": ["production", "orders", "mysql"],
  "allowedTables": ["orders", "order_items", "customers", "products"],
  "deniedTables": [],
  "createdBy": "admin",
  "createdAt": ISODate("..."),
  "updatedBy": "admin",
  "updatedAt": ISODate("...")
}
```

#### insight_table_config

```json
{
  "_id": "...",
  "tableKey": "prod_orders",
  "tableName": "orders",
  "datasourceKey": "prod_mysql_orders",
  "displayName": "Order List",
  "description": "Main order transactions",
  "tableType": "TABLE",
  "status": "ACTIVE",
  "queryConfig": {
    "defaultSortField": "created_at",
    "defaultSortOrder": "desc",
    "defaultPageSize": 20,
    "maxPageSize": 500,
    "enableExport": true,
    "exportFormats": ["CSV", "EXCEL", "JSON"]
  },
  "createdBy": "admin",
  "createdAt": ISODate("...")
}
```

#### insight_column_config

```json
{
  "_id": "...",
  "columnKey": "prod_orders.order_id",
  "tableKey": "prod_orders",
  "columnName": "order_id",
  "displayName": "Order ID",
  "description": "Unique order identifier",
  "dataType": "BIGINT",
  "columnType": "PRIMARY_KEY",
  "sortable": true,
  "filterable": true,
  "nullable": true,
  "displayOrder": 1,
  "width": 100,
  "minWidth": 60,
  "maxWidth": 300,
  "fixedPosition": "LEFT",
  "formatConfig": { "type": "TEXT", "truncateLength": 50 },
  "renderConfig": {
    "type": "LINK",
    "props": { "href": "/orders/{value}", "target": "_blank" }
  },
  "filterConfig": { "type": "INPUT", "operator": "LIKE" },
  "aggregateConfig": { "enableSum": false, "enableCount": true },
  "createdBy": "admin",
  "createdAt": ISODate("...")
}
```

---

## 8. 关键设计决策

### 8.1 Data Explorer：元数据驱动的优势

- **无需代码变更**：新增数据源只需在 MongoDB 中配置，无需修改 Java 代码
- **零 Entity 类**：外部数据表无 POJO 映射，结果以 `Map<String, Object>` 动态返回
- **动态 UI**：前端表格列完全由 API 的 `ColumnMetadata` 驱动，无需重新编译
- **可复用**：同一引擎，不同的 MongoDB 元数据 = 不同的业务数据查询应用

### 8.2 AI Provider 切换机制

通过 `AiProperties` + `SpringHelper` 实现多 Provider 统一封装，运行时根据 `AI_PROVIDER` 环境变量切换。AI 功能默认关闭（`AI_ENABLED=false`），避免生产环境不必要的 API 消耗。

### 8.3 多数据源连接池

`DynamicDatasourceManager` 按 `datasourceKey` 懒加载连接池：
- MySQL / PostgreSQL → HikariDataSource（`JdbcTemplate` 执行）
- MongoDB → `MongoClient`（`MongoTemplate` 执行）
- 连接不占用时不创建，不用时不占资源

### 8.4 缓存策略

| 数据类型 | 缓存方式 | TTL |
|----------|----------|------|
| 请求概览 | Redis | 5 分钟 |
| 执行轨迹 | Redis | 30 分钟 |
| 查询结果 | Redis | 可配置 |
| 源码内容 | 本地内存（LruCache） | 24 小时 |

---

## 9. 部署架构

```
┌──────────────────────────────────────────────────────────────┐
│                      Nginx / 网关                            │
│                  (路由 /agent-insight/*)                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   ┌───────────┐ ┌───────────┐ ┌───────────┐
   │  Node-1  │ │  Node-2  │ │  Node-3  │
   │ Insight-1 │ │ Insight-2 │ │ Insight-3 │
   │ (Docker)  │ │ (Docker)  │ │ (Docker)  │
   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘
         │             │             │
         └─────────────┼─────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   ┌───────────┐ ┌───────────┐ ┌───────────────┐
   │   MySQL   │ │ MongoDB   │ │  FileSystem   │
   │ (日志表)  │ │ (元数据)  │ │  (per-req     │
   │           │ │           │ │   日志文件)   │
   └───────────┘ └───────────┘ └───────────────┘
         │             │
         ▼             ▼
   ┌───────────┐ ┌───────────┐
   │ PostgreSQL│ │   Redis   │
   │(外部数据) │ │  (缓存)   │
   └───────────┘ └───────────┘
```

---

## 10. 风险与应对

| 风险 | 影响 | 应对方案 |
|------|------|----------|
| 日志文件跨月存储 | requestId 无法定位目录 | 从 `log_llm_agent_main.create_time` 反推年月 |
| PostgreSQL 占位符冲突 | `$1` vs `?` 混用报错 | 使用 `NamedParameterJdbcTemplate`，统一占位符 |
| JPQL LIMIT 语法错误 | 分页查询失败 | 改为原生 SQL 或 `Pageable` 接口 |
| MongoDB 大文档内存 | 查询大集合 OOM | 限制 `pageSize` 最大 500 |
| AI Provider 不可用 | AI 功能降级 | `AI_ENABLED=false` 默认关闭，异常不阻塞查询 |
| 外部数据源连接失败 | 查询超时 | HikariCP 30s 超时，连接池隔离 |
