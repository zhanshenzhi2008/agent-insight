# Agent Insight GitHub Actions 自动化部署

| 版本 | 日期 | 作者 | 备注 |
|------|------|------|------|
| v1.0 | 2026-07-11 | - | 初始版本，GitHub Actions 配置说明 |
| v1.1 | 2026-07-20 | - | 修正：DEPLOY_REGISTRY / DEPLOY_REGISTRY_TOKEN → IMAGE_REGISTRY_TOKEN（workflow 实际变量名）；修正 §3.3/§4 引导到 Repository Secrets → Environment `xcy` Secrets（CD workflow 有 `environment: xcy`） |
| v1.2 | 2026-07-21 | - | 新增 §2.3 GitHub Actions 内置变量说明（`github.repository_owner` 等无需配置）；§2.4/§2.5 顺延到 §2.5/§2.6 |

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

> ⚠️ **重要**：本项目 CD workflow（`.github/workflows/cd.yml`）的 build 和 deploy job 都声明了 `environment: xcy`，所有 Secrets **必须挂在 environment `xcy` 下**，不能只挂在 Repository Secrets。
>
> - **CI workflow**（`ci.yml`）不使用任何 secrets（数据库密码通过 `services.env` 在临时容器内本地注入）
> - **CD workflow** 读 secret 的顺序：`environment xcy` → （可选 fallback）→ repository

### 2.0 安装 GitHub CLI

```bash
# macOS
brew install gh

# 登录 GitHub
gh auth login

# 先创建 environment（如果还没创建）
gh api -X PUT repos/<owner>/agent-insight/environments/xcy
```

> 💡 **提示**：推荐使用 GitHub CLI 批量配置（每个命令都带 `--env xcy`），比网页操作更高效。

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
| `IMAGE_REGISTRY_TOKEN` | GitHub Personal Access Token（需 `write:packages` + `read:packages` 权限） | `ghp_xxxxxxxxxxxx` |

> 📝 **注意**：
> - `IMAGE_REGISTRY`（`ghcr.io`）写在 `cd.yml` 的 `env` 段（line 21），**不是 secret**，不需要设置
> - 不要设 `DEPLOY_REGISTRY_TOKEN` 或 `DEPLOY_REGISTRY`，workflow 不读这两个名字（旧版文档误写）

### 2.3 GitHub Actions 内置变量（无需配置）

CD workflow 用到的 `${{ github.xxx }}` 全部是 **GitHub Actions 内置 context**，**每个仓库自动可用，不需要在 Secrets / Variables 里设置**：

| 变量 | 当前 workflow 用法 | 取值示例（仓库 `liujun/agent-insight`） |
|------|-------------------|-------------------------------------|
| `github.repository_owner` | 镜像 namespace（cd.yml line 22, 125） | `liujun` |
| `github.actor` | 触发 workflow 的用户（cd.yml line 188 手动触发时记录） | `liujun` |
| `github.event_name` | 触发事件名（cd.yml line 45） | `push` / `pull_request` / `workflow_dispatch` |
| `github.ref` | 触发 ref（cd.yml line 160） | `refs/heads/main` / `refs/tags/v1.0.0` |
| `github.sha` | 触发 commit SHA（cd.yml line 136, 149） | `a1b2c3d4e5f6...` |
| `github.token` / `secrets.GITHUB_TOKEN` | 临时 token，可推 PR / 写 issue 等 | （自动签发） |

