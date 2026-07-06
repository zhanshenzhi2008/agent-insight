# AGENTS.md — agent-insight 协作规约

> 适用于所有在本仓库工作的 AI 编码 Agent（Cursor / Claude Code / Copilot / 其他）。
> 这是一份**项目级**规约，与 `docs/` 并列，专为 Agent 协作场景设计。

---

## 1. 项目一句话

**Agent Insight = Agent 执行链路分析平台。** 三维分析：源码 / 执行轨迹 / LLM 调用。
数据来源：`per-request` 日志文件 + MySQL 上的 `log_llm_*` 结构化表 + MongoDB 元数据 + 动态外部数据源。

---

## 2. 技术栈（硬约束）

| 层 | 技术 | 版本 | 不要 |
|----|------|------|------|
| 后端 | Spring Boot | 4.1 | 不要降级到 3.x；不要换 jakarta → javax |
| AI | Spring AI | 2.0 | 不要直接用 OpenAI SDK，**必须走 Spring AI** |
| ORM | Spring Data JPA / MongoDB | 6.x / 4.x | 不要写原生 JdbcTemplate 直连业务表 |
| 前端 | React + TypeScript | 19 / 5 | 不要引入 Vue / Svelte |
| 构建 | Vite | 8 | 不要换 webpack |
| JDK | Java | 21 | 不要用 17 / 25 |

---

## 3. 端口契约（端口是规约的一部分）

| 服务 | 端口 | 配置位置 |
|------|------|----------|
| 后端 API | **9280** | `application.yml: server.port` |
| 前端 dev | **3010** | `agent-insight-web/vite.config.ts` |
| 前端 Vite proxy | `/api → 9280` | `vite.config.ts` |
| E2E baseURL | **3010** | `playwright.config.ts` |

> ⚠️ 修改端口必须同步 3 处：vite + playwright + `docs/07-部署文档.md` §2.3。
> E2E 默认 9280 → 3010 的历史教训已踩过，禁止回滚。

---

## 4. 目录导航

```
agent-insight/
├── agent-insight-server/                  ← 后端
│   └── src/main/java/com/llm/insight/
│       ├── controller/                     ← REST API
│       ├── service/  service/impl/         ← 业务层
│       ├── repository/                     ← JPA（仅 log_llm_* 表）
│       ├── explorer/
│       │   ├── engine/                     ← 多数据源查询引擎
│       │   │   ├── DynamicDatasourceManager.java
│       │   │   ├── QueryRouter.java
│       │   │   ├── SqlQueryExecutor.java   ← NamedParameterJdbcTemplate
│       │   │   ├── MongoQueryExecutor.java
│       │   │   └── ColumnAnalyzerService.java
│       │   ├── ai/                         ← Spring AI 调用点
│       │   └── document/                   ← Mongo 文档 POJO
│       ├── dto/  config/  common/
│       └── AgentInsightApplication.java
├── agent-insight-web/                      ← 前端
│   └── src/
│       ├── pages/Explorer/...              ← Data Explorer 子模块
│       ├── components/DynamicTable/        ← 通用
│       └── services/{api,explorerApi}.ts
├── docs/                                   ← 文档（不要改 .md 编号）
├── performance/                            ← JMeter PT-01~PT-08
└── docker/  Dockerfile*  docker-compose.yml
```

---

## 5. 命名规约

| 对象 | 规则 | 示例 |
|------|------|------|
| Java 类 | PascalCase，**职责明确** | `QueryRouter` / `SqlQueryExecutor` |
| Java 方法 | camelCase，动词开头 | `route(tableKey)` / `getSqlDataSource(ds)` |
| 包名 | 全小写，点分层 | `com.llm.insight.explorer.engine` |
| 数据库表 | snake_case，全小写 | `log_llm_task_detail` |
| 字段名 | 严格遵循原表（`create_time` / `request_id`） | **不要**改成驼峰 |
| React 组件 | PascalCase 文件夹 + index.tsx | `pages/Explorer/TableExplorer/` |
| API path | kebab-case，复数 | `/api/v1/llm-calls/slow` |
| 环境变量 | UPPER_SNAKE | `OPENAI_API_KEY` |
| 日志 MDC key | 小写下划线 | `requestId` / `taskIndex` |

