# llm-agent 模板包元数据规范（v1.0）

> 本文档是 `templates/llm-agent/` 下**所有 JSON 文件的母版**。
> 写 JSON 之前先看本文，定了就不会再改。
> 与 HLD `docs/06-DataExplorer-HLD.md` 的 `insight_table_config` / `insight_column_config` / `insight_query_config` 字段对齐。

---

## 0. 命名规范（统一口径）

| 概念 | 命名规则 | 示例 | 说明 |
|------|---------|------|------|
| `logicalGroup` | `{env}-{system}` 形式，kebab-case | `llm-agent` | 与 `insight_datasource.logicalGroup` 对应 |
| `datasourceKey` | `{env}-{system}-{instance}` | `prod-llm-agent-shard-1` | 一个 logicalGroup 下可有多个物理实例 |
| `tableKey` | `{logicalGroup}.{collectionName}` | `llm-agent.log_llm_agent_main` | 跨实例唯一 |
| `columnKey` | `{tableKey}.{fieldName}` | `llm-agent.log_llm_agent_main.requestId` | 列唯一 |
| `queryKey` | `{tableKey}.{queryPurpose}` | `llm-agent.log_llm_agent_main.by-request` | 查询唯一 |

**Mongo collection 字段命名**：camelCase（与 Java 字段对齐），与 JPA `@Column(name=...)` 里的 snake_case **不同**。
> MongoQueryEngine 直接读 camelCase，不做大小写转换。如果发现查询返回 null，**先怀疑字段名拼写**，不要怀疑引擎。

---

## 1. insight_datasource 元数据

**位置**：`templates/llm-agent/datasource.json`（**一个模板包只一份**）

```jsonc
{
  "_id_note": "导入时自动生成",
  "datasourceKey": "prod-llm-agent-shard-1",
  "datasourceName": "LLM-Agent 生产环境（分片1）",
  "datasourceType": "MONGODB",
  "logicalGroup": "llm-agent",                              // 必填
  "shardRole": "PRIMARY",                                   // PRIMARY / REPLICA / SHARD-N
  "status": "ACTIVE",
  "connectionConfig": {
    "host": "<由用户填写，如 10.20.30.41>",
    "port": 27017,
    "database": "llm_agent",
    "username": "<由用户填写>",
    "password": "<加密占位符：{ENCRYPTED}>",               // ⚠️ 禁止明文
    "authDatabase": "admin",
    "connectionPoolSize": 10,
    "connectionTimeoutMs": 30000,
    "socketTimeoutMs": 60000,
    "extraParams": { "serverSelectionTimeoutMs": 5000 }
  },
  "allowedCollections": [
    "log_llm_agent_main", "log_llm_task_detail",
    "log_llm_task_step", "log_llm_http_request"
  ],
  "deniedCollections": [],
  "description": "v1 模板包 - llm-agent 工程 Mongo 数据源",
  "tags": ["v1-template", "llm-agent", "production"],
  "createdBy": "template-import",
  "version": "1.0.0"
}
```

> **导入时变量替换**：
> - `host` / `port` / `username` / `password` 由用户实际配置覆盖
> - `database` 用户可改名
> - `password` **必须**经 KMS 加密后入库

---

## 2. insight_table_config 元数据（4 张表）

**位置**：`templates/llm-agent/tables/{collectionName}.json` × 4

### 2.1 4 张表总览

| tableKey | 真实 collection | 用途 | 关键字段 |
|---------|----------------|------|---------|
| `llm-agent.log_llm_agent_main` | `log_llm_agent_main` | Agent 实例 | `requestId`, `agentId`, `bizId`, `taskStatus`, `agentStatus` |
| `llm-agent.log_llm_task_detail` | `log_llm_task_detail` | 任务明细 | `requestId`, `taskUniqueName`, `taskIndex`, `success` |
| `llm-agent.log_llm_task_step` | `log_llm_task_step` | 任务步骤 | `logLlmTaskDetailId`, `step`, `input`, `output` |
| `llm-agent.log_llm_http_request` | `log_llm_http_request` | LLM HTTP 调用 | `requestId`, `modelType`, `spendTime`, `promptTokens` |

