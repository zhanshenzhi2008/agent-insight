# Agent Insight 分析引擎

![Java](https://img.shields.io/badge/Java-21-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-green.svg)
![React](https://img.shields.io/badge/React-19-blue.svg)
![Vite](https://img.shields.io/badge/Vite-8-yellow.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)
![Tests](https://img.shields.io/badge/Tests-121%20Passed-brightgreen.svg)

Agent 执行链路分析平台。通过整合 **per-request 日志文件** + **结构化执行轨迹表** + **Agent 脚本源码**，提供源码级、执行轨迹级、LLM 调用级的三维分析能力。

> ⚠️ **2026-07-03 重大架构修订**：本仓库已确认向**通用 BI 平台 + llm-agent 模板包**演进。
> - `log_llm_*` 真实存储是 **llm-agent 工程的 MongoDB**，不在本工程库内
> - 本工程**永远不修改** llm-agent 任何代码
> - v1.0 仅实现 **MongoDB 执行器**；MySQL/PostgreSQL 留待 v1.1+
> - 详细修订见 [`docs/00-revision-2026-07-03.md`](./docs/00-revision-2026-07-03.md)
> - 文档版本对应：`01-SRS.md` v0.3 · `06-DataExplorer-HLD.md` v1.2

---

## 核心功能

- **F1 请求检索**：按 requestId / 时间 / Agent 名称多维度检索 Agent 执行记录
- **F2 Per-Request 日志查看**：日志文件读取、分页、正则搜索、跨月路径解析
- **F3 执行轨迹分析**：taskIndex 排序、任务树结构、失败任务筛选、源码行号映射
- **F4 LLM 调用分析**：慢调用 TopN、Token 消耗统计、调用详情
- **F5 源码对照视图**：Monaco Editor + 执行轨迹叠加
- **F6 Data Explorer**：跨数据源（MySQL / PostgreSQL / MongoDB）统一查询、AI 增强分析

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 4.1 | Jakarta EE 10 |
| AI 框架 | Spring AI 2.0 | 多 Provider（OpenAI / DeepSeek / Ollama） |
| 前端框架 | React 19 | + TypeScript 5 |
| 构建工具 | Vite 8 | 极速开发体验 |
| 数据库 | MySQL 8 / PostgreSQL / MongoDB 7 | 多数据源支持 |
| 缓存 | Redis 7 | 查询结果缓存 |
| API 文档 | Knife4j 4.5 | Spring Boot 4 兼容 |
| 连接池 | HikariCP | 动态多数据源连接池 |

---

## 快速开始

### 方法一：Docker Compose（推荐）

```bash
# 克隆项目
git clone <repository-url>
cd agent-insight

# 配置环境变量
cp .env .env.local
# 编辑 .env.local，填入数据库密码等配置

# 启动所有服务（MySQL + MongoDB + Redis + Backend + Frontend）
docker-compose up -d

# 验证服务
curl http://localhost:9280/actuator/health   # 后端健康检查
curl http://localhost:80                      # 前端
```

访问地址：
- 前端：http://localhost:80
- 后端 API：http://localhost:9280
- Swagger 文档：http://localhost:9280/doc.html

### 方法二：本地开发

```bash
# 后端
cd agent-insight-server
mvn clean package -DskipTests
java -jar target/agent-insight-server-*.jar

# 前端（新终端）
cd agent-insight-web
npm install
npm run dev
```

服务端口：`9280`

---

## 项目结构

```
agent-insight/
├── Dockerfile                  # 后端容器镜像
├── Dockerfile.web              # 前端容器镜像（nginx）
├── docker-compose.yml          # 一键部署编排
├── docker-entrypoint.sh        # 后端启动脚本
├── docker/mysql/init.sql       # MySQL 初始化脚本
├── performance/                # JMeter 性能测试脚本
│   ├── PT-01.jmx ~ PT-08.jmx  # 对应 docs/04-测试文档.md 中的 PT-01~PT-08
│   └── README.md
├── agent-insight-server/       # Spring Boot 4.1 后端
│   └── src/main/java/com/llm/insight/
│       ├── controller/          # REST API（5+7 个 Controller）
│       ├── service/            # 业务逻辑层
│       ├── repository/          # JPA Repository（MySQL 日志表）
│       ├── explorer/            # Data Explorer 模块
│       │   ├── engine/         # 查询引擎（Sql/Mongo QueryExecutor）
│       │   ├── controller/      # Explorer API
│       │   ├── document/        # MongoDB 文档（POJO）
│       │   └── ai/              # AI 增强服务
│       ├── dto/                 # 数据传输对象
│       ├── config/              # 配置类
│       └── common/              # 通用类（ApiResponse / PageResult）
├── agent-insight-web/           # React 19 + Vite 8 前端
│   └── src/
│       ├── pages/               # 页面组件
│       ├── components/          # 通用组件（DynamicTable 等）
│       └── services/            # API 调用层
└── docs/                        # 项目文档
    ├── 01-SRS.md               # 需求规格说明书
    ├── 02-HLD.md               # 技术设计说明书
    ├── 03-开发指南.md           # 开发指南
    ├── 04-测试文档.md           # 测试计划与用例
    ├── 05-任务排期.md           # 任务排期与里程碑
    ├── 06-DataExplorer-HLD.md   # Data Explorer HLD
    └── 07-部署文档.md           # 部署指南
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/01-SRS.md](docs/01-SRS.md) | 需求规格说明书（SRS） |
| [docs/02-HLD.md](docs/02-HLD.md) | 技术设计说明书（HLD） |
| [docs/03-开发指南.md](docs/03-开发指南.md) | 开发指南（接口/代码/部署） |
| [docs/04-测试文档.md](docs/04-测试文档.md) | 测试计划与测试用例（含性能测试 PT-01~PT-08） |
| [docs/05-任务排期.md](docs/05-任务排期.md) | 任务排期与里程碑 |
| [docs/07-部署文档.md](docs/07-部署文档.md) | Docker 部署指南与环境变量说明 |

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

## 贡献指南

### Git Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

**type**：`feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore`

**示例**：

```bash
git commit -m "feat(explorer): add MongoDB query executor"
git commit -m "fix(log): resolve path traversal vulnerability"
git commit -m "docs: update deployment guide"
git commit -m "test: add PT-01~PT-08 JMeter scripts"
```

### 分支策略

```
main          — 生产就绪代码
├── develop   — 开发分支（可选）
├── feature/* — 新功能开发
├── fix/*     — Bug 修复
└── docs/*    — 文档更新
```

---

## 测试

```bash
# 后端测试（121 个测试，全量通过）
cd agent-insight-server
mvn test

# 前端测试
cd agent-insight-web
npm test

# 性能测试（JMeter）
# 见 performance/README.md
```

---

## 许可证

MIT License
