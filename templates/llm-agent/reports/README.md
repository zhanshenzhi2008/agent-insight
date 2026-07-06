# 内置报表（v1.1 填充）

本目录存放**预置报表定义** JSON。报表是基于查询的组合展示。

## 计划 v1.1 填充

| 报表 | 关联查询 | 展示形式 |
|---|---|---|
| `agent-health.json` | `failures` × 2 | 卡片 + 折线：成功/失败率、慢 task 排行 |
| `llm-cost.json` | `cost` + `slow` | 卡片 + 柱状：按模型分摊的成本 + 慢调用 |
| `slow-trend.json` | `slow` | 时间趋势：P50 / P95 / P99 折线 |

每个报表 JSON 结构（草案）：

```json
{
  "reportKey": "agent-health",
  "reportName": "Agent 健康度",
  "description": "整体健康度概览",
  "layout": "grid",
  "widgets": [
    {
      "type": "KPI",
      "title": "今日成功率",
      "queryKey": "llm-agent.log_llm_agent_main.by-request",
      "compute": "count(success=true) / count(*)"
    }
  ]
}
```

详细 schema 在 v1.1 的 `docs/02b-template-spec.md` 中补全。