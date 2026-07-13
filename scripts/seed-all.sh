#!/bin/bash
# ============================================================================
# Agent Insight — 一键造数脚本
# ----------------------------------------------------------------------------
# 用途：CI / 本地启动时执行，自动注入所有测试数据
# 用法：
#   ./scripts/seed-all.sh                    # 全量造数
#   ./scripts/seed-all.sh --only mysql       # ⚠️ DEPRECATED，W4 前仅兜底用
#   ./scripts/seed-all.sh --only mongodb     # 仅 MongoDB（推荐）
#   ./scripts/seed-all.sh --only large-log   # 仅生成大日志
#   ./scripts/seed-all.sh --skip-check       # 跳过连通性检查
# ----------------------------------------------------------------------------
# 依赖：mysql client、mongosh（CI 镜像需预装）
# 退出码：0 成功；非 0 表示某一步失败
#
# ⚠️ 2026-07-03 架构修订
#   - MySQL 步骤已废弃，仅 W4 前保留兜底
#   - 详见 docs/00-revision-2026-07-03.md
# ============================================================================
set -euo pipefail

# ----------------------------------------------------------------------------
# 路径与默认值（与 application.yml 保持一致）
# ----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# fixtures/ 在仓库根（与 src/ 平级），便于跨模块共享 & 一键造数
FIXTURE_DIR="${PROJECT_ROOT}/fixtures"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-agent_insight}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root830i}"

MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017/agent_insight}"

# ----------------------------------------------------------------------------
# 参数解析
# ----------------------------------------------------------------------------
TARGET="all"
SKIP_CHECK=false

usage() {
  cat <<'EOF'
用法: ./scripts/seed-all.sh [选项]

选项:
  --only=<target>   仅执行某一步：mysql / mongodb / large-log（默认 all）
  --skip-check      跳过数据库连通性检查
  -h, --help        显示本帮助

示例:
  ./scripts/seed-all.sh
  ./scripts/seed-all.sh --only=mysql
  ./scripts/seed-all.sh --skip-check
EOF
}

# 用 while 替代 for，避免 shift 在 for 循环里的踩坑
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only=*)   TARGET="${1#--only=}"; shift ;;
    --only)     shift; TARGET="${1:-all}" ;;
    --skip-check) SKIP_CHECK=true; shift ;;
    -h|--help)  usage; exit 0 ;;
    *) echo "未知参数: $1"; usage; exit 1 ;;
  esac
done

# ----------------------------------------------------------------------------
# 工具函数
# ----------------------------------------------------------------------------
log_info()  { echo -e "\033[0;34m[INFO]\033[0m  $*"; }
log_ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
log_warn()  { echo -e "\033[0;33m[WARN]\033[0m  $*"; }
log_err()   { echo -e "\033[0;31m[ERR]\033[0m   $*"; }

check_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_err "命令不存在: $1，请先安装（参见 docs/04-测试文档.md §6）"
    return 1
  fi
}

check_connectivity() {
  local kind="$1"
  case "$kind" in
    mysql)
      if ! mysqladmin -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
                      -u "$MYSQL_USERNAME" -p"$MYSQL_PASSWORD" ping >/dev/null 2>&1; then
        log_err "MySQL 不可达: ${MYSQL_HOST}:${MYSQL_PORT}"
        return 1
      fi
      ;;
    mongodb)
      if ! command -v mongosh >/dev/null 2>&1; then
        log_warn "mongosh 未安装，跳过连通性检查"
        return 0
      fi
      if ! mongosh "$MONGODB_URI" --quiet --eval 'db.runCommand({ping:1})' >/dev/null 2>&1; then
        log_err "MongoDB 不可达: $MONGODB_URI"
        return 1
      fi
      ;;
  esac
}

# ----------------------------------------------------------------------------
# ⚠️ DEPRECATED: 步骤 1：MySQL 造数（W4 前老 Service 兜底用）
#   详见 docs/00-revision-2026-07-03.md
# ----------------------------------------------------------------------------
step_mysql() {
  log_warn "⚠️ step_mysql() 已废弃，仅 W4 前兜底。详见 docs/00-revision-2026-07-03.md"
  log_info "▶ MySQL 造数 (host=${MYSQL_HOST}:${MYSQL_PORT}, db=${MYSQL_DB})"
  check_cmd mysql || return 1
  [ "$SKIP_CHECK" = true ] || check_connectivity mysql || return 1

  local sql_file="${FIXTURE_DIR}/mysql/init-log-llm.sql"
  if [ ! -f "$sql_file" ]; then
    log_err "找不到 SQL 文件: $sql_file"
    return 1
  fi

  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
        -u "$MYSQL_USERNAME" -p"$MYSQL_PASSWORD" \
        "$MYSQL_DB" < "$sql_file"

  log_ok "MySQL 造数完成"
}

# ----------------------------------------------------------------------------
# 步骤 2：MongoDB
# ----------------------------------------------------------------------------
step_mongodb() {
  log_info "▶ MongoDB 造数 (uri=${MONGODB_URI})"
  check_cmd mongosh || return 1
  [ "$SKIP_CHECK" = true ] || check_connectivity mongodb || return 1

  local js_file="${FIXTURE_DIR}/mongodb/init-insight-meta.js"
  if [ ! -f "$js_file" ]; then
    log_err "找不到 JS 文件: $js_file"
    return 1
  fi

  mongosh "$MONGODB_URI" --quiet "$js_file"

  log_ok "MongoDB 造数完成"
}

# ----------------------------------------------------------------------------
# 步骤 3：大日志文件（按需生成）
# ----------------------------------------------------------------------------
step_large_log() {
  log_info "▶ 生成大日志文件"
  local gen_script="${FIXTURE_DIR}/logs/generate-large-log.sh"
  if [ ! -f "$gen_script" ]; then
    log_err "找不到脚本: $gen_script"
    return 1
  fi
  (cd "$(dirname "$gen_script")" && bash generate-large-log.sh)
  log_ok "大日志生成完成"
}

# ----------------------------------------------------------------------------
# 主流程
# ----------------------------------------------------------------------------
echo "================================================================"
echo " Agent Insight — Test Data Seeder"
echo "================================================================"
echo " Fixture dir: $FIXTURE_DIR  (仓库根 fixtures/, 跨模块共享)"
echo " Target     : $TARGET"
echo "================================================================"

case "$TARGET" in
  all)
    step_mysql
    step_mongodb
    step_large_log
    ;;
  mysql)    step_mysql ;;
  mongodb)  step_mongodb ;;
  large-log) step_large_log ;;
  *)
    log_err "未知 --only 值: $TARGET（应为 mysql / mongodb / large-log / all）"
    exit 1
    ;;
esac

echo "================================================================"
log_ok "全部造数完成 ✓"
echo "================================================================"
echo " 下一步：cd agent-insight-server && mvn test"