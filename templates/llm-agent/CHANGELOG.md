# Changelog — llm-agent 模板包

所有版本变动记录于此。最新版本在最上面。

---

## [1.0.0] - 2026-07-03

### Added（首次发布）

- 数据源配置：`datasource.json`（`logicalGroup: llm-agent`，`shardRole: PRIMARY`）
- 4 张表元数据：
  - `tables/log_llm_agent_main.json`（Agent 实例表）
  - `tables/log_llm_task_detail.json`（任务明细表）
  - `tables/log_llm_task_step.json`（任务步骤表）
  - `tables/log_llm_http_request.json`（LLM HTTP 调用表）
- 7 条预置查询：
  - `by-request`（4 条，按 requestId 查 4 张表）
  - `failures`（2 条，失败实例 / 失败任务）
  - `slow`（1 条，LLM 慢调用 TopN）
- 配套规范文档：[`docs/02b-template-spec.md`](../../docs/02b-template-spec.md)

### Notes

- 这是 Agent Insight 平台的**第一个**业务模板包
- 列定义文件第一批只覆盖 `log_llm_agent_main`（19 个字段），其余 3 张表在 v1.0.1 补全
- 报表（`reports/`）目录预留，v1.1 填充

### Compatibility

- 依赖：Agent Insight v1.0.0+（MongoQueryEngine）
- 引擎要求：MongoDB 6.0+，Spring Boot 4.1+

---

## [1.0.1] - 待定

### Planned

- 补全 `log_llm_task_detail` / `log_llm_task_step` / `log_llm_http_request` 的列定义
- 报表目录填充 3 个内置报表：
  - Agent 健康度（成功/失败率、慢 task 排行）
  - LLM 成本（按模型/agent 维度分摊）
  - 慢调用分析（TopN + 趋势）

---

## 版本号规则

参考 [Semantic Versioning](https://semver.org/)：

- **MAJOR**：表结构或字段语义变更
- **MINOR**：新增表/查询/列
- **PATCH**：配置描述/默认排序等微调