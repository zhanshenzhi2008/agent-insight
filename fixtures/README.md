# Test Fixtures（测试数据夹具）

> ⚠️ **2026-07-03 架构修订 — 部分目录已废弃**
>
> `mysql/` 目录已标记 DEPRECATED，详见：
> - [docs/00-revision-2026-07-03.md](./00-revision-2026-07-03.md) §1
> - [docs/01-SRS.md](./01-SRS.md) v0.3
>
> **log_llm_* 业务表真实存储在 llm-agent 工程的 MongoDB**，不再属于本工程。
> `mysql/` 暂时保留仅供 W4 前老 Service 兜底。

> 本目录所有内容都服务于 **CI 可一键造数** + **Agent 改动后可立即验证**。

## 目录结构

```
fixtures/                              ← 仓库根（与 src/ 平级，跨模块共享）
├── README.md                          ← 本文件
├── mysql/                             ← ⚠️ DEPRECATED，见顶部说明
│   └── init-log-llm.sql              ← ⚠️ DEPRECATED，仅 W4 前兜底
├── mongodb/
│   └── init-insight-meta.js           ← Data Explorer 元数据造数
└── logs/
    ├── req_test_001.log               ← 正常流程（~2KB）
    ├── req_test_002.log               ← 超时失败（~1KB）
    ├── generate-large-log.sh          ← 10MB 大文件生成器
    └── req_test_large.log             ← 大文件（按需生成，不入 git）
```

## 设计原则

1. **幂等**：所有脚本用 `REPLACE INTO` / `replaceOne({...}, {...}, {upsert:true})`，可重跑
2. **时间固定**：所有 `create_time` / `update_time` 都是常量（`2026-06-30 10:00:00`），CI 跑两次结果一致
3. **凭据安全**：datasource 配置中密码字段是 `test_only_encrypted_placeholder`，**不是真实密码**
4. **不依赖外部网络**：所有 host 默认 `localhost`，CI 时通过环境变量覆盖
5. **可独立运行**：可单独执行 MySQL 或 MongoDB 部分

## 一键执行

> ⚠️ MySQL fixture 已废弃：`--only mysql` 仅在 W4 前保留，W4 后请勿使用。

```bash
# 仓库根目录
./scripts/seed-all.sh

# 仅造 MongoDB（推荐）
./scripts/seed-all.sh --only mongodb

# 仅生成大日志
./scripts/seed-all.sh --only large-log

## 与应用配置的关系

| 应用配置（application.yml） | Fixture 脚本 | 状态 | 验证方式 |
|---|---|---|---|
| ~~`spring.datasource.url` → `llm_agent`~~ | ~~`mysql/init-log-llm.sql`~~ | ⚠️ DEPRECATED | ~~`mvn test` 跑集成测试~~ |
| `spring.mongodb.uri` → `agent_insight` | `mongodb/init-insight-meta.js` | ✅ 当前 | `mvn test` 跑 ConfigServiceTest |
| `INSIGHT_LOG_ROOT` → 日志根目录 | `logs/req_test_*.log` | ✅ 当前 | `LogViewerServiceTest` 读文件断言 |

## CI 集成（建议）

> ⚠️ MySQL 步骤已废弃：CI 不再执行 `--only mysql`。

在 `.github/workflows/ci.yml` 中：

```yaml
- name: Seed test data
  run: ./scripts/seed-all.sh
  env:
    MONGODB_URI: mongodb://127.0.0.1:27017/agent_insight

- name: Run tests
  run: mvn test
```

## 添加新 fixture 的流程

1. SQL / JS 文件放进对应子目录
2. 时间字段用**常量**（禁 `NOW()` / `new Date()`）
3. 主键固定（用 `REPLACE INTO` / `upsert`），保证幂等
4. 在 `scripts/seed-all.sh` 注册新文件
5. 更新本 README 的目录结构

## 已知约束

- 大日志文件（10MB）不入 git，按需运行 `generate-large-log.sh`
- Mongo 脚本用 `mongosh` 语法（4.x+），CI 镜像必须安装
- 凭据默认值仅供本地测试；接生产前必走加密 + KMS（见 `AGENTS.md §6.6`）