#!/bin/bash
# =============================================================================
# Agent Insight Traefik 部署 — 一键管理脚本
# =============================================================================
# 用法：
#   ./manage.sh init          初始化（创建网络、acme.json）
#   ./manage.sh start         启动所有服务
#   ./manage.sh stop          停止所有服务
#   ./manage.sh restart       重启所有服务
#   ./manage.sh logs          查看后端日志
#   ./manage.sh logs -f       实时查看后端日志
#   ./manage.sh status        查看服务状态
#   ./manage.sh clean         清理数据卷（慎用！）
#   ./manage.sh traefik       启动/停止 Traefik 基础服务
#   ./manage.sh databases     管理数据库集合（/opt/databases）
#   ./manage.sh help          显示帮助
#
# 依赖：
#   - docker & docker compose v2
#   - Traefik 基础服务（./manage.sh traefik start）
#   - 数据库服务（./manage.sh databases start）
# =============================================================================

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 目录定义
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PARENT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# /opt/docker 路径布局：
#   /opt/docker/docker-compose-base.yml
#   /opt/docker/traefik/             (Traefik 配置)
#   /opt/docker/portainer/data/
BASE_DIR="${PARENT_DIR}/docker"
DATABASES_DIR="${PARENT_DIR}/databases"
# Agent Insight 应用配置在 agent-insight/ 子目录
COMPOSE_FILE="${SCRIPT_DIR}/agent-insight/docker-compose.yml"
ENV_DIR="${SCRIPT_DIR}/agent-insight/envs"
DB_ENV_FILE="${ENV_DIR}/db.env"
LLM_ENV_FILE="${ENV_DIR}/llm.env"
# Traefik 配置文件目录（/opt/docker/traefik/）
TRAEFIK_DIR="${BASE_DIR}/traefik"

log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}   $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_err()   { echo -e "${RED}[ERR]${NC}   $*" >&2; }

# 检查环境
check_env() {
  if ! command -v docker >/dev/null 2>&1; then
    log_err "Docker 未安装，请先安装 Docker"
    exit 1
  fi

  if ! docker compose version >/dev/null 2>&1; then
    log_err "docker compose v2 未安装，请升级 Docker"
    exit 1
  fi

  # 检查 DOMAIN 是否在 docker-compose.yml 中已填值
  if grep -q '^[[:space:]]*DOMAIN: ""' "${COMPOSE_FILE}"; then
    log_err "DOMAIN 未配置，请编辑 ${COMPOSE_FILE} 填写域名"
    exit 1
  fi

  # 检查凭据文件（db.env / llm.env）
  check_env_files

  # 检查 proxy 网络
  if ! docker network ls | grep -q "^proxy "; then
    log_warn "proxy 网络不存在，请先运行 ./manage.sh traefik"
  fi

  # 检查 db-net 网络（由 databases 创建）
  if ! docker network ls | grep -q "^db-net "; then
    log_warn "db-net 网络不存在，请先运行 ./manage.sh databases start"
  fi
}

# 检查 Traefik 基础服务
check_traefik() {
  if ! docker ps --format '{{.Names}}' | grep -q "^traefik$"; then
    log_warn "Traefik 未运行，请先运行 ./manage.sh traefik"
    return 1
  fi
}

