# llm-agent 监控模板包（v1.0.0）

> Agent Insight 平台的**第一个**业务监控模板。
> 通过本模板，**5 分钟**接入 llm-agent 工程的执行轨迹数据，开始 BI 分析。

---

## 0. 这是什么

`templates/llm-agent/` 是**纯 JSON 元数据目录**，定义了：
- 1 个**数据源配置**（指向 llm-agent Mongo 集群）
- 4 个**业务表**的元数据（`log_llm_agent_main` / `_task_detail` / `_task_step` / `_http_request`）
- 这些表的所有**列定义**（displayName / 渲染方式 / 过滤方式）
- 7 条**预置查询**（按 requestId 查、按时间查、TopN 慢调用等）

**与引擎代码完全解耦**：
- 不导入这个目录 → Agent Insight 是个空平台（只有 insight_* 元数据集合）
- 导入这个目录 → 立即获得 llm-agent 全套监控能力
- **任何团队**都可以写自己的模板包（templates/{team}/）

---

## 1. 目录结构

```
templates/llm-agent/
├── README.md                              ← 本文件
├── CHANGELOG.md                           ← 版本历史
├── datasource.json                        ← 1 个 llm-agent Mongo 数据源配置
├── tables/
│   ├── log_llm_agent_main.json            ← 4 张表
│   ├── log_llm_task_detail.json
│   ├── log_llm_task_step.json
│   └── log_llm_http_request.json
├── columns/
│   └── llm-agent.log_llm_agent_main.{field}.json  ← 列定义（每列 1 个文件）
├── queries/
│   ├── llm-agent.log_llm_agent_main.by-request.json
│   ├── llm-agent.log_llm_agent_main.failures.json
│   ├── llm-agent.log_llm_task_detail.by-request.json
│   ├── llm-agent.log_llm_task_detail.failures.json
│   ├── llm-agent.log_llm_http_request.by-request.json
│   ├── llm-agent.log_llm_http_request.slow.json
│   └── llm-agent.log_llm_http_request.cost.json
└── reports/                               ← 内置报表（待 v1.1）
```

---

## 2. 5 分钟接入指南

### 2.1 准备

```bash
# 1. 确认 llm-agent Mongo 可达
mongosh "mongodb://10.20.30.41:27017/llm_agent" --eval "db.runCommand({ping:1})"

# 2. 准备 Insight Mongo
export MONGODB_URI="mongodb://10.20.30.40:27017/agent_insight"
```

### 2.2 编辑 `datasource.json`

把 `connectionConfig` 里的占位符替换成实际值：
- `host` / `port` / `database` / `username` / `password`
- `password` **必须**用 KMS 加密后的密文（明文禁止入库）

### 2.3 导入（W2 之后才有 importer）

```bash
# 方式 A：CLI 工具（推荐）
./scripts/import-template.sh llm-agent

# 方式 B：UI 端
# Templates → Import Template → 选择 templates/llm-agent/
```

### 2.4 验证

```bash
# 浏览器打开
open http://localhost:3010/explorer

# 应看到：
# - 左侧 4 张表（log_llm_agent_main 等）
# - 右侧默认查询界面
# - 顶部"预置查询"下拉框 7 条
```

---

## 3. 4 张表速览

| 表 | Mongo collection | 用途 | 常用查询 |
|---|---|---|---|
| Agent 实例 | `log_llm_agent_main` | 每次 Agent 调用的总览 | 按 requestId、失败列表 |
| 任务明细 | `log_llm_task_detail` | Agent 内部每个 task 的输入输出 | 按 requestId + taskIndex |
| 任务步骤 | `log_llm_task_step` | task 内的 template/rag/action 步骤 | 按 taskDetailId |
| LLM HTTP | `log_llm_http_request` | 每次 LLM API 调用的请求/响应 | 慢调用、Token 成本 |

---

## 4. 7 条预置查询

| 查询 | 用途 | 参数 |
|---|---|---|
| `by-request`（4 条） | 按 requestId 查 | `requestId` |
| `failures`（2 条） | 查失败实例/任务 | `startTime` |
| `slow` | 慢 LLM 调用 TopN | `minSpendMs` |
| `cost` | LLM 成本聚合 | `startTime`, `endTime` |

---

## 5. 字段命名约定

> ⚠️ **Mongo 字段是 camelCase，不是 snake_case**。详见 [`docs/02b-template-spec.md`](../../docs/02b-template-spec.md) §0。

| Mongo 真实字段 | 老 MySQL 列名 | JPA @Column(name=) |
|---|---|---|
| `requestId` | `request_id` | `request_id` |
| `agentId` | `agent_id` | `agent_id` |
| `bizId` | `biz_id` | `biz_id` |
| `topAgentName` | `top_agent_name` | `top_agent_name` |

**MongoQueryEngine 读 camelCase**。如果查询返回 null，先怀疑字段名拼写。

---

## 6. 升级 / 卸载

### 升级到新版本

```bash
# 拉取新版 templates/llm-agent/
git pull

# 重新导入（保留用户自定义）
./scripts/import-template.sh llm-agent --preserve-user-edits
```

### 卸载

```bash
./scripts/remove-template.sh llm-agent
# 会同时删 datasource / table_config / column_config / query_config
# 不会动 insight_query_history（审计追溯用）
```

---

## 7. 接入新业务（写新模板）

参照本目录结构，在 `templates/{your-team}/` 下新建一份。**预计工作量**：

| 步骤 | 时间 | 谁 |
|---|---|---|
| 写 `datasource.json` × 1 | 10 分钟 | 业务方 |
| 写 `tables/*.json` × N | 半天 | 业务方 |
| 写 `columns/*.json` × M | 半天 | 业务方 |
| 写 `queries/*.json` × K | 1 天 | 业务方 |
| 在 UI 注册数据源 + 导入 | 10 分钟 | 业务方 |

**参考本目录**作为模板。

---

## 8. 引用文档

- [`docs/02b-template-spec.md`](../../docs/02b-template-spec.md) — 元数据 schema 详细规范（写 JSON 前必看）
- [`docs/06-DataExplorer-HLD.md`](../../docs/06-DataExplorer-HLD.md) v1.2 — 通用 Data Explorer 设计
- [`docs/00-revision-2026-07-03.md`](../../docs/00-revision-2026-07-03.md) — 2026-07-03 架构修订
- [`AGENTS.md`](../../AGENTS.md) — 项目协作规约