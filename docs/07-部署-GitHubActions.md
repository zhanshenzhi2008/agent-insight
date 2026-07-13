# Agent Insight GitHub Actions 自动化部署

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v1.0 | 2026-07-11 | - | 初始版本，GitHub Actions 配置说明 |

---

## 1. 概述

本项目使用 GitHub Actions 实现代码提交后自动构建并部署到远程服务器。

### 1.1 部署流程

```
开发者 push 代码
       ↓
  GitHub Actions
       ↓
  ┌────────────┐
  │ Build Job  │ → 构建 Docker 镜像，推送到 GitHub Container Registry
  └────────────┘
       ↓ (仅 main 分支)
  ┌────────────┐
  │ Deploy Job │ → SSH 连接服务器，拉取镜像，重启容器
  └────────────┘
```

### 1.2 相关文档

> 服务器 Docker 环境配置请参考：[docker-traefik/README.md](../docker-traefik/README.md)

---

## 2. 配置 GitHub Secrets

> 💡 **提示**：推荐使用 GitHub CLI 批量配置，比网页操作更高效。

### 2.0 安装 GitHub CLI

```bash
# macOS
brew install gh

# 登录 GitHub
gh auth login
```

### 2.1 服务器连接

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `DEPLOY_HOST` | 服务器 IP 地址 | `156.226.176.141` |
| `DEPLOY_USER` | SSH 用户名（建议创建专用 deploy 用户，不要用 root） | `deploy` |
| `DEPLOY_SSH_KEY` | SSH 私钥（包含 `-----BEGIN` 和 `-----END`） | `-----BEGIN OPENSSH PRIVATE KEY-----...` |
| `DEPLOY_SSH_PORT` | SSH 端口（可选，默认 22） | `22` |
| `DEPLOY_PATH` | 服务器部署目录路径 | `/opt/docker/agent-insight` |

### 2.2 镜像仓库

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `DEPLOY_REGISTRY` | 镜像仓库地址（默认 `ghcr.io`） | `ghcr.io` |
| `DEPLOY_REGISTRY_TOKEN` | GitHub Personal Access Token | `ghp_xxxxxxxxxxxx` |

### 2.3 数据库连接

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `MYSQL_HOST` | MySQL 主机 | `mysql` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DB` | 数据库名 | `agent_insight` |
| `MYSQL_USERNAME` | 数据库用户名 | `root` |
| `MYSQL_PASSWORD` | 数据库密码 | `your_password` |
| `MONGODB_URI` | MongoDB 连接 URI | `mongodb://mongo:27017/agent_insight` |
| `REDIS_HOST` | Redis 主机 | `redis` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PWD` | Redis 密码（无密码留空） | `your_redis_password` |

### 2.4 AI 配置（可选）

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `AI_ENABLED` | AI 功能开关 | `false` |
| `AI_PROVIDER` | AI Provider | `openai` |
| `OPENAI_API_KEY` | OpenAI API Key | `sk-xxx` |
| `OPENAI_BASE_URL` | OpenAI 代理地址 | `https://api.xty.app/v1` |
| `OPENAI_MODEL` | OpenAI 模型名 | `gpt-4o` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-xxx` |

### 2.5 使用 GitHub CLI 批量配置

安装 `gh` 后，进入项目目录执行以下命令（**需要修改实际值**）：

```bash
# 进入项目目录
cd /path/to/agent-insight

# 服务器连接
gh secret set DEPLOY_HOST --body "你的服务器IP" --env xcy
gh secret set DEPLOY_USER --body "deploy" --env xcy
gh secret set DEPLOY_SSH_KEY --body "你的SSH私钥内容" --env xcy
gh secret set DEPLOY_SSH_PORT --body "22" --env xcy
gh secret set DEPLOY_PATH --body "/opt/docker/agent-insight" --env xcy

# 镜像仓库
gh secret set DEPLOY_REGISTRY --body "ghcr.io" --env xcy
gh secret set DEPLOY_REGISTRY_TOKEN --body "你的GitHub_PAT" --env xcy

# 数据库连接（CI/CD 暂不自动同步，由人工维护服务器上的 ../envs/db.env）
# gh secret set MYSQL_HOST --body "你的MySQL主机" --env xcy
# gh secret set MYSQL_PORT --body "3306" --env xcy
# gh secret set MYSQL_DB --body "agent_insight" --env xcy
# gh secret set MYSQL_USERNAME --body "root" --env xcy
# gh secret set MYSQL_PASSWORD --body "你的MySQL密码" --env xcy
# gh secret set MONGODB_URI --body "mongodb://你的MongoDB主机:27017/agent_insight" --env xcy
# gh secret set REDIS_HOST --body "你的Redis主机" --env xcy
# gh secret set REDIS_PORT --body "6379" --env xcy
# gh secret set REDIS_PWD --body "你的Redis密码" --env xcy

