#!/bin/bash
# =============================================================================
# scripts/test-frontend-nginx.sh — 验证前端 nginx 镜像的可启动性
# =============================================================================
# 背景：2026-07-19 云端前端容器持续 unhealthy 8371 次
#
# 根因：
#   1. nginx.conf 只监听 IPv4 (listen 80)，不监听 IPv6 ([::]:80)
#   2. wget http://localhost:80 默认解析为 [::1]:80（IPv6 localhost）
#   3. 连接被拒 → HEALTHCHECK 失败 → unhealthy
#
# 此测试验证（修改后必须全绿）：
#   - nginx 同时监听 IPv4 和 IPv6
#   - wget 走 IPv4 (127.0.0.1) 能成功
#   - wget 走 IPv6 ([::1]) 能成功（这是云端 HEALTHCHECK 走的路径）
#   - /health 端点返回 200
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="$SCRIPT_DIR/../agent-insight-web"

# 清理函数（前置定义，因为 bash 不预解析函数）
cleanup_and_exit() {
    local code=${1:-0}
    docker rm -f nginx-test agent-insight-backend 2>/dev/null || true
    docker network rm test-net 2>/dev/null || true
    if [ "$code" -ne 0 ]; then
        exit "$code"
    fi
}

cd "$WEB_DIR"

echo "═══════════════════════════════════════════════════════════════"
echo "🧪 测试前端 nginx 镜像的可启动性"
echo "═══════════════════════════════════════════════════════════════"

# 1. 清理
docker rm -f nginx-test fake-backend agent-insight-backend 2>/dev/null || true
docker network rm test-net 2>/dev/null || true

# 2. 创建测试网络 + fake-backend（让 agent-insight-backend 能解析）
echo ""
echo "→ 准备测试环境（网络 + fake-backend）..."
docker network create test-net >/dev/null
docker run -d --name fake-backend --network test-net nginx:alpine \
    sh -c "while true; do sleep 3600; done" >/dev/null
sleep 1
docker rename fake-backend agent-insight-backend

# 3. 构建测试镜像
echo ""
echo "→ 构建测试镜像..."
docker build -t agent-insight-frontend-test:latest . 2>&1 | tail -3

# 4. 启动容器
echo ""
echo "→ 启动前端容器..."
docker run -d --name nginx-test --network test-net \
    agent-insight-frontend-test:latest >/dev/null
sleep 3

# 5. 容器状态
echo ""
echo "→ 容器状态:"
docker ps --filter name=nginx-test --format "  table {{.Names}}\t{{.Status}}"
echo ""

# ----------------------------------------------------------------------------
# Test 1: IPv4 健康检查
# ----------------------------------------------------------------------------
echo "→ Test 1: IPv4 (127.0.0.1:80)"
if docker exec nginx-test wget --no-verbose --tries=1 --spider \
    "http://127.0.0.1:80/health" 2>&1; then
    echo "  ✅ PASS"
else
    echo "  ❌ FAIL — IPv4 health check failed"
    echo ""
    echo "=== 容器日志 ==="
    docker logs nginx-test
    cleanup_and_exit 1
fi

# ----------------------------------------------------------------------------
# Test 2: IPv6 健康检查（关键！云端 wget 默认走 IPv6）
# ----------------------------------------------------------------------------
echo ""
echo "→ Test 2: IPv6 ([::1]:80) — 这是云端 HEALTHCHECK 走的路径"
if docker exec nginx-test wget --no-verbose --tries=1 --spider \
    "http://[::1]:80/health" 2>&1; then
    echo "  ✅ PASS"
else
    echo "  ❌ FAIL — IPv6 health check failed"
    echo "    这就是云端 unhealthy 8371 次的根因！"
    echo "    nginx.conf 需要添加 'listen [::]:80;' 才能让 IPv6 访问通过"
    echo ""
    echo "=== 容器日志 ==="
    docker logs nginx-test
    cleanup_and_exit 1
fi

# ----------------------------------------------------------------------------
# Test 3: /health 端点响应内容
# ----------------------------------------------------------------------------
echo ""
echo "→ Test 3: /health 端点响应"
HEALTH_OUTPUT=$(docker exec nginx-test wget --no-verbose --tries=1 -O - \
    "http://127.0.0.1:80/health" 2>&1 | grep -v "^--")
echo "$HEALTH_OUTPUT" | sed 's/^/    /'
if echo "$HEALTH_OUTPUT" | grep -q "OK"; then
    echo "  ✅ PASS"
else
    echo "  ❌ FAIL — /health 端点未返回 OK"
    cleanup_and_exit 1
fi

# 清理已在函数里完成（exit 不会返回到这里）
exit 0