### 2.2 单表 schema 模板

```jsonc
{
  "_id_note": "导入时自动生成",
  "tableKey": "llm-agent.{collectionName}",
  "tableName": "{collectionName}",
  "datasourceKey": "prod-llm-agent-shard-1",              // 模板里是占位，导入时按实际替换
  "logicalGroup": "llm-agent",                            // 冗余存储便于查询
  "displayName": "{人类可读名}",
  "description": "{用途说明}",
  "tableType": "COLLECTION",                              // COLLECTION / VIEW
  "status": "ACTIVE",
  "queryConfig": {
    "defaultSortField": "{createTime}",
    "defaultSortOrder": "desc",
    "defaultPageSize": 20,
    "maxPageSize": 500,
    "enableExport": true,
    "exportFormats": ["CSV", "JSON"]
  },
  "tags": ["v1-template", "llm-agent"],
  "version": "1.0.0"
}
```

---

## 3. insight_column_config 元数据（列定义）

**位置**：`templates/llm-agent/columns/{tableKey}.{fieldName}.json`

### 3.1 数据类型映射（Mongo BSON → BI dataType）

| Mongo BSON | BI dataType | 备注 |
|-----------|------------|------|
| String | `VARCHAR` | |
| Int32 | `INT` | |
| Int64 / Long | `BIGINT` | |
| Double / Decimal128 | `DECIMAL` | |
| Boolean | `BOOLEAN` | |
| Date (ISODate) | `DATETIME` | |
| Object / EmbeddedDocument | `JSON` | |
| Array | `JSON` | |
| ObjectId | `VARCHAR` | 显示成 24 位 hex |
| Null | nullable=true | |

### 3.2 列配置字段

```jsonc
{
  "columnKey": "llm-agent.log_llm_agent_main.{fieldName}",
  "tableKey": "llm-agent.log_llm_agent_main",
  "columnName": "{fieldName}",                            // Mongo 真实字段名（camelCase）
  "displayName": "{人类可读名}",
  "description": "{说明}",
  "dataType": "{见上表}",
  "columnType": "DIMENSION",                              // DIMENSION / MEASURE / PRIMARY_KEY / FOREIGN_KEY / SYSTEM
  "sortable": true,
  "filterable": true,
  "required": false,
  "nullable": true,
  "displayOrder": 1,
  "width": 120,
  "minWidth": 60,
  "maxWidth": 400,
  "fixedPosition": null,                                  // "LEFT" / "RIGHT" / null
  "formatConfig": {                                       // 见下方
    "type": "TEXT"
  },
  "renderConfig": {                                       // 见下方
    "type": "TEXT"
  },
  "filterConfig": {                                       // 见下方
    "type": "INPUT"
  },
  "version": "1.0.0"
}
```

### 3.3 formatConfig 速查

| 场景 | type | 配置示例 |
|------|------|----------|
| 文本 | `TEXT` | `{ "type": "TEXT", "truncateLength": 50 }` |
| 金额 | `MONEY` | `{ "type": "MONEY", "symbol": "¥", "decimalPlaces": 2 }` |
| 日期 | `DATETIME` | `{ "type": "DATETIME", "pattern": "yyyy-MM-dd HH:mm:ss" }` |
| 数字 | `DECIMAL` | `{ "type": "DECIMAL", "decimalPlaces": 2, "thousandsSeparator": true }` |
| JSON 文本 | `JSON` | `{ "type": "JSON", "isCollapsible": true }` |

### 3.4 renderConfig 速查

| 场景 | type | 配置示例 |
|------|------|----------|
| 普通文本 | `TEXT` | `{ "type": "TEXT" }` |
| 超链接 | `LINK` | `{ "type": "LINK", "props": { "href": "/agent/{value}" } }` |
| 状态标签 | `TAG` | `{ "type": "TAG", "props": { "colorMap": { "2": "green", "3": "red" } } }` |
| JSON 折叠 | `JSON` | `{ "type": "JSON", "props": { "expandDepth": 1 } }` |
| 时间 | `DATETIME` | `{ "type": "DATETIME", "props": { "pattern": "yyyy-MM-dd HH:mm:ss" } }` |