# AI 配置（CI/CD 暂不自动同步，由人工维护服务器上的 ../envs/ai.env）
# gh secret set AI_ENABLED --body "false" --env xcy
```

> ⚠️ **说明**：数据库和 AI 配置暂不通过 CI/CD 同步，改为**人工维护**。如需修改配置，请直接登录服务器编辑对应目录下的 env 文件：
> - `/opt/project/envs/db.env` — 数据库连接
> - `/opt/project/envs/ai.env` — AI 配置

---

## 3. 生成 SSH 密钥

### 3.1 本地生成密钥对

在**本地终端**执行（不是在 GitHub 网页上）：

```bash
# 生成 SSH 密钥对
ssh-keygen -t ed25519 -C "github-actions@agent-insight" -f ~/.ssh/github_actions

# 查看公钥（添加到服务器）
cat ~/.ssh/github_actions.pub
# 输出类似：
# ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA... github-actions@agent-insight

# 查看私钥（添加到 GitHub Secrets）
cat ~/.ssh/github_actions
```

### 3.2 将公钥添加到服务器

```bash
# 将公钥追加到服务器的 authorized_keys
ssh root@YOUR_SERVER_IP "mkdir -p ~/.ssh && echo 'YOUR_PUBLIC_KEY' >> ~/.ssh/authorized_keys"
```

### 3.3 将私钥添加到 GitHub Secrets

1. 复制上一步 `cat ~/.ssh/github_actions` 的完整输出
2. 打开 GitHub 仓库 → **Settings → Secrets and variables → Actions**
3. 点击 **New repository secret**
4. Name: `DEPLOY_SSH_KEY`
5. Secret: 粘贴私钥的完整内容（包含 `-----BEGIN OPENSSH PRIVATE KEY-----` 和 `-----END OPENSSH PRIVATE KEY-----`）
6. 点击 **Add secret**

---

## 4. 生成 GitHub Personal Access Token

1. GitHub → 右上角头像 → **Settings**
2. 左侧菜单底部 → **Developer settings**
3. **Personal access tokens → Tokens (classic)**
4. 点击 **Generate new token (classic)**
5. 设置：
   - **Name**: `ghcr-push`（任意名称）
   - **Expiration**: 建议 30 天或 90 天
   - **Scopes**: 勾选 `read:packages`（用于读取镜像）
6. 点击 **Generate token**
7. **立即复制**，关闭页面后无法再查看

将生成的 Token 填入 GitHub Secrets：
- Name: `DEPLOY_REGISTRY_TOKEN`
- Secret: 粘贴 Token

---

## 5. 触发部署

### 5.1 自动触发

push 代码到 `main` 分支时自动触发。

### 5.2 手动触发

1. 进入 GitHub 仓库 → **Actions** 标签页
2. 左侧选择 **Deploy**
3. 点击 **Run workflow**
4. 选择部署服务：
   - `all`：部署全部服务
   - `backend`：仅部署后端
   - `frontend`：仅部署前端

---

## 6. 查看部署状态

进入 GitHub 仓库 → **Actions** 标签页，可以查看：

- 所有 workflow 运行历史
- 每个 job 的详细日志
- 部署状态（成功/失败）

---

## 7. 常见问题

### 7.1 SSH 连接失败

```
Error: ssh: handshake failed
```

**解决**：
1. 检查 `DEPLOY_SSH_KEY` 是否正确（私钥内容，不是公钥）
2. 检查服务器公钥是否在 `~/.ssh/authorized_keys` 中
3. 确认 SSH 端口是否正确（`DEPLOY_SSH_PORT`）

### 7.2 镜像推送失败

```
Error: denied: permission denied
```

**解决**：检查 `DEPLOY_REGISTRY_TOKEN` 是否有效，GitHub PAT 需要 `read:packages` 权限。

### 7.3 部署失败

查看 GitHub Actions 日志确认具体错误，常见原因：
- 服务器 SSH 连接失败
- 服务器未安装 Docker
- 部署目录路径错误

---

## 8. 相关文档

| 文档 | 说明 |
|------|------|
| [docs/07-部署文档.md](07-部署文档.md) | 整体部署方案说明 |
| [docker-traefik/README.md](../docker-traefik/README.md) | 服务器 Docker 环境配置 |
| [.github/workflows/cd.yml](../.github/workflows/cd.yml) | GitHub Actions 部署配置 |
