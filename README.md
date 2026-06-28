# Agent Insight 分析引擎

Agent 执行链路分析平台。通过整合 **per-request 日志文件** + **结构化执行轨迹表** + **Agent 脚本源码**，提供源码级、执行轨迹级、LLM 调用级的三维分析能力。

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/01-SRS.md](docs/01-SRS.md) | 需求规格说明书（SRS） |
| [docs/02-HLD.md](docs/02-HLD.md) | 技术设计说明书（HLD） |
| [docs/03-开发指南.md](docs/03-开发指南.md) | 开发指南（接口/代码/部署） |
| [docs/04-测试文档.md](docs/04-测试文档.md) | 测试计划与测试用例 |
| [docs/05-任务排期.md](docs/05-任务排期.md) | 任务排期与里程碑 |

---

## 核心数据源

```
per-request 日志文件
  └─ {dataRoot}/user/{username}/{yyyyMM}/{requestId}.log
       │
       ├─→ log_llm_agent_main      (Agent 实例，入口 + 子 Agent)
       ├─→ log_llm_task_detail     (执行单元，按 taskIndex 排序)
       │       └─→ log_llm_task_step  (步骤级 input/output)
       └─→ log_llm_http_request    (LLM 调用详情)
```

---

## 快速开始

```bash
# 后端
cd agent-insight-server
mvn clean package -DskipTests
java -jar target/agent-insight-server-*.jar

# 前端
cd agent-insight-web
npm install
npm run dev
```

服务端口：`9280`
API 文档：`http://localhost:9280/doc.html`