### 3.5 filterConfig 速查

| 场景 | type | 配置示例 |
|------|------|----------|
| 精确匹配 | `INPUT` | `{ "type": "INPUT", "operator": "EQ" }` |
| 模糊匹配 | `INPUT` | `{ "type": "INPUT", "operator": "LIKE" }` |
| 范围 | `DATE_RANGE` | `{ "type": "DATE_RANGE", "operator": "BETWEEN" }` |
| 数字范围 | `NUMBER_RANGE` | `{ "type": "NUMBER_RANGE", "operator": "BETWEEN" }` |
| 枚举多选 | `SELECT` | `{ "type": "SELECT", "operator": "IN", "options": [...] }` |

---

## 4. 4 张表的字段清单（源数据）

> 字段定义以 Mongo 中真实存储为准（与 JPA `@Column(name=...)` 解耦）
> JPA Entity 字段是 camelCase，Mongo 字段也是 camelCase，但 MySQL 列名是 snake_case——三层互相对照表：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| ... | ... | ... | ... |

### 4.1 `log_llm_agent_main`（Agent 实例表）

```jsonc
// 字段清单（templates/llm-agent/columns/llm-agent.log_llm_agent_main.*.json）
{
  "id":              { type: "BIGINT",        role: "PRIMARY_KEY" },
  "bizId":           { type: "VARCHAR",       role: "DIMENSION" },  // 业务实例 ID（一个 biz 可多 request）
  "requestId":       { type: "VARCHAR",       role: "DIMENSION", sortable, filterable },
  "chatMessageId":   { type: "BIGINT",        role: "FOREIGN_KEY" },
  "agentId":         { type: "BIGINT",        role: "DIMENSION" },
  "topAgentName":    { type: "VARCHAR",       role: "DIMENSION" },
  "entranceAgent":   { type: "BOOLEAN",       role: "DIMENSION" },
  "title":           { type: "VARCHAR",       role: "DIMENSION" },
  "taskStatus":      { type: "INT",           role: "DIMENSION" },  // 0=pending,1=running,2=success,3=failed
  "agentStatus":     { type: "INT",           role: "DIMENSION" },  // 1=active,2=completed
  "shiftMustTask":   { type: "INT",           role: "DIMENSION" },
  "agentTryCount":   { type: "INT",           role: "MEASURE" },
  "taskIndex":       { type: "INT",           role: "DIMENSION" },
  "success":         { type: "BOOLEAN",       role: "DIMENSION" },
  "taskTryCount":    { type: "INT",           role: "MEASURE" },
  "finalTaskDetailId":{ type: "BIGINT",       role: "FOREIGN_KEY" },
  "createTime":      { type: "DATETIME",      role: "SYSTEM" },
  "createById":      { type: "BIGINT",        role: "FOREIGN_KEY" },
  "agentResult":     { type: "TEXT",          role: "DIMENSION" },  // JSON 文本，可折叠
  "agentEndTime":    { type: "DATETIME",      role: "SYSTEM" }
}
```

### 4.2 `log_llm_task_detail`（任务明细表）

