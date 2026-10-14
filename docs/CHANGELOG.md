# Changelog

> agent-insight 项目变更日志。记录值得注意的安全事件、breaking change、迁移说明。
> 时间戳格式：YYYY-MM-DD。
> 旧版 commit message 历史请查阅 `git log`。

---

## 2026-07-19 — 安全事件：`.env` 文件意外提交历史

### 🔴 严重级别：高

### 事件描述

`.env` 文件（含数据库密码，已轮换；此处不复述明文）此前已在以下 commit 中被意外提交至 `origin/main`：

| Commit | 说明 |
|--------|------|
| `c48f554 refactor 测试` | 包含 `.env` |
| `47ce06d refactor safe` | 包含 `.env` |
| `ba0ac81 feat mongodb配置：自动创建索引和时区问题` | 包含 `.env`（`develop` 分支） |
| `5408dd6 feat 修改配置` | 包含 `.env`（`develop` 分支） |

**风险面**：`origin` 为公开仓库，所有 GitHub 用户可 clone。泄露密码与当时云端 MySQL root 相同（来自旧 `docker-compose.yml` 默认值），必须轮换。

### 修复时间线

- **2026-07-19 立即**：本地 `.env` 已备份至 `~/.agent-insight-secrets/`（权限 600）
- **2026-07-19 立即**：`git rm --cached .env` 从索引移除（保留工作区文件）
- **2026-07-19 已完成**：加强 `.gitignore` 规则（覆盖 `**/.env`、`**/envs/*.env`、`*.pem`、`*.key` 等）
- **2026-07-19 已完成**：添加 `scripts/hooks/pre-commit`，8/8 测试通过，拦截 .env 文件与明文凭据
- **2026-07-19 已完成**：添加 `.github/workflows/gitleaks.yml` + `.gitleaks.toml`，每次 push / PR 自动扫描
- **待办（用户亲自执行）**：
  1. 在云端服务器轮换 MySQL root 密码：`ALTER USER 'root'@'%' IDENTIFIED BY '<新密码>';`
  2. 执行 `bash scripts/clean-secret-history.sh` 清理 git 历史 + force-push
  3. 通知团队所有协作者：`git pull --rebase` 不再适用，必须 `rm -rf agent-insight && git clone ...`

### 经验教训（写入 AGENTS.md §6.4）

1. **默认密码必须改**：弱默认值 / 示例密码不要进 git 或生产配置
2. **`.gitignore` 应该最严格**：哪怕只是开发用的 `.env`，也不能进 git
3. **pre-commit hook 是最后一道防线**：本地拦截比远端审计更重要
4. **Gitleaks 是补充而非替代**：扫描历史能找到 hook 漏过去的旧 commit
5. **凭据一旦进 git 历史就视同泄露**：必须轮换，不能只删 commit

### 相关文件

- `.gitignore` — 加固的忽略规则
- `scripts/hooks/pre-commit` — 本地拦截 hook
- `scripts/clean-secret-history.sh` — 历史清理脚本（含 force-push + 回滚锚点）
- `.github/workflows/gitleaks.yml` — 远端扫描工作流
- `.gitleaks.toml` — 自定义扫描规则（国产 LLM / MySQL / Spring Boot）
- `~/.agent-insight-secrets/` — 本地 `.env` 备份（git 之外）

---

## 历史记录

更早的变更记录请查阅 `git log` 与 `docs/05-任务排期.md`。