# 检查数据库服务
check_databases() {
  local missing=()
  for svc in mysql mongodb redis; do
    if ! docker ps --format '{{.Names}}' | grep -q "^agent-insight-${svc}$"; then
      missing+=("${svc}")
    fi
  done

  if [ ${#missing[@]} -gt 0 ]; then
    log_warn "以下数据库服务未运行：${missing[*]}"
    log_warn "请先运行 ./manage.sh databases start"
    return 1
  fi
}

# 检查凭据文件（envs/db.env 与 envs/llm.env）
check_env_files() {
  local ok=0

  # db.env 必须存在，且 MYSQL_PASSWORD 非空
  if [ ! -f "${DB_ENV_FILE}" ]; then
    log_err "缺少凭据文件 ${DB_ENV_FILE}"
    log_info "  请执行: cp ${ENV_DIR}/db.env.example ${DB_ENV_FILE}"
    ok=1
  elif grep -Eq '^[[:space:]]*MYSQL_PASSWORD=($|[[:space:]]*$)' "${DB_ENV_FILE}"; then
    log_err "MYSQL_PASSWORD 未配置，请编辑 ${DB_ENV_FILE} 填写数据库密码"
    ok=1
  fi

  # llm.env：缺文件则提示创建（但允许留空，因为 AI_ENABLED=false 时不需要）
  if [ ! -f "${LLM_ENV_FILE}" ]; then
    log_warn "缺少凭据文件 ${LLM_ENV_FILE}（AI_ENABLED=false 时可忽略）"
    log_info "  创建方法: cp ${ENV_DIR}/llm.env.example ${LLM_ENV_FILE}"
  fi

  if [ "${ok}" = "1" ]; then
    exit 1
  fi
}

# 初始化
do_init() {
  log_info "初始化 Agent Insight Traefik 部署环境..."

  # 检查 proxy 网络
  if ! docker network ls | grep -q "^proxy "; then
    log_info "创建 proxy 网络..."
    docker network create proxy 2>/dev/null || true
    log_ok "proxy 网络已创建"
  fi

  # 创建 Traefik acme.json（/opt/docker/traefik/acme.json）
  if [ ! -f "${TRAEFIK_DIR}/acme.json" ]; then
    mkdir -p "${TRAEFIK_DIR}/dynamic"
    mkdir -p "${TRAEFIK_DIR}/logs"
    touch "${TRAEFIK_DIR}/acme.json"
    chmod 600 "${TRAEFIK_DIR}/acme.json"
    log_ok "已创建 Traefik acme.json"
  fi

  # 创建 envs/ 目录并自动复制凭据模板（首次部署友好）
  mkdir -p "${ENV_DIR}"
  for f in db.env llm.env; do
    if [ ! -f "${ENV_DIR}/${f}" ] && [ -f "${ENV_DIR}/${f}.example" ]; then
      cp "${ENV_DIR}/${f}.example" "${ENV_DIR}/${f}"
      log_ok "已创建 ${ENV_DIR}/${f}（请按需填写凭据）"
    fi
  done

  log_ok "初始化完成！"
  echo ""
  echo "下一步："
  echo "  1. 编辑 ${COMPOSE_FILE} 填写 DOMAIN"
  echo "  2. 编辑 ${DB_ENV_FILE} 填写 MYSQL_PASSWORD（与 /opt/databases 保持一致）"
  echo "     如需启用 AI，请编辑 ${LLM_ENV_FILE}"
  echo "  3. 运行 ./manage.sh databases start 启动数据库"
  echo "  4. 运行 ./manage.sh traefik start 启动 Traefik"
  echo "  5. 运行 ./manage.sh start 启动 Agent Insight"
}

# 启动 Traefik 基础服务
do_traefik_start() {
  log_info "启动 Traefik 基础服务..."

  if [ ! -d "${BASE_DIR}" ]; then
    log_err "未找到 /opt/docker 目录，请先创建 Traefik 基础配置"
    log_info "参考: https://docs.agent-insight.com/deployment#traefik"
    exit 1
  fi

  cd "${BASE_DIR}"
  docker compose up -d traefik
  log_ok "Traefik 已启动"
  log_info "Dashboard: http://localhost:8080"
}

# 停止 Traefik
do_traefik_stop() {
  log_info "停止 Traefik..."
  cd "${BASE_DIR}"
  docker compose stop traefik
  log_ok "Traefik 已停止"
}

# 数据库管理 - 启动
do_databases_start() {
  log_info "启动数据库集合..."

  if [ ! -d "${DATABASES_DIR}" ]; then
    log_err "未找到 ${DATABASES_DIR} 目录"
    log_info "请先将 agent-insight-databases/ 部署到 ${DATABASES_DIR}"
    log_info "  cp -r agent-insight-databases/* ${DATABASES_DIR}/"
    exit 1
  fi

  # 创建数据库 .env
  if [ ! -f "${DATABASES_DIR}/.env" ]; then
    if [ -f "${DATABASES_DIR}/.env.example" ]; then
      cp "${DATABASES_DIR}/.env.example" "${DATABASES_DIR}/.env"
      log_warn "已从 .env.example 创建数据库 .env"
      log_info "请检查 ${DATABASES_DIR}/.env 配置"
    fi
  fi

  cd "${DATABASES_DIR}"
  docker compose --env-file "${DATABASES_DIR}/.env" up -d
  log_ok "数据库已启动"

  # 等待健康检查
  log_info "等待数据库健康检查..."
  sleep 5
  docker compose --env-file "${DATABASES_DIR}/.env" ps
}

# 数据库管理 - 停止
do_databases_stop() {
  log_info "停止数据库..."
  if [ ! -d "${DATABASES_DIR}" ]; then
    log_warn "未找到 ${DATABASES_DIR}，跳过"
    return
  fi
  cd "${DATABASES_DIR}"
  docker compose --env-file "${DATABASES_DIR}/.env" stop
  log_ok "数据库已停止"
}

# 数据库管理 - 重启
do_databases_restart() {
  do_databases_stop
  do_databases_start
}

# 数据库管理 - 查看日志
do_databases_logs() {
  if [ ! -d "${DATABASES_DIR}" ]; then
    log_err "未找到 ${DATABASES_DIR}"
    exit 1
  fi
  cd "${DATABASES_DIR}"
  docker compose --env-file "${DATABASES_DIR}/.env" logs -f "${@}"
}

# 数据库管理 - 查看状态
do_databases_status() {
  if [ ! -d "${DATABASES_DIR}" ]; then
    log_warn "未找到 ${DATABASES_DIR}"
    return
  fi
  cd "${DATABASES_DIR}"
  docker compose --env-file "${DATABASES_DIR}/.env" ps
}

# 数据库管理 - 清理（危险操作）
do_databases_clean() {
  log_warn "清理将删除所有数据库数据，确定继续？ [y/N]"
  read -r answer
  if [[ "$answer" =~ ^[Yy]$ ]]; then
    log_info "清理数据库..."
    cd "${DATABASES_DIR}"
    docker compose --env-file "${DATABASES_DIR}/.env" down -v
    log_ok "清理完成"
  else
    log_info "已取消"
  fi
}

# 启动
do_start() {
  check_env

  # 检查数据库
  if ! check_databases 2>/dev/null; then
    log_warn "数据库未运行，是否启动？ [y/N]"
    read -r answer
    if [[ "$answer" =~ ^[Yy]$ ]]; then
      do_databases_start
    else
      log_err "数据库未运行，应用启动后无法连接"
      exit 1
    fi
  fi

  # 检查 Traefik
  if ! check_traefik 2>/dev/null; then
    log_warn "Traefik 未运行，是否启动？ [y/N]"
    read -r answer
    if [[ "$answer" =~ ^[Yy]$ ]]; then
      do_traefik_start
    fi
  fi

  log_info "启动 Agent Insight..."
  cd "${SCRIPT_DIR}/agent-insight"
  docker compose -f "${COMPOSE_FILE}" up -d
  log_ok "Agent Insight 已启动"
  echo ""
  echo "服务地址（请确认 docker-compose.yml 里 DOMAIN 已填）："
  echo "  查看域名：grep '^[[:space:]]*DOMAIN:' ${COMPOSE_FILE}"
}

# 停止
do_stop() {
  log_info "停止 Agent Insight..."
  cd "${SCRIPT_DIR}/agent-insight"
  docker compose -f "${COMPOSE_FILE}" stop
  log_ok "Agent Insight 已停止"
}

# 重启
do_restart() {
  do_stop
  do_start
}

# 查看日志
do_logs() {
  cd "${SCRIPT_DIR}/agent-insight"
  docker compose -f "${COMPOSE_FILE}" logs -f agent-insight-backend "${@}"
}

# 查看状态
do_status() {
  echo ""
  echo "=== Agent Insight 服务状态 ==="
  cd "${SCRIPT_DIR}/agent-insight"
  docker compose -f "${COMPOSE_FILE}" ps

  echo ""
  echo "=== 数据库状态 ==="
  if [ -d "${DATABASES_DIR}" ]; then
    cd "${DATABASES_DIR}"
    docker compose --env-file "${DATABASES_DIR}/.env" ps 2>/dev/null || log_warn "数据库未启动"
  else
    log_warn "${DATABASES_DIR} 不存在"
  fi

  echo ""
  echo "=== Traefik 路由状态 ==="
  if docker ps --format '{{.Names}}' | grep -q "^traefik$"; then
    docker exec traefik traefik healthcheck --api
  else
    log_warn "Traefik 未运行"
  fi
}

# 清理
do_clean() {
  log_warn "清理将删除 Agent Insight 应用数据卷（不影响数据库），确定继续？ [y/N]"
  read -r answer
  if [[ "$answer" =~ ^[Yy]$ ]]; then
    log_info "清理中..."
    cd "${SCRIPT_DIR}/agent-insight"
    docker compose -f "${COMPOSE_FILE}" down -v
    log_ok "清理完成"
  else
    log_info "已取消"
  fi
}

# 帮助
do_help() {
  cat <<'EOF'
Agent Insight Traefik 部署管理脚本

用法: ./manage.sh <命令>

命令:
  init           初始化部署环境
  start          启动 Agent Insight 应用
  stop           停止 Agent Insight 应用
  restart        重启 Agent Insight 应用
  logs [选项]    查看应用日志（支持 docker compose logs 选项）
  status         查看服务状态
  clean          清理应用数据卷（不影响数据库）
  traefik        管理 Traefik 基础服务
                   - 无参数: 显示帮助
                   - start: 启动 Traefik
                   - stop:  停止 Traefik
  databases      管理数据库集合（MySQL/MongoDB/Redis）
                   - 无参数: 显示帮助
                   - start: 启动数据库
                   - stop:  停止数据库
                   - restart: 重启数据库
                   - logs:   查看数据库日志
                   - status: 查看数据库状态
                   - clean:  清理数据库数据卷（危险！）
  help           显示本帮助

示例:
  ./manage.sh init                      # 首次使用
  ./manage.sh databases start           # 启动数据库
  ./manage.sh traefik start             # 启动 Traefik
  ./manage.sh start                     # 启动应用
  ./manage.sh logs -f                  # 实时日志
  ./manage.sh databases logs -f        # 数据库实时日志
EOF
}

# 主流程
case "${1:-help}" in
  init)      do_init ;;
  start)     do_start ;;
  stop)      do_stop ;;
  restart)   do_restart ;;
  logs)      shift; do_logs "${@}" ;;
  status)    do_status ;;
  clean)     do_clean ;;
  traefik)
    case "${2:-help}" in
      start) do_traefik_start ;;
      stop)  do_traefik_stop ;;
      *)     do_help ;;
    esac
    ;;
  databases)
    case "${2:-help}" in
      start)   do_databases_start ;;
      stop)    do_databases_stop ;;
      restart) do_databases_restart ;;
      logs)    shift; shift; do_databases_logs "${@}" ;;
      status)  do_databases_status ;;
      clean)   do_databases_clean ;;
      *)       do_help ;;
    esac
    ;;
  help|--help|-h) do_help ;;
  *)
    log_err "未知命令: $1"
    do_help
    exit 1
    ;;
esac