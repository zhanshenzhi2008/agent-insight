# Agent Insight — 数据库集合

> 与应用分离部署的 MySQL / MongoDB / Redis 集合。
>
> 本文件仅编排数据库服务，供应用容器连接。适用于生产或独立数据库部署。
>
> 若你使用默认单文件部署，直接运行仓库根目录的 `docker-compose.yml` 即可，无需本目录。

## 何时使用本目录

| 场景 | 使用文件 | 说明 |
|------|----------|------|
| 开发 / 内网 / 单机 | `docker-compose.yml` | 前后端 + 数据库一键启动 |
| 生产（Traefik / 多项目） | 本目录 `docker-compose.yml` | 数据库独立部署，应用通过 `db-net` 连接 |

> 默认部署方案见 [`docs/07-部署文档.md`](../../docs/07-部署文档.md)。

## 部署到服务器

将本目录内容复制到 `/opt/databases/`：

```bash
# 创建目标目录
mkdir -p /opt/databases

# 复制文件（从 docker-traefik 子目录）
cp -r agent-insight/docker-traefik/databases/* /opt/databases/

# 创建 .env（从本目录模板复制）
cd /opt/databases && cp databases.env.example .env

# 编辑 .env，填入 MYSQL_ROOT_PASSWORD 等必填值
vim /opt/databases/.env

# 启动
docker compose up -d
```

## 目录结构

```
/opt/databases/
├── docker-compose.yml     # 数据库编排
├── .env                   # 环境变量（密码等）
├── mysql/
│   ├── init.sql           # MySQL 初始化脚本（首次启动执行）
│   └── data/              # MySQL 数据持久化
├── mongodb/
│   └── data/              # MongoDB 数据持久化
└── redis/
    └── data/              # Redis 数据持久化
```

## 网络

创建的 external 网络：

- **db-net** —— 供应用项目连接使用

应用通过引用此网络名连接数据库（容器名 `mysql` / `mongodb` / `redis`）。

## 常用命令

```bash
cd /opt/databases

docker compose ps                      # 查看状态
docker compose logs -f                 # 查看所有日志
docker compose logs -f mysql            # 查看 MySQL 日志
docker compose logs -f mongodb         # 查看 MongoDB 日志
docker compose logs -f redis           # 查看 Redis 日志
docker compose restart mysql            # 重启单个服务
docker compose stop                    # 停止所有
docker compose down -v                 # 删除数据卷（危险！）
```

## 数据备份

```bash
# MySQL 逻辑备份（确保 .env 已配置 MYSQL_ROOT_PASSWORD）
cd /opt/databases
source .env
docker exec mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" agent_insight > backup.sql

# MongoDB 备份
docker exec mongodb mongodump --db agent_insight --out /tmp/backup

# 文件系统备份（停服后）
tar czf db-backup-$(date +%Y%m%d).tar.gz mysql/data mongodb/data redis/data
```

## 手动设置密码

> 说明：Redis / MongoDB 的密码通过 docker-compose 环境变量配置时存在已知兼容性问题（容器重建不生效、数据文件持久化等），建议在服务启动后手动设置密码。

### Redis 设置密码

```bash
# 1. 进入 Redis 容器
docker exec -it redis redis-cli

# 2. 设置密码（将 your_password 替换为实际密码，注意不要包含 # 符号）
CONFIG SET requirepass "your_password"

# 3. 验证
AUTH your_password
PONG

# 4. 持久化配置（否则容器重启后丢失）
CONFIG REWRITE
```

> 重要：密码中不要包含 `#` 字符，否则会被 `.env` 文件解析为注释。

### MySQL 创建业务用户

```bash
# 1. 进入 MySQL 容器
docker exec -it mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}"

# 2. 创建业务库（insight）
CREATE DATABASE IF NOT EXISTS agent_insight CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

# 3. 创建业务用户并授权（将 your_user / your_password 替换为实际值）
CREATE USER 'your_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON agent_insight.* TO 'your_user'@'%';
FLUSH PRIVILEGES;

# 4. 验证
SELECT user, host FROM mysql.user WHERE user = 'your_user';

# 5. 退出
EXIT;
```

### MongoDB 设置密码

```bash
# 1. 进入 MongoDB 容器
docker exec -it mongodb mongosh admin

# 2. 创建 root 账号（将 your_user / your_password 替换为实际值）
db.createUser({
  user: "your_user",
  pwd: "your_password",
  roles: [{ role: "root", db: "admin" }]
})

# 3. 验证
db.getSiblingDB("admin").auth("your_user", "your_password")

# 4. 退出
exit
```

### 应用连接配置

设置完密码后，需要在应用的 `.env` 文件中添加对应配置（参考 `agent-insight/envs/db.env.example`）：

```bash
# 应用所在服务器的 .env
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password
REDIS_PWD=your_password
MONGO_INITDB_ROOT_USERNAME=your_user
MONGO_INITDB_ROOT_PASSWORD=your_password
```

然后重启应用服务使配置生效。