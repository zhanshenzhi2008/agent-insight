# Agent Insight — Traefik 部署指南

> 适用于多项目管理场景，通过 Traefik 实现自动 HTTPS 路由和统一入口管理。

---

## 目录结构

```
agent-insight/
├── docker-traefik/                # Traefik 部署模式（当前项目）
│   ├── manage.sh                 # 一键管理脚本（含 databases / agent-insight 子命令）
│   ├── agent-insight/            # Agent Insight 应用配置（项目独立部署目录）
│   │   ├── docker-compose.yml    # 应用编排（backend + frontend）
│   │   └── envs/                 # 环境变量模板
│   │       ├── db.env.example    # 数据库连接配置
│   │       └── llm.env.example   # LLM Provider 配置
│   ├── databases/                # 数据库集合（MySQL / MongoDB / Redis）
│   │   ├── docker-compose.yml
│   │   ├── databases.env.example # 数据库服务自身配置
│   │   └── mysql/init.sql        # MySQL 初始化脚本
│   └── traefik/                  # Traefik 基础设施配置
│       ├── docker-compose-base.yml   # Traefik + Portainer 基础服务
│       ├── traefik.yml             # Traefik 主配置
│       └── dynamic/
│           └── https.yml           # 动态路由配置
└── ...
```

### 部署后的服务器目录

```
/opt/
├── docker/                        # Traefik 基础设施根目录
│   ├── docker-compose-base.yml    # /opt/docker/docker-compose-base.yml
│   ├── traefik/                   # /opt/docker/traefik/
│   │   ├── traefik.yml
│   │   ├── acme.json
│   │   └── dynamic/
│   └── portainer/
│       └── data/
│
├── databases/                     # 数据库集合（docker-traefik/databases 部署到这里）
│   ├── docker-compose.yml
│   ├── .env
│   ├── mysql/data/
│   ├── mongodb/data/
│   └── redis/data/
│
└── project/                       # 应用项目（每个项目一个目录）
    ├── agent-insight/
    │   └── docker-traefik/        # 当前项目
    └── other-project/
```

### 网络架构

```
┌────────────────────────────────────────────────────────────┐
│                       docker host                          │
│                                                            │
│  ┌──────────────────┐      ┌──────────────────┐           │
│  │   proxy 网络     │      │db-net │           │
│  │  (Traefik 代理)  │      │  (项目数据库网络) │           │
│  ├──────────────────┤      ├──────────────────┤           │
│  │ traefik          │◄─────┤ backend          │           │
│  │ portainer        │      │ frontend         │           │
│  │ backend (从上面) │      │ mysql            │           │
│  │ frontend (从上面)│      │ mongodb          │           │
│  └──────────────────┘      │ redis            │           │
│                            └──────────────────┘           │
│                                                            │
│  /opt/databases 提供 mysql/mongodb/redis                  │
│  /opt/project/agent-insight 提供 backend/frontend         │
│  /opt/docker 提供 traefik/portainer                       │
└────────────────────────────────────────────────────────────┘
```

**关键点**：
- 数据库独立部署在 `/opt/databases`，可被多个应用共享
- 应用通过 `external` 网络 `db-net` 连接数据库
- Traefik 通过 `external` 网络 `proxy` 转发请求到应用

---

## 部署步骤

### Step 1: 创建基础目录

在服务器上执行：

```bash
# 创建基础设施目录
mkdir -p /opt/docker/traefik/dynamic
mkdir -p /opt/docker/traefik/logs
mkdir -p /opt/docker/portainer/data

# 创建数据库目录
mkdir -p /opt/databases/mysql
mkdir -p /opt/databases/mongodb
mkdir -p /opt/databases/redis

# 创建证书存储文件
touch /opt/docker/traefik/acme.json
chmod 600 /opt/docker/traefik/acme.json
```

### Step 2: 复制配置文件

```bash
# Traefik 基础配置
cp -r agent-insight/docker-traefik/traefik/* /opt/docker/traefik/
cp agent-insight/docker-traefik/traefik/docker-compose-base.yml /opt/docker/

# 数据库配置
cp -r agent-insight/docker-traefik/databases/* /opt/databases/
cd /opt/databases && cp databases.env.example .env && cd -

# 应用配置
mkdir -p /opt/app/agent-insight
cp -r agent-insight/docker-traefik/agent-insight/* /opt/app/agent-insight/
cd /opt/app/agent-insight && cp envs/db.env.example envs/db.env && cp envs/llm.env.example envs/llm.env && cd -
```