```jsonc
{
  "id":                 { type: "BIGINT",    role: "PRIMARY_KEY" },
  "requestId":          { type: "VARCHAR",   role: "DIMENSION" },
  "chatMessageId":      { type: "BIGINT",    role: "FOREIGN_KEY" },
  "agentName":          { type: "VARCHAR",   role: "DIMENSION" },
  "roundNum":           { type: "INT",       role: "DIMENSION" },
  "taskName":           { type: "VARCHAR",   role: "DIMENSION" },
  "taskUniqueName":     { type: "VARCHAR",   role: "DIMENSION" },  // 定位源码行号
  "comment":            { type: "VARCHAR",   role: "DIMENSION" },
  "taskType":           { type: "VARCHAR",   role: "DIMENSION" },  // expression/template/action
  "resultPlan":         { type: "BOOLEAN",   role: "DIMENSION" },
  "fullPath":           { type: "VARCHAR",   role: "DIMENSION" },  // 源码行号映射
  "dynamicPlanDetailId":{ type: "BIGINT",    role: "FOREIGN_KEY" },
  "taskIndex":          { type: "INT",       role: "DIMENSION" },
  "agentTryCount":      { type: "INT",       role: "MEASURE" },
  "taskTryCount":       { type: "INT",       role: "MEASURE" },
  "finalResult":        { type: "BOOLEAN",   role: "DIMENSION" },
  "success":            { type: "BOOLEAN",   role: "DIMENSION" },
  "result":             { type: "TEXT",      role: "DIMENSION" },
  "resultType":         { type: "INT",       role: "DIMENSION" },
  "createTime":         { type: "DATETIME",  role: "SYSTEM" },
  "taskEndTime":        { type: "DATETIME",  role: "SYSTEM" },
  "logLlmAgentMainId":  { type: "BIGINT",    role: "FOREIGN_KEY" }
}
```

### 4.3 `log_llm_task_step`（任务步骤表）

```jsonc
{
  "id":                  { type: "BIGINT",    role: "PRIMARY_KEY" },
  "step":                { type: "INT",       role: "DIMENSION" },  // 1=template,2=rag,3=parser,4=verifier,5=action,6=result
  "template":            { type: "VARCHAR",   role: "DIMENSION" },
  "resultType":          { type: "INT",       role: "DIMENSION" },
  "input":               { type: "TEXT",      role: "DIMENSION" },
  "output":              { type: "TEXT",      role: "DIMENSION" },
  "success":             { type: "BOOLEAN",   role: "DIMENSION" },
  "endTime":             { type: "DATETIME",  role: "SYSTEM" },
  "logLlmTaskDetailId":  { type: "BIGINT",    role: "FOREIGN_KEY" },
  "requestId":           { type: "VARCHAR",   role: "DIMENSION" },
  "chatMessageId":       { type: "BIGINT",    role: "FOREIGN_KEY" }
}
```

### 4.4 `log_llm_http_request`（LLM HTTP 调用表）

```jsonc
{
  "id":                  { type: "BIGINT",    role: "PRIMARY_KEY" },
  "bizId":               { type: "BIGINT",    role: "FOREIGN_KEY" },
  "requestId":           { type: "VARCHAR",   role: "DIMENSION" },
  "taskDetailId":        { type: "BIGINT",    role: "FOREIGN_KEY" },
  "agent":               { type: "VARCHAR",   role: "DIMENSION" },
  "agentStoreId":        { type: "BIGINT",    role: "FOREIGN_KEY" },
  "templateName":        { type: "VARCHAR",   role: "DIMENSION" },
  "planUniqueName":      { type: "VARCHAR",   role: "DIMENSION" },
  "modelType":           { type: "VARCHAR",   role: "DIMENSION" },
  "spendTime":           { type: "BIGINT",    role: "MEASURE" },    // 毫秒
  "promptTokens":        { type: "INT",       role: "MEASURE" },
  "completionTokens":    { type: "INT",       role: "MEASURE" },
  "requestParam":        { type: "JSON",      role: "DIMENSION" },
  "requestBody":         { type: "TEXT",      role: "DIMENSION" },
  "requestUrl":          { type: "VARCHAR",   role: "DIMENSION" },
  "responseBody":        { type: "TEXT",      role: "DIMENSION" },
  "successExpression":   { type: "BOOLEAN",   role: "DIMENSION" },
  "createTime":          { type: "DATETIME",  role: "SYSTEM" },
  "createDate":          { type: "DATE",      role: "SYSTEM" },
  "createById":          { type: "BIGINT",    role: "FOREIGN_KEY" }
}
```

---

## 5. insight_query_config 元数据（预置查询）

**位置**：`templates/llm-agent/queries/{queryKey}.json`

### 5.1 预置查询清单