> 💡 **使用规则**：
> - **永远不要**把这些内置变量存到 Repository Secrets / Environment Secrets——不仅冗余，还会因为值变化（push 时 `sha` 每次都不同）导致 secret 失效
> - 完整列表见 [GitHub 官方文档：contexts](https://docs.github.com/en/actions/learn-github-actions/contexts#github-context)

### 2.4 数据库连接

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

### 2.5 AI 配置（可选）

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `AI_ENABLED` | AI 功能开关 | `false` |
| `AI_PROVIDER` | AI Provider | `openai` |
| `OPENAI_API_KEY` | OpenAI API Key | `sk-xxx` |
| `OPENAI_BASE_URL` | OpenAI 代理地址 | `https://api.xty.app/v1` |
| `OPENAI_MODEL` | OpenAI 模型名 | `gpt-4o` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-xxx` |

### 2.6 使用 GitHub CLI 批量配置

安装 `gh` 后，进入项目目录执行以下命令（**需要修改实际值**）：

```bash
# 进入项目目录
cd /path/to/agent-insight

# 服务器连接
gh secret set DEPLOY_HOST --body "你的服务器IP" --env xcy
gh secret set DEPLOY_USER --body "deploy" --env xcy
## github_actions推荐使用 < file 方式。比如ssh文件是 ~/.ssh/github_actions
#gh secret set DEPLOY_SSH_KEY --body "你的SSH私钥内容" --env xcy
gh secret set DEPLOY_SSH_KEY < ~/.ssh/你的SSH文件名 --env xcy
gh secret set DEPLOY_SSH_PORT --body "22" --env xcy
gh secret set DEPLOY_PATH --body "/opt/docker/agent-insight" --env xcy

# 镜像仓库
# IMAGE_REGISTRY 写在 cd.yml 的 env 段（默认 ghcr.io），不是 secret，不需要 gh secret set
# 实际登录用的是 IMAGE_REGISTRY_TOKEN（GitHub PAT，权限：read:packages / write:packages）
gh secret set IMAGE_REGISTRY_TOKEN --body "你的GitHub_PAT" --env xcy

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

# AI 配置（CI/CD 暂不自动同步，由人工维护服务器上的 ../envs/llm.env）
# gh secret set AI_ENABLED --body "false" --env xcy
```

> ⚠️ **说明**：数据库和 AI 配置暂不通过 CI/CD 同步，改为**人工维护**。如需修改配置，请直接登录服务器编辑对应目录下的 env 文件：
> - `/opt/project/envs/db.env` — 数据库连接
> - `/opt/project/envs/llm.env` — LLM 配置

---

## 3. 生成 SSH 密钥

### 3.1 本地生成密钥对

在**本地终端**执行（不是在 GitHub 网页上）：

```bash
# 生成 SSH 密钥对
ssh-keygen -t ed25519 -C "github-actions@xcy" -f ~/.ssh/github_actions

# 查看公钥（添加到服务器）
cat ~/.ssh/github_actions.pub
# 输出类似：
# ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA... github-actions@xcy

# 查看私钥（添加到 GitHub Secrets）
cat ~/.ssh/github_actions
```

### 3.2 将公钥添加到服务器

```bash
# 将公钥追加到服务器的 authorized_keys。注意如果github action DEPLOY_USER 设置的用户访问是deploy则用deploy用户，切记不要用错用户，否则及时添加成功也没权限访问
ssh deploy@你的服务器IP "mkdir -p ~/.ssh && echo 'YOUR_PUBLIC_KEY' >> ~/.ssh/authorized_keys"
 或 
 ssh-copy-id -i ~/.ssh/github_actions.pub  -p 你的服务器PORT deploy@你的服务器IP
```

### 3.3 将私钥添加到 GitHub Secrets（**Environment `xcy` 下**）

1. 复制上一步 `cat ~/.ssh/github_actions` 的完整输出
2. 打开 GitHub 仓库 → **Settings → Environments → `xcy`**
   - 如果 environment `xcy` 还没创建，先点 **"New environment"**，Name 填 `xcy`，创建后再进入
3. 左侧菜单 **"Environment secrets"** → 点击 **"Add secret"**
4. 填写：
   - **Name**: `DEPLOY_SSH_KEY`
   - **Secret**: 粘贴私钥的完整内容（包含 `-----BEGIN OPENSSH PRIVATE KEY-----` 和 `-----END OPENSSH PRIVATE KEY-----`）
5. 点击 **Add secret**

> ⚠️ **必须挂在 `xcy` environment 下**，不能挂在 Repository Secrets 下——CD workflow 的 deploy job 声明了 `environment: xcy`，只读该 environment 的 secrets。

---

## 4. 生成 GitHub Personal Access Token

1. GitHub → 右上角头像 → **Settings**
2. 左侧菜单底部 → **Developer settings**
3. **Personal access tokens → Tokens (classic)**
4. 点击 **Generate new token (classic)**
5. 设置：
   - **Name**: `ghcr-push`（任意名称）
   - **Expiration**: 建议 30 天或 90 天
   - **Scopes**: 必须勾选
     - ✅ `write:packages`（推镜像到 ghcr.io，**必须**）
     - ✅ `read:packages`（拉镜像，**必须**）
6. 点击 **Generate token**
7. **立即复制**，关闭页面后无法再查看

将生成的 Token 填入 GitHub Secrets（**Environment `xcy` 下**）：
- 路径：**Settings → Environments → `xcy` → Environment secrets → Add secret**
- Name: `IMAGE_REGISTRY_TOKEN`（与 `.github/workflows/cd.yml` 中的 `secrets.IMAGE_REGISTRY_TOKEN` 对应）
- Secret: 粘贴 Token

> ⚠️ **不要设** `DEPLOY_REGISTRY_TOKEN` 或 `DEPLOY_REGISTRY`，这是文档旧版误写，workflow 不读这两个名字。
> ⚠️ **必须挂在 environment `xcy` 下**，不要挂在 Repository Secrets 下。

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