### Step 3: 修改配置

#### 3.1 修改 Traefik 配置

编辑 `/opt/docker/traefik/traefik.yml`：

```yaml
certificatesResolvers:
  letsencrypt:
    acme:
      email: your@email.com  # 改成你的邮箱
```

编辑 `/opt/docker/traefik/dynamic/https.yml`：

```yaml
# portainer.yourdomain.com 改成你的域名
rule: "Host(`portainer.yourdomain.com`)"
```

#### 3.2 配置 DNS（必须有域名时）

在域名服务商添加 A 记录：

| 主机记录 | 记录类型 | 记录值 |
|----------|----------|--------|
| `portainer` | A | 服务器 IP |
| `insight` | A | 服务器 IP |

### Step 4: 启动基础设施（Traefik + 数据库）

```bash
# 启动数据库
cd /opt/databases
docker compose up -d
docker compose ps   # 验证

# 启动 Traefik
cd /opt/docker
docker compose -f docker-compose-base.yml up -d
docker ps | grep traefik   # 验证
```

### Step 5: 部署 Agent Insight

```bash
cd /opt/app/agent-insight

# 编辑 .env，配置域名和凭据
vim envs/db.env
vim envs/llm.env

# 编辑 DOMAIN（在 docker-compose.yml 中设置）
vim docker-compose.yml  # 找到 DOMAIN: "" 填入域名

# 启动
docker compose up -d

# 查看状态
docker compose ps
```

---

## 部署检查清单

### 首次部署

- [ ] 服务器 Docker 版本 >= 20.10
- [ ] 已创建 `/opt/docker/`、`/opt/databases/` 目录结构
- [ ] 已修改 `traefik.yml` 中的 email
- [ ] 已修改 `https.yml` 中的域名（或改用 IP）
- [ ] DNS 解析已生效（如果有域名）
- [ ] acme.json 权限已设置为 600
- [ ] 数据库服务已启动（`docker ps | grep mysql`）
- [ ] Traefik 基础服务已启动
- [ ] Agent Insight 已启动

### 访问验证

| 服务 | 地址 | 预期结果 |
|------|------|----------|
| 数据库 | `docker ps` | mysql/mongodb/redis 都 healthy |
| Traefik Dashboard | `http://<ip>:8080` | 看到 Dashboard |
| Portainer | `https://portainer.yourdomain.com:9443` | Portainer 登录页 |
| Agent Insight 前端 | `https://insight.yourdomain.com/` | 前端页面 |
| Swagger | `https://insight.yourdomain.com/doc.html` | API 文档 |

---

## 常用命令

### 数据库管理

```bash
cd /opt/databases
docker compose ps                # 查看状态
docker compose logs -f           # 查看日志
docker compose restart mysql     # 重启单个服务
docker compose down -v           # 清理数据（危险！）
```

或通过 agent-insight 的 manage.sh：

```bash
cd agent-insight/docker-traefik
./manage.sh databases start      # 启动数据库
./manage.sh databases stop       # 停止数据库
./manage.sh databases status     # 查看状态
./manage.sh databases logs -f    # 实时日志
```

### Traefik 管理

```bash
cd /opt/docker
docker compose -f docker-compose-base.yml up -d          # 启动
docker compose -f docker-compose-base.yml logs -f        # 查看日志
docker compose -f docker-compose-base.yml restart traefik # 重启
```

### Agent Insight 管理

```bash
cd agent-insight/docker-traefik
./manage.sh start        # 启动
./manage.sh stop         # 停止
./manage.sh restart      # 重启
./manage.sh logs -f      # 查看日志
./manage.sh status       # 查看状态
./manage.sh clean        # 清理应用数据卷（不影响数据库）
```

---

## 添加新项目

当添加新项目时，只需：

