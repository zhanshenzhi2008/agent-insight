# Agent Insight 需求规格说明书（SRS）

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v0.1 | 2026-06-25 | - | 初稿 |

---

## 1. 概述

### 1.1 项目背景

当前 Agent（LLM Agent）运行链路缺乏系统性分析能力。当 Agent 执行出错或结果异常时，开发者需要：

1. 在海量日志文件中按 `requestId` 搜索，手动拼凑完整链路
2. 对照 Agent 脚本源码，理解每步任务的预期行为
3. 逐条查看 `log_llm_task_detail` / `log_llm_task_step` / `log_llm_http_request` 等多张表，手动关联上下文
4. 缺乏源码级别的执行轨迹映射，定位问题耗时极长

**核心痛点**：日志散、数据孤岛、源码与执行轨迹割裂，无法快速定位 Agent 代码级问题。

### 1.2 项目目标

构建 **Agent Insight 分析引擎**，通过整合 **per-request 日志文件** + **结构化执行轨迹表** + **Agent 脚本源码**，提供源码级、执行轨迹级、LLM 调用级的三维分析能力。

### 1.3 术语表

| 术语 | 定义 |
|------|------|
| **Agent** | LLM Agent 实例，由脚本（.java/.py/.md）定义执行流程 |
| **requestId / traceId** | 一次 Agent 调用的唯一标识，MDC 中贯穿全链路；per-request 日志文件名即为此 ID |
| **taskIndex** | Agent 脚本中任务的执行顺序编号 |
| **taskUniqueName** | 任务的唯一标识符（planInfo.uniqueName），对应脚本中节点 |
| **log_llm_task_detail** | 任务明细日志表，每行 = Agent 脚本中的一个执行单元 |
| **log_llm_task_step** | 任务步骤日志表，每行 = task 内的具体步骤（template/rag/parser/verifier/action/result） |
| **log_llm_http_request** | LLM HTTP 请求日志表，每行 = 一次 LLM 调用 |
| **log_llm_agent_main** | Agent 运行主表，每行 = 一个 Agent 实例（入口或子 Agent） |
| **per-request 日志文件** | `{dataRoot}/user/{username}/{yyyyMM}/{requestId}.log`，包含完整链路文本 |

---

## 2. 功能需求

### 2.1 模块总览

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent Insight                             │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ 日志检索     │  │ 执行轨迹    │  │ LLM 调用分析         │ │
│  │ (per-req)   │  │ (detail)    │  │ (http_request)      │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                     │            │
│         └────────────────┴─────────────────────┘          │
│                          │                                  │
│              ┌───────────▼───────────┐                      │
│              │    源码对照视图        │                      │
│              │ (源码 + 轨迹 叠加显示) │                      │
│              └───────────────────────┘                      │
│                                                             │
│              ┌───────────────────────┐                      │
│              │    根因分析 & 报告     │                      │
│              └───────────────────────┘                      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 功能列表

#### F1：请求检索与概览

- **F1.1** 按 `requestId` 精确查询，附带时间范围、Agent Name 过滤
- **F1.2** 请求概览卡片：显示总请求数、成功/失败率、平均耗时、涉及的 Agent 列表
- **F1.3** 请求列表：支持分页、按时间/状态排序
- **F1.4** 请求状态追踪：展示该 requestId 下的 Agent 实例列表（入口 + 子 Agent）

#### F2：Per-Request 日志全文查看

- **F2.1** 读取并展示指定 requestId 的完整 per-request 日志文件
- **F2.2** 日志高亮：按 traceId / threadId 分色块显示不同线程的日志
- **F2.3** 日志时间线视图：以时间轴方式展示完整调用链
- **F2.4** 日志全文搜索：支持正则表达式、关键词高亮
- **F2.5** 日志下载：支持导出原始 .log 文件

#### F3：Agent 执行轨迹分析

- **F3.1** 结构化执行轨迹列表：按 `taskIndex` 排序展示 `log_llm_task_detail`
- **F3.2** 每条轨迹的详细信息：输入参数、输出结果、耗时、是否成功、错误信息
- **F3.3** 步骤级明细：展开查看 `log_llm_task_step`（template / rag / action 等每步的 input/output）
- **F3.4** 执行状态图：可视化展示任务树结构，标注成功/失败节点
- **F3.5** 失败任务定位：高亮失败的 task，直达相关日志位置

#### F4：LLM 调用分析

- **F4.1** LLM 调用列表：展示该 requestId 下所有 `log_llm_http_request`
- **F4.2** 慢 LLM 调用识别：按 `spendTime` 降序，展示耗时最长的调用
- **F4.3** Token 消耗分析：`promptTokens` + `completionTokens` 汇总
- **F4.4** 请求体 / 响应体查看：支持查看完整的 requestBody 和 responseBody
- **F4.5** LLM 失败列表：筛选 `successExpression = false` 的调用

#### F5：源码对照

- **F5.1** Agent 脚本文件树：展示该请求涉及的 Agent 脚本列表
- **F5.2** 源码查看：支持查看 .java / .py / .md 格式的 Agent 脚本
- **F5.3** 源码 + 轨迹叠加：左侧源码，右侧执行结果，taskIndex 对应代码块高亮
- **F5.4** 代码行号导航：从执行轨迹直接跳转到对应源码行

#### F6：根因分析与报告（可选，后续迭代）