---

## 6. 安全红线（**改这些代码必须人工 review**）

### 6.1 SQL 注入
- ✅ **唯一允许的方式**：`NamedParameterJdbcTemplate` + 占位符 `:paramName`
- ❌ **禁止**：`String.format("... WHERE id = %s", id)` / 字符串拼接 / `"... " + value`
- ❌ **禁止**：把用户输入直接拼进 `tableName` / `column` / `ORDER BY`

### 6.2 路径穿越
- ✅ 文件读取走 `LogPathResolver` / `SourcePathResolver`，**经过归一化 + 前缀白名单校验**
- ❌ **禁止**：`Paths.get(userInput).toString()` 后直接 `new File(...)`
- 日志根目录：`{dataRoot}/user/{username}/{yyyyMM}/{requestId}.log`
- 脚本根目录：`{scriptRoot}/...`
- 任何读取操作必须验证归一化路径仍在白名单前缀内

### 6.3 数据源注入
- ✅ 走 `DynamicDatasourceManager` + 配置表（MongoDB 存 `InsightDatasource`）
- ❌ **禁止**：接受前端传入的 JDBC URL 直连

### 6.4 敏感信息
- ❌ **禁止** commit：`.env` / `.env.local` / 含 `*_KEY` / `*_PASSWORD` 的文件
- ✅ 用 `${ENV:default}` 占位，默认值不能是真凭据
- 提交前必须 `git status` 检查

### 6.5 鉴权（当前是骨架）
- 所有 `@PreAuthorize` / `hasRole(...)` 当前**默认放行**
- 对接外部 `llm-agent` 权限体系时，禁止在前端 `localStorage` 明文存 token 超过 24h

---

## 7. 日志与错误规约

### 7.1 日志
- 用 SLF4J + Lombok `@Slf4j`，**禁止** `System.out.println`
- 业务关键路径必须打 INFO（含 requestId / taskIndex）
- 异常必须打 ERROR + stacktrace
- MDC：`requestId` / `agentName` / `taskIndex`

### 7.2 错误响应
- 统一 `ApiResponse<T>`：见 `common/ApiResponse.java`
- 业务异常抛 `BizException(code, message)`，由全局 handler 转 JSON
- 不在前端 catch 后 `alert()`，用 antd `message.error`

---

## 8. AI Agent 行为规约（**最关键**）

### 8.1 改动前必须做
1. **先探索**：用 `explore` subagent 或 Read/Grep 把上下文吃透，再动代码
2. **先红测**：每个新功能 / 修复，先写一个失败的 e2e 或 JUnit，再写实现
3. **改 SQL / 路径相关代码**：必须先列出影响面（"会改到哪些 Controller / Service / 测试"）

### 8.2 改动中
- 每次只改一类问题（不要把 fix + refactor + docs 混在一个 commit）
- 改完一个文件立刻跑相关测试（不要攒到最后）
- 不要创建 README 之外的 `.md`，除非明确要求
- 不要删测试，除非测试本身错误

### 8.3 改动后必须跑（Verification Checklist）

```bash
# 1. 后端编译 + 单元测试
cd agent-insight-server && mvn -q test

# 2. 前端类型检查 + 单元测试
cd agent-insight-web && npm run build && npm test

# 3. E2E（dev server 自动启）
cd agent-insight-web && npx playwright test

# 4. 安全 grep（提交前自检）
git diff --staged | grep -iE 'password|api_key|secret|token' && echo "⚠️ 包含敏感词" || echo "✓ clean"
```

**全部通过才能标记完成。** 任何一项红，必须修到绿为止。

