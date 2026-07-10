# Agent Insight — 数据库集合

> 与应用分离部署的 MySQL / MongoDB / Redis 集合。

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