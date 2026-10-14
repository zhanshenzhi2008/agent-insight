# Agent Insight Traefik 路由方案

本目录三个 Compose 文件**只能选一个**部署（容器名相同，不可并存）。

| 文件 | 适用场景 | 推荐度 |
| --- | --- | --- |
| `docker-compose.yml` | 前端 Nginx 代理 `/api/*`，Traefik 只挂前端 | **推荐** |
| `docker-compose.b-traefik-api.yml` | Traefik 同域分流 `/api/*` → 后端 | 按需 |
| `docker-compose.c-separate-domains.yml` | 前后端不同域名 | 按需 |

## 配置约定（项目独立 `.env`）

```
/opt/project/agent-insight/
├── docker-compose.yml   # 或 b / c 方案
├── .env                 # cp .env.example .env 后填写
└── data/                # DATA_ROOT 默认挂载点
```

- **不再使用** `/opt/project/envs/`（已废弃）
- 每个工程自己的 `.env`，互不影响
- cogniforge 家族（backend / ai / web）共用 `/opt/project/cogniforge/.env`
- agent-insight 只用 `/opt/project/agent-insight/.env`

前置：external 网络 `db-net`、`proxy` 已存在；Traefik 已加入 `proxy`。

## 方案 A（推荐）

```bash
cd /opt/project/agent-insight
cp .env.example .env
# 编辑 .env：DOMAIN、MYSQL_PASSWORD 等
docker compose up -d
```

Portainer Stack 环境变量也可直接填（等价于 `.env`）：

```text
DOMAIN=insight.example.com
DATA_ROOT=/opt/project/agent-insight/data
MYSQL_PASSWORD=change_me
# …其余见 .env.example
```

## 方案 B

同方案 A，改用 `docker-compose.b-traefik-api.yml`；前端镜像须 `VITE_API_BASE_URL=/api`。

## 方案 C

`.env` 中设 `FRONTEND_DOMAIN`、`API_DOMAIN`；前端构建时 `VITE_API_BASE_URL=https://${API_DOMAIN}/api`；后端开 CORS。