- **F6.1** 失败根因推断：基于源码上下文 + 执行结果 + LLM 调用日志，生成根因分析
- **F6.2** 分析报告导出：PDF / Markdown 格式
- **F6.3** 历史问题模式库：记录高频错误类型及解决方案

### 2.3 非功能需求

| 维度 | 要求 |
|------|------|
| **性能** | 单个 requestId 查询响应时间 < 2s（日志文件 + 数据库联合查询） |
| **数据范围** | 支持查询最近 30 天的数据 |
| **日志文件大小** | 单个 .log 文件最大支持 100MB 读取，超大文件分页加载 |
| **并发** | 支持 10 用户同时在线分析 |
| **安全** | 日志查看需权限校验，防止敏感数据泄露 |

---

## 3. 数据源规格

### 3.1 数据源总览

| 数据源 | 路径/表 | 用途 |
|--------|---------|------|
| Per-Request 日志 | `{dataRoot}/user/{username}/{yyyyMM}/{requestId}.log` | 完整链路文本，按 traceId 过滤 |
| 任务明细 | `log_llm_task_detail` | 每行 = Agent 脚本一个执行单元 |
| 任务步骤 | `log_llm_task_step` | 每行 = task 内的具体步骤 |
| LLM 调用 | `log_llm_http_request` | 每次 LLM 调用的请求/响应/耗时 |
| Agent 主表 | `log_llm_agent_main` | Agent 实例维度信息 |
| Agent 脚本 | `{systemRoot}/agt/*.java` 等 | 源码 |

### 3.2 数据关联

```
per-request 日志文件
  └─ requestId (= traceId)
       │
       ├─→ log_llm_agent_main.requestId      (Agent 实例，entrance / sub)
       │       └─→ log_llm_agent_context      (完整上下文快照)
       │
       ├─→ log_llm_task_detail.requestId      (执行单元，按 taskIndex 排序)
       │       └─→ log_llm_task_step          (步骤级 input/output)
       │
       └─→ log_llm_http_request.requestId     (LLM 调用)
```

**关键字段对照**：

- `log_llm_task_detail.taskIndex` = 执行顺序
- `log_llm_task_detail.taskUniqueName` = 脚本中节点标识
- `log_llm_task_detail.fullPath` = 任务在 AgentFlow 中的完整路径
- `log_llm_task_step.step` = 步骤类型（template=0, rag=1, parser=2, verifier=3, action=4, result=5）

---

## 4. 用户交互流程

### 4.1 主流程：分析一个失败的 Agent 请求

```
1. 输入 requestId（从告警/反馈中获得）
   ↓
2. 系统加载请求概览
   ├─ Agent 实例列表（入口 + 子 Agent）
   ├─ 成功/失败状态
   └─ 总耗时
   ↓
3. 查看 Per-Request 日志全文
   ├─ 定位错误发生的精确位置
   └─ 按线程分组查看并发链路
   ↓
4. 查看执行轨迹
   ├─ 确认哪个 taskIndex 失败
   ├─ 展开失败的 task 查看 step 详情
   └─ 查看 input / output / error message
   ↓
5. 查看 LLM 调用详情（如果是 LLM 响应异常）
   ├─ 查看 requestBody / responseBody
   ├─ 识别慢调用
   └─ 分析 token 消耗
   ↓
6. 源码对照
   ├─ 加载对应 Agent 脚本
   ├─ 高亮失败 task 对应的源码行
   └─ 结合源码 + 执行结果定位根因
```

### 4.2 快捷入口

- 从监控告警页面点击 "Agent Insight 分析" → 直接传入 requestId 跳转
- 从 Agent 调试页面 → 一键查看该实例的执行轨迹

---

## 5. 边界条件与异常处理

| 场景 | 处理方式 |
|------|----------|
| requestId 不存在 | 返回空结果，提示 "未找到该请求，请确认 requestId 正确" |
| per-request 日志文件不存在 | 仅展示数据库轨迹部分，提示 "原始日志文件已过期/删除" |
| 日志文件超过 100MB | 分页加载，每页 5MB，提供"加载更多" |
| Agent 脚本文件不存在 | 源码对照区域显示"脚本文件未找到，可能已被删除或未入库" |
| 数据库连接超时 | 重试 2 次，失败后返回部分数据 + 错误提示 |
| 跨月日志文件 | 按 requestId 中的时间戳自动定位到对应月份目录 |

---

## 6. 后续迭代方向

| 优先级 | 功能 | 说明 |
|--------|------|------|
| P1 | 根因分析报告 | LLM + 源码 + 轨迹 联合推理 |
| P2 | 性能分析 | 统计各 task / step 的耗时分布，识别瓶颈 |
| P2 | 历史模式库 | 记录高频错误及解决方案，支持快速检索 |
| P3 | 实时流式分析 | SSE 推送，支持查看正在运行的 Agent |
| P3 | 对比分析 | 对比两次执行的轨迹差异 |

---

## 7. 附录

### 7.1 现有系统参照

- 日志设计参考：`llm-agent-framework/agent-runtime/docs/design/logging-design.md`
- 主表设计参考：`llm-agent-framework/agent-runtime/docs/design/log-agent-main-design.md`

### 7.2 依赖系统

| 系统 | 依赖内容 |
|------|----------|
| MySQL | `log_llm_task_detail`、`log_llm_task_step`、`log_llm_http_request`、`log_llm_agent_main` |
| MongoDB | 历史执行轨迹归档 |
| 文件存储 | per-request 日志文件 |
| Redis | 查询缓存（可选） |
| Agent 脚本仓库 | Agent 源码文件 |