1. 在新项目的 `docker-compose.yml` 中：
   - 去掉端口映射（ports），改为 `expose`
   - 加入 `proxy` 网络和 `db-net` 网络（如果需要数据库）：
     ```yaml
     networks:
       - proxy            # Traefik 代理网络
       - db-net  # 数据库网络（如需连接数据库）
     ```
   - 添加 Traefik labels
   - 引用 external 网络：
     ```yaml
     networks:
       proxy:
         external: true
       db-net:
         external: true
     ```

2. 在 DNS 中添加新域名的 A 记录

示例（新项目的 docker-compose.yml）：

```yaml
services:
  my-app:
    image: nginx:alpine
    expose:
      - "80"
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.my-app.rule=Host(`myapp.yourdomain.com`)"
      - "traefik.http.routers.my-app.entrypoints=websecure"
      - "traefik.http.routers.my-app.tls.certresolver=letsencrypt"
      - "traefik.http.services.my-app.loadbalancer.server.port=80"
    networks:
      - proxy
      - db-net

networks:
  proxy:
    name: proxy
    external: true
  db-net:
    name: db-net
    external: true
```

---

## 故障排查

### Traefik 无法发现服务

```bash
# 检查容器是否在 proxy 网络中
docker inspect <container> | grep proxy

# 检查 Traefik 日志
docker logs traefik --tail 100
```

### 应用无法连接数据库

```bash
# 1. 确认数据库容器在运行
docker ps | grep -E 'mysql|mongodb|redis'

# 2. 确认应用在 db-net 网络中
docker inspect agent-insight-backend | grep db-net

# 3. 在应用容器内测试连接
docker exec -it agent-insight-backend bash
ping mysql        # 应能解析到 mysql 容器
nc -zv mysql 3306 # 应能连接
```

### HTTPS 证书申请失败

```bash
# 检查 acme.json 权限
ls -la /opt/docker/traefik/acme.json
# 必须为 -rw-------

# 检查 Let's Encrypt 日志
docker logs traefik | grep acme
```

### 服务无法访问

```bash
# 检查 Traefik 路由
curl -I https://insight.yourdomain.com/

# 查看 Traefik Dashboard
http://<ip>:8080
```

---

## 数据备份

### 数据库数据位置

```bash
/opt/databases/mysql/data/       # MySQL
/opt/databases/mongodb/data/     # MongoDB
/opt/databases/redis/data/       # Redis
```

### 备份示例

```bash
# MySQL 备份（确保 .env 已配置 MYSQL_ROOT_PASSWORD）
cd /opt/databases
source .env
docker exec mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" agent_insight > backup.sql

# MongoDB 备份
docker exec mongodb mongodump --db agent_insight --out /tmp/backup

# 直接备份数据目录（停服后）
tar czf mysql-backup-$(date +%Y%m%d).tar.gz /opt/databases/mysql/data/
```

---

## 安全建议

1. **修改 Traefik Dashboard 密码**：默认无密码，仅内网访问
2. **关闭 Traefik Dashboard**：生产环境建议关闭 `api.dashboard.insecure`
3. **使用强密码**：MySQL、Redis 等服务使用强密码，不要使用默认值
4. **防火墙**：只开放 80/443 端口，其他端口不对外
5. **数据库隔离**：数据库只挂 `db-net`，不暴露到 `proxy` 网络
6. **敏感文件保护**：`.env` 文件不要提交到 git，已通过 `.gitignore` 排除

---

## 架构图

```
                    ┌──────────────────────────┐
   用户浏览器 ──────►│  Traefik (proxy 网络)    │
   https://...      │  - 自动 HTTPS            │
                    │  - 域名路由              │
                    └──────────┬───────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │ backend      │   │ frontend     │   │ portainer    │
    │ (9280)       │   │ (80)         │   │ (9443)       │
    │ Spring Boot  │   │ React+Nginx  │   │ 容器管理      │
    └──────┬───────┘   └──────────────┘   └──────────────┘
           │
           │  db-net 网络
           │
    ┌──────▼───────┐   ┌──────────────┐   ┌──────────────┐
    │ mysql        │   │ mongodb      │   │ redis        │
    │ (3306)       │   │ (27017)      │   │ (6379)       │
    │ 元数据       │   │ 业务日志     │   │ 查询缓存     │
    └──────────────┘   └──────────────┘   └──────────────┘
```