| queryKey | tableKey | 用途 | 关键 filter |
|---|---|---|---|
| `llm-agent.log_llm_agent_main.by-request` | log_llm_agent_main | 按 requestId 查主表 | `{requestId: {$eq: ?}}` |
| `llm-agent.log_llm_agent_main.failures` | log_llm_agent_main | 失败实例列表 | `{success: {$eq: false}, createTime: {$gte: ?}}` |
| `llm-agent.log_llm_task_detail.by-request` | log_llm_task_detail | 按 requestId 查任务 | `{requestId: {$eq: ?}}`（按 taskIndex 排序） |
| `llm-agent.log_llm_task_detail.failures` | log_llm_task_detail | 失败任务 | `{success: {$eq: false}, createTime: {$gte: ?}}` |
| `llm-agent.log_llm_http_request.by-request` | log_llm_http_request | 按 requestId 查 LLM 调用 | `{requestId: {$eq: ?}}` |
| `llm-agent.log_llm_http_request.slow` | log_llm_http_request | 慢调用 TopN | `{spendTime: {$gte: ?}}`（按 spendTime desc） |
| `llm-agent.log_llm_http_request.cost` | log_llm_http_request | 成本聚合 | 时间范围 |

### 5.2 单条 query 配置模板

```jsonc
{
  "queryKey": "llm-agent.log_llm_agent_main.by-request",
  "queryName": "按 RequestId 查 Agent 实例",
  "tableKey": "llm-agent.log_llm_agent_main",
  "queryType": "TEMPLATE",
  "description": "通过 requestId 精确查询单次请求的 Agent 实例",
  "mongoFilter": { "requestId": { "$eq": ":requestId" } },       // :xxx 是占位符
  "mongoSort":    { "createTime": -1 },
  "mongoLimit":   10,
  "parameters": [
    {
      "name": "requestId",
      "dataType": "VARCHAR",
      "required": true,
      "label": "Request ID",
      "placeholder": "请输入 requestId"
    }
  ],
  "resultConfig": {
    "defaultColumns": ["id", "bizId", "requestId", "agentId", "topAgentName", "taskStatus", "success", "createTime"]
  },
  "shareConfig": { "public": true },
  "version": "1.0.0"
}
```

> **占位符语法**：`:paramName` —— MongoQueryEngine 注入到 mongoFilter 之前会做 `replace(":requestId", value)`。

---

## 6. 导入工作流（用户视角）

```bash
# 1. 准备环境变量
export MONGODB_URI="mongodb://10.20.30.40:27017/agent_insight"
export LLMA_MONGO_HOST="10.20.30.41"
export LLMA_MONGO_PORT="27017"
export LLMA_MONGO_DB="llm_agent"
export LLMA_MONGO_USER="readonly"
export LLMA_MONGO_PWD="<KMS 加密后>"

# 2. 一键导入（待 W2 写 importer 工具）
./scripts/import-template.sh llm-agent

# 3. UI 验证
open http://localhost:3010/explorer
# 应看到 4 张表 + 7 条预置查询
```

---

## 7. 字段命名陷阱速查

| 坑 | 后果 | 防范 |
|---|---|---|
| Mongo `requestId` vs MySQL `request_id` | 查询返回 null | spec 统一用 camelCase |
| `id` vs `_id` (ObjectId) | 排序/分页乱 | 列定义里用 `id`（Mongo 业务主键），ObjectId 留给 `_id` |
| `success` 字段类型不一致 | 老 MySQL 是 TINYINT，Mongo 是 Boolean | MongoQueryEngine 支持自动转换，列定义写 BOOLEAN |
| `createTime` 是 LocalDateTime 还是 String | 比较出错 | Mongo 必须是 ISODate，列定义写 DATETIME |
| `logLlmAgentMainId` 这种 long 字段名 | 不报错但难懂 | 不重命名，保留业务原貌 |

---

## 8. 版本记录

| 版本 | 日期 | 备注 |
|---|---|---|
| 1.0.0 | 2026-07-03 | 初稿：4 张表 + 7 条预置查询 + 字段命名规范 |