### 8.4 改动后必须更新
- 改了端口/环境变量 → 更新 `docs/07-部署文档.md` §2
- 改了 API 路径/参数 → 更新 `docs/02-HLD.md` §5
- 改了安全相关 → 更新 §6 红线清单 + 增量加 changelog
- 改了 SQL 生成 → 在 PR 描述里贴 sample SQL（红→绿对比）

---

## 9. AI 改动失败的兜底

| 症状 | 5 分钟回滚 |
|------|-----------|
| E2E 大面积红 | `git stash` → `git checkout main -- <files>` |
| mvn test 编译挂 | `mvn -q clean && mvn -q test-compile` 看错误 |
| 端口冲突 | 先看 `terminals/*.txt` 哪个进程占用，按端口 kill |
| 引入循环依赖 | 看 import 图，回退到上一次 `mvn dependency:tree` clean |
| AI Provider 报错 | 确认 `AI_ENABLED=false`，AI 失败不应阻塞主链路 |

**遇到 4 次失败**：停止重复动作，整理"现象 + 已尝试 + 阻塞点"再问用户。

---

## 10. 文档规约

- 文档编号固定：`01-SRS / 02-HLD / 03-开发指南 / 04-测试 / 05-排期 / 06-DataExplorer-HLD / 07-部署`，**不新增编号**
- 新增文档放 `docs/` 根目录，文件名带版本日期
- 所有 `.md` 顶部带版本表格（v0.x + 日期 + 备注）
- 改动 HLD 必须同步更新 §10 风险表

---

## 11. Commit & Branch

- 遵循 `README.md` 中的 Conventional Commits（feat / fix / docs / refactor / test / chore）
- scope 用模块名：`feat(explorer):` / `fix(log):` / `docs(readme):`
- 分支：`feature/*` / `fix/*` / `docs/*` / `refactor/*`
- **禁止**直推 main（除了 hotfix 且必须 CODEOWNER 同意）

---

## 12. 不要做的事（Landmines）

- ❌ 不要把 `log_llm_*` 表的字段改成驼峰（Hibernate 映射会断）
- ❌ 不要用 `*` 通配 import
- ❌ 不要在前端组件里写 SQL / 文件 IO
- ❌ 不要在 Controller 直接 `@Autowired DataSource`
- ❌ 不要让 AI Provider 调用阻塞主查询（异步 + 超时）
- ❌ 不要提交 `application-local.yml` / `*.local.properties`
- ❌ 不要改 Spring Boot 4.1 → 3.x（Jakarta → javax 会炸）
- ❌ 不要新增 README 之外的英文 `.md`，除非用户明确要求

---

## 13. 术语速查（精简版）

| 术语 | 含义 |
|------|------|
| `requestId` / `traceId` | 一次 Agent 调用的唯一 ID |
| `taskIndex` | 任务在脚本中的执行序号 |
| `taskUniqueName` | 节点唯一标识 = `planInfo.uniqueName` |
| `log_llm_task_detail` | 任务明细表 |
| `log_llm_task_step` | 任务步骤表（template/rag/parser/verifier/action/result） |
| `log_llm_http_request` | 一次 LLM 调用 |
| `log_llm_agent_main` | Agent 实例表（入口 + 子 Agent） |
| `InsightDatasource` | Mongo 中的数据源配置文档 |
| `InsightTableConfig` | Mongo 中的表配置文档 |

完整术语见 `docs/01-SRS.md` §1.3。

---

## 附录：上下文提示词模板

Agent 在收到任务时，应该在第一轮回复中确认：

```
项目：agent-insight（Spring Boot 4.1 + React 19，分析 Agent 执行链路）
我理解的改动范围：[模块] → [影响文件列表]
我会：[具体步骤]
Verification：[mvn test / npm test / playwright test] 的预期结果
```

如果不能清晰回答上面 4 行 → 回去再读 AGENTS.md §4~§8。